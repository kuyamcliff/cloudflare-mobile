package dev.cfmobile.app.ui.healthchecks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MonitorHeart
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.StatusPill
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthChecksScreen(zoneName: String, viewModel: HealthChecksViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Health Checks", zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openForm) {
                Icon(Icons.Filled.Add, contentDescription = "Create health check")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            RefreshableStateContent(
                state = uiState.checks,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh
            ) { checks ->
                if (checks.isEmpty()) {
                    EmptyState("No health checks yet.\n\nThese are the zone's standalone monitors, separate from the ones a load balancer pool attaches.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(checks, key = { it.id }) { check ->
                            DeletableListRow(
                                icon = Icons.Filled.MonitorHeart,
                                title = check.name,
                                subtitle = listOfNotNull(check.type, check.address).joinToString(" · ").ifBlank { null },
                                detail = check.failureReason ?: check.description,
                                isDeleting = uiState.deletingId == check.id,
                                deleteContentDescription = "Delete health check",
                                confirmTitle = "Delete health check?",
                                confirmText = "\"${check.name}\" will be permanently deleted and stop monitoring ${check.address}. This can't be undone.",
                                onDelete = { viewModel.delete(check) },
                                trailing = {
                                    check.status?.let { status ->
                                        StatusPill(
                                            status.replaceFirstChar { it.uppercase() },
                                            when (healthStatusTone(status)) {
                                                "healthy" -> MaterialTheme.colorScheme.primary
                                                "unhealthy" -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
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
        CreateCheckSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCheckSheet(form: HealthCheckFormState, onDismiss: () -> Unit, viewModel: HealthChecksViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Create health check", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Check name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.address,
                onValueChange = { v -> viewModel.updateForm { it.copy(address = v) } },
                label = { Text("Address") },
                placeholder = { Text("origin.example.com or 203.0.113.10") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            OptionRow(
                title = "Type",
                currentValue = form.type.name,
                options = HealthCheckType.entries.map { it.name to it.label },
                isSaving = false,
                onSelect = { v -> viewModel.updateForm { it.copy(type = HealthCheckType.valueOf(v)) } }
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
                label = { Text("Description (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            if (form.error != null) {
                Text(
                    form.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            FormActions(
                isSaving = form.isSaving,
                onCancel = onDismiss,
                onSave = viewModel::save,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}
