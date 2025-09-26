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
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.drop.Drop
import net.sf.jsqlparser.expression.Function as SqlFunction
import net.sf.jsqlparser.expression.StringValue
import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.expression.CastExpression
import net.sf.jsqlparser.expression.operators.relational.ExpressionList
import org.springframework.stereotype.Component

/**
 * Oracle 특화 SQL 문법 및 함수 변환 규칙을 구현하는 방언 클래스
 */
@Component
class OracleDialect : AbstractDatabaseDialect() {
    
    override fun getDialectType(): DialectType = DialectType.ORACLE
    
    override fun getQuoteCharacter(): String = "\""
    
    override fun getSupportedFunctions(): Set<String> = DialectType.ORACLE.supportedFunctions
    
    override fun getDataTypeMapping(sourceType: String, targetDialect: DialectType): String {
        return when (targetDialect) {
            DialectType.ORACLE -> sourceType // 동일한 방언이므로 그대로 반환
            DialectType.MYSQL -> convertToMySqlDataType(sourceType)
            DialectType.POSTGRESQL -> convertToPostgreSqlDataType(sourceType)
            DialectType.TIBERO -> convertToTiberoDataType(sourceType)
        }
    }
    
    override fun canConvert(statement: Statement, targetDialect: DialectType): Boolean {
        // Oracle에서 다른 방언으로의 변환 가능 여부 확인
        return when (targetDialect) {
            DialectType.ORACLE -> true
            DialectType.MYSQL -> true
            DialectType.POSTGRESQL -> true
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
            is CreateTable -> {
                val convertedSql = convertCreateTable(statement, targetDialect, warnings, appliedRules)
                return ConversionResult(
                    convertedSql = convertedSql,
                    warnings = warnings,
                    appliedRules = appliedRules
                )
            }
            is Drop -> {
                val convertedSql = convertDropStatement(statement, targetDialect, warnings, appliedRules)
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
                // Oracle ROWNUM → MySQL LIMIT 변환
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Oracle ROWNUM 구문을 MySQL LIMIT으로 변환해야 합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "LIMIT n OFFSET m 구문을 사용하세요."
                ))
                appliedRules.add("ROWNUM → LIMIT 변환 필요")
            }
            DialectType.POSTGRESQL -> {
                // Oracle ROWNUM → PostgreSQL LIMIT 변환
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Oracle ROWNUM 구문을 PostgreSQL LIMIT으로 변환해야 합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "LIMIT n OFFSET m 구문을 사용하세요."
                ))
                appliedRules.add("ROWNUM → LIMIT 변환 필요")
            }
            DialectType.TIBERO -> {
                // Oracle과 Tibero는 동일한 ROWNUM 구문 사용
                appliedRules.add("ROWNUM 구문 유지")
            }
            else -> {
                // Oracle은 그대로
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
                            message = "Oracle NVL2() 함수는 MySQL에서 지원되지 않습니다.",
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
                            message = "Oracle DECODE() 함수는 MySQL에서 지원되지 않습니다.",
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
                            message = "Oracle NVL2() 함수는 PostgreSQL에서 지원되지 않습니다.",
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
                            message = "Oracle DECODE() 함수는 PostgreSQL에서 지원되지 않습니다.",
                            severity = WarningSeverity.ERROR,
                            suggestion = "CASE WHEN 구문을 사용하세요."
                        ))
                        appliedRules.add("DECODE() → CASE WHEN 변환 필요")
                    }
                }
            }
            DialectType.TIBERO -> {
                // Oracle과 Tibero는 대부분 동일한 함수를 사용
                appliedRules.add("Oracle 함수 유지")
            }
            else -> {
                // Oracle은 그대로
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
                    message = "Oracle ROWNUM 구문을 LIMIT으로 변환해야 합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "LIMIT n OFFSET m 구문을 사용하세요."
                ))
                appliedRules.add("ROWNUM → LIMIT 변환 필요")
            }
            DialectType.TIBERO -> {
                // Oracle과 Tibero는 동일한 ROWNUM 구문 사용
                appliedRules.add("ROWNUM 구문 유지")
            }
            else -> {
                // Oracle은 그대로
            }
        }
    }
    
    /**
     * CREATE TABLE 문 변환
     */
    private fun convertCreateTable(
        createTable: CreateTable,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // 테이블명 인용 문자 변환
        val tableName = createTable.table.name
        val convertedTableName = when (targetDialect) {
            DialectType.MYSQL -> tableName.replace("\"", "`")
            DialectType.POSTGRESQL -> tableName.replace("\"", "")
            else -> tableName
        }
        createTable.table.name = convertedTableName

        // 컬럼 정의 변환
        createTable.columnDefinitions?.forEach { colDef ->
            // 컬럼명 인용 문자 변환
            val colName = colDef.columnName
            val convertedColName = when (targetDialect) {
                DialectType.MYSQL -> colName.replace("\"", "`")
                DialectType.POSTGRESQL -> colName.replace("\"", "")
                else -> colName
            }
            colDef.columnName = convertedColName

            // 데이터 타입 변환
            colDef.colDataType?.let { dataType ->
                val typeName = dataType.dataType
                val convertedType = getDataTypeMapping(typeName, targetDialect)
                if (typeName != convertedType) {
                    dataType.dataType = convertedType
                    appliedRules.add("$typeName → $convertedType")
                }
            }
        }

        // Primary Key Constraint 변환
        createTable.indexes?.forEach { index ->
            index.columnsNames?.forEach { colName ->
                val convertedColName = when (targetDialect) {
                    DialectType.MYSQL -> colName.replace("\"", "`")
                    DialectType.POSTGRESQL -> colName.replace("\"", "")
                    else -> colName
                }
                index.columnsNames = index.columnsNames.map {
                    if (it == colName) convertedColName else it
                }
            }
        }

        appliedRules.add("CREATE TABLE 구문 변환")

        return createTable.toString()
    }

    /**
     * DROP 문 변환
     */
    private fun convertDropStatement(
        drop: Drop,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // 테이블명 인용 문자 변환
        drop.name?.let { table ->
            val tableName = table.name
            val convertedTableName = when (targetDialect) {
                DialectType.MYSQL -> tableName.replace("\"", "`")
                DialectType.POSTGRESQL -> tableName.replace("\"", "")
                else -> tableName
            }
            table.name = convertedTableName
        }

        appliedRules.add("DROP 구문 변환")

        return drop.toString()
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
                // Oracle 큰따옴표(") → MySQL 백틱(`) 변환
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Oracle 큰따옴표(\") 인용 문자를 MySQL 백틱(`)으로 변환해야 합니다.",
                    severity = WarningSeverity.INFO,
                    suggestion = "식별자 인용 문자를 확인하고 수정하세요."
                ))
                appliedRules.add("큰따옴표(\") → 백틱(`) 변환")
            }
            DialectType.POSTGRESQL, DialectType.TIBERO -> {
                // Oracle과 PostgreSQL/Tibero는 모두 큰따옴표 사용
                appliedRules.add("큰따옴표(\") 유지")
            }
            else -> {
                // Oracle은 그대로
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
