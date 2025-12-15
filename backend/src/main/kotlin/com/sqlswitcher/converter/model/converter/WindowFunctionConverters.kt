package com.sqlswitcher.converter.model.converter

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.model.WindowFunctionInfo

/**
 * WindowFunctionInfo를 타겟 방언으로 변환
 */
fun WindowFunctionInfo.toDialect(targetDialect: DialectType): String {
    return when (targetDialect) {
        DialectType.ORACLE -> toOracle()
        DialectType.POSTGRESQL -> toPostgreSql()
        DialectType.MYSQL -> toMySql()
    }
}

private fun WindowFunctionInfo.toOracle(): String {
    return buildString {
        append("$functionName(")
        if (arguments.isNotEmpty()) {
            append(arguments.joinToString(", "))
        }
        append(") OVER(")

        if (partitionBy.isNotEmpty()) {
            append("PARTITION BY ")
            append(partitionBy.joinToString(", ") { "\"$it\"" })
        }

        if (orderBy.isNotEmpty()) {
            if (partitionBy.isNotEmpty()) append(" ")
            append("ORDER BY ")
            append(orderBy.joinToString(", ") { col ->
                val nullsStr = col.nullsPosition?.let { " $it" } ?: ""
                "\"${col.column}\" ${col.direction}$nullsStr"
            })
        }

        append(")")
        alias?.let { append(" AS \"$it\"") }
    }
}

private fun WindowFunctionInfo.toPostgreSql(): String {
    return buildString {
        append("$functionName(")
        if (arguments.isNotEmpty()) {
            append(arguments.joinToString(", "))
        }
        append(") OVER(")

        if (partitionBy.isNotEmpty()) {
            append("PARTITION BY ")
            append(partitionBy.joinToString(", ") { "\"$it\"" })
        }

        if (orderBy.isNotEmpty()) {
            if (partitionBy.isNotEmpty()) append(" ")
            append("ORDER BY ")
            append(orderBy.joinToString(", ") { col ->
                val nullsStr = col.nullsPosition?.let { " $it" } ?: ""
                "\"${col.column}\" ${col.direction}$nullsStr"
            })
        }

        append(")")
        alias?.let { append(" AS \"$it\"") }
    }
}

private fun WindowFunctionInfo.toMySql(): String {
    return buildString {
        append("$functionName(")
        if (arguments.isNotEmpty()) {
            append(arguments.joinToString(", "))
        }
        append(") OVER(")

        if (partitionBy.isNotEmpty()) {
            append("PARTITION BY ")
            append(partitionBy.joinToString(", ") { "`$it`" })
        }

        if (orderBy.isNotEmpty()) {
            if (partitionBy.isNotEmpty()) append(" ")
            append("ORDER BY ")
            append(orderBy.joinToString(", ") { col ->
                // MySQL은 NULLS FIRST/LAST를 직접 지원하지 않음
                "`${col.column}` ${col.direction}"
            })
        }

        append(")")
        alias?.let { append(" AS `$it`") }
    }
}