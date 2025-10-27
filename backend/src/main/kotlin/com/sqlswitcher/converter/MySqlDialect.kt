package com.sqlswitcher.converter

import com.sqlswitcher.parser.model.AstAnalysisResult
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.statement.select.Limit
import net.sf.jsqlparser.statement.select.Offset
import net.sf.jsqlparser.statement.select.Top
import net.sf.jsqlparser.statement.select.Fetch
import net.sf.jsqlparser.statement.select.SelectItem
import net.sf.jsqlparser.expression.Function as SqlFunction
import net.sf.jsqlparser.expression.StringValue
import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.expression.CastExpression
import net.sf.jsqlparser.expression.operators.relational.ExpressionList
import net.sf.jsqlparser.expression.BinaryExpression
import org.springframework.stereotype.Component

/**
 * MySQL 특화 SQL 문법 및 함수 변환 규칙을 구현하는 방언 클래스
 */
@Component
class MySqlDialect : AbstractDatabaseDialect() {
    
    override fun getDialectType(): DialectType = DialectType.MYSQL
    
    override fun getQuoteCharacter(): String = "`"
    
    override fun getSupportedFunctions(): Set<String> = DialectType.MYSQL.supportedFunctions
    
    override fun getDataTypeMapping(sourceType: String, targetDialect: DialectType): String {
        return when (targetDialect) {
            DialectType.MYSQL -> sourceType // 동일한 방언이므로 그대로 반환
            DialectType.POSTGRESQL -> convertToPostgreSqlDataType(sourceType)
            DialectType.ORACLE -> convertToOracleDataType(sourceType)
            DialectType.TIBERO -> convertToTiberoDataType(sourceType)
        }
    }
    
