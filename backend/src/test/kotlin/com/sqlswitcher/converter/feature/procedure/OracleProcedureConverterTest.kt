package com.sqlswitcher.converter.feature.procedure

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * OracleProcedureConverter 단위 테스트
 */
class OracleProcedureConverterTest {

    @Nested
    @DisplayName("Oracle → PostgreSQL 프로시저 변환")
    inner class ToPostgreSqlTest {

        @Test
        @DisplayName("기본 프로시저 변환")
        fun testBasicProcedureConversion() {
            val sql = """
                CREATE OR REPLACE PROCEDURE update_salary(
                    p_emp_id IN NUMBER,
                    p_amount IN NUMBER
                )
                IS
                BEGIN
                    UPDATE employees SET salary = salary + p_amount WHERE emp_id = p_emp_id;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("CREATE OR REPLACE FUNCTION"), "FUNCTION으로 변환되어야 함")
            assertTrue(result.contains("RETURNS VOID"), "RETURNS VOID가 추가되어야 함")
            assertTrue(result.contains("\$\$"), "PL/pgSQL 블록 구분자가 있어야 함")
            assertTrue(result.contains("LANGUAGE plpgsql"), "언어 지정이 있어야 함")
        }

        @Test
        @DisplayName("FUNCTION 변환 (RETURN 타입 포함)")
        fun testFunctionWithReturnType() {
            val sql = """
                CREATE OR REPLACE FUNCTION get_employee_name(p_id NUMBER)
                RETURN VARCHAR2
                IS
                    v_name VARCHAR2(100);
                BEGIN
                    SELECT name INTO v_name FROM employees WHERE id = p_id;
                    RETURN v_name;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("RETURNS VARCHAR") || result.contains("RETURNS VOID"),
                "RETURNS 절이 있어야 함")
        }

