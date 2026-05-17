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
import com.aiapuri.domain.chat.ChatCompletionUseCase
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
    val isSending: Boolean = false
)

/**
 * ViewModel for the chat screen.
 *
 * Observes messages reactively, handles sending user messages,
 * persists them locally, and calls llama.cpp for assistant responses.
 */
class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val chatCompletionUseCase: ChatCompletionUseCase,
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
     * Send a user message and get an assistant response.
     *
     * 1. Creates a conversation if needed
     * 2. Saves the user message
     * 3. Calls llama.cpp for assistant response
     * 4. Saves the assistant response
     */
    fun sendMessage() {
        val text = uiState.composerText.trim()
        if (text.isEmpty()) return
        if (uiState.isSending) return

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

                // Call llama.cpp
                val result = chatCompletionUseCase(
                    conversationId = currentConvId,
                    userMessage = userMessage,
                    serverSettings = serverSettings
                )

                when (result) {
                    is ChatCompletionUseCase.Result.Success -> {
                        // Response saved and will appear via reactive flow
                    }
                    is ChatCompletionUseCase.Result.Error -> {
                        uiState = uiState.copy(
                            error = UiError(
                                userMessage = result.message,
                                technicalMessage = result.technicalDetail,
                                canRetry = result.isRetryable
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                val safeMessage = e.message ?: "${e.javaClass.simpleName}"
                uiState = uiState.copy(
                    error = UiError(
                        userMessage = "Failed to send message",
                        technicalMessage = safeMessage,
                        canRetry = true
                    )
                )
            } finally {
                uiState = uiState.copy(isSending = false)
            }
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
            uiState = uiState.copy(isSending = true, error = null)

            try {
                val serverSettings = settingsRepository.serverSettingsFlow.first()

                val result = chatCompletionUseCase(
                    conversationId = convId,
                    userMessage = lastUserMessage,
                    serverSettings = serverSettings
                )

                when (result) {
                    is ChatCompletionUseCase.Result.Success -> {
                        // Response saved and will appear via reactive flow
                    }
                    is ChatCompletionUseCase.Result.Error -> {
                        uiState = uiState.copy(
                            error = UiError(
                                userMessage = result.message,
                                technicalMessage = result.technicalDetail,
                                canRetry = result.isRetryable
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                val safeMessage = e.message ?: "${e.javaClass.simpleName}"
                uiState = uiState.copy(
                    error = UiError(
                        userMessage = "Retry failed",
                        technicalMessage = safeMessage,
                        canRetry = true
                    )
                )
            } finally {
                uiState = uiState.copy(isSending = false)
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
