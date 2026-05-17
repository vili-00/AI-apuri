package com.aiapuri.domain.chat

/**
 * Base system prompt for AI-apuri Version 1.
 *
 * Composed with the selected persona's system prompt at request time.
 */
object SystemPrompt {

    /**
     * Base system prompt included in every chat request.
     */
    val BASE = """
You are AI-apuri, a private assistant running through a local model server. Be helpful, accurate, and concise.

You do not have web access in this version. Do not claim that you searched the web or checked live sources. If the user asks for current information, explain that web search is not available in this version and answer from existing knowledge only when appropriate.
""".trimIndent()

    /**
     * Compose the final system prompt from base + persona.
     *
     * @param personaSystemPrompt The persona's custom system prompt, or null.
     * @return The combined system prompt string.
     */
    fun compose(personaSystemPrompt: String?): String {
        return buildString {
            append(BASE)
            if (!personaSystemPrompt.isNullOrBlank()) {
                append("\n\n")
                append(personaSystemPrompt)
            }
        }
    }
}
