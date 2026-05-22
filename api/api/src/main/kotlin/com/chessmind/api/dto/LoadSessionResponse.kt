package com.chessmind.api.dto

data class LoadSessionResponse(
    val id: String,
    val white: String,
    val black: String,
    val event: String,
    val status: String,
    val playerToGuess: String?,
    val startMoveNum: Int?,
    val currentMoveIdx: Int?,
    val currentFen: String?,
    val mode: String?,
    val moves: List<String>?,
    val plyCount: Int?,
)
