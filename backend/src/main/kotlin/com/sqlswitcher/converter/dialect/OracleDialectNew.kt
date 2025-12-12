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
import com.sqlswitcher.converter.feature.PartitionConversionService
import com.sqlswitcher.converter.feature.ViewConversionService
import com.sqlswitcher.converter.feature.MergeConversionService
import com.sqlswitcher.converter.feature.ProcedureConversionService
import org.springframework.stereotype.Component

/**
 * Oracle SQL 방언 - 슬림화된 버전
 *
 * 파티션 변환은 BaseDialect의 PartitionConversionService에서 처리
 * Oracle 특화 기능(CONNECT BY, Hints)만 구현
 */
@Component("oracleDialectNew")
class OracleDialectNew(
    functionService: FunctionConversionService,
    dataTypeService: DataTypeConversionService,
    ddlService: DDLConversionService,
    selectService: SelectConversionService,
    triggerService: TriggerConversionService,
    sequenceService: SequenceConversionService,
    partitionService: PartitionConversionService,
    viewService: ViewConversionService,
    mergeService: MergeConversionService,
    procedureService: ProcedureConversionService
) : BaseDialect(
    functionService, dataTypeService, ddlService, selectService, triggerService, sequenceService, partitionService, viewService, mergeService, procedureService
) {
    override fun getDialectType() = DialectType.ORACLE

    override fun convertSql(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = super.convertSql(sql, targetDialect, warnings, appliedRules)

        // Oracle 특화: CONNECT BY 계층 쿼리 처리
        if (result.uppercase().contains("CONNECT BY")) {
            result = handleConnectBy(result, targetDialect, warnings, appliedRules)
        }

        // Oracle 특화: Hint 처리
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
                    message = "Oracle CONNECT BY 계층 쿼리는 WITH RECURSIVE로 수동 변환이 필요합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "WITH RECURSIVE cte AS (...) SELECT * FROM cte"
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
                appliedRules.add("Oracle 힌트 제거됨")
            }
            DialectType.MYSQL -> {
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Oracle 힌트는 MySQL에서 다른 형식으로 변환됩니다.",
                    severity = WarningSeverity.INFO
                ))
            }
            else -> {}
        }

        return result
    }
}