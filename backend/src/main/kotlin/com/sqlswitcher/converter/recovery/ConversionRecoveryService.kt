package com.sqlswitcher.converter.recovery

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * 변환 에러 복구 서비스
 *
 * SQL 변환 중 오류가 발생해도 가능한 부분은 계속 변환하고,
 * 실패한 부분은 원본을 유지하거나 주석으로 표시
 *
 * 기능:
 * - 다중 SQL 문 분리 및 개별 변환
 * - 변환 실패 시 원본 유지
 * - 상세한 에러 리포팅
 * - 부분 성공/실패 통계
 * - 고급 복구 전략 적용 (AdvancedRecoveryStrategies 통합)
 */
object ConversionRecoveryService {

    /**
     * 고급 복구 사용 여부
     */
    var enableAdvancedRecovery: Boolean = true

    /**
     * SQL 문 분리 패턴
     */
    private val STATEMENT_SEPARATOR = Regex(
        """;(?=\s*(?:CREATE|ALTER|DROP|INSERT|UPDATE|DELETE|SELECT|MERGE|GRANT|REVOKE|COMMENT|WITH|DECLARE|BEGIN|--|\n|$))""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    /**
     * 블록 문 패턴 (BEGIN...END 등)
     */
    private val BLOCK_PATTERN = Regex(
        """(CREATE\s+(?:OR\s+REPLACE\s+)?(?:PROCEDURE|FUNCTION|TRIGGER|PACKAGE)[\s\S]+?END\s*(?:\w+)?\s*;)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * 변환 결과
     */
    data class RecoveryResult(
        /** 최종 변환 결과 */
        val convertedSql: String,
        /** 전체 문장 수 */
        val totalStatements: Int,
        /** 성공한 문장 수 */
        val successfulStatements: Int,
        /** 실패한 문장 수 */
        val failedStatements: Int,
        /** 부분 변환된 문장 수 */
        val partialStatements: Int,
        /** 경고 목록 */
        val warnings: List<ConversionWarning>,
        /** 적용된 규칙 목록 */
        val appliedRules: List<String>,
        /** 개별 문장 결과 */
        val statementResults: List<StatementResult>
    ) {
        val successRate: Double
            get() = if (totalStatements > 0) successfulStatements.toDouble() / totalStatements else 0.0

        val isFullySuccessful: Boolean
            get() = failedStatements == 0

        val isPartiallySuccessful: Boolean
            get() = successfulStatements > 0 && failedStatements > 0
    }

    /**
     * 개별 문장 변환 결과
     */
    data class StatementResult(
        /** 원본 SQL */
        val originalSql: String,
        /** 변환된 SQL */
        val convertedSql: String,
        /** 변환 상태 */
        val status: ConversionStatus,
        /** 에러 메시지 (실패 시) */
        val errorMessage: String? = null,
        /** 문장 유형 */
        val statementType: StatementType? = null
    )

    enum class ConversionStatus {
        SUCCESS,        // 완전 성공
        PARTIAL,        // 부분 성공 (일부 기능 미변환)
        FAILED,         // 변환 실패
        SKIPPED         // 변환 불필요 (주석 등)
    }

    enum class StatementType {
        DDL_CREATE,
        DDL_ALTER,
        DDL_DROP,
        DML_SELECT,
        DML_INSERT,
        DML_UPDATE,
        DML_DELETE,
        DML_MERGE,
        PLSQL_PROCEDURE,
        PLSQL_FUNCTION,
        PLSQL_TRIGGER,
        PLSQL_PACKAGE,
        DCL,
        COMMENT,
        OTHER
    }

    /**
     * 안전한 다중 SQL 변환
     *
     * 각 SQL 문을 개별적으로 변환하고, 실패 시 원본을 유지
     */
    fun convertWithRecovery(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        converterFn: (String, DialectType, DialectType, MutableList<ConversionWarning>, MutableList<String>) -> String
    ): RecoveryResult {
        val statements = splitStatements(sql)
        val results = mutableListOf<StatementResult>()
        val allWarnings = mutableListOf<ConversionWarning>()
        val allRules = mutableListOf<String>()

        var successCount = 0
        var failCount = 0
        var partialCount = 0

        for (statement in statements) {
            val trimmed = statement.trim()
            if (trimmed.isEmpty() || isComment(trimmed)) {
                results.add(StatementResult(
                    originalSql = trimmed,
                    convertedSql = trimmed,
                    status = ConversionStatus.SKIPPED,
                    statementType = StatementType.COMMENT
                ))
                continue
            }

            val statementType = detectStatementType(trimmed)
            val warnings = mutableListOf<ConversionWarning>()
            val rules = mutableListOf<String>()

            try {
                val converted = converterFn(trimmed, sourceDialect, targetDialect, warnings, rules)
                allWarnings.addAll(warnings)
                allRules.addAll(rules)

                val status = when {
                    warnings.any { it.severity == WarningSeverity.ERROR } -> {
                        failCount++
                        ConversionStatus.FAILED
                    }
                    warnings.any { it.severity == WarningSeverity.WARNING } -> {
                        partialCount++
                        ConversionStatus.PARTIAL
                    }
                    else -> {
                        successCount++
                        ConversionStatus.SUCCESS
                    }
                }

                results.add(StatementResult(
                    originalSql = trimmed,
                    convertedSql = converted,
                    status = status,
                    statementType = statementType
                ))
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error"

                // 고급 복구 전략 시도
                if (enableAdvancedRecovery) {
                    val recoveryResult = AdvancedRecoveryStrategies.attemptRecovery(
                        trimmed, e, targetDialect
                    ) { recoveredSql ->
                        converterFn(recoveredSql, sourceDialect, targetDialect, warnings, rules)
                    }

                    if (recoveryResult.success) {
                        allWarnings.addAll(recoveryResult.warnings)
                        allRules.addAll(rules)
                        allRules.add("고급 복구 전략 적용: ${recoveryResult.strategyUsed}")

                        val status = if (recoveryResult.confidence >= 0.8) {
                            successCount++
                            ConversionStatus.SUCCESS
                        } else {
                            partialCount++
                            ConversionStatus.PARTIAL
                        }

                        results.add(StatementResult(
                            originalSql = trimmed,
                            convertedSql = recoveryResult.finalSql,
                            status = status,
                            statementType = statementType
                        ))
                        continue
                    }
                }

                // 복구 실패 시 기존 로직
                failCount++
                allWarnings.add(ConversionWarning(
                    type = WarningType.MANUAL_REVIEW_NEEDED,
                    message = "변환 중 오류 발생: $errorMessage",
                    severity = WarningSeverity.ERROR,
                    suggestion = "이 구문은 수동 변환이 필요합니다."
                ))

                // 원본 유지 + 에러 주석 추가
                val errorComment = buildErrorComment(trimmed, errorMessage, targetDialect)
                results.add(StatementResult(
                    originalSql = trimmed,
                    convertedSql = errorComment,
                    status = ConversionStatus.FAILED,
                    errorMessage = errorMessage,
                    statementType = statementType
                ))
            }
        }

        // 결과 조합
        val finalSql = results.joinToString("\n\n") { it.convertedSql }

        return RecoveryResult(
            convertedSql = finalSql,
            totalStatements = statements.filter { it.trim().isNotEmpty() && !isComment(it.trim()) }.size,
            successfulStatements = successCount,
            failedStatements = failCount,
            partialStatements = partialCount,
            warnings = allWarnings,
            appliedRules = allRules,
            statementResults = results
        )
    }

    /**
     * 단일 문장 안전 변환
     */
    fun convertSingleWithRecovery(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        converterFn: (String, DialectType, DialectType, MutableList<ConversionWarning>, MutableList<String>) -> String
    ): StatementResult {
        val trimmed = sql.trim()
        val warnings = mutableListOf<ConversionWarning>()
        val rules = mutableListOf<String>()

        return try {
            val converted = converterFn(trimmed, sourceDialect, targetDialect, warnings, rules)

            val status = when {
                warnings.any { it.severity == WarningSeverity.ERROR } -> ConversionStatus.FAILED
                warnings.any { it.severity == WarningSeverity.WARNING } -> ConversionStatus.PARTIAL
                else -> ConversionStatus.SUCCESS
            }

            StatementResult(
                originalSql = trimmed,
                convertedSql = converted,
                status = status,
                statementType = detectStatementType(trimmed)
            )
        } catch (e: Exception) {
            // 고급 복구 전략 시도
            if (enableAdvancedRecovery) {
                val recoveryResult = AdvancedRecoveryStrategies.attemptRecovery(
                    trimmed, e, targetDialect
                ) { recoveredSql ->
                    converterFn(recoveredSql, sourceDialect, targetDialect, warnings, rules)
                }

                if (recoveryResult.success) {
                    val status = if (recoveryResult.confidence >= 0.8) {
                        ConversionStatus.SUCCESS
                    } else {
                        ConversionStatus.PARTIAL
                    }

                    return StatementResult(
                        originalSql = trimmed,
                        convertedSql = recoveryResult.finalSql,
                        status = status,
                        statementType = detectStatementType(trimmed)
                    )
                }
            }

            StatementResult(
                originalSql = trimmed,
                convertedSql = buildErrorComment(trimmed, e.message ?: "Unknown error", targetDialect),
                status = ConversionStatus.FAILED,
                errorMessage = e.message,
                statementType = detectStatementType(trimmed)
            )
        }
    }

    /**
     * SQL 문 분리
     */
    fun splitStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        var remaining = sql

        // 먼저 블록 문 추출 (BEGIN...END)
        val blocks = BLOCK_PATTERN.findAll(remaining)
        val blockRanges = blocks.map { it.range }.toList()

        // 블록 외부의 세미콜론으로 분리
        var lastEnd = 0
        for (match in STATEMENT_SEPARATOR.findAll(remaining)) {
            val pos = match.range.first

            // 블록 내부에 있는지 확인
            val inBlock = blockRanges.any { pos >= it.first && pos <= it.last }
            if (!inBlock) {
                val statement = remaining.substring(lastEnd, pos + 1).trim()
                if (statement.isNotEmpty()) {
                    statements.add(statement)
                }
                lastEnd = pos + 1
            }
        }

        // 마지막 부분
        if (lastEnd < remaining.length) {
            val lastStatement = remaining.substring(lastEnd).trim()
            if (lastStatement.isNotEmpty()) {
                statements.add(lastStatement)
            }
        }

        return if (statements.isEmpty() && sql.trim().isNotEmpty()) {
            listOf(sql.trim())
        } else {
            statements
        }
    }

