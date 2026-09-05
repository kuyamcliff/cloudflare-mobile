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
class AppLockPreferencesTest {

    private lateinit var preferences: AppLockPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        preferences = AppLockPreferences(context.getSharedPreferences("test_app_lock", android.content.Context.MODE_PRIVATE))
    }

    @Test
    fun `defaults to disabled with immediate timeout and no screenshot protection`() {
        assertThat(preferences.isEnabled()).isFalse()
        assertThat(preferences.lockTimeoutSeconds()).isEqualTo(AppLockPreferences.DEFAULT_TIMEOUT_SECONDS)
        assertThat(preferences.isScreenshotProtectionEnabled()).isFalse()
    }

    @Test
    fun `settings round-trip`() {
        preferences.setEnabled(true)
        preferences.setLockTimeoutSeconds(300)
        preferences.setScreenshotProtectionEnabled(true)

        assertThat(preferences.isEnabled()).isTrue()
        assertThat(preferences.lockTimeoutSeconds()).isEqualTo(300)
        assertThat(preferences.isScreenshotProtectionEnabled()).isTrue()
    }
}
