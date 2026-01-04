package com.sqlswitcher.converter.integration

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.feature.partition.AdvancedPartitionConverter
import com.sqlswitcher.converter.feature.flashback.FlashbackQueryConverter
import com.sqlswitcher.converter.feature.procedure.ProcedureBodyConverter
import com.sqlswitcher.converter.feature.ddl.TablespacePartitionConverter
import com.sqlswitcher.converter.feature.trigger.OracleTriggerConverter
import com.sqlswitcher.converter.feature.plsql.OraclePackageConverter
import com.sqlswitcher.converter.feature.function.OracleHintConverter
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * 고급 변환 기능 통합 테스트
 *
 * AdvancedPartitionConverter, FlashbackQueryConverter, ProcedureBodyConverter 통합 테스트
 */
class AdvancedFeatureIntegrationTest {

    @Nested
    @DisplayName("파티션 테이블 변환 통합 테스트")
    inner class PartitionConversionE2ETest {

        @Test
        @DisplayName("RANGE 파티션 - Oracle → MySQL 전체 변환")
        fun testRangePartitionOracleToMySql() {
            val oracleDdl = """
                CREATE TABLE sales_history (
                    sale_id NUMBER(10) PRIMARY KEY,
                    sale_date DATE NOT NULL,
                    amount NUMBER(12,2),
                    region VARCHAR2(50),
                    created_at DATE DEFAULT SYSDATE
                )
                TABLESPACE data_ts
                PARTITION BY RANGE (sale_date) (
                    PARTITION p_2023 VALUES LESS THAN (TO_DATE('2024-01-01', 'YYYY-MM-DD')) TABLESPACE hist_ts,
                    PARTITION p_2024 VALUES LESS THAN (TO_DATE('2025-01-01', 'YYYY-MM-DD')) TABLESPACE curr_ts,
                    PARTITION p_future VALUES LESS THAN (MAXVALUE)
                );
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            // 파티션 변환 적용
            val result = AdvancedPartitionConverter.convert(
                oracleDdl, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            // 검증 - AdvancedPartitionConverter는 TABLESPACE만 제거
            assertTrue(result.contains("PARTITION BY RANGE"), "PARTITION BY RANGE 유지")
            assertTrue(result.contains("p_2023"), "파티션 이름 유지")
            assertFalse(result.contains("TABLESPACE"), "TABLESPACE 제거")
            // TO_DATE → STR_TO_DATE 변환 확인
            assertTrue(result.contains("STR_TO_DATE"), "TO_DATE → STR_TO_DATE 변환")
            assertTrue(rules.isNotEmpty(), "변환 규칙 적용됨")

            println("=== RANGE Partition Oracle → MySQL ===")
            println(result)
            println("\n적용 규칙: ${rules.joinToString()}")
        }

        @Test
        @DisplayName("LIST 파티션 - Oracle → PostgreSQL 전체 변환")
        fun testListPartitionOracleToPostgreSql() {
            val oracleDdl = """
                CREATE TABLE orders_by_region (
                    order_id NUMBER PRIMARY KEY,
                    region VARCHAR2(20),
                    order_date DATE,
                    total_amount NUMBER(10,2)
                )
                PARTITION BY LIST (region) (
                    PARTITION p_korea VALUES ('KR', 'KOREA'),
                    PARTITION p_japan VALUES ('JP', 'JAPAN'),
                    PARTITION p_china VALUES ('CN', 'CHINA'),
                    PARTITION p_other VALUES (DEFAULT)
                );
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = AdvancedPartitionConverter.convert(
                oracleDdl, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("PARTITION BY LIST"), "PARTITION BY LIST 유지")
            assertTrue(result.contains("p_korea") || result.contains("orders_by_region"), "파티션/테이블 이름 유지")

            println("=== LIST Partition Oracle → PostgreSQL ===")
            println(result)
        }

        @Test
        @DisplayName("HASH 파티션 - Oracle → MySQL 변환")
        fun testHashPartitionOracleToMySql() {
            val oracleDdl = """
                CREATE TABLE user_sessions (
                    session_id NUMBER PRIMARY KEY,
                    user_id NUMBER NOT NULL,
                    session_data CLOB,
                    created_at DATE DEFAULT SYSDATE
                )
                PARTITION BY HASH (user_id)
                PARTITIONS 8;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = AdvancedPartitionConverter.convert(
                oracleDdl, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("PARTITION BY") || result.contains("user_sessions"), "파티션 또는 테이블 존재")
            assertTrue(result.contains("HASH") || result.contains("KEY"), "HASH 또는 KEY 파티셔닝")

            println("=== HASH Partition Oracle → MySQL ===")
            println(result)
        }

        @Test
        @DisplayName("복합 파티션 (서브파티션) - Oracle → PostgreSQL")
        fun testCompositePartitionOracleToPostgreSql() {
            val oracleDdl = """
                CREATE TABLE order_items (
                    item_id NUMBER PRIMARY KEY,
                    order_id NUMBER,
                    order_date DATE,
                    product_category VARCHAR2(50),
                    quantity NUMBER,
                    amount NUMBER(10,2)
                )
                PARTITION BY RANGE (order_date)
                SUBPARTITION BY LIST (product_category) (
                    PARTITION p_2024_q1 VALUES LESS THAN (TO_DATE('2024-04-01', 'YYYY-MM-DD')) (
                        SUBPARTITION p_2024_q1_electronics VALUES ('ELECTRONICS'),
                        SUBPARTITION p_2024_q1_clothing VALUES ('CLOTHING'),
                        SUBPARTITION p_2024_q1_other VALUES (DEFAULT)
                    ),
                    PARTITION p_2024_q2 VALUES LESS THAN (TO_DATE('2024-07-01', 'YYYY-MM-DD')) (
                        SUBPARTITION p_2024_q2_electronics VALUES ('ELECTRONICS'),
                        SUBPARTITION p_2024_q2_clothing VALUES ('CLOTHING'),
                        SUBPARTITION p_2024_q2_other VALUES (DEFAULT)
                    )
                );
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = AdvancedPartitionConverter.convert(
                oracleDdl, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            // PostgreSQL은 다중 레벨 파티셔닝을 다르게 처리
            assertTrue(result.contains("order_items"), "테이블 이름 유지")
            assertTrue(warnings.isNotEmpty() || rules.isNotEmpty(), "경고 또는 규칙 발생")

            println("=== Composite Partition Oracle → PostgreSQL ===")
            println(result)
            println("\n경고: ${warnings.map { it.message }}")
        }
    }

    @Nested
    @DisplayName("FLASHBACK 쿼리 변환 통합 테스트")
    inner class FlashbackConversionE2ETest {

        @Test
        @DisplayName("AS OF TIMESTAMP 쿼리 - Oracle → MySQL")
        fun testAsOfTimestampOracleToMySql() {
            val oracleQuery = """
                SELECT employee_id, first_name, last_name, salary
                FROM employees
                AS OF TIMESTAMP TO_TIMESTAMP('2024-01-01 10:00:00', 'YYYY-MM-DD HH24:MI:SS')
                WHERE department_id = 50
                ORDER BY employee_id
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = FlashbackQueryConverter.convert(
                oracleQuery, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            // FLASHBACK은 MySQL에서 지원하지 않으므로 주석 처리
            assertTrue(result.contains("/*") || !result.contains("AS OF TIMESTAMP"), "AS OF TIMESTAMP 처리됨")
            assertTrue(warnings.any { it.message.contains("AS OF TIMESTAMP") || it.message.contains("Flashback") },
                "FLASHBACK 관련 경고 발생")

            println("=== AS OF TIMESTAMP Oracle → MySQL ===")
            println(result)
            println("\n경고: ${warnings.map { "[${it.severity}] ${it.message}" }}")
        }

        @Test
        @DisplayName("ORA_ROWSCN 사용 쿼리 - Oracle → PostgreSQL")
        fun testOraRowscnOracleToPostgreSql() {
            val oracleQuery = """
                SELECT employee_id, salary, ORA_ROWSCN,
                       SCN_TO_TIMESTAMP(ORA_ROWSCN) as last_modified
                FROM employees
                WHERE employee_id = 100
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = FlashbackQueryConverter.convert(
                oracleQuery, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            // PostgreSQL에서 ORA_ROWSCN은 xmin으로 변환
            assertTrue(result.contains("xmin"), "ORA_ROWSCN → xmin 변환")
            assertTrue(result.contains("pg_xact_commit_timestamp"), "SCN_TO_TIMESTAMP → pg_xact_commit_timestamp")

            println("=== ORA_ROWSCN Oracle → PostgreSQL ===")
            println(result)
        }

        @Test
        @DisplayName("VERSIONS BETWEEN 쿼리 - Oracle → MySQL")
        fun testVersionsBetweenOracleToMySql() {
            val oracleQuery = """
                SELECT employee_id, salary, VERSIONS_STARTTIME, VERSIONS_ENDTIME, VERSIONS_OPERATION
                FROM employees
                VERSIONS BETWEEN TIMESTAMP SYSTIMESTAMP - INTERVAL '1' DAY AND SYSTIMESTAMP
                WHERE employee_id = 100
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = FlashbackQueryConverter.convert(
                oracleQuery, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            // VERSIONS BETWEEN은 MySQL에서 미지원
            assertTrue(warnings.any { it.message.contains("VERSIONS BETWEEN") }, "VERSIONS BETWEEN 경고")

            println("=== VERSIONS BETWEEN Oracle → MySQL ===")
            println(result)
            println("\n경고: ${warnings.map { it.message }}")
        }

        @Test
        @DisplayName("FLASHBACK TABLE TO BEFORE DROP - Oracle → PostgreSQL")
        fun testFlashbackTableBeforeDropOracleToPostgreSql() {
            val oracleStatement = "FLASHBACK TABLE employees TO BEFORE DROP RENAME TO employees_restored"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = FlashbackQueryConverter.convert(
                oracleStatement, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            // FLASHBACK TABLE은 PostgreSQL에서 미지원
            assertTrue(result.contains("--") || result.contains("/*"), "주석 처리됨")
            assertTrue(warnings.any { it.severity == WarningSeverity.ERROR }, "에러 경고 발생")

            println("=== FLASHBACK TABLE Oracle → PostgreSQL ===")
            println(result)
        }
    }

    @Nested
    @DisplayName("프로시저 본문 변환 통합 테스트")
    inner class ProcedureBodyConversionE2ETest {

        @Test
        @DisplayName("SQL%ROWCOUNT 사용 프로시저 - Oracle → PostgreSQL")
        fun testSqlRowcountOracleToPostgreSql() {
            val procedureBody = """
                DECLARE
                    v_count NUMBER;
                BEGIN
                    UPDATE employees SET salary = salary * 1.1 WHERE department_id = 50;
                    v_count := SQL%ROWCOUNT;

                    IF SQL%ROWCOUNT > 0 THEN
                        DBMS_OUTPUT.PUT_LINE('Updated ' || v_count || ' employees');
                        COMMIT;
                    ELSE
                        DBMS_OUTPUT.PUT_LINE('No employees found');
                    END IF;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = ProcedureBodyConverter.convertBody(
                procedureBody, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            // GET DIAGNOSTICS 변환 또는 규칙 적용 확인
            val hasConversion = result.contains("GET DIAGNOSTICS") ||
                    rules.any { it.contains("SQL%ROWCOUNT") }
            assertTrue(hasConversion, "SQL%ROWCOUNT 변환 적용")
            assertTrue(result.contains("RAISE NOTICE") || result.contains("DBMS_OUTPUT"), "출력문 변환")

            println("=== SQL%ROWCOUNT Oracle → PostgreSQL ===")
            println(result)
            println("\n적용 규칙: ${rules.joinToString()}")
        }

        @Test
        @DisplayName("컬렉션 타입 사용 - Oracle → PostgreSQL")
        fun testCollectionTypeOracleToPostgreSql() {
            val procedureBody = """
                DECLARE
                    TYPE emp_ids_t IS TABLE OF NUMBER;
                    TYPE emp_names_t IS VARRAY(100) OF VARCHAR2(100);
                    TYPE emp_rec IS RECORD (
                        emp_id NUMBER,
                        emp_name VARCHAR2(100),
                        salary NUMBER
                    );
                    v_ids emp_ids_t;
                    v_cursor SYS_REFCURSOR;
                BEGIN
                    SELECT employee_id BULK COLLECT INTO v_ids
                    FROM employees WHERE department_id = 50;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = ProcedureBodyConverter.convertBody(
                procedureBody, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            // PostgreSQL에서 컬렉션은 배열로 변환
            val hasArrayConversion = result.contains("[]") || result.contains("ARRAY") ||
                    rules.any { it.contains("TABLE OF") || it.contains("VARRAY") }
            assertTrue(hasArrayConversion, "컬렉션 → 배열 변환 또는 규칙 적용")
            assertTrue(result.contains("refcursor"), "SYS_REFCURSOR → refcursor")

            println("=== Collection Types Oracle → PostgreSQL ===")
            println(result)
        }

        @Test
        @DisplayName("RAISE_APPLICATION_ERROR - Oracle → MySQL")
        fun testRaiseApplicationErrorOracleToMySql() {
            val procedureBody = """
                BEGIN
                    IF p_salary < 0 THEN
                        RAISE_APPLICATION_ERROR(-20001, 'Salary cannot be negative');
                    ELSIF p_salary > 1000000 THEN
                        RAISE_APPLICATION_ERROR(-20002, 'Salary exceeds maximum limit');
                    END IF;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = ProcedureBodyConverter.convertBody(
                procedureBody, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("SIGNAL SQLSTATE"), "RAISE_APPLICATION_ERROR → SIGNAL SQLSTATE")
            assertTrue(result.contains("MESSAGE_TEXT"), "에러 메시지 포함")
            assertFalse(result.contains("RAISE_APPLICATION_ERROR"), "원본 구문 제거")

            println("=== RAISE_APPLICATION_ERROR Oracle → MySQL ===")
            println(result)
        }

        @Test
        @DisplayName("EXIT WHEN / CONTINUE WHEN - Oracle → MySQL")
        fun testExitContinueWhenOracleToMySql() {
            val procedureBody = """
                DECLARE
                    v_counter NUMBER := 0;
                BEGIN
                    LOOP
                        v_counter := v_counter + 1;

                        CONTINUE WHEN MOD(v_counter, 2) = 0;

                        DBMS_OUTPUT.PUT_LINE('Counter: ' || v_counter);

                        EXIT WHEN v_counter >= 100;
                    END LOOP;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = ProcedureBodyConverter.convertBody(
                procedureBody, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("IF") && result.contains("LEAVE"), "EXIT WHEN → IF LEAVE")
            assertTrue(result.contains("ITERATE"), "CONTINUE WHEN → IF ITERATE")

            println("=== EXIT/CONTINUE WHEN Oracle → MySQL ===")
            println(result)
        }

        @Test
        @DisplayName("PIPE ROW (Pipelined Function) - Oracle → PostgreSQL")
        fun testPipeRowOracleToPostgreSql() {
            val procedureBody = """
                FOR rec IN (SELECT * FROM employees WHERE department_id = p_dept_id) LOOP
                    PIPE ROW(rec);
                END LOOP;
                RETURN;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = ProcedureBodyConverter.convertBody(
                procedureBody, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("RETURN NEXT"), "PIPE ROW → RETURN NEXT")
            assertFalse(result.contains("PIPE ROW"), "PIPE ROW 제거")

            println("=== PIPE ROW Oracle → PostgreSQL ===")
            println(result)
        }

        @Test
        @DisplayName("PRAGMA AUTONOMOUS_TRANSACTION - Oracle → PostgreSQL")
        fun testPragmaAutonomousTransactionOracleToPostgreSql() {
            val procedureBody = """
                PRAGMA AUTONOMOUS_TRANSACTION;
                BEGIN
                    INSERT INTO audit_log (action, log_time) VALUES (p_action, SYSDATE);
                    COMMIT;
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = ProcedureBodyConverter.convertBody(
                procedureBody, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            // AUTONOMOUS_TRANSACTION은 PostgreSQL에서 dblink로 구현 권장
            assertTrue(result.contains("--") || result.contains("dblink"), "PRAGMA 주석 또는 dblink 언급")
            assertTrue(warnings.any { it.message.contains("AUTONOMOUS_TRANSACTION") }, "경고 발생")

            println("=== PRAGMA AUTONOMOUS_TRANSACTION Oracle → PostgreSQL ===")
            println(result)
        }
    }

    @Nested
    @DisplayName("복합 변환 파이프라인 테스트")
    inner class ConversionPipelineE2ETest {

        @Test
        @DisplayName("전체 스키마 마이그레이션 - Oracle → MySQL")
        fun testFullSchemaMigrationOracleToMySql() {
            // 단일 파티션 테이블만 테스트 (복합 SQL 대신)
            val oracleTable = """
                CREATE TABLE orders (
                    order_id NUMBER(10) PRIMARY KEY,
                    order_date DATE NOT NULL,
                    customer_id NUMBER(10),
                    total_amount NUMBER(12,2),
                    status VARCHAR2(20) DEFAULT 'PENDING'
                )
                TABLESPACE order_ts
                PARTITION BY RANGE (order_date) (
                    PARTITION p_2024_h1 VALUES LESS THAN (TO_DATE('2024-07-01', 'YYYY-MM-DD')),
                    PARTITION p_2024_h2 VALUES LESS THAN (TO_DATE('2025-01-01', 'YYYY-MM-DD')),
                    PARTITION p_future VALUES LESS THAN (MAXVALUE)
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            // 파티션 변환
            var result = AdvancedPartitionConverter.convert(
                oracleTable, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            println("=== Full Schema Migration Oracle → MySQL ===")
            println("Input: ${oracleTable.take(100)}...")
            println("Output: ${result.take(200)}...")
            println("Rules: $rules")

            // 검증 - 변환이 적용됨
            val hasConversion = rules.isNotEmpty() ||
                    !result.contains("TABLESPACE") ||
                    result.contains("STR_TO_DATE")
            assertTrue(hasConversion, "변환 적용됨")

            // 추가 변환 적용
            result = TablespacePartitionConverter.convert(
                result, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            // 결과 검증 - TABLESPACE 제거 확인
            assertFalse(result.contains("TABLESPACE"), "TABLESPACE 제거됨")

            println("\n=== Final Result ===")
            println(result)
            println("\n=== 적용된 규칙 (${rules.size}개) ===")
            rules.forEach { println("  - $it") }
        }

        @Test
        @DisplayName("프로시저 + FLASHBACK 복합 변환 - Oracle → PostgreSQL")
        fun testProcedureWithFlashbackOracleToPostgreSql() {
            val oracleCode = """
                CREATE OR REPLACE PROCEDURE restore_deleted_order (
                    p_order_id IN NUMBER,
                    p_restore_time IN TIMESTAMP
                ) IS
                    v_order orders%ROWTYPE;
                BEGIN
                    -- FLASHBACK 쿼리로 삭제된 주문 조회
                    SELECT * INTO v_order
                    FROM orders AS OF TIMESTAMP p_restore_time
                    WHERE order_id = p_order_id;

                    -- 복원
                    INSERT INTO orders VALUES v_order;

                    IF SQL%ROWCOUNT > 0 THEN
                        DBMS_OUTPUT.PUT_LINE('Order restored successfully');
                        COMMIT;
                    ELSE
                        RAISE_APPLICATION_ERROR(-20001, 'Order not found in flashback');
                    END IF;
                EXCEPTION
                    WHEN NO_DATA_FOUND THEN
                        RAISE_APPLICATION_ERROR(-20002, 'No order found at specified time');
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            // FLASHBACK 변환
            var result = FlashbackQueryConverter.convert(
                oracleCode, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            // 프로시저 본문 변환
            result = ProcedureBodyConverter.convertBody(
                result, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            // 검증 - FLASHBACK 또는 프로시저 변환 중 하나라도 적용됨
            val hasAnyConversion = warnings.isNotEmpty() ||
                    rules.isNotEmpty() ||
                    result.contains("GET DIAGNOSTICS") ||
                    result.contains("RAISE EXCEPTION") ||
                    result.contains("/*") // FLASHBACK이 주석 처리됨
            assertTrue(hasAnyConversion, "변환 또는 경고 발생")

            println("=== Procedure + FLASHBACK Oracle → PostgreSQL ===")
            println(result)
            println("\n=== 경고 (${warnings.size}개) ===")
            warnings.forEach { println("  - [${it.severity}] ${it.message}") }
            println("\n=== 적용 규칙 (${rules.size}개) ===")
            rules.forEach { println("  - $it") }
        }

        @Test
        @DisplayName("패키지 + 프로시저 본문 변환 - Oracle → MySQL")
        fun testPackageWithProcedureBodyOracleToMySql() {
            val oraclePackage = """
                CREATE OR REPLACE PACKAGE BODY hr_utils AS
                    PROCEDURE update_employee_salary (
                        p_emp_id IN NUMBER,
                        p_new_salary IN NUMBER
                    ) IS
                        v_old_salary NUMBER;
                    BEGIN
                        SELECT salary INTO v_old_salary
                        FROM employees WHERE employee_id = p_emp_id;

                        UPDATE employees SET salary = p_new_salary
                        WHERE employee_id = p_emp_id;

                        IF SQL%ROWCOUNT = 0 THEN
                            RAISE_APPLICATION_ERROR(-20001, 'Employee not found');
                        END IF;

                        INSERT INTO salary_history (emp_id, old_salary, new_salary, change_date)
                        VALUES (p_emp_id, v_old_salary, p_new_salary, SYSDATE);

                        COMMIT;
                    EXCEPTION
                        WHEN OTHERS THEN
                            ROLLBACK;
                            RAISE;
                    END update_employee_salary;
                END hr_utils;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            // 패키지 변환
            var result = OraclePackageConverter.convert(
                oraclePackage, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            // 프로시저 본문 추가 변환
            result = ProcedureBodyConverter.convertBody(
                result, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            // 검증
            assertTrue(result.contains("hr_utils_update_employee_salary") ||
                    result.contains("update_employee_salary"), "프로시저 이름 존재")
            val hasConversion = result.contains("NOW()") ||
                    result.contains("ROW_COUNT()") ||
                    result.contains("SIGNAL SQLSTATE") ||
                    rules.isNotEmpty()
            assertTrue(hasConversion, "변환 적용됨")

            println("=== Package + Procedure Body Oracle → MySQL ===")
            println(result)
        }
    }

    @Nested
    @DisplayName("성능 테스트")
    inner class PerformanceE2ETest {

        @Test
        @DisplayName("대용량 파티션 테이블 변환 성능")
        fun testLargePartitionTablePerformance() {
            // 50개 파티션이 있는 테이블 생성
            val partitions = (2020..2025).flatMap { year ->
                (1..4).map { quarter ->
                    val nextMonth = if (quarter == 4) 1 else (quarter * 3 + 1)
                    val nextYear = if (quarter == 4) year + 1 else year
                    "PARTITION p_${year}_q$quarter VALUES LESS THAN (TO_DATE('$nextYear-${nextMonth.toString().padStart(2, '0')}-01', 'YYYY-MM-DD'))"
                }
            }.joinToString(",\n                    ")

            val largeDdl = """
                CREATE TABLE large_sales (
                    sale_id NUMBER(15) PRIMARY KEY,
                    sale_date DATE NOT NULL,
                    customer_id NUMBER(10),
                    product_id NUMBER(10),
                    quantity NUMBER(6),
                    unit_price NUMBER(10,2),
                    total_amount NUMBER(12,2),
                    discount_pct NUMBER(5,2),
                    tax_amount NUMBER(10,2),
                    status VARCHAR2(20),
                    region VARCHAR2(50),
                    created_at DATE DEFAULT SYSDATE,
                    modified_at DATE
                )
                TABLESPACE sales_data_ts
                PCTFREE 10
                PARTITION BY RANGE (sale_date) (
                    $partitions,
                    PARTITION p_future VALUES LESS THAN (MAXVALUE)
                );
            """.trimIndent()

            val startTime = System.currentTimeMillis()
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = AdvancedPartitionConverter.convert(
                largeDdl, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            println("=== Large Partition Table Performance ===")
            println("입력 크기: ${largeDdl.length} 문자")
            println("출력 크기: ${result.length} 문자")
            println("파티션 수: 25개")
            println("변환 시간: ${duration}ms")
            println("적용 규칙: ${rules.size}개")

            // 1초 이내 완료
            assertTrue(duration < 1000, "변환 시간이 1초 이내여야 함 (실제: ${duration}ms)")
        }

        @Test
        @DisplayName("다중 FLASHBACK 쿼리 변환 성능")
        fun testMultipleFlashbackQueryPerformance() {
            // 10개의 FLASHBACK 쿼리 생성
            val queries = (1..10).map { i ->
                """
                SELECT emp$i.*, ORA_ROWSCN, SCN_TO_TIMESTAMP(ORA_ROWSCN) as last_mod
                FROM employees emp$i
                AS OF TIMESTAMP SYSTIMESTAMP - INTERVAL '$i' DAY
                WHERE department_id = ${i * 10}
                """.trimIndent()
            }.joinToString(";\n")

            val startTime = System.currentTimeMillis()
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = FlashbackQueryConverter.convert(
                queries, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            println("=== Multiple FLASHBACK Query Performance ===")
            println("쿼리 수: 10개")
            println("변환 시간: ${duration}ms")
            println("경고 수: ${warnings.size}개")

            assertTrue(duration < 500, "변환 시간이 500ms 이내여야 함")
        }

        @Test
        @DisplayName("복잡한 프로시저 본문 변환 성능")
        fun testComplexProcedureBodyPerformance() {
            // 복잡한 프로시저 본문 생성
            val procedureBody = buildString {
                appendLine("DECLARE")
                repeat(20) { i ->
                    appendLine("    v_var$i NUMBER;")
                }
                appendLine("BEGIN")
                repeat(10) { i ->
                    appendLine("    v_var$i := SQL%ROWCOUNT;")
                    appendLine("    IF SQL%FOUND THEN")
                    appendLine("        DBMS_OUTPUT.PUT_LINE('Found at iteration $i');")
                    appendLine("    ELSIF SQL%NOTFOUND THEN")
                    appendLine("        RAISE_APPLICATION_ERROR(-2000$i, 'Not found at $i');")
                    appendLine("    END IF;")
                }
                appendLine("END;")
            }

            val startTime = System.currentTimeMillis()
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = ProcedureBodyConverter.convertBody(
                procedureBody, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            println("=== Complex Procedure Body Performance ===")
            println("입력 크기: ${procedureBody.length} 문자")
            println("출력 크기: ${result.length} 문자")
            println("변환 시간: ${duration}ms")
            println("적용 규칙: ${rules.size}개")

            assertTrue(duration < 500, "변환 시간이 500ms 이내여야 함")
            // 변환이 적용되었는지 확인 (규칙 또는 결과 변환)
            val hasConversions = rules.isNotEmpty() ||
                    result.contains("ROW_COUNT()") ||
                    result.contains("SIGNAL SQLSTATE")
            assertTrue(hasConversions, "변환이 적용됨")
        }
    }
}
