package dev.cfmobile.app.ui.zonesettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.repository.ZoneSettingsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ZoneSettingsGroupUiState(
    /** Setting id to its current value, for the settings that loaded. */
    val values: UiState<Map<String, String>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    /** The setting id currently being written, so only that row shows a spinner. */
    val savingId: String? = null,
    val error: String? = null,
    /** Settings this zone's plan doesn't include, so the screen can say so instead of
     *  pretending they're off. */
    val unavailableIds: Set<String> = emptySet()
)

/**
 * Drives any group of [ZoneSettingSpec]s.
 *
 * Two behaviours matter here. Settings a plan doesn't include return an error rather than a
 * value, and that must not blank the whole screen - those rows are marked unavailable and the
 * rest still work. And a failed write leaves the row showing the server's value, never the one
 * the user tried to set, so a toggle never claims a change that didn't happen.
 */
class ZoneSettingsGroupViewModel(
    private val zoneId: String,
    private val repository: ZoneSettingsRepository,
    val specs: List<ZoneSettingSpec>
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZoneSettingsGroupUiState())
    val uiState: StateFlow<ZoneSettingsGroupUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(values = UiState.Loading) }
        viewModelScope.launch {
            val values = mutableMapOf<String, String>()
            val unavailable = mutableSetOf<String>()
            var lastFailure: ApiResult.Failure? = null

            // Sequential rather than parallel: these are small requests, and issuing them one
            // at a time keeps ordering deterministic instead of racing at the HTTP layer.
            specs.forEach { spec ->
                when (val result = repository.getSetting(zoneId, spec.id)) {
                    is ApiResult.Success -> values[spec.id] = result.data
                    is ApiResult.Failure -> {
                        unavailable += spec.id
                        lastFailure = result
                    }
                }
            }

            _uiState.update {
                it.copy(
                    // Only a total failure is an error state: if even one setting answered,
                    // the screen is still useful.
                    values = if (values.isEmpty() && lastFailure != null) {
                        UiState.Error(ErrorClassifier.classify(lastFailure!!))
                    } else {
                        UiState.Data(values.toMap())
                    },
                    unavailableIds = unavailable,
                    isRefreshing = false
                )
            }
        }
    }

    fun setValue(spec: ZoneSettingSpec, value: String) {
        _uiState.update { it.copy(savingId = spec.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.setSetting(zoneId, spec.id, value)) {
                is ApiResult.Success -> _uiState.update { state ->
                    val current = (state.values as? UiState.Data)?.value.orEmpty()
                    state.copy(
                        values = UiState.Data(current + (spec.id to result.data)),
                        savingId = null
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    // The stored value is left untouched, so the control snaps back to what
                    // Cloudflare actually has.
                    it.copy(savingId = null, error = "${spec.title}: ${result.message}")
                }
            }
        }
    }

    fun toggle(spec: ZoneSettingSpec, enabled: Boolean) = setValue(spec, enabled.toSettingValue())

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
