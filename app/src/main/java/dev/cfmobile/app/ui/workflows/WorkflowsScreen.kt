package dev.cfmobile.app.ui.workflows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.WorkflowInstance
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.ReadOnlyListRow
import dev.cfmobile.app.ui.common.StatusPill
import dev.cfmobile.app.ui.common.UiState

@Composable
fun WorkflowsScreen(viewModel: WorkflowsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Workflows",
        onBack = onBack,
        state = uiState.workflows,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Workflows deployed yet",
        key = { it.id },
        searchPlaceholder = "Search workflows",
        searchMatches = { workflow, query -> workflow.name.contains(query, ignoreCase = true) }
    ) { workflow ->
        ReadOnlyListRow(
            icon = Icons.Filled.AccountTree,
            title = workflow.name,
            subtitle = listOfNotNull(workflow.scriptName, workflow.className).joinToString(" · ").ifBlank { null },
            detail = workflow.modifiedOn?.let { "Updated $it" },
            onClick = { viewModel.selectWorkflow(workflow) }
        )
    }

    uiState.selectedWorkflowName?.let { name ->
        InstancesSheet(name, uiState, viewModel, onDismiss = viewModel::closeInstances)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstancesSheet(
    workflowName: String,
    uiState: WorkflowsUiState,
    viewModel: WorkflowsViewModel,
    onDismiss: () -> Unit
) {
    var confirmTerminate by remember { mutableStateOf<WorkflowInstance?>(null) }
    val instances = uiState.instances
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(workflowName, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            Text("Recent instances", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = viewModel::trigger, enabled = !uiState.isTriggering) {
                if (uiState.isTriggering) CircularProgressIndicator(Modifier.padding(end = 6.dp))
                Text(if (uiState.isTriggering) "Starting…" else "Start a run")
            }
            uiState.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (uiState.error == null) {
                uiState.message?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
            when (instances) {
                null, is UiState.Loading -> CircularProgressIndicator(Modifier.padding(12.dp))
                is UiState.Error -> Text(
                    instances.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                is UiState.Data -> if (instances.value.isEmpty()) {
                    Text("This Workflow hasn't run yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(instances.value, key = { it.id }) { instance ->
                            InstanceRow(
                                instance,
                                isBusy = uiState.busyInstanceId == instance.id,
                                onPause = { viewModel.setInstanceStatus(instance, "pause") },
                                onResume = { viewModel.setInstanceStatus(instance, "resume") },
                                onTerminate = { confirmTerminate = instance }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
    confirmTerminate?.let { instance ->
        ConfirmTerminateDialog(
            instance,
            onConfirm = { viewModel.setInstanceStatus(instance, "terminate") },
            onDismiss = { confirmTerminate = null }
        )
    }
}

@Composable
private fun InstanceRow(
    instance: WorkflowInstance,
    isBusy: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onTerminate: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        instance.status?.let { status ->
            StatusPill(status.replaceFirstChar { it.uppercase() }, workflowStatusColor(status))
        }
        Text(instance.id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        val timing = listOfNotNull(
            instance.startedOn?.let { "Started $it" },
            instance.endedOn?.let { "Ended $it" }
        ).joinToString(" · ")
        if (timing.isNotBlank()) {
            Text(timing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isBusy) {
            CircularProgressIndicator(Modifier.padding(4.dp))
        } else {
            // Only the transitions Cloudflare will accept for this run's state are offered.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canPause(instance)) TextButton(onClick = onPause) { Text("Pause") }
                if (canResume(instance)) TextButton(onClick = onResume) { Text("Resume") }
                if (canTerminate(instance)) TextButton(onClick = onTerminate) { Text("Terminate") }
            }
        }
    }
}

@Composable
private fun ConfirmTerminateDialog(instance: WorkflowInstance, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terminate this run?") },
        text = { Text("Run ${instance.id} stops where it is. Steps already done aren't undone, and the run can't be resumed.") },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text("Terminate") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun workflowStatusColor(status: String?) = when (workflowStatusTone(status)) {
    "success" -> MaterialTheme.colorScheme.primary
    "error" -> MaterialTheme.colorScheme.error
    "pending" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
