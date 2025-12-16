package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * Oracle 의사 컬럼 변환 (ROWID, ROWNUM)
 *
 * ROWNUM 변환 지원:
 * - WHERE ROWNUM <= n → LIMIT n
 * - WHERE ROWNUM = 1 → LIMIT 1
 * - SELECT ROWNUM → ROW_NUMBER() OVER ()
 * - 서브쿼리 ROWNUM 페이징 패턴 감지
 */
object OraclePseudoColumnConverter {

    private val ROWID_PATTERN = Regex("\\bROWID\\b", RegexOption.IGNORE_CASE)
    private val ROWNUM_PATTERN = Regex("\\bROWNUM\\b", RegexOption.IGNORE_CASE)

    // SELECT 절에서 ROWNUM 사용 패턴 (별칭 포함)
    private val ROWNUM_IN_SELECT_PATTERN = Regex(
        """(\bROWNUM\b)(\s+(?:AS\s+)?(\w+))?""",
        RegexOption.IGNORE_CASE
    )

    // ROWNUM 범위 조건 (페이징용)
    private val ROWNUM_BETWEEN_PATTERN = Regex(
        """ROWNUM\s+BETWEEN\s+(\d+)\s+AND\s+(\d+)""",
        RegexOption.IGNORE_CASE
    )

    // ROWNUM > n 패턴
    private val ROWNUM_GREATER_PATTERN = Regex(
        """ROWNUM\s*>\s*(\d+)""",
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

        var result = sql
        result = convertRowId(result, targetDialect, warnings, appliedRules)
        result = convertRowNum(result, targetDialect, warnings, appliedRules)
        return result
    }

    private fun convertRowId(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!ROWID_PATTERN.containsMatchIn(sql)) return sql

