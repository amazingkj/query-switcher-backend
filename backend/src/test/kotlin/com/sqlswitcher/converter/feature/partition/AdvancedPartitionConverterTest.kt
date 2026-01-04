package com.sqlswitcher.converter.feature.partition

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * AdvancedPartitionConverter 테스트
 */
class AdvancedPartitionConverterTest {

    @Nested
    @DisplayName("파티션 감지 테스트")
    inner class DetectionTest {

        @Test
        @DisplayName("RANGE 파티션 테이블 감지")
        fun testDetectRangePartition() {
            val sql = """
                CREATE TABLE sales (
                    id NUMBER,
                    sale_date DATE
                )
                PARTITION BY RANGE (sale_date)
                (
                    PARTITION p2023 VALUES LESS THAN (TO_DATE('2024-01-01', 'YYYY-MM-DD')),
                    PARTITION p2024 VALUES LESS THAN (MAXVALUE)
                )
            """.trimIndent()

            assertTrue(AdvancedPartitionConverter.isPartitionedTable(sql))
        }

        @Test
        @DisplayName("LIST 파티션 테이블 감지")
        fun testDetectListPartition() {
            val sql = """
                CREATE TABLE regions (
                    id INT,
                    region VARCHAR(50)
                )
                PARTITION BY LIST (region)
                (
                    PARTITION p_east VALUES ('EAST', 'NORTHEAST'),
                    PARTITION p_west VALUES ('WEST', 'NORTHWEST')
                )
            """.trimIndent()

            assertTrue(AdvancedPartitionConverter.isPartitionedTable(sql))
        }

        @Test
        @DisplayName("HASH 파티션 테이블 감지")
        fun testDetectHashPartition() {
            val sql = """
                CREATE TABLE users (
                    id NUMBER,
                    name VARCHAR2(100)
                )
                PARTITION BY HASH (id)
                PARTITIONS 4
            """.trimIndent()

            assertTrue(AdvancedPartitionConverter.isPartitionedTable(sql))
        }

        @Test
        @DisplayName("일반 테이블 감지 안 함")
        fun testNonPartitionedTable() {
            val sql = "CREATE TABLE simple (id INT, name VARCHAR(100))"

            assertFalse(AdvancedPartitionConverter.isPartitionedTable(sql))
        }
    }

