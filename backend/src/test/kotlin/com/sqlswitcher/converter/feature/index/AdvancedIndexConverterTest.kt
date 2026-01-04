package com.sqlswitcher.converter.feature.index

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * AdvancedIndexConverter 단위 테스트
 */
class AdvancedIndexConverterTest {

    private fun convert(sql: String, source: DialectType, target: DialectType): Triple<String, List<ConversionWarning>, List<String>> {
        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()
        val result = AdvancedIndexConverter.convert(sql, source, target, warnings, rules)
        return Triple(result, warnings, rules)
    }

    @Nested
    @DisplayName("CREATE INDEX 변환 테스트")
    inner class CreateIndexTest {

        @Test
        @DisplayName("Oracle → MySQL 기본 인덱스")
        fun testOracleToMySqlBasic() {
            val sql = "CREATE INDEX emp_idx ON employees (last_name, first_name);"

            val (result, _, rules) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("CREATE INDEX emp_idx"))
            assertTrue(result.contains("ON employees"))
            assertTrue(result.contains("last_name"))
            assertTrue(rules.isNotEmpty())
        }

        @Test
        @DisplayName("Oracle → PostgreSQL 기본 인덱스")
        fun testOracleToPostgreSql() {
            val sql = "CREATE INDEX emp_idx ON employees (department_id);"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("CREATE INDEX"))
            assertTrue(result.contains("IF NOT EXISTS"))
            assertTrue(result.contains("emp_idx"))
        }

        @Test
        @DisplayName("Oracle UNIQUE 인덱스 변환")
        fun testUniqueIndex() {
            val sql = "CREATE UNIQUE INDEX emp_email_uk ON employees (email);"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("CREATE UNIQUE INDEX"))
            assertTrue(result.contains("emp_email_uk"))
        }

        @Test
        @DisplayName("Oracle BITMAP 인덱스 → MySQL (경고 발생)")
        fun testBitmapIndex() {
            val sql = "CREATE BITMAP INDEX emp_status_idx ON employees (status);"

            val (result, warnings, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("CREATE INDEX"))
            assertFalse(result.contains("BITMAP"))
            assertTrue(warnings.any { it.message.contains("BITMAP") })
        }

        @Test
        @DisplayName("Oracle BITMAP 인덱스 → PostgreSQL (BRIN 제안)")
        fun testBitmapToPostgreSql() {
            val sql = "CREATE BITMAP INDEX emp_status_idx ON employees (status);"

            val (result, warnings, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("CREATE INDEX"))
            assertTrue(warnings.any { it.message.contains("BRIN") })
        }

        @Test
        @DisplayName("스키마가 있는 인덱스")
        fun testSchemaQualifiedIndex() {
            val sql = "CREATE INDEX hr.emp_idx ON hr.employees (department_id);"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("hr.emp_idx"))
            assertTrue(result.contains("hr.employees"))
        }

        @Test
        @DisplayName("TABLESPACE 옵션 변환")
        fun testTablespaceOption() {
            val sql = "CREATE INDEX emp_idx ON employees (last_name) TABLESPACE idx_ts;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("TABLESPACE idx_ts"))
        }

        @Test
        @DisplayName("Oracle REVERSE 인덱스 → MySQL (경고)")
        fun testReverseIndex() {
            val sql = "CREATE INDEX emp_seq_idx ON employees (emp_id) REVERSE;"

            val (result, warnings, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("CREATE INDEX"))
            assertFalse(result.contains("REVERSE"))
            assertTrue(warnings.any { it.message.contains("REVERSE") })
        }

        @Test
        @DisplayName("Oracle LOCAL 파티션 인덱스")
        fun testLocalIndex() {
            val sql = "CREATE INDEX emp_date_idx ON employees (hire_date) LOCAL;"

            val (result, warnings, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("CREATE INDEX"))
            assertTrue(warnings.any { it.message.contains("파티션") })
        }

        @Test
        @DisplayName("Oracle ONLINE 인덱스 → PostgreSQL CONCURRENTLY")
        fun testOnlineIndex() {
            val sql = "CREATE INDEX emp_idx ON employees (department_id) ONLINE;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("CONCURRENTLY"))
        }

        @Test
        @DisplayName("Oracle INVISIBLE 인덱스 → MySQL")
        fun testInvisibleIndex() {
            val sql = "CREATE INDEX emp_idx ON employees (last_name) INVISIBLE;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("INVISIBLE"))
        }
    }

    @Nested
    @DisplayName("DROP INDEX 변환 테스트")
    inner class DropIndexTest {

        @Test
        @DisplayName("Oracle → MySQL DROP INDEX")
        fun testDropIndexToMySql() {
            val sql = "DROP INDEX emp_idx ON employees;"

            val (result, _, rules) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("DROP INDEX"))
            assertTrue(result.contains("ON employees"))
            assertTrue(rules.isNotEmpty())
        }

        @Test
        @DisplayName("Oracle → PostgreSQL DROP INDEX")
        fun testDropIndexToPostgreSql() {
            val sql = "DROP INDEX emp_idx;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("DROP INDEX IF EXISTS"))
            assertTrue(result.contains("CASCADE"))
        }

        @Test
        @DisplayName("Oracle DROP INDEX (ON 절 없음) → MySQL (경고)")
        fun testDropIndexWithoutOnClause() {
            val sql = "DROP INDEX emp_idx;"

            val (result, warnings, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("DROP INDEX"))
            assertTrue(warnings.any { it.message.contains("ON 테이블명") })
        }
    }

    @Nested
    @DisplayName("함수 기반 인덱스 변환 테스트")
    inner class FunctionBasedIndexTest {

        @Test
        @DisplayName("UPPER() 함수 기반 인덱스 → MySQL")
        fun testUpperFunctionIndex() {
            val sql = "CREATE INDEX emp_name_upper_idx ON employees (UPPER(last_name));"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("(UPPER(last_name))"))
        }

        @Test
        @DisplayName("NVL() → COALESCE() 변환")
        fun testNvlToCoalesce() {
            val sql = "CREATE INDEX emp_comm_idx ON employees (NVL(commission_pct, 0));"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("COALESCE"))
            assertFalse(result.contains("NVL"))
        }

        @Test
        @DisplayName("SUBSTR → SUBSTRING 변환")
        fun testSubstrToSubstring() {
            val sql = "CREATE INDEX emp_code_idx ON employees (SUBSTR(employee_code, 1, 3));"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("SUBSTRING"))
            assertFalse(result.contains("SUBSTR("))
        }
    }

    @Nested
    @DisplayName("Oracle 옵션 테스트")
    inner class OracleOptionsTest {

        @Test
        @DisplayName("PARALLEL 옵션 → Oracle")
        fun testParallelOption() {
            val sql = "CREATE INDEX emp_idx ON employees (department_id) PARALLEL 4;"

            val (result, _, _) = convert(sql, DialectType.MYSQL, DialectType.ORACLE)

            assertTrue(result.contains("PARALLEL 4"))
        }

        @Test
        @DisplayName("COMPRESS 옵션 → Oracle")
        fun testCompressOption() {
            val sql = "CREATE INDEX emp_idx ON employees (department_id, job_id) COMPRESS 1;"

            val (result, _, _) = convert(sql, DialectType.MYSQL, DialectType.ORACLE)

            assertTrue(result.contains("COMPRESS 1"))
        }

        @Test
        @DisplayName("전체 Oracle 옵션 조합")
        fun testFullOracleOptions() {
            val sql = """
                CREATE UNIQUE INDEX emp_comp_idx ON employees (department_id, job_id)
                TABLESPACE idx_ts
                LOCAL
                PARALLEL 8
                COMPRESS 2
                ONLINE
                INVISIBLE;
            """.trimIndent()

            val (result, _, _) = convert(sql, DialectType.MYSQL, DialectType.ORACLE)

            assertTrue(result.contains("CREATE UNIQUE INDEX"))
            assertTrue(result.contains("TABLESPACE idx_ts"))
            assertTrue(result.contains("LOCAL"))
            assertTrue(result.contains("PARALLEL 8"))
            assertTrue(result.contains("COMPRESS 2"))
            assertTrue(result.contains("ONLINE"))
            assertTrue(result.contains("INVISIBLE"))
        }
    }

    @Nested
    @DisplayName("유틸리티 메서드 테스트")
    inner class UtilityMethodsTest {

        @Test
        @DisplayName("인덱스 문 감지")
        fun testIsIndexStatement() {
            assertTrue(AdvancedIndexConverter.isIndexStatement("CREATE INDEX emp_idx ON employees (id);"))
            assertTrue(AdvancedIndexConverter.isIndexStatement("DROP INDEX emp_idx;"))
            assertTrue(AdvancedIndexConverter.isIndexStatement("ALTER INDEX emp_idx REBUILD;"))
            assertFalse(AdvancedIndexConverter.isIndexStatement("SELECT * FROM employees"))
        }
    }

    @Nested
    @DisplayName("IndexInfo 테스트")
    inner class IndexInfoTest {

        @Test
        @DisplayName("기본값 확인")
        fun testDefaultValues() {
            val info = AdvancedIndexConverter.IndexInfo()

            assertFalse(info.isUnique)
            assertFalse(info.isBitmap)
            assertFalse(info.isLocal)
            assertFalse(info.isGlobal)
            assertFalse(info.isReverse)
            assertFalse(info.isOnline)
            assertFalse(info.isInvisible)
        }
    }
}
