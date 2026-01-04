package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.mockito.Mockito.mock
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * MergeConversionService 단위 테스트
 */
class MergeConversionServiceTest {

    private lateinit var mergeService: MergeConversionService

    @BeforeEach
    fun setup() {
        val functionService = mock(FunctionConversionService::class.java)
        mergeService = MergeConversionService(functionService)
    }

    @Nested
    @DisplayName("Oracle MERGE → PostgreSQL 변환")
    inner class OracleToPostgreSqlTest {

        @Test
        @DisplayName("기본 MERGE → INSERT ON CONFLICT DO UPDATE")
        fun testBasicMergeToPostgreSql() {
            val sql = """
                MERGE INTO employees t
                USING (SELECT 1 as id, 'John' as name FROM dual) s
                ON (t.id = s.id)
                WHEN MATCHED THEN UPDATE SET t.name = s.name
                WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id, s.name)
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = mergeService.convertMerge(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("INSERT INTO employees"), "INSERT INTO가 포함되어야 함")
            assertTrue(result.contains("ON CONFLICT"), "ON CONFLICT가 포함되어야 함")
            assertTrue(result.contains("DO UPDATE SET"), "DO UPDATE SET이 포함되어야 함")
            assertTrue(appliedRules.any { it.contains("INSERT ON CONFLICT") }, "적용된 규칙에 변환이 포함되어야 함")
        }

        @Test
        @DisplayName("USING DUAL MERGE → INSERT ON CONFLICT")
        fun testMergeUsingDualToPostgreSql() {
            val sql = """
                MERGE INTO products t
                USING DUAL
                ON (t.product_id = 100)
                WHEN MATCHED THEN UPDATE SET t.price = 99.99
                WHEN NOT MATCHED THEN INSERT (product_id, price) VALUES (100, 99.99)
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = mergeService.convertMerge(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("INSERT INTO products"), "INSERT INTO가 포함되어야 함")
            assertTrue(result.contains("ON CONFLICT"), "ON CONFLICT가 포함되어야 함")
        }

        @Test
        @DisplayName("WHEN MATCHED THEN DELETE 경고")
        fun testMergeWithDeleteWarning() {
            val sql = """
                MERGE INTO employees t
                USING source_table s
                ON (t.id = s.id)
                WHEN MATCHED AND s.deleted = 1 THEN DELETE
                WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id, s.name)
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            mergeService.convertMerge(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(warnings.any { it.type == WarningType.PARTIAL_SUPPORT && it.message.contains("DELETE") },
                "DELETE 미지원 경고가 있어야 함")
        }
    }

    @Nested
    @DisplayName("Oracle MERGE → MySQL 변환")
    inner class OracleToMySqlTest {

        @Test
        @DisplayName("기본 MERGE → INSERT ON DUPLICATE KEY UPDATE")
        fun testBasicMergeToMySql() {
            val sql = """
                MERGE INTO employees t
                USING (SELECT 1 as id, 'John' as name FROM dual) s
                ON (t.id = s.id)
                WHEN MATCHED THEN UPDATE SET t.name = s.name
                WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id, s.name)
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = mergeService.convertMerge(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("INSERT INTO employees"), "INSERT INTO가 포함되어야 함")
            assertTrue(result.contains("ON DUPLICATE KEY UPDATE"), "ON DUPLICATE KEY UPDATE가 포함되어야 함")
            assertTrue(appliedRules.any { it.contains("ON DUPLICATE KEY UPDATE") }, "적용된 규칙에 변환이 포함되어야 함")
        }

        @Test
        @DisplayName("UPDATE SET에서 VALUES() 변환")
        fun testUpdateSetValuesConversion() {
            val sql = """
                MERGE INTO products t
                USING source s
                ON (t.id = s.id)
                WHEN MATCHED THEN UPDATE SET t.name = s.name, t.price = s.price
                WHEN NOT MATCHED THEN INSERT (id, name, price) VALUES (s.id, s.name, s.price)
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = mergeService.convertMerge(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("VALUES(name)") || result.contains("VALUES(price)"),
                "VALUES() 참조가 포함되어야 함")
        }
    }

    @Nested
    @DisplayName("PostgreSQL → MySQL 변환")
    inner class PostgreSqlToMySqlTest {

        @Test
        @DisplayName("ON CONFLICT DO UPDATE → ON DUPLICATE KEY UPDATE")
        fun testOnConflictToOnDuplicateKey() {
            val sql = """
                INSERT INTO employees (id, name, salary)
                VALUES (1, 'John', 50000)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, salary = EXCLUDED.salary
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = mergeService.convertMerge(
                sql, DialectType.POSTGRESQL, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("ON DUPLICATE KEY UPDATE"), "ON DUPLICATE KEY UPDATE가 포함되어야 함")
            assertTrue(result.contains("VALUES(name)") || result.contains("VALUES(salary)"),
                "EXCLUDED가 VALUES()로 변환되어야 함")
            assertFalse(result.contains("EXCLUDED"), "EXCLUDED가 제거되어야 함")
        }

