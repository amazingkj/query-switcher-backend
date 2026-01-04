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
