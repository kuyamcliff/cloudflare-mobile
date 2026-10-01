package dev.cfmobile.app.ui.zonedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.data.repository.ZoneSettingsRepository
import dev.cfmobile.app.data.repository.ZonesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A zone setting shown as a switch on the zone hub. */
data class QuickToggle(
    val settingId: String,
    val label: String,
    val detail: String,
    val onValue: String,
    val offValue: String,
    /** Null while loading or when the token can't read it. */
    val value: String? = null,
    val saving: Boolean = false,
    val error: String? = null
) {
    val isOn: Boolean get() = value == onValue
}

class ZoneMenuViewModel(
    private val zoneId: String,
    private val repository: ZonesRepository,
    private val settings: ZoneSettingsRepository? = null,
    /** False when the token's policies rule out purging; null when unknown. */
    private val purgeAllowed: suspend () -> Boolean? = { null }
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<CfZone>>(UiState.Loading)
    val state: StateFlow<UiState<CfZone>> = _state.asStateFlow()

    private val _lastUpdatedAt = MutableStateFlow<Long?>(null)
    val lastUpdatedAt: StateFlow<Long?> = _lastUpdatedAt.asStateFlow()

    private val _toggles = MutableStateFlow(
        listOf(
            QuickToggle("development_mode", "Development mode", "Bypass the cache for 3 hours", "on", "off"),
            QuickToggle("security_level", "Under Attack mode", "Challenge every visitor", "under_attack", "medium"),
            QuickToggle("always_use_https", "Always Use HTTPS", "Redirect HTTP to HTTPS", "on", "off")
        )
    )
    val toggles: StateFlow<List<QuickToggle>> = _toggles.asStateFlow()

    private val _purgeBlocked = MutableStateFlow(false)
    /** The token can't purge this zone's cache, so the control explains instead of failing. */
    val purgeBlocked: StateFlow<Boolean> = _purgeBlocked.asStateFlow()

    private val _purge = MutableStateFlow<String?>(null)
    /** Result line of the last purge, shown under the button. */
    val purge: StateFlow<String?> = _purge.asStateFlow()

    init {
        load()
        viewModelScope.launch { _purgeBlocked.value = purgeAllowed() == false }
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = repository.getZone(zoneId)) {
                is ApiResult.Success -> {
                    _lastUpdatedAt.value = System.currentTimeMillis()
                    UiState.Data(result.data)
                }
                is ApiResult.Failure -> UiState.Error(ErrorClassifier.classify(result))
            }
        }
        loadToggles()
    }

    private fun loadToggles() {
        val repo = settings ?: return
        viewModelScope.launch {
            val reads = _toggles.value.map { t -> async { t.settingId to repo.getSetting(zoneId, t.settingId) } }.map { it.await() }.toMap()
            _toggles.update { list ->
                list.map { t ->
                    when (val r = reads[t.settingId]) {
                        is ApiResult.Success -> t.copy(value = r.data, error = null)
                        is ApiResult.Failure -> t.copy(value = null, error = "Unavailable")
                        null -> t
                    }
                }
            }
        }
    }

    fun setToggle(settingId: String, on: Boolean) {
        val repo = settings ?: return
        val t = _toggles.value.firstOrNull { it.settingId == settingId } ?: return
        val target = if (on) t.onValue else t.offValue
        _toggles.update { list -> list.map { if (it.settingId == settingId) it.copy(saving = true, error = null) else it } }
        viewModelScope.launch {
            val r = repo.setSetting(zoneId, settingId, target)
            _toggles.update { list ->
                list.map {
                    if (it.settingId != settingId) it
                    else when (r) {
                        is ApiResult.Success -> it.copy(value = r.data, saving = false)
                        is ApiResult.Failure -> it.copy(saving = false, error = r.message)
                    }
                }
            }
        }
    }

    fun purgeEverything() {
        val repo = settings ?: return
        _purge.value = "Purging"
        viewModelScope.launch {
            _purge.value = when (val r = repo.purgeEverything(zoneId)) {
                is ApiResult.Success -> "Cache purged. New requests go to your origin."
                is ApiResult.Failure -> "Purge failed: ${r.message}"
            }
        }
    }
}
