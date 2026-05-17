package com.aiapuri.data.conversation

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for conversations.
 */
@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun observeAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    suspend fun getAllConversations(): List<ConversationEntity>

    @Query("""
        SELECT c.id, c.title, c.updated_at, c.model,
               p.name AS persona_name,
               (SELECT m.content FROM messages m
                WHERE m.conversation_id = c.id
                ORDER BY m.created_at DESC LIMIT 1) AS last_message
        FROM conversations c
        LEFT JOIN personas p ON c.persona_id = p.id
        ORDER BY c.updated_at DESC
    """)
    fun observeConversationSummaries(): Flow<List<ConversationSummaryRow>>

    @Query("UPDATE conversations SET updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTimestamp(id: String, updatedAt: Long)

    @Query("UPDATE conversations SET persona_id = :personaId, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePersona(id: String, personaId: String?, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    /**
     * Update the title of a conversation only if it still has the default placeholder title.
     * This prevents overwriting a user-provided manual rename.
     *
     * @return true if the title was updated, false if the title had already been changed.
     */
    @Query("UPDATE conversations SET title = :newTitle, updated_at = :updatedAt " +
           "WHERE id = :id AND title = :placeholderTitle")
    suspend fun updateTitleIfDefault(
        id: String,
        placeholderTitle: String,
        newTitle: String,
        updatedAt: Long
    ): Int

    /**
     * Row class for conversation summary queries.
     */
    data class ConversationSummaryRow(
        @androidx.room.ColumnInfo(name = "id")
        val id: String,
        @androidx.room.ColumnInfo(name = "title")
        val title: String,
        @androidx.room.ColumnInfo(name = "updated_at")
        val updatedAt: Long,
        @androidx.room.ColumnInfo(name = "model")
        val model: String?,
        @androidx.room.ColumnInfo(name = "persona_name")
        val personaName: String?,
        @androidx.room.ColumnInfo(name = "last_message")
        val lastMessage: String?
    )
}