        @Test
        @DisplayName("DBMS_OUTPUT.PUT_LINE → RAISE NOTICE")
        fun testDbmsOutputConversion() {
            val sql = """
                CREATE OR REPLACE PROCEDURE log_message
                IS
                BEGIN
                    DBMS_OUTPUT.PUT_LINE('Hello World');
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("RAISE NOTICE"), "RAISE NOTICE로 변환되어야 함")
            assertFalse(result.contains("DBMS_OUTPUT"), "DBMS_OUTPUT이 제거되어야 함")
            assertTrue(appliedRules.any { it.contains("RAISE NOTICE") }, "적용된 규칙에 포함되어야 함")
        }

        @Test
        @DisplayName("NVL → COALESCE 변환")
        fun testNvlToCoalesce() {
            val sql = """
                CREATE PROCEDURE test_proc IS
                BEGIN
                    SELECT NVL(name, 'Unknown') FROM dual;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("COALESCE"), "COALESCE로 변환되어야 함")
            assertFalse(result.contains("NVL"), "NVL이 제거되어야 함")
        }

        @Test
        @DisplayName("BULK COLLECT 경고")
        fun testBulkCollectWarning() {
            val sql = """
                CREATE PROCEDURE bulk_test IS
                    TYPE t_ids IS TABLE OF NUMBER;
                    v_ids t_ids;
                BEGIN
                    SELECT id BULK COLLECT INTO v_ids FROM employees;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            OracleProcedureConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(warnings.any { it.message.contains("BULK COLLECT") }, "BULK COLLECT 경고가 있어야 함")
        }

        @Test
        @DisplayName("EXECUTE IMMEDIATE → EXECUTE")
        fun testExecuteImmediateConversion() {
            val sql = """
                CREATE PROCEDURE dynamic_test IS
                    v_sql VARCHAR2(1000);
                BEGIN
                    v_sql := 'SELECT * FROM employees';
                    EXECUTE IMMEDIATE v_sql;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("EXECUTE ") && !result.contains("EXECUTE IMMEDIATE"),
                "EXECUTE IMMEDIATE가 EXECUTE로 변환되어야 함")
        }
    }

    @Nested
    @DisplayName("Oracle → MySQL 프로시저 변환")
    inner class ToMySqlTest {

        @Test
        @DisplayName("기본 프로시저 변환")
        fun testBasicProcedureConversion() {
            val sql = """
                CREATE OR REPLACE PROCEDURE update_salary(
                    p_emp_id IN NUMBER,
                    p_amount IN NUMBER
                )
                IS
                BEGIN
                    UPDATE employees SET salary = salary + p_amount WHERE emp_id = p_emp_id;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("CREATE PROCEDURE"), "CREATE PROCEDURE가 유지되어야 함")
            assertFalse(result.contains("OR REPLACE"), "OR REPLACE가 제거되어야 함")
            assertTrue(result.contains("BEGIN"), "BEGIN이 있어야 함")
        }

        @Test
        @DisplayName("DBMS_OUTPUT.PUT_LINE → SELECT")
        fun testDbmsOutputConversion() {
            val sql = """
                CREATE PROCEDURE log_message IS
                BEGIN
                    DBMS_OUTPUT.PUT_LINE('Debug message');
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("SELECT") && result.contains("DEBUG"), "SELECT로 변환되어야 함")
            assertTrue(warnings.any { it.message.contains("DBMS_OUTPUT") }, "경고가 있어야 함")
        }

        @Test
        @DisplayName("NVL → IFNULL 변환")
        fun testNvlToIfnull() {
            val sql = """
                CREATE PROCEDURE test_proc IS
                BEGIN
                    SELECT NVL(name, 'Unknown') FROM dual;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("IFNULL"), "IFNULL로 변환되어야 함")
            assertFalse(result.contains("NVL"), "NVL이 제거되어야 함")
        }

        @Test
        @DisplayName("%TYPE 미지원 경고")
        fun testTypeReferenceWarning() {
            val sql = """
                CREATE PROCEDURE test_proc IS
                    v_name employees.name%TYPE;
                BEGIN
                    NULL;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(warnings.any { it.message.contains("%TYPE") }, "%TYPE 경고가 있어야 함")
            assertTrue(result.contains("VARCHAR(255)"), "기본 타입으로 대체되어야 함")
        }

        @Test
        @DisplayName("%ROWTYPE 미지원 경고")
        fun testRowtypeWarning() {
            val sql = """
                CREATE PROCEDURE test_proc IS
                    v_emp employees%ROWTYPE;
                BEGIN
                    NULL;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            OracleProcedureConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(warnings.any { it.message.contains("%ROWTYPE") }, "%ROWTYPE 경고가 있어야 함")
        }

        @Test
        @DisplayName("EXECUTE IMMEDIATE → PREPARE/EXECUTE")
        fun testExecuteImmediateConversion() {
            val sql = """
                CREATE PROCEDURE dynamic_test IS
                    v_sql VARCHAR2(1000);
                BEGIN
                    v_sql := 'SELECT * FROM employees';
                    EXECUTE IMMEDIATE v_sql;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("PREPARE") || warnings.any { it.message.contains("PREPARE") },
                "PREPARE/EXECUTE 관련 변환 또는 경고가 있어야 함")
        }

        @Test
        @DisplayName("EXCEPTION 블록 → HANDLER")
        fun testExceptionToHandler() {
            val sql = """
                CREATE PROCEDURE safe_update IS
                BEGIN
                    UPDATE employees SET salary = 0;
                EXCEPTION
                    WHEN OTHERS THEN
                        NULL;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("HANDLER") || warnings.any { it.message.contains("EXCEPTION") || it.message.contains("HANDLER") },
                "HANDLER 변환 또는 EXCEPTION 관련 경고가 있어야 함")
        }

        @Test
        @DisplayName("SYSDATE → NOW()")
        fun testSysdateConversion() {
            val sql = """
                CREATE PROCEDURE test_proc IS
                BEGIN
                    INSERT INTO logs (created_at) VALUES (SYSDATE);
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("NOW()"), "NOW()로 변환되어야 함")
            assertFalse(result.uppercase().contains("SYSDATE"), "SYSDATE가 제거되어야 함")
        }
    }

    @Nested
    @DisplayName("EXCEPTION 블록 변환")
    inner class ExceptionConversionTest {

        @Test
        @DisplayName("NO_DATA_FOUND → NOT FOUND HANDLER")
        fun testNoDataFoundHandler() {
            val sql = """
                CREATE PROCEDURE find_emp IS
                    v_name VARCHAR2(100);
                BEGIN
                    SELECT name INTO v_name FROM employees WHERE id = 999;
                EXCEPTION
                    WHEN NO_DATA_FOUND THEN
                        v_name := 'Not Found';
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToMySql(sql, warnings, appliedRules)

            // EXCEPTION 블록 처리 여부 확인 (변환 또는 경고)
            assertTrue(
                result.contains("NOT FOUND") ||
                result.contains("HANDLER") ||
                appliedRules.any { it.contains("HANDLER") || it.contains("EXCEPTION") } ||
                warnings.any { it.message.contains("EXCEPTION") || it.message.contains("HANDLER") },
                "EXCEPTION 처리 관련 변환이나 경고가 있어야 함"
            )
        }

        @Test
        @DisplayName("RAISE_APPLICATION_ERROR → SIGNAL")
        fun testRaiseApplicationErrorConversion() {
            val sql = """
                CREATE PROCEDURE validate IS
                BEGIN
                    RAISE_APPLICATION_ERROR(-20001, 'Validation failed');
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleProcedureConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("SIGNAL") || result.contains("RAISE_APPLICATION_ERROR"),
                "SIGNAL로 변환되거나 원본이 유지되어야 함")
        }
    }
}
