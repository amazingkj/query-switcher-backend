package com.sqlswitcher.model

import com.sqlswitcher.converter.DialectType

/**
 * SQL 검증 요청
 */
data class ValidationRequest(
    val sql: String,
    val dialect: DialectType
)

/**
 * SQL 테스트 요청
 */
data class TestRequest(
    val sql: String,
    val dialect: DialectType,
    val dryRun: Boolean = true
)

/**
 * SQL 검증 응답
 */
data class ValidationResponse(
    val isValid: Boolean,
    val dialect: DialectType,
    val errors: List<ValidationErrorDto> = emptyList(),
    val warnings: List<String> = emptyList(),
    val parsedStatementType: String? = null
)

data class ValidationErrorDto(
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val suggestion: String? = null
)

/**
 * SQL 테스트 응답
 */
data class TestResponse(
    val success: Boolean,
    val dialect: DialectType,
    val executionTimeMs: Long,
    val error: TestErrorDto? = null,
    val rowsAffected: Int? = null,
    val message: String? = null
)

data class TestErrorDto(
    val code: String?,
    val message: String,
    val sqlState: String?,
    val suggestion: String? = null
)

/**
 * 컨테이너 상태 응답
 */
data class ContainerStatusResponse(
    val containers: Map<DialectType, ContainerInfo>
)

data class ContainerInfo(
    val running: Boolean,
    val dialect: DialectType
)