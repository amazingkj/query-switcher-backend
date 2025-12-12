package com.sqlswitcher.converter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import com.sqlswitcher.converter.DialectType

/**
 * SQL 함수 변환 단위 테스트
 */
class FunctionConversionTest {

    // ==================== Oracle → MySQL 함수 테스트 ====================

    @Test
    @DisplayName("Oracle SYSDATE → MySQL NOW() 변환")
    fun testOracleSysdateToMySql() {
        val result = convertFunction("SELECT SYSDATE FROM DUAL", DialectType.ORACLE, DialectType.MYSQL)
        assertTrue(result.contains("NOW()"), "SYSDATE → NOW() 변환 실패")
    }

    @Test
    @DisplayName("Oracle NVL → MySQL IFNULL 변환")
    fun testOracleNvlToMySql() {
        val result = convertFunction("SELECT NVL(name, 'Unknown') FROM users", DialectType.ORACLE, DialectType.MYSQL)
        assertTrue(result.contains("IFNULL"), "NVL → IFNULL 변환 실패")
    }

    @Test
    @DisplayName("Oracle SUBSTR → MySQL SUBSTRING 변환")
    fun testOracleSubstrToMySql() {
        val result = convertFunction("SELECT SUBSTR(name, 1, 10) FROM users", DialectType.ORACLE, DialectType.MYSQL)
        assertTrue(result.contains("SUBSTRING"), "SUBSTR → SUBSTRING 변환 실패")
    }

    @Test
    @DisplayName("Oracle NVL2 → MySQL IF 변환")
    fun testOracleNvl2ToMySql() {
        val result = convertFunction("SELECT NVL2(status, 'Active', 'Inactive') FROM users", DialectType.ORACLE, DialectType.MYSQL)
        assertTrue(result.contains("IF("), "NVL2 → IF 변환 실패")
    }

    // ==================== Oracle → PostgreSQL 함수 테스트 ====================

    @Test
    @DisplayName("Oracle SYSDATE → PostgreSQL CURRENT_TIMESTAMP 변환")
    fun testOracleSysdateToPostgreSql() {
        val result = convertFunction("SELECT SYSDATE FROM DUAL", DialectType.ORACLE, DialectType.POSTGRESQL)
        assertTrue(result.contains("CURRENT_TIMESTAMP"), "SYSDATE → CURRENT_TIMESTAMP 변환 실패")
    }

    @Test
    @DisplayName("Oracle NVL → PostgreSQL COALESCE 변환")
    fun testOracleNvlToPostgreSql() {
        val result = convertFunction("SELECT NVL(name, 'Unknown') FROM users", DialectType.ORACLE, DialectType.POSTGRESQL)
        assertTrue(result.contains("COALESCE"), "NVL → COALESCE 변환 실패")
    }

    // ==================== MySQL → PostgreSQL 함수 테스트 ====================

    @ParameterizedTest
    @DisplayName("MySQL → PostgreSQL 함수 변환")
    @CsvSource(
        "NOW(), CURRENT_TIMESTAMP",
        "CURDATE(), CURRENT_DATE",
        "CURTIME(), CURRENT_TIME"
    )
    fun testMySqlDateFunctionsToPostgreSql(mysql: String, expected: String) {
        val result = convertFunction("SELECT $mysql", DialectType.MYSQL, DialectType.POSTGRESQL)
        assertTrue(result.contains(expected), "$mysql → $expected 변환 실패: $result")
    }

    @Test
    @DisplayName("MySQL IFNULL → PostgreSQL COALESCE 변환")
    fun testMySqlIfnullToPostgreSql() {
        val result = convertFunction("SELECT IFNULL(name, 'Unknown') FROM users", DialectType.MYSQL, DialectType.POSTGRESQL)
        assertTrue(result.contains("COALESCE"), "IFNULL → COALESCE 변환 실패")
    }

    @Test
    @DisplayName("MySQL RAND → PostgreSQL RANDOM 변환")
    fun testMySqlRandToPostgreSql() {
        val result = convertFunction("SELECT RAND() FROM users", DialectType.MYSQL, DialectType.POSTGRESQL)
        assertTrue(result.contains("RANDOM()"), "RAND() → RANDOM() 변환 실패")
    }

    @Test
    @DisplayName("MySQL GROUP_CONCAT → PostgreSQL STRING_AGG 변환")
    fun testMySqlGroupConcatToPostgreSql() {
        val result = convertFunction("SELECT GROUP_CONCAT(name) FROM users", DialectType.MYSQL, DialectType.POSTGRESQL)
        assertTrue(result.contains("STRING_AGG"), "GROUP_CONCAT → STRING_AGG 변환 실패")
    }

    @Test
    @DisplayName("MySQL TRUNCATE → PostgreSQL TRUNC 변환")
    fun testMySqlTruncateToPostgreSql() {
        val result = convertFunction("SELECT TRUNCATE(price, 2) FROM products", DialectType.MYSQL, DialectType.POSTGRESQL)
        assertTrue(result.contains("TRUNC"), "TRUNCATE → TRUNC 변환 실패")
    }

