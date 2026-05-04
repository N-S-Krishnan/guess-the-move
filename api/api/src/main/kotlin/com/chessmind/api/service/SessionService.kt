package com.chessmind.api.service

import com.chessmind.api.client.AnalysisClient
import com.chessmind.api.client.dto.ParseResponse
import com.chessmind.api.dto.CreateSessionResponse
import com.chessmind.api.dto.GuessRequest
import com.chessmind.api.dto.GuessResponse
import com.chessmind.api.dto.SessionProgress
import com.chessmind.api.dto.SetupSessionRequest
import com.chessmind.api.dto.SetupSessionResponse
import com.chessmind.api.dto.SkipResponse
import com.chessmind.api.entity.StudySession
import com.chessmind.api.exception.SessionConflictException
import com.chessmind.api.exception.SessionNotFoundException
import com.chessmind.api.repository.StudySessionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class SessionService(
    private val analysisClient: AnalysisClient,
    private val sessionRepository: StudySessionRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    suspend fun createSession(pgn: String): CreateSessionResponse {
        val parsed = analysisClient.parse(pgn)

        val session = StudySession(
            pgnRaw = pgn,
            white = parsed.white,
            black = parsed.black,
            event = parsed.event,
        )
        val saved = sessionRepository.save(session)

        redisTemplate.opsForValue().set(
            "session:${saved.id}:state",
            parsed,
            24,
            TimeUnit.HOURS,
        )

        return CreateSessionResponse(
            id = saved.id.toString(),
            white = saved.white,
            black = saved.black,
            plyCount = parsed.plyCount,
        )
    }

    @Transactional
    suspend fun setupSession(sessionId: UUID, request: SetupSessionRequest): SetupSessionResponse {
        if (request.playerToGuess !in setOf("white", "black")) {
            throw IllegalArgumentException("playerToGuess must be 'white' or 'black'")
        }

        val session = sessionRepository.findById(sessionId).orElseThrow {
            SessionNotFoundException("Session $sessionId not found")
        }

        if (session.status != "pending_setup") {
            throw SessionConflictException("Session $sessionId is already in '${session.status}' state")
        }

        // RedisConfig passes the Spring ObjectMapper (without default typing) to
        // GenericJackson2JsonRedisSerializer, so the JSON stored in Redis has no @class
        // field. Jackson therefore deserializes the value as a LinkedHashMap rather than
        // ParseResponse. objectMapper.convertValue handles both cases: it returns the
        // object unchanged when it is already a ParseResponse, and correctly maps a
        // LinkedHashMap to ParseResponse (honouring @JsonProperty aliases) otherwise.
        val rawValue = redisTemplate.opsForValue().get("session:$sessionId:state")
            ?: throw IllegalStateException("Session state not found in cache for $sessionId")
        val parseResponse = objectMapper.convertValue(rawValue, ParseResponse::class.java)

        // moveIndex is the 0-based ply index of the first move to guess.
        // White's Nth full move = ply 2*(N-1); black's Nth reply = ply 2*(N-1)+1.
        val moveIndex = when (request.playerToGuess) {
            "white" -> 2 * (request.startMoveNumber - 1)
            else -> 2 * (request.startMoveNumber - 1) + 1
        }

        if (request.startMoveNumber > parseResponse.fullMoveCount) {
            throw IllegalArgumentException(
                "startMoveNumber ${request.startMoveNumber} exceeds game length (${parseResponse.fullMoveCount} full moves)",
            )
        }
        if (moveIndex >= parseResponse.plyCount) {
            throw IllegalArgumentException(
                "No ${request.playerToGuess} move exists at move ${request.startMoveNumber}",
            )
        }

        // FEN displayed before the first guess: position just before ply moveIndex.
        // ply 0 → startingFen; ply i → moves[i-1].fenAfter
        val startFen = if (moveIndex == 0) {
            parseResponse.startingFen
        } else {
            parseResponse.moves[moveIndex - 1].fenAfter
        }

        session.playerToGuess = request.playerToGuess
        session.startMoveNum = request.startMoveNumber
        session.currentMoveIdx = moveIndex
        session.status = "in_progress"
        sessionRepository.save(session)

        redisTemplate.opsForValue().set(
            "session:$sessionId:progress",
            SessionProgress(currentFen = startFen, moveIndex = moveIndex, mode = "guess"),
            24,
            TimeUnit.HOURS,
        )

        return SetupSessionResponse(
            fen = startFen,
            moveNumber = request.startMoveNumber,
            moves = parseResponse.moves.map { it.san },
        )
    }

    @Transactional
    suspend fun submitGuess(sessionId: UUID, request: GuessRequest): GuessResponse {
        val session = sessionRepository.findById(sessionId).orElseThrow {
            SessionNotFoundException("Session $sessionId not found")
        }

        if (session.status != "in_progress") {
            throw SessionConflictException("Session $sessionId is not in 'in_progress' state")
        }

        val rawProgress = redisTemplate.opsForValue().get("session:$sessionId:progress")
            ?: throw IllegalStateException("Session progress not found in cache for $sessionId")
        val progress = objectMapper.convertValue(rawProgress, SessionProgress::class.java)

        if (progress.mode != "guess") {
            throw SessionConflictException("Session $sessionId is not in 'guess' mode")
        }

        val rawState = redisTemplate.opsForValue().get("session:$sessionId:state")
            ?: throw IllegalStateException("Session state not found in cache for $sessionId")
        val parseResponse = objectMapper.convertValue(rawState, ParseResponse::class.java)

        val expectedMove = parseResponse.moves[progress.moveIndex].uci

        val validateResult = analysisClient.validate(progress.currentFen, request.move)
        if (!validateResult.legal) {
            throw IllegalArgumentException("Illegal move: ${request.move}")
        }

        val compareResult = analysisClient.compare(progress.currentFen, request.move, expectedMove)

        if (!compareResult.correct) {
            return GuessResponse(correct = false, correctMove = null, nextFen = null, fenAfterPlayer = null)
        }

        val fenAfterPlayer = compareResult.fenAfter
        val nextMoveIdx = progress.moveIndex + 2
        val hasNextMove = nextMoveIdx < parseResponse.plyCount

        return if (hasNextMove) {
            val nextFen = parseResponse.moves[nextMoveIdx - 1].fenAfter
            session.currentMoveIdx = nextMoveIdx
            sessionRepository.save(session)
            redisTemplate.opsForValue().set(
                "session:$sessionId:progress",
                SessionProgress(currentFen = nextFen, moveIndex = nextMoveIdx, mode = "guess"),
                24,
                TimeUnit.HOURS,
            )
            GuessResponse(correct = true, correctMove = null, nextFen = nextFen, fenAfterPlayer = fenAfterPlayer)
        } else {
            session.status = "completed"
            session.currentMoveIdx = nextMoveIdx
            sessionRepository.save(session)
            redisTemplate.opsForValue().set(
                "session:$sessionId:progress",
                SessionProgress(currentFen = progress.currentFen, moveIndex = nextMoveIdx, mode = "complete"),
                24,
                TimeUnit.HOURS,
            )
            GuessResponse(correct = true, correctMove = null, nextFen = null, fenAfterPlayer = fenAfterPlayer)
        }
    }

    @Transactional
    suspend fun skipGuess(sessionId: UUID): SkipResponse {
        val session = sessionRepository.findById(sessionId).orElseThrow {
            SessionNotFoundException("Session $sessionId not found")
        }

        if (session.status != "in_progress") {
            throw SessionConflictException("Session $sessionId is not in 'in_progress' state")
        }

        val rawProgress = redisTemplate.opsForValue().get("session:$sessionId:progress")
            ?: throw IllegalStateException("Session progress not found in cache for $sessionId")
        val progress = objectMapper.convertValue(rawProgress, SessionProgress::class.java)

        if (progress.mode != "guess") {
            throw SessionConflictException("Session $sessionId is not in 'guess' mode")
        }

        val rawState = redisTemplate.opsForValue().get("session:$sessionId:state")
            ?: throw IllegalStateException("Session state not found in cache for $sessionId")
        val parseResponse = objectMapper.convertValue(rawState, ParseResponse::class.java)

        val nextMoveIdx = progress.moveIndex + 2
        val hasNextMove = nextMoveIdx < parseResponse.plyCount

        return if (hasNextMove) {
            val nextFen = parseResponse.moves[nextMoveIdx - 1].fenAfter
            session.currentMoveIdx = nextMoveIdx
            sessionRepository.save(session)
            redisTemplate.opsForValue().set(
                "session:$sessionId:progress",
                SessionProgress(currentFen = nextFen, moveIndex = nextMoveIdx, mode = "guess"),
                24,
                TimeUnit.HOURS,
            )
            SkipResponse(nextFen = nextFen)
        } else {
            session.status = "completed"
            session.currentMoveIdx = nextMoveIdx
            sessionRepository.save(session)
            redisTemplate.opsForValue().set(
                "session:$sessionId:progress",
                SessionProgress(currentFen = progress.currentFen, moveIndex = nextMoveIdx, mode = "complete"),
                24,
                TimeUnit.HOURS,
            )
            SkipResponse(nextFen = null)
        }
    }
}
