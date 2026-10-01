package dev.cfmobile.app.ui.action

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.api.BodyField
import dev.cfmobile.app.core.api.EndpointDef
import dev.cfmobile.app.core.api.EndpointParam
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.api.RawApiClient
import dev.cfmobile.app.core.api.RawRequest
import dev.cfmobile.app.core.api.RawResponse
import dev.cfmobile.app.core.api.RequestRejected
import dev.cfmobile.app.core.capabilities.CapabilityState
import dev.cfmobile.app.core.command.ActionDraft
import dev.cfmobile.app.core.command.Json
import dev.cfmobile.app.core.command.ParsedResult
import dev.cfmobile.app.core.command.ResultModel
import dev.cfmobile.app.core.command.SavedAction
import dev.cfmobile.app.data.local.NamedRef
import dev.cfmobile.app.data.local.WorkingContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** One choice for a path placeholder, from the list operation one level up. */
data class PathOption(val value: String, val label: String, val detail: String?)

sealed class OptionsState {
    data object Idle : OptionsState()
    data object Loading : OptionsState()
    data class Loaded(val options: List<PathOption>) : OptionsState()
    data class Unavailable(val reason: String) : OptionsState()
}

enum class BodyMode { FORM, JSON }

data class RunResult(
    val response: RawResponse,
    val parsed: ParsedResult,
    /** All items loaded so far, across "load more" pages. */
    val items: List<Map<String, Any?>>?,
    val prettyLines: List<String>
)

data class ActionUiState(
    val loading: Boolean = true,
    val endpoint: EndpointDef? = null,
    val method: String = "GET",
    val path: String = "",
    val title: String = "",
    val note: String? = null,
    val source: ActionDraft.Source = ActionDraft.Source.SEARCH,
    val pathValues: Map<String, String> = emptyMap(),
    val pathOptions: Map<String, OptionsState> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    val body: Map<String, Any?> = emptyMap(),
    /** Raw text for fields edited as JSON (objects, arrays, "any"), keyed by field name. */
    val jsonFieldText: Map<String, String> = emptyMap(),
    val bodyMode: BodyMode = BodyMode.FORM,
    val bodyText: String = "",
    val errors: Map<String, String> = emptyMap(),
    val showAllFields: Boolean = false,
    val tokenState: CapabilityState = CapabilityState.UNKNOWN,
    val running: Boolean = false,
    val loadingMore: Boolean = false,
    val result: RunResult? = null,
    val failure: String? = null,
    val pinned: Boolean = false
) {
    val isRead: Boolean get() = method == "GET"
    val hasBody: Boolean get() = method != "GET" && (endpoint?.bodyContentType != null || body.isNotEmpty() || endpoint == null)
    val isJsonBody: Boolean get() = endpoint?.bodyContentType?.contains("json") != false
    val pathParams: List<String> get() = Regex("\\{([^}]+)}").findAll(path).map { it.groupValues[1] }.toList()
    val resolvedPath: String
        get() = path.split('/').joinToString("/") { seg ->
            if (seg.startsWith("{") && seg.endsWith("}")) pathValues[seg.trim('{', '}')]?.takeIf { it.isNotBlank() } ?: seg else seg
        }
    val verb: String get() = ResultModel.verb(endpoint, method)
}

/**
 * Runs any Cloudflare API operation from a reviewed form. Every route into "doing something"
 * ends here: recipes, search hits, Workers AI plans and follow-ups on results.
 */
