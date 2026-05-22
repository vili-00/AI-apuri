package com.aiapuri.data.conversation

import com.aiapuri.core.database.AiapuriDatabase
import com.aiapuri.core.database.ContentEncryptor
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
    private lateinit var encryptor: ContentEncryptor
    private lateinit var repository: DatabaseConversationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AiapuriDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        encryptor = ContentEncryptor(context)
        repository = DatabaseConversationRepository(database, encryptor)
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
    fun `message content is encrypted at rest and returned as plaintext through repository`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "model-x"
        )
        repository.createConversation(conv)

        val originalContent = "Hello world"
        val msg = com.aiapuri.core.model.Message(
            id = "msg-1",
            conversationId = "conv-1",
            role = MessageRole.USER,
            content = originalContent
        )
        repository.saveMessage(msg)

        // Read raw entity from DAO — should be encrypted (marked with prefix)
        val rawEntity = database.messageDao().getMessageById("msg-1")
        assertNotNull(rawEntity)
        assertTrue(
            "Content should be marked encrypted at rest",
            rawEntity!!.content.startsWith(ContentEncryptor.ENCRYPTED_PREFIX)
        )
        assertNotEquals(
            "Content should be encrypted at rest in the database",
            originalContent,
            rawEntity.content
        )

        // Read through repository — should be decrypted to original plaintext
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

    // ==================== Encryption round-trip tests ====================

    @Test
    fun `save USER message then reload returns plaintext`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "model-x"
        )
        repository.createConversation(conv)

        val originalContent = "Hello from user side"
        val msg = com.aiapuri.core.model.Message(
            id = "msg-user",
            conversationId = "conv-1",
            role = MessageRole.USER,
            content = originalContent
        )
        repository.saveMessage(msg)

        // Reload through repository — must return original plaintext
        val messages = repository.observeMessages("conv-1").first()
        assertEquals(1, messages.size)
        assertEquals(originalContent, messages[0].content)
        assertNotEquals(
            "UI must never receive ciphertext",
            encryptor.encrypt(originalContent),
            messages[0].content
        )
    }

    @Test
    fun `save ASSISTANT message then reload returns plaintext`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "model-x"
        )
        repository.createConversation(conv)

        val originalContent = "Hello from assistant side"
        val msg = com.aiapuri.core.model.Message(
            id = "msg-assistant",
            conversationId = "conv-1",
            role = MessageRole.ASSISTANT,
            content = originalContent
        )
        repository.saveMessage(msg)

        // Reload through repository — must return original plaintext
        val messages = repository.observeMessages("conv-1").first()
        assertEquals(1, messages.size)
        assertEquals(originalContent, messages[0].content)
    }

    @Test
    fun `streaming-style updateMessageContentAndStatus also encrypts at rest`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "model-x"
        )
        repository.createConversation(conv)

        // Save initial assistant message (simulates streaming placeholder)
        val placeholder = com.aiapuri.core.model.Message(
            id = "msg-stream",
            conversationId = "conv-1",
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.STREAMING
        )
        repository.saveMessage(placeholder)

        // Update content via updateMessageContentAndStatus (simulates streaming delta)
        val streamingContent = "This is a streaming response"
        repository.updateMessageContentAndStatus(
            id = "msg-stream",
            content = streamingContent,
            status = MessageStatus.STREAMING
        )

        // Verify raw DB content is encrypted (marked with prefix)
        val rawEntity = database.messageDao().getMessageById("msg-stream")
        assertNotNull(rawEntity)
        assertTrue(
            "Streaming content must be marked encrypted at rest",
            rawEntity!!.content.startsWith(ContentEncryptor.ENCRYPTED_PREFIX)
        )
        assertNotEquals(
            "Streaming content must be encrypted at rest",
            streamingContent,
            rawEntity.content
        )

        // Verify repository returns plaintext
        val messages = repository.observeMessages("conv-1").first()
        assertEquals(1, messages.size)
        assertEquals(streamingContent, messages[0].content)
        assertEquals(MessageStatus.STREAMING, messages[0].status)

        // Finalize as COMPLETE
        repository.updateMessageContentAndStatus(
            id = "msg-stream",
            content = streamingContent,
            status = MessageStatus.COMPLETE
        )

        val finalMessages = repository.observeMessages("conv-1").first()
        assertEquals(streamingContent, finalMessages[0].content)
        assertEquals(MessageStatus.COMPLETE, finalMessages[0].status)
    }

    @Test
    fun `legacy plaintext rows do not crash decrypt and are returned as-is`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "model-x"
        )
        repository.createConversation(conv)

        // Insert a plaintext row directly via DAO (simulates legacy data from before encryption)
        val legacyEntity = MessageEntity(
            id = "msg-legacy",
            conversationId = "conv-1",
            role = MessageRole.ASSISTANT.name,
            content = "Legacy plaintext content",
            createdAt = Instant.now().epochSecond,
            status = MessageStatus.COMPLETE.name
        )
        database.messageDao().insertMessage(legacyEntity)

        // Reading through repository must not crash and must return the plaintext as-is
        val messages = repository.observeMessages("conv-1").first()
        assertEquals(1, messages.size)
        assertEquals("Legacy plaintext content", messages[0].content)
    }

    @Test
    fun `ciphertext is never returned to UI domain models`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "model-x"
        )
        repository.createConversation(conv)

        val userContent = "User says hello"
        val assistantContent = "Assistant says hi back"

        repository.saveMessage(
            com.aiapuri.core.model.Message(
                id = "msg-u",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = userContent
            )
        )
        repository.saveMessage(
            com.aiapuri.core.model.Message(
                id = "msg-a",
                conversationId = "conv-1",
                role = MessageRole.ASSISTANT,
                content = assistantContent
            )
        )

        // Get raw encrypted blobs from DB
        val rawEntities = database.messageDao().getMessagesForConversation("conv-1")
        val userEncrypted = rawEntities.find { it.role == MessageRole.USER.name }!!.content
        val assistantEncrypted = rawEntities.find { it.role == MessageRole.ASSISTANT.name }!!.content

        // Get domain messages from repository
        val domainMessages = repository.observeMessages("conv-1").first()

        // Domain messages must NOT contain the raw encrypted blobs
        domainMessages.forEach { msg ->
            assertNotEquals(
                "Domain message content must not be raw ciphertext",
                userEncrypted,
                msg.content
            )
            assertNotEquals(
                "Domain message content must not be raw ciphertext",
                assistantEncrypted,
                msg.content
            )
        }

        // Domain messages must contain the original plaintext
        assertEquals(userContent, domainMessages.find { it.role == MessageRole.USER }!!.content)
        assertEquals(assistantContent, domainMessages.find { it.role == MessageRole.ASSISTANT }!!.content)
    }

    @Test
    fun `both USER and ASSISTANT messages in same conversation are both plaintext after reload`() = runBlocking {
        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "model-x"
        )
        repository.createConversation(conv)

        val userContent = "What is the meaning of life?"
        val assistantContent = "42"

        repository.saveMessage(
            com.aiapuri.core.model.Message(
                id = "msg-user",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = userContent
            )
        )
        repository.saveMessage(
            com.aiapuri.core.model.Message(
                id = "msg-assistant",
                conversationId = "conv-1",
                role = MessageRole.ASSISTANT,
                content = assistantContent
            )
        )

        // Verify both roles return plaintext
        val messages = repository.observeMessages("conv-1").first()
        assertEquals(2, messages.size)
        assertEquals(userContent, messages.find { it.role == MessageRole.USER }!!.content)
        assertEquals(assistantContent, messages.find { it.role == MessageRole.ASSISTANT }!!.content)

        // Verify raw DB values are marked encrypted
        val rawEntities = database.messageDao().getMessagesForConversation("conv-1")
        rawEntities.forEach { entity ->
            assertTrue(
                "Raw DB content must be marked with encrypted prefix",
                entity.content.startsWith(ContentEncryptor.ENCRYPTED_PREFIX)
            )
        }

        // Verify domain messages do NOT contain encrypted prefix
        messages.forEach { msg ->
            assertFalse(
                "Domain message must not contain encrypted prefix",
                msg.content.startsWith(ContentEncryptor.ENCRYPTED_PREFIX)
            )
        }
    }

    // ==================== Encryptor recreation (simulates app restart) ====================

    @Test
    fun `encryptor recreation after save returns plaintext for both roles`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val conv = com.aiapuri.core.model.Conversation(
            id = "conv-1",
            title = "Test",
            model = "model-x"
        )
        repository.createConversation(conv)

        val userContent = "Hello from user"
        val assistantContent = "Hello from assistant"

        repository.saveMessage(
            com.aiapuri.core.model.Message(
                id = "msg-user",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = userContent
            )
        )
        repository.saveMessage(
            com.aiapuri.core.model.Message(
                id = "msg-assistant",
                conversationId = "conv-1",
                role = MessageRole.ASSISTANT,
                content = assistantContent
            )
        )

        // Verify messages are readable immediately
        var messages = repository.observeMessages("conv-1").first()
        assertEquals(userContent, messages.find { it.role == MessageRole.USER }!!.content)
        assertEquals(assistantContent, messages.find { it.role == MessageRole.ASSISTANT }!!.content)

        // Simulate app restart: create a NEW ContentEncryptor instance
        // This tests that the Keystore key is reused, not regenerated
        val newEncryptor = ContentEncryptor(context)
        val newRepository = DatabaseConversationRepository(database, newEncryptor)

        // Reload messages through the NEW repository
        messages = newRepository.observeMessages("conv-1").first()
        assertEquals(2, messages.size)

        // Both roles must return plaintext — NOT ciphertext
        val userMsg = messages.find { it.role == MessageRole.USER }
        val assistantMsg = messages.find { it.role == MessageRole.ASSISTANT }

        assertNotNull(userMsg)
        assertNotNull(assistantMsg)
        assertEquals(userContent, userMsg!!.content)
        assertEquals(assistantContent, assistantMsg!!.content)

        // Verify neither message contains the encrypted prefix
        assertFalse(userMsg.content.startsWith(ContentEncryptor.ENCRYPTED_PREFIX))
        assertFalse(assistantMsg.content.startsWith(ContentEncryptor.ENCRYPTED_PREFIX))

        // Verify neither message is the error placeholder
        assertNotEquals(
            ContentEncryptor.DECRYPT_ERROR_PLACEHOLDER,
            userMsg.content
        )
        assertNotEquals(
            ContentEncryptor.DECRYPT_ERROR_PLACEHOLDER,
            assistantMsg.content
        )
    }

    @Test
    fun `encryptor encrypt then decrypt round-trip preserves content`() = runBlocking {
        val testStrings = listOf(
            "Simple text",
            "Text with emoji 🚀",
            "Line one\nLine two",
            "Special chars: <>&\"'",
            "Unicode: café naïve 日本語"
        )

        testStrings.forEach { original ->
            val encrypted = encryptor.encrypt(original)
            assertNotNull(encrypted)
            assertTrue("Encrypted value must have prefix", encrypted!!.startsWith(ContentEncryptor.ENCRYPTED_PREFIX))
            assertNotEquals("Encrypted value must differ from original", original, encrypted)

            val decrypted = encryptor.decrypt(encrypted)
            assertEquals("Round-trip must preserve content: $original", original, decrypted)
        }
    }

    @Test
    fun `encryptor isEncrypted correctly identifies marked and unmarked values`() = runBlocking {
        val encrypted = encryptor.encrypt("test content")
        assertTrue(encryptor.isEncrypted(encrypted))
        assertFalse(encryptor.isEncrypted("plain text"))
        assertFalse(encryptor.isEncrypted(null))
        assertFalse(encryptor.isEncrypted(""))
    }
}
