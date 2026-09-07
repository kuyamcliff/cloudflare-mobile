package dev.cfmobile.app.ui.workerroutes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
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

@Composable
fun WorkerRoutesScreen(zoneName: String, viewModel: WorkerRoutesViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Worker Routes",
        subtitle = zoneName,
        onBack = onBack,
        state = uiState.routes,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No Worker routes on this zone",
        key = { it.id },
        onCreate = viewModel::openCreateForm,
        createContentDescription = "Add route",
        searchPlaceholder = "Search routes",
        searchMatches = { route, query ->
            route.pattern.contains(query, ignoreCase = true) ||
                route.script.orEmpty().contains(query, ignoreCase = true)
        }
    ) { route ->
        DeletableListRow(
            icon = Icons.AutoMirrored.Filled.AltRoute,
            title = route.pattern,
            monospaceTitle = true,
            subtitle = routeScriptLabel(route),
            isDeleting = uiState.deletingId == route.id,
            deleteContentDescription = "Delete route",
            confirmTitle = "Delete route?",
            confirmText = "\"${route.pattern}\" will stop sending traffic to its Worker. This can't be undone.",
            onDelete = { viewModel.delete(route) },
            onClick = { viewModel.openEditForm(route) }
        )
    }

    uiState.form?.let { form ->
        RouteSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteSheet(form: WorkerRouteFormState, onDismiss: () -> Unit, viewModel: WorkerRoutesViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (form.isEditing) "Edit route" else "Add route", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.pattern,
                onValueChange = { v -> viewModel.updateForm { it.copy(pattern = v) } },
                label = { Text("Route pattern") },
                placeholder = { Text("example.com/api/*") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.script,
                onValueChange = { v -> viewModel.updateForm { it.copy(script = v) } },
                label = { Text("Worker script") },
                placeholder = { Text("my-worker") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Leave the script empty to make the route match without running a Worker - that's how you carve an exception out of a wider pattern.",
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
