package com.sqlswitcher.converter.feature.dblink

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * DatabaseLinkConverter 단위 테스트
 */
class DatabaseLinkConverterTest {

    private fun convert(sql: String, source: DialectType, target: DialectType): Triple<String, List<ConversionWarning>, List<String>> {
        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()
        val result = DatabaseLinkConverter.convert(sql, source, target, warnings, rules)
        return Triple(result, warnings, rules)
    }

    @Nested
    @DisplayName("CREATE DATABASE LINK 변환 테스트")
    inner class CreateDbLinkTest {

        @Test
        @DisplayName("Oracle → PostgreSQL FDW 변환")
        fun testOracleToPostgreSql() {
            val sql = """
                CREATE DATABASE LINK remote_db
                CONNECT TO scott IDENTIFIED BY tiger
                USING 'remote_host:1521/orcl'
            """.trimIndent()

            val (result, warnings, rules) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("CREATE EXTENSION IF NOT EXISTS postgres_fdw"))
            assertTrue(result.contains("CREATE SERVER"))
            assertTrue(result.contains("remote_db_server"))
            assertTrue(result.contains("CREATE USER MAPPING"))
            assertTrue(result.contains("remote_host"))
            assertTrue(warnings.any { it.message.contains("Foreign Data Wrapper") })
            assertTrue(rules.isNotEmpty())
        }

        @Test
        @DisplayName("Oracle → MySQL FEDERATED 변환")
        fun testOracleToMySql() {
            val sql = """
                CREATE DATABASE LINK remote_db
                CONNECT TO scott IDENTIFIED BY tiger
                USING 'remote_host:1521/orcl'
            """.trimIndent()

            val (result, warnings, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("ENGINE=FEDERATED"))
            assertTrue(result.contains("CONNECTION="))
            assertTrue(warnings.any { it.message.contains("FEDERATED") || it.message.contains("지원하지 않습니다") })
        }

