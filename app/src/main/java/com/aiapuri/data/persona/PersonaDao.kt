package com.aiapuri.data.persona

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for personas.
 */
@Dao
interface PersonaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersona(persona: PersonaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonas(personas: List<PersonaEntity>)

    @Query("SELECT * FROM personas ORDER BY name ASC")
    fun observeAllPersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas ORDER BY name ASC")
    suspend fun getAllPersonas(): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getPersonaById(id: String): PersonaEntity?

    @Query("SELECT * FROM personas WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultPersona(): PersonaEntity?

    @Query("UPDATE personas SET is_default = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE personas SET is_default = 1 WHERE id = :id")
    suspend fun setAsDefault(id: String)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun deletePersona(id: String)

    @Query("DELETE FROM personas")
    suspend fun deleteAllPersonas()

    @Query("SELECT COUNT(*) FROM personas")
    suspend fun countPersonas(): Int
}
