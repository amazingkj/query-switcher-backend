package com.sqlswitcher.converter.validation

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import org.springframework.stereotype.Service

/**
 * SQL 변환 결과 검증 서비스
 *
 * 변환된 SQL의 유효성을 검사하고 잠재적인 문제를 경고합니다.
 */
@Service
class SqlValidationService {

    /**
     * 변환된 SQL 검증
     * @return 검증 경고 목록
     */
    fun validateConversion(
        originalSql: String,
        convertedSql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType
    ): List<ConversionWarning> {
        val warnings = mutableListOf<ConversionWarning>()

        // 1. 기본 구문 검증
        warnings.addAll(validateBasicSyntax(convertedSql, targetDialect))

        // 2. 괄호 균형 검증
        validateBracketBalance(convertedSql)?.let { warnings.add(it) }

        // 3. 따옴표 균형 검증
        validateQuoteBalance(convertedSql)?.let { warnings.add(it) }

        // 4. 미완료 변환 검출
        warnings.addAll(detectIncompleteConversions(convertedSql, sourceDialect, targetDialect))

        // 5. 잠재적 데이터 손실 검출
        warnings.addAll(detectPotentialDataLoss(originalSql, convertedSql))

        // 6. 성능 관련 경고
        warnings.addAll(detectPerformanceIssues(convertedSql, targetDialect))

        return warnings
    }

