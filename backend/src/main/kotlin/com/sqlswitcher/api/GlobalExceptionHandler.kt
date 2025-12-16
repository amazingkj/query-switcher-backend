package com.sqlswitcher.api

import com.sqlswitcher.parser.error.SqlParseException
import com.sqlswitcher.parser.error.ConversionException
import com.sqlswitcher.parser.error.ConversionErrorMessageBuilder
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Validation error: ${ex.message}")

        val errors = ex.bindingResult.fieldErrors.associate { fieldError ->
            fieldError.field to (fieldError.defaultMessage ?: "Invalid value")
        }

        val errorResponse = ErrorResponse(
            errorCode = "VALIDATION_ERROR",
            message = "Request validation failed",
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            details = errors.toString()
        )

        return ResponseEntity.badRequest().body(errorResponse)
    }

    @ExceptionHandler(ConversionException::class)
    fun handleConversionException(
        ex: ConversionException,
        request: WebRequest
    ): ResponseEntity<ConversionErrorResponse> {
        logger.warn("SQL conversion error: ${ex.errorCode.code} - ${ex.message}")

        val errorMessage = ConversionErrorMessageBuilder.buildUserFriendlyMessage(ex)

        val errorResponse = ConversionErrorResponse(
            errorCode = errorMessage.code,
            title = errorMessage.title,
            message = errorMessage.description,
            suggestions = errorMessage.suggestions,
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            technicalDetails = if (logger.isDebugEnabled) errorMessage.technicalDetails else null
        )

        return ResponseEntity.badRequest().body(errorResponse)
    }

    @ExceptionHandler(SqlParseException::class)
    fun handleSqlParseException(
        ex: SqlParseException,
        request: WebRequest
    ): ResponseEntity<SqlParseErrorResponse> {
        logger.warn("SQL parsing error: ${ex.message}")

        val errorResponse = SqlParseErrorResponse(
            errorCode = "SQL_PARSE_ERROR",
            errorType = ex.errorType.name,
            message = "SQL 파싱 오류: ${ex.message}",
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            location = if (ex.line != null || ex.column != null) {
                ErrorLocation(ex.line, ex.column)
            } else null,
            suggestions = ex.suggestions.ifEmpty {
                getDefaultSuggestions(ex.errorType)
            },
            sqlSnippet = extractRelevantSqlSnippet(ex.sql, ex.line, ex.column)
        )

        return ResponseEntity.badRequest().body(errorResponse)
    }

    private fun getDefaultSuggestions(errorType: com.sqlswitcher.parser.error.SqlErrorType): List<String> {
        return when (errorType) {
            com.sqlswitcher.parser.error.SqlErrorType.SYNTAX_ERROR -> listOf(
                "SQL 구문을 다시 확인해주세요",
                "괄호나 따옴표가 올바르게 닫혔는지 확인해주세요",
                "키워드 철자가 올바른지 확인해주세요"
            )
            com.sqlswitcher.parser.error.SqlErrorType.UNSUPPORTED_FEATURE -> listOf(
                "해당 기능은 현재 지원되지 않습니다",
                "쿼리를 단순화하여 다시 시도해주세요",
                "데이터베이스별 문법 차이를 확인해주세요"
            )
            com.sqlswitcher.parser.error.SqlErrorType.INVALID_IDENTIFIER -> listOf(
                "테이블명과 컬럼명을 확인해주세요",
                "예약어는 백틱(`) 또는 큰따옴표(\")로 감싸주세요"
            )
            com.sqlswitcher.parser.error.SqlErrorType.MISSING_KEYWORD -> listOf(
                "필수 키워드가 누락되었습니다",
                "SELECT-FROM, INSERT INTO, UPDATE-SET 등 필수 절을 확인해주세요"
            )
            com.sqlswitcher.parser.error.SqlErrorType.INVALID_FUNCTION -> listOf(
                "함수명과 매개변수를 확인해주세요",
                "데이터베이스별 함수 지원 여부를 확인해주세요"
            )
            com.sqlswitcher.parser.error.SqlErrorType.TYPE_MISMATCH -> listOf(
                "데이터 타입을 확인해주세요",
                "명시적 타입 변환(CAST)을 사용해보세요"
            )
            else -> listOf(
                "SQL을 다시 확인해주세요",
                "문제가 지속되면 관리자에게 문의해주세요"
            )
        }
    }

    private fun extractRelevantSqlSnippet(sql: String, line: Int?, column: Int?): String? {
        if (sql.length <= 200) return sql

        if (line != null) {
            val lines = sql.lines()
            if (line > 0 && line <= lines.size) {
                val startLine = maxOf(0, line - 2)
                val endLine = minOf(lines.size, line + 1)
                return lines.subList(startLine, endLine).joinToString("\n")
            }
        }

        return sql.take(200) + "..."
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Illegal argument: ${ex.message}")
        
        val errorResponse = ErrorResponse(
            errorCode = "INVALID_ARGUMENT",
            message = ex.message ?: "Invalid argument provided",
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        
        return ResponseEntity.badRequest().body(errorResponse)
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(
        ex: NoResourceFoundException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        // 404는 일반적인 상황이므로 DEBUG 레벨로 로깅
        logger.debug("Resource not found: ${ex.resourcePath}")

        val errorResponse = ErrorResponse(
            errorCode = "NOT_FOUND",
            message = "Resource not found: ${ex.resourcePath}",
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error occurred", ex)
        
        val errorResponse = ErrorResponse(
            errorCode = "INTERNAL_SERVER_ERROR",
            message = "An unexpected error occurred",
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            details = if (logger.isDebugEnabled) ex.message else null
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
}
