package dev.cfmobile.app.ui.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsFirewallScreen(viewModel: DnsFirewallViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "DNS Firewall",
        onBack = onBack,
        state = uiState.clusters,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No DNS Firewall clusters",
        key = { it.id },
        onCreate = viewModel::openForm,
        createContentDescription = "Create cluster",
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
                // Worth saying plainly: people arrive here looking for their zone's records.
                "Cloudflare resolvers fronting your own authoritative nameservers. This is a separate product from the DNS records on a zone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 8.dp)
            )
        }
    ) { cluster ->
        DeletableListRow(
            icon = Icons.Filled.Dns,
            title = cluster.name,
            subtitle = clusterSummary(cluster),
            // The addresses to point NS records at - the useful thing to read off a phone.
            detail = cluster.dnsFirewallIps?.joinToString(", ")?.ifBlank { null },
            isDeleting = uiState.deletingId == cluster.id,
            deleteContentDescription = "Delete cluster",
            confirmTitle = "Delete this cluster?",
            confirmText = "Queries to its addresses stop resolving. Anything still delegating to \"${cluster.name}\" goes dark until its NS records are moved.",
            onDelete = { viewModel.delete(cluster) }
        )
    }

    uiState.form?.let { form ->
        ClusterSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClusterSheet(
    form: DnsFirewallFormState,
    onDismiss: () -> Unit,
    viewModel: DnsFirewallViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create cluster", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Cluster name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.upstreamIps,
                onValueChange = { v -> viewModel.updateForm { it.copy(upstreamIps = v) } },
                label = { Text("Upstream nameservers") },
                placeholder = { Text("192.0.2.1") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp)
            )
            Text(
                "One IP address per line - your own authoritative servers, which Cloudflare will forward to. Cache TTLs, rate limits and negative caching keep Cloudflare's defaults; changing them is done from the dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save, saveLabel = "Create")
        }
    }
}
