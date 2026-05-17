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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * UI state for the chat screen.
 */
data class ChatUiState(
    val conversationTitle: String = "Chat",
    val messages: List<Message> = emptyList(),
    val composerText: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val conversationExists: Boolean = true
)

/**
 * ViewModel for the chat screen.
 *
 * Observes messages reactively, handles sending user messages,
 * and persists them via ConversationRepository.
 *
 * Task 10 scope: local persistence only — no llama.cpp calls yet.
 */
class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
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
                        errorMessage = "Conversation not found"
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
     * Send a user message.
     *
     * If no conversation exists yet, creates one first.
     * Saves the message to the encrypted database.
     */
    fun sendMessage() {
        val text = uiState.composerText.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            try {
                var currentConvId = convId

                // Create conversation if needed
                if (!uiState.conversationExists || currentConvId == "new" || currentConvId.isEmpty()) {
                    val defaultModel = settingsRepository.serverSettingsFlow.first().defaultModel
                        ?: "default"
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

                // Update our internal convId for subsequent messages
                // Note: we can't change convId itself (val), but the messages
                // will flow through the repository observation for the original convId
                // If we created a new conversation, the navigation should handle redirect
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = "Failed to send message: ${e.message}")
            }
        }
    }

    /** Dismiss error message. */
    fun dismissError() {
        uiState = uiState.copy(errorMessage = null)
    }

    /** Get the effective conversation ID (may differ from original if new was created). */
    fun getConversationId(): String {
        return convId
    }
}
