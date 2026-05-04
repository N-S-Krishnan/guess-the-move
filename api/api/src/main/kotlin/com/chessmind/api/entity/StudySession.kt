package com.chessmind.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "study_sessions")
class StudySession(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "pgn_raw", columnDefinition = "TEXT", nullable = false)
    val pgnRaw: String,

    @Column(nullable = false)
    val white: String,

    @Column(nullable = false)
    val black: String,

    @Column(nullable = false)
    val event: String,

    @Column(nullable = false)
    var status: String = "pending_setup",

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "player_to_guess", nullable = true)
    var playerToGuess: String? = null,

    @Column(name = "start_move_num", nullable = true)
    var startMoveNum: Int? = null,

    @Column(name = "current_move_idx", nullable = true)
    var currentMoveIdx: Int? = null,
)
