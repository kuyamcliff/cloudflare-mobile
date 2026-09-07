package dev.cfmobile.app.ui.tunnels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions

@Composable
fun TunnelsScreen(viewModel: TunnelsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Tunnels",
        onBack = onBack,
        state = uiState.tunnels,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No tunnels yet",
        key = { it.id },
        onCreate = viewModel::openForm,
        createContentDescription = "Create tunnel",
        searchPlaceholder = "Search tunnels",
        searchMatches = { tunnel, query -> tunnel.name.contains(query, ignoreCase = true) }
    ) { tunnel ->
        DeletableListRow(
            icon = Icons.Filled.Router,
            title = tunnel.name,
            subtitle = tunnel.status?.replaceFirstChar { it.uppercase() },
            isDeleting = uiState.deletingId == tunnel.id,
            deleteContentDescription = "Delete tunnel",
            confirmTitle = "Delete tunnel?",
            confirmText = "\"${tunnel.name}\" will be permanently deleted. This can't be undone.",
            onDelete = { viewModel.delete(tunnel) }
        )
    }

    uiState.form?.let { form ->
        CreateTunnelSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTunnelSheet(form: TunnelFormState, onDismiss: () -> Unit, viewModel: TunnelsViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Create tunnel", style = MaterialTheme.typography.titleMedium)
            Text(
                "This registers a tunnel with Cloudflare. Running it still needs the cloudflared daemon on a machine elsewhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Tunnel name") },
                placeholder = { Text("my-tunnel") },
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
