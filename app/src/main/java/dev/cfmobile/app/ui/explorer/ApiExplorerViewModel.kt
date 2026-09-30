package dev.cfmobile.app.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.api.CurlCodec
import dev.cfmobile.app.core.api.EndpointDef
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.api.RawApiClient
import dev.cfmobile.app.core.api.RawRequest
import dev.cfmobile.app.core.api.RawResponse
import dev.cfmobile.app.core.api.RequestRejected
import dev.cfmobile.app.core.capabilities.CapabilityRepository
import dev.cfmobile.app.core.capabilities.CapabilityState
import dev.cfmobile.app.data.local.WorkingContext
import dev.cfmobile.app.data.local.db.SavedRequestDao
import dev.cfmobile.app.data.local.db.SavedRequestEntity
import dev.cfmobile.app.ui.components.JsonFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

data class KeyValue(val key: String = "", val value: String = "")

data class ExplorerResult(
    val response: RawResponse,
    /** Pretty-printed body split into lines, computed off the main thread. */
    val lines: List<String>
)

data class ApiExplorerUiState(
    val endpoint: EndpointDef? = null,
    val method: String = "GET",
    val pathTemplate: String = "",
    val pathValues: Map<String, String> = emptyMap(),
    val query: List<KeyValue> = emptyList(),
    val headers: List<KeyValue> = emptyList(),
    val body: String = "",
    val bodyError: String? = null,
    val isSending: Boolean = false,
    val result: ExplorerResult? = null,
    val error: String? = null,
    val tokenState: CapabilityState = CapabilityState.UNKNOWN,
    val notice: String? = null
) {
    val resolvedPath: String
        get() = pathTemplate.split('/').joinToString("/") { seg ->
            if (seg.startsWith("{") && seg.endsWith("}")) pathValues[seg.trim('{', '}')]?.takeIf { it.isNotBlank() } ?: seg else seg
        }
    val isMutation: Boolean get() = method != "GET"
}

/**
 * The generic Cloudflare API request builder (spec 26, 162). Requests go through
 * [RawApiClient], so the host boundary, retries, redacted history and token injection all
 * apply; nothing typed here can widen what the token is allowed to do.
 */
