package com.aiapuri.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiapuri.AiapuriApplication
import com.aiapuri.core.model.Message
import com.aiapuri.core.model.MessageRole
import com.aiapuri.core.model.ModelInfo
import com.aiapuri.core.model.Persona
import com.aiapuri.domain.chat.StreamingChatUseCase
import kotlinx.coroutines.launch

/**
 * Chat screen for a single conversation.
 *
 * Displays messages, handles sending user messages, persists locally,
 * and calls llama.cpp for streaming assistant responses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as AiapuriApplication
    val viewModel = rememberChatViewModel(application, conversationId)
    val state = viewModel.uiState
    val scope = rememberCoroutineScope()

    // Fetch available models on first compose
    LaunchedEffect(Unit) {
        viewModel.fetchModels()
    }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when messages change
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(
                    minOf(state.messages.size, listState.layoutInfo.totalItemsCount - 1)
                )
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.conversationTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Model selector dropdown
                    ChatModelSelector(
                        currentModel = state.currentModel,
                        availableModels = state.availableModels,
                        isFetchingModels = state.isFetchingModels,
                        onModelSelected = viewModel::switchModel,
                        onRefreshModels = viewModel::fetchModels
                    )

                    // Persona selector dropdown
                    ChatPersonaSelector(
                        currentPersonaId = state.currentPersonaId,
                        availablePersonas = state.availablePersonas,
                        onPersonaSelected = viewModel::switchPersona
                    )

                    // Streaming indicator in top bar
                    if (state.isStreaming) {
                        Icon(
                            Icons.Default.Circle,
                            contentDescription = "Streaming",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Error banner with retry
            AnimatedVisibility(visible = state.error != null) {
                val currentError = state.error
                ChatErrorBanner(
                    message = currentError?.userMessage ?: "An error occurred",
                    technicalMessage = currentError?.technicalMessage,
                    onDismiss = { viewModel.dismissError() },
                    onRetry = { viewModel.retryLastMessage() },
                    retryEnabled = currentError?.canRetry != false
                )
            }

            // Message list or empty state
            if (state.messages.isEmpty() && !state.isSending && !state.isStreaming) {
                ChatEmptyState(
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        val isStreamingMessage = state.streamingMessageId == message.id
                        MessageBubble(
                            message = message,
                            isStreaming = isStreamingMessage
                        )
                    }

                    // Loading indicator while waiting for assistant response to start
                    if (state.isSending && !state.isStreaming) {
                        item {
                            AssistantLoadingIndicator()
                        }
                    }
                }
            }

            // Message composer (or stop button during streaming)
            if (state.isStreaming) {
                StreamingStopButton(
                    onStop = viewModel::stopStreaming
                )
            } else {
                MessageComposer(
                    text = state.composerText,
                    onTextChanged = viewModel::onComposerTextChanged,
                    onSend = viewModel::sendMessage,
                    enabled = !state.isSending && !state.isStreaming
                )
            }
        }
    }
}

/**
 * Empty state shown when a conversation has no messages.
 */
@Composable
private fun ChatEmptyState(modifier: Modifier = Modifier) {
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No messages yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Send a message to start the conversation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Error banner for chat errors with optional retry.
 *
 * Technical details are shown in an expandable section so they don't
 * clutter the UI but remain accessible for debugging.
 */
@Composable
private fun ChatErrorBanner(
    message: String,
    technicalMessage: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    retryEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                TextButton(onClick = onRetry, enabled = retryEnabled) {
                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry")
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }

            // Expandable technical detail
            technicalMessage?.let { detail ->
                TextButton(
                    onClick = { showDetails = !showDetails },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(
                        imageVector = if (showDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showDetails) "Hide details" else "Show details",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                AnimatedVisibility(visible = showDetails) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .padding(start = 28.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Loading indicator shown while waiting for assistant response.
 */
@Composable
private fun AssistantLoadingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Animated dots
                LoadingDots()
            }
        }
    }
}

/**
 * Animated loading dots.
 */
@Composable
private fun LoadingDots() {
    var dotCount by remember { mutableStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(400)
            dotCount = (dotCount % 3) + 1
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            CircularProgressIndicator(
                modifier = Modifier.size(8.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (index < dotCount) 1f else 0.3f
                )
            )
        }
    }
}

/**
 * Individual message bubble.
 */
@Composable
private fun MessageBubble(
    message: Message,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    // Skip system messages from display (they're for the model)
    if (isSystem) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            // User message bubble
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 1.dp
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            // Assistant message bubble
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Assistant label
                    Text(
                        text = "Assistant",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Message content
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Streaming indicator on the active streaming message
                    if (isStreaming && message.content.isBlank()) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Thinking…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Small pulsing dot next to streaming content
                    if (isStreaming) {
                        StreamingCursorIndicator()
                    }
                }
            }
        }
    }
}

