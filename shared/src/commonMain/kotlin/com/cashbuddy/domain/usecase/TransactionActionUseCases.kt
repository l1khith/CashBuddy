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
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(transaction: Transaction): Long {
        val confirmedTx = transaction.copy(
            status = TransactionStatus.CONFIRMED,
            confidence = 1.0f,
            sourceApp = "manual"
        )
        return transactionRepository.insert(confirmedTx)
    }
}

class ConfirmTransactionUseCase(
    private val repository: TransactionRepository,
    private val merchantRuleRepository: MerchantRuleRepository? = null,
    private val categoryEngine: com.cashbuddy.core.CategoryEngine? = null
) {
    suspend operator fun invoke(id: Long) {
        val tx = repository.getById(id).firstOrNull()
        repository.updateStatus(id, TransactionStatus.CONFIRMED)

        // Learn rule automatically into merchant_rules and CategoryEngine in-memory cache
        if (tx != null && tx.merchant.isNotBlank() && !isGenericMerchant(tx.merchant)) {
            val merchantNorm = tx.merchant.trim()
            merchantRuleRepository?.learnRule(
                merchant = merchantNorm,
                categoryId = tx.categoryId,
                priority = 100L
            )
            val catName = tx.categoryName
            if (!catName.isNullOrBlank()) {
                categoryEngine?.learn(merchantNorm, catName)
            }
        }
    }

    private fun isGenericMerchant(name: String): Boolean {
        val lower = name.lowercase().trim()
        val generic = setOf("upi", "merchant", "unknown", "payment", "bank", "user", "vpa", "sent", "received")
        return lower.length < 3 || generic.contains(lower)
    }
}

class RejectTransactionUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(id: Long) {
        repository.deleteById(id)
    }
}

class ModifyTransactionUseCase(
    private val repository: TransactionRepository,
    private val trainingDataRepository: TrainingDataRepository,
    private val merchantRuleRepository: MerchantRuleRepository,
    private val categoryEngine: com.cashbuddy.core.CategoryEngine? = null
) {
    suspend operator fun invoke(
        transaction: Transaction,
        oldCategoryName: String? = null,
        newCategoryName: String? = null
    ) {
        repository.update(transaction)

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
            merchantRuleRepository.learnRule(
                merchant = transaction.merchant,
                categoryId = transaction.categoryId,
                priority = 100L
            )
            categoryEngine?.learn(transaction.merchant, targetLabel)
        }
    }
}
