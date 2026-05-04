package com.chessmind.api.client.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ValidateRequest(
    val fen: String,
    @JsonProperty("uci_move") val uciMove: String,
)
