package com.sqlswitcher.converter.performance

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.feature.dblink.DatabaseLinkConverter
import com.sqlswitcher.converter.feature.index.AdvancedIndexConverter
import com.sqlswitcher.converter.feature.mview.MaterializedViewConverter
import com.sqlswitcher.converter.feature.sequence.AdvancedSequenceConverter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.system.measureTimeMillis

/**
 * 변환 성능 테스트
 *
 * 각 변환기의 성능을 측정하고 임계값 이내인지 확인합니다.
 */
class ConversionPerformanceTest {

    companion object {
        // 성능 임계값 (밀리초)
        const val SINGLE_CONVERSION_THRESHOLD_MS = 50L
        const val BATCH_CONVERSION_THRESHOLD_MS = 1000L
        const val ITERATION_COUNT = 100
    }

    @Test
    @DisplayName("시퀀스 변환 성능 - 단일 쿼리")
    fun testSequenceConversionPerformance() {
        val sql = "CREATE SEQUENCE emp_seq START WITH 1000 INCREMENT BY 10 MAXVALUE 999999 CACHE 20 CYCLE;"
        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()

        // 워밍업
        repeat(10) {
            AdvancedSequenceConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
            warnings.clear()
            rules.clear()
        }

        // 측정
        val elapsed = measureTimeMillis {
            AdvancedSequenceConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
        }

        println("시퀀스 단일 변환: ${elapsed}ms")
        assertTrue(elapsed < SINGLE_CONVERSION_THRESHOLD_MS, "시퀀스 변환이 ${SINGLE_CONVERSION_THRESHOLD_MS}ms 이내여야 합니다")
    }

    @Test
    @DisplayName("시퀀스 변환 성능 - 배치")
    fun testSequenceBatchConversionPerformance() {
        val sql = "CREATE SEQUENCE emp_seq START WITH 1000 INCREMENT BY 10;"

        // 워밍업
        repeat(10) {
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            AdvancedSequenceConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
        }

        // 측정
        val elapsed = measureTimeMillis {
            repeat(ITERATION_COUNT) {
                val warnings = mutableListOf<ConversionWarning>()
                val rules = mutableListOf<String>()
                AdvancedSequenceConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
            }
        }

        val avgTime = elapsed.toDouble() / ITERATION_COUNT
        println("시퀀스 배치 변환 (${ITERATION_COUNT}회): 총 ${elapsed}ms, 평균 ${avgTime}ms/건")
        assertTrue(elapsed < BATCH_CONVERSION_THRESHOLD_MS, "시퀀스 배치 변환이 ${BATCH_CONVERSION_THRESHOLD_MS}ms 이내여야 합니다")
    }

    @Test
    @DisplayName("인덱스 변환 성능 - 단일 쿼리")
    fun testIndexConversionPerformance() {
        val sql = "CREATE UNIQUE INDEX emp_comp_idx ON employees (department_id, job_id, salary DESC) TABLESPACE idx_ts PARALLEL 4;"
        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()

        // 워밍업
        repeat(10) {
            AdvancedIndexConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
            warnings.clear()
            rules.clear()
        }

        // 측정
        val elapsed = measureTimeMillis {
            AdvancedIndexConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
        }

        println("인덱스 단일 변환: ${elapsed}ms")
        assertTrue(elapsed < SINGLE_CONVERSION_THRESHOLD_MS, "인덱스 변환이 ${SINGLE_CONVERSION_THRESHOLD_MS}ms 이내여야 합니다")
    }

    @Test
    @DisplayName("인덱스 변환 성능 - 배치")
    fun testIndexBatchConversionPerformance() {
        val sql = "CREATE INDEX emp_idx ON employees (last_name, first_name);"

        // 워밍업
        repeat(10) {
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()
            AdvancedIndexConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules)
        }

