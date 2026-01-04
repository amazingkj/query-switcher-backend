package com.sqlswitcher.converter.feature.mview

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * MaterializedViewConverter 단위 테스트
 */
class MaterializedViewConverterTest {

    private fun convert(sql: String, source: DialectType, target: DialectType): Triple<String, List<ConversionWarning>, List<String>> {
        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()
        val result = MaterializedViewConverter.convert(sql, source, target, warnings, rules)
        return Triple(result, warnings, rules)
    }

    @Nested
    @DisplayName("CREATE MATERIALIZED VIEW 변환 테스트")
    inner class CreateMViewTest {

        @Test
        @DisplayName("Oracle → MySQL MView 에뮬레이션")
        fun testOracleToMySql() {
            val sql = """
                CREATE MATERIALIZED VIEW emp_summary
                BUILD IMMEDIATE
                REFRESH COMPLETE ON DEMAND
                AS
                SELECT department_id, COUNT(*) as cnt
                FROM employees
                GROUP BY department_id;
            """.trimIndent()

            val (result, warnings, rules) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("CREATE TABLE emp_summary AS"))
            assertTrue(result.contains("CREATE PROCEDURE emp_summary_refresh"))
            assertTrue(result.contains("TRUNCATE TABLE"))
            assertTrue(warnings.any { it.message.contains("네이티브로 지원") })
            assertTrue(rules.isNotEmpty())
        }

