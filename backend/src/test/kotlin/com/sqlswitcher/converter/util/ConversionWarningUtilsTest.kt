package com.sqlswitcher.converter.util

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ConversionWarningUtils 단위 테스트
 */
class ConversionWarningUtilsTest {

    @Nested
    @DisplayName("unsupportedFunction 테스트")
    inner class UnsupportedFunctionTest {

        @Test
        @DisplayName("미지원 함수 경고 생성")
        fun testUnsupportedFunctionWarning() {
            val warning = ConversionWarningUtils.unsupportedFunction(
                functionName = "DECODE",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            assertEquals(WarningType.UNSUPPORTED_FUNCTION, warning.type)
            assertEquals(WarningSeverity.WARNING, warning.severity)
            assertTrue(warning.message.contains("DECODE"))
            assertTrue(warning.message.contains("MySQL"))
            assertNotNull(warning.suggestion)
        }

        @Test
        @DisplayName("커스텀 제안사항 포함")
        fun testUnsupportedFunctionWithCustomSuggestion() {
            val customSuggestion = "CASE WHEN 문을 사용하세요."
            val warning = ConversionWarningUtils.unsupportedFunction(
                functionName = "NVL",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL,
                suggestion = customSuggestion
            )

            assertEquals(customSuggestion, warning.suggestion)
        }
    }

    @Nested
    @DisplayName("partialSupport 테스트")
    inner class PartialSupportTest {

        @Test
        @DisplayName("부분 지원 경고 생성")
        fun testPartialSupportWarning() {
            val warning = ConversionWarningUtils.partialSupport(
                functionName = "REGEXP_COUNT",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                limitationDescription = "정확한 정규식 매칭이 아닌 단순 문자열 치환으로 근사 계산됩니다."
            )

            assertEquals(WarningType.PARTIAL_SUPPORT, warning.type)
            assertEquals(WarningSeverity.INFO, warning.severity)
            assertTrue(warning.message.contains("REGEXP_COUNT"))
            assertTrue(warning.message.contains("근사 계산"))
        }
    }

    @Nested
    @DisplayName("manualReviewNeeded 테스트")
    inner class ManualReviewNeededTest {

        @Test
        @DisplayName("수동 검토 필요 경고 생성")
        fun testManualReviewWarning() {
            val warning = ConversionWarningUtils.manualReviewNeeded(
                feature = "CONNECT BY",
                reason = "계층적 쿼리 구조가 복잡하여 자동 변환이 불가능합니다."
            )

            assertEquals(WarningType.MANUAL_REVIEW_NEEDED, warning.type)
            assertEquals(WarningSeverity.WARNING, warning.severity)
            assertTrue(warning.message.contains("CONNECT BY"))
        }
    }

    @Nested
    @DisplayName("performanceWarning 테스트")
    inner class PerformanceWarningTest {

        @Test
        @DisplayName("성능 경고 생성")
        fun testPerformanceWarning() {
            val warning = ConversionWarningUtils.performanceWarning(
                feature = "ROWNUM 변환",
                description = "사용자 변수를 사용한 ROWNUM 에뮬레이션은 대용량 데이터에서 성능 저하가 발생할 수 있습니다.",
                suggestion = "가능하면 ROW_NUMBER() 윈도우 함수 사용을 권장합니다."
            )

            assertEquals(WarningType.PERFORMANCE_WARNING, warning.type)
            assertEquals(WarningSeverity.WARNING, warning.severity)
            assertTrue(warning.message.contains("성능 저하"))
            assertNotNull(warning.suggestion)
        }
    }

    @Nested
    @DisplayName("syntaxDifference 테스트")
    inner class SyntaxDifferenceTest {

        @Test
        @DisplayName("구문 차이 경고 생성")
        fun testSyntaxDifferenceWarning() {
            val warning = ConversionWarningUtils.syntaxDifference(
                feature = "날짜 연산",
                description = "Oracle의 날짜 연산 방식과 MySQL의 DATE_ADD 함수 동작이 다를 수 있습니다."
            )

            assertEquals(WarningType.SYNTAX_DIFFERENCE, warning.type)
            assertEquals(WarningSeverity.INFO, warning.severity)
        }
    }

    @Nested
    @DisplayName("dataTypeMismatch 테스트")
    inner class DataTypeMismatchTest {

        @Test
        @DisplayName("데이터 타입 불일치 경고 생성")
        fun testDataTypeMismatchWarning() {
            val warning = ConversionWarningUtils.dataTypeMismatch(
                sourceType = "NUMBER(38)",
                targetType = "BIGINT",
                description = "정밀도 손실이 발생할 수 있습니다."
            )

            assertEquals(WarningType.DATA_TYPE_MISMATCH, warning.type)
            assertEquals(WarningSeverity.WARNING, warning.severity)
            assertTrue(warning.message.contains("NUMBER(38)"))
            assertTrue(warning.message.contains("BIGINT"))
        }
    }

    @Nested
    @DisplayName("UnsupportedFeatures 정적 경고 테스트")
    inner class UnsupportedFeaturesTest {

        @Test
        @DisplayName("CUBE 미지원 경고")
        fun testCubeWarning() {
            val warning = ConversionWarningUtils.UnsupportedFeatures.cubeInMySql
            assertEquals(WarningType.UNSUPPORTED_FUNCTION, warning.type)
            assertEquals(WarningSeverity.ERROR, warning.severity)
            assertTrue(warning.message.contains("CUBE"))
        }

        @Test
        @DisplayName("GROUPING SETS 미지원 경고")
        fun testGroupingSetsWarning() {
            val warning = ConversionWarningUtils.UnsupportedFeatures.groupingSetsInMySql
            assertEquals(WarningType.UNSUPPORTED_FUNCTION, warning.type)
            assertTrue(warning.message.contains("GROUPING SETS"))
        }

        @Test
        @DisplayName("시퀀스 미지원 경고")
        fun testSequenceWarning() {
            val warning = ConversionWarningUtils.UnsupportedFeatures.sequenceInMySql
            assertEquals(WarningType.UNSUPPORTED_FUNCTION, warning.type)
            assertTrue(warning.message.contains("시퀀스"))
        }

        @Test
        @DisplayName("CONNECT BY 미지원 경고 (MySQL)")
        fun testConnectByMySqlWarning() {
            val warning = ConversionWarningUtils.UnsupportedFeatures.connectByInMySql
            assertTrue(warning.message.contains("CONNECT BY"))
            assertTrue(warning.suggestion!!.contains("WITH RECURSIVE"))
        }

        @Test
        @DisplayName("CONNECT BY 미지원 경고 (PostgreSQL)")
        fun testConnectByPostgreSqlWarning() {
            val warning = ConversionWarningUtils.UnsupportedFeatures.connectByInPostgreSql
            assertTrue(warning.message.contains("CONNECT BY"))
            assertTrue(warning.suggestion!!.contains("WITH RECURSIVE"))
        }

        @Test
        @DisplayName("Full-Text 검색 경고")
        fun testFullTextSearchWarning() {
            val warning = ConversionWarningUtils.UnsupportedFeatures.fullTextSearch(
                DialectType.MYSQL,
                DialectType.POSTGRESQL
            )
            assertEquals(WarningType.PARTIAL_SUPPORT, warning.type)
            assertTrue(warning.message.contains("Full-Text"))
        }

        @Test
        @DisplayName("힌트 제거 경고")
        fun testHintsRemovedWarning() {
            val warning = ConversionWarningUtils.UnsupportedFeatures.hintsRemoved
            assertEquals(WarningType.INFO, warning.type)
            assertEquals(WarningSeverity.INFO, warning.severity)
        }

        @Test
        @DisplayName("암호화 함수 확장 필요 경고")
        fun testCryptoFunctionWarning() {
            val warning = ConversionWarningUtils.UnsupportedFeatures.cryptoFunctionExtensionNeeded
            assertTrue(warning.suggestion!!.contains("pgcrypto"))
        }

        @Test
        @DisplayName("ROWNUM 근사 변환 경고")
        fun testRownumApproximationWarning() {
            val warning = ConversionWarningUtils.UnsupportedFeatures.rownumApproximation
            assertEquals(WarningType.PARTIAL_SUPPORT, warning.type)
            assertTrue(warning.message.contains("ROWNUM"))
        }
    }
}
