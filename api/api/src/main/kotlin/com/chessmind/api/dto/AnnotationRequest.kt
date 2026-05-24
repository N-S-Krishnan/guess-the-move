package com.chessmind.api.dto

import jakarta.validation.constraints.NotBlank
//data = data class, meant to hold data.
data class AnnotationRequest(
    @field:NotBlank val fen: String,
    val comment: String? = null,
    val symbol: String? = null,
)
