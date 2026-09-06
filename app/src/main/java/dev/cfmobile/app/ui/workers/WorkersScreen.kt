package dev.cfmobile.app.ui.workers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.WorkerScript
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.ReadOnlyListRow

@Composable
fun WorkersScreen(viewModel: WorkersViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var detailScript by remember { mutableStateOf<WorkerScript?>(null) }

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
            onClick = { detailScript = script }
        )
    }

    detailScript?.let { script ->
        WorkerScriptDetailSheet(
            script,
            isDeleting = uiState.deletingId == script.id,
            onDismiss = { detailScript = null },
            onDelete = {
                viewModel.delete(script)
                detailScript = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkerScriptDetailSheet(
    script: WorkerScript,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(script.id, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            script.usageModel?.let { DetailLine("Usage model", it) }
            script.handlers?.takeIf { it.isNotEmpty() }?.let { DetailLine("Handlers", it.joinToString(", ")) }
            script.createdOn?.let { DetailLine("Created", it) }
            script.modifiedOn?.let { DetailLine("Modified", it) }
            script.etag?.let { DetailLine("ETag", it) }
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
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
