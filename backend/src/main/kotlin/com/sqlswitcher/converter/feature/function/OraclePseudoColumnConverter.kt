package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * Oracle 의사 컬럼 변환 (ROWID, ROWNUM)
 */
object OraclePseudoColumnConverter {

    private val ROWID_PATTERN = Regex("\\bROWID\\b", RegexOption.IGNORE_CASE)
    private val ROWNUM_PATTERN = Regex("\\bROWNUM\\b", RegexOption.IGNORE_CASE)

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

        // ROWNUM 조건 패턴들
        val rownumWithAndPattern = Regex("""\s+AND\s+ROWNUM\s*(<=?|=)\s*(\d+)""", RegexOption.IGNORE_CASE)
        val rownumAtStartPattern = Regex("""ROWNUM\s*(<=?|=)\s*(\d+)\s+AND\s+""", RegexOption.IGNORE_CASE)
        val rownumOnlyPattern = Regex("""ROWNUM\s*(<=?|=)\s*(\d+)""", RegexOption.IGNORE_CASE)

        var limit: String? = null

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
                // 단독 ROWNUM <= n 패턴
                val matchOnly = rownumOnlyPattern.find(result)
                if (matchOnly != null) {
                    limit = matchOnly.groupValues[2]
                    result = rownumOnlyPattern.replace(result, "")
                }
            }
        }

        if (limit != null) {
            // 빈 WHERE 절 정리
            result = result.replace(Regex("""\s+WHERE\s+ORDER\b""", RegexOption.IGNORE_CASE), " ORDER")
            result = result.replace(Regex("""\s+WHERE\s+GROUP\b""", RegexOption.IGNORE_CASE), " GROUP")
            result = result.replace(Regex("""\s+WHERE\s*;""", RegexOption.IGNORE_CASE), ";")
            result = result.replace(Regex("""\s+WHERE\s*$""", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("""\s{2,}"""), " ")
            result = result.trimEnd() + " LIMIT $limit"
            appliedRules.add("ROWNUM ≤ $limit → LIMIT $limit 변환")
        } else {
            warnings.add(ConversionWarning(
                type = WarningType.MANUAL_REVIEW_NEEDED,
                message = "복잡한 ROWNUM 사용이 감지되었습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "${targetDialect.name}에서는 ROW_NUMBER() 윈도우 함수 또는 LIMIT을 사용하세요."
            ))
            appliedRules.add("ROWNUM 감지됨 - 수동 변환 필요")
        }

        return result
    }
}