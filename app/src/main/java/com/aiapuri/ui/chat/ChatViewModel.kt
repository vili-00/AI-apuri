package com.aiapuri.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiapuri.core.model.Conversation
import com.aiapuri.core.model.Message
import com.aiapuri.core.model.MessageRole
import com.aiapuri.core.model.MessageStatus
import com.aiapuri.data.conversation.ConversationRepository
import com.aiapuri.data.settings.SettingsRepository
import com.aiapuri.domain.chat.StreamingChatUseCase
import com.aiapuri.domain.chat.StreamingChatUseCase.StreamingUpdate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * A structured error displayed to the user.
 *
 * @property userMessage Human-readable message shown in the banner (never null).
 * @property technicalMessage Optional technical detail for debugging (may be null).
 * @property canRetry Whether the Retry button should be enabled.
 */
data class UiError(
    val userMessage: String,
    val technicalMessage: String? = null,
    val canRetry: Boolean = true
)

/**
 * UI state for the chat screen.
 */
data class ChatUiState(
    val conversationTitle: String = "Chat",
    val messages: List<Message> = emptyList(),
    val composerText: String = "",
    val isLoading: Boolean = false,
    val error: UiError? = null,
    val conversationExists: Boolean = true,
    val isSending: Boolean = false,
    /** True while a streaming response is in progress. */
    val isStreaming: Boolean = false,
    /** ID of the message currently being streamed (for UI highlighting). */
    val streamingMessageId: String? = null
)

/**
 * ViewModel for the chat screen.
 *
 * Observes messages reactively, handles sending user messages,
 * persists them locally, and calls llama.cpp for streaming assistant responses.
 */
