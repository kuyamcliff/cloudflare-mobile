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

/**
 * Which Gateway engine evaluates a policy. Each one matches on a different field, so the form
 * asks for a hostname, a URL host, or a destination IP accordingly:
 *
 *  - DNS resolves names, so it matches the queried FQDN
 *  - HTTP proxies requests, so it matches the request's host
 *  - Network sees L4 traffic, so it matches the destination IP
 */
enum class GatewayFilter(val apiValue: String, val label: String, val fieldLabel: String, val placeholder: String) {
    DNS("dns", "DNS", "Domain", "example.com"),
    HTTP("http", "HTTP", "Host", "example.com"),
    NETWORK("l4", "Network", "Destination IP", "203.0.113.10")
}

data class GatewayFormState(
    val name: String = "",
    val action: GatewayAction = GatewayAction.BLOCK,
    val filter: GatewayFilter = GatewayFilter.DNS,
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

private val IPV4_REGEX = Regex("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$")

fun validateGatewayForm(form: GatewayFormState): String? {
    if (form.name.isBlank()) return "Policy name is required"
    val value = form.domain.trim()
    if (value.isBlank()) return "${form.filter.fieldLabel} is required"
    return when (form.filter) {
        GatewayFilter.DNS, GatewayFilter.HTTP ->
            if (value.matches(DOMAIN_REGEX)) null else "Enter a valid hostname, e.g. example.com"
        // Only IPv4 is accepted here; a CIDR range or IPv6 destination needs the full
        // expression editor this form doesn't have.
        GatewayFilter.NETWORK ->
            if (value.matches(IPV4_REGEX)) null else "Enter a single IPv4 address, e.g. 203.0.113.10"
    }
}

/** Builds Cloudflare's Wirefilter expression for the single-value match each engine supports
 *  here. Anything richer - categories, identity, device posture - is out of scope for this
 *  form and disclosed as such. */
fun buildGatewayTraffic(form: GatewayFormState): String {
    val value = form.domain.trim()
    return when (form.filter) {
        GatewayFilter.DNS -> "dns.fqdn == \"$value\""
        GatewayFilter.HTTP -> "http.request.host == \"$value\""
        GatewayFilter.NETWORK -> "net.dst.ip == $value"
    }
}

/** Zero Trust Gateway: block/allow policies for the DNS, HTTP, and network engines, each
 *  matching a single value - see CapabilityRegistry's migrationHint for what's out of scope. */
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
            val rule = GatewayRuleCreate(
                name = form.name.trim(),
                action = form.action.apiValue,
                traffic = buildGatewayTraffic(form),
                filters = listOf(form.filter.apiValue)
            )
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
