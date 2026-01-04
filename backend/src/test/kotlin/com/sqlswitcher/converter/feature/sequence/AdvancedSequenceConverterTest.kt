package com.sqlswitcher.converter.feature.sequence

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * AdvancedSequenceConverter 단위 테스트
 */
class AdvancedSequenceConverterTest {

    private fun convert(sql: String, source: DialectType, target: DialectType): Triple<String, List<ConversionWarning>, List<String>> {
        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()
        val result = AdvancedSequenceConverter.convert(sql, source, target, warnings, rules)
        return Triple(result, warnings, rules)
    }

    @Nested
    @DisplayName("CREATE SEQUENCE 변환 테스트")
    inner class CreateSequenceTest {

        @Test
        @DisplayName("Oracle → MySQL 기본 시퀀스")
        fun testOracleToMySqlBasic() {
            val sql = "CREATE SEQUENCE emp_seq START WITH 100 INCREMENT BY 5;"

            val (result, _, rules) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("CREATE SEQUENCE emp_seq"))
            assertTrue(result.contains("START WITH 100"))
            assertTrue(result.contains("INCREMENT BY 5"))
            assertTrue(rules.isNotEmpty())
        }

        @Test
        @DisplayName("Oracle → PostgreSQL 시퀀스")
        fun testOracleToPostgreSql() {
            val sql = "CREATE SEQUENCE emp_seq START WITH 100 INCREMENT BY 10 MAXVALUE 99999 NOCYCLE;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("CREATE SEQUENCE emp_seq"))
            assertTrue(result.contains("START WITH 100"))
            assertTrue(result.contains("INCREMENT BY 10"))
            assertTrue(result.contains("MAXVALUE 99999"))
            assertTrue(result.contains("NO CYCLE"))
        }

        @Test
        @DisplayName("PostgreSQL → Oracle 시퀀스")
        fun testPostgreSqlToOracle() {
            val sql = "CREATE SEQUENCE emp_seq START WITH 1 NO MINVALUE NO MAXVALUE NO CYCLE;"

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL, DialectType.ORACLE)

