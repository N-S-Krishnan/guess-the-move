package com.chessmind.api.service

import com.chessmind.api.client.AnalysisClient
import com.chessmind.api.client.dto.CompareResponse
import com.chessmind.api.client.dto.ExportAnnotationNode
import com.chessmind.api.client.dto.ExportRequest
import com.chessmind.api.client.dto.ExportResponse
import com.chessmind.api.client.dto.MoveEntry
import com.chessmind.api.client.dto.ParseResponse
import com.chessmind.api.client.dto.ValidateResponse
import com.chessmind.api.dto.LoadSessionResponse
import com.chessmind.api.dto.AddAnalysisMoveRequest
import com.chessmind.api.dto.AnnotationRequest
import com.chessmind.api.dto.GuessRequest
import com.chessmind.api.dto.ResumeResponse
import com.chessmind.api.dto.SessionProgress
import com.chessmind.api.dto.SetupSessionRequest
import com.chessmind.api.dto.SkipResponse
import com.chessmind.api.entity.Annotation
import com.chessmind.api.entity.StudySession
import com.chessmind.api.exception.AnalysisException
import com.chessmind.api.exception.SessionConflictException
import com.chessmind.api.exception.SessionNotFoundException
import com.chessmind.api.repository.AnnotationRepository
import com.chessmind.api.repository.StudySessionRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.Optional
import java.util.UUID
import java.util.concurrent.TimeUnit

class SessionServiceTest {

    private val analysisClient: AnalysisClient = mockk()
    private val sessionRepository: StudySessionRepository = mockk()
    private val annotationRepository: AnnotationRepository = mockk()
    private val valueOps: ValueOperations<String, Any> = mockk()
    private val redisTemplate: RedisTemplate<String, Any> = mockk<RedisTemplate<String, Any>>().also {
        every { it.opsForValue() } returns valueOps
    }
    // Real ObjectMapper with Kotlin module — mirrors the Spring auto-configured bean.
    private val objectMapper = jacksonObjectMapper()

    private val service = SessionService(analysisClient, sessionRepository, annotationRepository, redisTemplate, objectMapper)

    // 7 full moves (14 plies): moves[0..13]
    private val sampleMoves = (0 until 14).map { i ->
        MoveEntry(san = "move$i", uci = "uci$i", fenAfter = "fen_after_ply_$i")
    }

    private val sampleParsed = ParseResponse(
        white = "Magnus Carlsen",
        black = "Fabiano Caruana",
        event = "Test Event",
        date = "2024.01.01",
        result = "1-0",
        plyCount = 14,
        fullMoveCount = 7,
        startingFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        moves = sampleMoves,
    )

    private fun mockValidateLegal(
        fen: String,
        uci: String,
        san: String = "san_$uci",
        fenAfter: String = "fen_after_$uci",
    ) {
        coEvery { analysisClient.validate(fen, uci) } returns ValidateResponse(legal = true, san = san, fenAfter = fenAfter)
    }

    private fun mockValidateIllegal(fen: String, uci: String) {
        coEvery { analysisClient.validate(fen, uci) } returns ValidateResponse(legal = false, san = null, fenAfter = null)
    }

    private fun mockCompare(
        fen: String,
        submittedUci: String,
        expectedUci: String,
        correct: Boolean,
        expectedSan: String = "san_$expectedUci",
        fenAfter: String = "fen_after_expected",
    ) {
        coEvery { analysisClient.compare(fen, submittedUci, expectedUci) } returns CompareResponse(
            correct = correct,
            legal = true,
            submittedSan = if (correct) expectedSan else "wrong_san",
            expectedSan = expectedSan,
            fenAfter = fenAfter,
        )
    }

    private fun savedSessionFor(id: UUID = UUID.randomUUID()) = StudySession(
        id = id,
        pgnRaw = "1. e4",
        white = sampleParsed.white,
        black = sampleParsed.black,
        event = sampleParsed.event,
    )

    // ── createSession ──────────────────────────────────────────────────────────

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

    // ── setupSession ───────────────────────────────────────────────────────────

