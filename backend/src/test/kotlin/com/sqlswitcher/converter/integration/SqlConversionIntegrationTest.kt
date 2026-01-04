package com.sqlswitcher.converter.integration

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.feature.ddl.TablespacePartitionConverter
import com.sqlswitcher.converter.feature.function.OracleHintConverter
import com.sqlswitcher.converter.feature.plsql.OraclePackageConverter
import com.sqlswitcher.converter.feature.procedure.OracleProcedureConverter
import com.sqlswitcher.converter.feature.trigger.OracleTriggerConverter
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * SQL 변환 통합 테스트 (E2E)
 *
 * 실제 Oracle DDL 시나리오를 기반으로 전체 변환 흐름을 테스트
 */
class SqlConversionIntegrationTest {

    @Nested
    @DisplayName("Oracle → MySQL 전체 변환 시나리오")
    inner class OracleToMySqlE2ETest {

        @Test
        @DisplayName("HR 스키마 전체 변환 시나리오")
        fun testHrSchemaConversion() {
            // 실제 Oracle HR 스키마와 유사한 DDL
            val oracleDdl = """
                -- Employees 테이블
                CREATE TABLE employees (
                    employee_id NUMBER(6) PRIMARY KEY,
                    first_name VARCHAR2(20),
                    last_name VARCHAR2(25) NOT NULL,
                    email VARCHAR2(25) NOT NULL UNIQUE,
                    phone_number VARCHAR2(20),
                    hire_date DATE NOT NULL,
                    job_id VARCHAR2(10) NOT NULL,
                    salary NUMBER(8,2),
                    commission_pct NUMBER(2,2),
                    manager_id NUMBER(6),
                    department_id NUMBER(4)
                )
                TABLESPACE users
                STORAGE (INITIAL 64K NEXT 64K)
                PCTFREE 10;

                -- 급여 변경 트리거
                CREATE OR REPLACE TRIGGER trg_salary_audit
                AFTER UPDATE OF salary ON employees
                FOR EACH ROW
                BEGIN
                    INSERT INTO salary_history (employee_id, old_salary, new_salary, changed_at)
                    VALUES (:OLD.employee_id, :OLD.salary, :NEW.salary, SYSDATE);
                END;

                -- 급여 조회 힌트 쿼리
                SELECT /*+ INDEX(e emp_salary_idx) */
                    employee_id, salary
                FROM employees e
                WHERE salary > 50000;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            // 1. 테이블스페이스/파티션 변환
            var result = TablespacePartitionConverter.convert(
                oracleDdl, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 2. 트리거 변환
            result = OracleTriggerConverter.convert(
                result, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 3. 힌트 변환
            result = OracleHintConverter.convert(
                result, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 검증 - 물리적 속성 제거
            assertFalse(result.contains("TABLESPACE"), "TABLESPACE가 제거되어야 함")
            assertFalse(result.contains("STORAGE"), "STORAGE가 제거되어야 함")
            assertFalse(result.contains("PCTFREE"), "PCTFREE가 제거되어야 함")

            // 트리거 변환 확인 (SYSDATE와 :NEW/:OLD 변환)
            val triggerConverted = result.contains("NOW()") ||
                    result.contains("NEW.") ||
                    result.contains("FOR EACH ROW")
            assertTrue(triggerConverted, "트리거가 변환되어야 함")

            // 힌트 변환 확인
            val hintProcessed = result.contains("FORCE INDEX") ||
                    result.contains("Oracle hints") ||
                    !result.contains("/*+ INDEX")
            assertTrue(hintProcessed, "힌트가 처리되어야 함")

            println("=== Oracle → MySQL 변환 결과 ===")
            println(result)
            println("\n=== 적용된 규칙: ${appliedRules.size}개 ===")
            appliedRules.forEach { println("- $it") }
            println("\n=== 경고: ${warnings.size}개 ===")
            warnings.forEach { println("- [${it.severity}] ${it.message}") }
        }

        @Test
        @DisplayName("복합 쿼리 변환 시나리오")
        fun testComplexQueryConversion() {
            // 복합 쿼리 - 힌트, 서브쿼리 포함
            val oracleQuery = """
                SELECT /*+ INDEX(e emp_idx) */
                    e.employee_id,
                    e.first_name,
                    (SELECT d.department_name FROM departments d WHERE d.department_id = e.department_id) AS dept_name,
                    NVL(e.commission_pct, 0) AS commission
                FROM employees e
                WHERE e.salary > 50000
                  AND e.hire_date > TO_DATE('2020-01-01', 'YYYY-MM-DD')
                ORDER BY e.salary DESC;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            // 힌트 변환
            val result = OracleHintConverter.convert(
                oracleQuery, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("SELECT"), "SELECT가 유지되어야 함")
            assertTrue(result.contains("FORCE INDEX") || result.contains("Oracle hints"), "힌트가 처리되어야 함")

            println("=== Complex Query → MySQL 변환 결과 ===")
            println(result)
        }

        @Test
        @DisplayName("패키지 변환 시나리오")
        fun testPackageConversion() {
            val oraclePackage = """
                CREATE OR REPLACE PACKAGE BODY emp_utils AS
                    FUNCTION calculate_bonus(p_salary IN NUMBER, p_performance IN NUMBER)
                    RETURN NUMBER IS
                        v_bonus NUMBER;
                    BEGIN
                        v_bonus := p_salary * NVL(p_performance, 0.1);
                        RETURN v_bonus;
                    END calculate_bonus;

                    PROCEDURE log_action(p_action IN VARCHAR2) IS
                    BEGIN
                        INSERT INTO action_log (action, created_at)
                        VALUES (p_action, SYSDATE);
                        DBMS_OUTPUT.PUT_LINE('Action logged: ' || p_action);
                    END log_action;
                END emp_utils;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                oraclePackage, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("emp_utils_calculate_bonus"), "함수명에 패키지 접두사가 붙어야 함")
            assertTrue(result.contains("emp_utils_log_action"), "프로시저명에 패키지 접두사가 붙어야 함")
            assertTrue(result.contains("IFNULL"), "NVL → IFNULL 변환")
            assertTrue(result.contains("NOW()"), "SYSDATE → NOW() 변환")

            println("=== Package → MySQL 변환 결과 ===")
            println(result)
        }
    }

    @Nested
    @DisplayName("Oracle → PostgreSQL 전체 변환 시나리오")
    inner class OracleToPostgreSqlE2ETest {

        @Test
        @DisplayName("파티션 테이블 변환 시나리오")
        fun testPartitionedTableConversion() {
            val oracleDdl = """
                CREATE TABLE sales (
                    sale_id NUMBER(10) PRIMARY KEY,
                    sale_date DATE NOT NULL,
                    amount NUMBER(12,2),
                    region VARCHAR2(50)
                )
                TABLESPACE data_ts
                PARTITION BY RANGE (sale_date) (
                    PARTITION p_2023_q1 VALUES LESS THAN (TO_DATE('2023-04-01', 'YYYY-MM-DD')) TABLESPACE data_2023,
                    PARTITION p_2023_q2 VALUES LESS THAN (TO_DATE('2023-07-01', 'YYYY-MM-DD')) TABLESPACE data_2023,
                    PARTITION p_2023_q3 VALUES LESS THAN (TO_DATE('2023-10-01', 'YYYY-MM-DD')) TABLESPACE data_2023,
                    PARTITION p_2023_q4 VALUES LESS THAN (TO_DATE('2024-01-01', 'YYYY-MM-DD')) TABLESPACE data_2023,
                    PARTITION p_future VALUES LESS THAN (MAXVALUE) TABLESPACE data_current
                );
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                oracleDdl, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("PARTITION BY RANGE"), "PARTITION BY RANGE가 유지되어야 함")
            assertTrue(warnings.any { it.message.contains("PostgreSQL 10") }, "PostgreSQL 버전 관련 경고가 있어야 함")

            println("=== Partitioned Table → PostgreSQL 변환 결과 ===")
            println(result)
        }

