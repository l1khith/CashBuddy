package com.cashbuddy.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import cashbuddy.shared.generated.resources.cd_settings
import cashbuddy.shared.generated.resources.ic_cashbuddy_logo
import com.cashbuddy.domain.model.UserSettings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashBuddyTopBar(
    appName: String = UserSettings.DEFAULT_APP_NAME,
    appSubtitle: String = UserSettings.DEFAULT_APP_SUBTITLE,
    showLogo: Boolean = true,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resolvedTitle = appName.ifBlank { stringResource(Res.string.app_name) }
    val resolvedSubtitle = appSubtitle.ifBlank { stringResource(Res.string.app_subtitle) }

    TopAppBar(
        modifier = modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showLogo) {
                    Image(
                        painter = painterResource(Res.drawable.ic_cashbuddy_logo),
                        contentDescription = stringResource(Res.string.cd_app_icon),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(Res.string.cd_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
