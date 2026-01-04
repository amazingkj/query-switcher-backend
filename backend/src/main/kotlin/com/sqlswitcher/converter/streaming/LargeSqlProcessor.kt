package com.sqlswitcher.converter.streaming

import com.sqlswitcher.converter.ConversionResult
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ValidationInfo
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import java.io.BufferedReader
import java.io.Reader
import java.io.StringReader
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 대용량 SQL 파일 처리기
 *
 * 대용량 SQL 파일을 스트리밍 방식으로 처리하여 메모리 효율성을 높이고
 * 청크 단위 병렬 처리로 성능을 최적화합니다.
 *
 * 기능:
 * - 스트리밍 SQL 파싱 (메모리 효율적)
 * - 청크 단위 병렬 변환
 * - 진행률 콜백 지원
 * - 대용량 파일 감지 및 자동 최적화
 */
object LargeSqlProcessor {

    /**
     * 대용량으로 간주하는 기준 (문자 수)
     */
    const val LARGE_SQL_THRESHOLD = 100_000  // 100KB

    /**
     * 기본 청크 크기 (문장 수)
     */
    const val DEFAULT_CHUNK_SIZE = 50

    /**
     * 병렬 처리 스레드 수
     */
    val THREAD_COUNT = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

    /**
     * 처리 결과
     */
    data class ProcessingResult(
        val convertedSql: String,
        val totalStatements: Int,
        val successfulStatements: Int,
        val failedStatements: Int,
        val warnings: List<ConversionWarning>,
        val appliedRules: List<String>,
        val processingTimeMs: Long,
        val chunksProcessed: Int,
        val validation: ValidationInfo?
    ) {
        val successRate: Double
            get() = if (totalStatements > 0) successfulStatements.toDouble() / totalStatements else 0.0
    }

    /**
     * 진행률 콜백 인터페이스
     */
    fun interface ProgressCallback {
        fun onProgress(processed: Int, total: Int, currentStatement: String?)
    }

    /**
     * 대용량 SQL 여부 확인
     */
    fun isLargeSql(sql: String): Boolean {
        return sql.length > LARGE_SQL_THRESHOLD
    }

    /**
     * SQL 문장 스트리밍 파서
     * 메모리 효율적으로 SQL 문장을 하나씩 추출
     */
    fun streamStatements(reader: Reader): Sequence<String> = sequence {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var inBlockComment = false
        var prevChar: Char? = null

        bufferedReader.useLines { lines ->
            for (line in lines) {
                for (char in line) {
                    // 블록 주석 처리
                    if (!inSingleQuote && !inDoubleQuote) {
                        if (prevChar == '/' && char == '*') {
                            inBlockComment = true
                        } else if (prevChar == '*' && char == '/' && inBlockComment) {
                            inBlockComment = false
                            current.append(char)
                            prevChar = char
                            continue
                        }
                    }

                    if (inBlockComment) {
                        current.append(char)
                        prevChar = char
                        continue
                    }

                    when {
                        // 이스케이프된 따옴표
                        char == '\'' && prevChar == '\'' && inSingleQuote -> {
                            current.append(char)
                        }
                        // 작은따옴표
                        char == '\'' && !inDoubleQuote -> {
                            inSingleQuote = !inSingleQuote
                            current.append(char)
                        }
                        // 큰따옴표
                        char == '"' && !inSingleQuote -> {
                            inDoubleQuote = !inDoubleQuote
                            current.append(char)
                        }
                        // 세미콜론 (문자열 밖에서만)
                        char == ';' && !inSingleQuote && !inDoubleQuote -> {
                            val stmt = current.toString().trim()
                            if (stmt.isNotEmpty() && !isCommentOnly(stmt)) {
                                yield(stmt)
                            }
                            current.clear()
                        }
                        else -> {
                            current.append(char)
                        }
                    }
                    prevChar = char
                }
                current.append('\n')
            }
        }

        // 마지막 문장 처리
        val lastStmt = current.toString().trim()
        if (lastStmt.isNotEmpty() && !isCommentOnly(lastStmt)) {
            yield(lastStmt)
        }
    }

    /**
     * 문자열에서 SQL 문장 스트리밍
     */
    fun streamStatements(sql: String): Sequence<String> {
        return streamStatements(StringReader(sql))
    }

