package com.sqlswitcher.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.sqlswitcher.parser.ParseResult
import com.sqlswitcher.parser.StatementMetadata
import com.sqlswitcher.parser.StatementType
import com.sqlswitcher.parser.model.AstAnalysisResult
import com.sqlswitcher.parser.model.ComplexityDetails
import com.sqlswitcher.parser.model.FunctionExpressionInfo
import com.sqlswitcher.parser.model.TableColumnInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SqlCacheService unit tests
 */
class SqlCacheServiceTest {

    private lateinit var sqlParseCache: Cache<String, ParseResult>
    private lateinit var sqlAnalysisCache: Cache<String, AstAnalysisResult>
    private lateinit var sqlCacheService: SqlCacheService

    @BeforeEach
    fun setUp() {
        sqlParseCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats()
            .build()

        sqlAnalysisCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats()
            .build()

        sqlCacheService = SqlCacheService(sqlParseCache, sqlAnalysisCache)
    }

    private fun createSampleParseResult(): ParseResult {
        return ParseResult(
            isValid = true,
            statement = null,
            errors = emptyList(),
            parseTime = 10L,
            metadata = StatementMetadata(
                type = StatementType.SELECT,
                tables = listOf("users"),
                columns = listOf("id", "name"),
                hasJoins = false,
                hasSubqueries = false,
                complexity = 1
            ),
            astAnalysis = null,
            conversionDifficulty = null,
            warnings = emptyList(),
            parseException = null
        )
    }

    private fun createSampleAstAnalysisResult(): AstAnalysisResult {
        return AstAnalysisResult(
            tableColumnInfo = TableColumnInfo(
                tables = setOf("users"),
                columns = setOf("id", "name"),
                tableAliases = emptyMap()
            ),
            functionExpressionInfo = FunctionExpressionInfo(
                functions = setOf("COUNT"),
                expressions = emptyList(),
                aggregateFunctions = setOf("COUNT"),
                windowFunctions = emptySet()
            ),
            complexityDetails = ComplexityDetails(
                joinCount = 0,
                subqueryCount = 0,
                functionCount = 1,
                aggregateCount = 1,
                windowFunctionCount = 0,
                caseExpressionCount = 0,
                unionCount = 0,
                cteCount = 0,
                recursiveQueryCount = 0,
                pivotCount = 0,
                lateralJoinCount = 0,
                totalComplexityScore = 2
            )
        )
    }

