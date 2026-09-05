package com.cashbuddy.presentation.budget

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.presentation.components.formatCurrency
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.RadiusSmall
import com.cashbuddy.presentation.theme.WarningAmber
import com.cashbuddy.presentation.theme.getCategoryColor
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.theme.ConfidenceMedium
import com.cashbuddy.presentation.theme.ExpenseCrimson
import com.cashbuddy.presentation.theme.IncomeEmerald
import com.cashbuddy.presentation.theme.TrustBluePrimary

@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = TrustBluePrimary,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Budget"
                )
            }
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TrustBluePrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "Monthly Budgets",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (state.budgets.isEmpty()) {
                    item {
                        EmptyStateView(
                            imageVector = Icons.Default.PieChart,
                            title = "No budgets configured",
                            subtitle = "Set monthly spending limits for categories to stay on track.",
                            actionButtonText = "Set First Budget",
                            onActionClick = { showAddDialog = true }
                        )
                    }
                } else {
                    items(state.budgets, key = { it.id }) { b ->
                        BudgetProgressCard(
                            budget = b,
                            onDelete = { viewModel.processIntent(BudgetIntent.DeleteBudget(b.id)) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog && state.categories.isNotEmpty()) {
        AddBudgetDialog(
            categories = state.categories,
            onDismiss = { showAddDialog = false },
            onAdd = { catId, amount ->
                viewModel.processIntent(BudgetIntent.SetBudget(catId, amount, BudgetPeriod.MONTHLY))
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun BudgetProgressCard(
    budget: Budget,
    onDelete: () -> Unit
) {
    val fraction = if (budget.amount > 0) {
        (budget.spentAmount / budget.amount).toFloat().coerceIn(0f, 1f)
    } else 0f
    val percentUsed = (if (budget.amount > 0) (budget.spentAmount / budget.amount * 100) else 0.0).toInt()
    val categoryColor = getCategoryColor(budget.categoryName)

    val (barColor, statusIcon, statusText) = when {
        budget.spentAmount >= budget.amount -> Triple(DangerRed, Icons.Default.Warning, "Over budget!")
        percentUsed >= 80 -> Triple(WarningAmber, Icons.Default.Warning, "Approaching limit ($percentUsed% used)")
        else -> Triple(AccentEmerald, Icons.Default.CheckCircle, "On track ($percentUsed% used)")
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = budget.categoryName ?: "General",
                    style = CashBuddyTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor
                )
                Text(
                    text = "₹${formatCurrency(budget.spentAmount)} / ₹${formatCurrency(budget.amount)}",
                    style = CashBuddyTypography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RadiusSmall),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = barColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = statusText,
                    style = CashBuddyTypography.bodySmall,
                    color = barColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AddBudgetDialog(
    categories: List<com.cashbuddy.domain.model.Category>,
    onDismiss: () -> Unit,
    onAdd: (Long, Double) -> Unit
) {
    var selectedCategoryId by remember { mutableStateOf(categories.first().id) }
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Monthly Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select Category:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = cat.id == selectedCategoryId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) TrustBluePrimary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedCategoryId = cat.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat.name,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monthly Budget Limit (₹)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0) {
                        onAdd(selectedCategoryId, amount)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
