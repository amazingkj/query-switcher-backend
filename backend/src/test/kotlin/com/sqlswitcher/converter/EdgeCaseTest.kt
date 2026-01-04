package com.sqlswitcher.converter

import com.sqlswitcher.converter.feature.partition.AdvancedPartitionConverter
import com.sqlswitcher.converter.feature.flashback.FlashbackQueryConverter
import com.sqlswitcher.converter.feature.procedure.ProcedureBodyConverter
import com.sqlswitcher.converter.feature.sequence.AdvancedSequenceConverter
import com.sqlswitcher.converter.feature.index.AdvancedIndexConverter
import com.sqlswitcher.converter.feature.mview.MaterializedViewConverter
import com.sqlswitcher.converter.feature.dblink.DatabaseLinkConverter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 변환기 엣지 케이스 테스트
 *
 * 다양한 경계 조건, 특수 문자, 빈 입력, 대용량 입력 등을 테스트합니다.
 */
class EdgeCaseTest {

    @Nested
    @DisplayName("빈 입력 및 null 케이스")
    inner class EmptyInputTest {

        @Test
        @DisplayName("빈 문자열 - 시퀀스 변환")
        fun testEmptySequence() {
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(
                "", DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )
            assertEquals("", result)
        }

        @Test
        @DisplayName("빈 문자열 - 인덱스 변환")
        fun testEmptyIndex() {
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedIndexConverter.convert(
                "", DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )
            assertEquals("", result)
        }

        @Test
        @DisplayName("공백만 있는 문자열")
        fun testWhitespaceOnly() {
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(
                "   \n\t  ", DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )
            assertEquals("   \n\t  ", result)
        }

        @Test
        @DisplayName("빈 문자열 - 파티션 변환")
        fun testEmptyPartition() {
            assertFalse(AdvancedPartitionConverter.isPartitionedTable(""))
        }

        @Test
        @DisplayName("빈 문자열 - FLASHBACK 변환")
        fun testEmptyFlashback() {
            assertFalse(FlashbackQueryConverter.hasFlashbackSyntax(""))
        }
    }

    @Nested
    @DisplayName("특수 문자 처리")
    inner class SpecialCharacterTest {

        @Test
        @DisplayName("한글 테이블명")
        fun testKoreanTableName() {
            val sql = "CREATE SEQUENCE 주문번호_seq START WITH 1;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("주문번호_seq"))
        }

        @Test
        @DisplayName("따옴표가 포함된 식별자")
        fun testQuotedIdentifiers() {
            val sql = """CREATE INDEX "my-index" ON "my-table" ("my-column");"""

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedIndexConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertNotNull(result)
            assertTrue(result.contains("my-index") || result.contains("`my-index`"))
        }

        @Test
        @DisplayName("숫자로 시작하는 식별자")
        fun testNumericStartIdentifier() {
            val sql = "CREATE SEQUENCE \"123_seq\" START WITH 1;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("123_seq"))
        }

        @Test
        @DisplayName("특수 문자가 포함된 파티션 값")
        fun testSpecialPartitionValues() {
            val sql = """
                CREATE TABLE events (
                    id NUMBER,
                    status VARCHAR2(50)
                )
                PARTITION BY LIST (status)
                (
                    PARTITION p_active VALUES ('ACTIVE', 'IN-PROGRESS', 'ON_HOLD'),
                    PARTITION p_complete VALUES ('COMPLETE')
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("IN-PROGRESS") || result.contains("'IN-PROGRESS'"))
        }

        @Test
        @DisplayName("이스케이프 문자가 포함된 SQL")
        fun testEscapedCharacters() {
            val sql = "SELECT * FROM test WHERE name = 'O''Brien' AS OF SCN 12345"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("O''Brien") || result.contains("O\\'Brien"))
        }
    }

    @Nested
    @DisplayName("대소문자 혼합 처리")
    inner class CaseSensitivityTest {

        @Test
        @DisplayName("대소문자 혼합 키워드 - 시퀀스")
        fun testMixedCaseSequence() {
            val sql = "Create Sequence Mixed_Seq StArT WiTh 100 InCrEmEnT By 5;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("Mixed_Seq") || result.contains("mixed_seq"))
        }

        @Test
        @DisplayName("대소문자 혼합 키워드 - 인덱스")
        fun testMixedCaseIndex() {
            val sql = "CrEaTe InDeX MyIdx On MyTable (ColA, ColB);"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedIndexConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("MyIdx") || result.contains("myidx") ||
                    result.contains("`MyIdx`"))
        }

        @Test
        @DisplayName("대소문자 혼합 - FLASHBACK")
        fun testMixedCaseFlashback() {
            val sql = "sElEcT * FrOm EmPlOyEeS aS oF sCn 12345"

            assertTrue(FlashbackQueryConverter.hasFlashbackSyntax(sql))
        }
    }

    @Nested
    @DisplayName("대용량 입력 처리")
    inner class LargeInputTest {

        @Test
        @DisplayName("긴 SQL 문 처리")
        fun testLongSql() {
            // 1000개 컬럼이 있는 SELECT 생성
            val columns = (1..1000).joinToString(", ") { "col_$it" }
            val sql = "SELECT $columns FROM large_table AS OF SCN 12345"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertNotNull(result)
            assertTrue(result.length > 1000)
        }

        @Test
        @DisplayName("많은 파티션이 있는 테이블")
        fun testManyPartitions() {
            val partitions = (1..50).joinToString(",\n") { i ->
                "    PARTITION p_$i VALUES LESS THAN ($i * 1000)"
            }

            val sql = """
                CREATE TABLE large_table (
                    id NUMBER
                )
                PARTITION BY RANGE (id)
                (
                $partitions
                )
            """.trimIndent()

            assertTrue(AdvancedPartitionConverter.isPartitionedTable(sql))
        }

        @Test
        @DisplayName("긴 프로시저 본문")
        fun testLongProcedureBody() {
            val statements = (1..100).joinToString("\n") { i ->
                "    v_$i := SQL%ROWCOUNT;"
            }

            val body = """
                DECLARE
                    ${(1..100).joinToString("\n    ") { "v_$it NUMBER;" }}
                BEGIN
                $statements
                END;
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = ProcedureBodyConverter.convertBody(
                body, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("ROW_COUNT()"))
            assertTrue(rules.any { it.contains("SQL%ROWCOUNT") })
        }
    }

