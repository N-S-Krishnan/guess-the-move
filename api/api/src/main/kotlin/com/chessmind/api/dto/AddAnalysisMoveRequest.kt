package com.chessmind.api.dto

import jakarta.validation.constraints.NotBlank
//data = data class, meant to hold data.
data class AddAnalysisMoveRequest(
    @field:NotBlank val uciMove: String,
    @field:NotBlank val fromFen: String,
)
