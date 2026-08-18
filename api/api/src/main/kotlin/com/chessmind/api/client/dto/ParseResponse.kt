package com.chessmind.api.client.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ParseResponse(
    val white: String,
    val black: String,
    val event: String,
    val date: String,
    val result: String,
    @JsonProperty("ply_count") val plyCount: Int,
    @JsonProperty("full_move_count") val fullMoveCount: Int,
    @JsonProperty("starting_fen") val startingFen: String,
    val moves: List<MoveEntry>,
)
