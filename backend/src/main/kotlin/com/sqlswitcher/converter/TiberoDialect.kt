package com.sqlswitcher.converter

import com.sqlswitcher.parser.model.AstAnalysisResult
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.statement.select.SelectItem
import net.sf.jsqlparser.statement.select.Limit
import net.sf.jsqlparser.statement.select.Offset
import net.sf.jsqlparser.statement.select.Top
import net.sf.jsqlparser.statement.select.Fetch
import net.sf.jsqlparser.expression.Function as SqlFunction
import net.sf.jsqlparser.expression.StringValue
import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.expression.CastExpression
import net.sf.jsqlparser.expression.LongValue
import net.sf.jsqlparser.expression.operators.relational.ExpressionList
import org.springframework.stereotype.Component

/**
 * Tibero 특화 SQL 문법 및 함수 변환 규칙을 구현하는 방언 클래스
 * Tibero는 Oracle과 높은 호환성을 가지므로 Oracle 방언을 기반으로 구현
 */
@Component
class TiberoDialect : AbstractDatabaseDialect() {
    
    override fun getDialectType(): DialectType = DialectType.TIBERO
    
    override fun getQuoteCharacter(): String = "\""
    
    override fun getSupportedFunctions(): Set<String> = DialectType.TIBERO.supportedFunctions
    
    override fun getDataTypeMapping(sourceType: String, targetDialect: DialectType): String {
        return when (targetDialect) {
            DialectType.TIBERO -> sourceType // 동일한 방언이므로 그대로 반환
            DialectType.MYSQL -> convertToMySqlDataType(sourceType)
            DialectType.POSTGRESQL -> convertToPostgreSqlDataType(sourceType)
            DialectType.ORACLE -> convertToOracleDataType(sourceType)
        }
    }
    
    override fun canConvert(statement: Statement, targetDialect: DialectType): Boolean {
        // Tibero에서 다른 방언으로의 변환 가능 여부 확인
        return when (targetDialect) {
            DialectType.TIBERO -> true
            DialectType.MYSQL -> true
            DialectType.POSTGRESQL -> true
            DialectType.ORACLE -> true
        }
    }
    
    override fun performConversion(
        statement: Statement,
        targetDialect: DialectType,
        analysisResult: AstAnalysisResult
    ): ConversionResult {
        val warnings = mutableListOf<ConversionWarning>()
        val appliedRules = mutableListOf<String>()
        
        when (statement) {
            is Select -> {
                val convertedSql = convertSelectStatement(statement, targetDialect, warnings, appliedRules)
                return ConversionResult(
                    convertedSql = convertedSql,
                    warnings = warnings,
                    appliedRules = appliedRules
                )
            }
            else -> {
                // 다른 Statement 타입들은 기본 변환
                return ConversionResult(
                    convertedSql = statement.toString(),
                    warnings = warnings
                )
            }
        }
    }
    
    /**
     * SELECT 문 변환
     */
    private fun convertSelectStatement(
        select: Select,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val selectBody = select.selectBody
        if (selectBody is PlainSelect) {
            // LIMIT/OFFSET 변환
            convertLimitOffset(selectBody, targetDialect, warnings, appliedRules)
            
            // 함수 변환
            convertFunctions(selectBody, targetDialect, warnings, appliedRules)
            
            // 인용 문자 변환
            convertQuoteCharacters(selectBody, targetDialect, warnings, appliedRules)
            
            // ROWNUM 변환
            convertRownum(selectBody, targetDialect, warnings, appliedRules)
            
            // Tibero 특화 기능 변환
            convertTiberoSpecificFeatures(selectBody, targetDialect, warnings, appliedRules)
        }
        
        return select.toString()
    }
    
