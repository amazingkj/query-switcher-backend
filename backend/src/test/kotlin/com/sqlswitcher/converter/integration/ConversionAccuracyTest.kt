package com.sqlswitcher.converter.integration

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.feature.dblink.DatabaseLinkConverter
import com.sqlswitcher.converter.feature.index.AdvancedIndexConverter
import com.sqlswitcher.converter.feature.mview.MaterializedViewConverter
import com.sqlswitcher.converter.feature.sequence.AdvancedSequenceConverter
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * 변환 정확도 검증 테스트
 *
 * 다양한 SQL 패턴에 대한 변환 정확도를 검증합니다.
 */
class ConversionAccuracyTest {

    @Nested
    @DisplayName("시퀀스 변환 정확도")
    inner class SequenceAccuracyTest {

        @Test
        @DisplayName("Oracle → PostgreSQL 시퀀스 옵션 보존")
        fun testSequenceOptionsPreservation() {
            val sql = """
                CREATE SEQUENCE order_seq
                START WITH 1000
                INCREMENT BY 10
                MAXVALUE 999999999
                MINVALUE 1
                CACHE 100
                CYCLE;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)

            assertTrue(result.contains("START WITH 1000"))
            assertTrue(result.contains("INCREMENT BY 10"))
            assertTrue(result.contains("MAXVALUE 999999999"))
            assertTrue(result.contains("MINVALUE 1"))
            assertTrue(result.contains("CACHE 100"))
            assertTrue(result.contains("CYCLE"))
        }

        @Test
        @DisplayName("NEXTVAL/CURRVAL 일관성 있는 변환")
        fun testSequenceReferenceConsistency() {
            val sql = """
                INSERT INTO orders (order_id, order_date)
                VALUES (order_seq.NEXTVAL, SYSDATE);
                SELECT order_seq.CURRVAL FROM dual;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)

            assertTrue(result.contains("nextval('order_seq')"))
            assertTrue(result.contains("currval('order_seq')"))
            assertFalse(result.contains(".NEXTVAL"))
            assertFalse(result.contains(".CURRVAL"))
        }
    }

    @Nested
    @DisplayName("인덱스 변환 정확도")
    inner class IndexAccuracyTest {

        @Test
        @DisplayName("복합 인덱스 컬럼 순서 보존")
        fun testCompositeIndexColumnOrder() {
            val sql = "CREATE INDEX emp_comp_idx ON employees (department_id, job_id, salary DESC);"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedIndexConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)

            // 컬럼 순서가 보존되어야 함
            assertTrue(result.contains("department_id"))
            assertTrue(result.contains("job_id"))
            assertTrue(result.contains("salary"))
            assertTrue(result.indexOf("department_id") < result.indexOf("job_id"))
            assertTrue(result.indexOf("job_id") < result.indexOf("salary"))
        }

        @Test
        @DisplayName("함수 기반 인덱스 표현식 보존")
        fun testFunctionBasedIndexExpression() {
            val sql = "CREATE INDEX emp_name_idx ON employees (UPPER(last_name), LOWER(first_name));"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedIndexConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules)

            assertTrue(result.contains("UPPER"))
            assertTrue(result.contains("LOWER"))
            assertTrue(result.contains("last_name"))
            assertTrue(result.contains("first_name"))
        }
    }

    @Nested
    @DisplayName("Materialized View 변환 정확도")
    inner class MViewAccuracyTest {

        @Test
        @DisplayName("간단한 쿼리 보존")
        fun testSimpleQueryPreservation() {
            val sql = """
                CREATE MATERIALIZED VIEW emp_summary AS
                SELECT department_id, COUNT(*) as cnt FROM employees GROUP BY department_id;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = MaterializedViewConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)

            // 주요 쿼리 요소들이 보존되어야 함
            assertTrue(result.contains("emp_summary"))
            assertTrue(result.contains("department_id"))
            assertTrue(result.contains("employees"))
        }

        @Test
        @DisplayName("MySQL 에뮬레이션 구조 완전성")
        fun testMySqlEmulationCompleteness() {
            val sql = """
                CREATE MATERIALIZED VIEW daily_stats AS
                SELECT date, COUNT(*) as cnt FROM events GROUP BY date;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = MaterializedViewConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules)

            // 필수 구성 요소 확인
            assertTrue(result.contains("CREATE TABLE daily_stats"))
            assertTrue(result.contains("CREATE PROCEDURE daily_stats_refresh"))
            assertTrue(result.contains("TRUNCATE TABLE"))
            assertTrue(result.contains("INSERT INTO daily_stats"))
        }
    }

    @Nested
    @DisplayName("Database Link 변환 정확도")
    inner class DbLinkAccuracyTest {

        @Test
        @DisplayName("연결 정보 추출 정확도")
        fun testConnectionInfoExtraction() {
            val sql = """
                CREATE DATABASE LINK prod_db
                CONNECT TO app_user IDENTIFIED BY secret_pass
                USING '(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=prod.example.com)(PORT=1522))(CONNECT_DATA=(SERVICE_NAME=PRODDB)))'
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = DatabaseLinkConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)

            assertTrue(result.contains("prod.example.com"))
            assertTrue(result.contains("1522"))
            assertTrue(result.contains("PRODDB"))
            assertTrue(result.contains("app_user"))
        }

        @Test
        @DisplayName("@dblink 참조 일괄 변환")
        fun testBatchDbLinkReferenceConversion() {
            val sql = """
                SELECT
                    e.employee_id,
                    e.name,
                    d.department_name,
                    s.salary
                FROM employees@hr_remote e
                JOIN departments@hr_remote d ON e.dept_id = d.dept_id
                JOIN salaries@payroll_remote s ON e.employee_id = s.emp_id
                WHERE e.status = 'ACTIVE';
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = DatabaseLinkConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)

            // 모든 @dblink 참조가 변환되어야 함
            assertFalse(result.contains("@hr_remote"))
            assertFalse(result.contains("@payroll_remote"))
            assertTrue(result.contains("hr_remote_employees"))
            assertTrue(result.contains("hr_remote_departments"))
            assertTrue(result.contains("payroll_remote_salaries"))
        }
    }

    @Nested
    @DisplayName("경계 케이스 테스트")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("빈 SQL 처리")
        fun testEmptySql() {
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result1 = AdvancedSequenceConverter.convert("", DialectType.ORACLE, DialectType.MYSQL, warnings, rules)
            val result2 = AdvancedIndexConverter.convert("", DialectType.ORACLE, DialectType.MYSQL, warnings, rules)

            assertTrue(result1.isEmpty())
            assertTrue(result2.isEmpty())
        }

        @Test
        @DisplayName("변환 대상 없는 SQL")
        fun testNoConversionNeeded() {
            val sql = "SELECT id, name FROM users WHERE active = 1;"
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = AdvancedSequenceConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules)

            // 변환 대상이 없으면 원본 그대로
            assertTrue(result == sql)
        }

        @Test
        @DisplayName("대소문자 혼합 키워드")
        fun testMixedCaseKeywords() {
            val sql = "Create Sequence Test_Seq Start With 1 Increment By 1;"
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = AdvancedSequenceConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)

            assertTrue(result.contains("CREATE SEQUENCE"))
            assertTrue(result.contains("Test_Seq") || result.contains("test_seq"))
        }

        @Test
        @DisplayName("특수 문자가 포함된 식별자")
        fun testSpecialCharacterIdentifiers() {
            val sql = "CREATE INDEX emp_idx ON employees (last_name);"
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = AdvancedIndexConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules)

            assertTrue(result.contains("emp_idx"))
            assertTrue(result.contains("last_name"))
        }
    }

    @Nested
    @DisplayName("왕복 변환 테스트")
    inner class RoundTripTest {

        @Test
        @DisplayName("시퀀스 Oracle → PostgreSQL → Oracle 왕복")
        fun testSequenceRoundTrip() {
            val originalSql = "CREATE SEQUENCE test_seq START WITH 100 INCREMENT BY 10 MAXVALUE 99999 NOCYCLE;"
            val warnings1 = mutableListOf<ConversionWarning>()
            val rules1 = mutableListOf<String>()

            // Oracle → PostgreSQL
            val pgResult = AdvancedSequenceConverter.convert(originalSql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings1, rules1)

            val warnings2 = mutableListOf<ConversionWarning>()
            val rules2 = mutableListOf<String>()

            // PostgreSQL → Oracle
            val oracleResult = AdvancedSequenceConverter.convert(pgResult, DialectType.POSTGRESQL, DialectType.ORACLE, warnings2, rules2)

            // 핵심 옵션들이 보존되어야 함
            assertTrue(oracleResult.contains("test_seq"))
            assertTrue(oracleResult.contains("START WITH 100"))
            assertTrue(oracleResult.contains("INCREMENT BY 10"))
            assertTrue(oracleResult.contains("MAXVALUE 99999"))
        }
    }
}
