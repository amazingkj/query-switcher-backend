package com.sqlswitcher.parser.error

import net.sf.jsqlparser.JSQLParserException
import org.springframework.stereotype.Service

/**
 * Service to handle SQL parsing errors and provide user-friendly messages
 */
@Service
class SqlErrorHandler {
    
    private val messageBuilder = SqlErrorMessageBuilder()
    
    fun handleParsingError(exception: JSQLParserException, sql: String): SqlParseException {
        val errorType = determineErrorType(exception)
        val location = extractErrorLocation(exception, sql)
        val suggestions = generateSuggestions(exception, sql, errorType)
        
        return SqlParseException(
            message = exception.message ?: "Unknown parsing error",
            sql = sql,
            errorType = errorType,
            line = location.first,
            column = location.second,
            suggestions = suggestions,
            cause = exception
        )
    }
    
    fun handleUnexpectedError(exception: Exception, sql: String): SqlParseException {
        return SqlParseException(
            message = "Unexpected error occurred: ${exception.message}",
            sql = sql,
            errorType = SqlErrorType.UNKNOWN_ERROR,
            suggestions = listOf(
                "SQL을 단순화해보세요",
                "다른 SQL 도구로 문법을 확인해보세요",
                "문제가 지속되면 관리자에게 문의해주세요"
            ),
            cause = exception
        )
    }
    
    fun getUserFriendlyMessage(exception: SqlParseException): String {
        return messageBuilder.buildErrorMessage(exception)
    }
    
    private fun determineErrorType(exception: JSQLParserException): SqlErrorType {
        val message = exception.message?.lowercase() ?: ""
        
        return when {
            message.contains("syntax") || message.contains("parse") -> SqlErrorType.SYNTAX_ERROR
            message.contains("unsupported") || message.contains("not supported") -> SqlErrorType.UNSUPPORTED_FEATURE
            message.contains("identifier") || message.contains("table") || message.contains("column") -> SqlErrorType.INVALID_IDENTIFIER
            message.contains("missing") || message.contains("expected") -> SqlErrorType.MISSING_KEYWORD
            message.contains("function") -> SqlErrorType.INVALID_FUNCTION
            message.contains("type") || message.contains("mismatch") -> SqlErrorType.TYPE_MISMATCH
            else -> SqlErrorType.SYNTAX_ERROR
        }
    }
    
    private fun extractErrorLocation(exception: JSQLParserException, sql: String): Pair<Int?, Int?> {
        val message = exception.message ?: ""
        
        // Try to extract line and column from error message
        val lineMatch = Regex("line (\\d+)").find(message)
        val columnMatch = Regex("column (\\d+)").find(message)
        
        val line = lineMatch?.groupValues?.get(1)?.toIntOrNull()
        val column = columnMatch?.groupValues?.get(1)?.toIntOrNull()
        
        return Pair(line, column)
    }
    
    private fun generateSuggestions(exception: JSQLParserException, sql: String, errorType: SqlErrorType): List<String> {
        val suggestions = mutableListOf<String>()
        
        when (errorType) {
            SqlErrorType.SYNTAX_ERROR -> {
                suggestions.addAll(generateSyntaxErrorSuggestions(sql))
            }
            SqlErrorType.UNSUPPORTED_FEATURE -> {
                suggestions.addAll(generateUnsupportedFeatureSuggestions(sql))
            }
            SqlErrorType.INVALID_IDENTIFIER -> {
                suggestions.addAll(generateInvalidIdentifierSuggestions(sql))
            }
            SqlErrorType.MISSING_KEYWORD -> {
                suggestions.addAll(generateMissingKeywordSuggestions(sql))
            }
            SqlErrorType.INVALID_FUNCTION -> {
                suggestions.addAll(generateInvalidFunctionSuggestions(sql))
            }
            SqlErrorType.TYPE_MISMATCH -> {
                suggestions.addAll(generateTypeMismatchSuggestions(sql))
            }
            SqlErrorType.UNKNOWN_ERROR -> {
                suggestions.addAll(generateUnknownErrorSuggestions(sql))
            }
        }
        
        return suggestions
    }
    
    private fun generateSyntaxErrorSuggestions(sql: String): List<String> {
        val suggestions = mutableListOf<String>()
        
        // Check for common syntax issues
        if (sql.count { it == '(' } != sql.count { it == ')' }) {
            suggestions.add("괄호가 올바르게 닫혔는지 확인해주세요")
        }
        
        if (sql.count { it == '\'' } % 2 != 0) {
            suggestions.add("작은따옴표가 올바르게 닫혔는지 확인해주세요")
        }
        
        if (sql.count { it == '"' } % 2 != 0) {
            suggestions.add("큰따옴표가 올바르게 닫혔는지 확인해주세요")
        }
        
        if (sql.contains("SELECT") && !sql.contains("FROM")) {
            suggestions.add("SELECT 문에는 FROM 절이 필요합니다")
        }
        
        if (sql.contains("INSERT") && !sql.contains("INTO")) {
            suggestions.add("INSERT 문에는 INTO 키워드가 필요합니다")
        }
        
        if (sql.contains("UPDATE") && !sql.contains("SET")) {
            suggestions.add("UPDATE 문에는 SET 절이 필요합니다")
        }
        
        if (sql.contains("DELETE") && !sql.contains("FROM")) {
            suggestions.add("DELETE 문에는 FROM 절이 필요합니다")
        }
        
        return suggestions
    }
    
    private fun generateUnsupportedFeatureSuggestions(sql: String): List<String> {
        return listOf(
            "지원되는 SQL 기능만 사용해주세요",
            "복잡한 쿼리는 단순화해보세요",
            "데이터베이스별 문법 차이를 확인해주세요"
        )
    }
    
    private fun generateInvalidIdentifierSuggestions(sql: String): List<String> {
        return listOf(
            "테이블명과 컬럼명을 확인해주세요",
            "예약어는 백틱(`) 또는 큰따옴표(\")로 감싸주세요",
            "대소문자를 정확히 입력해주세요"
        )
    }
    
    private fun generateMissingKeywordSuggestions(sql: String): List<String> {
        return listOf(
            "필수 키워드가 누락되었습니다",
            "SQL 문법 가이드를 참고해주세요",
            "예제 쿼리와 비교해보세요"
        )
    }
    
    private fun generateInvalidFunctionSuggestions(sql: String): List<String> {
        return listOf(
            "함수명을 확인해주세요",
            "함수의 매개변수를 확인해주세요",
            "데이터베이스별 함수 지원 여부를 확인해주세요"
        )
    }
    
    private fun generateTypeMismatchSuggestions(sql: String): List<String> {
        return listOf(
            "데이터 타입을 확인해주세요",
            "암시적 타입 변환이 가능한지 확인해주세요",
            "명시적 타입 변환을 사용해보세요"
        )
    }
    
    private fun generateUnknownErrorSuggestions(sql: String): List<String> {
        return listOf(
            "SQL을 단순화해보세요",
            "다른 SQL 도구로 문법을 확인해보세요",
            "문제가 지속되면 관리자에게 문의해주세요"
        )
    }
}
