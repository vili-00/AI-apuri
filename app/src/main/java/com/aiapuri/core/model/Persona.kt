package com.aiapuri.core.model

/**
 * A persona defines custom assistant behavior via a system prompt.
 */
data class Persona(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val isDefault: Boolean = false
)
