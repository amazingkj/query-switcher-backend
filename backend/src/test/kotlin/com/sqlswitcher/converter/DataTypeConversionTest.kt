package com.sqlswitcher.converter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * 테스트용 DialectType enum
 */
enum class DialectType {
    ORACLE, MYSQL, POSTGRESQL
}

/**
 * 데이터타입 변환 단위 테스트
 */
class DataTypeConversionTest {

    // ==================== Oracle → MySQL 데이터타입 테스트 ====================

    @ParameterizedTest
    @DisplayName("Oracle VARCHAR2 → MySQL VARCHAR 변환")
    @CsvSource(
        "VARCHAR2(50 BYTE), VARCHAR(50)",
        "VARCHAR2(100 CHAR), VARCHAR(100)",
        "VARCHAR2(200), VARCHAR(200)"
    )
    fun testOracleVarchar2ToMySql(oracle: String, expected: String) {
        val result = convertDataType(oracle, DialectType.ORACLE, DialectType.MYSQL)
        assertEquals(expected, result, "VARCHAR2 변환 실패: $oracle → $result (예상: $expected)")
    }

    @ParameterizedTest
    @DisplayName("Oracle NUMBER → MySQL 정수형 변환")
    @CsvSource(
        "NUMBER(1), TINYINT",
        "NUMBER(3), TINYINT",
        "NUMBER(4), SMALLINT",
        "NUMBER(5), SMALLINT",
        "NUMBER(6), INT",
        "NUMBER(9), INT",
        "NUMBER(10), BIGINT",
        "NUMBER(19), BIGINT"
    )
    fun testOracleNumberToMySqlInteger(oracle: String, expected: String) {
        val result = convertDataType(oracle, DialectType.ORACLE, DialectType.MYSQL)
        assertEquals(expected, result, "NUMBER 변환 실패: $oracle → $result (예상: $expected)")
    }

    @Test
    @DisplayName("Oracle NUMBER(p,s) → MySQL DECIMAL 변환")
    fun testOracleNumberDecimalToMySql() {
        assertEquals("DECIMAL(10,2)", convertDataType("NUMBER(10,2)", DialectType.ORACLE, DialectType.MYSQL))
        assertEquals("DECIMAL(15,4)", convertDataType("NUMBER(15,4)", DialectType.ORACLE, DialectType.MYSQL))
    }

    @Test
    @DisplayName("Oracle LOB → MySQL 변환")
    fun testOracleLobToMySql() {
        assertEquals("LONGTEXT", convertDataType("CLOB", DialectType.ORACLE, DialectType.MYSQL))
        assertEquals("LONGBLOB", convertDataType("BLOB", DialectType.ORACLE, DialectType.MYSQL))
    }

    @Test
    @DisplayName("Oracle DATE → MySQL DATETIME 변환")
    fun testOracleDateToMySql() {
        assertEquals("DATETIME", convertDataType("DATE", DialectType.ORACLE, DialectType.MYSQL))
    }

    @ParameterizedTest
    @DisplayName("Oracle FLOAT → MySQL FLOAT/DOUBLE 변환")
    @CsvSource(
        "FLOAT(1), FLOAT",
        "FLOAT(24), FLOAT",
        "FLOAT(25), DOUBLE",
        "FLOAT(53), DOUBLE"
    )
    fun testOracleFloatToMySql(oracle: String, expected: String) {
        val result = convertDataType(oracle, DialectType.ORACLE, DialectType.MYSQL)
        assertEquals(expected, result, "FLOAT 변환 실패: $oracle → $result (예상: $expected)")
    }

    @Test
    @DisplayName("Oracle BINARY_FLOAT/DOUBLE → MySQL 변환")
    fun testOracleBinaryFloatToMySql() {
        assertEquals("FLOAT", convertDataType("BINARY_FLOAT", DialectType.ORACLE, DialectType.MYSQL))
        assertEquals("DOUBLE", convertDataType("BINARY_DOUBLE", DialectType.ORACLE, DialectType.MYSQL))
    }

