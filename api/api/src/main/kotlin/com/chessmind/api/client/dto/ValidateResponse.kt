package com.chessmind.api.client.dto

data class ValidateResponse(
    val legal: Boolean,
    val san: String?,
)
