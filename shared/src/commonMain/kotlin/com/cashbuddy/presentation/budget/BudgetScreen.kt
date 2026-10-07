package com.cashbuddy.presentation.budget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cashbuddy.domain.model.BudgetStatus

@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel,
    onBudgetClick: (BudgetStatus) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BudgetListScreen(
        viewModel = viewModel,
        onBudgetClick = onBudgetClick,
        modifier = modifier
    )
}
