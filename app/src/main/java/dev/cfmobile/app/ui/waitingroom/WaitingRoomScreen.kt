package dev.cfmobile.app.ui.waitingroom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.StatusPill
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingRoomScreen(zoneName: String, viewModel: WaitingRoomViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Waiting Room", zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openForm) {
                Icon(Icons.Filled.Add, contentDescription = "Create waiting room")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            RefreshableStateContent(
                state = uiState.rooms,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh
            ) { rooms ->
                if (rooms.isEmpty()) {
                    EmptyState("No waiting rooms yet.\n\nA waiting room queues visitors when a page would otherwise overwhelm your origin.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(rooms, key = { it.id }) { room ->
                            DeletableListRow(
                                icon = Icons.Filled.Groups,
                                title = room.name,
                                subtitle = room.host + (room.path ?: ""),
                                detail = listOfNotNull(
                                    room.newUsersPerMinute?.let { "$it new users/min" },
                                    room.totalActiveUsers?.let { "$it active" }
                                ).joinToString(" · ").ifBlank { null },
                                isDeleting = uiState.deletingId == room.id,
                                deleteContentDescription = "Delete waiting room",
                                confirmTitle = "Delete waiting room?",
                                confirmText = "\"${room.name}\" will be permanently deleted and visitors will stop being queued. This can't be undone.",
                                onDelete = { viewModel.delete(room) },
                                onClick = { viewModel.openEvents(room) },
                                trailing = {
                                    StatusPill(
                                        waitingRoomStatusLabel(room),
                                        if (room.suspended) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }

    uiState.form?.let { form ->
        CreateRoomSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
    uiState.events?.let { events ->
        EventsSheet(events, onDismiss = viewModel::closeEvents, viewModel = viewModel)
    }
}

/** A room's scheduled events. Cloudflare stores these under the room, so they only make sense
 *  in the context of the room that was tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventsSheet(
    state: RoomEventsState,
    onDismiss: () -> Unit,
    viewModel: WaitingRoomViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Scheduled events", style = MaterialTheme.typography.titleMedium)
            Text(
                state.room.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                state.isLoading -> CircularProgressIndicator()
                state.form != null -> EventForm(state.form, viewModel)
                else -> {
                    state.error?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.events.isEmpty()) {
                        Text(
                            "No events scheduled. An event overrides this room's thresholds for one window - a sale, a drop, a ticket release.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    state.events.forEach { event ->
                        DeletableListRow(
                            icon = Icons.Filled.Event,
                            title = event.name,
                            subtitle = eventSummary(event),
                            detail = eventOverrides(event) ?: event.description,
                            isDeleting = state.deletingId == event.id,
                            deleteContentDescription = "Delete event",
                            confirmTitle = "Delete this event?",
                            confirmText = "The room falls back to its own thresholds for that window. Visitors already queued aren't affected.",
                            onDelete = { viewModel.deleteEvent(event) }
                        )
                    }
                    TextButton(onClick = viewModel::openEventForm) { Text("Schedule an event") }
                }
            }
        }
    }
}

@Composable
private fun EventForm(form: EventFormState, viewModel: WaitingRoomViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> viewModel.updateEventForm { it.copy(name = v) } },
            label = { Text("Event name") },
            placeholder = { Text("Summer sale") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.startTime,
            onValueChange = { v -> viewModel.updateEventForm { it.copy(startTime = v) } },
            label = { Text("Start (UTC)") },
            placeholder = { Text("2026-01-02T15:00:00Z") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.endTime,
            onValueChange = { v -> viewModel.updateEventForm { it.copy(endTime = v) } },
            label = { Text("End (UTC)") },
            placeholder = { Text("2026-01-02T18:00:00Z") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.newUsersPerMinute,
            onValueChange = { v -> viewModel.updateEventForm { it.copy(newUsersPerMinute = v) } },
            label = { Text("New users per minute") },
            placeholder = { Text("leave blank to keep the room's own") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.totalActiveUsers,
            onValueChange = { v -> viewModel.updateEventForm { it.copy(totalActiveUsers = v) } },
            label = { Text("Total active users") },
            placeholder = { Text("leave blank to keep the room's own") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            // Cloudflare stores and returns these in UTC, so the app doesn't invent a local
            // timezone it would then have to convert back.
            "Times are UTC, in Cloudflare's own format. A pre-queue window and a custom queueing method are set from the dashboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (form.error != null) {
            Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        FormActions(
            isSaving = form.isSaving,
            onCancel = viewModel::closeEventForm,
            onSave = viewModel::saveEvent,
            saveLabel = "Schedule"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRoomSheet(form: WaitingRoomFormState, onDismiss: () -> Unit, viewModel: WaitingRoomViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Create waiting room", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Room name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.host,
                onValueChange = { v -> viewModel.updateForm { it.copy(host = v) } },
                label = { Text("Host") },
                placeholder = { Text("shop.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.path,
                onValueChange = { v -> viewModel.updateForm { it.copy(path = v) } },
                label = { Text("Path") },
                placeholder = { Text("/checkout") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.newUsersPerMinute,
                onValueChange = { v -> viewModel.updateForm { it.copy(newUsersPerMinute = v) } },
                label = { Text("New users per minute") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.totalActiveUsers,
                onValueChange = { v -> viewModel.updateForm { it.copy(totalActiveUsers = v) } },
                label = { Text("Total active users") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save)
        }
    }
}
