package dev.cfmobile.app.ui.waitingroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.WaitingRoom
import dev.cfmobile.app.data.repository.WaitingRoomRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WaitingRoomFormState(
    val name: String = "",
    val host: String = "",
    val path: String = "/",
    val newUsersPerMinute: String = "200",
    val totalActiveUsers: String = "200",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class WaitingRoomUiState(
    val rooms: UiState<List<WaitingRoom>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: WaitingRoomFormState? = null,
    val deletingId: String? = null
)

fun validateWaitingRoomForm(form: WaitingRoomFormState): String? {
    val newUsers = form.newUsersPerMinute.trim().toIntOrNull()
    val activeUsers = form.totalActiveUsers.trim().toIntOrNull()
    return when {
        form.name.isBlank() -> "Room name is required"
        form.host.isBlank() -> "Host is required"
        !form.path.startsWith("/") -> "Path must start with /"
        newUsers == null || newUsers <= 0 -> "New users per minute must be a positive number"
        activeUsers == null || activeUsers <= 0 -> "Total active users must be a positive number"
        else -> null
    }
}

/** A room is only actually queueing when it isn't suspended. */
fun waitingRoomStatusLabel(room: WaitingRoom): String = if (room.suspended) "Suspended" else "Active"

class WaitingRoomViewModel(
    private val zoneId: String,
    private val repository: WaitingRoomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaitingRoomUiState())
    val uiState: StateFlow<WaitingRoomUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(rooms = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listRooms(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(rooms = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(rooms = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = WaitingRoomFormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (WaitingRoomFormState) -> WaitingRoomFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateWaitingRoomForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.createRoom(
                zoneId = zoneId,
                name = form.name.trim(),
                host = form.host.trim(),
                path = form.path.trim(),
                newUsersPerMinute = form.newUsersPerMinute.trim().toInt(),
                totalActiveUsers = form.totalActiveUsers.trim().toInt()
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(room: WaitingRoom) {
        _uiState.update { it.copy(deletingId = room.id) }
        viewModelScope.launch {
            repository.deleteRoom(zoneId, room.id)
            _uiState.update { it.copy(deletingId = null) }
            load(isRefresh = true)
        }
    }
}
