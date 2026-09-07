package dev.cfmobile.app.ui.zonesecurity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CustomNameserver
import dev.cfmobile.app.data.remote.dto.ZoneCustomNameservers
import dev.cfmobile.app.data.remote.dto.ZoneHold
import dev.cfmobile.app.data.repository.ZoneOwnershipRepository
import dev.cfmobile.app.data.repository.ZonesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ZoneOwnershipUiState(
    val nameserverSets: UiState<List<CustomNameserver>> = UiState.Loading,
    val zoneNameservers: ZoneCustomNameservers? = null,
    val hold: ZoneHold? = null,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

/** Cloudflare groups custom nameservers into numbered sets; a zone picks one set. */
fun nameserverSets(nameservers: List<CustomNameserver>): List<Pair<String, String>> =
    nameservers.mapNotNull { it.nsSet }
        .distinct()
        .sorted()
        .map { set ->
            val names = nameservers.filter { it.nsSet == set }.mapNotNull { it.nsName }
            set.toString() to "Set $set: ${names.joinToString(", ").ifBlank { "no nameservers listed" }}"
        }

fun holdSummary(hold: ZoneHold?): String = when {
    hold == null || !hold.hold -> "No hold - this domain can be added to another Cloudflare account"
    hold.includeSubdomains == true -> "Held, including subdomains"
    else -> "Held"
}

class ZoneOwnershipViewModel(
    private val zoneId: String,
    private val repository: ZoneOwnershipRepository,
    private val zonesRepository: ZonesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZoneOwnershipUiState())
    val uiState: StateFlow<ZoneOwnershipUiState> = _uiState.asStateFlow()

    private var accountId: String? = null

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    /** Nameserver sets belong to the account, so the zone is read first to find which account
     *  that is - the same lookup Email Routing does for destination addresses. */
    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(nameserverSets = UiState.Loading) }
        viewModelScope.launch {
            val account = accountId ?: when (val zone = zonesRepository.getZone(zoneId)) {
                is ApiResult.Success -> zone.data.account?.id
                is ApiResult.Failure -> null
            }
            accountId = account
            if (account == null) {
                _uiState.update {
                    it.copy(
                        nameserverSets = UiState.Error(
                            ErrorClassifier.classify(
                                ApiResult.Failure("Couldn't work out which account this zone belongs to")
                            )
                        )
                    )
                }
            } else {
                when (val sets = repository.listAccountNameservers(account)) {
                    is ApiResult.Success -> _uiState.update { it.copy(nameserverSets = UiState.Data(sets.data)) }
                    is ApiResult.Failure -> _uiState.update {
                        it.copy(nameserverSets = UiState.Error(ErrorClassifier.classify(sets)))
                    }
                }
            }
            when (val zoneNameservers = repository.getZoneNameservers(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(zoneNameservers = zoneNameservers.data) }
                is ApiResult.Failure -> Unit
            }
            when (val hold = repository.getHold(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(hold = hold.data, isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun setCustomNameservers(enabled: Boolean, nsSet: Int? = null) {
        val current = _uiState.value.zoneNameservers
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val set = nsSet ?: current?.nsSet
            when (val result = repository.setZoneNameservers(zoneId, enabled, set)) {
                is ApiResult.Success -> _uiState.update { it.copy(zoneNameservers = result.data, isSaving = false) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    /**
     * Turning the hold off is what has to happen before this domain can move to another
     * Cloudflare account - including a move you meant to make - so the screen confirms it.
     */
    fun setHold(enabled: Boolean, includeSubdomains: Boolean = false) {
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = if (enabled) {
                repository.createHold(zoneId, includeSubdomains)
            } else {
                repository.removeHold(zoneId)
            }
            when (result) {
                is ApiResult.Success -> _uiState.update { it.copy(hold = result.data, isSaving = false) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
