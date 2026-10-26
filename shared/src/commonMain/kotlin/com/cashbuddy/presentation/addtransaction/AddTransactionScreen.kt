package com.cashbuddy.presentation.addtransaction

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.cashbuddy.platform.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.presentation.theme.ExpenseCrimson
import com.cashbuddy.presentation.theme.IncomeEmerald
import com.cashbuddy.presentation.theme.TrustBluePrimary
import kotlinx.coroutines.flow.collectLatest

import com.cashbuddy.presentation.components.CustomNumpad
import com.cashbuddy.presentation.components.formatCurrency
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.RadiusSmall
import com.cashbuddy.presentation.theme.getCategoryColor

@Composable
fun AddTransactionScreen(
    viewModel: AddTransactionViewModel,
    prefilledId: Long? = null,
    prefilledUtr: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var amountString by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(TransactionType.DEBIT) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var hasInitializedPrefill by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    val hasUnsavedChanges = (amountString.isNotBlank() && amountString != "0") || merchant.isNotBlank()

    BackHandler(enabled = hasUnsavedChanges) {
        showDiscardDialog = true
    }

    LaunchedEffect(prefilledUtr) {
        if (!prefilledUtr.isNullOrBlank()) {
            viewModel.setPrefilledUtr(prefilledUtr)
            if (notes.isBlank()) {
                notes = "UPI Ref: $prefilledUtr"
            }
        }
    }

    LaunchedEffect(prefilledId) {
        if (!hasInitializedPrefill && prefilledId != null && prefilledId > 0L) {
            hasInitializedPrefill = true
            viewModel.prefillTransaction(prefilledId) { amt, type, merch, catId, accId, nts ->
                amountString = if (amt == amt.toLong().toDouble()) amt.toLong().toString() else amt.toString()
                selectedType = type
                merchant = merch
                selectedCategoryId = catId
                selectedAccountId = accId
                notes = nts ?: ""
            }
        }
    }

    LaunchedEffect(state.categories, state.accounts) {
        if (selectedCategoryId == null && state.categories.isNotEmpty()) {
            selectedCategoryId = state.suggestedCategoryId ?: state.categories.first().id
        }
        if (selectedAccountId == null && state.accounts.isNotEmpty()) {
            selectedAccountId = state.accounts.first().id
        }
    }

    LaunchedEffect(state.suggestedCategoryId) {
        if (selectedCategoryId == null && state.suggestedCategoryId != null) {
            selectedCategoryId = state.suggestedCategoryId
        }
    }

    LaunchedEffect(state.defaultMerchant) {
        if (merchant.isBlank() && state.defaultMerchant.isNotBlank()) {
            merchant = state.defaultMerchant
        }
    }

    LaunchedEffect(Unit) {
        viewModel.savedEffect.collectLatest {
            onNavigateBack()
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
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (hasUnsavedChanges) showDiscardDialog = true else onNavigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "New Transaction",
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }

                // Type Toggle (Expense / Income)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RadiusMedium
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RadiusMedium)
                            .background(if (selectedType == TransactionType.DEBIT) DangerRed else Color.Transparent)
                            .clickable { selectedType = TransactionType.DEBIT }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Expense",
                            style = CashBuddyTypography.labelLarge,
                            color = if (selectedType == TransactionType.DEBIT) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RadiusMedium)
                            .background(if (selectedType == TransactionType.CREDIT) AccentEmerald else Color.Transparent)
                            .clickable { selectedType = TransactionType.CREDIT }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Income",
                            style = CashBuddyTypography.labelLarge,
                            color = if (selectedType == TransactionType.CREDIT) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Big Hero Amount Display Card
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
                            .padding(vertical = 20.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "AMOUNT",
                            style = CashBuddyTypography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val displayAmount = if (amountString.isBlank()) "0.00" else amountString
                        val prefix = if (selectedType == TransactionType.DEBIT) "-₹" else "+₹"
                        val amountColor = if (selectedType == TransactionType.DEBIT) MaterialTheme.colorScheme.onSurface else AccentEmerald

                        Text(
                            text = "$prefix$displayAmount",
                            style = CashBuddyTypography.displayLarge.copy(fontSize = 42.sp),
                            fontWeight = FontWeight.Bold,
                            color = amountColor
                        )
                    }
                }

                // Frequent Amount Quick-Select Chips
                if (state.frequentAmounts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.frequentAmounts.forEach { amt ->
                            val amtStr = if (amt % 1.0 == 0.0) amt.toLong().toString() else amt.toString()
                            Box(
                                modifier = Modifier
                                    .clip(RadiusMedium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .clickable { amountString = amtStr }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "₹$amtStr",
                                    style = CashBuddyTypography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Tactile Custom Numpad
                CustomNumpad(
                    onDigitClick = { digit ->
                        if (amountString.length < 9) {
                            if (amountString == "0") {
                                amountString = digit
                            } else {
                                amountString += digit
                            }
                        }
                    },
                    onDecimalClick = {
                        if (!amountString.contains(".") && amountString.length < 8) {
                            amountString = if (amountString.isEmpty()) "0." else "$amountString."
                        }
                    },
                    onBackspaceClick = {
                        if (amountString.isNotEmpty()) {
                            amountString = amountString.dropLast(1)
                        }
                    }
                )

                // Merchant / Description
                OutlinedTextField(
                    value = merchant,
                    onValueChange = {
                        merchant = it
                        val suggestedId = viewModel.onMerchantChanged(it)
                        if (suggestedId != null) {
                            selectedCategoryId = suggestedId
                        }
                    },
                    label = { Text("Merchant / Description (e.g. Swiggy, Metro)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RadiusMedium
                )

                // Recent Merchants Quick-Select Chips
                if (state.recentMerchants.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.recentMerchants.forEach { m ->
                            Box(
                                modifier = Modifier
                                    .clip(RadiusMedium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable {
                                        merchant = m
                                        val suggestedId = viewModel.onMerchantChanged(m)
                                        if (suggestedId != null) {
                                            selectedCategoryId = suggestedId
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = m,
                                    style = CashBuddyTypography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Category Selection
                Text(
                    text = "Category",
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
                        val isSelected = cat.id == selectedCategoryId
                        val catColor = getCategoryColor(cat.name)
                        Box(
                            modifier = Modifier
                                .clip(RadiusMedium)
                                .background(
                                    if (isSelected) catColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable { selectedCategoryId = cat.id }
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

                // Account Selection
                if (state.accounts.isNotEmpty()) {
                    Text(
                        text = "Account",
                        style = CashBuddyTypography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.accounts.forEach { acc ->
                            val isSelected = acc.id == selectedAccountId
                            Box(
                                modifier = Modifier
                                    .clip(RadiusMedium)
                                    .background(
                                        if (isSelected) PrimaryIndigo else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .clickable { selectedAccountId = acc.id }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = acc.name,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    style = CashBuddyTypography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Notes Field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RadiusMedium
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Save Button
                val parsedAmount = amountString.toDoubleOrNull() ?: 0.0
                val isSaveEnabled = parsedAmount > 0.0

                Button(
                    onClick = {
                        val catId = selectedCategoryId ?: 1L
                        val accId = selectedAccountId ?: 1L
                        if (parsedAmount > 0.0) {
                            viewModel.saveTransaction(
                                amount = parsedAmount,
                                type = selectedType,
                                merchant = merchant.ifBlank { "Manual Entry" },
                                categoryId = catId,
                                accountId = accId,
                                notes = notes.ifBlank { null }
                            )
                        }
                    },
                    enabled = isSaveEnabled && !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryIndigo,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RadiusMedium
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Save Transaction",
                            style = CashBuddyTypography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSaveEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("Discard Changes?", style = CashBuddyTypography.titleMedium, fontWeight = FontWeight.Bold) },
                text = { Text("You have unsaved changes. Are you sure you want to discard them?", style = CashBuddyTypography.bodyMedium) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDiscardDialog = false
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text("Discard", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text("Keep Editing")
                    }
                }
            )
        }
    }
}
