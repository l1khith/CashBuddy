package com.cashbuddy.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.diagnostics_action_fix
import cashbuddy.shared.generated.resources.diagnostics_last_sync_never
import cashbuddy.shared.generated.resources.diagnostics_last_sync_title
import cashbuddy.shared.generated.resources.diagnostics_notif_access_desc
import cashbuddy.shared.generated.resources.diagnostics_notif_access_disabled
import cashbuddy.shared.generated.resources.diagnostics_notif_access_enabled
import cashbuddy.shared.generated.resources.diagnostics_notif_access_title
import cashbuddy.shared.generated.resources.diagnostics_sheet_all_healthy
import cashbuddy.shared.generated.resources.diagnostics_sheet_attention_needed
import cashbuddy.shared.generated.resources.diagnostics_sheet_open_settings
import cashbuddy.shared.generated.resources.diagnostics_sheet_title
import cashbuddy.shared.generated.resources.ic_check_circle
import cashbuddy.shared.generated.resources.ic_notifications_active
import cashbuddy.shared.generated.resources.ic_schedule
import cashbuddy.shared.generated.resources.ic_shield
import cashbuddy.shared.generated.resources.ic_warning
import cashbuddy.shared.generated.resources.time_days_ago
import cashbuddy.shared.generated.resources.time_hours_ago
import cashbuddy.shared.generated.resources.time_just_now
import cashbuddy.shared.generated.resources.time_minutes_ago
import com.cashbuddy.domain.model.SystemDiagnostics
import com.cashbuddy.platform.currentTimeMillis
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.WarningAmber
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticBottomSheet(
    sheetState: SheetState,
    diagnostics: SystemDiagnostics,
    onDismissRequest: () -> Unit,
    onFixNotificationAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface, // Solid surface (prevents text bleed)
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Title + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (diagnostics.isFullyHealthy) AccentEmerald.copy(alpha = 0.15f)
                                else DangerRed.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                if (diagnostics.isFullyHealthy) Res.drawable.ic_shield
                                else Res.drawable.ic_warning
                            ),
                            contentDescription = null,
                            tint = if (diagnostics.isFullyHealthy) AccentEmerald else DangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = stringResource(Res.string.diagnostics_sheet_title),
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Overall Status Pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (diagnostics.isFullyHealthy) AccentEmerald.copy(alpha = 0.12f)
                            else WarningAmber.copy(alpha = 0.15f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (diagnostics.isFullyHealthy) AccentEmerald.copy(alpha = 0.35f) else WarningAmber.copy(alpha = 0.45f),
                            shape = CircleShape
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (diagnostics.isFullyHealthy) Res.string.diagnostics_sheet_all_healthy
                            else Res.string.diagnostics_sheet_attention_needed
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (diagnostics.isFullyHealthy) AccentEmerald else WarningAmber
                    )
                }
            }

            // Card 1: Notification Access Status
            DiagnosticMetricCard(
                icon = Res.drawable.ic_notifications_active,
                title = stringResource(Res.string.diagnostics_notif_access_title),
                subtitle = stringResource(Res.string.diagnostics_notif_access_desc),
                statusLabel = stringResource(
                    if (diagnostics.isNotificationAccessGranted) Res.string.diagnostics_notif_access_enabled
                    else Res.string.diagnostics_notif_access_disabled
                ),
                isHealthy = diagnostics.isNotificationAccessGranted,
                actionText = if (!diagnostics.isNotificationAccessGranted) stringResource(Res.string.diagnostics_action_fix) else null,
                onActionClick = onFixNotificationAccess
            )

            // Card 2: Last Sync Timestamp
            val syncText = formatRelativeSyncTime(diagnostics.lastSyncTimestamp)
            DiagnosticMetricCard(
                icon = Res.drawable.ic_schedule,
                title = stringResource(Res.string.diagnostics_last_sync_title),
                subtitle = null,
                statusLabel = syncText,
                isHealthy = diagnostics.lastSyncTimestamp > 0L,
                actionText = null,
                onActionClick = {}
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action: Open Settings
            OutlinedButton(
                onClick = {
                    onDismissRequest()
                    onOpenSettings()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RadiusMedium
            ) {
                Text(
                    text = stringResource(Res.string.diagnostics_sheet_open_settings),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DiagnosticMetricCard(
    icon: org.jetbrains.compose.resources.DrawableResource,
    title: String,
    subtitle: String?,
    statusLabel: String,
    isHealthy: Boolean,
    actionText: String?,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RadiusLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                        .size(34.dp)
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
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = CashBuddyTypography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            painter = painterResource(
                                if (isHealthy) Res.drawable.ic_check_circle
                                else Res.drawable.ic_warning
                            ),
                            contentDescription = null,
                            tint = if (isHealthy) AccentEmerald else DangerRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
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
}

@Composable
private fun formatRelativeSyncTime(timestamp: Long): String {
    if (timestamp <= 0L) {
        return stringResource(Res.string.diagnostics_last_sync_never)
    }
    val now = currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0L)
    val minutes = diff / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> stringResource(Res.string.time_just_now)
        minutes < 60 -> stringResource(Res.string.time_minutes_ago, minutes.toInt())
        hours < 24 -> stringResource(Res.string.time_hours_ago, hours.toInt())
        else -> stringResource(Res.string.time_days_ago, days.toInt())
    }
}
