package dev.cfmobile.app.ui.turnstile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.CopyIconButton
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.OptionRow

@Composable
fun TurnstileScreen(viewModel: TurnstileViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Turnstile",
        onBack = onBack,
        state = uiState.widgets,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Turnstile widgets yet",
        key = { it.sitekey },
        onCreate = viewModel::openForm,
        createContentDescription = "Create widget",
        searchPlaceholder = "Search widgets",
        searchMatches = { widget, query ->
            widget.name.contains(query, ignoreCase = true) ||
                widget.domains.any { it.contains(query, ignoreCase = true) }
        }
    ) { widget ->
        DeletableListRow(
            icon = Icons.Filled.VerifiedUser,
            title = widget.name,
            subtitle = widget.domains.joinToString(", ").ifBlank { null },
            detail = listOfNotNull(widget.mode, widget.sitekey).joinToString(" · "),
            isDeleting = uiState.deletingSitekey == widget.sitekey,
            deleteContentDescription = "Delete widget",
            confirmTitle = "Delete widget?",
            confirmText = "\"${widget.name}\" will be permanently deleted, and any page still embedding its sitekey will stop validating. This can't be undone.",
            onDelete = { viewModel.delete(widget) },
            // Only the public sitekey is ever shown or copied - this app never reads the
            // widget's secret key.
            trailing = { CopyIconButton(value = widget.sitekey, label = "sitekey") }
        )
    }

    uiState.form?.let { form ->
        CreateWidgetSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateWidgetSheet(form: TurnstileFormState, onDismiss: () -> Unit, viewModel: TurnstileViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Create widget", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Widget name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.domains,
                onValueChange = { v -> viewModel.updateForm { it.copy(domains = v) } },
                label = { Text("Domains") },
                placeholder = { Text("example.com, app.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            OptionRow(
                title = "Mode",
                currentValue = form.mode.name,
                options = TurnstileMode.entries.map { it.name to it.label },
                isSaving = false,
                onSelect = { v -> viewModel.updateForm { it.copy(mode = TurnstileMode.valueOf(v)) } }
            )
            if (form.error != null) {
                Text(
                    form.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            FormActions(
                isSaving = form.isSaving,
                onCancel = onDismiss,
                onSave = viewModel::save,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}
