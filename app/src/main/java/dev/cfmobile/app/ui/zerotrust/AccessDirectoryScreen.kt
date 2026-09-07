package dev.cfmobile.app.ui.zerotrust

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cfmobile.app.ui.common.CfListScreen
import dev.cfmobile.app.ui.common.DeletableListRow
import dev.cfmobile.app.ui.common.FormActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessDirectoryScreen(viewModel: AccessDirectoryViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val header: @Composable () -> Unit = {
        PrimaryScrollableTabRow(selectedTabIndex = uiState.tab.ordinal, edgePadding = 0.dp) {
            AccessDirectoryTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.tab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(tab.label) }
                )
            }
        }
        uiState.error?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp, 4.dp)
            )
        }
    }

    when (uiState.tab) {
        AccessDirectoryTab.GROUPS -> CfListScreen(
            title = "Access Directory",
            onBack = onBack,
            state = uiState.groups,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No Access groups yet",
            key = { it.id },
            onCreate = viewModel::openForm,
            createContentDescription = "Create group",
            header = header
        ) { group ->
            DeletableListRow(
                icon = Icons.Filled.Group,
                title = group.name,
                subtitle = groupSummary(group),
                isDeleting = uiState.deletingId == group.id,
                deleteContentDescription = "Delete group",
                confirmTitle = "Delete group?",
                confirmText = "Any policy referencing \"${group.name}\" loses that rule, which can widen or close off access.",
                onDelete = { viewModel.deleteGroup(group) }
            )
        }

        AccessDirectoryTab.BOOKMARKS -> CfListScreen(
            title = "Access Directory",
            onBack = onBack,
            state = uiState.bookmarks,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No bookmarks on the launchpad",
            key = { it.id },
            onCreate = viewModel::openForm,
            createContentDescription = "Add bookmark",
            header = header
        ) { bookmark ->
            DeletableListRow(
                icon = Icons.Filled.Bookmark,
                title = bookmark.name ?: bookmark.domain.orEmpty(),
                subtitle = bookmark.domain,
                detail = if (bookmark.appLauncherVisible == false) "Hidden from the launchpad" else null,
                isDeleting = uiState.deletingId == bookmark.id,
                deleteContentDescription = "Delete bookmark",
                confirmTitle = "Delete bookmark?",
                confirmText = "The link disappears from the launchpad. The app itself isn't affected - Access doesn't guard a bookmark.",
                onDelete = { viewModel.deleteBookmark(bookmark) }
            )
        }

        AccessDirectoryTab.TAGS -> CfListScreen(
            title = "Access Directory",
            onBack = onBack,
            state = uiState.tags,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No tags yet",
            key = { it.name },
            onCreate = viewModel::openForm,
            createContentDescription = "Create tag",
            header = header
        ) { tag ->
            DeletableListRow(
                icon = Icons.Filled.Sell,
                title = tag.name,
                subtitle = tag.appCount?.let { "$it app${if (it == 1) "" else "s"}" },
                isDeleting = uiState.deletingId == tag.name,
                deleteContentDescription = "Delete tag",
                confirmTitle = "Delete tag?",
                confirmText = "Applications keep working; they just lose this grouping on the launchpad.",
                onDelete = { viewModel.deleteTag(tag) }
            )
        }

        AccessDirectoryTab.CERTIFICATES -> CfListScreen(
            title = "Access Directory",
            onBack = onBack,
            state = uiState.certificates,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            emptyMessage = "No mTLS root certificates",
            key = { it.id },
            header = {
                header()
                Text(
                    // Uploading one means handling a certificate chain, which this app doesn't do.
                    "Root certificates Access accepts client certificates from. Uploading one is done from the dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
        ) { certificate ->
            DeletableListRow(
                icon = Icons.Filled.Badge,
                title = certificate.name ?: certificate.id,
                subtitle = certificateSummary(certificate),
                detail = certificate.fingerprint,
                isDeleting = uiState.deletingId == certificate.id,
                deleteContentDescription = "Delete certificate",
                confirmTitle = "Delete this root certificate?",
                confirmText = "Client certificates issued from it stop being accepted immediately.",
                onDelete = { viewModel.deleteCertificate(certificate) }
            )
        }
    }

    uiState.groupForm?.let { form ->
        GroupSheet(form, onDismiss = viewModel::closeForms, viewModel = viewModel)
    }
    uiState.bookmarkForm?.let { form ->
        BookmarkSheet(form, onDismiss = viewModel::closeForms, viewModel = viewModel)
    }
    uiState.tagForm?.let { form ->
        TagSheet(form, onDismiss = viewModel::closeForms, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSheet(
    form: AccessGroupFormState,
    onDismiss: () -> Unit,
    viewModel: AccessDirectoryViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create group", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateGroupForm { it.copy(name = v) } },
                label = { Text("Group name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.emailDomain,
                onValueChange = { v -> viewModel.updateGroupForm { it.copy(emailDomain = v) } },
                label = { Text("Email domain") },
                placeholder = { Text("example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.emails,
                onValueChange = { v -> viewModel.updateGroupForm { it.copy(emails = v) } },
                label = { Text("Individual addresses") },
                placeholder = { Text("someone@example.com") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp)
            )
            Text(
                "One address per line. Either field alone is enough - a group with neither would match nobody. Other rule kinds (IP ranges, device posture, service tokens) are set from the dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::saveGroup, saveLabel = "Create")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkSheet(
    form: AccessBookmarkFormState,
    onDismiss: () -> Unit,
    viewModel: AccessDirectoryViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add bookmark", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateBookmarkForm { it.copy(name = v) } },
                label = { Text("Name") },
                placeholder = { Text("Internal wiki") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.domain,
                onValueChange = { v -> viewModel.updateBookmarkForm { it.copy(domain = v) } },
                label = { Text("Domain") },
                placeholder = { Text("wiki.example.com") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "A bookmark is a link on the launchpad. Access doesn't guard it - protecting the app itself means an Access application.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::saveBookmark, saveLabel = "Add")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSheet(
    form: AccessTagFormState,
    onDismiss: () -> Unit,
    viewModel: AccessDirectoryViewModel
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create tag", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.updateTagForm { it.copy(name = v) } },
                label = { Text("Tag name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (form.error != null) {
                Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(isSaving = form.isSaving, onCancel = onDismiss, onSave = viewModel::saveTag, saveLabel = "Create")
        }
    }
}
