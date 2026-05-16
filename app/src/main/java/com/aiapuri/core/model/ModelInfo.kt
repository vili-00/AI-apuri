package com.aiapuri.core.model

/**
 * Information about a model available on the llama.cpp server.
 */
data class ModelInfo(
    val id: String,
    val displayName: String = id
)
