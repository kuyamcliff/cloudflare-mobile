package dev.cfmobile.app.ui.spectrum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.SpectrumApp
import dev.cfmobile.app.data.repository.SpectrumRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpectrumUiState(
    val apps: UiState<List<SpectrumApp>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val deletingId: String? = null
)

/** Spectrum apps are identified by their DNS name; fall back to the id when it's absent. */
fun spectrumAppLabel(app: SpectrumApp): String =
    app.dns?.name?.takeIf { it.isNotBlank() } ?: app.id

/** "tcp/22 → 203.0.113.10:22", skipping whatever wasn't returned. */
fun spectrumRouteLabel(app: SpectrumApp): String? {
    val origins = app.originDirect?.joinToString(", ")?.takeIf { it.isNotBlank() }
    return when {
        app.protocol != null && origins != null -> "${app.protocol} → $origins"
        app.protocol != null -> app.protocol
        else -> origins
    }
}

class SpectrumViewModel(
    private val zoneId: String,
    private val repository: SpectrumRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpectrumUiState())
    val uiState: StateFlow<SpectrumUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(apps = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listApps(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(apps = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(apps = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun delete(app: SpectrumApp) {
        _uiState.update { it.copy(deletingId = app.id) }
        viewModelScope.launch {
            repository.deleteApp(zoneId, app.id)
            _uiState.update { it.copy(deletingId = null) }
            load(isRefresh = true)
        }
    }
}
