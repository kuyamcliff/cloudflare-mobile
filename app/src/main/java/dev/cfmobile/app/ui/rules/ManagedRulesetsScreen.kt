package dev.cfmobile.app.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow

@Composable
fun ManagedRulesetsScreen(zoneName: String, viewModel: ManagedRulesetsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDeploy by remember { mutableStateOf<ManagedRulesetItem?>(null) }

    CfListScreen(
        title = "Managed WAF",
        subtitle = zoneName,
        onBack = onBack,
        state = uiState.items,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "Cloudflare didn't return any managed rulesets for this zone",
        key = { it.rulesetId },
        searchPlaceholder = "Search rulesets",
        searchMatches = { item, query ->
            item.name.contains(query, ignoreCase = true) ||
                item.description.orEmpty().contains(query, ignoreCase = true)
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
    ) { item ->
        DeletableListRow(
            icon = Icons.Filled.Shield,
            title = item.name,
            subtitle = item.description,
            detail = deploymentLabel(item),
            isDeleting = uiState.busyRulesetId == item.rulesetId,
            deleteContentDescription = "Remove deployment",
            confirmTitle = "Remove this ruleset?",
            confirmText = "\"${item.name}\" will stop filtering traffic on this zone.",
            // Undeploying only makes sense for a ruleset that is actually deployed; the row
            // still needs a delete handler, so it no-ops otherwise.
            onDelete = { if (item.isDeployed) viewModel.undeploy(item) },
            trailing = {
                if (uiState.busyRulesetId == item.rulesetId) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                } else {
                    Switch(
                        checked = item.isDeployed && item.enabled,
                        onCheckedChange = { checked ->
                            // Turning one on is a change to what the WAF blocks, so it is
                            // confirmed; turning it off is not, since that is the safe way out.
                            if (checked) confirmDeploy = item else viewModel.setDeployed(item, false)
                        }
                    )
                }
            }
        )
    }

    confirmDeploy?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDeploy = null },
            title = { Text("Deploy ${item.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cloudflare will start evaluating this managed ruleset against traffic on this zone.")
                    Text(
                        "Per-rule overrides and the paranoia/sensitivity settings inside a managed ruleset aren't editable here - use the dashboard for those.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDeployed(item, true)
                    confirmDeploy = null
                }) { Text("Deploy") }
            },
            dismissButton = { TextButton(onClick = { confirmDeploy = null }) { Text("Cancel") } }
        )
    }
}

/** Says plainly whether a ruleset is filtering traffic right now. */
fun deploymentLabel(item: ManagedRulesetItem): String = when {
    !item.isDeployed -> "Not deployed"
    !item.enabled -> "Deployed but disabled"
    item.expression != null && item.expression != "true" -> "Active on: ${item.expression}"
    else -> "Active on all traffic"
}