    /**
     * 주석 여부 확인
     */
    private fun isComment(sql: String): Boolean {
        val trimmed = sql.trim()
        return trimmed.startsWith("--") || trimmed.startsWith("/*")
    }

    /**
     * 문장 유형 감지
     */
    private fun detectStatementType(sql: String): StatementType {
        val upper = sql.uppercase().trim()

        return when {
            upper.startsWith("CREATE OR REPLACE PACKAGE BODY") -> StatementType.PLSQL_PACKAGE
            upper.startsWith("CREATE OR REPLACE PACKAGE") -> StatementType.PLSQL_PACKAGE
            upper.startsWith("CREATE PACKAGE") -> StatementType.PLSQL_PACKAGE
            upper.startsWith("CREATE OR REPLACE PROCEDURE") -> StatementType.PLSQL_PROCEDURE
            upper.startsWith("CREATE PROCEDURE") -> StatementType.PLSQL_PROCEDURE
            upper.startsWith("CREATE OR REPLACE FUNCTION") -> StatementType.PLSQL_FUNCTION
            upper.startsWith("CREATE FUNCTION") -> StatementType.PLSQL_FUNCTION
            upper.startsWith("CREATE OR REPLACE TRIGGER") -> StatementType.PLSQL_TRIGGER
            upper.startsWith("CREATE TRIGGER") -> StatementType.PLSQL_TRIGGER
            upper.startsWith("CREATE") -> StatementType.DDL_CREATE
            upper.startsWith("ALTER") -> StatementType.DDL_ALTER
            upper.startsWith("DROP") -> StatementType.DDL_DROP
            upper.startsWith("SELECT") || upper.startsWith("WITH") -> StatementType.DML_SELECT
            upper.startsWith("INSERT") -> StatementType.DML_INSERT
            upper.startsWith("UPDATE") -> StatementType.DML_UPDATE
            upper.startsWith("DELETE") -> StatementType.DML_DELETE
            upper.startsWith("MERGE") -> StatementType.DML_MERGE
            upper.startsWith("GRANT") || upper.startsWith("REVOKE") -> StatementType.DCL
            upper.startsWith("COMMENT") -> StatementType.COMMENT
            upper.startsWith("--") || upper.startsWith("/*") -> StatementType.COMMENT
            else -> StatementType.OTHER
        }
    }

