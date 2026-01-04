package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * PivotUnpivotConverter 단위 테스트
 */
class PivotUnpivotConverterTest {

    @Nested
    @DisplayName("PIVOT 변환 테스트")
    inner class PivotConversionTest {

        @Test
        @DisplayName("기본 PIVOT 변환")
        fun testBasicPivotConversion() {
            val sql = """
                SELECT * FROM sales
                PIVOT (
                    SUM(amount)
                    FOR category IN ('A' AS cat_a, 'B' AS cat_b)
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = PivotUnpivotConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("CASE WHEN"), "CASE WHEN으로 변환되어야 함")
            assertTrue(result.contains("SUM"), "집계 함수가 유지되어야 함")
            assertTrue(result.contains("cat_a"), "별칭이 유지되어야 함")
            assertTrue(result.contains("cat_b"), "별칭이 유지되어야 함")
            assertTrue(appliedRules.any { it.contains("PIVOT") }, "적용된 규칙에 PIVOT 변환이 포함되어야 함")
        }

        @Test
        @DisplayName("Oracle이 아닌 소스에서는 변환하지 않음")
        fun testNonOracleSourceNotConverted() {
            val sql = "SELECT * FROM table PIVOT (SUM(val) FOR col IN ('A' AS a))"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = PivotUnpivotConverter.convert(
                sql, DialectType.MYSQL, DialectType.POSTGRESQL, warnings, appliedRules
            )

            // MySQL 소스는 변환하지 않음
            assertTrue(result.contains("PIVOT"), "원본이 유지되어야 함")
        }
    }

    @Nested
    @DisplayName("UNPIVOT 변환 테스트")
    inner class UnpivotConversionTest {

        @Test
        @DisplayName("기본 UNPIVOT 변환")
        fun testBasicUnpivotConversion() {
            val sql = """
                SELECT * FROM wide_table
                UNPIVOT (
                    value FOR category IN (col1, col2, col3)
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = PivotUnpivotConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("UNION ALL"), "UNION ALL로 변환되어야 함")
            assertTrue(appliedRules.any { it.contains("UNPIVOT") }, "적용된 규칙에 UNPIVOT 변환이 포함되어야 함")
        }

        @Test
        @DisplayName("별칭이 있는 UNPIVOT 변환")
        fun testUnpivotWithAliases() {
            val sql = """
                SELECT * FROM wide_table
                UNPIVOT (
                    amount FOR period IN (q1 AS 'Quarter 1', q2 AS 'Quarter 2')
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = PivotUnpivotConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("UNION ALL"), "UNION ALL로 변환되어야 함")
            assertTrue(result.contains("Quarter"), "별칭이 유지되어야 함")
        }
    }

    @Nested
    @DisplayName("변환하지 않는 케이스")
    inner class NoConversionTest {

        @Test
        @DisplayName("PIVOT/UNPIVOT이 없는 SQL")
        fun testNoPivotUnpivot() {
            val sql = "SELECT id, name FROM employees WHERE dept_id = 10"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = PivotUnpivotConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 원본 그대로 반환
            assertTrue(appliedRules.isEmpty(), "적용된 규칙이 없어야 함")
        }
    }
}
