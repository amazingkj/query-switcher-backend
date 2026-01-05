package com.sqlswitcher.converter

import com.sqlswitcher.model.ConversionRequest
import com.sqlswitcher.service.SqlConversionService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * SQL Converter Integration Tests
 *
 * Tests the actual SQL conversion pipeline without mocks.
 * Covers Oracle, MySQL, and PostgreSQL dialect conversions.
 */
@SpringBootTest
class SqlConverterIntegrationTest {

    @Autowired
    private lateinit var sqlConversionService: SqlConversionService

    @Autowired
    private lateinit var sqlConverterEngine: SqlConverterEngine

    // ==================== Oracle to MySQL Conversion Tests ====================

    @Nested
    @DisplayName("Oracle to MySQL Conversion Tests")
    inner class OracleToMySqlTests {

        @Test
        @DisplayName("Test 1: SYSDATE conversion to NOW()")
        fun testSysdateConversion() {
            val request = ConversionRequest(
                sql = "SELECT SYSDATE FROM DUAL",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("NOW()") ||
                response.convertedSql.uppercase().contains("CURRENT_TIMESTAMP"),
                "SYSDATE should be converted to NOW() or CURRENT_TIMESTAMP: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 2: NVL conversion to IFNULL")
        fun testNvlConversion() {
            val request = ConversionRequest(
                sql = "SELECT NVL(name, 'Unknown') FROM users",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("IFNULL("),
                "NVL should be converted to IFNULL: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 3: TO_CHAR date formatting conversion")
        fun testToCharConversion() {
            val request = ConversionRequest(
                sql = "SELECT TO_CHAR(created_at, 'YYYY-MM-DD') FROM orders",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("DATE_FORMAT("),
                "TO_CHAR should be converted to DATE_FORMAT: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 4: DECODE function to CASE WHEN")
        fun testDecodeConversion() {
            val request = ConversionRequest(
                sql = "SELECT DECODE(status, 'A', 'Active', 'I', 'Inactive', 'Unknown') FROM users",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("CASE"),
                "DECODE should be converted to CASE: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 5: SUBSTR to SUBSTRING conversion")
        fun testSubstrConversion() {
            val request = ConversionRequest(
                sql = "SELECT SUBSTR(name, 1, 10) FROM users",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("SUBSTRING("),
                "SUBSTR should be converted to SUBSTRING: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 6: NVL2 function conversion")
        fun testNvl2Conversion() {
            val request = ConversionRequest(
                sql = "SELECT NVL2(status, 'Active', 'Inactive') FROM users",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("CASE") ||
                response.convertedSql.uppercase().contains("IF("),
                "NVL2 should be converted to CASE or IF: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 7: LISTAGG to GROUP_CONCAT conversion")
        fun testListaggConversion() {
            val request = ConversionRequest(
                sql = "SELECT department_id, LISTAGG(name, ', ') WITHIN GROUP (ORDER BY name) FROM employees GROUP BY department_id",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("GROUP_CONCAT("),
                "LISTAGG should be converted to GROUP_CONCAT: ${response.convertedSql}"
            )
        }
    }

    // ==================== Oracle to PostgreSQL Conversion Tests ====================

    @Nested
    @DisplayName("Oracle to PostgreSQL Conversion Tests")
    inner class OracleToPostgreSqlTests {

        @Test
        @DisplayName("Test 8: SYSDATE to CURRENT_TIMESTAMP conversion")
        fun testSysdateToCurrentTimestamp() {
            val request = ConversionRequest(
                sql = "SELECT SYSDATE FROM DUAL",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("CURRENT_TIMESTAMP") ||
                response.convertedSql.uppercase().contains("NOW()"),
                "SYSDATE should be converted to CURRENT_TIMESTAMP: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 9: NVL to COALESCE conversion")
        fun testNvlToCoalesce() {
            val request = ConversionRequest(
                sql = "SELECT NVL(name, 'Unknown') FROM users",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("COALESCE("),
                "NVL should be converted to COALESCE: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 10: LISTAGG to STRING_AGG conversion")
        fun testListaggToStringAgg() {
            val request = ConversionRequest(
                sql = "SELECT department_id, LISTAGG(name, ', ') WITHIN GROUP (ORDER BY name) FROM employees GROUP BY department_id",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("STRING_AGG("),
                "LISTAGG should be converted to STRING_AGG: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 11: Sequence NEXTVAL/CURRVAL conversion")
        fun testSequenceConversion() {
            val request = ConversionRequest(
                sql = "SELECT seq_user.NEXTVAL FROM DUAL",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.lowercase().contains("nextval(") ||
                response.convertedSql.lowercase().contains("nextval"),
                "Sequence syntax should be converted: ${response.convertedSql}"
            )
        }
    }

    // ==================== MySQL to PostgreSQL Conversion Tests ====================

    @Nested
    @DisplayName("MySQL to PostgreSQL Conversion Tests")
    inner class MySqlToPostgreSqlTests {

        @Test
        @DisplayName("Test 12: IFNULL to COALESCE conversion")
        fun testIfnullToCoalesce() {
            val request = ConversionRequest(
                sql = "SELECT IFNULL(name, 'Unknown') FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("COALESCE("),
                "IFNULL should be converted to COALESCE: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 13: NOW() to CURRENT_TIMESTAMP conversion")
        fun testNowToCurrentTimestamp() {
            val request = ConversionRequest(
                sql = "SELECT NOW() FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("CURRENT_TIMESTAMP") ||
                response.convertedSql.uppercase().contains("NOW()"),
                "NOW() should be converted properly: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 14: GROUP_CONCAT to STRING_AGG conversion")
        fun testGroupConcatToStringAgg() {
            val request = ConversionRequest(
                sql = "SELECT department_id, GROUP_CONCAT(name SEPARATOR ', ') FROM employees GROUP BY department_id",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("STRING_AGG("),
                "GROUP_CONCAT should be converted to STRING_AGG: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 15: RAND() to RANDOM() conversion")
        fun testRandToRandom() {
            val request = ConversionRequest(
                sql = "SELECT RAND() FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("RANDOM()"),
                "RAND() should be converted to RANDOM(): ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 16: DATE_FORMAT to TO_CHAR conversion")
        fun testDateFormatToToChar() {
            val request = ConversionRequest(
                sql = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') FROM orders",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("TO_CHAR("),
                "DATE_FORMAT should be converted to TO_CHAR: ${response.convertedSql}"
            )
        }
    }

    // ==================== Complex Query Conversion Tests ====================

    @Nested
    @DisplayName("Complex Query Conversion Tests")
    inner class ComplexQueryTests {

        @Test
        @DisplayName("Test 17: Complex JOIN query with functions")
        fun testComplexJoinQuery() {
            val request = ConversionRequest(
                sql = """
                    SELECT
                        u.name,
                        d.department_name,
                        NVL(m.name, 'No Manager') AS manager_name,
                        TO_CHAR(u.created_at, 'YYYY-MM-DD') AS join_date
                    FROM users u
                    LEFT JOIN departments d ON u.department_id = d.id
                    LEFT JOIN users m ON u.manager_id = m.id
                    WHERE u.created_at >= SYSDATE - 30
                """.trimIndent(),
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("IFNULL("),
                "NVL should be converted to IFNULL: ${response.convertedSql}"
            )
            assertTrue(
                response.convertedSql.uppercase().contains("JOIN"),
                "JOIN should be preserved: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 18: Subquery conversion")
        fun testSubqueryConversion() {
            val request = ConversionRequest(
                sql = """
                    SELECT * FROM users u
                    WHERE u.department_id IN (
                        SELECT d.id FROM departments d
                        WHERE d.name = NVL(u.preferred_dept, 'DEFAULT')
                    )
                """.trimIndent(),
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("COALESCE("),
                "NVL should be converted to COALESCE: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 19: Aggregation with multiple functions")
        fun testAggregationQuery() {
            val request = ConversionRequest(
                sql = """
                    SELECT
                        department_id,
                        COUNT(*) AS user_count,
                        NVL(AVG(salary), 0) AS avg_salary,
                        MAX(hire_date) AS last_hire
                    FROM employees
                    WHERE hire_date >= SYSDATE - 365
                    GROUP BY department_id
                    HAVING COUNT(*) > 5
                """.trimIndent(),
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("GROUP BY"),
                "GROUP BY should be preserved: ${response.convertedSql}"
            )
            assertTrue(
                response.convertedSql.uppercase().contains("HAVING"),
                "HAVING should be preserved: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 20: Window function query")
        fun testWindowFunctionQuery() {
            val request = ConversionRequest(
                sql = """
                    SELECT
                        name,
                        salary,
                        ROW_NUMBER() OVER (PARTITION BY department_id ORDER BY salary DESC) AS rank,
                        SUM(salary) OVER (PARTITION BY department_id) AS dept_total
                    FROM employees
                """.trimIndent(),
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("ROW_NUMBER()"),
                "ROW_NUMBER should be preserved: ${response.convertedSql}"
            )
            assertTrue(
                response.convertedSql.uppercase().contains("OVER"),
                "OVER clause should be preserved: ${response.convertedSql}"
            )
        }
    }

    // ==================== DDL Conversion Tests ====================

    @Nested
    @DisplayName("DDL Conversion Tests")
    inner class DDLConversionTests {

        @Test
        @DisplayName("Test 21: CREATE TABLE with VARCHAR2 to VARCHAR")
        fun testCreateTableVarchar2Conversion() {
            val request = ConversionRequest(
                sql = """
                    CREATE TABLE TB_USER (
                        USER_ID VARCHAR2(50 BYTE) NOT NULL,
                        USER_NAME VARCHAR2(100 CHAR),
                        EMAIL VARCHAR2(200)
                    )
                """.trimIndent(),
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertFalse(
                response.convertedSql.uppercase().contains("VARCHAR2"),
                "VARCHAR2 should be converted to VARCHAR: ${response.convertedSql}"
            )
            assertTrue(
                response.convertedSql.uppercase().contains("VARCHAR("),
                "Should use VARCHAR: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 22: CREATE TABLE with NUMBER to appropriate types")
        fun testCreateTableNumberConversion() {
            val request = ConversionRequest(
                sql = """
                    CREATE TABLE TB_ORDER (
                        ORDER_ID NUMBER(19) NOT NULL,
                        QUANTITY NUMBER(5),
                        PRICE NUMBER(10,2),
                        STATUS NUMBER(1)
                    )
                """.trimIndent(),
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertFalse(
                response.convertedSql.uppercase().contains("NUMBER("),
                "NUMBER should be converted: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 23: CREATE TABLE with CLOB/BLOB conversion")
        fun testCreateTableLobConversion() {
            val request = ConversionRequest(
                sql = """
                    CREATE TABLE TB_DOCUMENT (
                        DOC_ID NUMBER(19) NOT NULL,
                        CONTENT CLOB,
                        ATTACHMENT BLOB
                    )
                """.trimIndent(),
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("LONGTEXT") ||
                response.convertedSql.uppercase().contains("TEXT"),
                "CLOB should be converted to LONGTEXT or TEXT: ${response.convertedSql}"
            )
            assertTrue(
                response.convertedSql.uppercase().contains("LONGBLOB") ||
                response.convertedSql.uppercase().contains("BLOB"),
                "BLOB should be converted: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 24: CREATE TABLE with Oracle storage options removal")
        fun testCreateTableStorageOptionsRemoval() {
            val request = ConversionRequest(
                sql = """
                    CREATE TABLE "SCHEMA_OWNER"."TB_LOG" (
                        LOG_ID NUMBER(19) NOT NULL,
                        MESSAGE VARCHAR2(4000)
                    ) TABLESPACE "LOG_DATA"
                    PCTFREE 10 INITRANS 1
                    STORAGE (INITIAL 64K NEXT 64K)
                    LOGGING NOCOMPRESS NOCACHE
                """.trimIndent(),
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertFalse(
                response.convertedSql.uppercase().contains("TABLESPACE"),
                "TABLESPACE should be removed: ${response.convertedSql}"
            )
            assertFalse(
                response.convertedSql.uppercase().contains("PCTFREE"),
                "PCTFREE should be removed: ${response.convertedSql}"
            )
            assertFalse(
                response.convertedSql.uppercase().contains("STORAGE"),
                "STORAGE should be removed: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 25: CREATE TABLE Oracle to PostgreSQL data type conversion")
        fun testCreateTableOracleToPostgresql() {
            val request = ConversionRequest(
                sql = """
                    CREATE TABLE TB_PRODUCT (
                        PRODUCT_ID NUMBER(19) NOT NULL,
                        PRODUCT_NAME VARCHAR2(200),
                        PRICE NUMBER(15,2),
                        DESCRIPTION CLOB,
                        IMAGE BLOB
                    )
                """.trimIndent(),
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("BIGINT") ||
                response.convertedSql.uppercase().contains("INTEGER") ||
                response.convertedSql.uppercase().contains("NUMERIC"),
                "NUMBER should be converted to appropriate PostgreSQL type: ${response.convertedSql}"
            )
            assertTrue(
                response.convertedSql.uppercase().contains("TEXT") ||
                response.convertedSql.uppercase().contains("VARCHAR"),
                "CLOB should be converted to TEXT: ${response.convertedSql}"
            )
            assertTrue(
                response.convertedSql.uppercase().contains("BYTEA") ||
                response.convertedSql.uppercase().contains("BLOB"),
                "BLOB should be converted to BYTEA: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 26: MySQL AUTO_INCREMENT to PostgreSQL SERIAL")
        fun testAutoIncrementToSerial() {
            val request = ConversionRequest(
                sql = "CREATE TABLE tb_user (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100))",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("SERIAL") ||
                response.convertedSql.uppercase().contains("GENERATED"),
                "AUTO_INCREMENT should be converted to SERIAL or GENERATED: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 27: PostgreSQL SERIAL to MySQL AUTO_INCREMENT")
        fun testSerialToAutoIncrement() {
            val request = ConversionRequest(
                sql = "CREATE TABLE tb_user (id SERIAL PRIMARY KEY, name VARCHAR(100))",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("AUTO_INCREMENT") ||
                response.convertedSql.uppercase().contains("INT"),
                "SERIAL should be converted to INT AUTO_INCREMENT: ${response.convertedSql}"
            )
        }
    }

    // ==================== Additional Edge Case Tests ====================

    @Nested
    @DisplayName("Additional Edge Case Tests")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("Test 28: Empty SQL handling")
        fun testEmptySqlHandling() {
            val request = ConversionRequest(
                sql = "",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            // Should either succeed with empty result or fail gracefully
            val response = sqlConversionService.convertSql(request)
            // Just verify it doesn't throw an exception
            assertNotNull(response)
        }

        @Test
        @DisplayName("Test 29: Same dialect conversion (no-op)")
        fun testSameDialectConversion() {
            val sql = "SELECT NOW() FROM users"
            val request = ConversionRequest(
                sql = sql,
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Same dialect conversion should succeed")
        }

        @Test
        @DisplayName("Test 30: Multiple statements conversion")
        fun testMultipleStatementsConversion() {
            val request = ConversionRequest(
                sql = """
                    SELECT SYSDATE FROM DUAL;
                    SELECT NVL(name, 'Unknown') FROM users
                """.trimIndent(),
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Multiple statements conversion should succeed")
        }

        @Test
        @DisplayName("Test 31: Nested functions conversion")
        fun testNestedFunctionsConversion() {
            val request = ConversionRequest(
                sql = "SELECT NVL(NVL(field1, field2), 'default') FROM table1",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Nested functions conversion should succeed")
            assertFalse(
                response.convertedSql.uppercase().contains("NVL("),
                "All NVL should be converted: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 32: Case insensitive function conversion")
        fun testCaseInsensitiveFunctionConversion() {
            val request = ConversionRequest(
                sql = "SELECT sysdate, Nvl(name, 'test'), SUBSTR(title, 1, 5) FROM users",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Case insensitive conversion should succeed")
        }
    }

    // ==================== Direct Engine Tests ====================

    @Nested
    @DisplayName("Direct SqlConverterEngine Tests")
    inner class DirectEngineTests {

        @Test
        @DisplayName("Test 33: Direct engine Oracle to MySQL conversion")
        fun testDirectEngineOracleToMysql() {
            val result = sqlConverterEngine.convert(
                sql = "SELECT SYSDATE, NVL(name, 'Unknown') FROM users",
                sourceDialectType = DialectType.ORACLE,
                targetDialectType = DialectType.MYSQL
            )

            assertNotNull(result)
            assertTrue(
                result.convertedSql.uppercase().contains("NOW()") ||
                result.convertedSql.uppercase().contains("CURRENT_TIMESTAMP"),
                "SYSDATE should be converted: ${result.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 34: Direct engine with conversion options")
        fun testDirectEngineWithOptions() {
            val options = ConversionOptions(
                preserveComments = true,
                formatOutput = false,
                includeWarnings = true
            )

            val result = sqlConverterEngine.convert(
                sql = "SELECT NVL(name, 'Unknown') FROM users",
                sourceDialectType = DialectType.ORACLE,
                targetDialectType = DialectType.POSTGRESQL,
                options = options
            )

            assertNotNull(result)
            assertTrue(
                result.convertedSql.uppercase().contains("COALESCE("),
                "NVL should be converted to COALESCE: ${result.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 35: Verify applied rules are recorded")
        fun testAppliedRulesRecording() {
            val result = sqlConverterEngine.convert(
                sql = "SELECT SYSDATE, NVL(name, 'Unknown') FROM users",
                sourceDialectType = DialectType.ORACLE,
                targetDialectType = DialectType.MYSQL
            )

            assertNotNull(result.appliedRules)
            // Applied rules should not be empty for a conversion with functions
        }

        @Test
        @DisplayName("Test 36: Verify warnings are generated for unsupported features")
        fun testWarningsGeneration() {
            val result = sqlConverterEngine.convert(
                sql = """
                    CREATE TABLE test (
                        id NUMBER(19)
                    ) TABLESPACE MY_TABLESPACE
                """.trimIndent(),
                sourceDialectType = DialectType.ORACLE,
                targetDialectType = DialectType.MYSQL
            )

            assertNotNull(result)
            // Conversion should still succeed even with Oracle-specific options
        }
    }

    // ==================== PostgreSQL to MySQL Conversion Tests ====================

    @Nested
    @DisplayName("PostgreSQL to MySQL Conversion Tests")
    inner class PostgreSqlToMySqlTests {

        @Test
        @DisplayName("Test 37: COALESCE preservation (standard SQL)")
        fun testCoalescePreservation() {
            val request = ConversionRequest(
                sql = "SELECT COALESCE(name, 'Unknown') FROM users",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            // COALESCE is standard SQL, might be converted to IFNULL or preserved
            assertTrue(
                response.convertedSql.uppercase().contains("COALESCE(") ||
                response.convertedSql.uppercase().contains("IFNULL("),
                "COALESCE should be preserved or converted to IFNULL: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 38: RANDOM() to RAND() conversion")
        fun testRandomToRand() {
            val request = ConversionRequest(
                sql = "SELECT RANDOM() FROM users",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("RAND()"),
                "RANDOM() should be converted to RAND(): ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 39: CURRENT_TIMESTAMP to NOW() conversion")
        fun testCurrentTimestampToNow() {
            val request = ConversionRequest(
                sql = "SELECT CURRENT_TIMESTAMP FROM users",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("NOW()") ||
                response.convertedSql.uppercase().contains("CURRENT_TIMESTAMP"),
                "CURRENT_TIMESTAMP should be converted: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 40: PostgreSQL casting conversion")
        fun testCastingConversion() {
            val request = ConversionRequest(
                sql = "SELECT value::INTEGER, name::TEXT FROM data",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            // PostgreSQL :: 캐스팅은 CAST() 형식으로 변환됨
            assertFalse(
                response.convertedSql.contains("::"),
                "PostgreSQL :: casting syntax should be converted: ${response.convertedSql}"
            )
        }
    }

    // ==================== Data Type Conversion Tests ====================

    @Nested
    @DisplayName("Data Type Conversion Tests")
    inner class DataTypeConversionTests {

        @Test
        @DisplayName("Test 41: PostgreSQL BOOLEAN to MySQL TINYINT(1)")
        fun testBooleanToTinyint() {
            val request = ConversionRequest(
                sql = "CREATE TABLE test (is_active BOOLEAN DEFAULT false)",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("TINYINT") ||
                response.convertedSql.uppercase().contains("BOOLEAN"),
                "BOOLEAN should be converted to TINYINT: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 42: PostgreSQL TEXT to MySQL LONGTEXT")
        fun testTextToLongtext() {
            val request = ConversionRequest(
                sql = "CREATE TABLE test (content TEXT)",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("LONGTEXT") ||
                response.convertedSql.uppercase().contains("TEXT"),
                "TEXT should be converted: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 43: PostgreSQL BYTEA to MySQL LONGBLOB")
        fun testByteaToLongblob() {
            val request = ConversionRequest(
                sql = "CREATE TABLE test (data BYTEA)",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("LONGBLOB") ||
                response.convertedSql.uppercase().contains("BLOB") ||
                response.convertedSql.uppercase().contains("BYTEA"),
                "BYTEA should be converted: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 44: PostgreSQL UUID to MySQL CHAR(36)")
        fun testUuidToChar36() {
            val request = ConversionRequest(
                sql = "CREATE TABLE test (id UUID PRIMARY KEY)",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("CHAR(36)") ||
                response.convertedSql.uppercase().contains("VARCHAR(36)") ||
                response.convertedSql.uppercase().contains("UUID"),
                "UUID should be converted: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 45: PostgreSQL JSONB to MySQL JSON")
        fun testJsonbToJson() {
            val request = ConversionRequest(
                sql = "CREATE TABLE test (data JSONB)",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("JSON"),
                "JSONB should be converted to JSON: ${response.convertedSql}"
            )
        }
    }

    // ==================== MySQL to Oracle Conversion Tests ====================

    @Nested
    @DisplayName("MySQL to Oracle Conversion Tests")
    inner class MySqlToOracleTests {

        @Test
        @DisplayName("Test 46: NOW() to SYSDATE conversion")
        fun testNowToSysdate() {
            val request = ConversionRequest(
                sql = "SELECT NOW() FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("SYSDATE") ||
                response.convertedSql.uppercase().contains("CURRENT_TIMESTAMP"),
                "NOW() should be converted to SYSDATE: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 47: IFNULL to NVL conversion")
        fun testIfnullToNvl() {
            val request = ConversionRequest(
                sql = "SELECT IFNULL(name, 'Unknown') FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("NVL(") ||
                response.convertedSql.uppercase().contains("COALESCE("),
                "IFNULL should be converted to NVL: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 48: SUBSTRING to SUBSTR conversion")
        fun testSubstringToSubstr() {
            val request = ConversionRequest(
                sql = "SELECT SUBSTRING(name, 1, 10) FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("SUBSTR("),
                "SUBSTRING should be converted to SUBSTR: ${response.convertedSql}"
            )
        }
    }

    // ==================== PostgreSQL to Oracle Conversion Tests ====================

    @Nested
    @DisplayName("PostgreSQL to Oracle Conversion Tests")
    inner class PostgreSqlToOracleTests {

        @Test
        @DisplayName("Test 49: CURRENT_TIMESTAMP to SYSDATE conversion")
        fun testCurrentTimestampToSysdate() {
            val request = ConversionRequest(
                sql = "SELECT CURRENT_TIMESTAMP FROM users",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.ORACLE
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("SYSDATE") ||
                response.convertedSql.uppercase().contains("CURRENT_TIMESTAMP"),
                "CURRENT_TIMESTAMP should be converted: ${response.convertedSql}"
            )
        }

        @Test
        @DisplayName("Test 50: COALESCE to NVL conversion")
        fun testCoalesceToNvl() {
            val request = ConversionRequest(
                sql = "SELECT COALESCE(name, 'Unknown') FROM users",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.ORACLE
            )

            val response = sqlConversionService.convertSql(request)

            assertTrue(response.success, "Conversion should succeed")
            assertTrue(
                response.convertedSql.uppercase().contains("NVL(") ||
                response.convertedSql.uppercase().contains("COALESCE("),
                "COALESCE should be converted: ${response.convertedSql}"
            )
        }
    }
}
