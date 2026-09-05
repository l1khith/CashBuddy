package com.cashbuddy.presentation.personalization

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashbuddy.domain.model.RawTrainingData
import com.cashbuddy.domain.model.UserCorrection
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusMedium

private enum class DataFilter { ALL, RAW_NOTIFICATIONS, USER_CORRECTIONS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataViewScreen(
    viewModel: PersonalizationViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(DataFilter.ALL) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Stored Learning Data",
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(
                            text = "← Back",
                            style = CashBuddyTypography.labelLarge,
                            color = PrimaryIndigo
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryIndigo)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Filter Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        label = "All (${state.rawSamplesCount + state.correctionsCount})",
                        isSelected = selectedFilter == DataFilter.ALL,
                        onClick = { selectedFilter = DataFilter.ALL }
                    )
                    FilterChip(
                        label = "Raw Alerts (${state.rawSamplesCount})",
                        isSelected = selectedFilter == DataFilter.RAW_NOTIFICATIONS,
                        onClick = { selectedFilter = DataFilter.RAW_NOTIFICATIONS }
                    )
                    FilterChip(
                        label = "Corrections (${state.correctionsCount})",
                        isSelected = selectedFilter == DataFilter.USER_CORRECTIONS,
                        onClick = { selectedFilter = DataFilter.USER_CORRECTIONS }
                    )
                }

                // Data Feed
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedFilter == DataFilter.ALL || selectedFilter == DataFilter.USER_CORRECTIONS) {
                        if (state.recentCorrections.isNotEmpty()) {
                            item {
                                Text(
                                    text = "USER CORRECTIONS (${state.recentCorrections.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryIndigo
                                )
                            }
                            items(state.recentCorrections, key = { "corr_${it.id}" }) { item ->
                                CorrectionCard(correction = item)
                            }
                        }
                    }

                    if (selectedFilter == DataFilter.ALL || selectedFilter == DataFilter.RAW_NOTIFICATIONS) {
                        if (state.rawNotifications.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "RAW BANKING NOTIFICATIONS (${state.rawNotifications.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentEmerald
                                )
                            }
                            items(state.rawNotifications, key = { "raw_${it.id}" }) { item ->
                                RawSampleCard(sample = item)
                            }
                        }
                    }

                    if (state.recentCorrections.isEmpty() && state.rawNotifications.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No training logs or corrections captured yet",
                                    style = CashBuddyTypography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RadiusMedium)
            .background(if (isSelected) PrimaryIndigo else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun CorrectionCard(correction: UserCorrection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RadiusMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = correction.merchant,
                    style = CashBuddyTypography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "User Feedback",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryIndigo,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = correction.oldCategory ?: "Unassigned",
                    style = CashBuddyTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = " ➔ ",
                    style = CashBuddyTypography.bodySmall,
                    color = PrimaryIndigo,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = correction.newCategory,
                    style = CashBuddyTypography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = AccentEmerald
                )
            }
            if (!correction.rawText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\"${correction.rawText}\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun RawSampleCard(sample: RawTrainingData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RadiusMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sample.extractedMerchant ?: sample.sourceApp ?: "Bank Notification",
                    style = CashBuddyTypography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (sample.extractedAmount != null) {
                    Text(
                        text = "₹${sample.extractedAmount}",
                        style = CashBuddyTypography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentEmerald
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "\"${sample.rawText}\"",
                style = CashBuddyTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sample.extractedType?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryIndigo
                    )
                }
                sample.sourceApp?.let {
                    Text(
                        text = "•  $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
