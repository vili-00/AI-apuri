package com.aiapuri.core.model

/**
 * Server connection settings for the llama.cpp backend.
 */
data class ServerSettings(
    val baseUrl: String = "",
    val apiKey: String? = null,
    val allowNoApiKey: Boolean = false,
    val defaultModel: String? = null
)
