package com.chessmind.api.dto

data class GuessResponse(
    val correct: Boolean,
    val correctMove: String?,
    val nextFen: String?,
    val fenAfterPlayer: String?,
)