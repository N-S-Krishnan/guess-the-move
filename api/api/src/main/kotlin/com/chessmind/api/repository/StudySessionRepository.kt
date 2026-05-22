package com.chessmind.api.repository

import com.chessmind.api.entity.StudySession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StudySessionRepository : JpaRepository<StudySession, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<StudySession>
}
