package com.aiapuri.core.model

/**
 * Role of a participant in a chat message.
 *
 * Version 1 uses only SYSTEM, USER, and ASSISTANT.
 * No TOOL role is included.
 */
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}
