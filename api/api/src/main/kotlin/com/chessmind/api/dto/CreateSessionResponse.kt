package com.chessmind.api.dto

data class CreateSessionResponse(
    val id: String,
    val white: String,
    val black: String,
    val plyCount: Int,
)
