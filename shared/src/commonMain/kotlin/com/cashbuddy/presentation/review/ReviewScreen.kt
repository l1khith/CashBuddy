package com.cashbuddy.presentation.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.presentation.components.ConfidenceIndicator
import com.cashbuddy.presentation.components.formatCurrency
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.getCategoryColor
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.presentation.components.ConfidenceBadge
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.theme.ExpenseCrimson
import com.cashbuddy.presentation.theme.IncomeEmerald
import com.cashbuddy.presentation.theme.TrustBluePrimary
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ReviewEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
        } else {
            val filteredTransactions = state.transactions.filter {
                it.confidence >= state.selectedConfidenceFilter
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Review Inbox",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(TrustBluePrimary.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${state.transactions.size} pending",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TrustBluePrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Verify categorized transactions from payment notifications",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Confidence Filter Chips
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.selectedConfidenceFilter == 0.0f,
                            onClick = { viewModel.processIntent(ReviewIntent.FilterByConfidence(0.0f)) },
                            label = { Text("All (${state.transactions.size})") }
                        )
                        FilterChip(
                            selected = state.selectedConfidenceFilter == 0.85f,
                            onClick = { viewModel.processIntent(ReviewIntent.FilterByConfidence(0.85f)) },
                            label = { Text("High (≥85%) • ${state.highConfidenceCount}") }
                        )
                        FilterChip(
                            selected = state.selectedConfidenceFilter == 0.50f,
                            onClick = { viewModel.processIntent(ReviewIntent.FilterByConfidence(0.50f)) },
                            label = { Text("Medium (≥50%)") }
                        )
                    }
                }

                // Batch Confirm All High-Confidence Button
                if (state.highConfidenceCount > 0 && state.selectedConfidenceFilter != 0.85f) {
                    item {
                        Button(
                            onClick = { viewModel.processIntent(ReviewIntent.ConfirmAllHighConfidence) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = IncomeEmerald),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Quick Confirm All (${state.highConfidenceCount})",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Empty State or Transactions List
                if (filteredTransactions.isEmpty()) {
                    item {
                        EmptyStateView(
                            imageVector = Icons.Default.CheckCircle,
                            title = "Inbox Zero",
                            subtitle = "All pending transactions have been reviewed and confirmed."
                        )
                    }
                } else {
                    items(filteredTransactions, key = { it.id }) { tx ->
                        PendingReviewCard(
                            transaction = tx,
                            allCategories = state.allCategories,
                            onConfirm = { viewModel.processIntent(ReviewIntent.ConfirmTransaction(tx.id)) },
                            onReject = { viewModel.processIntent(ReviewIntent.RejectTransaction(tx.id)) },
                            onCategoryChange = { newCatId ->
                                viewModel.processIntent(ReviewIntent.ModifyCategory(tx.id, newCatId))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingReviewCard(
    transaction: Transaction,
    allCategories: List<com.cashbuddy.domain.model.Category>,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onCategoryChange: (Long) -> Unit
) {
    val isDebit = transaction.type == TransactionType.DEBIT
    val prefix = if (isDebit) "-₹" else "+₹"
    val amountColor = if (isDebit) MaterialTheme.colorScheme.onSurface else AccentEmerald
    val categoryColor = getCategoryColor(transaction.categoryName)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RadiusLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Amount & Confidence Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$prefix${formatCurrency(transaction.amount)}",
                    style = CashBuddyTypography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                ConfidenceIndicator(confidence = transaction.confidence)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Merchant & Source
            Text(
                text = transaction.merchant.ifBlank { "Unknown Merchant" },
                style = CashBuddyTypography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Detected via ${transaction.sourceApp.substringAfterLast('.')}",
                style = CashBuddyTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Raw notification text preview
            if (transaction.rawText.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RadiusMedium)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = transaction.rawText,
                        style = CashBuddyTypography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Quick-Chip Selector
            Text(
                text = "Assigned Category:",
                style = CashBuddyTypography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allCategories.forEach { category ->
                    val isSelected = category.id == transaction.categoryId
                    val catColor = getCategoryColor(category.name)
                    Box(
                        modifier = Modifier
                            .clip(RadiusMedium)
                            .background(
                                if (isSelected) catColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .clickable { onCategoryChange(category.id) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = category.name,
                            style = CashBuddyTypography.labelMedium,
                            color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons (Confirm & Discard)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    shape = RadiusMedium
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = DangerRed
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Discard", style = CashBuddyTypography.labelLarge, color = DangerRed)
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    shape = RadiusMedium
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Confirm", style = CashBuddyTypography.labelLarge, color = Color.White)
                }
            }
        }
    }
}
