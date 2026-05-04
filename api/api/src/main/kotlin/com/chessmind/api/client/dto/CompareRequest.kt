package com.chessmind.api.client.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class CompareRequest(
    val fen: String,
    @JsonProperty("expected_uci") val expectedUci: String,
    @JsonProperty("submitted_uci") val submittedUci: String,
)
