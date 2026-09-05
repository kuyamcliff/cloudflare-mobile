package dev.cfmobile.app.ui.gateway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.GatewayRule
import dev.cfmobile.app.data.remote.dto.GatewayRuleCreate
import dev.cfmobile.app.data.repository.GatewayRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GatewayAction(val apiValue: String, val label: String) {
    BLOCK("block", "Block"),
    ALLOW("allow", "Allow")
}

data class GatewayFormState(
    val name: String = "",
    val action: GatewayAction = GatewayAction.BLOCK,
    val domain: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class GatewayUiState(
    val rules: UiState<List<GatewayRule>> = UiState.Loading,
    val form: GatewayFormState? = null,
    val deletingId: String? = null
)

private val DOMAIN_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$")

fun validateGatewayForm(form: GatewayFormState): String? = when {
    form.name.isBlank() -> "Policy name is required"
    form.domain.isBlank() -> "Domain is required"
    !form.domain.trim().matches(DOMAIN_REGEX) -> "Enter a valid domain, e.g. example.com"
    else -> null
}

/** Builds Cloudflare's Wirefilter expression for a single-domain DNS match - the common case
 *  this form supports. */
fun buildGatewayTraffic(form: GatewayFormState): String = "dns.fqdn == \"${form.domain.trim()}\""

/** Zero Trust Gateway: DNS policies (block/allow a single domain) only - see
 *  CapabilityRegistry's migrationHint for what's out of scope. */
class GatewayViewModel(
    private val accountId: String,
    private val repository: GatewayRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GatewayUiState())
    val uiState: StateFlow<GatewayUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(rules = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listRules(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(rules = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(rules = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = GatewayFormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (GatewayFormState) -> GatewayFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateGatewayForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val rule = GatewayRuleCreate(name = form.name.trim(), action = form.action.apiValue, traffic = buildGatewayTraffic(form))
            when (val result = repository.createRule(accountId, rule)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    refresh()
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(rule: GatewayRule) {
        _uiState.update { it.copy(deletingId = rule.id) }
        viewModelScope.launch {
            repository.deleteRule(accountId, rule.id)
            _uiState.update { it.copy(deletingId = null) }
            refresh()
        }
    }
}
