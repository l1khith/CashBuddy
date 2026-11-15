package com.cashbuddy.presentation.transactions.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.action_cancel
import cashbuddy.shared.generated.resources.date_range_apply
import cashbuddy.shared.generated.resources.date_range_from
import cashbuddy.shared.generated.resources.date_range_or_specific
import cashbuddy.shared.generated.resources.date_range_preset_30d
import cashbuddy.shared.generated.resources.date_range_preset_6m
import cashbuddy.shared.generated.resources.date_range_preset_7d
import cashbuddy.shared.generated.resources.date_range_preset_90d
import cashbuddy.shared.generated.resources.date_range_presets
import cashbuddy.shared.generated.resources.date_range_title
import cashbuddy.shared.generated.resources.date_range_to
import cashbuddy.shared.generated.resources.dialog_ok
import cashbuddy.shared.generated.resources.ic_calendar_today
import com.cashbuddy.domain.model.DateRangeHelper
import com.cashbuddy.platform.currentTimeMillis
import com.cashbuddy.presentation.theme.CashBuddyTypography
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangeDialog(
    onDismiss: () -> Unit,
    onRangeSelected: (Long, Long) -> Unit
) {
    val now = currentTimeMillis()
    val oneDayMs = 24L * 60 * 60 * 1000

    var startDateMillis by rememberSaveable { mutableStateOf(now - (30L * oneDayMs)) }
    var endDateMillis by rememberSaveable { mutableStateOf(now) }

    var isPickingStartDate by rememberSaveable { mutableStateOf(false) }
    var isPickingEndDate by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.date_range_title),
                style = CashBuddyTypography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick Presets
                Text(
                    text = stringResource(Res.string.date_range_presets),
                    style = CashBuddyTypography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRangeSelected(now - (7L * oneDayMs), now) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.date_range_preset_7d), fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onRangeSelected(now - (30L * oneDayMs), now) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.date_range_preset_30d), fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRangeSelected(now - (90L * oneDayMs), now) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.date_range_preset_90d), fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onRangeSelected(now - (180L * oneDayMs), now) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.date_range_preset_6m), fontSize = 12.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Specific Date Range Picker Section
                Text(
                    text = stringResource(Res.string.date_range_or_specific),
                    style = CashBuddyTypography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val fromDateLabel = stringResource(Res.string.date_range_from)
                    OutlinedCard(
                        onClick = { isPickingStartDate = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = fromDateLabel,
                                style = CashBuddyTypography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_calendar_today),
                                    contentDescription = fromDateLabel,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatEpochToDate(startDateMillis),
                                    style = CashBuddyTypography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    val toDateLabel = stringResource(Res.string.date_range_to)
                    OutlinedCard(
                        onClick = { isPickingEndDate = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = toDateLabel,
                                style = CashBuddyTypography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_calendar_today),
                                    contentDescription = toDateLabel,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatEpochToDate(endDateMillis),
                                    style = CashBuddyTypography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val startRange = DateRangeHelper.localDayBounds(minOf(startDateMillis, endDateMillis))
                        val endRange = DateRangeHelper.localDayBounds(maxOf(startDateMillis, endDateMillis))
                        onRangeSelected(startRange.startTimestamp, endRange.endTimestamp)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(Res.string.date_range_apply))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )

    if (isPickingStartDate) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateRangeHelper.localMillisToUtcPickerMillis(startDateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { isPickingStartDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            startDateMillis = DateRangeHelper.utcPickerMillisToLocalStartOfDay(it)
                        }
                        isPickingStartDate = false
                    }
                ) {
                    Text(stringResource(Res.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { isPickingStartDate = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (isPickingEndDate) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateRangeHelper.localMillisToUtcPickerMillis(endDateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { isPickingEndDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            endDateMillis = DateRangeHelper.utcPickerMillisToLocalStartOfDay(it)
                        }
                        isPickingEndDate = false
                    }
                ) {
                    Text(stringResource(Res.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { isPickingEndDate = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatEpochToDate(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis)
    val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val monthName = localDate.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "${localDate.dayOfMonth} $monthName ${localDate.year}"
}