    @Nested
    @DisplayName("Oracle → MySQL 변환 테스트")
    inner class OracleToMySqlTest {

        @Test
        @DisplayName("RANGE 파티션 변환")
        fun testRangePartitionConversion() {
            val sql = """
                CREATE TABLE orders (
                    order_id NUMBER,
                    order_date DATE
                )
                PARTITION BY RANGE (order_date)
                (
                    PARTITION p2023 VALUES LESS THAN (TO_DATE('2024-01-01', 'YYYY-MM-DD')),
                    PARTITION p2024 VALUES LESS THAN (MAXVALUE)
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("PARTITION BY RANGE"))
            assertTrue(result.contains("STR_TO_DATE"))
            assertTrue(result.contains("MAXVALUE"))
            // TO_DATE가 STR_TO_DATE로 변환되었는지 확인 (독립적인 TO_DATE( 호출이 없어야 함)
            assertFalse(Regex("""(?<!STR_)TO_DATE\s*\(""").containsMatchIn(result))
        }

        @Test
        @DisplayName("INTERVAL 파티션 경고")
        fun testIntervalPartitionWarning() {
            val sql = """
                CREATE TABLE logs (
                    log_id NUMBER,
                    log_date DATE
                )
                PARTITION BY RANGE (log_date)
                INTERVAL (NUMTOYMINTERVAL(1, 'MONTH'))
                (
                    PARTITION p_init VALUES LESS THAN (TO_DATE('2024-01-01', 'YYYY-MM-DD'))
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(warnings.any { it.message.contains("INTERVAL") })
            assertTrue(rules.any { it.contains("INTERVAL") })
            assertFalse(result.contains("INTERVAL"))
        }

        @Test
        @DisplayName("LIST 파티션 변환")
        fun testListPartitionConversion() {
            val sql = """
                CREATE TABLE products (
                    id NUMBER,
                    category VARCHAR2(50)
                )
                PARTITION BY LIST (category)
                (
                    PARTITION p_electronics VALUES ('PHONE', 'LAPTOP', 'TABLET'),
                    PARTITION p_clothing VALUES ('SHIRT', 'PANTS', 'SHOES')
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("PARTITION BY LIST"))
            assertTrue(result.contains("p_electronics"))
            assertTrue(result.contains("p_clothing"))
        }

        @Test
        @DisplayName("HASH 파티션 변환")
        fun testHashPartitionConversion() {
            val sql = """
                CREATE TABLE customers (
                    customer_id NUMBER,
                    name VARCHAR2(100)
                )
                PARTITION BY HASH (customer_id)
                PARTITIONS 8
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("PARTITION BY HASH"))
            assertTrue(result.contains("PARTITIONS 8") || result.contains("PARTITIONS"))
        }

        @Test
        @DisplayName("TABLESPACE 제거")
        fun testTablespaceRemoval() {
            val sql = """
                CREATE TABLE data (
                    id NUMBER
                )
                PARTITION BY RANGE (id)
                (
                    PARTITION p1 VALUES LESS THAN (1000) TABLESPACE ts_data1,
                    PARTITION p2 VALUES LESS THAN (MAXVALUE) TABLESPACE ts_data2
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertFalse(result.contains("TABLESPACE"))
            assertFalse(result.contains("ts_data1"))
            assertFalse(result.contains("ts_data2"))
        }
    }

    @Nested
    @DisplayName("Oracle → PostgreSQL 변환 테스트")
    inner class OracleToPostgreSqlTest {

        @Test
        @DisplayName("RANGE 파티션 → PostgreSQL 선언적 파티션")
        fun testRangeToDeclarative() {
            val sql = """
                CREATE TABLE metrics (
                    id NUMBER,
                    metric_date DATE,
                    value NUMBER
                )
                PARTITION BY RANGE (metric_date)
                (
                    PARTITION p_q1 VALUES LESS THAN (TO_DATE('2024-04-01', 'YYYY-MM-DD')),
                    PARTITION p_q2 VALUES LESS THAN (TO_DATE('2024-07-01', 'YYYY-MM-DD')),
                    PARTITION p_max VALUES LESS THAN (MAXVALUE)
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("PARTITION BY RANGE"))
            assertTrue(result.contains("CREATE TABLE") && result.contains("PARTITION OF"))
            assertTrue(result.contains("FOR VALUES FROM"))
        }

        @Test
        @DisplayName("LIST 파티션 → PostgreSQL")
        fun testListPartitionToPostgreSql() {
            val sql = """
                CREATE TABLE events (
                    id NUMBER,
                    event_type VARCHAR2(50)
                )
                PARTITION BY LIST (event_type)
                (
                    PARTITION p_login VALUES ('LOGIN', 'LOGOUT'),
                    PARTITION p_purchase VALUES ('PURCHASE', 'REFUND')
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("PARTITION BY LIST"))
            assertTrue(result.contains("FOR VALUES IN"))
        }

        @Test
        @DisplayName("HASH 파티션 → PostgreSQL")
        fun testHashPartitionToPostgreSql() {
            val sql = """
                CREATE TABLE sessions (
                    session_id NUMBER
                )
                PARTITION BY HASH (session_id)
                PARTITIONS 4
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("PARTITION BY HASH"))
            assertTrue(result.contains("FOR VALUES WITH") || result.contains("MODULUS"))
        }

        @Test
        @DisplayName("INTERVAL 파티션 pg_partman 안내")
        fun testIntervalPartitionPgPartman() {
            val sql = """
                CREATE TABLE audit_logs (
                    log_id NUMBER,
                    created_at DATE
                )
                PARTITION BY RANGE (created_at)
                INTERVAL (NUMTOYMINTERVAL(1, 'MONTH'))
                (
                    PARTITION p_init VALUES LESS THAN (TO_DATE('2024-01-01', 'YYYY-MM-DD'))
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(warnings.any {
                it.message.contains("INTERVAL") && it.suggestion?.contains("pg_partman") == true
            })
        }
    }

    @Nested
    @DisplayName("MySQL → Oracle 변환 테스트")
    inner class MySqlToOracleTest {

        @Test
        @DisplayName("RANGE COLUMNS → RANGE 변환")
        fun testRangeColumnsToRange() {
            val sql = """
                CREATE TABLE orders (
                    order_id INT,
                    order_date DATE
                )
                PARTITION BY RANGE COLUMNS (order_date)
                (
                    PARTITION p2023 VALUES LESS THAN ('2024-01-01'),
                    PARTITION p2024 VALUES LESS THAN MAXVALUE
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.MYSQL, DialectType.ORACLE, warnings, rules
            )

            assertTrue(result.contains("PARTITION BY RANGE"))
            assertFalse(result.contains("COLUMNS"))
        }

        @Test
        @DisplayName("KEY → HASH 변환")
        fun testKeyToHash() {
            val sql = """
                CREATE TABLE users (
                    user_id INT,
                    email VARCHAR(100)
                )
                PARTITION BY KEY (user_id)
                PARTITIONS 4
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.MYSQL, DialectType.ORACLE, warnings, rules
            )

            assertTrue(result.contains("PARTITION BY HASH"))
            assertFalse(result.contains("KEY"))
        }

        @Test
        @DisplayName("STR_TO_DATE → TO_DATE 변환")
        fun testStrToDateConversion() {
            val sql = """
                CREATE TABLE sales (
                    id INT,
                    sale_date DATE
                )
                PARTITION BY RANGE (sale_date)
                (
                    PARTITION p1 VALUES LESS THAN (STR_TO_DATE('2024-01-01', '%Y-%m-%d'))
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.MYSQL, DialectType.ORACLE, warnings, rules
            )

            assertTrue(result.contains("TO_DATE"))
            assertTrue(result.contains("YYYY-MM-DD"))
            assertFalse(result.contains("STR_TO_DATE"))
        }
    }

