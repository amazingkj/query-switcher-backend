package com.sqlswitcher.converter.feature.flashback

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * FlashbackQueryConverter 테스트
 */
class FlashbackQueryConverterTest {

    @Nested
    @DisplayName("FLASHBACK 구문 감지 테스트")
    inner class DetectionTest {

        @Test
        @DisplayName("AS OF SCN 감지")
        fun testDetectAsOfScn() {
            val sql = "SELECT * FROM employees AS OF SCN 12345678"
            assertTrue(FlashbackQueryConverter.hasFlashbackSyntax(sql))
        }

        @Test
        @DisplayName("AS OF TIMESTAMP 감지")
        fun testDetectAsOfTimestamp() {
            val sql = """
                SELECT * FROM employees
                AS OF TIMESTAMP TO_TIMESTAMP('2024-01-01 10:00:00', 'YYYY-MM-DD HH24:MI:SS')
            """.trimIndent()
            assertTrue(FlashbackQueryConverter.hasFlashbackSyntax(sql))
        }

        @Test
        @DisplayName("VERSIONS BETWEEN 감지")
        fun testDetectVersionsBetween() {
            val sql = """
                SELECT * FROM employees
                VERSIONS BETWEEN TIMESTAMP SYSTIMESTAMP - INTERVAL '1' HOUR AND SYSTIMESTAMP
            """.trimIndent()
            assertTrue(FlashbackQueryConverter.hasFlashbackSyntax(sql))
        }

        @Test
        @DisplayName("FLASHBACK TABLE 감지")
        fun testDetectFlashbackTable() {
            val sql = "FLASHBACK TABLE employees TO BEFORE DROP"
            assertTrue(FlashbackQueryConverter.hasFlashbackSyntax(sql))
        }

        @Test
        @DisplayName("ORA_ROWSCN 감지")
        fun testDetectOraRowscn() {
            val sql = "SELECT employee_id, ORA_ROWSCN FROM employees"
            assertTrue(FlashbackQueryConverter.hasFlashbackSyntax(sql))
        }

        @Test
        @DisplayName("DBMS_FLASHBACK 감지")
        fun testDetectDbmsFlashback() {
            val sql = "CALL DBMS_FLASHBACK.ENABLE_AT_TIME(TO_TIMESTAMP('2024-01-01', 'YYYY-MM-DD'))"
            assertTrue(FlashbackQueryConverter.hasFlashbackSyntax(sql))
        }

        @Test
        @DisplayName("SCN_TO_TIMESTAMP 감지")
        fun testDetectScnToTimestamp() {
            val sql = "SELECT SCN_TO_TIMESTAMP(12345678) FROM dual"
            assertTrue(FlashbackQueryConverter.hasFlashbackSyntax(sql))
        }

        @Test
        @DisplayName("일반 SQL - FLASHBACK 구문 없음")
        fun testNoFlashbackSyntax() {
            val sql = "SELECT * FROM employees WHERE department_id = 10"
            assertFalse(FlashbackQueryConverter.hasFlashbackSyntax(sql))
        }
    }

