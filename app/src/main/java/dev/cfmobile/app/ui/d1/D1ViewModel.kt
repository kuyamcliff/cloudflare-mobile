package dev.cfmobile.app.ui.d1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.D1Database
import dev.cfmobile.app.data.repository.D1Repository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val DATABASE_NAME_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")

data class D1FormState(val name: String = "", val isSaving: Boolean = false, val error: String? = null)

data class D1UiState(
    val databases: UiState<List<D1Database>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: D1FormState? = null,
    val deletingUuid: String? = null
)

fun validateDatabaseName(name: String): String? = when {
    name.isBlank() -> "Database name is required"
    !name.matches(DATABASE_NAME_REGEX) -> "Use up to 64 letters, numbers, underscores, or hyphens"
    else -> null
}

class D1ViewModel(
    private val accountId: String,
    private val repository: D1Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(D1UiState())
    val uiState: StateFlow<D1UiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    /** [isRefresh] keeps the current list on screen during a pull-to-refresh, rather than
     *  replacing content the user is reading with a spinner. */
    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(databases = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listDatabases(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(databases = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(databases = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = D1FormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (D1FormState) -> D1FormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateDatabaseName(form.name)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createDatabase(accountId, form.name.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    refresh()
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(database: D1Database) {
        _uiState.update { it.copy(deletingUuid = database.uuid) }
        viewModelScope.launch {
            repository.deleteDatabase(accountId, database.uuid)
            _uiState.update { it.copy(deletingUuid = null) }
            refresh()
        }
    }
}
