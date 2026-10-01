package dev.cfmobile.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NamedRef(val id: String, val name: String, val parentId: String? = null)

data class WorkingContext(
    val profileId: String? = null,
    val account: NamedRef? = null,
    val zone: NamedRef? = null,
    val recentAccounts: List<NamedRef> = emptyList(),
    val recentZones: List<NamedRef> = emptyList(),
    val favoriteZones: List<NamedRef> = emptyList()
)

/**
 * The account and zone the user is working in, per profile (spec 11, 12, 201). Only IDs and
 * names, never a credential. Screens read it for defaults, but every request still carries
 * its own explicit account and zone: a background job captures its target when created and
 * never follows later changes here.
 */
class ContextStore(private val prefs: SharedPreferences) {
    private val _state = MutableStateFlow(WorkingContext())
    val state: StateFlow<WorkingContext> = _state.asStateFlow()

    fun load(profileId: String?) {
        if (profileId == null) { _state.value = WorkingContext(); return }
        _state.value = WorkingContext(
            profileId = profileId,
            account = readRef("$profileId.account"),
            zone = readRef("$profileId.zone"),
            recentAccounts = readList("$profileId.recentAccounts"),
            recentZones = readList("$profileId.recentZones"),
            favoriteZones = readList("$profileId.favZones")
        )
    }

    fun selectAccount(ref: NamedRef) {
        val s = _state.value
        val profile = s.profileId ?: return
        val recents = (listOf(ref) + s.recentAccounts.filter { it.id != ref.id }).take(MAX_RECENTS)
        // A zone from another account would be a mismatched context; drop it.
        val zone = s.zone?.takeIf { it.parentId == null || it.parentId == ref.id }
        writeRef("$profile.account", ref)
        writeRef("$profile.zone", zone)
        writeList("$profile.recentAccounts", recents)
        _state.value = s.copy(account = ref, zone = zone, recentAccounts = recents)
    }

    fun selectZone(ref: NamedRef?) {
        val s = _state.value
        val profile = s.profileId ?: return
        writeRef("$profile.zone", ref)
        if (ref == null) { _state.value = s.copy(zone = null); return }
        val recents = (listOf(ref) + s.recentZones.filter { it.id != ref.id }).take(MAX_RECENTS)
        writeList("$profile.recentZones", recents)
        _state.value = s.copy(zone = ref, recentZones = recents)
    }

    fun toggleFavoriteZone(ref: NamedRef) {
        val s = _state.value
        val profile = s.profileId ?: return
        val favs = if (s.favoriteZones.any { it.id == ref.id }) s.favoriteZones.filter { it.id != ref.id } else s.favoriteZones + ref
        writeList("$profile.favZones", favs)
        _state.value = s.copy(favoriteZones = favs)
    }

    fun forgetProfile(profileId: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$profileId.") }.forEach { editor.remove(it) }
        editor.apply()
        if (_state.value.profileId == profileId) _state.value = WorkingContext()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _state.value = WorkingContext()
    }

    // Compact line format: id \u001F name \u001F parent, one ref per \u001E-separated record.
    private fun encode(ref: NamedRef) = listOf(ref.id, ref.name, ref.parentId.orEmpty()).joinToString(FIELD)
    private fun decode(raw: String): NamedRef? {
        val parts = raw.split(FIELD)
        if (parts.size < 2 || parts[0].isBlank()) return null
        return NamedRef(parts[0], parts[1], parts.getOrNull(2)?.takeIf { it.isNotBlank() })
    }
    private fun readRef(key: String) = prefs.getString(key, null)?.let(::decode)
    private fun writeRef(key: String, ref: NamedRef?) {
        prefs.edit().apply { if (ref == null) remove(key) else putString(key, encode(ref)) }.apply()
    }
    private fun readList(key: String) = prefs.getString(key, null)?.split(RECORD)?.mapNotNull(::decode).orEmpty()
    private fun writeList(key: String, refs: List<NamedRef>) = prefs.edit().putString(key, refs.joinToString(RECORD, transform = ::encode)).apply()

    companion object {
        private const val FIELD = "\u001F"
        private const val RECORD = "\u001E"
        private const val MAX_RECENTS = 8
        fun create(context: Context) = ContextStore(context.getSharedPreferences("cf_context", Context.MODE_PRIVATE))
    }
}
