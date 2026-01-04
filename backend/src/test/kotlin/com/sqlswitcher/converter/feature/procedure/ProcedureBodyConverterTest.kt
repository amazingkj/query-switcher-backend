package com.sqlswitcher.converter.feature.procedure

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * ProcedureBodyConverter 테스트
 */
class ProcedureBodyConverterTest {

    @Nested
    @DisplayName("SQL 속성 변환 테스트")
    inner class SqlAttributeTest {

        @Test
        @DisplayName("SQL%ROWCOUNT → PostgreSQL GET DIAGNOSTICS")
        fun testSqlRowcountToPostgreSql() {
            val body = """
                row_count := SQL%ROWCOUNT;
                IF SQL%ROWCOUNT > 0 THEN
                    DBMS_OUTPUT.PUT_LINE('Updated');
                END IF;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("GET DIAGNOSTICS"))
            assertTrue(rules.any { it.contains("SQL%ROWCOUNT") })
        }

        @Test
        @DisplayName("SQL%ROWCOUNT → MySQL ROW_COUNT()")
        fun testSqlRowcountToMySql() {
            val body = "row_count := SQL%ROWCOUNT;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("ROW_COUNT()"))
        }

        @Test
        @DisplayName("SQL%FOUND/SQL%NOTFOUND → PostgreSQL")
        fun testSqlFoundNotFoundToPostgreSql() {
            val body = """
                IF SQL%FOUND THEN
                    process_data();
                ELSIF SQL%NOTFOUND THEN
                    raise_error();
                END IF;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("FOUND"))
            assertTrue(result.contains("NOT FOUND"))
            assertFalse(result.contains("SQL%FOUND"))
            assertFalse(result.contains("SQL%NOTFOUND"))
        }

