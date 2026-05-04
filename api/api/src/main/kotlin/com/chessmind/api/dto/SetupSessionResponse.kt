package com.chessmind.api.dto

data class SetupSessionResponse(
    val fen: String,
    val moveNumber: Int,
    val moves: List<String>,
)
