package com.sqlswitcher.converter.error

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * SQL 변환 에러 정보
 */
data class ConversionError(
    val type: ErrorType,
    val message: String,
    val lineNumber: Int? = null,
    val columnNumber: Int? = null,
    val sqlFragment: String? = null,
    val suggestion: String? = null,
    val recoverable: Boolean = false
)

/**
 * 에러 타입
 */
enum class ErrorType {
    SYNTAX_ERROR,           // SQL 구문 오류
    UNSUPPORTED_FEATURE,    // 지원하지 않는 기능
    PARSING_ERROR,          // 파싱 실패
    CONVERSION_ERROR,       // 변환 중 오류
    VALIDATION_ERROR,       // 검증 실패
    INTERNAL_ERROR          // 내부 오류
}

/**
 * 변환 에러 처리 및 복구 서비스
 */
object ConversionErrorHandler {

    /**
     * 예외에서 ConversionError 추출
     */
    fun extractError(exception: Exception, sql: String): ConversionError {
        val message = exception.message ?: "Unknown error"

        // 파싱 에러 패턴 매칭
        val lineMatch = Regex("""line\s*:?\s*(\d+)""", RegexOption.IGNORE_CASE).find(message)
        val columnMatch = Regex("""column\s*:?\s*(\d+)""", RegexOption.IGNORE_CASE).find(message)
        val positionMatch = Regex("""position\s*:?\s*(\d+)""", RegexOption.IGNORE_CASE).find(message)

        val lineNumber = lineMatch?.groupValues?.get(1)?.toIntOrNull()
        var columnNumber = columnMatch?.groupValues?.get(1)?.toIntOrNull()
        val position = positionMatch?.groupValues?.get(1)?.toIntOrNull()

        // position이 있으면 라인/컬럼으로 변환
        if (position != null && lineNumber == null) {
            val (line, col) = positionToLineColumn(sql, position)
            return ConversionError(
                type = classifyError(exception),
                message = message,
                lineNumber = line,
                columnNumber = col,
                sqlFragment = extractSqlFragment(sql, position),
                suggestion = generateSuggestion(exception, sql),
                recoverable = isRecoverable(exception)
            )
        }

        return ConversionError(
            type = classifyError(exception),
            message = message,
            lineNumber = lineNumber,
            columnNumber = columnNumber,
            sqlFragment = extractSqlFragmentByLine(sql, lineNumber, columnNumber),
            suggestion = generateSuggestion(exception, sql),
            recoverable = isRecoverable(exception)
        )
    }

    /**
     * SQL 내 위치로 라인/컬럼 계산
     */
    fun positionToLineColumn(sql: String, position: Int): Pair<Int, Int> {
        if (position < 0 || position >= sql.length) {
            return Pair(1, 1)
        }

        var line = 1
        var lastNewlinePos = -1

        for (i in 0 until minOf(position, sql.length)) {
            if (sql[i] == '\n') {
                line++
                lastNewlinePos = i
            }
        }

        val column = position - lastNewlinePos
        return Pair(line, column)
    }

    /**
     * 위치 주변 SQL 조각 추출
     */
    private fun extractSqlFragment(sql: String, position: Int, contextLength: Int = 50): String {
        val start = maxOf(0, position - contextLength / 2)
        val end = minOf(sql.length, position + contextLength / 2)

        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < sql.length) "..." else ""
        val fragment = sql.substring(start, end)

        // 에러 위치 표시
        val relativePos = position - start
        val marker = " ".repeat(prefix.length + relativePos) + "^"

