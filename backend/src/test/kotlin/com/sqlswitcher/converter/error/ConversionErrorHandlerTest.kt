package com.sqlswitcher.converter.error

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningSeverity
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * ConversionErrorHandler 단위 테스트
 */
class ConversionErrorHandlerTest {

    @Nested
    @DisplayName("위치 계산 테스트")
    inner class PositionCalculationTest {

        @Test
        @DisplayName("단일 라인 위치 계산")
        fun testSingleLinePosition() {
            val sql = "SELECT * FROM table"
            val (line, col) = ConversionErrorHandler.positionToLineColumn(sql, 7)
            assertEquals(1, line)
            assertEquals(8, col)
        }

        @Test
        @DisplayName("여러 라인 위치 계산")
        fun testMultiLinePosition() {
            val sql = "SELECT *\nFROM table\nWHERE id = 1"
            val (line, col) = ConversionErrorHandler.positionToLineColumn(sql, 15)
            assertEquals(2, line)
        }

        @Test
        @DisplayName("첫 번째 위치")
        fun testFirstPosition() {
            val sql = "SELECT"
            val (line, col) = ConversionErrorHandler.positionToLineColumn(sql, 0)
            assertEquals(1, line)
            assertEquals(1, col)
        }

        @Test
        @DisplayName("범위 밖 위치")
        fun testOutOfBoundsPosition() {
            val sql = "SELECT"
            val (line, col) = ConversionErrorHandler.positionToLineColumn(sql, 100)
            assertEquals(1, line)
            assertEquals(1, col)
        }
    }

    @Nested
    @DisplayName("에러 추출 테스트")
    inner class ErrorExtractionTest {

        @Test
        @DisplayName("기본 예외에서 에러 추출")
        fun testBasicExceptionExtraction() {
            val exception = RuntimeException("Syntax error at line 5")
            val sql = "SELECT\n*\nFROM\ntable\nWHERE"
            val error = ConversionErrorHandler.extractError(exception, sql)

            assertEquals(5, error.lineNumber)
            assertNotNull(error.suggestion)
        }

        @Test
        @DisplayName("위치 정보가 있는 예외")
        fun testPositionException() {
            val exception = RuntimeException("Error at position 10")
            val sql = "SELECT * FROM table"
            val error = ConversionErrorHandler.extractError(exception, sql)

            assertNotNull(error.sqlFragment)
            assertTrue(error.message.contains("position"))
        }

        @Test
        @DisplayName("구문 오류 타입 분류")
        fun testSyntaxErrorClassification() {
            val exception = RuntimeException("Syntax error: unexpected token")
            val sql = "SELECT FROM"
            val error = ConversionErrorHandler.extractError(exception, sql)

            assertEquals(ErrorType.SYNTAX_ERROR, error.type)
        }

        @Test
        @DisplayName("미지원 기능 타입 분류")
        fun testUnsupportedFeatureClassification() {
            val exception = RuntimeException("Feature not supported in MySQL")
            val sql = "SELECT * FROM dual"
            val error = ConversionErrorHandler.extractError(exception, sql)

            assertEquals(ErrorType.UNSUPPORTED_FEATURE, error.type)
        }
    }

    @Nested
    @DisplayName("부분 변환 테스트")
    inner class PartialConversionTest {

        @Test
        @DisplayName("전체 변환 성공")
        fun testFullConversionSuccess() {
            val sql = "SELECT 1; SELECT 2"
            val warnings = mutableListOf<ConversionWarning>()

            val result = ConversionErrorHandler.attemptPartialConversion(
                sql,
                { it.uppercase() },
                warnings
            )

            assertTrue(result.success)
            assertTrue(result.failedStatements.isEmpty())
        }

        @Test
        @DisplayName("부분 변환 - 일부 실패")
        fun testPartialConversionWithFailures() {
            val sql = "SELECT 1; INVALID SQL; SELECT 3"
            val warnings = mutableListOf<ConversionWarning>()

            var callCount = 0
            val result = ConversionErrorHandler.attemptPartialConversion(
                sql,
                { stmt ->
                    callCount++
                    if (stmt.contains("INVALID")) {
                        throw RuntimeException("Invalid SQL")
                    }
                    stmt.uppercase()
                },
                warnings
            )

            assertTrue(result.success, "일부 성공했으므로 true")
            assertEquals(1, result.failedStatements.size)
            assertTrue(result.failedStatements[0].sql.contains("INVALID"))
        }

        @Test
        @DisplayName("단일 구문 변환 실패")
        fun testSingleStatementFailure() {
            val sql = "INVALID SQL"
            val warnings = mutableListOf<ConversionWarning>()

            val result = ConversionErrorHandler.attemptPartialConversion(
                sql,
                { throw RuntimeException("Failed") },
                warnings
            )

            assertTrue(!result.success)
        }
    }

    @Nested
    @DisplayName("에러를 경고로 변환")
    inner class ErrorToWarningTest {

        @Test
        @DisplayName("구문 에러 변환")
        fun testSyntaxErrorToWarning() {
            val error = ConversionError(
                type = ErrorType.SYNTAX_ERROR,
                message = "Unexpected token",
                lineNumber = 5,
                columnNumber = 10,
                recoverable = true
            )

            val warning = ConversionErrorHandler.errorToWarning(error)

            assertTrue(warning.message.contains("라인 5"))
            assertTrue(warning.message.contains("컬럼 10"))
            assertEquals(WarningSeverity.WARNING, warning.severity)
        }

        @Test
        @DisplayName("복구 불가능 에러 변환")
        fun testUnrecoverableErrorToWarning() {
            val error = ConversionError(
                type = ErrorType.INTERNAL_ERROR,
                message = "Fatal error",
                recoverable = false
            )

            val warning = ConversionErrorHandler.errorToWarning(error)

            assertEquals(WarningSeverity.ERROR, warning.severity)
        }
    }
}
