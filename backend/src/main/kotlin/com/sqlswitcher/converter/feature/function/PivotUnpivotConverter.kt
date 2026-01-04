package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.util.SqlParsingUtils

/**
 * PIVOT/UNPIVOT 변환기
 *
 * Oracle PIVOT/UNPIVOT → MySQL/PostgreSQL 조건부 집계로 변환
 *
 * Oracle PIVOT 구문:
 * SELECT * FROM table
 * PIVOT (
 *   SUM(amount)
 *   FOR category IN ('A' AS cat_a, 'B' AS cat_b, 'C' AS cat_c)
 * )
 *
 * → MySQL/PostgreSQL CASE WHEN 변환:
 * SELECT id,
 *   SUM(CASE WHEN category = 'A' THEN amount END) AS cat_a,
 *   SUM(CASE WHEN category = 'B' THEN amount END) AS cat_b,
 *   SUM(CASE WHEN category = 'C' THEN amount END) AS cat_c
 * FROM table
 * GROUP BY id
 */
object PivotUnpivotConverter {

    // PIVOT 패턴 매칭
    private val PIVOT_PATTERN = Regex(
        """PIVOT\s*\(\s*([\w]+)\s*\(\s*(\w+)\s*\)\s+FOR\s+(\w+)\s+IN\s*\(([^)]+)\)\s*\)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // UNPIVOT 패턴 매칭
    private val UNPIVOT_PATTERN = Regex(
        """UNPIVOT\s*\(\s*(\w+)\s+FOR\s+(\w+)\s+IN\s*\(([^)]+)\)\s*\)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // SELECT ... FROM 패턴
    private val SELECT_FROM_PATTERN = Regex(
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

        var result = sql

        // PIVOT 변환
        if (PIVOT_PATTERN.containsMatchIn(sql)) {
            result = convertPivot(result, targetDialect, warnings, appliedRules)
        }

        // UNPIVOT 변환
        if (UNPIVOT_PATTERN.containsMatchIn(result)) {
            result = convertUnpivot(result, targetDialect, warnings, appliedRules)
        }

        return result
    }

