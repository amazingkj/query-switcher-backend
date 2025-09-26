package com.sqlswitcher.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {
    
    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager()
        cacheManager.setCaffeine(caffeineCacheBuilder())
        return cacheManager
    }
    
    @Bean
    fun sqlParseCache(): com.github.benmanes.caffeine.cache.Cache<String, com.sqlswitcher.parser.ParseResult> {
        return Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .expireAfterAccess(Duration.ofMinutes(5))
            .recordStats()
            .build()
    }
    
    @Bean
    fun sqlAnalysisCache(): com.github.benmanes.caffeine.cache.Cache<String, com.sqlswitcher.parser.model.AstAnalysisResult> {
        return Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .recordStats()
            .build()
    }
    
    private fun caffeineCacheBuilder(): Caffeine<Any, Any> {
        return Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .expireAfterAccess(Duration.ofMinutes(5))
            .recordStats()
    }
}
