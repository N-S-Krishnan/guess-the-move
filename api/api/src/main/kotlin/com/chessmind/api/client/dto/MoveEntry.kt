package com.chessmind.api.client.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class MoveEntry(
    val san: String,
    val uci: String,
    @JsonProperty("fen_after") val fenAfter: String,
)
