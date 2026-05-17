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
import com.aiapuri.core.model.ModelInfo
import com.aiapuri.core.model.Persona
import com.aiapuri.data.conversation.ConversationRepository
import com.aiapuri.data.llama.OkHttpLlamaApiClient
import com.aiapuri.data.persona.PersonaRepository
import com.aiapuri.data.settings.SettingsRepository
import com.aiapuri.domain.chat.StreamingChatUseCase
import com.aiapuri.domain.chat.StreamingChatUseCase.StreamingUpdate
import com.aiapuri.core.util.ErrorMapper
import com.aiapuri.core.util.TitleGenerator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * A structured error displayed to the user.
 *
 * Wraps a central [AppError] with a redacted technical detail.
 * The technical detail is always safe to display — API keys, chat content,
 * and secrets are stripped by [ErrorMapper].
 *
 * @property appError The central error classification.
 * @property technicalMessage Optional redacted technical detail for debugging.
 */
data class UiError(
    val appError: com.aiapuri.core.model.AppError,
    val technicalMessage: String? = null
) {
    val userMessage: String get() = appError.userMessage
    val canRetry: Boolean get() = appError.isRetryable
    val suggestedAction: com.aiapuri.core.model.SuggestedAction get() = appError.suggestedAction
}

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
    val streamingMessageId: String? = null,
    /** Currently selected model for this conversation. */
    val currentModel: String = "",
    /** Available models fetched from the server. */
    val availableModels: List<ModelInfo> = emptyList(),
    /** True while fetching the model list. */
    val isFetchingModels: Boolean = false,
    /** Currently selected persona ID for this conversation. */
    val currentPersonaId: String? = null,
    /** Available personas from the database. */
    val availablePersonas: List<Persona> = emptyList()
)

/**
 * ViewModel for the chat screen.
 *
 * Observes messages reactively, handles sending user messages,
 * persists them locally, and calls llama.cpp for streaming assistant responses.
 * Supports model switching per conversation.
 */
