package dev.cfmobile.app.ui.loadbalancing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.LoadBalancer
import dev.cfmobile.app.data.remote.dto.LoadBalancerMonitor
import dev.cfmobile.app.data.remote.dto.LoadBalancerPool
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.UiState

private val TABS = listOf("Pools", "Load Balancers", "Monitors")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadBalancingScreen(viewModel: LoadBalancingViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Load Balancing") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            val createLabel = when (selectedTab) {
                0 -> "Add pool"
                1 -> "Add load balancer"
                else -> "Add monitor"
            }
            FloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        0 -> viewModel.openPoolForm()
                        1 -> viewModel.openLbForm()
                        else -> viewModel.openMonitorForm()
                    }
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = createLabel)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                TABS.forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                }
            }
            when (selectedTab) {
                0 -> PoolsTab(uiState, viewModel)
                1 -> LoadBalancersTab(uiState, viewModel)
                else -> MonitorsTab(uiState, viewModel)
            }
        }
    }

    uiState.poolForm?.let { form ->
        PoolFormSheet(
            form,
            monitors = (uiState.monitors as? UiState.Data)?.value ?: emptyList(),
            onDismiss = viewModel::closePoolForm,
            viewModel = viewModel
        )
    }
    uiState.lbForm?.let { form ->
        LbFormSheet(form, pools = (uiState.pools as? UiState.Data)?.value ?: emptyList(), onDismiss = viewModel::closeLbForm, viewModel = viewModel)
    }
    uiState.monitorForm?.let { form ->
        MonitorFormSheet(form, onDismiss = viewModel::closeMonitorForm, viewModel = viewModel)
    }
}

