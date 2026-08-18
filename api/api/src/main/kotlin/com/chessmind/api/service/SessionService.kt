package com.chessmind.api.service

import com.chessmind.api.client.AnalysisClient
import com.chessmind.api.dto.CreateSessionResponse
import com.chessmind.api.entity.StudySession
import com.chessmind.api.repository.StudySessionRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Service
class SessionService(
    private val analysisClient: AnalysisClient,
    private val sessionRepository: StudySessionRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
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
}
