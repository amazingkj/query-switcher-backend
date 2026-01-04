package com.sqlswitcher.converter.feature.plsql

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * OraclePackageConverter 단위 테스트
 */
class OraclePackageConverterTest {

    @Nested
    @DisplayName("패키지 스펙 변환 테스트")
    inner class PackageSpecTest {

        @Test
        @DisplayName("기본 패키지 스펙 → MySQL 주석 변환")
        fun testBasicSpecToMySql() {
            val sql = """
                CREATE OR REPLACE PACKAGE emp_pkg AS
                    FUNCTION get_salary(p_emp_id NUMBER) RETURN NUMBER;
                    PROCEDURE update_salary(p_emp_id NUMBER, p_amount NUMBER);
                END emp_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("--"), "주석이 포함되어야 함")
            assertTrue(result.contains("emp_pkg"), "패키지명이 포함되어야 함")
            assertTrue(warnings.isNotEmpty(), "경고가 있어야 함")
            assertTrue(appliedRules.any { it.contains("패키지 스펙") }, "적용된 규칙에 포함되어야 함")
        }

        @Test
        @DisplayName("상수 포함 패키지 스펙 → MySQL 함수")
        fun testSpecWithConstantsToMySql() {
            val sql = """
                CREATE PACKAGE config_pkg AS
                    MAX_RETRIES CONSTANT NUMBER := 3;
                    DEFAULT_TIMEOUT CONSTANT NUMBER := 30;
                END config_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("CREATE FUNCTION"), "상수가 함수로 변환되어야 함")
            assertTrue(result.contains("config_pkg_MAX_RETRIES"), "패키지_상수명 형식이어야 함")
            assertTrue(result.contains("RETURN 3"), "상수값이 반환되어야 함")
        }