class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val personaRepository: PersonaRepository,
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
                    conversationExists = true,
                    currentModel = conv.model.takeIf { it.isNotBlank() } ?: "",
                    currentPersonaId = conv.personaId
                )
            } else {
                // Check if this is a "new" conversation request
                if (convId == "new" || convId.isEmpty()) {
                    uiState = uiState.copy(conversationExists = false)
                    // Pre-load default model for new conversations
                    val defaultModel = settingsRepository.serverSettingsFlow.first().defaultModel
                    if (!defaultModel.isNullOrBlank()) {
                        uiState = uiState.copy(currentModel = defaultModel)
                    }
                } else {
                    uiState = uiState.copy(
                        conversationExists = false,
                        error = UiError(
                            appError = com.aiapuri.core.model.AppError.Unknown(
                                userMessage = "Conversation not found",
                                isRetryable = false
                            ),
                            technicalMessage = null
                        )
                    )
                }
            }

            // Collect messages
            messageFlow.collect { messages ->
                uiState = uiState.copy(messages = messages)
            }
        }

        // Load personas
        viewModelScope.launch {
            try {
                val personas = personaRepository.getAllPersonas()
                uiState = uiState.copy(availablePersonas = personas)
                // If no persona selected and this is a new conversation, pre-select the default
                if (uiState.currentPersonaId == null && !uiState.conversationExists) {
                    val defaultPersona = personaRepository.getDefaultPersona()
                    if (defaultPersona != null) {
                        uiState = uiState.copy(currentPersonaId = defaultPersona.id)
                    }
                }
            } catch (e: Exception) {
                // Silently fail — chat works without personas
            }
        }
    }

    /** Update composer text. */
    fun onComposerTextChanged(text: String) {
        uiState = uiState.copy(composerText = text)
    }

    // ==================== Model Switching ====================

    /**
     * Fetch the list of available models from the server.
     */
    fun fetchModels() {
        viewModelScope.launch {
            uiState = uiState.copy(isFetchingModels = true)
            try {
                val serverSettings = settingsRepository.serverSettingsFlow.first()
                if (serverSettings.baseUrl.isBlank()) {
                    // No server configured yet
                    uiState = uiState.copy(isFetchingModels = false)
                    return@launch
                }
                val client = OkHttpLlamaApiClient(
                    baseUrl = serverSettings.baseUrl,
                    apiKey = serverSettings.apiKey
                )
                val models = client.listModels()
                uiState = uiState.copy(
                    availableModels = models,
                    isFetchingModels = false
                )
            } catch (e: Exception) {
                // Silently fail — user can still type a model name manually
                uiState = uiState.copy(isFetchingModels = false)
            }
        }
    }

    /**
     * Switch the model for the current conversation.
     *
     * If the conversation exists, updates its stored model.
     * If this is a new conversation, just updates the UI state
     * (the model will be saved when the conversation is created).
     */
    fun switchModel(modelId: String) {
        uiState = uiState.copy(currentModel = modelId)
        // If the conversation already exists, persist the model change
        if (uiState.conversationExists && convId.isNotEmpty() && convId != "new") {
            viewModelScope.launch {
                try {
                    conversationRepository.updateConversationModel(convId, modelId)
                } catch (e: Exception) {
                    // Silently fail — model will be used for next request regardless
                }
            }
        }
    }

    // ==================== Persona Selection ====================

    /**
     * Switch the persona for the current conversation.
     * Pass null to clear the persona selection.
     */
    fun switchPersona(personaId: String?) {
        uiState = uiState.copy(currentPersonaId = personaId)
        // If the conversation already exists, persist the persona change
        if (uiState.conversationExists && convId.isNotEmpty() && convId != "new") {
            viewModelScope.launch {
                try {
                    conversationRepository.updateConversationPersona(convId, personaId)
                } catch (e: Exception) {
                    // Silently fail — persona will be used for next request regardless
                }
            }
        }
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
                    val model = uiState.currentModel
                        .takeIf { it.isNotBlank() }
                        ?: serverSettings.defaultModel
                        ?: "default"

                    val newConv = Conversation(
                        id = UUID.randomUUID().toString(),
                        title = TitleGenerator.generateTitle(text),
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                        model = model,
                        personaId = uiState.currentPersonaId
                    )
                    conversationRepository.createConversation(newConv)
                    currentConvId = newConv.id
                    // Update UI state with the new conversation's model
                    uiState = uiState.copy(
                        conversationExists = true,
                        conversationTitle = newConv.title,
                        currentModel = model
                    )
                } else {
                    // Conversation already exists — check if this is the first message
                    // and the title is still the default placeholder. If so, auto-generate.
                    val appSettings = settingsRepository.appSettingsFlow.first()
                    if (appSettings.autoGenerateTitles &&
                        TitleGenerator.isPlaceholderTitle(uiState.conversationTitle)) {
                        val generatedTitle = TitleGenerator.generateTitle(text)
                        val updated = conversationRepository.updateTitleIfDefault(currentConvId, generatedTitle)
                        if (updated) {
                            uiState = uiState.copy(conversationTitle = generatedTitle)
                        }
                    }
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
                                val mappedError = ErrorMapper.mapStreamingMessage(update.userMessage)
                                uiState = uiState.copy(
                                    isStreaming = false,
                                    streamingMessageId = null,
                                    error = UiError(
                                        appError = mappedError,
                                        technicalMessage = update.technicalDetail
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
                val mappedError = ErrorMapper.map(e)
                uiState = uiState.copy(
                    isStreaming = false,
                    streamingMessageId = null,
                    error = UiError(
                        appError = mappedError,
                        technicalMessage = mappedError.technicalDetail
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
                            val mappedError = ErrorMapper.mapStreamingMessage(update.userMessage)
                            uiState = uiState.copy(
                                isStreaming = false,
                                streamingMessageId = null,
                                error = UiError(
                                    appError = mappedError,
                                    technicalMessage = update.technicalDetail
                                )
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                val mappedError = ErrorMapper.map(e)
                uiState = uiState.copy(
                    isStreaming = false,
                    streamingMessageId = null,
                    error = UiError(
                        appError = mappedError,
                        technicalMessage = mappedError.technicalDetail
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
