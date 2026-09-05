package com.cashbuddy.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.presentation.theme.CashBuddyEasing
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.getCategoryColor
import kotlin.math.PI
import kotlin.math.atan2

@Composable
fun CategoryPieChart(
    breakdowns: List<CategoryBreakdown>,
    modifier: Modifier = Modifier,
    centerTitle: String = "Total Spent"
) {
    if (breakdowns.isEmpty()) return

    val total = breakdowns.sumOf { it.totalAmount }.coerceAtLeast(1.0)
    var selectedSliceIndex by remember { mutableIntStateOf(-1) }
    var animationPlayed by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = CashBuddyEasing.EaseInOutQuart),
        label = "pie_animation"
    )

    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .pointerInput(breakdowns) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                        val radius = kotlin.math.min(size.width, size.height) / 2f

                        // Check if tap is within donut ring
                        if (distance >= radius * 0.55f && distance <= radius) {
                            var angle = (atan2(dy, dx) * 180 / PI).toFloat()
                            if (angle < 0) angle += 360f

                            // Offset so 12 o'clock (-90 deg) is 0 deg
                            val normalizedAngle = (angle + 90f) % 360f

                            var cumulativeAngle = 0f
                            var clickedIndex = -1
                            for (i in breakdowns.indices) {
                                val sweep = (breakdowns[i].totalAmount / total * 360f).toFloat()
                                if (normalizedAngle in cumulativeAngle..(cumulativeAngle + sweep)) {
                                    clickedIndex = i
                                    break
                                }
                                cumulativeAngle += sweep
                            }

                            selectedSliceIndex = if (selectedSliceIndex == clickedIndex) -1 else clickedIndex
                        } else {
                            selectedSliceIndex = -1
                        }
                    }
                }
        ) {
            val radius = size.minDimension / 2f
            val arcSize = Size(radius * 2, radius * 2)
            val topLeft = Offset((size.width - radius * 2) / 2, (size.height - radius * 2) / 2)
            var startAngle = -90f

            breakdowns.forEachIndexed { index, item ->
                val fullSweep = (item.totalAmount / total * 360f).toFloat()
                val sweep = fullSweep * animatedProgress
                val isSelected = index == selectedSliceIndex

                val sliceColor = getCategoryColor(item.categoryName)

                if (sweep > 0f) {
                    // Draw slice
                    drawArc(
                        color = sliceColor,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                        alpha = if (selectedSliceIndex == -1 || isSelected) 1f else 0.45f
                    )

                    // Draw 2-degree separator gap
                    if (breakdowns.size > 1) {
                        drawArc(
                            color = backgroundColor,
                            startAngle = startAngle,
                            sweepAngle = 2f,
                            useCenter = true,
                            topLeft = topLeft,
                            size = arcSize
                        )
                    }
                }

                startAngle += fullSweep
            }

            // Draw donut center hole (55% of radius)
            drawCircle(
                color = backgroundColor,
                radius = radius * 0.55f,
                center = center
            )
        }

        // Center Text Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedSliceIndex in breakdowns.indices) {
                val selectedItem = breakdowns[selectedSliceIndex]
                Text(
                    text = selectedItem.categoryName,
                    style = CashBuddyTypography.labelMedium,
                    color = getCategoryColor(selectedItem.categoryName),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = "₹${formatCurrency(selectedItem.totalAmount)}",
                    style = CashBuddyTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${((selectedItem.totalAmount / total) * 100).toInt()}%",
                    style = CashBuddyTypography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = centerTitle,
                    style = CashBuddyTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "₹${formatCurrency(total)}",
                    style = CashBuddyTypography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
