package com.chessmind.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class SetupSessionRequest(
    @field:NotBlank(message = "playerToGuess must not be blank")
    val playerToGuess: String,

    @field:Min(value = 1, message = "startMoveNumber must be at least 1")
    val startMoveNumber: Int,
)
