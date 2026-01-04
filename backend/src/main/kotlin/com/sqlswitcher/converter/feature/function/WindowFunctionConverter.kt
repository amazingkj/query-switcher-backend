package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * 윈도우 함수(분석 함수) 변환기
 *
 * 지원 함수:
 * - ROW_NUMBER(), RANK(), DENSE_RANK()
 * - LEAD(), LAG()
 * - FIRST_VALUE(), LAST_VALUE(), NTH_VALUE()
 * - NTILE()
 * - SUM/AVG/COUNT/MIN/MAX OVER()
 *
 * 방언별 차이:
 * - Oracle: OVER (PARTITION BY ... ORDER BY ...)
 * - MySQL 8.0+: 동일 (8.0 미만은 미지원)
 * - PostgreSQL: 동일
 */
object WindowFunctionConverter {

    // 윈도우 함수 목록
    private val WINDOW_FUNCTIONS = setOf(
        "ROW_NUMBER", "RANK", "DENSE_RANK", "PERCENT_RANK", "CUME_DIST",
        "LEAD", "LAG", "FIRST_VALUE", "LAST_VALUE", "NTH_VALUE",
        "NTILE", "RATIO_TO_REPORT"
    )

    // 집계 함수 (OVER 절과 함께 사용 가능)
    private val AGGREGATE_FUNCTIONS = setOf(
        "SUM", "AVG", "COUNT", "MIN", "MAX", "STDDEV", "VARIANCE",
        "LISTAGG", "STRING_AGG", "GROUP_CONCAT"
    )

    // Oracle 전용 분석 함수
    private val ORACLE_ONLY_ANALYTIC = mapOf(
        "RATIO_TO_REPORT" to "Oracle 전용 함수입니다. SUM OVER()를 사용하여 수동 계산하세요.",
        "PERCENTILE_CONT" to "PERCENTILE_CONT 함수의 문법이 방언별로 다릅니다.",
        "PERCENTILE_DISC" to "PERCENTILE_DISC 함수의 문법이 방언별로 다릅니다.",
        "LISTAGG" to "집계 문자열 연결 함수 - 대상 DB에 맞게 변환됩니다."
    )

    // KEEP (DENSE_RANK FIRST/LAST) 패턴
    private val KEEP_PATTERN = Regex(
        """(\w+)\s*\(\s*([^)]*)\s*\)\s+KEEP\s*\(\s*DENSE_RANK\s+(FIRST|LAST)\s+ORDER\s+BY\s+([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    // WITHIN GROUP 패턴 (LISTAGG, PERCENTILE 등)
    private val WITHIN_GROUP_PATTERN = Regex(
        """(\w+)\s*\(\s*([^)]*)\s*\)\s+WITHIN\s+GROUP\s*\(\s*ORDER\s+BY\s+([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) return sql

        var result = sql

        // 1. Oracle KEEP (DENSE_RANK FIRST/LAST) 변환
        if (sourceDialect == DialectType.ORACLE) {
            result = convertKeepClause(result, targetDialect, warnings, appliedRules)
        }

        // 2. WITHIN GROUP 변환 (LISTAGG 등)
        result = convertWithinGroup(result, sourceDialect, targetDialect, warnings, appliedRules)

        // 3. Oracle RATIO_TO_REPORT 변환
        if (sourceDialect == DialectType.ORACLE && targetDialect != DialectType.ORACLE) {
            result = convertRatioToReport(result, targetDialect, warnings, appliedRules)
        }

        // 4. MySQL 8.0 미만 경고
        if (targetDialect == DialectType.MYSQL) {
            checkMySqlWindowFunctionSupport(sql, warnings)
        }

        // 5. FIRST_VALUE/LAST_VALUE IGNORE NULLS 변환
        result = convertIgnoreNulls(result, sourceDialect, targetDialect, warnings, appliedRules)

        return result
    }

    /**
     * Oracle KEEP (DENSE_RANK FIRST/LAST) 변환
     * MAX(col) KEEP (DENSE_RANK FIRST ORDER BY date) → 서브쿼리 또는 윈도우 함수 조합
     */
    private fun convertKeepClause(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!KEEP_PATTERN.containsMatchIn(sql)) return sql

        var result = sql
        result = KEEP_PATTERN.replace(result) { match ->
            @Suppress("UNUSED_VARIABLE") val aggFunc = match.groupValues[1].uppercase()
            val aggArg = match.groupValues[2]
            val firstOrLast = match.groupValues[3].uppercase()
            val orderBy = match.groupValues[4]

            when (targetDialect) {
                DialectType.POSTGRESQL -> {
                    // PostgreSQL: FIRST_VALUE/LAST_VALUE 사용
                    val windowFunc = if (firstOrLast == "FIRST") "FIRST_VALUE" else "LAST_VALUE"
                    "$windowFunc($aggArg) OVER (ORDER BY $orderBy)"
                }
                DialectType.MYSQL -> {
                    // MySQL: 서브쿼리 필요
                    warnings.add(ConversionWarning(
                        type = WarningType.PARTIAL_SUPPORT,
                        message = "KEEP (DENSE_RANK $firstOrLast)가 근사적으로 변환되었습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "MySQL에서는 서브쿼리나 변수를 사용한 수동 구현이 필요할 수 있습니다."
                    ))
                    val windowFunc = if (firstOrLast == "FIRST") "FIRST_VALUE" else "LAST_VALUE"
                    "$windowFunc($aggArg) OVER (ORDER BY $orderBy)"
                }
                else -> match.value
            }
        }

        if (result != sql) {
            appliedRules.add("KEEP (DENSE_RANK) → 윈도우 함수 변환")
        }

        return result
    }

