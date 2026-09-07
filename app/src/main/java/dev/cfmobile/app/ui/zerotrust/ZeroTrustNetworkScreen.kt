package dev.cfmobile.app.ui.zerotrust

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
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
import dev.cfmobile.app.ui.rules.LabelledSwitch
import dev.cfmobile.app.ui.rules.RuleDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroTrustNetworkScreen(viewModel: ZeroTrustNetworkViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val header: @Composable () -> Unit = {
        PrimaryScrollableTabRow(selectedTabIndex = uiState.tab.ordinal, edgePadding = 0.dp) {
            ZeroTrustNetworkTab.entries.forEach { tab ->
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
        ZeroTrustNetworkTab.ROUTES -> CfListScreen(
            title = "Zero Trust Network",
            onBack = onBack,
            state = uiState.routes,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No private network routes",
            key = { it.id },
            onCreate = viewModel::openForm,
            createContentDescription = "Add route",
            header = header
        ) { route ->
            DeletableListRow(
                icon = Icons.AutoMirrored.Filled.AltRoute,
                title = route.network,
                monospaceTitle = true,
                subtitle = routeSummary(route),
                isDeleting = uiState.deletingId == route.id,
                deleteContentDescription = "Delete route",
                confirmTitle = "Delete route?",
                confirmText = "Clients lose access to ${route.network} through this tunnel.",
                onDelete = { viewModel.deleteRoute(route) }
            )
        }

        ZeroTrustNetworkTab.VIRTUAL_NETWORKS -> CfListScreen(
            title = "Zero Trust Network",
            onBack = onBack,
            state = uiState.virtualNetworks,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No virtual networks",
            key = { it.id },
            onCreate = viewModel::openForm,
            createContentDescription = "Create virtual network",
            header = header
        ) { network ->
            DeletableListRow(
                icon = Icons.Filled.Hub,
                title = network.name,
                subtitle = network.comment,
                detail = if (network.isDefault == true) "Default virtual network" else null,
                isDeleting = uiState.deletingId == network.id,
                deleteContentDescription = "Delete virtual network",
                confirmTitle = "Delete virtual network?",
                confirmText = "Routes assigned to \"${network.name}\" have to be moved first, or they stop resolving.",
                onDelete = { viewModel.deleteVirtualNetwork(network) }
            )
        }

        ZeroTrustNetworkTab.LOCATIONS -> CfListScreen(
            title = "Zero Trust Network",
            onBack = onBack,
            state = uiState.locations,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No Gateway DNS locations",
            key = { it.id },
            onCreate = viewModel::openForm,
            createContentDescription = "Create location",
            header = header
        ) { location ->
            DeletableListRow(
                icon = Icons.Filled.Dns,
                title = location.name,
                subtitle = locationSummary(location),
                detail = location.ip,
                isDeleting = uiState.deletingId == location.id,
                deleteContentDescription = "Delete location",
                confirmTitle = "Delete location?",
                confirmText = "DNS from these networks stops being filtered by Gateway.",
                onDelete = { viewModel.deleteLocation(location) }
            )
        }

        ZeroTrustNetworkTab.WARP -> CfListScreen(
            title = "Zero Trust Network",
            onBack = onBack,
            state = uiState.warpProfiles,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No WARP profiles",
            key = { it.policyId ?: it.name.orEmpty() },
            header = {
                header()
                Text(
                    // Split tunnels and fallback domains are list editors of their own.
                    "Read-only: a profile's split tunnel and fallback-domain lists need more room than this screen has.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
        ) { profile ->
            ReadOnlyListRow(
                icon = Icons.Filled.PhoneAndroid,
                title = profile.name ?: profile.policyId.orEmpty(),
                subtitle = warpProfileSummary(profile),
                detail = profile.match
            )
        }
    }

    uiState.routeForm?.let { form ->
        RouteSheet(form, uiState, onDismiss = viewModel::closeForms, viewModel = viewModel)
    }
    uiState.virtualNetworkForm?.let { form ->
        VirtualNetworkSheet(form, onDismiss = viewModel::closeForms, viewModel = viewModel)
    }
    uiState.locationForm?.let { form ->
        LocationSheet(form, onDismiss = viewModel::closeForms, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteSheet(
    form: TunnelRouteFormState,
    uiState: ZeroTrustNetworkUiState,
    onDismiss: () -> Unit,
    viewModel: ZeroTrustNetworkViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add route", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.network,
                onValueChange = { v -> viewModel.updateRouteForm { it.copy(network = v) } },
                label = { Text("Network") },
                placeholder = { Text("10.0.0.0/8") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            // Tunnels are picked by name; Cloudflare wants their ids.
            if (uiState.tunnels.isEmpty()) {
                Text(
                    "No tunnels on this account to route through.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                RuleDropdown(
                    label = "Tunnel",
                    options = uiState.tunnels.map { it.id to it.name },
                    selected = form.tunnelId ?: uiState.tunnels.first().id,
                    onSelect = { v -> viewModel.updateRouteForm { it.copy(tunnelId = v) } }
                )
            }
            OutlinedTextField(
                value = form.comment,
                onValueChange = { v -> viewModel.updateRouteForm { it.copy(comment = v) } },
                label = { Text("Comment") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "The route stays on the default virtual network. Assigning one to a specific virtual network isn't offered here.",
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
private fun VirtualNetworkSheet(
    form: VirtualNetworkFormState,
    onDismiss: () -> Unit,
    viewModel: ZeroTrustNetworkViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create virtual network", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateVirtualNetworkForm { it.copy(name = v) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.comment,
                onValueChange = { v -> viewModel.updateVirtualNetworkForm { it.copy(comment = v) } },
                label = { Text("Comment") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "A virtual network lets two sites with overlapping private ranges both be routable, each behind its own tunnel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(
                isSaving = form.isSaving,
                onCancel = onDismiss,
                onSave = viewModel::saveVirtualNetwork,
                saveLabel = "Create"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSheet(
    form: GatewayLocationFormState,
    onDismiss: () -> Unit,
    viewModel: ZeroTrustNetworkViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create DNS location", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateLocationForm { it.copy(name = v) } },
                label = { Text("Location name") },
                placeholder = { Text("London office") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.networks,
                onValueChange = { v -> viewModel.updateLocationForm { it.copy(networks = v) } },
                label = { Text("Source networks") },
                placeholder = { Text("203.0.113.0/24") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp)
            )
            LabelledSwitch("Default location", form.clientDefault) { v ->
                viewModel.updateLocationForm { it.copy(clientDefault = v) }
            }
            Text(
                "One CIDR range per line - the networks whose DNS queries arrive at this location. Leave empty for a location clients reach over DoH instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(
                isSaving = form.isSaving,
                onCancel = onDismiss,
                onSave = viewModel::saveLocation,
                saveLabel = "Create"
            )
        }
    }
}