    override fun canConvert(statement: Statement, targetDialect: DialectType): Boolean {
        // MySQL에서 다른 방언으로의 변환 가능 여부 확인
        return when (targetDialect) {
            DialectType.MYSQL -> true
            DialectType.POSTGRESQL -> true
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
            DialectType.POSTGRESQL -> {
                // MySQL LIMIT/OFFSET → PostgreSQL LIMIT/OFFSET (동일)
                if (selectBody.limit != null) {
                    appliedRules.add("LIMIT 구문 유지")
                }
            }
            DialectType.ORACLE -> {
                // MySQL LIMIT/OFFSET → Oracle FETCH FIRST
                if (selectBody.limit != null) {
                    val limitValue = selectBody.limit.rowCount
                    val offsetValue = selectBody.offset?.offset

                    // FETCH 구문 생성 (Oracle 12c+)
                    val fetch = Fetch()
                    fetch.rowCount = limitValue
                    fetch.isFetchParamFirst = true

                    if (offsetValue != null) {
                        // OFFSET이 있는 경우
                        val offset = Offset()
                        offset.offset = offsetValue
                        offset.offsetParam = "ROWS"
                        selectBody.offset = offset
                        fetch.fetchParam = "ROWS"
                    } else {
                        fetch.fetchParam = "ROWS"
                    }

                    // LIMIT 제거하고 FETCH로 교체
                    selectBody.limit = null
                    selectBody.fetch = fetch

                    appliedRules.add("LIMIT → FETCH FIRST 변환 완료")

                    if (offsetValue != null) {
                        appliedRules.add("OFFSET 구문 추가")
                    }
                }
            }
            DialectType.TIBERO -> {
                // MySQL LIMIT/OFFSET → Tibero FETCH FIRST
                if (selectBody.limit != null) {
                    val limitValue = selectBody.limit.rowCount
                    val offsetValue = selectBody.offset?.offset

                    // FETCH 구문 생성 (Tibero는 Oracle 호환)
                    val fetch = Fetch()
                    fetch.rowCount = limitValue
                    fetch.isFetchParamFirst = true

                    if (offsetValue != null) {
                        // OFFSET이 있는 경우
                        val offset = Offset()
                        offset.offset = offsetValue
                        offset.offsetParam = "ROWS"
                        selectBody.offset = offset
                        fetch.fetchParam = "ROWS"
                    } else {
                        fetch.fetchParam = "ROWS"
                    }

                    // LIMIT 제거하고 FETCH로 교체
                    selectBody.limit = null
                    selectBody.fetch = fetch

                    appliedRules.add("LIMIT → FETCH FIRST 변환 완료")

                    if (offsetValue != null) {
                        appliedRules.add("OFFSET 구문 추가")
                    }
                }
            }
            else -> {
                // MySQL은 그대로
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
        selectBody.where?.let { whereExpression ->
            convertExpression(whereExpression, targetDialect, warnings, appliedRules)
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
            is BinaryExpression -> {
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
            DialectType.POSTGRESQL -> {
                when (functionName) {
                    "NOW" -> {
                        function.name = "CURRENT_TIMESTAMP"
                        appliedRules.add("NOW() → CURRENT_TIMESTAMP")
                    }
                    "IFNULL" -> {
                        function.name = "COALESCE"
                        appliedRules.add("IFNULL() → COALESCE()")
                    }
                    "DATE_FORMAT" -> {
                        function.name = "TO_CHAR"
                        warnings.add(createWarning(
                            type = WarningType.SYNTAX_DIFFERENCE,
                            message = "MySQL DATE_FORMAT() 포맷 문자열이 PostgreSQL TO_CHAR() 포맷과 다를 수 있습니다.",
                            severity = WarningSeverity.WARNING,
                            suggestion = "포맷 문자열을 PostgreSQL 형식으로 변경하세요. (예: '%Y-%m-%d' → 'YYYY-MM-DD')"
                        ))
                        appliedRules.add("DATE_FORMAT() → TO_CHAR() 변환 완료")
                    }
                }
            }
            DialectType.ORACLE -> {
                when (functionName) {
                    "NOW" -> {
                        function.name = "SYSDATE"
                        appliedRules.add("NOW() → SYSDATE")
                    }
                    "IFNULL" -> {
                        function.name = "NVL"
                        appliedRules.add("IFNULL() → NVL()")
                    }
                    "DATE_FORMAT" -> {
                        function.name = "TO_CHAR"
                        appliedRules.add("DATE_FORMAT() → TO_CHAR()")
                    }
                    "GROUP_CONCAT" -> {
                        function.name = "LISTAGG"
                        appliedRules.add("GROUP_CONCAT() → LISTAGG()")
                    }
                }
            }
            DialectType.TIBERO -> {
                when (functionName) {
                    "NOW" -> {
                        function.name = "SYSDATE"
                        appliedRules.add("NOW() → SYSDATE")
                    }
                    "IFNULL" -> {
                        function.name = "NVL"
                        appliedRules.add("IFNULL() → NVL()")
                    }
                    "DATE_FORMAT" -> {
                        function.name = "TO_CHAR"
                        appliedRules.add("DATE_FORMAT() → TO_CHAR()")
                    }
                    "GROUP_CONCAT" -> {
                        function.name = "LISTAGG"
                        appliedRules.add("GROUP_CONCAT() → LISTAGG()")
                    }
                }
            }
            else -> {
                // MySQL은 그대로
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
            DialectType.POSTGRESQL, DialectType.ORACLE, DialectType.TIBERO -> {
                // MySQL 백틱(`) → 큰따옴표(") 변환
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "MySQL 백틱(`) 인용 문자를 큰따옴표(\")로 변환해야 합니다.",
                    severity = WarningSeverity.INFO,
                    suggestion = "식별자 인용 문자를 확인하고 수정하세요."
                ))
                appliedRules.add("백틱(`) → 큰따옴표(\") 변환")
            }
            else -> {
                // MySQL은 그대로
            }
        }
    }
    
    // AbstractDatabaseDialect의 추상 메서드 구현
    
    override fun convertToMySqlFunction(functionName: String): String = functionName
    
    override fun convertToPostgreSqlFunction(functionName: String): String {
        return when (functionName.uppercase()) {
            "NOW" -> "CURRENT_TIMESTAMP"
            "IFNULL" -> "COALESCE"
            "DATE_FORMAT" -> "TO_CHAR"
            "GROUP_CONCAT" -> "STRING_AGG"
            else -> functionName
        }
    }
    
    override fun convertToOracleFunction(functionName: String): String {
        return when (functionName.uppercase()) {
            "NOW" -> "SYSDATE"
            "IFNULL" -> "NVL"
            "DATE_FORMAT" -> "TO_CHAR"
            "GROUP_CONCAT" -> "LISTAGG"
            else -> functionName
        }
    }
    
    override fun convertToTiberoFunction(functionName: String): String {
        return when (functionName.uppercase()) {
            "NOW" -> "SYSDATE"
            "IFNULL" -> "NVL"
            "DATE_FORMAT" -> "TO_CHAR"
            "GROUP_CONCAT" -> "LISTAGG"
            else -> functionName
        }
    }
    
    override fun convertToMySqlDataType(dataType: String): String = dataType
    
    override fun convertToPostgreSqlDataType(dataType: String): String {
        return when (dataType.uppercase()) {
            "TINYINT" -> "SMALLINT"
            "MEDIUMINT" -> "INTEGER"
            "LONGTEXT" -> "TEXT"
            "TINYTEXT" -> "TEXT"
            "MEDIUMTEXT" -> "TEXT"
            "LONGBLOB" -> "BYTEA"
            "TINYBLOB" -> "BYTEA"
            "MEDIUMBLOB" -> "BYTEA"
            "DATETIME" -> "TIMESTAMP"
            "BOOLEAN", "BOOL" -> "BOOLEAN"
            "JSON" -> "JSONB"
            else -> dataType
        }
    }
    
    override fun convertToOracleDataType(dataType: String): String {
        return when (dataType.uppercase()) {
            "TINYINT" -> "NUMBER(3)"
            "SMALLINT" -> "NUMBER(5)"
            "MEDIUMINT" -> "NUMBER(7)"
            "INT", "INTEGER" -> "NUMBER(10)"
            "BIGINT" -> "NUMBER(19)"
            "FLOAT" -> "BINARY_FLOAT"
            "DOUBLE" -> "BINARY_DOUBLE"
            "DECIMAL", "NUMERIC" -> "NUMBER"
            "VARCHAR" -> "VARCHAR2"
            "TEXT", "LONGTEXT", "MEDIUMTEXT", "TINYTEXT" -> "CLOB"
            "BLOB", "LONGBLOB", "MEDIUMBLOB", "TINYBLOB" -> "BLOB"
            "DATETIME", "TIMESTAMP" -> "TIMESTAMP"
            "DATE" -> "DATE"
            "TIME" -> "TIMESTAMP"
            "BOOLEAN", "BOOL" -> "NUMBER(1)"
            "JSON" -> "CLOB"
            else -> dataType
        }
    }
    
    override fun convertToTiberoDataType(dataType: String): String {
        // Tibero는 Oracle과 유사하므로 동일한 변환 규칙 사용
        return convertToOracleDataType(dataType)
    }
}
