package com.cashbuddy.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.app_name
import cashbuddy.shared.generated.resources.app_subtitle
import cashbuddy.shared.generated.resources.cd_app_icon
import cashbuddy.shared.generated.resources.cd_health_shield_healthy
import cashbuddy.shared.generated.resources.cd_health_shield_warning
import cashbuddy.shared.generated.resources.cd_settings
import cashbuddy.shared.generated.resources.ic_cashbuddy_logo
import cashbuddy.shared.generated.resources.ic_settings
import cashbuddy.shared.generated.resources.ic_shield
import cashbuddy.shared.generated.resources.ic_warning
import com.cashbuddy.domain.model.UserSettings
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.DangerRed
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashBuddyTopBar(
    appName: String = UserSettings.DEFAULT_APP_NAME,
    appSubtitle: String = UserSettings.DEFAULT_APP_SUBTITLE,
    showLogo: Boolean = true,
    showHealthShield: Boolean = false,
    isHealthShieldHealthy: Boolean = true,
    onHealthShieldClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resolvedTitle = appName.ifBlank { stringResource(Res.string.app_name) }
    val resolvedSubtitle = appSubtitle.ifBlank { stringResource(Res.string.app_subtitle) }

    TopAppBar(
        modifier = modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = showLogo,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(Res.drawable.ic_cashbuddy_logo),
                            contentDescription = stringResource(Res.string.cd_app_icon),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                }
                Column {
                    Text(
                        text = resolvedTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = resolvedSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            if (showHealthShield) {
                IconButton(onClick = onHealthShieldClick) {
                    Icon(
                        painter = painterResource(
                            if (isHealthShieldHealthy) Res.drawable.ic_shield else Res.drawable.ic_warning
                        ),
                        contentDescription = stringResource(
                            if (isHealthShieldHealthy) Res.string.cd_health_shield_healthy
                            else Res.string.cd_health_shield_warning
                        ),
                        tint = if (isHealthShieldHealthy) AccentEmerald else DangerRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    painter = painterResource(Res.drawable.ic_settings),
                    contentDescription = stringResource(Res.string.cd_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
