package com.aiapuri.data.persona

import com.aiapuri.core.database.AiapuriDatabase
import com.aiapuri.core.model.Persona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for persona data.
 *
 * Backed by an encrypted Room database.
 */
interface PersonaRepository {

    /** Observe all personas. */
    fun observeAllPersonas(): Flow<List<Persona>>

    /** Get all personas. */
    suspend fun getAllPersonas(): List<Persona>

    /** Get a persona by ID. */
    suspend fun getPersona(id: String): Persona?

    /** Get the default persona. */
    suspend fun getDefaultPersona(): Persona?

    /** Save or update a persona. */
    suspend fun savePersona(persona: Persona)

    /** Save multiple personas. */
    suspend fun savePersonas(personas: List<Persona>)

    /** Set a persona as the default (unsets others). */
    suspend fun setDefaultPersona(id: String)

    /** Delete a persona. */
    suspend fun deletePersona(id: String)

    /** Delete all personas. */
    suspend fun deleteAllPersonas()

    /** Count total personas (for diagnostics). */
    suspend fun countPersonas(): Int
}
