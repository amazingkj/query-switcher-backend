package com.sqlswitcher.converter.model.converter

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.model.PivotInfo
import com.sqlswitcher.converter.model.UnpivotInfo

// =============================================================================
// PivotInfo 변환
// =============================================================================

/**
 * PivotInfo를 타겟 방언으로 변환
 */
fun PivotInfo.toDialect(
    targetDialect: DialectType,
    schemaOwner: String = ""
): String {
    return when (targetDialect) {
        DialectType.ORACLE -> toOracle(schemaOwner)
        DialectType.POSTGRESQL -> toPostgreSql()
        DialectType.MYSQL -> toMySql()
    }
}

private fun PivotInfo.toOracle(schemaOwner: String): String {
    return buildString {
        appendLine("SELECT *")
        appendLine("FROM (")
        appendLine("    SELECT ${(groupByColumns + listOf(pivotColumn, aggregateColumn)).joinToString(", ") { "\"$it\"" }}")
        appendLine("    FROM \"$schemaOwner\".\"$sourceTable\"")
        appendLine(")")
        append("PIVOT (")
        appendLine()
        append("    $aggregateFunction(\"$aggregateColumn\")")
        appendLine()
        append("    FOR \"$pivotColumn\" IN (")
        append(pivotValues.joinToString(", ") { "'$it' AS \"$it\"" })
        appendLine(")")
        append(")")
    }
}

private fun PivotInfo.toPostgreSql(): String {
    return buildString {
        appendLine("-- PostgreSQL은 PIVOT을 직접 지원하지 않습니다.")
        appendLine("-- crosstab 함수 또는 CASE WHEN을 사용하세요.")
        appendLine("-- tablefunc 확장 필요: CREATE EXTENSION IF NOT EXISTS tablefunc;")
        appendLine()
        appendLine("SELECT ${groupByColumns.joinToString(", ") { "\"$it\"" }},")

        pivotValues.forEachIndexed { index, value ->
            append("    $aggregateFunction(CASE WHEN \"$pivotColumn\" = '$value' THEN \"$aggregateColumn\" END) AS \"$value\"")
            if (index < pivotValues.size - 1) append(",")
            appendLine()
        }

        appendLine("FROM \"$sourceTable\"")
        append("GROUP BY ${groupByColumns.joinToString(", ") { "\"$it\"" }}")
    }
}

private fun PivotInfo.toMySql(): String {
    return buildString {
        appendLine("SELECT ${groupByColumns.joinToString(", ") { "`$it`" }},")

        pivotValues.forEachIndexed { index, value ->
            append("    $aggregateFunction(CASE WHEN `$pivotColumn` = '$value' THEN `$aggregateColumn` END) AS `$value`")
            if (index < pivotValues.size - 1) append(",")
            appendLine()
        }

        appendLine("FROM `$sourceTable`")
        append("GROUP BY ${groupByColumns.joinToString(", ") { "`$it`" }}")
    }
}

// =============================================================================
// UnpivotInfo 변환
// =============================================================================

/**
 * UnpivotInfo를 타겟 방언으로 변환
 */
fun UnpivotInfo.toDialect(
    targetDialect: DialectType,
    schemaOwner: String = ""
): String {
    return when (targetDialect) {
        DialectType.ORACLE -> toOracle(schemaOwner)
        DialectType.POSTGRESQL -> toPostgreSql()
        DialectType.MYSQL -> toMySql()
    }
}

private fun UnpivotInfo.toOracle(schemaOwner: String): String {
    return buildString {
        appendLine("SELECT *")
        appendLine("FROM \"$schemaOwner\".\"$sourceTable\"")
        append("UNPIVOT (")
        appendLine()
        append("    \"$valueColumn\" FOR \"$nameColumn\" IN (")
        append(unpivotColumns.joinToString(", ") { "\"$it\"" })
        appendLine(")")
        append(")")
    }
}

private fun UnpivotInfo.toPostgreSql(): String {
    return buildString {
        appendLine("-- PostgreSQL은 UNPIVOT을 직접 지원하지 않습니다.")
        appendLine("-- UNION ALL 또는 LATERAL JOIN을 사용하세요.")
        appendLine()

        unpivotColumns.forEachIndexed { index, col ->
            append("SELECT ${keepColumns.joinToString(", ") { "\"$it\"" }}, ")
            append("'$col' AS \"$nameColumn\", ")
            append("\"$col\" AS \"$valueColumn\" ")
            append("FROM \"$sourceTable\"")
            if (index < unpivotColumns.size - 1) {
                appendLine()
                append("UNION ALL")
                appendLine()
            }
        }
    }
}

private fun UnpivotInfo.toMySql(): String {
    return buildString {
        unpivotColumns.forEachIndexed { index, col ->
            append("SELECT ${keepColumns.joinToString(", ") { "`$it`" }}, ")
            append("'$col' AS `$nameColumn`, ")
            append("`$col` AS `$valueColumn` ")
            append("FROM `$sourceTable`")
            if (index < unpivotColumns.size - 1) {
                appendLine()
                append("UNION ALL")
                appendLine()
            }
        }
    }
}