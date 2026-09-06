package dev.cfmobile.app.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.PagesDeployment
import dev.cfmobile.app.data.remote.dto.PagesProject
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesScreen(viewModel: PagesViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        StateContent(state = uiState.projects, onRetry = viewModel::refresh) { projects ->
            if (projects.isEmpty()) {
                EmptyState("No Pages projects yet", Modifier.padding(padding))
            } else {
                LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(projects, key = { it.name }) { project ->
                        PagesProjectRow(project, onClick = { viewModel.selectProject(project) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }

    uiState.selectedProjectName?.let { projectName ->
        DeploymentsSheet(
            projectName = projectName,
            deployments = uiState.deployments,
            isDeploying = uiState.deployingProject == projectName,
            deployError = uiState.deployError,
            deployMessage = uiState.deployMessage,
            onDeploy = viewModel::deploy,
            onRetry = viewModel::retry,
            onDismiss = viewModel::closeDeployments
        )
    }
}

@Composable
private fun PagesProjectRow(project: PagesProject, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.bodyLarge)
            val subtitle = project.subdomain ?: project.domains?.firstOrNull()
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeploymentsSheet(
    projectName: String,
    deployments: UiState<List<PagesDeployment>>?,
    isDeploying: Boolean,
    deployError: String?,
    deployMessage: String?,
    onDeploy: () -> Unit,
    onRetry: (PagesDeployment) -> Unit,
    onDismiss: () -> Unit
) {
    var confirmDeploy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(projectName, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            Text("Deployment history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { confirmDeploy = true }, enabled = !isDeploying) {
                if (isDeploying) CircularProgressIndicator(Modifier.padding(end = 6.dp))
                Text(if (isDeploying) "Deploying…" else "Deploy production branch")
            }
            deployError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (deployError == null) {
                deployMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
            when (deployments) {
                is UiState.Loading, null -> Box(Modifier.fillMaxWidth().padding(24.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                }
                is UiState.Error -> Column {
                    Text(deployments.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                is UiState.Data -> {
                    if (deployments.value.isEmpty()) {
                        Text("No deployments yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(deployments.value, key = { it.id }) { deployment ->
                                DeploymentRow(deployment, isDeploying = isDeploying, onRetry = { onRetry(deployment) })
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDeploy) {
        ConfirmDeployDialog(
            projectName = projectName,
            onConfirm = onDeploy,
            onDismiss = { confirmDeploy = false }
        )
    }
}

@Composable
private fun ConfirmDeployDialog(projectName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deploy now?") },
        text = { Text("This rebuilds \"$projectName\" from its production branch and publishes the result live.") },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text("Deploy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DeploymentRow(deployment: PagesDeployment, isDeploying: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val branch = deployment.deploymentTrigger?.metadata?.branch
        Text(
            listOfNotNull(deployment.environment, branch).joinToString(" · ").ifBlank { deployment.id },
            style = MaterialTheme.typography.bodyMedium
        )
        deployment.deploymentTrigger?.metadata?.commitMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        val statusLine = listOfNotNull(deployment.latestStage?.status, deployment.createdOn).joinToString(" · ")
        if (statusLine.isNotBlank()) {
            Text(statusLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Only a deployment that actually failed can be retried; offering it on a healthy one
        // would just queue a pointless build.
        if (isRetryableDeployment(deployment)) {
            TextButton(onClick = onRetry, enabled = !isDeploying) { Text("Retry this deployment") }
        }
    }
}

/** Cloudflare reports the last build stage's status; "failure" and "canceled" are the two
 *  states a retry is meant for. */
fun isRetryableDeployment(deployment: PagesDeployment): Boolean =
    deployment.latestStage?.status?.lowercase() in setOf("failure", "failed", "canceled", "cancelled")
