package com.cashbuddy.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.CategoryType
import com.cashbuddy.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManageCategoriesUiState(
    val categories: List<Category> = emptyList(),
    val filterType: CategoryType? = null,
    val isAddDialogOpen: Boolean = false,
    val editingCategory: Category? = null,
    val categoryToDelete: Category? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

private data class DialogState(
    val isAddDialogOpen: Boolean = false,
    val editingCategory: Category? = null,
    val categoryToDelete: Category? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class ManageCategoriesViewModel(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _filterType = MutableStateFlow<CategoryType?>(null)
    private val _dialogState = MutableStateFlow(DialogState())

    private val _snackbarEffect = MutableSharedFlow<String>()
    val snackbarEffect: SharedFlow<String> = _snackbarEffect.asSharedFlow()

    val uiState: StateFlow<ManageCategoriesUiState> = combine(
        categoryRepository.getAll(),
        _filterType,
        _dialogState
    ) { allCategories, filter, dialog ->
        val filtered = when (filter) {
            null -> allCategories
            else -> allCategories.filter { it.type == filter }
        }
        ManageCategoriesUiState(
            categories = filtered,
            filterType = filter,
            isAddDialogOpen = dialog.isAddDialogOpen,
            editingCategory = dialog.editingCategory,
            categoryToDelete = dialog.categoryToDelete,
            isSaving = dialog.isSaving,
            errorMessage = dialog.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ManageCategoriesUiState()
    )

    fun setFilterType(type: CategoryType?) {
        _filterType.value = type
    }

    fun openAddDialog() {
        _dialogState.update {
            it.copy(
                isAddDialogOpen = true,
                editingCategory = null,
                errorMessage = null
            )
        }
    }

    fun openEditDialog(category: Category) {
        if (category.isDefault) return
        _dialogState.update {
            it.copy(
                isAddDialogOpen = true,
                editingCategory = category,
                errorMessage = null
            )
        }
    }

    fun closeDialog() {
        _dialogState.update {
            it.copy(
                isAddDialogOpen = false,
                editingCategory = null,
                errorMessage = null
            )
        }
    }

    fun promptDelete(category: Category) {
        if (category.isDefault) return
        _dialogState.update {
            it.copy(categoryToDelete = category)
        }
    }

    fun dismissDeletePrompt() {
        _dialogState.update {
            it.copy(categoryToDelete = null)
        }
    }

    fun saveCategory(
        name: String,
        type: CategoryType,
        icon: String,
        colorHex: String
    ) {
        val trimmed = name.trim()
        if (trimmed.length < 2 || trimmed.length > 30) {
            _dialogState.update { it.copy(errorMessage = "Category name must be between 2 and 30 characters") }
            return
        }

        viewModelScope.launch {
            _dialogState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val editing = _dialogState.value.editingCategory
                val isTaken = categoryRepository.isNameTaken(trimmed, excludeId = editing?.id)
                if (isTaken) {
                    _dialogState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "A category named '$trimmed' already exists"
                        )
                    }
                    return@launch
                }

                if (editing != null) {
                    categoryRepository.updateCustom(
                        id = editing.id,
                        name = trimmed,
                        type = type,
                        icon = icon,
                        color = colorHex
                    )
                    _snackbarEffect.emit("Category updated")
                } else {
                    categoryRepository.insertCustom(
                        name = trimmed,
                        type = type,
                        icon = icon,
                        color = colorHex
                    )
                    _snackbarEffect.emit("Category created")
                }
                closeDialog()
            } catch (e: Throwable) {
                _dialogState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.message ?: "Failed to save category"
                    )
                }
            } finally {
                _dialogState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun confirmDelete() {
        val target = _dialogState.value.categoryToDelete ?: return
        if (target.isDefault) return

        viewModelScope.launch {
            _dialogState.update { it.copy(isSaving = true) }
            try {
                categoryRepository.deleteCustom(target.id)
                _snackbarEffect.emit("Category deleted. Transactions moved to Uncategorized.")
                _dialogState.update { it.copy(categoryToDelete = null) }
            } catch (e: Throwable) {
                _snackbarEffect.emit(e.message ?: "Failed to delete category")
            } finally {
                _dialogState.update { it.copy(isSaving = false) }
            }
        }
    }
}