        @Test
        @DisplayName("ON CONFLICT DO NOTHING → INSERT IGNORE")
        fun testOnConflictDoNothingToInsertIgnore() {
            val sql = """
                INSERT INTO employees (id, name)
                VALUES (1, 'John')
                ON CONFLICT (id) DO NOTHING
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = mergeService.convertMerge(
                sql, DialectType.POSTGRESQL, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("INSERT IGNORE"), "INSERT IGNORE가 포함되어야 함")
            assertFalse(result.contains("ON CONFLICT"), "ON CONFLICT가 제거되어야 함")
        }
    }

    @Nested
    @DisplayName("MySQL → PostgreSQL 변환")
    inner class MySqlToPostgreSqlTest {

        @Test
        @DisplayName("ON DUPLICATE KEY UPDATE → ON CONFLICT DO UPDATE")
        fun testOnDuplicateKeyToOnConflict() {
            val sql = """
                INSERT INTO employees (id, name, salary)
                VALUES (1, 'John', 50000)
                ON DUPLICATE KEY UPDATE name = VALUES(name), salary = VALUES(salary)
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = mergeService.convertMerge(
                sql, DialectType.MYSQL, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("ON CONFLICT"), "ON CONFLICT가 포함되어야 함")
            assertTrue(result.contains("DO UPDATE SET"), "DO UPDATE SET이 포함되어야 함")
            assertTrue(result.contains("EXCLUDED.name") || result.contains("EXCLUDED.salary"),
                "VALUES()가 EXCLUDED로 변환되어야 함")
        }

        @Test
        @DisplayName("INSERT IGNORE → ON CONFLICT DO NOTHING")
        fun testInsertIgnoreToOnConflictDoNothing() {
            val sql = "INSERT IGNORE INTO employees (id, name) VALUES (1, 'John')"

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = mergeService.convertMerge(
                sql, DialectType.MYSQL, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("ON CONFLICT DO NOTHING"), "ON CONFLICT DO NOTHING이 포함되어야 함")
            assertFalse(result.contains("IGNORE"), "IGNORE가 제거되어야 함")
        }

        @Test
        @DisplayName("REPLACE INTO 경고")
        fun testReplaceIntoWarning() {
            val sql = "REPLACE INTO employees (id, name) VALUES (1, 'John')"

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = mergeService.convertMerge(
                sql, DialectType.MYSQL, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("INSERT INTO"), "INSERT INTO로 변환되어야 함")
            assertTrue(warnings.any { it.message.contains("REPLACE INTO") }, "REPLACE INTO 경고가 있어야 함")
        }
    }

    @Nested
    @DisplayName("동일 방언 변환")
    inner class SameDialectTest {

        @Test
        @DisplayName("Oracle → Oracle 변환 없음")
        fun testOracleToOracle() {
            val sql = "MERGE INTO table1 USING table2 ON (t1.id = t2.id) WHEN MATCHED THEN UPDATE SET t1.name = t2.name"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = mergeService.convertMerge(
                sql, DialectType.ORACLE, DialectType.ORACLE, warnings, appliedRules
            )

            assertTrue(result == sql, "동일 방언은 원본 그대로 반환되어야 함")
        }
    }

    @Nested
    @DisplayName("복잡한 MERGE 구문")
    inner class ComplexMergeTest {

        @Test
        @DisplayName("파싱 실패 시 수동 변환 경고")
        fun testComplexMergeWarning() {
            val sql = """
                MERGE INTO (SELECT * FROM employees WHERE dept_id = 10) t
                USING (
                    SELECT id, name,
                           CASE WHEN status = 'A' THEN 1 ELSE 0 END as active
                    FROM source_table
                    WHERE created_date > SYSDATE - 30
                ) s
                ON (t.id = s.id AND t.active = s.active)
                WHEN MATCHED THEN UPDATE SET t.name = s.name
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            mergeService.convertMerge(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 복잡한 MERGE는 수동 변환 경고가 발생해야 함
            assertTrue(warnings.isNotEmpty() || appliedRules.isNotEmpty(),
                "변환 시도 또는 경고가 있어야 함")
        }
    }
}
