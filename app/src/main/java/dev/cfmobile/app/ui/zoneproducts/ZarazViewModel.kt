package dev.cfmobile.app.ui.zoneproducts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.ZarazConfig
import dev.cfmobile.app.data.repository.ZarazRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One third-party tool as the screen lists it. Cloudflare keys tools by an opaque id, so the
 *  id is kept for the list key while the name is what's shown. */
data class ZarazToolRow(
    val id: String,
    val name: String,
    val type: String?,
    val enabled: Boolean
)

/** A privacy setting Zaraz applies to what it forwards to third parties. */
data class ZarazSettingRow(
    val label: String,
    val enabled: Boolean
)

data class ZarazUiState(
    val config: UiState<ZarazConfig> = UiState.Loading,
    val isRefreshing: Boolean = false
)

fun zarazTools(config: ZarazConfig): List<ZarazToolRow> =
    config.tools.orEmpty().map { (id, tool) ->
        ZarazToolRow(
            id = id,
            name = tool.name?.takeIf { it.isNotBlank() } ?: tool.type ?: id,
            type = tool.type,
            // Cloudflare omits `enabled` for a tool that is on, so absent means enabled.
            enabled = tool.enabled ?: true
        )
    }.sortedBy { it.name.lowercase() }

/** The privacy switches worth surfacing: each one decides whether a piece of the visitor's
 *  request reaches the third-party tools Zaraz loads. */
fun zarazPrivacySettings(config: ZarazConfig): List<ZarazSettingRow> {
    val settings = config.settings ?: return emptyList()
    return listOfNotNull(
        settings.hideIPAddress?.let { ZarazSettingRow("Hide visitor IP address", it) },
        settings.hideQueryParams?.let { ZarazSettingRow("Hide URL query parameters", it) },
        settings.hideUserAgent?.let { ZarazSettingRow("Hide user agent", it) },
        settings.hideExternalReferer?.let { ZarazSettingRow("Hide external referrer", it) },
        settings.autoInjectScript?.let { ZarazSettingRow("Inject the Zaraz script automatically", it) },
        settings.ecommerce?.let { ZarazSettingRow("E-commerce tracking", it) }
    )
}

fun zarazTriggerNames(config: ZarazConfig): List<String> =
    config.triggers.orEmpty().values.mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }.sorted()

class ZarazViewModel(
    private val zoneId: String,
    private val repository: ZarazRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZarazUiState())
    val uiState: StateFlow<ZarazUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(config = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.getConfig(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(config = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(config = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }
}
