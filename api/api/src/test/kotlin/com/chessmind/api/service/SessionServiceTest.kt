package com.chessmind.api.service

import com.chessmind.api.client.AnalysisClient
import com.chessmind.api.client.dto.MoveEntry
import com.chessmind.api.client.dto.ParseResponse
import com.chessmind.api.entity.StudySession
import com.chessmind.api.exception.AnalysisException
import com.chessmind.api.repository.StudySessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.UUID
import java.util.concurrent.TimeUnit

class SessionServiceTest {

    private val analysisClient: AnalysisClient = mockk()
    private val sessionRepository: StudySessionRepository = mockk()
    private val valueOps: ValueOperations<String, Any> = mockk()
    private val redisTemplate: RedisTemplate<String, Any> = mockk<RedisTemplate<String, Any>>().also {
        every { it.opsForValue() } returns valueOps
    }

    private val service = SessionService(analysisClient, sessionRepository, redisTemplate)

    private val sampleParsed = ParseResponse(
        white = "Magnus Carlsen",
        black = "Fabiano Caruana",
        event = "Test Event",
        date = "2024.01.01",
        result = "1-0",
        plyCount = 14,
        fullMoveCount = 7,
        startingFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        moves = listOf(MoveEntry("e4", "e2e4", "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")),
    )

    private fun savedSessionFor(id: UUID = UUID.randomUUID()) = StudySession(
        id = id,
        pgnRaw = "1. e4",
        white = sampleParsed.white,
        black = sampleParsed.black,
        event = sampleParsed.event,
    )

    @Test
    fun `createSession returns correct response on happy path`() = runTest {
        val session = savedSessionFor()
        coEvery { analysisClient.parse(any()) } returns sampleParsed
        every { sessionRepository.save(any()) } returns session
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        val result = service.createSession("1. e4")

        assertEquals(session.id.toString(), result.id)
        assertEquals("Magnus Carlsen", result.white)
        assertEquals("Fabiano Caruana", result.black)
        assertEquals(14, result.plyCount)
    }

    @Test
    fun `createSession persists correct entity fields`() = runTest {
        val entitySlot = slot<StudySession>()
        val session = savedSessionFor()
        coEvery { analysisClient.parse(any()) } returns sampleParsed
        every { sessionRepository.save(capture(entitySlot)) } returns session
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        service.createSession("1. e4")

        with(entitySlot.captured) {
            assertEquals("1. e4", pgnRaw)
            assertEquals("Magnus Carlsen", white)
            assertEquals("Fabiano Caruana", black)
            assertEquals("pending_setup", status)
        }
    }

    @Test
    fun `createSession caches parsed response in Redis with 24 hour TTL`() = runTest {
        val session = savedSessionFor()
        coEvery { analysisClient.parse(any()) } returns sampleParsed
        every { sessionRepository.save(any()) } returns session
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        service.createSession("1. e4")

        coVerify {
            valueOps.set(
                "session:${session.id}:state",
                sampleParsed,
                24L,
                TimeUnit.HOURS,
            )
        }
    }

    @Test
    fun `createSession propagates AnalysisException when parse fails`() = runTest {
        coEvery { analysisClient.parse(any()) } throws AnalysisException("bad pgn")

        var thrown: Throwable? = null
        try {
            service.createSession("garbage")
        } catch (e: AnalysisException) {
            thrown = e
        }

        assertNotNull(thrown)
        assertInstanceOf(AnalysisException::class.java, thrown)
    }
}
