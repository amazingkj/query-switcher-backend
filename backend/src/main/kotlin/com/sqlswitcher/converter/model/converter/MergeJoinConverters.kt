package com.sqlswitcher.converter.model.converter

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.model.DeleteJoinInfo
import com.sqlswitcher.converter.model.MergeStatementInfo
import com.sqlswitcher.converter.model.UpdateJoinInfo

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