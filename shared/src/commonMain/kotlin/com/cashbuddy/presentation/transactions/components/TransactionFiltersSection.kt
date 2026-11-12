package com.cashbuddy.presentation.transactions.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.date_range_title
import cashbuddy.shared.generated.resources.ic_calendar_today
import cashbuddy.shared.generated.resources.tx_list_filter_all_types
import cashbuddy.shared.generated.resources.tx_list_filter_expenses
import cashbuddy.shared.generated.resources.tx_list_filter_income
import com.cashbuddy.domain.model.TimePeriod
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.presentation.components.PaisaPalFilterChip
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun TransactionFiltersSection(
    selectedPeriod: TimePeriod,
    selectedType: TransactionType?,
    onPeriodClick: (TimePeriod) -> Unit,
    onTypeClick: (TransactionType?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Period Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimePeriod.entries.forEach { period ->
                val isSelected = selectedPeriod == period
                PaisaPalFilterChip(
                    selected = isSelected,
                    onClick = { onPeriodClick(period) },
                    label = period.displayName,
                    leadingIcon = if (period == TimePeriod.CUSTOM) {
                        {
                            Icon(
                                painter = painterResource(Res.drawable.ic_calendar_today),
                                contentDescription = stringResource(Res.string.date_range_title),
                                modifier = Modifier.size(14.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }

        // Type Filter Chips (All, Debits, Credits)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PaisaPalFilterChip(
                selected = selectedType == null,
                onClick = { onTypeClick(null) },
                label = stringResource(Res.string.tx_list_filter_all_types)
            )
            PaisaPalFilterChip(
                selected = selectedType == TransactionType.DEBIT,
                onClick = { onTypeClick(TransactionType.DEBIT) },
                label = stringResource(Res.string.tx_list_filter_expenses)
            )
            PaisaPalFilterChip(
                selected = selectedType == TransactionType.CREDIT,
                onClick = { onTypeClick(TransactionType.CREDIT) },
                label = stringResource(Res.string.tx_list_filter_income)
            )
        }
    }
}
