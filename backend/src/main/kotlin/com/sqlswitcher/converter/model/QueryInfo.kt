package com.sqlswitcher.converter.model

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * 재귀 CTE (WITH RECURSIVE) 정보
 */
data class RecursiveCteInfo(
    val cteName: String,
    val columns: List<String>,
    val anchorQuery: String,        // 비재귀(앵커) 부분
    val recursiveQuery: String,     // 재귀 부분
    val mainQuery: String           // 최종 SELECT
) {
    /**
     * Oracle 계층형 쿼리로 변환 (CONNECT BY 사용)
     * 주의: 모든 재귀 CTE가 CONNECT BY로 변환 가능하지는 않음
     */
    fun toOracleConnectBy(warnings: MutableList<ConversionWarning>): String {
        // Oracle 12c+ 에서는 WITH RECURSIVE도 지원하므로 그대로 사용
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "Oracle 12c 이상에서는 재귀 CTE를 지원합니다. 이전 버전에서는 CONNECT BY를 사용해야 합니다.",
            severity = WarningSeverity.INFO,
            suggestion = "Oracle 버전을 확인하고 적절한 구문을 선택하세요."
        ))

        val sb = StringBuilder()
        sb.appendLine("WITH \"$cteName\" (${columns.joinToString(", ") { "\"$it\"" }}) AS (")
        sb.appendLine("    $anchorQuery")
        sb.appendLine("    UNION ALL")
        sb.appendLine("    $recursiveQuery")
        sb.appendLine(")")
        sb.append(mainQuery)

        return sb.toString()
    }

    /**
     * PostgreSQL WITH RECURSIVE 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.appendLine("WITH RECURSIVE \"$cteName\" (${columns.joinToString(", ") { "\"$it\"" }}) AS (")
        sb.appendLine("    $anchorQuery")
        sb.appendLine("    UNION ALL")
        sb.appendLine("    $recursiveQuery")
        sb.appendLine(")")
        sb.append(mainQuery)

        return sb.toString()
    }

    /**
     * MySQL WITH RECURSIVE 생성 (MySQL 8.0+)
     */
    fun toMySql(warnings: MutableList<ConversionWarning>): String {
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "MySQL 8.0 이상에서만 WITH RECURSIVE를 지원합니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "MySQL 버전을 확인하세요. 5.7 이하에서는 저장 프로시저나 임시 테이블을 사용해야 합니다."
        ))

        val sb = StringBuilder()
        sb.appendLine("WITH RECURSIVE `$cteName` (${columns.joinToString(", ") { "`$it`" }}) AS (")
        sb.appendLine("    $anchorQuery")
        sb.appendLine("    UNION ALL")
        sb.appendLine("    $recursiveQuery")
        sb.appendLine(")")
        sb.append(mainQuery)

        return sb.toString()
    }
}

/**
 * 윈도우 함수 정보
 */
data class WindowFunctionInfo(
    val functionName: String,           // ROW_NUMBER, RANK, DENSE_RANK, LAG, LEAD 등
    val arguments: List<String> = emptyList(),  // 함수 인자 (LAG, LEAD의 경우)
    val partitionBy: List<String> = emptyList(),
    val orderBy: List<OrderByColumn> = emptyList(),
    val alias: String? = null
) {
    data class OrderByColumn(
        val column: String,
        val direction: String = "ASC",  // ASC, DESC
        val nullsPosition: String? = null  // NULLS FIRST, NULLS LAST
    )

    /**
     * Oracle 윈도우 함수 생성
     */
    fun toOracle(): String {
        val sb = StringBuilder()
        sb.append("$functionName(")
        if (arguments.isNotEmpty()) {
            sb.append(arguments.joinToString(", "))
        }
        sb.append(") OVER(")

        if (partitionBy.isNotEmpty()) {
            sb.append("PARTITION BY ")
            sb.append(partitionBy.joinToString(", ") { "\"$it\"" })
        }

        if (orderBy.isNotEmpty()) {
            if (partitionBy.isNotEmpty()) sb.append(" ")
            sb.append("ORDER BY ")
            sb.append(orderBy.joinToString(", ") { col ->
                val nullsStr = col.nullsPosition?.let { " $it" } ?: ""
                "\"${col.column}\" ${col.direction}$nullsStr"
            })
        }

        sb.append(")")
        alias?.let { sb.append(" AS \"$it\"") }

        return sb.toString()
    }

    /**
     * PostgreSQL 윈도우 함수 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.append("$functionName(")
        if (arguments.isNotEmpty()) {
            sb.append(arguments.joinToString(", "))
        }
        sb.append(") OVER(")

        if (partitionBy.isNotEmpty()) {
            sb.append("PARTITION BY ")
            sb.append(partitionBy.joinToString(", ") { "\"$it\"" })
        }

        if (orderBy.isNotEmpty()) {
            if (partitionBy.isNotEmpty()) sb.append(" ")
            sb.append("ORDER BY ")
            sb.append(orderBy.joinToString(", ") { col ->
                val nullsStr = col.nullsPosition?.let { " $it" } ?: ""
                "\"${col.column}\" ${col.direction}$nullsStr"
            })
        }

        sb.append(")")
        alias?.let { sb.append(" AS \"$it\"") }

        return sb.toString()
    }

    /**
     * MySQL 윈도우 함수 생성 (MySQL 8.0+)
     */
    fun toMySql(): String {
        val sb = StringBuilder()
        sb.append("$functionName(")
        if (arguments.isNotEmpty()) {
            sb.append(arguments.joinToString(", "))
        }
        sb.append(") OVER(")

        if (partitionBy.isNotEmpty()) {
            sb.append("PARTITION BY ")
            sb.append(partitionBy.joinToString(", ") { "`$it`" })
        }

        if (orderBy.isNotEmpty()) {
            if (partitionBy.isNotEmpty()) sb.append(" ")
            sb.append("ORDER BY ")
            sb.append(orderBy.joinToString(", ") { col ->
                // MySQL은 NULLS FIRST/LAST를 직접 지원하지 않음
                "`${col.column}` ${col.direction}"
            })
        }

        sb.append(")")
        alias?.let { sb.append(" AS `$it`") }

        return sb.toString()
    }
}

