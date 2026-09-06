package dev.cfmobile.app.ui.emailrouting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.EmailRoutingRule
import dev.cfmobile.app.data.repository.EmailRoutingRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

data class EmailRoutingFormState(
    val name: String = "",
    val fromAddress: String = "",
    val toAddress: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class EmailRoutingUiState(
    val rules: UiState<List<EmailRoutingRule>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val isEnabled: Boolean? = null,
    val statusLabel: String? = null,
    val form: EmailRoutingFormState? = null,
    val deletingTag: String? = null
)

fun validateEmailRoutingForm(form: EmailRoutingFormState): String? = when {
    form.name.isBlank() -> "Rule name is required"
    form.fromAddress.isBlank() -> "Custom address is required"
    !form.fromAddress.trim().matches(EMAIL_REGEX) -> "Enter a valid custom address, e.g. hello@example.com"
    form.toAddress.isBlank() -> "Destination address is required"
    !form.toAddress.trim().matches(EMAIL_REGEX) -> "Enter a valid destination address"
    else -> null
}

/** "hello@example.com → me@gmail.com" from the rule's matcher and forward action. */
fun ruleRouteLabel(rule: EmailRoutingRule): String? {
    val from = rule.matchers.firstOrNull()?.value
    val to = rule.actions.firstOrNull()?.value?.joinToString(", ")?.takeIf { it.isNotBlank() }
    return when {
        from != null && to != null -> "$from → $to"
        from != null -> from
        to != null -> "→ $to"
        else -> null
    }
}

class EmailRoutingViewModel(
    private val zoneId: String,
    private val repository: EmailRoutingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmailRoutingUiState())
    val uiState: StateFlow<EmailRoutingUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(rules = UiState.Loading) }
        viewModelScope.launch {
            when (val settings = repository.getSettings(zoneId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isEnabled = settings.data.enabled, statusLabel = settings.data.status)
                }
                // Settings are context, not the main content - a failure here shouldn't hide
                // the rules below it.
                is ApiResult.Failure -> Unit
            }
            when (val rules = repository.listRules(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(rules = UiState.Data(rules.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(rules = UiState.Error(ErrorClassifier.classify(rules)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = EmailRoutingFormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (EmailRoutingFormState) -> EmailRoutingFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateEmailRoutingForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.createForwardRule(
                zoneId = zoneId,
                name = form.name.trim(),
                fromAddress = form.fromAddress.trim(),
                toAddress = form.toAddress.trim()
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

    fun delete(rule: EmailRoutingRule) {
        _uiState.update { it.copy(deletingTag = rule.tag) }
        viewModelScope.launch {
            repository.deleteRule(zoneId, rule.tag)
            _uiState.update { it.copy(deletingTag = null) }
            load(isRefresh = true)
        }
    }
}
