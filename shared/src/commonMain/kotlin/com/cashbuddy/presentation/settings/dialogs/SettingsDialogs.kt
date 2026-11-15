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
import cashbuddy.shared.generated.resources.action_cancel
import cashbuddy.shared.generated.resources.settings_clear_all_dialog_title
import cashbuddy.shared.generated.resources.settings_clear_all_dialog_msg
import cashbuddy.shared.generated.resources.settings_clear_all_confirm
import cashbuddy.shared.generated.resources.settings_clear_training_dialog_title
import cashbuddy.shared.generated.resources.settings_clear_training_dialog_msg
import cashbuddy.shared.generated.resources.settings_clear_training_confirm
import cashbuddy.shared.generated.resources.settings_ignored_apps_title
import cashbuddy.shared.generated.resources.settings_ignored_apps_desc
import cashbuddy.shared.generated.resources.settings_ignored_apps_empty
import cashbuddy.shared.generated.resources.settings_action_add
import cashbuddy.shared.generated.resources.settings_action_remove
import cashbuddy.shared.generated.resources.settings_import_csv_title
import cashbuddy.shared.generated.resources.settings_import_csv_desc
import cashbuddy.shared.generated.resources.settings_select_csv_file
import cashbuddy.shared.generated.resources.settings_change_file
import cashbuddy.shared.generated.resources.settings_loaded_csv_rows
import cashbuddy.shared.generated.resources.settings_csv_preview_label
import cashbuddy.shared.generated.resources.settings_action_import
import cashbuddy.shared.generated.resources.settings_action_importing
import org.jetbrains.compose.resources.stringResource
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
        title = { Text(stringResource(Res.string.settings_clear_all_dialog_title)) },
        text = { Text(stringResource(Res.string.settings_clear_all_dialog_msg)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ExpenseCrimson)
            ) {
                Text(stringResource(Res.string.settings_clear_all_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
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
        title = { Text(stringResource(Res.string.settings_clear_training_dialog_title)) },
        text = { Text(stringResource(Res.string.settings_clear_training_dialog_msg)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
            ) {
                Text(stringResource(Res.string.settings_clear_training_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
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
                text = stringResource(Res.string.settings_ignored_apps_title),
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
                    text = stringResource(Res.string.settings_ignored_apps_desc),
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
                        Text(stringResource(Res.string.settings_action_add))
                    }
                }

                if (ignoredPackages.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.settings_ignored_apps_empty),
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
                                    Text(stringResource(Res.string.settings_action_remove), color = DangerRed, style = CashBuddyTypography.labelSmall)
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
                text = stringResource(Res.string.settings_import_csv_title),
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
                    text = stringResource(Res.string.settings_import_csv_desc),
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
                        text = if (selectedFileName != null) {
                            stringResource(Res.string.settings_change_file, selectedFileName!!)
                        } else {
                            stringResource(Res.string.settings_select_csv_file)
                        },
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
                            text = stringResource(Res.string.settings_loaded_csv_rows, selectedFileName!!, lineCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentEmerald,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(Res.string.settings_csv_preview_label),
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
                Text(
                    if (isImporting) stringResource(Res.string.settings_action_importing)
                    else stringResource(Res.string.settings_action_import)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}
