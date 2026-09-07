package dev.cfmobile.app.ui.emailrouting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.EmailCatchAll
import dev.cfmobile.app.data.remote.dto.EmailDestinationAddress
import dev.cfmobile.app.data.remote.dto.EmailRoutingRule
import dev.cfmobile.app.data.repository.EmailRoutingRepository
import dev.cfmobile.app.data.repository.ZonesRepository
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

enum class EmailRoutingTab(val label: String) {
    RULES("Rules"),
    DESTINATIONS("Destinations")
}

data class DestinationFormState(
    val email: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class EmailRoutingUiState(
    val tab: EmailRoutingTab = EmailRoutingTab.RULES,
    val rules: UiState<List<EmailRoutingRule>> = UiState.Loading,
    val destinations: UiState<List<EmailDestinationAddress>> = UiState.Loading,
    val catchAll: EmailCatchAll? = null,
    val isRefreshing: Boolean = false,
    val isEnabled: Boolean? = null,
    val statusLabel: String? = null,
    val form: EmailRoutingFormState? = null,
    val destinationForm: DestinationFormState? = null,
    val deletingTag: String? = null,
    val isSavingCatchAll: Boolean = false,
    val error: String? = null
)

fun validateDestination(email: String): String? = when {
    email.isBlank() -> "Destination address is required"
    !email.trim().matches(EMAIL_REGEX) -> "Enter a valid email address"
    else -> null
}

/** Cloudflare only delivers to an address once its owner clicks the verification link, so an
 *  unverified one is worth calling out rather than listing as if it worked. */
fun destinationStatus(address: EmailDestinationAddress): String =
    if (address.verified.isNullOrBlank()) "Not verified - check the inbox for Cloudflare's link" else "Verified"

/** What happens to mail with no matching rule. */
fun catchAllSummary(catchAll: EmailCatchAll?): String {
    if (catchAll == null || !catchAll.enabled) return "Catch-all off - unmatched mail is rejected"
    val action = catchAll.actions.firstOrNull()
    val forwardTo = action?.value?.joinToString(", ")?.takeIf { it.isNotBlank() }
    return when {
        action?.type == "forward" && forwardTo != null -> "Catch-all forwards to $forwardTo"
        action?.type == "drop" -> "Catch-all drops unmatched mail silently"
        else -> "Catch-all on"
    }
}

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
    private val repository: EmailRoutingRepository,
    private val zonesRepository: ZonesRepository
) : ViewModel() {

    /** Destination addresses are account-scoped while everything else here is zone-scoped, so
     *  the account has to be discovered from the zone itself. */
    private var accountId: String? = null

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
            when (val catchAll = repository.getCatchAll(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(catchAll = catchAll.data) }
                // Also context rather than content: a zone that has never had a catch-all
                // answers with an error, which isn't worth a red screen.
                is ApiResult.Failure -> Unit
            }
            loadDestinations()
        }
    }

    fun selectTab(tab: EmailRoutingTab) = _uiState.update { it.copy(tab = tab) }

    private suspend fun loadDestinations() {
        val account = accountId ?: when (val zone = zonesRepository.getZone(zoneId)) {
            is ApiResult.Success -> zone.data.account?.id
            is ApiResult.Failure -> null
        }
        if (account == null) {
            // The zone lookup is what tells us the account; without it there is nothing to
            // list, and saying so beats an empty list that looks like "no destinations".
            _uiState.update {
                it.copy(
                    destinations = UiState.Error(
                        ErrorClassifier.classify(
                            ApiResult.Failure("Couldn't work out which account this zone belongs to")
                        )
                    )
                )
            }
            return
        }
        accountId = account
        when (val result = repository.listDestinations(account)) {
            is ApiResult.Success -> _uiState.update { it.copy(destinations = UiState.Data(result.data)) }
            is ApiResult.Failure -> _uiState.update {
                it.copy(destinations = UiState.Error(ErrorClassifier.classify(result)))
            }
        }
    }

    fun openDestinationForm() = _uiState.update { it.copy(destinationForm = DestinationFormState()) }

    fun closeDestinationForm() = _uiState.update { it.copy(destinationForm = null) }

    fun updateDestinationForm(transform: (DestinationFormState) -> DestinationFormState) =
        _uiState.update { state -> state.destinationForm?.let { state.copy(destinationForm = transform(it)) } ?: state }

    fun saveDestination() {
        val form = _uiState.value.destinationForm ?: return
        val account = accountId ?: return
        val validationError = validateDestination(form.email)
        if (validationError != null) {
            updateDestinationForm { it.copy(error = validationError) }
            return
        }
        updateDestinationForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createDestination(account, form.email.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(destinationForm = null) }
                    loadDestinations()
                }
                is ApiResult.Failure -> updateDestinationForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteDestination(address: EmailDestinationAddress) {
        val account = accountId ?: return
        _uiState.update { it.copy(deletingTag = address.tag) }
        viewModelScope.launch {
            repository.deleteDestination(account, address.tag)
            _uiState.update { it.copy(deletingTag = null) }
            loadDestinations()
        }
    }

    /** Turning the catch-all on without a destination would silently drop mail, so the screen
     *  passes the address to forward to and this sends "drop" only when that list is empty. */
    fun setCatchAll(enabled: Boolean, forwardTo: List<String>) {
        _uiState.update { it.copy(isSavingCatchAll = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.setCatchAll(zoneId, enabled, forwardTo)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(catchAll = result.data, isSavingCatchAll = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSavingCatchAll = false, error = result.message)
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

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
