package com.sqlswitcher.converter.feature.synonym

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
 * SynonymConverter 단위 테스트
 */
class SynonymConverterTest {

    private fun convert(sql: String, targetDialect: DialectType): Triple<String, List<ConversionWarning>, List<String>> {
        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()
        val result = SynonymConverter.convert(
            sql,
            DialectType.ORACLE,
            targetDialect,
            warnings,
            rules
        )
        return Triple(result, warnings, rules)
    }

    @Nested
    @DisplayName("CREATE SYNONYM 변환 테스트")
    inner class CreateSynonymTest {

        @Test
        @DisplayName("단순 SYNONYM → MySQL VIEW")
        fun testSimpleSynonymToMySql() {
            val sql = "CREATE SYNONYM emp FOR hr.employees;"

            val (result, warnings, rules) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("CREATE"), "CREATE 문이 있어야 함")
            assertTrue(result.contains("VIEW"), "VIEW로 변환되어야 함")
            assertTrue(result.contains("emp"), "Synonym 이름이 있어야 함")
            assertTrue(result.contains("hr.employees"), "대상 객체가 있어야 함")
            assertTrue(rules.any { it.contains("SYNONYM") && it.contains("VIEW") })
        }

        @Test
        @DisplayName("단순 SYNONYM → PostgreSQL VIEW")
        fun testSimpleSynonymToPostgreSql() {
            val sql = "CREATE SYNONYM emp FOR hr.employees;"

            val (result, warnings, rules) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("CREATE"))
            assertTrue(result.contains("VIEW"))
            assertTrue(result.contains("emp"))
            assertTrue(result.contains("hr.employees"))
        }

        @Test
        @DisplayName("PUBLIC SYNONYM → MySQL VIEW")
        fun testPublicSynonymToMySql() {
            val sql = "CREATE PUBLIC SYNONYM emp_pub FOR hr.employees;"

            val (result, warnings, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("VIEW"))
            assertTrue(result.contains("emp_pub"))
            assertTrue(warnings.any { it.message.contains("PUBLIC") })
        }

        @Test
        @DisplayName("PUBLIC SYNONYM → PostgreSQL public schema VIEW")
        fun testPublicSynonymToPostgreSql() {
            val sql = "CREATE PUBLIC SYNONYM emp_pub FOR hr.employees;"

            val (result, warnings, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("public.emp_pub") || result.contains("emp_pub"))
            assertTrue(result.contains("VIEW"))
            assertTrue(warnings.any { it.message.contains("PUBLIC") })
        }

        @Test
        @DisplayName("OR REPLACE SYNONYM")
        fun testOrReplaceSynonym() {
            val sql = "CREATE OR REPLACE SYNONYM dept FOR hr.departments;"

            val (result, _, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("CREATE OR REPLACE VIEW") ||
                       result.contains("CREATE VIEW") ||
                       result.contains("OR REPLACE"))
            assertTrue(result.contains("dept"))
        }

        @Test
        @DisplayName("스키마가 있는 SYNONYM")
        fun testSynonymWithSchema() {
            val sql = "CREATE SYNONYM myschema.emp FOR hr.employees;"

            val (resultMy, _, _) = convert(sql, DialectType.MYSQL)
            val (resultPg, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(resultMy.contains("myschema.emp") || resultMy.contains("emp"))
            assertTrue(resultPg.contains("myschema.emp") || resultPg.contains("emp"))
        }

        @Test
        @DisplayName("대상 스키마 없는 SYNONYM")
        fun testSynonymWithoutTargetSchema() {
            val sql = "CREATE SYNONYM emp_alias FOR employees;"

            val (result, _, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("VIEW"))
            assertTrue(result.contains("emp_alias"))
            assertTrue(result.contains("employees"))
        }
    }

    @Nested
    @DisplayName("DB Link SYNONYM 변환 테스트")
    inner class DbLinkSynonymTest {

        @Test
        @DisplayName("DB Link SYNONYM → MySQL 경고")
        fun testDbLinkSynonymToMySql() {
            val sql = "CREATE SYNONYM remote_emp FOR hr.employees@remote_db;"

            val (result, warnings, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("Cannot convert") || result.contains("DB Link"))
            assertTrue(result.contains("remote_db"))
            assertTrue(warnings.any {
                it.severity == WarningSeverity.ERROR &&
                it.message.contains("DB Link")
            })
        }

        @Test
        @DisplayName("DB Link SYNONYM → PostgreSQL FDW 안내")
        fun testDbLinkSynonymToPostgreSql() {
            val sql = "CREATE SYNONYM remote_emp FOR hr.employees@remote_db;"

            val (result, warnings, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("postgres_fdw") || result.contains("Cannot convert"))
            assertTrue(warnings.any { it.severity == WarningSeverity.ERROR })
        }
    }

    @Nested
    @DisplayName("DROP SYNONYM 변환 테스트")
    inner class DropSynonymTest {

        @Test
        @DisplayName("DROP SYNONYM → MySQL DROP VIEW")
        fun testDropSynonymToMySql() {
            val sql = "DROP SYNONYM emp;"

            val (result, _, rules) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("DROP VIEW"))
            assertTrue(result.contains("emp"))
            assertTrue(rules.any { it.contains("DROP") })
        }

        @Test
        @DisplayName("DROP SYNONYM → PostgreSQL DROP VIEW CASCADE")
        fun testDropSynonymToPostgreSql() {
            val sql = "DROP SYNONYM emp;"

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("DROP VIEW"))
            assertTrue(result.contains("CASCADE") || result.contains("emp"))
        }

        @Test
        @DisplayName("DROP PUBLIC SYNONYM")
        fun testDropPublicSynonym() {
            val sql = "DROP PUBLIC SYNONYM emp_pub;"

            val (result, _, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("DROP VIEW"))
            assertTrue(result.contains("emp_pub"))
        }

        @Test
        @DisplayName("DROP SYNONYM FORCE")
        fun testDropSynonymForce() {
            val sql = "DROP SYNONYM emp FORCE;"

            val (result, _, _) = convert(sql, DialectType.MYSQL)

            assertTrue(result.contains("DROP VIEW"))
        }

        @Test
        @DisplayName("스키마가 있는 DROP SYNONYM")
        fun testDropSynonymWithSchema() {
            val sql = "DROP SYNONYM myschema.emp;"

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL)

            assertTrue(result.contains("myschema.emp") || result.contains("emp"))
        }
    }

    @Nested
    @DisplayName("유틸리티 메서드 테스트")
    inner class UtilityMethodsTest {

        @Test
        @DisplayName("Synonym 구문 감지")
        fun testHasSynonymStatements() {
            assertTrue(SynonymConverter.hasSynonymStatements(
                "CREATE SYNONYM emp FOR hr.employees;"
            ))
            assertTrue(SynonymConverter.hasSynonymStatements(
                "DROP SYNONYM emp;"
            ))
            assertTrue(SynonymConverter.hasSynonymStatements(
                "CREATE PUBLIC SYNONYM emp FOR hr.employees;"
            ))
            assertFalse(SynonymConverter.hasSynonymStatements(
                "SELECT * FROM employees"
            ))
            assertFalse(SynonymConverter.hasSynonymStatements(
                "CREATE TABLE emp (id NUMBER)"
            ))
        }

        @Test
        @DisplayName("정의된 Synonym 목록 추출")
        fun testGetDefinedSynonyms() {
            val sql = """
                CREATE SYNONYM emp FOR hr.employees;
                CREATE PUBLIC SYNONYM dept FOR hr.departments;
                CREATE SYNONYM remote_t FOR data.table@remote_db;
            """.trimIndent()

            val synonyms = SynonymConverter.getDefinedSynonyms(sql)

            assertEquals(3, synonyms.size)

            // 첫 번째 Synonym 검증
            val emp = synonyms.find { it.name == "emp" }
            assertTrue(emp != null)
            assertEquals("employees", emp!!.targetObject)
            assertEquals("hr", emp.targetSchema)
            assertFalse(emp.isPublic)
            assertFalse(emp.isRemote)

            // PUBLIC Synonym 검증
            val dept = synonyms.find { it.name == "dept" }
            assertTrue(dept != null)
            assertTrue(dept!!.isPublic)

            // 원격 Synonym 검증
            val remote = synonyms.find { it.name == "remote_t" }
            assertTrue(remote != null)
            assertTrue(remote!!.isRemote)
            assertEquals("remote_db", remote.dbLink)
        }

        @Test
        @DisplayName("Synonym 참조 대체")
        fun testReplaceSynonymReferences() {
            val sql = "SELECT * FROM emp WHERE emp.id = 1"
            val synonymMap = mapOf("emp" to "hr.employees")
            val warnings = mutableListOf<ConversionWarning>()

            val result = SynonymConverter.replaceSynonymReferences(sql, synonymMap, warnings)

            assertTrue(result.contains("hr.employees"))
            assertFalse(result.contains("FROM emp"))
            assertTrue(warnings.isNotEmpty())
        }

        @Test
        @DisplayName("스키마 search_path 생성")
        fun testGenerateSchemaSearchPath() {
            val synonyms = listOf(
                SynonymConverter.SynonymInfo(
                    name = "emp",
                    schema = null,
                    targetObject = "employees",
                    targetSchema = "hr",
                    dbLink = null,
                    isPublic = false
                ),
                SynonymConverter.SynonymInfo(
                    name = "dept",
                    schema = null,
                    targetObject = "departments",
                    targetSchema = "admin",
                    dbLink = null,
                    isPublic = false
                )
            )

            val searchPath = SynonymConverter.generateSchemaSearchPath(synonyms)

            assertTrue(searchPath.contains("search_path"))
            assertTrue(searchPath.contains("hr"))
            assertTrue(searchPath.contains("admin"))
            assertTrue(searchPath.contains("public"))
        }
    }

    @Nested
    @DisplayName("SynonymInfo 테스트")
    inner class SynonymInfoTest {

        @Test
        @DisplayName("fullName 속성")
        fun testFullName() {
            val withSchema = SynonymConverter.SynonymInfo(
                name = "emp",
                schema = "myschema",
                targetObject = "employees",
                targetSchema = null,
                dbLink = null,
                isPublic = false
            )
            assertEquals("myschema.emp", withSchema.fullName)

            val withoutSchema = SynonymConverter.SynonymInfo(
                name = "emp",
                schema = null,
                targetObject = "employees",
                targetSchema = null,
                dbLink = null,
                isPublic = false
            )
            assertEquals("emp", withoutSchema.fullName)
        }

        @Test
        @DisplayName("fullTargetName 속성")
        fun testFullTargetName() {
            val simple = SynonymConverter.SynonymInfo(
                name = "emp",
                schema = null,
                targetObject = "employees",
                targetSchema = null,
                dbLink = null,
                isPublic = false
            )
            assertEquals("employees", simple.fullTargetName)

            val withSchema = SynonymConverter.SynonymInfo(
                name = "emp",
                schema = null,
                targetObject = "employees",
                targetSchema = "hr",
                dbLink = null,
                isPublic = false
            )
            assertEquals("hr.employees", withSchema.fullTargetName)

            val withDbLink = SynonymConverter.SynonymInfo(
                name = "emp",
                schema = null,
                targetObject = "employees",
                targetSchema = "hr",
                dbLink = "remote",
                isPublic = false
            )
            assertEquals("hr.employees@remote", withDbLink.fullTargetName)
        }

        @Test
        @DisplayName("isRemote 속성")
        fun testIsRemote() {
            val local = SynonymConverter.SynonymInfo(
                name = "emp",
                schema = null,
                targetObject = "employees",
                targetSchema = null,
                dbLink = null,
                isPublic = false
            )
            assertFalse(local.isRemote)

            val remote = SynonymConverter.SynonymInfo(
                name = "emp",
                schema = null,
                targetObject = "employees",
                targetSchema = null,
                dbLink = "remote_db",
                isPublic = false
            )
            assertTrue(remote.isRemote)
        }
    }

    @Nested
    @DisplayName("DDL 생성 테스트")
    inner class DdlGenerationTest {

        @Test
        @DisplayName("Oracle Synonym DDL 생성")
        fun testGenerateOracleSynonymDdl() {
            val info = SynonymConverter.SynonymInfo(
                name = "emp",
                schema = null,
                targetObject = "employees",
                targetSchema = "hr",
                dbLink = null,
                isPublic = false
            )

            val ddl = SynonymConverter.generateOracleSynonymDdl(info)

            assertTrue(ddl.contains("CREATE SYNONYM emp FOR hr.employees;"))
        }

        @Test
        @DisplayName("PUBLIC Synonym Oracle DDL 생성")
        fun testGeneratePublicOracleSynonymDdl() {
            val info = SynonymConverter.SynonymInfo(
                name = "emp",
                schema = null,
                targetObject = "employees",
                targetSchema = "hr",
                dbLink = null,
                isPublic = true
            )

            val ddl = SynonymConverter.generateOracleSynonymDdl(info)

            assertTrue(ddl.contains("PUBLIC SYNONYM"))
        }

        @Test
        @DisplayName("MySQL 대체 DDL 생성")
        fun testGenerateMySqlAlternative() {
            val info = SynonymConverter.SynonymInfo(
                name = "emp",
                schema = null,
                targetObject = "employees",
                targetSchema = "hr",
                dbLink = null,
                isPublic = false
            )

            val ddl = SynonymConverter.generateMySqlAlternative(info)

            assertTrue(ddl.contains("CREATE"))
            assertTrue(ddl.contains("VIEW"))
            assertTrue(ddl.contains("emp"))
            assertTrue(ddl.contains("hr.employees"))
        }

        @Test
        @DisplayName("원격 Synonym MySQL 대체 DDL")
        fun testGenerateMySqlRemoteAlternative() {
            val info = SynonymConverter.SynonymInfo(
                name = "remote_emp",
                schema = null,
                targetObject = "employees",
                targetSchema = "hr",
                dbLink = "remote_db",
                isPublic = false
            )

            val ddl = SynonymConverter.generateMySqlAlternative(info)

            assertTrue(ddl.contains("FEDERATED") || ddl.contains("Remote"))
            assertTrue(ddl.contains("remote_db"))
        }

        @Test
        @DisplayName("PostgreSQL 대체 DDL 생성")
        fun testGeneratePostgreSqlAlternative() {
            val info = SynonymConverter.SynonymInfo(
                name = "emp",
                schema = null,
                targetObject = "employees",
                targetSchema = "hr",
                dbLink = null,
                isPublic = false
            )

            val ddl = SynonymConverter.generatePostgreSqlAlternative(info)

            assertTrue(ddl.contains("CREATE"))
            assertTrue(ddl.contains("VIEW"))
            assertTrue(ddl.contains("emp"))
        }

        @Test
        @DisplayName("PUBLIC Synonym PostgreSQL 대체 DDL")
        fun testGeneratePostgreSqlPublicAlternative() {
            val info = SynonymConverter.SynonymInfo(
                name = "emp",
                schema = null,
                targetObject = "employees",
                targetSchema = "hr",
                dbLink = null,
                isPublic = true
            )

            val ddl = SynonymConverter.generatePostgreSqlAlternative(info)

            assertTrue(ddl.contains("public."))
            assertTrue(ddl.contains("GRANT") || ddl.contains("VIEW"))
        }

        @Test
        @DisplayName("원격 Synonym PostgreSQL 대체 DDL")
        fun testGeneratePostgreSqlRemoteAlternative() {
            val info = SynonymConverter.SynonymInfo(
                name = "remote_emp",
                schema = null,
                targetObject = "employees",
                targetSchema = "hr",
                dbLink = "remote_db",
                isPublic = false
            )

            val ddl = SynonymConverter.generatePostgreSqlAlternative(info)

            assertTrue(ddl.contains("postgres_fdw"))
            assertTrue(ddl.contains("CREATE SERVER") || ddl.contains("FOREIGN"))
        }
    }

    @Nested
    @DisplayName("소스 방언 검증 테스트")
    inner class SourceDialectTest {

        @Test
        @DisplayName("Oracle 외 소스는 변환하지 않음")
        fun testNonOracleSourceNotConverted() {
            val sql = "CREATE SYNONYM emp FOR employees;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = SynonymConverter.convert(
                sql,
                DialectType.MYSQL,
                DialectType.POSTGRESQL,
                warnings,
                rules
            )

            assertEquals(sql, result, "변환되지 않아야 함")
            assertTrue(rules.isEmpty())
        }
    }

    @Nested
    @DisplayName("복합 시나리오 테스트")
    inner class ComplexScenarioTest {

        @Test
        @DisplayName("여러 Synonym DDL 포함 스크립트")
        fun testMultipleSynonymDdl() {
            val sql = """
                CREATE SYNONYM emp FOR hr.employees;
                CREATE PUBLIC SYNONYM dept FOR hr.departments;
                CREATE SYNONYM loc FOR hr.locations;
                DROP SYNONYM old_syn;
            """.trimIndent()

            val (result, warnings, rules) = convert(sql, DialectType.MYSQL)

            // 여러 VIEW 생성 및 DROP 확인
            assertTrue(result.contains("VIEW"))
            assertTrue(result.contains("emp"))
            assertTrue(result.contains("dept"))
            assertTrue(result.contains("loc"))
            assertTrue(result.contains("DROP"))
            assertTrue(rules.size >= 4)
        }

        @Test
        @DisplayName("혼합된 DDL과 Synonym")
        fun testMixedDdlWithSynonym() {
            val sql = """
                CREATE TABLE test (id NUMBER);
                CREATE SYNONYM test_syn FOR test;
                INSERT INTO test VALUES (1);
            """.trimIndent()

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL)

            // Synonym만 변환되고 나머지는 유지
            assertTrue(result.contains("CREATE TABLE"))
            assertTrue(result.contains("VIEW"))
            assertTrue(result.contains("INSERT"))
        }
    }
}
