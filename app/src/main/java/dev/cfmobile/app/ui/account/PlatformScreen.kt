package dev.cfmobile.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Waves
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.CopyIconButton
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions
import dev.cfmobile.app.ui.common.ToggleRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformScreen(viewModel: PlatformViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val header: @Composable () -> Unit = {
        PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal) {
            PlatformTab.entries.forEach { tab ->
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
        PlatformTab.AI_GATEWAY -> CfListScreen(
            title = "Platform",
            onBack = onBack,
            state = uiState.gateways,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No AI gateways yet",
            key = { it.id },
            onCreate = viewModel::openForm,
            createContentDescription = "Create gateway",
            header = header
        ) { gateway ->
            DeletableListRow(
                icon = Icons.Filled.Hub,
                title = gateway.id,
                subtitle = gatewaySummary(gateway),
                isDeleting = uiState.deletingId == gateway.id,
                deleteContentDescription = "Delete gateway",
                confirmTitle = "Delete this gateway?",
                confirmText = "Anything calling \"${gateway.id}\" starts failing immediately, and its cached responses and logs go with it.",
                onDelete = { viewModel.deleteGateway(gateway) }
            )
        }

        PlatformTab.CALLS -> CfListScreen(
            title = "Platform",
            onBack = onBack,
            state = uiState.callsApps,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No Calls applications yet",
            key = { it.uid },
            onCreate = viewModel::openForm,
            createContentDescription = "Create application",
            header = header
        ) { app ->
            DeletableListRow(
                icon = Icons.Filled.Videocam,
                title = app.name ?: app.uid,
                subtitle = app.uid,
                isDeleting = uiState.deletingId == app.uid,
                deleteContentDescription = "Delete application",
                confirmTitle = "Delete this application?",
                confirmText = "Clients using this app's credentials lose access to the SFU and TURN service straight away.",
                onDelete = { viewModel.deleteCallsApp(app) }
            )
        }

        PlatformTab.PIPELINES -> CfListScreen(
            title = "Platform",
            onBack = onBack,
            state = uiState.pipelines,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No pipelines yet",
            key = { it.id },
            header = {
                header()
                Text(
                    // Creating one needs a source, a destination bucket, and that bucket's
                    // access key pair - a form that belongs on a desktop.
                    "Pipelines batch records into R2. Creating one needs a source and R2 credentials, so it's done from the dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
        ) { pipeline ->
            DeletableListRow(
                icon = Icons.Filled.Waves,
                title = pipeline.name,
                subtitle = pipeline.endpoint,
                detail = pipeline.version?.let { "Version $it" },
                isDeleting = uiState.deletingId == pipeline.id,
                deleteContentDescription = "Delete pipeline",
                confirmTitle = "Delete this pipeline?",
                confirmText = "Records sent to its endpoint stop being accepted. Data already written to R2 stays where it is.",
                onDelete = { viewModel.deletePipeline(pipeline) }
            )
        }

        PlatformTab.SECRETS -> CfListScreen(
            title = "Platform",
            onBack = onBack,
            state = uiState.stores,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No secret stores yet",
            key = { it.id },
            onCreate = viewModel::openForm,
            createContentDescription = "Create store",
            header = {
                header()
                Text(
                    "Stores hold account-wide secrets a Worker can bind to. Values are never readable - not here, not in the dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
        ) { store ->
            DeletableListRow(
                icon = Icons.Filled.Key,
                title = store.name,
                subtitle = store.id,
                isDeleting = uiState.deletingId == store.id,
                deleteContentDescription = "Delete store",
                confirmTitle = "Delete this store?",
                confirmText = "Every secret in \"${store.name}\" is destroyed, and Workers bound to them start failing. The values cannot be recovered.",
                onDelete = { viewModel.deleteStore(store) }
            )
        }
    }

    uiState.gatewayForm?.let { form ->
        GatewaySheet(form, onDismiss = viewModel::closeForms, viewModel = viewModel)
    }
    uiState.nameForm?.let { form ->
        NameSheet(form, tab = uiState.tab, onDismiss = viewModel::closeForms, viewModel = viewModel)
    }
    uiState.newCallsApp?.let { app ->
        NewCallsAppDialog(app, onDismiss = viewModel::dismissNewCallsApp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GatewaySheet(
    form: AiGatewayFormState,
    onDismiss: () -> Unit,
    viewModel: PlatformViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create AI gateway", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.id,
                onValueChange = { v -> viewModel.updateGatewayForm { it.copy(id = v) } },
                label = { Text("Gateway name") },
                placeholder = { Text("my-gateway") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.cacheTtl,
                onValueChange = { v -> viewModel.updateGatewayForm { it.copy(cacheTtl = v) } },
                label = { Text("Cache TTL (seconds)") },
                supportingText = { Text("0 caches nothing") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.rateLimitLimit,
                    onValueChange = { v -> viewModel.updateGatewayForm { it.copy(rateLimitLimit = v) } },
                    label = { Text("Rate limit") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = form.rateLimitInterval,
                    onValueChange = { v -> viewModel.updateGatewayForm { it.copy(rateLimitInterval = v) } },
                    label = { Text("Per (seconds)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            ToggleRow(
                title = "Collect logs",
                subtitle = "Record each request through the gateway",
                checked = form.collectLogs,
                isSaving = false,
                onToggle = { v -> viewModel.updateGatewayForm { it.copy(collectLogs = v) } }
            )
            Text(
                "The name becomes part of the gateway's URL and can't be changed afterwards. Provider keys are sent by the caller, not stored here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::saveGateway, saveLabel = "Create")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameSheet(
    form: PlatformNameFormState,
    tab: PlatformTab,
    onDismiss: () -> Unit,
    viewModel: PlatformViewModel
) {
    val isCalls = tab == PlatformTab.CALLS
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (isCalls) "Create Calls application" else "Create secret store",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateNameForm { it.copy(name = v) } },
                label = { Text("Name") },
                placeholder = { Text(if (isCalls) "video-room" else "production") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (isCalls) {
                    "The app secret is shown once, right after it's created. Cloudflare never sends it again."
                } else {
                    "An empty store to start with. Adding secrets to it is done from the dashboard or Wrangler - a value has to be typed somewhere it won't be logged."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::saveName, saveLabel = "Create")
        }
    }
}

/** The one and only time a Calls app secret is visible. Held in memory for this dialog and
 *  never written to storage or logs. */
@Composable
private fun NewCallsAppDialog(app: NewCallsApp, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy the secret now") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "\"${app.name}\" is created. Cloudflare shows the app secret only once - if you close this without copying it, you'll have to create a new application.",
                    style = MaterialTheme.typography.bodySmall
                )
                SecretRow("App ID", app.appId)
                SecretRow("App secret", app.secret)
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
