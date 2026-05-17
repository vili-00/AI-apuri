package com.aiapuri.data.conversation

import com.aiapuri.core.database.AiapuriDatabase
import com.aiapuri.core.model.MessageRole
import com.aiapuri.core.model.MessageStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Instrumented tests for [DatabaseConversationRepository] using an in-memory Room database.
 *
 * Runs on Android test runtime so we have access to Context and Keystore.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseConversationRepositoryTest {

    private lateinit var database: AiapuriDatabase
    private lateinit var repository: DatabaseConversationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AiapuriDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DatabaseConversationRepository(database, com.aiapuri.core.database.ContentEncryptor(context))
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Conversation CRUD ====================

    @Test
    fun `create and observe conversation`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test Chat",
            model = "qwen3.6-27b"
        )
        repository.createConversation(conv)

        val result = repository.getConversation("conv-1")
        assertNotNull(result)
        assertEquals("conv-1", result!!.id)
        assertEquals("Test Chat", result.title)
        assertEquals("qwen3.6-27b", result.model)
    }

    @Test
    fun `delete conversation also deletes its messages`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "model-x"
        )
        repository.createConversation(conv)

        val msg = com.aiapuri.core.model.Message(
            id = "msg-1",
            conversationId = "conv-1",
            role = MessageRole.USER,
            content = "Hello"
        )
        repository.saveMessage(msg)

        repository.deleteConversation("conv-1")

        val remainingConv = repository.getConversation("conv-1")
        val remainingMsgs = repository.observeMessages("conv-1").first()

        assertNull(remainingConv)
        assertTrue(remainingMsgs.isEmpty())
    }

    @Test
    fun `conversations are ordered by updatedAt descending`() = runBlocking {
        val older = com.aiapuri.core.model.Conversation(
            id = "conv-old",
            title = "Old",
            model = "model-x"
        )
        repository.createConversation(older)

        val newer = com.aiapuri.core.model.Conversation(
            id = "conv-new",
            title = "New",
            model = "model-x"
        )
        repository.createConversation(newer)

        val summaries = repository.observeConversationSummaries().first()
        assertEquals(2, summaries.size)
        assertEquals("conv-new", summaries[0].id)
        assertEquals("conv-old", summaries[1].id)
    }

    // ==================== Message CRUD ====================

    @Test
    fun `save and observe messages`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "model-x"
        )
        repository.createConversation(conv)

        val msg1 = com.aiapuri.core.model.Message(
            id = "msg-1",
            conversationId = "conv-1",
            role = MessageRole.USER,
            content = "Hello"
        )
        val msg2 = com.aiapuri.core.model.Message(
            id = "msg-2",
            conversationId = "conv-1",
            role = MessageRole.ASSISTANT,
            content = "Hi there!"
        )

        repository.saveMessage(msg1)
        repository.saveMessage(msg2)

        val messages = repository.observeMessages("conv-1").first()
        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
    }

    @Test
    fun `message content is encrypted at rest`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "model-x"
        )
        repository.createConversation(conv)

        val originalContent = "Secret message content"
        val msg = com.aiapuri.core.model.Message(
            id = "msg-1",
            conversationId = "conv-1",
            role = MessageRole.USER,
            content = originalContent
        )
        repository.saveMessage(msg)

        // Read raw entity from DAO — should be encrypted
        val rawEntity = database.messageDao().getMessageById("msg-1")
        assertNotNull(rawEntity)
        assertNotEquals(
            "Content should be encrypted in the database",
            originalContent,
            rawEntity!!.content
        )

        // Read through repository — should be decrypted
        val messages = repository.observeMessages("conv-1").first()
        assertEquals(1, messages.size)
        assertEquals(originalContent, messages[0].content)
    }

    @Test
    fun `update message status`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "model-x"
        )
        repository.createConversation(conv)

        val msg = com.aiapuri.core.model.Message(
            id = "msg-1",
            conversationId = "conv-1",
            role = MessageRole.ASSISTANT,
            content = "partial",
            status = MessageStatus.STREAMING
        )
        repository.saveMessage(msg)

        repository.updateMessageStatus("msg-1", MessageStatus.COMPLETE)

        val messages = repository.observeMessages("conv-1").first()
        assertEquals(MessageStatus.COMPLETE, messages[0].status)
    }

    @Test
    fun `conversation summaries are reactive`() = runBlocking {
        var summaries = repository.observeConversationSummaries().first()
        assertTrue(summaries.isEmpty())

        repository.createConversation(
            com.aiapuri.core.model.Conversation(
                id = "conv-1",
                title = "New Chat",
                model = "model-x"
            )
        )

        summaries = repository.observeConversationSummaries().first()
        assertEquals(1, summaries.size)
        assertEquals("conv-1", summaries[0].id)
    }

    @Test
    fun `deleteAllConversations clears everything`() = runBlocking {
        repository.createConversation(
            com.aiapuri.core.model.Conversation(id = "c1", title = "A", model = "m")
        )
        repository.createConversation(
            com.aiapuri.core.model.Conversation(id = "c2", title = "B", model = "m")
        )

        repository.deleteAllConversations()

        val summaries = repository.observeConversationSummaries().first()
        assertTrue(summaries.isEmpty())
    }

    @Test
    fun `updateConversationTitle works`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Original",
            model = "model-x"
        )
        repository.createConversation(conv)

        repository.updateConversationTitle("conv-1", "Renamed")

        val updated = repository.getConversation("conv-1")
        assertNotNull(updated)
        assertEquals("Renamed", updated!!.title)
    }

    @Test
    fun `updateConversationModel works`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "old-model"
        )
        repository.createConversation(conv)

        repository.updateConversationModel("conv-1", "new-model")

        val updated = repository.getConversation("conv-1")
        assertNotNull(updated)
        assertEquals("new-model", updated!!.model)
    }
}
