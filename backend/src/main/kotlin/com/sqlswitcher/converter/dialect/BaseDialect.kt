package com.sqlswitcher.converter.dialect

import com.sqlswitcher.converter.DatabaseDialect
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionResult
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.ConversionMetadata
import com.sqlswitcher.converter.supportedFunctions
import com.sqlswitcher.converter.feature.FunctionConversionService
import com.sqlswitcher.converter.feature.DataTypeConversionService
import com.sqlswitcher.converter.feature.DDLConversionService
import com.sqlswitcher.converter.feature.SelectConversionService
import com.sqlswitcher.converter.feature.TriggerConversionService
import com.sqlswitcher.converter.feature.SequenceConversionService
import com.sqlswitcher.parser.model.AstAnalysisResult
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.drop.Drop

/**
 * 모든 Dialect의 기본 클래스 - 공통 로직을 중앙화
 *
 * DatabaseDialect 인터페이스를 구현하여 SqlConverterEngine과 호환됨
 */
abstract class BaseDialect(
    protected val functionService: FunctionConversionService,
    protected val dataTypeService: DataTypeConversionService,
    protected val ddlService: DDLConversionService,
    protected val selectService: SelectConversionService,
    protected val triggerService: TriggerConversionService,
    protected val sequenceService: SequenceConversionService
) : DatabaseDialect {

    // 서브클래서 구현
    abstract override fun getDialectType(): DialectType

    override fun getQuoteCharacter(): String = when (getDialectType()) {
        DialectType.MYSQL -> "`"
        else -> "\""
    }

    override fun getSupportedFunctions(): Set<String> = getDialectType().supportedFunctions

    override fun getDataTypeMapping(sourceType: String, targetDialect: DialectType): String {
        val warnings = mutableListOf<ConversionWarning>()
        val appliedRules = mutableListOf<String>()
        return dataTypeService.convertDataType(sourceType, getDialectType(), targetDialect, warnings, appliedRules)
    }

    override fun canConvert(statement: Statement, targetDialect: DialectType): Boolean = true

    override fun convertQuery(
        statement: Statement,
        targetDialect: DialectType,
        analysisResult: AstAnalysisResult
    ): ConversionResult {
        val startTime = System.currentTimeMillis()
        val warnings = mutableListOf<ConversionWarning>()
        val appliedRules = mutableListOf<String>()

        val convertedSql = convertStatement(statement, targetDialect, warnings, appliedRules)

        return ConversionResult(
            convertedSql = convertedSql,
            warnings = warnings,
            appliedRules = appliedRules,
            executionTime = System.currentTimeMillis() - startTime,
            metadata = ConversionMetadata(
                sourceDialect = getDialectType(),
                targetDialect = targetDialect,
                complexityScore = analysisResult.complexityDetails.totalComplexityScore,
                functionCount = analysisResult.functionExpressionInfo.functions.size,
                tableCount = analysisResult.tableColumnInfo.tables.size,
                joinCount = analysisResult.complexityDetails.joinCount,
                subqueryCount = analysisResult.complexityDetails.subqueryCount
            )
        )
    }

    /**
     * Statement 기반 변환
     */
    protected open fun convertStatement(
        statement: Statement,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return when (statement) {
            is Select -> selectService.convertSelect(
                statement.toString(), getDialectType(), targetDialect, warnings, appliedRules
            )
            is CreateTable -> ddlService.convertCreateTable(
                statement, getDialectType(), targetDialect, warnings, appliedRules
            )
            is Drop -> ddlService.convertDropTable(
                statement.toString(), getDialectType(), targetDialect, appliedRules
            )
            else -> {
                val sql = statement.toString()
                functionService.convertFunctionsInSql(sql, getDialectType(), targetDialect, warnings, appliedRules)
            }
        }
    }

    /**
     * SQL 문자열 직접 변환
     */
    open fun convertSql(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val upperSql = sql.trim().uppercase()

        return when {
            upperSql.startsWith("SELECT") -> {
                selectService.convertSelect(sql, getDialectType(), targetDialect, warnings, appliedRules)
            }
            upperSql.startsWith("CREATE SEQUENCE") -> {
                sequenceService.convertCreateSequence(sql, getDialectType(), targetDialect, warnings, appliedRules)
            }
            upperSql.startsWith("CREATE TRIGGER") || upperSql.startsWith("CREATE OR REPLACE TRIGGER") -> {
                triggerService.convertTrigger(sql, getDialectType(), targetDialect, warnings, appliedRules)
            }
            upperSql.startsWith("DROP SEQUENCE") -> {
                sequenceService.convertDropSequence(sql, getDialectType(), targetDialect, warnings, appliedRules)
            }
            upperSql.startsWith("CREATE INDEX") || upperSql.startsWith("CREATE UNIQUE INDEX") -> {
                ddlService.convertCreateIndex(sql, getDialectType(), targetDialect, warnings, appliedRules)
            }
            upperSql.startsWith("DROP TABLE") -> {
                ddlService.convertDropTable(sql, getDialectType(), targetDialect, appliedRules)
            }
            else -> {
                functionService.convertFunctionsInSql(sql, getDialectType(), targetDialect, warnings, appliedRules)
            }
        }
    }
}