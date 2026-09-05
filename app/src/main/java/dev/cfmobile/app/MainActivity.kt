package dev.cfmobile.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.cfmobile.app.ui.navigation.CfNavHost
import dev.cfmobile.app.ui.navigation.Routes
import dev.cfmobile.app.ui.theme.CfMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as CfApplication).container
        val startDestination = if (container.tokenStore.getActive() != null) Routes.ZONES else Routes.LOGIN

        setContent {
            CfMobileTheme {
                CfApp(container, startDestination)
            }
        }
    }
}

@Composable
private fun CfApp(container: AppContainer, startDestination: String) {
    Surface(modifier = Modifier.fillMaxSize()) {
        CfNavHost(container = container, startDestination = startDestination)
    }
}
