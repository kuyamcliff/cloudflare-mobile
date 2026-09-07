package dev.cfmobile.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.UUID

/**
 * Composes [CredentialStore] (secret) and [AccountMetadataStore] (non-secret) into the single
 * API the rest of the app uses for managing locally-connected Cloudflare accounts. Splitting
 * the two stores means everything except [getActiveToken] and [AuthRepository]'s token
 * verification path only ever sees [AccountSummary] - the raw token can't leak into UI state
 * by accident (PRD §83).
 */
class AccountStore(
    private val credentials: CredentialStore,
    private val metadata: AccountMetadataStore
) {
    fun getAll(): List<AccountSummary> = metadata.getAll()

    fun getActiveId(): String? = metadata.getActiveId()

    fun getActive(): AccountSummary? {
        val id = getActiveId() ?: return null
        return getAll().firstOrNull { it.id == id }
    }

    /** The only accessor that returns a raw secret. Callers: [AuthRepository]'s token
     *  provider for [dev.cfmobile.app.data.remote.AuthInterceptor], and nothing UI-facing. */
    fun getActiveToken(): String? = getActiveId()?.let { credentials.get(it) }

    fun setActive(id: String) = metadata.setActive(id)

    /** Adds an account and makes it active. Returns the non-secret summary. */
    fun add(label: String, token: String, email: String? = null): AccountSummary {
        val id = UUID.randomUUID().toString()
        credentials.put(id, token)
        metadata.add(AccountMetadata(id = id, label = label, email = email))
        metadata.setActive(id)
        return AccountSummary(id = id, label = label, email = email)
    }

    fun remove(id: String) {
        credentials.remove(id)
        metadata.remove(id)
    }

    fun clearAll() {
        credentials.clearAll()
        metadata.clearAll()
    }

    companion object {
        /** Builds the real stores and migrates any pre-split-storage data before returning. */
        fun create(context: Context): AccountStore {
            val credentialStore = CredentialStore.create(context)
            val metadataStore = AccountMetadataStore.create(context)
            LegacyTokenStoreMigration.migrateIfNeeded(context, credentialStore, metadataStore)
            return AccountStore(credentialStore, metadataStore)
        }
    }
}

/**
 * One-time migration from the original `TokenStore` shape, which kept a single encrypted
 * blob per account containing id, label, token, AND email together (PRD §83: "migration from
 * existing EncryptedSharedPreferences must be supported"). Runs once - after migrating, the
 * legacy blob is cleared so this never re-runs or leaves the secret sitting in two places.
 *
 * [migrate] takes the legacy [SharedPreferences] as a parameter rather than constructing it
 * itself so this logic is unit-testable with a plain prefs instance - only [migrateIfNeeded]
 * (the production entry point) needs the real Android-Keystore-backed store.
 */
internal object LegacyTokenStoreMigration {

    @JsonClass(generateAdapter = true)
    internal data class LegacySavedToken(
        val id: String,
        val label: String,
        val token: String,
        val email: String? = null
    )

    private const val LEGACY_PREFS_NAME = "cf_secure_tokens"
    private const val LEGACY_KEY_TOKENS = "tokens"
    private const val LEGACY_KEY_ACTIVE_ID = "active_id"

    fun migrateIfNeeded(context: Context, credentials: CredentialStore, metadata: AccountMetadataStore) {
        val legacyPrefs = try {
            CredentialStore.encryptedPrefs(context, LEGACY_PREFS_NAME)
        } catch (e: Exception) {
            return
        }
        migrate(legacyPrefs, credentials, metadata)
    }

    internal fun migrate(legacyPrefs: SharedPreferences, credentials: CredentialStore, metadata: AccountMetadataStore) {
        // Only ever a fresh-install concern: if the new store already has data, either
        // migration already ran or this is a new-format install with nothing to migrate.
        if (metadata.getAllMetadata().isNotEmpty()) return

        val rawTokens = legacyPrefs.getString(LEGACY_KEY_TOKENS, null) ?: return

        val moshi = Moshi.Builder().build()
        val listType = Types.newParameterizedType(List::class.java, LegacySavedToken::class.java)
        val legacyTokens = try {
            moshi.adapter<List<LegacySavedToken>>(listType).fromJson(rawTokens)
        } catch (e: Exception) {
            null
        } ?: return

        if (legacyTokens.isEmpty()) return

        legacyTokens.forEach { legacy ->
            credentials.put(legacy.id, legacy.token)
            metadata.add(AccountMetadata(id = legacy.id, label = legacy.label, email = legacy.email))
        }
        legacyPrefs.getString(LEGACY_KEY_ACTIVE_ID, null)?.let { metadata.setActive(it) }

        // Clear the legacy blob now that everything's been carried over - a token must never
        // be left sitting in two stores at once.
        legacyPrefs.edit().clear().apply()
    }
}
