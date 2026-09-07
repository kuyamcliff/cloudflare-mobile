package dev.cfmobile.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Webhook
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val header: @Composable () -> Unit = {
        PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal) {
            NotificationsTab.entries.forEach { tab ->
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
        NotificationsTab.POLICIES -> CfListScreen(
            title = "Notifications",
            onBack = onBack,
            state = uiState.policies,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No notification policies on this account",
            key = { it.id },
            searchPlaceholder = "Search notifications",
            searchMatches = { policy, query ->
                policy.name.contains(query, ignoreCase = true) ||
                    alertTypeLabel(policy.alertType).contains(query, ignoreCase = true)
            },
            header = header
        ) { policy ->
            DeletableListRow(
                icon = Icons.Filled.NotificationsActive,
                title = policy.name.ifBlank { alertTypeLabel(policy.alertType) },
                subtitle = alertTypeLabel(policy.alertType),
                detail = mechanismSummary(policy),
                isDeleting = uiState.deletingId == policy.id,
                deleteContentDescription = "Delete notification",
                confirmTitle = "Delete notification?",
                confirmText = "\"${policy.name}\" will stop alerting anyone. Disabling it instead keeps the configuration.",
                onDelete = { viewModel.delete(policy) },
                trailing = {
                    if (uiState.busyId == policy.id) {
                        CircularProgressIndicator(Modifier.padding(4.dp))
                    } else {
                        Switch(
                            checked = policy.enabled,
                            onCheckedChange = { viewModel.setEnabled(policy, it) }
                        )
                    }
                }
            )
        }

        NotificationsTab.DESTINATIONS -> CfListScreen(
            title = "Notifications",
            onBack = onBack,
            state = uiState.webhooks,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No webhook destinations yet",
            key = { it.id },
            onCreate = viewModel::openWebhookForm,
            createContentDescription = "Add webhook",
            header = {
                header()
                Text(
                    // Email addresses live inside each policy, not in this list.
                    "Webhooks an alert policy can notify. Email recipients are set per policy, and PagerDuty is connected from the dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
        ) { webhook ->
            DeletableListRow(
                icon = Icons.Filled.Webhook,
                title = webhook.name ?: webhook.id,
                // The URL can carry a token in its path, so only the host is shown - the same
                // rule the Logpush screen follows for its destinations.
                subtitle = webhookHost(webhook.url),
                detail = webhookSummary(webhook),
                isDeleting = uiState.deletingId == webhook.id,
                deleteContentDescription = "Delete webhook",
                confirmTitle = "Delete this destination?",
                confirmText = "Policies pointing at it stop delivering there. The policies themselves are left alone.",
                onDelete = { viewModel.deleteWebhook(webhook) }
            )
        }

        NotificationsTab.HISTORY -> CfListScreen(
            title = "Notifications",
            onBack = onBack,
            state = uiState.history,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "Nothing sent in the retained window",
            key = { it.id },
            header = header
        ) { entry ->
            ReadOnlyListRow(
                icon = Icons.Filled.History,
                title = entry.name ?: alertTypeLabel(entry.alertType),
                subtitle = entry.description,
                detail = listOfNotNull(entry.sent, entry.mechanismType).joinToString(" · ").ifBlank { null }
            )
        }
    }

    uiState.webhookForm?.let { form ->
        WebhookSheet(form, onDismiss = viewModel::closeWebhookForm, viewModel = viewModel)
    }
}

/** Only the host of a webhook URL: the path can carry the delivery token. */
fun webhookHost(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val withoutScheme = url.substringAfter("://", url)
    return withoutScheme.substringBefore('/').ifBlank { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebhookSheet(
    form: WebhookFormState,
    onDismiss: () -> Unit,
    viewModel: NotificationsViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add webhook", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateWebhookForm { it.copy(name = v) } },
                label = { Text("Name") },
                placeholder = { Text("Ops channel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.url,
                onValueChange = { v -> viewModel.updateWebhookForm { it.copy(url = v) } },
                label = { Text("URL") },
                placeholder = { Text("https://hooks.example.com/alerts") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Cloudflare POSTs the alert to this URL. A Slack or Teams incoming-webhook URL works as-is. The destination is created here but still has to be attached to a policy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::saveWebhook, saveLabel = "Add")
        }
    }
}
