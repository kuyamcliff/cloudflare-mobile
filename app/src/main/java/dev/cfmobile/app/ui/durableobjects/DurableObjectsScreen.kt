package dev.cfmobile.app.ui.durableobjects

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.ReadOnlyListRow

@Composable
fun DurableObjectsScreen(viewModel: DurableObjectsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Durable Objects",
        onBack = onBack,
        state = uiState.namespaces,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Durable Object namespaces yet",
        key = { it.id },
        searchPlaceholder = "Search namespaces",
        searchMatches = { namespace, query ->
            namespace.name.contains(query, ignoreCase = true) ||
                namespace.script.orEmpty().contains(query, ignoreCase = true)
        },
        header = {
            Text(
                "Namespaces are created by deploying a Worker that declares them, so there's nothing to add or remove here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    ) { namespace ->
        ReadOnlyListRow(
            icon = Icons.Filled.Storage,
            title = namespace.name,
            monospaceTitle = true,
            subtitle = listOfNotNull(namespace.script, namespace.className).joinToString(" · ").ifBlank { null },
            detail = if (namespace.useSqlite == true) "SQLite-backed" else null
        )
    }
}
