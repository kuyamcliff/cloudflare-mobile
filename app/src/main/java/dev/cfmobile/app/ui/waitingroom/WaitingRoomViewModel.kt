package dev.cfmobile.app.ui.waitingroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.WaitingRoom
import dev.cfmobile.app.data.remote.dto.WaitingRoomEvent
import dev.cfmobile.app.data.remote.dto.WaitingRoomEventWrite
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

data class EventFormState(
    val name: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val description: String = "",
    val newUsersPerMinute: String = "",
    val totalActiveUsers: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

/** The scheduled events on one room, opened from that room's row. */
data class RoomEventsState(
    val room: WaitingRoom,
    val events: List<WaitingRoomEvent> = emptyList(),
    val isLoading: Boolean = true,
    val deletingId: String? = null,
    val form: EventFormState? = null,
    val error: String? = null
)

data class WaitingRoomUiState(
    val rooms: UiState<List<WaitingRoom>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: WaitingRoomFormState? = null,
    val deletingId: String? = null,
    val events: RoomEventsState? = null
)

/** Cloudflare wants RFC3339 in UTC. Accepting anything looser would be rejected server-side
 *  with a message that doesn't say which field was wrong. */
private val RFC3339_UTC = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")

fun validateEventForm(form: EventFormState): String? {
    if (form.name.isBlank()) return "Event name is required"
    if (!RFC3339_UTC.matches(form.startTime.trim())) {
        return "Start time must look like 2026-01-02T15:00:00Z (UTC)"
    }
    if (!RFC3339_UTC.matches(form.endTime.trim())) {
        return "End time must look like 2026-01-02T18:00:00Z (UTC)"
    }
    if (form.endTime.trim() <= form.startTime.trim()) return "The event has to end after it starts"
    val newUsers = form.newUsersPerMinute.trim()
    if (newUsers.isNotBlank() && (newUsers.toIntOrNull() ?: 0) <= 0) {
        return "New users per minute must be a positive number"
    }
    val active = form.totalActiveUsers.trim()
    if (active.isNotBlank() && (active.toIntOrNull() ?: 0) <= 0) {
        return "Total active users must be a positive number"
    }
    return null
}

fun buildEventWrite(form: EventFormState) = WaitingRoomEventWrite(
    name = form.name.trim(),
    eventStartTime = form.startTime.trim(),
    eventEndTime = form.endTime.trim(),
    description = form.description.trim().ifBlank { null },
    // Blank means "keep the room's own threshold for this window".
    newUsersPerMinute = form.newUsersPerMinute.trim().toIntOrNull(),
    totalActiveUsers = form.totalActiveUsers.trim().toIntOrNull()
)

/** "2 Jan 15:00 → 18:00" is what the API gives us as text, so it's shown as-is with its
 *  overrides rather than reformatted into a timezone the API never mentioned. */
fun eventSummary(event: WaitingRoomEvent): String = listOfNotNull(
    event.eventStartTime,
    event.eventEndTime
).joinToString(" → ").ifBlank { "No window set" }

fun eventOverrides(event: WaitingRoomEvent): String? = listOfNotNull(
    event.newUsersPerMinute?.let { "$it new users/min" },
    event.totalActiveUsers?.let { "$it active" },
    if (event.suspended == true) "suspended" else null
).joinToString(" · ").ifBlank { null }

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

    // ---- Events ----

    /** Events are a sub-resource of one room, so they're fetched when that room is opened. */
    fun openEvents(room: WaitingRoom) {
        _uiState.update { it.copy(events = RoomEventsState(room = room)) }
        viewModelScope.launch { reloadEvents(room.id) }
    }

    fun closeEvents() = _uiState.update { it.copy(events = null) }

    private suspend fun reloadEvents(roomId: String) {
        when (val result = repository.listEvents(zoneId, roomId)) {
            is ApiResult.Success -> _uiState.update { state ->
                val current = state.events ?: return@update state
                if (current.room.id != roomId) return@update state
                state.copy(events = current.copy(events = result.data, isLoading = false, error = null))
            }
            is ApiResult.Failure -> _uiState.update { state ->
                val current = state.events ?: return@update state
                if (current.room.id != roomId) return@update state
                state.copy(events = current.copy(isLoading = false, error = result.message))
            }
        }
    }

    fun openEventForm() = _uiState.update { state ->
        state.events?.let { state.copy(events = it.copy(form = EventFormState())) } ?: state
    }

    fun closeEventForm() = _uiState.update { state ->
        state.events?.let { state.copy(events = it.copy(form = null)) } ?: state
    }

    fun updateEventForm(transform: (EventFormState) -> EventFormState) = _uiState.update { state ->
        val events = state.events ?: return@update state
        val form = events.form ?: return@update state
        state.copy(events = events.copy(form = transform(form)))
    }

    fun saveEvent() {
        val events = _uiState.value.events ?: return
        val form = events.form ?: return
        val validationError = validateEventForm(form)
        if (validationError != null) {
            updateEventForm { it.copy(error = validationError) }
            return
        }
        updateEventForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createEvent(zoneId, events.room.id, buildEventWrite(form))) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        val current = state.events ?: return@update state
                        state.copy(events = current.copy(form = null))
                    }
                    reloadEvents(events.room.id)
                }
                is ApiResult.Failure -> updateEventForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun deleteEvent(event: WaitingRoomEvent) {
        val events = _uiState.value.events ?: return
        _uiState.update { state ->
            val current = state.events ?: return@update state
            state.copy(events = current.copy(deletingId = event.id, error = null))
        }
        viewModelScope.launch {
            when (val result = repository.deleteEvent(zoneId, events.room.id, event.id)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        val current = state.events ?: return@update state
                        state.copy(events = current.copy(deletingId = null))
                    }
                    reloadEvents(events.room.id)
                }
                is ApiResult.Failure -> _uiState.update { state ->
                    val current = state.events ?: return@update state
                    state.copy(events = current.copy(deletingId = null, error = result.message))
                }
            }
        }
    }
}
