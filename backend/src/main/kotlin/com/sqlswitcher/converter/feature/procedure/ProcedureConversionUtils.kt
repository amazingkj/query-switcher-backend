package com.sqlswitcher.converter.feature.procedure

import com.sqlswitcher.converter.DialectType

/**
 * 프로시저 변환 공통 유틸리티
 */
object ProcedureConversionUtils {

    /**
     * 프로시저 내 데이터타입 변환
     */
    fun convertDataTypeForProcedure(dataType: String, targetDialect: DialectType): String {
        val upperType = dataType.uppercase()
        return when (targetDialect) {
            DialectType.MYSQL -> when (upperType) {
                "VARCHAR2" -> "VARCHAR"
                "NUMBER" -> "DECIMAL"
                "CLOB" -> "LONGTEXT"
                "BLOB" -> "LONGBLOB"
                "BOOLEAN" -> "TINYINT(1)"
                else -> dataType
            }
            DialectType.POSTGRESQL -> when (upperType) {
                "VARCHAR2" -> "VARCHAR"
                "NUMBER" -> "NUMERIC"
                "CLOB" -> "TEXT"
                "BLOB" -> "BYTEA"
                "INTEGER" -> "INTEGER"
                else -> dataType
            }
            else -> dataType
        }
    }

    /**
     * Oracle 프로시저 내 데이터타입 변환
     */
    fun convertOracleDataTypesInProcedure(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.MYSQL -> {
                result = result.replace(Regex("\\bVARCHAR2\\b", RegexOption.IGNORE_CASE), "VARCHAR")
                result = result.replace(Regex("\\bNUMBER\\b", RegexOption.IGNORE_CASE), "DECIMAL")
                result = result.replace(Regex("\\bCLOB\\b", RegexOption.IGNORE_CASE), "LONGTEXT")
            }
            DialectType.POSTGRESQL -> {
                result = result.replace(Regex("\\bVARCHAR2\\b", RegexOption.IGNORE_CASE), "VARCHAR")
                result = result.replace(Regex("\\bNUMBER\\b", RegexOption.IGNORE_CASE), "NUMERIC")
                result = result.replace(Regex("\\bCLOB\\b", RegexOption.IGNORE_CASE), "TEXT")
            }
            else -> {}
        }

        if (result != sql) {
            appliedRules.add("프로시저 내 데이터타입 변환")
        }

        return result
    }

    /**
     * Oracle 선언문을 MySQL DECLARE로 변환
     */
    fun convertOracleDeclarationsToMySql(declarations: String): String {
        val lines = declarations.split(";").filter { it.isNotBlank() }
        return lines.joinToString(";\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.uppercase().startsWith("DECLARE")) {
                "DECLARE $trimmed"
            } else {
                trimmed
            }
        }
    }

    /**
     * Oracle 파라미터 변환 (param IN VARCHAR2 → IN param VARCHAR / param VARCHAR)
     */
    fun convertOracleParameters(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // Oracle: param_name [IN|OUT|IN OUT] data_type
        // MySQL: [IN|OUT|INOUT] param_name data_type
        // PostgreSQL: param_name data_type (OUT은 RETURNS로)

        val paramPattern = Regex(
            """(\w+)\s+(IN\s+OUT|IN|OUT)?\s*(VARCHAR2|NUMBER|DATE|CLOB|BLOB|INTEGER|BOOLEAN)(\s*\(\s*\d+\s*(?:,\s*\d+)?\s*\))?""",
            RegexOption.IGNORE_CASE
        )

        when (targetDialect) {
            DialectType.MYSQL -> {
                result = paramPattern.replace(result) { match ->
                    val paramName = match.groupValues[1]
                    val mode = match.groupValues[2].ifEmpty { "IN" }.replace(Regex("\\s+"), "")
                        .replace("INOUT", "INOUT").replace("IN OUT", "INOUT")
                    val dataType = convertDataTypeForProcedure(match.groupValues[3], DialectType.MYSQL)
                    val size = match.groupValues[4]
                    "$mode $paramName $dataType$size"
                }
                appliedRules.add("Oracle 파라미터 → MySQL 파라미터 변환")
            }
            DialectType.POSTGRESQL -> {
                result = paramPattern.replace(result) { match ->
                    val paramName = match.groupValues[1]
                    val mode = match.groupValues[2].ifEmpty { "" }
                    val dataType = convertDataTypeForProcedure(match.groupValues[3], DialectType.POSTGRESQL)
                    val size = match.groupValues[4]
                    if (mode.uppercase().contains("OUT")) {
                        "$paramName OUT $dataType$size"
                    } else {
                        "$paramName $dataType$size"
                    }
                }
                appliedRules.add("Oracle 파라미터 → PostgreSQL 파라미터 변환")
            }
            else -> {}
        }

        return result
    }
}