package com.sqlswitcher.service

import com.sqlswitcher.converter.DialectType
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import org.springframework.stereotype.Service

/**
 * SQL 문법 검증 서비스 (빠른 검증)
 */
@Service
class SqlValidationService {

    /**
     * SQL 문법 검증 결과
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationError> = emptyList(),
        val warnings: List<String> = emptyList(),
        val parsedStatementType: String? = null
    )

    data class ValidationError(
        val message: String,
        val line: Int? = null,
        val column: Int? = null,
        val suggestion: String? = null
    )

    /**
     * SQL 문법 검증 (JSQLParser 기반)
     */
    fun validateSyntax(sql: String, dialect: DialectType): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<String>()

        // 빈 SQL 체크
        if (sql.isBlank()) {
            return ValidationResult(
                isValid = false,
                errors = listOf(ValidationError("SQL이 비어있습니다."))
            )
        }

        // SQL 전처리: 주석 제거 및 정리
        val cleanedSql = preprocessSql(sql)

        // 빈 SQL이 된 경우 (주석만 있었던 경우)
        if (cleanedSql.isBlank()) {
            return ValidationResult(
                isValid = true,
                warnings = listOf("SQL에 실행 가능한 문장이 없습니다 (주석만 포함)."),
                parsedStatementType = "Comment"
            )
        }

        // COMMENT ON 문인지 확인 (JSQLParser가 지원하지 않음)
        if (isCommentStatement(cleanedSql)) {
            val commentWarnings = validateCommentStatement(cleanedSql)
            return ValidationResult(
                isValid = commentWarnings.isEmpty(),
                warnings = if (commentWarnings.isEmpty())
                    listOf("COMMENT 문은 JSQLParser에서 완전히 지원되지 않지만, 기본 구조는 올바릅니다.")
                    else commentWarnings,
                parsedStatementType = "Comment",
                errors = if (commentWarnings.isNotEmpty())
                    listOf(ValidationError(commentWarnings.first()))
                    else emptyList()
            )
        }

        // 복수 문장 처리: 세미콜론으로 분리하여 각각 검증
        val statements = splitStatements(cleanedSql)

