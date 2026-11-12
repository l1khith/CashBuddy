package com.cashbuddy.presentation.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.action_cancel
import cashbuddy.shared.generated.resources.action_delete
import cashbuddy.shared.generated.resources.action_edit
import cashbuddy.shared.generated.resources.cd_back
import cashbuddy.shared.generated.resources.dialog_delete_tx_message
import cashbuddy.shared.generated.resources.dialog_delete_tx_title
import cashbuddy.shared.generated.resources.dialog_edit_tx_save_changes
import cashbuddy.shared.generated.resources.dialog_edit_tx_title
import cashbuddy.shared.generated.resources.ic_delete_forever
import cashbuddy.shared.generated.resources.ic_edit
import cashbuddy.shared.generated.resources.tx_detail_change_category
import cashbuddy.shared.generated.resources.tx_detail_credit_label
import cashbuddy.shared.generated.resources.tx_detail_debit_label
import cashbuddy.shared.generated.resources.tx_detail_delete_button
import cashbuddy.shared.generated.resources.tx_detail_merchant_label
import cashbuddy.shared.generated.resources.tx_detail_original_notification
import cashbuddy.shared.generated.resources.tx_detail_source_label
import cashbuddy.shared.generated.resources.tx_detail_status_label
import cashbuddy.shared.generated.resources.tx_detail_title
import cashbuddy.shared.generated.resources.tx_unknown_merchant
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.presentation.theme.ExpenseCrimson
import com.cashbuddy.presentation.theme.IncomeEmerald
import com.cashbuddy.presentation.theme.TrustBluePrimary
import kotlinx.coroutines.flow.collectLatest

import com.cashbuddy.presentation.components.ConfidenceIndicator
import com.cashbuddy.presentation.components.EditTransactionDialog
import com.cashbuddy.presentation.components.StatusBadge
import com.cashbuddy.presentation.components.formatCurrency
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.getCategoryColor

@Composable
fun TransactionDetailScreen(
    viewModel: TransactionDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navBackEffect.collectLatest {
            onNavigateBack()
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        if (state.isLoading || state.transaction == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val tx = state.transaction!!
            val isDebit = tx.type == TransactionType.DEBIT
            val sign = if (isDebit) "-" else "+"
            val amountColor = if (isDebit) MaterialTheme.colorScheme.onSurface else AccentEmerald
            val formattedAmount = formatCurrency(tx.amount, tx.currency, includeSymbol = true)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Back Button & Title & Edit Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = stringResource(Res.string.tx_detail_title),
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_edit),
                            contentDescription = stringResource(Res.string.action_edit),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Amount Header Card
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
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$sign$formattedAmount",
                            style = CashBuddyTypography.displayLarge.copy(fontSize = 38.sp),
                            fontWeight = FontWeight.Bold,
                            color = amountColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isDebit) stringResource(Res.string.tx_detail_debit_label) else stringResource(Res.string.tx_detail_credit_label),
                            style = CashBuddyTypography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(status = tx.status)
                            ConfidenceIndicator(confidence = tx.confidence)
                        }
                    }
                }

                // Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RadiusLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val unknownMerchant = stringResource(Res.string.tx_unknown_merchant)
                        DetailRow(label = stringResource(Res.string.tx_detail_merchant_label), value = tx.merchant.ifBlank { unknownMerchant })
                        DetailRow(label = stringResource(Res.string.tx_detail_status_label), value = tx.status.name)
                        DetailRow(label = stringResource(Res.string.tx_detail_source_label), value = com.cashbuddy.platform.getApplicationLabel(tx.sourceApp))
                    }
                }

                // Category Selector
                Text(
                    text = stringResource(Res.string.tx_detail_change_category),
                    style = CashBuddyTypography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.categories.forEach { cat ->
                        val isSelected = cat.id == tx.categoryId
                        val catColor = getCategoryColor(cat.name)
                        Box(
                            modifier = Modifier
                                .clip(RadiusMedium)
                                .background(
                                    if (isSelected) catColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .clickable { viewModel.onCategoryChanged(cat.id) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cat.name,
                                color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface,
                                style = CashBuddyTypography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Raw Source Text
                if (tx.rawText.isNotBlank()) {
                    Text(
                        text = stringResource(Res.string.tx_detail_original_notification),
                        style = CashBuddyTypography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RadiusMedium)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = tx.rawText,
                            style = CashBuddyTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Edit Button
                val editLabel = stringResource(Res.string.action_edit)
                val deleteLabel = stringResource(Res.string.tx_detail_delete_button)
                Button(
                    onClick = { showEditDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RadiusMedium
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_edit),
                            contentDescription = editLabel,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = editLabel, style = CashBuddyTypography.labelLarge, color = Color.White)
                    }
                }

                // Delete Button
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    shape = RadiusMedium
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_delete_forever),
                            contentDescription = deleteLabel,
                            tint = DangerRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = deleteLabel, style = CashBuddyTypography.labelLarge, color = DangerRed)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text(stringResource(Res.string.dialog_delete_tx_title), style = CashBuddyTypography.titleMedium, fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(Res.string.dialog_delete_tx_message), style = CashBuddyTypography.bodyMedium) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.onDeleteClicked()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text(stringResource(Res.string.action_delete), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                }
            )
        }

        if (showEditDialog && state.transaction != null) {
            val currentTx = state.transaction!!
            EditTransactionDialog(
                initialAmount = currentTx.amount,
                initialType = currentTx.type,
                initialMerchant = currentTx.merchant,
                initialCategoryId = currentTx.categoryId,
                categories = state.categories,
                title = stringResource(Res.string.dialog_edit_tx_title),
                confirmButtonText = stringResource(Res.string.dialog_edit_tx_save_changes),
                onDismiss = { showEditDialog = false },
                onConfirm = { amount, type, merchant, categoryId ->
                    viewModel.onUpdateTransaction(amount, type, merchant, categoryId)
                }
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = CashBuddyTypography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = CashBuddyTypography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
