package dev.cfmobile.app.ui.workersai

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
fun WorkersAiScreen(viewModel: WorkersAiViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Workers AI",
        onBack = onBack,
        state = uiState.models,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No models available to this account",
        key = { it.id },
        searchPlaceholder = "Search models",
        searchMatches = { model, query ->
            model.name.contains(query, ignoreCase = true) ||
                model.description.orEmpty().contains(query, ignoreCase = true) ||
                model.task?.name.orEmpty().contains(query, ignoreCase = true)
        },
        header = {
            Text(
                "The model catalogue available to this account. Running inference happens in a Worker and bills per request, so it isn't done from here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    ) { model ->
        ReadOnlyListRow(
            icon = Icons.Filled.AutoAwesome,
            title = aiModelShortName(model),
            subtitle = model.task?.name,
            detail = model.description ?: model.name
        )
    }
}
