package com.chessmind.api.client

import com.chessmind.api.client.dto.ParseResponse

interface AnalysisClient {
    suspend fun parse(pgn: String): ParseResponse
}
