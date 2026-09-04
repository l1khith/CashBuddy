package com.cashbuddy.presentation.transactions

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.components.TransactionItemCard
import com.cashbuddy.presentation.theme.TrustBluePrimary

@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
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
                        text = "Transactions",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Search Bar
                item {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by merchant or text...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Type Filter Chips (All, Debits, Credits)
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.selectedType == null,
                            onClick = { viewModel.onTypeSelected(null) },
                            label = { Text("All Types") }
                        )
                        FilterChip(
                            selected = state.selectedType == TransactionType.DEBIT,
                            onClick = { viewModel.onTypeSelected(TransactionType.DEBIT) },
                            label = { Text("Debits (Expenses)") }
                        )
                        FilterChip(
                            selected = state.selectedType == TransactionType.CREDIT,
                            onClick = { viewModel.onTypeSelected(TransactionType.CREDIT) },
                            label = { Text("Credits (Income)") }
                        )
                    }
                }

                // Transactions List
                if (state.filteredTransactions.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = "🔍",
                            title = "No matching transactions",
                            subtitle = "Try adjusting your search query or filters."
                        )
                    }
                } else {
                    items(state.filteredTransactions, key = { it.id }) { tx ->
                        val catName = state.categories.find { it.id == tx.categoryId }?.name ?: "General"
                        TransactionItemCard(
                            transaction = tx,
                            categoryName = catName,
                            onClick = { onNavigateToDetail(tx.id) }
                        )
                    }
                }
            }
        }
    }
}
