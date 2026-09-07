package dev.cfmobile.app.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.TraceRequest
import dev.cfmobile.app.data.remote.dto.TraceResult
import dev.cfmobile.app.data.remote.dto.TraceStep
import dev.cfmobile.app.data.repository.DiagnosticsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The methods worth tracing from a phone. A trace never reaches the origin, so a POST here
 *  is safe - it only asks Cloudflare which of its own rules would match. */
enum class TraceMethod(val value: String) {
    GET("GET"),
    POST("POST"),
    HEAD("HEAD"),
    PUT("PUT"),
    DELETE("DELETE")
}

data class TracerUiState(
    val url: String = "",
    val method: TraceMethod = TraceMethod.GET,
    val isTracing: Boolean = false,
    val result: TraceResult? = null,
    /** The URL the displayed result belongs to, so a stale trace can't be misread. */
    val tracedUrl: String? = null,
    val error: String? = null
)

fun validateTraceUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return "A URL is required"
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        return "Include the scheme, like https://example.com/path"
    }
    return null
}

/** Cloudflare names each step in snake_case; this makes it readable without pretending to
 *  know every step type it might add. */
fun traceStepLabel(step: TraceStep): String {
    val raw = step.stepName ?: step.trace ?: return "Step ${step.step ?: 0}"
    return raw.split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}

/** "matched · block" - whether the step fired and what it did. */
fun traceStepSummary(step: TraceStep): String = listOfNotNull(
    if (step.matched == true) "matched" else "not matched",
    step.action
).joinToString(" · ")

/** The steps that actually fired, which is usually the whole answer. */
fun matchedSteps(result: TraceResult?): List<TraceStep> =
    result?.trace.orEmpty().filter { it.matched == true }

/**
 * The request tracer: Cloudflare replays a request against its own pipeline and reports which
 * rules matched, in order. Nothing is sent to the origin, so this is safe to run against
 * production - it answers "which of my rules caught this?" without making the request happen.
 */
class TracerViewModel(
    private val accountId: String,
    private val repository: DiagnosticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TracerUiState())
    val uiState: StateFlow<TracerUiState> = _uiState.asStateFlow()

    fun updateUrl(url: String) = _uiState.update { it.copy(url = url, error = null) }

    fun selectMethod(method: TraceMethod) = _uiState.update { it.copy(method = method) }

    fun trace() {
        val url = _uiState.value.url.trim()
        val validationError = validateTraceUrl(url)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }
        _uiState.update { it.copy(isTracing = true, error = null, result = null, tracedUrl = null) }
        viewModelScope.launch {
            val request = TraceRequest(url = url, method = _uiState.value.method.value)
            when (val result = repository.trace(accountId, request)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isTracing = false, result = result.data, tracedUrl = url)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isTracing = false, error = result.message)
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
