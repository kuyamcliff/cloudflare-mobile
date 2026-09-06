package dev.cfmobile.app.ui.kv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.KvKey
import dev.cfmobile.app.data.repository.KvRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The editor sheet. [originalKey] is null when adding a key, set when editing an existing one -
 *  which is also what stops a rename from silently creating a second key. */
data class KvValueFormState(
    val key: String = "",
    val value: String = "",
    val originalKey: String? = null,
    val isLoadingValue: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val isEditing: Boolean get() = originalKey != null
}

data class KvKeysUiState(
    val keys: UiState<List<KvKey>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: KvValueFormState? = null,
    val deletingKey: String? = null
)

fun validateKvKey(key: String): String? = when {
    key.isBlank() -> "Key name is required"
    key.length > 512 -> "Key names are limited to 512 characters"
    else -> null
}

/** KV expirations are unix seconds; null means the key never expires. */
fun kvExpiryLabel(key: KvKey): String? = key.expiration?.let { "Expires at $it" }

class KvKeysViewModel(
    private val accountId: String,
    private val namespaceId: String,
    private val repository: KvRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KvKeysUiState())
    val uiState: StateFlow<KvKeysUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(keys = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listKeys(accountId, namespaceId)) {
                is ApiResult.Success -> _uiState.update { it.copy(keys = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(keys = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openCreateForm() = _uiState.update { it.copy(form = KvValueFormState()) }

    /** Opens an existing key for editing and fetches its value, since the key list doesn't
     *  carry values. */
    fun openEditForm(key: KvKey) {
        _uiState.update {
            it.copy(form = KvValueFormState(key = key.name, originalKey = key.name, isLoadingValue = true))
        }
        viewModelScope.launch {
            when (val result = repository.getValue(accountId, namespaceId, key.name)) {
                is ApiResult.Success -> updateForm { it.copy(value = result.data, isLoadingValue = false) }
                is ApiResult.Failure -> updateForm { it.copy(isLoadingValue = false, error = result.message) }
            }
        }
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (KvValueFormState) -> KvValueFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateKvKey(form.key)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val newKey = form.key.trim()
            when (val result = repository.putValue(accountId, namespaceId, newKey, form.value)) {
                is ApiResult.Success -> {
                    // Renaming while editing writes to the new name, so the old one has to go
                    // or the namespace quietly ends up with both.
                    val previous = form.originalKey
                    if (previous != null && previous != newKey) {
                        repository.deleteValue(accountId, namespaceId, previous)
                    }
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(key: KvKey) {
        _uiState.update { it.copy(deletingKey = key.name) }
        viewModelScope.launch {
            repository.deleteValue(accountId, namespaceId, key.name)
            _uiState.update { it.copy(deletingKey = null) }
            load(isRefresh = true)
        }
    }
}
