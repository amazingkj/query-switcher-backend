package com.sqlswitcher.converter

import com.sqlswitcher.converter.util.SqlRegexPatterns
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

/**
 * 대용량 SQL 변환 성능 테스트
 */
class PerformanceTest {

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