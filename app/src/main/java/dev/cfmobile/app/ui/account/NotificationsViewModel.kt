package dev.cfmobile.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.NotificationHistoryEntry
import dev.cfmobile.app.data.remote.dto.NotificationPolicy
import dev.cfmobile.app.data.remote.dto.NotificationWebhook
import dev.cfmobile.app.data.repository.NotificationsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotificationsTab(val label: String) {
    POLICIES("Alerts"),
    DESTINATIONS("Destinations"),
    HISTORY("History")
}

data class WebhookFormState(
    val name: String = "",
    val url: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class NotificationsUiState(
    val tab: NotificationsTab = NotificationsTab.POLICIES,
    val policies: UiState<List<NotificationPolicy>> = UiState.Loading,
    val webhooks: UiState<List<NotificationWebhook>> = UiState.Loading,
    val history: UiState<List<NotificationHistoryEntry>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val busyId: String? = null,
    val deletingId: String? = null,
    val webhookForm: WebhookFormState? = null,
    val error: String? = null
)

fun validateWebhookForm(form: WebhookFormState): String? {
    if (form.name.isBlank()) return "Name is required"
    val url = form.url.trim()
    // Cloudflare posts the alert to this URL, so anything but HTTPS would put the payload on
    // the wire in the clear.
    if (!url.startsWith("https://")) return "The webhook URL must be https://"
    if (url.length <= "https://".length) return "Enter the full webhook URL"
    return null
}

/** "delivered 2 Jan" / "never delivered" - whether a destination is actually working. */
fun webhookSummary(webhook: NotificationWebhook): String = when {
    webhook.lastSuccess != null -> "Last delivered ${webhook.lastSuccess}"
    webhook.lastFailure != null -> "Last attempt failed ${webhook.lastFailure}"
    else -> "Never used"
}

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

    fun selectTab(tab: NotificationsTab) = _uiState.update { it.copy(tab = tab) }

    /** All three lists load in one coroutine: separate launches would race each other at the
     *  HTTP dispatcher, and the tabs are cheap enough to fetch together. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(policies = UiState.Loading, webhooks = UiState.Loading, history = UiState.Loading)
        }
        viewModelScope.launch {
            when (val result = repository.listPolicies(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(policies = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(policies = UiState.Error(ErrorClassifier.classify(result)))
                }
            }
            when (val result = repository.listWebhooks(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(webhooks = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(webhooks = UiState.Error(ErrorClassifier.classify(result)))
                }
            }
            when (val result = repository.listHistory(accountId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(history = UiState.Data(result.data), isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(history = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openWebhookForm() = _uiState.update { it.copy(webhookForm = WebhookFormState()) }

    fun closeWebhookForm() = _uiState.update { it.copy(webhookForm = null) }

    fun updateWebhookForm(transform: (WebhookFormState) -> WebhookFormState) =
        _uiState.update { state -> state.webhookForm?.let { state.copy(webhookForm = transform(it)) } ?: state }

    fun saveWebhook() {
        val form = _uiState.value.webhookForm ?: return
        val validationError = validateWebhookForm(form)
        if (validationError != null) {
            updateWebhookForm { it.copy(error = validationError) }
            return
        }
        updateWebhookForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createWebhook(accountId, form.name.trim(), form.url.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(webhookForm = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateWebhookForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteWebhook(webhook: NotificationWebhook) {
        _uiState.update { it.copy(deletingId = webhook.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteWebhook(accountId, webhook.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
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
