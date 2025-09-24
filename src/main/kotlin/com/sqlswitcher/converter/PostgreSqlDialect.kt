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
import net.sf.jsqlparser.expression.operators.relational.ExpressionList
import net.sf.jsqlparser.expression.BinaryExpression
import org.springframework.stereotype.Component

/**
 * PostgreSQL 특화 SQL 문법 및 함수 변환 규칙을 구현하는 방언 클래스
 */
@Component
class PostgreSqlDialect : AbstractDatabaseDialect() {
    
    override fun getDialectType(): DialectType = DialectType.POSTGRESQL
    
    override fun getQuoteCharacter(): String = "\""
    
    override fun getSupportedFunctions(): Set<String> = DialectType.POSTGRESQL.supportedFunctions
    
    override fun getDataTypeMapping(sourceType: String, targetDialect: DialectType): String {
        return when (targetDialect) {
            DialectType.POSTGRESQL -> sourceType // 동일한 방언이므로 그대로 반환
            DialectType.MYSQL -> convertToMySqlDataType(sourceType)
            DialectType.ORACLE -> convertToOracleDataType(sourceType)
            DialectType.TIBERO -> convertToTiberoDataType(sourceType)
        }
    }
    
    override fun canConvert(statement: Statement, targetDialect: DialectType): Boolean {
        // PostgreSQL에서 다른 방언으로의 변환 가능 여부 확인
        return when (targetDialect) {
            DialectType.POSTGRESQL -> true
            DialectType.MYSQL -> true
            DialectType.ORACLE -> true
            DialectType.TIBERO -> true
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
            DialectType.MYSQL -> {
                // PostgreSQL LIMIT/OFFSET → MySQL LIMIT/OFFSET (동일)
                if (selectBody.limit != null) {
                    appliedRules.add("LIMIT 구문 유지")
                }
            }
            DialectType.ORACLE -> {
                // PostgreSQL LIMIT/OFFSET → Oracle ROWNUM 또는 FETCH FIRST
                if (selectBody.limit != null) {
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "PostgreSQL LIMIT 구문을 Oracle ROWNUM 또는 FETCH FIRST로 변환해야 합니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "Oracle 12c 이상에서는 FETCH FIRST n ROWS ONLY를 사용하세요."
                    ))
                    appliedRules.add("LIMIT → ROWNUM/FETCH FIRST 변환 필요")
                }
            }
            DialectType.TIBERO -> {
                // PostgreSQL LIMIT/OFFSET → Tibero ROWNUM 또는 FETCH FIRST
                if (selectBody.limit != null) {
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "PostgreSQL LIMIT 구문을 Tibero ROWNUM 또는 FETCH FIRST로 변환해야 합니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "Tibero에서는 FETCH FIRST n ROWS ONLY를 사용하세요."
                    ))
                    appliedRules.add("LIMIT → ROWNUM/FETCH FIRST 변환 필요")
                }
            }
            else -> {
                // PostgreSQL은 그대로
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
            is CastExpression -> {
                convertCastExpression(expression, targetDialect, warnings, appliedRules)
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
                    "CURRENT_TIMESTAMP" -> {
                        function.name = "NOW"
                        appliedRules.add("CURRENT_TIMESTAMP → NOW()")
                    }
                    "COALESCE" -> {
                        // COALESCE는 MySQL에서도 지원되므로 그대로 유지
                        appliedRules.add("COALESCE() 유지")
                    }
                    "TO_CHAR" -> {
                        warnings.add(createWarning(
                            type = WarningType.SYNTAX_DIFFERENCE,
                            message = "PostgreSQL TO_CHAR() 함수를 MySQL DATE_FORMAT()으로 변환해야 합니다.",
                            severity = WarningSeverity.WARNING,
                            suggestion = "DATE_FORMAT(date, format) 구문을 사용하세요."
                        ))
                        appliedRules.add("TO_CHAR() → DATE_FORMAT() 변환 필요")
                    }
                    "STRING_AGG" -> {
                        function.name = "GROUP_CONCAT"
                        appliedRules.add("STRING_AGG() → GROUP_CONCAT()")
                    }
                    "ARRAY_AGG" -> {
                        warnings.add(createWarning(
                            type = WarningType.UNSUPPORTED_FUNCTION,
                            message = "PostgreSQL ARRAY_AGG() 함수는 MySQL에서 지원되지 않습니다.",
                            severity = WarningSeverity.ERROR,
                            suggestion = "GROUP_CONCAT()을 사용하거나 애플리케이션 레벨에서 배열 처리하세요."
                        ))
                        appliedRules.add("ARRAY_AGG() → GROUP_CONCAT() 변환 필요")
                    }
                }
            }
            DialectType.ORACLE -> {
                when (functionName) {
                    "CURRENT_TIMESTAMP" -> {
                        function.name = "SYSDATE"
                        appliedRules.add("CURRENT_TIMESTAMP → SYSDATE")
                    }
                    "COALESCE" -> {
                        // COALESCE는 Oracle에서도 지원되므로 그대로 유지
                        appliedRules.add("COALESCE() 유지")
                    }
                    "TO_CHAR" -> {
                        // TO_CHAR는 Oracle에서도 지원되므로 그대로 유지
                        appliedRules.add("TO_CHAR() 유지")
                    }
                    "STRING_AGG" -> {
                        function.name = "LISTAGG"
                        appliedRules.add("STRING_AGG() → LISTAGG()")
                    }
                    "ARRAY_AGG" -> {
                        warnings.add(createWarning(
                            type = WarningType.UNSUPPORTED_FUNCTION,
                            message = "PostgreSQL ARRAY_AGG() 함수는 Oracle에서 지원되지 않습니다.",
                            severity = WarningSeverity.ERROR,
                            suggestion = "LISTAGG()을 사용하거나 애플리케이션 레벨에서 배열 처리하세요."
                        ))
                        appliedRules.add("ARRAY_AGG() → LISTAGG() 변환 필요")
                    }
                }
            }
            DialectType.TIBERO -> {
                when (functionName) {
                    "CURRENT_TIMESTAMP" -> {
                        function.name = "SYSDATE"
                        appliedRules.add("CURRENT_TIMESTAMP → SYSDATE")
                    }
                    "COALESCE" -> {
                        // COALESCE는 Tibero에서도 지원되므로 그대로 유지
                        appliedRules.add("COALESCE() 유지")
                    }
                    "TO_CHAR" -> {
                        // TO_CHAR는 Tibero에서도 지원되므로 그대로 유지
                        appliedRules.add("TO_CHAR() 유지")
                    }
                    "STRING_AGG" -> {
                        function.name = "LISTAGG"
                        appliedRules.add("STRING_AGG() → LISTAGG()")
                    }
                    "ARRAY_AGG" -> {
                        warnings.add(createWarning(
                            type = WarningType.UNSUPPORTED_FUNCTION,
                            message = "PostgreSQL ARRAY_AGG() 함수는 Tibero에서 지원되지 않습니다.",
                            severity = WarningSeverity.ERROR,
                            suggestion = "LISTAGG()을 사용하거나 애플리케이션 레벨에서 배열 처리하세요."
                        ))
                        appliedRules.add("ARRAY_AGG() → LISTAGG() 변환 필요")
                    }
                }
            }
            else -> {
                // PostgreSQL은 그대로
            }
        }
    }
    
    /**
     * 타입 캐스팅 변환 (PostgreSQL :: 구문)
     */
    private fun convertCastExpression(
        castExpression: CastExpression,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ) {
        when (targetDialect) {
            DialectType.MYSQL, DialectType.ORACLE, DialectType.TIBERO -> {
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "PostgreSQL :: 타입 캐스팅 구문을 CAST() 함수로 변환해야 합니다.",
                    severity = WarningSeverity.INFO,
                    suggestion = "CAST(expression AS type) 구문을 사용하세요."
                ))
                appliedRules.add(":: 타입 캐스팅 → CAST() 함수 변환")
            }
            else -> {
                // PostgreSQL은 그대로
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
                // PostgreSQL 큰따옴표(") → MySQL 백틱(`) 변환
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "PostgreSQL 큰따옴표(\") 인용 문자를 MySQL 백틱(`)으로 변환해야 합니다.",
                    severity = WarningSeverity.INFO,
                    suggestion = "식별자 인용 문자를 확인하고 수정하세요."
                ))
                appliedRules.add("큰따옴표(\") → 백틱(`) 변환")
            }
            DialectType.ORACLE, DialectType.TIBERO -> {
                // PostgreSQL과 Oracle/Tibero는 모두 큰따옴표 사용
                appliedRules.add("큰따옴표(\") 유지")
            }
            else -> {
                // PostgreSQL은 그대로
            }
        }
    }
    
    // AbstractDatabaseDialect의 추상 메서드 구현
    
    override fun convertToMySqlFunction(functionName: String): String {
        return when (functionName.uppercase()) {
            "CURRENT_TIMESTAMP" -> "NOW"
            "TO_CHAR" -> "DATE_FORMAT"
            "STRING_AGG" -> "GROUP_CONCAT"
            "ARRAY_AGG" -> "GROUP_CONCAT" // 부분 지원
            else -> functionName
        }
    }
    
    override fun convertToPostgreSqlFunction(functionName: String): String = functionName
    
    override fun convertToOracleFunction(functionName: String): String {
        return when (functionName.uppercase()) {
            "CURRENT_TIMESTAMP" -> "SYSDATE"
            "STRING_AGG" -> "LISTAGG"
            "ARRAY_AGG" -> "LISTAGG" // 부분 지원
            else -> functionName
        }
    }
    
    override fun convertToTiberoFunction(functionName: String): String {
        return when (functionName.uppercase()) {
            "CURRENT_TIMESTAMP" -> "SYSDATE"
            "STRING_AGG" -> "LISTAGG"
            "ARRAY_AGG" -> "LISTAGG" // 부분 지원
            else -> functionName
        }
    }
    
    override fun convertToMySqlDataType(dataType: String): String {
        return when (dataType.uppercase()) {
            "SERIAL" -> "AUTO_INCREMENT"
            "BIGSERIAL" -> "BIGINT AUTO_INCREMENT"
            "TEXT" -> "LONGTEXT"
            "BYTEA" -> "LONGBLOB"
            "BOOLEAN" -> "BOOLEAN"
            "JSONB" -> "JSON"
            "UUID" -> "CHAR(36)"
            "ARRAY" -> "TEXT" // 부분 지원
            else -> dataType
        }
    }
    
    override fun convertToPostgreSqlDataType(dataType: String): String = dataType
    
    override fun convertToOracleDataType(dataType: String): String {
        return when (dataType.uppercase()) {
            "SERIAL" -> "NUMBER GENERATED BY DEFAULT AS IDENTITY"
            "BIGSERIAL" -> "NUMBER GENERATED BY DEFAULT AS IDENTITY"
            "TEXT" -> "CLOB"
            "BYTEA" -> "BLOB"
            "BOOLEAN" -> "NUMBER(1)"
            "JSONB" -> "CLOB"
            "UUID" -> "RAW(16)"
            "ARRAY" -> "CLOB" // 부분 지원
            else -> dataType
        }
    }
    
    override fun convertToTiberoDataType(dataType: String): String {
        // Tibero는 Oracle과 유사하므로 동일한 변환 규칙 사용
        return convertToOracleDataType(dataType)
    }
}
