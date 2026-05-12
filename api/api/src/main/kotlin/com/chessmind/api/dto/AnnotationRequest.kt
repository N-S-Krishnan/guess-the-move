package com.chessmind.api.dto

import jakarta.validation.constraints.NotBlank

data class AnnotationRequest(
    @field:NotBlank val fen: String,
    val comment: String? = null,
    val symbol: String? = null,
)
