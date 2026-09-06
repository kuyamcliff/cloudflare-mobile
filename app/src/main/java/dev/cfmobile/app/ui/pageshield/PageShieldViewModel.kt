package dev.cfmobile.app.ui.pageshield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.PageShieldConnection
import dev.cfmobile.app.data.remote.dto.PageShieldScript
import dev.cfmobile.app.data.repository.PageShieldRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PageShieldTab { SCRIPTS, CONNECTIONS }

data class PageShieldUiState(
    val tab: PageShieldTab = PageShieldTab.SCRIPTS,
    val isEnabled: Boolean? = null,
    val isTogglingEnabled: Boolean = false,
    val settingsError: String? = null,
    val scripts: UiState<List<PageShieldScript>> = UiState.Loading,
    val connections: UiState<List<PageShieldConnection>> = UiState.Loading,
    val isRefreshing: Boolean = false
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
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

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
