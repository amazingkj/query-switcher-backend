package com.sqlswitcher.converter.dialect

import com.sqlswitcher.converter.core.*
import com.sqlswitcher.converter.feature.*
import org.springframework.stereotype.Component

/**
 * Tibero SQL 방언 - 슬림화된 버전
 *
 * Tibero는 Oracle과 높은 호환성을 가지므로 Oracle 로직을 대부분 재사용
 */
@Component
class TiberoDialectNew(
    functionService: FunctionConversionService,
    dataTypeService: DataTypeConversionService,
    ddlService: DDLConversionService,
    selectService: SelectConversionService,
    triggerService: TriggerConversionService,
    sequenceService: SequenceConversionService
) : BaseDialect(
    functionService, dataTypeService, ddlService, selectService, triggerService, sequenceService
) {
    override val dialectType = DialectType.TIBERO

    /**
     * Tibero 특화 변환 (대부분 Oracle과 동일)
     */
    override fun convertSql(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        var result = super.convertSql(sql, targetDialect, warnings, appliedRules, options)

        // Tibero → Oracle (거의 동일)
        if (targetDialect == DialectType.ORACLE) {
            appliedRules.add("Tibero → Oracle (호환 유지)")
            return result
        }

        // Tibero 특화: CONNECT BY 계층 쿼리 처리 (Oracle과 동일)
        if (result.uppercase().contains("CONNECT BY")) {
            result = handleConnectBy(result, targetDialect, warnings, appliedRules)
        }

        // Tibero 특화: Hint 처리 (Oracle과 동일)
        if (result.contains("/*+")) {
            result = handleHints(result, targetDialect, warnings, appliedRules)
        }

        // Tibero 특화: ROWNUM 처리 (이미 SelectConversionService에서 처리)

        return result
    }

    /**
     * CONNECT BY 계층 쿼리 처리 (Oracle과 동일)
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
                    message = "Tibero CONNECT BY 계층 쿼리는 WITH RECURSIVE로 수동 변환이 필요합니다.",
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
            else -> {}
        }
        return sql
    }

    /**
     * Hint 처리 (Oracle과 동일)
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
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Tibero 힌트는 MySQL에서 다른 형식으로 변환됩니다.",
                    severity = WarningSeverity.INFO
                ))
            }
            DialectType.POSTGRESQL -> {
                warnings.add(ConversionWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "PostgreSQL은 기본적으로 쿼리 힌트를 지원하지 않습니다.",
                    severity = WarningSeverity.WARNING
                ))
                result = result.replace(Regex("/\\*\\+[^*]*\\*/"), "")
                appliedRules.add("Tibero 힌트 제거됨 (PostgreSQL 미지원)")
            }
            else -> {}
        }

        return result
    }

    /**
     * Tibero 특화 함수 (Oracle과 동일한 것들)
     */
    fun convertTiberoSpecificFunctions(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // Tibero는 Oracle 함수와 거의 동일
        // 추가적인 Tibero 전용 함수가 있다면 여기서 처리

        when (targetDialect) {
            DialectType.MYSQL -> {
                // SYSDATE, NVL 등은 FunctionConversionService에서 처리됨
            }
            DialectType.POSTGRESQL -> {
                // SYSDATE, NVL 등은 FunctionConversionService에서 처리됨
            }
            else -> {}
        }

        return result
    }

    /**
     * Tibero에서 다른 방언으로 변환 시 참고 사항 제공
     */
    fun getConversionNotes(targetDialect: DialectType): List<String> {
        return when (targetDialect) {
            DialectType.MYSQL -> listOf(
                "Tibero ROWNUM → MySQL LIMIT으로 변환됩니다",
                "Tibero CONNECT BY는 MySQL에서 지원되지 않습니다 (WITH RECURSIVE 사용)",
                "Tibero 시퀀스는 테이블+함수로 시뮬레이션됩니다",
                "Tibero DECODE는 CASE WHEN으로 변환됩니다"
            )
            DialectType.POSTGRESQL -> listOf(
                "Tibero ROWNUM → PostgreSQL LIMIT/ROW_NUMBER()로 변환됩니다",
                "Tibero CONNECT BY는 PostgreSQL WITH RECURSIVE로 변환이 필요합니다",
                "Tibero NVL → PostgreSQL COALESCE로 변환됩니다",
                "Tibero TO_DATE → PostgreSQL TO_TIMESTAMP로 변환됩니다"
            )
            DialectType.ORACLE -> listOf(
                "Tibero와 Oracle은 높은 호환성을 가집니다",
                "대부분의 SQL이 그대로 사용 가능합니다"
            )
            else -> emptyList()
        }
    }
}