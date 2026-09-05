package com.cashbuddy.presentation.personalization

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.WarningAmber
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationDashboardScreen(
    viewModel: PersonalizationViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDataView: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messageEffect.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Personalization",
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryIndigo
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryIndigo)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Personalization Stats Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RadiusLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Categorization Accuracy",
                                        style = CashBuddyTypography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${state.accuracyRate}%",
                                        style = CashBuddyTypography.displayLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AccentEmerald
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            AccentEmerald.copy(alpha = 0.15f),
                                            shape = RadiusMedium
                                        )
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = AccentEmerald,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Local Rules",
                                            style = CashBuddyTypography.labelSmall,
                                            color = AccentEmerald,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // 3 Metric Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricBadge(
                                    label = "Learned",
                                    value = "${state.learnedMerchantsCount}",
                                    unit = "merchants",
                                    color = PrimaryIndigo,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricBadge(
                                    label = "Corrections",
                                    value = "${state.correctionsCount}",
                                    unit = "feedback",
                                    color = WarningAmber,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricBadge(
                                    label = "Raw Alerts",
                                    value = "${state.rawSamplesCount}",
                                    unit = "samples",
                                    color = AccentEmerald,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Action Controls Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RadiusLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Personalization Actions",
                                style = CashBuddyTypography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Train Now Button
                            Button(
                                onClick = { viewModel.trainNow() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RadiusMedium,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                enabled = !state.isTraining
                            ) {
                                if (state.isTraining) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Evaluating Pending Rules...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Train / Re-categorize Now")
                                }
                            }

                            // View Data Button
                            OutlinedButton(
                                onClick = onNavigateToDataView,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RadiusMedium
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TableRows,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("View Training Data (${state.rawSamplesCount + state.correctionsCount} records)")
                            }

                            // Export Dataset Button
                            OutlinedButton(
                                onClick = { viewModel.exportDataset() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RadiusMedium
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Training Dataset (JSONL)")
                            }

                            // Reset Button
                            TextButton(
                                onClick = { showResetDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reset All Learned Personalization",
                                    color = DangerRed
                                )
                            }
                        }
                    }
                }

                // Recent Corrections Header
                item {
                    Text(
                        text = "Recent Learned Corrections",
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.recentCorrections.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RadiusMedium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No user corrections recorded yet",
                                    style = CashBuddyTypography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "When you edit a category in transaction details, CashBuddy will learn it instantly.",
                                    style = CashBuddyTypography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(state.recentCorrections, key = { it.id }) { correction ->
                        CorrectionItemCard(correction = correction)
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Learned Rules & Data?") },
            text = { Text("This will delete all learned merchant rules, user correction history, and raw training logs. Auto-categorization will revert to default built-in heuristics.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetLearningData()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MetricBadge(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RadiusMedium,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                style = CashBuddyTypography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "$label • $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CorrectionItemCard(
    correction: com.cashbuddy.domain.model.UserCorrection,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RadiusMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(PrimaryIndigo.copy(alpha = 0.15f), shape = RadiusMedium)
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = correction.merchant,
                    style = CashBuddyTypography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = correction.oldCategory ?: "Unassigned",
                        style = CashBuddyTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = correction.newCategory,
                        style = CashBuddyTypography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentEmerald
                    )
                }
            }
        }
    }
}
