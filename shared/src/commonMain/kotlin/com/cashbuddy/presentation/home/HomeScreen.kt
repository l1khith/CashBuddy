package com.cashbuddy.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.presentation.components.BalanceHeaderCard
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.components.TransactionItemCard
import com.cashbuddy.presentation.theme.ConfidenceMedium
import com.cashbuddy.presentation.theme.TealMintSecondary
import com.cashbuddy.presentation.theme.TrustBluePrimary
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToReview: (Long?) -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToAllTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is HomeUiEffect.NavigateToReview -> onNavigateToReview(effect.initialTransactionId)
                is HomeUiEffect.NavigateToTransactionDetail -> onNavigateToDetail(effect.transactionId)
                is HomeUiEffect.NavigateToAddTransaction -> onNavigateToAddTransaction()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top App Bar Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CashBuddy",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Personal Finance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Balance Header Card
                item {
                    BalanceHeaderCard(
                        totalBalance = state.balance,
                        monthlyDebit = state.monthlyDebit,
                        monthlyCredit = state.monthlyCredit
                    )
                }

                // Review Banner if pending transactions exist
                if (state.unreviewedCount > 0) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onReviewBannerClicked() },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = ConfidenceMedium.copy(alpha = 0.12f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = ConfidenceMedium,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "${state.unreviewedCount} Pending Review",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Tap to verify categorized transactions",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Review",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = ConfidenceMedium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = ConfidenceMedium,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Recent Activity Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Activity",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "See All",
                            style = MaterialTheme.typography.labelLarge,
                            color = TrustBluePrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { onNavigateToAllTransactions() }
                                .padding(4.dp)
                        )
                    }
                }

                // Recent Transactions
                if (state.recentTransactions.isEmpty()) {
                    item {
                        EmptyStateView(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = "No activity recorded yet",
                            subtitle = "Transactions from bank and UPI notifications will appear here automatically.",
                            actionButtonText = "Add First Transaction",
                            onActionClick = { viewModel.onAddTransactionClicked() }
                        )
                    }
                } else {
                    items(state.recentTransactions, key = { it.id }) { tx ->
                        TransactionItemCard(
                            transaction = tx,
                            onClick = { viewModel.onTransactionClicked(tx.id) }
                        )
                    }
                }
            }
        }
    }
}
