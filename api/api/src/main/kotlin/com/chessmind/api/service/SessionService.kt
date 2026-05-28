package com.chessmind.api.service

import com.chessmind.api.client.AnalysisClient
import com.chessmind.api.client.dto.ExportAnnotationNode
import com.chessmind.api.client.dto.ExportRequest
import com.chessmind.api.client.dto.ParseResponse
import com.chessmind.api.dto.AddAnalysisMoveRequest
import com.chessmind.api.dto.AddAnalysisMoveResponse
import com.chessmind.api.dto.AnnotationRequest
import com.chessmind.api.dto.AnnotationTreeNode
import com.chessmind.api.dto.CreateSessionResponse
import com.chessmind.api.dto.GuessRequest
import com.chessmind.api.dto.GuessResponse
import com.chessmind.api.dto.LoadSessionResponse
import com.chessmind.api.dto.ResumeResponse
import com.chessmind.api.dto.SessionProgress
import com.chessmind.api.dto.SessionSummary
import com.chessmind.api.dto.SetupSessionRequest
import com.chessmind.api.dto.SetupSessionResponse
import com.chessmind.api.dto.SkipResponse
import com.chessmind.api.entity.Annotation
import com.chessmind.api.entity.StudySession
import com.chessmind.api.exception.SessionConflictException
import com.chessmind.api.exception.SessionNotFoundException
import com.chessmind.api.repository.AnnotationRepository
import com.chessmind.api.repository.StudySessionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class SessionService(
    private val analysisClient: AnalysisClient,
    private val sessionRepository: StudySessionRepository,
    private val annotationRepository: AnnotationRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
) {

    fun listSessions(): List<SessionSummary> =
        sessionRepository.findAllByOrderByCreatedAtDesc().map { session ->
            SessionSummary(
                id = session.id.toString(),
                white = session.white,
                black = session.black,
                event = session.event,
                date = session.date,
                site = session.site,
                status = session.status,
                playerToGuess = session.playerToGuess,
                currentMoveIdx = session.currentMoveIdx,
                plyCount = session.plyCount,
                createdAt = session.createdAt.toString(),
            )
        }

    suspend fun loadSession(sessionId: UUID): LoadSessionResponse {
        val session = sessionRepository.findById(sessionId).orElseThrow {
            SessionNotFoundException("Session $sessionId not found")
        }

        if (session.status == "pending_setup") {
            return LoadSessionResponse(
                id = session.id.toString(),
                white = session.white,
                black = session.black,
                event = session.event,
                status = session.status,
                playerToGuess = null,
                startMoveNum = null,
                currentMoveIdx = null,
                currentFen = null,
                mode = null,
                moves = null,
                plyCount = session.plyCount,
            )
        }

        // Attempt to read both Redis keys; rehydrate from DB if either is absent.
        val cachedState = redisTemplate.opsForValue().get("session:$sessionId:state")
        val cachedProgress = redisTemplate.opsForValue().get("session:$sessionId:progress")

        val parseResponse: ParseResponse
        val progress: SessionProgress

        if (cachedState != null && cachedProgress != null) {
            parseResponse = objectMapper.convertValue(cachedState, ParseResponse::class.java)
            progress = objectMapper.convertValue(cachedProgress, SessionProgress::class.java)
        } else {
            // Cache expired — re-parse and repopulate.
            parseResponse = analysisClient.parse(session.pgnRaw)
            redisTemplate.opsForValue().set(
                "session:$sessionId:state",
                parseResponse,
                24,
                TimeUnit.HOURS,
            )
            val moveIdx = session.currentMoveIdx ?: 0
            val currentFen = when {
                moveIdx == 0 -> parseResponse.startingFen
                moveIdx - 1 < parseResponse.moves.size -> parseResponse.moves[moveIdx - 1].fenAfter
                else -> parseResponse.moves.last().fenAfter
            }
            progress = SessionProgress(currentFen = currentFen, moveIndex = moveIdx, mode = "guess")
            redisTemplate.opsForValue().set(
                "session:$sessionId:progress",
                progress,
                24,
                TimeUnit.HOURS,
            )
        }

        val annotations = annotationRepository.findAllBySessionId(sessionId)
        val variationTree = buildAnnotationTree(annotations)

        return LoadSessionResponse(
            id = session.id.toString(),
            white = session.white,
            black = session.black,
            event = session.event,
            status = session.status,
            playerToGuess = session.playerToGuess,
            startMoveNum = session.startMoveNum,
            currentMoveIdx = progress.moveIndex,
            currentFen = progress.currentFen,
            mode = when {
                session.status == "completed" -> "complete"
                progress.mode == "analysis" -> "guess"
                else -> progress.mode
            },
            moves = parseResponse.moves.map { it.san },
            plyCount = session.plyCount ?: parseResponse.plyCount,
            variationTree = variationTree,
        )
    }

    private fun buildAnnotationTree(annotations: List<Annotation>): List<AnnotationTreeNode> {
        val moveAnnotations = annotations.filter { it.moveUci != null }
        val byParent = moveAnnotations.groupBy { it.parentId }

        fun buildChildren(parentId: UUID?): List<AnnotationTreeNode> =
            (byParent[parentId] ?: emptyList())
                .sortedBy { it.createdAt }
                .map { ann ->
                    AnnotationTreeNode(
                        id = ann.id.toString(),
                        san = ann.moveSan ?: "",
                        uci = ann.moveUci ?: "",
                        fen = ann.fen,
                        fromFen = ann.fromFen,
                        symbol = ann.symbol,
                        comment = ann.comment,
                        children = buildChildren(ann.id),
                    )
                }

        return buildChildren(null)
    }

    suspend fun createSession(pgn: String): CreateSessionResponse {
        val parsed = analysisClient.parse(pgn)

        val session = StudySession(
            pgnRaw = pgn,
            white = parsed.white,
            black = parsed.black,
            event = parsed.event,
            date = parsed.date,
            site = parsed.site,
            plyCount = parsed.plyCount,
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
                SessionProgress(currentFen = nextFen, moveIndex = nextMoveIdx, mode = "analysis"),
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
                SessionProgress(currentFen = nextFen, moveIndex = nextMoveIdx, mode = "analysis"),
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

    suspend fun addAnalysisMove(sessionId: UUID, request: AddAnalysisMoveRequest): AddAnalysisMoveResponse {
        val session = sessionRepository.findById(sessionId).orElseThrow {
            SessionNotFoundException("Session $sessionId not found")
        }

        if (session.status != "in_progress") {
            throw SessionConflictException("Session $sessionId is not in 'in_progress' state")
        }

        val rawProgress = redisTemplate.opsForValue().get("session:$sessionId:progress")
            ?: throw IllegalStateException("Session progress not found in cache for $sessionId")
        val progress = objectMapper.convertValue(rawProgress, SessionProgress::class.java)

        if (progress.mode != "analysis") {
            throw SessionConflictException("Session $sessionId is not in 'analysis' mode")
        }

        val validateResult = analysisClient.validate(request.fromFen, request.uciMove)
        if (!validateResult.legal) {
            throw IllegalArgumentException("Illegal move: ${request.uciMove}")
        }

        val san = requireNotNull(validateResult.san) { "validate returned no SAN for legal move" }
        val fenAfter = requireNotNull(validateResult.fenAfter) { "validate returned no fenAfter for legal move" }

        val parentAnnotation = annotationRepository
            .findFirstBySessionIdAndFenAndMoveUciIsNotNullOrderByCreatedAtAsc(sessionId, request.fromFen)

        val annotation = annotationRepository.save(
            Annotation(
                sessionId = sessionId,
                fen = fenAfter,
                fromFen = request.fromFen,
                moveUci = request.uciMove,
                moveSan = san,
                parentId = parentAnnotation?.id,
            ),
        )

        redisTemplate.opsForValue().set(
            "session:$sessionId:progress",
            progress.copy(analysisFens = progress.analysisFens + fenAfter),
            24,
            TimeUnit.HOURS,
        )

        return AddAnalysisMoveResponse(id = annotation.id.toString(), san = san, fenAfter = fenAfter)
    }

    suspend fun deleteLastAnalysisMove(sessionId: UUID) {
        val session = sessionRepository.findById(sessionId).orElseThrow {
            SessionNotFoundException("Session $sessionId not found")
        }

        if (session.status != "in_progress") {
            throw SessionConflictException("Session $sessionId is not in 'in_progress' state")
        }

        val rawProgress = redisTemplate.opsForValue().get("session:$sessionId:progress")
            ?: throw IllegalStateException("Session progress not found in cache for $sessionId")
        val progress = objectMapper.convertValue(rawProgress, SessionProgress::class.java)

        if (progress.mode != "analysis") {
            throw SessionConflictException("Session $sessionId is not in 'analysis' mode")
        }

        if (progress.analysisFens.isEmpty()) {
            throw SessionConflictException("No analysis moves to take back for session $sessionId")
        }

        val annotation = annotationRepository.findTopBySessionIdOrderByCreatedAtDesc(sessionId)
            ?: throw IllegalStateException("No annotation found for session $sessionId")

        annotationRepository.delete(annotation)

        redisTemplate.opsForValue().set(
            "session:$sessionId:progress",
            progress.copy(analysisFens = progress.analysisFens.dropLast(1)),
            24,
            TimeUnit.HOURS,
        )
    }

    suspend fun upsertAnnotation(sessionId: UUID, request: AnnotationRequest) {
        if (request.comment == null && request.symbol == null) {
            throw IllegalArgumentException("At least one of comment or symbol must be provided")
        }
        val validSymbols = setOf("!", "?", "!!", "??", "!?", "?!")
        if (request.symbol != null && request.symbol.isNotEmpty() && request.symbol !in validSymbols) {
            throw IllegalArgumentException("Invalid symbol '${request.symbol}'. Must be one of: !, ?, !!, ??, !?, ?!")
        }
        val session = sessionRepository.findById(sessionId).orElseThrow {
            SessionNotFoundException("Session $sessionId not found")
        }
        if (session.status != "in_progress") {
            throw SessionConflictException("Session $sessionId is not in 'in_progress' state")
        }
        val existing = annotationRepository.findTopBySessionIdAndFenAndMoveUciIsNullOrderByCreatedAtDesc(sessionId, request.fen)
        if (existing != null) {
            if (request.comment != null) existing.comment = request.comment
            if (request.symbol != null) {
                existing.symbol = if (request.symbol.isEmpty()) null else request.symbol
            }
            annotationRepository.save(existing)
        } else {
            annotationRepository.save(
                Annotation(
                    sessionId = sessionId,
                    fen = request.fen,
                    comment = request.comment,
                    symbol = if (request.symbol?.isEmpty() == true) null else request.symbol,
                ),
            )
        }
    }

    suspend fun exportSession(sessionId: UUID): String {
        val session = sessionRepository.findById(sessionId).orElseThrow {
            SessionNotFoundException("Session $sessionId not found")
        }

        val annotations = annotationRepository.findAllBySessionId(sessionId).map { ann ->
            ExportAnnotationNode(
                fen = ann.fen,
                fromFen = ann.fromFen,
                moveUci = ann.moveUci,
                moveSan = ann.moveSan,
                comment = ann.comment,
                symbol = ann.symbol,
            )
        }

        return analysisClient.export(ExportRequest(pgnRaw = session.pgnRaw, annotations = annotations)).pgn
    }

    suspend fun resumeStudy(sessionId: UUID): ResumeResponse {
        val session = sessionRepository.findById(sessionId).orElseThrow {
            SessionNotFoundException("Session $sessionId not found")
        }

        if (session.status != "in_progress") {
            throw SessionConflictException("Session $sessionId is not in 'in_progress' state")
        }

        val rawProgress = redisTemplate.opsForValue().get("session:$sessionId:progress")
            ?: throw IllegalStateException("Session progress not found in cache for $sessionId")
        val progress = objectMapper.convertValue(rawProgress, SessionProgress::class.java)

        redisTemplate.opsForValue().set(
            "session:$sessionId:progress",
            SessionProgress(currentFen = progress.currentFen, moveIndex = progress.moveIndex, mode = "guess"),
            24,
            TimeUnit.HOURS,
        )

        return ResumeResponse(fen = progress.currentFen, moveIndex = progress.moveIndex)
    }
}
