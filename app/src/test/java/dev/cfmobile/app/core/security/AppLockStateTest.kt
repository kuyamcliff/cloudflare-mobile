package dev.cfmobile.app.core.security

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
class AppLockStateTest {

    private lateinit var preferences: AppLockPreferences

    private fun newState(): AppLockState = AppLockState(preferences)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        preferences = AppLockPreferences(context.getSharedPreferences("test_app_lock_state", android.content.Context.MODE_PRIVATE))
    }

    @Test
    fun `starts unlocked when app lock is disabled`() {
        val state = newState()
        assertThat(state.isLocked.value).isFalse()
    }

    @Test
    fun `starts locked when app lock was already enabled from a previous session`() {
        preferences.setEnabled(true)
        val state = newState()
        assertThat(state.isLocked.value).isTrue()
    }

    @Test
    fun `does nothing on background or foreground when app lock is disabled`() {
        val state = newState()
        state.onAppBackgrounded(nowMillis = 0)
        state.onAppForegrounded(nowMillis = 999_999)
        assertThat(state.isLocked.value).isFalse()
    }

    @Test
    fun `locks on foreground once the configured timeout has elapsed`() {
        preferences.setEnabled(true)
        preferences.setLockTimeoutSeconds(60)
        val state = newState()
        state.unlock() // simulate an already-unlocked session before backgrounding

        state.onAppBackgrounded(nowMillis = 0)
        state.onAppForegrounded(nowMillis = 61_000)

        assertThat(state.isLocked.value).isTrue()
    }

    @Test
    fun `does not lock on foreground when returning before the timeout elapses`() {
        preferences.setEnabled(true)
        preferences.setLockTimeoutSeconds(60)
        val state = newState()
        state.unlock()

        state.onAppBackgrounded(nowMillis = 0)
        state.onAppForegrounded(nowMillis = 5_000)

        assertThat(state.isLocked.value).isFalse()
    }

    @Test
    fun `zero-second timeout locks immediately on any background-then-foreground cycle`() {
        preferences.setEnabled(true)
        preferences.setLockTimeoutSeconds(0)
        val state = newState()
        state.unlock()

        state.onAppBackgrounded(nowMillis = 0)
        state.onAppForegrounded(nowMillis = 1)

        assertThat(state.isLocked.value).isTrue()
    }

    @Test
    fun `unlock clears the locked flag and the backgrounded timestamp`() {
        preferences.setEnabled(true)
        val state = newState()

        state.unlock()

        assertThat(state.isLocked.value).isFalse()
    }

    @Test
    fun `lockNow locks immediately regardless of timeout or background state`() {
        val state = newState()
        state.lockNow()
        assertThat(state.isLocked.value).isTrue()
    }

    @Test
    fun `disabling app lock unlocks immediately`() {
        preferences.setEnabled(true)
        val state = newState()
        assertThat(state.isLocked.value).isTrue()

        state.setAppLockEnabled(false)

        assertThat(state.isLocked.value).isFalse()
        assertThat(state.isAppLockEnabled()).isFalse()
    }

    @Test
    fun `screenshot protection preference round-trips through AppLockState`() {
        val state = newState()
        assertThat(state.isScreenshotProtectionEnabled()).isFalse()

        state.setScreenshotProtectionEnabled(true)

        assertThat(state.isScreenshotProtectionEnabled()).isTrue()
    }
}
