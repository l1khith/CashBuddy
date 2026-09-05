package com.cashbuddy.presentation.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.theme.ExpenseCrimson
import com.cashbuddy.presentation.theme.IncomeEmerald
import com.cashbuddy.presentation.theme.TrustBluePrimary

import com.cashbuddy.presentation.components.CategoryPieChart
import com.cashbuddy.presentation.components.formatCurrency
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.RadiusSmall
import com.cashbuddy.presentation.theme.getCategoryColor

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                item {
                    Text(
                        text = "Analytics & Insights",
                        style = CashBuddyTypography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
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
                                CategoryPieChart(breakdowns = state.categoryBreakdowns)
                            }
                        }
                    }
                }

                // Monthly In/Out Summary Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RadiusLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "Current Month Summary",
                                style = CashBuddyTypography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Total Spent",
                                        style = CashBuddyTypography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "₹${formatCurrency(state.totalExpense)}",
                                        style = CashBuddyTypography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseCrimson
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Total Received",
                                        style = CashBuddyTypography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "₹${formatCurrency(state.totalIncome)}",
                                        style = CashBuddyTypography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = IncomeEmerald
                                    )
                                }
                            }
                        }
                    }
                }

                // Category Spending Header
                item {
                    Text(
                        text = "Spending by Category",
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (state.categoryBreakdowns.isEmpty()) {
                    item {
                        EmptyStateView(
                            imageVector = Icons.Default.BarChart,
                            title = "No category data yet",
                            subtitle = "Confirmed transactions will generate spending analytics automatically."
                        )
                    }
                } else {
                    val totalSpending = state.categoryBreakdowns.sumOf { it.totalAmount }.coerceAtLeast(1.0)
                    items(state.categoryBreakdowns, key = { it.categoryName }) { cat ->
                        CategorySpendingRow(cat = cat, totalSpending = totalSpending)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySpendingRow(cat: CategoryBreakdown, totalSpending: Double) {
    val fraction = (cat.totalAmount / totalSpending).toFloat().coerceIn(0f, 1f)
    val percentage = (fraction * 100).toInt()
    val categoryColor = getCategoryColor(cat.categoryName)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RadiusMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                    text = cat.categoryName,
                    style = CashBuddyTypography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
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
            Text(
                text = "${cat.transactionCount} transactions • avg ₹${formatCurrency(cat.averageAmount)}",
                style = CashBuddyTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
