package dev.cfmobile.app.ui.botmanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.repository.StringSetting
import dev.cfmobile.app.data.repository.ZoneSettingsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BotManagementUiState(
    val botFightMode: UiState<Boolean> = UiState.Loading,
    val isSaving: Boolean = false
)

/** PRD §9 "Bot score insights and mitigation" - scoped to the free-tier Bot Fight Mode toggle
 *  only (a plain zone setting, reusing ZoneSettingsRepository). Super Bot Fight Mode's
 *  per-category (definitely/likely automated, verified bots, static resources) configuration
 *  and bot score analytics both require a paid plan and aren't implemented - see
 *  CapabilityRegistry's migrationHint. */
class BotManagementViewModel(
    private val zoneId: String,
    private val repository: ZoneSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BotManagementUiState())
    val uiState: StateFlow<BotManagementUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(botFightMode = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.getSetting(zoneId, StringSetting.BOT_FIGHT_MODE)) {
                is ApiResult.Success -> _uiState.update { it.copy(botFightMode = UiState.Data(result.data == "on")) }
                is ApiResult.Failure -> _uiState.update { it.copy(botFightMode = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun setBotFightMode(enabled: Boolean) {
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val value = if (enabled) "on" else "off"
            when (val result = repository.setSetting(zoneId, StringSetting.BOT_FIGHT_MODE, value)) {
                is ApiResult.Success -> _uiState.update { it.copy(botFightMode = UiState.Data(result.data == "on"), isSaving = false) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
