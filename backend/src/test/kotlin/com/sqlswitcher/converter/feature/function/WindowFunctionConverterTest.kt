package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * WindowFunctionConverter 단위 테스트
 */
class WindowFunctionConverterTest {

    @Nested
    @DisplayName("KEEP (DENSE_RANK) 변환 테스트")
    inner class KeepClauseTest {

        @Test
        @DisplayName("Oracle KEEP (DENSE_RANK FIRST) → PostgreSQL FIRST_VALUE")
        fun testKeepDenseRankFirst() {
            val sql = """
                SELECT MAX(salary) KEEP (DENSE_RANK FIRST ORDER BY hire_date)
                FROM employees
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = WindowFunctionConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("FIRST_VALUE"), "FIRST_VALUE로 변환되어야 함")
            assertTrue(result.contains("ORDER BY hire_date"), "ORDER BY가 유지되어야 함")
            assertTrue(appliedRules.any { it.contains("KEEP") }, "적용된 규칙에 KEEP 변환이 포함되어야 함")
        }

        @Test
        @DisplayName("Oracle KEEP (DENSE_RANK LAST) → MySQL LAST_VALUE")
        fun testKeepDenseRankLast() {
            val sql = """
                SELECT MIN(price) KEEP (DENSE_RANK LAST ORDER BY created_at)
                FROM products
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = WindowFunctionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("LAST_VALUE"), "LAST_VALUE로 변환되어야 함")
            assertTrue(warnings.any { it.message.contains("근사적") }, "MySQL 변환 경고가 있어야 함")
        }
    }

    @Nested
    @DisplayName("WITHIN GROUP 변환 테스트")
    inner class WithinGroupTest {

        @Test
        @DisplayName("Oracle LISTAGG WITHIN GROUP → PostgreSQL STRING_AGG")
        fun testListaggToStringAgg() {
            val sql = """
                SELECT dept_id, LISTAGG(emp_name, ', ') WITHIN GROUP (ORDER BY emp_name)
                FROM employees GROUP BY dept_id
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = WindowFunctionConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("STRING_AGG"), "STRING_AGG로 변환되어야 함")
            assertTrue(appliedRules.any { it.contains("LISTAGG") }, "적용된 규칙에 LISTAGG 변환이 포함되어야 함")
        }

        @Test
        @DisplayName("Oracle LISTAGG WITHIN GROUP → MySQL GROUP_CONCAT")
        fun testListaggToGroupConcat() {
            val sql = """
                SELECT dept_id, LISTAGG(emp_name, ', ') WITHIN GROUP (ORDER BY hire_date)
                FROM employees GROUP BY dept_id
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = WindowFunctionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("GROUP_CONCAT"), "GROUP_CONCAT로 변환되어야 함")
            assertTrue(result.contains("SEPARATOR"), "SEPARATOR가 포함되어야 함")
        }
    }

    @Nested
    @DisplayName("RATIO_TO_REPORT 변환 테스트")
    inner class RatioToReportTest {

        @Test
        @DisplayName("Oracle RATIO_TO_REPORT → SUM OVER 기반 계산")
        fun testRatioToReport() {
            val sql = """
                SELECT dept_id, salary, RATIO_TO_REPORT(salary) OVER (PARTITION BY dept_id) as ratio
                FROM employees
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = WindowFunctionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("salary / SUM(salary) OVER"), "SUM OVER 기반 계산으로 변환되어야 함")
            assertTrue(appliedRules.any { it.contains("RATIO_TO_REPORT") }, "적용된 규칙에 RATIO_TO_REPORT 변환이 포함되어야 함")
        }
    }

    @Nested
    @DisplayName("IGNORE NULLS 변환 테스트")
    inner class IgnoreNullsTest {

        @Test
        @DisplayName("FIRST_VALUE IGNORE NULLS → MySQL 호환 구문")
        fun testIgnoreNullsMySql() {
            val sql = """
                SELECT FIRST_VALUE(value) IGNORE NULLS OVER (ORDER BY created_at)
                FROM data_table
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = WindowFunctionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("/* IGNORE NULLS not supported"), "MySQL 미지원 주석이 있어야 함")
            assertTrue(warnings.any { it.message.contains("IGNORE NULLS") }, "경고에 IGNORE NULLS 관련 메시지가 있어야 함")
        }
    }

    @Nested
    @DisplayName("MySQL 8.0 경고 테스트")
    inner class MySqlVersionWarningTest {

        @Test
        @DisplayName("윈도우 함수 사용 시 MySQL 버전 경고")
        fun testMySqlWindowFunctionWarning() {
            val sql = """
                SELECT ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC)
                FROM employees
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            WindowFunctionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(warnings.any { it.message.contains("윈도우 함수") }, "윈도우 함수 경고가 있어야 함")
            assertTrue(warnings.any { it.suggestion?.contains("MySQL 8.0") == true }, "MySQL 8.0 버전 관련 제안이 있어야 함")
        }
    }

    @Nested
    @DisplayName("변환하지 않는 케이스 테스트")
    inner class NoConversionTest {

        @Test
        @DisplayName("동일 방언은 변환하지 않음")
        fun testSameDialectNoConversion() {
            val sql = "SELECT ROW_NUMBER() OVER (ORDER BY id) FROM table1"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = WindowFunctionConverter.convert(
                sql, DialectType.ORACLE, DialectType.ORACLE, warnings, appliedRules
            )

            assertTrue(result == sql, "동일 방언은 원본 그대로 반환되어야 함")
            assertTrue(appliedRules.isEmpty(), "적용된 규칙이 없어야 함")
        }

        @Test
        @DisplayName("윈도우 함수가 없는 SQL")
        fun testNoWindowFunction() {
            val sql = "SELECT id, name FROM employees WHERE dept_id = 10"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = WindowFunctionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result == sql, "윈도우 함수가 없으면 원본 그대로 반환되어야 함")
        }
    }
}
