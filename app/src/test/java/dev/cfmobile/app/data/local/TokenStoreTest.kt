package dev.cfmobile.app.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Uses the plain android.app.Application instead of CfApplication so Robolectric never
// constructs the real AppContainer (which builds an Android-Keystore-backed
// EncryptedSharedPreferences that Robolectric's JVM can't provide).
@Config(application = Application::class)
@RunWith(RobolectricTestRunner::class)
class TokenStoreTest {

    private lateinit var store: TokenStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = TokenStore(context.getSharedPreferences("test_tokens", android.content.Context.MODE_PRIVATE))
    }

    @Test
    fun `adding a token makes it active`() {
        val saved = store.add(label = "Personal", token = "abc123")

        assertThat(store.getAll()).containsExactly(saved)
        assertThat(store.getActiveId()).isEqualTo(saved.id)
        assertThat(store.getActive()?.token).isEqualTo("abc123")
    }

    @Test
    fun `switching active account changes getActive`() {
        val first = store.add(label = "Personal", token = "tok1")
        val second = store.add(label = "Work", token = "tok2")

        assertThat(store.getActiveId()).isEqualTo(second.id) // adding switches active

        store.setActive(first.id)
        assertThat(store.getActive()).isEqualTo(first)
    }

    @Test
    fun `removing the active account falls back to another remaining one`() {
        val first = store.add(label = "Personal", token = "tok1")
        val second = store.add(label = "Work", token = "tok2")

        store.remove(second.id)

        assertThat(store.getAll()).containsExactly(first)
        assertThat(store.getActiveId()).isEqualTo(first.id)
    }

    @Test
    fun `removing the only account clears the active id`() {
        val only = store.add(label = "Personal", token = "tok1")

        store.remove(only.id)

        assertThat(store.getAll()).isEmpty()
        assertThat(store.getActiveId()).isNull()
        assertThat(store.getActive()).isNull()
    }

    @Test
    fun `clearAll wipes every stored token`() {
        store.add(label = "Personal", token = "tok1")
        store.add(label = "Work", token = "tok2")

        store.clearAll()

        assertThat(store.getAll()).isEmpty()
        assertThat(store.getActive()).isNull()
    }
}
