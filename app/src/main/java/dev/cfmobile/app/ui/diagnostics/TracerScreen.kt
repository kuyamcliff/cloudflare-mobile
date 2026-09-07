package dev.cfmobile.app.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.rules.RuleDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracerScreen(viewModel: TracerViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Tracer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                // Worth stating plainly: this is safe to point at production.
                "Cloudflare replays a request against its own pipeline and reports which rules matched. Nothing reaches your origin, so this is safe to run against a live site.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = uiState.url,
                onValueChange = viewModel::updateUrl,
                label = { Text("URL") },
                placeholder = { Text("https://example.com/checkout") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            RuleDropdown(
                label = "Method",
                options = TraceMethod.entries.map { it to it.value },
                selected = uiState.method,
                onSelect = viewModel::selectMethod
            )
            Button(
                onClick = viewModel::trace,
                enabled = !uiState.isTracing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isTracing) "Tracing…" else "Trace")
            }
            uiState.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (uiState.isTracing) {
                CircularProgressIndicator()
            }

            val result = uiState.result
            if (result != null) {
                HorizontalDivider()
                Text(
                    uiState.tracedUrl.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.statusCode?.let { code ->
                    Text("Cloudflare would answer $code", style = MaterialTheme.typography.titleSmall)
                }
                val matched = matchedSteps(result)
                Text(
                    if (matched.isEmpty()) {
                        "No rule matched - the request would pass straight through to your origin."
                    } else {
                        "${matched.size} rule${if (matched.size == 1) "" else "s"} matched"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                // Every step is listed, not just the matches: knowing a rule was evaluated and
                // didn't fire is often the answer.
                result.trace.forEach { step ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(traceStepLabel(step), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            traceStepSummary(step),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (step.matched == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        step.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(
                    // The tracer reports the pipeline's decision, not a rendered response.
                    "A trace shows which of Cloudflare's rules would fire. It doesn't fetch the page, so there is no response body to inspect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
