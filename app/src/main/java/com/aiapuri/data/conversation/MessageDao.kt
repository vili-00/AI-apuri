package com.aiapuri.data.conversation

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for messages.
 */
@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun observeMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("UPDATE messages SET status = :status, content = :content, created_at = :updatedAt WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: String, content: String = "", updatedAt: Long = 0)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}
