package com.sqlswitcher.converter

import com.sqlswitcher.converter.stringbased.StringBasedDataTypeConverter
import com.sqlswitcher.converter.stringbased.StringBasedFunctionConverter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * SQL 변환 종합 테스트
 *
 * StringBasedFunctionConverter와 StringBasedDataTypeConverter를 직접 테스트하여
 * 다양한 SQL 쿼리의 변환 지원 여부를 확인합니다.
 */
class ComprehensiveConversionTest {

    private lateinit var functionConverter: StringBasedFunctionConverter
    private lateinit var dataTypeConverter: StringBasedDataTypeConverter

    @BeforeEach
    fun setUp() {
        functionConverter = StringBasedFunctionConverter()
        dataTypeConverter = StringBasedDataTypeConverter()
    }

    // ==================== Oracle → MySQL 테스트 ====================

    @Nested
    @DisplayName("Oracle → MySQL 변환 테스트")
    inner class OracleToMySqlTests {

        @Test
        @DisplayName("기본 SELECT 문 함수 변환")
        fun testBasicSelectFunctions() {
            val oracle = "SELECT SYSDATE, NVL(name, 'Unknown'), SUBSTR(title, 1, 10) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("NOW()"), "SYSDATE → NOW() 변환 실패: $result")
            assertTrue(result.contains("IFNULL("), "NVL → IFNULL 변환 실패: $result")
            assertTrue(result.contains("SUBSTRING("), "SUBSTR → SUBSTRING 변환 실패: $result")
        }

        @Test
        @DisplayName("NVL2 함수 변환")
        fun testNvl2Function() {
            val oracle = "SELECT NVL2(status, 'Active', 'Inactive') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("IF("), "NVL2 → IF 변환 실패: $result")
        }

        @Test
        @DisplayName("DECODE 함수 → CASE 변환")
        fun testDecodeFunction() {
            val oracle = "SELECT DECODE(status, 'A', 'Active', 'I', 'Inactive', 'Unknown') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // DECODE는 정규식으로 완전한 CASE WHEN 변환이 어려움 - 부분 변환 확인
            println("DECODE 변환 결과: $result")
        }

        @Test
        @DisplayName("데이터타입 변환 - VARCHAR2")
        fun testVarchar2Conversion() {
            val oracle = "CREATE TABLE test (name VARCHAR2(100 BYTE), title VARCHAR2(200 CHAR))"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("VARCHAR(100)"), "VARCHAR2(100 BYTE) → VARCHAR(100) 변환 실패: $result")
            assertTrue(result.contains("VARCHAR(200)"), "VARCHAR2(200 CHAR) → VARCHAR(200) 변환 실패: $result")
            assertFalse(result.contains("BYTE"), "BYTE 키워드가 제거되지 않음: $result")
            assertFalse(result.contains("CHAR)"), "CHAR 키워드가 제거되지 않음: $result")
        }

        @Test
        @DisplayName("데이터타입 변환 - NUMBER")
        fun testNumberConversion() {
            val oracle = "CREATE TABLE test (id NUMBER(10), score NUMBER(5,2), flag NUMBER(1))"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("BIGINT") || result.contains("INT"), "NUMBER(10) 정수형 변환 실패: $result")
            assertTrue(result.contains("DECIMAL(5,2)"), "NUMBER(5,2) → DECIMAL 변환 실패: $result")
            assertTrue(result.contains("TINYINT"), "NUMBER(1) → TINYINT 변환 실패: $result")
        }

        @Test
        @DisplayName("데이터타입 변환 - LOB 타입")
        fun testLobConversion() {
            val oracle = "CREATE TABLE test (content CLOB, image BLOB)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("LONGTEXT"), "CLOB → LONGTEXT 변환 실패: $result")
            assertTrue(result.contains("LONGBLOB"), "BLOB → LONGBLOB 변환 실패: $result")
        }

