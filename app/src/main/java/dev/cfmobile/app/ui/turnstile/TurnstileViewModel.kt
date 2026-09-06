package dev.cfmobile.app.ui.turnstile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.TurnstileWidget
import dev.cfmobile.app.data.repository.TurnstileRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val DOMAIN_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$")

enum class TurnstileMode(val apiValue: String, val label: String) {
    MANAGED("managed", "Managed"),
    NON_INTERACTIVE("non-interactive", "Non-interactive"),
    INVISIBLE("invisible", "Invisible")
}

data class TurnstileFormState(
    val name: String = "",
    val domains: String = "",
    val mode: TurnstileMode = TurnstileMode.MANAGED,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class TurnstileUiState(
    val widgets: UiState<List<TurnstileWidget>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: TurnstileFormState? = null,
    val deletingSitekey: String? = null
)

fun parseDomains(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

fun validateTurnstileForm(form: TurnstileFormState): String? {
    val domains = parseDomains(form.domains)
    return when {
        form.name.isBlank() -> "Widget name is required"
        domains.isEmpty() -> "At least one domain is required"
        domains.any { !it.matches(DOMAIN_REGEX) } -> "Enter valid, comma-separated domains"
        else -> null
    }
}

class TurnstileViewModel(
    private val accountId: String,
    private val repository: TurnstileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TurnstileUiState())
    val uiState: StateFlow<TurnstileUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(widgets = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listWidgets(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(widgets = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(widgets = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = TurnstileFormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (TurnstileFormState) -> TurnstileFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateTurnstileForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.createWidget(
                accountId = accountId,
                name = form.name.trim(),
                domains = parseDomains(form.domains),
                mode = form.mode.apiValue
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(widget: TurnstileWidget) {
        _uiState.update { it.copy(deletingSitekey = widget.sitekey) }
        viewModelScope.launch {
            repository.deleteWidget(accountId, widget.sitekey)
            _uiState.update { it.copy(deletingSitekey = null) }
            load(isRefresh = true)
        }
    }
}
