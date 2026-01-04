package com.sqlswitcher.converter.dialect

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.feature.FunctionConversionService
import com.sqlswitcher.converter.feature.DataTypeConversionService
import com.sqlswitcher.converter.feature.DDLConversionService
import com.sqlswitcher.converter.feature.SelectConversionService
import com.sqlswitcher.converter.feature.procedure.TriggerConversionService
import com.sqlswitcher.converter.feature.SequenceConversionService
import com.sqlswitcher.converter.feature.PartitionConversionService
import com.sqlswitcher.converter.feature.ViewConversionService
import com.sqlswitcher.converter.feature.MergeConversionService
import com.sqlswitcher.converter.feature.ProcedureConversionService
import org.springframework.stereotype.Component

/**
 * MySQL SQL 방언 - 슬림화된 버전
 *
 * 파티션 변환은 BaseDialect의 PartitionConversionService에서 처리
 * MySQL 특화 기능(LIMIT, ENGINE, ON DUPLICATE KEY)만 구현
 */
@Component("mySqlDialectNew")
class MySqlDialectNew(
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
    override fun getDialectType() = DialectType.MYSQL
    override fun getQuoteCharacter() = "`"

    override fun convertSql(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = super.convertSql(sql, targetDialect, warnings, appliedRules)

        // MySQL 특화: LIMIT 처리
        result = handleLimit(result, targetDialect, appliedRules)

        // MySQL 특화: ENGINE= 제거
        result = handleEngineClause(result, targetDialect, appliedRules)

        // MySQL 특화: ON DUPLICATE KEY UPDATE
        result = handleOnDuplicateKey(result, targetDialect, warnings, appliedRules)

        return result
    }

    private fun handleLimit(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.ORACLE -> {
                // LIMIT n → FETCH FIRST n ROWS ONLY
                val limitPattern = Regex("""LIMIT\s+(\d+)(?!\s*,)""", RegexOption.IGNORE_CASE)
                val match = limitPattern.find(result)
                if (match != null) {
                    val count = match.groupValues[1]
                    result = result.replace(match.value, "FETCH FIRST $count ROWS ONLY")
                    appliedRules.add("LIMIT → FETCH FIRST 변환")
                }

                // LIMIT offset, count → OFFSET .. FETCH
                val limitOffsetPattern = Regex("""LIMIT\s+(\d+)\s*,\s*(\d+)""", RegexOption.IGNORE_CASE)
                val matchOffset = limitOffsetPattern.find(result)
                if (matchOffset != null) {
                    val offset = matchOffset.groupValues[1]
                    val count = matchOffset.groupValues[2]
                    result = result.replace(matchOffset.value, "OFFSET $offset ROWS FETCH NEXT $count ROWS ONLY")
                    appliedRules.add("LIMIT offset,count → OFFSET/FETCH 변환")
                }
            }
            DialectType.POSTGRESQL -> {
                // LIMIT offset, count → LIMIT count OFFSET offset
                val limitPattern = Regex("""LIMIT\s+(\d+)\s*,\s*(\d+)""", RegexOption.IGNORE_CASE)
                val match = limitPattern.find(result)
                if (match != null) {
                    val offset = match.groupValues[1]
                    val count = match.groupValues[2]
                    result = result.replace(match.value, "LIMIT $count OFFSET $offset")
                    appliedRules.add("LIMIT offset,count → LIMIT/OFFSET 변환")
                }
            }
            else -> {}
        }

        return result
    }

    private fun handleEngineClause(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (targetDialect != DialectType.MYSQL) {
            val enginePattern = Regex("""ENGINE\s*=\s*\w+""", RegexOption.IGNORE_CASE)
            if (enginePattern.containsMatchIn(result)) {
                result = result.replace(enginePattern, "")
                appliedRules.add("ENGINE= 절 제거")
            }

            val charsetPattern = Regex("""DEFAULT\s+CHARSET\s*=\s*\w+""", RegexOption.IGNORE_CASE)
            if (charsetPattern.containsMatchIn(result)) {
                result = result.replace(charsetPattern, "")
                appliedRules.add("DEFAULT CHARSET= 절 제거")
            }
        }

        return result.trim()
    }

    private fun handleOnDuplicateKey(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!sql.uppercase().contains("ON DUPLICATE KEY UPDATE")) {
            return sql
        }

        when (targetDialect) {
            DialectType.ORACLE -> {
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "ON DUPLICATE KEY UPDATE는 MERGE INTO로 변환해야 합니다.",
                    severity = WarningSeverity.WARNING
                ))
                appliedRules.add("ON DUPLICATE KEY UPDATE 감지됨 - MERGE INTO로 수동 변환 필요")
            }
            DialectType.POSTGRESQL -> {
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "ON DUPLICATE KEY UPDATE는 ON CONFLICT로 변환됩니다.",
                    severity = WarningSeverity.INFO
                ))
            }
            else -> {}
        }

        return sql
    }
}