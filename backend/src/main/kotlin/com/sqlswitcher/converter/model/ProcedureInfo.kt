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

    fun toOracle(): String {
        val modeStr = when (mode) {
            ParameterMode.IN -> "IN"
            ParameterMode.OUT -> "OUT"
            ParameterMode.INOUT -> "IN OUT"
        }
        val defaultStr = defaultValue?.let { " DEFAULT $it" } ?: ""
        return "\"$name\" $modeStr $dataType$defaultStr"
    }

    fun toPostgreSql(): String {
        val modeStr = mode.name
        val defaultStr = defaultValue?.let { " DEFAULT $it" } ?: ""
        return "$modeStr \"$name\" $dataType$defaultStr"
    }

    fun toMySql(): String {
        val modeStr = mode.name
        val defaultStr = defaultValue?.let { " DEFAULT $it" } ?: ""
        return "$modeStr `$name` $dataType$defaultStr"
    }
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
) {
    /**
     * Oracle PROCEDURE/FUNCTION 생성
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        val objectType = if (isFunction) "FUNCTION" else "PROCEDURE"

        sb.append("CREATE OR REPLACE $objectType \"$schemaOwner\".\"$name\"")

        if (parameters.isNotEmpty()) {
            sb.appendLine(" (")
            sb.append(parameters.joinToString(",\n    ") { "    ${it.toOracle()}" })
            sb.appendLine()
            sb.append(")")
        }

        if (isFunction && returnType != null) {
            sb.appendLine()
            sb.append("RETURN $returnType")
        }

        sb.appendLine()
        sb.appendLine("IS")
        sb.appendLine("BEGIN")
        sb.append(body)
        sb.appendLine()
        sb.append("END;")

        return sb.toString()
    }

    /**
     * PostgreSQL PROCEDURE/FUNCTION 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        val objectType = if (isFunction) "FUNCTION" else "PROCEDURE"

        sb.append("CREATE OR REPLACE $objectType \"$name\"(")

        if (parameters.isNotEmpty()) {
            sb.appendLine()
            sb.append(parameters.joinToString(",\n    ") { "    ${it.toPostgreSql()}" })
            sb.appendLine()
        }

        sb.append(")")

        if (isFunction && returnType != null) {
            sb.appendLine()
            sb.append("RETURNS $returnType")
        }

        sb.appendLine()
        sb.appendLine("LANGUAGE plpgsql")
        sb.appendLine("AS \$\$")
        sb.appendLine("BEGIN")
        sb.append(body)
        sb.appendLine()
        sb.appendLine("END;")
        sb.append("\$\$;")

        return sb.toString()
    }

    /**
     * MySQL PROCEDURE/FUNCTION 생성
     */
    fun toMySql(): String {
        val sb = StringBuilder()
        val objectType = if (isFunction) "FUNCTION" else "PROCEDURE"

        sb.append("CREATE $objectType `$name`(")

        if (parameters.isNotEmpty()) {
            sb.append(parameters.joinToString(", ") { it.toMySql() })
        }

        sb.append(")")

        if (isFunction && returnType != null) {
            sb.appendLine()
            sb.append("RETURNS $returnType")
        }

        sb.appendLine()
        sb.appendLine("BEGIN")
        sb.append(body)
        sb.appendLine()
        sb.append("END")

        return sb.toString()
    }
}