            assertTrue(result.contains("CREATE SEQUENCE emp_seq"))
            assertTrue(result.contains("NOMINVALUE") || result.contains("NO MINVALUE"))
            assertTrue(result.contains("NOMAXVALUE") || result.contains("NO MAXVALUE"))
        }

        @Test
        @DisplayName("시퀀스 옵션 전체 파싱")
        fun testFullSequenceOptions() {
            val sql = """
                CREATE SEQUENCE test_seq
                START WITH 1000
                INCREMENT BY 5
                MINVALUE 100
                MAXVALUE 999999
                CACHE 20
                CYCLE
                ORDER;
            """.trimIndent()

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("START WITH 1000"))
            assertTrue(result.contains("INCREMENT BY 5"))
            assertTrue(result.contains("MINVALUE 100"))
            assertTrue(result.contains("MAXVALUE 999999"))
            assertTrue(result.contains("CACHE 20"))
            assertTrue(result.contains("CYCLE"))
        }

        @Test
        @DisplayName("스키마가 있는 시퀀스")
        fun testSchemaQualifiedSequence() {
            val sql = "CREATE SEQUENCE hr.emp_seq START WITH 1;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("hr.emp_seq"))
        }
    }

    @Nested
    @DisplayName("DROP SEQUENCE 변환 테스트")
    inner class DropSequenceTest {

        @Test
        @DisplayName("Oracle → MySQL DROP SEQUENCE")
        fun testDropSequenceToMySql() {
            val sql = "DROP SEQUENCE emp_seq;"

            val (result, _, rules) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("DROP SEQUENCE"))
            assertTrue(result.contains("IF EXISTS"))
            assertTrue(rules.isNotEmpty())
        }

        @Test
        @DisplayName("Oracle → PostgreSQL DROP SEQUENCE")
        fun testDropSequenceToPostgreSql() {
            val sql = "DROP SEQUENCE emp_seq;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("DROP SEQUENCE"))
            assertTrue(result.contains("CASCADE") || result.contains("IF EXISTS"))
        }
    }

    @Nested
    @DisplayName("NEXTVAL/CURRVAL 변환 테스트")
    inner class SequenceUsageTest {

        @Test
        @DisplayName("Oracle NEXTVAL → PostgreSQL nextval()")
        fun testNextvalToPostgreSql() {
            val sql = "INSERT INTO emp (id, name) VALUES (emp_seq.NEXTVAL, 'John');"

            val (result, _, rules) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("nextval('emp_seq')"))
            assertFalse(result.contains(".NEXTVAL"))
        }

        @Test
        @DisplayName("Oracle CURRVAL → PostgreSQL currval()")
        fun testCurrvalToPostgreSql() {
            val sql = "SELECT emp_seq.CURRVAL FROM dual;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL)

            assertTrue(result.contains("currval('emp_seq')"))
        }

        @Test
        @DisplayName("Oracle NEXTVAL → MySQL NEXTVAL()")
        fun testNextvalToMySql() {
            val sql = "SELECT emp_seq.NEXTVAL FROM dual;"

            val (result, _, _) = convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            assertTrue(result.contains("NEXTVAL(emp_seq)"))
        }

        @Test
        @DisplayName("PostgreSQL nextval() → Oracle NEXTVAL")
        fun testPgNextvalToOracle() {
            val sql = "INSERT INTO emp (id) VALUES (nextval('emp_seq'));"

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL, DialectType.ORACLE)

            assertTrue(result.contains("emp_seq.NEXTVAL"))
        }

        @Test
        @DisplayName("PostgreSQL currval() → Oracle CURRVAL")
        fun testPgCurrvalToOracle() {
            val sql = "SELECT currval('emp_seq');"

            val (result, _, _) = convert(sql, DialectType.POSTGRESQL, DialectType.ORACLE)

            assertTrue(result.contains("emp_seq.CURRVAL"))
        }
    }

    @Nested
    @DisplayName("MySQL 시퀀스 에뮬레이션 테스트")
    inner class MySqlEmulationTest {

        @Test
        @DisplayName("테이블 기반 시퀀스 에뮬레이션 생성")
        fun testGenerateMySqlEmulation() {
            val info = AdvancedSequenceConverter.SequenceInfo(startWith = 1000, incrementBy = 1)
            val result = AdvancedSequenceConverter.generateMySqlSequenceEmulation("emp_seq", info)

            assertTrue(result.contains("CREATE TABLE emp_seq_seq"))
            assertTrue(result.contains("AUTO_INCREMENT = 1000"))
            assertTrue(result.contains("FUNCTION emp_seq_nextval"))
            assertTrue(result.contains("FUNCTION emp_seq_currval"))
            assertTrue(result.contains("LAST_INSERT_ID()"))
        }
    }

    @Nested
    @DisplayName("Identity/AUTO_INCREMENT 변환 테스트")
    inner class IdentityConversionTest {

        @Test
        @DisplayName("Identity 컬럼 → Oracle 시퀀스 + 트리거")
        fun testIdentityToOracleSequence() {
            val result = AdvancedSequenceConverter.convertIdentityToSequence(
                "employees", "id", DialectType.ORACLE, 1, 1
            )

            assertTrue(result.contains("CREATE SEQUENCE employees_id_seq"))
            assertTrue(result.contains("CREATE OR REPLACE TRIGGER"))
            assertTrue(result.contains("NEXTVAL"))
        }

        @Test
        @DisplayName("Identity 컬럼 → PostgreSQL 시퀀스")
        fun testIdentityToPostgreSqlSequence() {
            val result = AdvancedSequenceConverter.convertIdentityToSequence(
                "employees", "id", DialectType.POSTGRESQL, 1, 1
            )

            assertTrue(result.contains("CREATE SEQUENCE employees_id_seq"))
            assertTrue(result.contains("SET DEFAULT nextval"))
            assertTrue(result.contains("OWNED BY"))
        }

        @Test
        @DisplayName("Identity 컬럼 → MySQL AUTO_INCREMENT")
        fun testIdentityToMySqlAutoIncrement() {
            val result = AdvancedSequenceConverter.convertIdentityToSequence(
                "employees", "id", DialectType.MYSQL, 100, 1
            )

            assertTrue(result.contains("AUTO_INCREMENT"))
            assertTrue(result.contains("AUTO_INCREMENT = 100"))
        }

        @Test
        @DisplayName("AUTO_INCREMENT → Oracle IDENTITY")
        fun testAutoIncrementToOracle() {
            val sql = "id BIGINT AUTO_INCREMENT"
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = AdvancedSequenceConverter.convertAutoIncrementToSequence(
                sql, DialectType.ORACLE, warnings, rules
            )

            assertTrue(result.contains("GENERATED ALWAYS AS IDENTITY"))
        }

        @Test
        @DisplayName("AUTO_INCREMENT → PostgreSQL SERIAL")
        fun testAutoIncrementToPostgreSql() {
            val sql = "id BIGINT AUTO_INCREMENT"
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = AdvancedSequenceConverter.convertAutoIncrementToSequence(
                sql, DialectType.POSTGRESQL, warnings, rules
            )

            assertTrue(result.contains("BIGSERIAL") || result.contains("SERIAL"))
        }

        @Test
        @DisplayName("SERIAL → Oracle IDENTITY")
        fun testSerialToOracle() {
            val sql = "id BIGSERIAL"
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = AdvancedSequenceConverter.convertAutoIncrementToSequence(
                sql, DialectType.ORACLE, warnings, rules
            )

            assertTrue(result.contains("GENERATED ALWAYS AS IDENTITY"))
            assertTrue(result.contains("NUMBER"))
        }

        @Test
        @DisplayName("SERIAL → MySQL AUTO_INCREMENT")
        fun testSerialToMySql() {
            val sql = "id SERIAL"
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            val result = AdvancedSequenceConverter.convertAutoIncrementToSequence(
                sql, DialectType.MYSQL, warnings, rules
            )

            assertTrue(result.contains("AUTO_INCREMENT"))
        }
    }

    @Nested
    @DisplayName("유틸리티 메서드 테스트")
    inner class UtilityMethodsTest {

        @Test
        @DisplayName("시퀀스 문 감지")
        fun testIsSequenceStatement() {
            assertTrue(AdvancedSequenceConverter.isSequenceStatement("CREATE SEQUENCE emp_seq;"))
            assertTrue(AdvancedSequenceConverter.isSequenceStatement("DROP SEQUENCE emp_seq;"))
            assertTrue(AdvancedSequenceConverter.isSequenceStatement("ALTER SEQUENCE emp_seq RESTART;"))
            assertFalse(AdvancedSequenceConverter.isSequenceStatement("SELECT * FROM employees"))
        }

        @Test
        @DisplayName("시퀀스 참조 감지")
        fun testHasSequenceReference() {
            assertTrue(AdvancedSequenceConverter.hasSequenceReference("emp_seq.NEXTVAL"))
            assertTrue(AdvancedSequenceConverter.hasSequenceReference("emp_seq.CURRVAL"))
            assertTrue(AdvancedSequenceConverter.hasSequenceReference("nextval('emp_seq')"))
            assertTrue(AdvancedSequenceConverter.hasSequenceReference("currval('emp_seq')"))
            assertFalse(AdvancedSequenceConverter.hasSequenceReference("SELECT * FROM employees"))
        }

        @Test
        @DisplayName("참조된 시퀀스 추출")
        fun testGetReferencedSequences() {
            val sql = """
                INSERT INTO t1 VALUES (seq1.NEXTVAL);
                INSERT INTO t2 VALUES (seq2.NEXTVAL);
                SELECT seq1.CURRVAL FROM dual;
            """.trimIndent()

            val sequences = AdvancedSequenceConverter.getReferencedSequences(sql)

            assertTrue(sequences.contains("seq1"))
            assertTrue(sequences.contains("seq2"))
        }

        @Test
        @DisplayName("PostgreSQL 형식 시퀀스 참조 추출")
        fun testGetReferencedSequencesPg() {
            val sql = """
                INSERT INTO t1 VALUES (nextval('emp_seq'));
                SELECT currval('dept_seq');
            """.trimIndent()

            val sequences = AdvancedSequenceConverter.getReferencedSequences(sql)

            assertTrue(sequences.contains("emp_seq"))
            assertTrue(sequences.contains("dept_seq"))
        }
    }

    @Nested
    @DisplayName("SequenceInfo 테스트")
    inner class SequenceInfoTest {

        @Test
        @DisplayName("기본값 확인")
        fun testDefaultValues() {
            val info = AdvancedSequenceConverter.SequenceInfo()

            assertEquals(1L, info.startWith)
            assertEquals(1L, info.incrementBy)
            assertEquals(null, info.minValue)
            assertEquals(null, info.maxValue)
            assertEquals(null, info.cache)
            assertFalse(info.cycle)
            assertFalse(info.order)
        }

        @Test
        @DisplayName("커스텀 값 설정")
        fun testCustomValues() {
            val info = AdvancedSequenceConverter.SequenceInfo(
                startWith = 1000,
                incrementBy = 10,
                minValue = 1,
                maxValue = 99999,
                cache = 20,
                cycle = true,
                order = true
            )

            assertEquals(1000L, info.startWith)
            assertEquals(10L, info.incrementBy)
            assertEquals(1L, info.minValue)
            assertEquals(99999L, info.maxValue)
            assertEquals(20, info.cache)
            assertTrue(info.cycle)
            assertTrue(info.order)
        }
    }
}
