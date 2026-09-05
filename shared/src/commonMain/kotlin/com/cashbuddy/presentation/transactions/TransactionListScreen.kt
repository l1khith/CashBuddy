package com.cashbuddy.presentation.transactions

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.components.TransactionItemCard
import com.cashbuddy.presentation.theme.TrustBluePrimary

import com.cashbuddy.presentation.components.TransactionCard
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium

@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel,
    onNavigateToDetail: (Long) -> Unit,
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Pinned Header, Search & Filters Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Transactions",
                        style = CashBuddyTypography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Search Bar
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search merchant, note, or raw text...", style = CashBuddyTypography.bodyMedium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        shape = RadiusMedium
                    )

                    // Type Filter Chips (All, Debits, Credits)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.selectedType == null,
                            onClick = { viewModel.onTypeSelected(null) },
                            label = { Text("All (${state.totalCount})", style = CashBuddyTypography.labelMedium) }
                        )
                        FilterChip(
                            selected = state.selectedType == TransactionType.DEBIT,
                            onClick = { viewModel.onTypeSelected(TransactionType.DEBIT) },
                            label = { Text("Debits (Expenses)", style = CashBuddyTypography.labelMedium) }
                        )
                        FilterChip(
                            selected = state.selectedType == TransactionType.CREDIT,
                            onClick = { viewModel.onTypeSelected(TransactionType.CREDIT) },
                            label = { Text("Credits (Income)", style = CashBuddyTypography.labelMedium) }
                        )
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
                            imageVector = Icons.Default.Search,
                            title = "No matching transactions",
                            subtitle = "Try adjusting your search query or filters."
                        )
                    }
                } else {
                    LazyColumn(
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
                                onClick = { onNavigateToDetail(tx.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
