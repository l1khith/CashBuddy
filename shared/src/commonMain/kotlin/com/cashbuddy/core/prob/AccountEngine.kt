// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.domain.model.TransactionType

/**
 * Stage 4: Multi-Account Net Expense Balance Operator (AccountEngine.kt)
 *
 * Mathematical formulation:
 *
 * ΔE = +A   if Intent = DEBIT ∧ AccType ∈ {BANK, WALLET, BNPL}
 *       0   if Intent ∈ {SELF_TRANSFER, WALLET_LOAD, LIABILITY_REPAYMENT}
 *      -A   if Intent = REFUND
 *
 * Bidirectional transfer detection:
 * If ∃ (e_i, e_j) s.t. A_i = A_j ∧ |t_i - t_j| ≤ ΔT_transfer ∧
 * Intent_i = DEBIT ∧ Intent_j = CREDIT
 * ⟹ Set Intent = SELF_TRANSFER ⟹ ΔE = 0
 */
object AccountEngine {

    const val TRANSFER_WINDOW_MS: Long = 120_000L // 2 minutes

    enum class AccountType {
        BANK, WALLET, BNPL, CREDIT_CARD, CASH, UNKNOWN
    }

    enum class FinancialIntent {
        EXPENSE,
        REFUND,
        INCOME,
        WALLET_LOAD,
        SELF_TRANSFER,
        LIABILITY_REPAYMENT
    }

    data class NetImpactResult(
        val intent: FinancialIntent,
        val netExpenseImpact: Double
    )

    /**
     * Calculates net expense impact ΔE for an isolated transaction.
     */
    fun calculateNetExpense(
        amount: Double,
        type: TransactionType,
        intent: FinancialIntent = FinancialIntent.EXPENSE,
        accountType: AccountType = AccountType.BANK
    ): Double {
        return when (intent) {
            FinancialIntent.EXPENSE -> {
                if (type == TransactionType.DEBIT) amount else 0.0
            }
            FinancialIntent.REFUND -> {
                -amount // Refunds reduce total expense
            }
            FinancialIntent.WALLET_LOAD,
            FinancialIntent.SELF_TRANSFER,
            FinancialIntent.LIABILITY_REPAYMENT -> {
                0.0 // Internal transfers do not change net expense
            }
            FinancialIntent.INCOME -> {
                0.0 // Income is not an expense
            }
        }
    }

    /**
     * Determines whether two transactions form a self-transfer or wallet reload pair.
     */
    fun isTransferPair(
        debitAmount: Double,
        debitTimestamp: Long,
        creditAmount: Double,
        creditTimestamp: Long
    ): Boolean {
        val amountDiff = kotlin.math.abs(debitAmount - creditAmount)
        val timeDiff = kotlin.math.abs(debitTimestamp - creditTimestamp)
        return amountDiff < 0.01 && timeDiff <= TRANSFER_WINDOW_MS
    }
}