/**
 * Blinking cursor indicator shown during streaming.
 */
@Composable
private fun StreamingCursorIndicator() {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(530)
            visible = !visible
        }
    }

    if (visible) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .padding(top = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                )
        ) {}
    }
}

/**
 * Message input composer at the bottom of the chat.
 */
@Composable
private fun MessageComposer(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                placeholder = { Text("Type a message…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                enabled = enabled,
                shape = RoundedCornerShape(24.dp)
            )
            val sendEnabled = text.isNotBlank() && enabled
            IconButton(
                onClick = onSend,
                enabled = sendEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (sendEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp),
                    tint = if (sendEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                )
            }
        }
    }
}

/**
 * Stop button shown during streaming. Replaces the message composer.
 */
@Composable
private fun StreamingStopButton(
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        FilledTonalButton(
            onClick = onStop,
            modifier = Modifier.width(160.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(
                Icons.Default.Stop,
                contentDescription = "Stop generation",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop")
        }
    }
}

/**
 * Persona selector shown in the chat top bar.
 *
 * Allows the user to switch the persona for the current conversation.
 */
@Composable
private fun ChatPersonaSelector(
    currentPersonaId: String?,
    availablePersonas: List<Persona>,
    onPersonaSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // Find the currently selected persona
    val currentPersona = availablePersonas.find { it.id == currentPersonaId }

    Box(modifier = modifier) {
        // Button to open the dropdown
        TextButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Select persona",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = currentPersona?.name ?: "Persona",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // "None" option to clear persona
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("No persona (default)")
                    }
                },
                onClick = {
                    onPersonaSelected(null)
                    expanded = false
                }
            )

            if (availablePersonas.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                availablePersonas.forEach { persona ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = persona.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (persona.isDefault) {
                                        Text(
                                            text = "(default)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (persona.description.isNotBlank()) {
                                    Text(
                                        text = persona.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        onClick = {
                            onPersonaSelected(persona.id)
                            expanded = false
                        }
                    )
                }
            } else {
                DropdownMenuItem(
                    text = { Text("No personas available") },
                    enabled = false,
                    onClick = { }
                )
            }
        }
    }
}

/**
 * Model selector shown in the chat top bar.
 *
 * Allows the user to switch the model for the current conversation.
 * Shows a dropdown with fetched models and allows manual entry.
 */
@Composable
private fun ChatModelSelector(
    currentModel: String,
    availableModels: List<ModelInfo>,
    isFetchingModels: Boolean,
    onModelSelected: (String) -> Unit,
    onRefreshModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf(currentModel) }

    // Sync manual text when currentModel changes externally
    LaunchedEffect(currentModel) {
        manualText = currentModel
    }

    Box(modifier = modifier) {
        // Button to open the dropdown
        TextButton(
            onClick = { expanded = true },
            enabled = !isFetchingModels
        ) {
            if (isFetchingModels) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = "Select model",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (currentModel.isNotBlank()) currentModel else "Model",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Manual entry field
            Box(modifier = Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = manualText,
                    onValueChange = {
                        manualText = it
                        onModelSelected(it)
                    },
                    placeholder = { Text("Model name") },
                    modifier = Modifier.width(240.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            // Divider
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Refresh button
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Refresh models")
                    }
                },
                onClick = {
                    onRefreshModels()
                    expanded = false
                }
            )

            // Available models list
            if (availableModels.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = model.id,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onModelSelected(model.id)
                            manualText = model.id
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Create or retrieve the chat ViewModel.
 */
@Composable
fun rememberChatViewModel(
    application: AiapuriApplication,
    conversationId: String
): ChatViewModel {
    val streamingChatUseCase = remember {
        StreamingChatUseCase(
            conversationRepository = application.conversationRepository,
            personaRepository = application.personaRepository
        )
    }
    return remember(conversationId) {
        ChatViewModel(
            conversationRepository = application.conversationRepository,
            settingsRepository = application.settingsRepository,
            personaRepository = application.personaRepository,
            streamingChatUseCase = streamingChatUseCase,
            conversationId = conversationId
        )
    }
}
