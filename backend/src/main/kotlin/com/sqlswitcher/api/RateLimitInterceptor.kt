package com.sqlswitcher.api

import io.github.bucket4j.Bucket
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class RateLimitInterceptor(
    private val rateLimitBucket: Bucket
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // Rate limiting은 /api/v1/convert 엔드포인트에만 적용
        if (request.requestURI.startsWith("/api/v1/convert")) {
            val clientIp = getClientIpAddress(request)
            val bucket = getBucketForClient(clientIp)
            
            if (bucket.tryConsume(1)) {
                // 요청 허용
                response.setHeader("X-RateLimit-Remaining", bucket.getAvailableTokens().toString())
                response.setHeader("X-RateLimit-Reset", getResetTime(bucket).toString())
                return true
            } else {
                // 요청 거부
                response.status = HttpStatus.TOO_MANY_REQUESTS.value()
                response.setHeader("X-RateLimit-Remaining", "0")
                response.setHeader("X-RateLimit-Reset", getResetTime(bucket).toString())
                response.contentType = "application/json"
                response.writer.write("""
                    {
                        "errorCode": "RATE_LIMIT_EXCEEDED",
                        "message": "Rate limit exceeded. Please try again later.",
                        "timestamp": "${java.time.LocalDateTime.now()}"
                    }
                """.trimIndent())
                return false
            }
        }
        
        return true
    }

    private fun getClientIpAddress(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (xForwardedFor != null && xForwardedFor.isNotEmpty()) {
            return xForwardedFor.split(",")[0].trim()
        }
        
        val xRealIp = request.getHeader("X-Real-IP")
        if (xRealIp != null && xRealIp.isNotEmpty()) {
            return xRealIp
        }
        
        return request.remoteAddr ?: "unknown"
    }

    private fun getBucketForClient(clientIp: String): Bucket {
        // 간단한 구현: 모든 클라이언트에 대해 동일한 버킷 사용
        // 실제 운영 환경에서는 Redis나 다른 분산 캐시를 사용해야 함
        return rateLimitBucket
    }

    private fun getResetTime(bucket: Bucket): Long {
        // 버킷이 리셋되는 시간을 계산
        return System.currentTimeMillis() + 60000 // 1분 후
    }
}
