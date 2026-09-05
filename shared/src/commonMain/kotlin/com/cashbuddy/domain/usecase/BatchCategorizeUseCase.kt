package com.cashbuddy.domain.usecase

import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.MerchantRuleRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull

data class BatchCategorizeResult(
    val reEvaluatedCount: Int,
    val newlyCategorizedCount: Int,
    val autoConfirmedCount: Int
)

class BatchCategorizeUseCase(
    private val transactionRepository: TransactionRepository,
    private val merchantRuleRepository: MerchantRuleRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(): BatchCategorizeResult {
        val pendingTxs = transactionRepository.getPending().firstOrNull() ?: emptyList()
        val allCategories = categoryRepository.getAll().firstOrNull() ?: emptyList()
        val autoConfirmThreshold = settingsRepository.getAutoConfirmThreshold().firstOrNull() ?: 10000.0
        val minConfidence = settingsRepository.getMinConfidenceThreshold().firstOrNull() ?: 0.85f

        var newlyCategorized = 0
        var autoConfirmed = 0

        for (tx in pendingTxs) {
            val matchedRule = merchantRuleRepository.findMatch(tx.merchant) ?: continue
            val category = allCategories.find { it.id == matchedRule.categoryId } ?: continue

            val shouldAutoConfirm = tx.amount < autoConfirmThreshold && 1.0f >= minConfidence
            val newStatus = if (shouldAutoConfirm) TransactionStatus.CONFIRMED else TransactionStatus.PENDING

            val updatedTx = tx.copy(
                categoryId = category.id,
                categoryName = category.name,
                categoryColor = category.color,
                confidence = 1.0f,
                status = newStatus
            )

            transactionRepository.update(updatedTx)
            if (shouldAutoConfirm) {
                transactionRepository.updateStatus(tx.id, TransactionStatus.CONFIRMED)
                autoConfirmed++
            }
            newlyCategorized++
        }

        return BatchCategorizeResult(
            reEvaluatedCount = pendingTxs.size,
            newlyCategorizedCount = newlyCategorized,
            autoConfirmedCount = autoConfirmed
        )
    }
}