        // 측정
        val elapsed = measureTimeMillis {
            repeat(ITERATION_COUNT) {
                val warnings = mutableListOf<ConversionWarning>()
                val rules = mutableListOf<String>()
                AdvancedIndexConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules)
            }
        }

        val avgTime = elapsed.toDouble() / ITERATION_COUNT
        println("인덱스 배치 변환 (${ITERATION_COUNT}회): 총 ${elapsed}ms, 평균 ${avgTime}ms/건")
        assertTrue(elapsed < BATCH_CONVERSION_THRESHOLD_MS, "인덱스 배치 변환이 ${BATCH_CONVERSION_THRESHOLD_MS}ms 이내여야 합니다")
    }

    @Test
    @DisplayName("MView 변환 성능 - 단일 쿼리")
    fun testMViewConversionPerformance() {
        val sql = """
            CREATE MATERIALIZED VIEW emp_summary
            BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND
            AS SELECT department_id, COUNT(*) as cnt FROM employees GROUP BY department_id;
        """.trimIndent()
        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()

        // 워밍업
        repeat(10) {
            MaterializedViewConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules)
            warnings.clear()
            rules.clear()
        }

        // 측정
        val elapsed = measureTimeMillis {
            MaterializedViewConverter.convert(sql, DialectType.ORACLE, DialectType.MYSQL, warnings, rules)
        }

        println("MView 단일 변환: ${elapsed}ms")
        assertTrue(elapsed < SINGLE_CONVERSION_THRESHOLD_MS, "MView 변환이 ${SINGLE_CONVERSION_THRESHOLD_MS}ms 이내여야 합니다")
    }

    @Test
    @DisplayName("Database Link 참조 변환 성능 - 많은 참조")
    fun testDbLinkReferencePerformance() {
        // 많은 @dblink 참조를 포함한 SQL
        val sql = buildString {
            append("SELECT ")
            repeat(50) { i ->
                if (i > 0) append(", ")
                append("t$i.col@remote_db$i")
            }
            append(" FROM ")
            repeat(50) { i ->
                if (i > 0) append(", ")
                append("table$i@remote_db$i t$i")
            }
        }

        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()

        // 워밍업
        repeat(10) {
            DatabaseLinkConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
            warnings.clear()
            rules.clear()
        }

        // 측정
        val elapsed = measureTimeMillis {
            DatabaseLinkConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
        }

        println("DBLink 참조 변환 (50개 참조): ${elapsed}ms")
        assertTrue(elapsed < 100, "많은 DBLink 참조 변환이 100ms 이내여야 합니다")
    }

    @Test
    @DisplayName("복합 변환 성능")
    fun testCombinedConversionPerformance() {
        val sqls = listOf(
            "CREATE SEQUENCE emp_seq START WITH 1;",
            "CREATE INDEX emp_idx ON employees (name);",
            "SELECT * FROM employees@remote_db;",
            "CREATE MATERIALIZED VIEW mv AS SELECT 1;"
        )

        // 워밍업
        repeat(10) {
            sqls.forEach { sql ->
                val warnings = mutableListOf<ConversionWarning>()
                val rules = mutableListOf<String>()
                if (AdvancedSequenceConverter.isSequenceStatement(sql)) {
                    AdvancedSequenceConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
                }
                if (AdvancedIndexConverter.isIndexStatement(sql)) {
                    AdvancedIndexConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
                }
                if (DatabaseLinkConverter.hasDbLinkReference(sql)) {
                    DatabaseLinkConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
                }
                if (MaterializedViewConverter.isMaterializedViewStatement(sql)) {
                    MaterializedViewConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
                }
            }
        }

        // 측정
        val elapsed = measureTimeMillis {
            repeat(ITERATION_COUNT) {
                sqls.forEach { sql ->
                    val warnings = mutableListOf<ConversionWarning>()
                    val rules = mutableListOf<String>()
                    if (AdvancedSequenceConverter.isSequenceStatement(sql)) {
                        AdvancedSequenceConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
                    }
                    if (AdvancedIndexConverter.isIndexStatement(sql)) {
                        AdvancedIndexConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
                    }
                    if (DatabaseLinkConverter.hasDbLinkReference(sql)) {
                        DatabaseLinkConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
                    }
                    if (MaterializedViewConverter.isMaterializedViewStatement(sql)) {
                        MaterializedViewConverter.convert(sql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
                    }
                }
            }
        }

        val totalOps = ITERATION_COUNT * sqls.size
        val avgTime = elapsed.toDouble() / totalOps
        println("복합 변환 (${totalOps}건): 총 ${elapsed}ms, 평균 ${avgTime}ms/건")
        assertTrue(elapsed < BATCH_CONVERSION_THRESHOLD_MS * 2, "복합 배치 변환이 ${BATCH_CONVERSION_THRESHOLD_MS * 2}ms 이내여야 합니다")
    }

    @Test
    @DisplayName("메모리 효율성 - 대용량 SQL")
    fun testMemoryEfficiency() {
        // 대용량 SQL 생성 (10KB 이상)
        val largeSql = buildString {
            append("SELECT ")
            repeat(500) { i ->
                if (i > 0) append(", ")
                append("column_$i")
            }
            append(" FROM large_table WHERE ")
            repeat(100) { i ->
                if (i > 0) append(" AND ")
                append("column_$i = $i")
            }
        }

        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()

        // 메모리 사용량 측정을 위한 GC
        System.gc()
        val beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // 변환 수행
        val elapsed = measureTimeMillis {
            repeat(100) {
                AdvancedSequenceConverter.convert(largeSql, DialectType.ORACLE, DialectType.POSTGRESQL, warnings, rules)
                warnings.clear()
                rules.clear()
            }
        }

        System.gc()
        val afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryUsed = (afterMemory - beforeMemory) / 1024 / 1024 // MB

        println("대용량 SQL 처리 (${largeSql.length} bytes): ${elapsed}ms, 메모리 증가: ${memoryUsed}MB")
        assertTrue(elapsed < 500, "대용량 SQL 처리가 500ms 이내여야 합니다")
    }
}
