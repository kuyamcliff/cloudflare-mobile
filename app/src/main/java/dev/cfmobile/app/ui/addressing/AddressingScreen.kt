package dev.cfmobile.app.ui.addressing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.ReadOnlyListRow
import dev.cfmobile.app.ui.common.ToggleRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressingScreen(viewModel: AddressingViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val header: @Composable () -> Unit = {
        PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal) {
            AddressingTab.entries.forEach { tab ->
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
        AddressingTab.PREFIXES -> CfListScreen(
            title = "Addressing",
            onBack = onBack,
            state = uiState.prefixes,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No BYOIP prefixes on this account",
            key = { it.id },
            header = {
                header()
                Text(
                    // Bringing a prefix on needs a signed letter of authorization and
                    // Cloudflare's approval - not something a phone can do.
                    "Address space you own, announced by Cloudflare. Adding a prefix needs a letter of authorization and Cloudflare's approval, so it starts in the dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
        ) { prefix ->
            val blocked = advertisementBlockedReason(prefix)
            ReadOnlyListRow(
                icon = Icons.Filled.Lan,
                title = prefix.cidr,
                monospaceTitle = true,
                subtitle = prefix.description ?: prefixSummary(prefix),
                detail = blocked
                    ?: prefix.advertisedModifiedAt?.let { "Advertisement changed $it" }
                    ?: prefixSummary(prefix),
                trailing = {
                    if (uiState.busyId == prefix.id) {
                        CircularProgressIndicator(Modifier.padding(4.dp))
                    } else {
                        Switch(
                            checked = prefix.advertised == true,
                            // Left visible but inert when Cloudflare would refuse the change,
                            // so the current announcement state is still readable.
                            enabled = blocked == null,
                            onCheckedChange = { viewModel.setAdvertised(prefix, it) }
                        )
                    }
                }
            )
        }

        AddressingTab.ADDRESS_MAPS -> CfListScreen(
            title = "Addressing",
            onBack = onBack,
            state = uiState.addressMaps,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No address maps on this account",
            key = { it.id },
            onCreate = viewModel::openForm,
            createContentDescription = "Create address map",
            header = header
        ) { map ->
            DeletableListRow(
                icon = Icons.Filled.Map,
                title = map.description ?: map.id,
                subtitle = addressMapSummary(map),
                detail = map.defaultSni?.let { "Default SNI $it" },
                isDeleting = uiState.deletingId == map.id,
                deleteContentDescription = "Delete address map",
                confirmTitle = "Delete this address map?",
                confirmText = "Zones bound to it fall back to Cloudflare's shared anycast addresses. Anything pinned to these IPs - a firewall allowlist, a DNS A record - stops matching.",
                onDelete = { viewModel.deleteAddressMap(map) },
                onClick = { viewModel.openMap(map) },
                trailing = {
                    if (uiState.busyId == map.id) {
                        CircularProgressIndicator(Modifier.padding(4.dp))
                    } else {
                        Switch(
                            checked = map.enabled == true,
                            onCheckedChange = { viewModel.setAddressMapEnabled(map, it) }
                        )
                    }
                }
            )
        }
    }

    uiState.selectedMap?.let { map ->
        AddressMapSheet(map, isLoading = uiState.isLoadingMap, onDismiss = viewModel::closeMap)
    }
    uiState.form?.let { form ->
        AddressMapFormSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressMapSheet(
    map: dev.cfmobile.app.data.remote.dto.AddressMap,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(map.description ?: map.id, style = MaterialTheme.typography.titleMedium)
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                val ips = map.ips.orEmpty()
                Text(
                    if (ips.isEmpty()) "No addresses in this map yet" else "Addresses",
                    style = MaterialTheme.typography.labelLarge
                )
                ips.forEach { ip ->
                    Text(ip.ip, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                val memberships = map.memberships.orEmpty()
                Text(
                    if (memberships.isEmpty()) "Not bound to any zone or account" else "Bound to",
                    style = MaterialTheme.typography.labelLarge
                )
                memberships.forEach { membership ->
                    Text(
                        "${membership.kind ?: "member"} ${membership.identifier}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    // Both are separate PUT/DELETE endpoints per member, and getting the set
                    // wrong takes zones off these addresses.
                    "Adding or removing addresses and bindings is done from the dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressMapFormSheet(
    form: AddressMapFormState,
    onDismiss: () -> Unit,
    viewModel: AddressingViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create address map", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
                label = { Text("Description") },
                placeholder = { Text("EU customer IPs") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ToggleRow(
                title = "Enabled",
                subtitle = "Start serving traffic on this map's addresses",
                checked = form.enabled,
                isSaving = false,
                onToggle = { v -> viewModel.updateForm { it.copy(enabled = v) } }
            )
            Text(
                "A map starts empty. Cloudflare assigns its addresses, and binding it to a zone is done from the dashboard - until then, enabling it changes nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save, saveLabel = "Create")
        }
    }
}
