package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * CteConverter 단위 테스트
 */
class CteConverterTest {

    @Nested
    @DisplayName("Oracle → MySQL/PostgreSQL 재귀 CTE 변환")
    inner class OracleRecursiveCteTest {

        @Test
        @DisplayName("Oracle 재귀 CTE → WITH RECURSIVE 변환")
        fun testOracleRecursiveCteToWithRecursive() {
            val sql = """
                WITH emp_hierarchy(emp_id, manager_id, level_num) AS (
                    SELECT emp_id, manager_id, 1
                    FROM employees
                    WHERE manager_id IS NULL
                    UNION ALL
                    SELECT e.emp_id, e.manager_id, eh.level_num + 1
                    FROM employees e
                    JOIN emp_hierarchy eh ON e.manager_id = eh.emp_id
                )
                SELECT * FROM emp_hierarchy
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = CteConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("WITH RECURSIVE"), "WITH RECURSIVE로 변환되어야 함")
            assertTrue(appliedRules.any { it.contains("WITH RECURSIVE") }, "적용된 규칙에 WITH RECURSIVE 변환이 포함되어야 함")
        }

        @Test
        @DisplayName("이미 RECURSIVE가 있으면 변환하지 않음")
        fun testAlreadyRecursive() {
            val sql = """
                WITH RECURSIVE emp_hierarchy AS (
                    SELECT emp_id FROM employees WHERE manager_id IS NULL
                    UNION ALL
                    SELECT e.emp_id FROM employees e JOIN emp_hierarchy eh ON e.manager_id = eh.emp_id
                )
                SELECT * FROM emp_hierarchy
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = CteConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 이미 RECURSIVE가 있으므로 변경 없음
            assertTrue(result.contains("WITH RECURSIVE"), "WITH RECURSIVE가 유지되어야 함")
            assertFalse(result.contains("WITH RECURSIVE RECURSIVE"), "중복 RECURSIVE가 없어야 함")
        }
    }

    @Nested
    @DisplayName("MySQL/PostgreSQL → Oracle CTE 변환")
    inner class ToOracleCteTest {

        @Test
        @DisplayName("WITH RECURSIVE → Oracle WITH 변환")
        fun testWithRecursiveToOracleWith() {
            val sql = """
                WITH RECURSIVE category_tree AS (
                    SELECT id, name, parent_id, 1 as depth
                    FROM categories
                    WHERE parent_id IS NULL
                    UNION ALL
                    SELECT c.id, c.name, c.parent_id, ct.depth + 1
                    FROM categories c
                    JOIN category_tree ct ON c.parent_id = ct.id
                )
                SELECT * FROM category_tree
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = CteConverter.convert(
                sql, DialectType.MYSQL, DialectType.ORACLE, warnings, appliedRules
            )

            assertTrue(result.contains("WITH category_tree"), "WITH (RECURSIVE 제거)로 변환되어야 함")
            assertFalse(result.contains("RECURSIVE"), "RECURSIVE 키워드가 제거되어야 함")
            assertTrue(appliedRules.any { it.contains("Oracle WITH") }, "적용된 규칙에 Oracle WITH 변환이 포함되어야 함")
        }

        @Test
        @DisplayName("PostgreSQL WITH RECURSIVE → Oracle WITH 변환")
        fun testPostgresRecursiveToOracle() {
            val sql = """
                WITH RECURSIVE nums AS (
                    SELECT 1 as n
                    UNION ALL
                    SELECT n + 1 FROM nums WHERE n < 10
                )
                SELECT * FROM nums
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = CteConverter.convert(
                sql, DialectType.POSTGRESQL, DialectType.ORACLE, warnings, appliedRules
            )

            assertTrue(result.contains("WITH nums"), "WITH (RECURSIVE 제거)로 변환되어야 함")
            assertFalse(result.contains("RECURSIVE"), "RECURSIVE 키워드가 제거되어야 함")
        }
    }

    @Nested
    @DisplayName("MySQL CTE 경고 테스트")
    inner class MySqlCteWarningTest {

        @Test
        @DisplayName("CTE 사용 시 MySQL 버전 경고")
        fun testMySqlCteVersionWarning() {
            val sql = """
                WITH sales_summary AS (
                    SELECT product_id, SUM(amount) as total
                    FROM sales GROUP BY product_id
                )
                SELECT * FROM sales_summary
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            CteConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(warnings.any { it.message.contains("CTE") || it.message.contains("WITH 절") },
                "CTE 관련 경고가 있어야 함")
            assertTrue(warnings.any { it.suggestion?.contains("MySQL 8.0") == true },
                "MySQL 8.0 버전 관련 제안이 있어야 함")
        }
    }

    @Nested
    @DisplayName("비재귀 CTE 테스트")
    inner class NonRecursiveCteTest {

        @Test
        @DisplayName("비재귀 CTE는 RECURSIVE 키워드 추가하지 않음")
        fun testNonRecursiveCteNotConverted() {
            val sql = """
                WITH dept_totals AS (
                    SELECT dept_id, SUM(salary) as total_salary
                    FROM employees GROUP BY dept_id
                )
                SELECT * FROM dept_totals WHERE total_salary > 100000
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = CteConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertFalse(result.contains("RECURSIVE"), "비재귀 CTE에는 RECURSIVE가 추가되지 않아야 함")
        }
    }

    @Nested
    @DisplayName("변환하지 않는 케이스 테스트")
    inner class NoConversionTest {

        @Test
        @DisplayName("동일 방언은 변환하지 않음")
        fun testSameDialectNoConversion() {
            val sql = "WITH cte AS (SELECT 1) SELECT * FROM cte"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = CteConverter.convert(
                sql, DialectType.ORACLE, DialectType.ORACLE, warnings, appliedRules
            )

            assertTrue(result == sql, "동일 방언은 원본 그대로 반환되어야 함")
            assertTrue(appliedRules.isEmpty(), "적용된 규칙이 없어야 함")
        }

        @Test
        @DisplayName("CTE가 없는 SQL")
        fun testNoCte() {
            val sql = "SELECT id, name FROM employees WHERE dept_id = 10"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = CteConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result == sql, "CTE가 없으면 원본 그대로 반환되어야 함")
        }
    }
}
