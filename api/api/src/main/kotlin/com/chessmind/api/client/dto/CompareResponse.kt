package com.chessmind.api.client.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class CompareResponse(
    val correct: Boolean,
    val legal: Boolean,
    @JsonProperty("submitted_san") val submittedSan: String?,
    @JsonProperty("expected_san") val expectedSan: String,
    @JsonProperty("fen_after") val fenAfter: String,
)