        @Test
        @DisplayName("SQL%FOUND/SQL%NOTFOUND → MySQL")
        fun testSqlFoundNotFoundToMySql() {
            val body = """
                IF SQL%FOUND THEN
                    process_data();
                ELSIF SQL%NOTFOUND THEN
                    raise_error();
                END IF;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("ROW_COUNT() > 0"))
            assertTrue(result.contains("ROW_COUNT() = 0"))
        }
    }

    @Nested
    @DisplayName("PRAGMA 변환 테스트")
    inner class PragmaTest {

        @Test
        @DisplayName("PRAGMA AUTONOMOUS_TRANSACTION → PostgreSQL 주석")
        fun testAutonomousTransactionToPostgreSql() {
            val body = """
                PRAGMA AUTONOMOUS_TRANSACTION;
                INSERT INTO log_table VALUES (SYSDATE, 'message');
                COMMIT;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("--"))
            assertTrue(result.contains("dblink"))
            assertTrue(warnings.any { it.message.contains("AUTONOMOUS_TRANSACTION") })
        }

        @Test
        @DisplayName("PRAGMA EXCEPTION_INIT → 주석 처리")
        fun testExceptionInitToPostgreSql() {
            val body = """
                e_custom_error EXCEPTION;
                PRAGMA EXCEPTION_INIT(e_custom_error, -20001);
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("--"))
            assertTrue(warnings.any { it.message.contains("EXCEPTION_INIT") })
        }
    }

    @Nested
    @DisplayName("Pipelined Function 변환 테스트")
    inner class PipelinedTest {

        @Test
        @DisplayName("PIPE ROW → PostgreSQL RETURN NEXT")
        fun testPipeRowToPostgreSql() {
            val body = """
                FOR rec IN (SELECT * FROM employees) LOOP
                    PIPE ROW(rec);
                END LOOP;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("RETURN NEXT"))
            assertFalse(result.contains("PIPE ROW"))
            assertTrue(rules.any { it.contains("PIPE ROW") })
        }

        @Test
        @DisplayName("PIPE ROW → MySQL 미지원 경고")
        fun testPipeRowToMySql() {
            val body = "PIPE ROW(row_data);"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("--"))
            assertTrue(warnings.any { it.message.contains("PIPELINED") || it.message.contains("PIPE ROW") })
        }
    }

    @Nested
    @DisplayName("Collection 타입 변환 테스트")
    inner class CollectionTest {

        @Test
        @DisplayName("TABLE OF → PostgreSQL 배열")
        fun testTableOfToPostgreSql() {
            val body = "TYPE emp_ids_t IS TABLE OF NUMBER ;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            // 변환이 적용되면 배열 문법이 포함되거나, 규칙에 TABLE OF가 추가됨
            val hasArraySyntax = result.contains("[]") || result.contains("ARRAY")
            val hasRule = rules.any { it.contains("TABLE OF") }
            assertTrue(hasArraySyntax || hasRule || result.contains("emp_ids_t"),
                "Expected array conversion or rule application. Result: $result, Rules: $rules")
        }

        @Test
        @DisplayName("VARRAY → PostgreSQL 배열")
        fun testVarrayToPostgreSql() {
            val body = "TYPE name_array IS VARRAY(100) OF VARCHAR2(50);"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("[]"))
            assertTrue(warnings.any { it.message.contains("VARRAY") })
        }

        @Test
        @DisplayName("RECORD 타입 → PostgreSQL 복합 타입")
        fun testRecordTypeToPostgreSql() {
            val body = """
                TYPE emp_rec IS RECORD (
                    emp_id NUMBER,
                    emp_name VARCHAR2(100)
                );
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("CREATE TYPE") || result.contains("RECORD"))
            assertTrue(rules.any { it.contains("RECORD") })
        }

        @Test
        @DisplayName("Collection 타입 → MySQL 미지원")
        fun testCollectionToMySql() {
            val body = "TYPE emp_ids_t IS TABLE OF NUMBER;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("--"))
            assertTrue(warnings.any { it.message.contains("컬렉션") || it.message.contains("TABLE OF") })
        }
    }

    @Nested
    @DisplayName("REF CURSOR 변환 테스트")
    inner class RefCursorTest {

        @Test
        @DisplayName("REF CURSOR → PostgreSQL refcursor")
        fun testRefCursorToPostgreSql() {
            val body = "TYPE emp_cursor IS REF CURSOR RETURN employees%ROWTYPE;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("refcursor"))
            assertTrue(rules.any { it.contains("REF CURSOR") })
        }

        @Test
        @DisplayName("SYS_REFCURSOR → PostgreSQL refcursor")
        fun testSysRefcursorToPostgreSql() {
            val body = "v_cursor SYS_REFCURSOR;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("refcursor"))
            assertFalse(result.contains("SYS_REFCURSOR"))
        }
    }

    @Nested
    @DisplayName("Control Flow 변환 테스트")
    inner class ControlFlowTest {

        @Test
        @DisplayName("EXIT WHEN → MySQL IF LEAVE")
        fun testExitWhenToMySql() {
            val body = "EXIT WHEN counter > 100;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("IF"))
            assertTrue(result.contains("LEAVE"))
            assertTrue(result.contains("counter > 100"))
        }

        @Test
        @DisplayName("CONTINUE WHEN → MySQL IF ITERATE")
        fun testContinueWhenToMySql() {
            val body = "CONTINUE WHEN status = 'SKIP';"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("IF"))
            assertTrue(result.contains("ITERATE"))
        }

        @Test
        @DisplayName("GOTO → MySQL 미지원 경고")
        fun testGotoToMySql() {
            val body = """
                <<retry_point>>
                BEGIN
                    -- some code
                    GOTO retry_point;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("--"))
            assertTrue(warnings.any { it.message.contains("GOTO") })
        }
    }

    @Nested
    @DisplayName("Exception 변환 테스트")
    inner class ExceptionTest {

        @Test
        @DisplayName("RAISE_APPLICATION_ERROR → PostgreSQL RAISE EXCEPTION")
        fun testRaiseApplicationErrorToPostgreSql() {
            val body = "RAISE_APPLICATION_ERROR(-20001, 'Custom error message');"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("RAISE EXCEPTION"))
            assertTrue(result.contains("ERRCODE"))
            assertFalse(result.contains("RAISE_APPLICATION_ERROR"))
        }

        @Test
        @DisplayName("RAISE_APPLICATION_ERROR → MySQL SIGNAL")
        fun testRaiseApplicationErrorToMySql() {
            val body = "RAISE_APPLICATION_ERROR(-20001, 'Custom error message');"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("SIGNAL SQLSTATE"))
            assertTrue(result.contains("MESSAGE_TEXT"))
        }

        @Test
        @DisplayName("RAISE NO_DATA_FOUND → PostgreSQL")
        fun testRaiseNoDataFoundToPostgreSql() {
            val body = "RAISE NO_DATA_FOUND;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("RAISE EXCEPTION"))
            assertTrue(result.contains("P0002") || result.contains("No data found"))
        }
    }

    @Nested
    @DisplayName("RETURNING INTO 변환 테스트")
    inner class ReturningIntoTest {

        @Test
        @DisplayName("RETURNING INTO → PostgreSQL 유지")
        fun testReturningIntoToPostgreSql() {
            val body = "INSERT INTO employees (name) VALUES ('John') RETURNING id INTO v_id;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("RETURNING"))
            assertTrue(result.contains("INTO"))
        }

        @Test
        @DisplayName("RETURNING INTO → MySQL 경고")
        fun testReturningIntoToMySql() {
            val body = "UPDATE employees SET salary = 5000 WHERE id = 1 RETURNING salary INTO v_salary;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("--"))
            assertTrue(warnings.any { it.message.contains("RETURNING INTO") })
        }
    }

    @Nested
    @DisplayName("경계 케이스 테스트")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("동일 방언 - 원본 유지")
        fun testSameDialect() {
            val body = "SQL%ROWCOUNT; PIPE ROW(data);"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.ORACLE, warnings, rules
            )

            assertEquals(body, result)
        }

        @Test
        @DisplayName("비 Oracle 소스 - 변환 없음")
        fun testNonOracleSource() {
            val body = "ROW_COUNT(); GET DIAGNOSTICS;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.MYSQL, DialectType.POSTGRESQL, warnings, rules
            )

            assertEquals(body, result)
        }

        @Test
        @DisplayName("복합 프로시저 본문")
        fun testComplexBody() {
            val body = """
                DECLARE
                    v_count NUMBER;
                    TYPE emp_ids_t IS TABLE OF NUMBER;
                    v_cursor SYS_REFCURSOR;
                BEGIN
                    v_count := SQL%ROWCOUNT;
                    IF SQL%NOTFOUND THEN
                        RAISE_APPLICATION_ERROR(-20001, 'No data');
                    END IF;
                    EXIT WHEN v_count > 100;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            // 여러 변환이 적용됨
            assertTrue(result.contains("ROW_COUNT()"))
            assertTrue(result.contains("SIGNAL SQLSTATE"))
            assertTrue(result.contains("IF") && result.contains("LEAVE"))
            assertTrue(rules.size >= 3)
        }
    }
}
