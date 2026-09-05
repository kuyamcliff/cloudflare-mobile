package dev.cfmobile.app.ui.caching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.repository.StringSetting
import dev.cfmobile.app.data.repository.ZoneSettingsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SavingField { CACHE_LEVEL, DEV_MODE, BROWSER_TTL, PURGE, NONE }

data class CachingSettings(
    val cacheLevel: String = "aggressive",
    val developmentMode: String = "off",
    val browserCacheTtl: Int = 14400
)

data class CachingUiState(
    val settings: UiState<CachingSettings> = UiState.Loading,
    val saving: SavingField = SavingField.NONE,
    val purgeResult: String? = null
)

class CachingViewModel(
    private val zoneId: String,
    private val repository: ZoneSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CachingUiState())
    val uiState: StateFlow<CachingUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(settings = UiState.Loading) }
        viewModelScope.launch {
            val level = async { repository.getSetting(zoneId, StringSetting.CACHE_LEVEL) }
            val dev = async { repository.getSetting(zoneId, StringSetting.DEVELOPMENT_MODE) }
            val ttl = async { repository.getBrowserCacheTtl(zoneId) }
            val results = listOf(level, dev, ttl).awaitAll()
            val failure = results.filterIsInstance<ApiResult.Failure>().firstOrNull()
            _uiState.update {
                it.copy(
                    settings = if (failure != null) {
                        UiState.Error(failure.message)
                    } else {
                        UiState.Data(
                            CachingSettings(
                                cacheLevel = (level.await() as ApiResult.Success).data,
                                developmentMode = (dev.await() as ApiResult.Success).data,
                                browserCacheTtl = (ttl.await() as ApiResult.Success).data
                            )
                        )
                    }
                )
            }
        }
    }

    fun setCacheLevel(value: String) {
        val current = (_uiState.value.settings as? UiState.Data)?.value ?: return
        _uiState.update { it.copy(saving = SavingField.CACHE_LEVEL) }
        viewModelScope.launch {
            when (val result = repository.setSetting(zoneId, StringSetting.CACHE_LEVEL, value)) {
                is ApiResult.Success -> _uiState.update { it.copy(settings = UiState.Data(current.copy(cacheLevel = result.data)), saving = SavingField.NONE) }
                is ApiResult.Failure -> _uiState.update { it.copy(saving = SavingField.NONE) }
            }
        }
    }

    fun setDevelopmentMode(enabled: Boolean) {
        val current = (_uiState.value.settings as? UiState.Data)?.value ?: return
        _uiState.update { it.copy(saving = SavingField.DEV_MODE) }
        viewModelScope.launch {
            val value = if (enabled) "on" else "off"
            when (val result = repository.setSetting(zoneId, StringSetting.DEVELOPMENT_MODE, value)) {
                is ApiResult.Success -> _uiState.update { it.copy(settings = UiState.Data(current.copy(developmentMode = result.data)), saving = SavingField.NONE) }
                is ApiResult.Failure -> _uiState.update { it.copy(saving = SavingField.NONE) }
            }
        }
    }

    fun setBrowserCacheTtl(seconds: Int) {
        val current = (_uiState.value.settings as? UiState.Data)?.value ?: return
        _uiState.update { it.copy(saving = SavingField.BROWSER_TTL) }
        viewModelScope.launch {
            when (val result = repository.setBrowserCacheTtl(zoneId, seconds)) {
                is ApiResult.Success -> _uiState.update { it.copy(settings = UiState.Data(current.copy(browserCacheTtl = result.data)), saving = SavingField.NONE) }
                is ApiResult.Failure -> _uiState.update { it.copy(saving = SavingField.NONE) }
            }
        }
    }

    fun purgeEverything() {
        _uiState.update { it.copy(saving = SavingField.PURGE, purgeResult = null) }
        viewModelScope.launch {
            val message = when (repository.purgeEverything(zoneId)) {
                is ApiResult.Success -> "Cache purged"
                is ApiResult.Failure -> "Purge failed"
            }
            _uiState.update { it.copy(saving = SavingField.NONE, purgeResult = message) }
        }
    }

    fun dismissPurgeResult() = _uiState.update { it.copy(purgeResult = null) }
}
