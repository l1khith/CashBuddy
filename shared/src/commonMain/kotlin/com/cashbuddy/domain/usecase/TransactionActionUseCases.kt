package com.cashbuddy.domain.usecase

import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.MerchantRuleRepository
import com.cashbuddy.domain.repository.TrainingDataRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.platform.currentTimeMillis

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

class ConfirmTransactionUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(id: Long) {
        repository.updateStatus(id, TransactionStatus.CONFIRMED)
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
    private val merchantRuleRepository: MerchantRuleRepository
) {
    suspend operator fun invoke(
        transaction: Transaction,
        oldCategoryName: String? = null,
        newCategoryName: String? = null
    ) {
        val modifiedTx = transaction.copy(status = TransactionStatus.MODIFIED)
        repository.update(modifiedTx)

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
        }
    }
}
