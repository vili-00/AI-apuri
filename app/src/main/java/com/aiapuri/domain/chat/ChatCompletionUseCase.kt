package com.aiapuri.domain.chat

import com.aiapuri.core.model.Message
import com.aiapuri.core.model.MessageRole
import com.aiapuri.core.model.Persona
import com.aiapuri.core.model.ServerSettings
import com.aiapuri.data.conversation.ConversationRepository
import com.aiapuri.data.llama.LlamaApiClient
import com.aiapuri.data.llama.LlamaApiException
import com.aiapuri.data.llama.OkHttpLlamaApiClient
import com.aiapuri.data.persona.PersonaRepository
import com.aiapuri.data.llama.dto.ChatCompletionRequest
import com.aiapuri.data.llama.dto.ChatMessage
import kotlinx.coroutines.flow.first

/**
 * Use case for sending a non-streaming chat completion request.
 *
 * Steps:
 * 1. Load conversation and messages
 * 2. Load persona (if selected)
 * 3. Compose system prompt
 * 4. Build message history
 * 5. Send request to llama.cpp
 * 6. Save assistant response
 */
class ChatCompletionUseCase(
    private val conversationRepository: ConversationRepository,
    private val personaRepository: PersonaRepository,
    private val createApiClient: (ServerSettings) -> LlamaApiClient = { settings ->
        OkHttpLlamaApiClient(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey
        )
    }
) {

    /**
     * Result of a chat completion attempt.
     */
    sealed class Result {
        data class Success(val assistantMessage: Message) : Result()
        data class Error(
            val message: String,
            val technicalDetail: String? = null,
            val isRetryable: Boolean = true
        ) : Result()
    }

    /**
     * Send a chat completion request for the given conversation.
     *
     * @param conversationId The conversation to send the message in.
     * @param userMessage The user's message to send.
     * @param serverSettings The server settings (URL, API key, model).
     * @return A [Result] indicating success or failure.
     */
    suspend operator fun invoke(
        conversationId: String,
        userMessage: Message,
        serverSettings: ServerSettings
    ): Result {
        return try {
            // Load conversation to get model and persona
            val conversation = conversationRepository.getConversation(conversationId)
                ?: return Result.Error("Conversation not found", isRetryable = false)

            // Load persona if selected
            val persona = conversation.personaId?.let {
                personaRepository.getPersona(it)
            }

            // Compose system prompt
            val finalSystemPrompt = SystemPrompt.compose(persona?.systemPrompt)

            // Build message history from conversation
            val messageHistory = buildMessageHistory(
                conversationId = conversationId,
                systemPrompt = finalSystemPrompt
            )

            // Determine model: conversation model > server default > fallback
            val model = conversation.model
                ?: serverSettings.defaultModel
                ?: "default"

            // Create API client
            val client = createApiClient(serverSettings)

            // Build and send request
            val request = ChatCompletionRequest(
                model = model,
                messages = messageHistory,
                stream = false
            )

            val response = client.chatCompletion(request)

            // Extract assistant content
            val assistantContent = response.choices
                .firstOrNull()
                ?.message
                ?.content
                ?: ""

            if (assistantContent.isBlank()) {
                return Result.Error("Empty response from server", isRetryable = true)
            }

            // Create assistant message
            val assistantMessage = Message(
                id = java.util.UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = assistantContent,
                createdAt = java.time.Instant.now(),
                status = com.aiapuri.core.model.MessageStatus.COMPLETE
            )

            // Save assistant response
            conversationRepository.saveMessage(assistantMessage)

            Result.Success(assistantMessage)

        } catch (e: LlamaApiException) {
            val detail = e.message?.take(200) ?: "no details"
            when (e.code) {
                401 -> Result.Error("Authentication failed. Check your API key.", technicalDetail = "HTTP 401", isRetryable = false)
                404 -> Result.Error("Model not found on server.", technicalDetail = "HTTP 404", isRetryable = false)
                429 -> Result.Error("Rate limited. Please try again later.", technicalDetail = "HTTP 429", isRetryable = true)
                in 500..599 -> Result.Error("Server error (${e.code}). Please try again.", technicalDetail = "HTTP ${e.code}", isRetryable = true)
                else -> Result.Error("Request failed (${e.code})", technicalDetail = detail, isRetryable = true)
            }
        } catch (e: java.net.ConnectException) {
            Result.Error("Cannot reach server. Check your connection.", technicalDetail = e.javaClass.simpleName, isRetryable = true)
        } catch (e: java.net.SocketTimeoutException) {
            Result.Error("Request timed out. The server may be busy.", technicalDetail = e.javaClass.simpleName, isRetryable = true)
        } catch (e: Exception) {
            val detail = e.message?.take(200) ?: e.javaClass.simpleName
            Result.Error("Unexpected error", technicalDetail = detail, isRetryable = true)
        }
    }

    /**
     * Build the message history for a chat request.
     *
     * Includes the system prompt and all existing messages in the conversation.
     * The new user message is appended at the end.
     */
    private suspend fun buildMessageHistory(
        conversationId: String,
        systemPrompt: String
    ): List<ChatMessage> {
        // Get existing messages from the conversation
        val existingMessages = conversationRepository.observeMessages(conversationId).first()

        return buildList {
            // Add system prompt
            add(ChatMessage(role = "system", content = systemPrompt))

            // Add conversation history
            for (msg in existingMessages) {
                add(ChatMessage(
                    role = msg.role.name.lowercase(),
                    content = msg.content
                ))
            }
        }
    }
}
