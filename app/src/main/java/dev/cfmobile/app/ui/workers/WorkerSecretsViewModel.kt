package dev.cfmobile.app.ui.workers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.WorkerSecret
import dev.cfmobile.app.data.repository.WorkersRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The form for adding or replacing a secret. [value] is write-only in every direction: this
 * app never reads a secret back from Cloudflare, and never keeps one after the request.
 */
data class SecretFormState(
    val name: String = "",
    val value: String = "",
    /** Set when replacing an existing secret, so the name field can be locked. */
    val isReplacing: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class WorkerSecretsUiState(
    val secrets: UiState<List<WorkerSecret>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: SecretFormState? = null,
    val deletingName: String? = null,
    val error: String? = null
)

/** Cloudflare's secret names are environment-variable names, so the same rules apply. */
private val SECRET_NAME_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

fun validateSecretForm(form: SecretFormState): String? = when {
    form.name.isBlank() -> "Secret name is required"
    !form.name.trim().matches(SECRET_NAME_REGEX) ->
        "Use letters, digits, and underscores, starting with a letter or underscore"
    form.value.isBlank() -> "A value is required - Cloudflare has no empty secret"
    else -> null
}

class WorkerSecretsViewModel(
    private val accountId: String,
    private val scriptName: String,
    private val repository: WorkersRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkerSecretsUiState())
    val uiState: StateFlow<WorkerSecretsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(secrets = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listSecrets(accountId, scriptName)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(secrets = UiState.Data(result.data), isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(secrets = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openCreateForm() = _uiState.update { it.copy(form = SecretFormState()) }

    /** Replacing keeps the name and asks only for a new value - the old one can't be shown. */
    fun openReplaceForm(secret: WorkerSecret) =
        _uiState.update { it.copy(form = SecretFormState(name = secret.name, isReplacing = true)) }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (SecretFormState) -> SecretFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateSecretForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.putSecret(accountId, scriptName, form.name.trim(), form.value)
            when (result) {
                // The form is dropped whole on success, which is also what clears the value
                // from memory - nothing here keeps it.
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(secret: WorkerSecret) {
        _uiState.update { it.copy(deletingName = secret.name, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteSecret(accountId, scriptName, secret.name)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingName = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingName = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
