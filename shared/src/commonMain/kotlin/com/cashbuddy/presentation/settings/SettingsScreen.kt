package com.cashbuddy.presentation.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Undo
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.cd_back
import cashbuddy.shared.generated.resources.diagnostics_action_refresh
import cashbuddy.shared.generated.resources.ic_bug_report
import cashbuddy.shared.generated.resources.ic_cashbuddy_logo
import cashbuddy.shared.generated.resources.ic_delete_forever
import cashbuddy.shared.generated.resources.ic_download
import cashbuddy.shared.generated.resources.ic_file_download
import cashbuddy.shared.generated.resources.ic_home
import cashbuddy.shared.generated.resources.ic_monitoring
import cashbuddy.shared.generated.resources.ic_receipt_long
import cashbuddy.shared.generated.resources.ic_tune
import cashbuddy.shared.generated.resources.ic_visibility
import cashbuddy.shared.generated.resources.ic_warning
import cashbuddy.shared.generated.resources.nav_history
import cashbuddy.shared.generated.resources.nav_home
import cashbuddy.shared.generated.resources.nav_stats
import cashbuddy.shared.generated.resources.settings_app_name_label
import cashbuddy.shared.generated.resources.settings_app_subtitle_label
import cashbuddy.shared.generated.resources.settings_char_count
import cashbuddy.shared.generated.resources.settings_nav_order_hint
import cashbuddy.shared.generated.resources.settings_nav_order_label
import cashbuddy.shared.generated.resources.settings_reset_customization
import cashbuddy.shared.generated.resources.settings_section_app_customization
import cashbuddy.shared.generated.resources.settings_show_app_logo
import cashbuddy.shared.generated.resources.settings_show_app_logo_desc
import com.cashbuddy.domain.model.UserSettings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.cashbuddy.presentation.settings.components.DuplicateReconciliationDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import cashbuddy.shared.generated.resources.action_cancel
import cashbuddy.shared.generated.resources.action_save
import cashbuddy.shared.generated.resources.diagnostics_action_fix
import cashbuddy.shared.generated.resources.diagnostics_battery_desc
import cashbuddy.shared.generated.resources.diagnostics_battery_restricted
import cashbuddy.shared.generated.resources.diagnostics_battery_title
import cashbuddy.shared.generated.resources.diagnostics_battery_unrestricted
import cashbuddy.shared.generated.resources.diagnostics_health_icon_toggle_desc
import cashbuddy.shared.generated.resources.diagnostics_health_icon_toggle_title
import cashbuddy.shared.generated.resources.diagnostics_last_sync_never
import cashbuddy.shared.generated.resources.diagnostics_last_sync_title
import cashbuddy.shared.generated.resources.diagnostics_notif_access_desc
import cashbuddy.shared.generated.resources.diagnostics_notif_access_disabled
import cashbuddy.shared.generated.resources.diagnostics_notif_access_enabled
import cashbuddy.shared.generated.resources.diagnostics_notif_access_title
import cashbuddy.shared.generated.resources.diagnostics_section_title
import cashbuddy.shared.generated.resources.ic_battery_alert
import cashbuddy.shared.generated.resources.ic_check_circle
import cashbuddy.shared.generated.resources.ic_notifications_active
import cashbuddy.shared.generated.resources.ic_refresh
import cashbuddy.shared.generated.resources.ic_schedule
import cashbuddy.shared.generated.resources.time_days_ago
import cashbuddy.shared.generated.resources.time_hours_ago
import cashbuddy.shared.generated.resources.time_just_now
import cashbuddy.shared.generated.resources.time_minutes_ago
import cashbuddy.shared.generated.resources.wrapup_desc
import cashbuddy.shared.generated.resources.wrapup_time_picker_title
import cashbuddy.shared.generated.resources.wrapup_title
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cashbuddy.presentation.theme.ExpenseCrimson
import com.cashbuddy.presentation.theme.TealMintSecondary
import com.cashbuddy.presentation.theme.TrustBluePrimary
import kotlinx.coroutines.flow.collectLatest

