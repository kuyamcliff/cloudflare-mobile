package dev.cfmobile.app.ui.addressing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AddressMap
import dev.cfmobile.app.data.remote.dto.AddressingPrefix
import dev.cfmobile.app.data.repository.AddressingRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddressingTab(val label: String) {
    PREFIXES("Prefixes"),
    ADDRESS_MAPS("Address Maps")
}

data class AddressMapFormState(
    val description: String = "",
    val enabled: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class AddressingUiState(
    val tab: AddressingTab = AddressingTab.PREFIXES,
    val prefixes: UiState<List<AddressingPrefix>> = UiState.Loading,
    val addressMaps: UiState<List<AddressMap>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val busyId: String? = null,
    val deletingId: String? = null,
    val form: AddressMapFormState? = null,
    /** The map whose IPs and memberships are open in the detail sheet. */
    val selectedMap: AddressMap? = null,
    val isLoadingMap: Boolean = false,
    val error: String? = null
)

/** "AS64512 · approved" - the state that decides whether the prefix can be announced. */
fun prefixSummary(prefix: AddressingPrefix): String = listOfNotNull(
    prefix.asn?.let { "AS$it" },
    when (prefix.approved) {
        "approved" -> "approved"
        null -> null
        else -> "approval ${prefix.approved}"
    },
    if (prefix.onDemandLocked == true) "locked" else null
).joinToString(" · ").ifBlank { "No approval status reported" }

/**
 * Whether the advertisement switch can be operated at all. Cloudflare refuses the change when
 * the prefix isn't approved yet, or when on-demand control is locked off for it - showing a
 * live switch in either case would just produce a rejection.
 */
fun canToggleAdvertisement(prefix: AddressingPrefix): Boolean =
    prefix.approved == "approved" && prefix.onDemandEnabled == true && prefix.onDemandLocked != true

fun advertisementBlockedReason(prefix: AddressingPrefix): String? = when {
    canToggleAdvertisement(prefix) -> null
    prefix.approved != "approved" -> "Cloudflare hasn't approved this prefix yet"
    prefix.onDemandLocked == true -> "On-demand control is locked for this prefix"
    else -> "On-demand advertisement isn't enabled for this prefix"
}

/** "2 IPs · 1 binding · enabled" - what a map binds, without listing every address. */
fun addressMapSummary(map: AddressMap): String {
    val ips = map.ips?.size ?: 0
    val memberships = map.memberships?.size ?: 0
    return listOfNotNull(
        if (ips > 0) "$ips IP${if (ips == 1) "" else "s"}" else null,
        if (memberships > 0) "$memberships binding${if (memberships == 1) "" else "s"}" else null,
        if (map.enabled == true) "enabled" else "disabled"
    ).joinToString(" · ")
}

class AddressingViewModel(
    private val accountId: String,
    private val repository: AddressingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddressingUiState())
    val uiState: StateFlow<AddressingUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    fun selectTab(tab: AddressingTab) = _uiState.update { it.copy(tab = tab) }

    /** Both lists in one coroutine, as elsewhere: independent launches would race at the HTTP
     *  dispatcher with nothing gained. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(prefixes = UiState.Loading, addressMaps = UiState.Loading)
        }
        viewModelScope.launch {
            when (val result = repository.listPrefixes(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(prefixes = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(prefixes = UiState.Error(ErrorClassifier.classify(result)))
                }
            }
            when (val result = repository.listAddressMaps(accountId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(addressMaps = UiState.Data(result.data), isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(addressMaps = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    /**
     * Starts or stops Cloudflare announcing the prefix over BGP. The row is patched in place
     * from the status sub-resource rather than refetching the whole list, since that is the
     * only field that changed.
     */
    fun setAdvertised(prefix: AddressingPrefix, advertised: Boolean) {
        _uiState.update { it.copy(busyId = prefix.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.setAdvertised(accountId, prefix.id, advertised)) {
                is ApiResult.Success -> _uiState.update { state ->
                    val current = (state.prefixes as? UiState.Data)?.value
                    state.copy(
                        busyId = null,
                        prefixes = if (current == null) state.prefixes else UiState.Data(
                            current.map {
                                if (it.id == prefix.id) {
                                    it.copy(
                                        advertised = result.data.advertised,
                                        advertisedModifiedAt = result.data.advertisedModifiedAt
                                    )
                                } else {
                                    it
                                }
                            }
                        )
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(busyId = null, error = result.message) }
            }
        }
    }

    fun setAddressMapEnabled(map: AddressMap, enabled: Boolean) {
        _uiState.update { it.copy(busyId = map.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.setAddressMapEnabled(accountId, map.id, enabled)) {
                is ApiResult.Success -> _uiState.update { state ->
                    val current = (state.addressMaps as? UiState.Data)?.value
                    state.copy(
                        busyId = null,
                        addressMaps = if (current == null) state.addressMaps else UiState.Data(
                            // The PATCH response omits ips and memberships, so only the flag
                            // is taken from it - taking the whole object would blank the row.
                            current.map { if (it.id == map.id) it.copy(enabled = result.data.enabled) else it }
                        )
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(busyId = null, error = result.message) }
            }
        }
    }

    /** The list response carries no IPs or memberships; opening a map fetches them. */
    fun openMap(map: AddressMap) {
        _uiState.update { it.copy(selectedMap = map, isLoadingMap = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.getAddressMap(accountId, map.id)) {
                is ApiResult.Success -> _uiState.update { it.copy(selectedMap = result.data, isLoadingMap = false) }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoadingMap = false, error = result.message) }
            }
        }
    }

    fun closeMap() = _uiState.update { it.copy(selectedMap = null, isLoadingMap = false) }

    fun openForm() = _uiState.update { it.copy(form = AddressMapFormState()) }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (AddressMapFormState) -> AddressMapFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        if (form.description.isBlank()) {
            updateForm { it.copy(error = "A description is required - it's the only way to tell maps apart") }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createAddressMap(accountId, form.description.trim(), form.enabled)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteAddressMap(map: AddressMap) {
        _uiState.update { it.copy(deletingId = map.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteAddressMap(accountId, map.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingId = null, selectedMap = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
