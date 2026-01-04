package com.sqlswitcher.converter.recovery

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * AdvancedRecoveryStrategies 테스트
 */
class AdvancedRecoveryStrategiesTest {

    @Nested
    @DisplayName("주석 제거 전략 테스트")
    inner class CommentRemovalStrategyTest {

        @Test
        @DisplayName("단일 줄 주석 제거")
        fun testSingleLineCommentRemoval() {
            val sql = "SELECT * FROM users -- this is a comment\nWHERE id = 1"
            val error = Exception("parse error")

            val attempt = AdvancedRecoveryStrategies.CommentRemovalStrategy.recover(
                sql, error, DialectType.MYSQL
            )

            assertTrue(attempt.success)
            assertFalse(attempt.recoveredSql.contains("--"))
            assertTrue(attempt.recoveredSql.contains("SELECT"))
            assertTrue(attempt.recoveredSql.contains("WHERE"))
        }

        @Test
        @DisplayName("여러 줄 주석 제거")
        fun testMultiLineCommentRemoval() {
            val sql = "SELECT /* this is a \n multi-line comment */ * FROM users"
            val error = Exception("parse error")

            val attempt = AdvancedRecoveryStrategies.CommentRemovalStrategy.recover(
                sql, error, DialectType.MYSQL
            )

            assertTrue(attempt.success)
            assertFalse(attempt.recoveredSql.contains("/*"))
            assertTrue(attempt.recoveredSql.contains("SELECT"))
        }
    }

    @Nested
    @DisplayName("힌트 제거 전략 테스트")
    inner class HintRemovalStrategyTest {

        @Test
        @DisplayName("Oracle 힌트 제거")
        fun testOracleHintRemoval() {
            val sql = "SELECT /*+ INDEX(e emp_idx) PARALLEL(4) */ * FROM employees e"
            val error = Exception("parse error")

            assertTrue(AdvancedRecoveryStrategies.HintRemovalStrategy.canHandle(sql, error))

            val attempt = AdvancedRecoveryStrategies.HintRemovalStrategy.recover(
                sql, error, DialectType.MYSQL
            )

            assertTrue(attempt.success)
            assertFalse(attempt.recoveredSql.contains("/*+"))
            assertTrue(attempt.recoveredSql.contains("SELECT"))
            assertTrue(attempt.recoveredSql.contains("employees"))
        }

        @Test
        @DisplayName("힌트 없는 SQL - 처리 불가")
        fun testNoHintSql() {
            val sql = "SELECT * FROM users WHERE id = 1"
            val error = Exception("parse error")

            assertFalse(AdvancedRecoveryStrategies.HintRemovalStrategy.canHandle(sql, error))
        }
    }

    @Nested
    @DisplayName("괄호 균형 복구 전략 테스트")
    inner class ParenthesisBalanceStrategyTest {

        @Test
        @DisplayName("닫는 괄호 부족 수정")
        fun testMissingClosingParenthesis() {
            val sql = "SELECT * FROM users WHERE id IN (1, 2, 3"
            val error = Exception("missing )")

            assertTrue(AdvancedRecoveryStrategies.ParenthesisBalanceStrategy.canHandle(sql, error))

            val attempt = AdvancedRecoveryStrategies.ParenthesisBalanceStrategy.recover(
                sql, error, DialectType.MYSQL
            )

            assertTrue(attempt.success)
            val openCount = attempt.recoveredSql.count { it == '(' }
            val closeCount = attempt.recoveredSql.count { it == ')' }
            assertEquals(openCount, closeCount)
        }

        @Test
        @DisplayName("여는 괄호 부족 수정")
        fun testMissingOpeningParenthesis() {
            val sql = "SELECT * FROM users WHERE id IN 1, 2, 3)"
            val error = Exception("unexpected )")

            assertTrue(AdvancedRecoveryStrategies.ParenthesisBalanceStrategy.canHandle(sql, error))

            val attempt = AdvancedRecoveryStrategies.ParenthesisBalanceStrategy.recover(
                sql, error, DialectType.MYSQL
            )

            assertTrue(attempt.success)
            val openCount = attempt.recoveredSql.count { it == '(' }
            val closeCount = attempt.recoveredSql.count { it == ')' }
            assertEquals(openCount, closeCount)
        }

        @Test
        @DisplayName("균형잡힌 괄호 - 처리 불필요")
        fun testBalancedParentheses() {
            val sql = "SELECT * FROM users WHERE id IN (1, 2, 3)"
            val error = Exception("other error")

            assertFalse(AdvancedRecoveryStrategies.ParenthesisBalanceStrategy.canHandle(sql, error))
        }
    }

