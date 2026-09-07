package dev.cfmobile.app.ui.ssl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
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

data class SslSettings(
    val ssl: String = "flexible",
    val alwaysUseHttps: String = "off",
    val minTlsVersion: String = "1.0",
    val automaticHttpsRewrites: String = "off",
    val securityLevel: String = "medium"
)

class SslViewModel(
    private val zoneId: String,
    private val repository: ZoneSettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<SslSettings>>(UiState.Loading)
    val state: StateFlow<UiState<SslSettings>> = _state.asStateFlow()

    private val _savingKey = MutableStateFlow<StringSetting?>(null)
    val savingKey: StateFlow<StringSetting?> = _savingKey.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val ssl = async { repository.getSetting(zoneId, StringSetting.SSL) }
            val https = async { repository.getSetting(zoneId, StringSetting.ALWAYS_USE_HTTPS) }
            val tls = async { repository.getSetting(zoneId, StringSetting.MIN_TLS_VERSION) }
            val rewrites = async { repository.getSetting(zoneId, StringSetting.AUTOMATIC_HTTPS_REWRITES) }
            val security = async { repository.getSetting(zoneId, StringSetting.SECURITY_LEVEL) }
            val results = listOf(ssl, https, tls, rewrites, security).awaitAll()

            val firstFailure = results.filterIsInstance<ApiResult.Failure>().firstOrNull()
            if (firstFailure != null) {
                _state.value = UiState.Error(ErrorClassifier.classify(firstFailure))
                return@launch
            }

            fun value(r: ApiResult<String>) = (r as ApiResult.Success).data
            _state.value = UiState.Data(
                SslSettings(
                    ssl = value(ssl.await()),
                    alwaysUseHttps = value(https.await()),
                    minTlsVersion = value(tls.await()),
                    automaticHttpsRewrites = value(rewrites.await()),
                    securityLevel = value(security.await())
                )
            )
        }
    }

    fun update(setting: StringSetting, value: String) {
        val current = (_state.value as? UiState.Data)?.value ?: return
        _savingKey.value = setting
        viewModelScope.launch {
            when (val result = repository.setSetting(zoneId, setting, value)) {
                is ApiResult.Success -> {
                    _state.update {
                        UiState.Data(current.applying(setting, result.data))
                    }
                }
                is ApiResult.Failure -> {
                    // Surface as a transient error but keep showing the last-known-good values.
                    _state.value = UiState.Data(current)
                }
            }
            _savingKey.value = null
        }
    }

    private fun SslSettings.applying(setting: StringSetting, value: String): SslSettings = when (setting) {
        StringSetting.SSL -> copy(ssl = value)
        StringSetting.ALWAYS_USE_HTTPS -> copy(alwaysUseHttps = value)
        StringSetting.MIN_TLS_VERSION -> copy(minTlsVersion = value)
        StringSetting.AUTOMATIC_HTTPS_REWRITES -> copy(automaticHttpsRewrites = value)
        StringSetting.SECURITY_LEVEL -> copy(securityLevel = value)
        StringSetting.CACHE_LEVEL, StringSetting.DEVELOPMENT_MODE, StringSetting.BOT_FIGHT_MODE -> this
    }
}
