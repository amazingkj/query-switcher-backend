package com.sqlswitcher.converter.validation

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.update.Update
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.alter.Alter
import net.sf.jsqlparser.statement.drop.Drop
import org.springframework.stereotype.Service

/**
 * SQL 변환 결과 검증 서비스
 *
 * 변환된 SQL의 유효성을 검사하고 잠재적인 문제를 경고합니다.
 * JSQLParser를 사용한 파싱 검증 기능 포함
 */
@Service("sqlConversionValidationService")
class SqlConversionValidationService {

    // ============ JSQLParser 기반 검증 ============

    /**
     * JSQLParser 파싱 검증 결과
     */
    data class ParseValidationResult(
        val isValid: Boolean,
        val statementType: String?,
        val errorMessage: String?,
        val errorPosition: ErrorPosition?,
        val confidence: Double
    )

    /**
     * 에러 위치 정보
     */
    data class ErrorPosition(
        val line: Int,
        val column: Int
    )

    /**
     * 상세 검증 결과
     */
    data class DetailedValidationResult(
        val parseResult: ParseValidationResult,
        val syntaxWarnings: List<ConversionWarning>,
        val compatibilityIssues: List<String>,
        val overallConfidence: Double,
        val isProductionReady: Boolean
    )

    /**
     * JSQLParser를 사용한 SQL 파싱 검증
     */
    fun validateParsing(sql: String): ParseValidationResult {
        if (sql.isBlank()) {
            return ParseValidationResult(
                isValid = false,
                statementType = null,
                errorMessage = "SQL이 비어있습니다.",
                errorPosition = null,
                confidence = 0.0
            )
        }

        // SQL 전처리 (PL/SQL 블록 등은 파싱 스킵)
        if (isPlSqlBlock(sql)) {
            return ParseValidationResult(
                isValid = true,
                statementType = "PL/SQL Block",
                errorMessage = null,
                errorPosition = null,
                confidence = 0.8  // PL/SQL은 JSQLParser로 완전 검증 불가
            )
        }

        return try {
            val statement = CCJSqlParserUtil.parse(sql)
            val statementType = getStatementType(statement)

            ParseValidationResult(
                isValid = true,
                statementType = statementType,
                errorMessage = null,
                errorPosition = null,
                confidence = 1.0
            )
        } catch (e: Exception) {
            val errorMessage = e.message ?: "파싱 오류"
            val position = extractErrorPosition(errorMessage)

            ParseValidationResult(
                isValid = false,
                statementType = null,
                errorMessage = errorMessage,
                errorPosition = position,
                confidence = 0.0
            )
        }
    }

