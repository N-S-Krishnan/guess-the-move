package com.chessmind.api.repository

import com.chessmind.api.entity.Annotation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AnnotationRepository : JpaRepository<Annotation, UUID> {
    fun findTopBySessionIdOrderByCreatedAtDesc(sessionId: UUID): Annotation?
    fun findTopBySessionIdAndFenOrderByCreatedAtDesc(sessionId: UUID, fen: String): Annotation?
    fun findTopBySessionIdAndFenAndMoveUciIsNullOrderByCreatedAtDesc(sessionId: UUID, fen: String): Annotation?
    fun findAllBySessionId(sessionId: UUID): List<Annotation>
    fun findFirstBySessionIdAndFenAndMoveUciIsNotNullOrderByCreatedAtAsc(sessionId: UUID, fen: String): Annotation?
}