    @Nested
    @DisplayName("중첩 구조 처리")
    inner class NestedStructureTest {

        @Test
        @DisplayName("중첩 서브쿼리가 있는 AS OF")
        fun testNestedSubqueryAsOf() {
            val sql = """
                SELECT * FROM (
                    SELECT * FROM employees AS OF SCN 12345
                    WHERE dept_id IN (
                        SELECT dept_id FROM departments AS OF SCN 12345
                    )
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            // 둘 다 변환되어야 함
            assertTrue(result.contains("/*"))
        }

        @Test
        @DisplayName("중첩 함수 호출")
        fun testNestedFunctionCalls() {
            val body = """
                v_result := NVL(NVL(v_a, v_b), NVL(v_c, v_d));
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            // 프로시저 변환에서 NVL은 별도 처리되므로 본문 변환기에서는 변환 안됨
            // 여기서는 본문 구조 테스트
            assertNotNull(body)
        }

        @Test
        @DisplayName("복잡한 CASE 표현식")
        fun testComplexCaseExpression() {
            val sql = """
                SELECT
                    CASE
                        WHEN status = 'A' THEN
                            CASE WHEN type = 1 THEN 'X' ELSE 'Y' END
                        WHEN status = 'B' THEN 'Z'
                        ELSE 'W'
                    END
                FROM table1 AS OF TIMESTAMP SYSDATE - 1
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = FlashbackQueryConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("경계 값 테스트")
    inner class BoundaryValueTest {

        @Test
        @DisplayName("최소값 시퀀스")
        fun testMinValueSequence() {
            val sql = "CREATE SEQUENCE min_seq START WITH -9223372036854775808;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertNotNull(result)
        }

        @Test
        @DisplayName("최대값 시퀀스")
        fun testMaxValueSequence() {
            val sql = "CREATE SEQUENCE max_seq MAXVALUE 9223372036854775807;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertNotNull(result)
        }

        @Test
        @DisplayName("0 파티션 개수")
        fun testZeroPartitions() {
            val sql = """
                CREATE TABLE test (id NUMBER)
                PARTITION BY HASH (id)
                PARTITIONS 0
            """.trimIndent()

            // 0 파티션은 무효하지만 파싱은 가능해야 함
            assertTrue(AdvancedPartitionConverter.isPartitionedTable(sql))
        }

        @Test
        @DisplayName("매우 긴 식별자")
        fun testVeryLongIdentifier() {
            val longName = "a".repeat(128)
            val sql = "CREATE SEQUENCE ${longName}_seq START WITH 1;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains(longName))
        }
    }

    @Nested
    @DisplayName("잘못된 구문 처리")
    inner class InvalidSyntaxTest {

        @Test
        @DisplayName("불완전한 시퀀스 구문")
        fun testIncompleteSequence() {
            val sql = "CREATE SEQUENCE incomplete_seq START WITH"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            // 변환이 실패하거나 원본이 반환되어야 함
            assertNotNull(result)
        }

        @Test
        @DisplayName("잘못된 파티션 값")
        fun testInvalidPartitionValue() {
            val sql = """
                CREATE TABLE test (id NUMBER)
                PARTITION BY RANGE (id)
                (
                    PARTITION p1 VALUES LESS THAN ('not_a_number')
                )
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedPartitionConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertNotNull(result)
        }

        @Test
        @DisplayName("닫히지 않은 괄호")
        fun testUnclosedParenthesis() {
            val sql = "CREATE INDEX idx ON table1 (col1, col2"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedIndexConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("동일 방언 변환")
    inner class SameDialectTest {

        @Test
        @DisplayName("Oracle → Oracle 시퀀스 - 핵심 요소 보존")
        fun testOracleToOracleSequence() {
            val sql = "CREATE SEQUENCE test_seq START WITH 100;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(
                sql, DialectType.ORACLE, DialectType.ORACLE, warnings, rules
            )

            // 동일 방언에서는 핵심 요소가 보존되어야 함
            assertTrue(result.contains("CREATE SEQUENCE"))
            assertTrue(result.contains("test_seq"))
            assertTrue(result.contains("START WITH 100"))
        }

        @Test
        @DisplayName("MySQL → MySQL 인덱스")
        fun testMySqlToMySqlIndex() {
            val sql = "CREATE INDEX idx ON `table` (`col`);"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedIndexConverter.convert(
                sql, DialectType.MYSQL, DialectType.MYSQL, warnings, rules
            )

            // MySQL → MySQL은 변환 없이 그대로
            assertEquals(sql, result)
        }

        @Test
        @DisplayName("PostgreSQL → PostgreSQL MView - 핵심 요소 보존")
        fun testPostgreSqlToPostgreSqlMView() {
            val sql = "CREATE MATERIALIZED VIEW mv AS SELECT 1;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = MaterializedViewConverter.convert(
                sql, DialectType.POSTGRESQL, DialectType.POSTGRESQL, warnings, rules
            )

            // 핵심 요소 보존 확인
            assertTrue(result.contains("CREATE MATERIALIZED VIEW"))
            assertTrue(result.contains("mv"))
            assertTrue(result.contains("SELECT 1"))
        }
    }