    @Nested
    @DisplayName("Oracle → MySQL 변환 테스트")
    inner class OracleToMySqlTest {

        @Test
        @DisplayName("AS OF TIMESTAMP → 주석 처리")
        fun testAsOfTimestampToMySql() {
            val sql = """
                SELECT * FROM employees
                AS OF TIMESTAMP TO_TIMESTAMP('2024-01-01 10:00:00', 'YYYY-MM-DD HH24:MI:SS')
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("/*"))
            assertTrue(result.contains("*/"))
            assertTrue(warnings.any { it.message.contains("AS OF TIMESTAMP") })
        }

        @Test
        @DisplayName("AS OF SCN → 주석 처리")
        fun testAsOfScnToMySql() {
            val sql = "SELECT * FROM employees AS OF SCN 12345678"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("/*"))
            assertTrue(warnings.any { it.message.contains("SCN") })
        }

        @Test
        @DisplayName("ORA_ROWSCN → NULL 대체")
        fun testOraRowscnToMySql() {
            val sql = "SELECT employee_id, ORA_ROWSCN FROM employees"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("NULL"))
            assertFalse(result.contains("ORA_ROWSCN") && !result.contains("/*"))
        }

        @Test
        @DisplayName("FLASHBACK TABLE TO BEFORE DROP → 주석 처리")
        fun testFlashbackTableBeforeDropToMySql() {
            val sql = "FLASHBACK TABLE employees TO BEFORE DROP"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("--"))
            assertTrue(warnings.any { it.message.contains("BEFORE DROP") })
            assertTrue(warnings.any { it.severity == WarningSeverity.ERROR })
        }

        @Test
        @DisplayName("FLASHBACK TABLE TO TIMESTAMP → 주석 처리")
        fun testFlashbackTableTimestampToMySql() {
            val sql = "FLASHBACK TABLE employees TO TIMESTAMP TO_TIMESTAMP('2024-01-01', 'YYYY-MM-DD')"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("--"))
            assertTrue(warnings.any { it.severity == WarningSeverity.ERROR })
        }

        @Test
        @DisplayName("DBMS_FLASHBACK → 주석 처리")
        fun testDbmsFlashbackToMySql() {
            val sql = "BEGIN DBMS_FLASHBACK.ENABLE_AT_TIME(SYSDATE - 1); END;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("/*"))
            assertTrue(result.contains("NULL"))
            assertTrue(warnings.any { it.message.contains("DBMS_FLASHBACK") })
        }

        @Test
        @DisplayName("SCN_TO_TIMESTAMP → NULL 대체")
        fun testScnToTimestampToMySql() {
            val sql = "SELECT SCN_TO_TIMESTAMP(12345678) FROM dual"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("NULL"))
            assertTrue(result.contains("/*"))
        }

        @Test
        @DisplayName("TIMESTAMP_TO_SCN → NULL 대체")
        fun testTimestampToScnToMySql() {
            val sql = "SELECT TIMESTAMP_TO_SCN(SYSDATE) FROM dual"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("NULL"))
        }

        @Test
        @DisplayName("VERSIONS BETWEEN → 주석 처리")
        fun testVersionsBetweenToMySql() {
            val sql = """
                SELECT employee_id, salary
                FROM employees VERSIONS BETWEEN TIMESTAMP SYSTIMESTAMP - INTERVAL '1' HOUR AND SYSTIMESTAMP
                WHERE employee_id = 100
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("/*"))
            assertTrue(warnings.any { it.message.contains("VERSIONS BETWEEN") })
        }
    }

    @Nested
    @DisplayName("Oracle → PostgreSQL 변환 테스트")
    inner class OracleToPostgreSqlTest {

        @Test
        @DisplayName("AS OF TIMESTAMP → temporal_tables 안내")
        fun testAsOfTimestampToPostgreSql() {
            val sql = """
                SELECT * FROM employees
                AS OF TIMESTAMP TO_TIMESTAMP('2024-01-01 10:00:00', 'YYYY-MM-DD HH24:MI:SS')
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("/*"))
            assertTrue(warnings.any {
                it.message.contains("temporal_tables") || it.suggestion?.contains("temporal_tables") == true
            })
        }

        @Test
        @DisplayName("ORA_ROWSCN → xmin 변환")
        fun testOraRowscnToPostgreSql() {
            val sql = "SELECT employee_id, ORA_ROWSCN FROM employees"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("xmin"))
            assertFalse(result.contains("ORA_ROWSCN"))
        }

        @Test
        @DisplayName("DBMS_FLASHBACK.GET_SYSTEM_CHANGE_NUMBER → pg_current_xact_id")
        fun testDbmsFlashbackGetScnToPostgreSql() {
            val sql = "SELECT DBMS_FLASHBACK.GET_SYSTEM_CHANGE_NUMBER() FROM dual"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("pg_current_xact_id"))
        }

        @Test
        @DisplayName("SCN_TO_TIMESTAMP → pg_xact_commit_timestamp")
        fun testScnToTimestampToPostgreSql() {
            val sql = "SELECT SCN_TO_TIMESTAMP(12345678) FROM dual"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("pg_xact_commit_timestamp"))
            assertTrue(warnings.any {
                it.suggestion?.contains("track_commit_timestamp") == true
            })
        }

        @Test
        @DisplayName("FLASHBACK TABLE TO BEFORE DROP → 주석 처리")
        fun testFlashbackTableBeforeDropToPostgreSql() {
            val sql = "FLASHBACK TABLE employees TO BEFORE DROP"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("--"))
            assertTrue(warnings.any { it.message.contains("BEFORE DROP") })
        }

        @Test
        @DisplayName("VERSIONS BETWEEN → temporal_tables 안내")
        fun testVersionsBetweenToPostgreSql() {
            val sql = """
                SELECT * FROM employees
                VERSIONS BETWEEN SCN 12345 AND 67890
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("/*"))
            assertTrue(result.contains("SYSTEM_TIME") || warnings.any {
                it.message.contains("temporal_tables") || it.suggestion?.contains("temporal_tables") == true
            })
        }

        @Test
        @DisplayName("FLASHBACK ARCHIVE → temporal_tables 안내")
        fun testFlashbackArchiveToPostgreSql() {
            val sql = "ALTER TABLE employees FLASHBACK ARCHIVE fla_1year"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("/*"))
            assertTrue(warnings.any {
                it.message.contains("temporal_tables") || it.suggestion?.contains("temporal_tables") == true
            })
        }
    }

    @Nested
    @DisplayName("경계 케이스 테스트")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("FLASHBACK 구문 없는 SQL - 변환 없음")
        fun testNoFlashbackSql() {
            val sql = "SELECT * FROM employees WHERE department_id = 10"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertEquals(sql, result)
            assertTrue(warnings.isEmpty())
        }

        @Test
        @DisplayName("동일 방언 변환 - 원본 유지")
        fun testSameDialect() {
            val sql = "SELECT * FROM employees AS OF SCN 12345678"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.ORACLE, warnings, rules
            )

            assertEquals(sql, result)
        }

        @Test
        @DisplayName("비 Oracle 소스 - 변환 없음")
        fun testNonOracleSource() {
            val sql = "SELECT * FROM employees AS OF SCN 12345678"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.MYSQL, DialectType.POSTGRESQL, warnings, rules
            )

            assertEquals(sql, result)
        }

        @Test
        @DisplayName("복합 FLASHBACK 쿼리")
        fun testComplexFlashbackQuery() {
            val sql = """
                SELECT e.employee_id, e.salary, ORA_ROWSCN,
                       SCN_TO_TIMESTAMP(ORA_ROWSCN) as last_modified
                FROM employees AS OF TIMESTAMP SYSTIMESTAMP - INTERVAL '1' DAY e
                WHERE e.department_id = 10
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            // ORA_ROWSCN이 xmin으로 변환됨
            assertTrue(result.contains("xmin"))
            // SCN_TO_TIMESTAMP가 pg_xact_commit_timestamp로 변환됨
            assertTrue(result.contains("pg_xact_commit_timestamp"))
            // 여러 경고 발생
            assertTrue(warnings.size >= 2)
        }

        @Test
        @DisplayName("파라미터 바인딩이 있는 AS OF SCN")
        fun testAsOfScnWithBindVariable() {
            val sql = "SELECT * FROM employees AS OF SCN :scn_value"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("/*"))
            assertTrue(warnings.isNotEmpty())
        }
    }
}
