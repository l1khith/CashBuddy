package com.cashbuddy.presentation.goals

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.domain.model.Goal
import com.cashbuddy.presentation.components.formatCurrency
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.RadiusSmall
import com.cashbuddy.presentation.components.EmptyStateView
import com.cashbuddy.presentation.theme.IncomeEmerald
import com.cashbuddy.presentation.theme.TealMintSecondary
import com.cashbuddy.presentation.theme.TrustBluePrimary

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var contributingGoal by remember { mutableStateOf<Goal?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = TealMintSecondary,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Goal"
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
                CircularProgressIndicator(color = TealMintSecondary)
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
                        text = "Savings Goals",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (state.goals.isEmpty()) {
                    item {
                        EmptyStateView(
                            imageVector = Icons.Default.Flag,
                            title = "No savings goals set",
                            subtitle = "Create a goal for an emergency fund, travel, or big purchase.",
                            actionButtonText = "Create First Goal",
                            onActionClick = { showAddDialog = true }
                        )
                    }
                } else {
                    items(state.goals, key = { it.id }) { goal ->
                        GoalItemCard(
                            goal = goal,
                            onContribute = { contributingGoal = goal }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, target ->
                viewModel.addGoal(name, target)
                showAddDialog = false
            }
        )
    }

    contributingGoal?.let { goal ->
        ContributeGoalDialog(
            goalName = goal.name,
            onDismiss = { contributingGoal = null },
            onContribute = { amount ->
                viewModel.contribute(goal.id, amount)
                contributingGoal = null
            }
        )
    }
}

@Composable
private fun GoalItemCard(goal: Goal, onContribute: () -> Unit) {
    val fraction = if (goal.targetAmount > 0) {
        (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f)
    } else 0f
    val percent = (fraction * 100).toInt()

    val isDone = goal.status == com.cashbuddy.domain.model.GoalStatus.COMPLETED
    val progressColor = if (isDone) AccentEmerald else PrimaryIndigo

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
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = goal.name,
                    style = CashBuddyTypography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$percent%",
                    style = CashBuddyTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RadiusSmall),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved ₹${formatCurrency(goal.currentAmount)} of ₹${formatCurrency(goal.targetAmount)}",
                    style = CashBuddyTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isDone) {
                    OutlinedButton(
                        onClick = onContribute,
                        shape = RadiusMedium
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Money", style = CashBuddyTypography.labelSmall)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Completed!",
                            color = AccentEmerald,
                            style = CashBuddyTypography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Savings Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Goal Name (e.g. Emergency Fund)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Target Amount (₹)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = targetText.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && target > 0) {
                        onAdd(name, target)
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ContributeGoalDialog(
    goalName: String,
    onDismiss: () -> Unit,
    onContribute: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contribute to $goalName") },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount to add (₹)") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0) {
                        onContribute(amount)
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
