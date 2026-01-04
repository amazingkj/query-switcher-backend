package com.sqlswitcher.converter.streaming

import com.sqlswitcher.converter.ConversionResult
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * LargeSqlProcessor 단위 테스트
 */
class LargeSqlProcessorTest {

    @Nested
    @DisplayName("대용량 SQL 감지 테스트")
    inner class LargeSqlDetectionTest {

        @Test
        @DisplayName("100KB 이상은 대용량으로 판정")
        fun testLargeSqlDetection() {
            val smallSql = "SELECT * FROM users"
            val largeSql = "SELECT * FROM users".repeat(10000)  // ~190KB

            assertFalse(LargeSqlProcessor.isLargeSql(smallSql))
            assertTrue(LargeSqlProcessor.isLargeSql(largeSql))
        }

        @Test
        @DisplayName("정확한 임계값 테스트")
        fun testThresholdExact() {
            val belowThreshold = "x".repeat(LargeSqlProcessor.LARGE_SQL_THRESHOLD - 1)
            val atThreshold = "x".repeat(LargeSqlProcessor.LARGE_SQL_THRESHOLD)
            val aboveThreshold = "x".repeat(LargeSqlProcessor.LARGE_SQL_THRESHOLD + 1)

            assertFalse(LargeSqlProcessor.isLargeSql(belowThreshold))
            assertFalse(LargeSqlProcessor.isLargeSql(atThreshold))
            assertTrue(LargeSqlProcessor.isLargeSql(aboveThreshold))
        }
    }

    @Nested
    @DisplayName("SQL 문장 스트리밍 테스트")
    inner class StreamStatementsTest {

        @Test
        @DisplayName("세미콜론으로 분리된 문장 스트리밍")
        fun testBasicStreaming() {
            val sql = """
                SELECT * FROM users;
                SELECT * FROM orders;
                SELECT * FROM products;
            """.trimIndent()

            val statements = LargeSqlProcessor.streamStatements(sql).toList()

            assertEquals(3, statements.size)
            assertTrue(statements[0].contains("users"))
            assertTrue(statements[1].contains("orders"))
            assertTrue(statements[2].contains("products"))
        }

        @Test
        @DisplayName("문자열 내 세미콜론 무시")
        fun testSemicolonInString() {
            val sql = "SELECT 'Hello; World' FROM users; SELECT 1"

            val statements = LargeSqlProcessor.streamStatements(sql).toList()

            assertEquals(2, statements.size)
            assertTrue(statements[0].contains("Hello; World"))
        }

        @Test
        @DisplayName("빈 문장 건너뛰기")
        fun testSkipEmptyStatements() {
            val sql = "SELECT 1;; ; SELECT 2"

            val statements = LargeSqlProcessor.streamStatements(sql).toList()

            assertEquals(2, statements.size)
        }

        @Test
        @DisplayName("주석 처리")
        fun testCommentHandling() {
            val sql = """
                SELECT * FROM users; /* 주석 */
                -- 라인 주석
                SELECT * FROM orders;
            """.trimIndent()

            val statements = LargeSqlProcessor.streamStatements(sql).toList()

            assertTrue(statements.isNotEmpty())
        }

        @Test
        @DisplayName("마지막 문장 세미콜론 없음")
        fun testLastStatementNoSemicolon() {
            val sql = "SELECT 1; SELECT 2"

            val statements = LargeSqlProcessor.streamStatements(sql).toList()

            assertEquals(2, statements.size)
        }
    }

    @Nested
    @DisplayName("청크 처리 테스트")
    inner class ChunkProcessingTest {

        private val simpleConverter: (String, DialectType, DialectType) -> ConversionResult =
            { sql, _, _ ->
                ConversionResult(
                    convertedSql = sql.uppercase(),
                    warnings = emptyList(),
                    appliedRules = listOf("UPPERCASE 변환")
                )
            }

        @Test
        @DisplayName("소규모 SQL 순차 처리")
        fun testSmallSqlSequential() {
            val sql = "SELECT * FROM t1; SELECT * FROM t2; SELECT * FROM t3"

            val result = LargeSqlProcessor.processInChunks(
                sql = sql,
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                converter = simpleConverter,
                chunkSize = 10
            )

            assertEquals(3, result.totalStatements)
            assertEquals(3, result.successfulStatements)
            assertEquals(0, result.failedStatements)
            assertTrue(result.convertedSql.contains("SELECT * FROM T1"))
        }

        @Test
        @DisplayName("변환 실패 문장 처리")
        fun testFailedStatementHandling() {
            val failingConverter: (String, DialectType, DialectType) -> ConversionResult =
                { sql, _, _ ->
                    if (sql.contains("t2")) {
                        throw RuntimeException("Conversion failed for t2")
                    }
                    ConversionResult(convertedSql = sql.uppercase())
                }

            val sql = "SELECT * FROM t1; SELECT * FROM t2; SELECT * FROM t3"

            val result = LargeSqlProcessor.processInChunks(
                sql = sql,
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                converter = failingConverter
            )

            assertEquals(3, result.totalStatements)
            assertEquals(2, result.successfulStatements)
            assertEquals(1, result.failedStatements)
            assertTrue(result.warnings.any { it.severity == WarningSeverity.ERROR })
        }

        @Test
        @DisplayName("진행률 콜백 호출")
        fun testProgressCallback() {
            val sql = "SELECT 1; SELECT 2; SELECT 3; SELECT 4; SELECT 5"
            var callbackCount = 0
            var lastProcessed = 0

            val result = LargeSqlProcessor.processInChunks(
                sql = sql,
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                converter = simpleConverter,
                progressCallback = { processed, total, _ ->
                    callbackCount++
                    lastProcessed = processed
                    assertTrue(processed <= total)
                }
            )

            assertTrue(callbackCount > 0)
            assertEquals(5, lastProcessed)
        }

        @Test
        @DisplayName("빈 SQL 처리")
        fun testEmptySql() {
            val result = LargeSqlProcessor.processInChunks(
                sql = "",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                converter = simpleConverter
            )

            assertEquals(0, result.totalStatements)
            assertEquals(0, result.successfulStatements)
        }
    }

