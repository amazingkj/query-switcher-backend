package com.sqlswitcher.converter.integration

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ValidationInfo
import com.sqlswitcher.converter.validation.SqlConversionValidationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

/**
 * 검증 서비스 통합 테스트
 *
 * SqlConversionValidationService의 JSQLParser 기반 검증 기능과
 * 변환 파이프라인 통합을 테스트합니다.
 */
class ValidationIntegrationTest {

    private lateinit var validationService: SqlConversionValidationService

    @BeforeEach
    fun setup() {
        validationService = SqlConversionValidationService()
    }

    @Nested
    @DisplayName("변환 쌍 검증 통합 테스트")
    inner class ConversionPairValidationTest {

        @Test
        @DisplayName("NVL → IFNULL 변환 검증")
        fun testNvlToIfnullValidation() {
            val original = "SELECT NVL(name, 'Unknown') FROM employees"
            val converted = "SELECT IFNULL(name, 'Unknown') FROM employees"

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertTrue(result.originalValid, "원본 SQL이 유효해야 함")
            assertTrue(result.convertedValid, "변환된 SQL이 유효해야 함")
            assertTrue(result.qualityScore >= 0.8, "품질 점수가 0.8 이상이어야 함")
            assertNotNull(result.recommendation)
        }

        @Test
        @DisplayName("DECODE → CASE 변환 검증")
        fun testDecodeToCaseValidation() {
            val original = "SELECT DECODE(status, 'A', 'Active', 'I', 'Inactive', 'Unknown') FROM users"
            val converted = "SELECT CASE status WHEN 'A' THEN 'Active' WHEN 'I' THEN 'Inactive' ELSE 'Unknown' END FROM users"

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertTrue(result.convertedValid)
            assertTrue(result.qualityScore >= 0.7)
        }

        @Test
        @DisplayName("SYSDATE → NOW() 변환 검증")
        fun testSysdateToNowValidation() {
            val original = "SELECT SYSDATE FROM dual"
            val converted = "SELECT NOW()"

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertTrue(result.convertedValid)
        }

        @Test
        @DisplayName("복잡한 JOIN 변환 검증")
        fun testComplexJoinValidation() {
            val original = """
                SELECT e.name, d.dept_name, l.city
                FROM employees e
                LEFT JOIN departments d ON e.dept_id = d.id
                LEFT JOIN locations l ON d.location_id = l.id
                WHERE e.salary > 50000
            """.trimIndent()

            val converted = original  // 동일한 경우 (MySQL과 동일)

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertTrue(result.originalValid)
            assertTrue(result.convertedValid)
            assertEquals("SELECT", result.detailedValidation.parseResult.statementType)
        }

        @Test
        @DisplayName("서브쿼리 포함 변환 검증")
        fun testSubqueryValidation() {
            val original = """
                SELECT * FROM employees
                WHERE salary > (SELECT AVG(salary) FROM employees)
            """.trimIndent()

            val converted = original

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertTrue(result.convertedValid)
        }

        @Test
        @DisplayName("CTE 변환 검증")
        fun testCteValidation() {
            val original = """
                WITH dept_stats AS (
                    SELECT dept_id, COUNT(*) as emp_count
                    FROM employees
                    GROUP BY dept_id
                )
                SELECT * FROM dept_stats WHERE emp_count > 5
            """.trimIndent()

            val converted = original

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertTrue(result.convertedValid)
            assertEquals("SELECT", result.detailedValidation.parseResult.statementType)
        }
    }

    @Nested
    @DisplayName("방언별 호환성 검증 테스트")
    inner class DialectCompatibilityTest {

        @Test
        @DisplayName("MySQL 비호환 - RETURNING 절")
        fun testMySqlReturningIncompatible() {
            val sql = "INSERT INTO users (name) VALUES ('test') RETURNING id"

            val result = validationService.validateForDialect(sql, DialectType.MYSQL)

            assertTrue(result.compatibilityIssues.isNotEmpty())
            assertTrue(result.compatibilityIssues.any { it.contains("RETURNING") })
            assertFalse(result.isProductionReady)
        }

        @Test
        @DisplayName("PostgreSQL 비호환 - 백틱 식별자")
        fun testPostgreSqlBacktickIncompatible() {
            val sql = "SELECT `name`, `status` FROM `users`"

            val result = validationService.validateForDialect(sql, DialectType.POSTGRESQL)

            assertTrue(result.compatibilityIssues.any { it.contains("백틱") })
        }

        @Test
        @DisplayName("Oracle 비호환 - LIMIT 절")
        fun testOracleLimitIncompatible() {
            val sql = "SELECT * FROM users ORDER BY id LIMIT 10 OFFSET 20"

            val result = validationService.validateForDialect(sql, DialectType.ORACLE)

            assertTrue(result.compatibilityIssues.any { it.contains("LIMIT") })
        }

        @Test
        @DisplayName("호환되는 표준 SQL")
        fun testStandardSqlCompatible() {
            val sql = "SELECT id, name FROM users WHERE active = 1 ORDER BY name"

            val mysqlResult = validationService.validateForDialect(sql, DialectType.MYSQL)
            val pgResult = validationService.validateForDialect(sql, DialectType.POSTGRESQL)
            val oracleResult = validationService.validateForDialect(sql, DialectType.ORACLE)

            assertTrue(mysqlResult.isProductionReady)
            assertTrue(pgResult.isProductionReady)
            assertTrue(oracleResult.isProductionReady)
        }
    }