/**
 * MERGE (UPSERT) 문 정보
 */
data class MergeStatementInfo(
    val targetTable: String,
    val sourceTable: String?,           // USING 절의 테이블 또는 서브쿼리
    val sourceValues: List<String>?,    // INSERT 값들 (테이블이 아닌 경우)
    val matchCondition: String,         // ON 절 조건
    val matchedUpdate: Map<String, String>?,    // WHEN MATCHED THEN UPDATE SET
    val notMatchedInsert: Pair<List<String>, List<String>>?  // (columns, values)
) {
    /**
     * Oracle MERGE 문 생성
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.appendLine("MERGE INTO \"$schemaOwner\".\"$targetTable\" t")

        if (sourceTable != null) {
            sb.appendLine("USING \"$schemaOwner\".\"$sourceTable\" s")
        } else if (sourceValues != null) {
            sb.appendLine("USING (SELECT ${sourceValues.joinToString(", ")} FROM DUAL) s")
        }

        sb.appendLine("ON ($matchCondition)")

        matchedUpdate?.let { updates ->
            sb.appendLine("WHEN MATCHED THEN")
            sb.append("    UPDATE SET ")
            sb.appendLine(updates.entries.joinToString(", ") { "\"${it.key}\" = ${it.value}" })
        }

        notMatchedInsert?.let { (columns, values) ->
            sb.appendLine("WHEN NOT MATCHED THEN")
            sb.append("    INSERT (${columns.joinToString(", ") { "\"$it\"" }})")
            sb.appendLine()
            sb.append("    VALUES (${values.joinToString(", ")})")
        }

        return sb.toString()
    }

    /**
     * PostgreSQL UPSERT (INSERT ... ON CONFLICT) 문 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()

        notMatchedInsert?.let { (columns, values) ->
            sb.append("INSERT INTO \"$targetTable\" (${columns.joinToString(", ") { "\"$it\"" }})")
            sb.appendLine()
            sb.append("VALUES (${values.joinToString(", ")})")
            sb.appendLine()

            // ON CONFLICT 절 추출 (matchCondition에서 컬럼 추출)
            val conflictColumns = extractConflictColumns(matchCondition)
            sb.append("ON CONFLICT (${conflictColumns.joinToString(", ") { "\"$it\"" }})")

            matchedUpdate?.let { updates ->
                sb.appendLine()
                sb.append("DO UPDATE SET ")
                sb.append(updates.entries.joinToString(", ") { "\"${it.key}\" = EXCLUDED.\"${it.key}\"" })
            } ?: sb.append(" DO NOTHING")
        }

        return sb.toString()
    }

    /**
     * MySQL INSERT ... ON DUPLICATE KEY UPDATE 문 생성
     */
    fun toMySql(): String {
        val sb = StringBuilder()

        notMatchedInsert?.let { (columns, values) ->
            sb.append("INSERT INTO `$targetTable` (${columns.joinToString(", ") { "`$it`" }})")
            sb.appendLine()
            sb.append("VALUES (${values.joinToString(", ")})")

            matchedUpdate?.let { updates ->
                sb.appendLine()
                sb.append("ON DUPLICATE KEY UPDATE ")
                sb.append(updates.entries.joinToString(", ") { "`${it.key}` = VALUES(`${it.key}`)" })
            }
        }

        return sb.toString()
    }

    private fun extractConflictColumns(condition: String): List<String> {
        // t.column = s.column 패턴에서 컬럼명 추출
        val columnPattern = Regex("[ts]\\.([a-zA-Z_][a-zA-Z0-9_]*)")
        return columnPattern.findAll(condition)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }
}

