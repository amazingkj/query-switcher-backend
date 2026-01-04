package com.sqlswitcher.converter.validation

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * SqlValidationService 단위 테스트
 */
class SqlValidationServiceTest {

    private lateinit var validationService: SqlValidationService

    @BeforeEach
    fun setup() {
        validationService = SqlValidationService()
    }

    @Nested
    @DisplayName("괄호 균형 검증")
    inner class BracketBalanceTest {

        @Test
        @DisplayName("균형 잡힌 괄호는 경고 없음")
        fun testBalancedBrackets() {
            val sql = "SELECT NVL(name, 'default') FROM users"
            val warnings = validationService.validateConversion(
                sql, sql, DialectType.ORACLE, DialectType.MYSQL
            )
            val bracketWarnings = warnings.filter { it.message.contains("괄호") }
            assertTrue(bracketWarnings.isEmpty(), "균형 잡힌 괄호에는 경고가 없어야 함")
        }

        @Test
        @DisplayName("불균형 괄호 검출")
        fun testUnbalancedBrackets() {
            val sql = "SELECT NVL(name, 'default' FROM users"
            val warnings = validationService.validateConversion(
                sql, sql, DialectType.ORACLE, DialectType.MYSQL
            )
            val bracketWarnings = warnings.filter { it.message.contains("괄호") }
            assertTrue(bracketWarnings.isNotEmpty(), "불균형 괄호에 대한 경고가 있어야 함")
            assertEquals(WarningSeverity.ERROR, bracketWarnings.first().severity)
        }

        @Test
        @DisplayName("문자열 내부 괄호는 무시")
        fun testBracketsInString() {
            val sql = "SELECT 'text (with) brackets' FROM users"
            val warnings = validationService.validateConversion(
                sql, sql, DialectType.ORACLE, DialectType.MYSQL
            )
            val bracketWarnings = warnings.filter { it.message.contains("괄호") }
            assertTrue(bracketWarnings.isEmpty(), "문자열 내부 괄호는 무시해야 함")
        }
    }

    @Nested
    @DisplayName("따옴표 균형 검증")
    inner class QuoteBalanceTest {

        @Test
        @DisplayName("닫히지 않은 문자열 검출")
        fun testUnclosedString() {
            val sql = "SELECT 'unclosed string FROM users"
            val warnings = validationService.validateConversion(
                sql, sql, DialectType.ORACLE, DialectType.MYSQL
            )
            val quoteWarnings = warnings.filter { it.message.contains("문자열") }
            assertTrue(quoteWarnings.isNotEmpty(), "닫히지 않은 문자열에 대한 경고가 있어야 함")
        }

        @Test
        @DisplayName("이스케이프된 따옴표 처리")
        fun testEscapedQuotes() {
            val sql = "SELECT 'it''s fine' FROM users"
            val warnings = validationService.validateConversion(
                sql, sql, DialectType.ORACLE, DialectType.MYSQL
            )
            val quoteWarnings = warnings.filter { it.message.contains("문자열") && it.severity == WarningSeverity.ERROR }
            assertTrue(quoteWarnings.isEmpty(), "이스케이프된 따옴표는 정상 처리되어야 함")
        }
    }

    @Nested
    @DisplayName("미완료 변환 검출")
    inner class IncompleteConversionTest {

        @Test
        @DisplayName("Oracle CONNECT BY 검출")
        fun testConnectByDetection() {
            val sql = "SELECT * FROM employees CONNECT BY PRIOR emp_id = manager_id"
            val warnings = validationService.validateConversion(
                sql, sql, DialectType.ORACLE, DialectType.MYSQL
            )
            val connectByWarnings = warnings.filter { it.message.contains("CONNECT BY") }
            assertTrue(connectByWarnings.isNotEmpty(), "CONNECT BY 미변환에 대한 경고가 있어야 함")
        }

        @Test
        @DisplayName("Oracle 시퀀스 검출")
        fun testSequenceDetection() {
            val sql = "INSERT INTO users VALUES (user_seq.NEXTVAL, 'name')"
            val warnings = validationService.validateConversion(
                sql, sql, DialectType.ORACLE, DialectType.MYSQL
            )
            val seqWarnings = warnings.filter { it.message.contains("NEXTVAL") }
            assertTrue(seqWarnings.isNotEmpty(), "시퀀스 미변환에 대한 경고가 있어야 함")
        }

        @Test
        @DisplayName("주석 내 미지원 표시 검출")
        fun testUnsupportedCommentDetection() {
            val sql = "SELECT TRANSLATE(name, 'abc', 'xyz') /* not supported */ FROM users"
            val warnings = validationService.validateConversion(
                sql, sql, DialectType.ORACLE, DialectType.MYSQL
            )
            val unsupportedWarnings = warnings.filter { it.message.contains("미지원") || it.message.contains("not supported") }
            assertTrue(unsupportedWarnings.isNotEmpty(), "주석 내 미지원 표시에 대한 경고가 있어야 함")
        }
    }

