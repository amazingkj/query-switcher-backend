package com.sqlswitcher.converter.recovery

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * 고급 에러 복구 전략
 *
 * SQL 변환 중 발생하는 다양한 에러에 대한 복구 전략을 제공
 *
 * 전략:
 * - 구문 단순화
 * - 대체 변환 시도
 * - 부분 추출 및 변환
 * - 폴백 처리
 */
object AdvancedRecoveryStrategies {

    /**
     * 복구 전략 인터페이스
     */
    interface RecoveryStrategy {
        val name: String
        val priority: Int
        fun canHandle(sql: String, error: Exception): Boolean
        fun recover(sql: String, error: Exception, targetDialect: DialectType): RecoveryAttempt
    }

    /**
     * 복구 시도 결과
     */
    data class RecoveryAttempt(
        val success: Boolean,
        val recoveredSql: String,
        val strategyUsed: String,
        val warning: ConversionWarning? = null,
        val confidence: Double = 1.0  // 변환 신뢰도 (0.0 ~ 1.0)
    )

    /**
     * 전체 복구 결과
     */
    data class AdvancedRecoveryResult(
        val originalSql: String,
        val finalSql: String,
        val success: Boolean,
        val strategiesAttempted: List<String>,
        val strategyUsed: String?,
        val warnings: List<ConversionWarning>,
        val confidence: Double
    )

    // ============ 복구 전략 구현 ============

    /**
     * 1. 주석 제거 전략
     * 주석으로 인한 파싱 오류 시 주석 제거 후 재시도
     */
    object CommentRemovalStrategy : RecoveryStrategy {
        override val name = "주석 제거"
        override val priority = 100

        private val SINGLE_LINE_COMMENT = Regex("""--[^\n]*""")
        private val MULTI_LINE_COMMENT = Regex("""/\*[\s\S]*?\*/""")

        override fun canHandle(sql: String, error: Exception): Boolean {
            return sql.contains("--") || sql.contains("/*")
        }

