package dev.cfmobile.app.ui.zonesecurity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.LockdownConfiguration
import dev.cfmobile.app.data.remote.dto.UserAgentRule
import dev.cfmobile.app.data.remote.dto.UserAgentRuleConfiguration
import dev.cfmobile.app.data.remote.dto.UserAgentRuleWrite
import dev.cfmobile.app.data.remote.dto.ZoneLockdown
import dev.cfmobile.app.data.remote.dto.ZoneLockdownWrite
import dev.cfmobile.app.data.repository.ZoneFirewallLegacyRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LegacyFirewallTab(val label: String) {
    LOCKDOWNS("Lockdown"),
    USER_AGENTS("User Agents")
}

/** What a User Agent Blocking rule does with a match. */
val UA_RULE_MODES = listOf(
    "block" to "Block",
    "challenge" to "Legacy CAPTCHA",
    "managed_challenge" to "Managed Challenge",
    "js_challenge" to "JS Challenge"
)

data class LockdownFormState(
    val editingId: String? = null,
    val description: String = "",
    /** One URL pattern per line, the same paste-friendly shape the Gateway list form uses. */
    val urls: String = "",
    /** One address, CIDR range, or two-letter country code per line. */
    val sources: String = "",
    val paused: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class UserAgentFormState(
    val editingId: String? = null,
    val description: String = "",
    val userAgent: String = "",
    val mode: String = "block",
    val paused: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class LegacyFirewallUiState(
    val tab: LegacyFirewallTab = LegacyFirewallTab.LOCKDOWNS,
    val lockdowns: UiState<List<ZoneLockdown>> = UiState.Loading,
    val userAgentRules: UiState<List<UserAgentRule>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val lockdownForm: LockdownFormState? = null,
    val userAgentForm: UserAgentFormState? = null,
    val deletingId: String? = null,
    val error: String? = null
)

/** Splits a pasted block into entries, dropping blanks and duplicates. */
fun parseLines(raw: String): List<String> =
    raw.split('\n', ',').map { it.trim() }.filter { it.isNotBlank() }.distinct()

/**
 * A lockdown source is an IP, a CIDR range, or a two-letter country code - Cloudflare picks the
 * target from the shape of the value, so this app has to as well.
 */
fun lockdownConfigurationOf(value: String): LockdownConfiguration {
    val trimmed = value.trim()
    val target = when {
        trimmed.contains("/") -> "ip_range"
        trimmed.length == 2 && trimmed.all { it.isLetter() } -> "country"
        else -> "ip"
    }
    return LockdownConfiguration(target = target, value = if (target == "country") trimmed.uppercase() else trimmed)
}

fun validateLockdownForm(form: LockdownFormState): String? = when {
    parseLines(form.urls).isEmpty() -> "Add at least one URL pattern, one per line"
    parseLines(form.sources).isEmpty() -> "Add at least one address, range, or country code"
    else -> null
}

fun buildLockdownWrite(form: LockdownFormState): ZoneLockdownWrite = ZoneLockdownWrite(
    description = form.description.trim().ifBlank { null },
    paused = form.paused,
    urls = parseLines(form.urls),
    configurations = parseLines(form.sources).map(::lockdownConfigurationOf)
)

fun lockdownFormOf(lockdown: ZoneLockdown): LockdownFormState = LockdownFormState(
    editingId = lockdown.id,
    description = lockdown.description.orEmpty(),
    urls = lockdown.urls.joinToString("\n"),
    sources = lockdown.configurations.joinToString("\n") { it.value },
    paused = lockdown.paused
)

/** "2 URLs · 3 sources", which is what the row has room for. */
fun lockdownSummary(lockdown: ZoneLockdown): String {
    val urls = lockdown.urls.size
    val sources = lockdown.configurations.size
    return "$urls URL${if (urls == 1) "" else "s"} · $sources source${if (sources == 1) "" else "s"}"
}

fun validateUserAgentForm(form: UserAgentFormState): String? = when {
    form.userAgent.isBlank() -> "A user agent string is required"
    // Cloudflare matches the header exactly here; a wildcard silently matches nothing.
    form.userAgent.contains("*") -> "This rule matches the header exactly - use a WAF custom rule for wildcards"
    else -> null
}

fun buildUserAgentWrite(form: UserAgentFormState): UserAgentRuleWrite = UserAgentRuleWrite(
    mode = form.mode,
    configuration = UserAgentRuleConfiguration(target = "ua", value = form.userAgent.trim()),
    description = form.description.trim().ifBlank { null },
    paused = form.paused
)

fun userAgentFormOf(rule: UserAgentRule): UserAgentFormState = UserAgentFormState(
    editingId = rule.id,
    description = rule.description.orEmpty(),
    userAgent = rule.configuration.value,
    mode = rule.mode,
    paused = rule.paused
)

fun uaModeLabel(mode: String): String =
    UA_RULE_MODES.firstOrNull { it.first == mode }?.second ?: mode

class LegacyFirewallViewModel(
    private val zoneId: String,
    private val repository: ZoneFirewallLegacyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LegacyFirewallUiState())
    val uiState: StateFlow<LegacyFirewallUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    fun selectTab(tab: LegacyFirewallTab) = _uiState.update { it.copy(tab = tab) }

    /** Both lists load sequentially in one coroutine: the tabs share a refresh, and two
     *  launches would race at the HTTP layer for no benefit. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(lockdowns = UiState.Loading, userAgentRules = UiState.Loading)
        }
        viewModelScope.launch {
            when (val lockdowns = repository.listLockdowns(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(lockdowns = UiState.Data(lockdowns.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(lockdowns = UiState.Error(ErrorClassifier.classify(lockdowns)))
                }
            }
            when (val rules = repository.listUserAgentRules(zoneId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(userAgentRules = UiState.Data(rules.data), isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(userAgentRules = UiState.Error(ErrorClassifier.classify(rules)), isRefreshing = false)
                }
            }
        }
    }

    fun openLockdownForm() = _uiState.update { it.copy(lockdownForm = LockdownFormState()) }

    fun openLockdownForm(lockdown: ZoneLockdown) =
        _uiState.update { it.copy(lockdownForm = lockdownFormOf(lockdown)) }

    fun closeLockdownForm() = _uiState.update { it.copy(lockdownForm = null) }

    fun updateLockdownForm(transform: (LockdownFormState) -> LockdownFormState) =
        _uiState.update { state -> state.lockdownForm?.let { state.copy(lockdownForm = transform(it)) } ?: state }

    fun saveLockdown() {
        val form = _uiState.value.lockdownForm ?: return
        val validationError = validateLockdownForm(form)
        if (validationError != null) {
            updateLockdownForm { it.copy(error = validationError) }
            return
        }
        updateLockdownForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val write = buildLockdownWrite(form)
            val editingId = form.editingId
            val result = if (editingId != null) {
                repository.updateLockdown(zoneId, editingId, write)
            } else {
                repository.createLockdown(zoneId, write)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(lockdownForm = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateLockdownForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteLockdown(lockdown: ZoneLockdown) {
        _uiState.update { it.copy(deletingId = lockdown.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteLockdown(zoneId, lockdown.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    fun openUserAgentForm() = _uiState.update { it.copy(userAgentForm = UserAgentFormState()) }

    fun openUserAgentForm(rule: UserAgentRule) =
        _uiState.update { it.copy(userAgentForm = userAgentFormOf(rule)) }

    fun closeUserAgentForm() = _uiState.update { it.copy(userAgentForm = null) }

    fun updateUserAgentForm(transform: (UserAgentFormState) -> UserAgentFormState) =
        _uiState.update { state -> state.userAgentForm?.let { state.copy(userAgentForm = transform(it)) } ?: state }

    fun saveUserAgentRule() {
        val form = _uiState.value.userAgentForm ?: return
        val validationError = validateUserAgentForm(form)
        if (validationError != null) {
            updateUserAgentForm { it.copy(error = validationError) }
            return
        }
        updateUserAgentForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val write = buildUserAgentWrite(form)
            val editingId = form.editingId
            val result = if (editingId != null) {
                repository.updateUserAgentRule(zoneId, editingId, write)
            } else {
                repository.createUserAgentRule(zoneId, write)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(userAgentForm = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateUserAgentForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteUserAgentRule(rule: UserAgentRule) {
        _uiState.update { it.copy(deletingId = rule.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteUserAgentRule(zoneId, rule.id)) {
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
