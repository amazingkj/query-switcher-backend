package com.sqlswitcher.converter.feature.ddl

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertContains

/**
 * TablespacePartitionConverter 단위 테스트
 */
class TablespacePartitionConverterTest {

    @Nested
    @DisplayName("테이블스페이스 변환 테스트")
    inner class TablespaceConversionTest {

        @Test
        @DisplayName("MySQL: TABLESPACE 절 제거")
        fun testTablespaceRemovedForMySql() {
            val sql = """
                CREATE TABLE employees (
                    id NUMBER PRIMARY KEY,
                    name VARCHAR2(100)
                ) TABLESPACE users_ts
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertFalse(result.contains("TABLESPACE"), "TABLESPACE가 제거되어야 함")
            assertTrue(result.contains("employees"), "테이블명은 유지되어야 함")
            assertTrue(appliedRules.any { it.contains("TABLESPACE") }, "적용된 규칙에 포함되어야 함")
        }

        @Test
        @DisplayName("PostgreSQL: TABLESPACE 절 유지")
        fun testTablespaceKeptForPostgreSql() {
            val sql = """
                CREATE TABLE employees (
                    id NUMBER PRIMARY KEY,
                    name VARCHAR2(100)
                ) TABLESPACE users_ts
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("TABLESPACE"), "TABLESPACE가 유지되어야 함")
            assertTrue(warnings.any { it.message.contains("TABLESPACE") }, "경고가 있어야 함")
        }

        @Test
        @DisplayName("STORAGE 절 제거")
        fun testStorageClauseRemoved() {
            val sql = """
                CREATE TABLE employees (
                    id NUMBER PRIMARY KEY
                ) STORAGE (INITIAL 64K NEXT 64K MINEXTENTS 1 MAXEXTENTS UNLIMITED)
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertFalse(result.contains("STORAGE"), "STORAGE가 제거되어야 함")
            assertFalse(result.contains("INITIAL"), "INITIAL이 제거되어야 함")
            assertTrue(appliedRules.any { it.contains("STORAGE") }, "적용된 규칙에 포함되어야 함")
        }

        @Test
        @DisplayName("물리적 속성 제거 (PCTFREE, INITRANS 등)")
        fun testPhysicalAttributesRemoved() {
            val sql = """
                CREATE TABLE employees (
                    id NUMBER PRIMARY KEY
                ) PCTFREE 10 PCTUSED 40 INITRANS 1 MAXTRANS 255
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertFalse(result.contains("PCTFREE"), "PCTFREE가 제거되어야 함")
            assertFalse(result.contains("INITRANS"), "INITRANS가 제거되어야 함")
            assertTrue(appliedRules.any { it.contains("물리적 속성") }, "적용된 규칙에 포함되어야 함")
        }