    /**
     * LIMIT/OFFSET 구문 변환
     */
    private fun convertLimitOffset(
        selectBody: PlainSelect,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ) {
        when (targetDialect) {
            DialectType.MYSQL, DialectType.POSTGRESQL -> {
                // Tibero FETCH FIRST → MySQL/PostgreSQL LIMIT 변환
                if (selectBody.fetch != null) {
                    val fetchValue = selectBody.fetch.rowCount

                    // LIMIT 구문 생성 - Expression을 LongValue로 변환
                    val limit = Limit()
                    limit.rowCount = LongValue(fetchValue)

                    // FETCH 제거하고 LIMIT로 교체
                    selectBody.fetch = null
                    selectBody.limit = limit

                    appliedRules.add("FETCH FIRST → LIMIT 변환 완료")

                    if (selectBody.offset != null) {
                        appliedRules.add("OFFSET 구문 유지")
                    }
                }

                // Tibero ROWNUM → LIMIT 변환은 복잡하여 경고만 제공
                if (selectBody.fetch == null && selectBody.limit == null) {
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Tibero ROWNUM 구문을 LIMIT으로 변환해야 합니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "LIMIT n OFFSET m 구문을 사용하세요."
                    ))
                    appliedRules.add("ROWNUM → LIMIT 수동 변환 필요")
                }
            }
            DialectType.ORACLE -> {
                // Tibero와 Oracle은 동일한 구문 사용
                appliedRules.add("Tibero 구문 유지")
            }
            else -> {
                // Tibero는 그대로
            }
        }
    }
    
    /**
     * 함수 변환
     */
    private fun convertFunctions(
        selectBody: PlainSelect,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ) {
        // SELECT 절의 함수들 변환
        selectBody.selectItems?.forEach { selectItem ->
            selectItem.expression?.let { expression ->
                convertExpression(expression, targetDialect, warnings, appliedRules)
            }
        }
        
        // WHERE 절의 함수들 변환
        selectBody.where?.let { expression ->
            convertExpression(expression, targetDialect, warnings, appliedRules)
        }
    }
    
    /**
     * 표현식 내 함수 변환
     */
    private fun convertExpression(
        expression: Expression?,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ) {
        when (expression) {
            is SqlFunction -> {
                convertFunction(expression, targetDialect, warnings, appliedRules)
            }
            is net.sf.jsqlparser.expression.BinaryExpression -> {
                convertExpression(expression.leftExpression, targetDialect, warnings, appliedRules)
                convertExpression(expression.rightExpression, targetDialect, warnings, appliedRules)
            }
            // 다른 표현식 타입들도 재귀적으로 처리
        }
    }
    
    /**
     * 개별 함수 변환
     */
    private fun convertFunction(
        function: SqlFunction,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ) {
        val functionName = function.name?.uppercase() ?: ""
        
        when (targetDialect) {
            DialectType.MYSQL -> {
                when (functionName) {
                    "SYSDATE" -> {
                        function.name = "NOW"
                        appliedRules.add("SYSDATE → NOW()")
                    }
                    "NVL" -> {
                        function.name = "IFNULL"
                        appliedRules.add("NVL() → IFNULL()")
                    }
                    "NVL2" -> {
                        warnings.add(createWarning(
                            type = WarningType.UNSUPPORTED_FUNCTION,
                            message = "Tibero NVL2() 함수는 MySQL에서 지원되지 않습니다.",
                            severity = WarningSeverity.ERROR,
                            suggestion = "CASE WHEN 구문을 사용하세요."
                        ))
                        appliedRules.add("NVL2() → CASE WHEN 변환 필요")
                    }
                    "TO_CHAR" -> {
                        function.name = "DATE_FORMAT"
                        appliedRules.add("TO_CHAR() → DATE_FORMAT()")
                    }
                    "TO_DATE" -> {
                        function.name = "STR_TO_DATE"
                        appliedRules.add("TO_DATE() → STR_TO_DATE()")
                    }
                    "LISTAGG" -> {
                        function.name = "GROUP_CONCAT"
                        appliedRules.add("LISTAGG() → GROUP_CONCAT()")
                    }
                    "DECODE" -> {
                        warnings.add(createWarning(
                            type = WarningType.UNSUPPORTED_FUNCTION,
                            message = "Tibero DECODE() 함수는 MySQL에서 지원되지 않습니다.",
                            severity = WarningSeverity.ERROR,
                            suggestion = "CASE WHEN 구문을 사용하세요."
                        ))
                        appliedRules.add("DECODE() → CASE WHEN 변환 필요")
                    }
                }
            }
            DialectType.POSTGRESQL -> {
                when (functionName) {
                    "SYSDATE" -> {
                        function.name = "CURRENT_TIMESTAMP"
                        appliedRules.add("SYSDATE → CURRENT_TIMESTAMP")
                    }
                    "NVL" -> {
                        function.name = "COALESCE"
                        appliedRules.add("NVL() → COALESCE()")
                    }
                    "NVL2" -> {
                        warnings.add(createWarning(
                            type = WarningType.UNSUPPORTED_FUNCTION,
                            message = "Tibero NVL2() 함수는 PostgreSQL에서 지원되지 않습니다.",
                            severity = WarningSeverity.ERROR,
                            suggestion = "CASE WHEN 구문을 사용하세요."
                        ))
                        appliedRules.add("NVL2() → CASE WHEN 변환 필요")
                    }
                    "TO_CHAR" -> {
                        // TO_CHAR는 PostgreSQL에서도 지원되므로 그대로 유지
                        appliedRules.add("TO_CHAR() 유지")
                    }
                    "TO_DATE" -> {
                        function.name = "TO_TIMESTAMP"
                        appliedRules.add("TO_DATE() → TO_TIMESTAMP()")
                    }
                    "LISTAGG" -> {
                        function.name = "STRING_AGG"
                        appliedRules.add("LISTAGG() → STRING_AGG()")
                    }
                    "DECODE" -> {
                        warnings.add(createWarning(
                            type = WarningType.UNSUPPORTED_FUNCTION,
                            message = "Tibero DECODE() 함수는 PostgreSQL에서 지원되지 않습니다.",
                            severity = WarningSeverity.ERROR,
                            suggestion = "CASE WHEN 구문을 사용하세요."
                        ))
                        appliedRules.add("DECODE() → CASE WHEN 변환 필요")
                    }
                }
            }
            DialectType.ORACLE -> {
                // Tibero와 Oracle은 대부분 동일한 함수를 사용
                appliedRules.add("Tibero 함수 유지")
            }
            else -> {
                // Tibero는 그대로
            }
        }
    }
    
    /**
     * ROWNUM 변환
     */
    private fun convertRownum(
        selectBody: PlainSelect,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ) {
        when (targetDialect) {
            DialectType.MYSQL, DialectType.POSTGRESQL -> {
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Tibero ROWNUM 구문을 LIMIT으로 변환해야 합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "LIMIT n OFFSET m 구문을 사용하세요."
                ))
                appliedRules.add("ROWNUM → LIMIT 변환 필요")
            }
            DialectType.ORACLE -> {
                // Tibero와 Oracle은 동일한 ROWNUM 구문 사용
                appliedRules.add("ROWNUM 구문 유지")
            }
            else -> {
                // Tibero는 그대로
            }
        }
    }
    
    /**
     * Tibero 특화 기능 변환
     */
    private fun convertTiberoSpecificFeatures(
        selectBody: PlainSelect,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ) {
        when (targetDialect) {
            DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE -> {
                warnings.add(createWarning(
                    type = WarningType.PARTIAL_SUPPORT,
                    message = "Tibero 특화 기능이 대상 데이터베이스에서 지원되지 않을 수 있습니다.",
                    severity = WarningSeverity.INFO,
                    suggestion = "변환된 결과를 검토하고 필요시 수정해주세요."
                ))
                appliedRules.add("Tibero 특화 기능 검토 필요")
            }
            else -> {
                // Tibero는 그대로
            }
        }
    }
    
    /**
     * 인용 문자 변환
     */
    private fun convertQuoteCharacters(
        selectBody: PlainSelect,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ) {
        when (targetDialect) {
            DialectType.MYSQL -> {
                // Tibero 큰따옴표(") → MySQL 백틱(`) 변환
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Tibero 큰따옴표(\") 인용 문자를 MySQL 백틱(`)으로 변환해야 합니다.",
                    severity = WarningSeverity.INFO,
                    suggestion = "식별자 인용 문자를 확인하고 수정하세요."
                ))
                appliedRules.add("큰따옴표(\") → 백틱(`) 변환")
            }
            DialectType.POSTGRESQL, DialectType.ORACLE -> {
                // Tibero와 PostgreSQL/Oracle은 모두 큰따옴표 사용
                appliedRules.add("큰따옴표(\") 유지")
            }
            else -> {
                // Tibero는 그대로
            }
        }
    }
    
    // AbstractDatabaseDialect의 추상 메서드 구현
    
    override fun convertToMySqlFunction(functionName: String): String {
        return when (functionName.uppercase()) {
            "SYSDATE" -> "NOW"
            "NVL" -> "IFNULL"
            "TO_CHAR" -> "DATE_FORMAT"
            "TO_DATE" -> "STR_TO_DATE"
            "LISTAGG" -> "GROUP_CONCAT"
            else -> functionName
        }
    }
    
    override fun convertToPostgreSqlFunction(functionName: String): String {
        return when (functionName.uppercase()) {
            "SYSDATE" -> "CURRENT_TIMESTAMP"
            "NVL" -> "COALESCE"
            "TO_DATE" -> "TO_TIMESTAMP"
            "LISTAGG" -> "STRING_AGG"
            else -> functionName
        }
    }
    
    override fun convertToOracleFunction(functionName: String): String = functionName
    
    override fun convertToTiberoFunction(functionName: String): String = functionName
    
    override fun convertToMySqlDataType(dataType: String): String {
        return when (dataType.uppercase()) {
            "NUMBER" -> "DECIMAL"
            "VARCHAR2" -> "VARCHAR"
            "CLOB" -> "LONGTEXT"
            "BLOB" -> "LONGBLOB"
            "RAW" -> "VARBINARY"
            "LONG RAW" -> "LONGBLOB"
            "TIMESTAMP" -> "DATETIME"
            "TIMESTAMP WITH TIME ZONE" -> "DATETIME"
            "TIMESTAMP WITH LOCAL TIME ZONE" -> "DATETIME"
            "INTERVAL YEAR TO MONTH" -> "VARCHAR(20)"
            "INTERVAL DAY TO SECOND" -> "VARCHAR(30)"
            "ROWID" -> "VARCHAR(18)"
            "UROWID" -> "VARCHAR(4000)"
            "XMLType" -> "LONGTEXT"
            else -> dataType
        }
    }
    
    override fun convertToPostgreSqlDataType(dataType: String): String {
        return when (dataType.uppercase()) {
            "NUMBER" -> "NUMERIC"
            "VARCHAR2" -> "VARCHAR"
            "CLOB" -> "TEXT"
            "BLOB" -> "BYTEA"
            "RAW" -> "BYTEA"
            "LONG RAW" -> "BYTEA"
            "TIMESTAMP" -> "TIMESTAMP"
            "TIMESTAMP WITH TIME ZONE" -> "TIMESTAMPTZ"
            "TIMESTAMP WITH LOCAL TIME ZONE" -> "TIMESTAMP"
            "INTERVAL YEAR TO MONTH" -> "INTERVAL"
            "INTERVAL DAY TO SECOND" -> "INTERVAL"
            "ROWID" -> "VARCHAR(18)"
            "UROWID" -> "VARCHAR(4000)"
            "XMLType" -> "XML"
            else -> dataType
        }
    }
    
    override fun convertToOracleDataType(dataType: String): String = dataType
    
    override fun convertToTiberoDataType(dataType: String): String = dataType
}