    /**
     * 대상 방언에 대한 SQL 상세 검증
     */
    fun validateForDialect(sql: String, targetDialect: DialectType): DetailedValidationResult {
        val parseResult = validateParsing(sql)
        val syntaxWarnings = mutableListOf<ConversionWarning>()
        val compatibilityIssues = mutableListOf<String>()
        var confidence = parseResult.confidence

        // 방언별 호환성 검사
        when (targetDialect) {
            DialectType.MYSQL -> {
                if (sql.contains("RETURNING", ignoreCase = true)) {
                    compatibilityIssues.add("RETURNING 절은 MySQL에서 지원되지 않음")
                    syntaxWarnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "RETURNING 절은 MySQL에서 지원되지 않습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "별도의 SELECT 문으로 대체하세요."
                    ))
                    confidence *= 0.8
                }
                if (Regex(""""[\w]+"""").containsMatchIn(sql)) {
                    compatibilityIssues.add("큰따옴표 식별자는 MySQL에서 백틱으로 변환 필요")
                    confidence *= 0.9
                }
            }
            DialectType.POSTGRESQL -> {
                if (Regex("""`\w+`""").containsMatchIn(sql)) {
                    compatibilityIssues.add("백틱은 PostgreSQL에서 큰따옴표로 변환 필요")
                    syntaxWarnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "PostgreSQL에서는 백틱 대신 큰따옴표를 사용하세요.",
                        severity = WarningSeverity.WARNING
                    ))
                    confidence *= 0.85
                }
                if (sql.contains("IFNULL(", ignoreCase = true)) {
                    compatibilityIssues.add("IFNULL은 PostgreSQL에서 COALESCE로 변환 필요")
                    confidence *= 0.9
                }
            }
            DialectType.ORACLE -> {
                if (sql.contains("LIMIT", ignoreCase = true)) {
                    compatibilityIssues.add("LIMIT은 Oracle에서 FETCH FIRST 또는 ROWNUM으로 변환 필요")
                    syntaxWarnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Oracle에서는 LIMIT 대신 FETCH FIRST를 사용하세요.",
                        severity = WarningSeverity.WARNING
                    ))
                    confidence *= 0.8
                }
                if (sql.contains("AUTO_INCREMENT", ignoreCase = true)) {
                    compatibilityIssues.add("AUTO_INCREMENT는 Oracle에서 IDENTITY 또는 시퀀스로 변환 필요")
                    confidence *= 0.85
                }
            }
        }

        // 파싱 실패 시 경고 추가
        if (!parseResult.isValid) {
            syntaxWarnings.add(ConversionWarning(
                type = WarningType.MANUAL_REVIEW_NEEDED,
                message = "SQL 파싱 실패: ${parseResult.errorMessage}",
                severity = WarningSeverity.ERROR,
                suggestion = "SQL 문법을 확인하세요."
            ))
        }

        val isProductionReady = parseResult.isValid &&
                compatibilityIssues.isEmpty() &&
                syntaxWarnings.none { it.severity == WarningSeverity.ERROR }

        return DetailedValidationResult(
            parseResult = parseResult,
            syntaxWarnings = syntaxWarnings,
            compatibilityIssues = compatibilityIssues,
            overallConfidence = confidence,
            isProductionReady = isProductionReady
        )
    }

    /**
     * 변환 전후 SQL 비교 검증
     */
    fun validateConversionPair(
        originalSql: String,
        convertedSql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType
    ): ConversionPairValidationResult {
        val originalParse = validateParsing(originalSql)
        val convertedValidation = validateForDialect(convertedSql, targetDialect)

        // 기존 validateConversion 결과도 포함
        val conversionWarnings = validateConversion(originalSql, convertedSql, sourceDialect, targetDialect)

        // 변환 품질 점수 계산
        val qualityScore = calculateQualityScore(
            originalParse,
            convertedValidation,
            conversionWarnings
        )

        return ConversionPairValidationResult(
            originalValid = originalParse.isValid,
            convertedValid = convertedValidation.parseResult.isValid,
            detailedValidation = convertedValidation,
            conversionWarnings = conversionWarnings,
            qualityScore = qualityScore,
            recommendation = generateRecommendation(qualityScore, conversionWarnings)
        )
    }

    /**
     * 변환 쌍 검증 결과
     */
    data class ConversionPairValidationResult(
        val originalValid: Boolean,
        val convertedValid: Boolean,
        val detailedValidation: DetailedValidationResult,
        val conversionWarnings: List<ConversionWarning>,
        val qualityScore: Double,
        val recommendation: String
    )

    // ============ 유틸리티 메서드 ============

    /**
     * PL/SQL 블록인지 확인
     */
    private fun isPlSqlBlock(sql: String): Boolean {
        val upper = sql.uppercase().trim()
        return upper.startsWith("CREATE OR REPLACE PROCEDURE") ||
                upper.startsWith("CREATE OR REPLACE FUNCTION") ||
                upper.startsWith("CREATE OR REPLACE TRIGGER") ||
                upper.startsWith("CREATE OR REPLACE PACKAGE") ||
                upper.startsWith("CREATE PROCEDURE") ||
                upper.startsWith("CREATE FUNCTION") ||
                upper.startsWith("DECLARE") ||
                upper.startsWith("BEGIN")
    }

    /**
     * 문장 유형 추출
     */
    private fun getStatementType(statement: Statement): String {
        return when (statement) {
            is Select -> "SELECT"
            is Insert -> "INSERT"
            is Update -> "UPDATE"
            is Delete -> "DELETE"
            is CreateTable -> "CREATE TABLE"
            is Alter -> "ALTER"
            is Drop -> "DROP"
            else -> statement.javaClass.simpleName
        }
    }

    /**
     * 에러 위치 추출
     */
    private fun extractErrorPosition(errorMessage: String): ErrorPosition? {
        val lineMatch = Regex("""line\s+(\d+)""", RegexOption.IGNORE_CASE).find(errorMessage)
        val columnMatch = Regex("""column\s+(\d+)""", RegexOption.IGNORE_CASE).find(errorMessage)

        return if (lineMatch != null) {
            ErrorPosition(
                line = lineMatch.groupValues[1].toIntOrNull() ?: 1,
                column = columnMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            )
        } else {
            null
        }
    }

    /**
     * 품질 점수 계산
     */
    private fun calculateQualityScore(
        originalParse: ParseValidationResult,
        convertedValidation: DetailedValidationResult,
        warnings: List<ConversionWarning>
    ): Double {
        var score = 1.0

        // 파싱 실패 시 감점
        if (!originalParse.isValid) score *= 0.5
        if (!convertedValidation.parseResult.isValid) score *= 0.3

        // 경고 수준별 감점
        warnings.forEach { warning ->
            when (warning.severity) {
                WarningSeverity.ERROR -> score *= 0.5
                WarningSeverity.WARNING -> score *= 0.9
                WarningSeverity.INFO -> score *= 0.95
            }
        }

        // 호환성 이슈 감점
        score *= (1.0 - convertedValidation.compatibilityIssues.size * 0.1).coerceAtLeast(0.3)

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * 권장 사항 생성
     */
    private fun generateRecommendation(qualityScore: Double, warnings: List<ConversionWarning>): String {
        return when {
            qualityScore >= 0.9 -> "변환 결과가 우수합니다. 프로덕션 사용에 적합합니다."
            qualityScore >= 0.7 -> "변환 결과가 양호합니다. 경고 사항을 확인 후 사용하세요."
            qualityScore >= 0.5 -> "변환 결과에 주의가 필요합니다. 수동 검토를 권장합니다."
            else -> "변환 결과에 심각한 문제가 있습니다. 수동 변환이 필요합니다."
        } + if (warnings.any { it.severity == WarningSeverity.ERROR }) {
            " (오류 ${warnings.count { it.severity == WarningSeverity.ERROR }}개 발견)"
        } else ""
    }

    // ============ 기존 검증 메서드 ============

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
