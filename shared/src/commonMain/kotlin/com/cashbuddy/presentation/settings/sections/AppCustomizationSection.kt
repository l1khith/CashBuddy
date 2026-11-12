package com.cashbuddy.presentation.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.ic_refresh
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
import com.cashbuddy.presentation.settings.components.ReorderableNavOrderList
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppCustomizationSection(
    customAppName: String,
    customAppSubtitle: String,
    showAppLogo: Boolean,
    navOrderList: List<String>,
    onAppNameChange: (String) -> Unit,
    onAppSubtitleChange: (String) -> Unit,
    onShowLogoChange: (Boolean) -> Unit,
    onNavOrderChange: (List<String>) -> Unit,
    onResetCustomization: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCustomized = remember(customAppName, customAppSubtitle, showAppLogo, navOrderList) {
        customAppName != UserSettings.DEFAULT_APP_NAME ||
            customAppSubtitle != UserSettings.DEFAULT_APP_SUBTITLE ||
            !showAppLogo ||
            navOrderList.joinToString(",") != UserSettings.DEFAULT_NAV_ORDER
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                onClick = onResetCustomization,
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RadiusLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                var appNameInput by rememberSaveable { mutableStateOf(customAppName) }
                var appSubtitleInput by rememberSaveable { mutableStateOf(customAppSubtitle) }

                LaunchedEffect(customAppName) {
                    if (appNameInput != customAppName) {
                        appNameInput = customAppName
                    }
                }
                LaunchedEffect(customAppSubtitle) {
                    if (appSubtitleInput != customAppSubtitle) {
                        appSubtitleInput = customAppSubtitle
                    }
                }

                OutlinedTextField(
                    value = appNameInput,
                    onValueChange = {
                        val limited = it.take(UserSettings.MAX_APP_NAME_LENGTH)
                        appNameInput = limited
                        onAppNameChange(limited)
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
                        onAppSubtitleChange(limited)
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
                        checked = showAppLogo,
                        onCheckedChange = onShowLogoChange
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
                        navItems = navOrderList,
                        onOrderChanged = onNavOrderChange
                    )
                }
            }
        }
    }
}
