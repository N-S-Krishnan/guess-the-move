package com.chessmind.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class AnalysisClientConfig(
    @Value("\${chessmind.analysis.base-url}") private val baseUrl: String,
    @Value("\${chessmind.analysis.timeout-seconds}") private val timeoutSeconds: Int,
) {

    @Bean
    fun analysisRestClient(): RestClient {
        val factory = SimpleClientHttpRequestFactory().apply {
            val millis = timeoutSeconds * 1000
            setConnectTimeout(millis)
            setReadTimeout(millis)
        }
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}
