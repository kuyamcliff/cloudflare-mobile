package dev.cfmobile.app.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.components.NavRow
import dev.cfmobile.app.ui.components.SectionHeader
import dev.cfmobile.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit, onProfileSwitched: () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    TextField(
                        value = ui.query, onValueChange = viewModel::setQuery, singleLine = true,
                        placeholder = { Text("Zones, features, accounts, APIs") },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (ui.zonesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxSize()) {
                if (ui.query.isBlank()) {
                    item("help") {
                        Text(
                            "Search everything at once, or narrow it with zone:, account:, feature:, api: or profile:",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                if (ui.features.isNotEmpty()) item("fh") { SectionHeader("Features") }
                items(ui.features, key = { "f" + it.route }) { f -> NavRow(f.title, f.subtitle, onClick = { onNavigate(f.route) }) }
                if (ui.zones.isNotEmpty()) item("zh") { SectionHeader("Zones") }
                items(ui.zones, key = { "z" + it.id }) { z ->
                    NavRow(z.name, listOfNotNull(z.account?.name, z.status, z.plan?.name).joinToString(" · "), onClick = {
                        viewModel.selectZone(z); onNavigate(Routes.zoneMenu(z.id, z.name))
                    })
                }
                if (ui.accounts.isNotEmpty()) item("ah") { SectionHeader("Accounts") }
                items(ui.accounts, key = { "a" + it.id }) { a -> NavRow(a.name, a.id, onClick = { viewModel.selectAccount(a); onBack() }) }
                if (ui.profiles.isNotEmpty()) item("ph") { SectionHeader("Profiles") }
                items(ui.profiles, key = { "p" + it.id }) { p -> NavRow(p.label, "Switch to this profile", onClick = { viewModel.switchProfile(p.id); onProfileSwitched() }) }
                if (ui.endpoints.isNotEmpty()) item("eh") { SectionHeader("API operations") }
                items(ui.endpoints, key = { "e" + it.id + it.method + it.path }) { e ->
                    NavRow("${e.method} ${e.summary}", e.path, onClick = { onNavigate(Routes.explorer(endpointId = e.id)) })
                }
                if (ui.query.isNotBlank() && !ui.zonesLoading && ui.features.isEmpty() && ui.zones.isEmpty() && ui.accounts.isEmpty() && ui.endpoints.isEmpty() && ui.profiles.isEmpty()) {
                    item("none") { Text("No matches for \"${ui.query}\".", modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }
}
