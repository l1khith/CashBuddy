package com.cashbuddy.presentation.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.action_cancel
import cashbuddy.shared.generated.resources.action_clear_search
import cashbuddy.shared.generated.resources.action_reset_all
import cashbuddy.shared.generated.resources.action_search
import cashbuddy.shared.generated.resources.date_range_apply
import cashbuddy.shared.generated.resources.date_range_from
import cashbuddy.shared.generated.resources.date_range_or_specific
import cashbuddy.shared.generated.resources.date_range_preset_30d
import cashbuddy.shared.generated.resources.date_range_preset_6m
import cashbuddy.shared.generated.resources.date_range_preset_7d
import cashbuddy.shared.generated.resources.date_range_preset_90d
import cashbuddy.shared.generated.resources.date_range_presets
import cashbuddy.shared.generated.resources.date_range_title
import cashbuddy.shared.generated.resources.date_range_to
import cashbuddy.shared.generated.resources.dialog_edit_tx_category_label
import cashbuddy.shared.generated.resources.dialog_edit_tx_save_changes
import cashbuddy.shared.generated.resources.dialog_edit_tx_title
import cashbuddy.shared.generated.resources.dialog_ok
import cashbuddy.shared.generated.resources.ic_calendar_today
import cashbuddy.shared.generated.resources.ic_filter_list
import cashbuddy.shared.generated.resources.ic_search
import cashbuddy.shared.generated.resources.tx_filter_all_categories
import cashbuddy.shared.generated.resources.tx_filter_amount_range
import cashbuddy.shared.generated.resources.tx_filter_apply
import cashbuddy.shared.generated.resources.tx_filter_max_amount
import cashbuddy.shared.generated.resources.tx_filter_min_amount
import cashbuddy.shared.generated.resources.tx_filter_title
import cashbuddy.shared.generated.resources.tx_general_category
import cashbuddy.shared.generated.resources.tx_list_empty_subtitle
import cashbuddy.shared.generated.resources.tx_list_empty_title
import cashbuddy.shared.generated.resources.tx_list_filter_all_types
import cashbuddy.shared.generated.resources.tx_list_filter_expenses
import cashbuddy.shared.generated.resources.tx_list_filter_income
import cashbuddy.shared.generated.resources.tx_list_search_placeholder
import cashbuddy.shared.generated.resources.tx_list_title
import cashbuddy.shared.generated.resources.tx_list_total_count
import cashbuddy.shared.generated.resources.tx_list_total_received
import cashbuddy.shared.generated.resources.tx_list_total_spent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashbuddy.domain.model.DateRangeHelper
import com.cashbuddy.domain.model.TimePeriod
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.platform.currentTimeMillis
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.components.PaisaPalFilterChip
import com.cashbuddy.presentation.components.TransactionCard
import com.cashbuddy.presentation.components.formatCurrency
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.RadiusMedium

