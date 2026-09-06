package dev.cfmobile.app.ui.workers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.WorkerSchedule
import dev.cfmobile.app.data.remote.dto.WorkerScript
import dev.cfmobile.app.data.repository.WorkersRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the detail sheet shows beyond the list row. [source] and [schedules] are fetched
 *  lazily, since neither is on the list response and both cost a request each. */
data class WorkerDetailState(
    val script: WorkerScript,
    val isLoading: Boolean = true,
    val source: String? = null,
    val sourceError: String? = null,
    val schedules: List<WorkerSchedule> = emptyList(),
    val schedulesError: String? = null
)

data class WorkersUiState(
    val scripts: UiState<List<WorkerScript>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val deletingId: String? = null,
    val detail: WorkerDetailState? = null
)

/** A module Worker's source comes back as a multipart body rather than bare JavaScript.
 *  Showing the raw body would bury the code under MIME boundaries, so the parts are split out
 *  and the JavaScript ones concatenated. A body that isn't multipart is returned unchanged. */
fun extractWorkerSource(body: String): String {
    val boundaryLine = body.lineSequence().firstOrNull()?.trim().orEmpty()
    if (!boundaryLine.startsWith("--")) return body
    return body.split(boundaryLine)
        .mapNotNull { part ->
            // Each part is headers, a blank line, then content.
            val separator = part.indexOf("\n\n").takeIf { it >= 0 }
                ?: part.indexOf("\r\n\r\n").takeIf { it >= 0 }
                ?: return@mapNotNull null
            part.substring(separator).trim().takeIf { it.isNotBlank() && it != "--" }
        }
        .joinToString("\n\n")
        .ifBlank { body }
}

/** List/inspect/delete - editing or deploying script code needs an editor and bundler
 *  that don't belong on mobile, see CapabilityRegistry's migrationHint. */
class WorkersViewModel(
    private val accountId: String,
    private val repository: WorkersRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkersUiState())
    val uiState: StateFlow<WorkersUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    /** [isRefresh] keeps the current list on screen during a pull-to-refresh, rather than
     *  replacing content the user is reading with a spinner. */
    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(scripts = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listScripts(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(scripts = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(scripts = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    /** Opens the detail sheet and fetches source and cron triggers. Both requests run in one
     *  coroutine, sequentially: two racing launches make the loading flag ambiguous and are
     *  untestable against a queued mock server. A failure on one is reported inline rather
     *  than blanking the sheet - the other half is still useful. */
    fun openDetail(script: WorkerScript) {
        _uiState.update { it.copy(detail = WorkerDetailState(script = script)) }
        viewModelScope.launch {
            val sourceResult = repository.getScriptSource(accountId, script.id)
            val schedulesResult = repository.getSchedules(accountId, script.id)
            val source = (sourceResult as? ApiResult.Success<String>)?.data
            val schedules = (schedulesResult as? ApiResult.Success<List<WorkerSchedule>>)?.data
            _uiState.update { state ->
                // A second script may have been opened while these were in flight; that sheet
                // owns the state now.
                val detail = state.detail
                if (detail == null || detail.script.id != script.id) return@update state
                state.copy(
                    detail = detail.copy(
                        isLoading = false,
                        source = source?.let(::extractWorkerSource),
                        sourceError = (sourceResult as? ApiResult.Failure)?.message,
                        schedules = schedules.orEmpty(),
                        schedulesError = (schedulesResult as? ApiResult.Failure)?.message
                    )
                )
            }
        }
    }

    fun closeDetail() = _uiState.update { it.copy(detail = null) }

    fun delete(script: WorkerScript) {
        _uiState.update { it.copy(deletingId = script.id) }
        viewModelScope.launch {
            repository.deleteScript(accountId, script.id)
            _uiState.update { it.copy(deletingId = null, detail = null) }
            refresh()
        }
    }
}
