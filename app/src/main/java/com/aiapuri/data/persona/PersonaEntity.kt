package com.aiapuri.data.persona

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a persona.
 *
 * Maps directly to [com.aiapuri.core.model.Persona].
 */
@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String,

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false
)
