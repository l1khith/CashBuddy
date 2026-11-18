package com.cashbuddy.domain.usecase

import com.cashbuddy.core.CategoryEngine
import com.cashbuddy.core.MerchantLabels
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
    private val settingsRepository: SettingsRepository,
    private val categoryEngine: CategoryEngine? = null
) {
    suspend operator fun invoke(): BatchCategorizeResult {
        val pendingTxs = transactionRepository.getPending().firstOrNull() ?: emptyList()
        val allTxs = transactionRepository.getAll().firstOrNull() ?: emptyList()
        val allCategories = categoryRepository.getAll().firstOrNull() ?: emptyList()
        val autoConfirmThreshold = settingsRepository.getAutoConfirmThreshold().firstOrNull() ?: 10000.0
        val minConfidence = settingsRepository.getMinConfidenceThreshold().firstOrNull() ?: 0.85f

        // 1. Sanitize rules: purge generic/invalid merchant rules
        val allRules = merchantRuleRepository.getAll().firstOrNull() ?: emptyList()
        val validRules = allRules.filter { rule ->
            val isGen = MerchantLabels.isGeneric(rule.pattern)
            if (isGen) {
                merchantRuleRepository.deleteById(rule.id)
            }
            !isGen
        }

        // 2. Sync clean rules to CategoryEngine
        categoryEngine?.let { engine ->
            engine.loadUserRules(validRules.map {
                com.cashbuddy.core.MerchantRuleEntry(
                    merchant = it.pattern,
                    category = it.categoryName ?: "Unknown"
                )
            })
        }

        // 3. Candidates: all pending + confirmed transactions with no category / Uncategorized / Unknown
        val uncategorizedConfirmed = allTxs.filter { tx ->
            tx.status == TransactionStatus.CONFIRMED && (
                tx.categoryName.isNullOrBlank() ||
                tx.categoryName.equals("Uncategorized", ignoreCase = true) ||
                tx.categoryName.equals("Unknown", ignoreCase = true)
            )
        }

        val candidates = (pendingTxs + uncategorizedConfirmed).distinctBy { it.id }

        var newlyCategorized = 0
        var autoConfirmed = 0

        for (tx in candidates) {
            val matchedRule = if (tx.merchant.isNotBlank() && !MerchantLabels.isGeneric(tx.merchant)) {
                merchantRuleRepository.findMatch(tx.merchant)
            } else {
                null
            }

            val category = if (matchedRule != null) {
                allCategories.find { it.id == matchedRule.categoryId }
            } else if (categoryEngine != null && tx.merchant.isNotBlank() && !MerchantLabels.isGeneric(tx.merchant)) {
                val catMatch = categoryEngine.getCategory(tx.merchant, tx.timestamp, tx.amount)
                if (catMatch.confidence >= minConfidence &&
                    catMatch.category != "Unknown" &&
                    !catMatch.category.equals("Uncategorized", ignoreCase = true)
                ) {
                    allCategories.find { it.name.equals(catMatch.category, ignoreCase = true) }
                } else null
            } else null

            if (category == null) continue

            val shouldAutoConfirm = tx.status == TransactionStatus.PENDING && tx.amount < autoConfirmThreshold
            val newStatus = if (shouldAutoConfirm) TransactionStatus.CONFIRMED else tx.status

            val updatedTx = tx.copy(
                categoryId = category.id,
                categoryName = category.name,
                categoryColor = category.color,
                confidence = 1.0f,
                status = newStatus
            )

            transactionRepository.update(updatedTx)
            if (shouldAutoConfirm) {
                autoConfirmed++
            }
            newlyCategorized++
        }

        return BatchCategorizeResult(
            reEvaluatedCount = candidates.size,
            newlyCategorizedCount = newlyCategorized,
            autoConfirmedCount = autoConfirmed
        )
    }
}
