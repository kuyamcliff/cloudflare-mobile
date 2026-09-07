package dev.cfmobile.app.ui.magicnetwork

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
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
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.ReadOnlyListRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicNetworkScreen(viewModel: MagicNetworkViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val header: @Composable () -> Unit = {
        PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal) {
            MagicTab.entries.forEach { tab ->
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

    /** Shared by the two tunnel tabs, which differ only in their icon and empty message. */
    @Composable
    fun tunnelNote() {
        Text(
            // A tunnel carries the interface addresses and health checks of a physical link;
            // getting one wrong takes a site down until someone is at a console.
            "Read-only. A tunnel's interface addresses and health checks are set with Cloudflare's networking team, not from a phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 8.dp)
        )
    }

    when (uiState.tab) {
        MagicTab.GRE -> CfListScreen(
            title = "Magic WAN",
            onBack = onBack,
            state = uiState.greTunnels,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No GRE tunnels configured",
            key = { it.id },
            header = { header(); tunnelNote() }
        ) { tunnel ->
            ReadOnlyListRow(
                icon = Icons.Filled.Hub,
                title = tunnel.name,
                subtitle = tunnelEndpointsLabel(tunnel.cloudflareEndpoint, tunnel.customerEndpoint),
                detail = tunnel.description ?: tunnel.interfaceAddress
            )
        }

        MagicTab.IPSEC -> CfListScreen(
            title = "Magic WAN",
            onBack = onBack,
            state = uiState.ipsecTunnels,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No IPsec tunnels configured",
            key = { it.id },
            header = { header(); tunnelNote() }
        ) { tunnel ->
            ReadOnlyListRow(
                icon = Icons.Filled.Lock,
                title = tunnel.name,
                subtitle = tunnelEndpointsLabel(tunnel.cloudflareEndpoint, tunnel.customerEndpoint),
                detail = tunnel.description ?: tunnel.interfaceAddress
            )
        }

        MagicTab.ROUTES -> CfListScreen(
            title = "Magic WAN",
            onBack = onBack,
            state = uiState.routes,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No static routes configured",
            key = { it.id },
            onCreate = viewModel::openRouteForm,
            createContentDescription = "Add route",
            header = header
        ) { route ->
            DeletableListRow(
                icon = Icons.Filled.Route,
                title = route.prefix,
                monospaceTitle = true,
                subtitle = routeSummary(route),
                detail = route.description,
                isDeleting = uiState.deletingId == route.id,
                deleteContentDescription = "Delete route",
                confirmTitle = "Delete this route?",
                confirmText = "Traffic for ${route.prefix} stops being sent over Magic WAN as soon as this is removed.",
                onDelete = { viewModel.deleteRoute(route) }
            )
        }

        MagicTab.SITES -> CfListScreen(
            title = "Magic WAN",
            onBack = onBack,
            state = uiState.sites,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No sites configured",
            key = { it.id },
            header = {
                header()
                Text(
                    // Creating a site means binding connector hardware to it.
                    "Locations behind a Magic WAN connector. Tap one to see its LAN and WAN interfaces. Adding a site means registering its connector, which is done from the dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
        ) { site ->
            ReadOnlyListRow(
                icon = Icons.Filled.Business,
                title = site.name ?: site.id,
                subtitle = siteSummary(site),
                detail = site.location?.let { location ->
                    listOfNotNull(location.lat, location.lon).joinToString(", ").ifBlank { null }
                },
                onClick = { viewModel.openSite(site) }
            )
        }
    }

    uiState.routeForm?.let { form ->
        RouteSheet(form, onDismiss = viewModel::closeRouteForm, viewModel = viewModel)
    }
    uiState.selectedSite?.let { site ->
        SiteSheet(site, onDismiss = viewModel::closeSite)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteSheet(
    form: MagicRouteFormState,
    onDismiss: () -> Unit,
    viewModel: MagicNetworkViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add static route", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.prefix,
                onValueChange = { v -> viewModel.updateRouteForm { it.copy(prefix = v) } },
                label = { Text("Prefix") },
                placeholder = { Text("10.0.0.0/8") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.nexthop,
                onValueChange = { v -> viewModel.updateRouteForm { it.copy(nexthop = v) } },
                label = { Text("Next hop") },
                placeholder = { Text("10.0.0.1") },
                supportingText = { Text("The tunnel interface address traffic is handed to") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.priority,
                    onValueChange = { v -> viewModel.updateRouteForm { it.copy(priority = v) } },
                    label = { Text("Priority") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = form.weight,
                    onValueChange = { v -> viewModel.updateRouteForm { it.copy(weight = v) } },
                    label = { Text("Weight") },
                    placeholder = { Text("optional") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updateRouteForm { it.copy(description = v) } },
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Lowest priority wins when two routes cover the same prefix; equal priorities share traffic by weight. A route takes effect as soon as it's saved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::saveRoute, saveLabel = "Add")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteSheet(site: SiteInterfaces, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(site.site.name ?: site.site.id, style = MaterialTheme.typography.titleMedium)
            when {
                site.isLoading -> CircularProgressIndicator()
                else -> {
                    site.error?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        if (site.lans.isEmpty()) "No LAN interfaces" else "LAN",
                        style = MaterialTheme.typography.labelLarge
                    )
                    site.lans.forEach { lan ->
                        InterfaceRow(
                            name = lan.name ?: lan.id,
                            physport = lan.physport,
                            vlanTag = lan.vlanTag,
                            address = lan.staticAddressing?.address,
                            note = if (lan.haLink == true) "HA link" else null
                        )
                    }
                    Text(
                        if (site.wans.isEmpty()) "No WAN interfaces" else "WAN",
                        style = MaterialTheme.typography.labelLarge
                    )
                    site.wans.forEach { wan ->
                        InterfaceRow(
                            name = wan.name ?: wan.id,
                            physport = wan.physport,
                            vlanTag = wan.vlanTag,
                            address = wan.staticAddressing?.address,
                            note = wan.priority?.let { "priority $it" }
                        )
                    }
                    Text(
                        "Read-only: changing an interface's addressing reconfigures the connector at that site.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InterfaceRow(name: String, physport: Int?, vlanTag: Int?, address: String?, note: String?) {
    Column {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(
            listOfNotNull(
                physport?.let { "port $it" },
                vlanTag?.let { "VLAN $it" },
                address,
                note
            ).joinToString(" · ").ifBlank { "No addressing reported" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )
    }
}
