package dev.cfmobile.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.UUID

@JsonClass(generateAdapter = true)
data class SavedToken(
    val id: String,
    val label: String,
    val token: String,
    val email: String? = null
)

/**
 * Stores Cloudflare API tokens in the [SharedPreferences] it's given. In production that's
 * [EncryptedSharedPreferences] (see [create]), which encrypts both keys and values at rest
 * using a key held in the Android Keystore. Nothing here is ever uploaded anywhere: no cloud
 * backup (see data_extraction_rules.xml) and no network calls from this class - tokens only
 * leave the device as an Authorization header sent directly to api.cloudflare.com by
 * [dev.cfmobile.app.data.remote.AuthInterceptor].
 *
 * The storage logic here is independent of the encryption itself, so it takes a plain
 * [SharedPreferences] - that keeps this class testable on a regular JVM, where the real
 * Android Keystore isn't available, without weakening what production actually uses.
 */
class TokenStore(private val prefs: SharedPreferences) {

    private val moshi: Moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, SavedToken::class.java)
    private val listAdapter = moshi.adapter<List<SavedToken>>(listType)

    fun getAll(): List<SavedToken> {
        val raw = prefs.getString(KEY_TOKENS, null) ?: return emptyList()
        return try {
            listAdapter.fromJson(raw) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getActiveId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    fun getActive(): SavedToken? {
        val activeId = getActiveId() ?: return null
        return getAll().firstOrNull { it.id == activeId }
    }

    fun setActive(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    /** Adds a token and makes it the active one. Returns the generated entry. */
    fun add(label: String, token: String, email: String? = null): SavedToken {
        val entry = SavedToken(id = UUID.randomUUID().toString(), label = label, token = token, email = email)
        val updated = getAll() + entry
        saveAll(updated)
        setActive(entry.id)
        return entry
    }

    fun remove(id: String) {
        val remaining = getAll().filterNot { it.id == id }
        saveAll(remaining)
        if (getActiveId() == id) {
            prefs.edit().apply {
                if (remaining.isEmpty()) remove(KEY_ACTIVE_ID) else putString(KEY_ACTIVE_ID, remaining.first().id)
            }.apply()
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun saveAll(tokens: List<SavedToken>) {
        prefs.edit().putString(KEY_TOKENS, listAdapter.toJson(tokens)).apply()
    }

    companion object {
        private const val KEY_TOKENS = "tokens"
        private const val KEY_ACTIVE_ID = "active_id"

        /** Builds the real, Android-Keystore-backed encrypted store used by the app. */
        fun create(context: Context): TokenStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                "cf_secure_tokens",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return TokenStore(prefs)
        }
    }
}
