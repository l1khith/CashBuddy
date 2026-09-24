package com.cashbuddy.presentation.addtransaction

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Layer 3: Manual Entry Screen
 * Exposes AddTransactionScreen with Smart Pre-fill and quick-select chips.
 */
@Composable
fun ManualEntryScreen(
    viewModel: AddTransactionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    AddTransactionScreen(
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}
