package com.sqlswitcher.converter.feature.udt

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * UserDefinedTypeConverter 단위 테스트
 */
class UserDefinedTypeConverterTest {

    private fun convert(sql: String, targetDialect: DialectType): Triple<String, List<ConversionWarning>, List<String>> {
        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()
        val result = UserDefinedTypeConverter.convert(
            sql,
            DialectType.ORACLE,
            targetDialect,
            warnings,
            rules
        )
        return Triple(result, warnings, rules)
    }

    @Nested
    @DisplayName("Object Type 변환 테스트")
    inner class ObjectTypeTest {

        @Test
        @DisplayName("단순 Object Type → MySQL 테이블/JSON")
        fun testSimpleObjectTypeToMySql() {
            val sql = """
                CREATE TYPE address_type AS OBJECT (
                    street VARCHAR2(100),
                    city VARCHAR2(50),
                    zip_code VARCHAR2(10)
                );
            """.trimIndent()

            val (result, warnings, rules) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("CREATE TABLE") || result.contains("JSON"),
                "테이블 또는 JSON으로 변환되어야 함")
            assertTrue(result.contains("street"), "street 컬럼이 있어야 함")
            assertTrue(result.contains("city"), "city 컬럼이 있어야 함")
            assertTrue(warnings.isNotEmpty())
            assertTrue(rules.any { it.contains("address_type") })
        }

