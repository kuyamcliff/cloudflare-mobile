package dev.cfmobile.app.ui.securityevents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.FirewallEvent
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.StatusPill
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityEventsScreen(zoneName: String, viewModel: SecurityEventsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var detailEvent by remember { mutableStateOf<FirewallEvent?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Security Events", zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OptionRow(
                title = "Time range",
                currentValue = uiState.window.name,
                options = EventWindow.entries.map { it.name to it.label },
                isSaving = false,
                onSelect = { viewModel.selectWindow(EventWindow.valueOf(it)) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            RefreshableStateContent(
                state = uiState.events,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh
            ) { events ->
                if (events.isEmpty()) {
                    EmptyState(
                        "No security events in this window.\n\nRetention and available fields depend on the zone's plan, so an empty list here can also mean this plan doesn't retain events that far back."
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(events, key = { it.datetime.orEmpty() + it.clientIP.orEmpty() + it.ruleId.orEmpty() }) { event ->
                            EventRow(event, onClick = { detailEvent = event })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }

    detailEvent?.let { event ->
        EventDetailSheet(event, onDismiss = { detailEvent = null })
    }
}

@Composable
private fun EventRow(event: FirewallEvent, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        StatusPill(eventActionLabel(event), actionColor(event.action))
        eventRequestLabel(event)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        eventOriginLabel(event)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        event.datetime?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(event: FirewallEvent, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(eventActionLabel(event), style = MaterialTheme.typography.titleMedium)
            eventRequestLabel(event)?.let { DetailLine("Request", it) }
            event.clientIP?.let { DetailLine("Client IP", it) }
            event.clientCountryName?.let { DetailLine("Country", it) }
            event.clientAsn?.let { DetailLine("ASN", "AS$it") }
            event.source?.let { DetailLine("Matched service", it) }
            event.ruleId?.let { DetailLine("Rule ID", it) }
            event.userAgent?.let { DetailLine("User agent", it) }
            event.datetime?.let { DetailLine("When", it) }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

/** Blocks read as severe, challenges as cautionary, everything else neutral. Unknown actions
 *  Cloudflare adds later fall through to neutral rather than being guessed at. */
@Composable
private fun actionColor(action: String?) = when (action?.lowercase()) {
    "block", "drop" -> MaterialTheme.colorScheme.error
    "challenge", "managed_challenge", "jschallenge", "js_challenge" -> MaterialTheme.colorScheme.tertiary
    "allow", "skip", "log" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
