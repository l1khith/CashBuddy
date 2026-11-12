package com.cashbuddy.presentation.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.action_cancel
import cashbuddy.shared.generated.resources.date_range_apply
import cashbuddy.shared.generated.resources.date_range_from
import cashbuddy.shared.generated.resources.date_range_title
import cashbuddy.shared.generated.resources.date_range_to
import cashbuddy.shared.generated.resources.dialog_ok
import cashbuddy.shared.generated.resources.ic_calendar_today
import cashbuddy.shared.generated.resources.ic_monitoring
import cashbuddy.shared.generated.resources.stats_budget_exceeded
import cashbuddy.shared.generated.resources.stats_budget_progress
import cashbuddy.shared.generated.resources.stats_budget_remaining
import cashbuddy.shared.generated.resources.stats_budget_status
import cashbuddy.shared.generated.resources.stats_cmp_down
import cashbuddy.shared.generated.resources.stats_cmp_flat
import cashbuddy.shared.generated.resources.stats_cmp_up
import cashbuddy.shared.generated.resources.stats_empty_subtitle
import cashbuddy.shared.generated.resources.stats_empty_title
import cashbuddy.shared.generated.resources.stats_highest_spend_day
import cashbuddy.shared.generated.resources.stats_insights_title
import cashbuddy.shared.generated.resources.stats_lent_out
import cashbuddy.shared.generated.resources.stats_no_budget_set
import cashbuddy.shared.generated.resources.stats_no_insights
import cashbuddy.shared.generated.resources.stats_outstanding_cards
import cashbuddy.shared.generated.resources.stats_period_last_month
import cashbuddy.shared.generated.resources.stats_period_last_week
import cashbuddy.shared.generated.resources.stats_period_last_year
import cashbuddy.shared.generated.resources.stats_period_prev_period
import cashbuddy.shared.generated.resources.stats_see_more
import cashbuddy.shared.generated.resources.stats_set_budget
import cashbuddy.shared.generated.resources.stats_spending_by_category
import cashbuddy.shared.generated.resources.stats_title
import cashbuddy.shared.generated.resources.stats_top_merchant
import cashbuddy.shared.generated.resources.stats_total_income
import cashbuddy.shared.generated.resources.stats_total_spent
import cashbuddy.shared.generated.resources.stats_tx_count_avg
import com.cashbuddy.domain.model.DateRangeHelper
import com.cashbuddy.domain.model.TimePeriod
import com.cashbuddy.presentation.components.CategoryPieChart
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.components.formatCurrency
import com.cashbuddy.presentation.stats.components.TransactionDetailSheet
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.ExpenseCrimson
import com.cashbuddy.presentation.theme.IncomeEmerald
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.RadiusSmall
import com.cashbuddy.presentation.theme.WarningAmber
import com.cashbuddy.presentation.theme.getCategoryColor
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onNavigateToHistory: (categoryId: Long?, type: String?, period: String?, startMillis: Long?, endMillis: Long?) -> Unit,
    onNavigateToBudgets: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCustomDateDialog by remember { mutableStateOf(false) }

    StatsContent(
        state = state,
        onPeriodSelected = { period ->
            if (period == TimePeriod.CUSTOM) {
                showCustomDateDialog = true
            } else {
                viewModel.onPeriodSelected(period)
            }
        },
        onSummarySpentClick = { viewModel.openSheet(StatsSheetType.TotalSpent) },
        onSummaryIncomeClick = { viewModel.openSheet(StatsSheetType.TotalIncome) },
        onCategoryClick = { cat ->
            viewModel.openSheet(StatsSheetType.Category(cat.breakdown.categoryName, cat.categoryId))
        },
        onSetBudgetClick = onNavigateToBudgets,
        modifier = modifier
    )

    state.activeSheet?.let { sheetState ->
        val accentColor = when (sheetState.type) {
            StatsSheetType.TotalIncome -> IncomeEmerald
            StatsSheetType.TotalSpent -> ExpenseCrimson
            is StatsSheetType.Category -> getCategoryColor(sheetState.title)
        }

        TransactionDetailSheet(
            title = sheetState.title,
            totalAmount = sheetState.totalAmount,
            transactions = sheetState.transactions,
            onDismiss = { viewModel.closeSheet() },
            onViewInHistory = {
                viewModel.closeSheet()
                onNavigateToHistory(
                    sheetState.categoryId,
                    sheetState.txTypeFilter?.name,
                    state.selectedPeriod.name,
                    state.startTimestamp,
                    state.endTimestamp
                )
            },
            accentColor = accentColor
        )
    }

    if (showCustomDateDialog) {
        val initialStart = state.customDateRange?.startTimestamp ?: (state.startTimestamp.takeIf { it > 0 } ?: com.cashbuddy.platform.currentTimeMillis())
        val initialEnd = state.customDateRange?.endTimestamp ?: (state.endTimestamp.takeIf { it > 0 } ?: com.cashbuddy.platform.currentTimeMillis())
        StatsCustomDateRangeDialog(
            initialStartMillis = initialStart,
            initialEndMillis = initialEnd,
            onDismiss = { showCustomDateDialog = false },
            onRangeSelected = { start, end ->
                viewModel.onCustomDateRangeSelected(start, end)
                showCustomDateDialog = false
            }
        )
    }
}

