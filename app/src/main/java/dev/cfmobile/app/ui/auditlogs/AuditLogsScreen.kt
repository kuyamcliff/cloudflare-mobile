package dev.cfmobile.app.ui.auditlogs

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.AuditLogEntry
import dev.cfmobile.app.ui.common.CfListScreen

@Composable
fun AuditLogsScreen(viewModel: AuditLogsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var detailEntry by remember { mutableStateOf<AuditLogEntry?>(null) }

    CfListScreen(
        title = "Audit Logs",
        onBack = onBack,
        state = uiState.entries,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No audit log entries found",
        key = { it.id },
        searchPlaceholder = "Search actions, people, resources",
        searchMatches = { entry, query ->
            auditActionLabel(entry).contains(query, ignoreCase = true) ||
                auditActorLabel(entry).contains(query, ignoreCase = true) ||
                auditResourceLabel(entry).orEmpty().contains(query, ignoreCase = true)
        }
    ) { entry ->
        AuditLogRow(entry, onClick = { detailEntry = entry })
    }

    detailEntry?.let { entry ->
        AuditLogDetailSheet(entry, onDismiss = { detailEntry = null })
    }
}

@Composable
private fun AuditLogRow(entry: AuditLogEntry, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(auditActionLabel(entry), style = MaterialTheme.typography.bodyLarge)
        Text(
            auditActorLabel(entry) + (auditResourceLabel(entry)?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        entry.occurredAt?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditLogDetailSheet(entry: AuditLogEntry, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(auditActionLabel(entry), style = MaterialTheme.typography.titleMedium)
            DetailLine("Actor", auditActorLabel(entry))
            entry.actor?.ip?.let { DetailLine("IP", it) }
            auditResourceLabel(entry)?.let { DetailLine("Resource", it) }
            entry.resource?.id?.let { DetailLine("Resource ID", it) }
            entry.occurredAt?.let { DetailLine("When", it) }
            entry.oldValue?.takeIf { it.isNotBlank() }?.let { DetailLine("Old value", it) }
            entry.newValue?.takeIf { it.isNotBlank() }?.let { DetailLine("New value", it) }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