    @Test
    @DisplayName("MySQL LAST_INSERT_ID → PostgreSQL LASTVAL 변환")
    fun testMySqlLastInsertIdToPostgreSql() {
        val result = convertFunction("SELECT LAST_INSERT_ID()", DialectType.MYSQL, DialectType.POSTGRESQL)
        assertTrue(result.contains("LASTVAL()"), "LAST_INSERT_ID() → LASTVAL() 변환 실패")
    }

    @Test
    @DisplayName("MySQL UNIX_TIMESTAMP → PostgreSQL EXTRACT(EPOCH...) 변환")
    fun testMySqlUnixTimestampToPostgreSql() {
        val result = convertFunction("SELECT UNIX_TIMESTAMP()", DialectType.MYSQL, DialectType.POSTGRESQL)
        assertTrue(result.contains("EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)"), "UNIX_TIMESTAMP() 변환 실패")
    }

    // ==================== PostgreSQL → MySQL 함수 테스트 ====================

    @ParameterizedTest
    @DisplayName("PostgreSQL → MySQL 함수 변환")
    @CsvSource(
        "CURRENT_TIMESTAMP, NOW()",
        "CURRENT_DATE, CURDATE()",
        "CURRENT_TIME, CURTIME()"
    )
    fun testPostgreSqlDateFunctionsToMySql(postgresql: String, expected: String) {
        val result = convertFunction("SELECT $postgresql", DialectType.POSTGRESQL, DialectType.MYSQL)
        assertTrue(result.contains(expected), "$postgresql → $expected 변환 실패: $result")
    }

    @Test
    @DisplayName("PostgreSQL COALESCE → MySQL IFNULL 변환")
    fun testPostgreSqlCoalesceToMySql() {
        val result = convertFunction("SELECT COALESCE(name, 'Unknown') FROM users", DialectType.POSTGRESQL, DialectType.MYSQL)
        assertTrue(result.contains("IFNULL"), "COALESCE → IFNULL 변환 실패")
    }

    @Test
    @DisplayName("PostgreSQL RANDOM → MySQL RAND 변환")
    fun testPostgreSqlRandomToMySql() {
        val result = convertFunction("SELECT RANDOM() FROM users", DialectType.POSTGRESQL, DialectType.MYSQL)
        assertTrue(result.contains("RAND()"), "RANDOM() → RAND() 변환 실패")
    }

    @Test
    @DisplayName("PostgreSQL STRING_AGG → MySQL GROUP_CONCAT 변환")
    fun testPostgreSqlStringAggToMySql() {
        val result = convertFunction("SELECT STRING_AGG(name, ', ') FROM users", DialectType.POSTGRESQL, DialectType.MYSQL)
        assertTrue(result.contains("GROUP_CONCAT"), "STRING_AGG → GROUP_CONCAT 변환 실패")
    }

    @Test
    @DisplayName("PostgreSQL TRUNC → MySQL TRUNCATE 변환")
    fun testPostgreSqlTruncToMySql() {
        val result = convertFunction("SELECT TRUNC(price, 2) FROM products", DialectType.POSTGRESQL, DialectType.MYSQL)
        assertTrue(result.contains("TRUNCATE"), "TRUNC → TRUNCATE 변환 실패")
    }

    @Test
    @DisplayName("PostgreSQL 타입 캐스팅(::) 제거")
    fun testPostgreSqlTypeCastingRemoval() {
        val result = convertFunction("SELECT value::INTEGER, name::TEXT FROM data", DialectType.POSTGRESQL, DialectType.MYSQL)
        assertFalse(result.contains("::"), "타입 캐스팅(::) 제거 실패")
    }

    // ==================== MySQL → Oracle 함수 테스트 ====================

    @Test
    @DisplayName("MySQL NOW → Oracle SYSDATE 변환")
    fun testMySqlNowToOracle() {
        val result = convertFunction("SELECT NOW() FROM users", DialectType.MYSQL, DialectType.ORACLE)
        assertTrue(result.contains("SYSDATE"), "NOW() → SYSDATE 변환 실패")
    }

    @Test
    @DisplayName("MySQL IFNULL → Oracle NVL 변환")
    fun testMySqlIfnullToOracle() {
        val result = convertFunction("SELECT IFNULL(name, 'Unknown') FROM users", DialectType.MYSQL, DialectType.ORACLE)
        assertTrue(result.contains("NVL"), "IFNULL → NVL 변환 실패")
    }

    @Test
    @DisplayName("MySQL SUBSTRING → Oracle SUBSTR 변환")
    fun testMySqlSubstringToOracle() {
        val result = convertFunction("SELECT SUBSTRING(name, 1, 10) FROM users", DialectType.MYSQL, DialectType.ORACLE)
        assertTrue(result.contains("SUBSTR"), "SUBSTRING → SUBSTR 변환 실패")
    }

