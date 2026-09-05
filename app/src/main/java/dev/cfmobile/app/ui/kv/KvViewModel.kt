package dev.cfmobile.app.ui.kv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.KvNamespace
import dev.cfmobile.app.data.repository.KvRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KvFormState(val title: String = "", val isSaving: Boolean = false, val error: String? = null)

data class KvUiState(
    val namespaces: UiState<List<KvNamespace>> = UiState.Loading,
    val form: KvFormState? = null,
    val deletingId: String? = null
)

fun validateNamespaceTitle(title: String): String? = if (title.isBlank()) "Namespace title is required" else null

class KvViewModel(
    private val accountId: String,
    private val repository: KvRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KvUiState())
    val uiState: StateFlow<KvUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(namespaces = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listNamespaces(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(namespaces = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(namespaces = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = KvFormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (KvFormState) -> KvFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateNamespaceTitle(form.title)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createNamespace(accountId, form.title.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    refresh()
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(namespace: KvNamespace) {
        _uiState.update { it.copy(deletingId = namespace.id) }
        viewModelScope.launch {
            repository.deleteNamespace(accountId, namespace.id)
            _uiState.update { it.copy(deletingId = null) }
            refresh()
        }
    }
}
