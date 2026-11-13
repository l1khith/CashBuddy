package com.cashbuddy.presentation.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.action_cancel
import cashbuddy.shared.generated.resources.action_delete
import cashbuddy.shared.generated.resources.action_edit
import cashbuddy.shared.generated.resources.action_save
import cashbuddy.shared.generated.resources.cd_back
import cashbuddy.shared.generated.resources.ic_account_balance_wallet
import cashbuddy.shared.generated.resources.ic_add
import cashbuddy.shared.generated.resources.ic_attach_money
import cashbuddy.shared.generated.resources.ic_category
import cashbuddy.shared.generated.resources.ic_delete_forever
import cashbuddy.shared.generated.resources.ic_directions_bus
import cashbuddy.shared.generated.resources.ic_edit
import cashbuddy.shared.generated.resources.ic_home
import cashbuddy.shared.generated.resources.ic_payments
import cashbuddy.shared.generated.resources.ic_receipt_long
import cashbuddy.shared.generated.resources.ic_restaurant
import cashbuddy.shared.generated.resources.ic_shield
import cashbuddy.shared.generated.resources.ic_shopping_bag
import cashbuddy.shared.generated.resources.ic_trending_up
import cashbuddy.shared.generated.resources.ic_two_wheeler
import cashbuddy.shared.generated.resources.manage_categories_add_action
import cashbuddy.shared.generated.resources.manage_categories_badge_custom
import cashbuddy.shared.generated.resources.manage_categories_badge_system
import cashbuddy.shared.generated.resources.manage_categories_color_label
import cashbuddy.shared.generated.resources.manage_categories_delete_confirm_msg
import cashbuddy.shared.generated.resources.manage_categories_delete_confirm_title
import cashbuddy.shared.generated.resources.manage_categories_edit_title
import cashbuddy.shared.generated.resources.manage_categories_icon_label
import cashbuddy.shared.generated.resources.manage_categories_name_hint
import cashbuddy.shared.generated.resources.manage_categories_name_label
import cashbuddy.shared.generated.resources.manage_categories_new_title
import cashbuddy.shared.generated.resources.manage_categories_tab_all
import cashbuddy.shared.generated.resources.manage_categories_tab_expense
import cashbuddy.shared.generated.resources.manage_categories_tab_income
import cashbuddy.shared.generated.resources.manage_categories_title
import cashbuddy.shared.generated.resources.manage_categories_type_label
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.CategoryRegistry
import com.cashbuddy.domain.model.CategoryType
import com.cashbuddy.presentation.theme.CashBuddyTypography
import com.cashbuddy.presentation.theme.CategoryVisuals
import com.cashbuddy.presentation.theme.DangerRed
import com.cashbuddy.presentation.theme.PrimaryIndigo
import com.cashbuddy.presentation.theme.RadiusLarge
import com.cashbuddy.presentation.theme.RadiusMedium
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesScreen(
    viewModel: ManageCategoriesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEffect.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.manage_categories_title),
                        style = CashBuddyTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                            tint = PrimaryIndigo
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = PrimaryIndigo,
                contentColor = Color.White,
                shape = RadiusLarge
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_add),
                        contentDescription = stringResource(Res.string.manage_categories_add_action),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.manage_categories_add_action),
                        style = CashBuddyTypography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Segmented Filter Tabs
            val selectedTabIndex = when (state.filterType) {
                null -> 0
                CategoryType.EXPENSE -> 1
                CategoryType.INCOME -> 2
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { viewModel.setFilterType(null) },
                    text = { Text(stringResource(Res.string.manage_categories_tab_all)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { viewModel.setFilterType(CategoryType.EXPENSE) },
                    text = { Text(stringResource(Res.string.manage_categories_tab_expense)) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { viewModel.setFilterType(CategoryType.INCOME) },
                    text = { Text(stringResource(Res.string.manage_categories_tab_income)) }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = state.categories,
                    key = { it.id },
                    contentType = { "category_item" }
                ) { category ->
                    CategoryItemRow(
                        category = category,
                        onEdit = { viewModel.openEditDialog(category) },
                        onDelete = { viewModel.promptDelete(category) }
                    )
                }
            }
        }
    }

    if (state.isAddDialogOpen) {
        CategoryEditDialog(
            category = state.editingCategory,
            isSaving = state.isSaving,
            errorMessage = state.errorMessage,
            onDismiss = { viewModel.closeDialog() },
            onSave = { name, type, icon, colorHex ->
                viewModel.saveCategory(name, type, icon, colorHex)
            }
        )
    }

    if (state.categoryToDelete != null) {
        val target = state.categoryToDelete!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeletePrompt() },
            title = {
                Text(
                    text = stringResource(Res.string.manage_categories_delete_confirm_title),
                    style = CashBuddyTypography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "${target.name}: " + stringResource(Res.string.manage_categories_delete_confirm_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeletePrompt() }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun CategoryItemRow(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = CategoryRegistry.parseHexColor(category.color)
    val iconDrawable = CategoryVisuals.getDrawableForIconName(category.icon)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RadiusLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconDrawable),
                    contentDescription = category.name,
                    tint = categoryColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = CashBuddyTypography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (category.type == CategoryType.EXPENSE) {
                        stringResource(Res.string.manage_categories_tab_expense)
                    } else {
                        stringResource(Res.string.manage_categories_tab_income)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (category.isDefault) {
                Row(
                    modifier = Modifier
                        .clip(RadiusMedium)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(Res.string.manage_categories_badge_system),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RadiusMedium)
                            .background(PrimaryIndigo.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.manage_categories_badge_custom),
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryIndigo,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_edit),
                            contentDescription = stringResource(Res.string.action_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_delete_forever),
                            contentDescription = stringResource(Res.string.action_delete),
                            tint = DangerRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryEditDialog(
    category: Category?,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: CategoryType, icon: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var type by remember { mutableStateOf(category?.type ?: CategoryType.EXPENSE) }
    var selectedColor by remember { mutableStateOf(category?.color ?: CategoryVisuals.PRESET_COLORS.first()) }
    var selectedIcon by remember { mutableStateOf(category?.icon ?: CategoryVisuals.PRESET_ICONS.first().first) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (category != null) {
                    stringResource(Res.string.manage_categories_edit_title)
                } else {
                    stringResource(Res.string.manage_categories_new_title)
                },
                style = CashBuddyTypography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    label = { Text(stringResource(Res.string.manage_categories_name_label)) },
                    placeholder = { Text(stringResource(Res.string.manage_categories_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RadiusMedium,
                    isError = errorMessage != null,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (errorMessage != null) {
                                Text(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Text(
                                text = "${name.length}/30",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                )

                // Type Segmented Toggle
                Text(
                    text = stringResource(Res.string.manage_categories_type_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { type = CategoryType.EXPENSE },
                        modifier = Modifier.weight(1f),
                        shape = RadiusMedium,
                        colors = if (type == CategoryType.EXPENSE) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = PrimaryIndigo.copy(alpha = 0.15f),
                                contentColor = PrimaryIndigo
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text(
                            text = stringResource(Res.string.manage_categories_tab_expense),
                            fontWeight = if (type == CategoryType.EXPENSE) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    OutlinedButton(
                        onClick = { type = CategoryType.INCOME },
                        modifier = Modifier.weight(1f),
                        shape = RadiusMedium,
                        colors = if (type == CategoryType.INCOME) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = PrimaryIndigo.copy(alpha = 0.15f),
                                contentColor = PrimaryIndigo
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text(
                            text = stringResource(Res.string.manage_categories_tab_income),
                            fontWeight = if (type == CategoryType.INCOME) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // Color Swatches Grid
                Text(
                    text = stringResource(Res.string.manage_categories_color_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CategoryVisuals.PRESET_COLORS.take(6).forEach { hex ->
                        ColorCircleItem(
                            hex = hex,
                            isSelected = selectedColor.equals(hex, ignoreCase = true),
                            onSelect = { selectedColor = hex }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CategoryVisuals.PRESET_COLORS.drop(6).take(6).forEach { hex ->
                        ColorCircleItem(
                            hex = hex,
                            isSelected = selectedColor.equals(hex, ignoreCase = true),
                            onSelect = { selectedColor = hex }
                        )
                    }
                }

                // Icon Grid
                Text(
                    text = stringResource(Res.string.manage_categories_icon_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CategoryVisuals.PRESET_ICONS.take(6).forEach { (iconKey, res) ->
                        IconSquareItem(
                            drawableRes = res,
                            tint = CategoryRegistry.parseHexColor(selectedColor),
                            isSelected = selectedIcon == iconKey,
                            onSelect = { selectedIcon = iconKey }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CategoryVisuals.PRESET_ICONS.drop(6).take(6).forEach { (iconKey, res) ->
                        IconSquareItem(
                            drawableRes = res,
                            tint = CategoryRegistry.parseHexColor(selectedColor),
                            isSelected = selectedIcon == iconKey,
                            onSelect = { selectedIcon = iconKey }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, type, selectedIcon, selectedColor) },
                enabled = name.isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ColorCircleItem(
    hex: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val color = CategoryRegistry.parseHexColor(hex)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onSelect)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun IconSquareItem(
    drawableRes: DrawableResource,
    tint: Color,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) tint.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .clickable(onClick = onSelect)
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, tint, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(drawableRes),
            contentDescription = null,
            tint = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
