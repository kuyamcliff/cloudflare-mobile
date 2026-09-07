package dev.cfmobile.app.ui.dns

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.ToggleRow
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSettingsScreen(zoneName: String, viewModel: DnsSettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("DNS Settings", zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 4.dp)
                )
            }
            RefreshableStateContent(
                state = uiState.settings,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh
            ) { settings ->
                Column {
                    Text(
                        // These apply to the whole zone, not to any one record.
                        "Zone-wide DNS behaviour. These are separate from the records themselves.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp, 8.dp)
                    )
                    DnsSetting.entries.forEach { setting ->
                        ToggleRow(
                            title = setting.title,
                            subtitle = setting.subtitle,
                            checked = setting.read(settings) == true,
                            isSaving = uiState.busySetting == setting,
                            onToggle = { viewModel.setSetting(setting, it) }
                        )
                    }
                    Text(
                        nameserverSummary(settings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp, 12.dp)
                    )
                    Text(
                        // Both belong to the nameserver assignment, which lives on the zone
                        // ownership screen and in the account's custom nameserver sets.
                        "Switching to custom nameservers and changing the NS TTL are done from Zone Ownership.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp, 0.dp)
                    )
                }
            }
        }
    }
}