    /**
     * PIVOT 변환
     * Oracle: PIVOT (AGG(col) FOR pivot_col IN (val1 AS alias1, val2 AS alias2))
     * → MySQL/PostgreSQL: AGG(CASE WHEN pivot_col = val1 THEN col END) AS alias1, ...
     */
    private fun convertPivot(
        sql: String,
        @Suppress("UNUSED_PARAMETER") targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val pivotMatch = PIVOT_PATTERN.find(sql) ?: return sql

        val aggFunc = pivotMatch.groupValues[1].uppercase()  // SUM, COUNT, AVG 등
        val aggCol = pivotMatch.groupValues[2]               // 집계할 컬럼
        val pivotCol = pivotMatch.groupValues[3]             // FOR 절의 컬럼
        val inClause = pivotMatch.groupValues[4]             // IN 절 내용

        // IN 절 파싱: 'A' AS alias_a, 'B' AS alias_b, ...
        val pivotValues = parseInClause(inClause)

        if (pivotValues.isEmpty()) {
            warnings.add(ConversionWarning(
                type = WarningType.MANUAL_REVIEW_NEEDED,
                message = "PIVOT IN 절 파싱 실패",
                severity = WarningSeverity.WARNING,
                suggestion = "PIVOT IN 절을 수동으로 확인하세요."
            ))
            return sql
        }

        // SELECT 절 파싱
        val selectMatch = SELECT_FROM_PATTERN.find(sql) ?: return sql
        val originalSelect = selectMatch.groupValues[1]
        val tableName = selectMatch.groupValues[2]

        // 그룹화 컬럼 추출 (SELECT 절에서 * 제외하고 pivotCol, aggCol 제외)
        val groupCols = extractGroupColumns(originalSelect, pivotCol, aggCol)

        // CASE WHEN 표현식 생성
        val caseExpressions = pivotValues.map { (value, alias) ->
            "$aggFunc(CASE WHEN $pivotCol = $value THEN $aggCol END) AS $alias"
        }.joinToString(",\n    ")

        // 최종 쿼리 생성
        val selectCols = if (groupCols.isNotEmpty()) {
            groupCols.joinToString(", ") + ",\n    " + caseExpressions
        } else {
            caseExpressions
        }

        val groupByClause = if (groupCols.isNotEmpty()) {
            "\nGROUP BY " + groupCols.joinToString(", ")
        } else ""

        // PIVOT 절 제거하고 새 쿼리 생성
        val newSql = """
SELECT
    $selectCols
FROM $tableName$groupByClause""".trim()

        appliedRules.add("Oracle PIVOT → CASE WHEN 조건부 집계 변환")
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "PIVOT이 CASE WHEN 조건부 집계로 변환되었습니다.",
            severity = WarningSeverity.INFO,
            suggestion = "GROUP BY 절과 컬럼 목록을 확인하세요."
        ))

        return newSql
    }

    /**
     * UNPIVOT 변환
     * Oracle: UNPIVOT (value_col FOR name_col IN (col1, col2, col3))
     * → MySQL/PostgreSQL: UNION ALL 기반 변환
     */
    private fun convertUnpivot(
        sql: String,
        @Suppress("UNUSED_PARAMETER") targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val unpivotMatch = UNPIVOT_PATTERN.find(sql) ?: return sql

        val valueCol = unpivotMatch.groupValues[1]  // 값을 담을 컬럼명
        val nameCol = unpivotMatch.groupValues[2]   // 이름을 담을 컬럼명
        val inClause = unpivotMatch.groupValues[3]  // IN 절의 컬럼들

        // IN 절 파싱: col1, col2, col3 또는 col1 AS 'alias1', col2 AS 'alias2'
        val unpivotCols = parseUnpivotInClause(inClause)

        if (unpivotCols.isEmpty()) {
            warnings.add(ConversionWarning(
                type = WarningType.MANUAL_REVIEW_NEEDED,
                message = "UNPIVOT IN 절 파싱 실패",
                severity = WarningSeverity.WARNING,
                suggestion = "UNPIVOT IN 절을 수동으로 확인하세요."
            ))
            return sql
        }

        // SELECT 절 파싱
        val selectMatch = SELECT_FROM_PATTERN.find(sql) ?: return sql
        val originalSelect = selectMatch.groupValues[1]
        val tableName = selectMatch.groupValues[2]

        // 기본 컬럼 (unpivot 대상이 아닌 컬럼들)
        val baseCols = extractBaseColumns(originalSelect, unpivotCols.map { it.first })

        // UNION ALL 기반 쿼리 생성
        val unionQueries = unpivotCols.map { (colName, alias) ->
            val selectPart = if (baseCols.isNotEmpty()) {
                baseCols.joinToString(", ") + ", "
            } else ""
            val aliasValue = alias ?: "'$colName'"
            "SELECT ${selectPart}$colName AS $valueCol, $aliasValue AS $nameCol FROM $tableName WHERE $colName IS NOT NULL"
        }

        val newSql = unionQueries.joinToString("\nUNION ALL\n")

        appliedRules.add("Oracle UNPIVOT → UNION ALL 변환")
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "UNPIVOT이 UNION ALL로 변환되었습니다.",
            severity = WarningSeverity.INFO,
            suggestion = "각 UNION 쿼리의 컬럼을 확인하세요."
        ))

        return newSql
    }

    /**
     * PIVOT IN 절 파싱
     * 'A' AS alias_a, 'B' AS alias_b → [(A, alias_a), (B, alias_b)]
     */
    private fun parseInClause(inClause: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val items = SqlParsingUtils.splitFunctionArgs(inClause)

        for (item in items) {
            val trimmed = item.trim()
            // 'value' AS alias 또는 'value' alias 형식
            val asPattern = Regex("""(['"][^'"]+['"]|\w+)\s+(?:AS\s+)?(\w+)""", RegexOption.IGNORE_CASE)
            val match = asPattern.find(trimmed)
            if (match != null) {
                result.add(Pair(match.groupValues[1], match.groupValues[2]))
            } else if (trimmed.isNotEmpty()) {
                // 단순 값인 경우 값 자체를 별칭으로 사용
                val cleanValue = trimmed.trim('\'', '"')
                result.add(Pair(trimmed, cleanValue))
            }
        }
        return result
    }

    /**
     * UNPIVOT IN 절 파싱
     * col1, col2, col3 → [(col1, null), (col2, null), (col3, null)]
     * col1 AS 'Name1', col2 AS 'Name2' → [(col1, 'Name1'), (col2, 'Name2')]
     */
    private fun parseUnpivotInClause(inClause: String): List<Pair<String, String?>> {
        val result = mutableListOf<Pair<String, String?>>()
        val items = SqlParsingUtils.splitFunctionArgs(inClause)

        for (item in items) {
            val trimmed = item.trim()
            // col AS 'alias' 형식
            val asPattern = Regex("""(\w+)\s+AS\s+(['"][^'"]+['"])""", RegexOption.IGNORE_CASE)
            val match = asPattern.find(trimmed)
            if (match != null) {
                result.add(Pair(match.groupValues[1], match.groupValues[2]))
            } else if (trimmed.isNotEmpty()) {
                // 단순 컬럼명
                result.add(Pair(trimmed, null))
            }
        }
        return result
    }

    /**
     * SELECT 절에서 그룹화 컬럼 추출 (pivotCol과 aggCol 제외)
     */
    private fun extractGroupColumns(select: String, pivotCol: String, aggCol: String): List<String> {
        if (select.trim() == "*") return emptyList()

        return select.split(",")
            .map { it.trim() }
            .filter { col ->
                val cleanCol = col.split(" ").first().trim()
                cleanCol != "*" &&
                !cleanCol.equals(pivotCol, ignoreCase = true) &&
                !cleanCol.equals(aggCol, ignoreCase = true)
            }
            .map { it.split(" ").first().trim() }
    }

    /**
     * SELECT 절에서 기본 컬럼 추출 (unpivot 대상 컬럼 제외)
     */
    private fun extractBaseColumns(select: String, unpivotCols: List<String>): List<String> {
        if (select.trim() == "*") return emptyList()

        return select.split(",")
            .map { it.trim() }
            .filter { col ->
                val cleanCol = col.split(" ").first().trim()
                cleanCol != "*" &&
                unpivotCols.none { it.equals(cleanCol, ignoreCase = true) }
            }
            .map { it.split(" ").first().trim() }
    }
}
