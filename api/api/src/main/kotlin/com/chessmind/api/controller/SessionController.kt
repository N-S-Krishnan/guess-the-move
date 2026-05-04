package com.chessmind.api.controller

import com.chessmind.api.dto.CreateSessionRequest
import com.chessmind.api.dto.CreateSessionResponse
import com.chessmind.api.dto.GuessRequest
import com.chessmind.api.dto.GuessResponse
import com.chessmind.api.dto.SetupSessionRequest
import com.chessmind.api.dto.SetupSessionResponse
import com.chessmind.api.dto.SkipResponse
import com.chessmind.api.exception.AnalysisException
import com.chessmind.api.exception.SessionConflictException
import com.chessmind.api.exception.SessionNotFoundException
import com.chessmind.api.service.SessionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sessions")
class SessionController(private val sessionService: SessionService) {

    @PostMapping
    suspend fun createSession(
        @Valid @RequestBody body: CreateSessionRequest,
    ): CreateSessionResponse = sessionService.createSession(body.pgn)

    @PutMapping("/{id}/setup")
    suspend fun setupSession(
        @PathVariable id: UUID,
        @Valid @RequestBody body: SetupSessionRequest,
    ): SetupSessionResponse = sessionService.setupSession(id, body)

    @PostMapping("/{id}/guess")
    suspend fun submitGuess(
        @PathVariable id: UUID,
        @Valid @RequestBody body: GuessRequest,
    ): GuessResponse = sessionService.submitGuess(id, body)

    @PostMapping("/{id}/skip")
    suspend fun skipGuess(@PathVariable id: UUID): SkipResponse = sessionService.skipGuess(id)

    @ExceptionHandler(AnalysisException::class)
    fun handleAnalysisException(ex: AnalysisException): ResponseEntity<Map<String, String>> =
        ResponseEntity.unprocessableEntity()
            .body(mapOf("detail" to (ex.message ?: "Invalid PGN")))

    @ExceptionHandler(SessionNotFoundException::class)
    fun handleSessionNotFound(ex: SessionNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("detail" to (ex.message ?: "Session not found")))

    @ExceptionHandler(SessionConflictException::class)
    fun handleSessionConflict(ex: SessionConflictException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("detail" to (ex.message ?: "Session conflict")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest()
            .body(mapOf("detail" to (ex.message ?: "Invalid request")))

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<Map<String, String>> =
        ResponseEntity.internalServerError()
            .body(mapOf("detail" to (ex.message ?: "Internal server error")))
}
