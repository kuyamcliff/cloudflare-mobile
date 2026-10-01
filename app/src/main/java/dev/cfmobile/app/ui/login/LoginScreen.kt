package dev.cfmobile.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.theme.CfOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoggedIn: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showToken by remember { mutableStateOf(false) }
    dev.cfmobile.app.ui.components.SecureWindow()

    LaunchedEffect(state.success) {
        if (state.success) onLoggedIn()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Cloudflare Control", style = MaterialTheme.typography.titleLarge)
            Text("Connect your Cloudflare account", style = MaterialTheme.typography.titleMedium)
            Text(
                "Paste an API token created in the Cloudflare dashboard under My Profile, API Tokens. " +
                    "Your token stays on this device, encrypted with the Android Keystore, and is sent only to Cloudflare.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = state.label,
                onValueChange = viewModel::onLabelChange,
                label = { Text("Label (optional)") },
                placeholder = { Text("e.g. Personal site") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.token,
                onValueChange = viewModel::onTokenChange,
                label = { Text("API Token") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showToken) "Hide token" else "Show token"
                        )
                    }
                },
                isError = state.error != null,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = viewModel::submit,
                enabled = !state.isVerifying,
                colors = ButtonDefaults.buttonColors(containerColor = CfOrange),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(if (state.isVerifying) "Connecting" else "Connect")
            }

            state.step?.let { current ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ConnectStep.entries.forEach { step ->
                        val reached = step.ordinal <= current.ordinal
                        Text(
                            step.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Text(
                "Use an API token, not the legacy Global API Key. The app shows only what the token is allowed to do.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
