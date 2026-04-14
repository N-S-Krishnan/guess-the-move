package com.chessmind.api.client

import com.chessmind.api.client.dto.ParseRequest
import com.chessmind.api.client.dto.ParseResponse
import com.chessmind.api.exception.AnalysisException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

@Component
class AnalysisClientImpl(private val analysisRestClient: RestClient) : AnalysisClient {

    override suspend fun parse(pgn: String): ParseResponse = withContext(Dispatchers.IO) {
        try {
            analysisRestClient.post()
                .uri("/parse")
                .body(ParseRequest(pgn))
                .retrieve()
                .body(ParseResponse::class.java)
                ?: throw AnalysisException("Empty response from analysis service")
        } catch (e: HttpClientErrorException.BadRequest) {
            val detail = e.responseBodyAsString
            throw AnalysisException(detail.ifBlank { "Analysis service rejected the PGN" })
        }
    }
}