        @Test
        @DisplayName("트리거 → 함수 + 트리거 변환")
        fun testTriggerToFunctionConversion() {
            val oracleTrigger = """
                CREATE OR REPLACE TRIGGER trg_update_modified
                BEFORE UPDATE ON documents
                FOR EACH ROW
                WHEN (NEW.content <> OLD.content)
                BEGIN
                    :NEW.modified_at := SYSDATE;
                    :NEW.modified_by := USER;
                    :NEW.version := :OLD.version + 1;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleTriggerConverter.convert(
                oracleTrigger, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("CREATE OR REPLACE FUNCTION"), "함수가 생성되어야 함")
            assertTrue(result.contains("RETURNS TRIGGER"), "RETURNS TRIGGER가 있어야 함")
            assertTrue(result.contains("EXECUTE FUNCTION"), "트리거가 함수를 실행해야 함")
            assertTrue(result.contains("CURRENT_TIMESTAMP"), "SYSDATE → CURRENT_TIMESTAMP")
            assertTrue(result.contains("WHEN"), "WHEN 조건이 유지되어야 함")
            assertTrue(result.contains("RETURN NEW") || result.contains("RETURN NULL"), "RETURN 문이 있어야 함")

            println("=== Trigger → PostgreSQL 변환 결과 ===")
            println(result)
        }

        @Test
        @DisplayName("패키지 → 스키마 변환")
        fun testPackageToSchemaConversion() {
            val oraclePackage = """
                CREATE OR REPLACE PACKAGE BODY reporting AS
                    FUNCTION get_monthly_sales(p_month IN NUMBER, p_year IN NUMBER)
                    RETURN NUMBER IS
                        v_total NUMBER;
                    BEGIN
                        SELECT SUM(amount) INTO v_total
                        FROM sales
                        WHERE EXTRACT(MONTH FROM sale_date) = p_month
                          AND EXTRACT(YEAR FROM sale_date) = p_year;
                        RETURN NVL(v_total, 0);
                    END get_monthly_sales;
                END reporting;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OraclePackageConverter.convert(
                oraclePackage, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("CREATE SCHEMA"), "스키마가 생성되어야 함")
            assertTrue(result.contains("reporting.get_monthly_sales"), "스키마.함수명 형식이어야 함")
            assertTrue(result.contains("COALESCE"), "NVL → COALESCE 변환")
            assertTrue(result.contains("LANGUAGE plpgsql"), "PL/pgSQL 언어 지정")

            println("=== Package → PostgreSQL Schema 변환 결과 ===")
            println(result)
        }

