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
        // Phase 3 Loop: Record tokens as non-transaction in Zero-Map learned_token table
        if (tx != null && tx.rawText.isNotBlank()) {
            val tokens = com.cashbuddy.core.prob.Tokenizer.tokenize(tx.rawText)
            if (tokens.isNotEmpty()) {
                learnedTokenRepository?.record(isTransaction = false, tokens = tokens)
            }
        }
        repository.deleteById(id)
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
