package com.cashbuddy.presentation.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.action_cancel
import cashbuddy.shared.generated.resources.action_close
import cashbuddy.shared.generated.resources.dup_groups_found
import cashbuddy.shared.generated.resources.dup_hide
import cashbuddy.shared.generated.resources.dup_keep_survivor
import cashbuddy.shared.generated.resources.dup_merge_button
import cashbuddy.shared.generated.resources.dup_merge_duplicate
import cashbuddy.shared.generated.resources.dup_merging_progress
import cashbuddy.shared.generated.resources.dup_preview_csv
import cashbuddy.shared.generated.resources.dup_summary_line
import cashbuddy.shared.generated.resources.dup_title
import cashbuddy.shared.generated.resources.dup_view_all
import cashbuddy.shared.generated.resources.dup_zero_found_desc
import cashbuddy.shared.generated.resources.dup_zero_found_title
import cashbuddy.shared.generated.resources.ic_check_circle
import cashbuddy.shared.generated.resources.ic_download
import cashbuddy.shared.generated.resources.ic_expand_less
import cashbuddy.shared.generated.resources.ic_expand_more
import cashbuddy.shared.generated.resources.ic_merge_type
import cashbuddy.shared.generated.resources.ic_remove_circle_outline
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashbuddy.core.DuplicateReconciler
import com.cashbuddy.domain.model.CurrencyRegistry
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import com.cashbuddy.presentation.theme.WarningAmber

@Composable
fun DuplicateReconciliationDialog(
    duplicateGroups: List<DuplicateReconciler.DuplicateGroup>,
    preferredCurrency: String,
    isMerging: Boolean,
    onMergeConfirmed: () -> Unit,
    onPreviewCsv: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalRecords = duplicateGroups.sumOf { it.totalCount }
    val totalToMerge = duplicateGroups.sumOf { it.duplicates.size }
    val recordsToKeep = duplicateGroups.size

    val expandedState = remember { mutableStateMapOf<Int, Boolean>() }

    AlertDialog(
        onDismissRequest = { if (!isMerging) onDismiss() },
        modifier = modifier.fillMaxWidth(),
        shape = RadiusLarge,
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PrimaryIndigo.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_merge_type),
                            contentDescription = null,
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = stringResource(Res.string.dup_title),
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (duplicateGroups.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            Res.string.dup_groups_found,
                            duplicateGroups.size,
                            totalRecords,
                            recordsToKeep
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            if (duplicateGroups.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.dup_zero_found_title),
                        style = CashBuddyTypography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.dup_zero_found_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = duplicateGroups,
                        key = { _, group -> group.survivor.id }
                    ) { index, group ->
                        val isExpanded = expandedState[index] == true
                        DuplicateGroupCard(
                            group = group,
                            preferredCurrency = preferredCurrency,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedState[index] = !isExpanded
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (duplicateGroups.isNotEmpty()) {
                Button(
                    onClick = onMergeConfirmed,
                    enabled = !isMerging,
                    shape = RadiusMedium,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                ) {
                    if (isMerging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(Res.string.dup_merging_progress))
                    } else {
                        Text(stringResource(Res.string.dup_merge_button, totalToMerge))
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (duplicateGroups.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onPreviewCsv,
                        shape = RadiusMedium
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_download),
                            contentDescription = stringResource(Res.string.dup_preview_csv),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(Res.string.dup_preview_csv), fontSize = 12.sp)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isMerging
                ) {
                    Text(if (duplicateGroups.isEmpty()) stringResource(Res.string.action_close) else stringResource(Res.string.action_cancel))
                }
            }
        }
    )
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateReconciler.DuplicateGroup,
    preferredCurrency: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val survivor = group.survivor
    val amountStr = CurrencyRegistry.format(survivor.amount, preferredCurrency)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RadiusMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = amountStr,
                            style = CashBuddyTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = survivor.merchant,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            Res.string.dup_summary_line,
                            group.totalCount,
                            survivor.sourceApp,
                            (survivor.confidence * 100).toInt()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleExpand() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) stringResource(Res.string.dup_hide) else stringResource(Res.string.dup_view_all, group.totalCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryIndigo,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        painter = painterResource(if (isExpanded) Res.drawable.ic_expand_less else Res.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Survivor Item
                    TransactionDetailItem(
                        tx = survivor,
                        isSurvivor = true,
                        preferredCurrency = preferredCurrency
                    )

                    // Duplicate Items
                    group.duplicates.forEach { dup ->
                        TransactionDetailItem(
                            tx = dup,
                            isSurvivor = false,
                            preferredCurrency = preferredCurrency
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailItem(
    tx: Transaction,
    isSurvivor: Boolean,
    preferredCurrency: String,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSurvivor) AccentEmerald.copy(alpha = 0.5f) else DangerRed.copy(alpha = 0.3f)
    val bgColor = if (isSurvivor) AccentEmerald.copy(alpha = 0.06f) else DangerRed.copy(alpha = 0.04f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(if (isSurvivor) Res.drawable.ic_check_circle else Res.drawable.ic_remove_circle_outline),
                        contentDescription = null,
                        tint = if (isSurvivor) AccentEmerald else DangerRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSurvivor) stringResource(Res.string.dup_keep_survivor) else stringResource(Res.string.dup_merge_duplicate),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSurvivor) AccentEmerald else DangerRed
                    )
                }

                Text(
                    text = "ID #${tx.id} • ${tx.status.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Source: ${tx.sourceApp} (Confidence: ${(tx.confidence * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (tx.rawText.isNotBlank()) {
                Text(
                    text = tx.rawText.replace("\n", " ").trim().take(120),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
