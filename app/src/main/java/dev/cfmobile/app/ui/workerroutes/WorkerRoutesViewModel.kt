package dev.cfmobile.app.ui.workerroutes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.WorkerRoute
import dev.cfmobile.app.data.repository.WorkersRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [routeId] is null when adding a route, set when editing an existing one. */
data class WorkerRouteFormState(
    val pattern: String = "",
    val script: String = "",
    val routeId: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val isEditing: Boolean get() = routeId != null
}

data class WorkerRoutesUiState(
    val routes: UiState<List<WorkerRoute>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: WorkerRouteFormState? = null,
    val deletingId: String? = null
)

/** A route pattern must include a hostname and a path, so a bare `example.com` is rejected
 *  before it reaches Cloudflare - the API's own error for this is not obvious on a phone. */
fun validateRoutePattern(pattern: String): String? {
    val trimmed = pattern.trim()
    return when {
        trimmed.isBlank() -> "Route pattern is required"
        trimmed.startsWith("http://") || trimmed.startsWith("https://") ->
            "Leave off the scheme - a pattern starts with the hostname, like example.com/*"
        !trimmed.contains("/") -> "Add a path, like example.com/* - a hostname alone isn't a route"
        else -> null
    }
}

/** An empty script name is a real Cloudflare setting: the route matches but runs no Worker,
 *  which is how a path is excluded from a broader pattern. */
fun routeScriptLabel(route: WorkerRoute): String =
    route.script?.takeIf { it.isNotBlank() } ?: "No Worker (route disabled)"

class WorkerRoutesViewModel(
    private val zoneId: String,
    private val repository: WorkersRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkerRoutesUiState())
    val uiState: StateFlow<WorkerRoutesUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(routes = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listRoutes(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(routes = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(routes = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openCreateForm() = _uiState.update { it.copy(form = WorkerRouteFormState()) }

    fun openEditForm(route: WorkerRoute) = _uiState.update {
        it.copy(form = WorkerRouteFormState(pattern = route.pattern, script = route.script.orEmpty(), routeId = route.id))
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (WorkerRouteFormState) -> WorkerRouteFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateRoutePattern(form.pattern)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val pattern = form.pattern.trim()
            val script = form.script.trim()
            val result = if (form.routeId != null) {
                repository.updateRoute(zoneId, form.routeId, pattern, script)
            } else {
                repository.createRoute(zoneId, pattern, script)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(route: WorkerRoute) {
        _uiState.update { it.copy(deletingId = route.id) }
        viewModelScope.launch {
            repository.deleteRoute(zoneId, route.id)
            _uiState.update { it.copy(deletingId = null) }
            load(isRefresh = true)
        }
    }
}
