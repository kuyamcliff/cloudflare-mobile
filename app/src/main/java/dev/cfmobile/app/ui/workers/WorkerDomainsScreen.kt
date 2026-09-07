package dev.cfmobile.app.ui.workers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.rules.RuleDropdown

@Composable
fun WorkerDomainsScreen(viewModel: WorkerDomainsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Worker Domains",
        onBack = onBack,
        state = uiState.domains,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Worker domains on this account",
        key = { it.id },
        onCreate = viewModel::openForm,
        createContentDescription = "Attach a domain",
        searchPlaceholder = "Search domains",
        searchMatches = { domain, query ->
            domain.hostname.contains(query, ignoreCase = true) ||
                domain.service.orEmpty().contains(query, ignoreCase = true)
        },
        header = {
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 4.dp)
                )
            }
        }
    ) { domain ->
        DeletableListRow(
            icon = Icons.Filled.Language,
            title = domain.hostname,
            monospaceTitle = true,
            subtitle = workerDomainSummary(domain),
            detail = domain.environment,
            isDeleting = uiState.deletingId == domain.id,
            deleteContentDescription = "Detach domain",
            confirmTitle = "Detach this domain?",
            confirmText = "\"${domain.hostname}\" will stop routing to its Worker immediately. This can't be undone.",
            onDelete = { viewModel.delete(domain) }
        )
    }

    uiState.form?.let { form ->
        AttachDomainSheet(form, uiState, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachDomainSheet(
    form: WorkerDomainFormState,
    uiState: WorkerDomainsUiState,
    onDismiss: () -> Unit,
    viewModel: WorkerDomainsViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Attach a domain", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.hostname,
                onValueChange = { v -> viewModel.updateForm { it.copy(hostname = v) } },
                label = { Text("Hostname") },
                placeholder = { Text("api.example.com") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            // Zones and Workers are picked by name; Cloudflare wants their ids, which nobody
            // should be typing on a phone.
            if (uiState.zones.isEmpty()) {
                Text(
                    "No zones on this account to attach a hostname to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                RuleDropdown(
                    label = "Zone",
                    options = uiState.zones.map { it.id to it.name },
                    selected = form.zoneId ?: uiState.zones.first().id,
                    onSelect = { v -> viewModel.updateForm { it.copy(zoneId = v) } }
                )
            }
            if (uiState.scripts.isEmpty()) {
                Text(
                    "No Workers on this account to bind.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                RuleDropdown(
                    label = "Worker",
                    options = uiState.scripts.map { it to it },
                    selected = form.service.ifBlank { uiState.scripts.first() },
                    onSelect = { v -> viewModel.updateForm { it.copy(service = v) } }
                )
            }
            Text(
                "A domain sends every request for that hostname to the Worker, replacing whatever the zone's DNS would have done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save, saveLabel = "Attach")
        }
    }
}
