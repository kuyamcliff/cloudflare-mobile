package dev.cfmobile.app.ui.deviceposture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.EnrolledDevice
import dev.cfmobile.app.data.remote.dto.PostureRule
import dev.cfmobile.app.data.repository.DevicePostureRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DevicePostureTab { DEVICES, POSTURE }

data class DevicePostureUiState(
    val tab: DevicePostureTab = DevicePostureTab.DEVICES,
    val devices: UiState<List<EnrolledDevice>> = UiState.Loading,
    val postureRules: UiState<List<PostureRule>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val revokingId: String? = null,
    val revokeError: String? = null
)

/** A device's own name is optional; fall back to the user it's enrolled to, then its id. */
fun deviceLabel(device: EnrolledDevice): String =
    device.name?.takeIf { it.isNotBlank() }
        ?: device.user?.email?.takeIf { it.isNotBlank() }
        ?: device.id

class DevicePostureViewModel(
    private val accountId: String,
    private val repository: DevicePostureRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicePostureUiState())
    val uiState: StateFlow<DevicePostureUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun selectTab(tab: DevicePostureTab) = _uiState.update { it.copy(tab = tab) }

    fun refresh() = load(isRefresh = true)

    /** Both lists load in one coroutine, sequentially. Two independent launches would race at
     *  the HTTP layer for no benefit - the same trap fixed in LoadBalancingViewModel. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(devices = UiState.Loading, postureRules = UiState.Loading)
        }
        viewModelScope.launch {
            when (val result = repository.listDevices(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(devices = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(devices = UiState.Error(ErrorClassifier.classify(result))) }
            }
            when (val result = repository.listPostureRules(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(postureRules = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(postureRules = UiState.Error(ErrorClassifier.classify(result))) }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Revokes one device's Zero Trust registration. Its user has to re-enrol before that
     * device can reach anything behind Zero Trust again, so the screen confirms first.
     */
    fun revoke(device: EnrolledDevice) {
        _uiState.update { it.copy(revokingId = device.id, revokeError = null) }
        viewModelScope.launch {
            when (val result = repository.revokeDevice(accountId, device.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(revokingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(revokingId = null, revokeError = result.message)
                }
            }
        }
    }

    fun dismissRevokeError() = _uiState.update { it.copy(revokeError = null) }
}
