package com.cashbuddy.presentation.transactions.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.action_reset_all
import cashbuddy.shared.generated.resources.dialog_edit_tx_category_label
import cashbuddy.shared.generated.resources.tx_filter_all_categories
import cashbuddy.shared.generated.resources.tx_filter_amount_range
import cashbuddy.shared.generated.resources.tx_filter_apply
import cashbuddy.shared.generated.resources.tx_filter_max_amount
import cashbuddy.shared.generated.resources.tx_filter_min_amount
import cashbuddy.shared.generated.resources.tx_filter_title
import com.cashbuddy.domain.model.Category
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.RadiusMedium
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionAdvancedFilterSheet(
    sheetState: SheetState,
    initialMinAmount: Double?,
    initialMaxAmount: Double?,
    selectedCategoryId: Long?,
    categories: List<Category>,
    onDismissRequest: () -> Unit,
    onApply: (min: Double?, max: Double?) -> Unit,
    onCategorySelect: (Long?) -> Unit,
    onResetAll: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var minAmtText by rememberSaveable { mutableStateOf(initialMinAmount?.toString() ?: "") }
    var maxAmtText by rememberSaveable { mutableStateOf(initialMaxAmount?.toString() ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.tx_filter_title),
                    style = CashBuddyTypography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        onResetAll()
                        minAmtText = ""
                        maxAmtText = ""
                    }
                ) {
                    Text(stringResource(Res.string.action_reset_all))
                }
            }

            Text(
                text = stringResource(Res.string.tx_filter_amount_range),
                style = CashBuddyTypography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = minAmtText,
                    onValueChange = { minAmtText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(Res.string.tx_filter_min_amount)) },
                    placeholder = { Text("0") },
                    singleLine = true,
                    shape = RadiusMedium
                )
                OutlinedTextField(
                    value = maxAmtText,
                    onValueChange = { maxAmtText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(Res.string.tx_filter_max_amount)) },
                    placeholder = { Text("No limit") },
                    singleLine = true,
                    shape = RadiusMedium
                )
            }

            Text(
                text = stringResource(Res.string.dialog_edit_tx_category_label),
                style = CashBuddyTypography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategoryId == null,
                    onClick = { onCategorySelect(null) },
                    label = { Text(stringResource(Res.string.tx_filter_all_categories)) }
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = {
                            onCategorySelect(if (selectedCategoryId == cat.id) null else cat.id)
                        },
                        label = { Text(cat.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val min = minAmtText.toDoubleOrNull()
                    val max = maxAmtText.toDoubleOrNull()
                    onApply(min, max)
                    coroutineScope.launch {
                        sheetState.hide()
                        onDismissRequest()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RadiusMedium
            ) {
                Text(stringResource(Res.string.tx_filter_apply))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
