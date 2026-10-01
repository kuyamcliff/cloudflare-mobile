package dev.cfmobile.app.core.transfers

import android.content.Context
import android.content.SharedPreferences
import dev.cfmobile.app.data.local.CredentialStore

/**
 * R2 S3 credentials in their own Keystore-encrypted file, keyed by profile and account. Kept
 * apart from Cloudflare API tokens so neither can be mistaken for the other (spec 36, 138).
 */
class R2CredentialStore(private val prefs: SharedPreferences) {

    private fun key(profileId: String, accountId: String) = "$profileId|$accountId"

    fun get(profileId: String, accountId: String): R2S3Credentials? {
        val base = key(profileId, accountId)
        val id = prefs.getString("$base|id", null) ?: return null
        val secret = prefs.getString("$base|secret", null) ?: return null
        return R2S3Credentials(id, secret, prefs.getString("$base|jurisdiction", null))
    }

    fun has(profileId: String, accountId: String) = prefs.contains("${key(profileId, accountId)}|id")

    fun put(profileId: String, accountId: String, creds: R2S3Credentials) {
        val base = key(profileId, accountId)
        prefs.edit()
            .putString("$base|id", creds.accessKeyId.trim())
            .putString("$base|secret", creds.secretAccessKey.trim())
            .putString("$base|jurisdiction", creds.jurisdiction)
            .apply()
    }

    fun remove(profileId: String, accountId: String) {
        val base = key(profileId, accountId)
        prefs.edit().remove("$base|id").remove("$base|secret").remove("$base|jurisdiction").apply()
    }

    fun removeProfile(profileId: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$profileId|") }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        fun create(context: Context) = R2CredentialStore(CredentialStore.encryptedPrefs(context, "cf_r2_credentials"))
    }
}
