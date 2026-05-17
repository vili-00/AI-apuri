package com.aiapuri.data.persona

import com.aiapuri.core.database.AiapuriDatabase
import com.aiapuri.core.model.Persona
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

/**
 * Instrumented tests for [DatabasePersonaRepository] using an in-memory Room database.
 */
@RunWith(AndroidJUnit4::class)
class DatabasePersonaRepositoryTest {

    private lateinit var database: AiapuriDatabase
    private lateinit var repository: DatabasePersonaRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AiapuriDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DatabasePersonaRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `save and query persona`() = runBlocking {
        val persona = Persona(
            id = "persona-1",
            name = "General Assistant",
            description = "A helpful general assistant",
            systemPrompt = "You are a helpful assistant.",
            isDefault = true
        )
        repository.savePersona(persona)

        val result = repository.getPersona("persona-1")
        assertNotNull(result)
        assertEquals("General Assistant", result!!.name)
        assertEquals("persona-1", result.id)
        assertTrue(result.isDefault)
    }

    @Test
    fun `getAllPersonas returns all personas`() = runBlocking {
        repository.savePersona(Persona("p1", "Alpha", "desc", "prompt", false))
        repository.savePersona(Persona("p2", "Beta", "desc", "prompt", false))

        val all = repository.getAllPersonas()
        assertEquals(2, all.size)
    }

    @Test
    fun `setDefaultPersona clears previous default`() = runBlocking {
        repository.savePersona(Persona("p1", "First", "desc", "prompt", true))
        repository.savePersona(Persona("p2", "Second", "desc", "prompt", false))

        repository.setDefaultPersona("p2")

        val default = repository.getDefaultPersona()
        assertNotNull(default)
        assertEquals("p2", default!!.id)

        val p1 = repository.getPersona("p1")
        assertNotNull(p1)
        assertFalse(p1!!.isDefault)
    }

    @Test
    fun `deletePersona removes it`() = runBlocking {
        repository.savePersona(Persona("p1", "Test", "desc", "prompt", false))
        repository.deletePersona("p1")

        val result = repository.getPersona("p1")
        assertNull(result)
    }

    @Test
    fun `deleteAllPersonas clears everything`() = runBlocking {
        repository.savePersona(Persona("p1", "A", "desc", "prompt", false))
        repository.savePersona(Persona("p2", "B", "desc", "prompt", false))

        repository.deleteAllPersonas()

        val all = repository.getAllPersonas()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `savePersona replaces existing with same id`() = runBlocking {
        repository.savePersona(Persona("p1", "Original", "old desc", "old prompt", false))
        repository.savePersona(Persona("p1", "Updated", "new desc", "new prompt", true))

        val result = repository.getPersona("p1")
        assertNotNull(result)
        assertEquals("Updated", result!!.name)
        assertEquals("new desc", result.description)
        assertTrue(result.isDefault)
    }

    @Test
    fun `observeAllPersonas is reactive`() = runBlocking {
        var personas = repository.observeAllPersonas().first()
        assertTrue(personas.isEmpty())

        repository.savePersona(Persona("p1", "New", "desc", "prompt", false))

        personas = repository.observeAllPersonas().first()
        assertEquals(1, personas.size)
        assertEquals("New", personas[0].name)
    }
}