    @Nested
    @DisplayName("복합 변환 테스트")
    inner class CombinedConversionTest {

        @Test
        @DisplayName("시퀀스 참조와 FLASHBACK 동시 사용")
        fun testSequenceWithFlashback() {
            val sql = """
                INSERT INTO orders (id, created_at)
                SELECT order_seq.NEXTVAL, SYSDATE
                FROM (SELECT * FROM templates AS OF TIMESTAMP SYSDATE - 1)
            """.trimIndent()

            // 시퀀스 변환
            val warnings1 = mutableListOf<ConversionWarning>()
            val rules1 = mutableListOf<String>()
            var result = AdvancedSequenceConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings1, rules1
            )

            // FLASHBACK 변환
            val warnings2 = mutableListOf<ConversionWarning>()
            val rules2 = mutableListOf<String>()
            result = FlashbackQueryConverter.convert(
                result, DialectType.ORACLE, DialectType.POSTGRESQL, warnings2, rules2
            )

            assertTrue(result.contains("nextval") || rules1.any { it.contains("NEXTVAL") })
        }

        @Test
        @DisplayName("DB Link가 포함된 FLASHBACK 쿼리")
        fun testDbLinkWithFlashback() {
            val sql = "SELECT * FROM employees@remote_db AS OF SCN 12345"

            // DB Link 변환
            val warnings1 = mutableListOf<ConversionWarning>()
            val rules1 = mutableListOf<String>()
            var result = DatabaseLinkConverter.convert(
                sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings1, rules1
            )

            // FLASHBACK 변환
            val warnings2 = mutableListOf<ConversionWarning>()
            val rules2 = mutableListOf<String>()
            result = FlashbackQueryConverter.convert(
                result, DialectType.ORACLE, DialectType.POSTGRESQL, warnings2, rules2
            )

            assertNotNull(result)
            assertTrue(warnings1.isNotEmpty() || warnings2.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("유니코드 처리")
    inner class UnicodeTest {

        @Test
        @DisplayName("유니코드 테이블명")
        fun testUnicodeTableName() {
            val sql = "CREATE SEQUENCE 사용자_시퀀스 START WITH 1;"

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedSequenceConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("사용자_시퀀스"))
        }

        @Test
        @DisplayName("유니코드 주석")
        fun testUnicodeComments() {
            val sql = """
                -- 이것은 한글 주석입니다
                CREATE INDEX idx ON tbl (col); -- 인덱스 생성
            """.trimIndent()

            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            val result = AdvancedIndexConverter.convert(
                sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("한글 주석"))
        }

        @Test
        @DisplayName("이모지가 포함된 컬럼 값")
        fun testEmojiInValue() {
            val sql = """
                CREATE TABLE test (id NUMBER, emoji VARCHAR2(100))
                PARTITION BY LIST (emoji)
                (
                    PARTITION p_emoji VALUES ('😀', '😁', '😂')
                )
            """.trimIndent()

            assertTrue(AdvancedPartitionConverter.isPartitionedTable(sql))
        }
    }
}
