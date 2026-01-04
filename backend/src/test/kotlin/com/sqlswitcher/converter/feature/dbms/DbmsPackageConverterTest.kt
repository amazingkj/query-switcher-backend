package com.sqlswitcher.converter.feature.dbms

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
 * DbmsPackageConverter 단위 테스트
 */
class DbmsPackageConverterTest {

    private fun convert(sql: String, targetDialect: DialectType): Triple<String, List<ConversionWarning>, List<String>> {
        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()
        val result = DbmsPackageConverter.convert(
            sql,
            DialectType.ORACLE,
            targetDialect,
            warnings,
            rules
        )
        return Triple(result, warnings, rules)
    }

    @Nested
    @DisplayName("DBMS_OUTPUT 변환 테스트")
    inner class DbmsOutputTest {

        @Test
        @DisplayName("PUT_LINE → MySQL SELECT")
        fun testPutLineToMySql() {
            val sql = "DBMS_OUTPUT.PUT_LINE('Hello World')"

            val (result, warnings, rules) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("SELECT"), "SELECT로 변환되어야 함")
            assertTrue(result.contains("debug_output"), "debug_output 별칭이 있어야 함")
            assertTrue(rules.any { it.contains("DBMS_OUTPUT.PUT_LINE") })
        }

        @Test
        @DisplayName("PUT_LINE → PostgreSQL RAISE NOTICE")
        fun testPutLineToPostgreSql() {
            val sql = "DBMS_OUTPUT.PUT_LINE('Hello World')"

            val (result, _, rules) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("RAISE NOTICE"), "RAISE NOTICE로 변환되어야 함")
            assertTrue(rules.any { it.contains("DBMS_OUTPUT.PUT_LINE") })
        }

        @Test
        @DisplayName("PUT_LINE 변수 출력")
        fun testPutLineWithVariable() {
            val sql = "DBMS_OUTPUT.PUT_LINE(v_message)"

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("RAISE NOTICE"))
            assertTrue(result.contains("v_message"))
        }

        @Test
        @DisplayName("PUT → 변환")
        fun testPut() {
            val sql = "DBMS_OUTPUT.PUT('Partial output')"

            val (resultMySql, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("SELECT"))
            assertTrue(resultPg.contains("RAISE NOTICE"))
        }
    }

    @Nested
    @DisplayName("DBMS_RANDOM 변환 테스트")
    inner class DbmsRandomTest {

        @Test
        @DisplayName("VALUE(범위) → MySQL RAND()")
        fun testValueRangeToMySql() {
            val sql = "SELECT DBMS_RANDOM.VALUE(1, 100) FROM dual"

            val (result, _, rules) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("RAND()"), "RAND()가 포함되어야 함")
            assertTrue(result.contains("100 - 1") || result.contains("(100 - 1)"), "범위 계산이 있어야 함")
            assertTrue(rules.any { it.contains("DBMS_RANDOM") })
        }

        @Test
        @DisplayName("VALUE(범위) → PostgreSQL random()")
        fun testValueRangeToPostgreSql() {
            val sql = "SELECT DBMS_RANDOM.VALUE(1, 100) FROM dual"

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("random()"), "random()이 포함되어야 함")
        }

        @Test
        @DisplayName("VALUE(범위없음) → random()")
        fun testValueNoRange() {
            val sql = "SELECT DBMS_RANDOM.VALUE FROM dual"

            val (resultMySql, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("RAND()"))
            assertTrue(resultPg.contains("random()"))
        }

        @Test
        @DisplayName("STRING 타입 변환")
        fun testRandomString() {
            val sql = "SELECT DBMS_RANDOM.STRING('U', 10) FROM dual"

            val (resultMySql, warningsMy, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, warningsPg, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("MD5") && resultMySql.contains("RAND"))
            assertTrue(resultPg.contains("md5") && resultPg.contains("random"))
            assertTrue(resultMySql.contains("UPPER"), "대문자 타입이어야 함")
            assertTrue(warningsMy.isNotEmpty() || warningsPg.isNotEmpty())
        }

        @Test
        @DisplayName("STRING 소문자 타입")
        fun testRandomStringLower() {
            val sql = "SELECT DBMS_RANDOM.STRING('L', 8) FROM dual"

            val (result, _, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("LOWER"))
        }
    }

    @Nested
    @DisplayName("DBMS_LOB 변환 테스트")
    inner class DbmsLobTest {

        @Test
        @DisplayName("GETLENGTH → LENGTH")
        fun testGetLength() {
            val sql = "SELECT DBMS_LOB.GETLENGTH(clob_column) FROM docs"

            val (resultMySql, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("LENGTH(clob_column)"))
            assertTrue(resultPg.contains("octet_length(clob_column)"))
        }

        @Test
        @DisplayName("SUBSTR → SUBSTRING")
        fun testSubstr() {
            val sql = "SELECT DBMS_LOB.SUBSTR(clob_column, 100, 1) FROM docs"

            val (resultMySql, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("SUBSTRING"))
            assertTrue(resultPg.contains("substring"))
        }

        @Test
        @DisplayName("INSTR → LOCATE/POSITION")
        fun testInstr() {
            val sql = "SELECT DBMS_LOB.INSTR(clob_column, 'text') FROM docs"

            val (resultMySql, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("LOCATE"))
            assertTrue(resultPg.contains("position"))
        }

        @Test
        @DisplayName("APPEND → CONCAT/연결")
        fun testAppend() {
            val sql = "DBMS_LOB.APPEND(dest_lob, src_lob)"

            val (resultMySql, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("CONCAT"))
            assertTrue(resultPg.contains("||"))
        }
    }

    @Nested
    @DisplayName("DBMS_UTILITY 변환 테스트")
    inner class DbmsUtilityTest {

        @Test
        @DisplayName("GET_TIME → 시간 함수")
        fun testGetTime() {
            val sql = "v_start := DBMS_UTILITY.GET_TIME"

            val (resultMySql, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("UNIX_TIMESTAMP"))
            assertTrue(resultPg.contains("clock_timestamp") || resultPg.contains("epoch"))
        }

        @Test
        @DisplayName("FORMAT_ERROR_BACKTRACE 변환")
        fun testFormatErrorBacktrace() {
            val sql = "v_trace := DBMS_UTILITY.FORMAT_ERROR_BACKTRACE"

            val (resultMySql, warningsMy, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("NULL") || resultMySql.contains("not supported"))
            assertTrue(resultPg.contains("PG_EXCEPTION_CONTEXT") || resultPg.contains("STACKED"))
            assertTrue(warningsMy.isNotEmpty())
        }

        @Test
        @DisplayName("FORMAT_ERROR_STACK 변환")
        fun testFormatErrorStack() {
            val sql = "v_error := DBMS_UTILITY.FORMAT_ERROR_STACK"

            val (resultMySql, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("NULL") || resultMySql.contains("not supported"))
            assertTrue(resultPg.contains("SQLERRM"))
        }
    }

    @Nested
    @DisplayName("DBMS_LOCK 변환 테스트")
    inner class DbmsLockTest {

        @Test
        @DisplayName("SLEEP → SLEEP/pg_sleep")
        fun testSleep() {
            val sql = "DBMS_LOCK.SLEEP(5)"

            val (resultMySql, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("SLEEP(5)"))
            assertTrue(resultPg.contains("pg_sleep(5)"))
        }

        @Test
        @DisplayName("SLEEP 변수 사용")
        fun testSleepWithVariable() {
            val sql = "DBMS_LOCK.SLEEP(v_seconds)"

            val (result, _, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("SLEEP(v_seconds)"))
        }
    }

    @Nested
    @DisplayName("DBMS_CRYPTO 변환 테스트")
    inner class DbmsCryptoTest {

        @Test
        @DisplayName("HASH MD5 → 해시 함수")
        fun testHashMd5() {
            val sql = "SELECT DBMS_CRYPTO.HASH(data, DBMS_CRYPTO.HASH_MD5) FROM t"

            val (resultMySql, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, warningsPg, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("MD5(data)"))
            assertTrue(resultPg.contains("digest(data, 'md5')"))
            assertTrue(warningsPg.any { it.message.contains("pgcrypto") })
        }

        @Test
        @DisplayName("HASH SHA256 → 해시 함수")
        fun testHashSha256() {
            val sql = "SELECT DBMS_CRYPTO.HASH(data, DBMS_CRYPTO.HASH_SHA256) FROM t"

            val (resultMySql, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMySql.contains("SHA2(data, 256)"))
            assertTrue(resultPg.contains("digest(data, 'sha256')"))
        }

        @Test
        @DisplayName("HASH SHA512 → 해시 함수")
        fun testHashSha512() {
            val sql = "SELECT DBMS_CRYPTO.HASH(data, DBMS_CRYPTO.HASH_SHA512) FROM t"

            val (resultMySql, _, _) = convert(sql, DialectType.MYSQL)

            assertTrue(resultMySql.contains("SHA2(data, 512)"))
        }

        @Test
        @DisplayName("ENCRYPT → 수동 변환 필요 경고")
        fun testEncrypt() {
            val sql = "DBMS_CRYPTO.ENCRYPT(data, algo, key)"

            val (_, warningsMy, _) = convert(sql, DialectType.MYSQL)
            val (_, warningsPg, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(warningsMy.any { it.severity == WarningSeverity.ERROR })
            assertTrue(warningsPg.any { it.severity == WarningSeverity.ERROR })
        }
    }

    @Nested
    @DisplayName("UTL_FILE 변환 테스트")
    inner class UtlFileTest {

        @Test
        @DisplayName("FOPEN → 경고")
        fun testFopen() {
            val sql = "UTL_FILE.FOPEN('/dir', 'file.txt', 'w')"

            val (result, warnings, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("NULL") || result.contains("not supported"))
            assertTrue(warnings.any { it.message.contains("UTL_FILE") })
            assertTrue(warnings.any { it.severity == WarningSeverity.ERROR })
        }

        @Test
        @DisplayName("PUT_LINE → 경고")
        fun testPutLine() {
            val sql = "UTL_FILE.PUT_LINE(file_handle, 'content')"

            val (result, warnings, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("NULL") || result.contains("not supported"))
            assertTrue(warnings.isNotEmpty())
        }

        @Test
        @DisplayName("FCLOSE → 경고")
        fun testFclose() {
            val sql = "UTL_FILE.FCLOSE(file_handle)"

            val (_, warnings, _) = convert(sql, DialectType.MYSQL)

            assertTrue(warnings.any { it.severity == WarningSeverity.ERROR })
        }
    }

    @Nested
    @DisplayName("UTL_HTTP 변환 테스트")
    inner class UtlHttpTest {

        @Test
        @DisplayName("REQUEST → 경고")
        fun testRequest() {
            val sql = "UTL_HTTP.REQUEST('http://example.com')"

            val (result, warnings, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("NULL") || result.contains("not supported"))
            assertTrue(warnings.any { it.message.contains("UTL_HTTP") })
        }

        @Test
        @DisplayName("BEGIN_REQUEST → 경고")
        fun testBeginRequest() {
            val sql = "req := UTL_HTTP.BEGIN_REQUEST(url)"

            val (_, warnings, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(warnings.any { it.severity == WarningSeverity.ERROR })
        }
    }

    @Nested
    @DisplayName("DBMS_SQL 변환 테스트")
    inner class DbmsSqlTest {

        @Test
        @DisplayName("OPEN_CURSOR → 경고")
        fun testOpenCursor() {
            val sql = "cursor_id := DBMS_SQL.OPEN_CURSOR"

            val (result, warnings, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("PREPARE") || result.contains("NULL"))
            assertTrue(warnings.any { it.message.contains("DBMS_SQL") })
        }

        @Test
        @DisplayName("PARSE → 경고")
        fun testParse() {
            val sql = "DBMS_SQL.PARSE(cursor_id, v_sql, DBMS_SQL.NATIVE)"

            val (_, warnings, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(warnings.any { it.suggestion?.contains("EXECUTE") == true })
        }
    }

    @Nested
    @DisplayName("DBMS_SCHEDULER 변환 테스트")
    inner class DbmsSchedulerTest {

        @Test
        @DisplayName("CREATE_JOB → 경고")
        fun testCreateJob() {
            val sql = "DBMS_SCHEDULER.CREATE_JOB(job_name => 'test_job')"

            val (result, warnings, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("Event Scheduler") || result.contains("NULL"))
            assertTrue(warnings.any { it.message.contains("DBMS_SCHEDULER") })
        }

        @Test
        @DisplayName("RUN_JOB → 경고 (PostgreSQL)")
        fun testRunJobPg() {
            val sql = "DBMS_SCHEDULER.RUN_JOB('test_job')"

            val (_, warnings, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(warnings.any { it.suggestion?.contains("pg_cron") == true })
        }
    }

    @Nested
    @DisplayName("RAISE_APPLICATION_ERROR 변환 테스트")
    inner class RaiseApplicationErrorTest {

        @Test
        @DisplayName("→ MySQL SIGNAL SQLSTATE")
        fun testToMySql() {
            val sql = "RAISE_APPLICATION_ERROR(-20001, 'Error message')"

            val (result, _, rules) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("SIGNAL SQLSTATE"))
            assertTrue(result.contains("45000"))
            assertTrue(result.contains("MESSAGE_TEXT"))
            assertTrue(rules.any { it.contains("RAISE_APPLICATION_ERROR") })
        }

        @Test
        @DisplayName("→ PostgreSQL RAISE EXCEPTION")
        fun testToPostgreSql() {
            val sql = "RAISE_APPLICATION_ERROR(-20001, 'Error message')"

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("RAISE EXCEPTION"))
            assertTrue(result.contains("-20001") || result.contains("20001"))
        }

        @Test
        @DisplayName("에러 메시지 변수 사용")
        fun testWithVariable() {
            val sql = "RAISE_APPLICATION_ERROR(-20500, v_error_msg)"

            val (resultMy, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMy.contains("v_error_msg"))
            assertTrue(resultPg.contains("v_error_msg"))
        }
    }

    @Nested
    @DisplayName("유틸리티 메서드 테스트")
    inner class UtilityMethodsTest {

        @Test
        @DisplayName("DBMS 패키지 호출 감지")
        fun testHasDbmsPackageCalls() {
            assertTrue(DbmsPackageConverter.hasDbmsPackageCalls("SELECT DBMS_RANDOM.VALUE FROM dual"))
            assertTrue(DbmsPackageConverter.hasDbmsPackageCalls("UTL_FILE.FOPEN('/dir', 'file', 'r')"))
            assertTrue(DbmsPackageConverter.hasDbmsPackageCalls("RAISE_APPLICATION_ERROR(-20001, 'error')"))
            assertFalse(DbmsPackageConverter.hasDbmsPackageCalls("SELECT * FROM employees"))
        }

        @Test
        @DisplayName("사용된 패키지 목록 추출")
        fun testGetUsedDbmsPackages() {
            val sql = """
                DBMS_OUTPUT.PUT_LINE('test');
                SELECT DBMS_RANDOM.VALUE FROM dual;
                UTL_FILE.FOPEN('/dir', 'file', 'r');
                DBMS_LOB.GETLENGTH(clob_col)
            """.trimIndent()

            val packages = DbmsPackageConverter.getUsedDbmsPackages(sql)

            assertTrue(packages.contains("DBMS_OUTPUT"))
            assertTrue(packages.contains("DBMS_RANDOM"))
            assertTrue(packages.contains("UTL_FILE"))
            assertTrue(packages.contains("DBMS_LOB"))
            assertEquals(4, packages.size)
        }

        @Test
        @DisplayName("중복 패키지 제거")
        fun testUniquePackages() {
            val sql = """
                DBMS_OUTPUT.PUT_LINE('test1');
                DBMS_OUTPUT.PUT_LINE('test2');
                DBMS_OUTPUT.PUT('test3');
            """.trimIndent()

            val packages = DbmsPackageConverter.getUsedDbmsPackages(sql)

            assertEquals(1, packages.size)
            assertTrue(packages.contains("DBMS_OUTPUT"))
        }
    }

    @Nested
    @DisplayName("복합 시나리오 테스트")
    inner class ComplexScenarioTest {

        @Test
        @DisplayName("여러 DBMS 함수 조합")
        fun testMultipleDbmsFunctions() {
            val sql = """
                DECLARE
                    v_start NUMBER;
                    v_random NUMBER;
                BEGIN
                    v_start := DBMS_UTILITY.GET_TIME;
                    v_random := DBMS_RANDOM.VALUE(1, 100);
                    DBMS_OUTPUT.PUT_LINE('Random: ' || v_random);
                    DBMS_LOCK.SLEEP(1);
                END;
            """.trimIndent()

            val (resultMy, _, rulesMy) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, rulesPg) = convert(sql, DialectType.POSTGRESQL)

            // MySQL 검증
            assertTrue(resultMy.contains("UNIX_TIMESTAMP"))
            assertTrue(resultMy.contains("RAND()"))
            assertTrue(resultMy.contains("SLEEP"))

            // PostgreSQL 검증
            assertTrue(resultPg.contains("clock_timestamp") || resultPg.contains("epoch"))
            assertTrue(resultPg.contains("random()"))
            assertTrue(resultPg.contains("pg_sleep"))
            assertTrue(resultPg.contains("RAISE NOTICE"))

            // 규칙 적용 확인
            assertTrue(rulesMy.size >= 3)
            assertTrue(rulesPg.size >= 3)
        }

        @Test
        @DisplayName("에러 처리 블록")
        fun testErrorHandlingBlock() {
            val sql = """
                EXCEPTION
                    WHEN OTHERS THEN
                        DBMS_OUTPUT.PUT_LINE(DBMS_UTILITY.FORMAT_ERROR_STACK);
                        DBMS_OUTPUT.PUT_LINE(DBMS_UTILITY.FORMAT_ERROR_BACKTRACE);
                        RAISE_APPLICATION_ERROR(-20001, 'Processing failed');
            """.trimIndent()

            val (resultMy, warningsMy, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            // MySQL
            assertTrue(resultMy.contains("SIGNAL SQLSTATE"))
            assertTrue(warningsMy.isNotEmpty())

            // PostgreSQL
            assertTrue(resultPg.contains("RAISE EXCEPTION"))
            assertTrue(resultPg.contains("SQLERRM") || resultPg.contains("PG_EXCEPTION"))
        }

        @Test
        @DisplayName("LOB 처리 프로시저")
        fun testLobProcessingProcedure() {
            val sql = """
                CREATE OR REPLACE PROCEDURE process_lob(p_id NUMBER) IS
                    v_clob CLOB;
                    v_length NUMBER;
                    v_substr VARCHAR2(100);
                BEGIN
                    SELECT content INTO v_clob FROM documents WHERE id = p_id;
                    v_length := DBMS_LOB.GETLENGTH(v_clob);
                    v_substr := DBMS_LOB.SUBSTR(v_clob, 100, 1);
                    DBMS_OUTPUT.PUT_LINE('Length: ' || v_length);
                END;
            """.trimIndent()

            val (resultMy, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            // MySQL
            assertTrue(resultMy.contains("LENGTH(v_clob)"))
            assertTrue(resultMy.contains("SUBSTRING"))

            // PostgreSQL
            assertTrue(resultPg.contains("octet_length(v_clob)"))
            assertTrue(resultPg.contains("substring"))
            assertTrue(resultPg.contains("RAISE NOTICE"))
        }
    }

    @Nested
    @DisplayName("소스 방언 검증 테스트")
    inner class SourceDialectTest {

        @Test
        @DisplayName("Oracle 외 소스는 변환하지 않음")
        fun testNonOracleSourceNotConverted() {
            val sql = "DBMS_OUTPUT.PUT_LINE('test')"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = DbmsPackageConverter.convert(
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
}
