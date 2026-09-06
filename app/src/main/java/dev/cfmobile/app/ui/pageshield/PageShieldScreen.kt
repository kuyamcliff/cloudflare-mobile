package dev.cfmobile.app.ui.pageshield

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.ReadOnlyListRow
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.ToggleRow
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageShieldScreen(zoneName: String, viewModel: PageShieldViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Page Shield", zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ToggleRow(
                title = "Page Shield",
                subtitle = "Monitor the scripts and connections running on your pages",
                checked = uiState.isEnabled == true,
                isSaving = uiState.isTogglingEnabled,
                onToggle = viewModel::setEnabled
            )
            uiState.settingsError?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            PrimaryTabRow(selectedTabIndex = PageShieldTab.entries.indexOf(uiState.tab)) {
                PageShieldTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(if (tab == PageShieldTab.SCRIPTS) "Scripts" else "Connections") }
                    )
                }
            }
            when (uiState.tab) {
                PageShieldTab.SCRIPTS -> RefreshableStateContent(
                    state = uiState.scripts,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh
                ) { scripts ->
                    if (scripts.isEmpty()) {
                        EmptyState("No scripts detected yet. Page Shield reports scripts after real traffic loads your pages.")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(scripts, key = { it.id }) { script ->
                                ReadOnlyListRow(
                                    icon = Icons.Filled.Code,
                                    title = script.host ?: script.url ?: script.id,
                                    monospaceTitle = true,
                                    subtitle = script.url,
                                    detail = listOfNotNull(
                                        scriptIntegrityLabel(script),
                                        script.lastSeenAt?.let { "Last seen $it" }
                                    ).joinToString(" · ").ifBlank { null }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }

                PageShieldTab.CONNECTIONS -> RefreshableStateContent(
                    state = uiState.connections,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh
                ) { connections ->
                    if (connections.isEmpty()) {
                        EmptyState("No outbound connections detected yet.")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(connections, key = { it.id }) { connection ->
                                ReadOnlyListRow(
                                    icon = Icons.Filled.Link,
                                    title = connection.host ?: connection.url ?: connection.id,
                                    monospaceTitle = true,
                                    subtitle = connection.url,
                                    detail = connection.lastSeenAt?.let { "Last seen $it" }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }
}