    @Nested
    @DisplayName("PostgreSQL → MySQL 변환 테스트")
    inner class PostgreSqlToMySqlTest {

        @Test
        @DisplayName("FOR VALUES FROM TO → VALUES LESS THAN 변환")
        fun testForValuesToLessThan() {
            val sql = """
                CREATE TABLE metrics (
                    id SERIAL,
                    created_at TIMESTAMP
                ) PARTITION BY RANGE (created_at);

                CREATE TABLE metrics_p1 PARTITION OF metrics
                FOR VALUES FROM ('2024-01-01') TO ('2024-04-01');
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.POSTGRESQL, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("VALUES LESS THAN"))
        }

        @Test
        @DisplayName("FOR VALUES IN 변환")
        fun testForValuesInConversion() {
            val sql = """
                CREATE TABLE products (
                    id SERIAL,
                    category TEXT
                ) PARTITION BY LIST (category);

                CREATE TABLE products_electronics PARTITION OF products
                FOR VALUES IN ('PHONE', 'LAPTOP');
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.POSTGRESQL, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("VALUES") && result.contains("IN"))
        }
    }

    @Nested
    @DisplayName("서브파티션 테스트")
    inner class SubpartitionTest {

        @Test
        @DisplayName("Range-Hash 복합 파티션 MySQL 변환")
        fun testRangeHashCompositeToMySql() {
            val sql = """
                CREATE TABLE sales_data (
                    id NUMBER,
                    sale_date DATE,
                    region_id NUMBER
                )
                PARTITION BY RANGE (sale_date)
                SUBPARTITION BY HASH (region_id)
                SUBPARTITIONS 4
                (
                    PARTITION p2023 VALUES LESS THAN (TO_DATE('2024-01-01', 'YYYY-MM-DD')),
                    PARTITION p2024 VALUES LESS THAN (MAXVALUE)
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("PARTITION BY RANGE"))
            assertTrue(result.contains("SUBPARTITION") || result.contains("HASH"))
        }

        @Test
        @DisplayName("Range-List 복합 파티션 경고")
        fun testRangeListCompositeWarning() {
            val sql = """
                CREATE TABLE orders (
                    id NUMBER,
                    order_date DATE,
                    status VARCHAR2(20)
                )
                PARTITION BY RANGE (order_date)
                SUBPARTITION BY LIST (status)
                (
                    PARTITION p2023 VALUES LESS THAN (TO_DATE('2024-01-01', 'YYYY-MM-DD'))
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            // MySQL은 LIST 서브파티션을 지원하지 않으므로 경고 확인
            assertTrue(warnings.any {
                it.message.contains("서브파티션") || it.message.contains("HASH") || it.message.contains("KEY")
            })
        }
    }

    @Nested
    @DisplayName("다중 컬럼 파티션 테스트")
    inner class MultiColumnPartitionTest {

        @Test
        @DisplayName("다중 컬럼 RANGE 파티션")
        fun testMultiColumnRangePartition() {
            val sql = """
                CREATE TABLE events (
                    year_col INT,
                    month_col INT,
                    data TEXT
                )
                PARTITION BY RANGE (year_col, month_col)
                (
                    PARTITION p_2023_q1 VALUES LESS THAN (2023, 4),
                    PARTITION p_2023_q2 VALUES LESS THAN (2023, 7),
                    PARTITION p_max VALUES LESS THAN (MAXVALUE, MAXVALUE)
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("year_col") || result.contains("\"year_col\""))
            assertTrue(result.contains("month_col") || result.contains("\"month_col\""))
        }
    }

    @Nested
    @DisplayName("경계 케이스 테스트")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("동일 방언 변환 - 원본 유지")
        fun testSameDialectNoChange() {
            val sql = """
                CREATE TABLE test (id INT)
                PARTITION BY HASH (id)
                PARTITIONS 4
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.ORACLE, warnings, rules
            )

            assertEquals(sql, result)
        }

        @Test
        @DisplayName("파티션 없는 테이블 - 원본 유지")
        fun testNonPartitionedTableNoChange() {
            val sql = "CREATE TABLE simple (id INT, name VARCHAR(100))"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertEquals(sql, result)
        }

        @Test
        @DisplayName("빈 파티션 목록 처리")
        fun testEmptyPartitionList() {
            val sql = """
                CREATE TABLE data (id INT)
                PARTITION BY HASH (id)
                PARTITIONS 4
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("PARTITION BY HASH"))
        }

        @Test
        @DisplayName("특수 문자가 포함된 파티션 이름")
        fun testSpecialCharacterPartitionName() {
            val sql = """
                CREATE TABLE data (id INT)
                PARTITION BY RANGE (id)
                (
                    PARTITION "p_2024_q1" VALUES LESS THAN (1000),
                    PARTITION "p_2024_q2" VALUES LESS THAN (MAXVALUE)
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("p_2024_q1") || result.contains("`p_2024_q1`"))
        }
    }
}