    /**
     * 청크 단위 병렬 처리
     */
    fun processInChunks(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        converter: (String, DialectType, DialectType) -> ConversionResult,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        progressCallback: ProgressCallback? = null
    ): ProcessingResult {
        val startTime = System.currentTimeMillis()

        // 문장 스트리밍으로 수집
        val statements = streamStatements(sql).toList()
        val totalStatements = statements.size

        if (totalStatements == 0) {
            return ProcessingResult(
                convertedSql = sql,
                totalStatements = 0,
                successfulStatements = 0,
                failedStatements = 0,
                warnings = emptyList(),
                appliedRules = emptyList(),
                processingTimeMs = System.currentTimeMillis() - startTime,
                chunksProcessed = 0,
                validation = null
            )
        }

        // 소규모는 순차 처리
        if (totalStatements <= chunkSize) {
            return processSequentially(
                statements, sourceDialect, targetDialect, converter, startTime, progressCallback
            )
        }

        // 대규모는 청크 병렬 처리
        return processParallel(
            statements, sourceDialect, targetDialect, converter, chunkSize, startTime, progressCallback
        )
    }

    /**
     * 순차 처리 (소규모 SQL)
     */
    private fun processSequentially(
        statements: List<String>,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        converter: (String, DialectType, DialectType) -> ConversionResult,
        startTime: Long,
        progressCallback: ProgressCallback?
    ): ProcessingResult {
        val results = mutableListOf<String>()
        val allWarnings = mutableListOf<ConversionWarning>()
        val allRules = mutableListOf<String>()
        var successCount = 0
        var failCount = 0

        statements.forEachIndexed { index, stmt ->
            progressCallback?.onProgress(index + 1, statements.size, stmt.take(50))

            try {
                val result = converter(stmt, sourceDialect, targetDialect)
                results.add(result.convertedSql)
                allWarnings.addAll(result.warnings)
                allRules.addAll(result.appliedRules)

                if (result.warnings.none { it.severity == WarningSeverity.ERROR }) {
                    successCount++
                } else {
                    failCount++
                }
            } catch (e: Exception) {
                failCount++
                results.add("/* 변환 실패: ${e.message} */\n$stmt")
                allWarnings.add(ConversionWarning(
                    type = WarningType.MANUAL_REVIEW_NEEDED,
                    message = "문장 변환 실패: ${e.message}",
                    severity = WarningSeverity.ERROR
                ))
            }
        }

        return ProcessingResult(
            convertedSql = results.joinToString(";\n\n") + if (results.isNotEmpty()) ";" else "",
            totalStatements = statements.size,
            successfulStatements = successCount,
            failedStatements = failCount,
            warnings = allWarnings,
            appliedRules = allRules.distinct(),
            processingTimeMs = System.currentTimeMillis() - startTime,
            chunksProcessed = 1,
            validation = null
        )
    }

