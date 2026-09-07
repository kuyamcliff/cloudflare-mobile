package dev.cfmobile.app.ui.botmanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.BotManagementConfig
import dev.cfmobile.app.data.repository.BotManagementRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Super Bot Fight Mode can do with a category of traffic. */
val SBFM_ACTIONS = listOf(
    "allow" to "Allow",
    "managed_challenge" to "Managed Challenge",
    "block" to "Block"
)

data class BotManagementUiState(
    val config: UiState<BotManagementConfig> = UiState.Loading,
    val isSaving: Boolean = false,
    val error: String? = null
)

/**
 * Whether the zone's plan includes Super Bot Fight Mode. Cloudflare answers the same endpoint
 * for every plan and simply omits the fields a plan doesn't have, so the presence of the
 * per-category settings is the signal - there is no plan field to read.
 */
fun hasSuperBotFightMode(config: BotManagementConfig): Boolean =
    config.definitelyAutomated != null || config.likelyAutomated != null

/**
 * Bot management for a zone. Free-tier Bot Fight Mode and paid Super Bot Fight Mode share one
 * configuration document, and Cloudflare rejects a write carrying a field the zone's plan
 * doesn't include - so every save starts from the config the zone itself returned and changes
 * one field of it, rather than sending a fully-populated object.
 */
class BotManagementViewModel(
    private val zoneId: String,
    private val repository: BotManagementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BotManagementUiState())
    val uiState: StateFlow<BotManagementUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(config = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.getConfig(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(config = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(config = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun setFightMode(enabled: Boolean) = update { it.copy(fightMode = enabled) }

    fun setDefinitelyAutomated(action: String) = update { it.copy(definitelyAutomated = action) }

    fun setLikelyAutomated(action: String) = update { it.copy(likelyAutomated = action) }

    fun setVerifiedBots(action: String) = update { it.copy(verifiedBots = action) }

    fun setStaticResourceProtection(enabled: Boolean) = update { it.copy(staticResourceProtection = enabled) }

    fun setOptimizeWordpress(enabled: Boolean) = update { it.copy(optimizeWordpress = enabled) }

    /** Applies one change to the configuration the zone reported and sends that back. A failed
     *  write leaves the shown values untouched, so the screen never claims a change that
     *  Cloudflare rejected. */
    private fun update(transform: (BotManagementConfig) -> BotManagementConfig) {
        val current = (_uiState.value.config as? UiState.Data)?.value ?: return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.updateConfig(zoneId, transform(current))) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(config = UiState.Data(result.data), isSaving = false)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
