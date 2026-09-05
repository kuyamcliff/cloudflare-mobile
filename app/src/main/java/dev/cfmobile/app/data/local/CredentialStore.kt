package dev.cfmobile.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The ONLY place a Cloudflare API token's secret value is persisted (PRD §83: "secret access
 * should be explicit"). Deliberately minimal - one encrypted key/value pair per account id,
 * nothing else. Non-secret account metadata (label, email) lives in [AccountMetadataStore]
 * instead, so most of the app's state never touches this class at all.
 */
class CredentialStore(private val prefs: SharedPreferences) {

    fun get(accountId: String): String? = prefs.getString(accountId, null)

    fun put(accountId: String, token: String) {
        prefs.edit().putString(accountId, token).apply()
    }

    fun remove(accountId: String) {
        prefs.edit().remove(accountId).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "cf_credentials"

        /** Builds the real, Android-Keystore-backed encrypted store used by the app. */
        fun create(context: Context): CredentialStore = CredentialStore(encryptedPrefs(context, PREFS_NAME))

        internal fun encryptedPrefs(context: Context, name: String): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                name,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
