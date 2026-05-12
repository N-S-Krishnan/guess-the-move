package com.chessmind.api.client.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ValidateResponse(
    val legal: Boolean,
    val san: String?,
    @JsonProperty("fen_after") val fenAfter: String? = null,
)