    /**
     * 에러 주석 생성
     */
    private fun buildErrorComment(originalSql: String, errorMessage: String, targetDialect: DialectType): String {
        val commentStart = if (targetDialect == DialectType.MYSQL) "/*" else "/*"
        val commentEnd = "*/"

        return """
$commentStart
 * CONVERSION ERROR
 * Error: $errorMessage
 * Original SQL (requires manual conversion):
 $commentEnd

-- Original:
${originalSql.lines().joinToString("\n") { "-- $it" }}

$commentStart TODO: Manually convert this statement $commentEnd
""".trim()
    }

    /**
     * 변환 보고서 생성
     */
    fun generateReport(result: RecoveryResult): String {
        val sb = StringBuilder()

        sb.appendLine("=" .repeat(60))
        sb.appendLine("SQL 변환 보고서")
        sb.appendLine("=".repeat(60))
        sb.appendLine()
        sb.appendLine("## 요약")
        sb.appendLine("- 전체 문장: ${result.totalStatements}개")
        sb.appendLine("- 성공: ${result.successfulStatements}개")
        sb.appendLine("- 부분 성공: ${result.partialStatements}개")
        sb.appendLine("- 실패: ${result.failedStatements}개")
        sb.appendLine("- 성공률: ${String.format("%.1f", result.successRate * 100)}%")
        sb.appendLine()

        if (result.appliedRules.isNotEmpty()) {
            sb.appendLine("## 적용된 변환 규칙")
            result.appliedRules.distinct().forEachIndexed { index, rule ->
                sb.appendLine("${index + 1}. $rule")
            }
            sb.appendLine()
        }

        if (result.warnings.isNotEmpty()) {
            sb.appendLine("## 경고 및 알림")
            result.warnings.groupBy { it.severity }.forEach { (severity, warnings) ->
                sb.appendLine("### $severity (${warnings.size}개)")
                warnings.forEach { warning ->
                    sb.appendLine("- ${warning.message}")
                    warning.suggestion?.let { sb.appendLine("  → 제안: $it") }
                }
            }
            sb.appendLine()
        }

        val failedResults = result.statementResults.filter { it.status == ConversionStatus.FAILED }
        if (failedResults.isNotEmpty()) {
            sb.appendLine("## 변환 실패 문장")
            failedResults.forEachIndexed { index, stmt ->
                sb.appendLine("### 실패 ${index + 1}")
                sb.appendLine("유형: ${stmt.statementType}")
                sb.appendLine("에러: ${stmt.errorMessage}")
                sb.appendLine("원본:")
                sb.appendLine("```sql")
                sb.appendLine(stmt.originalSql.take(500))
                if (stmt.originalSql.length > 500) sb.appendLine("... (생략)")
                sb.appendLine("```")
                sb.appendLine()
            }
        }

        sb.appendLine("=".repeat(60))

        return sb.toString()
    }

    /**
     * 부분 변환 결과 병합
     */
    fun mergeResults(results: List<RecoveryResult>): RecoveryResult {
        val allStatementResults = results.flatMap { it.statementResults }
        val allWarnings = results.flatMap { it.warnings }
        val allRules = results.flatMap { it.appliedRules }

        return RecoveryResult(
            convertedSql = allStatementResults.joinToString("\n\n") { it.convertedSql },
            totalStatements = results.sumOf { it.totalStatements },
            successfulStatements = results.sumOf { it.successfulStatements },
            failedStatements = results.sumOf { it.failedStatements },
            partialStatements = results.sumOf { it.partialStatements },
            warnings = allWarnings,
            appliedRules = allRules,
            statementResults = allStatementResults
        )
    }
}