@Composable
private fun MonitorsTab(uiState: LoadBalancingUiState, viewModel: LoadBalancingViewModel) {
    StateContent(state = uiState.monitors, onRetry = viewModel::refreshMonitors) { monitors ->
        if (monitors.isEmpty()) {
            EmptyState("No health monitors yet - without one, a pool never fails an origin out")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(monitors, key = { it.id }) { monitor ->
                    DeletableListRow(
                        icon = Icons.Filled.MonitorHeart,
                        title = monitor.description?.takeIf { it.isNotBlank() } ?: monitor.type.uppercase(),
                        subtitle = monitorSummary(monitor),
                        isDeleting = uiState.deletingMonitorId == monitor.id,
                        deleteContentDescription = "Delete monitor",
                        confirmTitle = "Delete monitor?",
                        confirmText = "Pools using this monitor will stop health-checking their origins, so failover stops too.",
                        onDelete = { viewModel.deleteMonitor(monitor) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorFormSheet(form: MonitorFormState, onDismiss: () -> Unit, viewModel: LoadBalancingViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add HTTP monitor", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updateMonitorForm { it.copy(description = v) } },
                label = { Text("Description") },
                placeholder = { Text("Origin health") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.path,
                onValueChange = { v -> viewModel.updateMonitorForm { it.copy(path = v) } },
                label = { Text("Path") },
                placeholder = { Text("/health") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.expectedCodes,
                onValueChange = { v -> viewModel.updateMonitorForm { it.copy(expectedCodes = v) } },
                label = { Text("Expected status codes") },
                placeholder = { Text("2xx") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Interval (s)", form.interval, Modifier.weight(1f)) { v ->
                    viewModel.updateMonitorForm { it.copy(interval = v) }
                }
                NumberField("Timeout (s)", form.timeout, Modifier.weight(1f)) { v ->
                    viewModel.updateMonitorForm { it.copy(timeout = v) }
                }
                NumberField("Retries", form.retries, Modifier.weight(1f)) { v ->
                    viewModel.updateMonitorForm { it.copy(retries = v) }
                }
            }
            Text(
                "Cloudflare sends a GET to this path on each origin. Attach the monitor to a pool when you create the pool.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::saveMonitor)
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun PoolsTab(uiState: LoadBalancingUiState, viewModel: LoadBalancingViewModel) {
    StateContent(state = uiState.pools, onRetry = viewModel::refreshPools) { pools ->
        if (pools.isEmpty()) {
            EmptyState("No pools yet - create one to group origins behind a load balancer")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(pools, key = { it.id }) { pool ->
                    PoolRow(
                        pool,
                        monitorLabel = poolMonitorLabel(pool, (uiState.monitors as? UiState.Data)?.value.orEmpty()),
                        isDeleting = uiState.deletingPoolId == pool.id,
                        onDelete = { viewModel.deletePool(pool) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun PoolRow(pool: LoadBalancerPool, monitorLabel: String, isDeleting: Boolean, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(pool.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${pool.origins.size} origin${if (pool.origins.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                monitorLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isDeleting) {
            CircularProgressIndicator(Modifier.padding(4.dp))
        } else {
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete pool", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete pool?") },
            text = { Text("Any load balancer still referencing \"${pool.name}\" will break. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadBalancersTab(uiState: LoadBalancingUiState, viewModel: LoadBalancingViewModel) {
    Column {
        if (uiState.zones.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            val selectedZoneName = uiState.zones.firstOrNull { it.id == uiState.selectedZoneId }?.name ?: ""
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(16.dp, 8.dp)) {
                OutlinedTextField(
                    value = selectedZoneName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Zone") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    uiState.zones.forEach { zone ->
                        DropdownMenuItem(text = { Text(zone.name) }, onClick = { viewModel.selectZone(zone.id); expanded = false })
                    }
                }
            }
        }
        StateContent(state = uiState.loadBalancers, onRetry = viewModel::refreshLoadBalancers) { balancers ->
            if (balancers.isEmpty()) {
                EmptyState("No load balancers yet for this zone")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(balancers, key = { it.id }) { lb ->
                        LoadBalancerRow(lb, isDeleting = uiState.deletingLbId == lb.id, onDelete = { viewModel.deleteLoadBalancer(lb) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadBalancerRow(lb: LoadBalancer, isDeleting: Boolean, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(lb.name, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
            Text(
                (if (lb.proxied) "Proxied" else "DNS only") + if (!lb.enabled) " · Disabled" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isDeleting) {
            CircularProgressIndicator(Modifier.padding(4.dp))
        } else {
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete load balancer", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete load balancer?") },
            text = { Text("Traffic to ${lb.name} will stop being load balanced immediately.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoolFormSheet(
    form: PoolFormState,
    monitors: List<LoadBalancerMonitor>,
    onDismiss: () -> Unit,
    viewModel: LoadBalancingViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Add pool", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updatePoolForm { it.copy(name = v) } },
                label = { Text("Pool name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Origins", style = MaterialTheme.typography.labelLarge)
            form.origins.forEachIndexed { index, origin ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = origin.address,
                        onValueChange = { v -> viewModel.updateOrigin(index) { it.copy(address = v) } },
                        label = { Text("Address") },
                        placeholder = { Text("203.0.113.10") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (form.origins.size > 1) {
                        IconButton(onClick = { viewModel.removeOriginRow(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove origin")
                        }
                    }
                }
            }
            TextButton(onClick = viewModel::addOriginRow) { Text("Add another origin") }
            MonitorPicker(
                monitors = monitors,
                selectedId = form.monitorId,
                onSelect = { id -> viewModel.updatePoolForm { it.copy(monitorId = id) } }
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = viewModel::savePool, enabled = !form.isSaving) {
                    if (form.isSaving) CircularProgressIndicator(Modifier.padding(end = 6.dp))
                    Text("Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LbFormSheet(form: LbFormState, pools: List<LoadBalancerPool>, onDismiss: () -> Unit, viewModel: LoadBalancingViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Add load balancer", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.hostname,
                onValueChange = { v -> viewModel.updateLbForm { it.copy(hostname = v) } },
                label = { Text("Hostname") },
                placeholder = { Text("www.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            var expanded by remember { mutableStateOf(false) }
            val selectedPoolName = pools.firstOrNull { it.id == form.poolId }?.name ?: ""
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedPoolName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Pool") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    pools.forEach { pool ->
                        DropdownMenuItem(text = { Text(pool.name) }, onClick = { viewModel.updateLbForm { it.copy(poolId = pool.id) }; expanded = false })
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = form.proxied, onCheckedChange = { v -> viewModel.updateLbForm { it.copy(proxied = v) } })
                Text("Proxy through Cloudflare")
            }

            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = viewModel::saveLoadBalancer, enabled = !form.isSaving) {
                    if (form.isSaving) CircularProgressIndicator(Modifier.padding(end = 6.dp))
                    Text("Save")
                }
            }
        }
    }
}

/**
 * Picks the monitor a new pool health-checks with. "None" is a real choice - Cloudflare allows
 * a pool without one - so it is offered explicitly rather than left as an empty field, with
 * what that costs spelled out.
 */
@Composable
private fun MonitorPicker(
    monitors: List<LoadBalancerMonitor>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    Text("Health monitor", style = MaterialTheme.typography.labelLarge)
    if (monitors.isEmpty()) {
        Text(
            "No monitors on this account yet. Create one on the Monitors tab first if you want automatic failover.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    MonitorChoice(
        label = "None - no automatic failover",
        selected = selectedId == null,
        onClick = { onSelect(null) }
    )
    monitors.forEach { monitor ->
        MonitorChoice(
            label = monitor.description?.takeIf { it.isNotBlank() } ?: monitorSummary(monitor),
            selected = selectedId == monitor.id,
            onClick = { onSelect(monitor.id) }
        )
    }
}

@Composable
private fun MonitorChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
