package com.aiapuri.data.conversation

import com.aiapuri.core.model.Conversation
import com.aiapuri.core.model.ConversationSummary
import com.aiapuri.core.model.ConversationWithMessages
import com.aiapuri.core.model.Message
import com.aiapuri.core.model.MessageRole
import kotlinx.coroutines.flow.Flow

/**
 * Repository for conversation and message data.
 *
 * Backed by an encrypted Room database.
 */
interface ConversationRepository {

    /** Observe all conversation summaries (lightweight, for list display). */
    fun observeConversationSummaries(): Flow<List<ConversationSummary>>

    /** Observe messages for a specific conversation. */
    fun observeMessages(conversationId: String): Flow<List<Message>>

    /** Get a single conversation by ID. */
    suspend fun getConversation(id: String): Conversation?

    /** Get all conversations with their messages. */
    suspend fun getConversationWithMessages(id: String): ConversationWithMessages?

    /** Create a new conversation. */
    suspend fun createConversation(conversation: Conversation)

    /** Update the title of a conversation. */
    suspend fun updateConversationTitle(id: String, title: String)

    /**
     * Update the title of a conversation only if it still has the default placeholder title.
     * This prevents overwriting a user-provided manual rename.
     *
     * @return true if the title was updated, false if it had already been renamed.
     */
    suspend fun updateTitleIfDefault(id: String, newTitle: String): Boolean

    /** Update the model for a conversation. */
    suspend fun updateConversationModel(id: String, model: String)

    /** Update the persona for a conversation. */
    suspend fun updateConversationPersona(id: String, personaId: String?)

    /** Save a message to a conversation. */
    suspend fun saveMessage(message: Message)

    /** Save multiple messages. */
    suspend fun saveMessages(messages: List<Message>)

    /** Update the status of a message (e.g. STREAMING → COMPLETE). */
    suspend fun updateMessageStatus(id: String, status: com.aiapuri.core.model.MessageStatus)

    /** Update a message's content and status. */
    suspend fun updateMessageContentAndStatus(
        id: String,
        content: String,
        status: com.aiapuri.core.model.MessageStatus
    )

    /** Delete a conversation and all its messages. */
    suspend fun deleteConversation(id: String)

    /** Delete all conversations and messages. */
    suspend fun deleteAllConversations()
}