        return try {
            var lastStatementType: String? = null

            for (stmt in statements) {
                val trimmedStmt = stmt.trim()
                if (trimmedStmt.isBlank()) continue

                // COMMENT ON 문 건너뛰기
                if (isCommentStatement(trimmedStmt)) {
                    lastStatementType = "Comment"
                    continue
                }

                // JSQLParser로 파싱 시도
                val statement = CCJSqlParserUtil.parse(trimmedStmt)
                lastStatementType = statement.javaClass.simpleName
            }

            // 방언별 키워드/함수 검증
            val dialectWarnings = validateDialectSpecifics(sql, dialect)
            warnings.addAll(dialectWarnings)

            ValidationResult(
                isValid = true,
                warnings = warnings,
                parsedStatementType = lastStatementType
            )
        } catch (e: Exception) {
            // 파싱 오류 분석
            val errorMessage = e.message ?: "알 수 없는 파싱 오류"
            val (line, column) = extractLineAndColumn(errorMessage)

            errors.add(ValidationError(
                message = cleanErrorMessage(errorMessage),
                line = line,
                column = column,
                suggestion = getSuggestion(errorMessage, dialect)
            ))

            ValidationResult(
                isValid = false,
                errors = errors
            )
        }
    }

    /**
     * SQL 전처리: 주석 제거
     */
    private fun preprocessSql(sql: String): String {
        var result = sql

        // 블록 주석 제거 (/* ... */)
        result = result.replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")

        // 라인 주석 제거 (-- ...)
        result = result.replace(Regex("--[^\r\n]*"), " ")

        // 연속 공백 정리
        result = result.replace(Regex("\\s+"), " ")

        return result.trim()
    }

    /**
     * COMMENT ON 문인지 확인
     */
    private fun isCommentStatement(sql: String): Boolean {
        val upperSql = sql.trim().uppercase()
        return upperSql.startsWith("COMMENT ON") ||
               upperSql.startsWith("COMMENT ON TABLE") ||
               upperSql.startsWith("COMMENT ON COLUMN")
    }

    /**
     * COMMENT 문 기본 검증
     */
    private fun validateCommentStatement(sql: String): List<String> {
        val upperSql = sql.trim().uppercase()
        val warnings = mutableListOf<String>()

        // 기본 구조 검증: COMMENT ON (TABLE|COLUMN) ... IS '...'
        val commentPattern = Regex(
            "^COMMENT\\s+ON\\s+(TABLE|COLUMN)\\s+[\\w.]+\\s+IS\\s+['\"].*['\"]\\s*;?$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        if (!commentPattern.matches(sql.trim())) {
            // 더 유연한 검증
            if (!upperSql.contains(" IS ")) {
                warnings.add("COMMENT 문에 IS 키워드가 필요합니다. 예: COMMENT ON TABLE table_name IS 'description'")
            }
        }

        return warnings
    }

    /**
     * 세미콜론으로 문장 분리 (문자열 내부 세미콜론 제외)
     */
    private fun splitStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        var current = StringBuilder()
        var inString = false
        var stringChar = ' '

        for (i in sql.indices) {
            val c = sql[i]

            when {
                // 문자열 시작/종료 감지
                (c == '\'' || c == '"') && (i == 0 || sql[i - 1] != '\\') -> {
                    if (!inString) {
                        inString = true
                        stringChar = c
                    } else if (c == stringChar) {
                        inString = false
                    }
                    current.append(c)
                }
                // 세미콜론 (문자열 밖)
                c == ';' && !inString -> {
                    val stmt = current.toString().trim()
                    if (stmt.isNotBlank()) {
                        statements.add(stmt)
                    }
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
        }

        // 마지막 문장 (세미콜론 없는 경우)
        val lastStmt = current.toString().trim()
        if (lastStmt.isNotBlank()) {
            statements.add(lastStmt)
        }

        return statements
    }

    /**
     * 방언별 특수 검증
     */
    private fun validateDialectSpecifics(sql: String, dialect: DialectType): List<String> {
        val warnings = mutableListOf<String>()
        val upperSql = sql.uppercase()

        when (dialect) {
            DialectType.MYSQL -> {
                // MySQL에서 지원하지 않는 기능 체크
                if (upperSql.contains("CONNECT BY")) {
                    warnings.add("MySQL은 CONNECT BY를 지원하지 않습니다. WITH RECURSIVE를 사용하세요.")
                }
                if (upperSql.contains("ROWNUM")) {
                    warnings.add("MySQL은 ROWNUM을 지원하지 않습니다. LIMIT을 사용하세요.")
                }
                if (Regex("\\bNVL\\s*\\(").containsMatchIn(upperSql)) {
                    warnings.add("MySQL은 NVL을 지원하지 않습니다. IFNULL 또는 COALESCE를 사용하세요.")
                }
            }
            DialectType.POSTGRESQL -> {
                // PostgreSQL에서 지원하지 않는 기능 체크
                if (upperSql.contains("CONNECT BY")) {
                    warnings.add("PostgreSQL은 CONNECT BY를 지원하지 않습니다. WITH RECURSIVE를 사용하세요.")
                }
                if (Regex("\\bDECODE\\s*\\(").containsMatchIn(upperSql)) {
                    warnings.add("PostgreSQL은 DECODE를 지원하지 않습니다. CASE WHEN을 사용하세요.")
                }
                if (Regex("\\bNVL2\\s*\\(").containsMatchIn(upperSql)) {
                    warnings.add("PostgreSQL은 NVL2를 지원하지 않습니다. CASE WHEN을 사용하세요.")
                }
            }
            DialectType.ORACLE -> {
                // Oracle에서 지원하지 않는 기능 체크
                if (Regex("\\bLIMIT\\s+\\d+").containsMatchIn(upperSql)) {
                    warnings.add("Oracle은 LIMIT을 지원하지 않습니다. ROWNUM 또는 FETCH FIRST를 사용하세요.")
                }
                if (Regex("\\bIFNULL\\s*\\(").containsMatchIn(upperSql)) {
                    warnings.add("Oracle은 IFNULL을 지원하지 않습니다. NVL을 사용하세요.")
                }
            }
        }

        return warnings
    }

    /**
     * 오류 메시지에서 라인/컬럼 추출
     */
    private fun extractLineAndColumn(message: String): Pair<Int?, Int?> {
        // "at line X, column Y" 패턴 추출
        val pattern = Regex("line\\s+(\\d+),\\s*column\\s+(\\d+)", RegexOption.IGNORE_CASE)
        val match = pattern.find(message)

        return if (match != null) {
            val line = match.groupValues[1].toIntOrNull()
            val column = match.groupValues[2].toIntOrNull()
            Pair(line, column)
        } else {
            Pair(null, null)
        }
    }

    /**
     * 오류 메시지 정리
     */
    private fun cleanErrorMessage(message: String): String {
        return message
            .replace(Regex("net\\.sf\\.jsqlparser\\.parser\\.ParseException:\\s*"), "")
            .replace(Regex("Encountered unexpected token:.*Was expecting"), "예상치 못한 토큰이 발견되었습니다.")
            .take(200) // 최대 200자
    }

    /**
     * 오류에 대한 제안 생성
     */
    private fun getSuggestion(errorMessage: String, dialect: DialectType): String? {
        val upperMessage = errorMessage.uppercase()

        return when {
            "UNEXPECTED TOKEN" in upperMessage && "LIMIT" in upperMessage ->
                "LIMIT 절의 위치나 문법을 확인하세요."
            "UNEXPECTED TOKEN" in upperMessage && "PARTITION" in upperMessage ->
                "파티션 구문이 대상 DB에서 지원되는지 확인하세요."
            "UNEXPECTED TOKEN" in upperMessage ->
                "SQL 문법을 확인하세요. 누락된 키워드나 잘못된 순서가 있을 수 있습니다."
            else -> null
        }
    }
}