class ActionViewModel(
    private val registryProvider: suspend () -> EndpointRegistry,
    private val client: RawApiClient,
    private val tokenState: suspend (EndpointDef, accountId: String?, zoneId: String?) -> CapabilityState,
    private val workingContext: () -> WorkingContext,
    private val zones: suspend () -> List<NamedRef>,
    private val accounts: suspend () -> List<NamedRef>,
    private val onRan: (SavedAction) -> Unit,
    private val isPinned: (SavedAction) -> Boolean,
    private val togglePin: (SavedAction) -> Unit,
    draft: ActionDraft?,
    endpointId: String?
) : ViewModel() {

    private val _ui = MutableStateFlow(ActionUiState())
    val ui: StateFlow<ActionUiState> = _ui.asStateFlow()
    private var runJob: Job? = null
    private var registry: EndpointRegistry? = null

    init {
        viewModelScope.launch {
            val reg = registryProvider().also { registry = it }
            val endpoint = when {
                endpointId != null -> reg.endpoints.firstOrNull { it.id == endpointId }
                draft != null -> reg.endpoints.firstOrNull { it.method == draft.method && it.path == draft.path }
                else -> null
            }
            val ctx = workingContext()
            val method = endpoint?.method ?: draft?.method ?: "GET"
            val path = endpoint?.path ?: draft?.path.orEmpty()
            val defaults = defaultPathValues(path, ctx)
            val body = draft?.body ?: emptyMap()
            _ui.value = ActionUiState(
                loading = false,
                endpoint = endpoint,
                method = method,
                path = path,
                title = draft?.title ?: endpoint?.summary ?: "$method $path",
                note = draft?.note,
                source = draft?.source ?: ActionDraft.Source.SEARCH,
                pathValues = defaults + draft?.pathValues.orEmpty().filterValues { it.isNotBlank() },
                query = draft?.query.orEmpty(),
                body = body,
                jsonFieldText = jsonTexts(endpoint, body),
                bodyText = if (body.isEmpty()) endpoint?.bodyExample.orEmpty().takeIf { endpoint?.bodyFields.isNullOrEmpty() } ?: "" else Json.stringify(body, pretty = true),
                bodyMode = if (endpoint?.bodyFields.isNullOrEmpty() && method != "GET") BodyMode.JSON else BodyMode.FORM
            )
            _ui.update { it.copy(pinned = isPinned(saved(it))) }
            refreshTokenState()
            loadOptionsFrom(0)
            if (draft?.autoRun == true && missingRequired().isEmpty()) run()
        }
    }

    // ---- Editing ---------------------------------------------------------------------------

    fun setPathValue(name: String, value: String) {
        _ui.update { it.copy(pathValues = it.pathValues + (name to value.trim()), errors = it.errors - name, result = null, failure = null) }
        viewModelScope.launch {
            refreshTokenState()
            val index = _ui.value.pathParams.indexOf(name)
            loadOptionsFrom(index + 1)
        }
    }

    fun setQuery(name: String, value: String) = _ui.update {
        it.copy(query = if (value.isEmpty()) it.query - name else it.query + (name to value), errors = it.errors - "q:$name")
    }

    fun setField(field: BodyField, value: Any?) = _ui.update {
        val body = if (value == null || value == "") it.body - field.name else it.body + (field.name to value)
        it.copy(body = body, errors = it.errors - "b:${field.name}")
    }

    /** Free-form JSON for an object/array field; parsed on every change, kept as text if invalid. */
    fun setJsonField(field: BodyField, text: String) = _ui.update {
        val parsed = if (text.isBlank()) null else Json.parseOrNull(text)
        val error = if (text.isNotBlank() && parsed == null && field.type != "string") "Not valid JSON" else null
        val body = when {
            text.isBlank() -> it.body - field.name
            parsed != null -> it.body + (field.name to parsed)
            field.type == "any" -> it.body + (field.name to text)
            else -> it.body
        }
        it.copy(
            body = body,
            jsonFieldText = it.jsonFieldText + (field.name to text),
            errors = if (error != null) it.errors + ("b:${field.name}" to error) else it.errors - "b:${field.name}"
        )
    }

    fun setBodyText(text: String) = _ui.update { it.copy(bodyText = text, errors = it.errors - "body") }

    fun setBodyMode(mode: BodyMode) {
        val s = _ui.value
        if (mode == s.bodyMode) return
        if (mode == BodyMode.JSON) {
            _ui.update { it.copy(bodyMode = BodyMode.JSON, bodyText = if (it.body.isEmpty()) it.bodyText else Json.stringify(it.body, pretty = true)) }
        } else {
            if (s.bodyText.isBlank()) {
                _ui.update { it.copy(bodyMode = BodyMode.FORM, body = emptyMap()) }
                return
            }
            @Suppress("UNCHECKED_CAST")
            val parsed = Json.parseOrNull(s.bodyText) as? Map<String, Any?>
            if (parsed == null) {
                _ui.update { it.copy(errors = it.errors + ("body" to "Fix the JSON before switching to the form: it must be one object.")) }
            } else {
                _ui.update { it.copy(bodyMode = BodyMode.FORM, body = parsed, jsonFieldText = jsonTexts(it.endpoint, parsed), errors = it.errors - "body") }
            }
        }
    }

    fun toggleAllFields() = _ui.update { it.copy(showAllFields = !it.showAllFields) }

    fun togglePin() {
        val action = saved(_ui.value)
        togglePin(action)
        _ui.update { it.copy(pinned = isPinned(action)) }
    }

    // ---- Validation and running -------------------------------------------------------------

    fun missingRequired(): Map<String, String> {
        val s = _ui.value
        val out = LinkedHashMap<String, String>()
        s.pathParams.filter { s.pathValues[it].isNullOrBlank() }.forEach { out[it] = "Required" }
        s.endpoint?.queryParams?.filter { it.required && s.query[it.name].isNullOrBlank() }?.forEach { out["q:${it.name}"] = "Required" }
        if (s.hasBody && s.bodyMode == BodyMode.FORM) {
            s.endpoint?.bodyFields?.filter { it.required && isEmpty(s.body[it.name]) }?.forEach { out["b:${it.name}"] = "Required" }
        }
        return out
    }

    private fun isEmpty(v: Any?): Boolean = v == null || v == "" || (v is Collection<*> && v.isEmpty())

    /** Checks everything locally, so nothing invalid leaves the device. Returns a message or null. */
    fun validate(): String? {
        val s = _ui.value
        val missing = missingRequired()
        val fieldErrors = s.errors.filterKeys { it.startsWith("b:") || it == "body" }
        if (missing.isNotEmpty() || fieldErrors.isNotEmpty()) {
            _ui.update { it.copy(errors = it.errors + missing) }
            val first = (missing.keys + fieldErrors.keys).first()
            return when {
                first == "body" -> fieldErrors[first]
                first.startsWith("b:") && first in fieldErrors -> "${first.removePrefix("b:")}: ${fieldErrors[first]}"
                else -> "Fill in ${first.substringAfter(':')} first."
            }
        }
        if (s.hasBody && s.bodyMode == BodyMode.JSON && s.bodyText.isNotBlank() && Json.parseOrNull(s.bodyText) == null) {
            _ui.update { it.copy(errors = it.errors + ("body" to "Body is not valid JSON")) }
            return "Body is not valid JSON"
        }
        return try {
            client.buildRequest(buildRequest()); null
        } catch (e: RequestRejected) {
            e.message
        }
    }

    fun buildRequest(extraQuery: List<Pair<String, String>> = emptyList()): RawRequest {
        val s = _ui.value
        val body = when {
            !s.hasBody -> null
            s.bodyMode == BodyMode.JSON -> s.bodyText.takeIf { it.isNotBlank() }
            else -> Json.stringify(s.body)
        }
        val query = s.query.filter { it.value.isNotBlank() }.map { it.key to it.value }
        val merged = query.filter { q -> extraQuery.none { it.first == q.first } } + extraQuery
        return RawRequest(s.method, s.resolvedPath, query = merged, body = body)
    }

    fun run() {
        val problem = validate()
        if (problem != null) {
            _ui.update { it.copy(failure = problem) }
            return
        }
        val request = buildRequest()
        _ui.update { it.copy(running = true, failure = null, result = null) }
        runJob?.cancel()
        runJob = viewModelScope.launch {
            try {
                val response = client.execute(request)
                val result = withContext(Dispatchers.Default) { toResult(response, previous = null) }
                _ui.update { it.copy(running = false, result = result) }
                onRan(saved(_ui.value))
                if (!response.isSuccess && (response.statusCode == 403 || response.statusCode == 401)) refreshTokenState()
            } catch (e: RequestRejected) {
                _ui.update { it.copy(running = false, failure = e.message) }
            } catch (e: IOException) {
                _ui.update { it.copy(running = false, failure = if (e.message == "Canceled") "Canceled" else "Couldn't reach Cloudflare: ${e.message ?: "network error"}") }
            }
        }
    }

    fun cancel() {
        runJob?.cancel()
        _ui.update { it.copy(running = false, loadingMore = false) }
    }

    /** Loads the next page (page number or cursor) and appends its items. */
    fun loadMore() {
        val s = _ui.value
        val current = s.result ?: return
        val parsed = current.parsed
        val next = when {
            !parsed.cursor.isNullOrBlank() -> listOf("cursor" to parsed.cursor)
            parsed.page != null -> listOf("page" to (parsed.page + 1).toString())
            else -> return
        }
        _ui.update { it.copy(loadingMore = true) }
        runJob = viewModelScope.launch {
            try {
                val response = client.execute(buildRequest(next))
                val result = withContext(Dispatchers.Default) { toResult(response, previous = current) }
                _ui.update { it.copy(loadingMore = false, result = result) }
            } catch (e: IOException) {
                _ui.update { it.copy(loadingMore = false, failure = "Couldn't load more: ${e.message ?: "network error"}") }
            }
        }
    }

    private fun toResult(response: RawResponse, previous: RunResult?): RunResult {
        val parsed = ResultModel.parse(response.body, response.statusCode)
        val items = parsed.items?.let { (previous?.items.orEmpty()) + it }
        val pretty = dev.cfmobile.app.ui.components.JsonFormat.pretty(response.body).lines()
        return RunResult(response, parsed, items, pretty)
    }

    // ---- Follow-ups -------------------------------------------------------------------------

    fun followUps(isList: Boolean): List<ResultModel.FollowUp> {
        val reg = registry ?: return emptyList()
        val s = _ui.value
        return ResultModel.followUps(reg, s.method, s.path, isList).take(14)
    }

    /** A draft that continues from [item] with [followUp]. */
    fun followUpDraft(followUp: ResultModel.FollowUp, item: Map<String, Any?>?): ActionDraft {
        val s = _ui.value
        val values = HashMap(s.pathValues)
        if (item != null && followUp.placeholder != null) {
            ResultModel.valueFor(followUp.placeholder, item)?.let { values[followUp.placeholder] = it }
        }
        val e = followUp.endpoint
        val body = if (item != null && (e.method == "PATCH" || e.method == "PUT")) ResultModel.editBody(e, item) else null
        return ActionDraft(
            method = e.method, path = e.path, title = e.summary,
            pathValues = values.filterKeys { it in e.placeholderNames() },
            body = body, autoRun = e.method == "GET", source = ActionDraft.Source.FOLLOW_UP
        )
    }

    // ---- Path options -----------------------------------------------------------------------

    /** Loads choices for each placeholder from [fromIndex] on whose parents are filled. */
    private suspend fun loadOptionsFrom(fromIndex: Int) {
        val s = _ui.value
        val names = s.pathParams
        for (i in names.indices) {
            if (i < fromIndex) continue
            val name = names[i]
            val lower = name.lowercase()
            when {
                lower.contains("zone") && (lower.endsWith("id") || lower == "zone_identifier") -> {
                    val list = zones().map { PathOption(it.id, it.name, null) }
                    setOptions(name, if (list.isEmpty()) OptionsState.Unavailable("No zones loaded") else OptionsState.Loaded(list))
                }
                lower.contains("account") && (lower.endsWith("id") || lower == "account_identifier") -> {
                    val list = accounts().map { PathOption(it.id, it.name, null) }
                    setOptions(name, if (list.isEmpty()) OptionsState.Unavailable("No accounts loaded") else OptionsState.Loaded(list))
                }
                else -> loadListOptions(name)
            }
        }
    }

    private suspend fun loadListOptions(name: String) {
        val s = _ui.value
        val template = s.path.substringBefore("{$name}").trimEnd('/')
        val reg = registry ?: return
        val list = reg.endpoints.firstOrNull { it.method == "GET" && it.path == template }
        if (list == null) {
            setOptions(name, OptionsState.Unavailable("Type the value"))
            return
        }
        val parents = Regex("\\{([^}]+)}").findAll(template).map { it.groupValues[1] }.toList()
        if (parents.any { s.pathValues[it].isNullOrBlank() }) {
            setOptions(name, OptionsState.Idle)
            return
        }
        val resolved = template.split('/').joinToString("/") { seg ->
            if (seg.startsWith("{")) s.pathValues[seg.trim('{', '}')].orEmpty() else seg
        }
        setOptions(name, OptionsState.Loading)
        val perPage = if (list.queryParams.any { it.name == "per_page" }) listOf("per_page" to "100") else emptyList()
        val outcome = runCatching { client.execute(RawRequest("GET", resolved, query = perPage)) }
        val response = outcome.getOrNull()
        if (response == null || !response.isSuccess) {
            setOptions(name, OptionsState.Unavailable(if (response?.statusCode == 403) "This token can't list these" else "Couldn't load choices"))
            return
        }
        val items = withContext(Dispatchers.Default) { ResultModel.parse(response.body, response.statusCode).items.orEmpty() }
        val options = items.mapNotNull { item ->
            val value = ResultModel.valueFor(name, item) ?: return@mapNotNull null
            val label = ResultModel.title(item)
            PathOption(value, label, ResultModel.subtitle(item) ?: value.takeIf { it != label })
        }
        setOptions(name, if (options.isEmpty()) OptionsState.Unavailable("Nothing to choose from yet") else OptionsState.Loaded(options))
    }

    private fun setOptions(name: String, state: OptionsState) = _ui.update { it.copy(pathOptions = it.pathOptions + (name to state)) }

    // ---- Helpers ----------------------------------------------------------------------------

    private suspend fun refreshTokenState() {
        val s = _ui.value
        val e = s.endpoint ?: return
        val account = s.pathValues.entries.firstOrNull { it.key.contains("account") }?.value?.ifBlank { null } ?: workingContext().account?.id
        val zone = s.pathValues.entries.firstOrNull { it.key.contains("zone") }?.value?.ifBlank { null }
        _ui.update { it.copy(tokenState = tokenState(e, account, zone)) }
    }

    private fun defaultPathValues(path: String, ctx: WorkingContext): Map<String, String> {
        val names = Regex("\\{([^}]+)}").findAll(path).map { it.groupValues[1] }
        return names.mapNotNull { n ->
            val lower = n.lowercase()
            val v = when {
                lower == "zone_id" || lower == "zone_identifier" || (lower == "identifier" && path.startsWith("zones/")) -> ctx.zone?.id
                lower == "account_id" || lower == "account_identifier" || (lower == "identifier" && path.startsWith("accounts/")) -> ctx.zone?.parentId ?: ctx.account?.id
                else -> null
            }
            v?.let { n to it }
        }.toMap()
    }

    private fun jsonTexts(endpoint: EndpointDef?, body: Map<String, Any?>): Map<String, String> =
        endpoint?.bodyFields.orEmpty().filter { isJsonField(it) }.mapNotNull { f ->
            body[f.name]?.let { v -> f.name to if (v is String && f.type == "any") v else Json.stringify(v, pretty = true) }
        }.toMap()

    private fun saved(s: ActionUiState): SavedAction {
        val ctx = workingContext()
        val zoneName = s.pathValues.entries.firstOrNull { it.key.contains("zone") }?.value?.let { id -> if (ctx.zone?.id == id) ctx.zone.name else null }
        return SavedAction(s.method, s.path, s.title, s.pathValues, s.query.filterValues { it.isNotBlank() }, zoneName, System.currentTimeMillis())
    }

    companion object {
        fun isJsonField(f: BodyField): Boolean =
            f.type == "object" || f.type == "any" && f.enumValues.isEmpty() || (f.type == "array" && f.itemType !in setOf("string", "integer", "number"))

        fun queryParamsFor(endpoint: EndpointDef?): List<EndpointParam> = endpoint?.queryParams.orEmpty()
    }
}
