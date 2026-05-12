package com.chessmind.api.dto

import jakarta.validation.constraints.NotBlank

data class AddAnalysisMoveRequest(
    @field:NotBlank val uciMove: String,
    @field:NotBlank val fromFen: String,
)
