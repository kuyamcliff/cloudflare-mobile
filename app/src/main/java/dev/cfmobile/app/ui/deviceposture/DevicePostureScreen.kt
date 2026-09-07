package dev.cfmobile.app.ui.deviceposture

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Devices
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
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.ReadOnlyListRow
import dev.cfmobile.app.ui.common.RefreshableStateContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePostureScreen(viewModel: DevicePostureViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices & Posture") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = DevicePostureTab.entries.indexOf(uiState.tab)) {
                DevicePostureTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(if (tab == DevicePostureTab.DEVICES) "Devices" else "Posture rules") }
                    )
                }
            }
            Text(
                "Read-only: revoking a device or editing a posture rule isn't done from here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            uiState.revokeError?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            when (uiState.tab) {
                DevicePostureTab.DEVICES -> RefreshableStateContent(
                    state = uiState.devices,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh
                ) { devices ->
                    if (devices.isEmpty()) {
                        EmptyState("No enrolled devices")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(devices, key = { it.id }) { device ->
                                DeletableListRow(
                                    icon = Icons.Filled.Devices,
                                    title = deviceLabel(device),
                                    subtitle = listOfNotNull(device.deviceType, device.version).joinToString(" · ").ifBlank { null },
                                    detail = device.lastSeen?.let { "Last seen $it" },
                                    isDeleting = uiState.revokingId == device.id,
                                    deleteContentDescription = "Revoke device",
                                    confirmTitle = "Revoke this device?",
                                    confirmText = "\"${deviceLabel(device)}\" loses access to everything behind Zero Trust until its user enrols again.",
                                    onDelete = { viewModel.revoke(device) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }

                DevicePostureTab.POSTURE -> RefreshableStateContent(
                    state = uiState.postureRules,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh
                ) { rules ->
                    if (rules.isEmpty()) {
                        EmptyState("No posture rules configured")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(rules, key = { it.id }) { rule ->
                                ReadOnlyListRow(
                                    icon = Icons.AutoMirrored.Filled.Rule,
                                    title = rule.name,
                                    subtitle = rule.type,
                                    detail = rule.description ?: rule.schedule?.let { "Every $it" }
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
