package dev.cfmobile.app.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Pinned to sdk=36 because Robolectric doesn't yet ship shadows for the app's targetSdk (37).
@Config(application = Application::class, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class AccountStoreTest {

    private lateinit var credentials: CredentialStore
    private lateinit var metadata: AccountMetadataStore
    private lateinit var store: AccountStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        credentials = CredentialStore(context.getSharedPreferences("test_credentials", android.content.Context.MODE_PRIVATE))
        metadata = AccountMetadataStore(context.getSharedPreferences("test_metadata", android.content.Context.MODE_PRIVATE))
        store = AccountStore(credentials, metadata)
    }

    @Test
    fun `add stores the secret separately from the summary the UI sees`() {
        val summary = store.add(label = "Personal", token = "cf-token-123")

        assertThat(summary).isEqualTo(AccountSummary(id = summary.id, label = "Personal", email = null))
        assertThat(store.getActiveToken()).isEqualTo("cf-token-123")
        assertThat(store.getAll()).containsExactly(summary)
    }

    @Test
    fun `adding an account makes it active`() {
        val summary = store.add(label = "Personal", token = "tok1")
        assertThat(store.getActiveId()).isEqualTo(summary.id)
        assertThat(store.getActive()).isEqualTo(summary)
    }

    @Test
    fun `removing an account deletes both its credential and its metadata`() {
        val summary = store.add(label = "Personal", token = "tok1")

        store.remove(summary.id)

        assertThat(store.getAll()).isEmpty()
        assertThat(store.getActiveToken()).isNull()
    }

    @Test
    fun `clearAll wipes credentials and metadata together`() {
        store.add(label = "Personal", token = "tok1")
        store.add(label = "Work", token = "tok2")

        store.clearAll()

        assertThat(store.getAll()).isEmpty()
        assertThat(store.getActiveToken()).isNull()
    }

    @Test
    fun `migrate carries over accounts from the pre-split legacy blob, then clears it`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val legacyPrefs = context.getSharedPreferences("test_legacy", android.content.Context.MODE_PRIVATE)

        val moshi = Moshi.Builder().build()
        val listType = Types.newParameterizedType(List::class.java, LegacyTokenStoreMigration.LegacySavedToken::class.java)
        val legacyJson = moshi.adapter<List<LegacyTokenStoreMigration.LegacySavedToken>>(listType).toJson(
            listOf(
                LegacyTokenStoreMigration.LegacySavedToken(id = "legacy1", label = "Old Account", token = "legacy-token", email = "old@example.com")
            )
        )
        legacyPrefs.edit().putString("tokens", legacyJson).putString("active_id", "legacy1").apply()

        LegacyTokenStoreMigration.migrate(legacyPrefs, credentials, metadata)

        assertThat(store.getAll()).containsExactly(AccountSummary(id = "legacy1", label = "Old Account", email = "old@example.com"))
        assertThat(store.getActiveToken()).isEqualTo("legacy-token")
        assertThat(store.getActiveId()).isEqualTo("legacy1")
        // The legacy blob is cleared so the secret isn't left sitting in two stores.
        assertThat(legacyPrefs.getString("tokens", null)).isNull()
    }

    @Test
    fun `migrate is a no-op when the new store already has accounts`() {
        store.add(label = "Already migrated", token = "tok1")

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val legacyPrefs = context.getSharedPreferences("test_legacy_2", android.content.Context.MODE_PRIVATE)
        legacyPrefs.edit().putString("tokens", """[{"id":"legacy1","label":"Old","token":"legacy-token"}]""").apply()

        LegacyTokenStoreMigration.migrate(legacyPrefs, credentials, metadata)

        assertThat(store.getAll()).hasSize(1)
        assertThat(store.getActiveToken()).isEqualTo("tok1")
        // Untouched - migration bailed out before reading it.
        assertThat(legacyPrefs.getString("tokens", null)).isNotNull()
    }

    @Test
    fun `migrate is a no-op when there is no legacy blob`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val emptyLegacyPrefs = context.getSharedPreferences("test_legacy_empty", android.content.Context.MODE_PRIVATE)

        LegacyTokenStoreMigration.migrate(emptyLegacyPrefs, credentials, metadata)

        assertThat(store.getAll()).isEmpty()
    }
}
