package dev.cfmobile.app.ui.pageshield

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.DeletableListRow
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
                        text = {
                            Text(
                                when (tab) {
                                    PageShieldTab.SCRIPTS -> "Scripts"
                                    PageShieldTab.CONNECTIONS -> "Connections"
                                    PageShieldTab.POLICIES -> "Policies"
                                }
                            )
                        }
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

                PageShieldTab.POLICIES -> RefreshableStateContent(
                    state = uiState.policies,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh
                ) { policies ->
                    Column {
                        uiState.policyError?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        TextButton(
                            onClick = viewModel::openPolicyForm,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) { Text("Add policy") }
                        if (policies.isEmpty()) {
                            EmptyState("No Page Shield policies yet. A policy allow-lists the scripts permitted on matching pages.")
                        } else {
                            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                                items(policies, key = { it.id }) { policy ->
                                    DeletableListRow(
                                        icon = Icons.Filled.Shield,
                                        title = policy.description?.takeIf { it.isNotBlank() } ?: pageShieldActionLabel(policy.action),
                                        subtitle = policy.expression,
                                        detail = listOfNotNull(
                                            pageShieldActionLabel(policy.action),
                                            if (policy.enabled) null else "Disabled"
                                        ).joinToString(" · "),
                                        isDeleting = uiState.deletingPolicyId == policy.id,
                                        deleteContentDescription = "Delete policy",
                                        confirmTitle = "Delete policy?",
                                        confirmText = "Scripts this policy allowed will no longer be allow-listed on matching pages.",
                                        onDelete = { viewModel.deletePolicy(policy) },
                                        onClick = { viewModel.openPolicyForm(policy) }
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

    uiState.policyForm?.let { form ->
        PolicySheet(form, onDismiss = viewModel::closePolicyForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolicySheet(
    form: PageShieldPolicyForm,
    onDismiss: () -> Unit,
    viewModel: PageShieldViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (form.editingId != null) "Edit policy" else "Add policy",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updatePolicyForm { it.copy(description = v) } },
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.expression,
                onValueChange = { v -> viewModel.updatePolicyForm { it.copy(expression = v) } },
                label = { Text("Pages this applies to") },
                placeholder = { Text("http.host eq \"example.com\"") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.value,
                onValueChange = { v -> viewModel.updatePolicyForm { it.copy(value = v) } },
                label = { Text("Scripts allowed") },
                placeholder = { Text("http.request.uri.host in {\"cdn.example.com\"}") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            OptionRow(
                title = "Action",
                currentValue = form.action,
                options = PAGE_SHIELD_ACTIONS,
                isSaving = false,
                onSelect = { v -> viewModel.updatePolicyForm { it.copy(action = v) } }
            )
            ToggleRow(
                title = "Enabled",
                checked = form.enabled,
                isSaving = false,
                onToggle = { v -> viewModel.updatePolicyForm { it.copy(enabled = v) } }
            )
            Text(
                "Both fields are Cloudflare filter expressions. A policy set to Allow blocks every script the second expression doesn't match, so test with Log only first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::savePolicy)
        }
    }
}
