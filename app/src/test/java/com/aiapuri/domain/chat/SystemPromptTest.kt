package com.aiapuri.domain.chat

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [SystemPrompt] composition.
 *
 * Verifies that the base system prompt and persona system prompts are
 * combined correctly for chat requests.
 */
class SystemPromptTest {

    @Test
    fun `compose returns base prompt when no persona`() {
        val result = SystemPrompt.compose(null)
        assertEquals(SystemPrompt.BASE, result)
    }

    @Test
    fun `compose returns base prompt when persona is empty`() {
        val result = SystemPrompt.compose("")
        assertEquals(SystemPrompt.BASE, result)
    }

    @Test
    fun `compose returns base prompt when persona is blank`() {
        val result = SystemPrompt.compose("   ")
        assertEquals(SystemPrompt.BASE, result)
    }

    @Test
    fun `compose appends persona prompt with double newline`() {
        val personaPrompt = "You are a coding expert."
        val result = SystemPrompt.compose(personaPrompt)

        assertTrue(result.startsWith(SystemPrompt.BASE))
        assertTrue(result.contains("\n\n"))
        assertTrue(result.endsWith(personaPrompt))
        assertEquals(SystemPrompt.BASE + "\n\n" + personaPrompt, result)
    }

    @Test
    fun `compose preserves multi-line persona prompt`() {
        val personaPrompt = """
            You are a research assistant.
            Always cite sources.
            Be concise.
        """.trimIndent()

        val result = SystemPrompt.compose(personaPrompt)

        assertTrue(result.contains("You are a research assistant."))
        assertTrue(result.contains("Always cite sources."))
        assertTrue(result.contains("Be concise."))
    }

    @Test
    fun `compose includes base prompt warning about no web access`() {
        val result = SystemPrompt.compose(null)

        assertTrue(result.contains("You do not have web access"))
        // The base prompt mentions "web search" in context of saying it's unavailable
        assertTrue(result.contains("web search is not available"))
        assertFalse(result.contains("tool call"))
    }

    @Test
    fun `compose result identifies as AI-apuri`() {
        val result = SystemPrompt.compose(null)

        assertTrue(result.contains("You are AI-apuri"))
    }

    @Test
    fun `compose with long persona prompt does not truncate`() {
        val longPersona = "A".repeat(5000)
        val result = SystemPrompt.compose(longPersona)

        assertTrue(result.contains(longPersona))
        assertEquals(SystemPrompt.BASE.length + 2 + longPersona.length, result.length)
    }
}
