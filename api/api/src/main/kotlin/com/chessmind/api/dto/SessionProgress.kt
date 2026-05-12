package com.chessmind.api.dto

data class SessionProgress(
    val currentFen: String,
    val moveIndex: Int,
    val mode: String,
    val analysisFens: List<String> = emptyList(),
)
