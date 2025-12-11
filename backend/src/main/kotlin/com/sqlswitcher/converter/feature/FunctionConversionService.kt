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

        // 문자열 연결 (||)
        private val STRING_CONCAT_PATTERN = Regex("""([^|])\|\|([^|])""")

        // TO_NUMBER 변환
        private val TO_NUMBER_PATTERN = Regex("""TO_NUMBER\s*\(\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)

        // 날짜 연산
        private val ADD_MONTHS_PATTERN = Regex("""ADD_MONTHS\s*\(\s*([^,]+)\s*,\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)
        private val DATE_ADD_PATTERN = Regex("""(\w+)\s*\+\s*(\d+)\b(?!\s*\*)""", RegexOption.IGNORE_CASE)
        private val DATE_SUB_PATTERN = Regex("""(\w+)\s*-\s*(\d+)\b(?!\s*\*)""", RegexOption.IGNORE_CASE)
        private val MONTHS_BETWEEN_PATTERN = Regex("""MONTHS_BETWEEN\s*\(\s*([^,]+)\s*,\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)

        // 구식 Oracle JOIN (+)
        private val ORACLE_LEFT_JOIN_PATTERN = Regex("""(\w+)\.(\w+)\s*=\s*(\w+)\.(\w+)\s*\(\+\)""", RegexOption.IGNORE_CASE)
        private val ORACLE_RIGHT_JOIN_PATTERN = Regex("""(\w+)\.(\w+)\s*\(\+\)\s*=\s*(\w+)\.(\w+)""", RegexOption.IGNORE_CASE)

        // Oracle 12c 페이징
        private val OFFSET_FETCH_PATTERN = Regex(
            """OFFSET\s+(\d+)\s+ROWS?\s+FETCH\s+(?:FIRST|NEXT)\s+(\d+)\s+ROWS?\s+ONLY""",
            RegexOption.IGNORE_CASE
        )

        // 계층 쿼리
        private val CONNECT_BY_PATTERN = Regex(
            """START\s+WITH\s+(.+?)\s+CONNECT\s+BY\s+(?:NOCYCLE\s+)?(?:PRIOR\s+)?(.+?)(?=\s+ORDER\s+BY|\s*;|\s*$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val LEVEL_PATTERN = Regex("\\bLEVEL\\b", RegexOption.IGNORE_CASE)
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

        // 문자열 연결 (||) 변환
        result = convertStringConcatenation(result, sourceDialect, targetDialect, appliedRules)

        // TO_NUMBER 변환
        result = convertToNumber(result, sourceDialect, targetDialect, appliedRules)

        // 날짜 연산 변환
        result = convertDateArithmetic(result, sourceDialect, targetDialect, warnings, appliedRules)

        // Oracle 12c OFFSET FETCH 변환
        result = convertOffsetFetch(result, sourceDialect, targetDialect, appliedRules)

        // 계층 쿼리 변환 (CONNECT BY)
        result = convertHierarchicalQuery(result, sourceDialect, targetDialect, warnings, appliedRules)

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

    /**
     * 문자열 연결 (||) 변환
     * Oracle/PostgreSQL: || → MySQL: CONCAT()
     */
    private fun convertStringConcatenation(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // Oracle/PostgreSQL → MySQL: || → CONCAT()
        if ((sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.POSTGRESQL)
            && targetDialect == DialectType.MYSQL) {

            // 복잡한 || 연결 처리를 위한 파싱
            val concatPattern = Regex("""([^\s,()]+(?:\s*\|\|\s*[^\s,()]+)+)""")
            result = concatPattern.replace(result) { match ->
                val parts = match.value.split(Regex("""\s*\|\|\s*"""))
                if (parts.size > 1) {
                    appliedRules.add("|| 문자열 연결 → CONCAT() 변환")
                    "CONCAT(${parts.joinToString(", ")})"
                } else {
                    match.value
                }
            }
        }

        // MySQL → Oracle/PostgreSQL: CONCAT() → ||
        if (sourceDialect == DialectType.MYSQL &&
            (targetDialect == DialectType.ORACLE || targetDialect == DialectType.POSTGRESQL)) {

            val concatFuncPattern = Regex("""CONCAT\s*\(\s*(.+?)\s*\)""", RegexOption.IGNORE_CASE)
            result = concatFuncPattern.replace(result) { match ->
                val args = splitFunctionArgs(match.groupValues[1])
                if (args.size > 1) {
                    appliedRules.add("CONCAT() → || 문자열 연결 변환")
                    args.joinToString(" || ")
                } else {
                    match.value
                }
            }
        }

        return result
    }

    /**
     * TO_NUMBER 변환
     * Oracle: TO_NUMBER() → MySQL/PostgreSQL: CAST(... AS DECIMAL)
     */
    private fun convertToNumber(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (sourceDialect == DialectType.ORACLE) {
            if (TO_NUMBER_PATTERN.containsMatchIn(result)) {
                result = TO_NUMBER_PATTERN.replace(result) { match ->
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
        }

        return result
    }

    /**
     * 날짜 연산 변환
     * Oracle: ADD_MONTHS(), date + n → MySQL: DATE_ADD(), PostgreSQL: INTERVAL
     */
    private fun convertDateArithmetic(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (sourceDialect == DialectType.ORACLE) {
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
            val dateAddDaysPattern = Regex(
                """(\b\w+_date\b|\bdate\b|\bhire_date\b|\bcreated_at\b|\bupdated_at\b|\bSYSDATE\b)\s*\+\s*(\d+)""",
                RegexOption.IGNORE_CASE
            )
            if (dateAddDaysPattern.containsMatchIn(result)) {
                result = dateAddDaysPattern.replace(result) { match ->
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
        }

        // MySQL DATE_ADD → Oracle/PostgreSQL
        if (sourceDialect == DialectType.MYSQL) {
            val mysqlDateAddPattern = Regex(
                """DATE_ADD\s*\(\s*([^,]+)\s*,\s*INTERVAL\s+(\d+)\s+(DAY|MONTH|YEAR)\s*\)""",
                RegexOption.IGNORE_CASE
            )
            if (mysqlDateAddPattern.containsMatchIn(result)) {
                result = mysqlDateAddPattern.replace(result) { match ->
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
        }

        return result
    }

    /**
     * Oracle 12c OFFSET FETCH 변환
     */
    private fun convertOffsetFetch(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (sourceDialect == DialectType.ORACLE && OFFSET_FETCH_PATTERN.containsMatchIn(result)) {
            result = OFFSET_FETCH_PATTERN.replace(result) { match ->
                val offset = match.groupValues[1]
                val limit = match.groupValues[2]
                when (targetDialect) {
                    DialectType.MYSQL, DialectType.POSTGRESQL -> {
                        appliedRules.add("OFFSET ... FETCH → LIMIT ... OFFSET 변환")
                        "LIMIT $limit OFFSET $offset"
                    }
                    else -> match.value
                }
            }
        }

        // MySQL/PostgreSQL LIMIT OFFSET → Oracle OFFSET FETCH
        if ((sourceDialect == DialectType.MYSQL || sourceDialect == DialectType.POSTGRESQL)
            && targetDialect == DialectType.ORACLE) {

            val limitOffsetPattern = Regex(
                """LIMIT\s+(\d+)\s+OFFSET\s+(\d+)""",
                RegexOption.IGNORE_CASE
            )
            val limitOnlyPattern = Regex(
                """LIMIT\s+(\d+)(?!\s+OFFSET)""",
                RegexOption.IGNORE_CASE
            )

            if (limitOffsetPattern.containsMatchIn(result)) {
                result = limitOffsetPattern.replace(result) { match ->
                    val limit = match.groupValues[1]
                    val offset = match.groupValues[2]
                    appliedRules.add("LIMIT ... OFFSET → OFFSET ... FETCH 변환")
                    "OFFSET $offset ROWS FETCH NEXT $limit ROWS ONLY"
                }
            } else if (limitOnlyPattern.containsMatchIn(result)) {
                result = limitOnlyPattern.replace(result) { match ->
                    val limit = match.groupValues[1]
                    appliedRules.add("LIMIT → FETCH FIRST 변환")
                    "FETCH FIRST $limit ROWS ONLY"
                }
            }
        }

        return result
    }

    /**
     * 계층 쿼리 변환 (Oracle CONNECT BY → WITH RECURSIVE)
     */
    private fun convertHierarchicalQuery(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (sourceDialect == DialectType.ORACLE && CONNECT_BY_PATTERN.containsMatchIn(result)) {
            when (targetDialect) {
                DialectType.MYSQL, DialectType.POSTGRESQL -> {
                    // 복잡한 CONNECT BY는 수동 변환이 필요하므로 가이드 주석 추가
                    val match = CONNECT_BY_PATTERN.find(result)
                    if (match != null) {
                        val startCondition = match.groupValues[1].trim()
                        val connectCondition = match.groupValues[2].trim()

                        // PRIOR 키워드에서 관계 추출 시도
                        val priorPattern = Regex("""PRIOR\s+(\w+)\s*=\s*(\w+)""", RegexOption.IGNORE_CASE)
                        val priorMatch = priorPattern.find(connectCondition)

                        // SELECT 절에서 테이블명과 컬럼 추출
                        val selectPattern = Regex("""SELECT\s+(.+?)\s+FROM\s+(\w+)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        val selectMatch = selectPattern.find(result)

                        if (priorMatch != null && selectMatch != null) {
                            val parentCol = priorMatch.groupValues[1]
                            val childCol = priorMatch.groupValues[2]
                            val columns = selectMatch.groupValues[1].trim()
                            val tableName = selectMatch.groupValues[2]

                            // LEVEL 처리
                            val hasLevel = LEVEL_PATTERN.containsMatchIn(columns)
                            val columnsWithoutLevel = LEVEL_PATTERN.replace(columns, "").replace(Regex(",\\s*,"), ",").trim().trimEnd(',')

                            val recursiveCte = buildString {
                                append("WITH RECURSIVE hierarchy_cte AS (\n")
                                append("    -- Base case: root nodes\n")
                                append("    SELECT $columnsWithoutLevel")
                                if (hasLevel) append(", 1 AS level")
                                append("\n    FROM $tableName\n")
                                append("    WHERE $startCondition\n")
                                append("    \n")
                                append("    UNION ALL\n")
                                append("    \n")
                                append("    -- Recursive case\n")
                                append("    SELECT ")
                                // 컬럼들을 t. 접두사로 변환
                                val colList = columnsWithoutLevel.split(",").map { it.trim() }
                                append(colList.joinToString(", ") { col ->
                                    if (col.contains(".")) col.replace(Regex("""^\w+\."""), "t.") else "t.$col"
                                })
                                if (hasLevel) append(", h.level + 1")
                                append("\n    FROM $tableName t\n")
                                append("    JOIN hierarchy_cte h ON t.$childCol = h.$parentCol\n")
                                append(")\n")
                                append("SELECT * FROM hierarchy_cte")
                            }

                            result = recursiveCte
                            appliedRules.add("CONNECT BY → WITH RECURSIVE CTE 변환")

                            warnings.add(ConversionWarning(
                                type = WarningType.SYNTAX_DIFFERENCE,
                                message = "Oracle CONNECT BY가 WITH RECURSIVE로 변환되었습니다.",
                                severity = WarningSeverity.WARNING,
                                suggestion = "변환된 CTE를 검토하고 컬럼명과 조인 조건을 확인하세요."
                            ))
                        } else {
                            // 파싱 실패 시 가이드 주석 추가
                            val guide = """
-- Oracle CONNECT BY를 WITH RECURSIVE로 변환 필요:
-- WITH RECURSIVE cte AS (
--     SELECT columns, 1 as level FROM table WHERE start_condition
--     UNION ALL
--     SELECT columns, level + 1 FROM table t JOIN cte c ON t.child_col = c.parent_col
-- )
-- SELECT * FROM cte;

""".trimIndent()
                            result = guide + result
                            appliedRules.add("CONNECT BY 감지 - 수동 변환 가이드 추가")

                            warnings.add(ConversionWarning(
                                type = WarningType.MANUAL_REVIEW_NEEDED,
                                message = "복잡한 CONNECT BY 구문입니다. WITH RECURSIVE로 수동 변환이 필요합니다.",
                                severity = WarningSeverity.WARNING,
                                suggestion = "START WITH 조건을 base case로, CONNECT BY를 재귀 JOIN으로 변환하세요."
                            ))
                        }
                    }
                }
                else -> {}
            }
        }

        return result
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
        var result = sql

        if (sourceDialect == DialectType.ORACLE && sql.contains("(+)")) {
            // LEFT JOIN: t1.col = t2.col(+) → t1 LEFT JOIN t2 ON t1.col = t2.col
            // RIGHT JOIN: t1.col(+) = t2.col → t1 RIGHT JOIN t2 ON t1.col = t2.col

            // WHERE 절에서 조인 조건 추출
            val wherePattern = Regex(
                """FROM\s+(.+?)\s+WHERE\s+(.+?)(?=\s+(?:GROUP|ORDER|HAVING|LIMIT|$))""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )

            val whereMatch = wherePattern.find(result)
            if (whereMatch != null) {
                var fromClause = whereMatch.groupValues[1].trim()
                var whereClause = whereMatch.groupValues[2].trim()

                val joinConditions = mutableListOf<JoinInfo>()
                val nonJoinConditions = mutableListOf<String>()

                // WHERE 조건을 AND로 분리
                val conditions = whereClause.split(Regex("""\s+AND\s+""", RegexOption.IGNORE_CASE))

                for (condition in conditions) {
                    val leftJoinMatch = ORACLE_LEFT_JOIN_PATTERN.find(condition)
                    val rightJoinMatch = ORACLE_RIGHT_JOIN_PATTERN.find(condition)

                    when {
                        leftJoinMatch != null -> {
                            // t1.col = t2.col(+) → LEFT JOIN
                            val table1 = leftJoinMatch.groupValues[1]
                            val col1 = leftJoinMatch.groupValues[2]
                            val table2 = leftJoinMatch.groupValues[3]
                            val col2 = leftJoinMatch.groupValues[4]
                            joinConditions.add(JoinInfo(table1, table2, "$table1.$col1 = $table2.$col2", "LEFT"))
                        }
                        rightJoinMatch != null -> {
                            // t1.col(+) = t2.col → RIGHT JOIN
                            val table1 = rightJoinMatch.groupValues[1]
                            val col1 = rightJoinMatch.groupValues[2]
                            val table2 = rightJoinMatch.groupValues[3]
                            val col2 = rightJoinMatch.groupValues[4]
                            joinConditions.add(JoinInfo(table1, table2, "$table1.$col1 = $table2.$col2", "RIGHT"))
                        }
                        else -> {
                            nonJoinConditions.add(condition.trim())
                        }
                    }
                }

                if (joinConditions.isNotEmpty()) {
                    // FROM 절에서 테이블 추출
                    val tables = fromClause.split(",").map { it.trim() }
                    val tableAliases = mutableMapOf<String, String>()

                    for (table in tables) {
                        val parts = table.split(Regex("""\s+""")).filter { it.isNotBlank() }
                        if (parts.size >= 2) {
                            tableAliases[parts.last()] = parts.first()
                        } else if (parts.size == 1) {
                            tableAliases[parts[0]] = parts[0]
                        }
                    }

                    // 첫 번째 테이블 + JOIN 구문 생성
                    val newFromClause = StringBuilder()
                    val usedTables = mutableSetOf<String>()

                    // 첫 테이블
                    val firstTable = tables.firstOrNull() ?: ""
                    newFromClause.append(firstTable)
                    val firstAlias = firstTable.split(Regex("""\s+""")).lastOrNull() ?: ""
                    usedTables.add(firstAlias)

                    // JOIN 조건들 추가
                    for (join in joinConditions) {
                        val joinTableAlias = if (join.table1 in usedTables) join.table2 else join.table1
                        val fullTableDef = tables.find { it.split(Regex("""\s+""")).lastOrNull() == joinTableAlias } ?: joinTableAlias

                        if (joinTableAlias !in usedTables) {
                            newFromClause.append("\n${join.joinType} JOIN $fullTableDef ON ${join.condition}")
                            usedTables.add(joinTableAlias)
                        }
                    }

                    // 나머지 테이블은 CROSS JOIN으로 (일반적인 경우는 없지만)
                    for (table in tables) {
                        val alias = table.split(Regex("""\s+""")).lastOrNull() ?: ""
                        if (alias !in usedTables) {
                            newFromClause.append(", $table")
                        }
                    }

                    // WHERE 절 재구성
                    val newWhereClause = if (nonJoinConditions.isNotEmpty()) {
                        "WHERE " + nonJoinConditions.joinToString(" AND ")
                    } else {
                        ""
                    }

                    // SQL 재구성
                    result = result.replace(whereMatch.value, "FROM $newFromClause $newWhereClause")
                    appliedRules.add("Oracle (+) 조인 → ANSI JOIN 변환")

                    warnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Oracle 구식 (+) 조인 문법이 ANSI JOIN으로 변환되었습니다.",
                        severity = WarningSeverity.INFO,
                        suggestion = "변환된 JOIN 구문을 검토하세요."
                    ))
                }
            }
        }

        return result
    }

    /**
     * 조인 정보 데이터 클래스
     */
    private data class JoinInfo(
        val table1: String,
        val table2: String,
        val condition: String,
        val joinType: String  // LEFT, RIGHT
    )
}