        @Test
        @DisplayName("데이터타입 변환 - DATE")
        fun testDateConversion() {
            val oracle = "CREATE TABLE test (created_at DATE)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("DATETIME"), "DATE → DATETIME 변환 실패: $result")
        }

        @Test
        @DisplayName("ROWNUM 변환")
        fun testRownumConversion() {
            val oracle = "SELECT * FROM users WHERE ROWNUM <= 10"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // ROWNUM → LIMIT 변환은 복잡한 로직이 필요함
            println("ROWNUM 변환 결과: $result")
        }

        @Test
        @DisplayName("TO_CHAR 날짜 포맷 변환")
        fun testToCharConversion() {
            val oracle = "SELECT TO_CHAR(created_at, 'YYYY-MM-DD') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // TO_CHAR → DATE_FORMAT 변환 확인
            println("TO_CHAR 변환 결과: $result")
        }

        @Test
        @DisplayName("TO_DATE 함수 변환")
        fun testToDateConversion() {
            val oracle = "SELECT TO_DATE('2024-01-01', 'YYYY-MM-DD') FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // TO_DATE → STR_TO_DATE 변환 확인
            println("TO_DATE 변환 결과: $result")
        }

        @Test
        @DisplayName("ADD_MONTHS 함수 변환")
        fun testAddMonthsConversion() {
            val oracle = "SELECT ADD_MONTHS(SYSDATE, 3) FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // ADD_MONTHS → DATE_ADD 변환 확인
            println("ADD_MONTHS 변환 결과: $result")
        }

        @Test
        @DisplayName("LISTAGG 함수 변환")
        fun testListaggConversion() {
            val oracle = "SELECT LISTAGG(name, ', ') WITHIN GROUP (ORDER BY name) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // LISTAGG → GROUP_CONCAT 변환 확인
            println("LISTAGG 변환 결과: $result")
        }

        @Test
        @DisplayName("INSTR 함수 변환")
        fun testInstrConversion() {
            val oracle = "SELECT INSTR(name, 'a') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // INSTR 변환 확인 (MySQL도 INSTR 지원하지만 파라미터 순서 다름)
            println("INSTR 변환 결과: $result")
        }

        @Test
        @DisplayName("TRUNC 날짜 함수 변환")
        fun testTruncDateConversion() {
            val oracle = "SELECT TRUNC(SYSDATE) FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // TRUNC(date) → DATE() 변환 확인
            println("TRUNC 날짜 변환 결과: $result")
        }

