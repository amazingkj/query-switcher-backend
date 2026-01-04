package com.sqlswitcher.converter

import com.sqlswitcher.converter.stringbased.StringBasedFunctionConverter
import com.sqlswitcher.converter.util.SqlRegexPatterns
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * 대용량 SQL 변환 성능 테스트
 */
class PerformanceTest {

    private lateinit var functionConverter: StringBasedFunctionConverter

    @BeforeEach
    fun setup() {
        functionConverter = StringBasedFunctionConverter()
    }

    @Nested
    @DisplayName("StringBasedFunctionConverter 성능 테스트")
    inner class FunctionConverterPerformanceTest {

        @Test
        @DisplayName("작은 SQL (100자 미만) 변환 성능")
        fun testSmallSqlPerformance() {
            val sql = "SELECT NVL(name, 'Unknown') FROM employees WHERE ROWNUM < 10"
            val appliedRules = mutableListOf<String>()

            val time = measureTimeMillis {
                repeat(1000) {
                    functionConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, appliedRules.toMutableList())
                }
            }

            println("작은 SQL 1000회 변환 시간: ${time}ms (평균: ${time / 1000.0}ms)")
            assertTrue(time < 5000, "작은 SQL 1000회 변환은 5초 이내여야 합니다")
        }

        @Test
        @DisplayName("중간 SQL (500자) 변환 성능")
        fun testMediumSqlPerformance() {
            val sql = buildMediumFunctionSql()
            val appliedRules = mutableListOf<String>()

            val time = measureTimeMillis {
                repeat(100) {
                    functionConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, appliedRules.toMutableList())
                }
            }