        @Test
        @DisplayName("Object Type → PostgreSQL Composite Type")
        fun testObjectTypeToPostgreSql() {
            val sql = """
                CREATE TYPE address_type AS OBJECT (
                    street VARCHAR2(100),
                    city VARCHAR2(50),
                    zip_code VARCHAR2(10)
                );
            """.trimIndent()

            val (result, _, rules) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("CREATE TYPE address_type AS"),
                "PostgreSQL CREATE TYPE 문으로 변환되어야 함")
            assertTrue(result.contains("street"))
            assertTrue(result.contains("VARCHAR"))
            assertTrue(rules.any { it.contains("Composite Type") })
        }

        @Test
        @DisplayName("복잡한 Object Type 변환")
        fun testComplexObjectType() {
            val sql = """
                CREATE OR REPLACE TYPE employee_type AS OBJECT (
                    emp_id NUMBER(10),
                    emp_name VARCHAR2(100),
                    hire_date DATE,
                    salary NUMBER(10,2)
                );
            """.trimIndent()

            val (resultMy, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            // MySQL
            assertTrue(resultMy.contains("emp_id"))
            assertTrue(resultMy.contains("emp_name"))

            // PostgreSQL
            assertTrue(resultPg.contains("emp_id"))
            assertTrue(resultPg.contains("NUMERIC") || resultPg.contains("INTEGER"))
        }

        @Test
        @DisplayName("NOT FINAL 옵션 처리")
        fun testNotFinalOption() {
            val sql = """
                CREATE TYPE person_type AS OBJECT (
                    name VARCHAR2(100),
                    age NUMBER
                ) NOT FINAL;
            """.trimIndent()

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("CREATE TYPE person_type"))
            assertTrue(result.contains("name"))
        }
    }

    @Nested
    @DisplayName("VARRAY 변환 테스트")
    inner class VarrayTest {

        @Test
        @DisplayName("VARRAY → MySQL JSON")
        fun testVarrayToMySql() {
            val sql = "CREATE TYPE phone_list AS VARRAY(5) OF VARCHAR2(20);"

            val (result, warnings, rules) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("VARRAY"))
            assertTrue(result.contains("JSON") || result.contains("converted"))
            assertTrue(warnings.any { it.message.contains("phone_list") })
            assertTrue(rules.any { it.contains("VARRAY") })
        }

        @Test
        @DisplayName("VARRAY → PostgreSQL ARRAY")
        fun testVarrayToPostgreSql() {
            val sql = "CREATE TYPE phone_list AS VARRAY(5) OF VARCHAR2(20);"

            val (result, warnings, rules) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("CREATE TYPE phone_list") || result.contains("[]"),
                "PostgreSQL 배열 타입으로 변환되어야 함")
            assertTrue(warnings.any { it.message.contains("크기") || it.message.contains("size") })
        }

        @Test
        @DisplayName("숫자 타입 VARRAY")
        fun testNumericVarray() {
            val sql = "CREATE TYPE number_list AS VARRAY(10) OF NUMBER;"

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("[]") || result.contains("ARRAY") || result.contains("VARRAY"))
        }
    }

    @Nested
    @DisplayName("NESTED TABLE 변환 테스트")
    inner class NestedTableTest {

        @Test
        @DisplayName("NESTED TABLE → MySQL 대체")
        fun testNestedTableToMySql() {
            val sql = "CREATE TYPE email_list AS TABLE OF VARCHAR2(100);"

            val (result, warnings, rules) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("NESTED TABLE") || result.contains("JSON") || result.contains("child table"))
            assertTrue(warnings.isNotEmpty())
            assertTrue(rules.any { it.contains("NESTED TABLE") })
        }

        @Test
        @DisplayName("NESTED TABLE → PostgreSQL ARRAY")
        fun testNestedTableToPostgreSql() {
            val sql = "CREATE TYPE email_list AS TABLE OF VARCHAR2(100);"

            val (result, _, rules) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("CREATE TYPE") || result.contains("[]"))
            assertTrue(rules.any { it.contains("NESTED TABLE") })
        }
    }

    @Nested
    @DisplayName("%TYPE 변환 테스트")
    inner class PercentTypeTest {

        @Test
        @DisplayName("%TYPE → MySQL 경고")
        fun testPercentTypeToMySql() {
            val sql = "v_name employees.name%TYPE;"

            val (result, warnings, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("employees.name") || result.contains("replace"))
            assertTrue(warnings.any { it.message.contains("%TYPE") })
        }

        @Test
        @DisplayName("%TYPE → PostgreSQL 유지")
        fun testPercentTypeToPostgreSql() {
            val sql = "v_name employees.name%TYPE;"

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL)

            // PostgreSQL은 %TYPE 지원
            assertTrue(result.contains("%TYPE"))
        }

        @Test
        @DisplayName("여러 %TYPE 참조")
        fun testMultiplePercentType() {
            val sql = """
                DECLARE
                    v_id employees.employee_id%TYPE;
                    v_name employees.name%TYPE;
                    v_dept departments.dept_name%TYPE;
                BEGIN
                    NULL;
                END;
            """.trimIndent()

            val (result, warnings, _) = convert(sql, DialectType.MYSQL)

            // 여러 개의 %TYPE가 처리되어야 함
            assertTrue(warnings.size >= 2 || result.contains("replace"))
        }
    }

    @Nested
    @DisplayName("%ROWTYPE 변환 테스트")
    inner class PercentRowtypeTest {

        @Test
        @DisplayName("%ROWTYPE → MySQL 경고")
        fun testRowtypeToMySql() {
            val sql = "v_emp employees%ROWTYPE;"

            val (result, warnings, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("employees") || result.contains("replace"))
            assertTrue(warnings.any {
                it.message.contains("%ROWTYPE") && it.severity == WarningSeverity.ERROR
            })
        }

        @Test
        @DisplayName("%ROWTYPE → PostgreSQL 유지")
        fun testRowtypeToPostgreSql() {
            val sql = "v_emp employees%ROWTYPE;"

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL)

            // PostgreSQL은 %ROWTYPE 지원
            assertTrue(result.contains("%ROWTYPE"))
        }
    }

    @Nested
    @DisplayName("REF 타입 변환 테스트")
    inner class RefTypeTest {

        @Test
        @DisplayName("REF → MySQL 외래 키")
        fun testRefToMySql() {
            val sql = "parent_ref REF person_type"

            val (result, warnings, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("foreign key") || result.contains("BIGINT"))
            assertTrue(warnings.any { it.message.contains("REF") })
        }

        @Test
        @DisplayName("REF → PostgreSQL 대체")
        fun testRefToPostgreSql() {
            val sql = "parent_ref REF person_type"

            val (result, warnings, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("foreign key") || result.contains("BIGINT"))
            assertTrue(warnings.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("데이터 타입 변환 테스트")
    inner class DataTypeConversionTest {

        @Test
        @DisplayName("VARCHAR2 → VARCHAR")
        fun testVarchar2Conversion() {
            val sql = """
                CREATE TYPE test_type AS OBJECT (
                    name VARCHAR2(100)
                );
            """.trimIndent()

            val (resultMy, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMy.contains("VARCHAR(100)") || resultMy.contains("VARCHAR"))
            assertTrue(resultPg.contains("VARCHAR"))
        }

        @Test
        @DisplayName("NUMBER 변환")
        fun testNumberConversion() {
            val sql = """
                CREATE TYPE num_type AS OBJECT (
                    small_num NUMBER(5),
                    big_num NUMBER(15),
                    decimal_num NUMBER(10,2)
                );
            """.trimIndent()

            val (resultMy, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            // MySQL
            assertTrue(resultMy.contains("INT") || resultMy.contains("DECIMAL") || resultMy.contains("BIGINT"))

            // PostgreSQL
            assertTrue(resultPg.contains("INTEGER") || resultPg.contains("NUMERIC") || resultPg.contains("SMALLINT"))
        }

        @Test
        @DisplayName("DATE/CLOB/BLOB 변환")
        fun testLobAndDateConversion() {
            val sql = """
                CREATE TYPE doc_type AS OBJECT (
                    created DATE,
                    content CLOB,
                    attachment BLOB
                );
            """.trimIndent()

            val (resultMy, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            // MySQL
            assertTrue(resultMy.contains("DATETIME") || resultMy.contains("LONGTEXT") || resultMy.contains("LONGBLOB") || resultMy.contains("DATE"))

            // PostgreSQL
            assertTrue(resultPg.contains("TIMESTAMP") || resultPg.contains("TEXT") || resultPg.contains("BYTEA") || resultPg.contains("date"))
        }
    }

    @Nested
    @DisplayName("유틸리티 메서드 테스트")
    inner class UtilityMethodsTest {

        @Test
        @DisplayName("UDT 관련 구문 감지")
        fun testHasUserDefinedTypes() {
            assertTrue(UserDefinedTypeConverter.hasUserDefinedTypes(
                "CREATE TYPE address_type AS OBJECT (street VARCHAR2(100));"
            ))
            assertTrue(UserDefinedTypeConverter.hasUserDefinedTypes(
                "v_name employees.name%TYPE"
            ))
            assertTrue(UserDefinedTypeConverter.hasUserDefinedTypes(
                "v_emp employees%ROWTYPE"
            ))
            assertTrue(UserDefinedTypeConverter.hasUserDefinedTypes(
                "CREATE TYPE phone_list AS VARRAY(5) OF VARCHAR2(20)"
            ))
            assertFalse(UserDefinedTypeConverter.hasUserDefinedTypes(
                "SELECT * FROM employees"
            ))
        }

        @Test
        @DisplayName("정의된 타입 이름 추출")
        fun testGetDefinedTypeNames() {
            val sql = """
                CREATE TYPE address_type AS OBJECT (street VARCHAR2(100));
                CREATE OR REPLACE TYPE phone_type AS OBJECT (number VARCHAR2(20));
                CREATE TYPE email_list AS TABLE OF VARCHAR2(100);
            """.trimIndent()

            val names = UserDefinedTypeConverter.getDefinedTypeNames(sql)

            assertTrue(names.contains("address_type"))
            assertTrue(names.contains("phone_type"))
            assertTrue(names.contains("email_list"))
            assertEquals(3, names.size)
        }

        @Test
        @DisplayName("사용된 타입 참조 추출")
        fun testGetUsedTypeReferences() {
            val sql = """
                DECLARE
                    v_id employees.employee_id%TYPE;
                    v_name employees.name%TYPE;
                    v_emp employees%ROWTYPE;
                BEGIN
                    NULL;
                END;
            """.trimIndent()

            val refs = UserDefinedTypeConverter.getUsedTypeReferences(sql)

            assertTrue(refs.any { it.contains("employee_id%TYPE") })
            assertTrue(refs.any { it.contains("name%TYPE") })
            assertTrue(refs.any { it.contains("employees%ROWTYPE") })
        }
    }

    @Nested
    @DisplayName("소스 방언 검증 테스트")
    inner class SourceDialectTest {

        @Test
        @DisplayName("Oracle 외 소스는 변환하지 않음")
        fun testNonOracleSourceNotConverted() {
            val sql = "CREATE TYPE test_type AS OBJECT (name VARCHAR2(100));"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = UserDefinedTypeConverter.convert(
                sql,
                DialectType.MYSQL,  // MySQL이 소스
                DialectType.POSTGRESQL,
                warnings,
                rules
            )

            assertEquals(sql, result, "변환되지 않아야 함")
            assertTrue(rules.isEmpty())
        }
    }

    @Nested
    @DisplayName("복합 시나리오 테스트")
    inner class ComplexScenarioTest {

        @Test
        @DisplayName("여러 UDT 정의 포함 스크립트")
        fun testMultipleUdtDefinitions() {
            val sql = """
                CREATE TYPE address_type AS OBJECT (
                    street VARCHAR2(100),
                    city VARCHAR2(50),
                    country VARCHAR2(50)
                );

                CREATE TYPE phone_list AS VARRAY(5) OF VARCHAR2(20);

                CREATE TYPE person_type AS OBJECT (
                    name VARCHAR2(100),
                    age NUMBER,
                    address address_type,
                    phones phone_list
                );
            """.trimIndent()

            val (result, warnings, rules) = convert(sql, DialectType.POSTGRESQL)

            // 여러 타입이 변환되어야 함
            assertTrue(result.contains("address_type"))
            assertTrue(result.contains("phone_list") || result.contains("VARRAY"))
            assertTrue(result.contains("person_type"))
            assertTrue(rules.size >= 2)
        }

        @Test
        @DisplayName("테이블 생성 시 UDT 참조")
        fun testTableWithUdtReference() {
            val sql = """
                v_address address_type;
                v_phones phone_list;
            """.trimIndent()

            val (resultMy, warningsMy, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            // 결과에 원본 참조가 유지되어야 함
            assertTrue(resultMy.contains("address_type") || resultPg.contains("address_type"))
        }
    }
}
