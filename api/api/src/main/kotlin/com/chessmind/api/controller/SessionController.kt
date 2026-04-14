package com.chessmind.api.controller

import com.chessmind.api.dto.CreateSessionRequest
import com.chessmind.api.dto.CreateSessionResponse
import com.chessmind.api.exception.AnalysisException
import com.chessmind.api.service.SessionService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/sessions")
class SessionController(private val sessionService: SessionService) {

    @PostMapping
    suspend fun createSession(
        @Valid @RequestBody body: CreateSessionRequest,
    ): CreateSessionResponse = sessionService.createSession(body.pgn)

    @ExceptionHandler(AnalysisException::class)
    fun handleAnalysisException(ex: AnalysisException): ResponseEntity<Map<String, String>> =
        ResponseEntity.unprocessableEntity()
            .body(mapOf("detail" to (ex.message ?: "Invalid PGN")))
}
