package dev.cfmobile.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.core.security.AppLockState
import dev.cfmobile.app.core.security.BiometricAuthenticator
import dev.cfmobile.app.ui.navigation.CfNavHost
import dev.cfmobile.app.ui.navigation.Routes
import dev.cfmobile.app.ui.security.LockScreen
import dev.cfmobile.app.ui.theme.CfMobileTheme

// FragmentActivity (not plain ComponentActivity) because BiometricPrompt hosts an invisible
// Fragment internally to survive configuration changes - PRD §48 wants the platform's own
// biometric UI, and this is the only Activity base it works with.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as CfApplication).container

        if (container.appLockState.isScreenshotProtectionEnabled()) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        val startDestination = if (container.accountStore.getActive() != null) Routes.ZONES else Routes.LOGIN
        val authenticator = BiometricAuthenticator(this)

        setContent {
            CfMobileTheme {
                CfApp(container, startDestination, authenticator)
            }
        }
    }
}

@Composable
private fun CfApp(container: AppContainer, startDestination: String, authenticator: BiometricAuthenticator) {
    val isLocked by container.appLockState.isLocked.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        if (isLocked) {
            LockScreenHost(container.appLockState, authenticator)
        } else {
            CfNavHost(container = container, startDestination = startDestination, authenticator = authenticator)
        }
    }
}

@Composable
private fun LockScreenHost(appLockState: AppLockState, authenticator: BiometricAuthenticator) {
    var error by remember { mutableStateOf<String?>(null) }

    LockScreen(
        availability = authenticator.availability(),
        error = error,
        onUnlockClick = {
            error = null
            authenticator.authenticate(
                onSuccess = { appLockState.unlock() },
                onError = { message -> error = message },
                onCancel = { /* stay locked; user can tap Unlock again */ }
            )
        }
    )
}
