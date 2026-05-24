package com.chessmind.api.dto

import jakarta.validation.constraints.NotBlank
//data = data class, meant to hold data.
data class CreateSessionRequest(
    //Bean validation: ensure that field contains characters, return custom message if not
    @field:NotBlank(message = "pgn must not be blank")
    val pgn: String,
)
