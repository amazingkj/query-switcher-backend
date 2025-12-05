package com.sqlswitcher.converter.dialect

import com.sqlswitcher.converter.core.*
import com.sqlswitcher.converter.feature.*
import com.sqlswitcher.converter.mapping.*
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.drop.Drop

/**
 * 모든 Dialect의 기본 클래스 - 공통 로직을 중앙화
 */
abstract class BaseDialect(
    protected val functionService: FunctionConversionService,
    protected val dataTypeService: DataTypeConversionService,
    protected val ddlService: DDLConversionService,
    protected val selectService: SelectConversionService,
    protected val triggerService: TriggerConversionService,
    protected val sequenceService: SequenceConversionService
) {
    /**
     * 이 Dialect의 타입
     */
    abstract val dialectType: DialectType

    /**
     * 인용 문자
     */
    open val quoteCharacter: String
        get() = dialectType.getQuoteCharacter()

    /**
     * SQL 문 변환 (메인 진입점)
     */
    open fun convert(
        sql: String,
        targetDialect: DialectType,
        options: ConversionOptions? = null
    ): ConversionResult {
        val warnings = mutableListOf<ConversionWarning>()
        val appliedRules = mutableListOf<String>()

        if (dialectType == targetDialect) {
            return ConversionResult(
                convertedSql = sql,
                warnings = warnings,
                appliedRules = listOf("동일한 방언 - 변환 불필요"),
                sourceDialect = dialectType,
                targetDialect = targetDialect
            )
        }

        val convertedSql = convertSql(sql, targetDialect, warnings, appliedRules, options)

        return ConversionResult(
            convertedSql = convertedSql,
            warnings = warnings,
            appliedRules = appliedRules,
            sourceDialect = dialectType,
            targetDialect = targetDialect
        )
    }

    /**
     * 실제 SQL 변환 로직
     */
    protected open fun convertSql(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val upperSql = sql.trim().uppercase()

        return when {
            upperSql.startsWith("SELECT") -> {
                selectService.convertSelect(sql, dialectType, targetDialect, warnings, appliedRules)
            }
            upperSql.startsWith("CREATE SEQUENCE") -> {
                sequenceService.convertCreateSequence(sql, dialectType, targetDialect, warnings, appliedRules, options?.schemaOwner)
            }
            upperSql.startsWith("CREATE TRIGGER") || upperSql.startsWith("CREATE OR REPLACE TRIGGER") -> {
                triggerService.convertTrigger(sql, dialectType, targetDialect, warnings, appliedRules)
            }
            upperSql.startsWith("DROP SEQUENCE") -> {
                sequenceService.convertDropSequence(sql, dialectType, targetDialect, warnings, appliedRules)
            }
            upperSql.startsWith("CREATE INDEX") || upperSql.startsWith("CREATE UNIQUE INDEX") -> {
                ddlService.convertCreateIndex(sql, dialectType, targetDialect, warnings, appliedRules)
            }
            upperSql.startsWith("DROP TABLE") -> {
                ddlService.convertDropTable(sql, dialectType, targetDialect, appliedRules)
            }
            else -> {
                // 기타 SQL - 기본 함수 변환만 적용
                functionService.convertFunctionsInSql(sql, dialectType, targetDialect, warnings, appliedRules)
            }
        }
    }

    /**
     * Statement 기반 변환 (JSQLParser 사용)
     */
    open fun convertStatement(
        statement: Statement,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions? = null
    ): String {
        return when (statement) {
            is Select -> selectService.convertSelect(statement.toString(), dialectType, targetDialect, warnings, appliedRules)
            is CreateTable -> ddlService.convertCreateTable(statement, dialectType, targetDialect, warnings, appliedRules)
            is Drop -> ddlService.convertDropTable(statement.toString(), dialectType, targetDialect, appliedRules)
            else -> {
                val sql = statement.toString()
                functionService.convertFunctionsInSql(sql, dialectType, targetDialect, warnings, appliedRules)
            }
        }
    }

    /**
     * 시퀀스 사용 구문 변환
     */
    fun convertSequenceUsage(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return sequenceService.convertSequenceUsage(sql, dialectType, targetDialect, warnings, appliedRules)
    }

    /**
     * 데이터 타입 변환
     */
    fun convertDataType(
        dataType: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return dataTypeService.convertDataType(dataType, dialectType, targetDialect, warnings, appliedRules)
    }
}