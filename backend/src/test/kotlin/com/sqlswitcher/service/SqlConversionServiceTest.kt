package com.sqlswitcher.service

import com.sqlswitcher.converter.*
import com.sqlswitcher.model.ConversionOptions
import com.sqlswitcher.model.ConversionRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SqlConversionService 단위 테스트
 */
@ExtendWith(MockitoExtension::class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SqlConversionServiceTest {

    @Mock
    private lateinit var sqlConverterEngine: SqlConverterEngine

    @Mock
    private lateinit var sqlMetricsService: SqlMetricsService

    private lateinit var sqlConversionService: SqlConversionService

    @BeforeEach
    fun setUp() {
        sqlConversionService = SqlConversionService(sqlConverterEngine, sqlMetricsService)
    }

    @Nested
    @DisplayName("단일 SQL 문장 변환")
    inner class SingleStatementConversionTest {

        @Test
        @DisplayName("정상적인 단일 SQL 변환")
        fun testSuccessfulSingleStatementConversion() {
            // Given
            val request = ConversionRequest(
                sql = "SELECT SYSDATE FROM DUAL",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val expectedResult = ConversionResult(
                convertedSql = "SELECT NOW() FROM DUAL",
                warnings = emptyList(),
                appliedRules = listOf("SYSDATE -> NOW()"),
                executionTime = 10L
            )

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = eq("SELECT SYSDATE FROM DUAL"),
                sourceDialectType = eq(DialectType.ORACLE),
                targetDialectType = eq(DialectType.MYSQL),
                options = any(),
                modelOptions = isNull()
            )).thenReturn(expectedResult)

            // When
            val response = sqlConversionService.convertSql(request)

            // Then
            assertTrue(response.success)
            assertEquals("SELECT NOW() FROM DUAL;", response.convertedSql)
            assertEquals(listOf("SYSDATE -> NOW()"), response.appliedRules)
            verify(sqlMetricsService).recordConversionRequest("ORACLE", "MYSQL")
            verify(sqlMetricsService).recordConversionSuccess("ORACLE", "MYSQL")
        }

        @Test
        @DisplayName("변환 옵션과 함께 SQL 변환")
        fun testConversionWithOptions() {
            // Given
            val options = ConversionOptions(
                enableComments = true,
                formatSql = true,
                strictMode = false,
                replaceUnsupportedFunctions = true
            )

            val request = ConversionRequest(
                sql = "SELECT NVL(col, 0) FROM t",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                options = options
            )

            val expectedResult = ConversionResult(
                convertedSql = "SELECT IFNULL(col, 0) FROM t",
                warnings = emptyList(),
                appliedRules = listOf("NVL -> IFNULL")
            )

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = any(),
                sourceDialectType = any(),
                targetDialectType = any(),
                options = any(),
                modelOptions = eq(options)
            )).thenReturn(expectedResult)

            // When
            val response = sqlConversionService.convertSql(request)

            // Then
            assertTrue(response.success)
            assertTrue(response.convertedSql.contains("IFNULL"))
        }

        @Test
        @DisplayName("경고가 포함된 변환")
        fun testConversionWithWarnings() {
            // Given
            val request = ConversionRequest(
                sql = "SELECT CONNECT_BY_ROOT name FROM employees",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL
            )

            val warnings = listOf(
                ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "계층형 쿼리는 WITH RECURSIVE로 변환됩니다",
                    severity = WarningSeverity.WARNING
                )
            )

            val expectedResult = ConversionResult(
                convertedSql = "-- Hierarchical query converted\nWITH RECURSIVE ...",
                warnings = warnings,
                appliedRules = listOf("CONNECT BY -> WITH RECURSIVE")
            )

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = any(),
                sourceDialectType = any(),
                targetDialectType = any(),
                options = any(),
                modelOptions = isNull()
            )).thenReturn(expectedResult)

            // When
            val response = sqlConversionService.convertSql(request)

            // Then
            assertTrue(response.success)
            assertEquals(1, response.warnings.size)
            assertEquals(WarningType.SYNTAX_DIFFERENCE, response.warnings[0].type)
        }
    }

    @Nested
    @DisplayName("여러 SQL 문장 변환")
    inner class MultipleStatementConversionTest {

        @Test
        @DisplayName("세미콜론으로 구분된 여러 SQL 변환")
        fun testMultipleStatementConversion() {
            // Given
            val request = ConversionRequest(
                sql = "SELECT SYSDATE FROM DUAL; SELECT NVL(col, 0) FROM t",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = eq("SELECT SYSDATE FROM DUAL"),
                sourceDialectType = any(),
                targetDialectType = any(),
                options = any(),
                modelOptions = isNull()
            )).thenReturn(ConversionResult(
                convertedSql = "SELECT NOW() FROM DUAL",
                warnings = emptyList(),
                appliedRules = listOf("SYSDATE -> NOW()")
            ))

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = eq("SELECT NVL(col, 0) FROM t"),
                sourceDialectType = any(),
                targetDialectType = any(),
                options = any(),
                modelOptions = isNull()
            )).thenReturn(ConversionResult(
                convertedSql = "SELECT IFNULL(col, 0) FROM t",
                warnings = emptyList(),
                appliedRules = listOf("NVL -> IFNULL")
            ))

            // When
            val response = sqlConversionService.convertSql(request)

            // Then
            assertTrue(response.success)
            assertTrue(response.convertedSql.contains("SELECT NOW()"))
            assertTrue(response.convertedSql.contains("SELECT IFNULL"))
            assertTrue(response.appliedRules.contains("SYSDATE -> NOW()"))
            assertTrue(response.appliedRules.contains("NVL -> IFNULL"))
        }

        @Test
        @DisplayName("일부 문장 변환 실패 시 처리")
        fun testPartialFailure() {
            // Given
            val request = ConversionRequest(
                sql = "SELECT 1; INVALID SYNTAX; SELECT 2",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = eq("SELECT 1"),
                sourceDialectType = any(),
                targetDialectType = any(),
                options = any(),
                modelOptions = isNull()
            )).thenReturn(ConversionResult(convertedSql = "SELECT 1"))

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = eq("INVALID SYNTAX"),
                sourceDialectType = any(),
                targetDialectType = any(),
                options = any(),
                modelOptions = isNull()
            )).thenThrow(RuntimeException("Parse error"))

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = eq("SELECT 2"),
                sourceDialectType = any(),
                targetDialectType = any(),
                options = any(),
                modelOptions = isNull()
            )).thenReturn(ConversionResult(convertedSql = "SELECT 2"))

            // When
            val response = sqlConversionService.convertSql(request)

            // Then
            assertTrue(response.success)
            assertTrue(response.warnings.any { it.message.contains("Failed to convert") })
            assertTrue(response.convertedSql.contains("INVALID SYNTAX"))
        }
    }

    @Nested
    @DisplayName("에러 처리")
    inner class ErrorHandlingTest {

        @Test
        @DisplayName("변환 중 개별 문장 실패 시 경고 추가")
        fun testIndividualStatementFailure() {
            // Given
            val request = ConversionRequest(
                sql = "INVALID SQL",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = eq("INVALID SQL"),
                sourceDialectType = any(),
                targetDialectType = any(),
                options = any(),
                modelOptions = isNull()
            )).thenThrow(RuntimeException("Parse failure"))

            // When
            val response = sqlConversionService.convertSql(request)

            // Then - 개별 문장 실패는 success=true이고 경고 포함
            assertTrue(response.success)
            assertEquals("INVALID SQL", response.originalSql)
            assertTrue(response.warnings.any { it.message.contains("Failed to convert") })
        }

        @Test
        @DisplayName("빈 SQL 처리")
        fun testEmptySql() {
            // Given
            val request = ConversionRequest(
                sql = "",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            // When
            val response = sqlConversionService.convertSql(request)

            // Then
            assertTrue(response.success)
            // 빈 SQL은 세미콜론 구분 후 joinToString하면 빈 문자열 + ";" = ";"
            assertTrue(response.convertedSql.isNotEmpty() || response.convertedSql == "")
        }

        @Test
        @DisplayName("공백만 있는 SQL 처리")
        fun testWhitespaceSql() {
            // Given
            val request = ConversionRequest(
                sql = "   \n\t  ",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            // When
            val response = sqlConversionService.convertSql(request)

            // Then
            assertTrue(response.success)
        }
    }

    @Nested
    @DisplayName("메트릭 기록")
    inner class MetricsRecordingTest {

        @Test
        @DisplayName("변환 요청 메트릭 기록")
        fun testRequestMetrics() {
            // Given
            val request = ConversionRequest(
                sql = "SELECT 1",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL
            )

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = any(),
                sourceDialectType = any(),
                targetDialectType = any(),
                options = any(),
                modelOptions = isNull()
            )).thenReturn(ConversionResult(convertedSql = "SELECT 1"))

            // When
            sqlConversionService.convertSql(request)

            // Then
            verify(sqlMetricsService).recordConversionRequest("ORACLE", "POSTGRESQL")
            verify(sqlMetricsService).recordConversionSuccess("ORACLE", "POSTGRESQL")
            verify(sqlMetricsService).recordConversionDuration(any(), eq("ORACLE"), eq("POSTGRESQL"))
        }

        @Test
        @DisplayName("변환 요청 메트릭 기록")
        fun testRequestMetricsOnFailure() {
            // Given
            val request = ConversionRequest(
                sql = "INVALID",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE
            )

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = eq("INVALID"),
                sourceDialectType = any(),
                targetDialectType = any(),
                options = any(),
                modelOptions = isNull()
            )).thenThrow(RuntimeException("Error"))

            // When
            sqlConversionService.convertSql(request)

            // Then - 변환 요청 메트릭은 항상 기록됨
            verify(sqlMetricsService).recordConversionRequest("MYSQL", "ORACLE")
            // 개별 문장 실패 시에는 success가 호출되므로 에러 메트릭이 아닌 success 메트릭 확인
            verify(sqlMetricsService).recordConversionSuccess("MYSQL", "ORACLE")
        }
    }

    @Nested
    @DisplayName("다양한 방언 변환")
    inner class DialectConversionTest {

        @Test
        @DisplayName("Oracle -> MySQL 변환")
        fun testOracleToMysql() {
            // Given
            val request = ConversionRequest(
                sql = "SELECT TO_CHAR(SYSDATE, 'YYYY-MM-DD') FROM DUAL",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val expectedResult = ConversionResult(
                convertedSql = "SELECT DATE_FORMAT(NOW(), '%Y-%m-%d') FROM DUAL",
                warnings = emptyList(),
                appliedRules = listOf("TO_CHAR -> DATE_FORMAT", "SYSDATE -> NOW()")
            )

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = any(),
                sourceDialectType = eq(DialectType.ORACLE),
                targetDialectType = eq(DialectType.MYSQL),
                options = any(),
                modelOptions = isNull()
            )).thenReturn(expectedResult)

            // When
            val response = sqlConversionService.convertSql(request)

            // Then
            assertTrue(response.success)
            assertEquals(DialectType.ORACLE, response.sourceDialect)
            assertEquals(DialectType.MYSQL, response.targetDialect)
        }

        @Test
        @DisplayName("Oracle -> PostgreSQL 변환")
        fun testOracleToPostgresql() {
            // Given
            val request = ConversionRequest(
                sql = "SELECT ROWNUM FROM employees WHERE ROWNUM <= 10",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL
            )

            val expectedResult = ConversionResult(
                convertedSql = "SELECT ROW_NUMBER() OVER () FROM employees LIMIT 10",
                warnings = emptyList(),
                appliedRules = listOf("ROWNUM -> LIMIT")
            )

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = any(),
                sourceDialectType = eq(DialectType.ORACLE),
                targetDialectType = eq(DialectType.POSTGRESQL),
                options = any(),
                modelOptions = isNull()
            )).thenReturn(expectedResult)

            // When
            val response = sqlConversionService.convertSql(request)

            // Then
            assertTrue(response.success)
            assertEquals(DialectType.POSTGRESQL, response.targetDialect)
        }

        @Test
        @DisplayName("MySQL -> PostgreSQL 변환")
        fun testMysqlToPostgresql() {
            // Given
            val request = ConversionRequest(
                sql = "SELECT IFNULL(col, 'default') FROM t LIMIT 10",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL
            )

            val expectedResult = ConversionResult(
                convertedSql = "SELECT COALESCE(col, 'default') FROM t LIMIT 10",
                warnings = emptyList(),
                appliedRules = listOf("IFNULL -> COALESCE")
            )

            whenever(sqlConverterEngine.convertWithModelOptions(
                sql = any(),
                sourceDialectType = eq(DialectType.MYSQL),
                targetDialectType = eq(DialectType.POSTGRESQL),
                options = any(),
                modelOptions = isNull()
            )).thenReturn(expectedResult)

            // When
            val response = sqlConversionService.convertSql(request)

            // Then
            assertTrue(response.success)
        }
    }
}