    /**
     * 기본 SQL 구문 검증
     */
    private fun validateBasicSyntax(sql: String, targetDialect: DialectType): List<ConversionWarning> {
        val warnings = mutableListOf<ConversionWarning>()
        val upperSql = sql.uppercase()

        // SELECT 문 기본 구조 검증
        if (upperSql.contains("SELECT") && !upperSql.contains("FROM") &&
            !upperSql.contains("DUAL") && targetDialect != DialectType.POSTGRESQL) {
            // PostgreSQL은 FROM 없이 SELECT 가능
            if (targetDialect == DialectType.MYSQL && !isSimpleExpression(sql)) {
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "SELECT 문에 FROM 절이 없습니다.",
                    severity = WarningSeverity.INFO,
                    suggestion = "MySQL에서는 FROM DUAL을 추가하거나, 단순 표현식인지 확인하세요."
                ))
            }
        }

        // 빈 괄호 검출
        if (sql.contains("()") && !sql.contains("NOW()") && !sql.contains("RAND()") &&
            !sql.contains("SYSDATE()") && !sql.contains("CURRENT_TIMESTAMP()")) {
            val emptyParenPattern = Regex("""(\w+)\s*\(\s*\)""")
            emptyParenPattern.findAll(sql).forEach { match ->
                val funcName = match.groupValues[1].uppercase()
                if (!isNoArgFunction(funcName)) {
                    warnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "함수 '$funcName'에 인자가 없습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "함수 인자가 올바르게 변환되었는지 확인하세요."
                    ))
                }
            }
        }

        return warnings
    }

    /**
     * 괄호 균형 검증
     */
    private fun validateBracketBalance(sql: String): ConversionWarning? {
        var depth = 0
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0

        while (i < sql.length) {
            val char = sql[i]

            when {
                char == '\'' && !inDoubleQuote -> {
                    if (inSingleQuote && i + 1 < sql.length && sql[i + 1] == '\'') {
                        i += 2
                        continue
                    }
                    inSingleQuote = !inSingleQuote
                }
                char == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
                !inSingleQuote && !inDoubleQuote -> {
                    when (char) {
                        '(' -> depth++
                        ')' -> depth--
                    }
                }
            }
            i++
        }

        return if (depth != 0) {
            ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "괄호가 균형을 이루지 않습니다. (열린 괄호: $depth)",
                severity = WarningSeverity.ERROR,
                suggestion = "변환된 SQL의 괄호를 확인하세요."
            )
        } else null
    }

    /**
     * 따옴표 균형 검증
     */
    private fun validateQuoteBalance(sql: String): ConversionWarning? {
        var inSingleQuote = false
        var i = 0

        while (i < sql.length) {
            val char = sql[i]
            if (char == '\'') {
                if (inSingleQuote && i + 1 < sql.length && sql[i + 1] == '\'') {
                    i += 2
                    continue
                }
                inSingleQuote = !inSingleQuote
            }
            i++
        }

        return if (inSingleQuote) {
            ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "문자열 리터럴이 닫히지 않았습니다.",
                severity = WarningSeverity.ERROR,
                suggestion = "변환된 SQL의 문자열 따옴표를 확인하세요."
            )
        } else null
    }

    /**
     * 미완료 변환 검출
     */
    private fun detectIncompleteConversions(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType
    ): List<ConversionWarning> {
        val warnings = mutableListOf<ConversionWarning>()
        val upperSql = sql.uppercase()

        // Oracle 전용 기능이 남아있는지 확인
        if (sourceDialect == DialectType.ORACLE && targetDialect != DialectType.ORACLE) {
            val oracleOnlyPatterns = listOf(
                "CONNECT BY" to "계층적 쿼리(CONNECT BY)가 변환되지 않았습니다. WITH RECURSIVE 사용을 고려하세요.",
                "START WITH" to "START WITH 절이 변환되지 않았습니다.",
                "PRIOR " to "PRIOR 키워드가 변환되지 않았습니다.",
                ".NEXTVAL" to "시퀀스 NEXTVAL이 변환되지 않았습니다.",
                ".CURRVAL" to "시퀀스 CURRVAL이 변환되지 않았습니다.",
                "ROWID" to "ROWID가 변환되지 않았습니다.",
                "MINUS" to "MINUS가 EXCEPT로 변환되어야 합니다."
            )

            for ((pattern, message) in oracleOnlyPatterns) {
                if (upperSql.contains(pattern)) {
                    warnings.add(ConversionWarning(
                        type = WarningType.PARTIAL_SUPPORT,
                        message = message,
                        severity = WarningSeverity.WARNING,
                        suggestion = "수동으로 변환이 필요합니다."
                    ))
                }
            }
        }

        // MySQL 전용 기능이 남아있는지 확인
        if (sourceDialect == DialectType.MYSQL && targetDialect != DialectType.MYSQL) {
            if (upperSql.contains("LIMIT") && targetDialect == DialectType.ORACLE) {
                if (!upperSql.contains("FETCH") && !upperSql.contains("ROWNUM")) {
                    warnings.add(ConversionWarning(
                        type = WarningType.PARTIAL_SUPPORT,
                        message = "LIMIT 절이 Oracle 구문으로 변환되지 않았을 수 있습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "FETCH FIRST 또는 ROWNUM을 사용하세요."
                    ))
                }
            }
        }

        // 주석으로 표시된 미완료 변환 검출
        val commentWarningPattern = Regex("""/\*\s*(not supported|unsupported|approximation|미지원)\s*\*/""", RegexOption.IGNORE_CASE)
        commentWarningPattern.findAll(sql).forEach {
            warnings.add(ConversionWarning(
                type = WarningType.PARTIAL_SUPPORT,
                message = "근사 변환 또는 미지원 기능이 포함되어 있습니다: ${it.value}",
                severity = WarningSeverity.INFO,
                suggestion = "주석을 확인하고 필요시 수동 수정하세요."
            ))
        }

        return warnings
    }

    /**
     * 잠재적 데이터 손실 검출
     */
    private fun detectPotentialDataLoss(originalSql: String, convertedSql: String): List<ConversionWarning> {
        val warnings = mutableListOf<ConversionWarning>()
        val upperOriginal = originalSql.uppercase()
        val upperConverted = convertedSql.uppercase()

        // 원본에 있던 중요 키워드가 변환 후 사라졌는지 확인
        val criticalKeywords = listOf(
            "WHERE" to "WHERE 절이 변환 중 손실되었을 수 있습니다.",
            "GROUP BY" to "GROUP BY 절이 변환 중 손실되었을 수 있습니다.",
            "ORDER BY" to "ORDER BY 절이 변환 중 손실되었을 수 있습니다.",
            "HAVING" to "HAVING 절이 변환 중 손실되었을 수 있습니다.",
            "DISTINCT" to "DISTINCT 키워드가 변환 중 손실되었을 수 있습니다."
        )

        for ((keyword, message) in criticalKeywords) {
            if (upperOriginal.contains(keyword) && !upperConverted.contains(keyword)) {
                warnings.add(ConversionWarning(
                    type = WarningType.DATA_TYPE_MISMATCH,
                    message = message,
                    severity = WarningSeverity.ERROR,
                    suggestion = "변환된 SQL을 확인하고 누락된 절을 복원하세요."
                ))
            }
        }

        // 함수 개수 비교 (대략적)
        val originalFuncCount = Regex("""\w+\s*\(""").findAll(upperOriginal).count()
        val convertedFuncCount = Regex("""\w+\s*\(""").findAll(upperConverted).count()

        if (originalFuncCount > 0 && convertedFuncCount < originalFuncCount * 0.5) {
            warnings.add(ConversionWarning(
                type = WarningType.PARTIAL_SUPPORT,
                message = "변환 후 함수 호출 수가 크게 감소했습니다. (원본: $originalFuncCount, 변환: $convertedFuncCount)",
                severity = WarningSeverity.WARNING,
                suggestion = "일부 함수가 변환되지 않았을 수 있습니다."
            ))
        }

        return warnings
    }

    /**
     * 성능 관련 경고 검출
     */
    private fun detectPerformanceIssues(sql: String, @Suppress("UNUSED_PARAMETER") targetDialect: DialectType): List<ConversionWarning> {
        val warnings = mutableListOf<ConversionWarning>()
        val upperSql = sql.uppercase()

        // 대형 IN 절 검출
        val inClausePattern = Regex("""IN\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
        inClausePattern.findAll(sql).forEach { match ->
            val elements = match.groupValues[1].split(",")
            if (elements.size > 100) {
                warnings.add(ConversionWarning(
                    type = WarningType.PERFORMANCE_WARNING,
                    message = "IN 절에 ${elements.size}개의 요소가 있습니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "성능 향상을 위해 임시 테이블 또는 JOIN 사용을 고려하세요."
                ))
            }
        }

        // 중첩 서브쿼리 검출
        val subqueryDepth = countSubqueryDepth(sql)
        if (subqueryDepth > 3) {
            warnings.add(ConversionWarning(
                type = WarningType.PERFORMANCE_WARNING,
                message = "서브쿼리 중첩 깊이가 ${subqueryDepth}입니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "CTE(WITH 절) 또는 JOIN으로 리팩토링을 고려하세요."
            ))
        }

        // LIKE '%...' 패턴 검출 (인덱스 사용 불가)
        if (upperSql.contains("LIKE '%") || upperSql.contains("LIKE '%")) {
            warnings.add(ConversionWarning(
                type = WarningType.PERFORMANCE_WARNING,
                message = "LIKE 절이 와일드카드로 시작합니다.",
                severity = WarningSeverity.INFO,
                suggestion = "이 패턴은 인덱스를 사용하지 못합니다. Full-Text 검색을 고려하세요."
            ))
        }

        // SELECT * 검출
        if (Regex("""SELECT\s+\*\s+FROM""", RegexOption.IGNORE_CASE).containsMatchIn(sql)) {
            warnings.add(ConversionWarning(
                type = WarningType.PERFORMANCE_WARNING,
                message = "SELECT * 사용이 감지되었습니다.",
                severity = WarningSeverity.INFO,
                suggestion = "필요한 컬럼만 명시적으로 선택하는 것이 권장됩니다."
            ))
        }

        return warnings
    }

    /**
     * 서브쿼리 중첩 깊이 계산
     */
    private fun countSubqueryDepth(sql: String): Int {
        val upperSql = sql.uppercase()
        var maxDepth = 0
        var currentDepth = 0
        var inString = false
        var i = 0

        while (i < upperSql.length) {
            if (upperSql[i] == '\'') {
                if (i + 1 < upperSql.length && upperSql[i + 1] == '\'') {
                    i += 2
                    continue
                }
                inString = !inString
            } else if (!inString) {
                if (i + 6 < upperSql.length && upperSql.substring(i, i + 6) == "SELECT") {
                    currentDepth++
                    maxDepth = maxOf(maxDepth, currentDepth)
                } else if (upperSql[i] == ')') {
                    if (currentDepth > 0) currentDepth--
                }
            }
            i++
        }

        return maxDepth
    }

    /**
     * 단순 표현식인지 확인
     */
    private fun isSimpleExpression(sql: String): Boolean {
        val upperSql = sql.uppercase().trim()
        return upperSql.startsWith("SELECT") &&
               !upperSql.contains("FROM") &&
               (upperSql.contains("1+1") ||
                upperSql.contains("NOW()") ||
                Regex("""SELECT\s+\d+""").containsMatchIn(upperSql))
    }

    /**
     * 인자 없는 함수인지 확인
     */
    private fun isNoArgFunction(funcName: String): Boolean {
        return funcName in setOf(
            "NOW", "SYSDATE", "SYSTIMESTAMP", "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME",
            "CURDATE", "CURTIME", "RAND", "RANDOM", "GETDATE", "NEWID", "UUID",
            "PI", "USER", "CURRENT_USER", "SESSION_USER", "SYSTEM_USER"
        )
    }
}
