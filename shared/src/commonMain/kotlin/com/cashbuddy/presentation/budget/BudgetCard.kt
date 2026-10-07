package com.cashbuddy.presentation.budget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.BudgetStatus
import com.cashbuddy.presentation.components.formatCurrency
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusSmall
import com.cashbuddy.presentation.theme.WarningAmber
import com.cashbuddy.presentation.theme.getCategoryColor
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BudgetCard(
    status: BudgetStatus,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val budget = status.budget
    val percentInt = status.percentUsed.toInt()
    val progress = (status.percentUsed / 100f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
        label = "budget_progress"
    )

    val (barColor, statusIcon, statusText) = when {
        status.percentUsed >= 100f || status.state == AlertState.EXCEEDED ->
            Triple(DangerRed, Icons.Default.Warning, "Exceeded by ${formatCurrency(abs(status.remaining), includeSymbol = true)}")
        status.percentUsed >= 80f || status.state == AlertState.WARNING ->
            Triple(WarningAmber, Icons.Default.Warning, "Approaching limit ($percentInt% used)")
        else ->
            Triple(AccentEmerald, Icons.Default.CheckCircle, "${formatCurrency(status.remaining, includeSymbol = true)} remaining ($percentInt% used)")
    }

    val categoryColor = getCategoryColor(budget.category)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RadiusLarge)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics {
                contentDescription = "${budget.category} budget: ${formatCurrency(status.spent, includeSymbol = true)} of ${formatCurrency(budget.amount, includeSymbol = true)} spent. $statusText"
            },
        shape = RadiusLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(categoryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = budget.category,
                        style = CashBuddyTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "${formatCurrency(status.spent, includeSymbol = true)} / ${formatCurrency(budget.amount, includeSymbol = true)}",
                    style = CashBuddyTypography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RadiusSmall),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = barColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = statusText,
                    style = CashBuddyTypography.bodySmall,
                    color = barColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
