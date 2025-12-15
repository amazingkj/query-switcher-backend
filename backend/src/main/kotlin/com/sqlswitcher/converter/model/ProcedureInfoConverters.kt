package com.sqlswitcher.converter.model

import com.sqlswitcher.converter.DialectType

/**
 * ProcedureInfo 데이터 클래스들의 방언별 변환 로직
 */

// =============================================================================
// ProcedureParameter 변환
// =============================================================================

/**
 * ProcedureParameter를 타겟 방언으로 변환
 */
fun ProcedureParameter.toDialect(targetDialect: DialectType): String {
    return when (targetDialect) {
        DialectType.ORACLE -> toOracle()
        DialectType.POSTGRESQL -> toPostgreSql()
        DialectType.MYSQL -> toMySql()
    }
}

private fun ProcedureParameter.toOracle(): String {
    val modeStr = when (mode) {
        ProcedureParameter.ParameterMode.IN -> "IN"
        ProcedureParameter.ParameterMode.OUT -> "OUT"
        ProcedureParameter.ParameterMode.INOUT -> "IN OUT"
    }
    val defaultStr = defaultValue?.let { " DEFAULT $it" } ?: ""
    return "\"$name\" $modeStr $dataType$defaultStr"
}

private fun ProcedureParameter.toPostgreSql(): String {
    val modeStr = mode.name
    val defaultStr = defaultValue?.let { " DEFAULT $it" } ?: ""
    return "$modeStr \"$name\" $dataType$defaultStr"
}

private fun ProcedureParameter.toMySql(): String {
    val modeStr = mode.name
    val defaultStr = defaultValue?.let { " DEFAULT $it" } ?: ""
    return "$modeStr `$name` $dataType$defaultStr"
}

// =============================================================================
// ProcedureInfo 변환
// =============================================================================

/**
 * ProcedureInfo를 타겟 방언으로 변환
 */
fun ProcedureInfo.toDialect(
    targetDialect: DialectType,
    schemaOwner: String = ""
): String {
    return when (targetDialect) {
        DialectType.ORACLE -> toOracle(schemaOwner)
        DialectType.POSTGRESQL -> toPostgreSql()
        DialectType.MYSQL -> toMySql()
    }
}

private fun ProcedureInfo.toOracle(schemaOwner: String): String {
    return buildString {
        val objectType = if (isFunction) "FUNCTION" else "PROCEDURE"

        append("CREATE OR REPLACE $objectType \"$schemaOwner\".\"$name\"")

        if (parameters.isNotEmpty()) {
            appendLine(" (")
            append(parameters.joinToString(",\n    ") { "    ${it.toDialect(DialectType.ORACLE)}" })
            appendLine()
            append(")")
        }

        if (isFunction && returnType != null) {
            appendLine()
            append("RETURN $returnType")
        }

        appendLine()
        appendLine("IS")
        appendLine("BEGIN")
        append(body)
        appendLine()
        append("END;")
    }
}

private fun ProcedureInfo.toPostgreSql(): String {
    return buildString {
        val objectType = if (isFunction) "FUNCTION" else "PROCEDURE"

        append("CREATE OR REPLACE $objectType \"$name\"(")

        if (parameters.isNotEmpty()) {
            appendLine()
            append(parameters.joinToString(",\n    ") { "    ${it.toDialect(DialectType.POSTGRESQL)}" })
            appendLine()
        }

        append(")")

        if (isFunction && returnType != null) {
            appendLine()
            append("RETURNS $returnType")
        }

        appendLine()
        appendLine("LANGUAGE plpgsql")
        appendLine("AS \$\$")
        appendLine("BEGIN")
        append(body)
        appendLine()
        appendLine("END;")
        append("\$\$;")
    }
}

private fun ProcedureInfo.toMySql(): String {
    return buildString {
        val objectType = if (isFunction) "FUNCTION" else "PROCEDURE"

        append("CREATE $objectType `$name`(")

        if (parameters.isNotEmpty()) {
            append(parameters.joinToString(", ") { it.toDialect(DialectType.MYSQL) })
        }

        append(")")

        if (isFunction && returnType != null) {
            appendLine()
            append("RETURNS $returnType")
        }

        appendLine()
        appendLine("BEGIN")
        append(body)
        appendLine()
        append("END")
    }
}