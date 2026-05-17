package com.aiapuri.data.persona

import com.aiapuri.core.database.AiapuriDatabase
import com.aiapuri.core.model.Persona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of [PersonaRepository].
 */
class DatabasePersonaRepository(
    private val database: AiapuriDatabase
) : PersonaRepository {

    private val personaDao: PersonaDao = database.personaDao()

    override fun observeAllPersonas(): Flow<List<Persona>> {
        return personaDao.observeAllPersonas().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllPersonas(): List<Persona> {
        return personaDao.getAllPersonas().map { it.toDomain() }
    }

    override suspend fun getPersona(id: String): Persona? {
        return personaDao.getPersonaById(id)?.toDomain()
    }

    override suspend fun getDefaultPersona(): Persona? {
        return personaDao.getDefaultPersona()?.toDomain()
    }

    override suspend fun savePersona(persona: Persona) {
        personaDao.insertPersona(persona.toEntity())
    }

    override suspend fun savePersonas(personas: List<Persona>) {
        personaDao.insertPersonas(personas.map { it.toEntity() })
    }

    override suspend fun setDefaultPersona(id: String) {
        personaDao.clearAllDefaults()
        personaDao.setAsDefault(id)
    }

    override suspend fun deletePersona(id: String) {
        personaDao.deletePersona(id)
    }

    override suspend fun deleteAllPersonas() {
        personaDao.deleteAllPersonas()
    }

    // ==================== Mapping helpers ====================

    private fun Persona.toEntity(): PersonaEntity {
        return PersonaEntity(
            id = id,
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            isDefault = isDefault
        )
    }

    private fun PersonaEntity.toDomain(): Persona {
        return Persona(
            id = id,
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            isDefault = isDefault
        )
    }
}
