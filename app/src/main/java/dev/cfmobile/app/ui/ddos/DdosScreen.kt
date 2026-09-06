package dev.cfmobile.app.ui.ddos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.DdosRule
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.StatusPill
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DdosScreen(zoneName: String, viewModel: DdosViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("DDoS Protection", zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            RefreshableStateContent(
                state = uiState.ruleset,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh
            ) { ruleset ->
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        Text(
                            "Cloudflare's L7 DDoS protection runs on every zone and can't be turned off. This shows the managed ruleset and any overrides layered on top of it - read-only here, since changing DDoS sensitivity deserves more context than a phone screen gives.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                    if (ruleset == null) {
                        item {
                            Text(
                                "No overrides configured - the managed DDoS ruleset is running with Cloudflare's defaults.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        item {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(ruleset.name ?: "DDoS ruleset", style = MaterialTheme.typography.titleSmall)
                                ruleset.description?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                ruleset.lastUpdated?.let {
                                    Text("Updated $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        if (ruleset.rules.isEmpty()) {
                            item {
                                Text(
                                    "This ruleset has no override rules.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            items(ruleset.rules, key = { it.id }) { rule ->
                                DdosRuleRow(rule)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DdosRuleRow(rule: DdosRule) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(rule.description ?: rule.id, style = MaterialTheme.typography.bodyMedium)
        DdosRuleStatus(rule)
    }
}

@Composable
private fun DdosRuleStatus(rule: DdosRule) {
    Column(Modifier.padding(top = 4.dp)) {
        StatusPill(
            if (rule.enabled) "Enabled" else "Disabled",
            if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        rule.action?.let {
            Text(
                "Action: ${it.replace('_', ' ')}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
