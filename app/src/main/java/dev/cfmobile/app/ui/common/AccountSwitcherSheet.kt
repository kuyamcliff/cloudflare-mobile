package dev.cfmobile.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cfmobile.app.data.local.AccountSummary

/** PRD §6.3: an unobtrusive context control that opens a sheet listing every connected
 *  account, so switching context never requires a trip through Settings. Shared by the
 *  Dashboard and Zones screens - anywhere the active account is shown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherSheet(
    accounts: List<AccountSummary>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Switch account",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(20.dp, 8.dp, 20.dp, 12.dp)
        )
        LazyColumn {
            items(accounts, key = { it.id }) { account ->
                ListItem(
                    headlineContent = { Text(account.label) },
                    supportingContent = account.email?.let { { Text(it) } },
                    leadingContent = {
                        Icon(
                            if (account.id == activeId) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (account.id == activeId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.selectable(selected = account.id == activeId, onClick = { onSelect(account.id) })
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Add account") },
                    leadingContent = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onAddAccount)
                )
            }
        }
    }
}
