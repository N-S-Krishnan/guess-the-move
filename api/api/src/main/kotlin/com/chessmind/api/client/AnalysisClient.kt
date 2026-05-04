package com.chessmind.api.client

import com.chessmind.api.client.dto.CompareResponse
import com.chessmind.api.client.dto.ParseResponse
import com.chessmind.api.client.dto.ValidateResponse

interface AnalysisClient {
    suspend fun parse(pgn: String): ParseResponse
    suspend fun validate(fen: String, uciMove: String): ValidateResponse
    suspend fun compare(fen: String, submittedUci: String, expectedUci: String): CompareResponse
}