            println("중간 SQL 100회 변환 시간: ${time}ms (평균: ${time / 100.0}ms)")
            assertTrue(time < 5000, "중간 SQL 100회 변환은 5초 이내여야 합니다")
        }

        @Test
        @DisplayName("대형 SQL (2000자) 변환 성능")
        fun testLargeSqlPerformance() {
            val sql = buildLargeFunctionSql()
            val appliedRules = mutableListOf<String>()

            val time = measureTimeMillis {
                repeat(50) {
                    functionConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, appliedRules.toMutableList())
                }
            }

            println("대형 SQL 50회 변환 시간: ${time}ms (평균: ${time / 50.0}ms)")
            assertTrue(time < 10000, "대형 SQL 50회 변환은 10초 이내여야 합니다")
        }

        @Test
        @DisplayName("중첩 DECODE가 많은 SQL 성능")
        fun testMultipleDecodePerformance() {
            val sql = buildMultipleDecodeSql()
            val appliedRules = mutableListOf<String>()

            val time = measureTimeMillis {
                repeat(50) {
                    functionConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, appliedRules.toMutableList())
                }
            }

            println("다중 DECODE SQL 50회 변환 시간: ${time}ms (평균: ${time / 50.0}ms)")
            assertTrue(time < 5000, "다중 DECODE SQL 50회 변환은 5초 이내여야 합니다")
        }

        @Test
        @DisplayName("다이얼렉트별 변환 성능 비교")
        fun testDialectComparisonPerformance() {
            val sql = buildMediumFunctionSql()
            val iterations = 100

            val oracleToMySql = measureTimeMillis {
                repeat(iterations) {
                    functionConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, mutableListOf())
                }
            }

            val oracleToPostgres = measureTimeMillis {
                repeat(iterations) {
                    functionConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, mutableListOf())
                }
            }

            val mySqlToPostgres = measureTimeMillis {
                val mySql = buildMySqlFunctionSql()
                repeat(iterations) {
                    functionConverter.convert(mySql, DialectType.MYSQL, DialectType.POSTGRESQL, mutableListOf())
                }
            }

            println("=== 다이얼렉트별 변환 성능 (${iterations}회) ===")
            println("Oracle → MySQL: ${oracleToMySql}ms (평균: ${oracleToMySql / iterations.toDouble()}ms)")
            println("Oracle → PostgreSQL: ${oracleToPostgres}ms (평균: ${oracleToPostgres / iterations.toDouble()}ms)")
            println("MySQL → PostgreSQL: ${mySqlToPostgres}ms (평균: ${mySqlToPostgres / iterations.toDouble()}ms)")
        }

        private fun buildMediumFunctionSql(): String = """
            SELECT
                NVL(e.name, 'Unknown') AS employee_name,
                TO_CHAR(e.hire_date, 'YYYY-MM-DD') AS formatted_date,
                DECODE(e.status, 'A', 'Active', 'I', 'Inactive', 'Unknown') AS status_text,
                SUBSTR(e.description, 1, 100) AS short_desc,
                MONTHS_BETWEEN(SYSDATE, e.hire_date) AS months_employed
            FROM employees e
            WHERE e.department_id IN (10, 20, 30)
              AND ROWNUM < 100
            ORDER BY e.name
        """.trimIndent()

        private fun buildLargeFunctionSql(): String = """
            SELECT
                e.employee_id,
                NVL(e.first_name, 'N/A') || ' ' || NVL(e.last_name, 'N/A') AS full_name,
                TO_CHAR(e.hire_date, 'YYYY-MM-DD HH24:MI:SS') AS hire_datetime,
                DECODE(e.status, 'A', 'Active', 'I', 'Inactive', 'T', 'Terminated', 'P', 'Pending', 'Unknown') AS status_desc,
                NVL2(e.manager_id, (SELECT m.first_name FROM employees m WHERE m.employee_id = e.manager_id), 'No Manager') AS manager_name,
                ROUND(e.salary * 1.1, 2) AS projected_salary,
                TRUNC(MONTHS_BETWEEN(SYSDATE, e.hire_date)) AS tenure_months,
                DECODE(TRUNC(MONTHS_BETWEEN(SYSDATE, e.hire_date) / 12),
                    0, 'New',
                    1, '1 Year',
                    2, '2 Years',
                    'Senior') AS tenure_category,
                LISTAGG(p.project_name, ', ') WITHIN GROUP (ORDER BY p.start_date) AS projects,
                ADD_MONTHS(e.hire_date, 12) AS review_date,
                CASE WHEN e.commission_pct IS NOT NULL THEN e.salary * (1 + e.commission_pct) ELSE e.salary END AS total_comp
            FROM employees e
            LEFT JOIN employee_projects ep ON e.employee_id = ep.employee_id
            LEFT JOIN projects p ON ep.project_id = p.project_id
            WHERE e.department_id IN (
                SELECT department_id FROM departments WHERE location_id IN (1000, 1100, 1200)
            )
            GROUP BY e.employee_id, e.first_name, e.last_name, e.hire_date, e.status, e.manager_id, e.salary, e.commission_pct
            HAVING COUNT(p.project_id) >= 2
            ORDER BY tenure_months DESC, full_name
        """.trimIndent()

        private fun buildMultipleDecodeSql(): String {
            val decodes = (1..15).map { i ->
                "DECODE(field$i, 'A', 'ValueA$i', 'B', 'ValueB$i', 'C', 'ValueC$i', 'D', 'ValueD$i', 'Default$i') AS decoded$i"
            }.joinToString(",\n            ")

            return """
                SELECT
                    id,
                    $decodes
                FROM multi_decode_table
                WHERE status = 'ACTIVE'
            """.trimIndent()
        }

        private fun buildMySqlFunctionSql(): String = """
            SELECT
                IFNULL(name, 'Unknown') AS name,
                DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS formatted_date,
                IF(status = 'A', 'Active', 'Inactive') AS status_text,
                GROUP_CONCAT(tag ORDER BY tag SEPARATOR ', ') AS tags,
                DATEDIFF(NOW(), created_at) AS days_old
            FROM users u
            JOIN user_tags ut ON u.id = ut.user_id
            WHERE u.active = 1
            GROUP BY u.id
            LIMIT 100
        """.trimIndent()
    }

    @Test
    fun `정규식 캐싱 성능 비교 테스트`() {
        val testSql = """
            CREATE TABLE test_table (
                id NUMBER PRIMARY KEY,
                name VARCHAR2(100) DEFAULT SYSDATE,
                created_at TIMESTAMP DEFAULT SYSTIMESTAMP
            )
            TABLESPACE users
            STORAGE (INITIAL 64K NEXT 64K)
            PCTFREE 10
            LOGGING
            NOPARALLEL
        """.trimIndent()

        val iterations = 10000

        // 캐시되지 않은 정규식 (매번 새로 생성)
        val uncachedTime = measureTimeMillis {
            repeat(iterations) {
                val pattern1 = Regex("""DEFAULT\s+SYSDATE\b""", RegexOption.IGNORE_CASE)
                val pattern2 = Regex("""DEFAULT\s+SYSTIMESTAMP\b""", RegexOption.IGNORE_CASE)
                pattern1.containsMatchIn(testSql)
                pattern2.containsMatchIn(testSql)
            }
        }

        // 캐시된 정규식 (SqlRegexPatterns 사용)
        val cachedTime = measureTimeMillis {
            repeat(iterations) {
                SqlRegexPatterns.STORAGE_CLAUSE.containsMatchIn(testSql)
                SqlRegexPatterns.TABLESPACE.containsMatchIn(testSql)
            }
        }

        println("=== 정규식 캐싱 성능 테스트 ===")
        println("반복 횟수: $iterations")
        println("캐시되지 않은 정규식: ${uncachedTime}ms")
        println("캐시된 정규식: ${cachedTime}ms")
        println("성능 향상: ${String.format("%.2f", uncachedTime.toDouble() / cachedTime)}배")
    }

    @Test
    fun `대용량 SQL 문자열 정규식 성능 테스트`() {
        // 대용량 SQL 생성 (약 100KB)
        val largeSql = buildString {
            repeat(1000) { i ->
                appendLine("""
                    CREATE TABLE table_$i (
                        id NUMBER PRIMARY KEY,
                        col1 VARCHAR2(100) DEFAULT SYSDATE,
                        col2 VARCHAR2(200),
                        col3 NUMBER(10,2),
                        col4 TIMESTAMP DEFAULT SYSTIMESTAMP
                    )
                    TABLESPACE users
                    STORAGE (INITIAL 64K NEXT 64K MINEXTENTS 1 MAXEXTENTS UNLIMITED)
                    PCTFREE 10
                    LOGGING;
                """.trimIndent())
            }
        }

        println("=== 대용량 SQL 성능 테스트 ===")
        println("SQL 크기: ${largeSql.length / 1024}KB")

        val iterations = 100

        // 여러 패턴 적용 테스트
        val totalTime = measureTimeMillis {
            repeat(iterations) {
                var result = largeSql
                result = SqlRegexPatterns.STORAGE_CLAUSE.replace(result, "")
                result = SqlRegexPatterns.TABLESPACE.replace(result, "")
                SqlRegexPatterns.PHYSICAL_OPTIONS_LINE.replace(result, "")
            }
        }

        println("반복 횟수: $iterations")
        println("총 소요 시간: ${totalTime}ms")
        println("평균 처리 시간: ${totalTime / iterations}ms/회")
    }

    @Test
    fun `복잡한 Oracle DDL 변환 성능 테스트`() {
        val complexDdl = """
            CREATE TABLE "SCHEMA"."COMPLEX_TABLE" (
                "ID" NUMBER(19,0) NOT NULL,
                "NAME" VARCHAR2(255 CHAR) NOT NULL,
                "DESCRIPTION" CLOB,
                "AMOUNT" NUMBER(19,4) DEFAULT 0,
                "STATUS" VARCHAR2(20 CHAR) DEFAULT 'ACTIVE',
                "CREATED_AT" TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
                "UPDATED_AT" TIMESTAMP(6) DEFAULT SYSTIMESTAMP,
                "VERSION" NUMBER(10,0) DEFAULT 0,
                CONSTRAINT "PK_COMPLEX" PRIMARY KEY ("ID")
            )
            TABLESPACE "DATA_TS"
            PCTFREE 10
            INITRANS 2
            MAXTRANS 255
            STORAGE (
                INITIAL 65536
                NEXT 1048576
                MINEXTENTS 1
                MAXEXTENTS 2147483645
                PCTINCREASE 0
                FREELISTS 1
                FREELIST GROUPS 1
                BUFFER_POOL DEFAULT
            )
            SEGMENT CREATION IMMEDIATE
            LOGGING
            NOCOMPRESS
            NOPARALLEL
            MONITORING
            ENABLE ROW MOVEMENT;

            CREATE UNIQUE INDEX "SCHEMA"."IDX_COMPLEX_NAME" ON "SCHEMA"."COMPLEX_TABLE" ("NAME")
            TABLESPACE "INDEX_TS"
            PCTFREE 10
            INITRANS 2
            MAXTRANS 255
            LOGGING
            LOCAL;

            COMMENT ON TABLE "SCHEMA"."COMPLEX_TABLE" IS '복잡한 테이블 예제';
            COMMENT ON COLUMN "SCHEMA"."COMPLEX_TABLE"."ID" IS '기본 키';
        """.trimIndent()

        val iterations = 1000

        val time = measureTimeMillis {
            repeat(iterations) {
                var result = complexDdl
                // Oracle 옵션 제거 시뮬레이션
                result = SqlRegexPatterns.STORAGE_CLAUSE.replace(result, "")
                result = SqlRegexPatterns.TABLESPACE.replace(result, "")
                result = SqlRegexPatterns.LOCAL_INDEX.replace(result, "")
                result = SqlRegexPatterns.GLOBAL_INDEX.replace(result, "")
                result = SqlRegexPatterns.PHYSICAL_OPTIONS_LINE.replace(result, "")
                result = SqlRegexPatterns.COMPRESS_LINE.replace(result, "")
                SqlRegexPatterns.CONSTRAINT_STATE.replace(result, "")
            }
        }

        println("=== 복잡한 Oracle DDL 변환 성능 테스트 ===")
        println("DDL 크기: ${complexDdl.length} 바이트")
        println("반복 횟수: $iterations")
        println("총 소요 시간: ${time}ms")
        println("평균 처리 시간: ${String.format("%.3f", time.toDouble() / iterations)}ms/회")
    }

    @Test
    fun `동시 변환 성능 테스트`() {
        val testSqls = (1..100).map { i ->
            """
            SELECT t$i.*, NVL(t$i.col1, 'default') as col1_safe
            FROM table_$i t$i
            WHERE ROWNUM <= 100
            AND t$i.created_at >= SYSDATE - 30
            ORDER BY t$i.id
            """.trimIndent()
        }

        val iterations = 100

        val time = measureTimeMillis {
            repeat(iterations) {
                testSqls.forEach { sql ->
                    SqlRegexPatterns.ROWNUM_WHERE.find(sql)
                    SqlRegexPatterns.NVL2_FUNCTION.find(sql)
                    SqlRegexPatterns.DECODE_START.find(sql)
                }
            }
        }

        println("=== 동시 변환 성능 테스트 ===")
        println("SQL 개수: ${testSqls.size}")
        println("반복 횟수: $iterations")
        println("총 처리 SQL: ${testSqls.size * iterations}")
        println("총 소요 시간: ${time}ms")
        println("처리량: ${String.format("%.0f", (testSqls.size * iterations * 1000.0) / time)} SQL/초")
    }
}