package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.mapping.FunctionMappingRule
import com.sqlswitcher.converter.util.DateFormatConverter
import com.sqlswitcher.converter.util.SqlParsingUtils

/**
 * 날짜 함수 변환
 * TO_CHAR, DATE_FORMAT, TO_DATE, STR_TO_DATE 등
 */
object DateFunctionConverter {

    /**
     * 날짜 포맷 함수 변환 (TO_CHAR, DATE_FORMAT 등)
     */
    fun convertDateFormatFunction(
        sql: String,
        rule: FunctionMappingRule,
        appliedRules: MutableList<String>
    ): String {
        val functionStartPattern = Regex("""${rule.sourceFunction}\s*\(""", RegexOption.IGNORE_CASE)
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

            if (args.isEmpty()) {
                match = functionStartPattern.find(result, match.range.last + 1)
                continue
            }

            val convertedArgs = convertDateFormatArgs(rule, args)
            val replacement = "${rule.targetFunction}(${convertedArgs.joinToString(", ")})"
            result = result.substring(0, startIdx) + replacement + result.substring(endIdx)
            appliedRules.add("${rule.sourceFunction}() → ${rule.targetFunction}()")

            match = functionStartPattern.find(result, startIdx + replacement.length)
        }

        return result
    }

    private fun convertDateFormatArgs(rule: FunctionMappingRule, args: List<String>): List<String> {
        return when {
            // Oracle TO_CHAR → MySQL DATE_FORMAT
            rule.sourceFunction.equals("TO_CHAR", ignoreCase = true) &&
            rule.targetFunction.equals("DATE_FORMAT", ignoreCase = true) -> {
                if (args.size >= 2) {
                    val dateExpr = args[0].trim()
                    val oracleFormat = args[1].trim().removeSurrounding("'")
                    val mysqlFormat = DateFormatConverter.oracleToMysql(oracleFormat)
                    listOf(dateExpr, "'$mysqlFormat'")
                } else args
            }
            // MySQL DATE_FORMAT → Oracle TO_CHAR
            rule.sourceFunction.equals("DATE_FORMAT", ignoreCase = true) &&
            rule.targetFunction.equals("TO_CHAR", ignoreCase = true) -> {
                if (args.size >= 2) {
                    val dateExpr = args[0].trim()
                    val mysqlFormat = args[1].trim().removeSurrounding("'")
                    val oracleFormat = DateFormatConverter.mysqlToOracle(mysqlFormat)
                    listOf(dateExpr, "'$oracleFormat'")
                } else args
            }
            // TO_DATE, STR_TO_DATE 등
            rule.sourceFunction.equals("TO_DATE", ignoreCase = true) ||
            rule.sourceFunction.equals("STR_TO_DATE", ignoreCase = true) -> {
                if (args.size >= 2) {
                    val dateStr = args[0].trim()
                    val format = args[1].trim().removeSurrounding("'")
                    val convertedFormat = when {
                        rule.targetFunction.equals("STR_TO_DATE", ignoreCase = true) ->
                            DateFormatConverter.oracleToMysql(format)
                        rule.targetFunction.equals("TO_DATE", ignoreCase = true) ||
                        rule.targetFunction.equals("TO_TIMESTAMP", ignoreCase = true) ->
                            DateFormatConverter.mysqlToOracle(format)
                        else -> format
                    }
                    listOf(dateStr, "'$convertedFormat'")
                } else args
            }
            else -> args
        }
    }
}