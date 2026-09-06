package dev.cfmobile.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.NotificationPolicy
import dev.cfmobile.app.data.repository.NotificationsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val policies: UiState<List<NotificationPolicy>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val busyId: String? = null,
    val deletingId: String? = null,
    val error: String? = null
)

/** Cloudflare's alert types are snake_case identifiers; this makes them readable without
 *  pretending to know every one Cloudflare might add. */
fun alertTypeLabel(alertType: String?): String {
    if (alertType.isNullOrBlank()) return "Alert"
    return alertType.split('_').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
}

/** "3 emails · 1 webhook" - what a policy actually notifies, without listing addresses. */
fun mechanismSummary(policy: NotificationPolicy): String {
    val mechanisms = policy.mechanisms
    fun count(items: List<*>?, singular: String) = items?.size?.takeIf { it > 0 }?.let {
        "$it ${if (it == 1) singular else singular + "s"}"
    }
    return listOfNotNull(
        count(mechanisms?.email, "email"),
        count(mechanisms?.webhooks, "webhook"),
        count(mechanisms?.pagerduty, "PagerDuty service")
    ).joinToString(" · ").ifBlank { "No destinations" }
}

class NotificationsViewModel(
    private val accountId: String,
    private val repository: NotificationsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(policies = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listPolicies(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(policies = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(policies = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    /** Silencing an alert is the reason to open this screen on a phone, so it's a switch rather
     *  than something buried behind a form. */
    fun setEnabled(policy: NotificationPolicy, enabled: Boolean) {
        _uiState.update { it.copy(busyId = policy.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.setEnabled(accountId, policy.id, enabled)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        // Patch the row in place rather than refetching the whole list for a
                        // one-field change.
                        val current = (state.policies as? UiState.Data)?.value
                        state.copy(
                            busyId = null,
                            policies = if (current == null) {
                                state.policies
                            } else {
                                UiState.Data(current.map { if (it.id == policy.id) result.data else it })
                            }
                        )
                    }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(busyId = null, error = result.message) }
            }
        }
    }

    fun delete(policy: NotificationPolicy) {
        _uiState.update { it.copy(deletingId = policy.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.deletePolicy(accountId, policy.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
