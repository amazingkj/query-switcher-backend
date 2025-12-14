package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.registry.FunctionConversionRegistry

/**
 * 날짜 연산 변환
 * ADD_MONTHS, MONTHS_BETWEEN, LAST_DAY, NEXT_DAY, TRUNC, DATE_ADD 등
 */
object DateArithmeticConverter {

    // 패턴 정의
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
    private val LAST_DAY_PATTERN = Regex(
        """LAST_DAY\s*\(\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )
    private val NEXT_DAY_PATTERN = Regex(
        """NEXT_DAY\s*\(\s*([^,]+)\s*,\s*'([^']+)'\s*\)""",
        RegexOption.IGNORE_CASE
    )
    private val TRUNC_DATE_PATTERN = Regex(
        """TRUNC\s*\(\s*([^,)]+?)(?:\s*,\s*'([^']+)')?\s*\)""",
        RegexOption.IGNORE_CASE
    )
    private val MYSQL_DATE_ADD_PATTERN = Regex(
        """DATE_ADD\s*\(\s*([^,]+)\s*,\s*INTERVAL\s+(\d+)\s+(DAY|MONTH|YEAR)\s*\)""",
        RegexOption.IGNORE_CASE
    )
    private val MYSQL_DATE_SUB_PATTERN = Regex(
        """DATE_SUB\s*\(\s*([^,]+)\s*,\s*INTERVAL\s+(\d+)\s+(DAY|MONTH|YEAR)\s*\)""",
        RegexOption.IGNORE_CASE
    )
    private val MYSQL_DATEDIFF_PATTERN = Regex(
        """DATEDIFF\s*\(\s*([^,]+)\s*,\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    // 변환 레지스트리
    private val conversionRegistry = FunctionConversionRegistry().apply {
        // Oracle → MySQL
        register(DialectType.ORACLE, DialectType.MYSQL) { sql, rules ->
            convertOracleToMySql(sql, rules)
        }
        // Oracle → PostgreSQL
        register(DialectType.ORACLE, DialectType.POSTGRESQL) { sql, rules ->
            convertOracleToPostgreSql(sql, rules)
        }
        // MySQL → Oracle
        register(DialectType.MYSQL, DialectType.ORACLE) { sql, rules ->
            convertMySqlToOracle(sql, rules)
        }
        // MySQL → PostgreSQL
        register(DialectType.MYSQL, DialectType.POSTGRESQL) { sql, rules ->
            convertMySqlToPostgreSql(sql, rules)
        }
    }

    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return conversionRegistry.apply(sql, sourceDialect, targetDialect, appliedRules)
    }

    // =========================================================================
    // Oracle → MySQL 변환
    // =========================================================================
    private fun convertOracleToMySql(sql: String, appliedRules: MutableList<String>): String {
        var result = sql

        // ADD_MONTHS → DATE_ADD
        result = ADD_MONTHS_PATTERN.replace(result) { match ->
            val dateExpr = match.groupValues[1].trim()
            val months = match.groupValues[2].trim()
            appliedRules.add("ADD_MONTHS() → DATE_ADD(..., INTERVAL ... MONTH) 변환")
            "DATE_ADD($dateExpr, INTERVAL $months MONTH)"
        }

        // MONTHS_BETWEEN → TIMESTAMPDIFF
        result = MONTHS_BETWEEN_PATTERN.replace(result) { match ->
            val date1 = match.groupValues[1].trim()
            val date2 = match.groupValues[2].trim()
            appliedRules.add("MONTHS_BETWEEN() → TIMESTAMPDIFF(MONTH, ...) 변환")
            "TIMESTAMPDIFF(MONTH, $date2, $date1)"
        }

        // 날짜 + n → DATE_ADD
        result = DATE_ADD_DAYS_PATTERN.replace(result) { match ->
            val dateExpr = match.groupValues[1]
            val days = match.groupValues[2]
            appliedRules.add("날짜 + n → DATE_ADD(..., INTERVAL n DAY) 변환")
            "DATE_ADD($dateExpr, INTERVAL $days DAY)"
        }

        // LAST_DAY (MySQL도 지원)
        if (LAST_DAY_PATTERN.containsMatchIn(result)) {
            appliedRules.add("LAST_DAY() 유지 (MySQL 호환)")
        }

        // NEXT_DAY → 계산식
        result = NEXT_DAY_PATTERN.replace(result) { match ->
            val dateExpr = match.groupValues[1].trim()
            val dayName = match.groupValues[2].uppercase()
            val dayNum = mapDayNameToNumber(dayName)
            appliedRules.add("NEXT_DAY() → 날짜 계산식 변환")
            "DATE_ADD($dateExpr, INTERVAL (($dayNum - DAYOFWEEK($dateExpr) + 7) % 7 + 1) DAY)"
        }

        // TRUNC → DATE/DATE_FORMAT
        result = convertTruncToMySql(result, appliedRules)

        return result
    }

    // =========================================================================
    // Oracle → PostgreSQL 변환
    // =========================================================================
    private fun convertOracleToPostgreSql(sql: String, appliedRules: MutableList<String>): String {
        var result = sql

        // ADD_MONTHS → + INTERVAL
        result = ADD_MONTHS_PATTERN.replace(result) { match ->
            val dateExpr = match.groupValues[1].trim()
            val months = match.groupValues[2].trim()
            appliedRules.add("ADD_MONTHS() → + INTERVAL 변환")
            "($dateExpr + INTERVAL '$months months')"
        }

        // MONTHS_BETWEEN → EXTRACT/AGE
        result = MONTHS_BETWEEN_PATTERN.replace(result) { match ->
            val date1 = match.groupValues[1].trim()
            val date2 = match.groupValues[2].trim()
            appliedRules.add("MONTHS_BETWEEN() → EXTRACT/AGE 조합 변환")
            "(EXTRACT(YEAR FROM AGE($date1, $date2)) * 12 + EXTRACT(MONTH FROM AGE($date1, $date2)))"
        }

        // 날짜 + n → + INTERVAL
        result = DATE_ADD_DAYS_PATTERN.replace(result) { match ->
            val dateExpr = match.groupValues[1]
            val days = match.groupValues[2]
            appliedRules.add("날짜 + n → + INTERVAL 'n days' 변환")
            "($dateExpr + INTERVAL '$days days')"
        }

        // LAST_DAY → DATE_TRUNC + INTERVAL
        result = LAST_DAY_PATTERN.replace(result) { match ->
            val dateExpr = match.groupValues[1].trim()
            appliedRules.add("LAST_DAY() → DATE_TRUNC + INTERVAL 변환")
            "(DATE_TRUNC('month', $dateExpr) + INTERVAL '1 month' - INTERVAL '1 day')::DATE"
        }

        // NEXT_DAY → 계산식
        result = NEXT_DAY_PATTERN.replace(result) { match ->
            val dateExpr = match.groupValues[1].trim()
            val dayName = match.groupValues[2].uppercase()
            val dayNum = mapDayNameToNumber(dayName)
            appliedRules.add("NEXT_DAY() → 날짜 계산식 변환")
            "($dateExpr + (($dayNum - EXTRACT(DOW FROM $dateExpr)::INTEGER + 7) % 7)::INTEGER * INTERVAL '1 day')"
        }

        // TRUNC → DATE_TRUNC
        result = convertTruncToPostgreSql(result, appliedRules)

        return result
    }

    // =========================================================================
    // MySQL → Oracle 변환
    // =========================================================================
    private fun convertMySqlToOracle(sql: String, appliedRules: MutableList<String>): String {
        var result = sql

        // DATE_ADD → 날짜 연산/ADD_MONTHS
        result = MYSQL_DATE_ADD_PATTERN.replace(result) { match ->
            val dateExpr = match.groupValues[1].trim()
            val value = match.groupValues[2]
            val unit = match.groupValues[3].uppercase()
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

        // DATE_SUB → 날짜 연산/ADD_MONTHS
        result = MYSQL_DATE_SUB_PATTERN.replace(result) { match ->
            val dateExpr = match.groupValues[1].trim()
            val value = match.groupValues[2]
            val unit = match.groupValues[3].uppercase()
            when (unit) {
                "DAY" -> {
                    appliedRules.add("DATE_SUB() → 날짜 - n 변환")
                    "($dateExpr - $value)"
                }
                "MONTH" -> {
                    appliedRules.add("DATE_SUB() → ADD_MONTHS() 변환")
                    "ADD_MONTHS($dateExpr, -$value)"
                }
                "YEAR" -> {
                    appliedRules.add("DATE_SUB() → ADD_MONTHS() 변환")
                    "ADD_MONTHS($dateExpr, -${value.toInt() * 12})"
                }
                else -> match.value
            }
        }

        // DATEDIFF → 날짜 - 날짜
        result = MYSQL_DATEDIFF_PATTERN.replace(result) { match ->
            val date1 = match.groupValues[1].trim()
            val date2 = match.groupValues[2].trim()
            appliedRules.add("DATEDIFF() → 날짜 - 날짜 변환")
            "TRUNC($date1) - TRUNC($date2)"
        }

        // LAST_DAY (Oracle도 지원)
        if (LAST_DAY_PATTERN.containsMatchIn(result)) {
            appliedRules.add("LAST_DAY() 유지 (Oracle 호환)")
        }

        return result
    }

    // =========================================================================
    // MySQL → PostgreSQL 변환
    // =========================================================================
    private fun convertMySqlToPostgreSql(sql: String, appliedRules: MutableList<String>): String {
        var result = sql

        // DATE_ADD → + INTERVAL
        result = MYSQL_DATE_ADD_PATTERN.replace(result) { match ->
            val dateExpr = match.groupValues[1].trim()
            val value = match.groupValues[2]
            val unit = match.groupValues[3].lowercase()
            appliedRules.add("DATE_ADD() → + INTERVAL 변환")
            "($dateExpr + INTERVAL '$value ${unit}s')"
        }

        // DATE_SUB → - INTERVAL
        result = MYSQL_DATE_SUB_PATTERN.replace(result) { match ->
            val dateExpr = match.groupValues[1].trim()
            val value = match.groupValues[2]
            val unit = match.groupValues[3].lowercase()
            appliedRules.add("DATE_SUB() → - INTERVAL 변환")
            "($dateExpr - INTERVAL '$value ${unit}s')"
        }

        // DATEDIFF → DATE 연산
        result = MYSQL_DATEDIFF_PATTERN.replace(result) { match ->
            val date1 = match.groupValues[1].trim()
            val date2 = match.groupValues[2].trim()
            appliedRules.add("DATEDIFF() → DATE_PART 변환")
            "($date1::DATE - $date2::DATE)"
        }

        // LAST_DAY → DATE_TRUNC + INTERVAL
        result = LAST_DAY_PATTERN.replace(result) { match ->
            val dateExpr = match.groupValues[1].trim()
            appliedRules.add("LAST_DAY() → DATE_TRUNC + INTERVAL 변환")
            "(DATE_TRUNC('month', $dateExpr) + INTERVAL '1 month' - INTERVAL '1 day')::DATE"
        }

        return result
    }

    // =========================================================================
    // 헬퍼 메서드
    // =========================================================================

    private fun convertTruncToMySql(sql: String, appliedRules: MutableList<String>): String {
        return TRUNC_DATE_PATTERN.replace(sql) { match ->
            val dateExpr = match.groupValues[1].trim()
            val format = match.groupValues.getOrNull(2)?.uppercase()

            // 숫자 TRUNC는 제외
            if (dateExpr.matches(Regex("""-?\d+\.?\d*"""))) {
                match.value
            } else {
                when (format) {
                    "YEAR", "YYYY", "YY" -> {
                        appliedRules.add("TRUNC(date, 'YEAR') → DATE_FORMAT 변환")
                        "DATE(DATE_FORMAT($dateExpr, '%Y-01-01'))"
                    }
                    "MONTH", "MM" -> {
                        appliedRules.add("TRUNC(date, 'MONTH') → DATE_FORMAT 변환")
                        "DATE(DATE_FORMAT($dateExpr, '%Y-%m-01'))"
                    }
                    "HH", "HH24" -> {
                        appliedRules.add("TRUNC(date, 'HH') → DATE_FORMAT 변환")
                        "DATE_FORMAT($dateExpr, '%Y-%m-%d %H:00:00')"
                    }
                    "MI" -> {
                        appliedRules.add("TRUNC(date, 'MI') → DATE_FORMAT 변환")
                        "DATE_FORMAT($dateExpr, '%Y-%m-%d %H:%i:00')"
                    }
                    else -> {
                        appliedRules.add("TRUNC(date) → DATE() 변환")
                        "DATE($dateExpr)"
                    }
                }
            }
        }
    }

    private fun convertTruncToPostgreSql(sql: String, appliedRules: MutableList<String>): String {
        return TRUNC_DATE_PATTERN.replace(sql) { match ->
            val dateExpr = match.groupValues[1].trim()
            val format = match.groupValues.getOrNull(2)?.uppercase()

            // 숫자 TRUNC는 제외
            if (dateExpr.matches(Regex("""-?\d+\.?\d*"""))) {
                match.value
            } else {
                val pgFormat = when (format) {
                    "YEAR", "YYYY", "YY" -> "year"
                    "MONTH", "MM" -> "month"
                    "DAY", "DD", null -> "day"
                    "HH", "HH24" -> "hour"
                    "MI" -> "minute"
                    "Q" -> "quarter"
                    "WW", "IW" -> "week"
                    else -> "day"
                }
                appliedRules.add("TRUNC(date) → DATE_TRUNC() 변환")
                "DATE_TRUNC('$pgFormat', $dateExpr)"
            }
        }
    }

    private fun mapDayNameToNumber(dayName: String): Int {
        return when (dayName.uppercase()) {
            "SUNDAY", "SUN" -> 1
            "MONDAY", "MON" -> 2
            "TUESDAY", "TUE" -> 3
            "WEDNESDAY", "WED" -> 4
            "THURSDAY", "THU" -> 5
            "FRIDAY", "FRI" -> 6
            "SATURDAY", "SAT" -> 7
            else -> 1
        }
    }
}