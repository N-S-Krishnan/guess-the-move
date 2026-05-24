package com.chessmind.api.dto
//data = data class, meant to hold data.
data class AddAnalysisMoveResponse(
    val id: String,
    val san: String,
    val fenAfter: String,
)