        @Test
        @DisplayName("PUBLIC DATABASE LINK 변환")
        fun testPublicDbLink() {
            val sql = """
                CREATE PUBLIC DATABASE LINK shared_db
                CONNECT TO admin IDENTIFIED BY password
                USING 'server:1521/prod'
            """.trimIndent()

            val (result, _, rules) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("shared_db_server"))
            assertTrue(rules.any { it.contains("shared_db") })
        }

        @Test
        @DisplayName("TNS 형식 연결 문자열 파싱")
        fun testTnsFormatParsing() {
            val sql = """
                CREATE DATABASE LINK remote_db
                CONNECT TO user1 IDENTIFIED BY pass1
                USING '(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=dbserver.example.com)(PORT=1522))(CONNECT_DATA=(SERVICE_NAME=proddb)))'
            """.trimIndent()

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("dbserver.example.com"))
            assertTrue(result.contains("1522"))
            assertTrue(result.contains("proddb"))
        }
    }

    @Nested
    @DisplayName("DROP DATABASE LINK 변환 테스트")
    inner class DropDbLinkTest {

        @Test
        @DisplayName("Oracle → PostgreSQL DROP")
        fun testDropToPostgreSql() {
            val sql = "DROP DATABASE LINK remote_db;"

            val (result, _, rules) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("DROP SERVER"))
            assertTrue(result.contains("DROP USER MAPPING"))
            assertTrue(result.contains("CASCADE"))
            assertTrue(rules.isNotEmpty())
        }

        @Test
        @DisplayName("Oracle → MySQL DROP (주석)")
        fun testDropToMySql() {
            val sql = "DROP DATABASE LINK remote_db;"

            val (result, warnings, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("--"))
            assertTrue(warnings.any { it.message.contains("지원하지 않습니다") })
        }

        @Test
        @DisplayName("DROP PUBLIC DATABASE LINK")
        fun testDropPublicDbLink() {
            val sql = "DROP PUBLIC DATABASE LINK shared_db;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("DROP SERVER"))
        }
    }

    @Nested
    @DisplayName("@dblink 참조 변환 테스트")
    inner class DbLinkReferenceTest {

        @Test
        @DisplayName("단순 테이블@dblink 참조 변환")
        fun testSimpleReference() {
            val sql = "SELECT * FROM employees@remote_db WHERE department_id = 10;"

            val (result, warnings, rules) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("remote_db_employees"))
            assertFalse(result.contains("@remote_db"))
            assertTrue(warnings.any { it.message.contains("로컬 테이블 참조") })
            assertTrue(rules.any { it.contains("@dblink") })
        }

        @Test
        @DisplayName("스키마.테이블@dblink 참조 변환")
        fun testSchemaTableReference() {
            val sql = "SELECT * FROM hr.employees@remote_db;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("remote_db_hr_employees"))
            assertFalse(result.contains("@remote_db"))
        }

        @Test
        @DisplayName("여러 @dblink 참조 변환")
        fun testMultipleReferences() {
            val sql = """
                SELECT e.*, d.department_name
                FROM employees@hr_db e
                JOIN departments@hr_db d ON e.department_id = d.department_id
                WHERE e.salary > (SELECT AVG(salary) FROM employees@finance_db);
            """.trimIndent()

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("hr_db_employees"))
            assertTrue(result.contains("hr_db_departments"))
            assertTrue(result.contains("finance_db_employees"))
            assertFalse(result.contains("@"))
        }

        @Test
        @DisplayName("Oracle → Oracle (변환 없음)")
        fun testOracleToOracle() {
            val sql = "SELECT * FROM employees@remote_db;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.ORACLE)

            assertTrue(result.contains("@remote_db"))
        }
    }

    @Nested
    @DisplayName("유틸리티 메서드 테스트")
    inner class UtilityMethodsTest {

        @Test
        @DisplayName("Database Link 문 감지")
        fun testIsDatabaseLinkStatement() {
            assertTrue(DatabaseLinkConverter.isDatabaseLinkStatement("CREATE DATABASE LINK remote_db CONNECT TO ..."))
            assertTrue(DatabaseLinkConverter.isDatabaseLinkStatement("DROP DATABASE LINK remote_db;"))
            assertFalse(DatabaseLinkConverter.isDatabaseLinkStatement("SELECT * FROM employees"))
            assertFalse(DatabaseLinkConverter.isDatabaseLinkStatement("SELECT * FROM employees@remote_db"))
        }

        @Test
        @DisplayName("@dblink 참조 감지")
        fun testHasDbLinkReference() {
            assertTrue(DatabaseLinkConverter.hasDbLinkReference("SELECT * FROM employees@remote_db"))
            assertTrue(DatabaseLinkConverter.hasDbLinkReference("INSERT INTO local SELECT * FROM hr.employees@prod_db"))
            assertFalse(DatabaseLinkConverter.hasDbLinkReference("SELECT * FROM employees"))
            assertFalse(DatabaseLinkConverter.hasDbLinkReference("SELECT email FROM users"))
        }

        @Test
        @DisplayName("참조된 Database Link 추출")
        fun testGetReferencedDbLinks() {
            val sql = """
                SELECT * FROM employees@hr_db
                UNION
                SELECT * FROM contractors@hr_db
                UNION
                SELECT * FROM temps@payroll_db;
            """.trimIndent()

            val dblinks = DatabaseLinkConverter.getReferencedDbLinks(sql)

            assertEquals(2, dblinks.size)
            assertTrue(dblinks.contains("hr_db"))
            assertTrue(dblinks.contains("payroll_db"))
        }
    }

    @Nested
    @DisplayName("DbLinkInfo 테스트")
    inner class DbLinkInfoTest {

        @Test
        @DisplayName("기본값 확인")
        fun testDefaultValues() {
            val info = DatabaseLinkConverter.DbLinkInfo(
                linkName = "test_link",
                username = "user",
                password = "pass",
                connectString = "host/db"
            )

            assertFalse(info.isPublic)
            assertEquals("test_link", info.linkName)
        }
    }
}
