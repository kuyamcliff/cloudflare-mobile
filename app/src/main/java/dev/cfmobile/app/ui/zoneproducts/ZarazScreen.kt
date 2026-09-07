package dev.cfmobile.app.ui.zoneproducts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.ZarazConfig
import dev.cfmobile.app.ui.common.RefreshableStateContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZarazScreen(zoneName: String, viewModel: ZarazViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Zaraz", style = MaterialTheme.typography.titleMedium)
                        Text(
                            zoneName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        RefreshableStateContent(
            state = uiState.config,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding)
        ) { config ->
            ZarazBody(config)
        }
    }
}

@Composable
private fun ZarazBody(config: ZarazConfig) {
    val tools = zarazTools(config)
    val settings = zarazPrivacySettings(config)
    val triggers = zarazTriggerNames(config)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            // Cloudflare's write endpoint replaces the whole configuration document, and this
            // app models only part of it - editing here could drop fields it never parsed.
            "Read-only. Zaraz keeps its whole configuration in one document that Cloudflare replaces wholesale on write, so changes belong in the dashboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Section("Tools") {
            if (tools.isEmpty()) {
                Text("No third-party tools configured", style = MaterialTheme.typography.bodyMedium)
            } else {
                tools.forEach { tool ->
                    LabelValue(
                        label = tool.name,
                        value = listOfNotNull(tool.type, if (tool.enabled) "enabled" else "disabled")
                            .joinToString(" · ")
                    )
                }
            }
        }

        Section("Triggers") {
            if (triggers.isEmpty()) {
                Text("No triggers configured", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(triggers.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (settings.isNotEmpty()) {
            Section("Data sent to tools") {
                settings.forEach { setting ->
                    LabelValue(setting.label, if (setting.enabled) "On" else "Off")
                }
            }
        }

        config.zarazVersion?.let {
            Text(
                "Configuration version $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        content()
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
