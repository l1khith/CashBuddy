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

@Composable
fun ConfidenceBadge(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val (badgeColor, label) = when {
        confidence >= 0.85f -> ConfidenceHigh to "${(confidence * 100).toInt()}% Match"
        confidence >= 0.50f -> ConfidenceMedium to "${(confidence * 100).toInt()}% Needs Review"
        else -> ConfidenceLow to "${(confidence * 100).toInt()}% Low Confidence"
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
