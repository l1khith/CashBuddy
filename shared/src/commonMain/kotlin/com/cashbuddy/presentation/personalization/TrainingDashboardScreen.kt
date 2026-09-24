package com.cashbuddy.presentation.personalization

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * On-Device Training & Learning Dashboard Screen.
 * Provides transparent view of learned rules, accuracy stats, and export controls.
 */
@Composable
fun TrainingDashboardScreen(
    viewModel: PersonalizationViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDataView: () -> Unit,
    modifier: Modifier = Modifier
) {
    PersonalizationDashboardScreen(
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        onNavigateToDataView = onNavigateToDataView,
        modifier = modifier
    )
}
