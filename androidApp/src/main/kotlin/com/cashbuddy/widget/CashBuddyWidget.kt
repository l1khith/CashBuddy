package com.cashbuddy.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.cashbuddy.MainActivity
import com.cashbuddy.core.budget.BudgetEngine
import com.cashbuddy.domain.model.BudgetCategories
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.BudgetRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar
import kotlin.math.roundToLong

class CashBuddyWidget : GlanceAppWidget(), KoinComponent {

    private val transactionRepository: TransactionRepository by inject()
    private val budgetRepository: BudgetRepository by inject()
    private val budgetEngine: BudgetEngine by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = System.currentTimeMillis()

        // 1. Total spent today
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val txList = transactionRepository.getByDateRange(startOfToday, now + 1000L).firstOrNull() ?: emptyList()
        val todaySpent = txList.filter { it.type == TransactionType.DEBIT && it.amount > 0 }.sumOf { it.amount }

        // 2. Global budget remaining
        val activeBudgets = budgetRepository.getActive()
        val globalBudget = activeBudgets.find { it.category == BudgetCategories.GLOBAL }
        val budgetStatus = if (globalBudget != null) {
            val status = budgetEngine.statusFor(globalBudget, now)
            val remaining = globalBudget.amount - status.spent
            if (remaining >= 0) {
                "Budget: ₹${formatAmount(remaining)} left"
            } else {
                "Budget: ₹${formatAmount(-remaining)} over"
            }
        } else {
            val totalBudget = activeBudgets.sumOf { it.amount }
            if (totalBudget > 0) {
                val totalSpent = activeBudgets.sumOf { budgetEngine.statusFor(it, now).spent }
                val remaining = totalBudget - totalSpent
                if (remaining >= 0) {
                    "Budget: ₹${formatAmount(remaining)} left"
                } else {
                    "Budget: ₹${formatAmount(-remaining)} over"
                }
            } else {
                "Budget: Not set"
            }
        }

        val navigateKey = androidx.glance.action.ActionParameters.Key<String>("EXTRA_NAVIGATE_TO")
        val openAppAction = actionStartActivity<MainActivity>()
        val addExpenseAction = actionStartActivity<MainActivity>(
            androidx.glance.action.actionParametersOf(navigateKey to "add")
        )

        provideContent {
            GlanceTheme {
                WidgetUi(
                    todaySpent = formatAmount(todaySpent),
                    budgetStatus = budgetStatus,
                    onOpenApp = openAppAction,
                    onAddExpense = addExpenseAction
                )
            }
        }
    }

    @Composable
    private fun WidgetUi(
        todaySpent: String,
        budgetStatus: String,
        onOpenApp: androidx.glance.action.Action,
        onAddExpense: androidx.glance.action.Action
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(14.dp)
                .clickable(onOpenApp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.Start
        ) {
            // Top: Total Spent Today
            Text(
                text = "Today: ₹$todaySpent",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Middle: Global Budget Remaining
            Text(
                text = budgetStatus,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Bottom: + Button deep linking to Add Transaction
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.End,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier
                        .clickable(onAddExpense)
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(8.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = "+ Add",
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount == amount.roundToLong().toDouble()) {
            amount.roundToLong().toString()
        } else {
            String.format("%.2f", amount)
        }
    }
}
