package com.cashbuddy.presentation.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetStatus
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.theme.TrustBluePrimary

@Composable
fun BudgetListScreen(
    viewModel: BudgetViewModel,
    onBudgetClick: (BudgetStatus) -> Unit = {},
    onBudgetLongClick: (Budget) -> Unit = {},
    onAddBudgetClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BudgetListContent(
        state = state,
        onBudgetClick = onBudgetClick,
        onBudgetLongClick = onBudgetLongClick,
        onAddBudgetClick = onAddBudgetClick,
        modifier = modifier
    )
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
                items(state.budgets, key = { it.budget.id }) { item ->
                    BudgetCard(
                        status = item,
                        onClick = { onBudgetClick(item) },
                        onLongClick = { onBudgetLongClick(item.budget) }
                    )
                }
            }
        }
    }
}