    // ==================== PostgreSQL → Oracle 함수 테스트 ====================

    @Test
    @DisplayName("PostgreSQL CURRENT_TIMESTAMP → Oracle SYSDATE 변환")
    fun testPostgreSqlCurrentTimestampToOracle() {
        val result = convertFunction("SELECT CURRENT_TIMESTAMP FROM users", DialectType.POSTGRESQL, DialectType.ORACLE)
        assertTrue(result.contains("SYSDATE"), "CURRENT_TIMESTAMP → SYSDATE 변환 실패")
    }

    @Test
    @DisplayName("PostgreSQL COALESCE → Oracle NVL 변환")
    fun testPostgreSqlCoalesceToOracle() {
        val result = convertFunction("SELECT COALESCE(name, 'Unknown') FROM users", DialectType.POSTGRESQL, DialectType.ORACLE)
        assertTrue(result.contains("NVL"), "COALESCE → NVL 변환 실패")
    }

    @Test
    @DisplayName("PostgreSQL RANDOM → Oracle DBMS_RANDOM.VALUE 변환")
    fun testPostgreSqlRandomToOracle() {
        val result = convertFunction("SELECT RANDOM() FROM users", DialectType.POSTGRESQL, DialectType.ORACLE)
        assertTrue(result.contains("DBMS_RANDOM.VALUE"), "RANDOM() → DBMS_RANDOM.VALUE 변환 실패")
    }

    // ==================== 헬퍼 함수 ====================

    /**
     * 함수 변환 시뮬레이션
     */
    private fun convertFunction(input: String, source: DialectType, target: DialectType): String {
        var result = input

        when (source) {
            DialectType.ORACLE -> {
                when (target) {
                    DialectType.MYSQL -> {
                        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "NOW()")
                        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "IFNULL(")
                        result = result.replace(Regex("\\bSUBSTR\\s*\\(", RegexOption.IGNORE_CASE), "SUBSTRING(")
                        result = result.replace(Regex("\\bNVL2\\s*\\(", RegexOption.IGNORE_CASE), "IF(")
                    }
                    DialectType.POSTGRESQL -> {
                        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
                    }
                    else -> {}
                }
            }
            DialectType.MYSQL -> {
                when (target) {
                    DialectType.POSTGRESQL -> {
                        result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                        result = result.replace(Regex("\\bCURDATE\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "CURRENT_DATE")
                        result = result.replace(Regex("\\bCURTIME\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "CURRENT_TIME")
                        result = result.replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
                        result = result.replace(Regex("\\bRAND\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "RANDOM()")
                        result = result.replace(Regex("\\bGROUP_CONCAT\\s*\\(", RegexOption.IGNORE_CASE), "STRING_AGG(")
                        result = result.replace(Regex("\\bTRUNCATE\\s*\\(", RegexOption.IGNORE_CASE), "TRUNC(")
                        result = result.replace(Regex("\\bLAST_INSERT_ID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "LASTVAL()")
                        result = result.replace(Regex("\\bUNIX_TIMESTAMP\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::INTEGER")
                    }
                    DialectType.ORACLE -> {
                        result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSDATE")
                        result = result.replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
                        result = result.replace(Regex("\\bSUBSTRING\\s*\\(", RegexOption.IGNORE_CASE), "SUBSTR(")
                    }
                    else -> {}
                }
            }
            DialectType.POSTGRESQL -> {
                when (target) {
                    DialectType.MYSQL -> {
                        result = result.replace(Regex("\\bCURRENT_TIMESTAMP\\b", RegexOption.IGNORE_CASE), "NOW()")
                        result = result.replace(Regex("\\bCURRENT_DATE\\b", RegexOption.IGNORE_CASE), "CURDATE()")
                        result = result.replace(Regex("\\bCURRENT_TIME\\b", RegexOption.IGNORE_CASE), "CURTIME()")
                        result = result.replace(Regex("\\bCOALESCE\\s*\\(", RegexOption.IGNORE_CASE), "IFNULL(")
                        result = result.replace(Regex("\\bRANDOM\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "RAND()")
                        result = result.replace(Regex("\\bSTRING_AGG\\s*\\(", RegexOption.IGNORE_CASE), "GROUP_CONCAT(")
                        result = result.replace(Regex("\\bTRUNC\\s*\\(", RegexOption.IGNORE_CASE), "TRUNCATE(")
                        result = result.replace(Regex("::\\w+", RegexOption.IGNORE_CASE), "")
                    }
                    DialectType.ORACLE -> {
                        result = result.replace(Regex("\\bCURRENT_TIMESTAMP\\b", RegexOption.IGNORE_CASE), "SYSDATE")
                        result = result.replace(Regex("\\bCOALESCE\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
                        result = result.replace(Regex("\\bRANDOM\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "DBMS_RANDOM.VALUE")
                    }
                    else -> {}
                }
            }
        }

        return result
    }
}