    /**
     * WITHIN GROUP 변환
     */
    private fun convertWithinGroup(
        sql: String,
        @Suppress("UNUSED_PARAMETER") sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!WITHIN_GROUP_PATTERN.containsMatchIn(sql)) return sql

        var result = sql

        result = WITHIN_GROUP_PATTERN.replace(result) { match ->
            val funcName = match.groupValues[1].uppercase()
            val funcArgs = match.groupValues[2]
            val orderBy = match.groupValues[3]

            when (funcName) {
                "LISTAGG" -> {
                    when (targetDialect) {
                        DialectType.POSTGRESQL -> {
                            // STRING_AGG 사용
                            appliedRules.add("LISTAGG WITHIN GROUP → STRING_AGG 변환")
                            "STRING_AGG($funcArgs ORDER BY $orderBy)"
                        }
                        DialectType.MYSQL -> {
                            // GROUP_CONCAT 사용
                            val parts = funcArgs.split(",").map { it.trim() }
                            val column = parts.getOrNull(0) ?: funcArgs
                            val separator = parts.getOrNull(1) ?: "','"
                            appliedRules.add("LISTAGG WITHIN GROUP → GROUP_CONCAT 변환")
                            "GROUP_CONCAT($column ORDER BY $orderBy SEPARATOR $separator)"
                        }
                        else -> match.value
                    }
                }
                "PERCENTILE_CONT", "PERCENTILE_DISC" -> {
                    when (targetDialect) {
                        DialectType.POSTGRESQL -> {
                            // PostgreSQL도 WITHIN GROUP 지원
                            match.value
                        }
                        DialectType.MYSQL -> {
                            warnings.add(ConversionWarning(
                                type = WarningType.UNSUPPORTED_FUNCTION,
                                message = "$funcName 함수는 MySQL에서 직접 지원되지 않습니다.",
                                severity = WarningSeverity.WARNING,
                                suggestion = "서브쿼리를 사용한 백분위수 계산으로 대체하세요."
                            ))
                            "/* $funcName not supported */ NULL"
                        }
                        else -> match.value
                    }
                }
                else -> match.value
            }
        }

        return result
    }

    /**
     * Oracle RATIO_TO_REPORT 변환
     * RATIO_TO_REPORT(col) OVER (PARTITION BY grp) → col / SUM(col) OVER (PARTITION BY grp)
     */
    private fun convertRatioToReport(
        sql: String,
        @Suppress("UNUSED_PARAMETER") targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val ratioPattern = Regex(
            """RATIO_TO_REPORT\s*\(\s*(\w+)\s*\)\s+OVER\s*\(([^)]*)\)""",
            RegexOption.IGNORE_CASE
        )

        if (!ratioPattern.containsMatchIn(sql)) return sql

        val result = ratioPattern.replace(sql) { match ->
            val column = match.groupValues[1]
            val overClause = match.groupValues[2]
            appliedRules.add("RATIO_TO_REPORT → SUM OVER 기반 계산 변환")
            "$column / SUM($column) OVER ($overClause)"
        }

        if (result != sql) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "RATIO_TO_REPORT가 SUM OVER 기반 계산으로 변환되었습니다.",
                severity = WarningSeverity.INFO
            ))
        }

        return result
    }

    /**
     * MySQL 8.0 미만 윈도우 함수 지원 확인
     */
    private fun checkMySqlWindowFunctionSupport(sql: String, warnings: MutableList<ConversionWarning>) {
        val upperSql = sql.uppercase()

        val hasWindowFunction = WINDOW_FUNCTIONS.any { func ->
            Regex("""\b$func\s*\(""").containsMatchIn(upperSql)
        }

        val hasAnalyticOver = Regex("""\bOVER\s*\(""").containsMatchIn(upperSql)

        if (hasWindowFunction || hasAnalyticOver) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "윈도우 함수가 사용되었습니다.",
                severity = WarningSeverity.INFO,
                suggestion = "MySQL 8.0 이상에서만 윈도우 함수가 지원됩니다. 8.0 미만 버전에서는 서브쿼리나 변수를 사용해야 합니다."
            ))
        }
    }

    /**
     * FIRST_VALUE/LAST_VALUE IGNORE NULLS 변환
     */
    private fun convertIgnoreNulls(
        sql: String,
        @Suppress("UNUSED_PARAMETER") sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val ignoreNullsPattern = Regex(
            """(FIRST_VALUE|LAST_VALUE)\s*\(\s*([^)]+)\s*\)\s+IGNORE\s+NULLS""",
            RegexOption.IGNORE_CASE
        )

        if (!ignoreNullsPattern.containsMatchIn(sql)) return sql

        var result = sql

        when (targetDialect) {
            DialectType.MYSQL -> {
                // MySQL은 IGNORE NULLS 미지원 - COALESCE로 대체
                result = ignoreNullsPattern.replace(result) { match ->
                    val func = match.groupValues[1]
                    val arg = match.groupValues[2]
                    warnings.add(ConversionWarning(
                        type = WarningType.PARTIAL_SUPPORT,
                        message = "IGNORE NULLS는 MySQL에서 직접 지원되지 않습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "NULL 처리를 위한 추가 로직이 필요할 수 있습니다."
                    ))
                    "$func($arg) /* IGNORE NULLS not supported in MySQL */"
                }
                appliedRules.add("IGNORE NULLS → MySQL 호환 구문 변환")
            }
            DialectType.POSTGRESQL -> {
                // PostgreSQL은 IGNORE NULLS를 다르게 처리
                // 최신 버전에서는 지원하지만, 구버전은 미지원
                result = ignoreNullsPattern.replace(result) { match ->
                    val func = match.groupValues[1]
                    val arg = match.groupValues[2]
                    "$func($arg IGNORE NULLS)"
                }
            }
            else -> {}
        }

        return result
    }
}