        @Test
        @DisplayName("LOGGING/NOLOGGING 처리")
        fun testLoggingClauseHandled() {
            val sql = """
                CREATE TABLE temp_data (
                    id NUMBER PRIMARY KEY
                ) NOLOGGING
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertFalse(result.contains("NOLOGGING"), "NOLOGGING이 제거되어야 함")
            assertTrue(warnings.any { it.suggestion?.contains("UNLOGGED") == true },
                "UNLOGGED 테이블 권장 경고가 있어야 함")
        }

        @Test
        @DisplayName("압축 설정 처리")
        fun testCompressClauseHandled() {
            val sql = """
                CREATE TABLE archive_data (
                    id NUMBER PRIMARY KEY
                ) COMPRESS
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertFalse(result.contains("COMPRESS"), "COMPRESS가 제거되어야 함")
            assertTrue(warnings.any { it.suggestion?.contains("ROW_FORMAT") == true },
                "MySQL 압축 권장 경고가 있어야 함")
        }
    }

    @Nested
    @DisplayName("RANGE 파티션 변환 테스트")
    inner class RangePartitionTest {

        @Test
        @DisplayName("기본 RANGE 파티션 MySQL 변환")
        fun testBasicRangePartitionToMySql() {
            val sql = """
                CREATE TABLE sales (
                    sale_id NUMBER,
                    sale_date DATE
                )
                PARTITION BY RANGE (sale_date) (
                    PARTITION p2023 VALUES LESS THAN (TO_DATE('2024-01-01', 'YYYY-MM-DD')),
                    PARTITION p2024 VALUES LESS THAN (MAXVALUE)
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("PARTITION BY RANGE"), "PARTITION BY RANGE가 유지되어야 함")
            assertTrue(appliedRules.any { it.contains("RANGE 파티션") }, "적용된 규칙에 포함되어야 함")
        }

        @Test
        @DisplayName("RANGE 파티션 PostgreSQL 변환")
        fun testRangePartitionToPostgreSql() {
            val sql = """
                CREATE TABLE sales (
                    sale_id NUMBER,
                    sale_date DATE
                )
                PARTITION BY RANGE (sale_date) (
                    PARTITION p2023 VALUES LESS THAN ('2024-01-01'),
                    PARTITION p2024 VALUES LESS THAN (MAXVALUE)
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("PARTITION BY RANGE"), "PARTITION BY RANGE가 유지되어야 함")
            assertTrue(warnings.any { it.message.contains("PostgreSQL 10") },
                "PostgreSQL 버전 관련 경고가 있어야 함")
        }

        @Test
        @DisplayName("INTERVAL 파티션 처리")
        fun testIntervalPartitionWarning() {
            val sql = """
                CREATE TABLE sales (
                    sale_id NUMBER,
                    sale_date DATE
                )
                PARTITION BY RANGE (sale_date)
                INTERVAL (NUMTOYMINTERVAL(1, 'MONTH')) (
                    PARTITION p_init VALUES LESS THAN ('2024-01-01')
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(warnings.any { it.message.contains("INTERVAL") }, "INTERVAL 경고가 있어야 함")
            assertTrue(appliedRules.any { it.contains("INTERVAL") }, "적용된 규칙에 포함되어야 함")
        }
    }

    @Nested
    @DisplayName("LIST 파티션 변환 테스트")
    inner class ListPartitionTest {

        @Test
        @DisplayName("기본 LIST 파티션 MySQL 변환")
        fun testBasicListPartitionToMySql() {
            val sql = """
                CREATE TABLE employees (
                    emp_id NUMBER,
                    region VARCHAR2(20)
                )
                PARTITION BY LIST (region) (
                    PARTITION p_east VALUES IN ('EAST', 'NORTHEAST'),
                    PARTITION p_west VALUES IN ('WEST', 'SOUTHWEST'),
                    PARTITION p_other VALUES IN (DEFAULT)
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("PARTITION BY LIST"), "PARTITION BY LIST가 유지되어야 함")
            assertTrue(appliedRules.any { it.contains("LIST 파티션") }, "적용된 규칙에 포함되어야 함")
        }

        @Test
        @DisplayName("LIST 파티션 PostgreSQL 변환")
        fun testListPartitionToPostgreSql() {
            val sql = """
                CREATE TABLE employees (
                    emp_id NUMBER,
                    department VARCHAR2(50)
                )
                PARTITION BY LIST (department) (
                    PARTITION p_sales VALUES IN ('SALES'),
                    PARTITION p_hr VALUES IN ('HR', 'ADMIN')
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("PARTITION BY LIST"), "PARTITION BY LIST가 유지되어야 함")
            assertTrue(warnings.any { it.suggestion?.contains("CREATE TABLE") == true },
                "별도 테이블 생성 필요 안내가 있어야 함")
        }
    }

    @Nested
    @DisplayName("HASH 파티션 변환 테스트")
    inner class HashPartitionTest {

        @Test
        @DisplayName("기본 HASH 파티션 MySQL 변환")
        fun testBasicHashPartitionToMySql() {
            val sql = """
                CREATE TABLE logs (
                    log_id NUMBER,
                    user_id NUMBER
                )
                PARTITION BY HASH (user_id)
                PARTITIONS 4
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(result.contains("PARTITION BY HASH"), "PARTITION BY HASH가 유지되어야 함")
            assertTrue(result.contains("PARTITIONS") || result.contains("partitions"),
                "PARTITIONS 절이 있어야 함")
        }

        @Test
        @DisplayName("HASH 파티션 PostgreSQL 변환")
        fun testHashPartitionToPostgreSql() {
            val sql = """
                CREATE TABLE logs (
                    log_id NUMBER,
                    user_id NUMBER
                )
                PARTITION BY HASH (user_id)
                PARTITIONS 4
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertTrue(result.contains("PARTITION BY HASH"), "PARTITION BY HASH가 유지되어야 함")
        }
    }

    @Nested
    @DisplayName("서브파티션 테스트")
    inner class SubpartitionTest {

        @Test
        @DisplayName("서브파티션 경고")
        fun testSubpartitionWarning() {
            val sql = """
                CREATE TABLE sales (
                    sale_id NUMBER,
                    sale_date DATE,
                    region VARCHAR2(20)
                )
                PARTITION BY RANGE (sale_date)
                SUBPARTITION BY LIST (region)
                SUBPARTITION TEMPLATE (
                    SUBPARTITION east VALUES ('EAST'),
                    SUBPARTITION west VALUES ('WEST')
                ) (
                    PARTITION p2023 VALUES LESS THAN ('2024-01-01'),
                    PARTITION p2024 VALUES LESS THAN (MAXVALUE)
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertTrue(warnings.any { it.message.contains("서브파티션") },
                "서브파티션 경고가 있어야 함")
        }
    }

    @Nested
    @DisplayName("유틸리티 메서드 테스트")
    inner class UtilityMethodsTest {

        @Test
        @DisplayName("테이블스페이스 추출")
        fun testExtractTablespaces() {
            val sql = """
                CREATE TABLE t1 (id NUMBER) TABLESPACE ts1;
                CREATE TABLE t2 (id NUMBER) TABLESPACE ts2;
                CREATE INDEX idx1 ON t1(id) TABLESPACE 'ts_index';
            """.trimIndent()

            val tablespaces = TablespacePartitionConverter.extractTablespaces(sql)

            assertTrue(tablespaces.contains("ts1"), "ts1이 추출되어야 함")
            assertTrue(tablespaces.contains("ts2"), "ts2이 추출되어야 함")
        }

        @Test
        @DisplayName("파티션 정보 추출")
        fun testExtractPartitionInfo() {
            val sql = """
                CREATE TABLE sales (
                    id NUMBER, region VARCHAR2(20)
                )
                PARTITION BY LIST (region) (
                    PARTITION p_east VALUES IN ('EAST'),
                    PARTITION p_west VALUES IN ('WEST')
                )
            """.trimIndent()

            val partitions = TablespacePartitionConverter.extractPartitionInfo(sql)

            assertTrue(partitions.size >= 2, "2개 이상의 파티션이 추출되어야 함")
            assertTrue(partitions.any { it.name == "p_east" }, "p_east 파티션이 있어야 함")
            assertTrue(partitions.any { it.name == "p_west" }, "p_west 파티션이 있어야 함")
        }

        @Test
        @DisplayName("PostgreSQL 파티션 DDL 생성")
        fun testGeneratePostgreSqlPartitionDdl() {
            val partitions = listOf(
                TablespacePartitionConverter.PartitionInfo(
                    name = "sales_east",
                    type = TablespacePartitionConverter.PartitionValueType.LIST,
                    values = listOf("'EAST'", "'NORTHEAST'")
                ),
                TablespacePartitionConverter.PartitionInfo(
                    name = "sales_west",
                    type = TablespacePartitionConverter.PartitionValueType.LIST,
                    values = listOf("'WEST'")
                )
            )

            val ddls = TablespacePartitionConverter.generatePostgreSqlPartitionDdl("sales", partitions)

            assertEquals(2, ddls.size, "2개의 DDL이 생성되어야 함")
            assertTrue(ddls[0].contains("PARTITION OF sales"), "PARTITION OF 구문이 있어야 함")
            assertTrue(ddls[0].contains("FOR VALUES IN"), "FOR VALUES IN 구문이 있어야 함")
        }

        @Test
        @DisplayName("테이블스페이스 매핑 생성 - MySQL")
        fun testCreateTablespaceMappingMySql() {
            val oracleTablespaces = listOf("USERS", "DATA_TS", "INDEX_TS")

            val mapping = TablespacePartitionConverter.createTablespaceMapping(
                oracleTablespaces, DialectType.MYSQL
            )

            assertEquals(3, mapping.size, "3개의 매핑이 생성되어야 함")
            assertTrue(mapping.values.all { it == "innodb_file_per_table" },
                "모두 innodb_file_per_table로 매핑되어야 함")
        }

        @Test
        @DisplayName("테이블스페이스 매핑 생성 - PostgreSQL")
        fun testCreateTablespaceMappingPostgreSql() {
            val oracleTablespaces = listOf("USERS", "DATA_TS")

            val mapping = TablespacePartitionConverter.createTablespaceMapping(
                oracleTablespaces, DialectType.POSTGRESQL
            )

            assertEquals(2, mapping.size, "2개의 매핑이 생성되어야 함")
            assertEquals("users", mapping["USERS"], "소문자로 변환되어야 함")
            assertEquals("data_ts", mapping["DATA_TS"], "소문자로 변환되어야 함")
        }
    }

    @Nested
    @DisplayName("복합 DDL 변환 테스트")
    inner class ComplexDdlTest {

        @Test
        @DisplayName("모든 Oracle 절이 포함된 DDL 변환")
        fun testComplexDdlConversion() {
            val sql = """
                CREATE TABLE employees (
                    emp_id NUMBER PRIMARY KEY,
                    name VARCHAR2(100),
                    hire_date DATE
                )
                TABLESPACE users_ts
                STORAGE (INITIAL 64K NEXT 64K)
                PCTFREE 10
                INITRANS 2
                LOGGING
                COMPRESS
                PARTITION BY RANGE (hire_date) (
                    PARTITION p2023 VALUES LESS THAN ('2024-01-01') TABLESPACE data_2023,
                    PARTITION p2024 VALUES LESS THAN (MAXVALUE) TABLESPACE data_2024
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            // 제거되어야 할 항목들
            assertFalse(result.contains("STORAGE"), "STORAGE가 제거되어야 함")
            assertFalse(result.contains("PCTFREE"), "PCTFREE가 제거되어야 함")
            assertFalse(result.contains("INITRANS"), "INITRANS가 제거되어야 함")
            assertFalse(result.contains("LOGGING"), "LOGGING이 제거되어야 함")
            assertFalse(result.contains("COMPRESS"), "COMPRESS가 제거되어야 함")

            // 유지되어야 할 항목들
            assertTrue(result.contains("employees"), "테이블명이 유지되어야 함")
            assertTrue(result.contains("PARTITION BY RANGE"), "파티션이 유지되어야 함")

            // 규칙 적용 확인
            assertTrue(appliedRules.size >= 3, "여러 규칙이 적용되어야 함")
        }

        @Test
        @DisplayName("인덱스 DDL 테이블스페이스 제거")
        fun testIndexTablespaceRemoval() {
            val sql = """
                CREATE INDEX idx_emp_name ON employees(name)
                TABLESPACE index_ts
                STORAGE (INITIAL 16K)
                PCTFREE 5
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertFalse(result.contains("TABLESPACE"), "TABLESPACE가 제거되어야 함")
            assertFalse(result.contains("STORAGE"), "STORAGE가 제거되어야 함")
            assertTrue(result.contains("CREATE INDEX"), "CREATE INDEX가 유지되어야 함")
        }
    }

    @Nested
    @DisplayName("엣지 케이스 테스트")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("Oracle이 아닌 소스는 변환하지 않음")
        fun testNonOracleSourceUnchanged() {
            val sql = "CREATE TABLE t1 (id INT) TABLESPACE ts1"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.MYSQL, DialectType.POSTGRESQL, warnings, appliedRules
            )

            assertEquals(sql, result, "Oracle이 아니면 변환하지 않아야 함")
        }

        @Test
        @DisplayName("테이블스페이스/파티션이 없는 DDL")
        fun testNoTablespaceOrPartition() {
            val sql = "CREATE TABLE t1 (id NUMBER PRIMARY KEY)"
            val warnings = mutableListOf<ConversionWarning>()
            val appliedRules = mutableListOf<String>()

            val result = TablespacePartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, appliedRules
            )

            assertEquals(sql, result, "변환할 내용이 없으면 원본 유지")
            assertTrue(appliedRules.isEmpty(), "적용된 규칙이 없어야 함")
        }

        @Test
        @DisplayName("따옴표로 감싼 테이블스페이스명 처리")
        fun testQuotedTablespaceName() {
            val sql = """CREATE TABLE t1 (id NUMBER) TABLESPACE "User_TS" """

            val tablespaces = TablespacePartitionConverter.extractTablespaces(sql)

            assertTrue(tablespaces.isNotEmpty(), "테이블스페이스가 추출되어야 함")
        }
    }
}