    /**
     * 병렬 처리 (대규모 SQL)
     */
    private fun processParallel(
        statements: List<String>,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        converter: (String, DialectType, DialectType) -> ConversionResult,
        chunkSize: Int,
        startTime: Long,
        progressCallback: ProgressCallback?
    ): ProcessingResult {
        val executor = Executors.newFixedThreadPool(THREAD_COUNT)
        val chunks = statements.chunked(chunkSize)
        val processedCount = AtomicInteger(0)

        try {
            val futures: List<Future<ChunkResult>> = chunks.mapIndexed { chunkIndex, chunk ->
                executor.submit<ChunkResult> {
                    val chunkResults = mutableListOf<String>()
                    val chunkWarnings = mutableListOf<ConversionWarning>()
                    val chunkRules = mutableListOf<String>()
                    var chunkSuccess = 0
                    var chunkFail = 0

                    for (stmt in chunk) {
                        try {
                            val result = converter(stmt, sourceDialect, targetDialect)
                            chunkResults.add(result.convertedSql)
                            chunkWarnings.addAll(result.warnings)
                            chunkRules.addAll(result.appliedRules)

                            if (result.warnings.none { it.severity == WarningSeverity.ERROR }) {
                                chunkSuccess++
                            } else {
                                chunkFail++
                            }
                        } catch (e: Exception) {
                            chunkFail++
                            chunkResults.add("/* 변환 실패: ${e.message} */\n$stmt")
                            chunkWarnings.add(ConversionWarning(
                                type = WarningType.MANUAL_REVIEW_NEEDED,
                                message = "문장 변환 실패: ${e.message}",
                                severity = WarningSeverity.ERROR
                            ))
                        }

                        val processed = processedCount.incrementAndGet()
                        progressCallback?.onProgress(processed, statements.size, null)
                    }

                    ChunkResult(
                        index = chunkIndex,
                        results = chunkResults,
                        warnings = chunkWarnings,
                        rules = chunkRules,
                        successCount = chunkSuccess,
                        failCount = chunkFail
                    )
                }
            }

            // 결과 수집 (순서 유지)
            val chunkResults = futures.map { it.get(5, TimeUnit.MINUTES) }
                .sortedBy { it.index }

            val allResults = chunkResults.flatMap { it.results }
            val allWarnings = chunkResults.flatMap { it.warnings }
            val allRules = chunkResults.flatMap { it.rules }.distinct()
            val totalSuccess = chunkResults.sumOf { it.successCount }
            val totalFail = chunkResults.sumOf { it.failCount }

            return ProcessingResult(
                convertedSql = allResults.joinToString(";\n\n") + if (allResults.isNotEmpty()) ";" else "",
                totalStatements = statements.size,
                successfulStatements = totalSuccess,
                failedStatements = totalFail,
                warnings = allWarnings,
                appliedRules = allRules + listOf("청크 병렬 처리 적용 (${chunks.size}개 청크, ${THREAD_COUNT}개 스레드)"),
                processingTimeMs = System.currentTimeMillis() - startTime,
                chunksProcessed = chunks.size,
                validation = null
            )
        } finally {
            executor.shutdown()
        }
    }

    /**
     * 청크 처리 결과
     */
    private data class ChunkResult(
        val index: Int,
        val results: List<String>,
        val warnings: List<ConversionWarning>,
        val rules: List<String>,
        val successCount: Int,
        val failCount: Int
    )

    /**
     * 주석만 있는 문장인지 확인
     */
    private fun isCommentOnly(sql: String): Boolean {
        val trimmed = sql.trim()
        return trimmed.startsWith("--") ||
                (trimmed.startsWith("/*") && trimmed.endsWith("*/") && !trimmed.contains('\n'))
    }

    /**
     * SQL 문장 수 추정 (빠른 카운트)
     */
    fun estimateStatementCount(sql: String): Int {
        var count = 0
        var inString = false
        var prevChar: Char? = null

        for (char in sql) {
            when {
                char == '\'' && prevChar != '\'' -> inString = !inString
                char == ';' && !inString -> count++
            }
            prevChar = char
        }

        return count.coerceAtLeast(1)
    }

    /**
     * 메모리 사용량 추정 (bytes)
     */
    fun estimateMemoryUsage(sql: String): Long {
        // SQL 문자열 크기 * 3 (원본 + 변환 결과 + 작업 버퍼)
        return sql.length.toLong() * 2 * 3
    }

    /**
     * 처리 전략 결정
     */
    fun determineStrategy(sql: String): ProcessingStrategy {
        val length = sql.length
        val estimatedStatements = estimateStatementCount(sql)

        return when {
            length < 10_000 -> ProcessingStrategy.SEQUENTIAL_SMALL
            length < LARGE_SQL_THRESHOLD -> ProcessingStrategy.SEQUENTIAL_MEDIUM
            estimatedStatements < 100 -> ProcessingStrategy.CHUNKED_SMALL
            estimatedStatements < 1000 -> ProcessingStrategy.CHUNKED_MEDIUM
            else -> ProcessingStrategy.CHUNKED_LARGE
        }
    }

    /**
     * 처리 전략
     */
    enum class ProcessingStrategy(val chunkSize: Int, val useParallel: Boolean) {
        SEQUENTIAL_SMALL(Int.MAX_VALUE, false),
        SEQUENTIAL_MEDIUM(Int.MAX_VALUE, false),
        CHUNKED_SMALL(30, false),
        CHUNKED_MEDIUM(50, true),
        CHUNKED_LARGE(100, true)
    }
}
