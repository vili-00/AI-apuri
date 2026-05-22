package com.aiapuri.domain.chat

import com.aiapuri.core.model.ChatStreamEvent
import com.aiapuri.core.model.Message
import com.aiapuri.core.model.MessageRole
import com.aiapuri.core.model.MessageStatus
import com.aiapuri.core.model.ServerSettings
import com.aiapuri.data.conversation.ConversationRepository
import com.aiapuri.data.llama.LlamaApiClient
import com.aiapuri.data.llama.LlamaApiException
import com.aiapuri.data.llama.OkHttpLlamaApiClient
import com.aiapuri.data.persona.PersonaRepository
import com.aiapuri.data.llama.dto.ChatCompletionRequest
import com.aiapuri.data.llama.dto.ChatMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/**
 * Use case for sending a streaming chat completion request.
 *
 * Steps:
 * 1. Load conversation and messages
 * 2. Load persona (if selected)
 * 3. Compose system prompt
 * 4. Build message history
 * 5. Create a streaming assistant message placeholder
 * 6. Send streaming request to llama.cpp
 * 7. Update assistant message content as deltas arrive
 * 8. Finalize message as COMPLETE, STOPPED, or ERROR
 *
 * Emits [StreamingUpdate] events through a [Flow] so the UI can
 * react to each text delta and to completion/cancellation/error.
 */
