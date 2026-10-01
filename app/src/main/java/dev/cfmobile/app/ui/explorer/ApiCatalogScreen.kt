package dev.cfmobile.app.ui.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.api.EndpointScope
import dev.cfmobile.app.core.capabilities.CapabilityState
import dev.cfmobile.app.ui.components.Badge
import dev.cfmobile.app.ui.components.MethodBadge
import dev.cfmobile.app.ui.components.ThinDivider
import dev.cfmobile.app.ui.theme.StatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiCatalogScreen(viewModel: ApiCatalogViewModel, onBack: () -> Unit, onOpen: (endpointId: String) -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("All Cloudflare APIs")
                        Text(
                            if (ui.isLoading) "Loading schema" else "${ui.shown} of ${ui.total} operations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search path, operation or permission") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CatalogGrouping.entries.forEach { g ->
                    FilterChip(selected = ui.grouping == g, onClick = { viewModel.setGrouping(g) }, label = { Text(g.label) })
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = ui.scope == null, onClick = { viewModel.setScope(null) }, label = { Text("All scopes") })
                listOf(EndpointScope.ACCOUNT, EndpointScope.ZONE, EndpointScope.USER, EndpointScope.OTHER).forEach { s ->
                    FilterChip(selected = ui.scope == s, onClick = { viewModel.setScope(s) }, label = { Text(s.name.lowercase().replaceFirstChar(Char::uppercase)) })
                }
                if (ui.policiesKnown) {
                    FilterChip(
                        selected = ui.hideRestricted,
                        onClick = { viewModel.setHideRestricted(!ui.hideRestricted) },
                        label = { Text("Only usable (${ui.restrictedCount} hidden)".takeIf { ui.hideRestricted } ?: "Only usable") }
                    )
                }
                FilterChip(selected = !ui.hideDeprecated, onClick = { viewModel.setHideDeprecated(!ui.hideDeprecated) }, label = { Text("Include deprecated") })
            }
            if (!ui.policiesKnown && !ui.isLoading) {
                Text(
                    "This token's policies are not readable, so availability is learned as you use endpoints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (ui.isLoading) {
                Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize()) {
                ui.sections.forEach { section ->
                    val open = section.title in ui.expanded
                    item(key = "h-${section.title}") {
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.toggle(section.title) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(section.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${section.entries.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Collapse" else "Expand")
                        }
                        ThinDivider()
                    }
                    if (open) {
                        items(section.entries, key = { "${section.title}|${it.endpoint.id}|${it.endpoint.method}|${it.endpoint.path}" }) { entry ->
                            EndpointRow(entry, onClick = { onOpen(entry.endpoint.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointRow(entry: CatalogEntry, onClick: () -> Unit) {
    val e = entry.endpoint
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MethodBadge(e.method)
            Text(e.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            e.path,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            if (e.native) Badge("Native", MaterialTheme.colorScheme.primary) else Badge("Generic API", MaterialTheme.colorScheme.onSurfaceVariant)
            CapabilityBadge(entry.state)
            if (e.deprecated) Badge("Deprecated", StatusColors.warning)
            if (e.plans.isNotEmpty()) Badge(e.plans.joinToString("/"), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CapabilityBadge(state: CapabilityState) {
    when (state) {
        CapabilityState.AVAILABLE_READ -> Badge("Read", StatusColors.success)
        CapabilityState.AVAILABLE_WRITE -> Badge("Edit", StatusColors.success)
        CapabilityState.TOKEN_RESTRICTED -> Badge("Denied", StatusColors.error)
        CapabilityState.PLAN_RESTRICTED -> Badge("Plan limited", StatusColors.warning)
        CapabilityState.API_UNSUPPORTED -> Badge("Unsupported", StatusColors.warning)
        CapabilityState.UNKNOWN -> Unit
    }
}
