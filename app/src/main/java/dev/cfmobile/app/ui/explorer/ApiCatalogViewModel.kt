package dev.cfmobile.app.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.api.EndpointDef
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.api.EndpointScope
import dev.cfmobile.app.core.capabilities.CapabilityRepository
import dev.cfmobile.app.core.capabilities.CapabilityState
import dev.cfmobile.app.core.capabilities.PolicySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CatalogGrouping(val label: String) { PRODUCT("Product"), SCOPE("Scope"), METHOD("Method"), PERMISSION("Permission") }

data class CatalogEntry(val endpoint: EndpointDef, val state: CapabilityState)

data class CatalogSection(val title: String, val entries: List<CatalogEntry>)

data class ApiCatalogUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val grouping: CatalogGrouping = CatalogGrouping.PRODUCT,
    val scope: EndpointScope? = null,
    val hideRestricted: Boolean = false,
    val hideDeprecated: Boolean = true,
    val policiesKnown: Boolean = false,
    val sections: List<CatalogSection> = emptyList(),
    val expanded: Set<String> = emptySet(),
    val total: Int = 0,
    val shown: Int = 0,
    val restrictedCount: Int = 0,
    val schemaRevision: String = "",
    val generatedAt: String = ""
)

/**
 * "All Cloudflare APIs" (spec 67, 258): every operation in Cloudflare's schema, grouped and
 * filtered, with what this token can reach marked from the capability engine. Nothing here
 * is hidden merely because it lacks a native screen (spec 166).
 */
@OptIn(FlowPreview::class)
class ApiCatalogViewModel(
    private val registryProvider: suspend () -> EndpointRegistry,
    private val capabilities: CapabilityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiCatalogUiState())
    val uiState: StateFlow<ApiCatalogUiState> = _uiState.asStateFlow()
    private val filters = MutableStateFlow(Filters())

    private data class Filters(
        val query: String = "",
        val grouping: CatalogGrouping = CatalogGrouping.PRODUCT,
        val scope: EndpointScope? = null,
        val hideRestricted: Boolean = false,
        val hideDeprecated: Boolean = true
    )

    init {
        viewModelScope.launch {
            val registry = registryProvider()
            combine(filters.debounce(150), capabilities.state) { f, caps -> f to caps }.collect { (f, capState) ->
                val caps = capState.capabilities
                val computed = withContext(Dispatchers.Default) {
                    val terms = f.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val entries = registry.endpoints.asSequence()
                        .filter { f.scope == null || it.scope == f.scope }
                        .filter { !f.hideDeprecated || !it.deprecated }
                        .filter { e ->
                            terms.isEmpty() || "${e.method} ${e.path} ${e.summary} ${e.group} ${e.permissions.joinToString(" ")}".lowercase().let { hay -> terms.all(hay::contains) }
                        }
                        .map { CatalogEntry(it, caps?.evaluate(it) ?: CapabilityState.UNKNOWN) }
                        .toList()
                    val restricted = entries.count { it.state == CapabilityState.TOKEN_RESTRICTED }
                    val visible = if (f.hideRestricted) entries.filter { it.state != CapabilityState.TOKEN_RESTRICTED } else entries
                    val sections = group(visible, f.grouping)
                    Triple(sections, visible.size, restricted)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        query = f.query,
                        grouping = f.grouping,
                        scope = f.scope,
                        hideRestricted = f.hideRestricted,
                        hideDeprecated = f.hideDeprecated,
                        policiesKnown = caps?.source == PolicySource.TOKEN_POLICIES,
                        sections = computed.first,
                        shown = computed.second,
                        restrictedCount = computed.third,
                        total = registry.endpoints.size,
                        schemaRevision = registry.schemaRevision.take(12),
                        generatedAt = registry.generatedAt,
                        // A search opens every matching section so results are visible at once.
                        expanded = if (f.query.isNotBlank() && computed.second <= 300) computed.first.map { s -> s.title }.toSet() else it.expanded
                    )
                }
            }
        }
    }

    private fun group(entries: List<CatalogEntry>, grouping: CatalogGrouping): List<CatalogSection> {
        val map: Map<String, List<CatalogEntry>> = when (grouping) {
            CatalogGrouping.PRODUCT -> entries.groupBy { it.endpoint.group }
            CatalogGrouping.SCOPE -> entries.groupBy { it.endpoint.scope.name.lowercase().replaceFirstChar(Char::uppercase) }
            CatalogGrouping.METHOD -> entries.groupBy { it.endpoint.method }
            CatalogGrouping.PERMISSION -> buildMap<String, MutableList<CatalogEntry>> {
                entries.forEach { e ->
                    val perms = e.endpoint.permissions.ifEmpty { listOf("Not specified in schema") }
                    perms.distinct().forEach { p -> getOrPut(p) { mutableListOf() }.add(e) }
                }
            }
        }
        return map.entries.sortedBy { it.key.lowercase() }.map { (k, v) -> CatalogSection(k, v.sortedBy { it.endpoint.path }) }
    }

    fun setQuery(q: String) = filters.update { it.copy(query = q) }.also { _uiState.update { s -> s.copy(query = q) } }
    fun setGrouping(g: CatalogGrouping) = filters.update { it.copy(grouping = g) }.also { _uiState.update { s -> s.copy(expanded = emptySet()) } }
    fun setScope(s: EndpointScope?) = filters.update { it.copy(scope = s) }
    fun setHideRestricted(v: Boolean) = filters.update { it.copy(hideRestricted = v) }
    fun setHideDeprecated(v: Boolean) = filters.update { it.copy(hideDeprecated = v) }
    fun toggle(section: String) = _uiState.update {
        it.copy(expanded = if (section in it.expanded) it.expanded - section else it.expanded + section)
    }
}
