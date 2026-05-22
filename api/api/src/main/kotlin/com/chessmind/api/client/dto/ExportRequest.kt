package com.chessmind.api.client.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ExportRequest(
    @JsonProperty("pgn_raw") val pgnRaw: String,
    val annotations: List<ExportAnnotationNode>,
)