    @Nested
    @DisplayName("문자열 이스케이프 전략 테스트")
    inner class StringEscapeStrategyTest {

        @Test
        @DisplayName("미종료 문자열 수정")
        fun testUnterminatedString() {
            val sql = "SELECT * FROM users WHERE name = 'John"
            val error = Exception("unterminated string literal")

            assertTrue(AdvancedRecoveryStrategies.StringEscapeStrategy.canHandle(sql, error))

            val attempt = AdvancedRecoveryStrategies.StringEscapeStrategy.recover(
                sql, error, DialectType.MYSQL
            )

            assertTrue(attempt.success)
            val quoteCount = attempt.recoveredSql.count { it == '\'' }
            assertEquals(0, quoteCount % 2, "따옴표가 짝수개여야 함")
        }
    }

    @Nested
    @DisplayName("CONNECT BY 대체 전략 테스트")
    inner class ConnectByReplacementStrategyTest {

        @Test
        @DisplayName("CONNECT BY 감지")
        fun testConnectByDetection() {
            val sql = """
                SELECT employee_id, manager_id, level
                FROM employees
                CONNECT BY PRIOR employee_id = manager_id
                START WITH manager_id IS NULL
            """.trimIndent()
            val error = Exception("unsupported syntax")

            assertTrue(AdvancedRecoveryStrategies.ConnectByReplacementStrategy.canHandle(sql, error))

            val attempt = AdvancedRecoveryStrategies.ConnectByReplacementStrategy.recover(
                sql, error, DialectType.POSTGRESQL
            )

            assertTrue(attempt.success)
            assertTrue(attempt.recoveredSql.contains("WITH RECURSIVE"))
            assertTrue(attempt.warning != null)
            assertEquals(WarningSeverity.WARNING, attempt.warning!!.severity)
        }
    }

    @Nested
    @DisplayName("물리적 속성 제거 전략 테스트")
    inner class PhysicalAttributeRemovalStrategyTest {

        @Test
        @DisplayName("TABLESPACE 제거")
        fun testTablespaceRemoval() {
            val sql = "CREATE TABLE test (id NUMBER) TABLESPACE users"
            val error = Exception("syntax error")

            assertTrue(AdvancedRecoveryStrategies.PhysicalAttributeRemovalStrategy.canHandle(sql, error))

            val attempt = AdvancedRecoveryStrategies.PhysicalAttributeRemovalStrategy.recover(
                sql, error, DialectType.MYSQL
            )

            assertTrue(attempt.success)
            assertFalse(attempt.recoveredSql.contains("TABLESPACE"))
        }

        @Test
        @DisplayName("STORAGE 절 제거")
        fun testStorageClauseRemoval() {
            val sql = "CREATE TABLE test (id NUMBER) STORAGE (INITIAL 64K NEXT 64K)"
            val error = Exception("syntax error")

            val attempt = AdvancedRecoveryStrategies.PhysicalAttributeRemovalStrategy.recover(
                sql, error, DialectType.MYSQL
            )

            assertTrue(attempt.success)
            assertFalse(attempt.recoveredSql.contains("STORAGE"))
        }

        @Test
        @DisplayName("PCTFREE, LOGGING 등 제거")
        fun testPctfreeLoggingRemoval() {
            val sql = "CREATE TABLE test (id NUMBER) PCTFREE 10 LOGGING PARALLEL(4)"
            val error = Exception("syntax error")

            val attempt = AdvancedRecoveryStrategies.PhysicalAttributeRemovalStrategy.recover(
                sql, error, DialectType.MYSQL
            )

            assertTrue(attempt.success)
            assertFalse(attempt.recoveredSql.contains("PCTFREE"))
            assertFalse(attempt.recoveredSql.contains("LOGGING"))
            assertFalse(attempt.recoveredSql.contains("PARALLEL"))
        }
    }

