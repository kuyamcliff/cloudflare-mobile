package dev.cfmobile.app.ui.queues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfQueue
import dev.cfmobile.app.data.remote.dto.QueueConsumer
import dev.cfmobile.app.data.repository.QueuesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val QUEUE_NAME_REGEX = Regex("^[a-zA-Z0-9][a-zA-Z0-9-_]{0,62}$")

data class QueueFormState(val name: String = "", val isSaving: Boolean = false, val error: String? = null)

data class QueuesUiState(
    val queues: UiState<List<CfQueue>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: QueueFormState? = null,
    val deletingId: String? = null,
    val consumersFor: CfQueue? = null,
    val consumers: UiState<List<QueueConsumer>>? = null,
    val detachingConsumer: String? = null,
    val consumerError: String? = null
)

/** "my-worker · batches of 10, up to 3 retries" - the settings that decide how a consumer is
 *  called, which is what someone opens this sheet to check. */
fun consumerSummary(consumer: QueueConsumer): String {
    val settings = consumer.settings
    return listOfNotNull(
        settings?.batchSize?.let { "batches of $it" },
        settings?.maxRetries?.let { "up to $it ${if (it == 1) "retry" else "retries"}" },
        settings?.maxConcurrency?.let { "concurrency $it" },
        consumer.deadLetterQueue?.takeIf { it.isNotBlank() }?.let { "dead letter: $it" }
    ).joinToString(" · ").ifBlank { consumer.type ?: "No settings reported" }
}

fun validateQueueName(name: String): String? = when {
    name.isBlank() -> "Queue name is required"
    !name.matches(QUEUE_NAME_REGEX) -> "Use up to 63 letters, numbers, hyphens, or underscores"
    else -> null
}

class QueuesViewModel(
    private val accountId: String,
    private val repository: QueuesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueuesUiState())
    val uiState: StateFlow<QueuesUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(queues = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listQueues(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(queues = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(queues = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = QueueFormState()) }
    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (QueueFormState) -> QueueFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateQueueName(form.name)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createQueue(accountId, form.name.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(queue: CfQueue) {
        _uiState.update { it.copy(deletingId = queue.queueId) }
        viewModelScope.launch {
            repository.deleteQueue(accountId, queue.queueId)
            _uiState.update { it.copy(deletingId = null) }
            load(isRefresh = true)
        }
    }

    /** Opens a queue's consumers. Adding one is part of a Worker's own configuration, so this
     *  sheet lists and detaches only. */
    fun openConsumers(queue: CfQueue) {
        _uiState.update { it.copy(consumersFor = queue, consumers = UiState.Loading, consumerError = null) }
        viewModelScope.launch {
            loadConsumers(queue)
        }
    }

    private suspend fun loadConsumers(queue: CfQueue) {
        when (val result = repository.listConsumers(accountId, queue.queueId)) {
            is ApiResult.Success -> _uiState.update { state ->
                if (state.consumersFor?.queueId != queue.queueId) state
                else state.copy(consumers = UiState.Data(result.data))
            }
            is ApiResult.Failure -> _uiState.update { state ->
                if (state.consumersFor?.queueId != queue.queueId) state
                else state.copy(consumers = UiState.Error(ErrorClassifier.classify(result)))
            }
        }
    }

    fun closeConsumers() = _uiState.update {
        it.copy(consumersFor = null, consumers = null, consumerError = null)
    }

    fun detachConsumer(consumer: QueueConsumer) {
        val queue = _uiState.value.consumersFor ?: return
        val consumerId = consumer.consumerId ?: return
        _uiState.update { it.copy(detachingConsumer = consumerId, consumerError = null) }
        viewModelScope.launch {
            when (val result = repository.deleteConsumer(accountId, queue.queueId, consumerId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(detachingConsumer = null) }
                    loadConsumers(queue)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(detachingConsumer = null, consumerError = result.message)
                }
            }
        }
    }

    fun dismissConsumerError() = _uiState.update { it.copy(consumerError = null) }
}
