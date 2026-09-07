package dev.cfmobile.app.ui.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.ZoneDnsSettings
import dev.cfmobile.app.data.remote.dto.ZoneDnsSettingsUpdate
import dev.cfmobile.app.data.repository.ZoneDnsSettingsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One zone-wide DNS switch, declared as data rather than as another branch in a `when` - the
 * same approach ZoneSettingSpec takes for the settings families. Each pairs a reader with the
 * single-field update Cloudflare merges into the stored settings.
 */
enum class DnsSetting(
    val title: String,
    val subtitle: String,
    val read: (ZoneDnsSettings) -> Boolean?,
    val write: (Boolean) -> ZoneDnsSettingsUpdate
) {
    FLATTEN_CNAMES(
        "Flatten all CNAMEs",
        "Answer every CNAME with the address it resolves to",
        { it.flattenAllCnames },
        { ZoneDnsSettingsUpdate(flattenAllCnames = it) }
    ),
    FOUNDATION_DNS(
        "Foundation DNS",
        "Use Cloudflare's advanced nameservers, if the plan includes them",
        { it.foundationDns },
        { ZoneDnsSettingsUpdate(foundationDns = it) }
    ),
    MULTI_PROVIDER(
        "Multi-provider DNS",
        "Allow another DNS provider to serve this zone alongside Cloudflare",
        { it.multiProvider },
        { ZoneDnsSettingsUpdate(multiProvider = it) }
    ),
    SECONDARY_OVERRIDES(
        "Secondary overrides",
        "Let Cloudflare override records it receives from a primary server",
        { it.secondaryOverrides },
        { ZoneDnsSettingsUpdate(secondaryOverrides = it) }
    )
}

data class DnsSettingsUiState(
    val settings: UiState<ZoneDnsSettings> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val busySetting: DnsSetting? = null,
    val error: String? = null
)

/** "Cloudflare nameservers · NS TTL 86400" - the parts that aren't switches. */
fun nameserverSummary(settings: ZoneDnsSettings): String = listOfNotNull(
    when (settings.nameservers?.type) {
        "cloudflare.standard" -> "Cloudflare nameservers"
        "custom.account", "custom.tenant" -> "Custom nameservers"
        null -> null
        else -> settings.nameservers.type
    },
    settings.nameservers?.nsSet?.let { "set $it" },
    settings.nsTtl?.let { "NS TTL $it" },
    settings.zoneMode?.let { "zone mode $it" }
).joinToString(" · ").ifBlank { "No nameserver details reported" }

/**
 * Zone-wide DNS behaviour, which is a different resource from the records themselves. Each
 * switch sends only its own field, so flipping one never rewrites the others.
 */
class DnsSettingsViewModel(
    private val zoneId: String,
    private val repository: ZoneDnsSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DnsSettingsUiState())
    val uiState: StateFlow<DnsSettingsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(settings = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.getSettings(zoneId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(settings = UiState.Data(result.data), isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(settings = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun setSetting(setting: DnsSetting, enabled: Boolean) {
        _uiState.update { it.copy(busySetting = setting, error = null) }
        viewModelScope.launch {
            when (val result = repository.update(zoneId, setting.write(enabled))) {
                // Cloudflare answers with the whole merged document, so this is the new truth
                // for every switch, not just the one that changed.
                is ApiResult.Success -> _uiState.update {
                    it.copy(settings = UiState.Data(result.data), busySetting = null)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(busySetting = null, error = result.message)
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
