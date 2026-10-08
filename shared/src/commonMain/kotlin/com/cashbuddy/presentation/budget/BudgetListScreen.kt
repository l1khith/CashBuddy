package com.cashbuddy.presentation.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetStatus
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.components.formatCurrency
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.TrustBluePrimary

@Composable
fun BudgetListScreen(
    viewModel: BudgetViewModel,
    onBudgetClick: (BudgetStatus) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showEditSheet by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }

    BudgetListContent(
        state = state,
        onBudgetClick = onBudgetClick,
        onBudgetLongClick = { budget ->
            editingBudget = budget
            showEditSheet = true
        },
        onAddBudgetClick = {
            editingBudget = null
            showEditSheet = true
        },
        modifier = modifier
    )

    if (showEditSheet) {
        BudgetEditSheet(
            initialBudget = editingBudget,
            categories = state.categories,
            existingBudgets = state.budgets,
            onDismiss = {
                showEditSheet = false
                editingBudget = null
            },
            onSave = { category, amount, period ->
                val current = editingBudget
                if (current != null) {
                    viewModel.updateBudget(
                        current.copy(
                            category = category,
                            amount = amount,
                            period = period
                        )
                    )
                } else {
                    viewModel.createBudget(category, amount, period)
                }
                showEditSheet = false
                editingBudget = null
            },
            onDelete = { id ->
                viewModel.deleteBudget(id)
                showEditSheet = false
                editingBudget = null
            }
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = CashBuddyTypography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(vertical = 4.dp)
            .semantics { heading() }
    )
}

@Composable
fun UnbudgetedCard(
    unbudgetedSpent: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RadiusLarge)
            .semantics {
                contentDescription = "Unbudgeted spending: ${formatCurrency(unbudgetedSpent, includeSymbol = true)}"
            },
        shape = RadiusLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Unbudgeted",
                    style = CashBuddyTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Spending outside tracked categories",
                    style = CashBuddyTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatCurrency(unbudgetedSpent, includeSymbol = true),
                style = CashBuddyTypography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun BudgetListContent(
    state: BudgetUiState,
    onBudgetClick: (BudgetStatus) -> Unit = {},
    onBudgetLongClick: (Budget) -> Unit = {},
    onAddBudgetClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddBudgetClick,
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
        } else if (state.budgets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    imageVector = Icons.Default.PieChart,
                    title = "No Budgets Yet",
                    subtitle = "Set spending limits for categories to stay in control of your monthly expenses.",
                    actionButtonText = "Set First Budget",
                    onActionClick = onAddBudgetClick
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Global Budget card at top
                state.globalBudget?.let { global ->
                    item(key = "global_${global.budget.id}") {
                        BudgetCard(
                            status = global,
                            isGlobal = true,
                            onClick = {},
                            onLongClick = { onBudgetLongClick(global.budget) }
                        )
                    }
                }

                // 2. Category Budgets section
                if (state.categoryBudgets.isNotEmpty()) {
                    if (state.globalBudget != null) {
                        item(key = "header_categories") {
                            SectionHeader(title = "Category Budgets")
                        }
                    }
                    items(state.categoryBudgets, key = { it.budget.id }) { item ->
                        BudgetCard(
                            status = item,
                            isGlobal = false,
                            onClick = { onBudgetClick(item) },
                            onLongClick = { onBudgetLongClick(item.budget) }
                        )
                    }
                }

                // 3. Unbudgeted Spending section (shown only when both global and category budgets exist)
                if (state.globalBudget != null && state.categoryBudgets.isNotEmpty()) {
                    item(key = "header_unbudgeted") {
                        SectionHeader(title = "Unbudgeted Spending")
                    }
                    item(key = "unbudgeted_card") {
                        UnbudgetedCard(unbudgetedSpent = state.unbudgetedSpent)
                    }
                }
            }
        }
    }
}
