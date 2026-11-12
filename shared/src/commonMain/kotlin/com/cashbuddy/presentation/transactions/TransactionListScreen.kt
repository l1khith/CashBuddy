package com.cashbuddy.presentation.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.dialog_edit_tx_save_changes
import cashbuddy.shared.generated.resources.dialog_edit_tx_title
import cashbuddy.shared.generated.resources.ic_search
import cashbuddy.shared.generated.resources.tx_general_category
import cashbuddy.shared.generated.resources.tx_list_empty_subtitle
import cashbuddy.shared.generated.resources.tx_list_empty_title
import cashbuddy.shared.generated.resources.tx_list_title
import com.cashbuddy.domain.model.TimePeriod
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.presentation.components.EditTransactionDialog
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.components.TransactionCard
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.transactions.components.CustomDateRangeDialog
import com.cashbuddy.presentation.transactions.components.TransactionAdvancedFilterSheet
import com.cashbuddy.presentation.transactions.components.TransactionFiltersSection
import com.cashbuddy.presentation.transactions.components.TransactionSearchBar
import com.cashbuddy.presentation.transactions.components.TransactionSummaryBanner
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCustomRangeDialog by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !state.isLoadingMore && !state.isEndReached) {
            viewModel.loadNextPage()
        }
    }

    val onTxClick: (Long) -> Unit = remember(onNavigateToDetail) {
        { id -> onNavigateToDetail(id) }
    }
    val onTxLongClick: (Transaction) -> Unit = remember(viewModel) {
        { tx -> viewModel.onStartEditTransaction(tx) }
    }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Header & Search & Filters Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.tx_list_title),
                        style = CashBuddyTypography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    val hasActiveFilters = state.minAmount != null || state.maxAmount != null || state.selectedCategoryId != null
                    TransactionSearchBar(
                        query = state.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChanged,
                        hasActiveFilters = hasActiveFilters,
                        onFilterClick = { showFilterSheet = true }
                    )

                    TransactionFiltersSection(
                        selectedPeriod = state.selectedPeriod,
                        selectedType = state.selectedType,
                        onPeriodClick = { period ->
                            if (period == TimePeriod.CUSTOM) {
                                showCustomRangeDialog = true
                            } else {
                                viewModel.onPeriodSelected(period)
                            }
                        },
                        onTypeClick = viewModel::onTypeSelected
                    )

                    TransactionSummaryBanner(
                        totalCount = state.totalCount,
                        totalDebit = state.totalDebit,
                        totalCredit = state.totalCredit
                    )
                }

                // High-performance LazyColumn for Transactions
                if (state.filteredTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateView(
                            painter = painterResource(Res.drawable.ic_search),
                            title = stringResource(Res.string.tx_list_empty_title),
                            subtitle = stringResource(Res.string.tx_list_empty_subtitle)
                        )
                    }
                } else {
                    val generalCat = stringResource(Res.string.tx_general_category)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = state.filteredTransactions,
                            key = { it.id },
                            contentType = { it.type }
                        ) { tx ->
                            TransactionCard(
                                transaction = tx,
                                categoryName = tx.categoryName ?: generalCat,
                                onClick = { onTxClick(tx.id) },
                                onLongClick = { onTxLongClick(tx) }
                            )
                        }

                        if (state.isLoadingMore) {
                            item(key = "loading_more_indicator") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Transaction Dialog Overlay
    state.editingTransaction?.let { txToEdit ->
        EditTransactionDialog(
            initialAmount = txToEdit.amount,
            initialType = txToEdit.type,
            initialMerchant = txToEdit.merchant,
            initialCategoryId = txToEdit.categoryId,
            categories = state.categories,
            title = stringResource(Res.string.dialog_edit_tx_title),
            confirmButtonText = stringResource(Res.string.dialog_edit_tx_save_changes),
            onDismiss = viewModel::onDismissEditTransaction,
            onConfirm = { amount, type, merchant, categoryId ->
                viewModel.updateTransaction(
                    id = txToEdit.id,
                    amount = amount,
                    type = type,
                    merchant = merchant,
                    categoryId = categoryId
                )
            }
        )
    }

    // Advanced Filter ModalBottomSheet
    if (showFilterSheet) {
        TransactionAdvancedFilterSheet(
            sheetState = sheetState,
            initialMinAmount = state.minAmount,
            initialMaxAmount = state.maxAmount,
            selectedCategoryId = state.selectedCategoryId,
            categories = state.categories,
            onDismissRequest = { showFilterSheet = false },
            onApply = { min, max ->
                viewModel.onAmountRangeSelected(min, max)
                showFilterSheet = false
            },
            onCategorySelect = viewModel::onCategorySelected,
            onResetAll = {
                viewModel.onAmountRangeSelected(null, null)
                viewModel.onCategorySelected(null)
            }
        )
    }

    // Custom Date Range Dialog
    if (showCustomRangeDialog) {
        CustomDateRangeDialog(
            onDismiss = { showCustomRangeDialog = false },
            onRangeSelected = { start, end ->
                viewModel.onCustomDateRangeSelected(start, end)
                showCustomRangeDialog = false
            }
        )
    }
}
