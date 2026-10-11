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
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.ic_account_balance_wallet
import cashbuddy.shared.generated.resources.ic_attach_money
import cashbuddy.shared.generated.resources.ic_category
import cashbuddy.shared.generated.resources.ic_directions_bus
import cashbuddy.shared.generated.resources.ic_home
import cashbuddy.shared.generated.resources.ic_payments
import cashbuddy.shared.generated.resources.ic_receipt_long
import cashbuddy.shared.generated.resources.ic_refresh
import cashbuddy.shared.generated.resources.ic_restaurant
import cashbuddy.shared.generated.resources.ic_shopping_bag
import cashbuddy.shared.generated.resources.ic_trending_down
import cashbuddy.shared.generated.resources.ic_trending_up
import cashbuddy.shared.generated.resources.ic_two_wheeler
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.presentation.theme.ExpenseCrimson
import com.cashbuddy.presentation.theme.IncomeEmerald

import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.TextPrimaryDark
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
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
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
                Icon(
                    painter = painterResource(getCategoryDrawable(categoryName, isDebit)),
                    contentDescription = categoryName,
                    tint = categoryColor,
                    modifier = Modifier.size(22.dp)
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
            val sign = if (isDebit) "-" else "+"
            val formattedAmount = formatCurrency(transaction.amount, transaction.currency, includeSymbol = true)

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$sign$formattedAmount",
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

fun getCategoryDrawable(categoryName: String, isDebit: Boolean = true): DrawableResource {
    val lower = categoryName.lowercase()
    return when {
        lower.contains("food") || lower.contains("dining") || lower.contains("restaurant") -> Res.drawable.ic_restaurant
        lower.contains("two wheeler") || lower.contains("bike") || lower.contains("motorcycle") -> Res.drawable.ic_two_wheeler
        lower.contains("transport") || lower.contains("bus") || lower.contains("travel") || lower.contains("cab") || lower.contains("fuel") || lower.contains("auto") || lower.contains("car") -> Res.drawable.ic_directions_bus
        lower.contains("shopping") || lower.contains("groceries") || lower.contains("retail") || lower.contains("store") -> Res.drawable.ic_shopping_bag
        lower.contains("bill") || lower.contains("utilit") || lower.contains("recharge") -> Res.drawable.ic_receipt_long
        lower.contains("housing") || lower.contains("rent") -> Res.drawable.ic_home
        lower.contains("invest") || lower.contains("stock") || lower.contains("mutual") -> Res.drawable.ic_trending_up
        lower.contains("salary") || lower.contains("account") || lower.contains("wallet") -> Res.drawable.ic_account_balance_wallet
        lower.contains("refund") -> Res.drawable.ic_refresh
        lower.contains("payment") || lower.contains("upi") -> Res.drawable.ic_payments
        lower.contains("cash") || lower.contains("money") -> Res.drawable.ic_attach_money
        categoryName.isNotBlank() && categoryName != "Unknown" -> Res.drawable.ic_category
        isDebit -> Res.drawable.ic_trending_down
        else -> Res.drawable.ic_trending_up
    }
}

fun formatCurrency(
    amount: Double,
    currencyCode: String? = null,
    includeSymbol: Boolean = false
): String {
    return com.cashbuddy.domain.model.CurrencyRegistry.format(amount, currencyCode, includeSymbol)
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
