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

    @Nested
    @DisplayName("JSQLParser 파싱 검증 테스트")
    inner class ParseValidationTest {

        @Test
        @DisplayName("유효한 SELECT 문 파싱")
        fun testValidSelectParsing() {
            val sql = "SELECT id, name FROM users WHERE active = 1"
            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid, "유효한 SQL은 파싱 성공해야 함")
            assertEquals("SELECT", result.statementType)
            assertEquals(1.0, result.confidence)
            assertTrue(result.errorMessage == null)
        }

        @Test
        @DisplayName("유효한 INSERT 문 파싱")
        fun testValidInsertParsing() {
            val sql = "INSERT INTO users (id, name) VALUES (1, 'test')"
            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid)
            assertEquals("INSERT", result.statementType)
        }

        @Test
        @DisplayName("유효한 UPDATE 문 파싱")
        fun testValidUpdateParsing() {
            val sql = "UPDATE users SET name = 'new' WHERE id = 1"
            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid)
            assertEquals("UPDATE", result.statementType)
        }

        @Test
        @DisplayName("유효한 DELETE 문 파싱")
        fun testValidDeleteParsing() {
            val sql = "DELETE FROM users WHERE id = 1"
            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid)
            assertEquals("DELETE", result.statementType)
        }

        @Test
        @DisplayName("유효한 CREATE TABLE 문 파싱")
        fun testValidCreateTableParsing() {
            val sql = "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100))"
            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid)
            assertEquals("CREATE TABLE", result.statementType)
        }

        @Test
        @DisplayName("유효하지 않은 SQL 파싱 실패")
        fun testInvalidSqlParsing() {
            val sql = "SELEC * FORM users"  // 오타
            val result = validationService.validateParsing(sql)

            assertTrue(!result.isValid, "유효하지 않은 SQL은 파싱 실패해야 함")
            assertNotNull(result.errorMessage)
            assertEquals(0.0, result.confidence)
        }

        @Test
        @DisplayName("빈 SQL 파싱")
        fun testEmptySqlParsing() {
            val result = validationService.validateParsing("")

            assertTrue(!result.isValid)
            assertEquals("SQL이 비어있습니다.", result.errorMessage)
        }

        @Test
        @DisplayName("PL/SQL 블록은 스킵 처리")
        fun testPlSqlBlockSkipped() {
            val sql = """
                CREATE OR REPLACE PROCEDURE test_proc IS
                BEGIN
                    NULL;
                END;
            """.trimIndent()
            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid)
            assertEquals("PL/SQL Block", result.statementType)
            assertEquals(0.8, result.confidence)  // PL/SQL은 낮은 신뢰도
        }

        @Test
        @DisplayName("복잡한 서브쿼리 파싱")
        fun testComplexSubqueryParsing() {
            val sql = """
                SELECT e.name, d.dept_name
                FROM employees e
                JOIN (SELECT * FROM departments WHERE active = 1) d
                ON e.dept_id = d.id
                WHERE e.salary > (SELECT AVG(salary) FROM employees)
            """.trimIndent()
            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid)
            assertEquals("SELECT", result.statementType)
        }

        @Test
        @DisplayName("CTE 쿼리 파싱")
        fun testCteParsing() {
            val sql = """
                WITH cte AS (
                    SELECT id, name FROM users WHERE active = 1
                )
                SELECT * FROM cte
            """.trimIndent()
            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid)
            assertEquals("SELECT", result.statementType)
        }
    }

    @Nested
    @DisplayName("방언별 검증 테스트")
    inner class DialectValidationTest {

        @Test
        @DisplayName("MySQL 타겟 - RETURNING 절 비호환")
        fun testMySqlReturningIncompatible() {
            val sql = "INSERT INTO users (name) VALUES ('test') RETURNING id"
            val result = validationService.validateForDialect(sql, DialectType.MYSQL)

            assertTrue(result.compatibilityIssues.isNotEmpty())
            assertTrue(result.compatibilityIssues.any { it.contains("RETURNING") })
            assertTrue(result.overallConfidence < 1.0)
        }

        @Test
        @DisplayName("PostgreSQL 타겟 - 백틱 비호환")
        fun testPostgreSqlBacktickIncompatible() {
            val sql = "SELECT `name` FROM `users`"
            val result = validationService.validateForDialect(sql, DialectType.POSTGRESQL)

            assertTrue(result.compatibilityIssues.isNotEmpty())
            assertTrue(result.compatibilityIssues.any { it.contains("백틱") })
            assertTrue(result.syntaxWarnings.isNotEmpty())
        }

        @Test
        @DisplayName("Oracle 타겟 - LIMIT 비호환")
        fun testOracleLimitIncompatible() {
            val sql = "SELECT * FROM users LIMIT 10"
            val result = validationService.validateForDialect(sql, DialectType.ORACLE)

            assertTrue(result.compatibilityIssues.isNotEmpty())
            assertTrue(result.compatibilityIssues.any { it.contains("LIMIT") })
        }

        @Test
        @DisplayName("호환되는 SQL - 프로덕션 준비됨")
        fun testCompatibleSqlProductionReady() {
            val sql = "SELECT id, name FROM users WHERE active = 1"
            val result = validationService.validateForDialect(sql, DialectType.MYSQL)

            assertTrue(result.isProductionReady, "호환되는 SQL은 프로덕션 준비되어야 함")
            assertTrue(result.compatibilityIssues.isEmpty())
            assertEquals(1.0, result.overallConfidence)
        }
    }

    @Nested
    @DisplayName("변환 쌍 검증 테스트")
    inner class ConversionPairValidationTest {

        @Test
        @DisplayName("성공적인 변환 쌍 검증")
        fun testSuccessfulConversionPair() {
            val original = "SELECT NVL(name, 'Unknown') FROM employees"
            val converted = "SELECT IFNULL(name, 'Unknown') FROM employees"

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertTrue(result.originalValid, "원본 SQL이 유효해야 함")
            assertTrue(result.convertedValid, "변환된 SQL이 유효해야 함")
            assertTrue(result.qualityScore >= 0.5, "품질 점수가 0.5 이상이어야 함")
            assertNotNull(result.recommendation)
        }

        @Test
        @DisplayName("변환 실패 쌍 검증")
        fun testFailedConversionPair() {
            val original = "SELECT * FROM employees WHERE active = 1"
            val converted = "SELEC * FORM employees"  // 유효하지 않은 SQL

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertTrue(result.originalValid)
            assertTrue(!result.convertedValid)
            assertTrue(result.qualityScore < 0.5, "품질 점수가 낮아야 함")
        }

        @Test
        @DisplayName("품질 점수 계산 - 경고에 따른 감점")
        fun testQualityScoreWithWarnings() {
            val original = "SELECT * FROM users WHERE active = 1 GROUP BY dept"
            val converted = "SELECT * FROM users"  // WHERE, GROUP BY 누락

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            // WHERE, GROUP BY 손실로 인해 점수 감점
            assertTrue(result.qualityScore < 0.9)
            assertTrue(result.conversionWarnings.any { it.severity == WarningSeverity.ERROR })
        }

        @Test
        @DisplayName("권장 사항 생성")
        fun testRecommendationGeneration() {
            val original = "SELECT id FROM users"
            val converted = "SELECT id FROM users"

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertNotNull(result.recommendation)
            assertTrue(result.recommendation.isNotEmpty())

            if (result.qualityScore >= 0.9) {
                assertTrue(result.recommendation.contains("우수") || result.recommendation.contains("적합"))
            }
        }
    }

    @Nested
    @DisplayName("에러 위치 추출 테스트")
    inner class ErrorPositionTest {

        @Test
        @DisplayName("파싱 오류 시 에러 위치 추출 시도")
        fun testErrorPositionExtraction() {
            val sql = "SELECT * FORM users"  // FROM 오타
            val result = validationService.validateParsing(sql)

            assertTrue(!result.isValid)
            // 에러 위치가 추출될 수도 있고 안 될 수도 있음 (파서에 따라 다름)
            assertNotNull(result.errorMessage)
        }
    }
}