class StreamingChatUseCase(
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
     * Intermediate updates emitted during streaming.
     */
    sealed class StreamingUpdate {
        /** A new text delta was appended. */
        data class TextAppended(val messageId: String, val currentContent: String) : StreamingUpdate()
        /** Streaming completed successfully. */
        data class Complete(val messageId: String, val finalMessage: Message) : StreamingUpdate()
        /** Streaming was cancelled by the user. */
        data class Stopped(val messageId: String, val partialMessage: Message) : StreamingUpdate()
        /** An error occurred. */
        data class Error(
            val messageId: String,
            val userMessage: String,
            val technicalDetail: String? = null,
            val partialContent: String? = null,
            val isRetryable: Boolean = true
        ) : StreamingUpdate()
    }

    /**
     * Start a streaming chat completion for the given conversation.
     *
     * Returns a [Flow] of [StreamingUpdate] events. The flow is cold — it
     * starts the request when collected and completes when streaming finishes,
     * is cancelled, or errors out.
     *
     * @param conversationId The conversation to send the message in.
     * @param serverSettings The server settings (URL, API key, model).
     * @return A [Flow] of [StreamingUpdate] events.
     */
    fun startStreaming(
        conversationId: String,
        serverSettings: ServerSettings
    ): Flow<StreamingUpdate> = flow {
        try {
            // Load conversation to get model and persona
            val conversation = conversationRepository.getConversation(conversationId)
                ?: throw IllegalStateException("Conversation not found")

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

            // Build streaming request
            val request = ChatCompletionRequest(
                model = model,
                messages = messageHistory,
                stream = true
            )

            // Create a streaming assistant message placeholder
            val streamingMessageId = UUID.randomUUID().toString()
            val placeholderMessage = Message(
                id = streamingMessageId,
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = "",
                createdAt = Instant.now(),
                status = MessageStatus.STREAMING
            )
            conversationRepository.saveMessage(placeholderMessage)

            // Collect streaming events from the API client
            val accumulatedContent = StringBuilder()

            // Channel to decouple database writes from the SSE collection loop.
            // The callbackFlow in OkHttpLlamaApiClient uses trySend with a default
            // buffer of 64. If the collect loop blocks (e.g. on Room writes), the
            // buffer fills and tokens are silently dropped.
            //
            // This channel feeds a dedicated writer coroutine that runs on IO,
            // keeping the SSE collection loop fast and preventing token loss.
            val dbUpdateChannel = Channel<String>(capacity = 64)

            // Wrap collection in a coroutineScope so we can launch the writer.
            kotlinx.coroutines.coroutineScope {
                // Single writer coroutine — processes database updates sequentially
                // on the IO dispatcher. Guarantees ordering of writes.
                val writerJob = launch {
                    for (content in dbUpdateChannel) {
                        withContext(Dispatchers.IO) {
                            conversationRepository.updateMessageContentAndStatus(
                                id = streamingMessageId,
                                content = content,
                                status = MessageStatus.STREAMING
                            )
                        }
                    }
                }

                client.chatCompletionStream(request).collect { event ->
                when (event) {
                    is ChatStreamEvent.TextDelta -> {
                        accumulatedContent.append(event.text)
                        val currentContent = accumulatedContent.toString()
                        // Non-blocking send to the writer coroutine.
                        // If the channel is full, we drop this intermediate snapshot
                        // (the next delta will have a newer snapshot).
                        dbUpdateChannel.trySend(currentContent)
                        emit(StreamingUpdate.TextAppended(streamingMessageId, currentContent))
                    }

                    is ChatStreamEvent.Complete -> {
                        // Close the channel so the writer finishes pending updates
                        dbUpdateChannel.close()
                        writerJob.join()

                        val finalContent = accumulatedContent.toString()
                        val finalMessage = Message(
                            id = streamingMessageId,
                            conversationId = conversationId,
                            role = MessageRole.ASSISTANT,
                            content = finalContent,
                            createdAt = Instant.now(),
                            status = MessageStatus.COMPLETE
                        )
                        // Final write with COMPLETE status
                        conversationRepository.updateMessageContentAndStatus(
                            id = streamingMessageId,
                            content = finalContent,
                            status = MessageStatus.COMPLETE
                        )
                        emit(StreamingUpdate.Complete(streamingMessageId, finalMessage))
                    }

                    is ChatStreamEvent.Stopped -> {
                        dbUpdateChannel.close()
                        writerJob.join()

                        val partialContent = accumulatedContent.toString()
                        conversationRepository.updateMessageContentAndStatus(
                            id = streamingMessageId,
                            content = partialContent,
                            status = MessageStatus.STOPPED
                        )
                        emit(StreamingUpdate.Stopped(
                            streamingMessageId,
                            Message(
                                id = streamingMessageId,
                                conversationId = conversationId,
                                role = MessageRole.ASSISTANT,
                                content = partialContent,
                                createdAt = Instant.now(),
                                status = MessageStatus.STOPPED
                            )
                        ))
                    }

                    is ChatStreamEvent.Error -> {
                        dbUpdateChannel.close()
                        writerJob.join()

                        val partialContent = accumulatedContent.toString()
                        val userFriendlyMessage = mapErrorToUserMessage(event.message)
                        val technicalDetail = event.message.take(200)

                        if (event.keepPartial) {
                            conversationRepository.updateMessageContentAndStatus(
                                id = streamingMessageId,
                                content = partialContent,
                                status = MessageStatus.ERROR
                            )
                        } else {
                            conversationRepository.updateMessageContentAndStatus(
                                id = streamingMessageId,
                                content = "",
                                status = MessageStatus.ERROR
                            )
                        }

                        emit(StreamingUpdate.Error(
                            messageId = streamingMessageId,
                            userMessage = userFriendlyMessage,
                            technicalDetail = technicalDetail,
                            partialContent = if (event.keepPartial) partialContent.takeIf { it.isNotEmpty() } else null,
                            isRetryable = true
                        ))
                    }
                }
            }
            }

        } catch (e: LlamaApiException) {
            emit(StreamingUpdate.Error(
                messageId = "unknown",
                userMessage = mapLlamaApiErrorToUserMessage(e),
                technicalDetail = e.message?.take(200),
                isRetryable = e.code !in listOf(401, 403)
            ))
        } catch (e: java.net.ConnectException) {
            emit(StreamingUpdate.Error(
                messageId = "unknown",
                userMessage = "Cannot reach server. Check your connection.",
                technicalDetail = e.javaClass.simpleName,
                isRetryable = true
            ))
        } catch (e: java.net.SocketTimeoutException) {
            emit(StreamingUpdate.Error(
                messageId = "unknown",
                userMessage = "Request timed out. The server may be busy.",
                technicalDetail = e.javaClass.simpleName,
                isRetryable = true
            ))
        } catch (e: Exception) {
            val detail = e.message?.take(200) ?: e.javaClass.simpleName
            emit(StreamingUpdate.Error(
                messageId = "unknown",
                userMessage = "Unexpected error",
                technicalDetail = detail,
                isRetryable = true
            ))
        }
    }

    /**
     * Cancel an ongoing streaming request by marking the message as STOPPED.
     *
     * @param messageId The ID of the streaming message to stop.
     */
    suspend fun stopStreaming(messageId: String) {
        conversationRepository.updateMessageStatus(messageId, MessageStatus.STOPPED)
    }

    /**
     * Build the message history for a chat request.
     *
     * Includes the system prompt and all existing messages in the conversation.
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

    /**
     * Map a raw error message from the streaming API to a user-friendly message.
     */
    private fun mapErrorToUserMessage(rawMessage: String): String {
        return when {
            rawMessage.contains("unauthorized", ignoreCase = true) ||
            rawMessage.contains("401", ignoreCase = true) ->
                "Authentication failed. Check your API key."
            rawMessage.contains("forbidden", ignoreCase = true) ||
            rawMessage.contains("403", ignoreCase = true) ->
                "Access forbidden. Check your API key and permissions."
            rawMessage.contains("timeout", ignoreCase = true) ->
                "Request timed out. The server may be busy."
            rawMessage.contains("connection", ignoreCase = true) ||
            rawMessage.contains("refused", ignoreCase = true) ||
            rawMessage.contains("unreachable", ignoreCase = true) ->
                "Cannot reach server. Check your connection."
            else -> "Streaming interrupted. Partial response may be available."
        }
    }

    /**
     * Map a [LlamaApiException] to a user-friendly error message.
     */
    private fun mapLlamaApiErrorToUserMessage(e: LlamaApiException): String {
        return when (e.code) {
            401 -> "Authentication failed. Check your API key."
            403 -> "Access forbidden."
            404 -> "Model not found on server."
            429 -> "Rate limited. Please try again later."
            in 500..599 -> "Server error (${e.code}). Please try again."
            else -> "Request failed (${e.code})"
        }
    }
}
