package com.sqlswitcher.converter.model

/**
 * STORED PROCEDURE 파라미터 정보
 */
data class ProcedureParameter(
    val name: String,
    val mode: ParameterMode,        // IN, OUT, INOUT
    val dataType: String,
    val defaultValue: String? = null
) {
    enum class ParameterMode { IN, OUT, INOUT }
}

/**
 * STORED PROCEDURE 정보
 */
data class ProcedureInfo(
    val name: String,
    val parameters: List<ProcedureParameter>,
    val body: String,
    val returnType: String? = null,     // FUNCTION인 경우 반환 타입
    val isFunction: Boolean = false,
    val language: String = "SQL",       // SQL, PLPGSQL 등
    val characteristics: List<String> = emptyList()  // DETERMINISTIC, NO SQL 등
)