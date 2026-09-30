package dev.cfmobile.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/** Non-secret per-account data: a local label, and whichever account is active. Deliberately
 *  never carries a token value - see [CredentialStore] and PRD §83. */
@JsonClass(generateAdapter = true)
data class AccountMetadata(
    val id: String,
    val label: String,
    val email: String? = null,
    /** Cloudflare's token ID (not the secret), when verification returned one. */
    val tokenId: String? = null,
    /** First 12 hex chars of SHA-256(secret): identifies a token locally without revealing it
     *  and is never used as a key (spec 108). */
    val fingerprint: String? = null,
    val expiresOn: String? = null,
    val lastVerifiedAt: Long? = null,
    val createdAt: Long? = null
)

/** What the UI is ever handed for an account - a [CredentialStore] token never reaches this
 *  far (PRD §83: "tokens never exposed to UI state unnecessarily"). */
data class AccountSummary(
    val id: String,
    val label: String,
    val email: String? = null,
    val tokenId: String? = null,
    val fingerprint: String? = null,
    val expiresOn: String? = null,
    val lastVerifiedAt: Long? = null
)

private fun AccountMetadata.toSummary() = AccountSummary(id, label, email, tokenId, fingerprint, expiresOn, lastVerifiedAt)

class AccountMetadataStore(private val prefs: SharedPreferences) {

    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, AccountMetadata::class.java)
    private val listAdapter = moshi.adapter<List<AccountMetadata>>(listType)

    fun getAll(): List<AccountSummary> = getAllMetadata().map { it.toSummary() }

    fun getActiveId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    fun setActive(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun add(metadata: AccountMetadata) {
        saveAll(getAllMetadata() + metadata)
    }

    fun rename(id: String, label: String) {
        saveAll(getAllMetadata().map { if (it.id == id) it.copy(label = label) else it })
    }

    fun setFingerprint(id: String, fingerprint: String) {
        saveAll(getAllMetadata().map { if (it.id == id) it.copy(fingerprint = fingerprint) else it })
    }

    fun recordVerification(id: String, tokenId: String?, expiresOn: String?, at: Long) {
        saveAll(getAllMetadata().map {
            if (it.id == id) it.copy(tokenId = tokenId ?: it.tokenId, expiresOn = expiresOn, lastVerifiedAt = at) else it
        })
    }

    fun remove(id: String) {
        val remaining = getAllMetadata().filterNot { it.id == id }
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

    internal fun getAllMetadata(): List<AccountMetadata> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            listAdapter.fromJson(raw) ?: emptyList()
        } catch (e: java.io.IOException) {
            emptyList()
        } catch (e: com.squareup.moshi.JsonDataException) {
            emptyList()
        }
    }

    private fun saveAll(accounts: List<AccountMetadata>) {
        prefs.edit().putString(KEY_ACCOUNTS, listAdapter.toJson(accounts)).apply()
    }

    companion object {
        private const val PREFS_NAME = "cf_account_metadata"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_ACTIVE_ID = "active_id"

        fun create(context: Context): AccountMetadataStore =
            AccountMetadataStore(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}
