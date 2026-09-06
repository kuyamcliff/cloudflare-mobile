package dev.cfmobile.app.ui.hyperdrive

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow

@Composable
fun HyperdriveScreen(viewModel: HyperdriveViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Hyperdrive",
        onBack = onBack,
        state = uiState.configs,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Hyperdrive configs yet",
        key = { it.id },
        searchPlaceholder = "Search configs",
        searchMatches = { config, query ->
            config.name.contains(query, ignoreCase = true) ||
                config.origin?.host.orEmpty().contains(query, ignoreCase = true)
        },
        header = {
            Text(
                "Creating a config means entering a database password, which doesn't belong on a phone keyboard - add new ones with wrangler or the dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    ) { config ->
        DeletableListRow(
            icon = Icons.Filled.Bolt,
            title = config.name,
            subtitle = hyperdriveOriginLabel(config),
            detail = if (config.caching?.disabled == true) "Caching disabled" else null,
            isDeleting = uiState.deletingId == config.id,
            deleteContentDescription = "Delete config",
            confirmTitle = "Delete Hyperdrive config?",
            confirmText = "\"${config.name}\" will be permanently deleted, and Workers bound to it will stop connecting through Hyperdrive. This can't be undone.",
            onDelete = { viewModel.delete(config) }
        )
    }
}
