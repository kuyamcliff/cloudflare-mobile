package dev.cfmobile.app.ui.zoneproducts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.UiState

@Composable
fun SnippetsScreen(zoneName: String, viewModel: SnippetsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val header: @Composable () -> Unit = {
        PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal) {
            SnippetsTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.tab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(tab.label) }
                )
            }
        }
        uiState.error?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp, 4.dp)
            )
        }
    }

    when (uiState.tab) {
        SnippetsTab.SNIPPETS -> CfListScreen(
            title = "Snippets",
            subtitle = zoneName,
            onBack = onBack,
            state = uiState.snippets,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No snippets on this zone",
            key = { it.snippetName },
            header = header
        ) { snippet ->
            DeletableListRow(
                icon = Icons.Filled.Code,
                title = snippet.snippetName,
                monospaceTitle = true,
                subtitle = snippet.modifiedOn?.let { "Modified $it" },
                isDeleting = uiState.busyId == snippet.snippetName,
                deleteContentDescription = "Delete snippet",
                confirmTitle = "Delete snippet?",
                confirmText = "\"${snippet.snippetName}\" will be permanently deleted, and any rule that runs it will stop working.",
                onDelete = { viewModel.delete(snippet) },
                onClick = { viewModel.openSource(snippet) }
            )
        }

        SnippetsTab.RULES -> CfListScreen(
            title = "Snippets",
            subtitle = zoneName,
            onBack = onBack,
            state = uiState.rules,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No snippet rules on this zone",
            key = { it.id ?: it.expression },
            header = header
        ) { rule ->
            DeletableListRow(
                icon = Icons.AutoMirrored.Filled.Rule,
                title = rule.description?.takeIf { it.isNotBlank() } ?: snippetRuleSummary(rule),
                subtitle = if (rule.description.isNullOrBlank()) null else snippetRuleSummary(rule),
                detail = rule.expression,
                isDeleting = uiState.busyId == rule.id,
                deleteContentDescription = "Delete rule",
                confirmTitle = "Delete rule?",
                confirmText = "This snippet will stop running on matching requests. The snippet itself is kept.",
                onDelete = { viewModel.deleteRule(rule) },
                trailing = {
                    Switch(
                        checked = rule.enabled,
                        enabled = uiState.busyId != rule.id,
                        onCheckedChange = { viewModel.setRuleEnabled(rule, it) }
                    )
                }
            )
        }
    }

    uiState.source?.let { source ->
        SnippetSourceSheet(source, onDismiss = viewModel::closeSource)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnippetSourceSheet(state: SnippetSourceState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(20.dp).heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(state.snippet.snippetName, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            Text(
                // Same limit as Workers: this app can't upload code, so an editable box here
                // would be a lie.
                "Read-only - uploading snippet code isn't supported from this app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (val source = state.source) {
                is UiState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                }
                is UiState.Error -> Text(
                    source.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                is UiState.Data -> Box(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        source.value.ifBlank { "This snippet is empty" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
