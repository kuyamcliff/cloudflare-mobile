package dev.cfmobile.app.ui.apishield

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.ListSearchField
import dev.cfmobile.app.ui.common.ReadOnlyListRow
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiShieldScreen(zoneName: String, viewModel: ApiShieldViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("API Shield", zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                "Endpoints Cloudflare has discovered from real traffic to this zone. Schema validation and mTLS aren't managed here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ListSearchField(value = query, onValueChange = { query = it }, placeholder = "Search endpoints")
            RefreshableStateContent(
                state = uiState.operations,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh
            ) { allOperations ->
                val operations = if (query.isBlank()) allOperations else allOperations.filter {
                    operationLabel(it).contains(query.trim(), ignoreCase = true) ||
                        it.host.orEmpty().contains(query.trim(), ignoreCase = true)
                }
                when {
                    allOperations.isEmpty() -> EmptyState("No API endpoints discovered yet.")
                    operations.isEmpty() -> EmptyState("Nothing matches \"${query.trim()}\".")
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(operations, key = { it.operationId }) { operation ->
                            ReadOnlyListRow(
                                icon = Icons.Filled.Api,
                                title = operationLabel(operation),
                                monospaceTitle = true,
                                subtitle = operation.host,
                                detail = operation.lastUpdated?.let { "Updated $it" }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}
