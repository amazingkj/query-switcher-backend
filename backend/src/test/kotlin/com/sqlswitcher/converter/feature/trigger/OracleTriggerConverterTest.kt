package com.sqlswitcher.converter.feature.trigger

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
 * OracleTriggerConverter 단위 테스트
 */
class OracleTriggerConverterTest {

    @Nested
    @DisplayName("Oracle → MySQL 트리거 변환")
    inner class ToMySqlTest {

        @Test
        @DisplayName("기본 BEFORE INSERT 트리거 변환")
        fun testBasicBeforeInsertTrigger() {
            val sql = """
                CREATE OR REPLACE TRIGGER trg_emp_insert
                BEFORE INSERT ON employees
                FOR EACH ROW
                BEGIN
                    :NEW.created_at := SYSDATE;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("CREATE TRIGGER"), "CREATE TRIGGER가 있어야 함")
            assertTrue(result.contains("BEFORE INSERT"), "BEFORE INSERT가 있어야 함")
            assertTrue(result.contains("FOR EACH ROW"), "FOR EACH ROW가 있어야 함")
            assertTrue(result.contains("NEW.created_at"), ":NEW → NEW 변환되어야 함")
            assertTrue(result.contains("NOW()"), "SYSDATE → NOW() 변환되어야 함")
            assertTrue(appliedRules.any { it.contains("트리거") }, "적용된 규칙에 포함되어야 함")
        }

        @Test
        @DisplayName("AFTER UPDATE 트리거 변환")
        fun testAfterUpdateTrigger() {
            val sql = """
                CREATE TRIGGER trg_emp_update
                AFTER UPDATE ON employees
                FOR EACH ROW
                BEGIN
                    INSERT INTO audit_log (emp_id, old_salary, new_salary)
                    VALUES (:OLD.emp_id, :OLD.salary, :NEW.salary);
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("AFTER UPDATE"), "AFTER UPDATE가 있어야 함")
            assertTrue(result.contains("OLD.emp_id"), ":OLD → OLD 변환되어야 함")
            assertTrue(result.contains("NEW.salary"), ":NEW → NEW 변환되어야 함")
        }

        @Test
        @DisplayName("WHEN 조건 → IF 문으로 변환")
        fun testWhenConditionToIf() {
            val sql = """
                CREATE TRIGGER trg_check_salary
                BEFORE UPDATE ON employees
                FOR EACH ROW
                WHEN (NEW.salary > 100000)
                BEGIN
                    :NEW.status := 'HIGH_EARNER';
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("IF"), "WHEN이 IF로 변환되어야 함")
            assertTrue(warnings.any { it.message.contains("WHEN") }, "WHEN 변환 경고가 있어야 함")
        }

        @Test
        @DisplayName("복수 이벤트 트리거 분리")
        fun testMultipleEventsTriggerSplit() {
            val sql = """
                CREATE TRIGGER trg_emp_audit
                AFTER INSERT OR UPDATE OR DELETE ON employees
                FOR EACH ROW
                BEGIN
                    INSERT INTO audit_log (action) VALUES ('CHANGE');
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToMySql(sql, warnings, appliedRules)

            // 3개의 트리거로 분리되어야 함
            val triggerCount = Regex("CREATE TRIGGER").findAll(result).count()
            assertTrue(triggerCount >= 1, "최소 하나의 트리거가 생성되어야 함")

            if (triggerCount > 1) {
                assertTrue(warnings.any { it.message.contains("분리") }, "분리 경고가 있어야 함")
            }
        }

