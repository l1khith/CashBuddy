package com.cashbuddy.presentation.accounts

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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.domain.model.Account
import com.cashbuddy.domain.model.AccountType
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.theme.TrustBluePrimary
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.cashbuddy.presentation.components.formatCurrency
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = TrustBluePrimary,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Account"
                )
            }
        }
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "Accounts & Wallets",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (state.accounts.isEmpty()) {
                    item {
                        EmptyStateView(
                            imageVector = Icons.Default.AccountBalance,
                            title = "No accounts added yet",
                            subtitle = "Add your bank account or payment wallet to track balances.",
                            actionButtonText = "Add First Account",
                            onActionClick = { showAddDialog = true }
                        )
                    }
                } else {
                    items(state.accounts, key = { it.id }) { acc ->
                        AccountItemCard(account = acc, onDelete = { viewModel.deleteAccount(acc.id) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAccountDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, type, balance, last4 ->
                viewModel.addAccount(name, type, balance, last4)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AccountItemCard(account: Account, onDelete: () -> Unit) {
    val icon = when (account.type) {
        AccountType.BANK -> Icons.Default.AccountBalance
        AccountType.WALLET -> Icons.Default.AccountBalanceWallet
        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
        else -> Icons.Default.Paid
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RadiusLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = account.type.name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = account.name,
                        style = CashBuddyTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${account.type.name} • ${account.number ?: "Active"}",
                        style = CashBuddyTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${formatCurrency(account.balance)}",
                    style = CashBuddyTypography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onAdd: (String, AccountType, Double, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.BANK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name (e.g. HDFC Bank)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Initial Balance (₹)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = last4,
                    onValueChange = { last4 = it },
                    label = { Text("Account / Card Last 4 Digits") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bal = balanceText.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) {
                        onAdd(name, selectedType, bal, if (last4.isNotBlank()) "XX$last4" else null)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
