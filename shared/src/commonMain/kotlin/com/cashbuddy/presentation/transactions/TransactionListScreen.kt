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
import cashbuddy.shared.generated.resources.ic_calendar_today
import cashbuddy.shared.generated.resources.ic_search
import org.jetbrains.compose.resources.painterResource
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var showCustomRangeDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
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
                        text = "Transactions",
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
                            placeholder = { Text("Search merchant, note, or raw text...", style = CashBuddyTypography.bodyMedium) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_search),
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
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
                                contentDescription = "Advanced Filters",
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
                                            contentDescription = "Custom Date Range",
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
                            label = "All Types"
                        )
                        PaisaPalFilterChip(
                            selected = state.selectedType == TransactionType.DEBIT,
                            onClick = { viewModel.onTypeSelected(TransactionType.DEBIT) },
                            label = "Expenses (Debits)"
                        )
                        PaisaPalFilterChip(
                            selected = state.selectedType == TransactionType.CREDIT,
                            onClick = { viewModel.onTypeSelected(TransactionType.CREDIT) },
                            label = "Income (Credits)"
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
                                text = "${state.totalCount} transactions",
                                style = CashBuddyTypography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (state.totalDebit > 0) {
                                    Text(
                                        text = "Spent: ₹${formatCurrency(state.totalDebit)}",
                                        style = CashBuddyTypography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DangerRed
                                    )
                                }
                                if (state.totalCredit > 0) {
                                    Text(
                                        text = "Income: ₹${formatCurrency(state.totalCredit)}",
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
                            title = "No matching transactions",
                            subtitle = "Try adjusting your search query, type filter, or selected time period."
                        )
                    }
                } else {
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
                                categoryName = tx.categoryName ?: "General",
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
            title = "Edit Transaction",
            confirmButtonText = "Save Changes",
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
            var minAmtText by remember { mutableStateOf(state.minAmount?.toString() ?: "") }
            var maxAmtText by remember { mutableStateOf(state.maxAmount?.toString() ?: "") }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter Transactions",
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
                        Text("Reset All")
                    }
                }

                Text(
                    text = "Amount Range (₹)",
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
                        label = { Text("Min Amount") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        shape = RadiusMedium
                    )
                    OutlinedTextField(
                        value = maxAmtText,
                        onValueChange = { maxAmtText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Max Amount") },
                        placeholder = { Text("No limit") },
                        singleLine = true,
                        shape = RadiusMedium
                    )
                }

                Text(
                    text = "Category",
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
                        label = { Text("All Categories") }
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
                        showFilterSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RadiusMedium
                ) {
                    Text("Apply Filters")
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

    var startDateMillis by remember { mutableStateOf(now - (30L * oneDayMs)) }
    var endDateMillis by remember { mutableStateOf(now) }

    var isPickingStartDate by remember { mutableStateOf(false) }
    var isPickingEndDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Custom Date Range",
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
                    text = "Quick Presets",
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
                        Text("Last 7 Days", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onRangeSelected(now - (30L * oneDayMs), now) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Last 30 Days", fontSize = 12.sp)
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
                        Text("Last 90 Days", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onRangeSelected(now - (180L * oneDayMs), now) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Last 6 Months", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Specific Date Range Picker Section
                Text(
                    text = "Or Specific Dates",
                    style = CashBuddyTypography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Start Date Selector
                    OutlinedCard(
                        onClick = { isPickingStartDate = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "From Date",
                                style = CashBuddyTypography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_calendar_today),
                                    contentDescription = "From Date",
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
                    OutlinedCard(
                        onClick = { isPickingEndDate = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "To Date",
                                style = CashBuddyTypography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_calendar_today),
                                    contentDescription = "To Date",
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
                        val start = minOf(startDateMillis, endDateMillis)
                        val end = maxOf(startDateMillis, endDateMillis) + (oneDayMs - 1)
                        onRangeSelected(start, end)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Apply Range")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Start Date Picker Modal
    if (isPickingStartDate) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { isPickingStartDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            startDateMillis = it
                        }
                        isPickingStartDate = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { isPickingStartDate = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // End Date Picker Modal
    if (isPickingEndDate) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { isPickingEndDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            endDateMillis = it
                        }
                        isPickingEndDate = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { isPickingEndDate = false }) {
                    Text("Cancel")
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
