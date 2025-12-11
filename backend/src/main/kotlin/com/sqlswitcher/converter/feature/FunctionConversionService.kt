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
                // 변환 타입에 따른 처리
                when (rule.parameterTransform) {
                    ParameterTransform.TO_CASE_WHEN -> {
                        result = convertToCaseWhen(result, rule, warnings, appliedRules)
                    }
                    ParameterTransform.DATE_FORMAT_CONVERT -> {
                        result = convertDateFormatFunction(result, rule, appliedRules)
                    }
                    ParameterTransform.SWAP_FIRST_TWO -> {
                        result = convertWithSwappedParams(result, rule, appliedRules)
                    }
                    else -> {
                        // 함수명만 변경
                        if (rule.sourceFunction.uppercase() != rule.targetFunction.uppercase()) {
                            result = result.replace(pattern, "${rule.targetFunction}(")
                            appliedRules.add("${rule.sourceFunction}() → ${rule.targetFunction}()")
                        }
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
                    DialectType.POSTGRESQL, DialectType.MYSQL -> {
                        // ROWNUM 조건 패턴들: AND ROWNUM <= n, ROWNUM <= n AND, 단독 ROWNUM <= n
                        val rownumWithAndPattern = Regex(
                            """\s+AND\s+ROWNUM\s*(<=?|=)\s*(\d+)""",
                            RegexOption.IGNORE_CASE
                        )
                        val rownumAtStartPattern = Regex(
                            """ROWNUM\s*(<=?|=)\s*(\d+)\s+AND\s+""",
                            RegexOption.IGNORE_CASE
                        )
                        val rownumOnlyPattern = Regex(
                            """ROWNUM\s*(<=?|=)\s*(\d+)""",
                            RegexOption.IGNORE_CASE
                        )

                        var limit: String? = null

                        // AND ROWNUM <= n 패턴 (조건 끝에 있는 경우)
                        val matchWithAnd = rownumWithAndPattern.find(result)
                        if (matchWithAnd != null) {
                            limit = matchWithAnd.groupValues[2]
                            result = rownumWithAndPattern.replace(result, "")
                        } else {
                            // ROWNUM <= n AND 패턴 (조건 시작에 있는 경우)
                            val matchAtStart = rownumAtStartPattern.find(result)
                            if (matchAtStart != null) {
                                limit = matchAtStart.groupValues[2]
                                result = rownumAtStartPattern.replace(result, "")
                            } else {
                                // 단독 ROWNUM <= n 패턴
                                val matchOnly = rownumOnlyPattern.find(result)
                                if (matchOnly != null) {
                                    limit = matchOnly.groupValues[2]
                                    result = rownumOnlyPattern.replace(result, "")
                                }
                            }
                        }

                        if (limit != null) {
                            // 빈 WHERE 절 정리
                            result = result.replace(Regex("""\s+WHERE\s+ORDER\b""", RegexOption.IGNORE_CASE), " ORDER")
                            result = result.replace(Regex("""\s+WHERE\s+GROUP\b""", RegexOption.IGNORE_CASE), " GROUP")
                            result = result.replace(Regex("""\s+WHERE\s*;""", RegexOption.IGNORE_CASE), ";")
                            result = result.replace(Regex("""\s+WHERE\s*$""", RegexOption.IGNORE_CASE), "")
                            // 연속된 공백 정리
                            result = result.replace(Regex("""\s{2,}"""), " ")
                            result = result.trimEnd() + " LIMIT $limit"
                            appliedRules.add("ROWNUM ≤ $limit → LIMIT $limit 변환")
                        } else {
                            warnings.add(ConversionWarning(
                                type = WarningType.MANUAL_REVIEW_NEEDED,
                                message = "복잡한 ROWNUM 사용이 감지되었습니다.",
                                severity = WarningSeverity.WARNING,
                                suggestion = "${targetDialect.name}에서는 ROW_NUMBER() 윈도우 함수 또는 LIMIT을 사용하세요."
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
        // 괄호 매칭을 고려한 DECODE 함수 파싱
        val decodeStartPattern = Regex("""DECODE\s*\(""", RegexOption.IGNORE_CASE)
        var result = sql

        var match = decodeStartPattern.find(result)
        while (match != null) {
            val startIdx = match.range.first
            val argsStartIdx = match.range.last + 1

            // 괄호 매칭으로 DECODE 함수의 끝 찾기
            var depth = 1
            var endIdx = argsStartIdx
            while (endIdx < result.length && depth > 0) {
                when (result[endIdx]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                endIdx++
            }

            if (depth != 0) {
                // 괄호가 맞지 않으면 다음으로
                match = decodeStartPattern.find(result, match.range.last + 1)
                continue
            }

            // DECODE 내부 인자 추출
            val argsStr = result.substring(argsStartIdx, endIdx - 1)
            val args = splitFunctionArgs(argsStr)

            if (args.size < 2) {
                match = decodeStartPattern.find(result, match.range.last + 1)
                continue
            }

            // 첫 번째 인자는 비교 대상
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

            // 치환 후 다음 DECODE 찾기
            match = decodeStartPattern.find(result, startIdx + sb.length)
        }

        return result
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
     * 날짜 포맷 함수 변환 (TO_CHAR, DATE_FORMAT 등)
     * 괄호 매칭을 고려한 파싱
     */
    private fun convertDateFormatFunction(
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

            // 괄호 매칭으로 함수의 끝 찾기
            var depth = 1
            var endIdx = argsStartIdx
            while (endIdx < result.length && depth > 0) {
                when (result[endIdx]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                endIdx++
            }

            if (depth != 0) {
                match = functionStartPattern.find(result, match.range.last + 1)
                continue
            }

            // 함수 내부 인자 추출
            val argsStr = result.substring(argsStartIdx, endIdx - 1)
            val args = splitFunctionArgs(argsStr)

            if (args.isEmpty()) {
                match = functionStartPattern.find(result, match.range.last + 1)
                continue
            }

            // 날짜 포맷 변환
            val convertedArgs = when {
                // Oracle TO_CHAR → MySQL DATE_FORMAT (포맷 변환 필요)
                rule.sourceFunction.equals("TO_CHAR", ignoreCase = true) &&
                rule.targetFunction.equals("DATE_FORMAT", ignoreCase = true) -> {
                    if (args.size >= 2) {
                        val dateExpr = args[0].trim()
                        val oracleFormat = args[1].trim().removeSurrounding("'")
                        val mysqlFormat = convertOracleToMysqlDateFormat(oracleFormat)
                        listOf(dateExpr, "'$mysqlFormat'")
                    } else {
                        args
                    }
                }
                // MySQL DATE_FORMAT → Oracle TO_CHAR (포맷 변환 필요)
                rule.sourceFunction.equals("DATE_FORMAT", ignoreCase = true) &&
                rule.targetFunction.equals("TO_CHAR", ignoreCase = true) -> {
                    if (args.size >= 2) {
                        val dateExpr = args[0].trim()
                        val mysqlFormat = args[1].trim().removeSurrounding("'")
                        val oracleFormat = convertMysqlToOracleDateFormat(mysqlFormat)
                        listOf(dateExpr, "'$oracleFormat'")
                    } else {
                        args
                    }
                }
                // TO_DATE, STR_TO_DATE 등도 포맷 변환 필요
                rule.sourceFunction.equals("TO_DATE", ignoreCase = true) ||
                rule.sourceFunction.equals("STR_TO_DATE", ignoreCase = true) -> {
                    if (args.size >= 2) {
                        val dateStr = args[0].trim()
                        val format = args[1].trim().removeSurrounding("'")
                        val convertedFormat = when {
                            rule.targetFunction.equals("STR_TO_DATE", ignoreCase = true) ->
                                convertOracleToMysqlDateFormat(format)
                            rule.targetFunction.equals("TO_DATE", ignoreCase = true) ||
                            rule.targetFunction.equals("TO_TIMESTAMP", ignoreCase = true) ->
                                convertMysqlToOracleDateFormat(format)
                            else -> format
                        }
                        listOf(dateStr, "'$convertedFormat'")
                    } else {
                        args
                    }
                }
                else -> args
            }

            val replacement = "${rule.targetFunction}(${convertedArgs.joinToString(", ")})"
            result = result.substring(0, startIdx) + replacement + result.substring(endIdx)
            appliedRules.add("${rule.sourceFunction}() → ${rule.targetFunction}()")

            match = functionStartPattern.find(result, startIdx + replacement.length)
        }

        return result
    }

    /**
     * Oracle 날짜 포맷 → MySQL 날짜 포맷 변환
     */
    private fun convertOracleToMysqlDateFormat(oracleFormat: String): String {
        return oracleFormat
            .replace("YYYY", "%Y")
            .replace("YY", "%y")
            .replace("MM", "%m")
            .replace("DD", "%d")
            .replace("HH24", "%H")
            .replace("HH", "%h")
            .replace("MI", "%i")
            .replace("SS", "%s")
            .replace("DAY", "%W")
            .replace("DY", "%a")
            .replace("MON", "%b")
            .replace("MONTH", "%M")
    }

    /**
     * MySQL 날짜 포맷 → Oracle 날짜 포맷 변환
     */
    private fun convertMysqlToOracleDateFormat(mysqlFormat: String): String {
        return mysqlFormat
            .replace("%Y", "YYYY")
            .replace("%y", "YY")
            .replace("%m", "MM")
            .replace("%d", "DD")
            .replace("%H", "HH24")
            .replace("%h", "HH")
            .replace("%i", "MI")
            .replace("%s", "SS")
            .replace("%W", "DAY")
            .replace("%a", "DY")
            .replace("%b", "MON")
            .replace("%M", "MONTH")
    }

    /**
     * 파라미터 스왑 변환 (INSTR ↔ LOCATE 등)
     */
    private fun convertWithSwappedParams(
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

            // 괄호 매칭으로 함수의 끝 찾기
            var depth = 1
            var endIdx = argsStartIdx
            while (endIdx < result.length && depth > 0) {
                when (result[endIdx]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                endIdx++
            }

            if (depth != 0) {
                match = functionStartPattern.find(result, match.range.last + 1)
                continue
            }

            // 함수 내부 인자 추출
            val argsStr = result.substring(argsStartIdx, endIdx - 1)
            val args = splitFunctionArgs(argsStr)

            // 첫 두 파라미터 스왑
            val swappedArgs = if (args.size >= 2) {
                listOf(args[1], args[0]) + args.drop(2)
            } else {
                args
            }

            val replacement = "${rule.targetFunction}(${swappedArgs.joinToString(", ")})"
            result = result.substring(0, startIdx) + replacement + result.substring(endIdx)
            appliedRules.add("${rule.sourceFunction}() → ${rule.targetFunction}() (파라미터 순서 변환)")

            match = functionStartPattern.find(result, startIdx + replacement.length)
        }

        return result
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

            // || 연산자로 연결된 표현식을 찾아서 CONCAT으로 변환
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
     * e.first_name || ' ' || e.last_name → CONCAT(e.first_name, ' ', e.last_name)
     */
    private fun convertPipeConcatToConcat(sql: String, appliedRules: MutableList<String>): String {
        var result = sql
        var converted = false

        // || 가 있는 표현식을 찾아서 처리
        while (result.contains("||")) {
            // || 위치 찾기
            val pipeIdx = result.indexOf("||")
            if (pipeIdx == -1) break

            // || 앞뒤의 표현식 추출
            val (beforePipe, beforeStartIdx) = extractExpressionBeforeWithIndex(result, pipeIdx)
            val (afterPipe, afterEndIdx) = extractExpressionAfterWithIndex(result, pipeIdx + 2)

            if (beforePipe.isEmpty() || afterPipe.isEmpty()) break

            val startIdx = beforeStartIdx
            var endIdx = afterEndIdx

            // 이어지는 || 연결 모두 수집
            val parts = mutableListOf(beforePipe.trim(), afterPipe.trim())
            var searchPos = endIdx

            while (true) {
                // 다음 || 찾기 (공백 허용)
                val remaining = result.substring(searchPos)
                val nextPipeMatch = Regex("""^\s*\|\|""").find(remaining)
                if (nextPipeMatch == null) break

                val actualPipeIdx = searchPos + nextPipeMatch.range.last - 1
                val (nextPart, nextEndIdx) = extractExpressionAfterWithIndex(result, actualPipeIdx + 2)
                if (nextPart.isEmpty()) break

                parts.add(nextPart.trim())
                endIdx = nextEndIdx
                searchPos = endIdx
            }

            // CONCAT으로 변환
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
     * || 앞의 표현식과 시작 인덱스 추출
     * 반환: Pair(표현식, 표현식 시작 인덱스)
     */
    private fun extractExpressionBeforeWithIndex(sql: String, pipeIdx: Int): Pair<String, Int> {
        var idx = pipeIdx - 1
        // 공백 건너뛰기
        while (idx >= 0 && sql[idx].isWhitespace()) idx--
        if (idx < 0) return Pair("", 0)

        val endIdx = idx + 1

        // 문자열 리터럴인 경우
        if (sql[idx] == '\'') {
            idx--
            while (idx >= 0 && sql[idx] != '\'') idx--
            if (idx < 0) return Pair("", 0)
            return Pair(sql.substring(idx, endIdx), idx)
        }

        // 괄호로 끝나는 경우 (함수 호출)
        if (sql[idx] == ')') {
            var depth = 1
            idx--
            while (idx >= 0 && depth > 0) {
                when (sql[idx]) {
                    ')' -> depth++
                    '(' -> depth--
                }
                idx--
            }
            // 함수명 추출
            while (idx >= 0 && (sql[idx].isLetterOrDigit() || sql[idx] == '_' || sql[idx] == '.')) idx--
            return Pair(sql.substring(idx + 1, endIdx), idx + 1)
        }

        // 일반 식별자 (컬럼명, 테이블.컬럼명)
        while (idx >= 0 && (sql[idx].isLetterOrDigit() || sql[idx] == '_' || sql[idx] == '.')) idx--
        return Pair(sql.substring(idx + 1, endIdx), idx + 1)
    }

    /**
     * || 뒤의 표현식과 끝 인덱스 추출
     * 반환: Pair(표현식, 표현식 끝 인덱스)
     */
    private fun extractExpressionAfterWithIndex(sql: String, startIdx: Int): Pair<String, Int> {
        var idx = startIdx
        // 공백 건너뛰기
        while (idx < sql.length && sql[idx].isWhitespace()) idx++
        if (idx >= sql.length) return Pair("", startIdx)

        val beginIdx = idx

        // 문자열 리터럴인 경우
        if (sql[idx] == '\'') {
            idx++
            while (idx < sql.length && sql[idx] != '\'') idx++
            if (idx >= sql.length) return Pair("", startIdx)
            return Pair(sql.substring(beginIdx, idx + 1), idx + 1)
        }

        // 괄호로 시작하는 경우 (함수 호출 또는 서브쿼리)
        if (sql[idx] == '(') {
            var depth = 1
            idx++
            while (idx < sql.length && depth > 0) {
                when (sql[idx]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                idx++
            }
            return Pair(sql.substring(beginIdx, idx), idx)
        }

        // 일반 식별자 (컬럼명, 테이블.컬럼명, 함수호출)
        while (idx < sql.length && (sql[idx].isLetterOrDigit() || sql[idx] == '_' || sql[idx] == '.')) idx++

        // 함수 호출인 경우 괄호까지 포함
        if (idx < sql.length && sql[idx] == '(') {
            var depth = 1
            idx++
            while (idx < sql.length && depth > 0) {
                when (sql[idx]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                idx++
            }
        }

        return Pair(sql.substring(beginIdx, idx), idx)
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

            // 괄호 매칭으로 CONCAT 함수의 끝 찾기
            var depth = 1
            var endIdx = argsStartIdx
            while (endIdx < result.length && depth > 0) {
                when (result[endIdx]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                endIdx++
            }

            if (depth != 0) {
                match = concatStartPattern.find(result, match.range.last + 1)
                continue
            }

            // CONCAT 내부 인자 추출
            val argsStr = result.substring(argsStartIdx, endIdx - 1)
            val args = splitFunctionArgs(argsStr)

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