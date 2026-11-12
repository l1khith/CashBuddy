package com.cashbuddy.presentation.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.diagnostics_action_fix
import cashbuddy.shared.generated.resources.diagnostics_battery_desc
import cashbuddy.shared.generated.resources.diagnostics_battery_restricted
import cashbuddy.shared.generated.resources.diagnostics_battery_title
import cashbuddy.shared.generated.resources.diagnostics_battery_unrestricted
import cashbuddy.shared.generated.resources.diagnostics_health_icon_toggle_desc
import cashbuddy.shared.generated.resources.diagnostics_health_icon_toggle_title
import cashbuddy.shared.generated.resources.diagnostics_notif_access_desc
import cashbuddy.shared.generated.resources.diagnostics_notif_access_disabled
import cashbuddy.shared.generated.resources.diagnostics_notif_access_enabled
import cashbuddy.shared.generated.resources.diagnostics_notif_access_title
import cashbuddy.shared.generated.resources.diagnostics_section_title
import cashbuddy.shared.generated.resources.ic_battery_alert
import cashbuddy.shared.generated.resources.ic_notifications_active
import com.cashbuddy.presentation.settings.components.DailyWrapUpTimePickerRow
import com.cashbuddy.presentation.settings.components.DiagnosticStatusRow
import com.cashbuddy.presentation.settings.components.DiagnosticSyncRow
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.RadiusLarge
import org.jetbrains.compose.resources.stringResource

@Composable
fun DiagnosticsSection(
    showHealthInTopBar: Boolean,
    isNotificationAccessGranted: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    lastSyncTimestamp: Long,
    dailyWrapUpHour: Int,
    dailyWrapUpMinute: Int,
    onShowHealthInTopBarChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onDailyWrapUpTimeSelected: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.diagnostics_section_title),
            style = CashBuddyTypography.titleLarge,
            fontWeight = FontWeight.Bold
        )

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
                        checked = showHealthInTopBar,
                        onCheckedChange = onShowHealthInTopBarChange
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Status Card 1: Notification Access
                DiagnosticStatusRow(
                    icon = Res.drawable.ic_notifications_active,
                    title = stringResource(Res.string.diagnostics_notif_access_title),
                    subtitle = stringResource(Res.string.diagnostics_notif_access_desc),
                    statusLabel = stringResource(
                        if (isNotificationAccessGranted) Res.string.diagnostics_notif_access_enabled
                        else Res.string.diagnostics_notif_access_disabled
                    ),
                    isHealthy = isNotificationAccessGranted,
                    actionText = if (!isNotificationAccessGranted) stringResource(Res.string.diagnostics_action_fix) else null,
                    onActionClick = onOpenNotificationSettings
                )

                // Status Card 2: Battery Saver
                DiagnosticStatusRow(
                    icon = Res.drawable.ic_battery_alert,
                    title = stringResource(Res.string.diagnostics_battery_title),
                    subtitle = stringResource(Res.string.diagnostics_battery_desc),
                    statusLabel = stringResource(
                        if (isBatteryOptimizationIgnored) Res.string.diagnostics_battery_unrestricted
                        else Res.string.diagnostics_battery_restricted
                    ),
                    isHealthy = isBatteryOptimizationIgnored,
                    actionText = if (!isBatteryOptimizationIgnored) stringResource(Res.string.diagnostics_action_fix) else null,
                    onActionClick = onOpenBatterySettings
                )

                // Status Card 3: Last Sync Time
                DiagnosticSyncRow(
                    lastSyncTimestamp = lastSyncTimestamp,
                    onRefresh = onRefreshDiagnostics
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Daily Wrap-Up Reminder Time Picker Row
                DailyWrapUpTimePickerRow(
                    hour = dailyWrapUpHour,
                    minute = dailyWrapUpMinute,
                    onTimeSelected = onDailyWrapUpTimeSelected
                )
            }
        }
    }
}
