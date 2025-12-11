package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.util.SqlParsingUtils

/**
 * 문자열 연결 변환
 * Oracle/PostgreSQL: || ↔ MySQL: CONCAT()
 */
object StringConcatConverter {

    /**
     * 문자열 연결 변환 메인 함수
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // Oracle/PostgreSQL → MySQL: || → CONCAT()
        if ((sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.POSTGRESQL)
            && targetDialect == DialectType.MYSQL) {
            result = convertPipeConcatToConcat(result, appliedRules)
        }

        // MySQL → Oracle/PostgreSQL: CONCAT() → ||
        if (sourceDialect == DialectType.MYSQL &&
            (targetDialect == DialectType.ORACLE || targetDialect == DialectType.POSTGRESQL)) {
            result = convertConcatToPipeConcat(result, appliedRules)
        }

        return result
    }

    /**
     * || 연결을 CONCAT()으로 변환
     */
    private fun convertPipeConcatToConcat(sql: String, appliedRules: MutableList<String>): String {
        var result = sql
        var converted = false

        while (result.contains("||")) {
            val pipeIdx = result.indexOf("||")
            if (pipeIdx == -1) break

            val (beforePipe, beforeStartIdx) = SqlParsingUtils.extractExpressionBeforeWithIndex(result, pipeIdx)
            val (afterPipe, afterEndIdx) = SqlParsingUtils.extractExpressionAfterWithIndex(result, pipeIdx + 2)

            if (beforePipe.isEmpty() || afterPipe.isEmpty()) break

            val startIdx = beforeStartIdx
            var endIdx = afterEndIdx

            // 이어지는 || 연결 모두 수집
            val parts = mutableListOf(beforePipe.trim(), afterPipe.trim())
            var searchPos = endIdx

            while (true) {
                val remaining = result.substring(searchPos)
                val nextPipeMatch = Regex("""^\s*\|\|""").find(remaining)
                if (nextPipeMatch == null) break

                val actualPipeIdx = searchPos + nextPipeMatch.range.last - 1
                val (nextPart, nextEndIdx) = SqlParsingUtils.extractExpressionAfterWithIndex(result, actualPipeIdx + 2)
                if (nextPart.isEmpty()) break

                parts.add(nextPart.trim())
                endIdx = nextEndIdx
                searchPos = endIdx
            }

            val concatExpr = "CONCAT(${parts.joinToString(", ")})"
            result = result.substring(0, startIdx) + concatExpr + result.substring(endIdx)
            converted = true
        }

        if (converted) {
            appliedRules.add("|| 문자열 연결 → CONCAT() 변환")
        }

        return result
    }

    /**
     * CONCAT()을 || 연결로 변환
     */
    private fun convertConcatToPipeConcat(sql: String, appliedRules: MutableList<String>): String {
        val concatStartPattern = Regex("""CONCAT\s*\(""", RegexOption.IGNORE_CASE)
        var result = sql
        var converted = false

        var match = concatStartPattern.find(result)
        while (match != null) {
            val startIdx = match.range.first
            val argsStartIdx = match.range.last + 1

            val endIdx = SqlParsingUtils.findMatchingBracket(result, argsStartIdx)
            if (endIdx == -1) {
                match = concatStartPattern.find(result, match.range.last + 1)
                continue
            }

            val argsStr = result.substring(argsStartIdx, endIdx - 1)
            val args = SqlParsingUtils.splitFunctionArgs(argsStr)

            if (args.size > 1) {
                val pipeConcat = args.joinToString(" || ") { it.trim() }
                result = result.substring(0, startIdx) + pipeConcat + result.substring(endIdx)
                converted = true
                match = concatStartPattern.find(result, startIdx + pipeConcat.length)
            } else {
                match = concatStartPattern.find(result, match.range.last + 1)
            }
        }

        if (converted) {
            appliedRules.add("CONCAT() → || 문자열 연결 변환")
        }

        return result
    }
}