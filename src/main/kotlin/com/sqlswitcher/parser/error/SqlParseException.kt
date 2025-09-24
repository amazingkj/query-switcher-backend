package com.sqlswitcher.parser.error

/**
 * Custom exception for SQL parsing errors with detailed information
 */
class SqlParseException(
    message: String,
    val sql: String,
    val errorType: SqlErrorType,
    val line: Int? = null,
    val column: Int? = null,
    val suggestions: List<String> = emptyList(),
    cause: Throwable? = null
) : Exception(message, cause)

enum class SqlErrorType {
    SYNTAX_ERROR,           // Invalid SQL syntax
    UNSUPPORTED_FEATURE,    // Feature not supported by parser
    INVALID_IDENTIFIER,     // Invalid table/column name
    MISSING_KEYWORD,        // Missing required keyword
    INVALID_FUNCTION,       // Invalid function usage
    TYPE_MISMATCH,          // Data type mismatch
    UNKNOWN_ERROR          // Unknown parsing error
}

/**
 * Builder for creating user-friendly error messages
 */
class SqlErrorMessageBuilder {
    
    fun buildErrorMessage(exception: SqlParseException): String {
        val baseMessage = when (exception.errorType) {
            SqlErrorType.SYNTAX_ERROR -> "SQL 구문 오류가 발견되었습니다."
            SqlErrorType.UNSUPPORTED_FEATURE -> "지원되지 않는 SQL 기능이 사용되었습니다."
            SqlErrorType.INVALID_IDENTIFIER -> "잘못된 식별자(테이블명 또는 컬럼명)가 사용되었습니다."
            SqlErrorType.MISSING_KEYWORD -> "필수 키워드가 누락되었습니다."
            SqlErrorType.INVALID_FUNCTION -> "잘못된 함수 사용법입니다."
            SqlErrorType.TYPE_MISMATCH -> "데이터 타입이 일치하지 않습니다."
            SqlErrorType.UNKNOWN_ERROR -> "알 수 없는 오류가 발생했습니다."
        }
        
        val locationInfo = buildLocationInfo(exception)
        val suggestionInfo = buildSuggestionInfo(exception)
        
        return buildString {
            appendLine(baseMessage)
            appendLine()
            appendLine("오류 상세:")
            appendLine(exception.message)
            if (locationInfo.isNotEmpty()) {
                appendLine()
                appendLine("위치:")
                appendLine(locationInfo)
            }
            if (suggestionInfo.isNotEmpty()) {
                appendLine()
                appendLine("제안:")
                appendLine(suggestionInfo)
            }
        }
    }
    
    private fun buildLocationInfo(exception: SqlParseException): String {
        return when {
            exception.line != null && exception.column != null -> 
                "라인 ${exception.line}, 컬럼 ${exception.column}"
            exception.line != null -> 
                "라인 ${exception.line}"
            else -> ""
        }
    }
    
    private fun buildSuggestionInfo(exception: SqlParseException): String {
        return if (exception.suggestions.isNotEmpty()) {
            exception.suggestions.joinToString("\n- ", "- ")
        } else {
            generateDefaultSuggestions(exception)
        }
    }
    
    private fun generateDefaultSuggestions(exception: SqlParseException): String {
        return when (exception.errorType) {
            SqlErrorType.SYNTAX_ERROR -> {
                """
                - SQL 구문을 다시 확인해주세요
                - 키워드 철자를 확인해주세요
                - 괄호나 따옴표가 올바르게 닫혔는지 확인해주세요
                """.trimIndent()
            }
            SqlErrorType.UNSUPPORTED_FEATURE -> {
                """
                - 지원되는 SQL 기능만 사용해주세요
                - 복잡한 쿼리는 단순화해보세요
                - 데이터베이스별 문법 차이를 확인해주세요
                """.trimIndent()
            }
            SqlErrorType.INVALID_IDENTIFIER -> {
                """
                - 테이블명과 컬럼명을 확인해주세요
                - 예약어는 백틱(`) 또는 큰따옴표(")로 감싸주세요
                - 대소문자를 정확히 입력해주세요
                """.trimIndent()
            }
            SqlErrorType.MISSING_KEYWORD -> {
                """
                - 필수 키워드가 누락되었습니다
                - SQL 문법 가이드를 참고해주세요
                - 예제 쿼리와 비교해보세요
                """.trimIndent()
            }
            SqlErrorType.INVALID_FUNCTION -> {
                """
                - 함수명을 확인해주세요
                - 함수의 매개변수를 확인해주세요
                - 데이터베이스별 함수 지원 여부를 확인해주세요
                """.trimIndent()
            }
            SqlErrorType.TYPE_MISMATCH -> {
                """
                - 데이터 타입을 확인해주세요
                - 암시적 타입 변환이 가능한지 확인해주세요
                - 명시적 타입 변환을 사용해보세요
                """.trimIndent()
            }
            SqlErrorType.UNKNOWN_ERROR -> {
                """
                - SQL을 단순화해보세요
                - 다른 SQL 도구로 문법을 확인해보세요
                - 문제가 지속되면 관리자에게 문의해주세요
                """.trimIndent()
            }
        }
    }
}