        return "$prefix$fragment$suffix\n$marker"
    }

    /**
     * 라인/컬럼으로 SQL 조각 추출
     */
    private fun extractSqlFragmentByLine(sql: String, lineNumber: Int?, columnNumber: Int?): String? {
        if (lineNumber == null) return null

        val lines = sql.lines()
        if (lineNumber < 1 || lineNumber > lines.size) return null

        val targetLine = lines[lineNumber - 1]

        return if (columnNumber != null && columnNumber > 0) {
            val marker = " ".repeat(minOf(columnNumber - 1, targetLine.length)) + "^"
            "$targetLine\n$marker"
        } else {
            targetLine
        }
    }

    /**
     * 예외 타입 분류
     */
    private fun classifyError(exception: Exception): ErrorType {
        val message = exception.message?.lowercase() ?: ""
        val className = exception::class.simpleName?.lowercase() ?: ""

        return when {
            message.contains("syntax") || className.contains("parse") -> ErrorType.SYNTAX_ERROR
            message.contains("unsupported") || message.contains("not supported") -> ErrorType.UNSUPPORTED_FEATURE
            message.contains("unexpected") || message.contains("invalid") -> ErrorType.PARSING_ERROR
            message.contains("convert") || message.contains("transform") -> ErrorType.CONVERSION_ERROR
            else -> ErrorType.INTERNAL_ERROR
        }
    }

    /**
     * 복구 가능 여부 판단
     */
    private fun isRecoverable(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: ""

        // 복구 불가능한 에러 패턴
        val unrecoverablePatterns = listOf(
            "fatal", "critical", "corrupted", "out of memory"
        )

        return unrecoverablePatterns.none { message.contains(it) }
    }

    /**
     * 에러에 대한 제안 생성
     */
    private fun generateSuggestion(exception: Exception, sql: String): String {
        val message = exception.message?.lowercase() ?: ""
        val upperSql = sql.uppercase()

        return when {
            message.contains("unexpected token") -> "SQL 구문을 확인하세요. 누락된 키워드나 잘못된 문자가 있을 수 있습니다."
            message.contains("missing") && message.contains("from") -> "FROM 절이 필요합니다."
            message.contains("missing") && message.contains("where") -> "WHERE 조건을 확인하세요."
            message.contains("unterminated") -> "문자열이나 주석이 제대로 닫히지 않았습니다."
            message.contains("connect by") -> "CONNECT BY는 WITH RECURSIVE로 수동 변환이 필요할 수 있습니다."
            message.contains("pivot") -> "PIVOT 구문을 CASE WHEN으로 수동 변환해보세요."
            upperSql.contains("CONNECT BY") -> "계층적 쿼리는 WITH RECURSIVE CTE로 변환이 필요합니다."
            upperSql.contains("PIVOT") || upperSql.contains("UNPIVOT") -> "PIVOT/UNPIVOT은 조건부 집계나 UNION ALL로 변환해보세요."
            else -> "SQL 구문을 확인하고 지원되는 기능인지 확인하세요."
        }
    }

    /**
     * 부분 변환 시도
     * 전체 SQL이 실패하면 구문 단위로 분리하여 개별 변환 시도
     */
    fun attemptPartialConversion(
        sql: String,
        conversionFunction: (String) -> String,
        warnings: MutableList<ConversionWarning>
    ): PartialConversionResult {
        // 먼저 전체 변환 시도
        try {
            val converted = conversionFunction(sql)
            return PartialConversionResult(
                success = true,
                convertedSql = converted,
                failedStatements = emptyList()
            )
        } catch (e: Exception) {
            // 전체 변환 실패 시 부분 변환 시도
        }

        // SQL을 구문별로 분리
        val statements = splitSqlStatements(sql)

        if (statements.size <= 1) {
            // 단일 구문이면 부분 변환 불가
            return PartialConversionResult(
                success = false,
                convertedSql = sql,
                failedStatements = listOf(FailedStatement(sql, "변환 실패"))
            )
        }

        val convertedParts = mutableListOf<String>()
        val failedParts = mutableListOf<FailedStatement>()

        for (statement in statements) {
            try {
                val converted = conversionFunction(statement)
                convertedParts.add(converted)
            } catch (e: Exception) {
                // 개별 구문 변환 실패
                convertedParts.add("-- [CONVERSION FAILED] ${e.message}\n$statement")
                failedParts.add(FailedStatement(statement, e.message ?: "Unknown error"))
            }
        }

        val hasAnySuccess = convertedParts.any { !it.startsWith("-- [CONVERSION FAILED]") }

        if (failedParts.isNotEmpty()) {
            warnings.add(ConversionWarning(
                type = WarningType.PARTIAL_SUPPORT,
                message = "${failedParts.size}개 구문의 변환에 실패했습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "실패한 구문을 수동으로 확인하세요."
            ))
        }

        return PartialConversionResult(
            success = hasAnySuccess,
            convertedSql = convertedParts.joinToString(";\n"),
            failedStatements = failedParts
        )
    }

    /**
     * SQL을 구문 단위로 분리
     */
    private fun splitSqlStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        var current = StringBuilder()
        var inString = false
        var stringChar = ' '
        var i = 0

        while (i < sql.length) {
            val char = sql[i]

            when {
                !inString && (char == '\'' || char == '"') -> {
                    inString = true
                    stringChar = char
                    current.append(char)
                }
                inString && char == stringChar -> {
                    if (i + 1 < sql.length && sql[i + 1] == stringChar) {
                        current.append(char)
                        current.append(sql[i + 1])
                        i++
                    } else {
                        inString = false
                        current.append(char)
                    }
                }
                !inString && char == ';' -> {
                    val stmt = current.toString().trim()
                    if (stmt.isNotEmpty()) {
                        statements.add(stmt)
                    }
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
            i++
        }

        // 마지막 구문 처리
        val lastStmt = current.toString().trim()
        if (lastStmt.isNotEmpty()) {
            statements.add(lastStmt)
        }

        return statements
    }

    /**
     * ConversionError를 ConversionWarning으로 변환
     */
    fun errorToWarning(error: ConversionError): ConversionWarning {
        return ConversionWarning(
            type = when (error.type) {
                ErrorType.SYNTAX_ERROR -> WarningType.SYNTAX_DIFFERENCE
                ErrorType.UNSUPPORTED_FEATURE -> WarningType.UNSUPPORTED_FUNCTION
                ErrorType.PARSING_ERROR -> WarningType.SYNTAX_DIFFERENCE
                ErrorType.VALIDATION_ERROR -> WarningType.DATA_TYPE_MISMATCH
                else -> WarningType.MANUAL_REVIEW_NEEDED
            },
            message = buildString {
                append(error.message)
                if (error.lineNumber != null) {
                    append(" (라인 ${error.lineNumber}")
                    if (error.columnNumber != null) {
                        append(", 컬럼 ${error.columnNumber}")
                    }
                    append(")")
                }
            },
            severity = if (error.recoverable) WarningSeverity.WARNING else WarningSeverity.ERROR,
            suggestion = error.suggestion,
            lineNumber = error.lineNumber,
            columnNumber = error.columnNumber
        )
    }
}

/**
 * 부분 변환 결과
 */
data class PartialConversionResult(
    val success: Boolean,
    val convertedSql: String,
    val failedStatements: List<FailedStatement>
)

/**
 * 실패한 구문 정보
 */
data class FailedStatement(
    val sql: String,
    val errorMessage: String
)
