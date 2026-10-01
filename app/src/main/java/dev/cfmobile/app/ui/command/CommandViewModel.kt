package dev.cfmobile.app.ui.command

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.command.ActionDraft
import dev.cfmobile.app.core.command.AiPlanner
import dev.cfmobile.app.core.command.CommandContext
import dev.cfmobile.app.core.command.CommandEngine
import dev.cfmobile.app.core.command.CommandHit
import dev.cfmobile.app.core.command.CommandResults
import dev.cfmobile.app.core.command.ResourceRef
import dev.cfmobile.app.core.command.SavedAction
import dev.cfmobile.app.data.local.NamedRef
import dev.cfmobile.app.data.local.WorkingContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PlannerState {
    data object Idle : PlannerState()
    data object Planning : PlannerState()
    data class Failed(val message: String) : PlannerState()
}

/** What the screen should do next; consumed once. */
sealed class CommandEvent {
    data class Open(val draft: ActionDraft) : CommandEvent()
    data class Navigate(val route: String) : CommandEvent()
}

data class CommandUiState(
    val query: String = "",
    val results: CommandResults = CommandResults.EMPTY,
    val context: WorkingContext = WorkingContext(),
    val zones: List<NamedRef> = emptyList(),
    val accounts: List<NamedRef> = emptyList(),
    val pins: List<SavedAction> = emptyList(),
    val recents: List<SavedAction> = emptyList(),
    val planner: PlannerState = PlannerState.Idle,
    val ready: Boolean = false,
    val event: CommandEvent? = null
)

/**
 * The command surface: one field that searches everything and turns sentences into reviewed
 * requests. Local matching runs on every keystroke; Workers AI only runs when asked.
 */
class CommandViewModel(
    private val engineProvider: suspend () -> CommandEngine,
    private val planner: AiPlanner,
    private val contextFlow: StateFlow<WorkingContext>,
    private val pinsFlow: StateFlow<List<SavedAction>>,
    private val recentsFlow: StateFlow<List<SavedAction>>,
    private val loadZones: suspend (accountId: String?) -> List<NamedRef>,
    private val loadAccounts: suspend () -> List<NamedRef>,
    private val loadResources: suspend (accountId: String) -> List<ResourceRef>,
    private val selectAccount: (NamedRef) -> Unit,
    private val selectZone: (NamedRef?) -> Unit,
    initialQuery: String = ""
) : ViewModel() {
    private val _ui = MutableStateFlow(CommandUiState(query = initialQuery))
    val ui: StateFlow<CommandUiState> = _ui.asStateFlow()
    private var engine: CommandEngine? = null
    private var resources: List<ResourceRef> = emptyList()
    private var searchJob: Job? = null
    private var planJob: Job? = null

    init {
        viewModelScope.launch {
            combine(contextFlow, pinsFlow, recentsFlow) { c, p, r -> Triple(c, p, r) }.collect { (c, p, r) ->
                _ui.update { it.copy(context = c, pins = p, recents = r) }
                search()
            }
        }
        viewModelScope.launch {
            engine = engineProvider()
            _ui.update { it.copy(ready = true) }
            search()
        }
        viewModelScope.launch {
            val accounts = runCatching { loadAccounts() }.getOrDefault(emptyList())
            _ui.update { it.copy(accounts = accounts) }
        }
        reloadAccountData()
    }

    private fun reloadAccountData() {
        viewModelScope.launch {
            val account = contextFlow.value.account?.id
            val zones = runCatching { loadZones(account) }.getOrDefault(emptyList())
            _ui.update { it.copy(zones = zones) }
            search()
            if (account != null) {
                resources = runCatching { loadResources(account) }.getOrDefault(emptyList())
                search()
            }
        }
    }

    fun setQuery(q: String) {
        _ui.update { it.copy(query = q, planner = if (it.planner is PlannerState.Planning) it.planner else PlannerState.Idle) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(40)
            search()
        }
    }

    private suspend fun search() {
        val e = engine ?: return
        val s = _ui.value
        val ctx = commandContext(s)
        val results = withContext(Dispatchers.Default) { e.search(s.query, ctx) }
        if (_ui.value.query == s.query) _ui.update { it.copy(results = results) }
    }

    private fun commandContext(s: CommandUiState): CommandContext {
        val c = contextFlow.value
        val zones = (s.zones + c.recentZones + c.favoriteZones + listOfNotNull(c.zone)).distinctBy { it.id }
        return CommandContext(account = c.account, zone = c.zone, zones = zones, resources = resources)
    }

    /** Enter: act on a confident match, otherwise ask Workers AI. */
    fun submit() {
        val s = _ui.value
        if (s.query.isBlank()) return
        val best = s.results.best
        when {
            best != null && best.score >= CommandEngine.CONFIDENT -> choose(best)
            s.results.isEmpty || s.results.suggestPlanner -> plan()
            best != null -> choose(best)
        }
    }

    fun choose(hit: CommandHit) {
        when (hit) {
            is CommandHit.Run -> emit(CommandEvent.Open(hit.draft))
            is CommandHit.Go -> {
                hit.selectZone?.let(selectZone)
                emit(CommandEvent.Navigate(hit.route))
            }
            is CommandHit.Resource -> {
                hit.zone?.let(selectZone)
                when {
                    hit.ref.route != null -> emit(CommandEvent.Navigate(hit.ref.route))
                    hit.ref.draft != null -> emit(CommandEvent.Open(hit.ref.draft))
                }
            }
        }
    }

    fun openSaved(a: SavedAction) = emit(CommandEvent.Open(a.toDraft()))

    fun plan() {
        val s = _ui.value
        if (s.query.isBlank() || s.planner is PlannerState.Planning) return
        _ui.update { it.copy(planner = PlannerState.Planning) }
        planJob = viewModelScope.launch {
            val ctx = commandContext(_ui.value).let { c -> s.results.mentionedZone?.let { c.copy(zone = it) } ?: c }
            when (val outcome = planner.plan(s.query, ctx, s.results.actions + s.results.operations)) {
                is AiPlanner.Outcome.Planned -> {
                    _ui.update { it.copy(planner = PlannerState.Idle) }
                    emit(CommandEvent.Open(outcome.draft))
                }
                is AiPlanner.Outcome.Failed -> _ui.update { it.copy(planner = PlannerState.Failed(outcome.message)) }
            }
        }
    }

    fun cancelPlan() {
        planJob?.cancel()
        _ui.update { it.copy(planner = PlannerState.Idle) }
    }

    fun switchAccount(a: NamedRef) {
        selectAccount(a)
        _ui.update { it.copy(zones = emptyList()) }
        // contextFlow updates synchronously in the store, so reload against the new account.
        reloadAccountData()
    }

    fun switchZone(z: NamedRef?) = selectZone(z)

    private fun emit(e: CommandEvent) = _ui.update { it.copy(event = e) }
    fun consumeEvent() = _ui.update { it.copy(event = null) }
}
