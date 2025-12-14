package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity

/**
 * 날짜 연산 변환
 * ADD_MONTHS, MONTHS_BETWEEN, LAST_DAY, NEXT_DAY, TRUNC, DATE_ADD 등
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

    // 추가 날짜 함수 패턴
    private val LAST_DAY_PATTERN = Regex(
        """LAST_DAY\s*\(\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )
    private val NEXT_DAY_PATTERN = Regex(
        """NEXT_DAY\s*\(\s*([^,]+)\s*,\s*'([^']+)'\s*\)""",
        RegexOption.IGNORE_CASE
    )
    // TRUNC(date) 또는 TRUNC(date, 'format')
    private val TRUNC_DATE_PATTERN = Regex(
        """TRUNC\s*\(\s*([^,)]+?)(?:\s*,\s*'([^']+)')?\s*\)""",
        RegexOption.IGNORE_CASE
    )
    // EXTRACT(unit FROM date)
    private val EXTRACT_PATTERN = Regex(
        """EXTRACT\s*\(\s*(YEAR|MONTH|DAY|HOUR|MINUTE|SECOND)\s+FROM\s+([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )
    // DATE_SUB (MySQL)
    private val MYSQL_DATE_SUB_PATTERN = Regex(
        """DATE_SUB\s*\(\s*([^,]+)\s*,\s*INTERVAL\s+(\d+)\s+(DAY|MONTH|YEAR)\s*\)""",
        RegexOption.IGNORE_CASE
    )
    // DATEADD (다양한 DB 지원)
    private val DATEADD_PATTERN = Regex(
        """DATEADD\s*\(\s*(day|month|year|week|hour|minute|second)\s*,\s*(-?\d+)\s*,\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )
    // DATEDIFF (MySQL)
    private val MYSQL_DATEDIFF_PATTERN = Regex(
        """DATEDIFF\s*\(\s*([^,]+)\s*,\s*([^)]+)\s*\)""",
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

        // LAST_DAY 변환 (Oracle → 타겟)
        if (LAST_DAY_PATTERN.containsMatchIn(result)) {
            result = LAST_DAY_PATTERN.replace(result) { match ->
                val dateExpr = match.groupValues[1].trim()
                when (targetDialect) {
                    DialectType.MYSQL -> {
                        // MySQL도 LAST_DAY 지원
                        appliedRules.add("LAST_DAY() 유지 (MySQL 호환)")
                        "LAST_DAY($dateExpr)"
                    }
                    DialectType.POSTGRESQL -> {
                        // PostgreSQL: DATE_TRUNC('month', date) + INTERVAL '1 month' - INTERVAL '1 day'
                        appliedRules.add("LAST_DAY() → DATE_TRUNC + INTERVAL 변환")
                        "(DATE_TRUNC('month', $dateExpr) + INTERVAL '1 month' - INTERVAL '1 day')::DATE"
                    }
                    else -> match.value
                }
            }
        }

        // NEXT_DAY 변환 (Oracle → 타겟)
        if (NEXT_DAY_PATTERN.containsMatchIn(result)) {
            result = NEXT_DAY_PATTERN.replace(result) { match ->
                val dateExpr = match.groupValues[1].trim()
                val dayName = match.groupValues[2].uppercase()
                when (targetDialect) {
                    DialectType.MYSQL -> {
                        // MySQL: 수동 계산 필요
                        val dayNum = mapDayNameToNumber(dayName)
                        appliedRules.add("NEXT_DAY() → 날짜 계산식 변환")
                        "DATE_ADD($dateExpr, INTERVAL (($dayNum - DAYOFWEEK($dateExpr) + 7) % 7 + 1) DAY)"
                    }
                    DialectType.POSTGRESQL -> {
                        // PostgreSQL: date + (dow - EXTRACT(dow FROM date) + 7) % 7 + 1
                        val dayNum = mapDayNameToNumber(dayName)
                        appliedRules.add("NEXT_DAY() → 날짜 계산식 변환")
                        "($dateExpr + (($dayNum - EXTRACT(DOW FROM $dateExpr)::INTEGER + 7) % 7)::INTEGER * INTERVAL '1 day')"
                    }
                    else -> match.value
                }
            }
        }

        // TRUNC(date) 변환 - 날짜에 대한 TRUNC만 처리
        if (TRUNC_DATE_PATTERN.containsMatchIn(result)) {
            result = TRUNC_DATE_PATTERN.replace(result) { match ->
                val dateExpr = match.groupValues[1].trim()
                val format = match.groupValues.getOrNull(2)?.uppercase()

                // 숫자형 표현인지 확인 (TRUNC(123.45) 같은 경우 제외)
                if (dateExpr.matches(Regex("""-?\d+\.?\d*"""))) {
                    match.value // 숫자 TRUNC는 그대로 유지
                } else {
                    when (targetDialect) {
                        DialectType.MYSQL -> {
                            val mysqlFormat = when (format) {
                                "YEAR", "YYYY", "YY" -> {
                                    appliedRules.add("TRUNC(date, 'YEAR') → DATE_FORMAT 변환")
                                    "DATE(DATE_FORMAT($dateExpr, '%Y-01-01'))"
                                }
                                "MONTH", "MM" -> {
                                    appliedRules.add("TRUNC(date, 'MONTH') → DATE_FORMAT 변환")
                                    "DATE(DATE_FORMAT($dateExpr, '%Y-%m-01'))"
                                }
                                "DAY", "DD", null -> {
                                    appliedRules.add("TRUNC(date) → DATE() 변환")
                                    "DATE($dateExpr)"
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
                            mysqlFormat
                        }
                        DialectType.POSTGRESQL -> {
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
                        else -> match.value
                    }
                }
            }
        }

        return result
    }

    /**
     * 요일명을 숫자로 변환 (Sunday=0 또는 1 기준)
     */
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

        // DATE_SUB 변환
        if (MYSQL_DATE_SUB_PATTERN.containsMatchIn(result)) {
            result = MYSQL_DATE_SUB_PATTERN.replace(result) { match ->
                val dateExpr = match.groupValues[1].trim()
                val value = match.groupValues[2]
                val unit = match.groupValues[3].uppercase()
                when (targetDialect) {
                    DialectType.ORACLE -> {
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
                    DialectType.POSTGRESQL -> {
                        appliedRules.add("DATE_SUB() → - INTERVAL 변환")
                        "($dateExpr - INTERVAL '$value ${unit.lowercase()}s')"
                    }
                    else -> match.value
                }
            }
        }

        // DATEDIFF 변환
        if (MYSQL_DATEDIFF_PATTERN.containsMatchIn(result)) {
            result = MYSQL_DATEDIFF_PATTERN.replace(result) { match ->
                val date1 = match.groupValues[1].trim()
                val date2 = match.groupValues[2].trim()
                when (targetDialect) {
                    DialectType.ORACLE -> {
                        appliedRules.add("DATEDIFF() → 날짜 - 날짜 변환")
                        "TRUNC($date1) - TRUNC($date2)"
                    }
                    DialectType.POSTGRESQL -> {
                        appliedRules.add("DATEDIFF() → DATE_PART 변환")
                        "($date1::DATE - $date2::DATE)"
                    }
                    else -> match.value
                }
            }
        }

        // LAST_DAY 변환 (MySQL → Oracle)
        if (targetDialect == DialectType.ORACLE && LAST_DAY_PATTERN.containsMatchIn(result)) {
            // MySQL LAST_DAY는 Oracle에서도 LAST_DAY로 동일
            appliedRules.add("LAST_DAY() 유지 (Oracle 호환)")
        }

        // LAST_DAY 변환 (MySQL → PostgreSQL)
        if (targetDialect == DialectType.POSTGRESQL && LAST_DAY_PATTERN.containsMatchIn(result)) {
            result = LAST_DAY_PATTERN.replace(result) { match ->
                val dateExpr = match.groupValues[1].trim()
                appliedRules.add("LAST_DAY() → DATE_TRUNC + INTERVAL 변환")
                "(DATE_TRUNC('month', $dateExpr) + INTERVAL '1 month' - INTERVAL '1 day')::DATE"
            }
        }

        return result
    }
}