package com.aiapuri.data.conversation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single chat message.
 *
 * Maps directly to [com.aiapuri.core.model.Message].
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["conversation_id", "created_at"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @ColumnInfo(name = "role")
    val role: String, // "SYSTEM", "USER", "ASSISTANT"

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long, // epoch seconds

    @ColumnInfo(name = "status")
    val status: String // "PENDING", "STREAMING", "COMPLETE", "STOPPED", "ERROR"
)
