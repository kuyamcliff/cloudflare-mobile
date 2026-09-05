package dev.cfmobile.app.ui.workers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import dev.cfmobile.app.data.remote.dto.WorkerScript
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.StateContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkersScreen(viewModel: WorkersViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var detailScript by remember { mutableStateOf<WorkerScript?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workers") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        StateContent(state = uiState.scripts, onRetry = viewModel::refresh) { scripts ->
            if (scripts.isEmpty()) {
                EmptyState("No Worker scripts yet", Modifier.padding(padding))
            } else {
                LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(scripts, key = { it.id }) { script ->
                        WorkerScriptRow(script, onClick = { detailScript = script })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
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

@Composable
private fun WorkerScriptRow(script: WorkerScript, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(script.id, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
            script.modifiedOn?.let {
                Text("Modified $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
