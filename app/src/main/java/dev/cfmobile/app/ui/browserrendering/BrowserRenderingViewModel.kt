package dev.cfmobile.app.ui.browserrendering

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.repository.BrowserRenderingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserRenderingUiState(
    val url: String = "",
    val isRendering: Boolean = false,
    val screenshot: ByteArray? = null,
    val renderedUrl: String? = null,
    val error: String? = null
) {
    // ByteArray needs structural equals/hashCode for this to behave as a value in StateFlow.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BrowserRenderingUiState) return false
        return url == other.url &&
            isRendering == other.isRendering &&
            renderedUrl == other.renderedUrl &&
            error == other.error &&
            screenshot.contentEquals(other.screenshot)
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + isRendering.hashCode()
        result = 31 * result + (screenshot?.contentHashCode() ?: 0)
        result = 31 * result + (renderedUrl?.hashCode() ?: 0)
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

/** Requires an absolute http(s) URL - the rendering browser has no notion of a relative one,
 *  and a bare hostname would be rejected server-side after a slow round trip. */
fun validateScreenshotUrl(url: String): String? {
    val trimmed = url.trim()
    return when {
        trimmed.isBlank() -> "Enter a URL to render"
        !trimmed.startsWith("http://") && !trimmed.startsWith("https://") ->
            "Include the scheme, e.g. https://example.com"
        else -> null
    }
}

/** Browser Rendering is a runtime API rather than something to administer, so this is a small
 *  utility: give it a URL, get back a screenshot rendered at Cloudflare's edge. */
class BrowserRenderingViewModel(
    private val accountId: String,
    private val repository: BrowserRenderingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserRenderingUiState())
    val uiState: StateFlow<BrowserRenderingUiState> = _uiState.asStateFlow()

    fun updateUrl(url: String) = _uiState.update { it.copy(url = url, error = null) }

    fun render() {
        val url = _uiState.value.url.trim()
        val validationError = validateScreenshotUrl(url)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }
        _uiState.update { it.copy(isRendering = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.screenshot(accountId, url)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isRendering = false, screenshot = result.data, renderedUrl = url, error = null)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isRendering = false, error = result.message)
                }
            }
        }
    }

    fun clear() = _uiState.update { it.copy(screenshot = null, renderedUrl = null, error = null) }
}