    @Nested
    @DisplayName("예약어 이스케이프 전략 테스트")
    inner class ReservedWordEscapeStrategyTest {

        @Test
        @DisplayName("예약어 컬럼 이스케이프 - MySQL")
        fun testReservedWordEscapeMySql() {
            val sql = "SELECT user, order, date FROM orders"
            val error = Exception("reserved keyword")

            assertTrue(AdvancedRecoveryStrategies.ReservedWordEscapeStrategy.canHandle(sql, error))

            val attempt = AdvancedRecoveryStrategies.ReservedWordEscapeStrategy.recover(
                sql, error, DialectType.MYSQL
            )

            assertTrue(attempt.success)
            // MySQL은 백틱 사용
            assertTrue(attempt.recoveredSql.contains("`"))
        }

        @Test
        @DisplayName("예약어 컬럼 이스케이프 - PostgreSQL")
        fun testReservedWordEscapePostgreSql() {
            val sql = "SELECT user, date FROM users"
            val error = Exception("reserved keyword")

            val attempt = AdvancedRecoveryStrategies.ReservedWordEscapeStrategy.recover(
                sql, error, DialectType.POSTGRESQL
            )

            assertTrue(attempt.success)
            // PostgreSQL은 쌍따옴표 사용
            assertTrue(attempt.recoveredSql.contains("\""))
        }
    }

    @Nested
    @DisplayName("복합 복구 테스트")
    inner class CompositeRecoveryTest {

        @Test
        @DisplayName("여러 전략 순차 적용")
        fun testSequentialStrategies() {
            val sql = """
                SELECT /*+ INDEX(e) */ employee_id, user, order
                FROM employees e
                TABLESPACE users
                PCTFREE 10
                -- comment
            """.trimIndent()

            val (recovered, warnings) = AdvancedRecoveryStrategies.applyStrategiesSequentially(
                sql, DialectType.MYSQL
            )

            // 힌트 제거됨
            assertFalse(recovered.contains("/*+"))
            // 물리적 속성 제거됨
            assertFalse(recovered.contains("TABLESPACE"))
            assertFalse(recovered.contains("PCTFREE"))
            // 주석 제거됨
            assertFalse(recovered.contains("--"))
            // 예약어 이스케이프됨
            assertTrue(recovered.contains("`"))
            // 경고 발생
            assertTrue(warnings.isNotEmpty())

            println("Recovered SQL: $recovered")
            println("Warnings: ${warnings.map { it.message }}")
        }

        @Test
        @DisplayName("복구 시도 결과")
        fun testAttemptRecovery() {
            val sql = "SELECT * FROM users TABLESPACE ts1 WHERE id = 1"
            val error = Exception("syntax error near TABLESPACE")

            val result = AdvancedRecoveryStrategies.attemptRecovery(
                sql, error, DialectType.MYSQL
            )

            assertTrue(result.success)
            assertFalse(result.finalSql.contains("TABLESPACE"))
            assertTrue(result.strategiesAttempted.isNotEmpty())
            assertTrue(result.strategyUsed != null)
            assertTrue(result.confidence > 0)

            println("Original: ${result.originalSql}")
            println("Final: ${result.finalSql}")
            println("Strategy used: ${result.strategyUsed}")
            println("Confidence: ${result.confidence}")
        }
    }

    @Nested
    @DisplayName("SQL 정규화 테스트")
    inner class NormalizeSqlTest {

        @Test
        @DisplayName("여러 공백 정규화")
        fun testMultipleSpaces() {
            val sql = "SELECT   *   FROM    users"
            val normalized = AdvancedRecoveryStrategies.normalizeSql(sql, DialectType.ORACLE)

            assertEquals("SELECT * FROM users", normalized)
        }

        @Test
        @DisplayName("앞뒤 공백 제거")
        fun testTrimSpaces() {
            val sql = "   SELECT * FROM users   "
            val normalized = AdvancedRecoveryStrategies.normalizeSql(sql, DialectType.ORACLE)

            assertEquals("SELECT * FROM users", normalized)
        }
    }

    @Nested
    @DisplayName("신뢰도 테스트")
    inner class ConfidenceTest {

        @Test
        @DisplayName("물리적 속성 제거 - 높은 신뢰도")
        fun testHighConfidenceRecovery() {
            val sql = "CREATE TABLE test (id NUMBER) TABLESPACE users"
            val error = Exception("syntax error")

            val attempt = AdvancedRecoveryStrategies.PhysicalAttributeRemovalStrategy.recover(
                sql, error, DialectType.MYSQL
            )

            assertTrue(attempt.confidence >= 0.9)
        }

        @Test
        @DisplayName("CONNECT BY 대체 - 낮은 신뢰도")
        fun testLowConfidenceRecovery() {
            val sql = "SELECT * FROM emp CONNECT BY PRIOR id = mgr_id"
            val error = Exception("unsupported")

            val attempt = AdvancedRecoveryStrategies.ConnectByReplacementStrategy.recover(
                sql, error, DialectType.POSTGRESQL
            )

            assertTrue(attempt.confidence <= 0.6)
        }
    }
}
