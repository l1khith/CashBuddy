package com.cashbuddy.presentation.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import com.cashbuddy.domain.model.BudgetCategories
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.BudgetStatus
import com.cashbuddy.domain.model.Category
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.TrustBluePrimary
import com.cashbuddy.presentation.theme.getCategoryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditSheet(
    initialBudget: Budget? = null,
    categories: List<Category>,
    existingBudgets: List<BudgetStatus> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (category: String, amount: Double, period: BudgetPeriod) -> Unit,
    onDelete: ((budgetId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedCategory by remember {
        mutableStateOf(initialBudget?.category ?: (categories.firstOrNull()?.name ?: ""))
    }
    val isGlobal = selectedCategory == BudgetCategories.GLOBAL
    val categoryDisplayText = if (isGlobal) "Global (all spending)" else selectedCategory

    var amountText by remember {
        mutableStateOf(
            initialBudget?.amount?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var selectedPeriod by remember {
        mutableStateOf(initialBudget?.period ?: BudgetPeriod.MONTHLY)
    }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RadiusLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (initialBudget != null) "Edit Budget" else "New Budget",
                style = CashBuddyTypography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Category Selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Category",
                    style = CashBuddyTypography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = categoryDisplayText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryDropdownExpanded = true },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Category",
                                modifier = Modifier.clickable { categoryDropdownExpanded = true }
                            )
                        },
                        leadingIcon = {
                            if (isGlobal) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "Global",
                                    tint = TrustBluePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else if (selectedCategory.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(getCategoryColor(selectedCategory))
                                )
                            }
                        },
                        shape = RadiusMedium
                    )

                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        // Global (all spending) as first option
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = TrustBluePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Global (all spending)", fontWeight = FontWeight.SemiBold)
                                }
                            },
                            onClick = {
                                selectedCategory = BudgetCategories.GLOBAL
                                categoryDropdownExpanded = false
                                inlineError = null
                            }
                        )

                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(getCategoryColor(cat.name))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(cat.name)
                                    }
                                },
                                onClick = {
                                    selectedCategory = cat.name
                                    categoryDropdownExpanded = false
                                    inlineError = null
                                }
                            )
                        }
                    }
                }
            }

            // Amount field
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Limit Amount",
                    style = CashBuddyTypography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        inlineError = null
                    },
                    prefix = { Text("₹", fontWeight = FontWeight.Bold) },
                    placeholder = { Text("e.g. 5000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RadiusMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Period segmented control
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Period",
                    style = CashBuddyTypography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BudgetPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = {
                                selectedPeriod = period
                                inlineError = null
                            },
                            label = { Text(period.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TrustBluePrimary,
                                selectedLabelColor = Color.White
                            ),
                            shape = RadiusMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Inline validation error
            inlineError?.let { err ->
                Text(
                    text = err,
                    color = DangerRed,
                    style = CashBuddyTypography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (initialBudget != null && onDelete != null) {
                    OutlinedButton(
                        onClick = { onDelete(initialBudget.id) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        shape = RadiusMedium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Budget",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }

                Button(
                    onClick = {
                        val error = validateBudgetInput(
                            category = selectedCategory,
                            amountText = amountText,
                            period = selectedPeriod,
                            isEditing = initialBudget != null,
                            existingBudgets = existingBudgets,
                            currentBudgetId = initialBudget?.id
                        )
                        if (error != null) {
                            inlineError = error
                        } else {
                            val amount = amountText.toDouble()
                            onSave(selectedCategory, amount, selectedPeriod)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrustBluePrimary),
                    shape = RadiusMedium,
                    modifier = Modifier.weight(if (initialBudget != null && onDelete != null) 2f else 1f)
                ) {
                    Text(if (initialBudget != null) "Update Budget" else "Save Budget")
                }
            }
        }
    }
}

fun validateBudgetInput(
    category: String,
    amountText: String,
    period: BudgetPeriod,
    isEditing: Boolean,
    existingBudgets: List<BudgetStatus>,
    currentBudgetId: String? = null
): String? {
    val amount = amountText.toDoubleOrNull()
    val isGlobal = category == BudgetCategories.GLOBAL
    val hasDuplicate = if (!isEditing) {
        if (isGlobal) {
            existingBudgets.any {
                it.budget.category == BudgetCategories.GLOBAL && it.budget.period == period
            }
        } else {
            existingBudgets.any {
                it.budget.category.equals(category, ignoreCase = true) && it.budget.period == period
            }
        }
    } else {
        existingBudgets.any {
            it.budget.id != currentBudgetId &&
            it.budget.category.equals(category, ignoreCase = true) &&
            it.budget.period == period
        }
    }

    return when {
        category.isBlank() -> "Please select a category"
        amount == null || amount <= 0.0 -> "Please enter an amount greater than ₹0"
        hasDuplicate -> {
            if (isGlobal) "A global budget for this period already exists"
            else "A ${period.name.lowercase()} budget for $category already exists"
        }
        else -> null
    }
}
