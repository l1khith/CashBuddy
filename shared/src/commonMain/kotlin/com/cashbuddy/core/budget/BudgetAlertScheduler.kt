// NO-NETWORK
package com.cashbuddy.core.budget

import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.BudgetCategories
import com.cashbuddy.domain.repository.BudgetRepository
import com.cashbuddy.platform.currentTimeMillis
import kotlin.math.roundToInt

data class AlertEvent(
    val budgetId: String,
    val category: String,
    val state: AlertState,
    val message: String
)

interface BudgetNotifier {
    fun sendAlert(event: AlertEvent)
}

class BudgetAlertScheduler(
    private val budgetRepository: BudgetRepository,
    private val budgetEngine: BudgetEngine,
    private val notifier: BudgetNotifier? = null,
    private val clock: () -> Long = { currentTimeMillis() }
) {

    suspend fun checkAndAlert(now: Long = clock()): List<AlertEvent> {
        val activeBudgets = budgetRepository.getActive()
        val firedEvents = mutableListOf<AlertEvent>()

        // Check category alerts before global alerts
        val categoryBudgets = activeBudgets.filter { it.category != BudgetCategories.GLOBAL }
        val globalBudgets = activeBudgets.filter { it.category == BudgetCategories.GLOBAL }
        val orderedBudgets = categoryBudgets + globalBudgets

        for (budget in orderedBudgets) {
            val range = budgetEngine.periodRange(budget.period, now)

            // Reset state if period has rolled over since last alert
            var lastState = budget.lastAlertState
            if (budget.lastAlertAt != null && budget.lastAlertAt < range.first) {
                budgetRepository.resetAlertState(budget.id)
                lastState = null
            }

            val status = budgetEngine.statusFor(budget, now)
            val pacing = BudgetPacer.calculatePacing(
                spent = status.spent,
                budgetAmount = budget.amount,
                period = budget.period,
                nowEpochMs = now
            )

            val daysRemaining = pacing.daysRemaining

            // Do NOT fire notifications on the last day of the month/period (user can't fix it then)
            if (daysRemaining <= 0) {
                continue
            }

            val isGlobal = (budget.category == BudgetCategories.GLOBAL)

            var alertState: AlertState? = null
            var alertMessage: String? = null

            // 1. BREACHED: actualPercent >= 100%
            if (pacing.actualPercent >= 100.0) {
                alertState = AlertState.EXCEEDED
                alertMessage = if (isGlobal) {
                    "You've exceeded your ${budget.period.name.lowercase()} budget"
                } else {
                    "${budget.category} budget exceeded"
                }
            }
            // 2. DANGER ZONE: actualPercent >= 85% AND daysRemaining >= 3
            else if (pacing.actualPercent >= 85.0 && daysRemaining >= 3) {
                if (!isGlobal || pacing.projectedOverage > 0.0) {
                    alertState = AlertState.WARNING
                    alertMessage = if (isGlobal) {
                        "You've used 80% of your ${budget.period.name.lowercase()} budget"
                    } else {
                        "${budget.category} budget is at 80%"
                    }
                }
            }
            // 3. OVERSPENDING PACE: actualPercent - expectedPercent >= 15% (spending 15% faster than period is passing)
            else if ((pacing.actualPercent - pacing.expectedPercent) >= 15.0 && daysRemaining >= 3 && pacing.projectedOverage > 0.0) {
                alertState = AlertState.WARNING
                val formattedOverage = formatOverage(pacing.projectedOverage)
                alertMessage = if (isGlobal) {
                    "At this rate, you'll exceed your ${budget.period.name.lowercase()} budget by ₹$formattedOverage"
                } else {
                    "At this rate, you'll exceed your ${budget.category} budget by ₹$formattedOverage"
                }
            }

            if (alertState != null && alertMessage != null) {
                val shouldAlert = when (alertState) {
                    AlertState.EXCEEDED -> lastState != AlertState.EXCEEDED
                    AlertState.WARNING -> lastState == null || lastState == AlertState.ON_TRACK
                    AlertState.ON_TRACK -> false
                }

                if (shouldAlert) {
                    budgetRepository.markAlerted(budget.id, alertState, now)
                    // Anti-spam contract (BudgetAlertSchedulerTest::testCategoryAt90AndGlobalAt85):
                    // category budgets are evaluated first (orderedBudgets); if any category alert
                    // already fired this cycle, the GLOBAL budget's state is marked silently and
                    // its notification is suppressed. When no category alert fired, global fires.
                    // This preserves the C4 no-loss fix above: markAlerted runs for every budget.
                    val suppressGlobalNotification = isGlobal && firedEvents.isNotEmpty()
                    if (!suppressGlobalNotification) {
                        val event = AlertEvent(
                            budgetId = budget.id,
                            category = budget.category,
                            state = alertState,
                            message = alertMessage
                        )
                        notifier?.sendAlert(event)
                        firedEvents.add(event)
                    }
                }
            }
        }

        return firedEvents
    }

    private fun formatOverage(amount: Double): String {
        return if (amount >= 1000) {
            val rounded = (amount / 100).roundToInt() * 100
            rounded.toString()
        } else {
            amount.roundToInt().toString()
        }
    }
}