class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val streamingChatUseCase: StreamingChatUseCase,
    conversationId: String
) : ViewModel() {

    private val convId = conversationId

    var uiState by mutableStateOf(ChatUiState())
        private set

    /** Reactive message flow for this conversation. */
    private val messageFlow = conversationRepository.observeMessages(convId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            // Load conversation info
            val conv = conversationRepository.getConversation(convId)
            if (conv != null) {
                uiState = uiState.copy(
                    conversationTitle = conv.title,
                    conversationExists = true
                )
            } else {
                // Check if this is a "new" conversation request
                if (convId == "new" || convId.isEmpty()) {
                    uiState = uiState.copy(conversationExists = false)
                } else {
                    uiState = uiState.copy(
                        conversationExists = false,
                        error = UiError(
                            userMessage = "Conversation not found",
                            technicalMessage = null,
                            canRetry = false
                        )
                    )
                }
            }

            // Collect messages
            messageFlow.collect { messages ->
                uiState = uiState.copy(messages = messages)
            }
        }
    }

    /** Update composer text. */
    fun onComposerTextChanged(text: String) {
        uiState = uiState.copy(composerText = text)
    }

    /**
     * Send a user message and get a streaming assistant response.
     *
     * 1. Creates a conversation if needed
     * 2. Saves the user message
     * 3. Starts streaming request to llama.cpp
     * 4. Updates assistant message as deltas arrive
     * 5. Finalizes message on complete, stop, or error
     */
    fun sendMessage() {
        val text = uiState.composerText.trim()
        if (text.isEmpty()) return
        if (uiState.isSending || uiState.isStreaming) return

        viewModelScope.launch {
            uiState = uiState.copy(isSending = true, error = null)

            try {
                var currentConvId = convId

                // Create conversation if needed
                if (!uiState.conversationExists || currentConvId == "new" || currentConvId.isEmpty()) {
                    val serverSettings = settingsRepository.serverSettingsFlow.first()
                    val defaultModel = serverSettings.defaultModel ?: "default"
                    val newConv = Conversation(
                        id = UUID.randomUUID().toString(),
                        title = text.take(50) + if (text.length > 50) "…" else "",
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                        model = defaultModel
                    )
                    conversationRepository.createConversation(newConv)
                    currentConvId = newConv.id
                }

                // Save user message
                val userMessage = Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = currentConvId,
                    role = MessageRole.USER,
                    content = text,
                    createdAt = Instant.now(),
                    status = MessageStatus.COMPLETE
                )
                conversationRepository.saveMessage(userMessage)

                // Clear composer
                uiState = uiState.copy(composerText = "")

                // Get server settings for API call
                val serverSettings = settingsRepository.serverSettingsFlow.first()

                // Start streaming — isSending transitions to isStreaming
                uiState = uiState.copy(isSending = false, isStreaming = true)

                val streamingJob = viewModelScope.launch {
                    streamingChatUseCase.startStreaming(
                        conversationId = currentConvId,
                        serverSettings = serverSettings
                    ).collect { update ->
                        when (update) {
                            is StreamingUpdate.TextAppended -> {
                                // Track the streaming message ID
                                uiState = uiState.copy(streamingMessageId = update.messageId)
                                // The message content is updated in the database and
                                // will appear via the reactive message flow
                            }

                            is StreamingUpdate.Complete -> {
                                // Streaming finished successfully
                                uiState = uiState.copy(
                                    isStreaming = false,
                                    streamingMessageId = null
                                )
                            }

                            is StreamingUpdate.Stopped -> {
                                // User cancelled streaming
                                uiState = uiState.copy(
                                    isStreaming = false,
                                    streamingMessageId = null
                                )
                            }

                            is StreamingUpdate.Error -> {
                                // Streaming error — show error banner
                                uiState = uiState.copy(
                                    isStreaming = false,
                                    streamingMessageId = null,
                                    error = UiError(
                                        userMessage = update.userMessage,
                                        technicalMessage = update.technicalDetail,
                                        canRetry = update.isRetryable
                                    )
                                )
                            }
                        }
                    }
                }

                // Store the job reference so we can cancel it on stop
                // We use a trick: the streaming flow will be cancelled when
                // the stopStreaming() method cancels the coroutine context.

            } catch (e: Exception) {
                val safeMessage = e.message ?: "${e.javaClass.simpleName}"
                uiState = uiState.copy(
                    isStreaming = false,
                    streamingMessageId = null,
                    error = UiError(
                        userMessage = "Failed to send message",
                        technicalMessage = safeMessage,
                        canRetry = true
                    )
                )
            } finally {
                // isSending is cleared; isStreaming is managed by the streaming flow
            }
        }
    }

    /**
     * Stop the current streaming request.
     *
     * Cancels the ongoing request and saves any partial response as STOPPED.
     */
    fun stopStreaming() {
        if (!uiState.isStreaming) return

        viewModelScope.launch {
            // Mark the streaming message as stopped
            uiState.streamingMessageId?.let { messageId ->
                streamingChatUseCase.stopStreaming(messageId)
            }

            uiState = uiState.copy(
                isStreaming = false,
                streamingMessageId = null
            )
        }
    }

    /**
     * Retry the last failed request by re-sending the last user message.
     * Does nothing if retry is not allowed or there is no last user message.
     */
    fun retryLastMessage() {
        // Safety: if the current error says retry is not allowed, bail out
        if (uiState.error?.canRetry != true) {
            uiState = uiState.copy(error = null)
            return
        }

        val messages = uiState.messages
        val lastUserMessage = messages.lastOrNull { it.role == MessageRole.USER }
            ?: run {
                // No user message to retry — just clear the error
                uiState = uiState.copy(error = null)
                return
            }

        viewModelScope.launch {
            uiState = uiState.copy(isSending = true, error = null, isStreaming = false)

            try {
                val serverSettings = settingsRepository.serverSettingsFlow.first()

                // Start streaming retry
                uiState = uiState.copy(isSending = false, isStreaming = true)

                streamingChatUseCase.startStreaming(
                    conversationId = convId,
                    serverSettings = serverSettings
                ).collect { update ->
                    when (update) {
                        is StreamingUpdate.TextAppended -> {
                            uiState = uiState.copy(streamingMessageId = update.messageId)
                        }

                        is StreamingUpdate.Complete -> {
                            uiState = uiState.copy(
                                isStreaming = false,
                                streamingMessageId = null
                            )
                        }

                        is StreamingUpdate.Stopped -> {
                            uiState = uiState.copy(
                                isStreaming = false,
                                streamingMessageId = null
                            )
                        }

                        is StreamingUpdate.Error -> {
                            uiState = uiState.copy(
                                isStreaming = false,
                                streamingMessageId = null,
                                error = UiError(
                                    userMessage = update.userMessage,
                                    technicalMessage = update.technicalDetail,
                                    canRetry = update.isRetryable
                                )
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                val safeMessage = e.message ?: "${e.javaClass.simpleName}"
                uiState = uiState.copy(
                    isStreaming = false,
                    streamingMessageId = null,
                    error = UiError(
                        userMessage = "Retry failed",
                        technicalMessage = safeMessage,
                        canRetry = true
                    )
                )
            }
        }
    }

    /** Dismiss error message. Safe even when no error is present. */
    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    /** Get the effective conversation ID. */
    fun getConversationId(): String {
        return convId
    }
}
