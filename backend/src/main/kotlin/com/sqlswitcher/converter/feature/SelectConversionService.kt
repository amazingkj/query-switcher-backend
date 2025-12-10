package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import org.springframework.stereotype.Service

/**
 * SELECT 문 변환 서비스
 */
@Service
class SelectConversionService(
    private val functionService: FunctionConversionService
) {
    // 정규식 상수 (컴파일 1회)
    companion object {
        private val ROWNUM_WHERE_PATTERN = Regex("""WHERE\s+ROWNUM\s*([<>=]+)\s*(\d+)""", RegexOption.IGNORE_CASE)
        private val ROWNUM_PATTERN = Regex("\\bROWNUM\\b", RegexOption.IGNORE_CASE)
        private val FROM_DUAL_PATTERN = Regex("\\bFROM\\s+DUAL\\b", RegexOption.IGNORE_CASE)
        private val FROM_DUAL_REMOVE_PATTERN = Regex("\\s+FROM\\s+DUAL\\b", RegexOption.IGNORE_CASE)
    }

    /**
     * SELECT 문 변환
     */
    fun convertSelect(
        selectSql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) {
            return selectSql
        }

        var result = selectSql

        // 함수 변환
        result = functionService.convertFunctionsInSql(result, sourceDialect, targetDialect, warnings, appliedRules)

        // ROWNUM 변환 (Oracle → MySQL/PostgreSQL)
        if (sourceDialect == DialectType.ORACLE) {
            result = convertRownum(result, targetDialect, warnings, appliedRules)
        }

        // DUAL 테이블 처리
        result = convertDualTable(result, sourceDialect, targetDialect, appliedRules)

        // 인용문자 변환
        result = convertQuoteCharacters(result, sourceDialect, targetDialect)

        return result
    }

    /**
     * ROWNUM 변환
     */
    private fun convertRownum(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        val match = ROWNUM_WHERE_PATTERN.find(result)
        if (match != null) {
            val operator = match.groupValues[1]
            val value = match.groupValues[2].toInt()

            val limit = when (operator) {
                "<=" -> value
                "<" -> value - 1
                "=" -> if (value == 1) 1 else value
                else -> value
            }

            when (targetDialect) {
                DialectType.MYSQL, DialectType.POSTGRESQL -> {
                    result = result.replace(match.value, "")
                    result = result.trimEnd() + " LIMIT $limit"
                    appliedRules.add("ROWNUM $operator $value → LIMIT $limit")
                }
                else -> {}
            }
        }

        // SELECT 컬럼에서 ROWNUM 사용
        if (ROWNUM_PATTERN.containsMatchIn(result)) {
            when (targetDialect) {
                DialectType.MYSQL -> {
                    warnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "ROWNUM은 MySQL에서 ROW_NUMBER() OVER()로 대체됩니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "ROW_NUMBER() OVER(ORDER BY ...) 사용"
                    ))
                }
                DialectType.POSTGRESQL -> {
                    warnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "ROWNUM은 PostgreSQL에서 ROW_NUMBER() OVER()로 대체됩니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "ROW_NUMBER() OVER(ORDER BY ...) 사용"
                    ))
                }
                else -> {}
            }
        }

        return result
    }

    /**
     * DUAL 테이블 처리
     */
    private fun convertDualTable(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (sourceDialect == DialectType.ORACLE) {
            when (targetDialect) {
                DialectType.POSTGRESQL -> {
                    if (FROM_DUAL_PATTERN.containsMatchIn(result)) {
                        result = FROM_DUAL_REMOVE_PATTERN.replace(result, "")
                        appliedRules.add("FROM DUAL 제거 (PostgreSQL)")
                    }
                }
                DialectType.MYSQL -> {
                    // MySQL도 DUAL 지원하지만 생략 가능
                }
                else -> {}
            }
        }

        return result
    }

    /**
     * 인용문자 변환
     */
    private fun convertQuoteCharacters(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType
    ): String {
        var result = sql

        val sourceQuote = getQuoteChar(sourceDialect)
        val targetQuote = getQuoteChar(targetDialect)

        if (sourceQuote != targetQuote) {
            result = result.replace(sourceQuote, targetQuote)
        }

        return result
    }

    private fun getQuoteChar(dialect: DialectType): String {
        return when (dialect) {
            DialectType.MYSQL -> "`"
            else -> "\""
        }
    }
}