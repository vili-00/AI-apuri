package com.aiapuri.data.llama.dto

import kotlinx.serialization.Serializable

/**
 * Error response from the llama.cpp server (OpenAI-compatible format).
 */
@Serializable
data class ApiErrorResponse(
    val error: ApiErrorDetail? = null
)

@Serializable
data class ApiErrorDetail(
    val message: String = "",
    val type: String = "",
    val param: String? = null,
    val code: String? = null
)
