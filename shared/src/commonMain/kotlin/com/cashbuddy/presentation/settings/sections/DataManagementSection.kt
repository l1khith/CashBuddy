package com.cashbuddy.presentation.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import cashbuddy.shared.generated.resources.ic_download
import cashbuddy.shared.generated.resources.ic_file_download
import cashbuddy.shared.generated.resources.ic_merge_type
import cashbuddy.shared.generated.resources.ic_undo
import cashbuddy.shared.generated.resources.settings_section_data_mgmt
import cashbuddy.shared.generated.resources.settings_export_csv_btn
import cashbuddy.shared.generated.resources.settings_import_csv_btn
import cashbuddy.shared.generated.resources.settings_clean_duplicates_btn
import cashbuddy.shared.generated.resources.settings_scanning_duplicates
import cashbuddy.shared.generated.resources.settings_undo_merge_btn
import cashbuddy.shared.generated.resources.settings_clear_all_data_btn
import com.cashbuddy.presentation.theme.ExpenseCrimson
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun DataManagementSection(
    isScanningDuplicates: Boolean,
    hasRecentMerges: Boolean,
    onExportCsv: () -> Unit,
    onImportCsvClick: () -> Unit,
    onScanDuplicatesClick: () -> Unit,
    onUndoMergeClick: () -> Unit,
    onClearAllDataClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.settings_section_data_mgmt),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onExportCsv,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_download),
                        contentDescription = stringResource(Res.string.settings_export_csv_btn),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.settings_export_csv_btn))
                }

                OutlinedButton(
                    onClick = onImportCsvClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_file_download),
                        contentDescription = stringResource(Res.string.settings_import_csv_btn),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.settings_import_csv_btn))
                }

                OutlinedButton(
                    onClick = onScanDuplicatesClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_merge_type),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isScanningDuplicates) stringResource(Res.string.settings_scanning_duplicates)
                        else stringResource(Res.string.settings_clean_duplicates_btn)
                    )
                }

                if (hasRecentMerges) {
                    OutlinedButton(
                        onClick = onUndoMergeClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_undo),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.settings_undo_merge_btn))
                    }
                }

                Button(
                    onClick = onClearAllDataClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseCrimson),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_delete_forever),
                        contentDescription = stringResource(Res.string.settings_clear_all_data_btn),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.settings_clear_all_data_btn), color = Color.White)
                }
            }
        }
    }
}