        override fun recover(sql: String, error: Exception, targetDialect: DialectType): RecoveryAttempt {
            val cleanedSql = sql
                .replace(MULTI_LINE_COMMENT, " ")
                .replace(SINGLE_LINE_COMMENT, "")
                .replace(Regex("""\s+"""), " ")
                .trim()

            return RecoveryAttempt(
                success = cleanedSql.isNotEmpty() && cleanedSql != sql,
                recoveredSql = cleanedSql,
                strategyUsed = name,
                warning = ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "주석이 제거되었습니다.",
                    severity = WarningSeverity.INFO
                ),
                confidence = 0.9
            )
        }
    }

    /**
     * 2. 힌트 제거 전략
     * Oracle 힌트로 인한 파싱 오류 시 힌트 제거
     */
    object HintRemovalStrategy : RecoveryStrategy {
        override val name = "힌트 제거"
        override val priority = 90

        private val HINT_PATTERN = Regex("""/\*\+[\s\S]*?\*/""")

        override fun canHandle(sql: String, error: Exception): Boolean {
            return HINT_PATTERN.containsMatchIn(sql)
        }

        override fun recover(sql: String, error: Exception, targetDialect: DialectType): RecoveryAttempt {
            val cleanedSql = HINT_PATTERN.replace(sql, "").replace(Regex("""\s+"""), " ").trim()

            return RecoveryAttempt(
                success = true,
                recoveredSql = cleanedSql,
                strategyUsed = name,
                warning = ConversionWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "Oracle 힌트가 제거되었습니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "힌트 기반 최적화는 대상 DB의 힌트 또는 인덱스로 대체하세요."
                ),
                confidence = 0.85
            )
        }
    }

    /**
     * 3. 괄호 균형 복구 전략
     * 불균형한 괄호로 인한 오류 시 균형 맞추기
     */
    object ParenthesisBalanceStrategy : RecoveryStrategy {
        override val name = "괄호 균형 복구"
        override val priority = 80

        override fun canHandle(sql: String, error: Exception): Boolean {
            val openCount = sql.count { it == '(' }
            val closeCount = sql.count { it == ')' }
            return openCount != closeCount
        }

        override fun recover(sql: String, error: Exception, targetDialect: DialectType): RecoveryAttempt {
            var result = sql
            val openCount = result.count { it == '(' }
            val closeCount = result.count { it == ')' }

            when {
                openCount > closeCount -> {
                    // 닫는 괄호 부족
                    result += ")".repeat(openCount - closeCount)
                }
                closeCount > openCount -> {
                    // 여는 괄호 부족 - 앞에 추가
                    result = "(".repeat(closeCount - openCount) + result
                }
            }

            return RecoveryAttempt(
                success = true,
                recoveredSql = result,
                strategyUsed = name,
                warning = ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "불균형한 괄호가 자동으로 수정되었습니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "변환된 SQL의 괄호 위치를 확인하세요."
                ),
                confidence = 0.7
            )
        }
    }

    /**
     * 4. 문자열 리터럴 이스케이프 전략
     */
    object StringEscapeStrategy : RecoveryStrategy {
        override val name = "문자열 이스케이프 수정"
        override val priority = 70

        private val UNTERMINATED_STRING = Regex("""'[^']*$""", RegexOption.MULTILINE)

        override fun canHandle(sql: String, error: Exception): Boolean {
            val message = error.message?.lowercase() ?: ""
            return message.contains("unterminated") ||
                    message.contains("string") ||
                    UNTERMINATED_STRING.containsMatchIn(sql)
        }

        override fun recover(sql: String, error: Exception, targetDialect: DialectType): RecoveryAttempt {
            // 미종료 문자열 찾아 닫기
            var result = sql

            // 홀수 개의 따옴표 확인
            val quoteCount = result.count { it == '\'' }
            if (quoteCount % 2 != 0) {
                // 마지막 홀수 따옴표 뒤에 닫는 따옴표 추가
                result += "'"
            }

            return RecoveryAttempt(
                success = result != sql,
                recoveredSql = result,
                strategyUsed = name,
                warning = ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "미종료 문자열이 수정되었습니다.",
                    severity = WarningSeverity.WARNING
                ),
                confidence = 0.6
            )
        }
    }

    /**
     * 5. CONNECT BY 대체 전략
     * Oracle CONNECT BY를 CTE로 대체
     */
    object ConnectByReplacementStrategy : RecoveryStrategy {
        override val name = "CONNECT BY → CTE 변환"
        override val priority = 60

        private val CONNECT_BY_PATTERN = Regex(
            """CONNECT\s+BY\s+(?:NOCYCLE\s+)?(?:PRIOR\s+)?[\s\S]+?(?=START\s+WITH|ORDER\s+SIBLINGS|$)""",
            RegexOption.IGNORE_CASE
        )

        override fun canHandle(sql: String, error: Exception): Boolean {
            return sql.uppercase().contains("CONNECT BY")
        }

        override fun recover(sql: String, error: Exception, targetDialect: DialectType): RecoveryAttempt {
            // CONNECT BY를 주석 처리하고 CTE 템플릿 제공
            val commentedSql = CONNECT_BY_PATTERN.replace(sql) { match ->
                "/* ${match.value} -- requires WITH RECURSIVE conversion */"
            }

            val cteTemplate = """
                |/*
                | * CONNECT BY를 WITH RECURSIVE로 변환하세요:
                | *
                | * WITH RECURSIVE cte AS (
                | *     -- 기본 케이스 (START WITH 조건)
                | *     SELECT ... FROM table WHERE [start_condition]
                | *     UNION ALL
                | *     -- 재귀 케이스 (CONNECT BY 조건)
                | *     SELECT ... FROM table t JOIN cte ON [connect_condition]
                | * )
                | * SELECT * FROM cte
                | */
            """.trimMargin()

            return RecoveryAttempt(
                success = true,
                recoveredSql = cteTemplate + "\n" + commentedSql,
                strategyUsed = name,
                warning = ConversionWarning(
                    type = WarningType.MANUAL_REVIEW_NEEDED,
                    message = "CONNECT BY 계층 쿼리는 수동 변환이 필요합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "WITH RECURSIVE CTE를 사용하여 계층 쿼리를 재작성하세요."
                ),
                confidence = 0.5
            )
        }
    }

    /**
     * 6. Oracle 물리적 속성 제거 전략
     */
    object PhysicalAttributeRemovalStrategy : RecoveryStrategy {
        override val name = "물리적 속성 제거"
        override val priority = 95

        private val PATTERNS = listOf(
            Regex("""TABLESPACE\s+["'`]?\w+["'`]?""", RegexOption.IGNORE_CASE),
            Regex("""STORAGE\s*\([^)]*\)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("""PCTFREE\s+\d+""", RegexOption.IGNORE_CASE),
            Regex("""PCTUSED\s+\d+""", RegexOption.IGNORE_CASE),
            Regex("""INITRANS\s+\d+""", RegexOption.IGNORE_CASE),
            Regex("""MAXTRANS\s+\d+""", RegexOption.IGNORE_CASE),
            Regex("""\b(LOGGING|NOLOGGING)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(PARALLEL|NOPARALLEL)(\s*\(\s*\d+\s*\))?""", RegexOption.IGNORE_CASE)
        )

        override fun canHandle(sql: String, error: Exception): Boolean {
            return PATTERNS.any { it.containsMatchIn(sql) }
        }

        override fun recover(sql: String, error: Exception, targetDialect: DialectType): RecoveryAttempt {
            var result = sql
            for (pattern in PATTERNS) {
                result = pattern.replace(result, "")
            }
            result = result.replace(Regex("""\s+"""), " ").trim()

            return RecoveryAttempt(
                success = result != sql,
                recoveredSql = result,
                strategyUsed = name,
                warning = ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Oracle 물리적 속성이 제거되었습니다.",
                    severity = WarningSeverity.INFO,
                    suggestion = "대상 DB의 스토리지 설정을 별도로 구성하세요."
                ),
                confidence = 0.95
            )
        }
    }

    /**
     * 7. 세미콜론 수정 전략
     */
    object SemicolonFixStrategy : RecoveryStrategy {
        override val name = "세미콜론 수정"
        override val priority = 85

        override fun canHandle(sql: String, error: Exception): Boolean {
            val message = error.message?.lowercase() ?: ""
            return message.contains("semicolon") ||
                    message.contains("expected ;") ||
                    message.contains("missing ;")
        }

        override fun recover(sql: String, error: Exception, targetDialect: DialectType): RecoveryAttempt {
            var result = sql.trimEnd()
            if (!result.endsWith(";")) {
                result += ";"
            }

            return RecoveryAttempt(
                success = true,
                recoveredSql = result,
                strategyUsed = name,
                confidence = 0.95
            )
        }
    }

    /**
     * 8. 예약어 이스케이프 전략
     */
    object ReservedWordEscapeStrategy : RecoveryStrategy {
        override val name = "예약어 이스케이프"
        override val priority = 75

        private val COMMON_RESERVED = setOf(
            "USER", "DATE", "TIME", "ORDER", "GROUP", "INDEX", "KEY", "VALUE",
            "LEVEL", "COMMENT", "STATUS", "TYPE", "NAME", "SIZE", "DESC", "ASC"
        )

        override fun canHandle(sql: String, error: Exception): Boolean {
            val message = error.message?.lowercase() ?: ""
            return message.contains("reserved") ||
                    message.contains("keyword") ||
                    COMMON_RESERVED.any { sql.uppercase().contains(" $it ") || sql.uppercase().contains(".$it") }
        }

        override fun recover(sql: String, error: Exception, targetDialect: DialectType): RecoveryAttempt {
            var result = sql
            val quoteChar = when (targetDialect) {
                DialectType.MYSQL -> "`"
                else -> "\""
            }

            for (word in COMMON_RESERVED) {
                // 테이블.컬럼 또는 공백 후 예약어 패턴
                val pattern = Regex("""(\.|,|\s)($word)(\s|,|\)|$)""", RegexOption.IGNORE_CASE)
                result = pattern.replace(result) { match ->
                    "${match.groupValues[1]}$quoteChar${match.groupValues[2]}$quoteChar${match.groupValues[3]}"
                }
            }

            return RecoveryAttempt(
                success = result != sql,
                recoveredSql = result,
                strategyUsed = name,
                warning = ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "예약어가 이스케이프되었습니다.",
                    severity = WarningSeverity.INFO
                ),
                confidence = 0.85
            )
        }
    }

    // ============ 전략 실행기 ============

    /**
     * 등록된 모든 전략
     */
    private val strategies: List<RecoveryStrategy> = listOf(
        CommentRemovalStrategy,
        HintRemovalStrategy,
        PhysicalAttributeRemovalStrategy,
        SemicolonFixStrategy,
        ParenthesisBalanceStrategy,
        ReservedWordEscapeStrategy,
        StringEscapeStrategy,
        ConnectByReplacementStrategy
    ).sortedByDescending { it.priority }

    /**
     * 에러 복구 시도
     *
     * 등록된 전략을 우선순위 순으로 시도하여 복구
     */
    fun attemptRecovery(
        sql: String,
        error: Exception,
        targetDialect: DialectType,
        conversionFn: ((String) -> String)? = null
    ): AdvancedRecoveryResult {
        val attemptedStrategies = mutableListOf<String>()
        val warnings = mutableListOf<ConversionWarning>()
        var currentSql = sql
        var successfulStrategy: String? = null
        var finalConfidence = 1.0

        for (strategy in strategies) {
            if (!strategy.canHandle(currentSql, error)) continue

            attemptedStrategies.add(strategy.name)
            val attempt = strategy.recover(currentSql, error, targetDialect)

            if (attempt.success) {
                currentSql = attempt.recoveredSql
                attempt.warning?.let { warnings.add(it) }
                finalConfidence = minOf(finalConfidence, attempt.confidence)
                successfulStrategy = strategy.name

                // 변환 함수가 있으면 복구된 SQL로 변환 재시도
                if (conversionFn != null) {
                    try {
                        val converted = conversionFn(currentSql)
                        return AdvancedRecoveryResult(
                            originalSql = sql,
                            finalSql = converted,
                            success = true,
                            strategiesAttempted = attemptedStrategies,
                            strategyUsed = successfulStrategy,
                            warnings = warnings,
                            confidence = finalConfidence
                        )
                    } catch (e: Exception) {
                        // 변환 여전히 실패 - 다음 전략 시도
                        continue
                    }
                }
            }
        }

        // 모든 전략 적용 후 결과 반환
        val finalSuccess = currentSql != sql

        if (!finalSuccess) {
            warnings.add(ConversionWarning(
                type = WarningType.MANUAL_REVIEW_NEEDED,
                message = "자동 복구에 실패했습니다. 수동 변환이 필요합니다.",
                severity = WarningSeverity.ERROR,
                suggestion = "SQL 구문을 확인하고 지원되는 형식으로 수정하세요."
            ))
        }

        return AdvancedRecoveryResult(
            originalSql = sql,
            finalSql = if (finalSuccess) currentSql else sql,
            success = finalSuccess,
            strategiesAttempted = attemptedStrategies,
            strategyUsed = successfulStrategy,
            warnings = warnings,
            confidence = if (finalSuccess) finalConfidence else 0.0
        )
    }

    /**
     * 여러 전략을 순차적으로 적용
     */
    fun applyStrategiesSequentially(
        sql: String,
        targetDialect: DialectType,
        strategiesToApply: List<RecoveryStrategy> = strategies
    ): Pair<String, List<ConversionWarning>> {
        var currentSql = sql
        val warnings = mutableListOf<ConversionWarning>()

        for (strategy in strategiesToApply) {
            try {
                if (strategy.canHandle(currentSql, Exception("pre-apply"))) {
                    val attempt = strategy.recover(currentSql, Exception("pre-apply"), targetDialect)
                    if (attempt.success) {
                        currentSql = attempt.recoveredSql
                        attempt.warning?.let { warnings.add(it) }
                    }
                }
            } catch (e: Exception) {
                // 전략 적용 실패 - 무시하고 계속
            }
        }

        return Pair(currentSql, warnings)
    }

    /**
     * SQL 정규화 (변환 전 전처리)
     */
    fun normalizeSql(sql: String, sourceDialect: DialectType): String {
        var result = sql

        // 여러 공백을 단일 공백으로
        result = result.replace(Regex("""\s+"""), " ")

        // 앞뒤 공백 제거
        result = result.trim()

        // Oracle 특수 구문 정규화
        if (sourceDialect == DialectType.ORACLE) {
            // 이중 따옴표 식별자 정규화
            result = result.replace(Regex(""""(\w+)""""), "\"$1\"")
        }

        // MySQL 특수 구문 정규화
        if (sourceDialect == DialectType.MYSQL) {
            // 백틱 식별자 유지
        }

        return result
    }
}
