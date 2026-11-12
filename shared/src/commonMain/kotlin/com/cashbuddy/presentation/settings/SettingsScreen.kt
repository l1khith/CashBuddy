package com.cashbuddy.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.cd_back
import cashbuddy.shared.generated.resources.ic_arrow_back
import com.cashbuddy.presentation.settings.components.DuplicateReconciliationDialog
import com.cashbuddy.presentation.settings.dialogs.ClearDataDialog
import com.cashbuddy.presentation.settings.dialogs.ClearTrainingDialog
import com.cashbuddy.presentation.settings.dialogs.IgnoredAppsDialog
import com.cashbuddy.presentation.settings.dialogs.ImportCsvDialog
import com.cashbuddy.presentation.settings.sections.AboutSection
import com.cashbuddy.presentation.settings.sections.AppCustomizationSection
import com.cashbuddy.presentation.settings.sections.AutomationSection
import com.cashbuddy.presentation.settings.sections.DataManagementSection
import com.cashbuddy.presentation.settings.sections.DeveloperSection
import com.cashbuddy.presentation.settings.sections.DiagnosticsSection
import com.cashbuddy.presentation.settings.sections.LearningSection
import com.cashbuddy.presentation.settings.sections.SecuritySection
import com.cashbuddy.presentation.theme.CashBuddyTypography
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToPersonalization: () -> Unit = {},
    onNavigateToDebugLog: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showClearTrainingDialog by rememberSaveable { mutableStateOf(false) }
    var showDuplicateDialog by rememberSaveable { mutableStateOf(false) }
    var showIgnoreDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }

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
                item(key = "header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_arrow_back),
                                    contentDescription = stringResource(Res.string.cd_back),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                        Text(
                            text = "Settings",
                            style = CashBuddyTypography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                item(key = "app_customization") {
                    AppCustomizationSection(
                        customAppName = state.customAppName,
                        customAppSubtitle = state.customAppSubtitle,
                        showAppLogo = state.showAppLogo,
                        navOrderList = state.navOrderList,
                        onAppNameChange = viewModel::setCustomAppName,
                        onAppSubtitleChange = viewModel::setCustomAppSubtitle,
                        onShowLogoChange = viewModel::setShowAppLogo,
                        onNavOrderChange = viewModel::commitNavOrder,
                        onResetCustomization = viewModel::resetAppCustomization
                    )
                }

                item(key = "diagnostics") {
                    DiagnosticsSection(
                        showHealthInTopBar = state.showHealthInTopBar,
                        isNotificationAccessGranted = state.isNotificationAccessGranted,
                        isBatteryOptimizationIgnored = state.isBatteryOptimizationIgnored,
                        lastSyncTimestamp = state.lastSyncTimestamp,
                        dailyWrapUpHour = state.dailyWrapUpHour,
                        dailyWrapUpMinute = state.dailyWrapUpMinute,
                        onShowHealthInTopBarChange = viewModel::setShowHealthInTopBar,
                        onOpenNotificationSettings = viewModel::openNotificationListenerSettings,
                        onOpenBatterySettings = viewModel::openBatteryOptimizationSettings,
                        onRefreshDiagnostics = viewModel::refreshDiagnostics,
                        onDailyWrapUpTimeSelected = viewModel::setDailyWrapUpTime
                    )
                }

                item(key = "automation") {
                    AutomationSection(
                        notificationEnabled = state.notificationEnabled,
                        smartNotificationsEnabled = state.smartNotificationsEnabled,
                        ignoredPackagesCount = state.ignoredPackages.size,
                        isOcrAvailable = state.isOcrAvailable,
                        onNotificationEnabledChange = viewModel::setNotificationEnabled,
                        onSmartNotificationsEnabledChange = viewModel::setSmartNotificationsEnabled,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onOpenIgnoreList = { showIgnoreDialog = true }
                    )
                }

                item(key = "security") {
                    SecuritySection(
                        biometricLockEnabled = state.biometricLockEnabled,
                        onBiometricLockChange = viewModel::setBiometricLockEnabled
                    )
                }

                item(key = "learning") {
                    LearningSection(
                        rawTrainingSamplesCount = state.rawTrainingSamplesCount,
                        userCorrectionsCount = state.userCorrectionsCount,
                        onNavigateToPersonalization = onNavigateToPersonalization,
                        onExportTrainingDataset = viewModel::exportTrainingDataset,
                        onResetTrainingData = { showClearTrainingDialog = true }
                    )
                }

                item(key = "data_management") {
                    DataManagementSection(
                        isScanningDuplicates = state.isScanningDuplicates,
                        hasRecentMerges = state.hasRecentMerges,
                        onExportCsv = { viewModel.exportCsvData {} },
                        onImportCsvClick = { showImportDialog = true },
                        onScanDuplicatesClick = {
                            viewModel.scanDuplicates { showDuplicateDialog = true }
                        },
                        onUndoMergeClick = viewModel::undoLastMerge,
                        onClearAllDataClick = { showClearDialog = true }
                    )
                }

                item(key = "developer") {
                    DeveloperSection(
                        isDebugLogEnabled = state.isDebugLogEnabled,
                        onDebugLogEnabledChange = viewModel::setDebugLogEnabled,
                        onNavigateToDebugLog = onNavigateToDebugLog
                    )
                }

                item(key = "about") {
                    AboutSection(
                        isDebugLogEnabled = state.isDebugLogEnabled,
                        onVersionClicked = viewModel::onVersionClicked
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        ClearDataDialog(
            onConfirm = {
                viewModel.clearAllData()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }

    if (showClearTrainingDialog) {
        ClearTrainingDialog(
            onConfirm = {
                viewModel.clearTrainingData()
                showClearTrainingDialog = false
            },
            onDismiss = { showClearTrainingDialog = false }
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
            onPreviewCsv = { viewModel.exportCsvData {} },
            onDismiss = { showDuplicateDialog = false }
        )
    }

    if (showIgnoreDialog) {
        IgnoredAppsDialog(
            ignoredPackages = state.ignoredPackages,
            onAddPackage = viewModel::addIgnoredPackage,
            onRemovePackage = viewModel::removeIgnoredPackage,
            onDismiss = { showIgnoreDialog = false }
        )
    }

    if (showImportDialog) {
        ImportCsvDialog(
            isImporting = state.isImportingCsv,
            onImport = { csvText ->
                viewModel.importCsvData(csvText)
                showImportDialog = false
            },
            onDismiss = { showImportDialog = false }
        )
    }
}
