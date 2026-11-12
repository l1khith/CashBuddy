package com.cashbuddy.presentation.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.ic_delete_forever
import cashbuddy.shared.generated.resources.ic_file_download
import cashbuddy.shared.generated.resources.ic_tune
import com.cashbuddy.presentation.theme.AccentEmerald
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import org.jetbrains.compose.resources.painterResource

@Composable
fun LearningSection(
    rawTrainingSamplesCount: Long,
    userCorrectionsCount: Long,
    onNavigateToPersonalization: () -> Unit,
    onExportTrainingDataset: () -> Unit,
    onResetTrainingData: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "On-Device Learning & Training Data",
            style = CashBuddyTypography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RadiusLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Personalize categorization by correcting categories. Correcting a transaction saves a merchant rule for future transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RadiusMedium,
                        colors = CardDefaults.cardColors(
                            containerColor = PrimaryIndigo.copy(alpha = 0.08f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "$rawTrainingSamplesCount",
                                style = CashBuddyTypography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryIndigo
                            )
                            Text(
                                text = "Raw Samples",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RadiusMedium,
                        colors = CardDefaults.cardColors(
                            containerColor = AccentEmerald.copy(alpha = 0.08f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "$userCorrectionsCount",
                                style = CashBuddyTypography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AccentEmerald
                            )
                            Text(
                                text = "User Corrections",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Actions
                Button(
                    onClick = onNavigateToPersonalization,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RadiusMedium,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_tune),
                        contentDescription = "Personalization Dashboard",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Personalization Dashboard")
                }

                OutlinedButton(
                    onClick = onExportTrainingDataset,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RadiusMedium
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_file_download),
                        contentDescription = "Export Dataset",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Training Dataset (JSONL)")
                }

                OutlinedButton(
                    onClick = onResetTrainingData,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RadiusMedium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_delete_forever),
                        contentDescription = "Reset Training Data",
                        tint = DangerRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Training Data & Corrections")
                }
            }
        }
    }
}
