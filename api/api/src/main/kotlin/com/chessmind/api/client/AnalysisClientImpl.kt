package com.chessmind.api.client

import com.chessmind.api.client.dto.CompareRequest
import com.chessmind.api.client.dto.CompareResponse
import com.chessmind.api.client.dto.ExportRequest
import com.chessmind.api.client.dto.ExportResponse
import com.chessmind.api.client.dto.ParseRequest
import com.chessmind.api.client.dto.ParseResponse
import com.chessmind.api.client.dto.ValidateRequest
import com.chessmind.api.client.dto.ValidateResponse
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

    override suspend fun validate(fen: String, uciMove: String): ValidateResponse = withContext(Dispatchers.IO) {
        try {
            analysisRestClient.post()
                .uri("/validate")
                .body(ValidateRequest(fen = fen, uciMove = uciMove))
                .retrieve()
                .body(ValidateResponse::class.java)
                ?: throw IllegalStateException("Empty response from analysis service")
        } catch (e: HttpClientErrorException.BadRequest) {
            throw IllegalStateException("Analysis service rejected FEN during validation: ${e.responseBodyAsString}")
        }
    }

    override suspend fun compare(fen: String, submittedUci: String, expectedUci: String): CompareResponse =
        withContext(Dispatchers.IO) {
            try {
                analysisRestClient.post()
                    .uri("/compare")
                    .body(CompareRequest(fen = fen, expectedUci = expectedUci, submittedUci = submittedUci))
                    .retrieve()
                    .body(CompareResponse::class.java)
                    ?: throw IllegalStateException("Empty response from analysis service")
            } catch (e: HttpClientErrorException.BadRequest) {
                throw IllegalStateException("Analysis service rejected chess data during comparison: ${e.responseBodyAsString}")
            }
        }

    override suspend fun export(request: ExportRequest): ExportResponse = withContext(Dispatchers.IO) {
        try {
            analysisRestClient.post()
                .uri("/export")
                .body(request)
                .retrieve()
                .body(ExportResponse::class.java)
                ?: throw IllegalStateException("Empty response from analysis service")
        } catch (e: HttpClientErrorException.BadRequest) {
            throw IllegalArgumentException("Analysis service rejected PGN during export: ${e.responseBodyAsString}")
        }
    }
}