    @Test
    fun `setupSession includes all SAN moves in the response`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(any()) } returns session
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        val result = service.setupSession(session.id, SetupSessionRequest("white", 1))

        assertEquals(sampleMoves.map { it.san }, result.moves)
    }

    @Test
    fun `setupSession for white at move 1 returns startingFen`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(any()) } returns session
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        val result = service.setupSession(session.id, SetupSessionRequest("white", 1))

        assertEquals(sampleParsed.startingFen, result.fen)
        assertEquals(1, result.moveNumber)
    }

    @Test
    fun `setupSession for white at move 3 returns fen after ply 4`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(any()) } returns session
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        // White's 3rd move = ply index 4; FEN before it = moves[3].fenAfter
        val result = service.setupSession(session.id, SetupSessionRequest("white", 3))

        assertEquals("fen_after_ply_3", result.fen)
        assertEquals(3, result.moveNumber)
    }

    @Test
    fun `setupSession for black at move 1 returns fen after white ply 0`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(any()) } returns session
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        // Black's reply to move 1 = ply index 1; FEN before it = moves[0].fenAfter
        val result = service.setupSession(session.id, SetupSessionRequest("black", 1))

        assertEquals("fen_after_ply_0", result.fen)
        assertEquals(1, result.moveNumber)
    }

    @Test
    fun `setupSession updates session entity to in_progress with correct fields`() = runTest {
        val session = savedSessionFor()
        val savedSlot = slot<StudySession>()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(capture(savedSlot)) } returns session
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        service.setupSession(session.id, SetupSessionRequest("white", 2))

        with(savedSlot.captured) {
            assertEquals("in_progress", status)
            assertEquals("white", playerToGuess)
            assertEquals(2, startMoveNum)
            assertEquals(2, currentMoveIdx) // white's 2nd move = ply 2*(2-1) = 2
        }
    }

    @Test
    fun `setupSession writes progress to Redis with 24 hour TTL`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(any()) } returns session
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        service.setupSession(session.id, SetupSessionRequest("white", 1))

        coVerify {
            valueOps.set(
                "session:${session.id}:progress",
                any(),
                24L,
                TimeUnit.HOURS,
            )
        }
    }

    @Test
    fun `setupSession deserializes ParseResponse correctly when Redis returns a LinkedHashMap`() = runTest {
        // GenericJackson2JsonRedisSerializer without default typing stores no @class field,
        // so Jackson deserializes the value as a LinkedHashMap on read-back.
        val session = savedSessionFor()
        val asMap: Any = objectMapper.convertValue(sampleParsed, Map::class.java)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(any()) } returns session
        every { valueOps.get("session:${session.id}:state") } returns asMap
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        val result = service.setupSession(session.id, SetupSessionRequest("white", 1))

        assertEquals(sampleParsed.startingFen, result.fen)
        assertEquals(1, result.moveNumber)
    }

    @Test
    fun `setupSession throws SessionNotFoundException when session does not exist`() = runTest {
        val id = UUID.randomUUID()
        every { sessionRepository.findById(id) } returns Optional.empty()

        assertThrows<SessionNotFoundException> {
            service.setupSession(id, SetupSessionRequest("white", 1))
        }
    }

    @Test
    fun `setupSession throws SessionConflictException when session is already in_progress`() = runTest {
        val session = savedSessionFor().also { it.status = "in_progress" }
        every { sessionRepository.findById(session.id) } returns Optional.of(session)

        assertThrows<SessionConflictException> {
            service.setupSession(session.id, SetupSessionRequest("white", 1))
        }
    }

    @Test
    fun `setupSession throws IllegalArgumentException for invalid playerToGuess`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)

        assertThrows<IllegalArgumentException> {
            service.setupSession(session.id, SetupSessionRequest("green", 1))
        }
    }

    @Test
    fun `setupSession throws IllegalArgumentException when startMoveNumber exceeds game length`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed

        assertThrows<IllegalArgumentException> {
            service.setupSession(session.id, SetupSessionRequest("white", 8)) // game has 7 full moves
        }
    }

    @Test
    fun `setupSession throws IllegalArgumentException when black has no move at final move`() = runTest {
        // 13 plies: 7 white moves, 6 black replies — no black move at full move 7
        val oddParsed = sampleParsed.copy(plyCount = 13, fullMoveCount = 7, moves = sampleMoves.take(13))
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:state") } returns oddParsed

        assertThrows<IllegalArgumentException> {
            service.setupSession(session.id, SetupSessionRequest("black", 7))
        }
    }

    // ── submitGuess ────────────────────────────────────────────────────────────

    private fun inProgressSessionFor(id: UUID = UUID.randomUUID()) =
        savedSessionFor(id).also { it.status = "in_progress"; it.currentMoveIdx = 0 }

    private fun fullInProgressSessionFor(id: UUID = UUID.randomUUID()) = StudySession(
        id = id,
        pgnRaw = "1. e4",
        white = sampleParsed.white,
        black = sampleParsed.black,
        event = sampleParsed.event,
        plyCount = 14,
    ).also {
        it.status = "in_progress"
        it.currentMoveIdx = 2
        it.playerToGuess = "white"
        it.startMoveNum = 2
    }

    private fun progressAt(moveIndex: Int, mode: String = "guess") =
        SessionProgress(currentFen = "fen_before_ply_$moveIndex", moveIndex = moveIndex, mode = mode)

    @Test
    fun `submitGuess returns fenAfterPlayer equal to fen_after from the compare response`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(any()) } returns session
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }
        mockValidateLegal("fen_before_ply_0", "uci0")
        mockCompare("fen_before_ply_0", "uci0", "uci0", correct = true, fenAfter = "fen_after_ply_0")

        val result = service.submitGuess(session.id, GuessRequest("uci0"))

        assertEquals("fen_after_ply_0", result.fenAfterPlayer)
    }

    @Test
    fun `submitGuess returns null fenAfterPlayer when guess is wrong`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        mockValidateLegal("fen_before_ply_0", "wrong_move")
        mockCompare("fen_before_ply_0", "wrong_move", "uci0", correct = false, fenAfter = "fen_after_ply_0")

        val result = service.submitGuess(session.id, GuessRequest("wrong_move"))

        assertEquals(null, result.fenAfterPlayer)
    }

    @Test
    fun `submitGuess returns correct=true and nextFen when guess is right and more moves remain`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(any()) } returns session
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }
        mockValidateLegal("fen_before_ply_0", "uci0")
        mockCompare("fen_before_ply_0", "uci0", "uci0", correct = true, fenAfter = "fen_after_ply_0")

        // uci0 is the correct move at ply 0; next white ply is 2, FEN before it = moves[1].fenAfter
        val result = service.submitGuess(session.id, GuessRequest("uci0"))

        assertEquals(true, result.correct)
        assertEquals(null, result.correctMove)
        assertEquals("fen_after_ply_1", result.nextFen)
    }

    @Test
    fun `submitGuess returns correct=true and nextFen=null when guess is right on the last move`() = runTest {
        // White's last move in a 14-ply game is ply 12 (0-indexed); next ply 14 >= 14 = game over
        val session = inProgressSessionFor().also { it.currentMoveIdx = 12 }
        val progress = progressAt(12)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(any()) } returns session
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }
        mockValidateLegal("fen_before_ply_12", "uci12")
        mockCompare("fen_before_ply_12", "uci12", "uci12", correct = true, fenAfter = "fen_after_ply_12")

        val result = service.submitGuess(session.id, GuessRequest("uci12"))

        assertEquals(true, result.correct)
        assertEquals(null, result.correctMove)
        assertEquals(null, result.nextFen)
    }

    @Test
    fun `submitGuess returns correct=false and null correctMove when guess is wrong`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        mockValidateLegal("fen_before_ply_0", "wrong_move")
        mockCompare("fen_before_ply_0", "wrong_move", "uci0", correct = false, fenAfter = "fen_after_ply_0")

        val result = service.submitGuess(session.id, GuessRequest("wrong_move"))

        assertEquals(false, result.correct)
        assertEquals(null, result.correctMove)
        assertEquals(null, result.nextFen)
    }

    @Test
    fun `submitGuess advances currentMoveIdx and writes updated progress to Redis on correct guess`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0)
        val savedSlot = slot<StudySession>()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(capture(savedSlot)) } returns session
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }
        mockValidateLegal("fen_before_ply_0", "uci0")
        mockCompare("fen_before_ply_0", "uci0", "uci0", correct = true, fenAfter = "fen_after_ply_0")

        service.submitGuess(session.id, GuessRequest("uci0"))

        assertEquals(2, savedSlot.captured.currentMoveIdx)
        coVerify {
            valueOps.set(
                "session:${session.id}:progress",
                SessionProgress(currentFen = "fen_after_ply_1", moveIndex = 2, mode = "analysis"),
                24L,
                TimeUnit.HOURS,
            )
        }
    }

    @Test
    fun `submitGuess sets session to completed and progress mode to complete on final correct guess`() = runTest {
        val session = inProgressSessionFor().also { it.currentMoveIdx = 12 }
        val progress = progressAt(12)
        val savedSlot = slot<StudySession>()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(capture(savedSlot)) } returns session
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }
        mockValidateLegal("fen_before_ply_12", "uci12")
        mockCompare("fen_before_ply_12", "uci12", "uci12", correct = true, fenAfter = "fen_after_ply_12")

        service.submitGuess(session.id, GuessRequest("uci12"))

        assertEquals("completed", savedSlot.captured.status)
        coVerify {
            valueOps.set(
                "session:${session.id}:progress",
                match<SessionProgress> { it.mode == "complete" },
                24L,
                TimeUnit.HOURS,
            )
        }
    }

    @Test
    fun `submitGuess throws IllegalArgumentException when move is illegal in the position`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        mockValidateIllegal("fen_before_ply_0", "e2e5")

        assertThrows<IllegalArgumentException> {
            service.submitGuess(session.id, GuessRequest("e2e5"))
        }
    }

    @Test
    fun `submitGuess throws SessionNotFoundException when session does not exist`() = runTest {
        val id = UUID.randomUUID()
        every { sessionRepository.findById(id) } returns Optional.empty()

        assertThrows<SessionNotFoundException> {
            service.submitGuess(id, GuessRequest("e2e4"))
        }
    }

    @Test
    fun `submitGuess throws SessionConflictException when session is not in_progress`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)

        assertThrows<SessionConflictException> {
            service.submitGuess(session.id, GuessRequest("e2e4"))
        }
    }

    @Test
    fun `submitGuess throws SessionConflictException when progress mode is not guess`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0, mode = "complete")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress

        assertThrows<SessionConflictException> {
            service.submitGuess(session.id, GuessRequest("e2e4"))
        }
    }

    @Test
    fun `submitGuess throws IllegalStateException when progress is missing from Redis`() = runTest {
        val session = inProgressSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns null

        assertThrows<IllegalStateException> {
            service.submitGuess(session.id, GuessRequest("e2e4"))
        }
    }

    @Test
    fun `submitGuess throws IllegalStateException when parse state is missing from Redis`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns null

        assertThrows<IllegalStateException> {
            service.submitGuess(session.id, GuessRequest("e2e4"))
        }
    }

    // ── skipGuess ──────────────────────────────────────────────────────────────

    @Test
    fun `skipGuess returns nextFen and advances position when more moves remain`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(any()) } returns session
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        val result = service.skipGuess(session.id)

        assertEquals("fen_after_ply_1", result.nextFen)
    }

    @Test
    fun `skipGuess returns nextFen=null and completes session when no moves remain`() = runTest {
        val session = inProgressSessionFor().also { it.currentMoveIdx = 12 }
        val progress = progressAt(12)
        val savedSlot = slot<StudySession>()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(capture(savedSlot)) } returns session
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        val result = service.skipGuess(session.id)

        assertEquals(null, result.nextFen)
        assertEquals("completed", savedSlot.captured.status)
    }

    @Test
    fun `skipGuess advances currentMoveIdx and writes updated progress to Redis`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0)
        val savedSlot = slot<StudySession>()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { sessionRepository.save(capture(savedSlot)) } returns session
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        service.skipGuess(session.id)

        assertEquals(2, savedSlot.captured.currentMoveIdx)
        coVerify {
            valueOps.set(
                "session:${session.id}:progress",
                SessionProgress(currentFen = "fen_after_ply_1", moveIndex = 2, mode = "analysis"),
                24L,
                TimeUnit.HOURS,
            )
        }
    }

    @Test
    fun `skipGuess throws SessionNotFoundException when session does not exist`() = runTest {
        val id = UUID.randomUUID()
        every { sessionRepository.findById(id) } returns Optional.empty()

        assertThrows<SessionNotFoundException> { service.skipGuess(id) }
    }

    @Test
    fun `skipGuess throws SessionConflictException when session is not in_progress`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)

        assertThrows<SessionConflictException> { service.skipGuess(session.id) }
    }

    @Test
    fun `skipGuess throws SessionConflictException when progress mode is not guess`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0, mode = "complete")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress

        assertThrows<SessionConflictException> { service.skipGuess(session.id) }
    }

    @Test
    fun `skipGuess throws IllegalStateException when progress is missing from Redis`() = runTest {
        val session = inProgressSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns null

        assertThrows<IllegalStateException> { service.skipGuess(session.id) }
    }

    @Test
    fun `skipGuess throws IllegalStateException when parse state is missing from Redis`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { valueOps.get("session:${session.id}:state") } returns null

        assertThrows<IllegalStateException> { service.skipGuess(session.id) }
    }

    // ── resumeStudy ────────────────────────────────────────────────────────────

    @Test
    fun `resumeStudy returns current fen and moveIndex`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(2)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        val result = service.resumeStudy(session.id)

        assertEquals("fen_before_ply_2", result.fen)
        assertEquals(2, result.moveIndex)
    }

    @Test
    fun `resumeStudy writes progress with mode=guess to Redis`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(2)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        service.resumeStudy(session.id)

        coVerify {
            valueOps.set(
                "session:${session.id}:progress",
                SessionProgress(currentFen = "fen_before_ply_2", moveIndex = 2, mode = "guess"),
                24L,
                TimeUnit.HOURS,
            )
        }
    }

    @Test
    fun `resumeStudy throws SessionNotFoundException when session does not exist`() = runTest {
        val id = UUID.randomUUID()
        every { sessionRepository.findById(id) } returns Optional.empty()

        assertThrows<SessionNotFoundException> { service.resumeStudy(id) }
    }

    @Test
    fun `resumeStudy throws SessionConflictException when session is not in_progress`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)

        assertThrows<SessionConflictException> { service.resumeStudy(session.id) }
    }

    @Test
    fun `resumeStudy throws IllegalStateException when progress is missing from Redis`() = runTest {
        val session = inProgressSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns null

        assertThrows<IllegalStateException> { service.resumeStudy(session.id) }
    }

    // ── addAnalysisMove ────────────────────────────────────────────────────────

    private fun analysisProgressAt(moveIndex: Int) =
        SessionProgress(currentFen = "fen_before_ply_$moveIndex", moveIndex = moveIndex, mode = "analysis")

    private fun savedAnnotation(sessionId: UUID, fen: String) = Annotation(
        sessionId = sessionId,
        fen = fen,
        fromFen = "from_fen",
        moveUci = "uci_move",
        moveSan = "san_move",
    )

    @Test
    fun `addAnalysisMove returns correct response with san and fenAfter`() = runTest {
        val session = inProgressSessionFor()
        val progress = analysisProgressAt(0)
        val annotation = savedAnnotation(session.id, "analysis_fen_after")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { annotationRepository.findFirstBySessionIdAndFenAndMoveUciIsNotNullOrderByCreatedAtAsc(session.id, "from_fen") } returns null
        every { annotationRepository.save(any()) } returns annotation
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }
        mockValidateLegal("from_fen", "e2e4", fenAfter = "analysis_fen_after")

        val result = service.addAnalysisMove(
            session.id,
            AddAnalysisMoveRequest(uciMove = "e2e4", fromFen = "from_fen"),
        )

        assertEquals(annotation.id.toString(), result.id)
        assertEquals("san_e2e4", result.san)
        assertEquals("analysis_fen_after", result.fenAfter)
    }

    @Test
    fun `addAnalysisMove appends fenAfter to analysisFens in Redis progress`() = runTest {
        val session = inProgressSessionFor()
        val progress = analysisProgressAt(0).copy(analysisFens = listOf("prev_fen"))
        val annotation = savedAnnotation(session.id, "new_fen")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { annotationRepository.findFirstBySessionIdAndFenAndMoveUciIsNotNullOrderByCreatedAtAsc(session.id, "from_fen") } returns null
        every { annotationRepository.save(any()) } returns annotation
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }
        mockValidateLegal("from_fen", "e2e4", fenAfter = "new_fen")

        service.addAnalysisMove(session.id, AddAnalysisMoveRequest(uciMove = "e2e4", fromFen = "from_fen"))

        coVerify {
            valueOps.set(
                "session:${session.id}:progress",
                match<SessionProgress> { it.analysisFens == listOf("prev_fen", "new_fen") },
                24L,
                TimeUnit.HOURS,
            )
        }
    }

    @Test
    fun `addAnalysisMove throws IllegalArgumentException when move is illegal`() = runTest {
        val session = inProgressSessionFor()
        val progress = analysisProgressAt(0)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress
        mockValidateIllegal("from_fen", "e2e5")

        assertThrows<IllegalArgumentException> {
            service.addAnalysisMove(session.id, AddAnalysisMoveRequest(uciMove = "e2e5", fromFen = "from_fen"))
        }
    }

    @Test
    fun `addAnalysisMove throws SessionNotFoundException when session does not exist`() = runTest {
        val id = UUID.randomUUID()
        every { sessionRepository.findById(id) } returns Optional.empty()

        assertThrows<SessionNotFoundException> {
            service.addAnalysisMove(id, AddAnalysisMoveRequest(uciMove = "e2e4", fromFen = "from_fen"))
        }
    }

    @Test
    fun `addAnalysisMove throws SessionConflictException when session is not in_progress`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)

        assertThrows<SessionConflictException> {
            service.addAnalysisMove(session.id, AddAnalysisMoveRequest(uciMove = "e2e4", fromFen = "from_fen"))
        }
    }

    @Test
    fun `addAnalysisMove throws SessionConflictException when progress mode is not analysis`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0, mode = "guess")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress

        assertThrows<SessionConflictException> {
            service.addAnalysisMove(session.id, AddAnalysisMoveRequest(uciMove = "e2e4", fromFen = "from_fen"))
        }
    }

    // ── deleteLastAnalysisMove ─────────────────────────────────────────────────

    @Test
    fun `deleteLastAnalysisMove removes last annotation and pops analysisFens`() = runTest {
        val session = inProgressSessionFor()
        val progress = analysisProgressAt(0).copy(analysisFens = listOf("fen_1", "fen_2"))
        val annotation = savedAnnotation(session.id, "fen_2")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { annotationRepository.findTopBySessionIdOrderByCreatedAtDesc(session.id) } returns annotation
        justRun { annotationRepository.delete(annotation) }
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }

        service.deleteLastAnalysisMove(session.id)

        coVerify { annotationRepository.delete(annotation) }
        coVerify {
            valueOps.set(
                "session:${session.id}:progress",
                match<SessionProgress> { it.analysisFens == listOf("fen_1") },
                24L,
                TimeUnit.HOURS,
            )
        }
    }

    @Test
    fun `deleteLastAnalysisMove throws SessionConflictException when analysisFens is empty`() = runTest {
        val session = inProgressSessionFor()
        val progress = analysisProgressAt(0)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress

        assertThrows<SessionConflictException> { service.deleteLastAnalysisMove(session.id) }
    }

    @Test
    fun `deleteLastAnalysisMove throws SessionNotFoundException when session does not exist`() = runTest {
        val id = UUID.randomUUID()
        every { sessionRepository.findById(id) } returns Optional.empty()

        assertThrows<SessionNotFoundException> { service.deleteLastAnalysisMove(id) }
    }

    @Test
    fun `deleteLastAnalysisMove throws SessionConflictException when session is not in_progress`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)

        assertThrows<SessionConflictException> { service.deleteLastAnalysisMove(session.id) }
    }

    @Test
    fun `deleteLastAnalysisMove throws SessionConflictException when progress mode is not analysis`() = runTest {
        val session = inProgressSessionFor()
        val progress = progressAt(0, mode = "guess")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:progress") } returns progress

        assertThrows<SessionConflictException> { service.deleteLastAnalysisMove(session.id) }
    }

    // ── upsertAnnotation ───────────────────────────────────────────────────────

    @Test
    fun `upsertAnnotation creates new annotation when none exists`() = runTest {
        val session = inProgressSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { annotationRepository.findTopBySessionIdAndFenOrderByCreatedAtDesc(session.id, "some-fen") } returns null
        every { annotationRepository.save(any()) } answers { firstArg() }

        service.upsertAnnotation(session.id, AnnotationRequest(fen = "some-fen", comment = "Nice move!"))

        coVerify { annotationRepository.save(match<Annotation> { it.comment == "Nice move!" && it.fen == "some-fen" }) }
    }

    @Test
    fun `upsertAnnotation updates existing annotation when found`() = runTest {
        val session = inProgressSessionFor()
        val existing = savedAnnotation(session.id, "some-fen").also { it.comment = "old comment" }
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { annotationRepository.findTopBySessionIdAndFenOrderByCreatedAtDesc(session.id, "some-fen") } returns existing
        every { annotationRepository.save(any()) } answers { firstArg() }

        service.upsertAnnotation(session.id, AnnotationRequest(fen = "some-fen", comment = "new comment"))

        coVerify { annotationRepository.save(match<Annotation> { it.comment == "new comment" }) }
    }

    @Test
    fun `upsertAnnotation sets valid symbol`() = runTest {
        val session = inProgressSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { annotationRepository.findTopBySessionIdAndFenOrderByCreatedAtDesc(session.id, "some-fen") } returns null
        every { annotationRepository.save(any()) } answers { firstArg() }

        service.upsertAnnotation(session.id, AnnotationRequest(fen = "some-fen", symbol = "!"))

        coVerify { annotationRepository.save(match<Annotation> { it.symbol == "!" }) }
    }

    @Test
    fun `upsertAnnotation clears symbol when empty string sent`() = runTest {
        val session = inProgressSessionFor()
        val existing = savedAnnotation(session.id, "some-fen").also { it.symbol = "!" }
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { annotationRepository.findTopBySessionIdAndFenOrderByCreatedAtDesc(session.id, "some-fen") } returns existing
        every { annotationRepository.save(any()) } answers { firstArg() }

        service.upsertAnnotation(session.id, AnnotationRequest(fen = "some-fen", symbol = ""))

        coVerify { annotationRepository.save(match<Annotation> { it.symbol == null }) }
    }

    @Test
    fun `upsertAnnotation throws IllegalArgumentException for invalid symbol`() = runTest {
        val id = UUID.randomUUID()

        assertThrows<IllegalArgumentException> {
            service.upsertAnnotation(id, AnnotationRequest(fen = "some-fen", symbol = "X"))
        }
    }

    @Test
    fun `upsertAnnotation throws IllegalArgumentException when both comment and symbol are null`() = runTest {
        val id = UUID.randomUUID()

        assertThrows<IllegalArgumentException> {
            service.upsertAnnotation(id, AnnotationRequest(fen = "some-fen"))
        }
    }

    @Test
    fun `upsertAnnotation throws SessionNotFoundException when session not found`() = runTest {
        val id = UUID.randomUUID()
        every { sessionRepository.findById(id) } returns Optional.empty()

        assertThrows<SessionNotFoundException> {
            service.upsertAnnotation(id, AnnotationRequest(fen = "some-fen", comment = "hello"))
        }
    }

    @Test
    fun `upsertAnnotation throws SessionConflictException when session not in_progress`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)

        assertThrows<SessionConflictException> {
            service.upsertAnnotation(session.id, AnnotationRequest(fen = "some-fen", comment = "hello"))
        }
    }

    // ── exportSession ──────────────────────────────────────────────────────────

    @Test
    fun `exportSession returns pgn string from analysis service`() = runTest {
        val session = inProgressSessionFor()
        val annotation = savedAnnotation(session.id, "some_fen").also { it.comment = "Nice!" }
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { annotationRepository.findAllBySessionId(session.id) } returns listOf(annotation)
        coEvery { analysisClient.export(any()) } returns ExportResponse(pgn = "1. e4 e5 *")

        val result = service.exportSession(session.id)

        assertEquals("1. e4 e5 *", result)
    }

    @Test
    fun `exportSession passes all annotation fields to analysis client`() = runTest {
        val session = inProgressSessionFor()
        val annotation = Annotation(
            sessionId = session.id,
            fen = "target_fen",
            fromFen = "source_fen",
            moveUci = "e2e4",
            moveSan = "e4",
            comment = "strong move",
            symbol = "!",
        )
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { annotationRepository.findAllBySessionId(session.id) } returns listOf(annotation)
        val exportRequestSlot = slot<ExportRequest>()
        coEvery { analysisClient.export(capture(exportRequestSlot)) } returns ExportResponse(pgn = "*")

        service.exportSession(session.id)

        val node = exportRequestSlot.captured.annotations.single()
        assertEquals("target_fen", node.fen)
        assertEquals("source_fen", node.fromFen)
        assertEquals("e2e4", node.moveUci)
        assertEquals("e4", node.moveSan)
        assertEquals("strong move", node.comment)
        assertEquals("!", node.symbol)
    }

    @Test
    fun `exportSession passes empty annotation list when no annotations exist`() = runTest {
        val session = inProgressSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { annotationRepository.findAllBySessionId(session.id) } returns emptyList()
        val requestSlot = slot<ExportRequest>()
        coEvery { analysisClient.export(capture(requestSlot)) } returns ExportResponse(pgn = "*")

        service.exportSession(session.id)

        assertEquals(emptyList<ExportAnnotationNode>(), requestSlot.captured.annotations)
    }

    @Test
    fun `exportSession throws SessionNotFoundException when session does not exist`() = runTest {
        val id = UUID.randomUUID()
        every { sessionRepository.findById(id) } returns Optional.empty()

        assertThrows<SessionNotFoundException> { service.exportSession(id) }
    }

    // ── listSessions ───────────────────────────────────────────────────────────

    @Test
    fun `listSessions returns empty list when no sessions exist`() {
        every { sessionRepository.findAllByOrderByCreatedAtDesc() } returns emptyList()

        val result = service.listSessions()

        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `listSessions maps session fields to SessionSummary`() {
        val session = fullInProgressSessionFor()
        every { sessionRepository.findAllByOrderByCreatedAtDesc() } returns listOf(session)

        val result = service.listSessions()

        assertEquals(1, result.size)
        val summary = result.first()
        assertEquals(session.id.toString(), summary.id)
        assertEquals("Magnus Carlsen", summary.white)
        assertEquals("Fabiano Caruana", summary.black)
        assertEquals("in_progress", summary.status)
        assertEquals("white", summary.playerToGuess)
        assertEquals(2, summary.currentMoveIdx)
        assertEquals(14, summary.plyCount)
    }

    // ── loadSession ────────────────────────────────────────────────────────────

    @Test
    fun `loadSession returns pending_setup response without board state`() = runTest {
        val session = savedSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)

        val result = service.loadSession(session.id)

        assertEquals("pending_setup", result.status)
        assertEquals(null, result.currentFen)
        assertEquals(null, result.mode)
        assertEquals(null, result.moves)
    }

    @Test
    fun `loadSession returns full response from Redis cache when keys are present`() = runTest {
        val session = fullInProgressSessionFor()
        val progress = SessionProgress(currentFen = "cached_fen", moveIndex = 2, mode = "guess")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { annotationRepository.findAllBySessionId(session.id) } returns emptyList()

        val result = service.loadSession(session.id)

        assertEquals("cached_fen", result.currentFen)
        assertEquals("guess", result.mode)
        assertEquals(14, result.moves?.size)
        assertEquals(sampleMoves.map { it.san }, result.moves)
    }

    @Test
    fun `loadSession rehydrates Redis when cache is expired`() = runTest {
        val session = fullInProgressSessionFor()
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:state") } returns null
        every { valueOps.get("session:${session.id}:progress") } returns null
        coEvery { analysisClient.parse(any()) } returns sampleParsed
        justRun { valueOps.set(any(), any<Any>(), any<Long>(), any()) }
        every { annotationRepository.findAllBySessionId(session.id) } returns emptyList()

        val result = service.loadSession(session.id)

        assertEquals("guess", result.mode)
        assertEquals(14, result.moves?.size)
        coVerify { analysisClient.parse(session.pgnRaw) }
    }

    @Test
    fun `loadSession returns mode=complete when session status is completed`() = runTest {
        val session = fullInProgressSessionFor().also { it.status = "completed" }
        val progress = SessionProgress(currentFen = "final_fen", moveIndex = 14, mode = "complete")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { annotationRepository.findAllBySessionId(session.id) } returns emptyList()

        val result = service.loadSession(session.id)

        assertEquals("complete", result.mode)
    }

    // ── loadSession — variation tree building ──────────────────────────────────

    private fun moveAnnotation(
        sessionId: UUID,
        id: UUID = UUID.randomUUID(),
        fen: String,
        fromFen: String? = null,
        moveUci: String = "e2e4",
        moveSan: String = "e4",
        parentId: UUID? = null,
        createdAtOffset: Long = 0L,
    ) = Annotation(
        id = id,
        sessionId = sessionId,
        fen = fen,
        fromFen = fromFen,
        moveUci = moveUci,
        moveSan = moveSan,
        parentId = parentId,
        createdAt = java.time.Instant.EPOCH.plusSeconds(createdAtOffset),
    )

    private fun commentAnnotation(sessionId: UUID, fen: String) = Annotation(
        sessionId = sessionId,
        fen = fen,
        comment = "nice position",
    )

    private fun loadSessionWithAnnotations(session: com.chessmind.api.entity.StudySession, anns: List<Annotation>) = runTest {
        val progress = SessionProgress(currentFen = "some_fen", moveIndex = 2, mode = "guess")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { annotationRepository.findAllBySessionId(session.id) } returns anns
        service.loadSession(session.id)
    }

    @Test
    fun `loadSession returns empty variationTree when there are no annotations`() = runTest {
        val session = fullInProgressSessionFor()
        val progress = SessionProgress(currentFen = "some_fen", moveIndex = 2, mode = "guess")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { annotationRepository.findAllBySessionId(session.id) } returns emptyList()

        val result = service.loadSession(session.id)

        assertEquals(emptyList<Any>(), result.variationTree)
    }

    @Test
    fun `loadSession builds a single linear variation line correctly`() = runTest {
        val session = fullInProgressSessionFor()
        val progress = SessionProgress(currentFen = "some_fen", moveIndex = 2, mode = "guess")
        val idA = UUID.randomUUID()
        val idB = UUID.randomUUID()
        val idC = UUID.randomUUID()
        val annA = moveAnnotation(session.id, id = idA, fen = "fen_A", moveUci = "e2e4", moveSan = "e4", createdAtOffset = 1)
        val annB = moveAnnotation(session.id, id = idB, fen = "fen_B", moveUci = "e7e5", moveSan = "e5", parentId = idA, createdAtOffset = 2)
        val annC = moveAnnotation(session.id, id = idC, fen = "fen_C", moveUci = "g1f3", moveSan = "Nf3", parentId = idB, createdAtOffset = 3)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { annotationRepository.findAllBySessionId(session.id) } returns listOf(annA, annB, annC)

        val result = service.loadSession(session.id)

        assertEquals(1, result.variationTree.size)
        val root = result.variationTree[0]
        assertEquals(idA.toString(), root.id)
        assertEquals("e4", root.san)
        assertEquals(1, root.children.size)
        val second = root.children[0]
        assertEquals(idB.toString(), second.id)
        assertEquals("e5", second.san)
        assertEquals(1, second.children.size)
        assertEquals(idC.toString(), second.children[0].id)
        assertEquals("Nf3", second.children[0].san)
        assertEquals(0, second.children[0].children.size)
    }

    @Test
    fun `loadSession builds a forking tree with two sibling branches`() = runTest {
        val session = fullInProgressSessionFor()
        val progress = SessionProgress(currentFen = "some_fen", moveIndex = 2, mode = "guess")
        val idA = UUID.randomUUID()
        val idB = UUID.randomUUID()
        val idC = UUID.randomUUID()
        // A is the root; B and C are both children of A (two responses)
        val annA = moveAnnotation(session.id, id = idA, fen = "fen_A", moveUci = "e2e4", moveSan = "e4", createdAtOffset = 1)
        val annB = moveAnnotation(session.id, id = idB, fen = "fen_B", moveUci = "e7e5", moveSan = "e5", parentId = idA, createdAtOffset = 2)
        val annC = moveAnnotation(session.id, id = idC, fen = "fen_C", moveUci = "d7d5", moveSan = "d5", parentId = idA, createdAtOffset = 3)
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { annotationRepository.findAllBySessionId(session.id) } returns listOf(annA, annB, annC)

        val result = service.loadSession(session.id)

        assertEquals(1, result.variationTree.size)
        val root = result.variationTree[0]
        assertEquals(idA.toString(), root.id)
        assertEquals(2, root.children.size)
        assertEquals(idB.toString(), root.children[0].id)
        assertEquals("e5", root.children[0].san)
        assertEquals(idC.toString(), root.children[1].id)
        assertEquals("d5", root.children[1].san)
    }

    @Test
    fun `loadSession excludes comment-only annotations from the variation tree`() = runTest {
        val session = fullInProgressSessionFor()
        val progress = SessionProgress(currentFen = "some_fen", moveIndex = 2, mode = "guess")
        val idA = UUID.randomUUID()
        val moveAnn = moveAnnotation(session.id, id = idA, fen = "fen_A", moveUci = "e2e4", moveSan = "e4")
        val commentAnn = commentAnnotation(session.id, "fen_A")
        every { sessionRepository.findById(session.id) } returns Optional.of(session)
        every { valueOps.get("session:${session.id}:state") } returns sampleParsed
        every { valueOps.get("session:${session.id}:progress") } returns progress
        every { annotationRepository.findAllBySessionId(session.id) } returns listOf(moveAnn, commentAnn)

        val result = service.loadSession(session.id)

        assertEquals(1, result.variationTree.size)
        assertEquals(idA.toString(), result.variationTree[0].id)
        assertEquals(0, result.variationTree[0].children.size)
    }

    @Test
    fun `loadSession throws SessionNotFoundException when session does not exist`() = runTest {
        val id = UUID.randomUUID()
        every { sessionRepository.findById(id) } returns Optional.empty()

        assertThrows<SessionNotFoundException> { service.loadSession(id) }
    }
}