/**
 * UPDATE JOIN 정보
 */
data class UpdateJoinInfo(
    val targetTable: String,
    val targetAlias: String?,
    val joinTable: String,
    val joinAlias: String?,
    val joinCondition: String,
    val setClause: Map<String, String>,
    val whereClause: String?
) {
    /**
     * Oracle UPDATE 문 생성 (서브쿼리 방식)
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.appendLine("UPDATE \"$schemaOwner\".\"$targetTable\" t")
        sb.appendLine("SET (${setClause.keys.joinToString(", ") { "\"$it\"" }}) = (")
        sb.appendLine("    SELECT ${setClause.values.joinToString(", ")}")
        sb.appendLine("    FROM \"$schemaOwner\".\"$joinTable\" j")
        sb.appendLine("    WHERE $joinCondition")
        sb.append(")")

        whereClause?.let {
            sb.appendLine()
            sb.append("WHERE $it")
        }

        return sb.toString()
    }

    /**
     * PostgreSQL UPDATE ... FROM 문 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.appendLine("UPDATE \"$targetTable\" AS t")
        sb.append("SET ")
        sb.appendLine(setClause.entries.joinToString(", ") { "\"${it.key}\" = ${it.value}" })
        sb.appendLine("FROM \"$joinTable\" AS j")
        sb.append("WHERE $joinCondition")

        whereClause?.let {
            sb.appendLine()
            sb.append("AND $it")
        }

        return sb.toString()
    }

    /**
     * MySQL UPDATE ... JOIN 문 생성
     */
    fun toMySql(): String {
        val sb = StringBuilder()
        sb.appendLine("UPDATE `$targetTable` AS t")
        sb.appendLine("INNER JOIN `$joinTable` AS j ON $joinCondition")
        sb.append("SET ")
        sb.append(setClause.entries.joinToString(", ") { "t.`${it.key}` = ${it.value}" })

        whereClause?.let {
            sb.appendLine()
            sb.append("WHERE $it")
        }

        return sb.toString()
    }
}

/**
 * DELETE JOIN 정보
 */
data class DeleteJoinInfo(
    val targetTable: String,
    val targetAlias: String?,
    val joinTable: String,
    val joinAlias: String?,
    val joinCondition: String,
    val whereClause: String?
) {
    /**
     * Oracle DELETE 문 생성 (서브쿼리 방식)
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.appendLine("DELETE FROM \"$schemaOwner\".\"$targetTable\"")
        sb.appendLine("WHERE EXISTS (")
        sb.appendLine("    SELECT 1 FROM \"$schemaOwner\".\"$joinTable\" j")
        sb.append("    WHERE $joinCondition")

        whereClause?.let {
            sb.appendLine()
            sb.append("    AND $it")
        }

        sb.appendLine()
        sb.append(")")

        return sb.toString()
    }

    /**
     * PostgreSQL DELETE ... USING 문 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.appendLine("DELETE FROM \"$targetTable\" AS t")
        sb.appendLine("USING \"$joinTable\" AS j")
        sb.append("WHERE $joinCondition")

        whereClause?.let {
            sb.appendLine()
            sb.append("AND $it")
        }

        return sb.toString()
    }

    /**
     * MySQL DELETE ... JOIN 문 생성
     */
    fun toMySql(): String {
        val sb = StringBuilder()
        sb.appendLine("DELETE t FROM `$targetTable` AS t")
        sb.appendLine("INNER JOIN `$joinTable` AS j ON $joinCondition")

        whereClause?.let {
            sb.append("WHERE $it")
        }

        return sb.toString()
    }
}

/**
 * PIVOT 정보
 */
