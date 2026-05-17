package com.aiapuri.data.conversation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aiapuri.data.persona.PersonaEntity

/**
 * Room entity representing a conversation.
 *
 * Maps directly to [com.aiapuri.core.model.Conversation].
 */
@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = PersonaEntity::class,
            parentColumns = ["id"],
            childColumns = ["persona_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["persona_id"]),
        Index(value = ["updated_at"])
    ]
)
data class ConversationEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long, // epoch seconds

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long, // epoch seconds

    @ColumnInfo(name = "model")
    val model: String,

    @ColumnInfo(name = "persona_id")
    val personaId: String? = null,

    @ColumnInfo(name = "system_prompt_snapshot")
    val systemPromptSnapshot: String? = null
)
