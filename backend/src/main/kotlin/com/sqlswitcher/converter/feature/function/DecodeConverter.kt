package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.util.SqlParsingUtils

/**
 * DECODE, NVL2, IF 함수를 CASE WHEN으로 변환
 */
object DecodeConverter {

    private val DECODE_START_PATTERN = Regex("""DECODE\s*\(""", RegexOption.IGNORE_CASE)
    private val NVL2_PATTERN = Regex("""NVL2\s*\(\s*([^,]+)\s*,\s*([^,]+)\s*,\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)
    private val IF_PATTERN = Regex("""IF\s*\(\s*([^,]+)\s*,\s*([^,]+)\s*,\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)

    /**
     * DECODE(expr, search1, result1, ..., default) → CASE expr WHEN search1 THEN result1 ... ELSE default END
     */
    fun convertDecodeToCaseWhen(sql: String): String {
        var result = sql

        var match = DECODE_START_PATTERN.find(result)
        while (match != null) {
            val startIdx = match.range.first
            val argsStartIdx = match.range.last + 1

            val endIdx = SqlParsingUtils.findMatchingBracket(result, argsStartIdx)
            if (endIdx == -1) {
                match = DECODE_START_PATTERN.find(result, match.range.last + 1)
                continue
            }

            val argsStr = result.substring(argsStartIdx, endIdx - 1)
            val args = SqlParsingUtils.splitFunctionArgs(argsStr)

            if (args.size < 2) {
                match = DECODE_START_PATTERN.find(result, match.range.last + 1)
                continue
            }

            val expr = args[0].trim()
            val remainingArgs = args.drop(1)

            val sb = StringBuilder("CASE $expr ")
            var i = 0
            while (i + 1 < remainingArgs.size) {
                sb.append("WHEN ${remainingArgs[i]} THEN ${remainingArgs[i + 1]} ")
                i += 2
            }
            // 홀수 개의 남은 인자가 있으면 ELSE (default value)
            if (i < remainingArgs.size) {
                sb.append("ELSE ${remainingArgs[i]} ")
            }
            sb.append("END")

            result = result.substring(0, startIdx) + sb.toString() + result.substring(endIdx)
            match = DECODE_START_PATTERN.find(result, startIdx + sb.length)
        }

        return result
    }

    /**
     * NVL2(expr, not_null_value, null_value) → CASE WHEN expr IS NOT NULL THEN not_null_value ELSE null_value END
     */
    fun convertNvl2ToCaseWhen(sql: String): String {
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
    fun convertIfToCaseWhen(sql: String): String {
        return IF_PATTERN.replace(sql) { match ->
            val condition = match.groupValues[1].trim()
            val trueVal = match.groupValues[2].trim()
            val falseVal = match.groupValues[3].trim()
            "CASE WHEN $condition THEN $trueVal ELSE $falseVal END"
        }
    }
}