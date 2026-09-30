package dev.cfmobile.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.data.local.db.RequestHistoryDao
import dev.cfmobile.app.data.local.db.RequestHistoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActivityTab(val label: String) { CHANGES("Changes"), REQUESTS("All requests") }
enum class StatusFilter(val label: String) { ALL("All"), ERRORS("Errors"), SUCCESS("Succeeded") }

data class ActivityRow(val entry: RequestHistoryEntity, val label: String)

data class ActivityUiState(
    val tab: ActivityTab = ActivityTab.CHANGES,
    val filter: StatusFilter = StatusFilter.ALL,
    val query: String = "",
    val rows: List<ActivityRow> = emptyList(),
    val loaded: Boolean = false
)

/**
 * Local activity (spec 68, 69): every Cloudflare API call this profile made from this device,
 * as metadata only. Labels come from Cloudflare's schema ("Create DNS Record"), so the
 * timeline reads as actions rather than raw paths. Server-side audit history is a separate
 * screen and is never mixed in here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(
    private val dao: RequestHistoryDao,
    private val profileId: String?,
    private val registryProvider: suspend () -> EndpointRegistry
) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()
    private val tab = MutableStateFlow(ActivityTab.CHANGES)

    init {
        viewModelScope.launch {
            val registry = registryProvider()
            val source = tab.flatMapLatest { t ->
                when {
                    profileId == null -> flowOf(emptyList())
                    t == ActivityTab.CHANGES -> dao.observeMutations(profileId, 500)
                    else -> dao.observe(profileId, 1000)
                }
            }
            combine(source, _uiState) { rows, state -> rows to state }.collect { (rows, state) ->
                val filtered = rows.filter { r ->
                    when (state.filter) {
                        StatusFilter.ALL -> true
                        StatusFilter.ERRORS -> r.statusCode == null || r.statusCode >= 400
                        StatusFilter.SUCCESS -> r.statusCode != null && r.statusCode in 200..399
                    }
                }.map { ActivityRow(it, registry.match(it.method, it.path)?.summary ?: "${it.method} ${it.path}") }
                    .filter { state.query.isBlank() || it.label.contains(state.query, true) || it.entry.path.contains(state.query, true) }
                if (filtered != state.rows || !state.loaded) _uiState.update { it.copy(rows = filtered, loaded = true) }
            }
        }
    }

    fun setTab(t: ActivityTab) { tab.value = t; _uiState.update { it.copy(tab = t, loaded = false) } }
    fun setFilter(f: StatusFilter) = _uiState.update { it.copy(filter = f) }
    fun setQuery(q: String) = _uiState.update { it.copy(query = q) }
    fun clear() {
        val p = profileId ?: return
        viewModelScope.launch { dao.clear(p) }
    }
}
