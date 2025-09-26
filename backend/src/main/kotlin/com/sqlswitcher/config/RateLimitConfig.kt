package com.sqlswitcher.config

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class RateLimitConfig {

    @Bean
    fun rateLimitBucket(): Bucket {
        // IP당 분당 100 요청 제한
        val limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)))
        return Bucket.builder()
            .addLimit(limit)
            .build()
    }
}