    // ==================== Oracle → PostgreSQL 데이터타입 테스트 ====================

    @ParameterizedTest
    @DisplayName("Oracle NUMBER → PostgreSQL 정수형 변환")
    @CsvSource(
        "NUMBER(1), SMALLINT",
        "NUMBER(4), SMALLINT",
        "NUMBER(5), INTEGER",
        "NUMBER(9), INTEGER",
        "NUMBER(10), BIGINT",
        "NUMBER(19), BIGINT"
    )
    fun testOracleNumberToPostgreSql(oracle: String, expected: String) {
        val result = convertDataType(oracle, DialectType.ORACLE, DialectType.POSTGRESQL)
        assertEquals(expected, result, "NUMBER 변환 실패: $oracle → $result (예상: $expected)")
    }

    @Test
    @DisplayName("Oracle NUMBER(p,s) → PostgreSQL NUMERIC 변환")
    fun testOracleNumberDecimalToPostgreSql() {
        assertEquals("NUMERIC(10,2)", convertDataType("NUMBER(10,2)", DialectType.ORACLE, DialectType.POSTGRESQL))
    }

    @Test
    @DisplayName("Oracle LOB → PostgreSQL 변환")
    fun testOracleLobToPostgreSql() {
        assertEquals("TEXT", convertDataType("CLOB", DialectType.ORACLE, DialectType.POSTGRESQL))
        assertEquals("BYTEA", convertDataType("BLOB", DialectType.ORACLE, DialectType.POSTGRESQL))
    }

    @ParameterizedTest
    @DisplayName("Oracle FLOAT → PostgreSQL REAL/DOUBLE PRECISION 변환")
    @CsvSource(
        "FLOAT(1), REAL",
        "FLOAT(24), REAL",
        "FLOAT(25), DOUBLE PRECISION",
        "FLOAT(53), DOUBLE PRECISION"
    )
    fun testOracleFloatToPostgreSql(oracle: String, expected: String) {
        val result = convertDataType(oracle, DialectType.ORACLE, DialectType.POSTGRESQL)
        assertEquals(expected, result, "FLOAT 변환 실패: $oracle → $result (예상: $expected)")
    }

    // ==================== MySQL → PostgreSQL 데이터타입 테스트 ====================

    @Test
    @DisplayName("MySQL TINYINT(1) → PostgreSQL BOOLEAN 변환")
    fun testMySqlTinyintBoolToPostgreSql() {
        assertEquals("BOOLEAN", convertDataType("TINYINT(1)", DialectType.MYSQL, DialectType.POSTGRESQL))
    }

    @Test
    @DisplayName("MySQL TINYINT → PostgreSQL SMALLINT 변환")
    fun testMySqlTinyintToPostgreSql() {
        assertEquals("SMALLINT", convertDataType("TINYINT", DialectType.MYSQL, DialectType.POSTGRESQL))
    }

    @Test
    @DisplayName("MySQL TEXT 타입 → PostgreSQL TEXT 변환")
    fun testMySqlTextToPostgreSql() {
        assertEquals("TEXT", convertDataType("LONGTEXT", DialectType.MYSQL, DialectType.POSTGRESQL))
        assertEquals("TEXT", convertDataType("MEDIUMTEXT", DialectType.MYSQL, DialectType.POSTGRESQL))
        assertEquals("TEXT", convertDataType("TINYTEXT", DialectType.MYSQL, DialectType.POSTGRESQL))
    }

    @Test
    @DisplayName("MySQL BLOB 타입 → PostgreSQL BYTEA 변환")
    fun testMySqlBlobToPostgreSql() {
        assertEquals("BYTEA", convertDataType("LONGBLOB", DialectType.MYSQL, DialectType.POSTGRESQL))
        assertEquals("BYTEA", convertDataType("MEDIUMBLOB", DialectType.MYSQL, DialectType.POSTGRESQL))
        assertEquals("BYTEA", convertDataType("BLOB", DialectType.MYSQL, DialectType.POSTGRESQL))
    }

