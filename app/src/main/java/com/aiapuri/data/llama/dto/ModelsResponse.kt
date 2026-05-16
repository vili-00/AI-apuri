package com.aiapuri.data.llama.dto

import kotlinx.serialization.Serializable

/**
 * Response body from /v1/models.
 */
@Serializable
data class ModelsResponse(
    val data: List<ModelObject>
)

@Serializable
data class ModelObject(
    val id: String,
    val `object`: String = "model",
    val ownedBy: String = ""
)
