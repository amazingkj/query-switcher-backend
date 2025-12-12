package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * 계층 쿼리 변환 (Oracle CONNECT BY → WITH RECURSIVE)
 */
object HierarchicalQueryConverter {

    private val CONNECT_BY_PATTERN = Regex(
        """START\s+WITH\s+(.+?)\s+CONNECT\s+BY\s+(?:NOCYCLE\s+)?(?:PRIOR\s+)?(.+?)(?=\s+ORDER\s+BY|\s*;|\s*$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val LEVEL_PATTERN = Regex("\\bLEVEL\\b", RegexOption.IGNORE_CASE)
    private val PRIOR_PATTERN = Regex("""PRIOR\s+(\w+)\s*=\s*(\w+)""", RegexOption.IGNORE_CASE)
    private val SELECT_PATTERN = Regex(
        """SELECT\s+(.+?)\s+FROM\s+(\w+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql
        if (!CONNECT_BY_PATTERN.containsMatchIn(sql)) return sql

        if (targetDialect != DialectType.MYSQL && targetDialect != DialectType.POSTGRESQL) {
            return sql
        }

        val match = CONNECT_BY_PATTERN.find(sql) ?: return sql
        val startCondition = match.groupValues[1].trim()
        val connectCondition = match.groupValues[2].trim()

        val priorMatch = PRIOR_PATTERN.find(connectCondition)
        val selectMatch = SELECT_PATTERN.find(sql)

        if (priorMatch != null && selectMatch != null) {
            return buildRecursiveCte(sql, startCondition, priorMatch, selectMatch, warnings, appliedRules)
        }

        // 파싱 실패 시 가이드 주석 추가
        return addConversionGuide(sql, warnings, appliedRules)
    }

    private fun buildRecursiveCte(
        sql: String,
        startCondition: String,
        priorMatch: MatchResult,
        selectMatch: MatchResult,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val parentCol = priorMatch.groupValues[1]
        val childCol = priorMatch.groupValues[2]
        val columns = selectMatch.groupValues[1].trim()
        val tableName = selectMatch.groupValues[2]

        val hasLevel = LEVEL_PATTERN.containsMatchIn(columns)
        val columnsWithoutLevel = LEVEL_PATTERN.replace(columns, "")
            .replace(Regex(",\\s*,"), ",")
            .trim()
            .trimEnd(',')

        val recursiveCte = buildString {
            append("WITH RECURSIVE hierarchy_cte AS (\n")
            append("    -- Base case: root nodes\n")
            append("    SELECT $columnsWithoutLevel")
            if (hasLevel) append(", 1 AS level")
            append("\n    FROM $tableName\n")
            append("    WHERE $startCondition\n")
            append("    \n")
            append("    UNION ALL\n")
            append("    \n")
            append("    -- Recursive case\n")
            append("    SELECT ")

            val colList = columnsWithoutLevel.split(",").map { it.trim() }
            append(colList.joinToString(", ") { col ->
                if (col.contains(".")) col.replace(Regex("""^\w+\."""), "t.") else "t.$col"
            })
            if (hasLevel) append(", h.level + 1")

            append("\n    FROM $tableName t\n")
            append("    JOIN hierarchy_cte h ON t.$childCol = h.$parentCol\n")
            append(")\n")
            append("SELECT * FROM hierarchy_cte")
        }

        appliedRules.add("CONNECT BY → WITH RECURSIVE CTE 변환")
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "Oracle CONNECT BY가 WITH RECURSIVE로 변환되었습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "변환된 CTE를 검토하고 컬럼명과 조인 조건을 확인하세요."
        ))

        return recursiveCte
    }

    private fun addConversionGuide(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val guide = """
-- Oracle CONNECT BY를 WITH RECURSIVE로 변환 필요:
-- WITH RECURSIVE cte AS (
--     SELECT columns, 1 as level FROM table WHERE start_condition
--     UNION ALL
--     SELECT columns, level + 1 FROM table t JOIN cte c ON t.child_col = c.parent_col
-- )
-- SELECT * FROM cte;

""".trimIndent()

        appliedRules.add("CONNECT BY 감지 - 수동 변환 가이드 추가")
        warnings.add(ConversionWarning(
            type = WarningType.MANUAL_REVIEW_NEEDED,
            message = "복잡한 CONNECT BY 구문입니다. WITH RECURSIVE로 수동 변환이 필요합니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "START WITH 조건을 base case로, CONNECT BY를 재귀 JOIN으로 변환하세요."
        ))

        return guide + sql
    }
}