package com.sqlswitcher.converter.dialect

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.feature.FunctionConversionService
import com.sqlswitcher.converter.feature.DataTypeConversionService
import com.sqlswitcher.converter.feature.DDLConversionService
import com.sqlswitcher.converter.feature.SelectConversionService
import com.sqlswitcher.converter.feature.TriggerConversionService
import com.sqlswitcher.converter.feature.SequenceConversionService
import org.springframework.stereotype.Component

/**
 * Tibero SQL 방언 - 슬림화된 버전 (약 100줄)
 *
 * Tibero는 Oracle과 높은 호환성을 가지므로 Oracle 로직을 대부분 재사용
 */
@Component("tiberoDialectNew")
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
    override fun getDialectType() = DialectType.TIBERO

    override fun convertSql(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = super.convertSql(sql, targetDialect, warnings, appliedRules)

        // Tibero → Oracle (거의 동일)
        if (targetDialect == DialectType.ORACLE) {
            appliedRules.add("Tibero → Oracle (호환 유지)")
            return result
        }

        // Tibero 특화: CONNECT BY 처리 (Oracle과 동일)
        if (result.uppercase().contains("CONNECT BY")) {
            result = handleConnectBy(result, targetDialect, warnings, appliedRules)
        }

        // Tibero 특화: Hint 처리 (Oracle과 동일)
        if (result.contains("/*+")) {
            result = handleHints(result, targetDialect, warnings, appliedRules)
        }

        return result
    }

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
                    message = "Tibero CONNECT BY는 WITH RECURSIVE로 수동 변환이 필요합니다.",
                    severity = WarningSeverity.WARNING
                ))
                appliedRules.add("CONNECT BY 감지됨 - WITH RECURSIVE로 수동 변환 필요")
            }
            else -> {}
        }
        return sql
    }

    private fun handleHints(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.POSTGRESQL -> {
                warnings.add(ConversionWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "PostgreSQL은 기본적으로 쿼리 힌트를 지원하지 않습니다.",
                    severity = WarningSeverity.WARNING
                ))
                result = result.replace(Regex("/\\*\\+[^*]*\\*/"), "")
                appliedRules.add("Tibero 힌트 제거됨")
            }
            else -> {}
        }

        return result
    }
}