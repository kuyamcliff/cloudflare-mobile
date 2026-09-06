package dev.cfmobile.app.ui.workers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.ReadOnlyListRow

@Composable
fun WorkersScreen(viewModel: WorkersViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Workers",
        onBack = onBack,
        state = uiState.scripts,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Worker scripts yet",
        key = { it.id },
        searchPlaceholder = "Search scripts",
        searchMatches = { script, query -> script.id.contains(query, ignoreCase = true) }
    ) { script ->
        ReadOnlyListRow(
            icon = Icons.Filled.Bolt,
            title = script.id,
            monospaceTitle = true,
            subtitle = script.modifiedOn?.let { "Modified $it" },
            onClick = { viewModel.openDetail(script) }
        )
    }

    uiState.detail?.let { detail ->
        WorkerScriptDetailSheet(
            detail = detail,
            isDeleting = uiState.deletingId == detail.script.id,
            onDismiss = viewModel::closeDetail,
            onDelete = { viewModel.delete(detail.script) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkerScriptDetailSheet(
    detail: WorkerDetailState,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val script = detail.script
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(20.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(script.id, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            script.usageModel?.let { DetailLine("Usage model", it) }
            script.handlers?.takeIf { it.isNotEmpty() }?.let { DetailLine("Handlers", it.joinToString(", ")) }
            script.createdOn?.let { DetailLine("Created", it) }
            script.modifiedOn?.let { DetailLine("Modified", it) }
            script.etag?.let { DetailLine("ETag", it) }

            if (detail.isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text("Loading source and triggers…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                CronTriggers(detail)
                WorkerSource(detail)
            }

            Button(
                onClick = { confirmDelete = true },
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = contentColorFor(MaterialTheme.colorScheme.error)
                )
            ) {
                if (isDeleting) CircularProgressIndicator(Modifier.padding(end = 6.dp))
                Text("Delete script")
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete script?") },
            text = { Text("\"${script.id}\" will be permanently deleted. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CronTriggers(detail: WorkerDetailState) {
    when {
        detail.schedulesError != null -> DetailLine("Cron triggers", "Couldn't load: ${detail.schedulesError}")
        detail.schedules.isEmpty() -> DetailLine("Cron triggers", "None")
        else -> DetailLine("Cron triggers", detail.schedules.joinToString("\n") { it.cron })
    }
}

@Composable
private fun WorkerSource(detail: WorkerDetailState) {
    val source = detail.source
    Column {
        Text("Source", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            detail.sourceError != null -> Text(
                "Couldn't load: ${detail.sourceError}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            source.isNullOrBlank() -> Text("Empty", style = MaterialTheme.typography.bodyMedium)
            // Read-only: the app can't deploy code, so editing the text here would be a lie.
            else -> Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Text(source, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
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
