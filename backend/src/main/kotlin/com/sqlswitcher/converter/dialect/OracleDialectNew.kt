package com.sqlswitcher.converter.dialect

import com.sqlswitcher.converter.core.*
import com.sqlswitcher.converter.feature.*
import org.springframework.stereotype.Component

/**
 * Oracle SQL 방언 - 슬림화된 버전
 *
 * 핵심 로직은 서비스 클래스에 위임하고, Oracle 특화 기능만 구현
 */
@Component
class OracleDialectNew(
    functionService: FunctionConversionService,
    dataTypeService: DataTypeConversionService,
    ddlService: DDLConversionService,
    selectService: SelectConversionService,
    triggerService: TriggerConversionService,
    sequenceService: SequenceConversionService
) : BaseDialect(
    functionService, dataTypeService, ddlService, selectService, triggerService, sequenceService
) {
    override val dialectType = DialectType.ORACLE

    /**
     * Oracle 특화 변환
     */
    override fun convertSql(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        var result = super.convertSql(sql, targetDialect, warnings, appliedRules, options)

        // Oracle 특화: CONNECT BY 계층 쿼리 처리
        if (result.uppercase().contains("CONNECT BY")) {
            result = handleConnectBy(result, targetDialect, warnings, appliedRules)
        }

        // Oracle 특화: PIVOT/UNPIVOT 처리
        if (result.uppercase().contains("PIVOT") || result.uppercase().contains("UNPIVOT")) {
            result = handlePivot(result, targetDialect, warnings, appliedRules)
        }

        // Oracle 특화: Hint 처리
        if (result.contains("/*+")) {
            result = handleHints(result, targetDialect, warnings, appliedRules)
        }

        return result
    }

    /**
     * CONNECT BY 계층 쿼리 처리
     */
    private fun handleConnectBy(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        when (targetDialect) {
            DialectType.MYSQL, DialectType.POSTGRESQL -> {
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Oracle CONNECT BY 계층 쿼리는 WITH RECURSIVE로 수동 변환이 필요합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = """
                        WITH RECURSIVE cte AS (
                            SELECT ... FROM table WHERE start_condition
                            UNION ALL
                            SELECT ... FROM table t JOIN cte c ON t.parent = c.id
                        )
                        SELECT * FROM cte;
                    """.trimIndent()
                ))
                appliedRules.add("CONNECT BY 감지됨 - WITH RECURSIVE로 수동 변환 필요")
            }
            DialectType.TIBERO -> {
                // Tibero는 CONNECT BY 지원
            }
            else -> {}
        }
        return sql
    }

    /**
     * PIVOT/UNPIVOT 처리
     */
    private fun handlePivot(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        when (targetDialect) {
            DialectType.MYSQL -> {
                warnings.add(ConversionWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "MySQL은 PIVOT/UNPIVOT을 지원하지 않습니다.",
                    severity = WarningSeverity.ERROR,
                    suggestion = "CASE WHEN과 GROUP BY를 조합하여 구현하세요."
                ))
            }
            DialectType.POSTGRESQL -> {
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "PostgreSQL은 tablefunc 확장의 crosstab 함수를 사용합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "CREATE EXTENSION tablefunc; 후 crosstab() 함수를 사용하세요."
                ))
            }
            else -> {}
        }
        return sql
    }

    /**
     * Oracle Hint 처리
     */
    private fun handleHints(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.MYSQL -> {
                // MySQL은 일부 힌트 지원
                val hintPattern = Regex("/\\*\\+\\s*([^*]+)\\s*\\*/")
                val hints = hintPattern.findAll(sql).map { it.groupValues[1].trim() }.toList()

                if (hints.isNotEmpty()) {
                    warnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Oracle 힌트는 MySQL에서 다른 형식으로 변환됩니다.",
                        severity = WarningSeverity.INFO,
                        suggestion = "MySQL 8.0+에서는 /*+ ... */ 형식도 지원됩니다."
                    ))
                    appliedRules.add("Oracle 힌트 발견: ${hints.joinToString(", ")}")
                }
            }
            DialectType.POSTGRESQL -> {
                // PostgreSQL은 힌트 미지원 (pg_hint_plan 확장 필요)
                warnings.add(ConversionWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "PostgreSQL은 기본적으로 쿼리 힌트를 지원하지 않습니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "pg_hint_plan 확장을 설치하거나 SET 명령으로 대체하세요."
                ))
                // 힌트 제거
                result = result.replace(Regex("/\\*\\+[^*]*\\*/"), "")
                appliedRules.add("Oracle 힌트 제거됨 (PostgreSQL 미지원)")
            }
            else -> {}
        }

        return result
    }

    /**
     * Oracle 정규식 함수 변환
     */
    fun convertRegexFunction(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.MYSQL -> {
                // REGEXP_LIKE → REGEXP
                result = result.replace(
                    Regex("""REGEXP_LIKE\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)
                ) { match ->
                    appliedRules.add("REGEXP_LIKE → REGEXP")
                    "${match.groupValues[1]} REGEXP '${match.groupValues[2]}'"
                }
            }
            DialectType.POSTGRESQL -> {
                // REGEXP_LIKE → ~
                result = result.replace(
                    Regex("""REGEXP_LIKE\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)
                ) { match ->
                    appliedRules.add("REGEXP_LIKE → ~ 연산자")
                    "${match.groupValues[1]} ~ '${match.groupValues[2]}'"
                }
            }
            else -> {}
        }

        return result
    }

    /**
     * Oracle JSON 함수 변환
     */
    fun convertJsonFunction(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.MYSQL -> {
                // JSON_VALUE → JSON_UNQUOTE(JSON_EXTRACT(...))
                result = result.replace(
                    Regex("""JSON_VALUE\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""", RegexOption.IGNORE_CASE)
                ) { match ->
                    appliedRules.add("JSON_VALUE → JSON_UNQUOTE(JSON_EXTRACT())")
                    "JSON_UNQUOTE(JSON_EXTRACT(${match.groupValues[1]}, '\$.${match.groupValues[2]}'))"
                }
            }
            DialectType.POSTGRESQL -> {
                // JSON_VALUE → ->> 연산자
                result = result.replace(
                    Regex("""JSON_VALUE\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""", RegexOption.IGNORE_CASE)
                ) { match ->
                    appliedRules.add("JSON_VALUE → ->> 연산자")
                    "${match.groupValues[1]} ->> '${match.groupValues[2]}'"
                }
            }
            else -> {}
        }

        return result
    }
}