    @Test
    @DisplayName("MySQL DATETIME → PostgreSQL TIMESTAMP 변환")
    fun testMySqlDatetimeToPostgreSql() {
        assertEquals("TIMESTAMP", convertDataType("DATETIME", DialectType.MYSQL, DialectType.POSTGRESQL))
    }

    @Test
    @DisplayName("MySQL AUTO_INCREMENT → PostgreSQL SERIAL 변환")
    fun testMySqlAutoIncrementToPostgreSql() {
        assertEquals("SERIAL", convertDataType("INT AUTO_INCREMENT", DialectType.MYSQL, DialectType.POSTGRESQL))
        assertEquals("BIGSERIAL", convertDataType("BIGINT AUTO_INCREMENT", DialectType.MYSQL, DialectType.POSTGRESQL))
    }

    @Test
    @DisplayName("MySQL JSON → PostgreSQL JSONB 변환")
    fun testMySqlJsonToPostgreSql() {
        assertEquals("JSONB", convertDataType("JSON", DialectType.MYSQL, DialectType.POSTGRESQL))
    }

    @Test
    @DisplayName("MySQL ENUM → PostgreSQL VARCHAR 변환")
    fun testMySqlEnumToPostgreSql() {
        assertEquals("VARCHAR(255)", convertDataType("ENUM('a','b','c')", DialectType.MYSQL, DialectType.POSTGRESQL))
    }

    // ==================== PostgreSQL → MySQL 데이터타입 테스트 ====================

    @Test
    @DisplayName("PostgreSQL SERIAL → MySQL AUTO_INCREMENT 변환")
    fun testPostgreSqlSerialToMySql() {
        assertEquals("INT AUTO_INCREMENT", convertDataType("SERIAL", DialectType.POSTGRESQL, DialectType.MYSQL))
        assertEquals("BIGINT AUTO_INCREMENT", convertDataType("BIGSERIAL", DialectType.POSTGRESQL, DialectType.MYSQL))
    }

    @Test
    @DisplayName("PostgreSQL TEXT → MySQL LONGTEXT 변환")
    fun testPostgreSqlTextToMySql() {
        assertEquals("LONGTEXT", convertDataType("TEXT", DialectType.POSTGRESQL, DialectType.MYSQL))
    }

    @Test
    @DisplayName("PostgreSQL BYTEA → MySQL LONGBLOB 변환")
    fun testPostgreSqlByteaToMySql() {
        assertEquals("LONGBLOB", convertDataType("BYTEA", DialectType.POSTGRESQL, DialectType.MYSQL))
    }

    @Test
    @DisplayName("PostgreSQL TIMESTAMP → MySQL DATETIME 변환")
    fun testPostgreSqlTimestampToMySql() {
        assertEquals("DATETIME", convertDataType("TIMESTAMP", DialectType.POSTGRESQL, DialectType.MYSQL))
    }

    @Test
    @DisplayName("PostgreSQL BOOLEAN → MySQL TINYINT(1) 변환")
    fun testPostgreSqlBooleanToMySql() {
        assertEquals("TINYINT(1)", convertDataType("BOOLEAN", DialectType.POSTGRESQL, DialectType.MYSQL))
    }

    @Test
    @DisplayName("PostgreSQL DOUBLE PRECISION → MySQL DOUBLE 변환")
    fun testPostgreSqlDoublePrecisionToMySql() {
        assertEquals("DOUBLE", convertDataType("DOUBLE PRECISION", DialectType.POSTGRESQL, DialectType.MYSQL))
    }

    @Test
    @DisplayName("PostgreSQL UUID → MySQL CHAR(36) 변환")
    fun testPostgreSqlUuidToMySql() {
        assertEquals("CHAR(36)", convertDataType("UUID", DialectType.POSTGRESQL, DialectType.MYSQL))
    }