        @Test
        @DisplayName("Oracle → PostgreSQL 기본 MView")
        fun testOracleToPostgreSql() {
            val sql = """
                CREATE MATERIALIZED VIEW emp_summary
                BUILD IMMEDIATE
                REFRESH COMPLETE ON DEMAND
                AS
                SELECT department_id, COUNT(*) as cnt
                FROM employees
                GROUP BY department_id;
            """.trimIndent()

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("CREATE MATERIALIZED VIEW emp_summary AS"))
            assertTrue(result.contains("WITH DATA"))
        }

        @Test
        @DisplayName("Oracle ON COMMIT → PostgreSQL (경고)")
        fun testOnCommitToPostgreSql() {
            val sql = """
                CREATE MATERIALIZED VIEW emp_summary
                REFRESH FAST ON COMMIT
                AS
                SELECT department_id, COUNT(*) as cnt FROM employees GROUP BY department_id;
            """.trimIndent()

            val (result, warnings, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("CREATE MATERIALIZED VIEW"))
            assertTrue(warnings.any { it.message.contains("ON COMMIT") })
        }

        @Test
        @DisplayName("Oracle FAST REFRESH → PostgreSQL (경고)")
        fun testFastRefreshToPostgreSql() {
            val sql = """
                CREATE MATERIALIZED VIEW emp_summary
                REFRESH FAST ON DEMAND
                AS
                SELECT * FROM employees;
            """.trimIndent()

            val (_, warnings, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(warnings.any { it.message.contains("FAST") || it.message.contains("증분") })
        }

        @Test
        @DisplayName("PostgreSQL → Oracle MView")
        fun testPostgreSqlToOracle() {
            val sql = """
                CREATE MATERIALIZED VIEW emp_summary AS
                SELECT department_id, COUNT(*) as cnt
                FROM employees
                GROUP BY department_id
                WITH DATA;
            """.trimIndent()

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL, DialectType.ORACLE)

            assertTrue(result.contains("CREATE MATERIALIZED VIEW emp_summary"))
            assertTrue(result.contains("BUILD IMMEDIATE"))
            assertTrue(result.contains("REFRESH COMPLETE"))
        }

        @Test
        @DisplayName("PostgreSQL WITH NO DATA → Oracle BUILD DEFERRED")
        fun testWithNoDataToOracle() {
            val sql = """
                CREATE MATERIALIZED VIEW emp_summary AS
                SELECT * FROM employees
                WITH NO DATA;
            """.trimIndent()

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL, DialectType.ORACLE)

            assertTrue(result.contains("BUILD DEFERRED"))
        }

        @Test
        @DisplayName("스키마가 있는 MView")
        fun testSchemaQualifiedMView() {
            val sql = """
                CREATE MATERIALIZED VIEW hr.emp_summary AS
                SELECT department_id, COUNT(*) FROM employees GROUP BY department_id;
            """.trimIndent()

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("hr.emp_summary"))
        }

        @Test
        @DisplayName("Oracle Query Rewrite → PostgreSQL (경고)")
        fun testQueryRewrite() {
            val sql = """
                CREATE MATERIALIZED VIEW emp_summary
                ENABLE QUERY REWRITE
                AS SELECT * FROM employees;
            """.trimIndent()

            val (_, warnings, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(warnings.any { it.message.contains("Query Rewrite") })
        }
    }

    @Nested
    @DisplayName("DROP MATERIALIZED VIEW 변환 테스트")
    inner class DropMViewTest {

        @Test
        @DisplayName("Oracle → MySQL DROP MView")
        fun testDropMViewToMySql() {
            val sql = "DROP MATERIALIZED VIEW emp_summary;"

            val (result, _, rules) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("DROP TABLE IF EXISTS emp_summary"))
            assertTrue(result.contains("DROP PROCEDURE IF EXISTS emp_summary_refresh"))
            assertTrue(rules.isNotEmpty())
        }

        @Test
        @DisplayName("Oracle → PostgreSQL DROP MView")
        fun testDropMViewToPostgreSql() {
            val sql = "DROP MATERIALIZED VIEW emp_summary;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("DROP MATERIALIZED VIEW IF EXISTS"))
            assertTrue(result.contains("CASCADE"))
        }
    }

    @Nested
    @DisplayName("REFRESH MATERIALIZED VIEW 변환 테스트")
    inner class RefreshMViewTest {

        @Test
        @DisplayName("PostgreSQL REFRESH → MySQL 프로시저 호출")
        fun testRefreshToMySql() {
            val sql = "REFRESH MATERIALIZED VIEW emp_summary;"

            val (result, _, rules) = convert(sql, DialectType.POSTGRESQL, DialectType.MYSQL)

            assertTrue(result.contains("CALL emp_summary_refresh()"))
            assertTrue(rules.isNotEmpty())
        }

        @Test
        @DisplayName("PostgreSQL REFRESH → Oracle DBMS_MVIEW")
        fun testRefreshToOracle() {
            val sql = "REFRESH MATERIALIZED VIEW emp_summary;"

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL, DialectType.ORACLE)

            assertTrue(result.contains("DBMS_MVIEW.REFRESH"))
        }

        @Test
        @DisplayName("PostgreSQL REFRESH WITH NO DATA")
        fun testRefreshWithNoData() {
            val sql = "REFRESH MATERIALIZED VIEW emp_summary WITH NO DATA;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("WITH NO DATA"))
        }
    }

    @Nested
    @DisplayName("유틸리티 메서드 테스트")
    inner class UtilityMethodsTest {

        @Test
        @DisplayName("MView 문 감지")
        fun testIsMaterializedViewStatement() {
            assertTrue(MaterializedViewConverter.isMaterializedViewStatement("CREATE MATERIALIZED VIEW mv AS SELECT 1;"))
            assertTrue(MaterializedViewConverter.isMaterializedViewStatement("DROP MATERIALIZED VIEW mv;"))
            assertTrue(MaterializedViewConverter.isMaterializedViewStatement("REFRESH MATERIALIZED VIEW mv;"))
            assertFalse(MaterializedViewConverter.isMaterializedViewStatement("SELECT * FROM employees"))
            assertFalse(MaterializedViewConverter.isMaterializedViewStatement("CREATE VIEW v AS SELECT 1;"))
        }
    }

    @Nested
    @DisplayName("MViewInfo 테스트")
    inner class MViewInfoTest {

        @Test
        @DisplayName("기본값 확인")
        fun testDefaultValues() {
            val info = MaterializedViewConverter.MViewInfo()

            assertTrue(info.refreshType == MaterializedViewConverter.RefreshType.COMPLETE)
            assertTrue(info.refreshTiming == MaterializedViewConverter.RefreshTiming.ON_DEMAND)
            assertTrue(info.buildOption == MaterializedViewConverter.BuildOption.IMMEDIATE)
            assertFalse(info.queryRewriteEnabled)
            assertTrue(info.withData)
        }
    }
}
