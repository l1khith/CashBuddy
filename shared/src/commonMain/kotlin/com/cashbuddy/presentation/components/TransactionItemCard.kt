package com.cashbuddy.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.presentation.theme.ExpenseCrimson
import com.cashbuddy.presentation.theme.IncomeEmerald

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.TextPrimaryDark
import com.cashbuddy.presentation.theme.TextTertiaryDark
import com.cashbuddy.presentation.theme.WarningAmber
import com.cashbuddy.presentation.theme.getCategoryColor

@Composable
fun TransactionCard(
    transaction: Transaction,
    categoryName: String = transaction.categoryName ?: "General",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(categoryName)
    val isDebit = transaction.type == TransactionType.DEBIT
    val isPending = transaction.status == TransactionStatus.PENDING
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (isPressed) 0.98f else 1.0f

    val borderModifier = if (isPending) {
        Modifier.border(
            width = 1.5.dp,
            color = WarningAmber.copy(alpha = 0.6f),
            shape = RadiusLarge
        )
    } else {
        Modifier
    }

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .scale(scale)
            .then(borderModifier),
        shape = RadiusLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getCategoryEmoji(categoryName, isDebit),
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.merchant.ifBlank { "Unknown Merchant" },
                    style = CashBuddyTypography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$categoryName • ${formatTimestamp(transaction.timestamp)}",
                    style = CashBuddyTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Amount & Status Badge
            val amountColor = if (isDebit) MaterialTheme.colorScheme.onSurface else AccentEmerald
            val prefix = if (isDebit) "-₹" else "+₹"
            val formattedAmount = formatCurrency(transaction.amount)

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$prefix$formattedAmount",
                    style = CashBuddyTypography.titleLarge,
                    color = amountColor,
                    fontWeight = FontWeight.SemiBold
                )
                if (isPending) {
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusBadge(status = transaction.status)
                }
            }
        }
    }
}

@Composable
fun TransactionItemCard(
    transaction: Transaction,
    categoryName: String = transaction.categoryName ?: "General",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    TransactionCard(
        transaction = transaction,
        categoryName = categoryName,
        onClick = onClick,
        modifier = modifier
    )
}

private fun getCategoryEmoji(categoryName: String, isDebit: Boolean): String {
    val lower = categoryName.lowercase()
    return when {
        lower.contains("food") || lower.contains("dining") -> "🍔"
        lower.contains("transport") -> "🚗"
        lower.contains("shopping") -> "🛍️"
        lower.contains("bill") || lower.contains("utilit") -> "📄"
        lower.contains("entertainment") -> "🎬"
        lower.contains("health") || lower.contains("medical") -> "🏥"
        lower.contains("education") -> "🎓"
        lower.contains("housing") || lower.contains("rent") -> "🏠"
        lower.contains("insurance") -> "🛡️"
        lower.contains("invest") -> "📈"
        lower.contains("salary") -> "💰"
        lower.contains("refund") -> "🔄"
        lower.contains("gift") -> "🎁"
        isDebit -> "↓"
        else -> "↑"
    }
}

fun formatCurrency(amount: Double): String {
    val absAmount = kotlin.math.abs(amount)
    return if (absAmount == absAmount.toLong().toDouble()) {
        val longVal = absAmount.toLong()
        val str = longVal.toString()
        // Format Indian number system (e.g. 1,24,500)
        if (str.length > 3) {
            val lastThree = str.substring(str.length - 3)
            val rest = str.substring(0, str.length - 3)
            val restFormatted = rest.reversed().chunked(2).joinToString(",").reversed()
            "$restFormatted,$lastThree"
        } else {
            str
        }
    } else {
        val cents = ((absAmount * 100).toLong() % 100).toString().padStart(2, '0')
        val whole = formatCurrency(absAmount.toLong().toDouble())
        "$whole.$cents"
    }
}

fun formatTimestamp(timestamp: Long): String {
    val now = com.cashbuddy.platform.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 172800_000 -> "Yesterday"
        else -> "${diff / 86400_000}d ago"
    }
}
