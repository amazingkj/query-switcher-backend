package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * 계층 쿼리 변환 (Oracle CONNECT BY → WITH RECURSIVE)
 *
 * 지원 기능:
 * - 기본 CONNECT BY PRIOR 구문
 * - LEVEL 의사 컬럼
 * - SYS_CONNECT_BY_PATH 함수
 * - CONNECT_BY_ROOT 함수
 * - CONNECT_BY_ISLEAF 의사 컬럼
 * - NOCYCLE 옵션
 */
object HierarchicalQueryConverter {

    private val CONNECT_BY_PATTERN = Regex(
        """START\s+WITH\s+(.+?)\s+CONNECT\s+BY\s+(?:NOCYCLE\s+)?(?:PRIOR\s+)?(.+?)(?=\s+ORDER\s+BY|\s*;|\s*$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // 역순 패턴: CONNECT BY가 먼저 오는 경우
    private val CONNECT_BY_REVERSE_PATTERN = Regex(
        """CONNECT\s+BY\s+(?:NOCYCLE\s+)?(?:PRIOR\s+)?(.+?)\s+START\s+WITH\s+(.+?)(?=\s+ORDER\s+BY|\s*;|\s*$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val LEVEL_PATTERN = Regex("\\bLEVEL\\b", RegexOption.IGNORE_CASE)
    private val PRIOR_PATTERN = Regex("""PRIOR\s+(\w+)\s*=\s*(\w+)""", RegexOption.IGNORE_CASE)
    private val PRIOR_REVERSE_PATTERN = Regex("""(\w+)\s*=\s*PRIOR\s+(\w+)""", RegexOption.IGNORE_CASE)
    private val SELECT_PATTERN = Regex(
        """SELECT\s+(.+?)\s+FROM\s+(\w+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // SYS_CONNECT_BY_PATH 패턴
    private val SYS_CONNECT_BY_PATH_PATTERN = Regex(
        """SYS_CONNECT_BY_PATH\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""",
        RegexOption.IGNORE_CASE
    )

    // CONNECT_BY_ROOT 패턴
    private val CONNECT_BY_ROOT_PATTERN = Regex(
        """CONNECT_BY_ROOT\s+(\w+)""",
        RegexOption.IGNORE_CASE
    )

    // CONNECT_BY_ISLEAF 패턴
    private val CONNECT_BY_ISLEAF_PATTERN = Regex(
        """\bCONNECT_BY_ISLEAF\b""",
        RegexOption.IGNORE_CASE
    )

    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql

        // CONNECT BY 패턴 확인 (정순 또는 역순)
        val hasConnectBy = CONNECT_BY_PATTERN.containsMatchIn(sql) ||
                           CONNECT_BY_REVERSE_PATTERN.containsMatchIn(sql)
        if (!hasConnectBy) return sql

        if (targetDialect != DialectType.MYSQL && targetDialect != DialectType.POSTGRESQL) {
            return sql
        }

        // 정순 또는 역순 매칭
        val (startCondition, connectCondition) = parseConnectByClause(sql) ?: run {
            return addConversionGuide(sql, warnings, appliedRules)
        }

        // PRIOR 패턴 매칭 (정순 또는 역순)
        val (parentCol, childCol) = parsePriorClause(connectCondition) ?: run {
            return addConversionGuide(sql, warnings, appliedRules)
        }

        val selectMatch = SELECT_PATTERN.find(sql) ?: run {
            return addConversionGuide(sql, warnings, appliedRules)
        }

        return buildRecursiveCte(
            sql, startCondition, parentCol, childCol, selectMatch,
            targetDialect, warnings, appliedRules
        )
    }

    /**
     * CONNECT BY 절 파싱 (정순/역순 모두 지원)
     */
    private fun parseConnectByClause(sql: String): Pair<String, String>? {
        // 정순: START WITH ... CONNECT BY ...
        val match = CONNECT_BY_PATTERN.find(sql)
        if (match != null) {
            return Pair(match.groupValues[1].trim(), match.groupValues[2].trim())
        }

        // 역순: CONNECT BY ... START WITH ...
        val reverseMatch = CONNECT_BY_REVERSE_PATTERN.find(sql)
        if (reverseMatch != null) {
            return Pair(reverseMatch.groupValues[2].trim(), reverseMatch.groupValues[1].trim())
        }

        return null
    }

    /**
     * PRIOR 절 파싱 (정순/역순 모두 지원)
     * 반환: Pair(parentCol, childCol)
     */
    private fun parsePriorClause(connectCondition: String): Pair<String, String>? {
        // 정순: PRIOR parent_col = child_col
        val match = PRIOR_PATTERN.find(connectCondition)
        if (match != null) {
            return Pair(match.groupValues[1], match.groupValues[2])
        }

        // 역순: child_col = PRIOR parent_col
        val reverseMatch = PRIOR_REVERSE_PATTERN.find(connectCondition)
        if (reverseMatch != null) {
            return Pair(reverseMatch.groupValues[2], reverseMatch.groupValues[1])
        }

        return null
    }

    private fun buildRecursiveCte(
        sql: String,
        startCondition: String,
        parentCol: String,
        childCol: String,
        selectMatch: MatchResult,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val columns = selectMatch.groupValues[1].trim()
        val tableName = selectMatch.groupValues[2]

        val hasLevel = LEVEL_PATTERN.containsMatchIn(columns)
        val hasSysConnectByPath = SYS_CONNECT_BY_PATH_PATTERN.containsMatchIn(columns)
        val hasConnectByRoot = CONNECT_BY_ROOT_PATTERN.containsMatchIn(columns)
        val hasConnectByIsLeaf = CONNECT_BY_ISLEAF_PATTERN.containsMatchIn(columns)

        // 컬럼 정리: LEVEL, SYS_CONNECT_BY_PATH, CONNECT_BY_ROOT, CONNECT_BY_ISLEAF 제거
        var columnsWithoutSpecial = LEVEL_PATTERN.replace(columns, "")
        columnsWithoutSpecial = SYS_CONNECT_BY_PATH_PATTERN.replace(columnsWithoutSpecial, "")
        columnsWithoutSpecial = CONNECT_BY_ROOT_PATTERN.replace(columnsWithoutSpecial, "")
        columnsWithoutSpecial = CONNECT_BY_ISLEAF_PATTERN.replace(columnsWithoutSpecial, "")
        columnsWithoutSpecial = columnsWithoutSpecial
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex(",\\s*$"), "")
            .replace(Regex("^\\s*,"), "")
            .trim()

        // SYS_CONNECT_BY_PATH 함수에서 사용된 컬럼과 구분자 추출
        val pathMatch = SYS_CONNECT_BY_PATH_PATTERN.find(columns)
        val pathColumn = pathMatch?.groupValues?.get(1)
        val pathDelimiter = pathMatch?.groupValues?.get(2) ?: "/"

        // CONNECT_BY_ROOT 컬럼 추출
        val rootMatch = CONNECT_BY_ROOT_PATTERN.find(columns)
        val rootColumn = rootMatch?.groupValues?.get(1)

        val recursiveCte = buildString {
            append("WITH RECURSIVE hierarchy_cte AS (\n")
            append("    -- Base case: root nodes\n")
            append("    SELECT $columnsWithoutSpecial")
            if (hasLevel) append(", 1 AS level")
            if (hasSysConnectByPath && pathColumn != null) {
                append(", CAST('$pathDelimiter' || $pathColumn AS VARCHAR(4000)) AS path")
            }
            if (hasConnectByRoot && rootColumn != null) {
                append(", $rootColumn AS root_$rootColumn")
            }
            append("\n    FROM $tableName\n")
            append("    WHERE $startCondition\n")
            append("    \n")
            append("    UNION ALL\n")
            append("    \n")
            append("    -- Recursive case\n")
            append("    SELECT ")

            val colList = columnsWithoutSpecial.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            append(colList.joinToString(", ") { col ->
                val cleanCol = col.split(" ").first().trim()
                if (cleanCol.contains(".")) cleanCol.replace(Regex("""^\w+\."""), "t.") else "t.$cleanCol"
            })
            if (hasLevel) append(", h.level + 1")
            if (hasSysConnectByPath && pathColumn != null) {
                append(", h.path || '$pathDelimiter' || t.$pathColumn")
            }
            if (hasConnectByRoot && rootColumn != null) {
                append(", h.root_$rootColumn")
            }

            append("\n    FROM $tableName t\n")
            append("    JOIN hierarchy_cte h ON t.$childCol = h.$parentCol\n")
            append(")\n")
            append("SELECT ")

            // 최종 SELECT 절
            append(colList.joinToString(", ") { col ->
                col.split(" ").first().trim()
            })
            if (hasLevel) append(", level")
            if (hasSysConnectByPath) append(", path")
            if (hasConnectByRoot && rootColumn != null) append(", root_$rootColumn")
            if (hasConnectByIsLeaf) {
                // CONNECT_BY_ISLEAF: 자식이 없는 노드인지 판별
                // NOT EXISTS 서브쿼리로 자식 존재 여부 확인
                append(",\n    CASE WHEN NOT EXISTS (\n")
                append("        SELECT 1 FROM $tableName child\n")
                append("        WHERE child.$childCol = hierarchy_cte.$parentCol\n")
                append("    ) THEN 1 ELSE 0 END AS is_leaf")
            }
            append(" FROM hierarchy_cte")
        }

        appliedRules.add("CONNECT BY → WITH RECURSIVE CTE 변환")

        val convertedFeatures = mutableListOf<String>()
        if (hasLevel) convertedFeatures.add("LEVEL")
        if (hasSysConnectByPath) convertedFeatures.add("SYS_CONNECT_BY_PATH")
        if (hasConnectByRoot) convertedFeatures.add("CONNECT_BY_ROOT")
        if (hasConnectByIsLeaf) convertedFeatures.add("CONNECT_BY_ISLEAF")

        val featureMsg = if (convertedFeatures.isNotEmpty()) {
            " (${convertedFeatures.joinToString(", ")} 포함)"
        } else ""

        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "Oracle CONNECT BY가 WITH RECURSIVE로 변환되었습니다$featureMsg.",
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