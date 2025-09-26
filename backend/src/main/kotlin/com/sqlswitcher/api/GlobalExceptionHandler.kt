package com.sqlswitcher.api

import com.sqlswitcher.parser.error.SqlParseException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
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

    @ExceptionHandler(SqlParseException::class)
    fun handleSqlParseException(
        ex: SqlParseException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("SQL parsing error: ${ex.message}")
        
        val errorResponse = ErrorResponse(
            errorCode = "SQL_PARSE_ERROR",
            message = "SQL parsing failed: ${ex.message}",
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            details = ex.message.toString()
        )
        
        return ResponseEntity.badRequest().body(errorResponse)
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
