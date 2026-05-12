package com.chessmind.api.dto

data class AddAnalysisMoveResponse(
    val id: String,
    val san: String,
    val fenAfter: String,
)