        return when (targetDialect) {
            DialectType.POSTGRESQL -> {
                warnings.add(ConversionWarning(
                    type = WarningType.PARTIAL_SUPPORT,
                    message = "Oracle ROWID가 PostgreSQL ctid로 변환되었습니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "ctid는 ROWID와 다르게 VACUUM 후 변경될 수 있습니다. 가능하면 기본 키를 사용하세요."
                ))
                appliedRules.add("ROWID → ctid 변환")
                ROWID_PATTERN.replace(sql, "ctid")
            }
            DialectType.MYSQL -> {
                warnings.add(ConversionWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "MySQL은 ROWID를 지원하지 않습니다.",
                    severity = WarningSeverity.ERROR,
                    suggestion = "기본 키(PRIMARY KEY) 또는 AUTO_INCREMENT 컬럼을 사용하세요."
                ))
                appliedRules.add("ROWID 감지됨 - MySQL 미지원")
                ROWID_PATTERN.replace(sql, "/* ROWID - MySQL 미지원 */")
            }
            else -> sql
        }
    }

    private fun convertRowNum(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!ROWNUM_PATTERN.containsMatchIn(sql)) return sql

        if (targetDialect != DialectType.POSTGRESQL && targetDialect != DialectType.MYSQL) return sql

        var result = sql

        // 1. SELECT 절의 ROWNUM을 ROW_NUMBER() OVER()로 변환
        result = convertRownumInSelect(result, targetDialect, appliedRules)

        // 2. WHERE 절의 ROWNUM 조건 처리
        result = convertRownumInWhere(result, targetDialect, warnings, appliedRules)

        return result
    }

    /**
     * SELECT 절에서 ROWNUM을 ROW_NUMBER() OVER()로 변환
     */
    private fun convertRownumInSelect(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        // SELECT 절 추출
        val selectPattern = Regex(
            """SELECT\s+(.*?)\s+FROM""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val selectMatch = selectPattern.find(sql) ?: return sql
        val selectClause = selectMatch.groupValues[1]

        // SELECT 절에 ROWNUM이 있는지 확인
        if (!ROWNUM_PATTERN.containsMatchIn(selectClause)) return sql

        // ROWNUM → ROW_NUMBER() OVER() 변환 (별칭 유지)
        val rownumWithAliasPattern = Regex(
            """\bROWNUM\b(\s+(?:AS\s+)?(\w+))?""",
            RegexOption.IGNORE_CASE
        )

        val newSelectClause = rownumWithAliasPattern.replace(selectClause) { match ->
            val alias = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
            if (alias != null) {
                appliedRules.add("ROWNUM $alias → ROW_NUMBER() OVER() AS $alias 변환")
                "ROW_NUMBER() OVER() AS $alias"
            } else {
                appliedRules.add("ROWNUM → ROW_NUMBER() OVER() 변환")
                "ROW_NUMBER() OVER() AS rn"
            }
        }

        return sql.replace(selectClause, newSelectClause)
    }

    /**
     * WHERE 절의 ROWNUM 조건 처리
     */
    private fun convertRownumInWhere(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // ROWNUM 조건 패턴들
        val rownumWithAndPattern = Regex("""\s+AND\s+ROWNUM\s*(<=?|=)\s*(\d+)""", RegexOption.IGNORE_CASE)
        val rownumAtStartPattern = Regex("""ROWNUM\s*(<=?|=)\s*(\d+)\s+AND\s+""", RegexOption.IGNORE_CASE)
        val rownumOnlyPattern = Regex("""WHERE\s+ROWNUM\s*(<=?|=)\s*(\d+)""", RegexOption.IGNORE_CASE)
        val rownumWhereOnlyPattern = Regex("""ROWNUM\s*(<=?|=)\s*(\d+)""", RegexOption.IGNORE_CASE)

        // WHERE 절에 ROWNUM이 있는지 확인 (SELECT 절 제외)
        val wherePattern = Regex("""WHERE\s+(.*)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val whereMatch = wherePattern.find(result)

        if (whereMatch == null || !ROWNUM_PATTERN.containsMatchIn(whereMatch.value)) {
            return result
        }

        var limit: String? = null
        var offset: String? = null

        // ROWNUM BETWEEN n AND m 패턴 (페이징)
        val betweenMatch = ROWNUM_BETWEEN_PATTERN.find(result)
        if (betweenMatch != null) {
            offset = (betweenMatch.groupValues[1].toInt() - 1).toString()
            limit = (betweenMatch.groupValues[2].toInt() - betweenMatch.groupValues[1].toInt() + 1).toString()
            result = result.replace(betweenMatch.value, "1=1")
            appliedRules.add("ROWNUM BETWEEN → LIMIT $limit OFFSET $offset 변환")
        } else {
            // AND ROWNUM <= n 패턴 (조건 끝에 있는 경우)
            val matchWithAnd = rownumWithAndPattern.find(result)
            if (matchWithAnd != null) {
                limit = matchWithAnd.groupValues[2]
                result = rownumWithAndPattern.replace(result, "")
            } else {
                // ROWNUM <= n AND 패턴 (조건 시작에 있는 경우)
                val matchAtStart = rownumAtStartPattern.find(result)
                if (matchAtStart != null) {
                    limit = matchAtStart.groupValues[2]
                    result = rownumAtStartPattern.replace(result, "")
                } else {
                    // WHERE ROWNUM <= n 패턴 (단독)
                    val matchWhereOnly = rownumOnlyPattern.find(result)
                    if (matchWhereOnly != null) {
                        limit = matchWhereOnly.groupValues[2]
                        result = result.replace(matchWhereOnly.value, "")
                    } else {
                        // 그 외 ROWNUM <= n 패턴
                        val matchOnly = rownumWhereOnlyPattern.find(result)
                        if (matchOnly != null && whereMatch.value.contains(matchOnly.value)) {
                            limit = matchOnly.groupValues[2]
                            result = result.replace(matchOnly.value, "1=1")
                        }
                    }
                }
            }
        }

        if (limit != null) {
            // 빈 WHERE 절 정리
            result = result.replace(Regex("""\s+WHERE\s+ORDER\b""", RegexOption.IGNORE_CASE), " ORDER")
            result = result.replace(Regex("""\s+WHERE\s+GROUP\b""", RegexOption.IGNORE_CASE), " GROUP")
            result = result.replace(Regex("""\s+WHERE\s+1\s*=\s*1\s*(ORDER|GROUP|$)""", RegexOption.IGNORE_CASE)) { m ->
                " " + m.groupValues[1]
            }
            result = result.replace(Regex("""\s+WHERE\s*;""", RegexOption.IGNORE_CASE), ";")
            result = result.replace(Regex("""\s+WHERE\s*$""", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("""\s{2,}"""), " ")

            // LIMIT/OFFSET 추가
            result = result.trimEnd()
            if (offset != null && offset != "0") {
                result += " LIMIT $limit OFFSET $offset"
                appliedRules.add("ROWNUM 페이징 → LIMIT $limit OFFSET $offset 변환")
            } else {
                result += " LIMIT $limit"
                appliedRules.add("ROWNUM ≤ $limit → LIMIT $limit 변환")
            }
        } else if (ROWNUM_PATTERN.containsMatchIn(result)) {
            // 아직 변환되지 않은 ROWNUM이 있는 경우
            warnings.add(ConversionWarning(
                type = WarningType.MANUAL_REVIEW_NEEDED,
                message = "복잡한 ROWNUM 사용이 감지되었습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "${targetDialect.name}에서는 ROW_NUMBER() 윈도우 함수 또는 LIMIT/OFFSET을 사용하세요."
            ))
            appliedRules.add("ROWNUM 감지됨 - 수동 변환 필요")
        }

        return result
    }
}