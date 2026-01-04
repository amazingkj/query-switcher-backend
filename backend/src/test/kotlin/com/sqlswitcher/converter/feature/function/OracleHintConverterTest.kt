package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertContains

/**
 * OracleHintConverter 단위 테스트
 */
class OracleHintConverterTest {

    @Nested
    @DisplayName("Oracle → MySQL 힌트 변환")
    inner class ToMySqlTest {

        @Test
        @DisplayName("INDEX 힌트 → FORCE INDEX 변환")
        fun testIndexHintConversion() {
            val sql = "SELECT /*+ INDEX(emp emp_idx) */ * FROM employees emp"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("FORCE INDEX"), "FORCE INDEX로 변환되어야 함")
            assertTrue(result.contains("emp_idx"), "인덱스명이 유지되어야 함")
            assertTrue(appliedRules.any { it.contains("INDEX") }, "적용된 규칙에 INDEX가 포함되어야 함")
        }

        @Test
        @DisplayName("NO_INDEX 힌트 → IGNORE INDEX 변환")
        fun testNoIndexHintConversion() {
            val sql = "SELECT /*+ NO_INDEX(emp emp_idx) */ * FROM employees emp"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("IGNORE INDEX"), "IGNORE INDEX로 변환되어야 함")
            assertTrue(result.contains("emp_idx"), "인덱스명이 유지되어야 함")
        }

        @Test
        @DisplayName("LEADING 힌트 → STRAIGHT_JOIN 변환")
        fun testLeadingHintConversion() {
            val sql = "SELECT /*+ LEADING(emp dept) */ * FROM employees emp, departments dept"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("STRAIGHT_JOIN"), "STRAIGHT_JOIN으로 변환되어야 함")
            assertTrue(appliedRules.any { it.contains("LEADING") || it.contains("STRAIGHT_JOIN") },
                "적용된 규칙에 포함되어야 함")
        }

        @Test
        @DisplayName("PARALLEL 힌트 - MySQL에서 미지원 경고")
        fun testParallelHintWarning() {
            val sql = "SELECT /*+ PARALLEL(emp, 4) */ * FROM employees emp"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(warnings.isNotEmpty(), "경고가 있어야 함")
            assertTrue(warnings.any { it.message.contains("미지원") || it.suggestion?.contains("PARALLEL") == true },
                "PARALLEL 관련 경고가 있어야 함")
        }

        @Test
        @DisplayName("FULL 힌트 - 테이블 스캔 경고")
        fun testFullHintWarning() {
            val sql = "SELECT /*+ FULL(emp) */ * FROM employees emp"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(warnings.isNotEmpty() || appliedRules.any { it.contains("FULL") },
                "FULL 힌트 처리 기록이 있어야 함")
        }

        @Test
        @DisplayName("USE_NL 힌트 - 옵티마이저 위임 경고")
        fun testUseNlHintWarning() {
            val sql = "SELECT /*+ USE_NL(emp dept) */ * FROM employees emp, departments dept"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(warnings.isNotEmpty() || appliedRules.any { it.contains("USE_NL") || it.contains("제거") },
                "USE_NL 처리 기록이 있어야 함")
        }

        @Test
        @DisplayName("USE_HASH 힌트 - 옵티마이저 위임 경고")
        fun testUseHashHintWarning() {
            val sql = "SELECT /*+ USE_HASH(emp dept) */ * FROM employees emp, departments dept"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(warnings.isNotEmpty() || appliedRules.any { it.contains("USE_HASH") || it.contains("제거") },
                "USE_HASH 처리 기록이 있어야 함")
        }

        @Test
        @DisplayName("FIRST_ROWS 힌트 - LIMIT 사용 권장")
        fun testFirstRowsHintWarning() {
            val sql = "SELECT /*+ FIRST_ROWS(10) */ * FROM employees"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(warnings.isNotEmpty() || appliedRules.any { it.contains("FIRST_ROWS") || it.contains("제거") },
                "FIRST_ROWS 처리 기록이 있어야 함")
        }

        @Test
        @DisplayName("복합 힌트 처리")
        fun testMultipleHints() {
            val sql = "SELECT /*+ INDEX(emp emp_idx) LEADING(emp dept) */ * FROM employees emp, departments dept"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("FORCE INDEX") || result.contains("STRAIGHT_JOIN"),
                "최소 하나의 힌트가 변환되어야 함")
        }

        @Test
        @DisplayName("인덱스명 없는 INDEX 힌트 처리")
        fun testIndexHintWithoutIndexName() {
            val sql = "SELECT /*+ INDEX(emp) */ * FROM employees emp"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 인덱스명이 없으면 변환 불가능하므로 경고 또는 제거됨
            assertTrue(warnings.isNotEmpty() || appliedRules.any { it.contains("제거") || it.contains("INDEX") },
                "인덱스명 없는 힌트 처리 기록이 있어야 함")
        }
    }

    @Nested
    @DisplayName("Oracle → PostgreSQL 힌트 변환")
    inner class ToPostgreSqlTest {

        @Test
        @DisplayName("PARALLEL 힌트 → SET max_parallel_workers_per_gather")
        fun testParallelHintConversion() {
            val sql = "SELECT /*+ PARALLEL(emp, 4) */ * FROM employees emp"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("SET max_parallel_workers_per_gather"),
                "PostgreSQL SET 명령으로 변환되어야 함")
            assertTrue(result.contains("4"), "병렬도가 유지되어야 함")
        }

        @Test
        @DisplayName("FIRST_ROWS 힌트 → SET cursor_tuple_fraction")
        fun testFirstRowsHintConversion() {
            val sql = "SELECT /*+ FIRST_ROWS(10) */ * FROM employees"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("SET cursor_tuple_fraction"),
                "cursor_tuple_fraction으로 변환되어야 함")
        }

        @Test
        @DisplayName("ALL_ROWS 힌트 → SET cursor_tuple_fraction = 1.0")
        fun testAllRowsHintConversion() {
            val sql = "SELECT /*+ ALL_ROWS */ * FROM employees"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("SET cursor_tuple_fraction = 1.0"),
                "cursor_tuple_fraction = 1.0으로 변환되어야 함")
        }

        @Test
        @DisplayName("INDEX 힌트 - pg_hint_plan 권장")
        fun testIndexHintPgHintPlanRecommendation() {
            val sql = "SELECT /*+ INDEX(emp emp_idx) */ * FROM employees emp"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("pg_hint_plan") || warnings.any {
                it.suggestion?.contains("pg_hint_plan") == true
            }, "pg_hint_plan 확장 권장 메시지가 있어야 함")
        }

        @Test
        @DisplayName("USE_NL 힌트 - pg_hint_plan NestLoop 권장")
        fun testUseNlPgHintPlanRecommendation() {
            val sql = "SELECT /*+ USE_NL(emp dept) */ * FROM employees emp, departments dept"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(warnings.any {
                it.suggestion?.contains("pg_hint_plan") == true ||
                it.suggestion?.contains("NestLoop") == true
            }, "NestLoop pg_hint_plan 권장 메시지가 있어야 함")
        }

        @Test
        @DisplayName("USE_HASH 힌트 - pg_hint_plan HashJoin 권장")
        fun testUseHashPgHintPlanRecommendation() {
            val sql = "SELECT /*+ USE_HASH(emp dept) */ * FROM employees emp, departments dept"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(warnings.any {
                it.suggestion?.contains("pg_hint_plan") == true ||
                it.suggestion?.contains("HashJoin") == true
            }, "HashJoin pg_hint_plan 권장 메시지가 있어야 함")
        }

        @Test
        @DisplayName("LEADING 힌트 - pg_hint_plan Leading 권장")
        fun testLeadingPgHintPlanRecommendation() {
            val sql = "SELECT /*+ LEADING(emp dept) */ * FROM employees emp, departments dept"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(warnings.any {
                it.suggestion?.contains("pg_hint_plan") == true ||
                it.suggestion?.contains("Leading") == true
            }, "Leading pg_hint_plan 권장 메시지가 있어야 함")
        }

        @Test
        @DisplayName("APPEND 힌트 - COPY 명령 권장")
        fun testAppendHintRecommendation() {
            val sql = "INSERT /*+ APPEND */ INTO employees SELECT * FROM temp_emp"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(warnings.any {
                it.suggestion?.contains("COPY") == true ||
                it.suggestion?.contains("UNLOGGED") == true
            }, "COPY 또는 UNLOGGED 테이블 권장 메시지가 있어야 함")
        }

        @Test
        @DisplayName("힌트가 주석으로 변환됨")
        fun testHintConvertedToComment() {
            val sql = "SELECT /*+ INDEX(emp emp_idx) */ * FROM employees emp"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("/*") && result.contains("*/"),
                "힌트가 주석 형태로 유지되어야 함")
            assertTrue(result.contains("Oracle hint") || result.contains("pg_hint_plan"),
                "Oracle 힌트임을 표시해야 함")
        }
    }

    @Nested
    @DisplayName("힌트 유틸리티 메서드 테스트")
    inner class UtilityMethodsTest {

        @Test
        @DisplayName("removeAllHints - 모든 힌트 제거")
        fun testRemoveAllHints() {
            val sql = "SELECT /*+ INDEX(emp emp_idx) PARALLEL(4) */ * FROM employees emp"

            val result = OracleHintConverter.removeAllHints(sql)

            assertFalse(result.contains("/*+"), "힌트가 제거되어야 함")
            assertFalse(result.contains("INDEX"), "INDEX 힌트가 제거되어야 함")
            assertFalse(result.contains("PARALLEL"), "PARALLEL 힌트가 제거되어야 함")
            assertTrue(result.contains("SELECT"), "SELECT는 유지되어야 함")
            assertTrue(result.contains("FROM"), "FROM은 유지되어야 함")
        }

        @Test
        @DisplayName("extractHints - 힌트 추출")
        fun testExtractHints() {
            val sql = "SELECT /*+ INDEX(emp emp_idx) PARALLEL(4) */ * FROM employees emp"

            val hints = OracleHintConverter.extractHints(sql)

            assertTrue(hints.isNotEmpty(), "힌트가 추출되어야 함")
            assertTrue(hints[0].contains("INDEX") || hints[0].contains("PARALLEL"),
                "추출된 힌트에 INDEX 또는 PARALLEL이 포함되어야 함")
        }

        @Test
        @DisplayName("parseHints - INDEX 힌트 파싱")
        fun testParseIndexHint() {
            val hintContent = "INDEX(emp emp_idx)"

            val hints = OracleHintConverter.parseHints(hintContent)

            assertTrue(hints.isNotEmpty(), "힌트가 파싱되어야 함")
            assertEquals(OracleHintConverter.HintType.INDEX, hints[0].type, "INDEX 타입이어야 함")
            assertEquals("emp", hints[0].table, "테이블명이 emp여야 함")
            assertEquals("emp_idx", hints[0].index, "인덱스명이 emp_idx여야 함")
        }

        @Test
        @DisplayName("parseHints - PARALLEL 힌트 파싱")
        fun testParseParallelHint() {
            val hintContent = "PARALLEL(emp, 4)"

            val hints = OracleHintConverter.parseHints(hintContent)

            assertTrue(hints.isNotEmpty(), "힌트가 파싱되어야 함")
            assertEquals(OracleHintConverter.HintType.PARALLEL, hints[0].type, "PARALLEL 타입이어야 함")
            assertEquals(4, hints[0].degree, "병렬도가 4여야 함")
        }

        @Test
        @DisplayName("parseHints - FULL 힌트 파싱")
        fun testParseFullHint() {
            val hintContent = "FULL(emp)"

            val hints = OracleHintConverter.parseHints(hintContent)

            assertTrue(hints.isNotEmpty(), "힌트가 파싱되어야 함")
            assertEquals(OracleHintConverter.HintType.FULL, hints[0].type, "FULL 타입이어야 함")
            assertEquals("emp", hints[0].table, "테이블명이 emp여야 함")
        }

        @Test
        @DisplayName("parseHints - LEADING 힌트 파싱")
        fun testParseLeadingHint() {
            val hintContent = "LEADING(emp dept loc)"

            val hints = OracleHintConverter.parseHints(hintContent)

            assertTrue(hints.isNotEmpty(), "힌트가 파싱되어야 함")
            assertEquals(OracleHintConverter.HintType.LEADING, hints[0].type, "LEADING 타입이어야 함")
            assertTrue(hints[0].tables.containsAll(listOf("emp", "dept", "loc")),
                "테이블 목록이 포함되어야 함")
        }

        @Test
        @DisplayName("parseHints - 복합 힌트 파싱")
        fun testParseMultipleHints() {
            val hintContent = "INDEX(emp emp_idx) PARALLEL(emp, 4) FULL(dept)"

            val hints = OracleHintConverter.parseHints(hintContent)

            assertTrue(hints.size >= 2, "여러 힌트가 파싱되어야 함")
            assertTrue(hints.any { it.type == OracleHintConverter.HintType.INDEX },
                "INDEX 힌트가 포함되어야 함")
            assertTrue(hints.any { it.type == OracleHintConverter.HintType.PARALLEL },
                "PARALLEL 힌트가 포함되어야 함")
        }
    }

    @Nested
    @DisplayName("엣지 케이스 테스트")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("힌트가 없는 SQL은 그대로 반환")
        fun testNoHintSql() {
            val sql = "SELECT * FROM employees"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertEquals(sql, result, "원본 SQL이 그대로 반환되어야 함")
            assertTrue(warnings.isEmpty(), "경고가 없어야 함")
            assertTrue(appliedRules.isEmpty(), "적용된 규칙이 없어야 함")
        }

        @Test
        @DisplayName("Oracle이 아닌 소스는 변환하지 않음")
        fun testNonOracleSource() {
            val sql = "SELECT /*+ INDEX(emp emp_idx) */ * FROM employees emp"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.MYSQL, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertEquals(sql, result, "Oracle이 아니면 변환하지 않아야 함")
        }

        @Test
        @DisplayName("Oracle to Oracle은 변환하지 않음")
        fun testOracleToOracle() {
            val sql = "SELECT /*+ INDEX(emp emp_idx) */ * FROM employees emp"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.ORACLE, warnings, appliedRules
            )

            assertEquals(sql, result, "같은 dialect면 변환하지 않아야 함")
        }

        @Test
        @DisplayName("일반 주석은 힌트로 처리하지 않음")
        fun testRegularCommentNotTreatedAsHint() {
            val sql = "SELECT /* this is a comment */ * FROM employees"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertEquals(sql, result, "일반 주석은 변환하지 않아야 함")
        }

        @Test
        @DisplayName("빈 힌트 블록 처리")
        fun testEmptyHintBlock() {
            val sql = "SELECT /*+  */ * FROM employees"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 빈 힌트는 제거되거나 주석으로 유지될 수 있음
            assertTrue(result.contains("SELECT"), "SELECT가 유지되어야 함")
            assertTrue(result.contains("FROM"), "FROM이 유지되어야 함")
        }

        @Test
        @DisplayName("대소문자 혼합 힌트 처리")
        fun testMixedCaseHints() {
            val sql = "SELECT /*+ InDeX(emp emp_idx) PaRaLLeL(4) */ * FROM employees emp"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 대소문자 무관하게 처리되어야 함
            assertTrue(
                result.contains("FORCE INDEX") ||
                result.contains("Oracle hints removed") ||
                appliedRules.isNotEmpty(),
                "대소문자 무관하게 힌트가 처리되어야 함"
            )
        }

        @Test
        @DisplayName("멀티라인 힌트 처리")
        fun testMultilineHint() {
            val sql = """
                SELECT /*+
                    INDEX(emp emp_idx)
                    PARALLEL(4)
                */ * FROM employees emp
            """.trimIndent()
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("SELECT"), "SELECT가 유지되어야 함")
            assertTrue(result.contains("FROM"), "FROM이 유지되어야 함")
            // 힌트 처리 여부 확인
            assertTrue(
                result.contains("FORCE INDEX") ||
                result.contains("Oracle hints removed") ||
                appliedRules.isNotEmpty() ||
                warnings.isNotEmpty(),
                "멀티라인 힌트가 처리되어야 함"
            )
        }
    }
}
