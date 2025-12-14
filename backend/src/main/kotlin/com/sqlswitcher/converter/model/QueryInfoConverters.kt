package com.sqlswitcher.converter.model

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * QueryInfo 데이터 클래스들의 방언별 변환 로직
 *
 * 데이터 모델과 변환 로직을 분리하여 단일 책임 원칙을 준수합니다.
 */

// =============================================================================
// RecursiveCteInfo 변환
// =============================================================================

/**
 * RecursiveCteInfo를 타겟 방언으로 변환
 */
fun RecursiveCteInfo.toDialect(
    targetDialect: DialectType,
    warnings: MutableList<ConversionWarning>
): String {
    return when (targetDialect) {
        DialectType.ORACLE -> toOracle(warnings)
        DialectType.POSTGRESQL -> toPostgreSql()
        DialectType.MYSQL -> toMySql(warnings)
    }
}

private fun RecursiveCteInfo.toOracle(warnings: MutableList<ConversionWarning>): String {
    warnings.add(ConversionWarning(
        type = WarningType.SYNTAX_DIFFERENCE,
        message = "Oracle 12c 이상에서는 재귀 CTE를 지원합니다. 이전 버전에서는 CONNECT BY를 사용해야 합니다.",
        severity = WarningSeverity.INFO,
        suggestion = "Oracle 버전을 확인하고 적절한 구문을 선택하세요."
    ))

    return buildString {
        appendLine("WITH \"$cteName\" (${columns.joinToString(", ") { "\"$it\"" }}) AS (")
        appendLine("    $anchorQuery")
        appendLine("    UNION ALL")
        appendLine("    $recursiveQuery")
        appendLine(")")
        append(mainQuery)
    }
}

private fun RecursiveCteInfo.toPostgreSql(): String {
    return buildString {
        appendLine("WITH RECURSIVE \"$cteName\" (${columns.joinToString(", ") { "\"$it\"" }}) AS (")
        appendLine("    $anchorQuery")
        appendLine("    UNION ALL")
        appendLine("    $recursiveQuery")
        appendLine(")")
        append(mainQuery)
    }
}

private fun RecursiveCteInfo.toMySql(warnings: MutableList<ConversionWarning>): String {
    warnings.add(ConversionWarning(
        type = WarningType.SYNTAX_DIFFERENCE,
        message = "MySQL 8.0 이상에서만 WITH RECURSIVE를 지원합니다.",
        severity = WarningSeverity.WARNING,
        suggestion = "MySQL 버전을 확인하세요. 5.7 이하에서는 저장 프로시저나 임시 테이블을 사용해야 합니다."
    ))

    return buildString {
        appendLine("WITH RECURSIVE `$cteName` (${columns.joinToString(", ") { "`$it`" }}) AS (")
        appendLine("    $anchorQuery")
        appendLine("    UNION ALL")
        appendLine("    $recursiveQuery")
        appendLine(")")
        append(mainQuery)
    }
}

// =============================================================================
// WindowFunctionInfo 변환
// =============================================================================

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

// =============================================================================
// MergeStatementInfo 변환
// =============================================================================

/**
 * MergeStatementInfo를 타겟 방언으로 변환
 */
fun MergeStatementInfo.toDialect(
    targetDialect: DialectType,
    schemaOwner: String = ""
): String {
    return when (targetDialect) {
        DialectType.ORACLE -> toOracle(schemaOwner)
        DialectType.POSTGRESQL -> toPostgreSql()
        DialectType.MYSQL -> toMySql()
    }
}

private fun MergeStatementInfo.toOracle(schemaOwner: String): String {
    return buildString {
        appendLine("MERGE INTO \"$schemaOwner\".\"$targetTable\" t")

        if (sourceTable != null) {
            appendLine("USING \"$schemaOwner\".\"$sourceTable\" s")
        } else if (sourceValues != null) {
            appendLine("USING (SELECT ${sourceValues.joinToString(", ")} FROM DUAL) s")
        }

        appendLine("ON ($matchCondition)")

        matchedUpdate?.let { updates ->
            appendLine("WHEN MATCHED THEN")
            append("    UPDATE SET ")
            appendLine(updates.entries.joinToString(", ") { "\"${it.key}\" = ${it.value}" })
        }

        notMatchedInsert?.let { (columns, values) ->
            appendLine("WHEN NOT MATCHED THEN")
            append("    INSERT (${columns.joinToString(", ") { "\"$it\"" }})")
            appendLine()
            append("    VALUES (${values.joinToString(", ")})")
        }
    }
}

