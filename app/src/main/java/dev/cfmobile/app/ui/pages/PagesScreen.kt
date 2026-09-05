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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    if (uiState.selectedProjectName != null) {
        DeploymentsSheet(
            projectName = uiState.selectedProjectName!!,
            deployments = uiState.deployments,
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
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(projectName, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            Text("Deployment history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                DeploymentRow(deployment)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeploymentRow(deployment: PagesDeployment) {
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
    }
}
