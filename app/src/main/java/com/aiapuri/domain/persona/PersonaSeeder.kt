package com.aiapuri.domain.persona

import com.aiapuri.core.model.Persona
import com.aiapuri.data.persona.PersonaRepository
import java.util.UUID

/**
 * Seeds the default personas into the database on first launch.
 *
 * Only runs if the persona table is empty.
 */
class PersonaSeeder(
    private val personaRepository: PersonaRepository
) {

    /**
     * Seed default personas if none exist yet.
     */
    suspend fun seedIfEmpty() {
        val existing = personaRepository.getAllPersonas()
        if (existing.isNotEmpty()) return

        val defaults = defaultPersonas()
        personaRepository.savePersonas(defaults)
    }

    /**
     * The four seed personas for Version 1.
     * First persona is set as the default.
     */
    private fun defaultPersonas(): List<Persona> {
        return listOf(
            Persona(
                id = UUID.randomUUID().toString(),
                name = "General Assistant",
                description = "A helpful, general-purpose assistant.",
                systemPrompt = """
                    Be helpful, clear, and concise. Adapt to the user's language and tone.
                    When uncertain, say so rather than guessing.
                """.trimIndent(),
                isDefault = true
            ),
            Persona(
                id = UUID.randomUUID().toString(),
                name = "Finnish Helper",
                description = "Assists with Finnish language, culture, and translation.",
                systemPrompt = """
                    You are a Finnish-speaking assistant. Respond in Finnish by default,
                    but switch to English if the user writes in English.
                    Help with Finnish grammar, vocabulary, cultural topics, and translation
                    between Finnish and English.
                """.trimIndent(),
                isDefault = false
            ),
            Persona(
                id = UUID.randomUUID().toString(),
                name = "Coding Assistant",
                description = "Helps with programming, debugging, and code review.",
                systemPrompt = """
                    You are a coding assistant. Help the user write, debug, and review code.
                    Provide clear explanations, prefer idiomatic solutions, and include
                    examples when helpful. Ask clarifying questions if the request is ambiguous.
                """.trimIndent(),
                isDefault = false
            ),
            Persona(
                id = UUID.randomUUID().toString(),
                name = "Research Assistant",
                description = "Helps analyze and synthesize information from existing knowledge.",
                systemPrompt = """
                    You are a research assistant. Help the user analyze topics, synthesize
                    information, and think through complex questions. Structure your answers
                    clearly. Acknowledge the limits of your knowledge and avoid speculation.
                    You do not have live web access.
                """.trimIndent(),
                isDefault = false
            )
        )
    }
}
