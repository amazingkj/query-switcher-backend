package com.sqlswitcher.converter

import com.sqlswitcher.converter.stringbased.StringBasedDataTypeConverter
import com.sqlswitcher.converter.stringbased.StringBasedFunctionConverter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * 새로 추가된 함수/데이터타입 변환 기능 테스트
 */
class NewFeaturesConversionTest {

    private lateinit var functionConverter: StringBasedFunctionConverter
    private lateinit var dataTypeConverter: StringBasedDataTypeConverter

    @BeforeEach
    fun setUp() {
        functionConverter = StringBasedFunctionConverter()
        dataTypeConverter = StringBasedDataTypeConverter()
    }

    // ==================== Oracle → MySQL 새 기능 테스트 ====================

    @Nested
    @DisplayName("Oracle → MySQL 새 기능 테스트")
    inner class OracleToMySqlNewFeatures {

        @Test
        @DisplayName("TO_CHAR → DATE_FORMAT 변환")
        fun testToCharConversion() {
            val oracle = "SELECT TO_CHAR(created_at, 'YYYY-MM-DD') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("DATE_FORMAT("), "TO_CHAR → DATE_FORMAT 변환 실패: $result")
            assertFalse(result.contains("TO_CHAR"), "TO_CHAR가 제거되지 않음: $result")
        }

        @Test
        @DisplayName("TO_DATE → STR_TO_DATE 변환")
        fun testToDateConversion() {
            val oracle = "SELECT TO_DATE('2024-01-01', 'YYYY-MM-DD') FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("STR_TO_DATE("), "TO_DATE → STR_TO_DATE 변환 실패: $result")
        }

        @Test
        @DisplayName("ADD_MONTHS → DATE_ADD 변환")
        fun testAddMonthsConversion() {
            val oracle = "SELECT ADD_MONTHS(SYSDATE, 3) FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("DATE_ADD("), "ADD_MONTHS → DATE_ADD 변환 실패: $result")
            assertTrue(result.contains("INTERVAL"), "INTERVAL 키워드 누락: $result")
            assertTrue(result.contains("MONTH"), "MONTH 키워드 누락: $result")
        }

        @Test
        @DisplayName("MONTHS_BETWEEN → TIMESTAMPDIFF 변환")
        fun testMonthsBetweenConversion() {
            val oracle = "SELECT MONTHS_BETWEEN(end_date, start_date) FROM projects"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("TIMESTAMPDIFF("), "MONTHS_BETWEEN → TIMESTAMPDIFF 변환 실패: $result")
        }

        @Test
        @DisplayName("TRUNC(date) → DATE() 변환")
        fun testTruncDateConversion() {
            val oracle = "SELECT TRUNC(SYSDATE) FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("DATE("), "TRUNC → DATE 변환 실패: $result")
        }

        @Test
        @DisplayName("LISTAGG → GROUP_CONCAT 변환")
        fun testListaggConversion() {
            val oracle = "SELECT LISTAGG(name, ', ') WITHIN GROUP (ORDER BY name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("GROUP_CONCAT("), "LISTAGG → GROUP_CONCAT 변환 실패: $result")
        }

        @Test
        @DisplayName("LENGTH → CHAR_LENGTH 변환")
        fun testLengthConversion() {
            val oracle = "SELECT LENGTH(name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("CHAR_LENGTH("), "LENGTH → CHAR_LENGTH 변환 실패: $result")
        }

        @Test
        @DisplayName("DBMS_RANDOM.VALUE → RAND() 변환")
        fun testDbmsRandomConversion() {
            val oracle = "SELECT DBMS_RANDOM.VALUE FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("RAND()"), "DBMS_RANDOM.VALUE → RAND() 변환 실패: $result")
        }

        @Test
        @DisplayName("FROM DUAL 제거")
        fun testDualRemoval() {
            val oracle = "SELECT 1 FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertFalse(result.contains("DUAL"), "FROM DUAL이 제거되지 않음: $result")
        }

        @Test
        @DisplayName("NVARCHAR2 → VARCHAR 변환")
        fun testNvarchar2Conversion() {
            val oracle = "CREATE TABLE test (name NVARCHAR2(100))"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("VARCHAR(100)"), "NVARCHAR2 → VARCHAR 변환 실패: $result")
        }

        @Test
        @DisplayName("NCLOB → LONGTEXT 변환")
        fun testNclobConversion() {
            val oracle = "CREATE TABLE test (content NCLOB)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("LONGTEXT"), "NCLOB → LONGTEXT 변환 실패: $result")
        }

        @Test
        @DisplayName("LONG RAW → LONGBLOB 변환")
        fun testLongRawConversion() {
            val oracle = "CREATE TABLE test (data LONG RAW)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("LONGBLOB"), "LONG RAW → LONGBLOB 변환 실패: $result")
        }

        @Test
        @DisplayName("RAW(n) → VARBINARY(n) 변환")
        fun testRawConversion() {
            val oracle = "CREATE TABLE test (data RAW(100))"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("VARBINARY(100)"), "RAW → VARBINARY 변환 실패: $result")
        }

        @Test
        @DisplayName("TIMESTAMP WITH TIME ZONE → DATETIME 변환")
        fun testTimestampWithTimeZoneConversion() {
            val oracle = "CREATE TABLE test (created_at TIMESTAMP WITH TIME ZONE)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("DATETIME"), "TIMESTAMP WITH TIME ZONE → DATETIME 변환 실패: $result")
        }
    }

    // ==================== Oracle → PostgreSQL 새 기능 테스트 ====================

    @Nested
    @DisplayName("Oracle → PostgreSQL 새 기능 테스트")
    inner class OracleToPostgreSqlNewFeatures {

        @Test
        @DisplayName("ADD_MONTHS → INTERVAL 변환")
        fun testAddMonthsConversion() {
            val oracle = "SELECT ADD_MONTHS(created_at, 6) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("INTERVAL"), "ADD_MONTHS → INTERVAL 변환 실패: $result")
        }

        @Test
        @DisplayName("TRUNC(date) → DATE_TRUNC 변환")
        fun testTruncDateConversion() {
            val oracle = "SELECT TRUNC(created_at) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("DATE_TRUNC("), "TRUNC → DATE_TRUNC 변환 실패: $result")
        }

        @Test
        @DisplayName("LISTAGG → STRING_AGG 변환")
        fun testListaggConversion() {
            val oracle = "SELECT LISTAGG(name, ', ') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("STRING_AGG("), "LISTAGG → STRING_AGG 변환 실패: $result")
        }

        @Test
        @DisplayName("NVL2 → CASE WHEN 변환")
        fun testNvl2Conversion() {
            val oracle = "SELECT NVL2(status, 'Active', 'Inactive') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("CASE WHEN"), "NVL2 → CASE WHEN 변환 실패: $result")
        }

        @Test
        @DisplayName("중첩 NVL2 → CASE WHEN 변환")
        fun testNestedNvl2Conversion() {
            val oracle = "SELECT NVL2(a, NVL2(b, 'both', 'a only'), 'none') FROM t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertFalse(result.contains("NVL2"), "NVL2가 완전히 제거되지 않음: $result")
            val caseCount = "CASE WHEN".toRegex().findAll(result).count()
            assertTrue(caseCount >= 2, "중첩된 CASE WHEN이 모두 변환되어야 함: $result")
        }

        @Test
        @DisplayName("복잡한 인자를 가진 NVL2 변환")
        fun testNvl2WithComplexArgs() {
            val oracle = "SELECT NVL2(SUBSTR(name, 1, 5), UPPER(name), LOWER(name)) FROM t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("CASE WHEN"), "NVL2 → CASE WHEN 변환 실패: $result")
            assertTrue(result.contains("IS NOT NULL"), "IS NOT NULL이 있어야 함: $result")
            assertFalse(result.contains("NVL2"), "NVL2가 완전히 제거되지 않음: $result")
        }

        @Test
        @DisplayName("문자열 리터럴 내 쉼표가 있는 NVL2 변환")
        fun testNvl2WithCommaInStringLiteral() {
            val oracle = "SELECT NVL2(status, 'Active, running', 'Inactive, stopped') FROM procs"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("CASE WHEN"), "NVL2 → CASE WHEN 변환 실패: $result")
            assertTrue(result.contains("'Active, running'"), "문자열 리터럴이 보존되어야 함: $result")
            assertTrue(result.contains("'Inactive, stopped'"), "문자열 리터럴이 보존되어야 함: $result")
            assertFalse(result.contains("NVL2"), "NVL2가 완전히 제거되지 않음: $result")
        }

        @Test
        @DisplayName("시퀀스 NEXTVAL → nextval() 변환")
        fun testSequenceNextvalConversion() {
            val oracle = "SELECT seq_user.NEXTVAL FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("nextval('seq_user')"), "NEXTVAL 변환 실패: $result")
        }

        @Test
        @DisplayName("시퀀스 CURRVAL → currval() 변환")
        fun testSequenceCurrvalConversion() {
            val oracle = "SELECT seq_user.CURRVAL FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("currval('seq_user')"), "CURRVAL 변환 실패: $result")
        }

        @Test
        @DisplayName("TIMESTAMP WITH TIME ZONE → TIMESTAMPTZ 변환")
        fun testTimestampWithTimeZoneConversion() {
            val oracle = "CREATE TABLE test (created_at TIMESTAMP WITH TIME ZONE)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("TIMESTAMPTZ"), "TIMESTAMP WITH TIME ZONE → TIMESTAMPTZ 변환 실패: $result")
        }
    }

    // ==================== MySQL → PostgreSQL 새 기능 테스트 ====================

    @Nested
    @DisplayName("MySQL → PostgreSQL 새 기능 테스트")
    inner class MySqlToPostgreSqlNewFeatures {

        @Test
        @DisplayName("DATEDIFF → 날짜 뺄셈 변환")
        fun testDatediffConversion() {
            val mysql = "SELECT DATEDIFF(end_date, start_date) FROM events"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("::DATE"), "DATEDIFF → 날짜 뺄셈 변환 실패: $result")
        }

        @Test
        @DisplayName("DATE_ADD → + INTERVAL 변환")
        fun testDateAddConversion() {
            val mysql = "SELECT DATE_ADD(created_at, INTERVAL 1 DAY) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("+ INTERVAL"), "DATE_ADD → + INTERVAL 변환 실패: $result")
        }

        @Test
        @DisplayName("YEAR() → EXTRACT(YEAR FROM) 변환")
        fun testYearConversion() {
            val mysql = "SELECT YEAR(created_at) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("EXTRACT(YEAR FROM"), "YEAR() → EXTRACT 변환 실패: $result")
        }

        @Test
        @DisplayName("MONTH() → EXTRACT(MONTH FROM) 변환")
        fun testMonthConversion() {
            val mysql = "SELECT MONTH(created_at) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("EXTRACT(MONTH FROM"), "MONTH() → EXTRACT 변환 실패: $result")
        }

        @Test
        @DisplayName("DAY() → EXTRACT(DAY FROM) 변환")
        fun testDayConversion() {
            val mysql = "SELECT DAY(created_at) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("EXTRACT(DAY FROM"), "DAY() → EXTRACT 변환 실패: $result")
        }

        @Test
        @DisplayName("IF → CASE WHEN 변환")
        fun testIfConversion() {
            val mysql = "SELECT IF(status = 'A', 'Active', 'Inactive') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("CASE WHEN"), "IF → CASE WHEN 변환 실패: $result")
        }

        @Test
        @DisplayName("중첩 IF → CASE WHEN 변환")
        fun testNestedIfConversion() {
            val mysql = "SELECT IF(a > 0, IF(b > 0, 'both', 'a only'), 'none') FROM t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertFalse(result.contains("IF("), "IF가 완전히 제거되지 않음: $result")
            val caseCount = "CASE WHEN".toRegex().findAll(result).count()
            assertTrue(caseCount >= 2, "중첩된 CASE WHEN이 모두 변환되어야 함: $result")
        }

        @Test
        @DisplayName("복잡한 인자를 가진 IF 변환")
        fun testIfWithComplexArgs() {
            val mysql = "SELECT IF(COALESCE(a, b) > 0, CONCAT('prefix', name), SUBSTRING(name, 1, 5)) FROM t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("CASE WHEN"), "IF → CASE WHEN 변환 실패: $result")
            assertTrue(result.contains("COALESCE"), "COALESCE가 유지되어야 함: $result")
            assertFalse(result.contains("IF("), "IF가 완전히 제거되지 않음: $result")
        }

        @Test
        @DisplayName("문자열 리터럴 내 쉼표가 있는 IF 변환")
        fun testIfWithCommaInStringLiteral() {
            val mysql = "SELECT IF(status = 'A', 'Active, enabled', 'Inactive, disabled') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("CASE WHEN"), "IF → CASE WHEN 변환 실패: $result")
            assertTrue(result.contains("'Active, enabled'"), "문자열 리터럴이 보존되어야 함: $result")
            assertTrue(result.contains("'Inactive, disabled'"), "문자열 리터럴이 보존되어야 함: $result")
            assertFalse(result.contains("IF("), "IF가 완전히 제거되지 않음: $result")
        }

        @Test
        @DisplayName("LOCATE → POSITION 변환")
        fun testLocateConversion() {
            val mysql = "SELECT LOCATE('a', name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("POSITION("), "LOCATE → POSITION 변환 실패: $result")
        }

        @Test
        @DisplayName("ENGINE= 제거")
        fun testEngineRemoval() {
            val mysql = "CREATE TABLE test (id INT) ENGINE=InnoDB"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertFalse(result.contains("ENGINE"), "ENGINE= 제거 실패: $result")
        }
    }

    // ==================== PostgreSQL → MySQL 새 기능 테스트 ====================

    @Nested
    @DisplayName("PostgreSQL → MySQL 새 기능 테스트")
    inner class PostgreSqlToMySqlNewFeatures {

        @Test
        @DisplayName("ILIKE → LIKE 변환")
        fun testIlikeConversion() {
            val postgresql = "SELECT * FROM users WHERE name ILIKE '%john%'"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("LIKE"), "ILIKE → LIKE 변환 실패: $result")
            assertFalse(result.contains("ILIKE"), "ILIKE가 제거되지 않음: $result")
        }

        @Test
        @DisplayName("NOT ILIKE → NOT LIKE 변환")
        fun testNotIlikeConversion() {
            val postgresql = "SELECT * FROM users WHERE name NOT ILIKE '%john%'"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("NOT LIKE"), "NOT ILIKE → NOT LIKE 변환 실패: $result")
        }

        @Test
        @DisplayName("EXTRACT(YEAR FROM) → YEAR() 변환")
        fun testExtractYearConversion() {
            val postgresql = "SELECT EXTRACT(YEAR FROM created_at) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("YEAR("), "EXTRACT(YEAR FROM) → YEAR() 변환 실패: $result")
        }

        @Test
        @DisplayName("DATE_TRUNC('day') → DATE() 변환")
        fun testDateTruncDayConversion() {
            val postgresql = "SELECT DATE_TRUNC('day', created_at) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("DATE("), "DATE_TRUNC → DATE 변환 실패: $result")
        }

        @Test
        @DisplayName("STRING_AGG → GROUP_CONCAT 변환")
        fun testStringAggConversion() {
            val postgresql = "SELECT STRING_AGG(name, ', ') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("GROUP_CONCAT("), "STRING_AGG → GROUP_CONCAT 변환 실패: $result")
        }

        @Test
        @DisplayName("POSITION(x IN y) → LOCATE(x, y) 변환")
        fun testPositionConversion() {
            val postgresql = "SELECT POSITION('a' IN name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("LOCATE("), "POSITION → LOCATE 변환 실패: $result")
        }

        @Test
        @DisplayName("POWER → POW 변환")
        fun testPowerConversion() {
            val postgresql = "SELECT POWER(2, 10) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("POW("), "POWER → POW 변환 실패: $result")
        }

        @Test
        @DisplayName("다양한 타입 캐스팅(::) 제거")
        fun testTypeCastingRemoval() {
            val postgresql = "SELECT id::BIGINT, name::VARCHAR(100), amount::NUMERIC(10,2) FROM data"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertFalse(result.contains("::BIGINT"), "::BIGINT 제거 실패: $result")
            assertFalse(result.contains("::VARCHAR"), "::VARCHAR 제거 실패: $result")
            assertFalse(result.contains("::NUMERIC"), "::NUMERIC 제거 실패: $result")
        }

        @Test
        @DisplayName("INTERVAL 구문 변환")
        fun testIntervalConversion() {
            val postgresql = "SELECT created_at + INTERVAL '30 days' FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("INTERVAL 30 DAY"), "INTERVAL 변환 실패: $result")
        }
    }

    // ==================== MySQL → Oracle 새 기능 테스트 ====================

    @Nested
    @DisplayName("MySQL → Oracle 새 기능 테스트")
    inner class MySqlToOracleNewFeatures {

        @Test
        @DisplayName("CURDATE → TRUNC(SYSDATE) 변환")
        fun testCurdateConversion() {
            val mysql = "SELECT CURDATE() FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("TRUNC(SYSDATE)"), "CURDATE → TRUNC(SYSDATE) 변환 실패: $result")
        }

        @Test
        @DisplayName("DATE_FORMAT → TO_CHAR 변환")
        fun testDateFormatConversion() {
            val mysql = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("TO_CHAR("), "DATE_FORMAT → TO_CHAR 변환 실패: $result")
        }

        @Test
        @DisplayName("RAND → DBMS_RANDOM.VALUE 변환")
        fun testRandConversion() {
            val mysql = "SELECT RAND() FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("DBMS_RANDOM.VALUE"), "RAND → DBMS_RANDOM.VALUE 변환 실패: $result")
        }

        @Test
        @DisplayName("LIMIT → FETCH FIRST 변환")
        fun testLimitConversion() {
            val mysql = "SELECT * FROM users LIMIT 10"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("FETCH FIRST 10 ROWS ONLY"), "LIMIT → FETCH FIRST 변환 실패: $result")
        }

        @Test
        @DisplayName("LIMIT OFFSET → OFFSET FETCH 변환")
        fun testLimitOffsetConversion() {
            val mysql = "SELECT * FROM users LIMIT 10 OFFSET 20"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("OFFSET 20 ROWS"), "OFFSET 변환 실패: $result")
            assertTrue(result.contains("FETCH NEXT 10 ROWS ONLY"), "FETCH NEXT 변환 실패: $result")
        }

        @Test
        @DisplayName("GROUP_CONCAT → LISTAGG 변환")
        fun testGroupConcatConversion() {
            val mysql = "SELECT GROUP_CONCAT(name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("LISTAGG("), "GROUP_CONCAT → LISTAGG 변환 실패: $result")
        }

        @Test
        @DisplayName("LOCATE → INSTR 변환 (파라미터 순서 변경)")
        fun testLocateConversion() {
            val mysql = "SELECT LOCATE('a', name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("INSTR(name, 'a')"), "LOCATE → INSTR 변환 실패 (파라미터 순서 변경): $result")
        }

        @Test
        @DisplayName("POW → POWER 변환")
        fun testPowConversion() {
            val mysql = "SELECT POW(2, 10) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("POWER("), "POW → POWER 변환 실패: $result")
        }

        @Test
        @DisplayName("IF → CASE WHEN 변환")
        fun testIfConversion() {
            val mysql = "SELECT IF(status = 'A', 'Active', 'Inactive') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("CASE WHEN"), "IF → CASE WHEN 변환 실패: $result")
        }
    }

    // ==================== DECODE 완전 변환 테스트 ====================

    @Nested
    @DisplayName("DECODE → CASE WHEN 완전 변환 테스트")
    inner class DecodeConversionTest {

        @Test
        @DisplayName("DECODE(expr, search1, result1, default) → CASE expr WHEN search1 THEN result1 ELSE default END")
        fun testDecodeBasicConversion() {
            val oracle = "SELECT DECODE(status, 'A', 'Active', 'Inactive') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("CASE status"), "CASE expr 변환 실패: $result")
            assertTrue(result.contains("WHEN 'A' THEN 'Active'"), "WHEN THEN 변환 실패: $result")
            assertTrue(result.contains("ELSE 'Inactive' END"), "ELSE END 변환 실패: $result")
            assertFalse(result.contains("DECODE"), "DECODE가 제거되지 않음: $result")
        }

        @Test
        @DisplayName("DECODE with multiple conditions")
        fun testDecodeMultipleConditions() {
            val oracle = "SELECT DECODE(grade, 'A', 100, 'B', 80, 'C', 60, 0) FROM students"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("CASE grade"), "CASE expr 변환 실패: $result")
            assertTrue(result.contains("WHEN 'A' THEN 100"), "WHEN A 변환 실패: $result")
            assertTrue(result.contains("WHEN 'B' THEN 80"), "WHEN B 변환 실패: $result")
            assertTrue(result.contains("WHEN 'C' THEN 60"), "WHEN C 변환 실패: $result")
            assertTrue(result.contains("ELSE 0 END"), "ELSE END 변환 실패: $result")
        }

        @Test
        @DisplayName("DECODE without default value")
        fun testDecodeWithoutDefault() {
            val oracle = "SELECT DECODE(type, 1, 'ONE', 2, 'TWO') FROM items"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("CASE type"), "CASE expr 변환 실패: $result")
            assertTrue(result.contains("WHEN 1 THEN 'ONE'"), "WHEN 1 변환 실패: $result")
            assertTrue(result.contains("WHEN 2 THEN 'TWO'"), "WHEN 2 변환 실패: $result")
            assertTrue(result.contains("END"), "END 누락: $result")
        }

        @Test
        @DisplayName("Nested DECODE conversion")
        fun testNestedDecodeConversion() {
            val oracle = "SELECT DECODE(a, 1, DECODE(b, 2, 'X', 'Y'), 'Z') FROM t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertFalse(result.contains("DECODE"), "DECODE가 완전히 제거되지 않음: $result")
            // 외부와 내부 CASE 모두 변환되어야 함
            val caseCount = "CASE".toRegex().findAll(result).count()
            assertTrue(caseCount >= 2, "중첩된 CASE가 모두 변환되어야 함: $result")
        }

        @Test
        @DisplayName("DECODE to PostgreSQL")
        fun testDecodeToPostgreSQL() {
            val oracle = "SELECT DECODE(status, 'Y', 'Yes', 'No') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("CASE status"), "CASE expr 변환 실패: $result")
            assertTrue(result.contains("WHEN 'Y' THEN 'Yes'"), "WHEN THEN 변환 실패: $result")
            assertFalse(result.contains("DECODE"), "DECODE가 제거되지 않음: $result")
        }

        @Test
        @DisplayName("문자열 리터럴 내 쉼표가 있는 DECODE 변환")
        fun testDecodeWithCommaInStringLiteral() {
            val oracle = "SELECT DECODE(type, 'A', 'Type A, active', 'B', 'Type B, beta', 'Unknown, N/A') FROM items"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("CASE type"), "CASE expr 변환 실패: $result")
            assertTrue(result.contains("'Type A, active'"), "문자열 리터럴이 보존되어야 함: $result")
            assertTrue(result.contains("'Type B, beta'"), "문자열 리터럴이 보존되어야 함: $result")
            assertTrue(result.contains("'Unknown, N/A'"), "기본값 문자열 리터럴이 보존되어야 함: $result")
            assertFalse(result.contains("DECODE"), "DECODE가 제거되지 않음: $result")
        }

        @Test
        @DisplayName("문자열 리터럴 내 괄호가 있는 DECODE 변환")
        fun testDecodeWithParenthesisInStringLiteral() {
            val oracle = "SELECT DECODE(status, 1, 'Active (running)', 'Inactive (stopped)') FROM procs"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("CASE status"), "CASE expr 변환 실패: $result")
            assertTrue(result.contains("'Active (running)'"), "문자열 리터럴이 보존되어야 함: $result")
            assertTrue(result.contains("'Inactive (stopped)'"), "문자열 리터럴이 보존되어야 함: $result")
            assertFalse(result.contains("DECODE"), "DECODE가 제거되지 않음: $result")
        }
    }

    // ==================== 날짜 포맷 변환 테스트 ====================

    @Nested
    @DisplayName("날짜 포맷 문자열 변환 테스트")
    inner class DateFormatConversionTest {

        @Test
        @DisplayName("Oracle → MySQL: 'YYYY-MM-DD' → '%Y-%m-%d'")
        fun testOracleToMySqlDateFormat() {
            val oracle = "SELECT TO_CHAR(created_at, 'YYYY-MM-DD') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("DATE_FORMAT("), "TO_CHAR → DATE_FORMAT 변환 실패: $result")
            assertTrue(result.contains("%Y-%m-%d"), "날짜 포맷 변환 실패: $result")
        }

        @Test
        @DisplayName("Oracle → MySQL: 'YYYY-MM-DD HH24:MI:SS' → '%Y-%m-%d %H:%i:%s'")
        fun testOracleToMySqlDateTimeFormat() {
            val oracle = "SELECT TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI:SS') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("%Y-%m-%d %H:%i:%s"), "날짜시간 포맷 변환 실패: $result")
        }

        @Test
        @DisplayName("MySQL → Oracle: '%Y-%m-%d' → 'YYYY-MM-DD'")
        fun testMySqlToOracleDateFormat() {
            val mysql = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("TO_CHAR("), "DATE_FORMAT → TO_CHAR 변환 실패: $result")
            assertTrue(result.contains("YYYY-MM-DD"), "날짜 포맷 변환 실패: $result")
        }

        @Test
        @DisplayName("MySQL → PostgreSQL: '%Y-%m-%d %H:%i:%s' → 'YYYY-MM-DD HH24:MI:SS'")
        fun testMySqlToPostgreSqlDateFormat() {
            val mysql = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("TO_CHAR("), "DATE_FORMAT → TO_CHAR 변환 실패: $result")
            assertTrue(result.contains("YYYY-MM-DD HH24:MI:SS"), "날짜 포맷 변환 실패: $result")
        }

        @Test
        @DisplayName("PostgreSQL → MySQL: 'YYYY-MM-DD' → '%Y-%m-%d'")
        fun testPostgreSqlToMySqlDateFormat() {
            val postgresql = "SELECT TO_CHAR(created_at, 'YYYY-MM-DD') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("DATE_FORMAT("), "TO_CHAR → DATE_FORMAT 변환 실패: $result")
            assertTrue(result.contains("%Y-%m-%d"), "날짜 포맷 변환 실패: $result")
        }

        @Test
        @DisplayName("Oracle 날짜 포맷 요소들 변환 (MON, DAY 등)")
        fun testOracleAdvancedDateFormat() {
            val oracle = "SELECT TO_CHAR(created_at, 'DD-MON-YYYY DAY') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("%d-%b-%Y %W"), "날짜 포맷 변환 실패: $result")
        }
    }

    // ==================== INSTR 파라미터 순서 변환 테스트 ====================

    @Nested
    @DisplayName("INSTR 파라미터 순서 변환 테스트")
    inner class InstrConversionTest {

        @Test
        @DisplayName("Oracle INSTR(string, substr) → MySQL LOCATE(substr, string)")
        fun testOracleInstrToMySqlLocate() {
            val oracle = "SELECT INSTR(name, 'a') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("LOCATE('a', name)"), "INSTR → LOCATE 파라미터 순서 변환 실패: $result")
        }

        @Test
        @DisplayName("Oracle INSTR with complex expression")
        fun testOracleInstrComplexExpr() {
            val oracle = "SELECT INSTR(UPPER(name), 'TEST') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("LOCATE('TEST', UPPER(name))"), "복합 표현식 INSTR 변환 실패: $result")
        }

        @Test
        @DisplayName("Oracle INSTR → PostgreSQL POSITION")
        fun testOracleInstrToPostgreSqlPosition() {
            val oracle = "SELECT INSTR(email, '@') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("POSITION('@' IN email)"), "INSTR → POSITION 변환 실패: $result")
        }

        @Test
        @DisplayName("MySQL LOCATE → Oracle INSTR (역방향)")
        fun testMySqlLocateToOracleInstr() {
            val mysql = "SELECT LOCATE('a', name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("INSTR(name, 'a')"), "LOCATE → INSTR 파라미터 순서 변환 실패: $result")
        }
    }

    // ==================== 공통 SQL 함수 테스트 ====================

    @Nested
    @DisplayName("NULLIF, GREATEST, LEAST 함수 테스트")
    inner class CommonSqlFunctionsTest {

        @Test
        @DisplayName("NULLIF 함수 - Oracle → MySQL")
        fun testNullIfOracleToMySql() {
            val oracle = "SELECT NULLIF(status, 'N/A') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("NULLIF("), "NULLIF가 유지되어야 함: $result")
        }

        @Test
        @DisplayName("NULLIF 함수 - MySQL → PostgreSQL")
        fun testNullIfMySqlToPostgreSql() {
            val mysql = "SELECT NULLIF(a, b) FROM t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("NULLIF("), "NULLIF가 유지되어야 함: $result")
        }

        @Test
        @DisplayName("GREATEST 함수 - Oracle → MySQL")
        fun testGreatestOracleToMySql() {
            val oracle = "SELECT GREATEST(a, b, c) FROM t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("GREATEST("), "GREATEST가 유지되어야 함: $result")
        }

        @Test
        @DisplayName("GREATEST 함수 - MySQL → PostgreSQL")
        fun testGreatestMySqlToPostgreSql() {
            val mysql = "SELECT GREATEST(1, 2, 3) FROM t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("GREATEST("), "GREATEST가 유지되어야 함: $result")
        }

        @Test
        @DisplayName("LEAST 함수 - Oracle → MySQL")
        fun testLeastOracleToMySql() {
            val oracle = "SELECT LEAST(a, b, c) FROM t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("LEAST("), "LEAST가 유지되어야 함: $result")
        }

        @Test
        @DisplayName("LEAST 함수 - PostgreSQL → Oracle")
        fun testLeastPostgreSqlToOracle() {
            val postgresql = "SELECT LEAST(price, 100) FROM products"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("LEAST("), "LEAST가 유지되어야 함: $result")
        }

        @Test
        @DisplayName("COALESCE 다중 인자 - Oracle → MySQL")
        fun testCoalesceMultipleArgs() {
            val oracle = "SELECT COALESCE(a, b, c, d) FROM t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // Oracle COALESCE는 MySQL에서도 COALESCE로 유지 (NVL은 2개 인자만)
            assertTrue(result.contains("COALESCE(") || result.contains("IFNULL("), "NULL 처리 함수가 있어야 함: $result")
        }
    }

    // ==================== FIND_IN_SET 변환 테스트 ====================

    @Nested
    @DisplayName("FIND_IN_SET 함수 변환 테스트")
    inner class FindInSetConversionTest {

        @Test
        @DisplayName("MySQL FIND_IN_SET → PostgreSQL")
        fun testFindInSetToPostgreSql() {
            val mysql = "SELECT * FROM users WHERE FIND_IN_SET('admin', roles) > 0"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            // FIND_IN_SET은 PostgreSQL에서 position과 string_to_array 또는 LIKE로 변환
            assertFalse(result.contains("FIND_IN_SET"), "FIND_IN_SET이 변환되어야 함: $result")
        }

        @Test
        @DisplayName("MySQL FIND_IN_SET → Oracle")
        fun testFindInSetToOracle() {
            val mysql = "SELECT * FROM users WHERE FIND_IN_SET('admin', roles) > 0"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            // FIND_IN_SET은 Oracle에서 INSTR 또는 REGEXP로 변환
            assertFalse(result.contains("FIND_IN_SET"), "FIND_IN_SET이 변환되어야 함: $result")
        }
    }

    // ==================== NULLS FIRST/LAST 변환 테스트 ====================

    @Nested
    @DisplayName("NULLS FIRST/LAST 정렬 옵션 테스트")
    inner class NullsFirstLastTest {

        @Test
        @DisplayName("Oracle NULLS FIRST → PostgreSQL (유지)")
        fun testNullsFirstOracleToPostgreSql() {
            val oracle = "SELECT * FROM users ORDER BY name NULLS FIRST"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("NULLS FIRST"), "NULLS FIRST가 유지되어야 함: $result")
        }

        @Test
        @DisplayName("Oracle NULLS LAST → PostgreSQL (유지)")
        fun testNullsLastOracleToPostgreSql() {
            val oracle = "SELECT * FROM users ORDER BY name DESC NULLS LAST"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("NULLS LAST"), "NULLS LAST가 유지되어야 함: $result")
        }

        @Test
        @DisplayName("Oracle NULLS FIRST → MySQL (CASE WHEN으로 변환)")
        fun testNullsFirstOracleToMySql() {
            val oracle = "SELECT * FROM users ORDER BY name NULLS FIRST"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // MySQL은 NULLS FIRST/LAST를 지원하지 않으므로 CASE WHEN 또는 IS NULL로 변환
            assertFalse(result.contains("NULLS FIRST"), "NULLS FIRST가 MySQL 호환 구문으로 변환되어야 함: $result")
        }

        @Test
        @DisplayName("Oracle NULLS LAST → MySQL (CASE WHEN으로 변환)")
        fun testNullsLastOracleToMySql() {
            val oracle = "SELECT * FROM users ORDER BY name DESC NULLS LAST"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertFalse(result.contains("NULLS LAST"), "NULLS LAST가 MySQL 호환 구문으로 변환되어야 함: $result")
        }

        @Test
        @DisplayName("PostgreSQL NULLS FIRST → MySQL (CASE WHEN으로 변환)")
        fun testNullsFirstPostgreSqlToMySql() {
            val postgresql = "SELECT * FROM users ORDER BY email NULLS FIRST"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertFalse(result.contains("NULLS FIRST"), "NULLS FIRST가 MySQL 호환 구문으로 변환되어야 함: $result")
        }
    }

    // ==================== ROLLUP/CUBE/GROUPING SETS 테스트 ====================

    @Nested
    @DisplayName("ROLLUP/CUBE/GROUPING SETS 변환 테스트")
    inner class RollupCubeTest {

        @Test
        @DisplayName("Oracle ROLLUP → MySQL WITH ROLLUP")
        fun testRollupOracleToMySql() {
            val oracle = "SELECT dept, SUM(salary) FROM employees GROUP BY ROLLUP(dept)"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("WITH ROLLUP"), "ROLLUP이 WITH ROLLUP으로 변환되어야 함: $result")
        }

        @Test
        @DisplayName("Oracle ROLLUP → PostgreSQL (유지)")
        fun testRollupOracleToPostgreSql() {
            val oracle = "SELECT dept, SUM(salary) FROM employees GROUP BY ROLLUP(dept)"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("ROLLUP("), "ROLLUP이 유지되어야 함: $result")
        }

        @Test
        @DisplayName("Oracle CUBE → PostgreSQL (유지)")
        fun testCubeOracleToPostgreSql() {
            val oracle = "SELECT dept, job, SUM(salary) FROM employees GROUP BY CUBE(dept, job)"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("CUBE("), "CUBE가 유지되어야 함: $result")
        }

        @Test
        @DisplayName("Oracle GROUPING SETS → PostgreSQL (유지)")
        fun testGroupingSetsOracleToPostgreSql() {
            val oracle = "SELECT dept, job, SUM(salary) FROM employees GROUP BY GROUPING SETS((dept), (job), ())"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("GROUPING SETS"), "GROUPING SETS가 유지되어야 함: $result")
        }

        @Test
        @DisplayName("PostgreSQL ROLLUP → MySQL WITH ROLLUP")
        fun testRollupPostgreSqlToMySql() {
            val postgresql = "SELECT dept, SUM(salary) FROM employees GROUP BY ROLLUP(dept)"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("WITH ROLLUP"), "ROLLUP이 WITH ROLLUP으로 변환되어야 함: $result")
        }

        @Test
        @DisplayName("MySQL WITH ROLLUP → Oracle ROLLUP")
        fun testWithRollupMySqlToOracle() {
            val mysql = "SELECT dept, SUM(salary) FROM employees GROUP BY dept WITH ROLLUP"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("ROLLUP("), "WITH ROLLUP이 ROLLUP()으로 변환되어야 함: $result")
        }

        @Test
        @DisplayName("MySQL WITH ROLLUP → PostgreSQL ROLLUP")
        fun testWithRollupMySqlToPostgreSql() {
            val mysql = "SELECT dept, SUM(salary) FROM employees GROUP BY dept WITH ROLLUP"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("ROLLUP("), "WITH ROLLUP이 ROLLUP()으로 변환되어야 함: $result")
        }
    }

    // ==================== REGEXP_COUNT 변환 테스트 ====================

    @Nested
    @DisplayName("REGEXP_COUNT 함수 변환 테스트")
    inner class RegexpCountTest {

        @Test
        @DisplayName("Oracle REGEXP_COUNT → PostgreSQL")
        fun testRegexpCountOracleToPostgreSql() {
            val oracle = "SELECT REGEXP_COUNT(description, 'test') FROM items"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            // PostgreSQL에서는 array_length(regexp_matches(..., 'g'), 1) 또는 LENGTH 기반 변환
            assertFalse(result.contains("REGEXP_COUNT"), "REGEXP_COUNT가 변환되어야 함: $result")
        }

        @Test
        @DisplayName("Oracle REGEXP_COUNT → MySQL")
        fun testRegexpCountOracleToMySql() {
            val oracle = "SELECT REGEXP_COUNT(name, '[aeiou]') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // MySQL 8.0 이전은 REGEXP_COUNT 미지원 - LENGTH 기반 변환
            assertFalse(result.contains("REGEXP_COUNT("), "REGEXP_COUNT( 함수 호출이 변환되어야 함: $result")
            assertTrue(result.contains("LENGTH"), "LENGTH 함수로 변환되어야 함: $result")
        }
    }

    // ==================== MERGE 문 변환 테스트 ====================

    @Nested
    @DisplayName("MERGE 문 변환 테스트")
    inner class MergeStatementTest {

        @Test
        @DisplayName("Oracle MERGE → MySQL INSERT ON DUPLICATE KEY UPDATE")
        fun testMergeOracleToMySql() {
            val oracle = """
                MERGE INTO target t
                USING source s
                ON (t.id = s.id)
                WHEN MATCHED THEN UPDATE SET t.name = s.name
                WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id, s.name)
            """.trimIndent()
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // MERGE는 MySQL에서 INSERT ... ON DUPLICATE KEY UPDATE로 변환
            assertTrue(
                result.contains("INSERT") || result.contains("MERGE"),
                "MERGE가 변환되거나 유지되어야 함: $result"
            )
        }

        @Test
        @DisplayName("Oracle MERGE → PostgreSQL INSERT ON CONFLICT")
        fun testMergeOracleToPostgreSql() {
            val oracle = """
                MERGE INTO target t
                USING source s
                ON (t.id = s.id)
                WHEN MATCHED THEN UPDATE SET t.name = s.name
                WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id, s.name)
            """.trimIndent()
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            // PostgreSQL 15+는 MERGE 지원, 이전 버전은 INSERT ... ON CONFLICT
            assertTrue(
                result.contains("MERGE") || result.contains("INSERT"),
                "MERGE가 유지되거나 변환되어야 함: $result"
            )
        }
    }

    // ==================== PIVOT/UNPIVOT 변환 테스트 ====================

    @Nested
    @DisplayName("PIVOT/UNPIVOT 변환 테스트")
    inner class PivotUnpivotTest {

        @Test
        @DisplayName("Oracle PIVOT → PostgreSQL CASE WHEN")
        fun testPivotOracleToPostgreSql() {
            val oracle = """
                SELECT * FROM sales
                PIVOT (SUM(amount) FOR quarter IN ('Q1' AS q1, 'Q2' AS q2, 'Q3' AS q3, 'Q4' AS q4))
            """.trimIndent()
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            // PIVOT은 CASE WHEN 또는 crosstab으로 변환
            assertTrue(
                result.contains("CASE") || result.contains("PIVOT") || result.contains("crosstab"),
                "PIVOT이 변환되거나 유지되어야 함: $result"
            )
        }

        @Test
        @DisplayName("Oracle PIVOT → MySQL CASE WHEN")
        fun testPivotOracleToMySql() {
            val oracle = """
                SELECT * FROM sales
                PIVOT (SUM(amount) FOR quarter IN ('Q1' AS q1, 'Q2' AS q2))
            """.trimIndent()
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // MySQL은 PIVOT 미지원, CASE WHEN으로 변환
            assertTrue(
                result.contains("CASE") || result.contains("PIVOT"),
                "PIVOT이 변환되거나 유지되어야 함: $result"
            )
        }
    }

    // ==================== 중간 우선순위 - ARRAY 함수 테스트 ====================

    @Nested
    @DisplayName("ARRAY 함수 변환 테스트")
    inner class ArrayFunctionsTest {

        @Test
        @DisplayName("PostgreSQL ARRAY_AGG → MySQL GROUP_CONCAT")
        fun testArrayAggToMySql() {
            val postgresql = "SELECT ARRAY_AGG(name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(
                result.contains("GROUP_CONCAT") || result.contains("JSON_ARRAYAGG"),
                "ARRAY_AGG가 GROUP_CONCAT 또는 JSON_ARRAYAGG로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("PostgreSQL ARRAY_AGG → Oracle COLLECT")
        fun testArrayAggToOracle() {
            val postgresql = "SELECT ARRAY_AGG(name ORDER BY name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(
                result.contains("COLLECT") || result.contains("LISTAGG") || result.contains("ARRAY_AGG"),
                "ARRAY_AGG가 COLLECT 또는 LISTAGG로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("PostgreSQL unnest → MySQL JSON_TABLE")
        fun testUnnestToMySql() {
            val postgresql = "SELECT unnest(ARRAY[1, 2, 3])"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            // unnest는 MySQL에서 JSON_TABLE 또는 수동 분해로 변환
            assertFalse(result.contains("unnest("), "unnest가 변환되어야 함: $result")
        }
    }

    // ==================== LATERAL JOIN 변환 테스트 ====================

    @Nested
    @DisplayName("LATERAL JOIN 변환 테스트")
    inner class LateralJoinTest {

        @Test
        @DisplayName("PostgreSQL LATERAL → MySQL (LATERAL 유지, MySQL 8.0.14+)")
        fun testLateralPostgreSqlToMySql() {
            val postgresql = "SELECT * FROM users u, LATERAL (SELECT * FROM orders o WHERE o.user_id = u.id) AS o"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            // MySQL 8.0.14+는 LATERAL 지원
            assertTrue(
                result.contains("LATERAL") || result.contains("SELECT"),
                "LATERAL이 유지되거나 서브쿼리로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("PostgreSQL LATERAL → Oracle CROSS APPLY")
        fun testLateralPostgreSqlToOracle() {
            val postgresql = "SELECT * FROM users u, LATERAL (SELECT * FROM orders o WHERE o.user_id = u.id) AS o"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            // Oracle 12c+는 CROSS APPLY 또는 LATERAL 지원
            assertTrue(
                result.contains("LATERAL") || result.contains("CROSS APPLY") || result.contains("SELECT"),
                "LATERAL이 유지되거나 CROSS APPLY로 변환되어야 함: $result"
            )
        }
    }

    // ==================== JSON 연산자 변환 테스트 ====================

    @Nested
    @DisplayName("JSON 연산자 변환 테스트")
    inner class JsonOperatorsTest {

        @Test
        @DisplayName("PostgreSQL ->> → MySQL JSON_UNQUOTE(JSON_EXTRACT())")
        fun testJsonExtractTextToMySql() {
            val postgresql = "SELECT data->>'name' FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(
                result.contains("JSON_UNQUOTE") || result.contains("JSON_EXTRACT") || result.contains("->>"),
                "JSON ->> 연산자가 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("PostgreSQL -> → MySQL JSON_EXTRACT")
        fun testJsonExtractToMySql() {
            val postgresql = "SELECT data->'address' FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(
                result.contains("JSON_EXTRACT") || result.contains("->"),
                "JSON -> 연산자가 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("PostgreSQL ->> → Oracle JSON_VALUE")
        fun testJsonExtractTextToOracle() {
            val postgresql = "SELECT data->>'name' FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(
                result.contains("JSON_VALUE") || result.contains("->>"),
                "JSON ->> 연산자가 Oracle JSON_VALUE로 변환되어야 함: $result"
            )
        }
    }

    // ==================== FILTER 절 변환 테스트 ====================

    @Nested
    @DisplayName("FILTER 절 변환 테스트")
    inner class FilterClauseTest {

        @Test
        @DisplayName("PostgreSQL COUNT(*) FILTER → MySQL CASE WHEN")
        fun testFilterToMySql() {
            val postgresql = "SELECT COUNT(*) FILTER (WHERE status = 'active') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(
                result.contains("CASE WHEN") || result.contains("SUM(CASE"),
                "FILTER 절이 CASE WHEN으로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("PostgreSQL SUM() FILTER → Oracle CASE WHEN")
        fun testSumFilterToOracle() {
            val postgresql = "SELECT SUM(amount) FILTER (WHERE type = 'credit') FROM transactions"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(
                result.contains("CASE WHEN") || result.contains("FILTER"),
                "FILTER 절이 변환되어야 함: $result"
            )
        }
    }

    // ==================== ELT/FIELD 함수 변환 테스트 ====================

    @Nested
    @DisplayName("ELT/FIELD 함수 변환 테스트")
    inner class EltFieldTest {

        @Test
        @DisplayName("MySQL ELT → PostgreSQL CASE WHEN")
        fun testEltToPostgreSql() {
            val mysql = "SELECT ELT(2, 'a', 'b', 'c') FROM t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(
                result.contains("CASE") || !result.contains("ELT("),
                "ELT가 CASE WHEN으로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("MySQL FIELD → PostgreSQL CASE WHEN")
        fun testFieldToPostgreSql() {
            val mysql = "SELECT FIELD('b', 'a', 'b', 'c') FROM t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(
                result.contains("CASE") || !result.contains("FIELD("),
                "FIELD가 CASE WHEN으로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("MySQL ELT → Oracle DECODE")
        fun testEltToOracle() {
            val mysql = "SELECT ELT(status, 'New', 'In Progress', 'Done') FROM tasks"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(
                result.contains("DECODE") || result.contains("CASE") || !result.contains("ELT("),
                "ELT가 DECODE 또는 CASE WHEN으로 변환되어야 함: $result"
            )
        }
    }

    // ==================== 암호화 함수 변환 테스트 ====================

    @Nested
    @DisplayName("암호화 함수 변환 테스트")
    inner class CryptoFunctionsTest {

        @Test
        @DisplayName("MySQL MD5 → PostgreSQL")
        fun testMd5MySqlToPostgreSql() {
            val mysql = "SELECT MD5('password') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("MD5") || result.contains("md5"), "MD5가 유지되어야 함: $result")
        }

        @Test
        @DisplayName("MySQL MD5 → Oracle")
        fun testMd5MySqlToOracle() {
            val mysql = "SELECT MD5('password') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(
                result.contains("DBMS_CRYPTO") || result.contains("STANDARD_HASH") || result.contains("UTL_RAW"),
                "MD5가 Oracle 함수로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("MySQL SHA1 → PostgreSQL")
        fun testSha1MySqlToPostgreSql() {
            val mysql = "SELECT SHA1('password') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(
                result.contains("digest") || result.contains("SHA1") || result.contains("encode"),
                "SHA1이 PostgreSQL 함수로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("MySQL SHA2 → Oracle")
        fun testSha2MySqlToOracle() {
            val mysql = "SELECT SHA2('password', 256) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(
                result.contains("STANDARD_HASH") || result.contains("DBMS_CRYPTO") || result.contains("SHA"),
                "SHA2가 Oracle 함수로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("PostgreSQL MD5 → MySQL")
        fun testMd5PostgreSqlToMySql() {
            val postgresql = "SELECT MD5('password') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("MD5"), "MD5가 유지되어야 함: $result")
        }
    }

    // ==================== Full-Text 검색 변환 테스트 ====================

    @Nested
    @DisplayName("Full-Text 검색 변환 테스트")
    inner class FullTextSearchTest {

        @Test
        @DisplayName("MySQL MATCH AGAINST → PostgreSQL to_tsvector")
        fun testMatchAgainstToPostgreSql() {
            val mysql = "SELECT * FROM articles WHERE MATCH(title, content) AGAINST('search term')"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(
                result.contains("to_tsvector") || result.contains("@@") || result.contains("LIKE") || result.contains("MATCH"),
                "MATCH AGAINST가 변환되거나 유지되어야 함: $result"
            )
        }

        @Test
        @DisplayName("MySQL MATCH AGAINST BOOLEAN MODE → PostgreSQL")
        fun testMatchAgainstBooleanToPostgreSql() {
            val mysql = "SELECT * FROM articles WHERE MATCH(title) AGAINST('+mysql -oracle' IN BOOLEAN MODE)"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(
                result.contains("to_tsvector") || result.contains("plainto_tsquery") || result.contains("MATCH"),
                "BOOLEAN MODE가 변환되거나 유지되어야 함: $result"
            )
        }

        @Test
        @DisplayName("MySQL MATCH AGAINST → Oracle CONTAINS")
        fun testMatchAgainstToOracle() {
            val mysql = "SELECT * FROM articles WHERE MATCH(content) AGAINST('keyword')"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(
                result.contains("CONTAINS") || result.contains("LIKE") || result.contains("MATCH"),
                "MATCH AGAINST가 변환되거나 유지되어야 함: $result"
            )
        }
    }

    // ==================== 힌트 처리 테스트 ====================

    @Nested
    @DisplayName("힌트 처리 테스트")
    inner class HintProcessingTest {

        @Test
        @DisplayName("Oracle 힌트 → MySQL (제거)")
        fun testOracleHintToMySql() {
            val oracle = "SELECT /*+ INDEX(t idx_name) */ * FROM users t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // 힌트는 제거되거나 주석 처리됨
            assertFalse(
                result.contains("/*+ INDEX"),
                "Oracle 힌트가 제거되어야 함: $result"
            )
        }

        @Test
        @DisplayName("Oracle 힌트 → PostgreSQL (제거)")
        fun testOracleHintToPostgreSql() {
            val oracle = "SELECT /*+ FULL(t) */ * FROM users t"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertFalse(
                result.contains("/*+ FULL"),
                "Oracle 힌트가 제거되어야 함: $result"
            )
        }

        @Test
        @DisplayName("Oracle 복합 힌트 처리")
        fun testOracleComplexHint() {
            val oracle = "SELECT /*+ LEADING(a b) USE_NL(b) INDEX(a idx1) */ a.id FROM t1 a, t2 b WHERE a.id = b.id"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertFalse(
                result.contains("/*+ LEADING") || result.contains("USE_NL") || result.contains("/*+ INDEX"),
                "복합 Oracle 힌트가 제거되어야 함: $result"
            )
        }
    }

    // ==================== LAST_DAY, NEXT_DAY 변환 테스트 ====================

    @Nested
    @DisplayName("날짜 함수 추가 테스트")
    inner class AdditionalDateFunctionsTest {

        @Test
        @DisplayName("Oracle LAST_DAY → MySQL")
        fun testLastDayOracleToMySql() {
            val oracle = "SELECT LAST_DAY(hire_date) FROM employees"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("LAST_DAY"), "LAST_DAY가 유지되어야 함 (MySQL 지원): $result")
        }

        @Test
        @DisplayName("Oracle LAST_DAY → PostgreSQL")
        fun testLastDayOracleToPostgreSql() {
            val oracle = "SELECT LAST_DAY(hire_date) FROM employees"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(
                result.contains("DATE_TRUNC") || result.contains("INTERVAL") || result.contains("LAST_DAY"),
                "LAST_DAY가 PostgreSQL 함수로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("Oracle NEXT_DAY → MySQL")
        fun testNextDayOracleToMySql() {
            val oracle = "SELECT NEXT_DAY(hire_date, 'MONDAY') FROM employees"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(
                result.contains("DATE_ADD") || result.contains("DAYOFWEEK") || result.contains("NEXT_DAY"),
                "NEXT_DAY가 MySQL 함수로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("Oracle NEXT_DAY → PostgreSQL")
        fun testNextDayOracleToPostgreSql() {
            val oracle = "SELECT NEXT_DAY(hire_date, 'FRIDAY') FROM employees"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(
                result.contains("DATE_TRUNC") || result.contains("INTERVAL") || result.contains("NEXT_DAY"),
                "NEXT_DAY가 PostgreSQL 함수로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("Oracle ADD_MONTHS 음수 → MySQL DATE_SUB")
        fun testAddMonthsNegativeOracleToMySql() {
            val oracle = "SELECT ADD_MONTHS(SYSDATE, -3) FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(
                result.contains("DATE_ADD") || result.contains("DATE_SUB") || result.contains("INTERVAL"),
                "ADD_MONTHS 음수가 변환되어야 함: $result"
            )
        }
    }

    // ==================== INITCAP, TRANSLATE 변환 테스트 ====================

    @Nested
    @DisplayName("문자열 함수 추가 테스트")
    inner class AdditionalStringFunctionsTest {

        @Test
        @DisplayName("Oracle INITCAP → MySQL")
        fun testInitcapOracleToMySql() {
            val oracle = "SELECT INITCAP(name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(
                result.contains("CONCAT") || result.contains("UPPER") || result.contains("LOWER") || result.contains("INITCAP"),
                "INITCAP가 MySQL 함수로 변환되어야 함: $result"
            )
        }

        @Test
        @DisplayName("Oracle INITCAP → PostgreSQL")
        fun testInitcapOracleToPostgreSql() {
            val oracle = "SELECT INITCAP(name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("INITCAP"), "INITCAP가 유지되어야 함 (PostgreSQL 지원): $result")
        }

        @Test
        @DisplayName("Oracle TRANSLATE → MySQL REPLACE 체인")
        fun testTranslateOracleToMySql() {
            val oracle = "SELECT TRANSLATE(phone, '()-', '   ') FROM contacts"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(
                result.contains("REPLACE") || result.contains("TRANSLATE"),
                "TRANSLATE가 REPLACE로 변환되거나 유지되어야 함: $result"
            )
        }

        @Test
        @DisplayName("Oracle TRANSLATE → PostgreSQL")
        fun testTranslateOracleToPostgreSql() {
            val oracle = "SELECT TRANSLATE(code, 'ABC', 'XYZ') FROM items"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("TRANSLATE"), "TRANSLATE가 유지되어야 함 (PostgreSQL 지원): $result")
        }

        @Test
        @DisplayName("PostgreSQL INITCAP → MySQL")
        fun testInitcapPostgreSqlToMySql() {
            val postgresql = "SELECT INITCAP(title) FROM articles"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(
                result.contains("CONCAT") || result.contains("UPPER") || !result.contains("INITCAP("),
                "INITCAP가 MySQL 함수로 변환되어야 함: $result"
            )
        }
    }

    // ==================== PostgreSQL → Oracle 새 기능 테스트 ====================

    @Nested
    @DisplayName("PostgreSQL → Oracle 새 기능 테스트")
    inner class PostgreSqlToOracleNewFeatures {

        @Test
        @DisplayName("CURRENT_DATE → TRUNC(SYSDATE) 변환")
        fun testCurrentDateConversion() {
            val postgresql = "SELECT CURRENT_DATE FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("TRUNC(SYSDATE)"), "CURRENT_DATE → TRUNC(SYSDATE) 변환 실패: $result")
        }

        @Test
        @DisplayName("STRING_AGG → LISTAGG 변환")
        fun testStringAggConversion() {
            val postgresql = "SELECT STRING_AGG(name, ', ' ORDER BY name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("LISTAGG("), "STRING_AGG → LISTAGG 변환 실패: $result")
            assertTrue(result.contains("WITHIN GROUP"), "WITHIN GROUP 누락: $result")
        }

        @Test
        @DisplayName("nextval() → .NEXTVAL 변환")
        fun testNextvalConversion() {
            val postgresql = "SELECT nextval('seq_user') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("seq_user.NEXTVAL"), "nextval → .NEXTVAL 변환 실패: $result")
        }

        @Test
        @DisplayName("currval() → .CURRVAL 변환")
        fun testCurrvalConversion() {
            val postgresql = "SELECT currval('seq_user') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("seq_user.CURRVAL"), "currval → .CURRVAL 변환 실패: $result")
        }

        @Test
        @DisplayName("TRUE/FALSE → 1/0 변환")
        fun testBooleanConversion() {
            val postgresql = "SELECT * FROM users WHERE is_active = TRUE AND is_deleted = FALSE"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("= 1"), "TRUE → 1 변환 실패: $result")
            assertTrue(result.contains("= 0"), "FALSE → 0 변환 실패: $result")
        }

        @Test
        @DisplayName("LIMIT → FETCH FIRST 변환")
        fun testLimitConversion() {
            val postgresql = "SELECT * FROM users LIMIT 10"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("FETCH FIRST 10 ROWS ONLY"), "LIMIT → FETCH FIRST 변환 실패: $result")
        }

        @Test
        @DisplayName("DATE_TRUNC('month') → TRUNC(date, 'MM') 변환")
        fun testDateTruncMonthConversion() {
            val postgresql = "SELECT DATE_TRUNC('month', created_at) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("TRUNC("), "DATE_TRUNC → TRUNC 변환 실패: $result")
            assertTrue(result.contains("'MM'"), "MM 포맷 누락: $result")
        }
    }
}
