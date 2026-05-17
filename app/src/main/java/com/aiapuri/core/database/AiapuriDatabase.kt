package com.aiapuri.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aiapuri.data.conversation.ConversationDao
import com.aiapuri.data.conversation.ConversationEntity
import com.aiapuri.data.conversation.MessageDao
import com.aiapuri.data.conversation.MessageEntity
import com.aiapuri.data.persona.PersonaDao
import com.aiapuri.data.persona.PersonaEntity

/**
 * Room database for AI-apuri.
 *
 * Uses standard Room with field-level encryption for sensitive content.
 * Message content is encrypted before being stored via the repository layer.
 */
@androidx.room.Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        PersonaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AiapuriDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun personaDao(): PersonaDao

    companion object {
        private const val DATABASE_NAME = "aiapuri.db"
        private var instance: AiapuriDatabase? = null

        /**
         * Build or retrieve the singleton database instance.
         */
        fun build(context: Context): AiapuriDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AiapuriDatabase::class.java,
                    DATABASE_NAME
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