private fun MergeStatementInfo.toPostgreSql(): String {
    return buildString {
        notMatchedInsert?.let { (columns, values) ->
            append("INSERT INTO \"$targetTable\" (${columns.joinToString(", ") { "\"$it\"" }})")
            appendLine()
            append("VALUES (${values.joinToString(", ")})")
            appendLine()

            val conflictColumns = extractConflictColumns(matchCondition)
            append("ON CONFLICT (${conflictColumns.joinToString(", ") { "\"$it\"" }})")

            matchedUpdate?.let { updates ->
                appendLine()
                append("DO UPDATE SET ")
                append(updates.entries.joinToString(", ") { "\"${it.key}\" = EXCLUDED.\"${it.key}\"" })
            } ?: append(" DO NOTHING")
        }
    }
}

private fun MergeStatementInfo.toMySql(): String {
    return buildString {
        notMatchedInsert?.let { (columns, values) ->
            append("INSERT INTO `$targetTable` (${columns.joinToString(", ") { "`$it`" }})")
            appendLine()
            append("VALUES (${values.joinToString(", ")})")

            matchedUpdate?.let { updates ->
                appendLine()
                append("ON DUPLICATE KEY UPDATE ")
                append(updates.entries.joinToString(", ") { "`${it.key}` = VALUES(`${it.key}`)" })
            }
        }
    }
}

private fun extractConflictColumns(condition: String): List<String> {
    val columnPattern = Regex("[ts]\\.([a-zA-Z_][a-zA-Z0-9_]*)")
    return columnPattern.findAll(condition)
        .map { it.groupValues[1] }
        .distinct()
        .toList()
}

// =============================================================================
// UpdateJoinInfo 변환
// =============================================================================

/**
 * UpdateJoinInfo를 타겟 방언으로 변환
 */
fun UpdateJoinInfo.toDialect(
    targetDialect: DialectType,
    schemaOwner: String = ""
): String {
    return when (targetDialect) {
        DialectType.ORACLE -> toOracle(schemaOwner)
        DialectType.POSTGRESQL -> toPostgreSql()
        DialectType.MYSQL -> toMySql()
    }
}

private fun UpdateJoinInfo.toOracle(schemaOwner: String): String {
    return buildString {
        appendLine("UPDATE \"$schemaOwner\".\"$targetTable\" t")
        appendLine("SET (${setClause.keys.joinToString(", ") { "\"$it\"" }}) = (")
        appendLine("    SELECT ${setClause.values.joinToString(", ")}")
        appendLine("    FROM \"$schemaOwner\".\"$joinTable\" j")
        appendLine("    WHERE $joinCondition")
        append(")")

        whereClause?.let {
            appendLine()
            append("WHERE $it")
        }
    }
}

private fun UpdateJoinInfo.toPostgreSql(): String {
    return buildString {
        appendLine("UPDATE \"$targetTable\" AS t")
        append("SET ")
        appendLine(setClause.entries.joinToString(", ") { "\"${it.key}\" = ${it.value}" })
        appendLine("FROM \"$joinTable\" AS j")
        append("WHERE $joinCondition")

        whereClause?.let {
            appendLine()
            append("AND $it")
        }
    }
}

private fun UpdateJoinInfo.toMySql(): String {
    return buildString {
        appendLine("UPDATE `$targetTable` AS t")
        appendLine("INNER JOIN `$joinTable` AS j ON $joinCondition")
        append("SET ")
        append(setClause.entries.joinToString(", ") { "t.`${it.key}` = ${it.value}" })

        whereClause?.let {
            appendLine()
            append("WHERE $it")
        }
    }
}

// =============================================================================
// DeleteJoinInfo 변환
// =============================================================================

/**
 * DeleteJoinInfo를 타겟 방언으로 변환
 */
fun DeleteJoinInfo.toDialect(
    targetDialect: DialectType,
    schemaOwner: String = ""
): String {
    return when (targetDialect) {
        DialectType.ORACLE -> toOracle(schemaOwner)
        DialectType.POSTGRESQL -> toPostgreSql()
        DialectType.MYSQL -> toMySql()
    }
}

private fun DeleteJoinInfo.toOracle(schemaOwner: String): String {
    return buildString {
        appendLine("DELETE FROM \"$schemaOwner\".\"$targetTable\"")
        appendLine("WHERE EXISTS (")
        appendLine("    SELECT 1 FROM \"$schemaOwner\".\"$joinTable\" j")
        append("    WHERE $joinCondition")

        whereClause?.let {
            appendLine()
            append("    AND $it")
        }

        appendLine()
        append(")")
    }
}

private fun DeleteJoinInfo.toPostgreSql(): String {
    return buildString {
        appendLine("DELETE FROM \"$targetTable\" AS t")
        appendLine("USING \"$joinTable\" AS j")
        append("WHERE $joinCondition")

        whereClause?.let {
            appendLine()
            append("AND $it")
        }
    }
}

private fun DeleteJoinInfo.toMySql(): String {
    return buildString {
        appendLine("DELETE t FROM `$targetTable` AS t")
        appendLine("INNER JOIN `$joinTable` AS j ON $joinCondition")

        whereClause?.let {
            append("WHERE $it")
        }
    }
}

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