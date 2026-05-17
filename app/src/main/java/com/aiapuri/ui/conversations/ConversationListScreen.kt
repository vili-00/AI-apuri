package com.aiapuri.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiapuri.AiapuriApplication
import com.aiapuri.core.model.ConversationSummary
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Conversation list screen.
 *
 * Observes saved conversations reactively, supports create, rename, delete,
 * and search/filter functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onNavigateToChat: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPersonas: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as AiapuriApplication
    val viewModel = rememberConversationListViewModel(application)
    val state = viewModel.uiState

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Conversations") },
                actions = {
                    IconButton(onClick = onNavigateToPersonas) {
                        Icon(Icons.Default.Person, contentDescription = "Personas")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.createConversation() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Conversation")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            SearchBar(viewModel = viewModel)

            // Error banner
            state.errorMessage?.let { error ->
                ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.dismissError() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Conversation list or empty state
            val conversations = viewModel.filteredConversations
            if (conversations.isEmpty()) {
                EmptyState(
                    hasSearchQuery = state.searchQuery.isNotBlank(),
                    onCreateConversation = { viewModel.createConversation() },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(conversations, key = { it.id }) { summary ->
                        ConversationItem(
                            summary = summary,
                            onClick = { onNavigateToChat(summary.id) },
                            onRename = { viewModel.startRename(summary.id, summary.title) },
                            onDelete = { viewModel.confirmDelete(summary.id) }
                        )
                    }
                }
            }
        }
    }

    // Rename dialog
    if (state.isRenaming) {
        RenameDialog(
            currentTitle = state.renameTitle,
            onTitleChanged = viewModel::onRenameTitleChanged,
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::cancelRename
        )
    }

    // Delete confirmation dialog
    state.deleteConfirmId?.let { convId ->
        val conv = state.conversations.find { it.id == convId }
        DeleteConfirmationDialog(
            conversationTitle = conv?.title ?: "Conversation",
            onConfirm = viewModel::deleteConversation,
            onDismiss = viewModel::cancelDelete
        )
    }
}

/**
 * Search bar with clear button.
 */
@Composable
private fun SearchBar(
    viewModel: ConversationListViewModel,
    modifier: Modifier = Modifier
) {
    val state = viewModel.uiState
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = viewModel::onSearchQueryChanged,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search conversations…") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Search")
        },
        trailingIcon = {
            if (state.searchQuery.isNotBlank()) {
                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp)
    )
}

/**
 * Empty state shown when there are no conversations.
 */
@Composable
private fun EmptyState(
    hasSearchQuery: Boolean,
    onCreateConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (hasSearchQuery) "No matching conversations" else "No conversations yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasSearchQuery) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap + to start a new chat",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onCreateConversation,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Conversation")
                }
            }
        }
    }
}

/**
 * Individual conversation item in the list.
 */
@Composable
private fun ConversationItem(
    summary: ConversationSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Main content
            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Last message preview
                summary.lastMessagePreview?.let { preview ->
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Metadata row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Model badge
                    summary.model?.let { model ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 0.dp
                        ) {
                            Text(
                                text = model,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Persona badge
                    summary.personaName?.let { persona ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            tonalElevation = 0.dp
                        ) {
                            Text(
                                text = persona,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Timestamp
                    Text(
                        text = formatRelativeTime(summary.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Overflow menu
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        onClick = {
                            expanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            expanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Rename dialog.
 */
@Composable
private fun RenameDialog(
    currentTitle: String,
    onTitleChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Conversation") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    onTitleChanged(it)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Enter new title") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = title.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Delete confirmation dialog.
 */
@Composable
private fun DeleteConfirmationDialog(
    conversationTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete Conversation") },
        text = {
            Text("Are you sure you want to delete \"$conversationTitle\"? This will remove all messages in this conversation and cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Error banner component.
 */
@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

/**
 * Format a timestamp as a relative time string.
 */
private fun formatRelativeTime(instant: Instant): String {
    val now = Instant.now()
    val duration = Duration.between(instant, now)

    return when {
        Math.abs(duration.seconds) < 60 -> "Just now"
        Math.abs(duration.seconds) < 3600 -> "${duration.seconds / 60}m ago"
        Math.abs(duration.seconds) < 86400 -> "${duration.seconds / 3600}h ago"
        Math.abs(duration.seconds) < 604800 -> "${duration.toDays()}d ago"
        else -> instant.atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

/**
 * Create or retrieve the conversation list ViewModel.
 */
@Composable
fun rememberConversationListViewModel(application: AiapuriApplication): ConversationListViewModel {
    return remember {
        ConversationListViewModel(
            conversationRepository = application.conversationRepository,
            personaRepository = application.personaRepository
        )
    }
}
