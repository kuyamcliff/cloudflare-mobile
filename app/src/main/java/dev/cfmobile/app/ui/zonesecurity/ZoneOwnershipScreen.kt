package dev.cfmobile.app.ui.zonesecurity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.ToggleRow
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneOwnershipScreen(zoneName: String, viewModel: ZoneOwnershipViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmRemoveHold by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Zone Ownership", zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }

            Text(
                "Zone hold",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp)
            )
            ToggleRow(
                title = "Hold this domain",
                subtitle = holdSummary(uiState.hold),
                checked = uiState.hold?.hold == true,
                isSaving = uiState.isSaving,
                onToggle = { enabled ->
                    // Removing a hold is the direction that lets someone else claim the
                    // domain, so that's the one worth confirming.
                    if (enabled) viewModel.setHold(true) else confirmRemoveHold = true
                }
            )
            if (uiState.hold?.hold == true) {
                ToggleRow(
                    title = "Include subdomains",
                    subtitle = "Also stops subdomains of this zone being added elsewhere",
                    checked = uiState.hold?.includeSubdomains == true,
                    isSaving = uiState.isSaving,
                    onToggle = { viewModel.setHold(enabled = true, includeSubdomains = it) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Text(
                "Custom nameservers",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp)
            )
            StateContent(state = uiState.nameserverSets, onRetry = viewModel::refresh) { nameservers ->
                val sets = nameserverSets(nameservers)
                Column(Modifier.fillMaxWidth()) {
                    if (sets.isEmpty()) {
                        Text(
                            "This account has no custom nameserver sets. They're configured at the account level before a zone can use one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        ToggleRow(
                            title = "Use custom nameservers",
                            subtitle = "Answer for this zone on your own nameserver names instead of Cloudflare's",
                            checked = uiState.zoneNameservers?.enabled == true,
                            isSaving = uiState.isSaving,
                            onToggle = { viewModel.setCustomNameservers(it) }
                        )
                        if (uiState.zoneNameservers?.enabled == true) {
                            OptionRow(
                                title = "Nameserver set",
                                currentValue = uiState.zoneNameservers?.nsSet?.toString() ?: sets.first().first,
                                options = sets,
                                isSaving = uiState.isSaving,
                                onSelect = { viewModel.setCustomNameservers(enabled = true, nsSet = it.toIntOrNull()) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmRemoveHold) {
        AlertDialog(
            onDismissRequest = { confirmRemoveHold = false },
            title = { Text("Remove the hold?") },
            text = {
                Text("Without a hold, anyone who controls this domain's DNS can add it to a different Cloudflare account. Remove it only if you're moving the domain on purpose.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveHold = false
                    viewModel.setHold(false)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveHold = false }) { Text("Cancel") } }
        )
    }
}
