package com.chessmind.api.dto

data class SessionSummary(
    val id: String,
    val white: String,
    val black: String,
    val event: String,
    val date: String?,
    val site: String?,
    val status: String,
    val playerToGuess: String?,
    val currentMoveIdx: Int?,
    val plyCount: Int?,
    val createdAt: String,
)
