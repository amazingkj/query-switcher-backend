package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.util.SqlParsingUtils

/**
 * DECODE, NVL2, IF 함수를 CASE WHEN으로 변환
 */
object DecodeConverter {

    private val DECODE_START_PATTERN = Regex("""DECODE\s*\(""", RegexOption.IGNORE_CASE)
    private val NVL2_START_PATTERN = Regex("""NVL2\s*\(""", RegexOption.IGNORE_CASE)
    private val IF_START_PATTERN = Regex("""(?<![A-Za-z_])IF\s*\(""", RegexOption.IGNORE_CASE)

    /**
     * DECODE(expr, search1, result1, ..., default) → CASE expr WHEN search1 THEN result1 ... ELSE default END
     * 중첩된 DECODE도 모두 변환 (반복적으로 처리)
     */
    fun convertDecodeToCaseWhen(sql: String): String {
        var result = sql
        var previousResult = ""

        // 모든 DECODE가 변환될 때까지 반복
        while (result != previousResult) {
            previousResult = result
            result = convertDecodeOnce(result)
        }

        return result
    }

    /**
     * DECODE를 한 번 변환 (가장 안쪽 DECODE부터 변환)
     */
    private fun convertDecodeOnce(sql: String): String {
        var result = sql

        // 가장 안쪽 DECODE를 먼저 찾아서 변환 (중첩 처리를 위해)
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

            // 이 DECODE 내부에 다른 DECODE가 있는지 확인
            if (DECODE_START_PATTERN.containsMatchIn(argsStr)) {
                // 내부에 DECODE가 있으면 다음 매치로 이동 (나중에 처리)
                match = DECODE_START_PATTERN.find(result, match.range.last + 1)
                continue
            }

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
            // 변환 후 바로 반환 (다음 반복에서 나머지 처리)
            return result
        }

        return result
    }

    /**
     * NVL2(expr, not_null_value, null_value) → CASE WHEN expr IS NOT NULL THEN not_null_value ELSE null_value END
     * 중첩된 함수도 처리 (SqlParsingUtils 사용)
     */
    fun convertNvl2ToCaseWhen(sql: String): String {
        var result = sql
        var previousResult = ""

        // 모든 NVL2가 변환될 때까지 반복
        while (result != previousResult) {
            previousResult = result
            result = convertNvl2Once(result)
        }

        return result
    }

    /**
     * NVL2를 한 번 변환 (가장 안쪽부터)
     */
    private fun convertNvl2Once(sql: String): String {
        var result = sql

        var match = NVL2_START_PATTERN.find(result)
        while (match != null) {
            val startIdx = match.range.first
            val argsStartIdx = match.range.last + 1

            val endIdx = SqlParsingUtils.findMatchingBracket(result, argsStartIdx)
            if (endIdx == -1) {
                match = NVL2_START_PATTERN.find(result, match.range.last + 1)
                continue
            }

            val argsStr = result.substring(argsStartIdx, endIdx - 1)

            // 내부에 다른 NVL2가 있으면 건너뜀
            if (NVL2_START_PATTERN.containsMatchIn(argsStr)) {
                match = NVL2_START_PATTERN.find(result, match.range.last + 1)
                continue
            }

            val args = SqlParsingUtils.splitFunctionArgs(argsStr)
            if (args.size != 3) {
                match = NVL2_START_PATTERN.find(result, match.range.last + 1)
                continue
            }

            val expr = args[0].trim()
            val notNullVal = args[1].trim()
            val nullVal = args[2].trim()
            val replacement = "CASE WHEN $expr IS NOT NULL THEN $notNullVal ELSE $nullVal END"

            result = result.substring(0, startIdx) + replacement + result.substring(endIdx)
            return result
        }

        return result
    }

    /**
     * IF(condition, true_val, false_val) → CASE WHEN condition THEN true_val ELSE false_val END
     * 중첩된 함수도 처리 (SqlParsingUtils 사용)
     */
    fun convertIfToCaseWhen(sql: String): String {
        var result = sql
        var previousResult = ""

        // 모든 IF가 변환될 때까지 반복
        while (result != previousResult) {
            previousResult = result
            result = convertIfOnce(result)
        }

        return result
    }

    /**
     * IF를 한 번 변환 (가장 안쪽부터)
     */
    private fun convertIfOnce(sql: String): String {
        var result = sql

        var match = IF_START_PATTERN.find(result)
        while (match != null) {
            val startIdx = match.range.first
            val argsStartIdx = match.range.last + 1

            val endIdx = SqlParsingUtils.findMatchingBracket(result, argsStartIdx)
            if (endIdx == -1) {
                match = IF_START_PATTERN.find(result, match.range.last + 1)
                continue
            }

            val argsStr = result.substring(argsStartIdx, endIdx - 1)

            // 내부에 다른 IF가 있으면 건너뜀
            if (IF_START_PATTERN.containsMatchIn(argsStr)) {
                match = IF_START_PATTERN.find(result, match.range.last + 1)
                continue
            }

            val args = SqlParsingUtils.splitFunctionArgs(argsStr)
            if (args.size != 3) {
                match = IF_START_PATTERN.find(result, match.range.last + 1)
                continue
            }

            val condition = args[0].trim()
            val trueVal = args[1].trim()
            val falseVal = args[2].trim()
            val replacement = "CASE WHEN $condition THEN $trueVal ELSE $falseVal END"

            result = result.substring(0, startIdx) + replacement + result.substring(endIdx)
            return result
        }

        return result
    }
}