package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * CTE (Common Table Expression) 변환기
 *
 * 지원 기능:
 * - WITH 절 기본 구문
 * - WITH RECURSIVE (재귀 CTE)
 * - 다중 CTE
 * - Oracle의 서브쿼리 팩토링
 *
 * 방언별 차이:
 * - Oracle 11g+: WITH 지원, RECURSIVE 키워드 없이 재귀 가능
 * - MySQL 8.0+: WITH RECURSIVE 필수 (8.0 미만은 CTE 미지원)
 * - PostgreSQL: WITH RECURSIVE 지원
 */
object CteConverter {

    // Oracle 재귀 CTE 패턴 (RECURSIVE 키워드 없음)
    private val ORACLE_RECURSIVE_CTE_PATTERN = Regex(
        """WITH\s+(\w+)\s*(?:\([^)]+\))?\s+AS\s*\(\s*SELECT.+UNION\s+ALL\s+SELECT.+FROM\s+\1\b""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // MySQL/PostgreSQL 재귀 CTE 패턴
    private val RECURSIVE_CTE_PATTERN = Regex(
        """WITH\s+RECURSIVE\s+""",
        RegexOption.IGNORE_CASE
    )

    // 기본 WITH 절 패턴
    private val WITH_PATTERN = Regex(
        """WITH\s+(?!RECURSIVE\b)(\w+)\s*(?:\([^)]+\))?\s+AS\s*\(""",
        RegexOption.IGNORE_CASE
    )

    // Oracle SEARCH/CYCLE 절 패턴
    private val SEARCH_PATTERN = Regex(
        """SEARCH\s+(DEPTH|BREADTH)\s+FIRST\s+BY\s+([^)]+)\s+SET\s+(\w+)""",
        RegexOption.IGNORE_CASE
    )

    private val CYCLE_PATTERN = Regex(
        """CYCLE\s+(\w+)\s+SET\s+(\w+)\s+TO\s+'([^']+)'\s+DEFAULT\s+'([^']+)'""",
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

        // 1. Oracle → MySQL/PostgreSQL: 재귀 CTE에 RECURSIVE 키워드 추가
        if (sourceDialect == DialectType.ORACLE && targetDialect != DialectType.ORACLE) {
            result = convertOracleRecursiveCte(result, targetDialect, warnings, appliedRules)
        }

        // 2. MySQL/PostgreSQL → Oracle: RECURSIVE 키워드 제거
        if (sourceDialect != DialectType.ORACLE && targetDialect == DialectType.ORACLE) {
            result = convertToOracleCte(result, warnings, appliedRules)
        }

        // 3. Oracle SEARCH/CYCLE 절 변환
        if (sourceDialect == DialectType.ORACLE && targetDialect != DialectType.ORACLE) {
            result = convertSearchCycle(result, targetDialect, warnings, appliedRules)
        }

        // 4. MySQL 8.0 미만 경고
        if (targetDialect == DialectType.MYSQL && WITH_PATTERN.containsMatchIn(sql)) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "CTE(WITH 절)가 사용되었습니다.",
                severity = WarningSeverity.INFO,
                suggestion = "MySQL 8.0 이상에서만 CTE가 지원됩니다. 8.0 미만 버전에서는 서브쿼리나 임시 테이블을 사용해야 합니다."
            ))
        }

        return result
    }

    /**
     * Oracle 재귀 CTE → MySQL/PostgreSQL WITH RECURSIVE 변환
     */
    private fun convertOracleRecursiveCte(
        sql: String,
        @Suppress("UNUSED_PARAMETER") targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // 이미 RECURSIVE가 있으면 스킵
        if (RECURSIVE_CTE_PATTERN.containsMatchIn(sql)) return sql

        // 재귀 CTE인지 확인 (UNION ALL + 자기 참조)
        if (!isRecursiveCte(sql)) return sql

        // WITH → WITH RECURSIVE 변환
        val result = sql.replaceFirst(
            Regex("""WITH\s+(?!RECURSIVE\b)""", RegexOption.IGNORE_CASE),
            "WITH RECURSIVE "
        )

        if (result != sql) {
            appliedRules.add("Oracle 재귀 CTE → WITH RECURSIVE 변환")
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "Oracle 재귀 CTE가 WITH RECURSIVE로 변환되었습니다.",
                severity = WarningSeverity.INFO
            ))
        }

        return result
    }

    /**
     * MySQL/PostgreSQL → Oracle CTE 변환
     */
    private fun convertToOracleCte(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!RECURSIVE_CTE_PATTERN.containsMatchIn(sql)) return sql

        // WITH RECURSIVE → WITH 변환
        val result = sql.replaceFirst(
            Regex("""WITH\s+RECURSIVE\s+""", RegexOption.IGNORE_CASE),
            "WITH "
        )

        if (result != sql) {
            appliedRules.add("WITH RECURSIVE → Oracle WITH 변환")
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "WITH RECURSIVE가 Oracle 호환 WITH로 변환되었습니다.",
                severity = WarningSeverity.INFO,
                suggestion = "Oracle 11g Release 2 이상에서 재귀 CTE가 지원됩니다."
            ))
        }

        return result
    }

    /**
     * Oracle SEARCH/CYCLE 절 변환
     * PostgreSQL은 SEARCH/CYCLE 지원, MySQL은 미지원
     */
    private fun convertSearchCycle(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // SEARCH 절 처리
        if (SEARCH_PATTERN.containsMatchIn(sql)) {
            when (targetDialect) {
                DialectType.POSTGRESQL -> {
                    // PostgreSQL도 SEARCH 지원 (PostgreSQL 14+)
                    warnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "SEARCH 절이 사용되었습니다.",
                        severity = WarningSeverity.INFO,
                        suggestion = "PostgreSQL 14 이상에서 SEARCH 절이 지원됩니다."
                    ))
                }
                DialectType.MYSQL -> {
                    // MySQL은 SEARCH 미지원
                    result = SEARCH_PATTERN.replace(result) { match ->
                        val searchType = match.groupValues[1]
                        val searchCol = match.groupValues[2]
                        val setCol = match.groupValues[3]
                        warnings.add(ConversionWarning(
                            type = WarningType.UNSUPPORTED_FUNCTION,
                            message = "SEARCH $searchType FIRST 절은 MySQL에서 지원되지 않습니다.",
                            severity = WarningSeverity.WARNING,
                            suggestion = "ORDER BY를 사용하거나 애플리케이션 레벨에서 정렬하세요."
                        ))
                        "/* SEARCH $searchType FIRST BY $searchCol SET $setCol - not supported in MySQL */"
                    }
                    appliedRules.add("SEARCH 절 제거 (MySQL 미지원)")
                }
                else -> {}
            }
        }

        // CYCLE 절 처리
        if (CYCLE_PATTERN.containsMatchIn(sql)) {
            when (targetDialect) {
                DialectType.POSTGRESQL -> {
                    // PostgreSQL도 CYCLE 지원 (PostgreSQL 14+)
                    warnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "CYCLE 절이 사용되었습니다.",
                        severity = WarningSeverity.INFO,
                        suggestion = "PostgreSQL 14 이상에서 CYCLE 절이 지원됩니다."
                    ))
                }
                DialectType.MYSQL -> {
                    // MySQL은 CYCLE 미지원
                    result = CYCLE_PATTERN.replace(result) { match ->
                        val cycleCol = match.groupValues[1]
                        val setCol = match.groupValues[2]
                        warnings.add(ConversionWarning(
                            type = WarningType.UNSUPPORTED_FUNCTION,
                            message = "CYCLE 절은 MySQL에서 지원되지 않습니다.",
                            severity = WarningSeverity.WARNING,
                            suggestion = "순환 감지를 위한 별도 로직을 추가하세요 (경로 배열 또는 깊이 제한)."
                        ))
                        "/* CYCLE $cycleCol SET $setCol - not supported in MySQL */"
                    }
                    appliedRules.add("CYCLE 절 제거 (MySQL 미지원)")
                }
                else -> {}
            }
        }

        return result
    }

    /**
     * 재귀 CTE인지 확인
     * UNION ALL과 자기 참조가 있으면 재귀 CTE
     */
    private fun isRecursiveCte(sql: String): Boolean {
        val upperSql = sql.uppercase()

        // WITH 절이 있는지 확인
        if (!upperSql.contains("WITH")) return false

        // UNION ALL이 있는지 확인
        if (!upperSql.contains("UNION ALL") && !upperSql.contains("UNION")) return false

        // CTE 이름 추출
        val cteNameMatch = WITH_PATTERN.find(sql)
        val cteName = cteNameMatch?.groupValues?.get(1) ?: return false

        // CTE 정의 내에서 자기 참조가 있는지 확인
        val ctePattern = Regex(
            """WITH\s+$cteName\s*(?:\([^)]+\))?\s+AS\s*\((.+?)\)(?:\s*,|\s*SELECT|\s*$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val cteMatch = ctePattern.find(sql)
        val cteBody = cteMatch?.groupValues?.get(1) ?: return false

        // CTE 본문에서 자기 참조 확인
        val selfRefPattern = Regex("""\bFROM\s+$cteName\b|\bJOIN\s+$cteName\b""", RegexOption.IGNORE_CASE)
        return selfRefPattern.containsMatchIn(cteBody)
    }
}
