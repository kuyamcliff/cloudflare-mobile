package dev.cfmobile.app.ui.zonedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.data.repository.ZonesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ZoneMenuViewModel(
    private val zoneId: String,
    private val repository: ZonesRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<CfZone>>(UiState.Loading)
    val state: StateFlow<UiState<CfZone>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = repository.getZone(zoneId)) {
                is ApiResult.Success -> UiState.Data(result.data)
                is ApiResult.Failure -> UiState.Error(ErrorClassifier.classify(result))
            }
        }
    }
}