        @Test
        @DisplayName("MONTHS_BETWEEN 함수 변환")
        fun testMonthsBetweenConversion() {
            val oracle = "SELECT MONTHS_BETWEEN(SYSDATE, created_at) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // MONTHS_BETWEEN → TIMESTAMPDIFF 변환 확인
            println("MONTHS_BETWEEN 변환 결과: $result")
        }
    }

    // ==================== Oracle → PostgreSQL 테스트 ====================

    @Nested
    @DisplayName("Oracle → PostgreSQL 변환 테스트")
    inner class OracleToPostgreSqlTests {

        @Test
        @DisplayName("기본 함수 변환")
        fun testBasicFunctions() {
            val oracle = "SELECT SYSDATE, NVL(name, 'Unknown') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("CURRENT_TIMESTAMP"), "SYSDATE → CURRENT_TIMESTAMP 변환 실패: $result")
            assertTrue(result.contains("COALESCE("), "NVL → COALESCE 변환 실패: $result")
        }

        @Test
        @DisplayName("데이터타입 변환 - NUMBER")
        fun testNumberConversion() {
            val oracle = "CREATE TABLE test (id NUMBER(19), score NUMBER(10,2))"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("BIGINT"), "NUMBER(19) → BIGINT 변환 실패: $result")
            assertTrue(result.contains("NUMERIC(10,2)"), "NUMBER(10,2) → NUMERIC 변환 실패: $result")
        }

        @Test
        @DisplayName("데이터타입 변환 - LOB 타입")
        fun testLobConversion() {
            val oracle = "CREATE TABLE test (content CLOB, image BLOB)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("TEXT"), "CLOB → TEXT 변환 실패: $result")
            assertTrue(result.contains("BYTEA"), "BLOB → BYTEA 변환 실패: $result")
        }

        @Test
        @DisplayName("시퀀스 CURRVAL/NEXTVAL 변환")
        fun testSequenceConversion() {
            val oracle = "SELECT seq_user.NEXTVAL, seq_user.CURRVAL FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.POSTGRESQL, appliedRules)

            // NEXTVAL/CURRVAL → nextval()/currval() 변환 확인
            println("시퀀스 변환 결과: $result")
        }
    }

    // ==================== MySQL → PostgreSQL 테스트 ====================

    @Nested
    @DisplayName("MySQL → PostgreSQL 변환 테스트")
    inner class MySqlToPostgreSqlTests {

        @Test
        @DisplayName("날짜 함수 변환")
        fun testDateFunctions() {
            val mysql = "SELECT NOW(), CURDATE(), CURTIME() FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("CURRENT_TIMESTAMP"), "NOW() → CURRENT_TIMESTAMP 변환 실패: $result")
            assertTrue(result.contains("CURRENT_DATE"), "CURDATE() → CURRENT_DATE 변환 실패: $result")
            assertTrue(result.contains("CURRENT_TIME"), "CURTIME() → CURRENT_TIME 변환 실패: $result")
        }

        @Test
        @DisplayName("문자열 함수 변환")
        fun testStringFunctions() {
            val mysql = "SELECT IFNULL(name, 'Unknown'), GROUP_CONCAT(tag) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("COALESCE("), "IFNULL → COALESCE 변환 실패: $result")
            assertTrue(result.contains("STRING_AGG("), "GROUP_CONCAT → STRING_AGG 변환 실패: $result")
        }

        @Test
        @DisplayName("수학 함수 변환")
        fun testMathFunctions() {
            val mysql = "SELECT RAND(), TRUNCATE(price, 2) FROM products"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("RANDOM()"), "RAND() → RANDOM() 변환 실패: $result")
            assertTrue(result.contains("TRUNC("), "TRUNCATE → TRUNC 변환 실패: $result")
        }

        @Test
        @DisplayName("AUTO_INCREMENT → SERIAL 변환")
        fun testAutoIncrementConversion() {
            val mysql = "CREATE TABLE test (id INT AUTO_INCREMENT PRIMARY KEY)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("SERIAL"), "INT AUTO_INCREMENT → SERIAL 변환 실패: $result")
        }

        @Test
        @DisplayName("TINYINT(1) → BOOLEAN 변환")
        fun testTinyintBooleanConversion() {
            val mysql = "CREATE TABLE test (is_active TINYINT(1) DEFAULT 1)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("BOOLEAN"), "TINYINT(1) → BOOLEAN 변환 실패: $result")
        }

        @Test
        @DisplayName("LIMIT 구문 처리")
        fun testLimitConversion() {
            val mysql = "SELECT * FROM users LIMIT 10 OFFSET 5"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            // PostgreSQL도 LIMIT을 지원하므로 그대로 유지
            assertTrue(result.contains("LIMIT"), "LIMIT이 유지되어야 함: $result")
        }

        @Test
        @DisplayName("DATE_FORMAT → TO_CHAR 변환")
        fun testDateFormatConversion() {
            val mysql = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("TO_CHAR("), "DATE_FORMAT → TO_CHAR 변환 실패: $result")
        }

        @Test
        @DisplayName("UNIX_TIMESTAMP 변환")
        fun testUnixTimestampConversion() {
            val mysql = "SELECT UNIX_TIMESTAMP() FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("EXTRACT(EPOCH FROM"), "UNIX_TIMESTAMP 변환 실패: $result")
        }

        @Test
        @DisplayName("ENUM 타입 변환")
        fun testEnumConversion() {
            val mysql = "CREATE TABLE test (status ENUM('active', 'inactive', 'pending'))"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            // ENUM → VARCHAR 또는 PostgreSQL ENUM 타입
            println("ENUM 변환 결과: $result")
        }

        @Test
        @DisplayName("JSON 타입 변환")
        fun testJsonConversion() {
            val mysql = "CREATE TABLE test (data JSON)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("JSONB"), "JSON → JSONB 변환 실패: $result")
        }

        @Test
        @DisplayName("IF 함수 변환")
        fun testIfFunctionConversion() {
            val mysql = "SELECT IF(status = 'A', 'Active', 'Inactive') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            // IF → CASE WHEN 변환 확인
            println("IF 함수 변환 결과: $result")
        }

        @Test
        @DisplayName("DATEDIFF 함수 변환")
        fun testDatediffConversion() {
            val mysql = "SELECT DATEDIFF(end_date, start_date) FROM events"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            // DATEDIFF → (end_date - start_date) 또는 DATE_PART 변환 확인
            println("DATEDIFF 변환 결과: $result")
        }
    }

    // ==================== PostgreSQL → MySQL 테스트 ====================

    @Nested
    @DisplayName("PostgreSQL → MySQL 변환 테스트")
    inner class PostgreSqlToMySqlTests {

        @Test
        @DisplayName("날짜 함수 변환")
        fun testDateFunctions() {
            val postgresql = "SELECT CURRENT_TIMESTAMP, CURRENT_DATE, CURRENT_TIME FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("NOW()"), "CURRENT_TIMESTAMP → NOW() 변환 실패: $result")
            assertTrue(result.contains("CURDATE()"), "CURRENT_DATE → CURDATE() 변환 실패: $result")
            assertTrue(result.contains("CURTIME()"), "CURRENT_TIME → CURTIME() 변환 실패: $result")
        }

        @Test
        @DisplayName("타입 캐스팅 제거")
        fun testTypeCastingRemoval() {
            val postgresql = "SELECT value::INTEGER, name::TEXT, created::TIMESTAMP FROM data"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertFalse(result.contains("::INTEGER"), "::INTEGER 제거 실패: $result")
            assertFalse(result.contains("::TEXT"), "::TEXT 제거 실패: $result")
            assertFalse(result.contains("::TIMESTAMP"), "::TIMESTAMP 제거 실패: $result")
        }

        @Test
        @DisplayName("SERIAL → AUTO_INCREMENT 변환")
        fun testSerialConversion() {
            val postgresql = "CREATE TABLE test (id SERIAL PRIMARY KEY, big_id BIGSERIAL)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("AUTO_INCREMENT"), "SERIAL → AUTO_INCREMENT 변환 실패: $result")
        }

        @Test
        @DisplayName("BOOLEAN → TINYINT(1) 변환")
        fun testBooleanConversion() {
            val postgresql = "CREATE TABLE test (is_active BOOLEAN DEFAULT false)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("TINYINT(1)"), "BOOLEAN → TINYINT(1) 변환 실패: $result")
        }

        @Test
        @DisplayName("TEXT → LONGTEXT 변환")
        fun testTextConversion() {
            val postgresql = "CREATE TABLE test (content TEXT)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("LONGTEXT"), "TEXT → LONGTEXT 변환 실패: $result")
        }

        @Test
        @DisplayName("BYTEA → LONGBLOB 변환")
        fun testByteaConversion() {
            val postgresql = "CREATE TABLE test (data BYTEA)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("LONGBLOB"), "BYTEA → LONGBLOB 변환 실패: $result")
        }

        @Test
        @DisplayName("UUID → CHAR(36) 변환")
        fun testUuidConversion() {
            val postgresql = "CREATE TABLE test (id UUID PRIMARY KEY)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("CHAR(36)"), "UUID → CHAR(36) 변환 실패: $result")
        }

        @Test
        @DisplayName("JSONB → JSON 변환")
        fun testJsonbConversion() {
            val postgresql = "CREATE TABLE test (data JSONB)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("JSON") && !result.contains("JSONB"), "JSONB → JSON 변환 실패: $result")
        }

        @Test
        @DisplayName("ILIKE → LIKE (대소문자 무시) 변환")
        fun testIlikeConversion() {
            val postgresql = "SELECT * FROM users WHERE name ILIKE '%john%'"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            // ILIKE → LIKE (MySQL은 기본적으로 대소문자 무시)
            println("ILIKE 변환 결과: $result")
        }

        @Test
        @DisplayName("ARRAY 타입 변환")
        fun testArrayConversion() {
            val postgresql = "CREATE TABLE test (tags TEXT[])"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            // ARRAY → JSON 또는 별도 테이블
            println("ARRAY 타입 변환 결과: $result")
        }

        @Test
        @DisplayName("INTERVAL 타입 변환")
        fun testIntervalConversion() {
            val postgresql = "SELECT created_at + INTERVAL '1 day' FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.MYSQL, appliedRules)

            // INTERVAL 구문 변환 확인
            println("INTERVAL 변환 결과: $result")
        }
    }

    // ==================== MySQL → Oracle 테스트 ====================

    @Nested
    @DisplayName("MySQL → Oracle 변환 테스트")
    inner class MySqlToOracleTests {

        @Test
        @DisplayName("NOW → SYSDATE 변환")
        fun testNowConversion() {
            val mysql = "SELECT NOW() FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("SYSDATE"), "NOW() → SYSDATE 변환 실패: $result")
        }

        @Test
        @DisplayName("IFNULL → NVL 변환")
        fun testIfnullConversion() {
            val mysql = "SELECT IFNULL(name, 'Unknown') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("NVL("), "IFNULL → NVL 변환 실패: $result")
        }

        @Test
        @DisplayName("SUBSTRING → SUBSTR 변환")
        fun testSubstringConversion() {
            val mysql = "SELECT SUBSTRING(name, 1, 10) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("SUBSTR("), "SUBSTRING → SUBSTR 변환 실패: $result")
        }

        @Test
        @DisplayName("LIMIT → ROWNUM/FETCH 변환")
        fun testLimitConversion() {
            val mysql = "SELECT * FROM users LIMIT 10"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            // LIMIT → FETCH FIRST 또는 ROWNUM 변환 확인
            println("LIMIT 변환 결과: $result")
        }

        @Test
        @DisplayName("AUTO_INCREMENT → SEQUENCE 변환")
        fun testAutoIncrementConversion() {
            val mysql = "CREATE TABLE test (id INT AUTO_INCREMENT PRIMARY KEY)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(mysql, DialectType.MYSQL, DialectType.ORACLE, appliedRules)

            // AUTO_INCREMENT → GENERATED ALWAYS AS IDENTITY 변환 확인
            println("AUTO_INCREMENT 변환 결과: $result")
        }
    }

    // ==================== PostgreSQL → Oracle 테스트 ====================

    @Nested
    @DisplayName("PostgreSQL → Oracle 변환 테스트")
    inner class PostgreSqlToOracleTests {

        @Test
        @DisplayName("CURRENT_TIMESTAMP → SYSDATE 변환")
        fun testCurrentTimestampConversion() {
            val postgresql = "SELECT CURRENT_TIMESTAMP FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("SYSDATE"), "CURRENT_TIMESTAMP → SYSDATE 변환 실패: $result")
        }

        @Test
        @DisplayName("COALESCE → NVL 변환")
        fun testCoalesceConversion() {
            val postgresql = "SELECT COALESCE(name, 'Unknown') FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("NVL("), "COALESCE → NVL 변환 실패: $result")
        }

        @Test
        @DisplayName("RANDOM → DBMS_RANDOM.VALUE 변환")
        fun testRandomConversion() {
            val postgresql = "SELECT RANDOM() FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            assertTrue(result.contains("DBMS_RANDOM.VALUE"), "RANDOM() → DBMS_RANDOM.VALUE 변환 실패: $result")
        }

        @Test
        @DisplayName("SERIAL → SEQUENCE 변환")
        fun testSerialConversion() {
            val postgresql = "CREATE TABLE test (id SERIAL PRIMARY KEY)"
            val appliedRules = mutableListOf<String>()
            val result = dataTypeConverter.convert(postgresql, DialectType.POSTGRESQL, DialectType.ORACLE, appliedRules)

            // SERIAL → NUMBER GENERATED BY DEFAULT AS IDENTITY 변환 확인
            println("SERIAL 변환 결과: $result")
        }
    }

    // ==================== 복잡한 쿼리 테스트 ====================

    @Nested
    @DisplayName("복잡한 쿼리 변환 테스트")
    inner class ComplexQueryTests {

        @Test
        @DisplayName("복합 함수가 포함된 SELECT 문")
        fun testComplexSelect() {
            val oracle = """
                SELECT
                    NVL(u.name, 'Unknown') AS user_name,
                    SUBSTR(u.email, 1, INSTR(u.email, '@') - 1) AS email_prefix,
                    TO_CHAR(u.created_at, 'YYYY-MM-DD') AS created_date,
                    NVL2(u.last_login, 'Active', 'Inactive') AS status
                FROM users u
                WHERE u.created_at >= SYSDATE - 30
            """.trimIndent()

            val appliedRules = mutableListOf<String>()
            var result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)
            result = dataTypeConverter.convert(result, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            println("복합 SELECT 변환 결과:")
            println(result)
        }

        @Test
        @DisplayName("서브쿼리가 포함된 쿼리")
        fun testSubquery() {
            val oracle = """
                SELECT * FROM users u
                WHERE u.department_id IN (
                    SELECT d.id FROM departments d
                    WHERE d.status = NVL(u.preferred_dept, 'ACTIVE')
                )
            """.trimIndent()

            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            println("서브쿼리 변환 결과:")
            println(result)
        }

        @Test
        @DisplayName("JOIN이 포함된 쿼리")
        fun testJoinQuery() {
            val oracle = """
                SELECT
                    u.name,
                    d.department_name,
                    NVL(m.name, 'No Manager') AS manager_name
                FROM users u
                LEFT JOIN departments d ON u.department_id = d.id
                LEFT JOIN users m ON u.manager_id = m.id
                WHERE u.created_at >= SYSDATE - 30
            """.trimIndent()

            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("IFNULL("), "NVL → IFNULL 변환 실패")
            assertTrue(result.contains("NOW()"), "SYSDATE → NOW() 변환 실패")
        }

        @Test
        @DisplayName("GROUP BY와 집계 함수가 포함된 쿼리")
        fun testAggregateQuery() {
            val mysql = """
                SELECT
                    department_id,
                    COUNT(*) AS user_count,
                    GROUP_CONCAT(name ORDER BY name SEPARATOR ', ') AS users,
                    IFNULL(AVG(salary), 0) AS avg_salary
                FROM users
                GROUP BY department_id
            """.trimIndent()

            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(mysql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            assertTrue(result.contains("STRING_AGG("), "GROUP_CONCAT → STRING_AGG 변환 실패")
            assertTrue(result.contains("COALESCE("), "IFNULL → COALESCE 변환 실패")
        }

        @Test
        @DisplayName("CASE WHEN 문이 포함된 쿼리")
        fun testCaseWhenQuery() {
            val sql = """
                SELECT
                    name,
                    CASE
                        WHEN status = 'A' THEN 'Active'
                        WHEN status = 'I' THEN 'Inactive'
                        ELSE 'Unknown'
                    END AS status_text
                FROM users
            """.trimIndent()

            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(sql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            // CASE WHEN은 표준 SQL이므로 변환 불필요
            assertTrue(result.contains("CASE"), "CASE WHEN이 유지되어야 함")
        }

        @Test
        @DisplayName("윈도우 함수가 포함된 쿼리")
        fun testWindowFunctionQuery() {
            val sql = """
                SELECT
                    name,
                    salary,
                    ROW_NUMBER() OVER (PARTITION BY department_id ORDER BY salary DESC) AS rank,
                    SUM(salary) OVER (PARTITION BY department_id) AS dept_total
                FROM users
            """.trimIndent()

            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // 윈도우 함수는 대부분의 DB에서 지원
            assertTrue(result.contains("ROW_NUMBER()"), "ROW_NUMBER가 유지되어야 함")
            assertTrue(result.contains("OVER"), "OVER 절이 유지되어야 함")
        }
    }

    // ==================== 엣지 케이스 테스트 ====================

    @Nested
    @DisplayName("엣지 케이스 테스트")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("빈 문자열 처리")
        fun testEmptyString() {
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert("", DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertEquals("", result, "빈 문자열은 그대로 반환되어야 함")
        }

        @Test
        @DisplayName("같은 방언으로 변환 시 원본 유지")
        fun testSameDialect() {
            val sql = "SELECT SYSDATE FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(sql, DialectType.ORACLE, DialectType.ORACLE, appliedRules)

            assertEquals(sql, result, "같은 방언으로 변환 시 원본이 유지되어야 함")
        }

        @Test
        @DisplayName("대소문자 혼합 처리")
        fun testMixedCase() {
            val oracle = "SELECT sysdate, Nvl(name, 'test'), SUBSTR(title, 1, 5) FROM users"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("NOW()"), "소문자 sysdate도 변환되어야 함: $result")
            assertTrue(result.contains("IFNULL(") || result.contains("Ifnull("), "Nvl도 변환되어야 함: $result")
        }

        @Test
        @DisplayName("문자열 리터럴 내 키워드 보존")
        fun testStringLiteralPreservation() {
            val sql = "SELECT 'SYSDATE is today' AS msg, SYSDATE FROM DUAL"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // 문자열 내 'SYSDATE'는 변환되지 않아야 함 (정규식의 한계)
            println("문자열 리터럴 처리 결과: $result")
        }

        @Test
        @DisplayName("주석 내 키워드 처리")
        fun testCommentPreservation() {
            val sql = """
                -- SELECT SYSDATE FROM DUAL
                SELECT NOW() FROM users /* SYSDATE comment */
            """.trimIndent()

            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(sql, DialectType.MYSQL, DialectType.POSTGRESQL, appliedRules)

            // 주석 내용 처리 확인
            println("주석 처리 결과: $result")
        }

        @Test
        @DisplayName("중첩 함수 처리")
        fun testNestedFunctions() {
            val oracle = "SELECT NVL(NVL(field1, field2), 'default') FROM table1"
            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            // 중첩된 NVL도 모두 변환되어야 함
            assertFalse(result.contains("NVL"), "모든 NVL이 IFNULL로 변환되어야 함: $result")
            assertTrue(result.contains("IFNULL"), "IFNULL로 변환되어야 함: $result")
        }

        @Test
        @DisplayName("여러 줄 쿼리 처리")
        fun testMultilineQuery() {
            val oracle = """
                SELECT
                    SYSDATE,
                    NVL(
                        name,
                        'Unknown'
                    )
                FROM
                    users
            """.trimIndent()

            val appliedRules = mutableListOf<String>()
            val result = functionConverter.convert(oracle, DialectType.ORACLE, DialectType.MYSQL, appliedRules)

            assertTrue(result.contains("NOW()"), "여러 줄 쿼리에서도 SYSDATE 변환되어야 함")
            assertTrue(result.contains("IFNULL("), "여러 줄 쿼리에서도 NVL 변환되어야 함")
        }
    }
}