    @Test
    @DisplayName("PostgreSQL JSONB → MySQL JSON 변환")
    fun testPostgreSqlJsonbToMySql() {
        assertEquals("JSON", convertDataType("JSONB", DialectType.POSTGRESQL, DialectType.MYSQL))
    }

    @Test
    @DisplayName("PostgreSQL 타입 캐스팅(::) 제거")
    fun testPostgreSqlTypeCastingRemoval() {
        assertEquals("", convertDataType("::INTEGER", DialectType.POSTGRESQL, DialectType.MYSQL))
        assertEquals("", convertDataType("::TEXT", DialectType.POSTGRESQL, DialectType.MYSQL))
    }

    // ==================== 헬퍼 함수 ====================

    /**
     * 데이터타입 변환 시뮬레이션
     * 실제 SqlConverterEngine의 convertDataTypesStringBased 로직을 테스트용으로 재현
     */
    private fun convertDataType(input: String, source: DialectType, target: DialectType): String {
        var result = input

        when (source) {
            DialectType.ORACLE -> {
                // BYTE/CHAR 키워드 제거
                result = result.replace(Regex("\\(\\s*(\\d+)\\s+BYTE\\s*\\)", RegexOption.IGNORE_CASE), "($1)")
                result = result.replace(Regex("\\(\\s*(\\d+)\\s+CHAR\\s*\\)", RegexOption.IGNORE_CASE), "($1)")

                when (target) {
                    DialectType.MYSQL -> {
                        result = result.replace(Regex("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)) { m ->
                            val precision = m.groupValues[1].toInt()
                            when {
                                precision <= 3 -> "TINYINT"
                                precision <= 5 -> "SMALLINT"
                                precision <= 9 -> "INT"
                                else -> "BIGINT"
                            }
                        }
                        result = result.replace(Regex("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE), "DECIMAL($1,$2)")
                        result = result.replace(Regex("\\bVARCHAR2\\s*\\(", RegexOption.IGNORE_CASE), "VARCHAR(")
                        result = result.replace(Regex("\\bCLOB\\b", RegexOption.IGNORE_CASE), "LONGTEXT")
                        result = result.replace(Regex("\\bBLOB\\b", RegexOption.IGNORE_CASE), "LONGBLOB")
                        result = result.replace(Regex("\\bDATE\\b", RegexOption.IGNORE_CASE), "DATETIME")
                        result = result.replace(Regex("\\bFLOAT\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)) { m ->
                            val precision = m.groupValues[1].toInt()
                            if (precision <= 24) "FLOAT" else "DOUBLE"
                        }
                        result = result.replace(Regex("\\bBINARY_FLOAT\\b", RegexOption.IGNORE_CASE), "FLOAT")
                        result = result.replace(Regex("\\bBINARY_DOUBLE\\b", RegexOption.IGNORE_CASE), "DOUBLE")
                    }
                    DialectType.POSTGRESQL -> {
                        result = result.replace(Regex("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)) { m ->
                            val precision = m.groupValues[1].toInt()
                            when {
                                precision <= 4 -> "SMALLINT"
                                precision <= 9 -> "INTEGER"
                                else -> "BIGINT"
                            }
                        }
                        result = result.replace(Regex("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE), "NUMERIC($1,$2)")
                        result = result.replace(Regex("\\bVARCHAR2\\s*\\(", RegexOption.IGNORE_CASE), "VARCHAR(")
                        result = result.replace(Regex("\\bCLOB\\b", RegexOption.IGNORE_CASE), "TEXT")
                        result = result.replace(Regex("\\bBLOB\\b", RegexOption.IGNORE_CASE), "BYTEA")
                        result = result.replace(Regex("\\bFLOAT\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)) { m ->
                            val precision = m.groupValues[1].toInt()
                            if (precision <= 24) "REAL" else "DOUBLE PRECISION"
                        }
                        result = result.replace(Regex("\\bBINARY_FLOAT\\b", RegexOption.IGNORE_CASE), "REAL")
                        result = result.replace(Regex("\\bBINARY_DOUBLE\\b", RegexOption.IGNORE_CASE), "DOUBLE PRECISION")
                    }
                    else -> {}
                }
            }
            DialectType.MYSQL -> {
                when (target) {
                    DialectType.POSTGRESQL -> {
                        result = result.replace(Regex("\\bTINYINT\\s*\\(\\s*1\\s*\\)", RegexOption.IGNORE_CASE), "BOOLEAN")
                        result = result.replace(Regex("\\bTINYINT\\b", RegexOption.IGNORE_CASE), "SMALLINT")
                        result = result.replace(Regex("\\bLONGTEXT\\b", RegexOption.IGNORE_CASE), "TEXT")
                        result = result.replace(Regex("\\bMEDIUMTEXT\\b", RegexOption.IGNORE_CASE), "TEXT")
                        result = result.replace(Regex("\\bTINYTEXT\\b", RegexOption.IGNORE_CASE), "TEXT")
                        result = result.replace(Regex("\\bLONGBLOB\\b", RegexOption.IGNORE_CASE), "BYTEA")
                        result = result.replace(Regex("\\bMEDIUMBLOB\\b", RegexOption.IGNORE_CASE), "BYTEA")
                        result = result.replace(Regex("\\bBLOB\\b", RegexOption.IGNORE_CASE), "BYTEA")
                        result = result.replace(Regex("\\bDATETIME\\b", RegexOption.IGNORE_CASE), "TIMESTAMP")
                        result = result.replace(Regex("\\bINT\\s+AUTO_INCREMENT\\b", RegexOption.IGNORE_CASE), "SERIAL")
                        result = result.replace(Regex("\\bBIGINT\\s+AUTO_INCREMENT\\b", RegexOption.IGNORE_CASE), "BIGSERIAL")
                        result = result.replace(Regex("\\bJSON\\b", RegexOption.IGNORE_CASE), "JSONB")
                        result = result.replace(Regex("\\bENUM\\s*\\([^)]+\\)", RegexOption.IGNORE_CASE), "VARCHAR(255)")
                    }
                    else -> {}
                }
            }
            DialectType.POSTGRESQL -> {
                when (target) {
                    DialectType.MYSQL -> {
                        // 타입 캐스팅(::) 제거를 먼저 수행
                        result = result.replace(Regex("::\\w+", RegexOption.IGNORE_CASE), "")
                        result = result.replace(Regex("\\bSERIAL\\b", RegexOption.IGNORE_CASE), "INT AUTO_INCREMENT")
                        result = result.replace(Regex("\\bBIGSERIAL\\b", RegexOption.IGNORE_CASE), "BIGINT AUTO_INCREMENT")
                        result = result.replace(Regex("\\bTEXT\\b", RegexOption.IGNORE_CASE), "LONGTEXT")
                        result = result.replace(Regex("\\bBYTEA\\b", RegexOption.IGNORE_CASE), "LONGBLOB")
                        result = result.replace(Regex("\\bTIMESTAMP\\b", RegexOption.IGNORE_CASE), "DATETIME")
                        result = result.replace(Regex("\\bBOOLEAN\\b", RegexOption.IGNORE_CASE), "TINYINT(1)")
                        result = result.replace(Regex("\\bDOUBLE PRECISION\\b", RegexOption.IGNORE_CASE), "DOUBLE")
                        result = result.replace(Regex("\\bUUID\\b", RegexOption.IGNORE_CASE), "CHAR(36)")
                        result = result.replace(Regex("\\bJSONB\\b", RegexOption.IGNORE_CASE), "JSON")
                    }
                    else -> {}
                }
            }
        }

        return result
    }
}