    @Nested
    @DisplayName("데이터 손실 검출")
    inner class DataLossDetectionTest {

        @Test
        @DisplayName("WHERE 절 손실 검출")
        fun testWhereClauseLoss() {
            val original = "SELECT * FROM users WHERE active = 1"
            val converted = "SELECT * FROM users"
            val warnings = validationService.validateConversion(
                original, converted, DialectType.ORACLE, DialectType.MYSQL
            )
            val lossWarnings = warnings.filter { it.message.contains("WHERE") }
            assertTrue(lossWarnings.isNotEmpty(), "WHERE 절 손실에 대한 경고가 있어야 함")
            assertEquals(WarningSeverity.ERROR, lossWarnings.first().severity)
        }

        @Test
        @DisplayName("GROUP BY 절 손실 검출")
        fun testGroupByLoss() {
            val original = "SELECT dept, COUNT(*) FROM users GROUP BY dept"
            val converted = "SELECT dept, COUNT(*) FROM users"
            val warnings = validationService.validateConversion(
                original, converted, DialectType.ORACLE, DialectType.MYSQL
            )
            val lossWarnings = warnings.filter { it.message.contains("GROUP BY") }
            assertTrue(lossWarnings.isNotEmpty(), "GROUP BY 절 손실에 대한 경고가 있어야 함")
        }
    }

    @Nested
    @DisplayName("성능 관련 경고")
    inner class PerformanceWarningTest {

        @Test
        @DisplayName("대형 IN 절 검출")
        fun testLargeInClause() {
            val values = (1..150).joinToString(", ")
            val sql = "SELECT * FROM users WHERE id IN ($values)"
            val warnings = validationService.validateConversion(
                sql, sql, DialectType.ORACLE, DialectType.MYSQL
            )
            val perfWarnings = warnings.filter { it.type == WarningType.PERFORMANCE_WARNING && it.message.contains("IN 절") }
            assertTrue(perfWarnings.isNotEmpty(), "대형 IN 절에 대한 경고가 있어야 함")
        }

        @Test
        @DisplayName("LIKE '%...' 패턴 검출")
        fun testLeadingWildcardLike() {
            val sql = "SELECT * FROM users WHERE name LIKE '%test%'"
            val warnings = validationService.validateConversion(
                sql, sql, DialectType.ORACLE, DialectType.MYSQL
            )
            val likeWarnings = warnings.filter { it.message.contains("LIKE") }
            assertTrue(likeWarnings.isNotEmpty(), "와일드카드로 시작하는 LIKE에 대한 경고가 있어야 함")
        }

        @Test
        @DisplayName("SELECT * 검출")
        fun testSelectStar() {
            val sql = "SELECT * FROM users"
            val warnings = validationService.validateConversion(
                sql, sql, DialectType.ORACLE, DialectType.MYSQL
            )
            val selectStarWarnings = warnings.filter { it.message.contains("SELECT *") }
            assertTrue(selectStarWarnings.isNotEmpty(), "SELECT *에 대한 경고가 있어야 함")
        }
    }

    @Nested
    @DisplayName("정상 변환 케이스")
    inner class ValidConversionTest {

        @Test
        @DisplayName("정상적인 변환은 심각한 경고 없음")
        fun testValidConversion() {
            val original = "SELECT NVL(name, 'Unknown') FROM employees WHERE dept_id = 10"
            val converted = "SELECT IFNULL(name, 'Unknown') FROM employees WHERE dept_id = 10"
            val warnings = validationService.validateConversion(
                original, converted, DialectType.ORACLE, DialectType.MYSQL
            )
            val errorWarnings = warnings.filter { it.severity == WarningSeverity.ERROR }
            assertTrue(errorWarnings.isEmpty(), "정상 변환에는 ERROR 레벨 경고가 없어야 함")
        }
    }
}
