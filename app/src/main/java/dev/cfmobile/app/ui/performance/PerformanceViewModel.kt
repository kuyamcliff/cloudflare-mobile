package dev.cfmobile.app.ui.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.ArgoSetting
import dev.cfmobile.app.data.remote.dto.CacheSetting
import dev.cfmobile.app.data.remote.dto.ManagedHeader
import dev.cfmobile.app.data.remote.dto.ManagedHeaders
import dev.cfmobile.app.data.remote.dto.UrlNormalization
import dev.cfmobile.app.data.repository.PerformanceRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How Cloudflare normalizes incoming URLs before rules and the cache see them. */
val URL_NORMALIZATION_TYPES = listOf(
    "cloudflare" to "Cloudflare",
    "rfc3986" to "RFC 3986"
)

val URL_NORMALIZATION_SCOPES = listOf(
    "incoming" to "Incoming URLs only",
    "both" to "Incoming URLs and rules"
)

/**
 * Each toggle on this screen has its own endpoint and its own response shape, so the state
 * carries one nullable value per control rather than a single settings map. Null means the
 * zone's plan didn't return that control at all - Argo and Cache Reserve are paid add-ons -
 * and a control the account can't have simply isn't rendered.
 */
data class PerformanceUiState(
    val smartRouting: String? = null,
    val tieredCaching: String? = null,
    val cacheReserve: String? = null,
    val regionalTieredCache: String? = null,
    val smartTieredCache: String? = null,
    val managedHeaders: ManagedHeaders? = null,
    val urlNormalization: UiState<UrlNormalization> = UiState.Loading,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val unavailable: List<String> = emptyList(),
    val error: String? = null
)

/** Cloudflare states these as the strings "on" and "off", like a zone setting. */
fun isOn(value: String?): Boolean = value == "on"

fun onOff(enabled: Boolean): String = if (enabled) "on" else "off"

/** Turns a managed header id like "add_true_client_ip_headers" into something readable. */
fun managedHeaderLabel(header: ManagedHeader): String =
    header.id.split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

/** A managed header that conflicts with one of your own Transform Rules is worth flagging -
 *  Cloudflare will refuse to enable it. */
fun managedHeaderConflict(header: ManagedHeader): String? =
    header.conflictsWith?.takeIf { header.hasConflict == true && it.isNotEmpty() }
        ?.let { "Conflicts with ${it.joinToString(", ")}" }

class PerformanceViewModel(
    private val zoneId: String,
    private val repository: PerformanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PerformanceUiState())
    val uiState: StateFlow<PerformanceUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /**
     * Seven independent endpoints, loaded sequentially in one coroutine. A control the zone's
     * plan doesn't include answers with an error, which is recorded as unavailable rather than
     * failing the whole screen - the rest of the controls are still usable.
     */
    fun load() {
        _uiState.update { it.copy(isLoading = true, unavailable = emptyList()) }
        viewModelScope.launch {
            val unavailable = mutableListOf<String>()

            when (val result = repository.getSmartRouting(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(smartRouting = result.data.value) }
                is ApiResult.Failure -> unavailable.add("Argo Smart Routing")
            }
            when (val result = repository.getTieredCaching(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(tieredCaching = result.data.value) }
                is ApiResult.Failure -> unavailable.add("Tiered Caching")
            }
            when (val result = repository.getCacheReserve(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(cacheReserve = result.data.value) }
                is ApiResult.Failure -> unavailable.add("Cache Reserve")
            }
            when (val result = repository.getRegionalTieredCache(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(regionalTieredCache = result.data.value) }
                is ApiResult.Failure -> unavailable.add("Regional Tiered Cache")
            }
            when (val result = repository.getSmartTieredCache(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(smartTieredCache = result.data.value) }
                is ApiResult.Failure -> unavailable.add("Smart Tiered Cache Topology")
            }
            when (val result = repository.getManagedHeaders(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(managedHeaders = result.data) }
                is ApiResult.Failure -> unavailable.add("Managed Transforms")
            }
            when (val result = repository.getUrlNormalization(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(urlNormalization = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(urlNormalization = UiState.Error(ErrorClassifier.classify(result)))
                }
            }

            _uiState.update { it.copy(isLoading = false, unavailable = unavailable.toList()) }
        }
    }

    fun setSmartRouting(enabled: Boolean) = save(
        write = { repository.setSmartRouting(zoneId, onOff(enabled)) },
        apply = { state, value -> state.copy(smartRouting = value) }
    )

    fun setTieredCaching(enabled: Boolean) = save(
        write = { repository.setTieredCaching(zoneId, onOff(enabled)) },
        apply = { state, value -> state.copy(tieredCaching = value) }
    )

    fun setCacheReserve(enabled: Boolean) = saveCache(
        write = { repository.setCacheReserve(zoneId, onOff(enabled)) },
        apply = { state, value -> state.copy(cacheReserve = value) }
    )

    fun setRegionalTieredCache(enabled: Boolean) = saveCache(
        write = { repository.setRegionalTieredCache(zoneId, onOff(enabled)) },
        apply = { state, value -> state.copy(regionalTieredCache = value) }
    )

    fun setSmartTieredCache(enabled: Boolean) = saveCache(
        write = { repository.setSmartTieredCache(zoneId, onOff(enabled)) },
        apply = { state, value -> state.copy(smartTieredCache = value) }
    )

    /** A failed write leaves the shown value alone, so the switch never claims a change
     *  Cloudflare rejected. */
    private fun save(
        write: suspend () -> ApiResult<ArgoSetting>,
        apply: (PerformanceUiState, String?) -> PerformanceUiState
    ) {
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = write()) {
                is ApiResult.Success -> _uiState.update { apply(it, result.data.value).copy(isSaving = false) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    private fun saveCache(
        write: suspend () -> ApiResult<CacheSetting>,
        apply: (PerformanceUiState, String?) -> PerformanceUiState
    ) {
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = write()) {
                is ApiResult.Success -> _uiState.update { apply(it, result.data.value).copy(isSaving = false) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    /**
     * Managed headers are replaced wholesale on write, so this sends the document back with
     * one header's flag changed. Sending only the changed header would clear the others.
     */
    fun setManagedHeader(header: ManagedHeader, enabled: Boolean, isRequest: Boolean) {
        val current = _uiState.value.managedHeaders ?: return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            fun List<ManagedHeader>.toggled() =
                map { if (it.id == header.id) it.copy(enabled = enabled) else it }
            val updated = if (isRequest) {
                current.copy(requestHeaders = current.requestHeaders.toggled())
            } else {
                current.copy(responseHeaders = current.responseHeaders.toggled())
            }
            when (val result = repository.setManagedHeaders(zoneId, updated)) {
                is ApiResult.Success -> _uiState.update { it.copy(managedHeaders = result.data, isSaving = false) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun setUrlNormalization(type: String? = null, scope: String? = null) {
        val current = (_uiState.value.urlNormalization as? UiState.Data)?.value ?: return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.setUrlNormalization(
                zoneId,
                type = type ?: current.type ?: URL_NORMALIZATION_TYPES.first().first,
                scope = scope ?: current.scope ?: URL_NORMALIZATION_SCOPES.first().first
            )
            when (result) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(urlNormalization = UiState.Data(result.data), isSaving = false)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
