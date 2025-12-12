package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType

/**
 * 날짜 연산 변환
 * ADD_MONTHS, MONTHS_BETWEEN, DATE_ADD, 날짜 +/- n 등
 */
object DateArithmeticConverter {

    private val ADD_MONTHS_PATTERN = Regex(
        """ADD_MONTHS\s*\(\s*([^,]+)\s*,\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )
    private val MONTHS_BETWEEN_PATTERN = Regex(
        """MONTHS_BETWEEN\s*\(\s*([^,]+)\s*,\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )
    private val DATE_ADD_DAYS_PATTERN = Regex(
        """(\b\w+_date\b|\bdate\b|\bhire_date\b|\bcreated_at\b|\bupdated_at\b|\bSYSDATE\b)\s*\+\s*(\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val MYSQL_DATE_ADD_PATTERN = Regex(
        """DATE_ADD\s*\(\s*([^,]+)\s*,\s*INTERVAL\s+(\d+)\s+(DAY|MONTH|YEAR)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (sourceDialect == DialectType.ORACLE) {
            result = convertOracleDateArithmetic(result, targetDialect, appliedRules)
        }

        if (sourceDialect == DialectType.MYSQL) {
            result = convertMysqlDateArithmetic(result, targetDialect, appliedRules)
        }

        return result
    }

    private fun convertOracleDateArithmetic(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // ADD_MONTHS 변환
        if (ADD_MONTHS_PATTERN.containsMatchIn(result)) {
            result = ADD_MONTHS_PATTERN.replace(result) { match ->
                val dateExpr = match.groupValues[1].trim()
                val months = match.groupValues[2].trim()
                when (targetDialect) {
                    DialectType.MYSQL -> {
                        appliedRules.add("ADD_MONTHS() → DATE_ADD(..., INTERVAL ... MONTH) 변환")
                        "DATE_ADD($dateExpr, INTERVAL $months MONTH)"
                    }
                    DialectType.POSTGRESQL -> {
                        appliedRules.add("ADD_MONTHS() → + INTERVAL 변환")
                        "($dateExpr + INTERVAL '$months months')"
                    }
                    else -> match.value
                }
            }
        }

        // MONTHS_BETWEEN 변환
        if (MONTHS_BETWEEN_PATTERN.containsMatchIn(result)) {
            result = MONTHS_BETWEEN_PATTERN.replace(result) { match ->
                val date1 = match.groupValues[1].trim()
                val date2 = match.groupValues[2].trim()
                when (targetDialect) {
                    DialectType.MYSQL -> {
                        appliedRules.add("MONTHS_BETWEEN() → TIMESTAMPDIFF(MONTH, ...) 변환")
                        "TIMESTAMPDIFF(MONTH, $date2, $date1)"
                    }
                    DialectType.POSTGRESQL -> {
                        appliedRules.add("MONTHS_BETWEEN() → EXTRACT/AGE 조합 변환")
                        "(EXTRACT(YEAR FROM AGE($date1, $date2)) * 12 + EXTRACT(MONTH FROM AGE($date1, $date2)))"
                    }
                    else -> match.value
                }
            }
        }

        // 날짜 + n 연산 (Oracle에서 n은 일수)
        if (DATE_ADD_DAYS_PATTERN.containsMatchIn(result)) {
            result = DATE_ADD_DAYS_PATTERN.replace(result) { match ->
                val dateExpr = match.groupValues[1]
                val days = match.groupValues[2]
                when (targetDialect) {
                    DialectType.MYSQL -> {
                        appliedRules.add("날짜 + n → DATE_ADD(..., INTERVAL n DAY) 변환")
                        "DATE_ADD($dateExpr, INTERVAL $days DAY)"
                    }
                    DialectType.POSTGRESQL -> {
                        appliedRules.add("날짜 + n → + INTERVAL 'n days' 변환")
                        "($dateExpr + INTERVAL '$days days')"
                    }
                    else -> match.value
                }
            }
        }

        return result
    }

    private fun convertMysqlDateArithmetic(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (MYSQL_DATE_ADD_PATTERN.containsMatchIn(result)) {
            result = MYSQL_DATE_ADD_PATTERN.replace(result) { match ->
                val dateExpr = match.groupValues[1].trim()
                val value = match.groupValues[2]
                val unit = match.groupValues[3].uppercase()
                when (targetDialect) {
                    DialectType.ORACLE -> {
                        when (unit) {
                            "DAY" -> {
                                appliedRules.add("DATE_ADD() → 날짜 + n 변환")
                                "($dateExpr + $value)"
                            }
                            "MONTH" -> {
                                appliedRules.add("DATE_ADD() → ADD_MONTHS() 변환")
                                "ADD_MONTHS($dateExpr, $value)"
                            }
                            "YEAR" -> {
                                appliedRules.add("DATE_ADD() → ADD_MONTHS() 변환")
                                "ADD_MONTHS($dateExpr, ${value.toInt() * 12})"
                            }
                            else -> match.value
                        }
                    }
                    DialectType.POSTGRESQL -> {
                        appliedRules.add("DATE_ADD() → + INTERVAL 변환")
                        "($dateExpr + INTERVAL '$value ${unit.lowercase()}s')"
                    }
                    else -> match.value
                }
            }
        }

        return result
    }
}