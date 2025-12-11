package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * Oracle 구식 (+) 조인 → ANSI JOIN 변환
 */
object OracleJoinConverter {

    private val ORACLE_LEFT_JOIN_PATTERN = Regex(
        """(\w+)\.(\w+)\s*=\s*(\w+)\.(\w+)\s*\(\+\)""",
        RegexOption.IGNORE_CASE
    )
    private val ORACLE_RIGHT_JOIN_PATTERN = Regex(
        """(\w+)\.(\w+)\s*\(\+\)\s*=\s*(\w+)\.(\w+)""",
        RegexOption.IGNORE_CASE
    )
    private val WHERE_PATTERN = Regex(
        """FROM\s+(.+?)\s+WHERE\s+(.+?)(?=\s+(?:GROUP|ORDER|HAVING|LIMIT|$))""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private data class JoinInfo(
        val table1: String,
        val table2: String,
        val condition: String,
        val joinType: String
    )

    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql
        if (!sql.contains("(+)")) return sql

        val whereMatch = WHERE_PATTERN.find(sql) ?: return sql

        val fromClause = whereMatch.groupValues[1].trim()
        val whereClause = whereMatch.groupValues[2].trim()

        val joinConditions = mutableListOf<JoinInfo>()
        val nonJoinConditions = mutableListOf<String>()

        val conditions = whereClause.split(Regex("""\s+AND\s+""", RegexOption.IGNORE_CASE))

        for (condition in conditions) {
            val leftJoinMatch = ORACLE_LEFT_JOIN_PATTERN.find(condition)
            val rightJoinMatch = ORACLE_RIGHT_JOIN_PATTERN.find(condition)

            when {
                leftJoinMatch != null -> {
                    val table1 = leftJoinMatch.groupValues[1]
                    val col1 = leftJoinMatch.groupValues[2]
                    val table2 = leftJoinMatch.groupValues[3]
                    val col2 = leftJoinMatch.groupValues[4]
                    joinConditions.add(JoinInfo(table1, table2, "$table1.$col1 = $table2.$col2", "LEFT"))
                }
                rightJoinMatch != null -> {
                    val table1 = rightJoinMatch.groupValues[1]
                    val col1 = rightJoinMatch.groupValues[2]
                    val table2 = rightJoinMatch.groupValues[3]
                    val col2 = rightJoinMatch.groupValues[4]
                    joinConditions.add(JoinInfo(table1, table2, "$table1.$col1 = $table2.$col2", "RIGHT"))
                }
                else -> nonJoinConditions.add(condition.trim())
            }
        }

        if (joinConditions.isEmpty()) return sql

        val tables = fromClause.split(",").map { it.trim() }
        val newFromClause = buildNewFromClause(tables, joinConditions)

        val newWhereClause = if (nonJoinConditions.isNotEmpty()) {
            "WHERE " + nonJoinConditions.joinToString(" AND ")
        } else ""

        val result = sql.replace(whereMatch.value, "FROM $newFromClause $newWhereClause")

        appliedRules.add("Oracle (+) 조인 → ANSI JOIN 변환")
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "Oracle 구식 (+) 조인 문법이 ANSI JOIN으로 변환되었습니다.",
            severity = WarningSeverity.INFO,
            suggestion = "변환된 JOIN 구문을 검토하세요."
        ))

        return result
    }

    private fun buildNewFromClause(tables: List<String>, joinConditions: List<JoinInfo>): String {
        val newFromClause = StringBuilder()
        val usedTables = mutableSetOf<String>()

        // 첫 테이블
        val firstTable = tables.firstOrNull() ?: ""
        newFromClause.append(firstTable)
        val firstAlias = firstTable.split(Regex("""\s+""")).lastOrNull() ?: ""
        usedTables.add(firstAlias)

        // JOIN 조건들 추가
        for (join in joinConditions) {
            val joinTableAlias = if (join.table1 in usedTables) join.table2 else join.table1
            val fullTableDef = tables.find {
                it.split(Regex("""\s+""")).lastOrNull() == joinTableAlias
            } ?: joinTableAlias

            if (joinTableAlias !in usedTables) {
                newFromClause.append("\n${join.joinType} JOIN $fullTableDef ON ${join.condition}")
                usedTables.add(joinTableAlias)
            }
        }

        // 나머지 테이블
        for (table in tables) {
            val alias = table.split(Regex("""\s+""")).lastOrNull() ?: ""
            if (alias !in usedTables) {
                newFromClause.append(", $table")
            }
        }

        return newFromClause.toString()
    }
}