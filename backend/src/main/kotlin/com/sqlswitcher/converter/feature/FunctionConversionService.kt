package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.mapping.FunctionMappingRegistry
import com.sqlswitcher.converter.mapping.ParameterTransform
import com.sqlswitcher.converter.feature.function.*
import com.sqlswitcher.converter.util.SqlParsingUtils
import org.springframework.stereotype.Service

/**
 * 함수 변환 서비스 - 모든 방언 간 SQL 함수 변환을 담당
 *
 * 리팩토링: 개별 변환 로직을 별도 클래스로 분리
 * - DecodeConverter: DECODE, NVL2, IF → CASE WHEN
 * - DateFunctionConverter: TO_CHAR, DATE_FORMAT 등
 * - StringConcatConverter: || ↔ CONCAT()
 * - OraclePseudoColumnConverter: ROWID, ROWNUM
 * - DateArithmeticConverter: ADD_MONTHS, MONTHS_BETWEEN 등
 * - PaginationConverter: OFFSET FETCH ↔ LIMIT
 * - HierarchicalQueryConverter: CONNECT BY → WITH RECURSIVE
 * - OracleJoinConverter: (+) 조인 → ANSI JOIN
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

    companion object {
        private val SYSDATE_PATTERN = Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE)
        private val SYSTIMESTAMP_PATTERN = Regex("\\bSYSTIMESTAMP\\b", RegexOption.IGNORE_CASE)
        private val NOW_PATTERN = Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE)
        private val TO_NUMBER_PATTERN = Regex("""TO_NUMBER\s*\(\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)
    }

    /**
     * SQL 내의 함수들을 타겟 방언으로 변환
     */
    fun convertFunctionsInSql(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) return sql

        var result = sql

        // 1. 매핑 레지스트리 기반 함수 변환
        result = convertMappedFunctions(result, sourceDialect, targetDialect, warnings, appliedRules)

        // 2. 파라미터 없는 함수 변환 (SYSDATE, NOW 등)
        result = convertParameterlessFunctions(result, sourceDialect, targetDialect, appliedRules)

        // 3. Oracle 의사 컬럼 변환 (ROWID, ROWNUM)
        result = OraclePseudoColumnConverter.convert(result, sourceDialect, targetDialect, warnings, appliedRules)

        // 4. 문자열 연결 변환 (|| ↔ CONCAT)
        result = StringConcatConverter.convert(result, sourceDialect, targetDialect, appliedRules)

        // 5. TO_NUMBER 변환
        result = convertToNumber(result, sourceDialect, targetDialect, appliedRules)

        // 6. 날짜 연산 변환
        result = DateArithmeticConverter.convert(result, sourceDialect, targetDialect, warnings, appliedRules)

        // 7. 페이징 구문 변환 (OFFSET FETCH ↔ LIMIT)
        result = PaginationConverter.convert(result, sourceDialect, targetDialect, appliedRules)

        // 8. 계층 쿼리 변환 (CONNECT BY)
        result = HierarchicalQueryConverter.convert(result, sourceDialect, targetDialect, warnings, appliedRules)

        return result
    }

    /**
     * 매핑 레지스트리 기반 함수 변환
     */
    private fun convertMappedFunctions(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql
        val rules = mappingRegistry.getMappingsForDialects(sourceDialect, targetDialect)

        for (rule in rules) {
            val pattern = getPattern(rule.sourceFunction)
            if (!pattern.containsMatchIn(result)) continue

            result = when (rule.parameterTransform) {
                ParameterTransform.TO_CASE_WHEN -> {
                    convertToCaseWhen(result, rule.sourceFunction, appliedRules)
                }
                ParameterTransform.DATE_FORMAT_CONVERT -> {
                    DateFunctionConverter.convertDateFormatFunction(result, rule, appliedRules)
                }
                ParameterTransform.SWAP_FIRST_TWO -> {
                    convertWithSwappedParams(result, rule.sourceFunction, rule.targetFunction, appliedRules)
                }
                else -> {
                    if (rule.sourceFunction.uppercase() != rule.targetFunction.uppercase()) {
                        appliedRules.add("${rule.sourceFunction}() → ${rule.targetFunction}()")
                        result.replace(pattern, "${rule.targetFunction}(")
                    } else result
                }
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

        return result
    }

    /**
     * DECODE, NVL2, IF 등을 CASE WHEN으로 변환
     */
    private fun convertToCaseWhen(
        sql: String,
        functionName: String,
        appliedRules: MutableList<String>
    ): String {
        return when (functionName.uppercase()) {
            "DECODE" -> {
                appliedRules.add("DECODE() → CASE WHEN 변환")
                DecodeConverter.convertDecodeToCaseWhen(sql)
            }
            "NVL2" -> {
                appliedRules.add("NVL2() → CASE WHEN 변환")
                DecodeConverter.convertNvl2ToCaseWhen(sql)
            }
            "IF" -> {
                appliedRules.add("IF() → CASE WHEN 변환")
                DecodeConverter.convertIfToCaseWhen(sql)
            }
            else -> sql
        }
    }

    /**
     * 파라미터 스왑 변환 (INSTR ↔ LOCATE 등)
     */
    private fun convertWithSwappedParams(
        sql: String,
        sourceFunction: String,
        targetFunction: String,
        appliedRules: MutableList<String>
    ): String {
        val functionStartPattern = Regex("""$sourceFunction\s*\(""", RegexOption.IGNORE_CASE)
        var result = sql

        var match = functionStartPattern.find(result)
        while (match != null) {
            val startIdx = match.range.first
            val argsStartIdx = match.range.last + 1

            val endIdx = SqlParsingUtils.findMatchingBracket(result, argsStartIdx)
            if (endIdx == -1) {
                match = functionStartPattern.find(result, match.range.last + 1)
                continue
            }

            val argsStr = result.substring(argsStartIdx, endIdx - 1)
            val args = SqlParsingUtils.splitFunctionArgs(argsStr)

            val swappedArgs = if (args.size >= 2) {
                listOf(args[1], args[0]) + args.drop(2)
            } else args

            val replacement = "$targetFunction(${swappedArgs.joinToString(", ")})"
            result = result.substring(0, startIdx) + replacement + result.substring(endIdx)
            appliedRules.add("$sourceFunction() → $targetFunction() (파라미터 순서 변환)")

            match = functionStartPattern.find(result, startIdx + replacement.length)
        }

        return result
    }

    /**
     * 파라미터 없는 함수 변환 (SYSDATE, SYSTIMESTAMP, NOW 등)
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

        if (sourceDialect == DialectType.MYSQL && targetDialect == DialectType.ORACLE) {
            if (NOW_PATTERN.containsMatchIn(result)) {
                result = NOW_PATTERN.replace(result, "SYSDATE")
                appliedRules.add("NOW() → SYSDATE")
            }
        }

        return result
    }

    /**
     * TO_NUMBER 변환
     */
    private fun convertToNumber(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql
        if (!TO_NUMBER_PATTERN.containsMatchIn(sql)) return sql

        return TO_NUMBER_PATTERN.replace(sql) { match ->
            val expr = match.groupValues[1].trim()
            when (targetDialect) {
                DialectType.MYSQL -> {
                    appliedRules.add("TO_NUMBER() → CAST(... AS DECIMAL) 변환")
                    "CAST($expr AS DECIMAL)"
                }
                DialectType.POSTGRESQL -> {
                    appliedRules.add("TO_NUMBER() → CAST(... AS NUMERIC) 변환")
                    "CAST($expr AS NUMERIC)"
                }
                else -> match.value
            }
        }
    }

    /**
     * 구식 Oracle JOIN (+) 변환 → ANSI JOIN
     */
    fun convertOracleJoinSyntax(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return OracleJoinConverter.convert(sql, sourceDialect, targetDialect, warnings, appliedRules)
    }
}