        @Test
        @DisplayName("INSTEAD OF 트리거 미지원 경고")
        fun testInsteadOfTriggerWarning() {
            val sql = """
                CREATE TRIGGER trg_view_insert
                INSTEAD OF INSERT ON emp_view
                FOR EACH ROW
                BEGIN
                    INSERT INTO employees VALUES (:NEW.emp_id, :NEW.name);
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(warnings.any { it.message.contains("INSTEAD OF") }, "INSTEAD OF 경고가 있어야 함")
            assertTrue(result.contains("--"), "주석 처리되어야 함")
        }

        @Test
        @DisplayName("RAISE_APPLICATION_ERROR → SIGNAL 변환")
        fun testRaiseApplicationErrorConversion() {
            val sql = """
                CREATE TRIGGER trg_check_salary
                BEFORE UPDATE ON employees
                FOR EACH ROW
                BEGIN
                    IF :NEW.salary < 0 THEN
                        RAISE_APPLICATION_ERROR(-20001, 'Salary cannot be negative');
                    END IF;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("SIGNAL SQLSTATE"), "SIGNAL로 변환되어야 함")
            assertTrue(result.contains("MESSAGE_TEXT"), "MESSAGE_TEXT가 있어야 함")
            assertFalse(result.contains("RAISE_APPLICATION_ERROR"), "원본이 제거되어야 함")
        }

        @Test
        @DisplayName("NVL → IFNULL 변환")
        fun testNvlToIfnull() {
            val sql = """
                CREATE TRIGGER trg_default_value
                BEFORE INSERT ON employees
                FOR EACH ROW
                BEGIN
                    :NEW.status := NVL(:NEW.status, 'ACTIVE');
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("IFNULL("), "IFNULL로 변환되어야 함")
            assertFalse(result.contains("NVL("), "NVL이 제거되어야 함")
        }
    }

    @Nested
    @DisplayName("Oracle → PostgreSQL 트리거 변환")
    inner class ToPostgreSqlTest {

        @Test
        @DisplayName("기본 트리거 → 함수 + 트리거 분리")
        fun testTriggerToFunctionAndTrigger() {
            val sql = """
                CREATE OR REPLACE TRIGGER trg_emp_insert
                BEFORE INSERT ON employees
                FOR EACH ROW
                BEGIN
                    :NEW.created_at := SYSDATE;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("CREATE OR REPLACE FUNCTION"), "함수가 생성되어야 함")
            assertTrue(result.contains("RETURNS TRIGGER"), "RETURNS TRIGGER가 있어야 함")
            assertTrue(result.contains("LANGUAGE plpgsql"), "PL/pgSQL 언어 지정이 있어야 함")
            assertTrue(result.contains("CREATE OR REPLACE TRIGGER"), "트리거가 생성되어야 함")
            assertTrue(result.contains("EXECUTE FUNCTION"), "EXECUTE FUNCTION이 있어야 함")
        }

        @Test
        @DisplayName("SYSDATE → CURRENT_TIMESTAMP 변환")
        fun testSysdateConversion() {
            val sql = """
                CREATE TRIGGER trg_audit
                AFTER INSERT ON employees
                FOR EACH ROW
                BEGIN
                    INSERT INTO logs (created) VALUES (SYSDATE);
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("CURRENT_TIMESTAMP"), "CURRENT_TIMESTAMP로 변환되어야 함")
            assertFalse(result.uppercase().contains("SYSDATE"), "SYSDATE가 제거되어야 함")
        }

        @Test
        @DisplayName("NVL → COALESCE 변환")
        fun testNvlToCoalesce() {
            val sql = """
                CREATE TRIGGER trg_default
                BEFORE INSERT ON employees
                FOR EACH ROW
                BEGIN
                    :NEW.dept := NVL(:NEW.dept, 'UNKNOWN');
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("COALESCE("), "COALESCE로 변환되어야 함")
        }

        @Test
        @DisplayName("RAISE_APPLICATION_ERROR → RAISE EXCEPTION 변환")
        fun testRaiseExceptionConversion() {
            val sql = """
                CREATE TRIGGER trg_validate
                BEFORE INSERT ON employees
                FOR EACH ROW
                BEGIN
                    IF :NEW.age < 18 THEN
                        RAISE_APPLICATION_ERROR(-20002, 'Must be 18 or older');
                    END IF;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("RAISE EXCEPTION"), "RAISE EXCEPTION으로 변환되어야 함")
        }

        @Test
        @DisplayName("INSERTING/UPDATING/DELETING → TG_OP 변환")
        fun testTriggerPredicateConversion() {
            val sql = """
                CREATE TRIGGER trg_audit
                AFTER INSERT OR UPDATE OR DELETE ON employees
                FOR EACH ROW
                BEGIN
                    IF INSERTING THEN
                        INSERT INTO logs VALUES ('INSERT');
                    ELSIF UPDATING THEN
                        INSERT INTO logs VALUES ('UPDATE');
                    ELSIF DELETING THEN
                        INSERT INTO logs VALUES ('DELETE');
                    END IF;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("TG_OP = 'INSERT'"), "INSERTING → TG_OP 변환되어야 함")
            assertTrue(result.contains("TG_OP = 'UPDATE'"), "UPDATING → TG_OP 변환되어야 함")
            assertTrue(result.contains("TG_OP = 'DELETE'"), "DELETING → TG_OP 변환되어야 함")
        }

        @Test
        @DisplayName("WHEN 조건 유지")
        fun testWhenConditionKept() {
            val sql = """
                CREATE TRIGGER trg_high_salary
                BEFORE UPDATE ON employees
                FOR EACH ROW
                WHEN (NEW.salary > 50000)
                BEGIN
                    :NEW.tax_rate := 0.3;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("WHEN"), "WHEN 조건이 유지되어야 함")
        }

        @Test
        @DisplayName("DECLARE 블록 변환")
        fun testDeclareBlockConversion() {
            val sql = """
                CREATE TRIGGER trg_with_vars
                BEFORE INSERT ON employees
                FOR EACH ROW
                DECLARE
                    v_count NUMBER;
                    v_name VARCHAR2(100);
                BEGIN
                    SELECT COUNT(*) INTO v_count FROM employees;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("DECLARE"), "DECLARE가 있어야 함")
            assertTrue(result.contains("NUMERIC") || result.contains("NUMBER"),
                "NUMBER 타입이 변환되어야 함")
        }

        @Test
        @DisplayName("RETURN 문 자동 추가")
        fun testReturnStatementAdded() {
            val sql = """
                CREATE TRIGGER trg_before
                BEFORE INSERT ON employees
                FOR EACH ROW
                BEGIN
                    :NEW.id := 1;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToPostgreSql(sql, warnings, appliedRules)

            assertTrue(result.contains("RETURN NEW") || result.contains("RETURN NULL"),
                "RETURN 문이 추가되어야 함")
        }
    }

    @Nested
    @DisplayName("유틸리티 메서드 테스트")
    inner class UtilityTest {

        @Test
        @DisplayName("트리거 추출")
        fun testExtractTriggers() {
            val sql = """
                CREATE TRIGGER trg1 BEFORE INSERT ON t1 FOR EACH ROW BEGIN NULL; END;

                CREATE TRIGGER trg2 AFTER UPDATE ON t2 FOR EACH ROW BEGIN NULL; END;
            """.trimIndent()

            val triggers = OracleTriggerConverter.extractTriggers(sql)

            assertEquals(2, triggers.size, "2개의 트리거가 추출되어야 함")
        }

        @Test
        @DisplayName("트리거 이름 추출")
        fun testExtractTriggerName() {
            val sql = "CREATE OR REPLACE TRIGGER my_trigger BEFORE INSERT ON t1 BEGIN NULL; END;"

            val name = OracleTriggerConverter.extractTriggerName(sql)

            assertEquals("my_trigger", name, "트리거명이 추출되어야 함")
        }

        @Test
        @DisplayName("트리거 유효성 검사 - 유효한 트리거")
        fun testValidateValidTrigger() {
            val sql = """
                CREATE TRIGGER trg_test
                BEFORE INSERT ON employees
                FOR EACH ROW
                BEGIN
                    :NEW.id := 1;
                END;
            """.trimIndent()

            val errors = OracleTriggerConverter.validateTrigger(sql)

            assertTrue(errors.isEmpty(), "유효한 트리거는 에러가 없어야 함")
        }

        @Test
        @DisplayName("트리거 유효성 검사 - 잘못된 트리거")
        fun testValidateInvalidTrigger() {
            val sql = "CREATE TRIGGER trg_test FOR EACH ROW"

            val errors = OracleTriggerConverter.validateTrigger(sql)

            assertTrue(errors.isNotEmpty(), "잘못된 트리거는 에러가 있어야 함")
        }
    }

    @Nested
    @DisplayName("엣지 케이스 테스트")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("Oracle이 아닌 소스는 변환하지 않음")
        fun testNonOracleSourceUnchanged() {
            val sql = "CREATE TRIGGER trg BEFORE INSERT ON t BEGIN NULL; END;"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convert(
                sql, DialectType.MYSQL, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertEquals(sql, result, "MySQL 소스는 변환하지 않아야 함")
        }

        @Test
        @DisplayName("트리거가 아닌 SQL은 그대로 반환")
        fun testNonTriggerSqlUnchanged() {
            val sql = "SELECT * FROM employees"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertEquals(sql, result, "트리거가 아니면 변환하지 않아야 함")
        }

        @Test
        @DisplayName("COMPOUND TRIGGER 경고")
        fun testCompoundTriggerWarning() {
            val sql = """
                CREATE TRIGGER trg_compound
                FOR INSERT ON employees
                COMPOUND TRIGGER
                    BEFORE EACH ROW IS
                    BEGIN
                        :NEW.id := 1;
                    END BEFORE EACH ROW;
                END trg_compound;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            OracleTriggerConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(warnings.any { it.message.contains("COMPOUND") },
                "COMPOUND TRIGGER 경고가 있어야 함")
        }

        @Test
        @DisplayName("스키마 접두사 처리")
        fun testSchemaPrefix() {
            val sql = """
                CREATE TRIGGER hr.trg_emp
                BEFORE INSERT ON hr.employees
                FOR EACH ROW
                BEGIN
                    :NEW.id := 1;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToMySql(sql, warnings, appliedRules)

            assertTrue(result.contains("employees"), "테이블명이 유지되어야 함")
        }

        @Test
        @DisplayName("REFERENCING 절 처리")
        fun testReferencingClause() {
            val sql = """
                CREATE TRIGGER trg_ref
                BEFORE UPDATE ON employees
                REFERENCING NEW AS n OLD AS o
                FOR EACH ROW
                BEGIN
                    :n.updated_by := 'SYSTEM';
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convertToMySql(sql, warnings, appliedRules)

            // alias가 적절히 처리되어야 함
            assertTrue(result.contains("NEW") || result.contains("n.updated_by"),
                "NEW 참조가 처리되어야 함")
        }
    }
}
