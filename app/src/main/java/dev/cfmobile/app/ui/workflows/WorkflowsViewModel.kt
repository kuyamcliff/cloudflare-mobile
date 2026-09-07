package dev.cfmobile.app.ui.workflows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfWorkflow
import dev.cfmobile.app.data.remote.dto.WorkflowInstance
import dev.cfmobile.app.data.repository.WorkflowsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkflowsUiState(
    val workflows: UiState<List<CfWorkflow>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val selectedWorkflowName: String? = null,
    val instances: UiState<List<WorkflowInstance>>? = null,
    /** Non-null while a trigger or status change is in flight, so the sheet can disable its
     *  controls without blanking the list. */
    val busyInstanceId: String? = null,
    val isTriggering: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

/** Only a run that is actually going can be paused or terminated; offering it on a finished
 *  one just produces a Cloudflare error. */
fun canTerminate(instance: WorkflowInstance): Boolean =
    workflowStatusTone(instance.status) == "pending"

fun canPause(instance: WorkflowInstance): Boolean =
    instance.status?.lowercase() in setOf("running", "queued", "waiting")

fun canResume(instance: WorkflowInstance): Boolean =
    instance.status?.lowercase() in setOf("paused", "waitingforpause")

/** Instance status strings Cloudflare returns, mapped to whether they read as healthy,
 *  in-flight, or failed - the screen colours the pill from this rather than from raw text. */
fun workflowStatusTone(status: String?): String = when (status?.lowercase()) {
    "complete", "completed", "success" -> "success"
    "errored", "error", "failed", "terminated" -> "error"
    "running", "queued", "paused", "waiting", "waitingforpause" -> "pending"
    else -> "neutral"
}

class WorkflowsViewModel(
    private val accountId: String,
    private val repository: WorkflowsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowsUiState())
    val uiState: StateFlow<WorkflowsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(workflows = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listWorkflows(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(workflows = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(workflows = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun selectWorkflow(workflow: CfWorkflow) {
        _uiState.update { it.copy(selectedWorkflowName = workflow.name, instances = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listInstances(accountId, workflow.name)) {
                is ApiResult.Success -> _uiState.update { it.copy(instances = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(instances = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun closeInstances() = _uiState.update {
        it.copy(selectedWorkflowName = null, instances = null, error = null, message = null)
    }

    /** Starts a new run of the open Workflow. Cloudflare generates the instance id. */
    fun trigger() {
        val workflowName = _uiState.value.selectedWorkflowName ?: return
        _uiState.update { it.copy(isTriggering = true, error = null, message = null) }
        viewModelScope.launch {
            when (val result = repository.triggerInstance(accountId, workflowName)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isTriggering = false, message = "Run started") }
                    reloadInstances(workflowName)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isTriggering = false, error = result.message) }
            }
        }
    }

    /** [status] is one of Cloudflare's verbs: "terminate", "pause", or "resume". */
    fun setInstanceStatus(instance: WorkflowInstance, status: String) {
        val workflowName = _uiState.value.selectedWorkflowName ?: return
        _uiState.update { it.copy(busyInstanceId = instance.id, error = null, message = null) }
        viewModelScope.launch {
            val result = repository.setInstanceStatus(accountId, workflowName, instance.id, status)
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(busyInstanceId = null) }
                    reloadInstances(workflowName)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(busyInstanceId = null, error = result.message) }
            }
        }
    }

    private suspend fun reloadInstances(workflowName: String) {
        when (val result = repository.listInstances(accountId, workflowName)) {
            is ApiResult.Success -> _uiState.update { state ->
                // Guard against the sheet having been closed or switched while the call ran.
                if (state.selectedWorkflowName != workflowName) state
                else state.copy(instances = UiState.Data(result.data))
            }
            is ApiResult.Failure -> _uiState.update { state ->
                if (state.selectedWorkflowName != workflowName) state
                else state.copy(instances = UiState.Error(ErrorClassifier.classify(result)))
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null, message = null) }
}
