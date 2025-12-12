package com.sqlswitcher.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 헬스체크 및 루트 엔드포인트 컨트롤러
 * Kubernetes/Docker 헬스체크 및 기본 상태 확인용
 */
@RestController
class HealthController {

    @GetMapping("/health", "/healthz", "/")
    fun health(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = "UP",
                service = "sql-switcher",
                timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        )
    }

    @GetMapping("/ready", "/readyz")
    fun ready(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = "READY",
                service = "sql-switcher",
                timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        )
    }
}

data class HealthResponse(
    val status: String,
    val service: String,
    val timestamp: String
)