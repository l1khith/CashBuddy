package com.cashbuddy.presentation.settings.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.ic_file_download
import com.cashbuddy.platform.rememberFilePicker
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.ExpenseCrimson
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.RadiusSmall
import org.jetbrains.compose.resources.painterResource

import androidx.compose.runtime.Stable
import com.cashbuddy.core.DuplicateReconciler.DuplicateGroup
import com.cashbuddy.presentation.settings.components.DuplicateReconciliationDialog

@Stable
class SettingsDialogState {
    var showClearDialog by mutableStateOf(false)
    var showClearTrainingDialog by mutableStateOf(false)
    var showDuplicateDialog by mutableStateOf(false)
    var showIgnoreDialog by mutableStateOf(false)
    var showImportDialog by mutableStateOf(false)

    fun dismissAll() {
        showClearDialog = false
        showClearTrainingDialog = false
        showDuplicateDialog = false
        showIgnoreDialog = false
        showImportDialog = false
    }
}

@Composable
fun SettingsDialogsHost(
    dialogState: SettingsDialogState,
    duplicateGroups: List<DuplicateGroup>,
    preferredCurrency: String,
    isMerging: Boolean,
    ignoredPackages: List<String>,
    isImportingCsv: Boolean,
    onClearAllData: () -> Unit,
    onClearTrainingData: () -> Unit,
    onMergeDuplicates: () -> Unit,
    onPreviewCsv: () -> Unit,
    onAddIgnoredPackage: (String) -> Unit,
    onRemoveIgnoredPackage: (String) -> Unit,
    onImportCsv: (String) -> Unit
) {
    if (dialogState.showClearDialog) {
        ClearDataDialog(
            onConfirm = {
                onClearAllData()
                dialogState.showClearDialog = false
            },
            onDismiss = { dialogState.showClearDialog = false }
        )
    }

    if (dialogState.showClearTrainingDialog) {
        ClearTrainingDialog(
            onConfirm = {
                onClearTrainingData()
                dialogState.showClearTrainingDialog = false
            },
            onDismiss = { dialogState.showClearTrainingDialog = false }
        )
    }

    if (dialogState.showDuplicateDialog) {
        DuplicateReconciliationDialog(
            duplicateGroups = duplicateGroups,
            preferredCurrency = preferredCurrency,
            isMerging = isMerging,
            onMergeConfirmed = onMergeDuplicates,
            onPreviewCsv = onPreviewCsv,
            onDismiss = { dialogState.showDuplicateDialog = false }
        )
    }

    if (dialogState.showIgnoreDialog) {
        IgnoredAppsDialog(
            ignoredPackages = ignoredPackages,
            onAddPackage = onAddIgnoredPackage,
            onRemovePackage = onRemoveIgnoredPackage,
            onDismiss = { dialogState.showIgnoreDialog = false }
        )
    }

    if (dialogState.showImportDialog) {
        ImportCsvDialog(
            isImporting = isImportingCsv,
            onImport = { csvText ->
                onImportCsv(csvText)
                dialogState.showImportDialog = false
            },
            onDismiss = { dialogState.showImportDialog = false }
        )
    }
}

@Composable
fun ClearDataDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete All Transactions?") },
        text = { Text("This will permanently remove all stored transactions from the local encrypted database. This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ExpenseCrimson)
            ) {
                Text("Delete Everything")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ClearTrainingDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Training Data & Corrections?") },
        text = { Text("This will permanently delete all captured raw notification logs and user corrections used for local fine-tuning. Learned merchant rules will also be cleared. This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
            ) {
                Text("Reset Dataset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun IgnoredAppsDialog(
    ignoredPackages: List<String>,
    onAddPackage: (String) -> Unit,
    onRemovePackage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPkgText by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                                onAddPackage(newPkgText)
                                newPkgText = ""
                            }
                        },
                        shape = RadiusSmall
                    ) {
                        Text("Add")
                    }
                }

                if (ignoredPackages.isEmpty()) {
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
                            items = ignoredPackages,
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
                                    onClick = { onRemovePackage(pkg) }
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
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ImportCsvDialog(
    isImporting: Boolean,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var csvText by rememberSaveable { mutableStateOf("") }
    var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }

    val openFilePicker = rememberFilePicker(mimeType = "text/csv") { content, fileName ->
        if (content != null) {
            csvText = content
            selectedFileName = fileName ?: "Selected File"
        }
    }

    val lineCount = remember(csvText) {
        if (csvText.isBlank()) 0 else csvText.lineSequence().count { it.isNotBlank() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Choose a CSV file from your device storage, or paste CSV text manually below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { openFilePicker() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RadiusMedium
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_file_download),
                        contentDescription = "Pick File",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedFileName != null) "Change File ($selectedFileName)" else "Select CSV File from Device",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (selectedFileName != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = AccentEmerald.copy(alpha = 0.1f)
                        ),
                        shape = RadiusSmall,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Loaded $selectedFileName ($lineCount rows)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentEmerald,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Text(
                    text = "CSV Preview / Manual Paste:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = csvText,
                    onValueChange = {
                        csvText = it
                        selectedFileName = null
                    },
                    placeholder = { Text("Amount,Type,Currency,Merchant,Notes...\n150,DEBIT,INR,Swiggy,Lunch", style = CashBuddyTypography.bodySmall) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RadiusMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (csvText.isNotBlank()) {
                        onImport(csvText)
                    }
                },
                enabled = csvText.isNotBlank() && !isImporting
            ) {
                Text(if (isImporting) "Importing..." else "Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
