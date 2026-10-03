package com.cashbuddy.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.getCategoryColor

@Composable
fun EditTransactionDialog(
    initialAmount: Double,
    initialType: TransactionType,
    initialMerchant: String,
    initialCategoryId: Long,
    categories: List<Category>,
    title: String = "Edit Transaction",
    confirmButtonText: String = "Save",
    actionButtonText: String? = null,
    onAction: ((amount: Double, type: TransactionType, merchant: String, categoryId: Long) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, type: TransactionType, merchant: String, categoryId: Long) -> Unit
) {
    var amountText by remember {
        mutableStateOf(if (initialAmount % 1.0 == 0.0) initialAmount.toLong().toString() else initialAmount.toString())
    }
    var selectedType by remember { mutableStateOf(initialType) }
    var merchantText by remember { mutableStateOf(initialMerchant) }
    var selectedCategoryId by remember { mutableStateOf(initialCategoryId) }

    val parsedAmount = amountText.toDoubleOrNull()
    val isAmountValid = parsedAmount != null && parsedAmount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = CashBuddyTypography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Type Toggle (Expense vs Income)
                Text(
                    text = "Transaction Type",
                    style = CashBuddyTypography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Expense Chip
                    FilterChip(
                        selected = selectedType == TransactionType.DEBIT,
                        onClick = { selectedType = TransactionType.DEBIT },
                        label = { Text("Expense", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DangerRed.copy(alpha = 0.2f),
                            selectedLabelColor = DangerRed,
                            selectedLeadingIconColor = DangerRed
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    // Income Chip
                    FilterChip(
                        selected = selectedType == TransactionType.CREDIT,
                        onClick = { selectedType = TransactionType.CREDIT },
                        label = { Text("Income", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentEmerald.copy(alpha = 0.2f),
                            selectedLabelColor = AccentEmerald,
                            selectedLeadingIconColor = AccentEmerald
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Amount Input
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                            amountText = input
                        }
                    },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountText.isNotBlank() && !isAmountValid,
                    modifier = Modifier.fillMaxWidth()
                )

                // Merchant Input
                OutlinedTextField(
                    value = merchantText,
                    onValueChange = { merchantText = it },
                    label = { Text("Merchant / Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Selector
                Text(
                    text = "Category",
                    style = CashBuddyTypography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = cat.id == selectedCategoryId
                        val catColor = getCategoryColor(cat.name)
                        Box(
                            modifier = Modifier
                                .clip(RadiusMedium)
                                .background(
                                    if (isSelected) catColor.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .clickable { selectedCategoryId = cat.id }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = catColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = cat.name,
                                    style = CashBuddyTypography.labelMedium,
                                    color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (actionButtonText != null && onAction != null) {
                    Button(
                        onClick = {
                            val amount = parsedAmount
                            if (amount != null && amount > 0.0) {
                                onAction(amount, selectedType, merchantText, selectedCategoryId)
                                onDismiss()
                            }
                        },
                        enabled = isAmountValid,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                    ) {
                        Text(actionButtonText, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        val amount = parsedAmount
                        if (amount != null && amount > 0.0) {
                            onConfirm(amount, selectedType, merchantText, selectedCategoryId)
                            onDismiss()
                        }
                    },
                    enabled = isAmountValid
                ) {
                    Text(confirmButtonText, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
