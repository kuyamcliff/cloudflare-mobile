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
    val instances: UiState<List<WorkflowInstance>>? = null
)

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

    fun closeInstances() = _uiState.update { it.copy(selectedWorkflowName = null, instances = null) }
}
