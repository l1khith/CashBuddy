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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.RemoveCircleOutline
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
                            imageVector = Icons.AutoMirrored.Filled.MergeType,
                            contentDescription = null,
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "Clean Up Duplicates",
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (duplicateGroups.isNotEmpty()) {
                    Text(
                        text = "Found ${duplicateGroups.size} duplicate group(s) ($totalRecords records → $recordsToKeep records)",
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
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Zero Duplicates Found",
                        style = CashBuddyTypography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your transaction ledger has no identical payment entries or duplicated screenshot imports.",
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
                    itemsIndexed(duplicateGroups) { index, group ->
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
                        Text("Merging...")
                    } else {
                        Text("Merge $totalToMerge Duplicates")
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
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Preview CSV", fontSize = 12.sp)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isMerging
                ) {
                    Text(if (duplicateGroups.isEmpty()) "Close" else "Cancel")
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
                        text = "${group.totalCount} records → keep 1 (${survivor.sourceApp}, p=${(survivor.confidence * 100).toInt()}%)",
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
                        text = if (isExpanded) "Hide" else "View all ${group.totalCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryIndigo,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
                        imageVector = if (isSurvivor) Icons.Default.CheckCircle else Icons.Default.RemoveCircleOutline,
                        contentDescription = null,
                        tint = if (isSurvivor) AccentEmerald else DangerRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSurvivor) "Keep • Survivor" else "Merge • Duplicate",
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
