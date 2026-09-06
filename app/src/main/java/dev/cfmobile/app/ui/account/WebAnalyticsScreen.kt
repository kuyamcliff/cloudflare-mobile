package dev.cfmobile.app.ui.account

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.RumSite
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.CopyIconButton
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.rules.LabelledSwitch

@Composable
fun WebAnalyticsScreen(viewModel: WebAnalyticsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Web Analytics",
        onBack = onBack,
        state = uiState.sites,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Web Analytics sites yet",
        key = { it.siteTag },
        onCreate = viewModel::openForm,
        createContentDescription = "Add site",
        searchPlaceholder = "Search sites",
        searchMatches = { site, query -> rumSiteLabel(site).contains(query, ignoreCase = true) }
    ) { site ->
        DeletableListRow(
            icon = Icons.Filled.Insights,
            title = rumSiteLabel(site),
            subtitle = rumSiteSubtitle(site),
            detail = site.siteTag,
            isDeleting = uiState.deletingTag == site.siteTag,
            deleteContentDescription = "Delete site",
            confirmTitle = "Delete this site?",
            confirmText = "\"${rumSiteLabel(site)}\" stops collecting analytics and its history is removed. This can't be undone.",
            onDelete = { viewModel.delete(site) },
            onClick = { viewModel.showSnippet(site) }
        )
    }

    uiState.form?.let { form ->
        AddSiteSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }

    uiState.snippetSite?.let { site ->
        SnippetDialog(site, onDismiss = viewModel::dismissSnippet)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSiteSheet(
    form: WebAnalyticsFormState,
    onDismiss: () -> Unit,
    viewModel: WebAnalyticsViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add site", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.host,
                onValueChange = { v -> viewModel.updateForm { it.copy(host = v) } },
                label = { Text("Hostname") },
                placeholder = { Text("example.com") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            LabelledSwitch("Install automatically", form.autoInstall) { v ->
                viewModel.updateForm { it.copy(autoInstall = v) }
            }
            Text(
                if (form.autoInstall) {
                    "Cloudflare injects the beacon for a proxied zone, so nothing has to change on the site itself."
                } else {
                    "You'll get a JavaScript snippet to paste into the site's pages."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save, saveLabel = "Add")
        }
    }
}

@Composable
private fun SnippetDialog(site: RumSite, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rumSiteLabel(site)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Site tag: ${site.siteTag}", style = MaterialTheme.typography.bodySmall)
                val snippet = site.snippet
                if (snippet.isNullOrBlank()) {
                    Text(
                        "Cloudflare didn't return a snippet for this site - an auto-installed site doesn't need one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Text(snippet, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = {
            site.snippet?.takeIf { it.isNotBlank() }?.let {
                CopyIconButton(value = it, label = "snippet")
            } ?: TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
