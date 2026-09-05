package dev.cfmobile.app.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Plain (unencrypted) prefs stand in for the real Android-Keystore-backed store here -
// Robolectric's JVM can't provide AndroidKeyStore. CredentialStore's own logic (get/put/
// remove/clear) is what's under test, not Android's encryption itself. Pinned to sdk=36
// because Robolectric doesn't yet ship shadows for the app's targetSdk (37).
@Config(application = Application::class, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class CredentialStoreTest {

    private lateinit var store: CredentialStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = CredentialStore(context.getSharedPreferences("test_credentials", android.content.Context.MODE_PRIVATE))
    }

    @Test
    fun `put then get returns the stored token`() {
        store.put("acct1", "cf-token-123")
        assertThat(store.get("acct1")).isEqualTo("cf-token-123")
    }

    @Test
    fun `get returns null for an unknown account id`() {
        assertThat(store.get("missing")).isNull()
    }

    @Test
    fun `remove deletes only the targeted credential`() {
        store.put("acct1", "tok1")
        store.put("acct2", "tok2")

        store.remove("acct1")

        assertThat(store.get("acct1")).isNull()
        assertThat(store.get("acct2")).isEqualTo("tok2")
    }

    @Test
    fun `clearAll wipes every credential`() {
        store.put("acct1", "tok1")
        store.put("acct2", "tok2")

        store.clearAll()

        assertThat(store.get("acct1")).isNull()
        assertThat(store.get("acct2")).isNull()
    }
}
