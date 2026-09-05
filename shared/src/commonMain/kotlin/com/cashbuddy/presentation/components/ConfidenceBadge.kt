package com.cashbuddy.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.presentation.theme.ConfidenceHigh
import com.cashbuddy.presentation.theme.ConfidenceLow
import com.cashbuddy.presentation.theme.ConfidenceMedium

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.WarningAmber

@Composable
fun ConfidenceBadge(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val (badgeColor, label) = when {
        confidence >= 0.85f -> AccentEmerald to "${(confidence * 100).toInt()}% Match"
        confidence >= 0.50f -> WarningAmber to "${(confidence * 100).toInt()}% Needs Review"
        else -> DangerRed to "${(confidence * 100).toInt()}% Low Confidence"
    }

    Box(
        modifier = modifier
            .background(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = badgeColor,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
        )
    }
}

@Composable
fun StatusBadge(
    status: TransactionStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, text) = when (status) {
        TransactionStatus.PENDING -> Triple(WarningAmber.copy(alpha = 0.15f), WarningAmber, "Review")
        TransactionStatus.CONFIRMED -> Triple(AccentEmerald.copy(alpha = 0.15f), AccentEmerald, "Confirmed")
        TransactionStatus.REJECTED -> Triple(DangerRed.copy(alpha = 0.15f), DangerRed, "Rejected")
        TransactionStatus.MODIFIED -> Triple(PrimaryIndigo.copy(alpha = 0.15f), PrimaryIndigo, "Modified")
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = CashBuddyTypography.labelMedium,
            color = textColor
        )
    }
}

@Composable
fun ConfidenceIndicator(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val color = when {
        confidence >= 0.85f -> AccentEmerald
        confidence >= 0.60f -> WarningAmber
        else -> DangerRed
    }

    val label = when {
        confidence >= 0.85f -> "High"
        confidence >= 0.60f -> "Medium"
        else -> "Low"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val filled = (confidence * 5).toInt() > index
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(12.dp)
                    .padding(end = 2.dp)
                    .background(
                        color = if (filled) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = "$label (${(confidence * 100).toInt()}%)",
            style = CashBuddyTypography.labelSmall,
            color = color
        )
    }
}
