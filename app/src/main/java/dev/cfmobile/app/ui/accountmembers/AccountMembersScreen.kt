package dev.cfmobile.app.ui.accountmembers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.data.remote.dto.AccountMember
import dev.cfmobile.app.ui.common.EmptyState
import dev.cfmobile.app.ui.common.StateContent
import dev.cfmobile.app.ui.common.StatusPill
import dev.cfmobile.app.ui.theme.StatusAmber
import dev.cfmobile.app.ui.theme.StatusGreen
import dev.cfmobile.app.ui.theme.StatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountMembersScreen(viewModel: AccountMembersViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Members & Roles") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openInviteForm) {
                Icon(Icons.Filled.Add, contentDescription = "Invite member")
            }
        }
    ) { padding ->
        StateContent(state = uiState.members, onRetry = viewModel::refresh) { members ->
            if (members.isEmpty()) {
                EmptyState("No members found", Modifier.padding(padding))
            } else {
                LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(members, key = { it.id }) { member ->
                        MemberRow(member = member, isRemoving = uiState.removingId == member.id, onRemove = { viewModel.remove(member) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }

    uiState.form?.let { form ->
        InviteMemberSheet(
            form = form,
            roles = uiState.roles,
            onDismiss = viewModel::closeInviteForm,
            onEmailChange = { email -> viewModel.updateForm { it.copy(email = email) } },
            onToggleRole = viewModel::toggleRole,
            onSave = viewModel::invite
        )
    }
}

@Composable
private fun MemberRow(member: AccountMember, isRemoving: Boolean, onRemove: () -> Unit) {
    var confirmRemove by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(member.user.email, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(member.status.replaceFirstChar { it.uppercase() }, memberStatusColor(member.status))
                if (member.roles.isNotEmpty()) {
                    Text(
                        member.roles.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
        if (isRemoving) {
            CircularProgressIndicator(Modifier.padding(4.dp))
        } else {
            IconButton(onClick = { confirmRemove = true }) {
                Icon(Icons.Filled.PersonRemove, contentDescription = "Remove member", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove member?") },
            text = { Text("${member.user.email} will immediately lose access to this Cloudflare account.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }
}

private fun memberStatusColor(status: String): Color = when (status) {
    "accepted" -> StatusGreen
    "pending" -> StatusAmber
    else -> StatusRed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteMemberSheet(
    form: InviteFormState,
    roles: List<dev.cfmobile.app.data.remote.dto.AccountRole>,
    onDismiss: () -> Unit,
    onEmailChange: (String) -> Unit,
    onToggleRole: (String) -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Invite member", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = form.email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                placeholder = { Text("teammate@example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Roles", style = MaterialTheme.typography.labelLarge)
            roles.forEach { role ->
                Row(
                    Modifier.fillMaxWidth().clickable { onToggleRole(role.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = role.id in form.selectedRoleIds, onCheckedChange = { onToggleRole(role.id) })
                    Column {
                        Text(role.name, style = MaterialTheme.typography.bodyMedium)
                        if (role.description.isNotBlank()) {
                            Text(role.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onSave, enabled = !form.isSaving) {
                    if (form.isSaving) CircularProgressIndicator(Modifier.padding(end = 6.dp))
                    Text("Invite")
                }
            }
        }
    }
}
