package com.cashbuddy.presentation.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.diagnostics_action_refresh
import cashbuddy.shared.generated.resources.diagnostics_last_sync_never
import cashbuddy.shared.generated.resources.diagnostics_last_sync_title
import cashbuddy.shared.generated.resources.ic_check_circle
import cashbuddy.shared.generated.resources.ic_refresh
import cashbuddy.shared.generated.resources.ic_schedule
import cashbuddy.shared.generated.resources.ic_warning
import cashbuddy.shared.generated.resources.time_days_ago
import cashbuddy.shared.generated.resources.time_hours_ago
import cashbuddy.shared.generated.resources.time_just_now
import cashbuddy.shared.generated.resources.time_minutes_ago
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.WarningAmber
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun DiagnosticStatusRow(
    icon: DrawableResource,
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
fun DiagnosticSyncRow(
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
