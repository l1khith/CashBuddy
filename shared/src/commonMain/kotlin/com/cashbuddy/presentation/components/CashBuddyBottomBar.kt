package com.cashbuddy.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.action_add_transaction
import cashbuddy.shared.generated.resources.ic_home
import cashbuddy.shared.generated.resources.ic_monitoring
import cashbuddy.shared.generated.resources.ic_receipt_long
import cashbuddy.shared.generated.resources.nav_history
import cashbuddy.shared.generated.resources.nav_home
import cashbuddy.shared.generated.resources.nav_stats
import com.cashbuddy.domain.model.UserSettings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val DEFAULT_NAV_KEYS = listOf("home", "stats", "history")

@Composable
fun CashBuddyBottomBar(
    currentRoute: String,
    navOrder: String = UserSettings.DEFAULT_NAV_ORDER,
    onNavigateToHome: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val orderedKeys = remember(navOrder) {
        val parsed = navOrder.split(",")
            .map { it.trim().lowercase() }
            .filter { it in DEFAULT_NAV_KEYS }
            .distinct()
        (parsed + DEFAULT_NAV_KEYS).distinct()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Floating Nav Pill rendered dynamically from navOrder
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .height(64.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                orderedKeys.forEach { key ->
                    when (key) {
                        "home" -> FloatingNavPillItem(
                            icon = Res.drawable.ic_home,
                            contentDescription = stringResource(Res.string.nav_home),
                            selected = currentRoute.equals("Home", ignoreCase = true),
                            onClick = onNavigateToHome
                        )
                        "stats" -> FloatingNavPillItem(
                            icon = Res.drawable.ic_monitoring,
                            contentDescription = stringResource(Res.string.nav_stats),
                            selected = currentRoute.equals("Stats", ignoreCase = true),
                            onClick = onNavigateToStats
                        )
                        "history" -> FloatingNavPillItem(
                            icon = Res.drawable.ic_receipt_long,
                            contentDescription = stringResource(Res.string.nav_history),
                            selected = currentRoute.equals("Transactions", ignoreCase = true),
                            onClick = onNavigateToTransactions
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right Side: Detached Circular FAB
        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 10.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(Res.string.action_add_transaction)
            )
        }
    }
}

@Composable
private fun FloatingNavPillItem(
    icon: DrawableResource,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "navItemBg"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "navItemTint"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
