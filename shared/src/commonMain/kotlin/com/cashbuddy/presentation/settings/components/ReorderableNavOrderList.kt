package com.cashbuddy.presentation.settings.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.ic_home
import cashbuddy.shared.generated.resources.ic_monitoring
import cashbuddy.shared.generated.resources.ic_receipt_long
import cashbuddy.shared.generated.resources.ic_tune
import cashbuddy.shared.generated.resources.nav_history
import cashbuddy.shared.generated.resources.nav_home
import cashbuddy.shared.generated.resources.nav_stats
import com.cashbuddy.presentation.theme.RadiusMedium
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ReorderableNavOrderList(
    navItems: List<String>,
    onOrderChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val spacingPx = LocalDensity.current.run { 8.dp.toPx() }

    val localItems = remember { mutableStateListOf<String>().also { it.addAll(navItems) } }

    LaunchedEffect(navItems) {
        if (localItems.toList() != navItems) {
            localItems.clear()
            localItems.addAll(navItems)
        }
    }

    var draggedKey by remember { mutableStateOf<String?>(null) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }
    var itemPitchPx by remember { mutableFloatStateOf(160f) }

    fun handleDragStart(key: String) {
        draggedKey = key
        draggingOffsetY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun handleDragDelta(key: String, deltaY: Float) {
        if (draggedKey != key || itemPitchPx <= 0f) return
        draggingOffsetY += deltaY

        val currentIdx = localItems.indexOf(key)
        if (currentIdx == -1) return

        val slotDelta = kotlin.math.round(draggingOffsetY / itemPitchPx).toInt()
        val targetIdx = (currentIdx + slotDelta).coerceIn(0, localItems.lastIndex)
        if (targetIdx != currentIdx) {
            draggingOffsetY -= (targetIdx - currentIdx) * itemPitchPx
            val item = localItems.removeAt(currentIdx)
            localItems.add(targetIdx, item)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun handleDragEnd() {
        val wasDragging = draggedKey != null
        draggedKey = null
        draggingOffsetY = 0f
        if (wasDragging) {
            onOrderChanged(localItems.toList())
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        localItems.forEach { key ->
            key(key) {
                val isDragging = key == draggedKey
                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.04f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "navCardScale"
                )
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 8.dp else 0.dp,
                    label = "navCardElevation"
                )

                val iconRes = when (key) {
                    "home" -> Res.drawable.ic_home
                    "stats" -> Res.drawable.ic_monitoring
                    else -> Res.drawable.ic_receipt_long
                }
                val titleText = when (key) {
                    "home" -> stringResource(Res.string.nav_home)
                    "stats" -> stringResource(Res.string.nav_stats)
                    else -> stringResource(Res.string.nav_history)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (size.height > 0) {
                                itemPitchPx = size.height + spacingPx
                            }
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) draggingOffsetY else 0f
                            scaleX = scale
                            scaleY = scale
                        }
                        .pointerInput(key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { handleDragStart(key) },
                                onDragEnd = { handleDragEnd() },
                                onDragCancel = { handleDragEnd() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    handleDragDelta(key, dragAmount.y)
                                }
                            )
                        },
                    shape = RadiusMedium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = elevation,
                    tonalElevation = if (isDragging) 4.dp else 0.dp,
                    border = if (isDragging) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    } else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = titleText,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painter = painterResource(Res.drawable.ic_tune),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
