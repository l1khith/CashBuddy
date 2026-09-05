package com.cashbuddy.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusXLarge

@Composable
fun CashBuddyBottomBar(
    currentRoute: String,
    onNavigateToHome: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onAddClick: () -> Unit,
    unreviewedCount: Int = 0,
    onNavigateToReview: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        // Bar Background with rounded top corners and subtle border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RadiusXLarge)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RadiusXLarge
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home
            BarNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                selected = currentRoute.contains("Home", ignoreCase = true),
                onClick = onNavigateToHome
            )

            // Transactions
            BarNavItem(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                label = "History",
                selected = currentRoute.contains("Transactions", ignoreCase = true),
                onClick = onNavigateToTransactions
            )

            // Center FAB (Add)
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = PrimaryIndigo,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .size(54.dp)
                    .offset(y = (-4).dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Transaction",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Stats
            BarNavItem(
                icon = Icons.Default.BarChart,
                label = "Stats",
                selected = currentRoute.contains("Stats", ignoreCase = true),
                onClick = onNavigateToStats
            )

            // Settings
            BarNavItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                selected = currentRoute.contains("Settings", ignoreCase = true),
                onClick = onNavigateToSettings
            )
        }
    }
}

@Composable
private fun BarNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val color = if (selected) activeColor else inactiveColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = color
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = CashBuddyTypography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
    }
}
