package dev.cfmobile.app.ui.zoneproducts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun CustomPagesScreen(zoneName: String, viewModel: CustomPagesViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Error Pages",
        subtitle = zoneName,
        onBack = onBack,
        state = uiState.pages,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "Cloudflare didn't return any custom page types for this zone",
        key = { it.id },
        searchPlaceholder = "Search pages",
        searchMatches = { page, query -> customPageLabel(page).contains(query, ignoreCase = true) },
        header = {
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 4.dp)
                )
            }
        }
    ) { page ->
        // Only a customized page has anything to take away; a default one just gets a tap to
        // customize it.
        if (isCustomized(page)) {
            DeletableListRow(
                icon = Icons.AutoMirrored.Filled.Article,
                title = customPageLabel(page),
                subtitle = customPageStatus(page),
                detail = page.modifiedOn?.let { "Modified $it" },
                isDeleting = uiState.revertingId == page.id,
                deleteContentDescription = "Revert to Cloudflare's page",
                confirmTitle = "Use Cloudflare's page?",
                confirmText = "\"${customPageLabel(page)}\" goes back to Cloudflare's default. Your hosted page isn't deleted - you can point at it again later.",
                onDelete = { viewModel.revert(page) },
                onClick = { viewModel.openForm(page) }
            )
        } else {
            ReadOnlyListRow(
                icon = Icons.AutoMirrored.Filled.Article,
                title = customPageLabel(page),
                subtitle = customPageStatus(page),
                onClick = { viewModel.openForm(page) }
            )
        }
    }

    uiState.form?.let { form ->
        CustomPageSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPageSheet(
    form: CustomPageFormState,
    onDismiss: () -> Unit,
    viewModel: CustomPagesViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(customPageLabel(form.page), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.url,
                onValueChange = { v -> viewModel.updateForm { it.copy(url = v) } },
                label = { Text("Page URL") },
                placeholder = { Text("https://example.com/error.html") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            requiredTokensLabel(form.page)?.let { tokens ->
                Text(
                    "The page you host must contain: $tokens - Cloudflare substitutes those at serve time and rejects a page without them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Cloudflare fetches and caches the HTML from this URL. Editing the page later means updating it at that URL, not here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save)
        }
    }
}
