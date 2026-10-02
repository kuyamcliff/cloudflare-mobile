package dev.cfmobile.app.ui.login

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.R
import dev.cfmobile.app.ui.design.PrimaryButton
import dev.cfmobile.app.ui.design.Space
import dev.cfmobile.app.ui.design.StatusDot
import dev.cfmobile.app.ui.theme.CfTheme
import dev.cfmobile.app.ui.theme.StatusColors

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoggedIn: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showToken by remember { mutableStateOf(false) }
    val context = LocalContext.current
    dev.cfmobile.app.ui.components.SecureWindow()

    LaunchedEffect(state.success) {
        if (state.success) onLoggedIn()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Space.xl, vertical = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.lg)
    ) {
        Spacer(Modifier.height(Space.xxl))
        // The launcher mark on its own dark ground, so the light pointer reads in both themes.
        androidx.compose.foundation.layout.Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(androidx.compose.ui.graphics.Color(0xFF1B1A19)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(96.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text("Run Cloudflare from your phone", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Connect an API token. You'll see exactly what it allows, and can do anything it allows by typing what you want.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = state.token,
            onValueChange = viewModel::onTokenChange,
            label = { Text("API Token") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = if (showToken) FontFamily.Monospace else null),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { if (!state.isVerifying) viewModel.submit() }),
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (showToken) "Hide token" else "Show token")
                }
            },
            isError = state.error != null,
            supportingText = state.error?.let { e -> { Text(e) } },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.label,
            onValueChange = viewModel::onLabelChange,
            label = { Text("Label (optional)") },
            placeholder = { Text("e.g. Personal site") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        PrimaryButton(
            label = if (state.isVerifying) "Connecting" else "Connect",
            loading = state.isVerifying,
            enabled = state.token.isNotBlank(),
            onClick = viewModel::submit
        )

        state.step?.let { current ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.xs)) {
                ConnectStep.entries.forEach { step ->
                    val done = step.ordinal < current.ordinal || state.success
                    val active = step == current && !state.success
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        when {
                            done -> Icon(Icons.Filled.Check, null, tint = StatusColors.success, modifier = Modifier.size(18.dp))
                            active -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = CfTheme.colors.accent)
                            else -> StatusDot(MaterialTheme.colorScheme.outline, Modifier.padding(5.dp))
                        }
                        Text(
                            step.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.lg))
        TextButton(
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://dash.cloudflare.com/profile/api-tokens".toUri())) } },
            modifier = Modifier.align(Alignment.Start)
        ) { Text("Create a token in the Cloudflare dashboard") }
        Text(
            "Your token is encrypted with the Android Keystore and sent only to api.cloudflare.com. " +
                "Use an API token, not the legacy Global API Key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
