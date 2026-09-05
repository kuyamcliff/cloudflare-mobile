package dev.cfmobile.app.ui.ssl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.repository.StringSetting
import dev.cfmobile.app.ui.common.OptionRow
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.ToggleRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SslScreen(viewModel: SslViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savingKey by viewModel.savingKey.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSL/TLS") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        StateContent(state = state, onRetry = viewModel::load) { settings ->
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
                OptionRow(
                    title = "SSL/TLS encryption mode",
                    subtitle = "How Cloudflare connects to your origin server",
                    currentValue = settings.ssl,
                    isSaving = savingKey == StringSetting.SSL,
                    options = listOf(
                        "off" to "Off",
                        "flexible" to "Flexible",
                        "full" to "Full",
                        "strict" to "Full (strict)"
                    ),
                    onSelect = { viewModel.update(StringSetting.SSL, it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                ToggleRow(
                    title = "Always Use HTTPS",
                    subtitle = "Redirect all requests to HTTPS",
                    checked = settings.alwaysUseHttps == "on",
                    isSaving = savingKey == StringSetting.ALWAYS_USE_HTTPS,
                    onToggle = { viewModel.update(StringSetting.ALWAYS_USE_HTTPS, if (it) "on" else "off") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                OptionRow(
                    title = "Minimum TLS version",
                    currentValue = settings.minTlsVersion,
                    isSaving = savingKey == StringSetting.MIN_TLS_VERSION,
                    options = listOf("1.0" to "TLS 1.0", "1.1" to "TLS 1.1", "1.2" to "TLS 1.2", "1.3" to "TLS 1.3"),
                    onSelect = { viewModel.update(StringSetting.MIN_TLS_VERSION, it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                ToggleRow(
                    title = "Automatic HTTPS Rewrites",
                    subtitle = "Rewrite HTTP links to HTTPS where safe",
                    checked = settings.automaticHttpsRewrites == "on",
                    isSaving = savingKey == StringSetting.AUTOMATIC_HTTPS_REWRITES,
                    onToggle = { viewModel.update(StringSetting.AUTOMATIC_HTTPS_REWRITES, if (it) "on" else "off") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                OptionRow(
                    title = "Security level",
                    subtitle = "Sensitivity of the Cloudflare challenge page",
                    currentValue = settings.securityLevel,
                    isSaving = savingKey == StringSetting.SECURITY_LEVEL,
                    options = listOf(
                        "off" to "Off",
                        "essentially_off" to "Essentially off",
                        "low" to "Low",
                        "medium" to "Medium",
                        "high" to "High",
                        "under_attack" to "I'm Under Attack"
                    ),
                    onSelect = { viewModel.update(StringSetting.SECURITY_LEVEL, it) }
                )
            }
        }
    }
}
