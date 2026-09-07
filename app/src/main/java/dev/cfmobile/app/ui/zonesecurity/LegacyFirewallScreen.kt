package dev.cfmobile.app.ui.zonesecurity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SmartToy
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
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.rules.LabelledSwitch

@Composable
fun LegacyFirewallScreen(zoneName: String, viewModel: LegacyFirewallViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val header: @Composable () -> Unit = {
        PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal) {
            LegacyFirewallTab.entries.forEach { tab ->
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
        LegacyFirewallTab.LOCKDOWNS -> CfListScreen(
            title = "Lockdown",
            subtitle = zoneName,
            onBack = onBack,
            state = uiState.lockdowns,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No lockdowns on this zone",
            key = { it.id },
            onCreate = viewModel::openLockdownForm,
            createContentDescription = "Add lockdown",
            header = header
        ) { lockdown ->
            DeletableListRow(
                icon = Icons.Filled.Lock,
                title = lockdown.description?.takeIf { it.isNotBlank() } ?: lockdown.urls.firstOrNull() ?: lockdown.id,
                subtitle = lockdownSummary(lockdown),
                detail = if (lockdown.paused) "Paused" else lockdown.urls.joinToString(", "),
                isDeleting = uiState.deletingId == lockdown.id,
                deleteContentDescription = "Delete lockdown",
                confirmTitle = "Delete lockdown?",
                confirmText = "These URLs will be reachable from anywhere again. This can't be undone.",
                onDelete = { viewModel.deleteLockdown(lockdown) },
                onClick = { viewModel.openLockdownForm(lockdown) }
            )
        }

        LegacyFirewallTab.USER_AGENTS -> CfListScreen(
            title = "User Agents",
            subtitle = zoneName,
            onBack = onBack,
            state = uiState.userAgentRules,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No user agent rules on this zone",
            key = { it.id },
            onCreate = viewModel::openUserAgentForm,
            createContentDescription = "Add user agent rule",
            header = header
        ) { rule ->
            DeletableListRow(
                icon = Icons.Filled.SmartToy,
                title = rule.description?.takeIf { it.isNotBlank() } ?: rule.configuration.value,
                subtitle = uaModeLabel(rule.mode),
                detail = if (rule.paused) "Paused" else rule.configuration.value,
                isDeleting = uiState.deletingId == rule.id,
                deleteContentDescription = "Delete rule",
                confirmTitle = "Delete rule?",
                confirmText = "This user agent will stop being challenged or blocked. This can't be undone.",
                onDelete = { viewModel.deleteUserAgentRule(rule) },
                onClick = { viewModel.openUserAgentForm(rule) }
            )
        }
    }

    uiState.lockdownForm?.let { form ->
        LockdownSheet(form, onDismiss = viewModel::closeLockdownForm, viewModel = viewModel)
    }
    uiState.userAgentForm?.let { form ->
        UserAgentSheet(form, onDismiss = viewModel::closeUserAgentForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockdownSheet(
    form: LockdownFormState,
    onDismiss: () -> Unit,
    viewModel: LegacyFirewallViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (form.editingId != null) "Edit lockdown" else "Add lockdown",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updateLockdownForm { it.copy(description = v) } },
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.urls,
                onValueChange = { v -> viewModel.updateLockdownForm { it.copy(urls = v) } },
                label = { Text("URL patterns") },
                placeholder = { Text("example.com/admin*") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp)
            )
            OutlinedTextField(
                value = form.sources,
                onValueChange = { v -> viewModel.updateLockdownForm { it.copy(sources = v) } },
                label = { Text("Allowed sources") },
                placeholder = { Text("203.0.113.10\n198.51.100.0/24\nGB") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp)
            )
            Text(
                "One per line. An address, a CIDR range, or a two-letter country code - everything else is locked out of those URLs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LabelledSwitch("Paused", form.paused) { v -> viewModel.updateLockdownForm { it.copy(paused = v) } }
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::saveLockdown)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserAgentSheet(
    form: UserAgentFormState,
    onDismiss: () -> Unit,
    viewModel: LegacyFirewallViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (form.editingId != null) "Edit rule" else "Add rule",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updateUserAgentForm { it.copy(description = v) } },
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.userAgent,
                onValueChange = { v -> viewModel.updateUserAgentForm { it.copy(userAgent = v) } },
                label = { Text("User agent") },
                placeholder = { Text("BadBot/1.0") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            OptionRow(
                title = "Action",
                currentValue = form.mode,
                options = UA_RULE_MODES,
                isSaving = false,
                onSelect = { v -> viewModel.updateUserAgentForm { it.copy(mode = v) } }
            )
            Text(
                "The header has to match exactly - this rule has no wildcards. For anything looser, write a WAF custom rule.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LabelledSwitch("Paused", form.paused) { v -> viewModel.updateUserAgentForm { it.copy(paused = v) } }
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::saveUserAgentRule)
        }
    }
}
