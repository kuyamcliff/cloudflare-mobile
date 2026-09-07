package dev.cfmobile.app.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Pinned to sdk=36 because Robolectric doesn't yet ship shadows for the app's targetSdk (37).
@Config(application = Application::class, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class AccountMetadataStoreTest {

    private lateinit var store: AccountMetadataStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = AccountMetadataStore(context.getSharedPreferences("test_account_metadata", android.content.Context.MODE_PRIVATE))
    }

    @Test
    fun `adding metadata never carries a token field`() {
        store.add(AccountMetadata(id = "a1", label = "Personal", email = "me@example.com"))

        val summary = store.getAll().single()
        assertThat(summary).isEqualTo(AccountSummary(id = "a1", label = "Personal", email = "me@example.com"))
    }

    @Test
    fun `setActive and getActiveId round-trip`() {
        store.add(AccountMetadata(id = "a1", label = "Personal"))
        store.setActive("a1")

        assertThat(store.getActiveId()).isEqualTo("a1")
    }

    @Test
    fun `removing the active account falls back to another remaining one`() {
        store.add(AccountMetadata(id = "a1", label = "Personal"))
        store.add(AccountMetadata(id = "a2", label = "Work"))
        store.setActive("a2")

        store.remove("a2")

        assertThat(store.getAll()).containsExactly(AccountSummary(id = "a1", label = "Personal"))
        assertThat(store.getActiveId()).isEqualTo("a1")
    }

    @Test
    fun `removing the only account clears the active id`() {
        store.add(AccountMetadata(id = "a1", label = "Personal"))
        store.setActive("a1")

        store.remove("a1")

        assertThat(store.getAll()).isEmpty()
        assertThat(store.getActiveId()).isNull()
    }

    @Test
    fun `clearAll wipes every account`() {
        store.add(AccountMetadata(id = "a1", label = "Personal"))
        store.add(AccountMetadata(id = "a2", label = "Work"))

        store.clearAll()

        assertThat(store.getAll()).isEmpty()
        assertThat(store.getActiveId()).isNull()
    }
}
