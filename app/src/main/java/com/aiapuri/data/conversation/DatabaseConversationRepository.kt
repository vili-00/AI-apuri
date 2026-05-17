package com.aiapuri.data.conversation

import com.aiapuri.core.database.AiapuriDatabase
import com.aiapuri.core.database.ContentEncryptor
import com.aiapuri.core.model.Conversation
import com.aiapuri.core.model.ConversationSummary
import com.aiapuri.core.model.ConversationWithMessages
import com.aiapuri.core.model.Message
import com.aiapuri.core.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Room-backed implementation of [ConversationRepository].
 *
 * Message content is encrypted at rest using [ContentEncryptor].
 */
class DatabaseConversationRepository(
    private val database: AiapuriDatabase,
    private val encryptor: ContentEncryptor
) : ConversationRepository {

    private val conversationDao: ConversationDao = database.conversationDao()
    private val messageDao: MessageDao = database.messageDao()

    // ==================== Conversations ====================

    override fun observeConversationSummaries(): Flow<List<ConversationSummary>> {
        return conversationDao.observeConversationSummaries().map { rows ->
            rows.map { row ->
                ConversationSummary(
                    id = row.id,
                    title = row.title,
                    updatedAt = Instant.ofEpochSecond(row.updatedAt),
                    lastMessagePreview = null, // Encrypted content not shown in preview
                    model = row.model,
                    personaName = row.personaName
                )
            }
        }
    }

    override suspend fun getConversation(id: String): Conversation? {
        val entity = conversationDao.getConversationById(id) ?: return null
        return entity.toDomain()
    }

    override suspend fun getConversationWithMessages(id: String): ConversationWithMessages? {
        val conversation = getConversation(id) ?: return null
        val messages = messageDao.getMessagesForConversation(id).map { it.toDomain(encryptor) }
        return ConversationWithMessages(conversation, messages)
    }

    override suspend fun createConversation(conversation: Conversation) {
        conversationDao.insertConversation(conversation.toEntity())
    }

    override suspend fun updateConversationTitle(id: String, title: String) {
        val existing = conversationDao.getConversationById(id) ?: return
        val updated = existing.copy(title = title, updatedAt = Instant.now().epochSecond)
        conversationDao.insertConversation(updated)
    }

    override suspend fun updateConversationModel(id: String, model: String) {
        val existing = conversationDao.getConversationById(id) ?: return
        val updated = existing.copy(model = model, updatedAt = Instant.now().epochSecond)
        conversationDao.insertConversation(updated)
    }

    override suspend fun deleteConversation(id: String) {
        messageDao.deleteMessagesForConversation(id)
        conversationDao.deleteConversation(id)
    }

    override suspend fun deleteAllConversations() {
        messageDao.deleteAllMessages()
        conversationDao.deleteAllConversations()
    }

    // ==================== Messages ====================

    override fun observeMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.observeMessagesForConversation(conversationId)
            .map { entities -> entities.map { it.toDomain(encryptor) } }
    }

    override suspend fun saveMessage(message: Message) {
        messageDao.insertMessage(message.toEntity(encryptor))
        // Update conversation timestamp
        conversationDao.updateTimestamp(
            message.conversationId,
            Instant.now().epochSecond
        )
    }

    override suspend fun saveMessages(messages: List<Message>) {
        messageDao.insertMessages(messages.map { it.toEntity(encryptor) })
        // Update conversation timestamp
        if (messages.isNotEmpty()) {
            val convId = messages.first().conversationId
            conversationDao.updateTimestamp(convId, Instant.now().epochSecond)
        }
    }

    override suspend fun updateMessageStatus(id: String, status: MessageStatus) {
        messageDao.updateMessageStatus(id, status.name, updatedAt = 0)
    }

    override suspend fun updateMessageContentAndStatus(
        id: String,
        content: String,
        status: MessageStatus
    ) {
        // Encrypt content before storing
        val encryptedContent = encryptor.encrypt(content) ?: content
        messageDao.updateMessageStatus(id, status.name, encryptedContent, Instant.now().epochSecond)
    }

    // ==================== Mapping helpers ====================

    private fun Conversation.toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id,
            title = title,
            createdAt = createdAt.epochSecond,
            updatedAt = updatedAt.epochSecond,
            model = model,
            personaId = personaId,
            systemPromptSnapshot = systemPromptSnapshot
        )
    }

    private fun ConversationEntity.toDomain(): Conversation {
        return Conversation(
            id = id,
            title = title,
            createdAt = Instant.ofEpochSecond(createdAt),
            updatedAt = Instant.ofEpochSecond(updatedAt),
            model = model,
            personaId = personaId,
            systemPromptSnapshot = systemPromptSnapshot
        )
    }

    private fun Message.toEntity(encryptor: ContentEncryptor): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role.name,
            content = encryptor.encrypt(content) ?: content,
            createdAt = createdAt.epochSecond,
            status = status.name
        )
    }

    private fun MessageEntity.toDomain(encryptor: ContentEncryptor): Message {
        val decryptedContent = encryptor.decrypt(content) ?: content
        return Message(
            id = id,
            conversationId = conversationId,
            role = com.aiapuri.core.model.MessageRole.valueOf(role),
            content = decryptedContent,
            createdAt = Instant.ofEpochSecond(createdAt),
            status = MessageStatus.valueOf(status)
        )
    }
}
