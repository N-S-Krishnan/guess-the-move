package com.chessmind.api.dto

import jakarta.validation.constraints.NotBlank

data class CreateSessionRequest(
    @field:NotBlank(message = "pgn must not be blank")
    val pgn: String,
)
