package dev.cfmobile.app.ui.securityevents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.FirewallEvent
import dev.cfmobile.app.data.repository.SecurityEventsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EventWindow(val hours: Long, val label: String) {
    LAST_HOUR(1, "Last hour"),
    LAST_24_HOURS(24, "Last 24 hours"),
    LAST_7_DAYS(24 * 7, "Last 7 days")
}

data class SecurityEventsUiState(
    val events: UiState<List<FirewallEvent>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val window: EventWindow = EventWindow.LAST_24_HOURS
)

/** "Block", "Managed challenge", ... from Cloudflare's raw action strings. */
fun eventActionLabel(event: FirewallEvent): String =
    event.action?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Unknown action"

/** GET host + path, skipping whatever the plan didn't return. */
fun eventRequestLabel(event: FirewallEvent): String? {
    val target = listOfNotNull(event.clientRequestHTTPHost, event.clientRequestPath)
        .joinToString("")
        .takeIf { it.isNotBlank() }
        ?: return null
    return event.clientRequestHTTPMethodName?.let { "$it $target" } ?: target
}

/** Where the request came from: IP, country, and ASN when present. */
fun eventOriginLabel(event: FirewallEvent): String? = listOfNotNull(
    event.clientIP,
    event.clientCountryName,
    event.clientAsn?.let { "AS$it" }
).joinToString(" · ").takeIf { it.isNotBlank() }

class SecurityEventsViewModel(
    private val zoneId: String,
    private val repository: SecurityEventsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityEventsUiState())
    val uiState: StateFlow<SecurityEventsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    fun selectWindow(window: EventWindow) {
        if (window == _uiState.value.window) return
        _uiState.update { it.copy(window = window) }
        load(isRefresh = false)
    }

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(events = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listEvents(zoneId, sinceHours = _uiState.value.window.hours)) {
                is ApiResult.Success -> _uiState.update { it.copy(events = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(events = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }
}
