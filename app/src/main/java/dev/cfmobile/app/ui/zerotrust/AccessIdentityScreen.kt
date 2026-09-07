package dev.cfmobile.app.ui.zerotrust

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.AccessIdentityProvider
import dev.cfmobile.app.data.remote.dto.AccessServiceToken
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.CopyIconButton
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions

@Composable
fun AccessIdentityScreen(viewModel: AccessIdentityViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val tabs: @Composable () -> Unit = {
        PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal) {
            IdentityTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.tab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(tab.label) }
                )
            }
        }
    }

    when (uiState.tab) {
        IdentityTab.PROVIDERS -> CfListScreen(
            title = "Access Identity",
            onBack = onBack,
            state = uiState.providers,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No login methods configured",
            key = { it.id },
            onCreate = viewModel::openForm,
            createContentDescription = "Add one-time PIN login",
            header = tabs
        ) { provider ->
            ProviderRow(provider, uiState.deletingId == provider.id) { viewModel.deleteProvider(provider) }
        }

        IdentityTab.SERVICE_TOKENS -> CfListScreen(
            title = "Access Identity",
            onBack = onBack,
            state = uiState.tokens,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No service tokens yet",
            key = { it.id },
            onCreate = viewModel::openForm,
            createContentDescription = "Create service token",
            header = tabs
        ) { token ->
            TokenRow(token, uiState.deletingId == token.id) { viewModel.deleteToken(token) }
        }
    }

    uiState.form?.let { form ->
        NameSheet(form, uiState.tab, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }

    uiState.newToken?.let { token ->
        NewTokenDialog(token, onDismiss = viewModel::dismissNewToken)
    }
}

@Composable
private fun ProviderRow(provider: AccessIdentityProvider, isDeleting: Boolean, onDelete: () -> Unit) {
    DeletableListRow(
        icon = Icons.AutoMirrored.Filled.Login,
        title = provider.name.ifBlank { identityProviderLabel(provider) },
        subtitle = identityProviderLabel(provider),
        detail = if (isOneTimePin(provider)) {
            null
        } else {
            // Editing an external provider means handling its client credentials, which this
            // app deliberately never asks for or displays.
            "Configured in the Cloudflare dashboard - not editable here"
        },
        isDeleting = isDeleting,
        deleteContentDescription = "Delete login method",
        confirmTitle = "Delete login method?",
        confirmText = "Users who sign in through \"${provider.name}\" will lose access to every Access application that relies on it.",
        onDelete = onDelete
    )
}

@Composable
private fun TokenRow(token: AccessServiceToken, isDeleting: Boolean, onDelete: () -> Unit) {
    DeletableListRow(
        icon = Icons.Filled.Key,
        title = token.name,
        subtitle = token.clientId,
        detail = token.expiresAt?.let { "Expires $it" },
        monospaceTitle = false,
        isDeleting = isDeleting,
        deleteContentDescription = "Delete service token",
        confirmTitle = "Delete service token?",
        confirmText = "Anything authenticating with \"${token.name}\" will stop working immediately. This can't be undone.",
        onDelete = onDelete,
        trailing = { token.clientId?.let { CopyIconButton(value = it, label = "client ID") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameSheet(
    form: NameFormState,
    tab: IdentityTab,
    onDismiss: () -> Unit,
    viewModel: AccessIdentityViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (tab == IdentityTab.PROVIDERS) "Add one-time PIN login" else "Create service token",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Name") },
                placeholder = { Text(if (tab == IdentityTab.PROVIDERS) "One-time PIN" else "ci-deploy-bot") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (tab == IdentityTab.PROVIDERS) {
                    "One-time PIN emails a code to the user, so it needs no configuration. Google, Okta, SAML and the rest carry client credentials - add those in the Cloudflare dashboard."
                } else {
                    "The token's secret is shown once, right after it's created. Cloudflare never sends it again."
                },
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

/** The one and only time the client secret is visible. It is held in memory for this dialog
 *  and never written to storage or logs. */
@Composable
private fun NewTokenDialog(token: NewServiceToken, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy the secret now") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "\"${token.name}\" is created. Cloudflare shows the client secret only once - if you close this without copying it, you'll have to create a new token.",
                    style = MaterialTheme.typography.bodySmall
                )
                SecretRow("Client ID", token.clientId)
                SecretRow("Client secret", token.clientSecret)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun SecretRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        CopyIconButton(value = value, label = label)
    }
}
