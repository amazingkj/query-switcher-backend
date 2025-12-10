package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.mapping.FunctionMappingRegistry
import com.sqlswitcher.converter.mapping.FunctionMappingRule
import com.sqlswitcher.converter.mapping.ParameterTransform
import org.springframework.stereotype.Service

/**
 * 함수 변환 서비스 - 모든 방언 간 SQL 함수 변환을 담당
 */
@Service
class FunctionConversionService(
    private val mappingRegistry: FunctionMappingRegistry
) {
    // 정규식 캐시 - 컴파일 비용 절감
    private val patternCache = mutableMapOf<String, Regex>()

    private fun getPattern(functionName: String): Regex {
        return patternCache.getOrPut(functionName) {
            Regex("\\b${functionName}\\s*\\(", RegexOption.IGNORE_CASE)
        }
    }

    // 자주 사용되는 정규식 상수 (컴파일 1회)
    companion object {
        private val SYSDATE_PATTERN = Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE)
        private val SYSTIMESTAMP_PATTERN = Regex("\\bSYSTIMESTAMP\\b", RegexOption.IGNORE_CASE)
        private val NOW_PATTERN = Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE)
        private val DECODE_PATTERN = Regex("""DECODE\s*\(\s*([^,]+)\s*,\s*(.+)\s*\)""", RegexOption.IGNORE_CASE)
        private val NVL2_PATTERN = Regex("""NVL2\s*\(\s*([^,]+)\s*,\s*([^,]+)\s*,\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)
        private val IF_PATTERN = Regex("""IF\s*\(\s*([^,]+)\s*,\s*([^,]+)\s*,\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)
        // Oracle 의사 컬럼
        private val ROWID_PATTERN = Regex("\\bROWID\\b", RegexOption.IGNORE_CASE)
        private val ROWNUM_PATTERN = Regex("\\bROWNUM\\b", RegexOption.IGNORE_CASE)
    }

    /**
     * SQL 내의 함수들을 타겟 방언으로 변환 (문자열 기반)
     */
    fun convertFunctionsInSql(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) {
            return sql
        }

        var result = sql
        val rules = mappingRegistry.getMappingsForDialects(sourceDialect, targetDialect)

        for (rule in rules) {
            val pattern = getPattern(rule.sourceFunction)
            if (pattern.containsMatchIn(result)) {
                // CASE WHEN 변환이 필요한 경우는 별도 처리
                if (rule.parameterTransform == ParameterTransform.TO_CASE_WHEN) {
                    result = convertToCaseWhen(result, rule, warnings, appliedRules)
                } else {
                    result = result.replace(pattern, "${rule.targetFunction}(")
                    appliedRules.add("${rule.sourceFunction}() → ${rule.targetFunction}()")
                }

                if (rule.warningType != null && rule.warningMessage != null) {
                    warnings.add(ConversionWarning(
                        type = rule.warningType,
                        message = rule.warningMessage,
                        severity = WarningSeverity.WARNING,
                        suggestion = rule.suggestion
                    ))
                }
            }
        }

        // 특수 변환: 파라미터 없는 함수들
        result = convertParameterlessFunctions(result, sourceDialect, targetDialect, appliedRules)

        // Oracle 의사 컬럼 변환 (ROWID, ROWNUM)
        result = convertOraclePseudoColumns(result, sourceDialect, targetDialect, warnings, appliedRules)

        return result
    }

    /**
     * Oracle ROWID, ROWNUM 의사 컬럼 변환
     */
    private fun convertOraclePseudoColumns(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (sourceDialect == DialectType.ORACLE) {
            // ROWID 변환
            if (ROWID_PATTERN.containsMatchIn(result)) {
                when (targetDialect) {
                    DialectType.POSTGRESQL -> {
                        result = ROWID_PATTERN.replace(result, "ctid")
                        warnings.add(ConversionWarning(
                            type = WarningType.PARTIAL_SUPPORT,
                            message = "Oracle ROWID가 PostgreSQL ctid로 변환되었습니다.",
                            severity = WarningSeverity.WARNING,
                            suggestion = "ctid는 ROWID와 다르게 VACUUM 후 변경될 수 있습니다. 가능하면 기본 키를 사용하세요."
                        ))
                        appliedRules.add("ROWID → ctid 변환")
                    }
                    DialectType.MYSQL -> {
                        warnings.add(ConversionWarning(
                            type = WarningType.UNSUPPORTED_FUNCTION,
                            message = "MySQL은 ROWID를 지원하지 않습니다.",
                            severity = WarningSeverity.ERROR,
                            suggestion = "기본 키(PRIMARY KEY) 또는 AUTO_INCREMENT 컬럼을 사용하세요."
                        ))
                        appliedRules.add("ROWID 감지됨 - MySQL 미지원")
                        // ROWID를 주석으로 표시
                        result = ROWID_PATTERN.replace(result, "/* ROWID - MySQL 미지원 */")
                    }
                    else -> {}
                }
            }

            // ROWNUM 변환
            if (ROWNUM_PATTERN.containsMatchIn(result)) {
                when (targetDialect) {
                    DialectType.POSTGRESQL -> {
                        // ROWNUM = n 또는 ROWNUM <= n 패턴 감지
                        val rownumConditionPattern = Regex(
                            """ROWNUM\s*(<=?|=)\s*(\d+)""",
                            RegexOption.IGNORE_CASE
                        )
                        val match = rownumConditionPattern.find(result)
                        if (match != null) {
                            val limit = match.groupValues[2]
                            result = rownumConditionPattern.replace(result, "")
                            // WHERE 절이 비어있으면 제거
                            result = result.replace(Regex("""WHERE\s+AND""", RegexOption.IGNORE_CASE), "WHERE")
                            result = result.replace(Regex("""WHERE\s*$""", RegexOption.IGNORE_CASE), "")
                            result = result.trimEnd() + " LIMIT $limit"
                            appliedRules.add("ROWNUM ≤ $limit → LIMIT $limit 변환")
                        } else {
                            warnings.add(ConversionWarning(
                                type = WarningType.MANUAL_REVIEW_NEEDED,
                                message = "복잡한 ROWNUM 사용이 감지되었습니다.",
                                severity = WarningSeverity.WARNING,
                                suggestion = "PostgreSQL에서는 ROW_NUMBER() 윈도우 함수 또는 LIMIT을 사용하세요."
                            ))
                            appliedRules.add("ROWNUM 감지됨 - 수동 변환 필요")
                        }
                    }
                    DialectType.MYSQL -> {
                        // ROWNUM = n 또는 ROWNUM <= n 패턴 감지
                        val rownumConditionPattern = Regex(
                            """ROWNUM\s*(<=?|=)\s*(\d+)""",
                            RegexOption.IGNORE_CASE
                        )
                        val match = rownumConditionPattern.find(result)
                        if (match != null) {
                            val limit = match.groupValues[2]
                            result = rownumConditionPattern.replace(result, "")
                            result = result.replace(Regex("""WHERE\s+AND""", RegexOption.IGNORE_CASE), "WHERE")
                            result = result.replace(Regex("""WHERE\s*$""", RegexOption.IGNORE_CASE), "")
                            result = result.trimEnd() + " LIMIT $limit"
                            appliedRules.add("ROWNUM ≤ $limit → LIMIT $limit 변환")
                        } else {
                            warnings.add(ConversionWarning(
                                type = WarningType.MANUAL_REVIEW_NEEDED,
                                message = "복잡한 ROWNUM 사용이 감지되었습니다.",
                                severity = WarningSeverity.WARNING,
                                suggestion = "MySQL에서는 ROW_NUMBER() 윈도우 함수 또는 LIMIT을 사용하세요."
                            ))
                            appliedRules.add("ROWNUM 감지됨 - 수동 변환 필요")
                        }
                    }
                    else -> {}
                }
            }
        }

        return result
    }

    /**
     * DECODE, NVL2, IF 등을 CASE WHEN으로 변환
     */
    private fun convertToCaseWhen(
        sql: String,
        rule: FunctionMappingRule,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql
        val functionName = rule.sourceFunction

        when (functionName.uppercase()) {
            "DECODE" -> {
                result = convertDecodeToCaseWhen(result)
                appliedRules.add("DECODE() → CASE WHEN 변환")
            }
            "NVL2" -> {
                result = convertNvl2ToCaseWhen(result)
                appliedRules.add("NVL2() → CASE WHEN 변환")
            }
            "IF" -> {
                result = convertIfToCaseWhen(result)
                appliedRules.add("IF() → CASE WHEN 변환")
            }
        }

        return result
    }

    /**
     * DECODE(expr, search1, result1, ..., default) → CASE expr WHEN search1 THEN result1 ... ELSE default END
     */
    private fun convertDecodeToCaseWhen(sql: String): String {
        return DECODE_PATTERN.replace(sql) { match ->
            val expr = match.groupValues[1].trim()
            val args = splitFunctionArgs(match.groupValues[2])

            if (args.size < 2) {
                return@replace match.value
            }

            val sb = StringBuilder("CASE $expr ")
            var i = 0
            while (i + 1 < args.size) {
                sb.append("WHEN ${args[i]} THEN ${args[i + 1]} ")
                i += 2
            }
            if (i < args.size) {
                sb.append("ELSE ${args[i]} ")
            }
            sb.append("END")
            sb.toString()
        }
    }

    /**
     * NVL2(expr, not_null_value, null_value) → CASE WHEN expr IS NOT NULL THEN not_null_value ELSE null_value END
     */
    private fun convertNvl2ToCaseWhen(sql: String): String {
        return NVL2_PATTERN.replace(sql) { match ->
            val expr = match.groupValues[1].trim()
            val notNullVal = match.groupValues[2].trim()
            val nullVal = match.groupValues[3].trim()
            "CASE WHEN $expr IS NOT NULL THEN $notNullVal ELSE $nullVal END"
        }
    }

    /**
     * IF(condition, true_val, false_val) → CASE WHEN condition THEN true_val ELSE false_val END
     */
    private fun convertIfToCaseWhen(sql: String): String {
        return IF_PATTERN.replace(sql) { match ->
            val condition = match.groupValues[1].trim()
            val trueVal = match.groupValues[2].trim()
            val falseVal = match.groupValues[3].trim()
            "CASE WHEN $condition THEN $trueVal ELSE $falseVal END"
        }
    }

    /**
     * 파라미터 없는 함수 변환 (SYSDATE, SYSTIMESTAMP 등)
     */
    private fun convertParameterlessFunctions(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (sourceDialect == DialectType.ORACLE) {
            when (targetDialect) {
                DialectType.MYSQL -> {
                    if (SYSDATE_PATTERN.containsMatchIn(result)) {
                        result = SYSDATE_PATTERN.replace(result, "NOW()")
                        appliedRules.add("SYSDATE → NOW()")
                    }
                    if (SYSTIMESTAMP_PATTERN.containsMatchIn(result)) {
                        result = SYSTIMESTAMP_PATTERN.replace(result, "CURRENT_TIMESTAMP")
                        appliedRules.add("SYSTIMESTAMP → CURRENT_TIMESTAMP")
                    }
                }
                DialectType.POSTGRESQL -> {
                    if (SYSDATE_PATTERN.containsMatchIn(result)) {
                        result = SYSDATE_PATTERN.replace(result, "CURRENT_TIMESTAMP")
                        appliedRules.add("SYSDATE → CURRENT_TIMESTAMP")
                    }
                    if (SYSTIMESTAMP_PATTERN.containsMatchIn(result)) {
                        result = SYSTIMESTAMP_PATTERN.replace(result, "CURRENT_TIMESTAMP")
                        appliedRules.add("SYSTIMESTAMP → CURRENT_TIMESTAMP")
                    }
                }
                else -> {}
            }
        }

        if (sourceDialect == DialectType.MYSQL) {
            when (targetDialect) {
                DialectType.ORACLE -> {
                    if (NOW_PATTERN.containsMatchIn(result)) {
                        result = NOW_PATTERN.replace(result, "SYSDATE")
                        appliedRules.add("NOW() → SYSDATE")
                    }
                }
                else -> {}
            }
        }

        return result
    }

    /**
     * 함수 인자를 콤마로 분리 (괄호 내부의 콤마는 무시)
     */
    private fun splitFunctionArgs(argsStr: String): List<String> {
        val args = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()

        for (char in argsStr) {
            when (char) {
                '(' -> {
                    depth++
                    current.append(char)
                }
                ')' -> {
                    depth--
                    current.append(char)
                }
                ',' -> {
                    if (depth == 0) {
                        args.add(current.toString().trim())
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            args.add(current.toString().trim())
        }

        return args
    }
}