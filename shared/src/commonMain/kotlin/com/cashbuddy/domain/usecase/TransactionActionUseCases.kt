package com.cashbuddy.domain.usecase

import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.MerchantRuleRepository
import com.cashbuddy.domain.repository.TrainingDataRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.platform.currentTimeMillis

import kotlinx.coroutines.flow.firstOrNull

class ManualAddTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val merchantRuleRepository: MerchantRuleRepository? = null,
    private val categoryEngine: com.cashbuddy.core.CategoryEngine? = null,
    private val learnedTokenRepository: com.cashbuddy.domain.repository.LearnedTokenRepository? = null
) {
    suspend operator fun invoke(transaction: Transaction): Long {
        val candidateUtr = transaction.utr
            ?: com.cashbuddy.core.prob.Regexes.UTR.find(transaction.rawText)?.groupValues?.getOrNull(1)?.trim()
            ?: com.cashbuddy.core.prob.Regexes.UTR.find(transaction.notes ?: "")?.groupValues?.getOrNull(1)?.trim()

        if (!candidateUtr.isNullOrBlank()) {
            val existing = transactionRepository.findByUtr(candidateUtr)
            if (existing != null) {
                // Deduplicate: update existing transaction with merchant, category, account, and set status to CONFIRMED
                val updatedTx = existing.copy(
                    merchant = if (isValidMerchantName(transaction.merchant)) transaction.merchant else existing.merchant,
                    categoryId = transaction.categoryId,
                    accountId = transaction.accountId ?: existing.accountId,
                    notes = transaction.notes ?: existing.notes,
                    status = TransactionStatus.CONFIRMED,
                    updatedAt = currentTimeMillis()
                )
                transactionRepository.update(updatedTx)

                if (isValidMerchantName(updatedTx.merchant)) {
                    val merchantNorm = updatedTx.merchant.trim()
                    merchantRuleRepository?.learnRule(
                        merchant = merchantNorm,
                        categoryId = updatedTx.categoryId,
                        priority = USER_RULE_PRIORITY
                    )
                    val catName = transaction.categoryName ?: updatedTx.categoryName
                    if (!catName.isNullOrBlank()) {
                        categoryEngine?.learn(merchantNorm, catName)
                    }
                }
                return existing.id
            }
        }

        val confirmedTx = transaction.copy(
            status = TransactionStatus.CONFIRMED,
            confidence = 1.0f,
            sourceApp = "manual"
        )
        val id = transactionRepository.insert(confirmedTx)

        // Phase 3 Loop: Learn from manually added transactions
        if (transaction.rawText.isNotBlank()) {
            val tokens = com.cashbuddy.core.prob.Tokenizer.tokenize(transaction.rawText)
            if (tokens.isNotEmpty()) {
                learnedTokenRepository?.record(isTransaction = true, tokens = tokens)
            }
        } else if (transaction.merchant.isNotBlank()) {
            val tokens = com.cashbuddy.core.prob.Tokenizer.tokenize(transaction.merchant)
            if (tokens.isNotEmpty()) {
                learnedTokenRepository?.record(isTransaction = true, tokens = tokens)
            }
        }

        if (transaction.merchant.isNotBlank() && isValidMerchantName(transaction.merchant)) {
            val merchantNorm = transaction.merchant.trim()
            merchantRuleRepository?.learnRule(
                merchant = merchantNorm,
                categoryId = transaction.categoryId,
                priority = USER_RULE_PRIORITY
            )
            val catName = transaction.categoryName
            if (!catName.isNullOrBlank()) {
                categoryEngine?.learn(merchantNorm, catName)
            }
        }

        return id
    }
}

