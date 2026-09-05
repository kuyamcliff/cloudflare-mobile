package dev.cfmobile.app.ui.zones

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ZonesViewModel(private val repository: ZonesRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<CfZone>>>(UiState.Loading)
    val state: StateFlow<UiState<List<CfZone>>> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var allZones: List<CfZone> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val result = repository.listZones()) {
                is ApiResult.Success -> {
                    allZones = result.data
                    applyFilter()
                }
                is ApiResult.Failure -> _state.value = UiState.Error(ErrorClassifier.classify(result))
            }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
        applyFilter()
    }

    private fun applyFilter() {
        val q = _query.value.trim().lowercase()
        val filtered = if (q.isEmpty()) allZones else allZones.filter { it.name.lowercase().contains(q) }
        _state.value = UiState.Data(filtered)
    }
}
