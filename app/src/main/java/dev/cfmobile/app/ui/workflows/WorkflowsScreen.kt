package dev.cfmobile.app.ui.workflows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        InstancesSheet(name, uiState.instances, onDismiss = viewModel::closeInstances)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstancesSheet(
    workflowName: String,
    instances: UiState<List<WorkflowInstance>>?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(workflowName, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            Text("Recent instances", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            InstanceRow(instance)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceRow(instance: WorkflowInstance) {
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
    }
}

@Composable
private fun workflowStatusColor(status: String?) = when (workflowStatusTone(status)) {
    "success" -> MaterialTheme.colorScheme.primary
    "error" -> MaterialTheme.colorScheme.error
    "pending" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
