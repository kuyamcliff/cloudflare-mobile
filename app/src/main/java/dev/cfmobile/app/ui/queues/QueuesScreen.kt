package dev.cfmobile.app.ui.queues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.CfQueue
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.UiState
import dev.cfmobile.app.data.remote.dto.QueueConsumer
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions

@Composable
fun QueuesScreen(viewModel: QueuesViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CfListScreen(
        title = "Queues",
        onBack = onBack,
        state = uiState.queues,
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        emptyMessage = "No queues yet",
        key = { it.queueId },
        onCreate = viewModel::openForm,
        createContentDescription = "Create queue",
        searchPlaceholder = "Search queues",
        searchMatches = { queue, query -> queue.queueName.contains(query, ignoreCase = true) }
    ) { queue ->
        QueueRow(queue, isDeleting = uiState.deletingId == queue.queueId, onDelete = { viewModel.delete(queue) })
    }

    uiState.consumersFor?.let { queue ->
        ConsumersSheet(
            queueName = queue.queueName,
            consumers = uiState.consumers,
            detaching = uiState.detachingConsumer,
            error = uiState.consumerError,
            onDetach = viewModel::detachConsumer,
            onDismiss = viewModel::closeConsumers
        )
    }

    uiState.form?.let { form ->
        CreateQueueSheet(form, onDismiss = viewModel::closeForm, viewModel = viewModel)
    }
}

@Composable
private fun QueueRow(queue: CfQueue, isDeleting: Boolean, onDelete: () -> Unit) {
    val counts = listOfNotNull(
        queue.producersCount?.let { "$it producer${if (it == 1) "" else "s"}" },
        queue.consumersCount?.let { "$it consumer${if (it == 1) "" else "s"}" }
    ).joinToString(" · ").ifBlank { null }

    DeletableListRow(
        icon = Icons.Filled.Queue,
        title = queue.queueName,
        monospaceTitle = true,
        subtitle = counts,
        detail = queue.createdOn?.let { "Created $it" },
        isDeleting = isDeleting,
        deleteContentDescription = "Delete queue",
        confirmTitle = "Delete queue?",
        confirmText = "\"${queue.queueName}\" and any messages still in it will be permanently deleted. This can't be undone.",
        onDelete = onDelete
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateQueueSheet(form: QueueFormState, onDismiss: () -> Unit, viewModel: QueuesViewModel) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Create queue", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                label = { Text("Queue name") },
                placeholder = { Text("my-queue") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::save)
        }
    }
}

/**
 * A queue's consumers, read-mostly. Attaching a Worker to a queue is part of that Worker's own
 * configuration, which this app can't write, so the sheet lists them and detaches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsumersSheet(
    queueName: String,
    consumers: UiState<List<QueueConsumer>>?,
    detaching: String?,
    error: String?,
    onDetach: (QueueConsumer) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(20.dp).heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(queueName, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            Text(
                "Consumers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            when (consumers) {
                null, is UiState.Loading -> CircularProgressIndicator(Modifier.padding(8.dp))
                is UiState.Error -> Text(
                    consumers.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                is UiState.Data -> if (consumers.value.isEmpty()) {
                    Text(
                        "Nothing is consuming this queue - messages will pile up until something does.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    consumers.value.forEach { consumer ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    consumer.scriptName ?: consumer.consumerId.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    consumerSummary(consumer),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (detaching == consumer.consumerId) {
                                CircularProgressIndicator(Modifier.padding(4.dp))
                            } else {
                                TextButton(onClick = { onDetach(consumer) }) { Text("Detach") }
                            }
                        }
                    }
                }
            }
        }
    }
}
