package com.cashbuddy.presentation.transactions.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.tx_list_total_count
import cashbuddy.shared.generated.resources.tx_list_total_received
import cashbuddy.shared.generated.resources.tx_list_total_spent
import com.cashbuddy.presentation.components.formatCurrency
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import org.jetbrains.compose.resources.stringResource

@Composable
fun TransactionSummaryBanner(
    totalCount: Int,
    totalDebit: Double,
    totalCredit: Double,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.tx_list_total_count, totalCount),
                style = CashBuddyTypography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (totalDebit > 0) {
                    Text(
                        text = stringResource(Res.string.tx_list_total_spent, formatCurrency(totalDebit)),
                        style = CashBuddyTypography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DangerRed
                    )
                }
                if (totalCredit > 0) {
                    Text(
                        text = stringResource(Res.string.tx_list_total_received, formatCurrency(totalCredit)),
                        style = CashBuddyTypography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentEmerald
                    )
                }
            }
        }
    }
}