@Composable
fun StatsContent(
    state: StatsUiState,
    onPeriodSelected: (TimePeriod) -> Unit,
    onSummarySpentClick: () -> Unit,
    onSummaryIncomeClick: () -> Unit,
    onCategoryClick: (CategoryStatsItem) -> Unit,
    onSetBudgetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Screen Title
                item {
                    Text(
                        text = stringResource(Res.string.stats_title),
                        style = CashBuddyTypography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // PART 1.1: Time Filter Bar (Horizontal scrollable FilterChips + date range label)
                item {
                    TimeFilterSection(
                        selectedPeriod = state.selectedPeriod,
                        dateRangeLabel = state.dateRangeLabel,
                        onPeriodSelected = onPeriodSelected
                    )
                }

                // Interactive Donut Chart Hero
                if (state.categoryBreakdowns.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RadiusLarge,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CategoryPieChart(breakdowns = state.categoryBreakdowns.map { it.breakdown })
                            }
                        }
                    }
                }

                // PART 1.2: Summary Cards (Total Spent, Total Income, Budget Status)
                item {
                    SummaryCardsSection(
                        state = state,
                        onSummarySpentClick = onSummarySpentClick,
                        onSummaryIncomeClick = onSummaryIncomeClick,
                        onSetBudgetClick = onSetBudgetClick
                    )
                }

                // PART 1.3: Category Spending Header
                item {
                    Text(
                        text = stringResource(Res.string.stats_spending_by_category),
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (state.categoryBreakdowns.isEmpty()) {
                    item {
                        EmptyStateView(
                            painter = painterResource(Res.drawable.ic_monitoring),
                            title = stringResource(Res.string.stats_empty_title),
                            subtitle = stringResource(Res.string.stats_empty_subtitle)
                        )
                    }
                } else {
                    items(state.categoryBreakdowns, key = { it.breakdown.categoryName }) { catItem ->
                        CategorySpendingRow(
                            catItem = catItem,
                            selectedPeriod = state.selectedPeriod,
                            onClick = { onCategoryClick(catItem) }
                        )
                    }
                }

                // PART 1.4: Insights Section
                item {
                    InsightsSection(state = state)
                }
            }
        }
    }
}

