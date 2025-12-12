package com.sqlswitcher.service

import com.github.benmanes.caffeine.cache.Cache
import com.sqlswitcher.parser.model.AstAnalysisResult
import com.sqlswitcher.parser.ParseResult
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture

@Service
class SqlCacheService(
    private val sqlParseCache: Cache<String, ParseResult>,
    private val sqlAnalysisCache: Cache<String, AstAnalysisResult>
) {
    
    /**
     * Get cached parse result for SQL
     */
    fun getCachedParseResult(sql: String): ParseResult? {
        val cacheKey = generateCacheKey(sql)
        return sqlParseCache.getIfPresent(cacheKey)
    }
    
    /**
     * Cache parse result for SQL
     */
    fun cacheParseResult(sql: String, parseResult: ParseResult) {
        val cacheKey = generateCacheKey(sql)
        sqlParseCache.put(cacheKey, parseResult)
    }
    
    /**
     * Get cached analysis result for SQL
     */
    fun getCachedAnalysisResult(sql: String): AstAnalysisResult? {
        val cacheKey = generateCacheKey(sql)
        return sqlAnalysisCache.getIfPresent(cacheKey)
    }
    
    /**
     * Cache analysis result for SQL
     */
    fun cacheAnalysisResult(sql: String, analysisResult: AstAnalysisResult) {
        val cacheKey = generateCacheKey(sql)
        sqlAnalysisCache.put(cacheKey, analysisResult)
    }
    
    /**
     * Get or compute parse result with caching
     */
    fun getOrComputeParseResult(
        sql: String, 
        computeFunction: (String) -> ParseResult
    ): ParseResult {
        val cacheKey = generateCacheKey(sql)
        return sqlParseCache.get(cacheKey) { computeFunction(sql) }
    }
    
    /**
     * Get or compute analysis result with caching
     */
    fun getOrComputeAnalysisResult(
        sql: String, 
        computeFunction: (String) -> AstAnalysisResult
    ): AstAnalysisResult {
        val cacheKey = generateCacheKey(sql)
        return sqlAnalysisCache.get(cacheKey) { computeFunction(sql) }
    }
    
    /**
     * Async get or compute parse result with caching
     */
    fun getOrComputeParseResultAsync(
        sql: String, 
        computeFunction: (String) -> ParseResult
    ): CompletableFuture<ParseResult> {
        return CompletableFuture.supplyAsync {
            getOrComputeParseResult(sql, computeFunction)
        }
    }
    
    /**
     * Async get or compute analysis result with caching
     */
    fun getOrComputeAnalysisResultAsync(
        sql: String, 
        computeFunction: (String) -> AstAnalysisResult
    ): CompletableFuture<AstAnalysisResult> {
        return CompletableFuture.supplyAsync {
            getOrComputeAnalysisResult(sql, computeFunction)
        }
    }
    
    /**
     * Invalidate cache for specific SQL
     */
    fun invalidateCache(sql: String) {
        val cacheKey = generateCacheKey(sql)
        sqlParseCache.invalidate(cacheKey)
        sqlAnalysisCache.invalidate(cacheKey)
    }
    
    /**
     * Clear all caches
     */
    fun clearAllCaches() {
        sqlParseCache.invalidateAll()
        sqlAnalysisCache.invalidateAll()
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val parseStats = sqlParseCache.stats()
        val analysisStats = sqlAnalysisCache.stats()
        
        return CacheStats(
            parseCacheSize = sqlParseCache.estimatedSize(),
            analysisCacheSize = sqlAnalysisCache.estimatedSize(),
            parseCacheHitRate = parseStats.hitRate(),
            analysisCacheHitRate = analysisStats.hitRate(),
            parseCacheHitCount = parseStats.hitCount(),
            analysisCacheHitCount = analysisStats.hitCount(),
            parseCacheMissCount = parseStats.missCount(),
            analysisCacheMissCount = analysisStats.missCount(),
            parseCacheEvictionCount = parseStats.evictionCount(),
            analysisCacheEvictionCount = analysisStats.evictionCount()
        )
    }
    
    /**
     * Generate cache key from SQL string
     */
    private fun generateCacheKey(sql: String): String {
        // Normalize SQL for consistent caching
        val normalizedSql = sql.trim()
            .replace(Regex("\\s+"), " ") // Replace multiple whitespaces with single space
            .uppercase() // Convert to uppercase for case-insensitive caching
        
        // Generate SHA-256 hash for consistent key length
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(normalizedSql.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}

data class CacheStats(
    val parseCacheSize: Long,
    val analysisCacheSize: Long,
    val parseCacheHitRate: Double,
    val analysisCacheHitRate: Double,
    val parseCacheHitCount: Long,
    val analysisCacheHitCount: Long,
    val parseCacheMissCount: Long,
    val analysisCacheMissCount: Long,
    val parseCacheEvictionCount: Long,
    val analysisCacheEvictionCount: Long
) {
    val totalCacheSize: Long
        get() = parseCacheSize + analysisCacheSize
    
    val overallHitRate: Double
        get() = if (parseCacheHitCount + analysisCacheHitCount + parseCacheMissCount + analysisCacheMissCount > 0) {
            (parseCacheHitCount + analysisCacheHitCount).toDouble() / 
            (parseCacheHitCount + analysisCacheHitCount + parseCacheMissCount + analysisCacheMissCount)
        } else {
            0.0
        }
}
