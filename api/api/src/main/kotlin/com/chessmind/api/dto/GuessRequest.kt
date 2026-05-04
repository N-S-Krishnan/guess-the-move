package com.chessmind.api.dto

import jakarta.validation.constraints.NotBlank

data class GuessRequest(
    @field:NotBlank(message = "move must not be blank")
    val move: String,
)