@Composable
private fun TimeFilterSection(
    selectedPeriod: TimePeriod,
    dateRangeLabel: String,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    val periods = listOf(
        TimePeriod.THIS_WEEK,
        TimePeriod.THIS_MONTH,
        TimePeriod.THIS_YEAR,
        TimePeriod.CUSTOM
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            periods.forEach { period ->
                val isSelected = selectedPeriod == period
                FilterChip(
                    selected = isSelected,
                    onClick = { onPeriodSelected(period) },
                    label = {
                        Text(
                            text = period.displayName,
                            style = CashBuddyTypography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        if (dateRangeLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateRangeLabel,
                style = CashBuddyTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun SummaryCardsSection(
    state: StatsUiState,
    onSummarySpentClick: () -> Unit,
    onSummaryIncomeClick: () -> Unit,
    onSetBudgetClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Spent & Income Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Total Spent
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSummarySpentClick),
                shape = RadiusLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.stats_total_spent),
                        style = CashBuddyTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹${formatCurrency(state.totalSpent)}",
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseCrimson
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(Res.string.stats_see_more),
                        style = CashBuddyTypography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Card 2: Total Income
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSummaryIncomeClick),
                shape = RadiusLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.stats_total_income),
                        style = CashBuddyTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹${formatCurrency(state.totalIncome)}",
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = IncomeEmerald
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(Res.string.stats_see_more),
                        style = CashBuddyTypography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Card 3: Budget Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RadiusLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.stats_budget_status),
                        style = CashBuddyTypography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    state.budgetStatus?.let { b ->
                        Text(
                            text = "${b.percentageInt}%",
                            style = CashBuddyTypography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (b.isExceeded) ExpenseCrimson else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val budgetStatus = state.budgetStatus
                if (budgetStatus == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.stats_no_budget_set),
                            style = CashBuddyTypography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onSetBudgetClick,
                            shape = RadiusMedium
                        ) {
                            Text(
                                text = stringResource(Res.string.stats_set_budget),
                                style = CashBuddyTypography.labelMedium
                            )
                        }
                    }
                } else {
                    LinearProgressIndicator(
                        progress = { budgetStatus.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RadiusSmall),
                        color = if (budgetStatus.isExceeded) ExpenseCrimson else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(
                                Res.string.stats_budget_progress,
                                "₹${formatCurrency(budgetStatus.spent)}",
                                "₹${formatCurrency(budgetStatus.budget.amount)}",
                                budgetStatus.percentageInt
                            ),
                            style = CashBuddyTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val remainingText = if (budgetStatus.isExceeded) {
                            stringResource(Res.string.stats_budget_exceeded, "₹${formatCurrency(budgetStatus.spent - budgetStatus.budget.amount)}")
                        } else {
                            stringResource(Res.string.stats_budget_remaining, "₹${formatCurrency(budgetStatus.remaining)}")
                        }
                        Text(
                            text = remainingText,
                            style = CashBuddyTypography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (budgetStatus.isExceeded) ExpenseCrimson else IncomeEmerald
                        )
                    }
                }
            }
        }

        // Outstanding cards & lent out info if applicable
        if (state.cardOutstanding > 0.0 || state.lentOut > 0.0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RadiusMedium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.cardOutstanding > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(Res.string.stats_outstanding_cards),
                                style = CashBuddyTypography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${formatCurrency(state.cardOutstanding)}",
                                style = CashBuddyTypography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (state.lentOut > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(Res.string.stats_lent_out),
                                style = CashBuddyTypography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${formatCurrency(state.lentOut)}",
                                style = CashBuddyTypography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySpendingRow(
    catItem: CategoryStatsItem,
    selectedPeriod: TimePeriod,
    onClick: () -> Unit
) {
    val cat = catItem.breakdown
    val fraction = catItem.progressFraction
    val percentage = catItem.percentageInt
    val categoryColor = getCategoryColor(cat.categoryName)

    val periodLabel = when (selectedPeriod) {
        TimePeriod.THIS_WEEK -> stringResource(Res.string.stats_period_last_week)
        TimePeriod.THIS_MONTH -> stringResource(Res.string.stats_period_last_month)
        TimePeriod.THIS_YEAR -> stringResource(Res.string.stats_period_last_year)
        else -> stringResource(Res.string.stats_period_prev_period)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RadiusMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = cat.categoryName,
                        style = CashBuddyTypography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    // Comparison Badge
                    catItem.comparisonPercent?.let { cmp ->
                        ComparisonBadge(percent = cmp, periodLabel = periodLabel)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "₹${formatCurrency(cat.totalAmount)} ($percentage%)",
                    style = CashBuddyTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RadiusSmall),
                color = categoryColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.stats_tx_count_avg, cat.transactionCount, formatCurrency(cat.averageAmount)),
                    style = CashBuddyTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(Res.string.stats_see_more),
                    style = CashBuddyTypography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ComparisonBadge(
    percent: Int,
    periodLabel: String
) {
    val (text, color, bgColor) = when {
        percent > 0 -> Triple(
            stringResource(Res.string.stats_cmp_up, percent, periodLabel),
            ExpenseCrimson,
            ExpenseCrimson.copy(alpha = 0.12f)
        )
        percent < 0 -> Triple(
            stringResource(Res.string.stats_cmp_down, -percent, periodLabel),
            IncomeEmerald,
            IncomeEmerald.copy(alpha = 0.12f)
        )
        else -> Triple(
            stringResource(Res.string.stats_cmp_flat, periodLabel),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = CashBuddyTypography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun InsightsSection(state: StatsUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.stats_insights_title),
            style = CashBuddyTypography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        val topMerchant = state.topMerchant
        val highestDay = state.highestSpendDay

        if (topMerchant == null && highestDay == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RadiusMedium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = stringResource(Res.string.stats_no_insights),
                    style = CashBuddyTypography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RadiusMedium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    topMerchant?.let { tm ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    Res.string.stats_top_merchant,
                                    tm.merchant,
                                    "₹${formatCurrency(tm.totalAmount)}"
                                ),
                                style = CashBuddyTypography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    highestDay?.let { hd ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    Res.string.stats_highest_spend_day,
                                    hd.dateLabel,
                                    "₹${formatCurrency(hd.totalAmount)}"
                                ),
                                style = CashBuddyTypography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsCustomDateRangeDialog(
    initialStartMillis: Long,
    initialEndMillis: Long,
    onDismiss: () -> Unit,
    onRangeSelected: (Long, Long) -> Unit
) {
    var startDateMillis by remember { mutableStateOf(initialStartMillis) }
    var endDateMillis by remember { mutableStateOf(initialEndMillis) }
    var isPickingStartDate by remember { mutableStateOf(false) }
    var isPickingEndDate by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.date_range_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { isPickingStartDate = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = stringResource(Res.string.date_range_from),
                                style = CashBuddyTypography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatEpochToDate(startDateMillis),
                                style = CashBuddyTypography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { isPickingEndDate = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = stringResource(Res.string.date_range_to),
                                style = CashBuddyTypography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatEpochToDate(endDateMillis),
                                style = CashBuddyTypography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val startRange = DateRangeHelper.localDayBounds(minOf(startDateMillis, endDateMillis))
                        val endRange = DateRangeHelper.localDayBounds(maxOf(startDateMillis, endDateMillis))
                        onRangeSelected(startRange.startTimestamp, endRange.endTimestamp)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(Res.string.date_range_apply))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )

    if (isPickingStartDate) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateRangeHelper.localMillisToUtcPickerMillis(startDateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { isPickingStartDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            startDateMillis = DateRangeHelper.utcPickerMillisToLocalStartOfDay(it)
                        }
                        isPickingStartDate = false
                    }
                ) {
                    Text(stringResource(Res.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { isPickingStartDate = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (isPickingEndDate) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateRangeHelper.localMillisToUtcPickerMillis(endDateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { isPickingEndDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            endDateMillis = DateRangeHelper.utcPickerMillisToLocalStartOfDay(it)
                        }
                        isPickingEndDate = false
                    }
                ) {
                    Text(stringResource(Res.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { isPickingEndDate = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatEpochToDate(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis)
    val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val monthName = localDate.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "${localDate.dayOfMonth} $monthName ${localDate.year}"
}
