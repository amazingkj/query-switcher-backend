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

        return try {
            // JSQLParser로 파싱 시도
            val statement = CCJSqlParserUtil.parse(sql)
            val statementType = statement.javaClass.simpleName

            // 방언별 키워드/함수 검증
            val dialectWarnings = validateDialectSpecifics(sql, dialect)
            warnings.addAll(dialectWarnings)

            ValidationResult(
                isValid = true,
                warnings = warnings,
                parsedStatementType = statementType
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