data class PivotInfo(
    val sourceTable: String,
    val aggregateFunction: String,      // SUM, COUNT, AVG 등
    val aggregateColumn: String,        // 집계할 컬럼
    val pivotColumn: String,            // 피벗 기준 컬럼
    val pivotValues: List<String>,      // 피벗할 값들
    val groupByColumns: List<String>    // 그룹화 컬럼들
) {
    /**
     * Oracle PIVOT 문 생성
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.appendLine("SELECT *")
        sb.appendLine("FROM (")
        sb.appendLine("    SELECT ${(groupByColumns + listOf(pivotColumn, aggregateColumn)).joinToString(", ") { "\"$it\"" }}")
        sb.appendLine("    FROM \"$schemaOwner\".\"$sourceTable\"")
        sb.appendLine(")")
        sb.append("PIVOT (")
        sb.appendLine()
        sb.append("    $aggregateFunction(\"$aggregateColumn\")")
        sb.appendLine()
        sb.append("    FOR \"$pivotColumn\" IN (")
        sb.append(pivotValues.joinToString(", ") { "'$it' AS \"$it\"" })
        sb.appendLine(")")
        sb.append(")")

        return sb.toString()
    }

    /**
     * PostgreSQL crosstab 함수 사용 (tablefunc 확장 필요)
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.appendLine("-- PostgreSQL은 PIVOT을 직접 지원하지 않습니다.")
        sb.appendLine("-- crosstab 함수 또는 CASE WHEN을 사용하세요.")
        sb.appendLine("-- tablefunc 확장 필요: CREATE EXTENSION IF NOT EXISTS tablefunc;")
        sb.appendLine()
        sb.appendLine("SELECT ${groupByColumns.joinToString(", ") { "\"$it\"" }},")

        // CASE WHEN 방식으로 변환
        pivotValues.forEachIndexed { index, value ->
            sb.append("    $aggregateFunction(CASE WHEN \"$pivotColumn\" = '$value' THEN \"$aggregateColumn\" END) AS \"$value\"")
            if (index < pivotValues.size - 1) sb.append(",")
            sb.appendLine()
        }

        sb.appendLine("FROM \"$sourceTable\"")
        sb.append("GROUP BY ${groupByColumns.joinToString(", ") { "\"$it\"" }}")

        return sb.toString()
    }

    /**
     * MySQL CASE WHEN 방식으로 변환
     */
    fun toMySql(): String {
        val sb = StringBuilder()
        sb.appendLine("SELECT ${groupByColumns.joinToString(", ") { "`$it`" }},")

        pivotValues.forEachIndexed { index, value ->
            sb.append("    $aggregateFunction(CASE WHEN `$pivotColumn` = '$value' THEN `$aggregateColumn` END) AS `$value`")
            if (index < pivotValues.size - 1) sb.append(",")
            sb.appendLine()
        }

        sb.appendLine("FROM `$sourceTable`")
        sb.append("GROUP BY ${groupByColumns.joinToString(", ") { "`$it`" }}")

        return sb.toString()
    }
}

/**
 * UNPIVOT 정보
 */
data class UnpivotInfo(
    val sourceTable: String,
    val valueColumn: String,            // 값이 들어갈 컬럼명
    val nameColumn: String,             // 원래 컬럼명이 들어갈 컬럼명
    val unpivotColumns: List<String>,   // 언피벗할 컬럼들
    val keepColumns: List<String>       // 유지할 컬럼들
) {
    /**
     * Oracle UNPIVOT 문 생성
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.appendLine("SELECT *")
        sb.appendLine("FROM \"$schemaOwner\".\"$sourceTable\"")
        sb.append("UNPIVOT (")
        sb.appendLine()
        sb.append("    \"$valueColumn\" FOR \"$nameColumn\" IN (")
        sb.append(unpivotColumns.joinToString(", ") { "\"$it\"" })
        sb.appendLine(")")
        sb.append(")")

        return sb.toString()
    }

    /**
     * PostgreSQL UNION ALL 방식으로 변환
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.appendLine("-- PostgreSQL은 UNPIVOT을 직접 지원하지 않습니다.")
        sb.appendLine("-- UNION ALL 또는 LATERAL JOIN을 사용하세요.")
        sb.appendLine()

        unpivotColumns.forEachIndexed { index, col ->
            sb.append("SELECT ${keepColumns.joinToString(", ") { "\"$it\"" }}, ")
            sb.append("'$col' AS \"$nameColumn\", ")
            sb.append("\"$col\" AS \"$valueColumn\" ")
            sb.append("FROM \"$sourceTable\"")
            if (index < unpivotColumns.size - 1) {
                sb.appendLine()
                sb.append("UNION ALL")
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    /**
     * MySQL UNION ALL 방식으로 변환
     */
    fun toMySql(): String {
        val sb = StringBuilder()

        unpivotColumns.forEachIndexed { index, col ->
            sb.append("SELECT ${keepColumns.joinToString(", ") { "`$it`" }}, ")
            sb.append("'$col' AS `$nameColumn`, ")
            sb.append("`$col` AS `$valueColumn` ")
            sb.append("FROM `$sourceTable`")
            if (index < unpivotColumns.size - 1) {
                sb.appendLine()
                sb.append("UNION ALL")
                sb.appendLine()
            }
        }

        return sb.toString()
    }
}