import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import cashbuddy.shared.generated.resources.ic_filter_list
import com.cashbuddy.presentation.components.EditTransactionDialog

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

                    // Search Bar with Filter Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(Res.string.tx_list_search_placeholder), style = CashBuddyTypography.bodyMedium) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_search),
                                    contentDescription = stringResource(Res.string.action_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(Res.string.action_clear_search),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RadiusMedium
                        )

                        val hasActiveFilters = state.minAmount != null || state.maxAmount != null || state.selectedCategoryId != null
                        IconButton(
                            onClick = { showFilterSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RadiusMedium)
                                .background(
                                    if (hasActiveFilters) MaterialTheme.colorScheme.primaryContainer 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_filter_list),
                                contentDescription = stringResource(Res.string.tx_filter_title),
                                tint = if (hasActiveFilters) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Period Filter Chips (All Time, Today, This Week, This Month, This Year, Custom)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimePeriod.entries.forEach { period ->
                            val isSelected = state.selectedPeriod == period
                            PaisaPalFilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (period == TimePeriod.CUSTOM) {
                                        showCustomRangeDialog = true
                                    } else {
                                        viewModel.onPeriodSelected(period)
                                    }
                                },
                                label = period.displayName,
                                leadingIcon = if (period == TimePeriod.CUSTOM) {
                                    {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_calendar_today),
                                            contentDescription = stringResource(Res.string.date_range_title),
                                            modifier = Modifier.size(14.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null
                            )
                        }
                    }

                    // Type Filter Chips (All, Debits, Credits)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaisaPalFilterChip(
                            selected = state.selectedType == null,
                            onClick = { viewModel.onTypeSelected(null) },
                            label = stringResource(Res.string.tx_list_filter_all_types)
                        )
                        PaisaPalFilterChip(
                            selected = state.selectedType == TransactionType.DEBIT,
                            onClick = { viewModel.onTypeSelected(TransactionType.DEBIT) },
                            label = stringResource(Res.string.tx_list_filter_expenses)
                        )
                        PaisaPalFilterChip(
                            selected = state.selectedType == TransactionType.CREDIT,
                            onClick = { viewModel.onTypeSelected(TransactionType.CREDIT) },
                            label = stringResource(Res.string.tx_list_filter_income)
                        )
                    }

                    // Period & Filter Summary Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(Res.string.tx_list_total_count, state.totalCount),
                                style = CashBuddyTypography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (state.totalDebit > 0) {
                                    Text(
                                        text = stringResource(Res.string.tx_list_total_spent, formatCurrency(state.totalDebit)),
                                        style = CashBuddyTypography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DangerRed
                                    )
                                }
                                if (state.totalCredit > 0) {
                                    Text(
                                        text = stringResource(Res.string.tx_list_total_received, formatCurrency(state.totalCredit)),
                                        style = CashBuddyTypography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentEmerald
                                    )
                                }
                            }
                        }
                    }
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
                                onClick = { onNavigateToDetail(tx.id) },
                                onLongClick = { viewModel.onStartEditTransaction(tx) }
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

    // Edit Transaction Dialog Overlay (Feature 2)
    state.editingTransaction?.let { txToEdit ->
        EditTransactionDialog(
            initialAmount = txToEdit.amount,
            initialType = txToEdit.type,
            initialMerchant = txToEdit.merchant,
            initialCategoryId = txToEdit.categoryId,
            categories = state.categories,
            title = stringResource(Res.string.dialog_edit_tx_title),
            confirmButtonText = stringResource(Res.string.dialog_edit_tx_save_changes),
            onDismiss = { viewModel.onDismissEditTransaction() },
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

    // Advanced Filter ModalBottomSheet (Feature 3)
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState
        ) {
            var minAmtText by rememberSaveable { mutableStateOf(state.minAmount?.toString() ?: "") }
            var maxAmtText by rememberSaveable { mutableStateOf(state.maxAmount?.toString() ?: "") }

            val filterScope = rememberCoroutineScope()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.tx_filter_title),
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            viewModel.onAmountRangeSelected(null, null)
                            viewModel.onCategorySelected(null)
                            minAmtText = ""
                            maxAmtText = ""
                        }
                    ) {
                        Text(stringResource(Res.string.action_reset_all))
                    }
                }

                Text(
                    text = stringResource(Res.string.tx_filter_amount_range),
                    style = CashBuddyTypography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = minAmtText,
                        onValueChange = { minAmtText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(Res.string.tx_filter_min_amount)) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        shape = RadiusMedium
                    )
                    OutlinedTextField(
                        value = maxAmtText,
                        onValueChange = { maxAmtText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(Res.string.tx_filter_max_amount)) },
                        placeholder = { Text("No limit") },
                        singleLine = true,
                        shape = RadiusMedium
                    )
                }

                Text(
                    text = stringResource(Res.string.dialog_edit_tx_category_label),
                    style = CashBuddyTypography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.selectedCategoryId == null,
                        onClick = { viewModel.onCategorySelected(null) },
                        label = { Text(stringResource(Res.string.tx_filter_all_categories)) }
                    )
                    state.categories.forEach { cat ->
                        FilterChip(
                            selected = state.selectedCategoryId == cat.id,
                            onClick = {
                                viewModel.onCategorySelected(
                                    if (state.selectedCategoryId == cat.id) null else cat.id
                                )
                            },
                            label = { Text(cat.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val min = minAmtText.toDoubleOrNull()
                        val max = maxAmtText.toDoubleOrNull()
                        viewModel.onAmountRangeSelected(min, max)
                        filterScope.launch {
                            sheetState.hide()
                            showFilterSheet = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RadiusMedium
                ) {
                    Text(stringResource(Res.string.tx_filter_apply))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDateRangeDialog(
    onDismiss: () -> Unit,
    onRangeSelected: (Long, Long) -> Unit
) {
    val now = currentTimeMillis()
    val oneDayMs = 24L * 60 * 60 * 1000

    var startDateMillis by rememberSaveable { mutableStateOf(now - (30L * oneDayMs)) }
    var endDateMillis by rememberSaveable { mutableStateOf(now) }

    var isPickingStartDate by rememberSaveable { mutableStateOf(false) }
    var isPickingEndDate by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.date_range_title),
                style = CashBuddyTypography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick Presets
                Text(
                    text = stringResource(Res.string.date_range_presets),
                    style = CashBuddyTypography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRangeSelected(now - (7L * oneDayMs), now) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.date_range_preset_7d), fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onRangeSelected(now - (30L * oneDayMs), now) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.date_range_preset_30d), fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRangeSelected(now - (90L * oneDayMs), now) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.date_range_preset_90d), fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onRangeSelected(now - (180L * oneDayMs), now) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.date_range_preset_6m), fontSize = 12.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Specific Date Range Picker Section
                Text(
                    text = stringResource(Res.string.date_range_or_specific),
                    style = CashBuddyTypography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Start Date Selector
                    val fromDateLabel = stringResource(Res.string.date_range_from)
                    OutlinedCard(
                        onClick = { isPickingStartDate = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = fromDateLabel,
                                style = CashBuddyTypography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_calendar_today),
                                    contentDescription = fromDateLabel,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatEpochToDate(startDateMillis),
                                    style = CashBuddyTypography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // End Date Selector
                    val toDateLabel = stringResource(Res.string.date_range_to)
                    OutlinedCard(
                        onClick = { isPickingEndDate = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = toDateLabel,
                                style = CashBuddyTypography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_calendar_today),
                                    contentDescription = toDateLabel,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatEpochToDate(endDateMillis),
                                    style = CashBuddyTypography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        // Audit fix 1.2: Material3's DatePicker yields UTC-midnight millis; using
                        // them raw skewed the queried window by the local UTC offset (e.g. IST
                        // excluded the first 05:30 of the start day and included 05:30 of the day
                        // after the end day). Query bounds are now true LOCAL day bounds of the
                        // picked calendar days (DST-safe, derived from the calendar date).
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

    // Start Date Picker Modal
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
                            // Store LOCAL midnight of the picked day so the chip label
                            // (formatted in local time) matches the queried window.
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

    // End Date Picker Modal
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
