package dev.cfmobile.app.ui.billing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.ReadOnlyListRow
import dev.cfmobile.app.ui.common.RefreshableStateContent
import dev.cfmobile.app.ui.common.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(viewModel: BillingViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Billing & Plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "This app never changes a plan or touches payment details. Anything that moves money happens in Cloudflare's own dashboard, behind its confirmation flow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { openBillingDashboard(context) },
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Text("Manage billing in the dashboard", modifier = Modifier.padding(start = 8.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            RefreshableStateContent(
                state = uiState.subscriptions,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh
            ) { subscriptions ->
                if (subscriptions.isEmpty()) {
                    EmptyState("No paid subscriptions on this account")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(subscriptions, key = { it.id }) { subscription ->
                            ReadOnlyListRow(
                                icon = Icons.Filled.CreditCard,
                                title = subscriptionTitle(subscription),
                                subtitle = subscriptionPriceLabel(subscription),
                                detail = subscription.currentPeriodEnd?.let { "Current period ends $it" },
                                trailing = {
                                    subscription.state?.let { state ->
                                        StatusPill(
                                            state.replaceFirstChar { it.uppercase() },
                                            if (state.equals("active", ignoreCase = true)) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}

private fun openBillingDashboard(context: Context) {
    val uri = Uri.parse("https://dash.cloudflare.com/?to=/:account/billing")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser app available", Toast.LENGTH_SHORT).show()
    }
}
