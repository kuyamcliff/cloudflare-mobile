package dev.cfmobile.app.ui.web3

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
fun Web3Screen(viewModel: Web3ViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Web3 Gateways",
        onBack = onBack,
        state = uiState.hostnames,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Web3 gateway hostnames",
        key = { it.id },
        onCreate = viewModel::openForm,
        createContentDescription = "Add gateway hostname",
        header = {
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 4.dp)
                )
            }
            Text(
                "An IPFS or Ethereum gateway served on your own hostname, so visitors reach decentralised content without running a node.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 8.dp)
            )
        }
    ) { hostname ->
        DeletableListRow(
            icon = Icons.Filled.Language,
            title = hostname.name,
            monospaceTitle = true,
            subtitle = web3Summary(hostname),
            detail = hostname.dnslink ?: hostname.description,
            isDeleting = uiState.deletingId == hostname.id,
            deleteContentDescription = "Delete gateway hostname",
            confirmTitle = "Delete this gateway?",
            confirmText = "${hostname.name} stops serving content. Its DNS record is left behind, so the hostname will fail to resolve content until that's cleaned up too.",
            onDelete = { viewModel.delete(hostname) }
        )
    }

    uiState.form?.let { form ->
        Web3Sheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Web3Sheet(form: Web3FormState, onDismiss: () -> Unit, viewModel: Web3ViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add gateway hostname", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Hostname") },
                placeholder = { Text("web3.example.com") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            RuleDropdown(
                label = "Gateway",
                options = Web3Target.entries.map { it to it.label },
                selected = form.target,
                onSelect = { v -> viewModel.updateForm { it.copy(target = v) } }
            )
            Text(
                form.target.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.target == Web3Target.IPFS) {
                OutlinedTextField(
                    value = form.dnslink,
                    onValueChange = { v -> viewModel.updateForm { it.copy(dnslink = v) } },
                    label = { Text("DNSLink") },
                    placeholder = { Text("/ipfs/bafy…") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Cloudflare creates the DNS record for this hostname itself. Editing a gateway's DNSLink afterwards isn't offered here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save, saveLabel = "Add")
        }
    }
}