class ApiExplorerViewModel(
    private val registryProvider: suspend () -> EndpointRegistry,
    private val client: RawApiClient,
    private val capabilities: CapabilityRepository,
    private val savedRequests: SavedRequestDao,
    private val workingContext: () -> WorkingContext,
    private val profileId: () -> String?,
    initialEndpointId: String?,
    initialMethod: String?,
    initialPath: String?,
    initialQuery: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiExplorerUiState())
    val uiState: StateFlow<ApiExplorerUiState> = _uiState.asStateFlow()
    private var sendJob: Job? = null

    init {
        viewModelScope.launch {
            val registry = registryProvider()
            val endpoint = initialEndpointId?.let { id -> registry.endpoints.firstOrNull { it.id == id } }
            when {
                endpoint != null -> applyEndpoint(endpoint)
                initialPath != null -> {
                    val matched = registry.match(initialMethod ?: "GET", initialPath)
                    val query = initialQuery?.split('&')?.filter { it.isNotBlank() }?.map {
                        KeyValue(it.substringBefore('='), it.substringAfter('=', ""))
                    }.orEmpty()
                    _uiState.update {
                        it.copy(endpoint = matched, method = initialMethod ?: "GET", pathTemplate = initialPath, query = query)
                    }
                    refreshTokenState()
                }
            }
        }
    }

    private suspend fun applyEndpoint(endpoint: EndpointDef) {
        val ctx = workingContext()
        val values = endpoint.pathParams.associate { p -> p.name to (defaultFor(p.name, ctx) ?: "") }
        val query = endpoint.queryParams.filter { it.required }.map { KeyValue(it.name, "") }
        _uiState.update {
            it.copy(
                endpoint = endpoint,
                method = endpoint.method,
                pathTemplate = endpoint.path,
                pathValues = values,
                query = query,
                body = endpoint.bodyExample.orEmpty(),
                result = null,
                error = null
            )
        }
        refreshTokenState()
    }

    private suspend fun refreshTokenState() {
        val s = _uiState.value
        val endpoint = s.endpoint ?: return
        val caps = capabilities.state.value.capabilities ?: return
        val ctx = workingContext()
        val state = caps.evaluate(
            endpoint,
            accountId = s.pathValues.entries.firstOrNull { it.key.contains("account") }?.value?.ifBlank { null } ?: ctx.account?.id,
            zoneId = s.pathValues.entries.firstOrNull { it.key.contains("zone") }?.value?.ifBlank { null },
            zoneAccountId = ctx.zone?.parentId
        )
        _uiState.update { it.copy(tokenState = state) }
    }

    private fun defaultFor(name: String, ctx: WorkingContext): String? {
        val n = name.lowercase()
        return when {
            n.contains("account") -> ctx.account?.id
            n.contains("zone") -> ctx.zone?.id
            else -> null
        }
    }

    fun setMethod(method: String) = _uiState.update { it.copy(method = method) }
    fun setPathTemplate(path: String) = _uiState.update { it.copy(pathTemplate = path.trim(), endpoint = null) }
    fun setPathValue(name: String, value: String) {
        _uiState.update { it.copy(pathValues = it.pathValues + (name to value.trim())) }
        viewModelScope.launch { refreshTokenState() }
    }
    fun setBody(body: String) = _uiState.update { it.copy(body = body, bodyError = null) }
    fun setQuery(index: Int, kv: KeyValue) = _uiState.update { s -> s.copy(query = s.query.toMutableList().also { it[index] = kv }) }
    fun addQuery(name: String = "") = _uiState.update { it.copy(query = it.query + KeyValue(name, "")) }
    fun removeQuery(index: Int) = _uiState.update { s -> s.copy(query = s.query.filterIndexed { i, _ -> i != index }) }
    fun setHeader(index: Int, kv: KeyValue) = _uiState.update { s -> s.copy(headers = s.headers.toMutableList().also { it[index] = kv }) }
    fun addHeader() = _uiState.update { it.copy(headers = it.headers + KeyValue()) }
    fun removeHeader(index: Int) = _uiState.update { s -> s.copy(headers = s.headers.filterIndexed { i, _ -> i != index }) }
    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    fun formatBody() {
        val body = _uiState.value.body
        if (body.isBlank()) return
        if (!JsonFormat.isValidJson(body)) {
            _uiState.update { it.copy(bodyError = "Body is not valid JSON") }
            return
        }
        _uiState.update { it.copy(body = JsonFormat.pretty(body), bodyError = null) }
    }

    fun buildRawRequest(): RawRequest {
        val s = _uiState.value
        return RawRequest(
            method = s.method,
            path = s.resolvedPath,
            query = s.query.filter { it.key.isNotBlank() }.map { it.key to it.value },
            headers = s.headers.filter { it.key.isNotBlank() }.map { it.key to it.value },
            body = s.body.takeIf { s.method != "GET" && it.isNotBlank() }
        )
    }

    /** Validates locally before anything leaves the device (spec 48, 110). */
    fun validate(): String? {
        val s = _uiState.value
        if (s.pathTemplate.isBlank()) return "Enter a path"
        if (s.method != "GET" && s.body.isNotBlank() && !JsonFormat.isValidJson(s.body)) return "Body is not valid JSON"
        return try {
            client.buildRequest(buildRawRequest()); null
        } catch (e: RequestRejected) {
            e.message
        }
    }

    fun send() {
        val problem = validate()
        if (problem != null) {
            _uiState.update { it.copy(error = problem) }
            return
        }
        val request = buildRawRequest()
        _uiState.update { it.copy(isSending = true, error = null, result = null) }
        sendJob = viewModelScope.launch {
            try {
                val response = client.execute(request)
                val lines = withContext(Dispatchers.Default) { JsonFormat.pretty(response.body).lines() }
                _uiState.update { it.copy(isSending = false, result = ExplorerResult(response, lines)) }
            } catch (e: RequestRejected) {
                _uiState.update { it.copy(isSending = false, error = e.message) }
            } catch (e: IOException) {
                _uiState.update { it.copy(isSending = false, error = if (e.message == "Canceled") "Request canceled" else "Network error: ${e.message ?: "unreachable"}") }
            }
        }
    }

    fun cancel() {
        sendJob?.cancel()
        _uiState.update { it.copy(isSending = false, error = "Request canceled") }
    }

    /** Re-runs a GET streaming the full body to [out] (for responses too large to show). */
    suspend fun downloadFullResponse(out: OutputStream): String? {
        val s = _uiState.value
        if (s.method != "GET") return "Only GET responses can be re-downloaded. Mutations are never repeated automatically."
        return try {
            client.execute(buildRawRequest(), saveTo = out)
            null
        } catch (e: IOException) {
            "Download failed: ${e.message}"
        } catch (e: RequestRejected) {
            e.message
        }
    }

    fun curl(): String {
        val url = runCatching { client.buildUrl(_uiState.value.resolvedPath, buildRawRequest().query).toString() }
            .getOrElse { _uiState.value.resolvedPath }
        val r = buildRawRequest()
        return CurlCodec.export(url, r.method, r.headers, r.body)
    }

    fun importCurl(command: String) {
        try {
            val parsed = CurlCodec.parse(command)
            viewModelScope.launch {
                val registry = registryProvider()
                val matched = registry.match(parsed.request.method, parsed.request.path)
                _uiState.update {
                    it.copy(
                        endpoint = matched,
                        method = parsed.request.method,
                        pathTemplate = parsed.request.path,
                        pathValues = emptyMap(),
                        query = parsed.request.query.map { (k, v) -> KeyValue(k, v) },
                        headers = parsed.request.headers.map { (k, v) -> KeyValue(k, v) },
                        body = parsed.request.body?.let(JsonFormat::pretty).orEmpty(),
                        notice = if (parsed.removedCredentialHeaders.isNotEmpty())
                            "Removed ${parsed.removedCredentialHeaders.joinToString()} from the import. The active profile's token is used instead."
                        else "Imported",
                        result = null
                    )
                }
                refreshTokenState()
            }
        } catch (e: IllegalArgumentException) {
            _uiState.update { it.copy(notice = e.message ?: "Could not import this command") }
        }
    }

    fun saveTemplate(name: String) {
        val profile = profileId() ?: return
        val s = _uiState.value
        viewModelScope.launch {
            savedRequests.insert(
                SavedRequestEntity(
                    profileId = profile,
                    kind = KIND_REST,
                    name = name.ifBlank { s.endpoint?.summary ?: "${s.method} ${s.pathTemplate}" },
                    method = s.method,
                    // The template keeps placeholders so it stays reusable across accounts.
                    path = s.pathTemplate,
                    query = s.query.filter { it.key.isNotBlank() }.joinToString("&") { "${it.key}=${it.value}" }.ifBlank { null },
                    body = s.body.ifBlank { null },
                    createdAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(notice = "Saved to templates") }
        }
    }

    fun loadTemplate(t: SavedRequestEntity) {
        viewModelScope.launch {
            val registry = registryProvider()
            val ctx = workingContext()
            val matched = registry.endpoints.firstOrNull { it.method == t.method && it.path == t.path }
            val placeholders = t.path.split('/').filter { it.startsWith("{") && it.endsWith("}") }.map { it.trim('{', '}') }
            _uiState.update {
                it.copy(
                    endpoint = matched,
                    method = t.method,
                    pathTemplate = t.path,
                    pathValues = placeholders.associateWith { name -> defaultFor(name, ctx).orEmpty() },
                    query = t.query?.split('&')?.map { q -> KeyValue(q.substringBefore('='), q.substringAfter('=', "")) }.orEmpty(),
                    body = t.body.orEmpty(),
                    result = null
                )
            }
            refreshTokenState()
        }
    }

    companion object {
        const val KIND_REST = "rest"
        const val KIND_GRAPHQL = "graphql"
    }
}
