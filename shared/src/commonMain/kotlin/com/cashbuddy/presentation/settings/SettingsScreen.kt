package com.cashbuddy.presentation.settings

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Undo
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.ic_bug_report
import cashbuddy.shared.generated.resources.ic_cashbuddy_logo
import cashbuddy.shared.generated.resources.ic_delete_forever
import cashbuddy.shared.generated.resources.ic_download
import cashbuddy.shared.generated.resources.ic_file_download
import cashbuddy.shared.generated.resources.ic_tune
import cashbuddy.shared.generated.resources.ic_visibility
import cashbuddy.shared.generated.resources.ic_warning
import org.jetbrains.compose.resources.painterResource
import com.cashbuddy.presentation.settings.components.DuplicateReconciliationDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.cashbuddy.presentation.theme.ExpenseCrimson
import com.cashbuddy.presentation.theme.TealMintSecondary
import com.cashbuddy.presentation.theme.TrustBluePrimary
import kotlinx.coroutines.flow.collectLatest

import androidx.compose.ui.platform.LocalUriHandler
import com.cashbuddy.platform.getAppVersionDisplay
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.RadiusSmall

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToPersonalization: () -> Unit = {},
    onNavigateToDebugLog: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearTrainingDialog by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showIgnoreDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messageEffect.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
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
                item {
                    Text(
                        text = "Settings",
                        style = CashBuddyTypography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }



                // Automation & Threshold Rules
                item {
                    Text(
                        text = "Automation & Review Policy",
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RadiusLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Notification Intake Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Notification Intake",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Parse bank & payment apps passively",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.notificationEnabled,
                                    onCheckedChange = { viewModel.setNotificationEnabled(it) }
                                )
                            }

                            // Smart Notifications Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Smart Notifications",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Budget pacing alerts, anomaly warnings & weekly recaps",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.smartNotificationsEnabled,
                                    onCheckedChange = { viewModel.setSmartNotificationsEnabled(it) }
                                )
                            }

                            OutlinedButton(
                                onClick = onOpenNotificationSettings,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_tune),
                                    contentDescription = "Notification Settings",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Android Notification Access Settings")
                            }

                            OutlinedButton(
                                onClick = { showIgnoreDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_warning),
                                    contentDescription = "Ignore List",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ignored Apps List (${state.ignoredPackages.size})")
                            }

                            if (state.isOcrAvailable) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Screenshot Receipt OCR",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Screenshot OCR uses ML Kit on the Play Store build and Tesseract on the F-Droid build. Both run entirely on-device.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AccentEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                        }
                    }
                }

                // Security & Biometrics
                item {
                    Text(
                        text = "Security & Biometrics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Biometric Lock",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Require fingerprint / face unlock on launch",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.biometricLockEnabled,
                                onCheckedChange = { viewModel.setBiometricLockEnabled(it) }
                            )
                        }
                    }
                }

                // On-Device Learning & Training Data
                item {
                    Text(
                        text = "On-Device Learning & Training Data",
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RadiusLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Personalize categorization by correcting categories. Correcting a transaction saves a merchant rule for future transactions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Stats Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RadiusMedium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = PrimaryIndigo.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "${state.rawTrainingSamplesCount}",
                                            style = CashBuddyTypography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryIndigo
                                        )
                                        Text(
                                            text = "Raw Samples",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RadiusMedium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = AccentEmerald.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "${state.userCorrectionsCount}",
                                            style = CashBuddyTypography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentEmerald
                                        )
                                        Text(
                                            text = "User Corrections",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Actions
                            Button(
                                onClick = onNavigateToPersonalization,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RadiusMedium,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_tune),
                                    contentDescription = "Personalization Dashboard",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Personalization Dashboard")
                            }

                            OutlinedButton(
                                onClick = { viewModel.exportTrainingDataset() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RadiusMedium
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_file_download),
                                    contentDescription = "Export Dataset",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Training Dataset (JSONL)")
                            }

                            OutlinedButton(
                                onClick = { showClearTrainingDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RadiusMedium,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_delete_forever),
                                    contentDescription = "Reset Training Data",
                                    tint = DangerRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reset Training Data & Corrections")
                            }
                        }
                    }
                }

                // Data Management Section
                item {
                    Text(
                        text = "Data Management",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.exportCsvData {} },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_download),
                                    contentDescription = "Export Transactions",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export All Transactions (CSV)")
                            }

                            OutlinedButton(
                                onClick = { showImportDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_file_download),
                                    contentDescription = "Import Transactions",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import Transactions (CSV)")
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.scanDuplicates {
                                        showDuplicateDialog = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MergeType,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (state.isScanningDuplicates) "Scanning Duplicates..."
                                    else "Clean Up Duplicates"
                                )
                            }

                            if (state.hasRecentMerges) {
                                OutlinedButton(
                                    onClick = { viewModel.undoLastMerge() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Undo Last Duplicate Merge")
                                }
                            }

                            Button(
                                onClick = { showClearDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ExpenseCrimson),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_delete_forever),
                                    contentDescription = "Clear All Data",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clear All Data", color = Color.White)
                            }
                        }
                    }
                }

                // Developer & Diagnostics: Debug Log Viewer Toggle
                item {
                    Text(
                        text = "Developer & Diagnostics",
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (state.isDebugLogEnabled) PrimaryIndigo.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_bug_report),
                                            contentDescription = "Debug Log",
                                            tint = if (state.isDebugLogEnabled) PrimaryIndigo else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "Debug Log Viewer",
                                            style = CashBuddyTypography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (state.isDebugLogEnabled) {
                                                "Active • Capturing pipeline logs"
                                            } else {
                                                "Disabled • Zero logs captured"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (state.isDebugLogEnabled) {
                                                AccentEmerald
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            fontWeight = if (state.isDebugLogEnabled) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }

                                Switch(
                                    checked = state.isDebugLogEnabled,
                                    onCheckedChange = { viewModel.setDebugLogEnabled(it) }
                                )
                            }

                            if (state.isDebugLogEnabled) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Inspect on-device parser decisions, regex extractions, and classification confidence scores. All data stays 100% on-device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onNavigateToDebugLog,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_visibility),
                                        contentDescription = "Open Log Viewer",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Log Viewer")
                                }
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Toggle ON to enable recording and view debug logs. When turned off, no logs are taken.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // About, Privacy Policy & Terms of Service
                item {
                    Text(
                        text = "About, Privacy Policy & Legal",
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
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
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Read CashBuddy's 100% offline architecture philosophy, core capabilities, privacy policy, and terms of service.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedButton(
                                onClick = { uriHandler.openUri("https://l1khith.github.io/CashBuddy/#2-privacy-policy") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Privacy Policy")
                            }

                            OutlinedButton(
                                onClick = { uriHandler.openUri("https://l1khith.github.io/CashBuddy/#3-terms-of-service") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Terms of Service")
                            }

                            OutlinedButton(
                                onClick = { uriHandler.openUri("https://l1khith.github.io/CashBuddy/") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("About CashBuddy (Official Website)")
                            }

                            OutlinedButton(
                                onClick = { uriHandler.openUri("https://github.com/l1khith/CashBuddy") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("GitHub Repository")
                            }
                        }
                    }
                }

                // Version Footer & License Attribution
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.ic_cashbuddy_logo),
                            contentDescription = "CashBuddy Logo",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = getAppVersionDisplay(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.onVersionClicked() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        if (state.isDebugLogEnabled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Debug Logging Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryIndigo,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Delete All Transactions?") },
            text = { Text("This will permanently remove all stored transactions from the local encrypted database. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseCrimson)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearTrainingDialog) {
        AlertDialog(
            onDismissRequest = { showClearTrainingDialog = false },
            title = { Text("Reset Training Data & Corrections?") },
            text = { Text("This will permanently delete all captured raw notification logs and user corrections used for local fine-tuning. Learned merchant rules will also be cleared. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearTrainingData()
                        showClearTrainingDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Reset Dataset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTrainingDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDuplicateDialog) {
        DuplicateReconciliationDialog(
            duplicateGroups = state.duplicateGroups,
            preferredCurrency = state.preferredCurrency,
            isMerging = state.isMerging,
            onMergeConfirmed = {
                viewModel.mergeDuplicates(state.duplicateGroups) {
                    showDuplicateDialog = false
                }
            },
            onPreviewCsv = {
                viewModel.exportCsvData {}
            },
            onDismiss = { showDuplicateDialog = false }
        )
    }

    // Ignore List Dialog (Feature 4)
    if (showIgnoreDialog) {
        var newPkgText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showIgnoreDialog = false },
            title = {
                Text(
                    text = "Ignored Apps",
                    style = CashBuddyTypography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Notifications from these packages will be silently dropped and ignored by the parser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newPkgText,
                            onValueChange = { newPkgText = it },
                            placeholder = { Text("com.example.app", style = CashBuddyTypography.bodySmall) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RadiusSmall
                        )
                        Button(
                            onClick = {
                                if (newPkgText.isNotBlank()) {
                                    viewModel.addIgnoredPackage(newPkgText)
                                    newPkgText = ""
                                }
                            },
                            shape = RadiusSmall
                        ) {
                            Text("Add")
                        }
                    }

                    if (state.ignoredPackages.isEmpty()) {
                        Text(
                            text = "No packages currently ignored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = state.ignoredPackages,
                                key = { it }
                            ) { pkg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RadiusSmall)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pkg,
                                        style = CashBuddyTypography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { viewModel.removeIgnoredPackage(pkg) }
                                    ) {
                                        Text("Remove", color = DangerRed, style = CashBuddyTypography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIgnoreDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // CSV Import Dialog (Feature 6)
    if (showImportDialog) {
        var csvText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text(
                    text = "Import Transactions (CSV)",
                    style = CashBuddyTypography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Paste CSV data below. Supports standard exported ledger columns or 'Amount,Type,Currency,Merchant,Notes'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = csvText,
                        onValueChange = { csvText = it },
                        placeholder = { Text("Amount,Type,Currency,Merchant...\n150,DEBIT,INR,Swiggy,Lunch", style = CashBuddyTypography.bodySmall) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RadiusMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (csvText.isNotBlank()) {
                            viewModel.importCsvData(csvText)
                            showImportDialog = false
                        }
                    },
                    enabled = csvText.isNotBlank() && !state.isImportingCsv
                ) {
                    Text(if (state.isImportingCsv) "Importing..." else "Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
