package dev.cfmobile.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AnalyticsDashboard
import dev.cfmobile.app.data.repository.AnalyticsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnalyticsViewModel(
    private val zoneId: String,
    private val repository: AnalyticsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<AnalyticsDashboard>>(UiState.Loading)
    val state: StateFlow<UiState<AnalyticsDashboard>> = _state.asStateFlow()

    private val _rangeHours = MutableStateFlow(24L)
    val rangeHours: StateFlow<Long> = _rangeHours.asStateFlow()

    init {
        load()
    }

    fun selectRange(hours: Long) {
        _rangeHours.value = hours
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = repository.getDashboard(zoneId, _rangeHours.value)) {
                is ApiResult.Success -> UiState.Data(result.data)
                is ApiResult.Failure -> UiState.Error(ErrorClassifier.classify(result))
            }
        }
    }
}