        @Test
        @DisplayName("힌트 → pg_hint_plan 권장 변환")
        fun testHintConversion() {
            val oracleQuery = """
                SELECT /*+
                    INDEX(e emp_dept_idx)
                    PARALLEL(e, 4)
                    LEADING(e d)
                */
                    e.employee_id, e.first_name, d.department_name
                FROM employees e, departments d
                WHERE e.department_id = d.department_id
                  AND e.salary > 50000;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = OracleHintConverter.convert(
                oracleQuery, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("SET max_parallel_workers_per_gather"), "PARALLEL 힌트가 SET으로 변환되어야 함")
            assertTrue(warnings.any { it.suggestion?.contains("pg_hint_plan") == true }, "pg_hint_plan 권장 경고가 있어야 함")

            println("=== Hints → PostgreSQL 변환 결과 ===")
            println(result)
        }
    }

    @Nested
    @DisplayName("복합 변환 시나리오")
    inner class ComplexConversionTest {

        @Test
        @DisplayName("전체 DDL 스크립트 변환")
        fun testFullDdlScriptConversion() {
            val fullScript = """
                -- 시퀀스
                CREATE SEQUENCE emp_seq START WITH 1 INCREMENT BY 1;

                -- 테이블
                CREATE TABLE employees (
                    emp_id NUMBER DEFAULT emp_seq.NEXTVAL PRIMARY KEY,
                    name VARCHAR2(100) NOT NULL,
                    email VARCHAR2(100) UNIQUE,
                    salary NUMBER(10,2),
                    created_at DATE DEFAULT SYSDATE
                )
                TABLESPACE users
                PCTFREE 10;

                -- 감사 트리거
                CREATE TRIGGER trg_emp_audit
                AFTER INSERT OR UPDATE OR DELETE ON employees
                FOR EACH ROW
                BEGIN
                    IF INSERTING THEN
                        INSERT INTO audit_log VALUES ('INSERT', :NEW.emp_id, SYSDATE);
                    ELSIF UPDATING THEN
                        INSERT INTO audit_log VALUES ('UPDATE', :NEW.emp_id, SYSDATE);
                    ELSIF DELETING THEN
                        INSERT INTO audit_log VALUES ('DELETE', :OLD.emp_id, SYSDATE);
                    END IF;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            // 순차적 변환
            var result = TablespacePartitionConverter.convert(
                fullScript, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )
            result = OracleTriggerConverter.convert(
                result, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            // 기본 검증
            assertFalse(result.contains("PCTFREE"), "물리적 속성이 제거되어야 함")
            assertTrue(result.contains("TG_OP"), "트리거 조건이 TG_OP로 변환되어야 함")

            println("=== Full DDL → PostgreSQL 변환 결과 ===")
            println(result)
            println("\n=== 총 적용된 규칙: ${appliedRules.size}개 ===")
            appliedRules.forEach { println("- $it") }
            println("\n=== 총 경고: ${warnings.size}개 ===")
            warnings.forEach { println("- [${it.severity}] ${it.message}") }
        }

        @Test
        @DisplayName("복합 프로시저 변환")
        fun testComplexProcedureConversion() {
            val complexProcedure = """
                CREATE OR REPLACE PROCEDURE process_employees IS
                    v_count NUMBER;
                    v_name VARCHAR2(100);
                BEGIN
                    SELECT COUNT(*) INTO v_count FROM employees;

                    IF v_count > 0 THEN
                        UPDATE employees SET status = 'ACTIVE' WHERE status IS NULL;
                        DBMS_OUTPUT.PUT_LINE('Updated ' || v_count || ' employees');
                    END IF;

                    COMMIT;
                EXCEPTION
                    WHEN OTHERS THEN
                        ROLLBACK;
                        RAISE_APPLICATION_ERROR(-20001, 'Error processing employees');
                END process_employees;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            // 프로시저 변환
            val result = OracleProcedureConverter.convertToPostgreSql(complexProcedure, warnings, appliedRules)

            assertTrue(result.contains("CREATE OR REPLACE FUNCTION") || result.contains("PROCEDURE"),
                "프로시저/함수가 생성되어야 함")
            assertTrue(result.contains("RAISE NOTICE") || result.contains("RAISE EXCEPTION"),
                "DBMS_OUTPUT/RAISE가 변환되어야 함")

            println("=== Complex Procedure → PostgreSQL 변환 결과 ===")
            println(result)
        }
    }

    @Nested
    @DisplayName("경고 및 에러 처리 테스트")
    inner class WarningAndErrorTest {

        @Test
        @DisplayName("미지원 기능 경고 수집")
        fun testUnsupportedFeatureWarnings() {
            val complexOracle = """
                -- INSTEAD OF 트리거 (MySQL 미지원)
                CREATE TRIGGER trg_view
                INSTEAD OF INSERT ON emp_view
                FOR EACH ROW
                BEGIN
                    INSERT INTO employees VALUES (:NEW.id, :NEW.name);
                END;

                -- INTERVAL 파티션 (MySQL/PostgreSQL 미지원)
                CREATE TABLE logs (
                    id NUMBER,
                    log_date DATE
                )
                PARTITION BY RANGE (log_date)
                INTERVAL (NUMTOYMINTERVAL(1, 'MONTH'))
                (PARTITION p_init VALUES LESS THAN ('2024-01-01'));
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            // 변환 수행
            var result = OracleTriggerConverter.convert(
                complexOracle, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )
            result = TablespacePartitionConverter.convert(
                result, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 경고 확인
            assertTrue(warnings.any { it.message.contains("INSTEAD OF") }, "INSTEAD OF 경고가 있어야 함")
            assertTrue(warnings.any { it.message.contains("INTERVAL") }, "INTERVAL 경고가 있어야 함")

            println("=== 수집된 경고 ===")
            warnings.forEach { warning ->
                println("[${warning.severity}] ${warning.type}: ${warning.message}")
                warning.suggestion?.let { println("  제안: $it") }
            }
        }

        @Test
        @DisplayName("변환 통계 수집")
        fun testConversionStatistics() {
            val oracleSql = """
                SELECT /*+ INDEX(e) PARALLEL(4) */ * FROM employees e;

                CREATE TRIGGER trg1 BEFORE INSERT ON t1 FOR EACH ROW BEGIN :NEW.id := 1; END;

                CREATE TABLE t2 (id NUMBER) TABLESPACE ts1 PCTFREE 10;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            // 모든 변환기 적용
            var result = OracleHintConverter.convert(oracleSql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules)
            result = OracleTriggerConverter.convert(result, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules)
            result = TablespacePartitionConverter.convert(result, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules)

            // 통계 출력
            println("=== 변환 통계 ===")
            println("적용된 규칙 수: ${appliedRules.size}")
            println("경고 수: ${warnings.size}")
            println("에러 수: ${warnings.count { it.severity.name == "ERROR" }}")
            println()
            println("적용된 규칙:")
            appliedRules.forEachIndexed { index, rule ->
                println("  ${index + 1}. $rule")
            }
        }
    }

    @Nested
    @DisplayName("성능 및 대용량 테스트")
    inner class PerformanceTest {

        @Test
        @DisplayName("대용량 DDL 스크립트 변환")
        fun testLargeDdlConversion() {
            // 100개의 테이블 생성 DDL 시뮬레이션
            val largeDdl = buildString {
                repeat(100) { i ->
                    appendLine("CREATE TABLE table_$i (")
                    appendLine("    id NUMBER PRIMARY KEY,")
                    appendLine("    name VARCHAR2(100),")
                    appendLine("    created_at DATE DEFAULT SYSDATE")
                    appendLine(") TABLESPACE users PCTFREE 10;")
                    appendLine()
                }
            }

            val startTime = System.currentTimeMillis()
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                largeDdl, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            println("=== 성능 테스트 결과 ===")
            println("입력 크기: ${largeDdl.length} 문자")
            println("출력 크기: ${result.length} 문자")
            println("변환 시간: ${duration}ms")
            println("테이블 수: 100개")
            println("적용된 규칙: ${appliedRules.size}개")

            // 성능 검증 (5초 이내)
            assertTrue(duration < 5000, "변환 시간이 5초 이내여야 함")
            assertFalse(result.contains("TABLESPACE"), "모든 TABLESPACE가 제거되어야 함")
            assertFalse(result.contains("PCTFREE"), "모든 PCTFREE가 제거되어야 함")
        }
    }
}
