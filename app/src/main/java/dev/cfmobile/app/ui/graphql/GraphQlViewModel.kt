package dev.cfmobile.app.ui.graphql

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.JsonReader
import dev.cfmobile.app.core.api.RawApiClient
import dev.cfmobile.app.core.api.RawRequest
import dev.cfmobile.app.data.local.WorkingContext
import dev.cfmobile.app.data.local.db.SavedRequestDao
import dev.cfmobile.app.data.local.db.SavedRequestEntity
import dev.cfmobile.app.data.remote.NetworkModule
import dev.cfmobile.app.ui.components.JsonFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.IOException

data class GraphQlTable(val columns: List<String>, val rows: List<List<String>>)

data class GraphQlUiState(
    val templateId: String? = null,
    val range: TimeRange = TimeRange.H24,
    val query: String = "",
    val variables: String = "{}",
    val isRunning: Boolean = false,
    val validation: String? = null,
    val status: Int? = null,
    val durationMillis: Long? = null,
    val errors: List<String> = emptyList(),
    val lines: List<String> = emptyList(),
    val table: GraphQlTable? = null,
    val rawBody: String = "",
    val notice: String? = null
)

class GraphQlViewModel(
    private val client: RawApiClient,
    private val savedRequests: SavedRequestDao,
    private val workingContext: () -> WorkingContext,
    private val profileId: () -> String?
) : ViewModel() {
    private val _uiState = MutableStateFlow(GraphQlUiState())
    val uiState: StateFlow<GraphQlUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    init {
        val ctx = workingContext()
        val first = if (ctx.zone != null) GraphQlTemplates.all.first() else GraphQlTemplates.all.first { it.scope == TemplateScope.ACCOUNT }
        applyTemplate(first)
    }

    fun applyTemplate(t: GraphQlTemplate) {
        val ctx = workingContext()
        val tag = if (t.scope == TemplateScope.ZONE) ctx.zone?.id else ctx.account?.id
        val range = _uiState.value.range
        _uiState.update {
            it.copy(
                templateId = t.id,
                query = t.query,
                variables = t.variables(tag ?: "", range),
                validation = if (tag == null) "Select a ${if (t.scope == TemplateScope.ZONE) "zone" else "account"} on Home first, or enter the tag in variables." else null
            )
        }
    }

    fun setRange(range: TimeRange) {
        _uiState.update { it.copy(range = range) }
        GraphQlTemplates.all.firstOrNull { it.id == _uiState.value.templateId }?.let { t ->
            val ctx = workingContext()
            val tag = if (t.scope == TemplateScope.ZONE) ctx.zone?.id else ctx.account?.id
            _uiState.update { it.copy(variables = t.variables(tag ?: "", range)) }
        }
    }

    fun setQuery(q: String) = _uiState.update { it.copy(query = q, templateId = null, validation = null) }
    fun setVariables(v: String) = _uiState.update { it.copy(variables = v, validation = null) }
    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    fun run() {
        val s = _uiState.value
        GraphQlValidator.validate(s.query)?.let { problem -> _uiState.update { it.copy(validation = problem) }; return }
        if (s.variables.isNotBlank() && !JsonFormat.isValidJson(s.variables)) {
            _uiState.update { it.copy(validation = "Variables are not valid JSON") }
            return
        }
        val body = "{\"query\":${jsonString(s.query)},\"variables\":${s.variables.ifBlank { "{}" }}}"
        _uiState.update { it.copy(isRunning = true, validation = null, errors = emptyList(), lines = emptyList(), table = null) }
        job = viewModelScope.launch {
            try {
                val response = client.execute(RawRequest("POST", NetworkModule.GRAPHQL_PATH, body = body))
                val parsed = withContext(Dispatchers.Default) { parse(response.body) }
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        status = response.statusCode,
                        durationMillis = response.durationMillis,
                        errors = parsed.first,
                        table = parsed.second,
                        lines = JsonFormat.pretty(response.body).lines(),
                        rawBody = response.body
                    )
                }
            } catch (e: IOException) {
                _uiState.update { it.copy(isRunning = false, errors = listOf("Network error: ${e.message}")) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _uiState.update { it.copy(isRunning = false, notice = "Query canceled") }
    }

    fun save(name: String) {
        val profile = profileId() ?: return
        val s = _uiState.value
        viewModelScope.launch {
            savedRequests.insert(
                SavedRequestEntity(
                    profileId = profile, kind = "graphql", name = name.ifBlank { "Saved query" }, method = "POST",
                    path = NetworkModule.GRAPHQL_PATH, query = s.range.name, body = s.query, createdAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(notice = "Query saved") }
        }
    }

    fun load(saved: SavedRequestEntity) {
        _uiState.update { it.copy(query = saved.body.orEmpty(), templateId = null, range = runCatching { TimeRange.valueOf(saved.query ?: "") }.getOrDefault(it.range)) }
    }

    private fun jsonString(s: String): String {
        val b = StringBuilder("\"")
        s.forEach { c ->
            when (c) {
                '"' -> b.append("\\\"")
                '\\' -> b.append("\\\\")
                '\n' -> b.append("\\n")
                '\r' -> b.append("\\r")
                '\t' -> b.append("\\t")
                else -> if (c < ' ') b.append("\\u%04x".format(c.code)) else b.append(c)
            }
        }
        return b.append('"').toString()
    }

    companion object {
        /** Returns GraphQL error messages and the first result array flattened into a table. */
        fun parse(body: String): Pair<List<String>, GraphQlTable?> {
            val root = try {
                JsonReader.of(Buffer().writeUtf8(body)).readJsonValue() as? Map<*, *>
            } catch (e: IOException) {
                null
            } ?: return listOf("Response was not JSON") to null
            val errors = (root["errors"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("message")?.toString() }.orEmpty()
            val array = firstArray(root["data"]) ?: return errors to null
            val rows = array.filterIsInstance<Map<*, *>>().map { flatten(it) }
            if (rows.isEmpty()) return errors to GraphQlTable(emptyList(), emptyList())
            val columns = rows.flatMap { it.keys }.distinct()
            return errors to GraphQlTable(columns, rows.map { r -> columns.map { c -> r[c].orEmpty() } })
        }

        private fun firstArray(node: Any?): List<*>? = when (node) {
            is List<*> -> if (node.firstOrNull() is Map<*, *> && (node.first() as Map<*, *>).values.any { it is List<*> }) firstArray(node.first()) else node
            is Map<*, *> -> node.values.asSequence().mapNotNull { firstArray(it) }.firstOrNull()
            else -> null
        }

        private fun flatten(map: Map<*, *>, prefix: String = ""): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            map.forEach { (k, v) ->
                val key = if (prefix.isEmpty()) k.toString() else "$prefix.$k"
                when (v) {
                    is Map<*, *> -> out.putAll(flatten(v, key))
                    is Double -> out[key] = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
                    else -> out[key] = v?.toString().orEmpty()
                }
            }
            return out
        }
    }
}
