package com.chessmind.api.controller

import com.chessmind.api.dto.CreateSessionResponse
import com.chessmind.api.dto.GuessResponse
import com.chessmind.api.dto.SetupSessionResponse
import com.chessmind.api.dto.SkipResponse
import com.chessmind.api.exception.AnalysisException
import com.chessmind.api.exception.SessionConflictException
import com.chessmind.api.exception.SessionNotFoundException
import com.chessmind.api.service.SessionService
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

@WebMvcTest(SessionController::class)
class SessionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var sessionService: SessionService

    private val sampleCreateResponse = CreateSessionResponse(
        id = "some-uuid",
        white = "Magnus Carlsen",
        black = "Fabiano Caruana",
        plyCount = 2,
    )

    private val sampleSetupResponse = SetupSessionResponse(
        fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        moveNumber = 1,
        moves = listOf("e4", "e5", "Nf3"),
    )

    // ── POST /sessions ─────────────────────────────────────────────────────────

    @Test
    fun `POST sessions with valid pgn returns 200 and response body`() {
        coEvery { sessionService.createSession(any()) } returns sampleCreateResponse

        mockMvc.post("/api/v1/sessions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("pgn" to "[Event \"Test\"]\n\n1. e4 e5 *"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value("some-uuid") }
            jsonPath("$.white") { value("Magnus Carlsen") }
            jsonPath("$.black") { value("Fabiano Caruana") }
            jsonPath("$.plyCount") { value(2) }
        }
    }

    @Test
    fun `POST sessions with blank pgn returns 400`() {
        mockMvc.post("/api/v1/sessions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("pgn" to ""))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST sessions with missing pgn field returns 400`() {
        mockMvc.post("/api/v1/sessions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(emptyMap<String, String>())
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST sessions when analysis rejects pgn returns 422`() {
        coEvery { sessionService.createSession(any()) } throws AnalysisException("No game found in PGN input")

        mockMvc.post("/api/v1/sessions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("pgn" to "garbage"))
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.detail") { value("No game found in PGN input") }
        }
    }

    // ── PUT /sessions/{id}/setup ───────────────────────────────────────────────

    @Test
    fun `PUT setup with valid body returns 200 and response body`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.setupSession(id, any()) } returns sampleSetupResponse

        mockMvc.put("/api/v1/sessions/$id/setup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("playerToGuess" to "white", "startMoveNumber" to 1))
        }.andExpect {
            status { isOk() }
            jsonPath("$.fen") { value(sampleSetupResponse.fen) }
            jsonPath("$.moveNumber") { value(1) }
        }
    }

    @Test
    fun `PUT setup with missing playerToGuess returns 400`() {
        val id = UUID.randomUUID()

        mockMvc.put("/api/v1/sessions/$id/setup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("startMoveNumber" to 1))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PUT setup with startMoveNumber less than 1 returns 400`() {
        val id = UUID.randomUUID()

        mockMvc.put("/api/v1/sessions/$id/setup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("playerToGuess" to "white", "startMoveNumber" to 0))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PUT setup when session not found returns 404`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.setupSession(id, any()) } throws SessionNotFoundException("Session $id not found")

        mockMvc.put("/api/v1/sessions/$id/setup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("playerToGuess" to "white", "startMoveNumber" to 1))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { value("Session $id not found") }
        }
    }

    @Test
    fun `PUT setup when session already in progress returns 409`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.setupSession(id, any()) } throws
            SessionConflictException("Session $id is already in 'in_progress' state")

        mockMvc.put("/api/v1/sessions/$id/setup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("playerToGuess" to "white", "startMoveNumber" to 1))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("Session $id is already in 'in_progress' state") }
        }
    }

    @Test
    fun `PUT setup with invalid playerToGuess value returns 400`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.setupSession(id, any()) } throws
            IllegalArgumentException("playerToGuess must be 'white' or 'black'")

        mockMvc.put("/api/v1/sessions/$id/setup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("playerToGuess" to "green", "startMoveNumber" to 1))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("playerToGuess must be 'white' or 'black'") }
        }
    }

    @Test
    fun `PUT setup when Redis state is missing returns 500 with detail`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.setupSession(id, any()) } throws
            IllegalStateException("Session state not found in cache for $id")

        mockMvc.put("/api/v1/sessions/$id/setup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("playerToGuess" to "white", "startMoveNumber" to 1))
        }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.detail") { value("Session state not found in cache for $id") }
        }
    }

    // ── POST /sessions/{id}/guess ──────────────────────────────────────────────

    @Test
    fun `POST guess with correct move returns 200 with correct=true and nextFen`() {
        val id = UUID.randomUUID()
        val response = GuessResponse(correct = true, correctMove = null, nextFen = "some-fen", fenAfterPlayer = "mid-fen")
        coEvery { sessionService.submitGuess(id, any()) } returns response

        mockMvc.post("/api/v1/sessions/$id/guess") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("move" to "e2e4"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.correct") { value(true) }
            jsonPath("$.correctMove") { doesNotExist() }
            jsonPath("$.nextFen") { value("some-fen") }
        }
    }

    @Test
    fun `POST guess with incorrect move returns 200 with correct=false and no correctMove`() {
        val id = UUID.randomUUID()
        val response = GuessResponse(correct = false, correctMove = null, nextFen = null, fenAfterPlayer = null)
        coEvery { sessionService.submitGuess(id, any()) } returns response

        mockMvc.post("/api/v1/sessions/$id/guess") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("move" to "d2d4"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.correct") { value(false) }
            jsonPath("$.correctMove") { doesNotExist() }
            jsonPath("$.nextFen") { doesNotExist() }
        }
    }

    @Test
    fun `POST guess with blank move returns 400`() {
        val id = UUID.randomUUID()

        mockMvc.post("/api/v1/sessions/$id/guess") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("move" to ""))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST guess when session not found returns 404`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.submitGuess(id, any()) } throws
            SessionNotFoundException("Session $id not found")

        mockMvc.post("/api/v1/sessions/$id/guess") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("move" to "e2e4"))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { value("Session $id not found") }
        }
    }

    @Test
    fun `POST guess when session is not in_progress returns 409`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.submitGuess(id, any()) } throws
            SessionConflictException("Session $id is not in 'in_progress' state")

        mockMvc.post("/api/v1/sessions/$id/guess") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("move" to "e2e4"))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("Session $id is not in 'in_progress' state") }
        }
    }

    @Test
    fun `POST guess when session mode is not guess returns 409`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.submitGuess(id, any()) } throws
            SessionConflictException("Session $id is not in 'guess' mode")

        mockMvc.post("/api/v1/sessions/$id/guess") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("move" to "e2e4"))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("Session $id is not in 'guess' mode") }
        }
    }

    @Test
    fun `POST guess when Redis progress is missing returns 500`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.submitGuess(id, any()) } throws
            IllegalStateException("Session progress not found in cache for $id")

        mockMvc.post("/api/v1/sessions/$id/guess") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("move" to "e2e4"))
        }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.detail") { value("Session progress not found in cache for $id") }
        }
    }

    // ── POST /sessions/{id}/skip ───────────────────────────────────────────────

    @Test
    fun `POST skip returns 200 with nextFen when more moves remain`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.skipGuess(id) } returns SkipResponse(nextFen = "some-fen")

        mockMvc.post("/api/v1/sessions/$id/skip").andExpect {
            status { isOk() }
            jsonPath("$.nextFen") { value("some-fen") }
        }
    }

    @Test
    fun `POST skip returns 200 with nextFen=null when game is complete`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.skipGuess(id) } returns SkipResponse(nextFen = null)

        mockMvc.post("/api/v1/sessions/$id/skip").andExpect {
            status { isOk() }
            jsonPath("$.nextFen") { doesNotExist() }
        }
    }

    @Test
    fun `POST skip when session not found returns 404`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.skipGuess(id) } throws SessionNotFoundException("Session $id not found")

        mockMvc.post("/api/v1/sessions/$id/skip").andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { value("Session $id not found") }
        }
    }

    @Test
    fun `POST skip when session is not in_progress returns 409`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.skipGuess(id) } throws
            SessionConflictException("Session $id is not in 'in_progress' state")

        mockMvc.post("/api/v1/sessions/$id/skip").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("Session $id is not in 'in_progress' state") }
        }
    }

    @Test
    fun `POST skip when session mode is not guess returns 409`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.skipGuess(id) } throws
            SessionConflictException("Session $id is not in 'guess' mode")

        mockMvc.post("/api/v1/sessions/$id/skip").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("Session $id is not in 'guess' mode") }
        }
    }

    @Test
    fun `POST skip when Redis progress is missing returns 500`() {
        val id = UUID.randomUUID()
        coEvery { sessionService.skipGuess(id) } throws
            IllegalStateException("Session progress not found in cache for $id")

        mockMvc.post("/api/v1/sessions/$id/skip").andExpect {
            status { isInternalServerError() }
            jsonPath("$.detail") { value("Session progress not found in cache for $id") }
        }
    }
}