        @Test
        @DisplayName("패키지 스펙 → PostgreSQL 스키마")
        fun testSpecToPostgreSql() {
            val sql = """
                CREATE OR REPLACE PACKAGE emp_pkg AS
                    FUNCTION get_name(p_id NUMBER) RETURN VARCHAR2;
                END emp_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("CREATE SCHEMA"), "스키마가 생성되어야 함")
            assertTrue(result.contains("emp_pkg"), "패키지명 스키마가 있어야 함")
        }

        @Test
        @DisplayName("상수 → PostgreSQL IMMUTABLE 함수")
        fun testConstantsToPostgreSqlImmutable() {
            val sql = """
                CREATE PACKAGE math_pkg AS
                    PI CONSTANT NUMBER := 3.14159;
                END math_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("IMMUTABLE"), "IMMUTABLE 함수로 생성되어야 함")
            assertTrue(result.contains("math_pkg"), "스키마명이 포함되어야 함")
        }
    }

    @Nested
    @DisplayName("패키지 바디 변환 테스트")
    inner class PackageBodyTest {

        @Test
        @DisplayName("기본 패키지 바디 → MySQL 개별 함수")
        fun testBasicBodyToMySql() {
            val sql = """
                CREATE OR REPLACE PACKAGE BODY emp_pkg AS
                    FUNCTION get_salary(p_emp_id IN NUMBER) RETURN NUMBER IS
                        v_salary NUMBER;
                    BEGIN
                        SELECT salary INTO v_salary FROM employees WHERE emp_id = p_emp_id;
                        RETURN v_salary;
                    END get_salary;
                END emp_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("CREATE FUNCTION emp_pkg_get_salary"), "접두사가 붙은 함수명이어야 함")
            assertTrue(result.contains("DELIMITER"), "DELIMITER가 있어야 함")
            assertTrue(result.contains("RETURNS"), "RETURNS가 있어야 함")
        }

        @Test
        @DisplayName("프로시저 포함 패키지 바디 → MySQL")
        fun testBodyWithProcedureToMySql() {
            val sql = """
                CREATE PACKAGE BODY hr_pkg AS
                    PROCEDURE update_salary(p_emp_id IN NUMBER, p_amount IN NUMBER) IS
                    BEGIN
                        UPDATE employees SET salary = salary + p_amount WHERE emp_id = p_emp_id;
                    END update_salary;
                END hr_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("CREATE PROCEDURE hr_pkg_update_salary"), "접두사가 붙은 프로시저명이어야 함")
        }

        @Test
        @DisplayName("패키지 바디 → PostgreSQL 스키마 내 함수")
        fun testBodyToPostgreSql() {
            val sql = """
                CREATE OR REPLACE PACKAGE BODY calc_pkg AS
                    FUNCTION add_numbers(a IN NUMBER, b IN NUMBER) RETURN NUMBER IS
                    BEGIN
                        RETURN a + b;
                    END add_numbers;
                END calc_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("CREATE SCHEMA IF NOT EXISTS calc_pkg"), "스키마가 생성되어야 함")
            assertTrue(result.contains("CREATE OR REPLACE FUNCTION calc_pkg.add_numbers"), "스키마.함수명 형식이어야 함")
            assertTrue(result.contains("LANGUAGE plpgsql"), "PL/pgSQL 언어 지정이 있어야 함")
        }

        @Test
        @DisplayName("SYSDATE → NOW()/CURRENT_TIMESTAMP 변환")
        fun testSysdateConversion() {
            val sql = """
                CREATE PACKAGE BODY log_pkg AS
                    PROCEDURE log_event(p_msg IN VARCHAR2) IS
                    BEGIN
                        INSERT INTO logs (msg, created_at) VALUES (p_msg, SYSDATE);
                    END log_event;
                END log_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val resultMySql = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )
            assertTrue(resultMySql.contains("NOW()"), "MySQL: SYSDATE → NOW()")

            warnings.clear()
            appliedRules.clear()

            val resultPg = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )
            assertTrue(resultPg.contains("CURRENT_TIMESTAMP"), "PostgreSQL: SYSDATE → CURRENT_TIMESTAMP")
        }

        @Test
        @DisplayName("NVL → IFNULL/COALESCE 변환")
        fun testNvlConversion() {
            val sql = """
                CREATE PACKAGE BODY util_pkg AS
                    FUNCTION safe_value(p_val IN VARCHAR2) RETURN VARCHAR2 IS
                    BEGIN
                        RETURN NVL(p_val, 'DEFAULT');
                    END safe_value;
                END util_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val resultMySql = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )
            assertTrue(resultMySql.contains("IFNULL("), "MySQL: NVL → IFNULL")

            warnings.clear()
            appliedRules.clear()

            val resultPg = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )
            assertTrue(resultPg.contains("COALESCE("), "PostgreSQL: NVL → COALESCE")
        }

        @Test
        @DisplayName("DBMS_OUTPUT.PUT_LINE 변환")
        fun testDbmsOutputConversion() {
            val sql = """
                CREATE PACKAGE BODY debug_pkg AS
                    PROCEDURE print_msg(p_msg IN VARCHAR2) IS
                    BEGIN
                        DBMS_OUTPUT.PUT_LINE(p_msg);
                    END print_msg;
                END debug_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val resultPg = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )
            assertTrue(resultPg.contains("RAISE NOTICE"), "PostgreSQL: DBMS_OUTPUT → RAISE NOTICE")
        }

        @Test
        @DisplayName("패키지 변수 → 테이블 변환")
        fun testPackageVariablesToTable() {
            val sql = """
                CREATE PACKAGE BODY state_pkg AS
                    g_counter NUMBER := 0;

                    FUNCTION get_counter RETURN NUMBER IS
                    BEGIN
                        RETURN g_counter;
                    END get_counter;
                END state_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            // 이 테스트는 변수 파싱이 더 복잡해질 수 있음
            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("state_pkg"), "패키지명이 포함되어야 함")
        }

        @Test
        @DisplayName("데이터 타입 변환 - VARCHAR2, NUMBER")
        fun testDataTypeConversion() {
            val sql = """
                CREATE PACKAGE BODY type_pkg AS
                    FUNCTION process(p_name IN VARCHAR2, p_value IN NUMBER) RETURN VARCHAR2 IS
                    BEGIN
                        RETURN p_name;
                    END process;
                END type_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val resultMySql = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )
            assertTrue(resultMySql.contains("VARCHAR") && !resultMySql.contains("VARCHAR2"),
                "MySQL: VARCHAR2 → VARCHAR")
            assertTrue(resultMySql.contains("DECIMAL") || resultMySql.contains("NUMBER"),
                "MySQL: NUMBER → DECIMAL")

            warnings.clear()
            appliedRules.clear()

            val resultPg = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )
            assertTrue(resultPg.contains("VARCHAR") && !resultPg.contains("VARCHAR2"),
                "PostgreSQL: VARCHAR2 → VARCHAR")
        }
    }

    @Nested
    @DisplayName("유틸리티 메서드 테스트")
    inner class UtilityTest {

        @Test
        @DisplayName("패키지 이름 추출 - 스펙")
        fun testExtractPackageNameFromSpec() {
            val sql = "CREATE OR REPLACE PACKAGE my_package AS END my_package;"

            val name = OraclePackageConverter.extractPackageName(sql)

            assertEquals("my_package", name)
        }

        @Test
        @DisplayName("패키지 이름 추출 - 바디")
        fun testExtractPackageNameFromBody() {
            val sql = "CREATE PACKAGE BODY another_pkg AS END another_pkg;"

            val name = OraclePackageConverter.extractPackageName(sql)

            assertEquals("another_pkg", name)
        }

        @Test
        @DisplayName("패키지 타입 확인 - SPECIFICATION")
        fun testGetPackageTypeSpec() {
            val sql = "CREATE PACKAGE test_pkg AS END test_pkg;"

            val type = OraclePackageConverter.getPackageType(sql)

            assertEquals(OraclePackageConverter.PackageType.SPECIFICATION, type)
        }

        @Test
        @DisplayName("패키지 타입 확인 - BODY")
        fun testGetPackageTypeBody() {
            val sql = "CREATE PACKAGE BODY test_pkg AS END test_pkg;"

            val type = OraclePackageConverter.getPackageType(sql)

            assertEquals(OraclePackageConverter.PackageType.BODY, type)
        }

        @Test
        @DisplayName("패키지가 아닌 SQL")
        fun testNonPackageSql() {
            val sql = "SELECT * FROM employees"

            val type = OraclePackageConverter.getPackageType(sql)

            assertEquals(null, type)
        }
    }

    @Nested
    @DisplayName("복합 패키지 변환 테스트")
    inner class ComplexPackageTest {

        @Test
        @DisplayName("여러 함수/프로시저가 포함된 패키지")
        fun testMultipleFunctionsAndProcedures() {
            val sql = """
                CREATE PACKAGE BODY complex_pkg AS
                    FUNCTION func1(p_a NUMBER) RETURN NUMBER IS
                    BEGIN
                        RETURN p_a * 2;
                    END func1;

                    FUNCTION func2(p_b VARCHAR2) RETURN VARCHAR2 IS
                    BEGIN
                        RETURN 'Hello ' || p_b;
                    END func2;

                    PROCEDURE proc1(p_c IN NUMBER) IS
                    BEGIN
                        DBMS_OUTPUT.PUT_LINE(p_c);
                    END proc1;
                END complex_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 3개의 함수/프로시저가 생성되어야 함
            val functionCount = Regex("CREATE FUNCTION").findAll(result).count()
            val procedureCount = Regex("CREATE PROCEDURE").findAll(result).count()

            assertTrue(functionCount == 2, "2개의 함수가 생성되어야 함")
            assertTrue(procedureCount == 1, "1개의 프로시저가 생성되어야 함")
        }

        @Test
        @DisplayName("스키마 접두사가 있는 패키지")
        fun testPackageWithSchema() {
            val sql = """
                CREATE PACKAGE BODY hr.employee_pkg AS
                    FUNCTION get_count RETURN NUMBER IS
                    BEGIN
                        RETURN 0;
                    END get_count;
                END employee_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("employee_pkg"), "패키지명이 포함되어야 함")
        }
    }

    @Nested
    @DisplayName("엣지 케이스 테스트")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("Oracle이 아닌 소스는 변환하지 않음")
        fun testNonOracleSourceUnchanged() {
            val sql = "CREATE PACKAGE test AS END test;"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.MYSQL, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertEquals(sql, result)
        }

        @Test
        @DisplayName("패키지가 아닌 SQL은 그대로 반환")
        fun testNonPackageSqlUnchanged() {
            val sql = "CREATE TABLE test (id NUMBER)"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertEquals(sql, result)
        }

        @Test
        @DisplayName("AUTHID 절이 있는 패키지")
        fun testPackageWithAuthid() {
            val sql = """
                CREATE PACKAGE secure_pkg AUTHID CURRENT_USER AS
                    FUNCTION get_user RETURN VARCHAR2;
                END secure_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("secure_pkg"), "패키지명이 포함되어야 함")
        }

        @Test
        @DisplayName("빈 패키지 바디")
        fun testEmptyPackageBody() {
            val sql = """
                CREATE PACKAGE BODY empty_pkg AS
                END empty_pkg;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 에러 없이 처리되어야 함
            assertNotNull(result)
        }
    }
}
