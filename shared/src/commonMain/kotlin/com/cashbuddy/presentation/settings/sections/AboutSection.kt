package com.cashbuddy.presentation.settings.sections

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.ic_cashbuddy_logo
import cashbuddy.shared.generated.resources.settings_section_legal
import cashbuddy.shared.generated.resources.settings_legal_desc
import cashbuddy.shared.generated.resources.settings_privacy_policy_btn
import cashbuddy.shared.generated.resources.settings_terms_btn
import cashbuddy.shared.generated.resources.settings_about_website_btn
import cashbuddy.shared.generated.resources.settings_github_repo_btn
import cashbuddy.shared.generated.resources.settings_debug_active_badge
import com.cashbuddy.platform.getAppVersionDisplay
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusLarge
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutSection(
    isDebugLogEnabled: Boolean,
    onVersionClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.settings_section_legal),
            style = CashBuddyTypography.titleLarge,
            fontWeight = FontWeight.Bold
        )

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
                    text = stringResource(Res.string.settings_legal_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { uriHandler.openUri("https://l1khith.github.io/CashBuddy/#2-privacy-policy") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(Res.string.settings_privacy_policy_btn))
                }

                OutlinedButton(
                    onClick = { uriHandler.openUri("https://l1khith.github.io/CashBuddy/#3-terms-of-service") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(Res.string.settings_terms_btn))
                }

                OutlinedButton(
                    onClick = { uriHandler.openUri("https://l1khith.github.io/CashBuddy/") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(Res.string.settings_about_website_btn))
                }

                OutlinedButton(
                    onClick = { uriHandler.openUri("https://github.com/l1khith/CashBuddy") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(Res.string.settings_github_repo_btn))
                }
            }
        }

        // Version Footer
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
                    .clickable { onVersionClicked() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
            if (isDebugLogEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.settings_debug_active_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryIndigo,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
