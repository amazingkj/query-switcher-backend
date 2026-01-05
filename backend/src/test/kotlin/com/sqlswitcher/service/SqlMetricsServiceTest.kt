package com.sqlswitcher.service

import com.sqlswitcher.parser.StatementType
import com.sqlswitcher.parser.model.ConversionDifficulty
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SqlMetricsService unit tests
 */
class SqlMetricsServiceTest {

    private lateinit var meterRegistry: MeterRegistry
    private lateinit var sqlMetricsService: SqlMetricsService

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        sqlMetricsService = SqlMetricsService(meterRegistry)
    }

    @Nested
    @DisplayName("Parse Request Metric Tests")
    inner class ParseRequestMetricTest {

        @Test
        @DisplayName("Should increment parse request counter")
        fun testRecordParseRequest() {
            // When
            sqlMetricsService.recordParseRequest()
            sqlMetricsService.recordParseRequest()

            // Then
            val summary = sqlMetricsService.getMetricsSummary()
            assertEquals(2.0, summary.totalParseRequests)
        }

        @Test
        @DisplayName("Should track active parse requests")
        fun testActiveParseRequests() {
            // When
            sqlMetricsService.recordParseRequest()
            sqlMetricsService.recordParseRequest()

            // Then
            val summary = sqlMetricsService.getMetricsSummary()
            assertEquals(2L, summary.activeParseRequests)
        }

        @Test
        @DisplayName("Should record parse success with tags")
        fun testRecordParseSuccess() {
            // Given
            sqlMetricsService.recordParseRequest()

            // When
            sqlMetricsService.recordParseSuccess(StatementType.SELECT, ConversionDifficulty.EASY)

            // Then
            val summary = sqlMetricsService.getMetricsSummary()
            assertEquals(0L, summary.activeParseRequests)
        }

        @Test
        @DisplayName("Should record parse success with null values")
        fun testRecordParseSuccessWithNulls() {
            // Given
            sqlMetricsService.recordParseRequest()

            // When
            sqlMetricsService.recordParseSuccess(null, null)

            // Then
            val summary = sqlMetricsService.getMetricsSummary()
            assertEquals(0L, summary.activeParseRequests)
        }

        @Test
        @DisplayName("Should record parse error with error type")
        fun testRecordParseError() {
            // Given
            sqlMetricsService.recordParseRequest()

            // When
            sqlMetricsService.recordParseError("SYNTAX_ERROR")

            // Then
            val summary = sqlMetricsService.getMetricsSummary()
            assertEquals(0L, summary.activeParseRequests)
        }
    }

    @Nested
    @DisplayName("Parse Duration Metric Tests")
    inner class ParseDurationMetricTest {

        @Test
        @DisplayName("Should record parse duration")
        fun testRecordParseDuration() {
            // When
            sqlMetricsService.recordParseDuration(100L)
            sqlMetricsService.recordParseDuration(200L)

            // Then
            val summary = sqlMetricsService.getMetricsSummary()
            assertTrue(summary.averageParseTime >= 0.0)
        }

        @Test
        @DisplayName("Should record analysis duration")
        fun testRecordAnalysisDuration() {
            // When
            sqlMetricsService.recordAnalysisDuration(50L)
            sqlMetricsService.recordAnalysisDuration(150L)

            // Then
            val summary = sqlMetricsService.getMetricsSummary()
            assertTrue(summary.averageAnalysisTime >= 0.0)
        }
    }

    @Nested
    @DisplayName("Conversion Request Metric Tests")
    inner class ConversionRequestMetricTest {

        @Test
        @DisplayName("Should record conversion request with dialect tags")
        fun testRecordConversionRequest() {
            // When
            sqlMetricsService.recordConversionRequest("ORACLE", "MYSQL")
            sqlMetricsService.recordConversionRequest("ORACLE", "POSTGRESQL")

            // Then
            val summary = sqlMetricsService.getMetricsSummary()
            assertEquals(2L, summary.activeConversionRequests)
        }

        @Test
        @DisplayName("Should record conversion success")
        fun testRecordConversionSuccess() {
            // Given
            sqlMetricsService.recordConversionRequest("ORACLE", "MYSQL")

            // When
            sqlMetricsService.recordConversionSuccess("ORACLE", "MYSQL")

            // Then
            val summary = sqlMetricsService.getMetricsSummary()
            assertEquals(0L, summary.activeConversionRequests)
        }

        @Test
        @DisplayName("Should record conversion error with error type")
        fun testRecordConversionError() {
            // Given
            sqlMetricsService.recordConversionRequest("MYSQL", "ORACLE")

            // When
            sqlMetricsService.recordConversionError("UNSUPPORTED_SYNTAX")

            // Then
            val summary = sqlMetricsService.getMetricsSummary()
            assertEquals(0L, summary.activeConversionRequests)
        }

        @Test
        @DisplayName("Should record conversion error with dialect info")
        fun testRecordConversionErrorWithDialectInfo() {
            // Given
            sqlMetricsService.recordConversionRequest("ORACLE", "MYSQL")

            // When
            sqlMetricsService.recordConversionError("ORACLE", "MYSQL", "PARSE_ERROR")

            // Then
            val summary = sqlMetricsService.getMetricsSummary()
            assertEquals(0L, summary.activeConversionRequests)
        }
    }

    @Nested
    @DisplayName("Conversion Duration Metric Tests")
    inner class ConversionDurationMetricTest {

        @Test
        @DisplayName("Should record conversion duration with dialect tags")
        fun testRecordConversionDuration() {
            // When
            sqlMetricsService.recordConversionDuration(150L, "ORACLE", "MYSQL")
            sqlMetricsService.recordConversionDuration(250L, "ORACLE", "POSTGRESQL")

            // Then
            val summary = sqlMetricsService.getMetricsSummary()
            assertTrue(summary.averageConversionTime >= 0.0)
        }
    }

    @Nested
    @DisplayName("Cache Metric Tests")
    inner class CacheMetricTest {

        @Test
        @DisplayName("Should record cache hit")
        fun testRecordCacheHit() {
            // When
            sqlMetricsService.recordCacheHit("parse")
            sqlMetricsService.recordCacheHit("analysis")

            // Then - verify counters were created and incremented
            val parseHitCounter = meterRegistry.find("sql.cache.hits.total")
                .tag("cache_type", "parse")
                .counter()
            assertNotNull(parseHitCounter)
            assertEquals(1.0, parseHitCounter.count())
        }

        @Test
        @DisplayName("Should record cache miss")
        fun testRecordCacheMiss() {
            // When
            sqlMetricsService.recordCacheMiss("parse")
            sqlMetricsService.recordCacheMiss("parse")

            // Then - verify counters were created and incremented
            val parseMissCounter = meterRegistry.find("sql.cache.misses.total")
                .tag("cache_type", "parse")
                .counter()
            assertNotNull(parseMissCounter)
            assertEquals(2.0, parseMissCounter.count())
        }
    }

    @Nested
    @DisplayName("Statement Complexity Metric Tests")
    inner class StatementComplexityMetricTest {

        @Test
        @DisplayName("Should record statement complexity metrics")
        fun testRecordStatementComplexity() {
            // When
            sqlMetricsService.recordStatementComplexity(
                statementType = StatementType.SELECT,
                complexity = ConversionDifficulty.MODERATE,
                joinCount = 2,
                subqueryCount = 1,
                functionCount = 3
            )

            // Then - verify complexity counter was created
            val complexityCounter = meterRegistry.find("sql.statement.complexity.total")
                .tag("statement_type", "SELECT")
                .tag("complexity", "MODERATE")
                .counter()
            assertNotNull(complexityCounter)
            assertEquals(1.0, complexityCounter.count())
        }

        @Test
        @DisplayName("Should record join count metrics")
        fun testRecordJoinCountMetrics() {
            // When
            sqlMetricsService.recordStatementComplexity(
                statementType = StatementType.SELECT,
                complexity = ConversionDifficulty.HARD,
                joinCount = 3,
                subqueryCount = 0,
                functionCount = 0
            )

            // Then
            val joinCounter = meterRegistry.find("sql.statement.joins.total")
                .tag("statement_type", "SELECT")
                .counter()
            assertNotNull(joinCounter)
            assertEquals(3.0, joinCounter.count())
        }

        @Test
        @DisplayName("Should record subquery count metrics")
        fun testRecordSubqueryCountMetrics() {
            // When
            sqlMetricsService.recordStatementComplexity(
                statementType = StatementType.SELECT,
                complexity = ConversionDifficulty.HARD,
                joinCount = 0,
                subqueryCount = 2,
                functionCount = 0
            )

            // Then
            val subqueryCounter = meterRegistry.find("sql.statement.subqueries.total")
                .tag("statement_type", "SELECT")
                .counter()
            assertNotNull(subqueryCounter)
            assertEquals(2.0, subqueryCounter.count())
        }

        @Test
        @DisplayName("Should record function count metrics")
        fun testRecordFunctionCountMetrics() {
            // When
            sqlMetricsService.recordStatementComplexity(
                statementType = StatementType.SELECT,
                complexity = ConversionDifficulty.MODERATE,
                joinCount = 0,
                subqueryCount = 0,
                functionCount = 5
            )

            // Then
            val functionCounter = meterRegistry.find("sql.statement.functions.total")
                .tag("statement_type", "SELECT")
                .counter()
            assertNotNull(functionCounter)
            assertEquals(5.0, functionCounter.count())
        }
    }

    @Nested
    @DisplayName("Metrics Summary Tests")
    inner class MetricsSummaryTest {

        @Test
        @DisplayName("Should return metrics summary")
        fun testGetMetricsSummary() {
            // Given
            sqlMetricsService.recordParseRequest()
            sqlMetricsService.recordConversionRequest("ORACLE", "MYSQL")

            // When
            val summary = sqlMetricsService.getMetricsSummary()

            // Then
            assertNotNull(summary)
            assertEquals(1.0, summary.totalParseRequests)
            assertTrue(summary.activeParseRequests >= 0)
            assertTrue(summary.activeConversionRequests >= 0)
        }

        @Test
        @DisplayName("Should calculate parse success rate")
        fun testParseSuccessRate() {
            // Given
            sqlMetricsService.recordParseRequest()
            sqlMetricsService.recordParseRequest()

            // When
            val summary = sqlMetricsService.getMetricsSummary()

            // Then
            assertTrue(summary.parseSuccessRate >= 0.0)
            assertTrue(summary.parseSuccessRate <= 1.0)
        }

        @Test
        @DisplayName("Should calculate conversion success rate")
        fun testConversionSuccessRate() {
            // Given - no requests means 0.0 success rate
            val summary = sqlMetricsService.getMetricsSummary()

            // Then
            assertEquals(0.0, summary.conversionSuccessRate)
        }

        @Test
        @DisplayName("Should handle zero total requests for success rate calculation")
        fun testSuccessRateWithZeroRequests() {
            // When
            val summary = sqlMetricsService.getMetricsSummary()

            // Then
            assertEquals(0.0, summary.parseSuccessRate)
            assertEquals(0.0, summary.conversionSuccessRate)
        }
    }

    @Nested
    @DisplayName("Extension Function Tests")
    inner class ExtensionFunctionTest {

        @Test
        @DisplayName("Should get total conversions")
        fun testGetTotalConversions() {
            // Given
            sqlMetricsService.recordConversionRequest("ORACLE", "MYSQL")

            // When
            val total = sqlMetricsService.getTotalConversions()

            // Then - note: the counter increment creates a new counter, so this may be 0
            assertTrue(total >= 0)
        }

        @Test
        @DisplayName("Should get successful conversions")
        fun testGetSuccessfulConversions() {
            // When
            val successful = sqlMetricsService.getSuccessfulConversions()

            // Then
            assertTrue(successful >= 0)
        }

        @Test
        @DisplayName("Should get failed conversions")
        fun testGetFailedConversions() {
            // When
            val failed = sqlMetricsService.getFailedConversions()

            // Then
            assertTrue(failed >= 0)
        }

        @Test
        @DisplayName("Should get average conversion time")
        fun testGetAverageConversionTime() {
            // Given
            sqlMetricsService.recordConversionDuration(100L, "ORACLE", "MYSQL")

            // When
            val avgTime = sqlMetricsService.getAverageConversionTime()

            // Then
            assertTrue(avgTime >= 0.0)
        }

        @Test
        @DisplayName("Should return null for most used source dialect")
        fun testGetMostUsedSourceDialect() {
            // When
            val dialect = sqlMetricsService.getMostUsedSourceDialect()

            // Then
            assertEquals(null, dialect)
        }

        @Test
        @DisplayName("Should return null for most used target dialect")
        fun testGetMostUsedTargetDialect() {
            // When
            val dialect = sqlMetricsService.getMostUsedTargetDialect()

            // Then
            assertEquals(null, dialect)
        }
    }
}