class ConfirmTransactionUseCase(
    private val repository: TransactionRepository,
    private val merchantRuleRepository: MerchantRuleRepository? = null,
    private val categoryEngine: com.cashbuddy.core.CategoryEngine? = null,
    private val learnedTokenRepository: com.cashbuddy.domain.repository.LearnedTokenRepository? = null
) {
    suspend operator fun invoke(id: Long) {
        val tx = repository.getById(id).firstOrNull()
        repository.updateStatus(id, TransactionStatus.CONFIRMED)

        // Phase 3 Loop: Record tokens into Zero-Map learned_token table
        if (tx != null && tx.rawText.isNotBlank()) {
            val tokens = com.cashbuddy.core.prob.Tokenizer.tokenize(tx.rawText)
            if (tokens.isNotEmpty()) {
                learnedTokenRepository?.record(isTransaction = true, tokens = tokens)
            }
        }

        // Phase 3 Loop: Learn rule into merchant_rules and CategoryEngine in-memory cache
        if (tx != null && tx.merchant.isNotBlank() && isValidMerchantName(tx.merchant)) {
            val merchantNorm = tx.merchant.trim()
            merchantRuleRepository?.learnRule(
                merchant = merchantNorm,
                categoryId = tx.categoryId,
                priority = USER_RULE_PRIORITY
            )
            val catName = tx.categoryName
            if (!catName.isNullOrBlank()) {
                categoryEngine?.learn(merchantNorm, catName)
            }
        }
    }
}

class RejectTransactionUseCase(
    private val repository: TransactionRepository,
    private val learnedTokenRepository: com.cashbuddy.domain.repository.LearnedTokenRepository? = null
) {
    suspend operator fun invoke(id: Long) {
        val tx = repository.getById(id).firstOrNull()
        // Audit fix B3: delete FIRST, record learning signals AFTER. The old order recorded
        // tokens before the delete — a crash (or failed delete) in between left the tokens
        // recorded as non-transaction for a transaction the user did not successfully
        // reject, and retrying the rejection double-incremented learned_token counts
        // (the upsert blindly adds +1). The new worst case — crash after a successful
        // delete — only loses one training signal, which is harmless and retry-safe.
        repository.deleteById(id)
        if (tx != null && tx.rawText.isNotBlank()) {
            val tokens = com.cashbuddy.core.prob.Tokenizer.tokenize(tx.rawText)
            if (tokens.isNotEmpty()) {
                learnedTokenRepository?.record(isTransaction = false, tokens = tokens)
            }
        }
    }
}

class ModifyTransactionUseCase(
    private val repository: TransactionRepository,
    private val trainingDataRepository: TrainingDataRepository,
    private val merchantRuleRepository: MerchantRuleRepository,
    private val categoryEngine: com.cashbuddy.core.CategoryEngine? = null,
    private val learnedTokenRepository: com.cashbuddy.domain.repository.LearnedTokenRepository? = null
) {
    suspend operator fun invoke(
        transaction: Transaction,
        oldCategoryName: String? = null,
        newCategoryName: String? = null
    ) {
        repository.update(transaction)

        // Phase 3 Loop: Record tokens as valid transaction
        if (transaction.rawText.isNotBlank()) {
            val tokens = com.cashbuddy.core.prob.Tokenizer.tokenize(transaction.rawText)
            if (tokens.isNotEmpty()) {
                learnedTokenRepository?.record(isTransaction = true, tokens = tokens)
            }
        }

        val targetLabel = newCategoryName ?: transaction.categoryName ?: return
        if (!targetLabel.equals(oldCategoryName, ignoreCase = true)) {
            trainingDataRepository.recordCorrection(
                merchant = transaction.merchant,
                sourceApp = transaction.sourceApp,
                rawText = transaction.rawText,
                oldCategory = oldCategoryName,
                newCategory = targetLabel,
                timestamp = currentTimeMillis()
            )
            if (isValidMerchantName(transaction.merchant)) {
                merchantRuleRepository.learnRule(
                    merchant = transaction.merchant.trim(),
                    categoryId = transaction.categoryId,
                    priority = USER_RULE_PRIORITY
                )
                categoryEngine?.learn(transaction.merchant.trim(), targetLabel)
            }
        }
    }
}

private const val USER_RULE_PRIORITY = 100L

private fun isValidMerchantName(name: String): Boolean {
    val trimmed = name.trim()
    return trimmed.length >= 2 && !trimmed.equals("Unknown", ignoreCase = true)
}