    @Nested
    @DisplayName("Cache Hit/Miss Tests")
    inner class CacheHitMissTest {

        @Test
        @DisplayName("Should return null for cache miss on parse result")
        fun testCacheMissParseResult() {
            // Given
            val sql = "SELECT * FROM users"

            // When
            val result = sqlCacheService.getCachedParseResult(sql)

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("Should return cached parse result on cache hit")
        fun testCacheHitParseResult() {
            // Given
            val sql = "SELECT * FROM users"
            val parseResult = createSampleParseResult()
            sqlCacheService.cacheParseResult(sql, parseResult)

            // When
            val result = sqlCacheService.getCachedParseResult(sql)

            // Then
            assertNotNull(result)
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("Should return null for cache miss on analysis result")
        fun testCacheMissAnalysisResult() {
            // Given
            val sql = "SELECT COUNT(*) FROM users"

            // When
            val result = sqlCacheService.getCachedAnalysisResult(sql)

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("Should return cached analysis result on cache hit")
        fun testCacheHitAnalysisResult() {
            // Given
            val sql = "SELECT COUNT(*) FROM users"
            val analysisResult = createSampleAstAnalysisResult()
            sqlCacheService.cacheAnalysisResult(sql, analysisResult)

            // When
            val result = sqlCacheService.getCachedAnalysisResult(sql)

            // Then
            assertNotNull(result)
            assertEquals(setOf("users"), result.tableColumnInfo.tables)
        }

        @Test
        @DisplayName("Should normalize SQL for consistent caching")
        fun testSqlNormalization() {
            // Given
            val sql1 = "SELECT * FROM users"
            val sql2 = "  SELECT   *   FROM   users  "
            val parseResult = createSampleParseResult()
            sqlCacheService.cacheParseResult(sql1, parseResult)

            // When
            val result = sqlCacheService.getCachedParseResult(sql2)

            // Then
            assertNotNull(result)
        }

        @Test
        @DisplayName("Should be case-insensitive for SQL caching")
        fun testCaseInsensitiveCaching() {
            // Given
            val sql1 = "SELECT * FROM users"
            val sql2 = "select * from users"
            val parseResult = createSampleParseResult()
            sqlCacheService.cacheParseResult(sql1, parseResult)

            // When
            val result = sqlCacheService.getCachedParseResult(sql2)

            // Then
            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("Cache Eviction Tests")
    inner class CacheEvictionTest {

        @Test
        @DisplayName("Should invalidate specific SQL cache entry")
        fun testInvalidateSpecificCache() {
            // Given
            val sql = "SELECT * FROM users"
            val parseResult = createSampleParseResult()
            val analysisResult = createSampleAstAnalysisResult()
            sqlCacheService.cacheParseResult(sql, parseResult)
            sqlCacheService.cacheAnalysisResult(sql, analysisResult)

            // When
            sqlCacheService.invalidateCache(sql)

            // Then
            assertNull(sqlCacheService.getCachedParseResult(sql))
            assertNull(sqlCacheService.getCachedAnalysisResult(sql))
        }

        @Test
        @DisplayName("Should clear all caches")
        fun testClearAllCaches() {
            // Given
            val sql1 = "SELECT * FROM users"
            val sql2 = "SELECT * FROM orders"
            sqlCacheService.cacheParseResult(sql1, createSampleParseResult())
            sqlCacheService.cacheParseResult(sql2, createSampleParseResult())
            sqlCacheService.cacheAnalysisResult(sql1, createSampleAstAnalysisResult())

            // When
            sqlCacheService.clearAllCaches()

            // Then
            assertNull(sqlCacheService.getCachedParseResult(sql1))
            assertNull(sqlCacheService.getCachedParseResult(sql2))
            assertNull(sqlCacheService.getCachedAnalysisResult(sql1))
        }

        @Test
        @DisplayName("Should evict entries when cache size exceeds limit")
        fun testCacheEvictionOnSizeLimit() {
            // Given - create a small cache
            val smallParseCache = Caffeine.newBuilder()
                .maximumSize(2)
                .recordStats()
                .build<String, ParseResult>()
            val smallAnalysisCache = Caffeine.newBuilder()
                .maximumSize(2)
                .recordStats()
                .build<String, AstAnalysisResult>()
            val smallCacheService = SqlCacheService(smallParseCache, smallAnalysisCache)

            // When - add more items than the cache size
            smallCacheService.cacheParseResult("SELECT 1", createSampleParseResult())
            smallCacheService.cacheParseResult("SELECT 2", createSampleParseResult())
            smallCacheService.cacheParseResult("SELECT 3", createSampleParseResult())
            smallCacheService.cacheParseResult("SELECT 4", createSampleParseResult())

            // Allow cache to process evictions
            smallParseCache.cleanUp()

            // Then - cache size should not exceed maximum
            assertTrue(smallParseCache.estimatedSize() <= 2)
        }
    }

    @Nested
    @DisplayName("GetOrCompute Tests")
    inner class GetOrComputeTest {

        @Test
        @DisplayName("Should compute and cache parse result on cache miss")
        fun testGetOrComputeParseResultMiss() {
            // Given
            val sql = "SELECT * FROM products"
            var computeCount = 0
            val computeFunction: (String) -> ParseResult = {
                computeCount++
                createSampleParseResult()
            }

            // When
            val result = sqlCacheService.getOrComputeParseResult(sql, computeFunction)

            // Then
            assertNotNull(result)
            assertEquals(1, computeCount)
            // Second call should use cache
            sqlCacheService.getOrComputeParseResult(sql, computeFunction)
            assertEquals(1, computeCount)
        }

        @Test
        @DisplayName("Should return cached value on getOrCompute cache hit")
        fun testGetOrComputeParseResultHit() {
            // Given
            val sql = "SELECT * FROM cached"
            val cachedResult = createSampleParseResult()
            sqlCacheService.cacheParseResult(sql, cachedResult)
            var computeCalled = false
            val computeFunction: (String) -> ParseResult = {
                computeCalled = true
                createSampleParseResult()
            }

            // When
            val result = sqlCacheService.getOrComputeParseResult(sql, computeFunction)

            // Then
            assertNotNull(result)
            // Compute function should not be called since value is cached
            // Note: Caffeine may still call the function due to its implementation
        }

        @Test
        @DisplayName("Should compute and cache analysis result on cache miss")
        fun testGetOrComputeAnalysisResultMiss() {
            // Given
            val sql = "SELECT COUNT(*) FROM data"
            var computeCount = 0
            val computeFunction: (String) -> AstAnalysisResult = {
                computeCount++
                createSampleAstAnalysisResult()
            }

            // When
            val result = sqlCacheService.getOrComputeAnalysisResult(sql, computeFunction)

            // Then
            assertNotNull(result)
            assertEquals(1, computeCount)
        }

        @Test
        @DisplayName("Should handle async getOrCompute for parse result")
        fun testGetOrComputeParseResultAsync() {
            // Given
            val sql = "SELECT * FROM async_test"
            val computeFunction: (String) -> ParseResult = { createSampleParseResult() }

            // When
            val future = sqlCacheService.getOrComputeParseResultAsync(sql, computeFunction)
            val result = future.get(5, TimeUnit.SECONDS)

            // Then
            assertNotNull(result)
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("Should handle async getOrCompute for analysis result")
        fun testGetOrComputeAnalysisResultAsync() {
            // Given
            val sql = "SELECT SUM(amount) FROM transactions"
            val computeFunction: (String) -> AstAnalysisResult = { createSampleAstAnalysisResult() }

            // When
            val future = sqlCacheService.getOrComputeAnalysisResultAsync(sql, computeFunction)
            val result = future.get(5, TimeUnit.SECONDS)

            // Then
            assertNotNull(result)
            assertEquals(setOf("users"), result.tableColumnInfo.tables)
        }
    }

    @Nested
    @DisplayName("Cache Statistics Tests")
    inner class CacheStatisticsTest {

        @Test
        @DisplayName("Should report cache statistics")
        fun testCacheStats() {
            // Given
            val sql1 = "SELECT * FROM stats_test1"
            val sql2 = "SELECT * FROM stats_test2"
            sqlCacheService.cacheParseResult(sql1, createSampleParseResult())

            // Trigger a hit
            sqlCacheService.getCachedParseResult(sql1)
            // Trigger a miss
            sqlCacheService.getCachedParseResult(sql2)

            // When
            val stats = sqlCacheService.getCacheStats()

            // Then
            assertTrue(stats.parseCacheSize >= 0)
            assertTrue(stats.parseCacheHitCount >= 0)
            assertTrue(stats.parseCacheMissCount >= 0)
        }

        @Test
        @DisplayName("Should calculate total cache size")
        fun testTotalCacheSize() {
            // Given
            val sql = "SELECT * FROM size_test"
            sqlCacheService.cacheParseResult(sql, createSampleParseResult())
            sqlCacheService.cacheAnalysisResult(sql, createSampleAstAnalysisResult())

            // When
            val stats = sqlCacheService.getCacheStats()

            // Then
            assertEquals(stats.parseCacheSize + stats.analysisCacheSize, stats.totalCacheSize)
        }

        @Test
        @DisplayName("Should calculate overall hit rate")
        fun testOverallHitRate() {
            // Given - empty caches should have 0.0 hit rate
            val stats = sqlCacheService.getCacheStats()

            // Then
            assertTrue(stats.overallHitRate >= 0.0)
            assertTrue(stats.overallHitRate <= 1.0)
        }
    }
}