    @Nested
    @DisplayName("품질 점수 계산 테스트")
    inner class QualityScoreTest {

        @Test
        @DisplayName("완벽한 변환 - 높은 품질 점수")
        fun testPerfectConversionHighScore() {
            val original = "SELECT id, name FROM users"
            val converted = "SELECT id, name FROM users"

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertTrue(result.qualityScore >= 0.9, "완벽한 변환은 0.9 이상이어야 함")
        }

        @Test
        @DisplayName("WHERE 절 누락 - 낮은 품질 점수")
        fun testWhereClauseLossLowScore() {
            val original = "SELECT * FROM users WHERE active = 1"
            val converted = "SELECT * FROM users"

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertTrue(result.qualityScore < 0.8, "WHERE 절 누락 시 점수 감점")
            assertTrue(result.conversionWarnings.any { it.message.contains("WHERE") })
        }

        @Test
        @DisplayName("GROUP BY 절 누락 - 낮은 품질 점수")
        fun testGroupByLossLowScore() {
            val original = "SELECT dept, COUNT(*) FROM users GROUP BY dept"
            val converted = "SELECT dept, COUNT(*) FROM users"

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertTrue(result.qualityScore < 0.9)
        }

        @Test
        @DisplayName("파싱 실패 - 매우 낮은 품질 점수")
        fun testParseFailureLowScore() {
            val original = "SELECT * FROM users"
            val converted = "SELEC * FORM users"  // 오타

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertTrue(result.qualityScore < 0.5, "파싱 실패 시 매우 낮은 점수")
            assertFalse(result.convertedValid)
        }
    }

    @Nested
    @DisplayName("권장 사항 생성 테스트")
    inner class RecommendationTest {

        @Test
        @DisplayName("높은 품질 - 프로덕션 적합 권장")
        fun testHighQualityRecommendation() {
            val original = "SELECT id FROM users"
            val converted = "SELECT id FROM users"

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertNotNull(result.recommendation)
            if (result.qualityScore >= 0.9) {
                assertTrue(
                    result.recommendation.contains("우수") ||
                    result.recommendation.contains("적합") ||
                    result.recommendation.contains("프로덕션")
                )
            }
        }

        @Test
        @DisplayName("낮은 품질 - 수동 검토 권장")
        fun testLowQualityRecommendation() {
            val original = "SELECT * FROM users WHERE active = 1"
            val converted = "SELECT *"  // 불완전

            val result = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            assertNotNull(result.recommendation)
            // 낮은 품질 시 검토 권장
            if (result.qualityScore < 0.7) {
                assertTrue(
                    result.recommendation.contains("검토") ||
                    result.recommendation.contains("주의") ||
                    result.recommendation.contains("수동")
                )
            }
        }
    }

    @Nested
    @DisplayName("ValidationInfo 생성 테스트")
    inner class ValidationInfoTest {

        @Test
        @DisplayName("ValidationInfo 필드 확인")
        fun testValidationInfoFields() {
            val original = "SELECT id, name FROM employees WHERE salary > 50000"
            val converted = "SELECT id, name FROM employees WHERE salary > 50000"

            val pairResult = validationService.validateConversionPair(
                original, converted,
                DialectType.ORACLE, DialectType.MYSQL
            )

            // ValidationInfo 수동 생성 (SqlConverterEngine에서 하는 것처럼)
            val validationInfo = ValidationInfo(
                isValid = pairResult.convertedValid,
                statementType = pairResult.detailedValidation.parseResult.statementType,
                isProductionReady = pairResult.detailedValidation.isProductionReady,
                qualityScore = pairResult.qualityScore,
                compatibilityIssues = pairResult.detailedValidation.compatibilityIssues,
                recommendation = pairResult.recommendation
            )

            assertTrue(validationInfo.isValid)
            assertEquals("SELECT", validationInfo.statementType)
            assertTrue(validationInfo.qualityScore > 0)
            assertNotNull(validationInfo.recommendation)
        }
    }

    @Nested
    @DisplayName("다양한 SQL 문 타입 검증")
    inner class StatementTypeValidationTest {

        @Test
        @DisplayName("INSERT 문 검증")
        fun testInsertValidation() {
            val sql = "INSERT INTO users (id, name) VALUES (1, 'test')"
            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid)
            assertEquals("INSERT", result.statementType)
        }

        @Test
        @DisplayName("UPDATE 문 검증")
        fun testUpdateValidation() {
            val sql = "UPDATE users SET name = 'new' WHERE id = 1"
            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid)
            assertEquals("UPDATE", result.statementType)
        }

        @Test
        @DisplayName("DELETE 문 검증")
        fun testDeleteValidation() {
            val sql = "DELETE FROM users WHERE id = 1"
            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid)
            assertEquals("DELETE", result.statementType)
        }

        @Test
        @DisplayName("CREATE TABLE 문 검증")
        fun testCreateTableValidation() {
            val sql = "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100))"
            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid)
            assertEquals("CREATE TABLE", result.statementType)
        }

        @Test
        @DisplayName("PL/SQL 블록 검증")
        fun testPlSqlBlockValidation() {
            val sql = """
                CREATE OR REPLACE PROCEDURE test_proc IS
                BEGIN
                    NULL;
                END;
            """.trimIndent()

            val result = validationService.validateParsing(sql)

            assertTrue(result.isValid)
            assertEquals("PL/SQL Block", result.statementType)
            assertEquals(0.8, result.confidence)
        }
    }
}
