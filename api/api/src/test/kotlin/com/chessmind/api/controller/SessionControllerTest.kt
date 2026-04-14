package com.chessmind.api.controller

import com.chessmind.api.dto.CreateSessionResponse
import com.chessmind.api.exception.AnalysisException
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

@WebMvcTest(SessionController::class)
class SessionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var sessionService: SessionService

    private val sampleResponse = CreateSessionResponse(
        id = "some-uuid",
        white = "Magnus Carlsen",
        black = "Fabiano Caruana",
        plyCount = 2,
    )

    @Test
    fun `POST sessions with valid pgn returns 200 and response body`() {
        coEvery { sessionService.createSession(any()) } returns sampleResponse

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
}