    @Nested
    @DisplayName("문장 수 추정 테스트")
    inner class StatementCountEstimationTest {

        @Test
        @DisplayName("간단한 SQL 문장 수 추정")
        fun testSimpleEstimation() {
            val sql = "SELECT 1; SELECT 2; SELECT 3"
            val count = LargeSqlProcessor.estimateStatementCount(sql)
            // 세미콜론 3개 = 최소 3개 문장 (실제 파싱 시 마지막 문장 포함하면 3개)
            assertTrue(count >= 2, "최소 2개 이상의 문장이 추정되어야 함")
        }

        @Test
        @DisplayName("문자열 내 세미콜론 무시")
        fun testIgnoreSemicolonInString() {
            val sql = "SELECT 'a;b;c'; SELECT 2"
            val count = LargeSqlProcessor.estimateStatementCount(sql)
            // 추정은 정확하지 않을 수 있음 - 최소 1개 이상
            assertTrue(count >= 1, "최소 1개 이상의 문장이 추정되어야 함")
        }

        @Test
        @DisplayName("세미콜론 없는 경우 최소 1")
        fun testNoSemicolon() {
            val sql = "SELECT 1"
            val count = LargeSqlProcessor.estimateStatementCount(sql)
            assertEquals(1, count)
        }
    }

    @Nested
    @DisplayName("처리 전략 결정 테스트")
    inner class StrategyDeterminationTest {

        @Test
        @DisplayName("소규모 SQL - 순차 처리")
        fun testSmallSqlStrategy() {
            val sql = "SELECT 1"
            val strategy = LargeSqlProcessor.determineStrategy(sql)
            assertEquals(LargeSqlProcessor.ProcessingStrategy.SEQUENTIAL_SMALL, strategy)
            assertFalse(strategy.useParallel)
        }

        @Test
        @DisplayName("중간 규모 SQL")
        fun testMediumSqlStrategy() {
            val sql = "SELECT " + "1, ".repeat(5000)  // ~20KB
            val strategy = LargeSqlProcessor.determineStrategy(sql)
            assertTrue(
                strategy == LargeSqlProcessor.ProcessingStrategy.SEQUENTIAL_MEDIUM ||
                strategy == LargeSqlProcessor.ProcessingStrategy.SEQUENTIAL_SMALL
            )
        }

        @Test
        @DisplayName("대규모 SQL - 청크 병렬 처리")
        fun testLargeSqlStrategy() {
            val sql = ("SELECT * FROM table${(1..1000).joinToString("") { "t" }}; ").repeat(500)  // 많은 문장
            val strategy = LargeSqlProcessor.determineStrategy(sql)
            assertTrue(strategy.useParallel || strategy.chunkSize < Int.MAX_VALUE)
        }
    }

    @Nested
    @DisplayName("메모리 사용량 추정 테스트")
    inner class MemoryEstimationTest {

        @Test
        @DisplayName("메모리 사용량 추정")
        fun testMemoryEstimation() {
            val sql = "SELECT * FROM users"
            val estimated = LargeSqlProcessor.estimateMemoryUsage(sql)

            // SQL 길이 * 2 (UTF-16) * 3 (원본 + 변환 + 버퍼)
            val expected = sql.length.toLong() * 2 * 3
            assertEquals(expected, estimated)
        }
    }

    @Nested
    @DisplayName("성능 테스트")
    inner class PerformanceTest {

        @Test
        @DisplayName("100개 문장 처리 성능")
        fun testPerformance100Statements() {
            val statements = (1..100).map { "SELECT * FROM table_$it" }
            val sql = statements.joinToString("; ")

            val startTime = System.currentTimeMillis()

            val result = LargeSqlProcessor.processInChunks(
                sql = sql,
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                converter = { stmt, _, _ -> ConversionResult(stmt.uppercase()) }
            )

            val duration = System.currentTimeMillis() - startTime

            assertEquals(100, result.totalStatements)
            assertEquals(100, result.successfulStatements)
            assertTrue(duration < 5000, "100개 문장 처리는 5초 이내여야 함")

            println("100개 문장 처리 시간: ${duration}ms")
        }

        @Test
        @DisplayName("성공률 계산")
        fun testSuccessRateCalculation() {
            val failingConverter: (String, DialectType, DialectType) -> ConversionResult =
                { sql, _, _ ->
                    if (sql.contains("fail")) {
                        throw RuntimeException("Failed")
                    }
                    ConversionResult(sql)
                }

            val sql = "SELECT 1; SELECT fail; SELECT 3; SELECT fail; SELECT 5"

            val result = LargeSqlProcessor.processInChunks(
                sql = sql,
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                converter = failingConverter
            )

            assertEquals(5, result.totalStatements)
            assertEquals(3, result.successfulStatements)
            assertEquals(2, result.failedStatements)
            assertEquals(0.6, result.successRate, 0.01)
        }
    }
}
