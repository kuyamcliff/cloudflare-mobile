package dev.cfmobile.app.ui.logpush

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.LogpushJob
import dev.cfmobile.app.data.repository.LogpushRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LogpushUiState(
    val jobs: UiState<List<LogpushJob>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val togglingId: Long? = null,
    val deletingId: Long? = null
)

/** Destination strings embed credentials as query parameters (an S3 secret, an R2 token, a
 *  Splunk token). Show enough to identify where logs go, never the credential itself. */
fun redactDestination(destination: String?): String? {
    if (destination.isNullOrBlank()) return null
    val withoutQuery = destination.substringBefore('?')
    return if (withoutQuery.length < destination.length) "$withoutQuery?…" else withoutQuery
}

class LogpushViewModel(
    private val accountId: String,
    private val repository: LogpushRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogpushUiState())
    val uiState: StateFlow<LogpushUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(jobs = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listJobs(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(jobs = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(jobs = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun setEnabled(job: LogpushJob, enabled: Boolean) {
        _uiState.update { it.copy(togglingId = job.id) }
        viewModelScope.launch {
            repository.setEnabled(accountId, job.id, enabled)
            _uiState.update { it.copy(togglingId = null) }
            load(isRefresh = true)
        }
    }

    fun delete(job: LogpushJob) {
        _uiState.update { it.copy(deletingId = job.id) }
        viewModelScope.launch {
            repository.deleteJob(accountId, job.id)
            _uiState.update { it.copy(deletingId = null) }
            load(isRefresh = true)
        }
    }
}
