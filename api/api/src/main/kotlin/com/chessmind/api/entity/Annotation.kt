package com.chessmind.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "annotations")
class Annotation(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "session_id", nullable = false)
    val sessionId: UUID,

    @Column(nullable = false, columnDefinition = "TEXT")
    val fen: String,

    @Column(name = "from_fen", columnDefinition = "TEXT")
    val fromFen: String? = null,

    @Column(name = "move_uci", length = 10)
    val moveUci: String? = null,

    @Column(name = "move_san", length = 10)
    val moveSan: String? = null,

    @Column(columnDefinition = "TEXT")
    var comment: String? = null,

    @Column(length = 5)
    var symbol: String? = null,

    @Column(name = "parent_id")
    val parentId: UUID? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
