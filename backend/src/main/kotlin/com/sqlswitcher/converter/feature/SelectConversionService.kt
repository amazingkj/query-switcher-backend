package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.core.*
import org.springframework.stereotype.Service

/**
 * SELECT 문 변환 서비스
 */
@Service
class SelectConversionService(
    private val functionService: FunctionConversionService
) {

    /**
     * SELECT 문 변환
     */
    fun convertSelect(
        selectSql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) {
            return selectSql
        }

        var result = selectSql

        // 함수 변환
        result = functionService.convertFunctionsInSql(result, sourceDialect, targetDialect, warnings, appliedRules)

        // ROWNUM 변환
        result = convertRownum(result, sourceDialect, targetDialect, warnings, appliedRules)

        // DUAL 테이블 변환
        result = convertDualTable(result, sourceDialect, targetDialect, appliedRules)

        // 인용문자 변환
        result = convertQuoteCharacters(result, targetDialect, appliedRules)

        // DECODE → CASE WHEN
        result = convertDecodeToCase(result, sourceDialect, targetDialect, warnings, appliedRules)

        // NVL2 → CASE WHEN
        result = convertNvl2ToCase(result, sourceDialect, targetDialect, warnings, appliedRules)

        return result
    }

    /**
     * ROWNUM → LIMIT/ROW_NUMBER() 변환
     */
    private fun convertRownum(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE && sourceDialect != DialectType.TIBERO) {
            return sql
        }
        if (targetDialect == DialectType.ORACLE || targetDialect == DialectType.TIBERO) {
            return sql
        }

        var result = sql

        // WHERE ROWNUM <= n
        val rownumLePattern = Regex("""WHERE\s+ROWNUM\s*<=?\s*(\d+)""", RegexOption.IGNORE_CASE)
        val leMatch = rownumLePattern.find(result)
        if (leMatch != null) {
            val limitValue = leMatch.groupValues[1].toIntOrNull() ?: 10
            result = result.replace(leMatch.value, "")
            result = result.replace(Regex("\\bWHERE\\s+AND\\b", RegexOption.IGNORE_CASE), "WHERE")
            result = result.trim()
            if (result.uppercase().endsWith("WHERE")) {
                result = result.dropLast(5).trim()
            }
            result = "$result LIMIT $limitValue"
            appliedRules.add("ROWNUM <= $limitValue → LIMIT $limitValue")
        }

        // AND ROWNUM <= n
        val andRownumPattern = Regex("""AND\s+ROWNUM\s*<=?\s*(\d+)""", RegexOption.IGNORE_CASE)
        val andMatch = andRownumPattern.find(result)
        if (andMatch != null) {
            val limitValue = andMatch.groupValues[1].toIntOrNull() ?: 10
            result = result.replace(andMatch.value, "")
            result = "$result LIMIT $limitValue"
            appliedRules.add("AND ROWNUM <= $limitValue → LIMIT $limitValue")
        }

        // SELECT 절의 ROWNUM
        if (result.uppercase().contains("SELECT") && result.uppercase().contains("ROWNUM") &&
            !result.uppercase().contains("WHERE ROWNUM")) {
            result = result.replace(Regex("\\bROWNUM\\b(?!\\s*<=?|\\s*=)", RegexOption.IGNORE_CASE), "ROW_NUMBER() OVER() AS rn")
            appliedRules.add("SELECT ROWNUM → ROW_NUMBER() OVER()")
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "ROWNUM은 ROW_NUMBER() OVER()로 변환되었습니다. ORDER BY 절을 확인하세요.",
                severity = WarningSeverity.WARNING
            ))
        }

        return result
    }

    /**
     * DUAL 테이블 변환
     */
    private fun convertDualTable(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE && sourceDialect != DialectType.TIBERO) {
            return sql
        }

        var result = sql

        when (targetDialect) {
            DialectType.MYSQL -> {
                // MySQL도 DUAL 지원하지만 생략 가능
                // 유지하거나 제거 모두 가능
            }
            DialectType.POSTGRESQL -> {
                // PostgreSQL은 DUAL 불필요
                result = result.replace(Regex("\\s+FROM\\s+DUAL\\b", RegexOption.IGNORE_CASE), "")
                if (result != sql) {
                    appliedRules.add("FROM DUAL 제거 (PostgreSQL)")
                }
            }
            else -> {}
        }

        return result
    }

    /**
     * 인용문자 변환
     */
    private fun convertQuoteCharacters(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.MYSQL -> {
                // " → ` (문자열 리터럴 제외)
                result = convertQuotesOutsideStrings(result, '"', '`')
                appliedRules.add("인용문자 → 백틱(`) 변환")
            }
            DialectType.ORACLE, DialectType.POSTGRESQL, DialectType.TIBERO -> {
                // ` → "
                result = convertQuotesOutsideStrings(result, '`', '"')
                appliedRules.add("인용문자 → 큰따옴표(\") 변환")
            }
        }

        return result
    }

    /**
     * 문자열 리터럴 외부의 인용문자만 변환
     */
    private fun convertQuotesOutsideStrings(sql: String, from: Char, to: Char): String {
        val sb = StringBuilder()
        var inString = false
        var stringChar = ' '

        for (char in sql) {
            when {
                !inString && char == '\'' -> {
                    inString = true
                    stringChar = char
                    sb.append(char)
                }
                inString && char == stringChar -> {
                    inString = false
                    sb.append(char)
                }
                !inString && char == from -> {
                    sb.append(to)
                }
                else -> {
                    sb.append(char)
                }
            }
        }

        return sb.toString()
    }

    /**
     * DECODE → CASE WHEN 변환
     */
    private fun convertDecodeToCase(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE && sourceDialect != DialectType.TIBERO) {
            return sql
        }
        if (targetDialect == DialectType.ORACLE || targetDialect == DialectType.TIBERO) {
            return sql
        }

        var result = sql
        val decodePattern = Regex("\\bDECODE\\s*\\(", RegexOption.IGNORE_CASE)
        var match = decodePattern.find(result)

        while (match != null) {
            val startIndex = match.range.first
            val argsStart = match.range.last + 1
            val args = extractFunctionArguments(result, argsStart - 1)
            val endIndex = findMatchingParenthesis(result, argsStart - 1)

            if (args.isNotEmpty() && endIndex > argsStart) {
                val caseExpr = buildCaseFromDecode(args)
                result = result.substring(0, startIndex) + caseExpr + result.substring(endIndex + 1)
                appliedRules.add("DECODE() → CASE WHEN 변환")
            }

            match = decodePattern.find(result, startIndex + 1)
        }

        return result
    }

    /**
     * DECODE 인자를 CASE WHEN으로 변환
     */
    private fun buildCaseFromDecode(args: List<String>): String {
        if (args.isEmpty()) return "NULL"

        val expr = args[0].trim()
        val sb = StringBuilder("CASE ")

        var i = 1
        while (i + 1 < args.size) {
            val search = args[i].trim()
            val resultVal = args[i + 1].trim()

            if (search.uppercase() == "NULL") {
                sb.append("WHEN $expr IS NULL THEN $resultVal ")
            } else {
                sb.append("WHEN $expr = $search THEN $resultVal ")
            }
            i += 2
        }

        // 마지막 하나가 남으면 default
        if (i < args.size) {
            sb.append("ELSE ${args[i].trim()} ")
        }

        sb.append("END")
        return sb.toString()
    }

    /**
     * NVL2 → CASE WHEN 변환
     */
    private fun convertNvl2ToCase(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE && sourceDialect != DialectType.TIBERO) {
            return sql
        }
        if (targetDialect == DialectType.ORACLE || targetDialect == DialectType.TIBERO) {
            return sql
        }

        var result = sql
        val nvl2Pattern = Regex("\\bNVL2\\s*\\(", RegexOption.IGNORE_CASE)
        var match = nvl2Pattern.find(result)

        while (match != null) {
            val startIndex = match.range.first
            val argsStart = match.range.last + 1
            val args = extractFunctionArguments(result, argsStart - 1)
            val endIndex = findMatchingParenthesis(result, argsStart - 1)

            if (args.size >= 3 && endIndex > argsStart) {
                val expr = args[0].trim()
                val notNullVal = args[1].trim()
                val nullVal = args[2].trim()
                val caseExpr = "CASE WHEN $expr IS NOT NULL THEN $notNullVal ELSE $nullVal END"
                result = result.substring(0, startIndex) + caseExpr + result.substring(endIndex + 1)
                appliedRules.add("NVL2() → CASE WHEN 변환")
            }

            match = nvl2Pattern.find(result, startIndex + 1)
        }

        return result
    }

    /**
     * 함수 인자 추출 (중첩 괄호 처리)
     */
    private fun extractFunctionArguments(sql: String, openParenIndex: Int): List<String> {
        val args = mutableListOf<String>()
        var depth = 0
        var currentArg = StringBuilder()
        var inString = false
        var stringChar = ' '

        for (i in openParenIndex until sql.length) {
            val c = sql[i]

            if ((c == '\'' || c == '"') && (i == 0 || sql[i - 1] != '\\')) {
                if (!inString) {
                    inString = true
                    stringChar = c
                } else if (c == stringChar) {
                    inString = false
                }
            }

            if (!inString) {
                when (c) {
                    '(' -> {
                        depth++
                        if (depth > 1) currentArg.append(c)
                    }
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            if (currentArg.isNotBlank()) {
                                args.add(currentArg.toString().trim())
                            }
                            return args
                        }
                        currentArg.append(c)
                    }
                    ',' -> {
                        if (depth == 1) {
                            args.add(currentArg.toString().trim())
                            currentArg = StringBuilder()
                        } else {
                            currentArg.append(c)
                        }
                    }
                    else -> {
                        if (depth >= 1) currentArg.append(c)
                    }
                }
            } else {
                if (depth >= 1) currentArg.append(c)
            }
        }

        return args
    }

    /**
     * 매칭되는 닫는 괄호 위치 찾기
     */
    private fun findMatchingParenthesis(sql: String, openParenIndex: Int): Int {
        var depth = 0
        var inString = false
        var stringChar = ' '

        for (i in openParenIndex until sql.length) {
            val c = sql[i]

            if ((c == '\'' || c == '"') && (i == 0 || sql[i - 1] != '\\')) {
                if (!inString) {
                    inString = true
                    stringChar = c
                } else if (c == stringChar) {
                    inString = false
                }
            }

            if (!inString) {
                when (c) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
        }

        return -1
    }
}