import androidx.compose.ui.platform.LocalUriHandler
import com.cashbuddy.platform.getAppVersionDisplay
import com.cashbuddy.platform.rememberFilePicker
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.RadiusSmall
import com.cashbuddy.presentation.theme.WarningAmber

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
    val uriHandler = LocalUriHandler.current
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
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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



                // App Customization
                item {
                    val isCustomized = state.customAppName != UserSettings.DEFAULT_APP_NAME ||
                        state.customAppSubtitle != UserSettings.DEFAULT_APP_SUBTITLE ||
                        !state.showAppLogo ||
                        state.navOrder != UserSettings.DEFAULT_NAV_ORDER

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_section_app_customization),
                            style = CashBuddyTypography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { viewModel.resetAppCustomization() },
                            enabled = isCustomized
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_refresh),
                                contentDescription = stringResource(Res.string.settings_reset_customization),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(Res.string.settings_reset_customization))
                        }
                    }
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
                            var appNameInput by rememberSaveable { mutableStateOf(state.customAppName) }
                            var appSubtitleInput by rememberSaveable { mutableStateOf(state.customAppSubtitle) }

                            LaunchedEffect(state.customAppName) {
                                if (appNameInput != state.customAppName) {
                                    appNameInput = state.customAppName
                                }
                            }
                            LaunchedEffect(state.customAppSubtitle) {
                                if (appSubtitleInput != state.customAppSubtitle) {
                                    appSubtitleInput = state.customAppSubtitle
                                }
                            }

                            OutlinedTextField(
                                value = appNameInput,
                                onValueChange = {
                                    val limited = it.take(UserSettings.MAX_APP_NAME_LENGTH)
                                    appNameInput = limited
                                    viewModel.setCustomAppName(limited)
                                },
                                label = { Text(stringResource(Res.string.settings_app_name_label)) },
                                supportingText = {
                                    Text(
                                        text = stringResource(
                                            Res.string.settings_char_count,
                                            appNameInput.length,
                                            UserSettings.MAX_APP_NAME_LENGTH
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RadiusMedium
                            )

                            OutlinedTextField(
                                value = appSubtitleInput,
                                onValueChange = {
                                    val limited = it.take(UserSettings.MAX_APP_SUBTITLE_LENGTH)
                                    appSubtitleInput = limited
                                    viewModel.setCustomAppSubtitle(limited)
                                },
                                label = { Text(stringResource(Res.string.settings_app_subtitle_label)) },
                                supportingText = {
                                    Text(
                                        text = stringResource(
                                            Res.string.settings_char_count,
                                            appSubtitleInput.length,
                                            UserSettings.MAX_APP_SUBTITLE_LENGTH
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RadiusMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(Res.string.settings_show_app_logo),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(Res.string.settings_show_app_logo_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.showAppLogo,
                                    onCheckedChange = { viewModel.setShowAppLogo(it) }
                                )
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(Res.string.settings_nav_order_label),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(Res.string.settings_nav_order_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ReorderableNavOrderList(
                                    navItems = state.navOrderList,
                                    onOrderChanged = { newOrder -> viewModel.commitNavOrder(newOrder) }
                                )
                            }
                        }
                    }
                }

                // System Diagnostics & Reminders
                item {
                    Text(
                        text = stringResource(Res.string.diagnostics_section_title),
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
                            // Top Bar Health Icon Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(Res.string.diagnostics_health_icon_toggle_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(Res.string.diagnostics_health_icon_toggle_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.showHealthInTopBar,
                                    onCheckedChange = { viewModel.setShowHealthInTopBar(it) }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Status Card 1: Notification Access
                            DiagnosticStatusRow(
                                icon = Res.drawable.ic_notifications_active,
                                title = stringResource(Res.string.diagnostics_notif_access_title),
                                subtitle = stringResource(Res.string.diagnostics_notif_access_desc),
                                statusLabel = stringResource(
                                    if (state.isNotificationAccessGranted) Res.string.diagnostics_notif_access_enabled
                                    else Res.string.diagnostics_notif_access_disabled
                                ),
                                isHealthy = state.isNotificationAccessGranted,
                                actionText = if (!state.isNotificationAccessGranted) stringResource(Res.string.diagnostics_action_fix) else null,
                                onActionClick = { viewModel.openNotificationListenerSettings() }
                            )

                            // Status Card 2: Battery Saver
                            DiagnosticStatusRow(
                                icon = Res.drawable.ic_battery_alert,
                                title = stringResource(Res.string.diagnostics_battery_title),
                                subtitle = stringResource(Res.string.diagnostics_battery_desc),
                                statusLabel = stringResource(
                                    if (state.isBatteryOptimizationIgnored) Res.string.diagnostics_battery_unrestricted
                                    else Res.string.diagnostics_battery_restricted
                                ),
                                isHealthy = state.isBatteryOptimizationIgnored,
                                actionText = if (!state.isBatteryOptimizationIgnored) stringResource(Res.string.diagnostics_action_fix) else null,
                                onActionClick = { viewModel.openBatteryOptimizationSettings() }
                            )

                            // Status Card 3: Last Sync Time
                            DiagnosticSyncRow(
                                lastSyncTimestamp = state.lastSyncTimestamp,
                                onRefresh = { viewModel.refreshDiagnostics() }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Daily Wrap-Up Reminder Time Picker Row
                            DailyWrapUpTimePickerRow(
                                hour = state.dailyWrapUpHour,
                                minute = state.dailyWrapUpMinute,
                                onTimeSelected = { h, m -> viewModel.setDailyWrapUpTime(h, m) }
                            )
                        }
                    }
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
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.onVersionClicked() }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
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
        var newPkgText by rememberSaveable { mutableStateOf("") }
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
        var csvText by rememberSaveable { mutableStateOf("") }
        var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }

        val openFilePicker = rememberFilePicker(mimeType = "text/csv") { content, fileName ->
            if (content != null) {
                csvText = content
                selectedFileName = fileName ?: "Selected File"
            }
        }

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
                        val lineCount = csvText.lineSequence().count { it.isNotBlank() }
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

@Composable
private fun ReorderableNavOrderList(
    navItems: List<String>,
    onOrderChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val spacingPx = LocalDensity.current.run { 8.dp.toPx() }

    // Maintain a local state list in UI memory.
    // The Database/ViewModel is completely untouched while the finger is dragging.
    val localItems = remember { mutableStateListOf<String>().also { it.addAll(navItems) } }

    LaunchedEffect(navItems) {
        if (localItems.toList() != navItems) {
            localItems.clear()
            localItems.addAll(navItems)
        }
    }

    var draggedKey by remember { mutableStateOf<String?>(null) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }
    var itemPitchPx by remember { mutableFloatStateOf(160f) }

    fun handleDragStart(key: String) {
        draggedKey = key
        draggingOffsetY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun handleDragDelta(key: String, deltaY: Float) {
        if (draggedKey != key || itemPitchPx <= 0f) return
        draggingOffsetY += deltaY

        val currentIdx = localItems.indexOf(key)
        if (currentIdx == -1) return

        val slotDelta = kotlin.math.round(draggingOffsetY / itemPitchPx).toInt()
        val targetIdx = (currentIdx + slotDelta).coerceIn(0, localItems.lastIndex)
        if (targetIdx != currentIdx) {
            draggingOffsetY -= (targetIdx - currentIdx) * itemPitchPx
            val item = localItems.removeAt(currentIdx)
            localItems.add(targetIdx, item)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun handleDragEnd() {
        val wasDragging = draggedKey != null
        draggedKey = null
        draggingOffsetY = 0f
        if (wasDragging) {
            // ONLY sync to Database/ViewModel when user drops the plate
            onOrderChanged(localItems.toList())
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        localItems.forEach { key ->
            key(key) {
                val isDragging = key == draggedKey
                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.04f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "navCardScale"
                )
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 8.dp else 0.dp,
                    label = "navCardElevation"
                )

                val iconRes = when (key) {
                    "home" -> Res.drawable.ic_home
                    "stats" -> Res.drawable.ic_monitoring
                    else -> Res.drawable.ic_receipt_long
                }
                val titleText = when (key) {
                    "home" -> stringResource(Res.string.nav_home)
                    "stats" -> stringResource(Res.string.nav_stats)
                    else -> stringResource(Res.string.nav_history)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (size.height > 0) {
                                itemPitchPx = size.height + spacingPx
                            }
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            // Only translate the single card currently being dragged by the finger.
                            // Sibling items are laid out cleanly in their Column slots without any stuck offsets!
                            translationY = if (isDragging) draggingOffsetY else 0f
                            scaleX = scale
                            scaleY = scale
                        }
                        .pointerInput(key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { handleDragStart(key) },
                                onDragEnd = { handleDragEnd() },
                                onDragCancel = { handleDragEnd() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    handleDragDelta(key, dragAmount.y)
                                }
                            )
                        },
                    shape = RadiusMedium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = elevation,
                    tonalElevation = if (isDragging) 4.dp else 0.dp,
                    border = if (isDragging) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    } else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = titleText,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        // Drag handle icon (visual affordance; card itself handles gesture cleanly)
                        Icon(
                            painter = painterResource(Res.drawable.ic_tune),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStatusRow(
    icon: org.jetbrains.compose.resources.DrawableResource,
    title: String,
    subtitle: String,
    statusLabel: String,
    isHealthy: Boolean,
    actionText: String?,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isHealthy) AccentEmerald.copy(alpha = 0.12f)
                        else WarningAmber.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (isHealthy) AccentEmerald else WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        painter = painterResource(
                            if (isHealthy) Res.drawable.ic_check_circle else Res.drawable.ic_warning
                        ),
                        contentDescription = null,
                        tint = if (isHealthy) AccentEmerald else DangerRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isHealthy) AccentEmerald else DangerRed
                )
            }
        }

        if (!actionText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onActionClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DiagnosticSyncRow(
    lastSyncTimestamp: Long,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val syncText = if (lastSyncTimestamp <= 0L) {
        stringResource(Res.string.diagnostics_last_sync_never)
    } else {
        val now = com.cashbuddy.platform.currentTimeMillis()
        val diff = (now - lastSyncTimestamp).coerceAtLeast(0L)
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24
        when {
            minutes < 1 -> stringResource(Res.string.time_just_now)
            minutes < 60 -> stringResource(Res.string.time_minutes_ago, minutes.toInt())
            hours < 24 -> stringResource(Res.string.time_hours_ago, hours.toInt())
            else -> stringResource(Res.string.time_days_ago, days.toInt())
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryIndigo.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_schedule),
                    contentDescription = null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = stringResource(Res.string.diagnostics_last_sync_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = syncText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        IconButton(onClick = onRefresh) {
            Icon(
                painter = painterResource(Res.drawable.ic_refresh),
                contentDescription = stringResource(Res.string.diagnostics_action_refresh),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DailyWrapUpTimePickerRow(
    hour: Int,
    minute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentEmerald.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_schedule),
                    contentDescription = null,
                    tint = AccentEmerald,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = stringResource(Res.string.wrapup_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(Res.string.wrapup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = { showDialog = true },
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = formatTime12Hour(hour, minute),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showDialog) {
        TimePickerSelectionDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showDialog = false },
            onConfirm = { h, m ->
                onTimeSelected(h, m)
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSelectionDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = androidx.compose.material3.rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.wrapup_time_picker_title),
                style = CashBuddyTypography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.TimePicker(
                    state = timePickerState
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) },
                shape = RadiusMedium
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

private fun formatTime12Hour(hour24: Int, minute: Int): String {
    val isPm = hour24 >= 12
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val minStr = if (minute < 10) "0$minute" else "$minute"
    val amPm = if (isPm) "PM" else "AM"
    return "$hour12:$minStr $amPm"
}
