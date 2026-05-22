package com.chessmind.api.client.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ExportAnnotationNode(
    val fen: String,
    @JsonProperty("from_fen") val fromFen: String? = null,
    @JsonProperty("move_uci") val moveUci: String? = null,
    @JsonProperty("move_san") val moveSan: String? = null,
    val comment: String? = null,
    val symbol: String? = null,
)
