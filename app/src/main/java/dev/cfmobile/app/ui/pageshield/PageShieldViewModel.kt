package dev.cfmobile.app.ui.pageshield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.PageShieldConnection
import dev.cfmobile.app.data.remote.dto.PageShieldPolicy
import dev.cfmobile.app.data.remote.dto.PageShieldPolicyWrite
import dev.cfmobile.app.data.remote.dto.PageShieldScript
import dev.cfmobile.app.data.repository.PageShieldRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PageShieldTab { SCRIPTS, CONNECTIONS, POLICIES }

/** What a policy does with a script that matches its expression. */
val PAGE_SHIELD_ACTIONS = listOf(
    "allow" to "Allow",
    "log" to "Log only"
)

data class PageShieldPolicyForm(
    val editingId: String? = null,
    val description: String = "",
    val expression: String = "",
    val value: String = "",
    val action: String = "allow",
    val enabled: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class PageShieldUiState(
    val tab: PageShieldTab = PageShieldTab.SCRIPTS,
    val policies: UiState<List<PageShieldPolicy>> = UiState.Loading,
    val policyForm: PageShieldPolicyForm? = null,
    val deletingPolicyId: String? = null,
    val policyError: String? = null,
    val isEnabled: Boolean? = null,
    val isTogglingEnabled: Boolean = false,
    val settingsError: String? = null,
    val scripts: UiState<List<PageShieldScript>> = UiState.Loading,
    val connections: UiState<List<PageShieldConnection>> = UiState.Loading,
    val isRefreshing: Boolean = false
)

/**
 * A policy's `expression` picks the pages it applies to and `value` picks the scripts allowed
 * on them - both are Cloudflare filter expressions, and a policy with an empty value would
 * allow nothing at all, which is why both are required.
 */
fun validatePageShieldPolicy(form: PageShieldPolicyForm): String? = when {
    form.expression.isBlank() -> "A page expression is required, e.g. http.host eq \"example.com\""
    form.value.isBlank() -> "A script expression is required - an empty one would allow no scripts"
    else -> null
}

fun pageShieldActionLabel(action: String?): String =
    PAGE_SHIELD_ACTIONS.firstOrNull { it.first == action }?.second ?: action.orEmpty()

fun policyFormOf(policy: PageShieldPolicy): PageShieldPolicyForm = PageShieldPolicyForm(
    editingId = policy.id,
    description = policy.description.orEmpty(),
    expression = policy.expression,
    value = policy.value,
    action = policy.action,
    enabled = policy.enabled
)

/** Cloudflare scores JavaScript integrity from 1-100; below ~10 is what its own dashboard
 *  treats as worth flagging. Returns null when the plan didn't provide a score. */
fun scriptIntegrityLabel(script: PageShieldScript): String? =
    script.jsIntegrityScore?.let { score ->
        if (score < 10) "Integrity score $score - review" else "Integrity score $score"
    }

class PageShieldViewModel(
    private val zoneId: String,
    private val repository: PageShieldRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PageShieldUiState())
    val uiState: StateFlow<PageShieldUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun selectTab(tab: PageShieldTab) = _uiState.update { it.copy(tab = tab) }

    fun refresh() = load(isRefresh = true)

    /** Settings, scripts, and connections load sequentially in one coroutine - three parallel
     *  launches would race at the HTTP layer for no benefit. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(scripts = UiState.Loading, connections = UiState.Loading)
        }
        viewModelScope.launch {
            when (val settings = repository.getSettings(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isEnabled = settings.data.enabled, settingsError = null) }
                is ApiResult.Failure -> _uiState.update { it.copy(settingsError = settings.message) }
            }
            when (val scripts = repository.listScripts(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(scripts = UiState.Data(scripts.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(scripts = UiState.Error(ErrorClassifier.classify(scripts))) }
            }
            when (val connections = repository.listConnections(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(connections = UiState.Data(connections.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(connections = UiState.Error(ErrorClassifier.classify(connections))) }
            }
            when (val policies = repository.listPolicies(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(policies = UiState.Data(policies.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(policies = UiState.Error(ErrorClassifier.classify(policies)))
                }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun openPolicyForm() = _uiState.update { it.copy(policyForm = PageShieldPolicyForm()) }

    fun openPolicyForm(policy: PageShieldPolicy) = _uiState.update { it.copy(policyForm = policyFormOf(policy)) }

    fun closePolicyForm() = _uiState.update { it.copy(policyForm = null) }

    fun updatePolicyForm(transform: (PageShieldPolicyForm) -> PageShieldPolicyForm) =
        _uiState.update { state -> state.policyForm?.let { state.copy(policyForm = transform(it)) } ?: state }

    fun savePolicy() {
        val form = _uiState.value.policyForm ?: return
        val validationError = validatePageShieldPolicy(form)
        if (validationError != null) {
            updatePolicyForm { it.copy(error = validationError) }
            return
        }
        updatePolicyForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val write = PageShieldPolicyWrite(
                action = form.action,
                description = form.description.trim().ifBlank { null },
                enabled = form.enabled,
                expression = form.expression.trim(),
                value = form.value.trim()
            )
            val editingId = form.editingId
            val result = if (editingId != null) {
                repository.updatePolicy(zoneId, editingId, write)
            } else {
                repository.createPolicy(zoneId, write)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(policyForm = null) }
                    reloadPolicies()
                }
                is ApiResult.Failure -> updatePolicyForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deletePolicy(policy: PageShieldPolicy) {
        _uiState.update { it.copy(deletingPolicyId = policy.id, policyError = null) }
        viewModelScope.launch {
            when (val result = repository.deletePolicy(zoneId, policy.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingPolicyId = null) }
                    reloadPolicies()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(deletingPolicyId = null, policyError = result.message)
                }
            }
        }
    }

    private suspend fun reloadPolicies() {
        when (val result = repository.listPolicies(zoneId)) {
            is ApiResult.Success -> _uiState.update { it.copy(policies = UiState.Data(result.data)) }
            is ApiResult.Failure -> _uiState.update {
                it.copy(policies = UiState.Error(ErrorClassifier.classify(result)))
            }
        }
    }

    fun dismissPolicyError() = _uiState.update { it.copy(policyError = null) }

    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isTogglingEnabled = true, settingsError = null) }
        viewModelScope.launch {
            when (val result = repository.setEnabled(zoneId, enabled)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isEnabled = result.data.enabled, isTogglingEnabled = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    // Leave the switch showing the server's last known value rather than the
                    // one the user tried to set, so it never lies about what's live.
                    it.copy(isTogglingEnabled = false, settingsError = result.message)
                }
            }
        }
    }
}
