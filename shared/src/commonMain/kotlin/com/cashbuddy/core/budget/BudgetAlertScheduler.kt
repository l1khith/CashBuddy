// NO-NETWORK
package com.cashbuddy.core.budget

import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.repository.BudgetRepository
import com.cashbuddy.platform.currentTimeMillis

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

        for (budget in activeBudgets) {
            val range = budgetEngine.periodRange(budget.period, now)

            // Reset state if period has rolled over since last alert
            var lastState = budget.lastAlertState
            if (budget.lastAlertAt != null && budget.lastAlertAt < range.first) {
                budgetRepository.resetAlertState(budget.id)
                lastState = null
            }

            val status = budgetEngine.statusFor(budget, now)

            when (status.state) {
                AlertState.WARNING -> {
                    // Fire 80% warning only if not already alerted for WARNING or EXCEEDED in this period
                    if (lastState == null || lastState == AlertState.ON_TRACK) {
                        val event = AlertEvent(
                            budgetId = budget.id,
                            category = budget.category,
                            state = AlertState.WARNING,
                            message = "${budget.category} budget is at 80%"
                        )
                        budgetRepository.markAlerted(budget.id, AlertState.WARNING, now)
                        notifier?.sendAlert(event)
                        firedEvents.add(event)
                    }
                }
                AlertState.EXCEEDED -> {
                    // Fire exceeded alert only if not already alerted for EXCEEDED in this period
                    if (lastState != AlertState.EXCEEDED) {
                        val event = AlertEvent(
                            budgetId = budget.id,
                            category = budget.category,
                            state = AlertState.EXCEEDED,
                            message = "${budget.category} budget exceeded"
                        )
                        budgetRepository.markAlerted(budget.id, AlertState.EXCEEDED, now)
                        notifier?.sendAlert(event)
                        firedEvents.add(event)
                    }
                }
                AlertState.ON_TRACK -> {
                    // No alert needed while spending remains on track
                }
            }
        }

        return firedEvents
    }
}
