package dev.cfmobile.app.ui.emailrouting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.StatusPill
import dev.cfmobile.app.ui.common.ToggleRow
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.ui.common.ZoneScopedTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailRoutingScreen(zoneName: String, viewModel: EmailRoutingViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ZoneScopedTitle("Email Routing", zoneName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            val isRules = uiState.tab == EmailRoutingTab.RULES
            FloatingActionButton(onClick = { if (isRules) viewModel.openForm() else viewModel.openDestinationForm() }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (isRules) "Create routing rule" else "Add destination address"
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                uiState.isEnabled?.let { enabled ->
                    StatusPill(
                        if (enabled) "Enabled" else "Not enabled",
                        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Rules forward mail from an address on this domain to a verified destination. Adding a destination sends a verification email - Cloudflare won't deliver there until its owner clicks the link.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                uiState.error?.let { error ->
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal) {
                EmailRoutingTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) }
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            if (uiState.tab == EmailRoutingTab.DESTINATIONS) {
                DestinationsTab(uiState, viewModel)
                return@Column
            }
            CatchAllRow(uiState, viewModel)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            RefreshableStateContent(
                state = uiState.rules,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh
            ) { rules ->
                if (rules.isEmpty()) {
                    EmptyState("No routing rules yet")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(rules, key = { it.tag }) { rule ->
                            val title = rule.name?.takeIf { it.isNotBlank() } ?: rule.tag
                            DeletableListRow(
                                icon = Icons.Filled.Email,
                                title = title,
                                subtitle = ruleRouteLabel(rule),
                                detail = if (!rule.enabled) "Disabled" else null,
                                isDeleting = uiState.deletingTag == rule.tag,
                                deleteContentDescription = "Delete rule",
                                confirmTitle = "Delete routing rule?",
                                confirmText = "\"$title\" will be permanently deleted and mail matching it will stop being forwarded. This can't be undone.",
                                onDelete = { viewModel.delete(rule) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }

    uiState.form?.let { form ->
        CreateRuleSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
    uiState.destinationForm?.let { form ->
        AddDestinationSheet(form, onDismiss = viewModel::closeDestinationForm, viewModel = viewModel)
    }
}

@Composable
private fun DestinationsTab(uiState: EmailRoutingUiState, viewModel: EmailRoutingViewModel) {
    RefreshableStateContent(
        state = uiState.destinations,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh
    ) { destinations ->
        if (destinations.isEmpty()) {
            EmptyState("No destination addresses on this account yet")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(destinations, key = { it.tag }) { address ->
                    DeletableListRow(
                        icon = Icons.Filled.AlternateEmail,
                        title = address.email,
                        subtitle = destinationStatus(address),
                        isDeleting = uiState.deletingTag == address.tag,
                        deleteContentDescription = "Delete destination",
                        confirmTitle = "Delete destination?",
                        confirmText = "Any rule forwarding to \"${address.email}\" will stop delivering. This can't be undone.",
                        onDelete = { viewModel.deleteDestination(address) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
    }
}

/**
 * The catch-all decides what happens to mail no rule matched. Turning it on forwards to the
 * first verified destination, because a catch-all with nowhere to go silently drops mail - the
 * one outcome nobody means to choose.
 */
@Composable
private fun CatchAllRow(uiState: EmailRoutingUiState, viewModel: EmailRoutingViewModel) {
    val verified = (uiState.destinations as? UiState.Data)?.value
        ?.filter { !it.verified.isNullOrBlank() }
        .orEmpty()
    val enabled = uiState.catchAll?.enabled == true

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ToggleRow(
            title = "Catch-all",
            subtitle = catchAllSummary(uiState.catchAll),
            checked = enabled,
            isSaving = uiState.isSavingCatchAll,
            enabled = enabled || verified.isNotEmpty(),
            onToggle = { on ->
                viewModel.setCatchAll(on, if (on) listOfNotNull(verified.firstOrNull()?.email) else emptyList())
            }
        )
        if (!enabled && verified.isEmpty()) {
            Text(
                "Add and verify a destination address before turning the catch-all on, or unmatched mail would be dropped without going anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDestinationSheet(
    form: DestinationFormState,
    onDismiss: () -> Unit,
    viewModel: EmailRoutingViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add destination address", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.email,
                onValueChange = { v -> viewModel.updateDestinationForm { it.copy(email = v) } },
                label = { Text("Email address") },
                placeholder = { Text("me@gmail.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Cloudflare emails this address a verification link. Until someone clicks it, mail routed here isn't delivered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::saveDestination, saveLabel = "Add")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRuleSheet(form: EmailRoutingFormState, onDismiss: () -> Unit, viewModel: EmailRoutingViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Create routing rule", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Rule name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.fromAddress,
                onValueChange = { v -> viewModel.updateForm { it.copy(fromAddress = v) } },
                label = { Text("Custom address on this domain") },
                placeholder = { Text("hello@example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.toAddress,
                onValueChange = { v -> viewModel.updateForm { it.copy(toAddress = v) } },
                label = { Text("Forward to (verified destination)") },
                placeholder = { Text("me@gmail.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save)
        }
    }
}
