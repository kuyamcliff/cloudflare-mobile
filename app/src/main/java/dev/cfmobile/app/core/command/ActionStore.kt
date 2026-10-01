package dev.cfmobile.app.core.command

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A remembered action. Bodies are never persisted: they can hold secrets (a Worker secret's
 *  text, a service token's name and policy) and are cheap to type again. */
data class SavedAction(
    val method: String,
    val path: String,
    val title: String,
    val pathValues: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    /** The zone it ran in, for display ("Purge everything · example.com"). */
    val context: String? = null,
    val at: Long = 0
) {
    val key: String get() = "$method $path ${pathValues.toSortedMap()} ${query.toSortedMap()}"

    fun toDraft(): ActionDraft = ActionDraft(
        method = method, path = path, title = title, pathValues = pathValues, query = query,
        body = null, autoRun = method == "GET", source = ActionDraft.Source.HISTORY
    )
}

/**
 * Hands drafts to the action screen (a draft can be too large for a navigation route) and keeps
 * each profile's recent and pinned actions.
 */
class ActionStore(private val prefs: SharedPreferences?) {
    private val drafts = ConcurrentHashMap<String, ActionDraft>()
    private var profileId: String? = null
    private val _recents = MutableStateFlow<List<SavedAction>>(emptyList())
    private val _pins = MutableStateFlow<List<SavedAction>>(emptyList())
    val recents: StateFlow<List<SavedAction>> = _recents.asStateFlow()
    val pins: StateFlow<List<SavedAction>> = _pins.asStateFlow()

    fun put(draft: ActionDraft): String {
        val id = UUID.randomUUID().toString().take(12)
        if (drafts.size > 32) drafts.keys.take(drafts.size - 32).forEach { drafts.remove(it) }
        drafts[id] = draft
        return id
    }

    fun get(id: String): ActionDraft? = drafts[id]

    fun load(profileId: String?) {
        this.profileId = profileId
        _recents.value = read("recents.$profileId")
        _pins.value = read("pins.$profileId")
    }

    fun recordRun(action: SavedAction) {
        val list = (listOf(action) + _recents.value.filter { it.key != action.key }).take(MAX_RECENTS)
        _recents.value = list
        write("recents.$profileId", list)
    }

    fun isPinned(action: SavedAction): Boolean = _pins.value.any { it.key == action.key }

    fun togglePin(action: SavedAction) {
        val list = if (isPinned(action)) _pins.value.filter { it.key != action.key } else (_pins.value + action).takeLast(MAX_PINS)
        _pins.value = list
        write("pins.$profileId", list)
    }

    fun clearRecents() {
        _recents.value = emptyList()
        write("recents.$profileId", emptyList())
    }

    fun forgetProfile(id: String) {
        prefs?.edit { remove("recents.$id"); remove("pins.$id") }
        if (id == profileId) load(null)
    }

    fun clearAll() {
        prefs?.edit { clear() }
        drafts.clear()
        _recents.value = emptyList()
        _pins.value = emptyList()
    }

    private fun read(key: String): List<SavedAction> {
        if (profileId == null) return emptyList()
        val raw = prefs?.getString(key, null) ?: return emptyList()
        return (Json.parseOrNull(raw) as? List<*>).orEmpty().mapNotNull { decode(it as? Map<*, *>) }
    }

    private fun write(key: String, list: List<SavedAction>) {
        if (profileId == null) return
        prefs?.edit { putString(key, Json.stringify(list.map(::encode))) }
    }

    companion object {
        private const val MAX_RECENTS = 12
        private const val MAX_PINS = 24

        fun create(context: Context) = ActionStore(context.getSharedPreferences("cf_actions", Context.MODE_PRIVATE))

        fun encode(a: SavedAction): Map<String, Any?> = mapOf(
            "m" to a.method, "p" to a.path, "t" to a.title, "pv" to a.pathValues, "q" to a.query, "c" to a.context, "at" to a.at
        )

        fun decode(m: Map<*, *>?): SavedAction? {
            m ?: return null
            val method = m["m"] as? String ?: return null
            val path = m["p"] as? String ?: return null
            fun strings(v: Any?) = (v as? Map<*, *>).orEmpty().entries.associate { (k, x) -> k.toString() to x.toString() }
            return SavedAction(
                method, path, m["t"] as? String ?: path, strings(m["pv"]), strings(m["q"]), m["c"] as? String,
                (m["at"] as? Number)?.toLong() ?: 0
            )
        }
    }
}
