package com.chessmind.api.dto

data class AnnotationTreeNode(
    val id: String,
    val san: String,
    val uci: String,
    val fen: String,
    val fromFen: String?,
    val symbol: String?,
    val comment: String?,
    val children: List<AnnotationTreeNode>,
)
