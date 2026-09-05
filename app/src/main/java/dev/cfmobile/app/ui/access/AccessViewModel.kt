package dev.cfmobile.app.ui.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AccessApplication
import dev.cfmobile.app.data.remote.dto.AccessApplicationCreate
import dev.cfmobile.app.data.remote.dto.AccessEmailDomainRule
import dev.cfmobile.app.data.remote.dto.AccessEmailRule
import dev.cfmobile.app.data.remote.dto.AccessPolicyCreate
import dev.cfmobile.app.data.remote.dto.AccessPolicyIncludeRule
import dev.cfmobile.app.data.repository.AccessRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AccessDecision(val apiValue: String, val label: String) {
    ALLOW("allow", "Allow"),
    DENY("deny", "Block")
}

enum class AccessRuleType(val label: String) {
    EMAIL_DOMAIN("Email domain"),
    EMAIL_LIST("Specific email addresses")
}

data class AccessFormState(
    val name: String = "",
    val domain: String = "",
    val decision: AccessDecision = AccessDecision.ALLOW,
    val ruleType: AccessRuleType = AccessRuleType.EMAIL_DOMAIN,
    val ruleValue: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class AccessUiState(
    val applications: UiState<List<AccessApplication>> = UiState.Loading,
    val form: AccessFormState? = null,
    val deletingId: String? = null
)

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val DOMAIN_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$")

fun validateAccessForm(form: AccessFormState): String? = when {
    form.name.isBlank() -> "Application name is required"
    form.domain.isBlank() -> "Domain is required"
    form.ruleValue.isBlank() -> "At least one ${if (form.ruleType == AccessRuleType.EMAIL_DOMAIN) "email domain" else "email address"} is required"
    form.ruleType == AccessRuleType.EMAIL_DOMAIN && !form.ruleValue.trim().matches(DOMAIN_REGEX) -> "Enter a valid domain, e.g. example.com"
    form.ruleType == AccessRuleType.EMAIL_LIST && form.ruleValue.split(",").map { it.trim() }.any { it.isNotEmpty() && !it.matches(EMAIL_REGEX) } ->
        "Enter valid, comma-separated email addresses"
    else -> null
}

fun buildIncludeRules(form: AccessFormState): List<AccessPolicyIncludeRule> = when (form.ruleType) {
    AccessRuleType.EMAIL_DOMAIN -> listOf(AccessPolicyIncludeRule(emailDomain = AccessEmailDomainRule(form.ruleValue.trim())))
    AccessRuleType.EMAIL_LIST -> form.ruleValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        .map { AccessPolicyIncludeRule(email = AccessEmailRule(it)) }
}

/** Zero Trust Access: applications with one inline policy each, covering the common
 *  "allow/block by email domain or specific addresses" cases only - see
 *  CapabilityRegistry's migrationHint for what's out of scope. */
class AccessViewModel(
    private val accountId: String,
    private val repository: AccessRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccessUiState())
    val uiState: StateFlow<AccessUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(applications = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listApplications(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(applications = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(applications = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = AccessFormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (AccessFormState) -> AccessFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateAccessForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val appResult = repository.createApplication(accountId, AccessApplicationCreate(name = form.name.trim(), domain = form.domain.trim()))) {
                is ApiResult.Success -> {
                    val policy = AccessPolicyCreate(
                        name = "${form.name.trim()} policy",
                        decision = form.decision.apiValue,
                        include = buildIncludeRules(form)
                    )
                    when (val policyResult = repository.createPolicy(accountId, appResult.data.id, policy)) {
                        is ApiResult.Success -> {
                            _uiState.update { it.copy(form = null) }
                            refresh()
                        }
                        is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = "Application created, but its policy failed: ${policyResult.message}") }
                    }
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = appResult.message) }
            }
        }
    }

    fun delete(application: AccessApplication) {
        _uiState.update { it.copy(deletingId = application.id) }
        viewModelScope.launch {
            repository.deleteApplication(accountId, application.id)
            _uiState.update { it.copy(deletingId = null) }
            refresh()
        }
    }
}
