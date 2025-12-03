package com.sqlswitcher.converter

import com.sqlswitcher.parser.model.AstAnalysisResult
import com.sqlswitcher.model.ConversionOptions
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.statement.select.SelectItem
import net.sf.jsqlparser.statement.select.Limit
import net.sf.jsqlparser.statement.select.Offset
import net.sf.jsqlparser.statement.select.Top
import net.sf.jsqlparser.statement.select.Fetch
import net.sf.jsqlparser.statement.create.table.CreateTable
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
        return performConversionWithOptions(statement, targetDialect, analysisResult, null)
    }

    /**
     * 옵션을 포함한 변환 수행
     */
    fun performConversionWithOptions(
        statement: Statement,
        targetDialect: DialectType,
        analysisResult: AstAnalysisResult,
        options: ConversionOptions?
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

    /**
     * Tibero CREATE TABLE을 다른 방언으로 변환
     */
    private fun convertCreateTable(
        createTable: CreateTable,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return when (targetDialect) {
            DialectType.MYSQL -> {
                convertCreateTableToMySql(createTable, warnings, appliedRules)
            }
            DialectType.POSTGRESQL -> {
                convertCreateTableToPostgreSql(createTable, warnings, appliedRules)
            }
            DialectType.ORACLE -> {
                // Tibero와 Oracle은 거의 동일한 구문 사용
                appliedRules.add("Tibero CREATE TABLE 구문 유지 (Oracle 호환)")
                createTable.toString()
            }
            else -> createTable.toString()
        }
    }

    /**
     * Tibero CREATE TABLE을 MySQL 형식으로 변환
     */
    private fun convertCreateTableToMySql(
        createTable: CreateTable,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()

        // 테이블명 변환 (큰따옴표 → 백틱)
        val rawTableName = createTable.table.name.trim('"')
        result.append("CREATE TABLE `$rawTableName` (\n")

        // 컬럼 정의 변환
        val columnDefs = mutableListOf<String>()
        createTable.columnDefinitions?.forEach { colDef ->
            val columnName = colDef.columnName.trim('"')

            // 데이터 타입 변환
            val tiberoType = colDef.colDataType?.dataType?.uppercase() ?: "VARCHAR2"
            val typeArgs = colDef.colDataType?.argumentsStringList

            val mysqlType = convertTiberoTypeToMySql(tiberoType, typeArgs)

            // 제약조건 추출
            val constraints = mutableListOf<String>()
            colDef.columnSpecs?.forEach { spec ->
                val specStr = spec.toString().uppercase()
                when {
                    specStr == "NOT" -> {}
                    specStr == "NULL" && constraints.lastOrNull() == "NOT" -> {
                        constraints.remove("NOT")
                        constraints.add("NOT NULL")
                    }
                    specStr == "NULL" -> {}
                    specStr.startsWith("DEFAULT") -> constraints.add(spec.toString())
                    else -> {}
                }
            }

            val constraintStr = if (constraints.isNotEmpty()) " ${constraints.joinToString(" ")}" else ""
            columnDefs.add("    `$columnName` $mysqlType$constraintStr")
        }

        // PRIMARY KEY 추가
        createTable.indexes?.forEach { index ->
            if (index.type?.uppercase() == "PRIMARY KEY" || index.toString().uppercase().contains("PRIMARY KEY")) {
                val pkColumns = index.columnsNames?.joinToString(", ") { "`${it.trim('"')}`" }
                if (pkColumns != null) {
                    columnDefs.add("    PRIMARY KEY ($pkColumns)")
                }
            }
        }

        result.appendLine(columnDefs.joinToString(",\n"))
        result.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4")

        appliedRules.add("CREATE TABLE Tibero → MySQL 형식으로 변환")
        appliedRules.add("큰따옴표(\") → 백틱(`) 변환")
        appliedRules.add("데이터 타입 MySQL 형식으로 변환")

        return result.toString()
    }

    /**
     * Tibero CREATE TABLE을 PostgreSQL 형식으로 변환
     */
    private fun convertCreateTableToPostgreSql(
        createTable: CreateTable,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()

        // 테이블명 (큰따옴표 유지)
        val rawTableName = createTable.table.name.trim('"')
        result.append("CREATE TABLE \"$rawTableName\" (\n")

        // 컬럼 정의 변환
        val columnDefs = mutableListOf<String>()
        createTable.columnDefinitions?.forEach { colDef ->
            val columnName = colDef.columnName.trim('"')

            // 데이터 타입 변환
            val tiberoType = colDef.colDataType?.dataType?.uppercase() ?: "VARCHAR2"
            val typeArgs = colDef.colDataType?.argumentsStringList

            val pgType = convertTiberoTypeToPostgreSql(tiberoType, typeArgs)

            // 제약조건 추출
            val constraints = mutableListOf<String>()
            colDef.columnSpecs?.forEach { spec ->
                val specStr = spec.toString().uppercase()
                when {
                    specStr == "NOT" -> {}
                    specStr == "NULL" && constraints.lastOrNull() == "NOT" -> {
                        constraints.remove("NOT")
                        constraints.add("NOT NULL")
                    }
                    specStr == "NULL" -> {}
                    specStr.startsWith("DEFAULT") -> constraints.add(spec.toString())
                    else -> {}
                }
            }

            val constraintStr = if (constraints.isNotEmpty()) " ${constraints.joinToString(" ")}" else ""
            columnDefs.add("    \"$columnName\" $pgType$constraintStr")
        }

        // PRIMARY KEY 추가
        createTable.indexes?.forEach { index ->
            if (index.type?.uppercase() == "PRIMARY KEY" || index.toString().uppercase().contains("PRIMARY KEY")) {
                val pkColumns = index.columnsNames?.joinToString(", ") { "\"${it.trim('"')}\"" }
                if (pkColumns != null) {
                    columnDefs.add("    PRIMARY KEY ($pkColumns)")
                }
            }
        }

        result.appendLine(columnDefs.joinToString(",\n"))
        result.append(")")

        appliedRules.add("CREATE TABLE Tibero → PostgreSQL 형식으로 변환")
        appliedRules.add("데이터 타입 PostgreSQL 형식으로 변환")

        return result.toString()
    }

    /**
     * Tibero 데이터 타입을 MySQL 데이터 타입으로 변환
     */
    private fun convertTiberoTypeToMySql(tiberoType: String, args: List<String>?): String {
        val precision = args?.firstOrNull()?.toIntOrNull()
        val scale = args?.getOrNull(1)?.toIntOrNull()

        return when (tiberoType.uppercase()) {
            "NUMBER" -> {
                when {
                    precision != null && scale != null -> "DECIMAL($precision,$scale)"
                    precision != null -> "DECIMAL($precision)"
                    else -> "DECIMAL"
                }
            }
            "VARCHAR2" -> {
                val size = precision ?: 255
                "VARCHAR($size)"
            }
            "CHAR" -> {
                val size = precision ?: 1
                "CHAR($size)"
            }
            "CLOB" -> "LONGTEXT"
            "BLOB" -> "LONGBLOB"
            "RAW" -> {
                val size = precision ?: 255
                "VARBINARY($size)"
            }
            "LONG RAW" -> "LONGBLOB"
            "DATE" -> "DATETIME"
            "TIMESTAMP" -> if (precision != null) "DATETIME($precision)" else "DATETIME"
            "TIMESTAMP WITH TIME ZONE" -> "DATETIME"
            "TIMESTAMP WITH LOCAL TIME ZONE" -> "DATETIME"
            "INTERVAL YEAR TO MONTH" -> "VARCHAR(20)"
            "INTERVAL DAY TO SECOND" -> "VARCHAR(30)"
            "ROWID" -> "VARCHAR(18)"
            "UROWID" -> "VARCHAR(4000)"
            "XMLType" -> "LONGTEXT"
            "BINARY_FLOAT" -> "FLOAT"
            "BINARY_DOUBLE" -> "DOUBLE"
            else -> tiberoType
        }
    }

    /**
     * Tibero 데이터 타입을 PostgreSQL 데이터 타입으로 변환
     */
    private fun convertTiberoTypeToPostgreSql(tiberoType: String, args: List<String>?): String {
        val precision = args?.firstOrNull()?.toIntOrNull()
        val scale = args?.getOrNull(1)?.toIntOrNull()

        return when (tiberoType.uppercase()) {
            "NUMBER" -> {
                when {
                    precision != null && scale != null -> "NUMERIC($precision,$scale)"
                    precision != null -> "NUMERIC($precision)"
                    else -> "NUMERIC"
                }
            }
            "VARCHAR2" -> {
                val size = precision ?: 255
                "VARCHAR($size)"
            }
            "CHAR" -> {
                val size = precision ?: 1
                "CHAR($size)"
            }
            "CLOB" -> "TEXT"
            "BLOB" -> "BYTEA"
            "RAW" -> "BYTEA"
            "LONG RAW" -> "BYTEA"
            "DATE" -> "TIMESTAMP"
            "TIMESTAMP" -> if (precision != null) "TIMESTAMP($precision)" else "TIMESTAMP"
            "TIMESTAMP WITH TIME ZONE" -> "TIMESTAMPTZ"
            "TIMESTAMP WITH LOCAL TIME ZONE" -> "TIMESTAMP"
            "INTERVAL YEAR TO MONTH" -> "INTERVAL"
            "INTERVAL DAY TO SECOND" -> "INTERVAL"
            "ROWID" -> "VARCHAR(18)"
            "UROWID" -> "VARCHAR(4000)"
            "XMLType" -> "XML"
            "BINARY_FLOAT" -> "REAL"
            "BINARY_DOUBLE" -> "DOUBLE PRECISION"
            else -> tiberoType
        }
    }

    // ==================== Phase 3: 고급 SQL 기능 변환 ====================

    /**
     * Tibero 함수 기반 인덱스를 다른 방언으로 변환
     * 예: CREATE INDEX idx_name ON table (UPPER(column))
     */
    fun convertFunctionBasedIndex(
        indexSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val indexInfo = extractFunctionBasedIndexInfo(indexSql)

        return when (targetDialect) {
            DialectType.MYSQL -> {
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "MySQL 8.0 미만에서는 함수 기반 인덱스가 지원되지 않습니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "MySQL 8.0 이상을 사용하거나 Generated Column을 사용하세요."
                ))
                appliedRules.add("Tibero 함수 기반 인덱스 → MySQL 형식으로 변환")
                indexInfo.toMySql()
            }
            DialectType.POSTGRESQL -> {
                appliedRules.add("Tibero 함수 기반 인덱스 → PostgreSQL 형식으로 변환")
                indexInfo.toPostgreSql()
            }
            DialectType.ORACLE -> {
                appliedRules.add("Tibero 함수 기반 인덱스 유지 (Oracle 호환)")
                indexSql
            }
            DialectType.TIBERO -> indexSql
        }
    }

    /**
     * Tibero 함수 기반 인덱스 정보 추출
     */
    private fun extractFunctionBasedIndexInfo(sql: String): FunctionBasedIndexInfo {
        val upperSql = sql.uppercase()
        val isUnique = upperSql.contains("CREATE UNIQUE INDEX")

        // 인덱스명 추출
        val indexNameRegex = if (isUnique) {
            """CREATE\s+UNIQUE\s+INDEX\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        } else {
            """CREATE\s+INDEX\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        }
        val indexNameMatch = indexNameRegex.find(sql)
        val indexName = indexNameMatch?.groupValues?.get(2) ?:
            Regex("""CREATE\s+(?:UNIQUE\s+)?INDEX\s+"?(\w+)"?""", RegexOption.IGNORE_CASE)
                .find(sql)?.groupValues?.get(1) ?: "UNKNOWN_INDEX"

        // 테이블명 추출
        val tableNameRegex = """ON\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        val tableNameMatch = tableNameRegex.find(sql)
        val tableName = tableNameMatch?.groupValues?.get(2) ?:
            Regex("""ON\s+"?(\w+)"?""", RegexOption.IGNORE_CASE)
                .find(sql)?.groupValues?.get(1) ?: "UNKNOWN_TABLE"

        // 표현식 추출
        val expressionRegex = """ON\s+"?\w+"?(?:\."?\w+"?)?\s*\((.+?)\)(?:\s+TABLESPACE|\s*;|\s*$)""".toRegex(RegexOption.IGNORE_CASE)
        val expressionMatch = expressionRegex.find(sql)
        val expressions = if (expressionMatch != null) {
            parseIndexExpressions(expressionMatch.groupValues[1])
        } else {
            emptyList()
        }

        // 테이블스페이스 추출
        val tablespaceRegex = """TABLESPACE\s+"?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        val tablespace = tablespaceRegex.find(sql)?.groupValues?.get(1)

        return FunctionBasedIndexInfo(
            indexName = indexName,
            tableName = tableName,
            expressions = expressions,
            isUnique = isUnique,
            tablespace = tablespace
        )
    }

    /**
     * 인덱스 표현식 파싱 (중첩 괄호 처리)
     */
    private fun parseIndexExpressions(exprsStr: String): List<String> {
        val expressions = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (char in exprsStr) {
            when (char) {
                '(' -> {
                    depth++
                    current.append(char)
                }
                ')' -> {
                    depth--
                    current.append(char)
                }
                ',' -> {
                    if (depth == 0) {
                        expressions.add(current.toString().trim())
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            expressions.add(current.toString().trim())
        }

        return expressions
    }

    /**
     * Tibero Materialized View를 다른 방언으로 변환
     */
    fun convertMaterializedView(
        mvSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val mvInfo = extractMaterializedViewInfo(mvSql)

        return when (targetDialect) {
            DialectType.MYSQL -> {
                warnings.add(createWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "MySQL은 네이티브 Materialized View를 지원하지 않습니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "테이블 + 프로시저 + 이벤트 스케줄러로 시뮬레이션됩니다."
                ))
                appliedRules.add("Tibero Materialized View → MySQL 시뮬레이션 (테이블 + 프로시저)")
                mvInfo.toMySql(warnings)
            }
            DialectType.POSTGRESQL -> {
                appliedRules.add("Tibero Materialized View → PostgreSQL 형식으로 변환")
                mvInfo.toPostgreSql(warnings)
            }
            DialectType.ORACLE -> {
                appliedRules.add("Tibero Materialized View 유지 (Oracle 호환)")
                mvSql
            }
            DialectType.TIBERO -> mvSql
        }
    }

    /**
     * Tibero Materialized View 정보 추출
     */
    private fun extractMaterializedViewInfo(sql: String): MaterializedViewInfo {
        val upperSql = sql.uppercase()

        // 뷰명 추출
        val viewNameRegex = """CREATE\s+MATERIALIZED\s+VIEW\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        val viewNameMatch = viewNameRegex.find(sql)
        val viewName = viewNameMatch?.groupValues?.get(2) ?:
            Regex("""CREATE\s+MATERIALIZED\s+VIEW\s+"?(\w+)"?""", RegexOption.IGNORE_CASE)
                .find(sql)?.groupValues?.get(1) ?: "UNKNOWN_MV"

        // BUILD 옵션 추출
        val buildOption = when {
            upperSql.contains("BUILD DEFERRED") -> MaterializedViewInfo.BuildOption.DEFERRED
            else -> MaterializedViewInfo.BuildOption.IMMEDIATE
        }

        // REFRESH 옵션 추출
        val refreshOption = when {
            upperSql.contains("REFRESH FAST") -> MaterializedViewInfo.RefreshOption.FAST
            upperSql.contains("REFRESH COMPLETE") -> MaterializedViewInfo.RefreshOption.COMPLETE
            upperSql.contains("REFRESH FORCE") -> MaterializedViewInfo.RefreshOption.FORCE
            upperSql.contains("NEVER REFRESH") -> MaterializedViewInfo.RefreshOption.NEVER
            else -> MaterializedViewInfo.RefreshOption.COMPLETE
        }

        // SELECT 쿼리 추출
        val selectRegex = """AS\s+(SELECT.+)$""".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val selectQuery = selectRegex.find(sql)?.groupValues?.get(1)?.trim()?.trimEnd(';') ?: ""

        return MaterializedViewInfo(
            viewName = viewName,
            selectQuery = selectQuery,
            buildOption = buildOption,
            refreshOption = refreshOption
        )
    }

    /**
     * Tibero 파티션 테이블을 다른 방언으로 변환
     */
    fun convertPartitionTable(
        partitionSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val partitionInfo = extractTablePartitionInfo(partitionSql)

        return when (targetDialect) {
            DialectType.MYSQL -> {
                appliedRules.add("Tibero 파티션 테이블 → MySQL 형식으로 변환")
                partitionInfo.toMySqlPartitionClause()
            }
            DialectType.POSTGRESQL -> {
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "PostgreSQL은 파티션을 별도의 테이블로 생성해야 합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "각 파티션에 대해 CREATE TABLE ... PARTITION OF 구문을 사용하세요."
                ))
                appliedRules.add("Tibero 파티션 테이블 → PostgreSQL 형식으로 변환")
                val mainTable = partitionInfo.toPostgreSqlPartitionClause()
                val partitionTables = partitionInfo.toPostgreSqlPartitionTables()
                if (partitionTables.isNotEmpty()) {
                    "$mainTable\n\n${partitionTables.joinToString("\n\n")}"
                } else {
                    mainTable
                }
            }
            DialectType.ORACLE -> {
                appliedRules.add("Tibero 파티션 테이블 유지 (Oracle 호환)")
                partitionSql
            }
            DialectType.TIBERO -> partitionSql
        }
    }

    /**
     * Tibero 파티션 정보 추출
     */
    private fun extractTablePartitionInfo(sql: String): TablePartitionDetailInfo {
        val upperSql = sql.uppercase()

        // 테이블명 추출
        val tableNameRegex = """CREATE\s+TABLE\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        val tableNameMatch = tableNameRegex.find(sql)
        val tableName = tableNameMatch?.groupValues?.get(2) ?:
            Regex("""CREATE\s+TABLE\s+"?(\w+)"?""", RegexOption.IGNORE_CASE)
                .find(sql)?.groupValues?.get(1) ?: "UNKNOWN_TABLE"

        // 파티션 타입 추출
        val partitionType = when {
            upperSql.contains("PARTITION BY RANGE") -> PartitionType.RANGE
            upperSql.contains("PARTITION BY LIST") -> PartitionType.LIST
            upperSql.contains("PARTITION BY HASH") -> PartitionType.HASH
            else -> PartitionType.RANGE
        }

        // 파티션 컬럼 추출
        val partitionColRegex = """PARTITION\s+BY\s+\w+\s*\(([^)]+)\)""".toRegex(RegexOption.IGNORE_CASE)
        val partitionColumns = partitionColRegex.find(sql)?.groupValues?.get(1)
            ?.split(",")?.map { it.trim().trim('"') } ?: emptyList()

        // 파티션 정의 추출
        val partitions = extractPartitionDefinitions(sql, partitionType)

        return TablePartitionDetailInfo(
            tableName = tableName,
            partitionType = partitionType,
            partitionColumns = partitionColumns,
            partitions = partitions
        )
    }

    /**
     * 파티션 정의 추출
     */
    private fun extractPartitionDefinitions(sql: String, partitionType: PartitionType): List<PartitionDefinition> {
        val partitions = mutableListOf<PartitionDefinition>()

        when (partitionType) {
            PartitionType.RANGE -> {
                val rangeRegex = """PARTITION\s+"?(\w+)"?\s+VALUES\s+LESS\s+THAN\s*\(([^)]+)\)""".toRegex(RegexOption.IGNORE_CASE)
                rangeRegex.findAll(sql).forEach { match ->
                    partitions.add(PartitionDefinition(
                        name = match.groupValues[1],
                        values = match.groupValues[2].trim()
                    ))
                }
            }
            PartitionType.LIST -> {
                val listRegex = """PARTITION\s+"?(\w+)"?\s+VALUES\s*\(([^)]+)\)""".toRegex(RegexOption.IGNORE_CASE)
                listRegex.findAll(sql).forEach { match ->
                    partitions.add(PartitionDefinition(
                        name = match.groupValues[1],
                        values = match.groupValues[2].trim()
                    ))
                }
            }
            PartitionType.HASH -> {
                val hashRegex = """PARTITION\s+"?(\w+)"?(?:\s+TABLESPACE|\s*,|\s*\))""".toRegex(RegexOption.IGNORE_CASE)
                hashRegex.findAll(sql).forEach { match ->
                    partitions.add(PartitionDefinition(
                        name = match.groupValues[1],
                        values = ""
                    ))
                }
            }
            else -> { }
        }

        return partitions
    }

    /**
     * Tibero JSON 함수를 다른 방언으로 변환
     * Tibero는 Oracle 호환이므로 JSON_VALUE, JSON_QUERY, JSON_EXISTS 지원
     */
    fun convertJsonFunction(
        jsonSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = jsonSql

        when (targetDialect) {
            DialectType.MYSQL -> {
                // Tibero JSON_VALUE → MySQL JSON_EXTRACT + JSON_UNQUOTE
                val jsonValueRegex = """JSON_VALUE\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = jsonValueRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("Tibero JSON_VALUE → MySQL JSON_UNQUOTE(JSON_EXTRACT()) 변환")
                    "JSON_UNQUOTE(JSON_EXTRACT($column, '\$.$path'))"
                }

                // Tibero JSON_QUERY → MySQL JSON_EXTRACT
                val jsonQueryRegex = """JSON_QUERY\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = jsonQueryRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("Tibero JSON_QUERY → MySQL JSON_EXTRACT 변환")
                    "JSON_EXTRACT($column, '\$.$path')"
                }

                // Tibero JSON_EXISTS → MySQL JSON_CONTAINS_PATH
                val jsonExistsRegex = """JSON_EXISTS\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = jsonExistsRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("Tibero JSON_EXISTS → MySQL JSON_CONTAINS_PATH 변환")
                    "JSON_CONTAINS_PATH($column, 'one', '\$.$path')"
                }
            }
            DialectType.POSTGRESQL -> {
                // Tibero JSON_VALUE → PostgreSQL ->> operator
                val jsonValueRegex = """JSON_VALUE\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = jsonValueRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("Tibero JSON_VALUE → PostgreSQL ->> 연산자 변환")
                    "$column ->> '$path'"
                }

                // Tibero JSON_QUERY → PostgreSQL -> operator
                val jsonQueryRegex = """JSON_QUERY\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = jsonQueryRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("Tibero JSON_QUERY → PostgreSQL -> 연산자 변환")
                    "$column -> '$path'"
                }

                // Tibero JSON_EXISTS → PostgreSQL ? operator
                val jsonExistsRegex = """JSON_EXISTS\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = jsonExistsRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("Tibero JSON_EXISTS → PostgreSQL ? 연산자 변환")
                    "$column ? '$path'"
                }
            }
            DialectType.ORACLE -> {
                appliedRules.add("Tibero JSON 함수 유지 (Oracle 호환)")
            }
            DialectType.TIBERO -> { /* 변환 불필요 */ }
        }

        return result
    }

    /**
     * Tibero 정규식 함수를 다른 방언으로 변환
     * Tibero는 Oracle 호환이므로 REGEXP_LIKE, REGEXP_SUBSTR, REGEXP_REPLACE 등 지원
     */
    fun convertRegexFunction(
        regexSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = regexSql

        when (targetDialect) {
            DialectType.MYSQL -> {
                // REGEXP_LIKE → REGEXP
                val regexpLikeRegex = """REGEXP_LIKE\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*(?:,\s*'([^']*)'\s*)?\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpLikeRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    appliedRules.add("Tibero REGEXP_LIKE → MySQL REGEXP 변환")
                    "$column REGEXP '$pattern'"
                }

                // REGEXP_SUBSTR → REGEXP_SUBSTR (MySQL 8.0+)
                val regexpSubstrRegex = """REGEXP_SUBSTR\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpSubstrRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "MySQL REGEXP_SUBSTR은 8.0 이상에서만 지원됩니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "MySQL 8.0 이상을 사용하세요."
                    ))
                    appliedRules.add("Tibero REGEXP_SUBSTR → MySQL REGEXP_SUBSTR 변환")
                    "REGEXP_SUBSTR($column, '$pattern')"
                }

                // REGEXP_REPLACE → REGEXP_REPLACE (MySQL 8.0+)
                val regexpReplaceRegex = """REGEXP_REPLACE\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*,\s*'([^']*)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpReplaceRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    val replacement = match.groupValues[3]
                    appliedRules.add("Tibero REGEXP_REPLACE → MySQL REGEXP_REPLACE 변환")
                    "REGEXP_REPLACE($column, '$pattern', '$replacement')"
                }
            }
            DialectType.POSTGRESQL -> {
                // REGEXP_LIKE → ~ operator
                val regexpLikeRegex = """REGEXP_LIKE\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*(?:,\s*'([^']*)'\s*)?\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpLikeRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    val flags = match.groupValues.getOrNull(3) ?: ""
                    appliedRules.add("Tibero REGEXP_LIKE → PostgreSQL ~ 연산자 변환")
                    if (flags.contains("i", ignoreCase = true)) {
                        "$column ~* '$pattern'"
                    } else {
                        "$column ~ '$pattern'"
                    }
                }

                // REGEXP_SUBSTR → regexp_matches()[1]
                val regexpSubstrRegex = """REGEXP_SUBSTR\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpSubstrRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    appliedRules.add("Tibero REGEXP_SUBSTR → PostgreSQL regexp_matches 변환")
                    "(regexp_matches($column, '$pattern'))[1]"
                }

                // REGEXP_REPLACE → regexp_replace
                val regexpReplaceRegex = """REGEXP_REPLACE\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*,\s*'([^']*)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpReplaceRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    val replacement = match.groupValues[3]
                    appliedRules.add("Tibero REGEXP_REPLACE → PostgreSQL regexp_replace 변환")
                    "regexp_replace($column, '$pattern', '$replacement', 'g')"
                }
            }
            DialectType.ORACLE -> {
                appliedRules.add("Tibero 정규식 함수 유지 (Oracle 호환)")
            }
            DialectType.TIBERO -> { /* 변환 불필요 */ }
        }

        return result
    }

    /**
     * JSON 함수 정보 추출
     */
    fun extractJsonFunctionInfo(jsonFuncStr: String): JsonFunctionInfo? {
        // JSON_VALUE
        val jsonValueRegex = """JSON_VALUE\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
        jsonValueRegex.find(jsonFuncStr)?.let { match ->
            return JsonFunctionInfo(
                functionType = JsonFunctionInfo.JsonFunctionType.EXTRACT,
                jsonExpression = match.groupValues[1],
                path = match.groupValues[2]
            )
        }

        // JSON_QUERY
        val jsonQueryRegex = """JSON_QUERY\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
        jsonQueryRegex.find(jsonFuncStr)?.let { match ->
            return JsonFunctionInfo(
                functionType = JsonFunctionInfo.JsonFunctionType.QUERY,
                jsonExpression = match.groupValues[1],
                path = match.groupValues[2]
            )
        }

        return null
    }

    /**
     * 정규식 함수 정보 추출
     */
    fun extractRegexFunctionInfo(regexFuncStr: String): RegexFunctionInfo? {
        // REGEXP_LIKE
        val regexpLikeRegex = """REGEXP_LIKE\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
        regexpLikeRegex.find(regexFuncStr)?.let { match ->
            return RegexFunctionInfo(
                functionType = RegexFunctionInfo.RegexFunctionType.LIKE,
                sourceExpression = match.groupValues[1],
                pattern = match.groupValues[2]
            )
        }

        // REGEXP_REPLACE
        val regexpReplaceRegex = """REGEXP_REPLACE\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*,\s*'([^']*)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
        regexpReplaceRegex.find(regexFuncStr)?.let { match ->
            return RegexFunctionInfo(
                functionType = RegexFunctionInfo.RegexFunctionType.REPLACE,
                sourceExpression = match.groupValues[1],
                pattern = match.groupValues[2],
                replacement = match.groupValues[3]
            )
        }

        return null
    }

    // ==================== Phase 4: 트리거 변환 ====================

    /**
     * Tibero 트리거를 다른 방언으로 변환
     */
    fun convertTrigger(
        triggerSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val triggerInfo = extractTriggerInfo(triggerSql)

        return when (targetDialect) {
            DialectType.MYSQL -> {
                appliedRules.add("Tibero 트리거 → MySQL 형식으로 변환")
                convertTriggerToMySql(triggerInfo, warnings, appliedRules)
            }
            DialectType.POSTGRESQL -> {
                appliedRules.add("Tibero 트리거 → PostgreSQL 형식으로 변환")
                convertTriggerToPostgreSql(triggerInfo, warnings, appliedRules)
            }
            DialectType.ORACLE -> {
                // Tibero와 Oracle은 호환되므로 그대로 유지
                appliedRules.add("Tibero 트리거 유지 (Oracle 호환)")
                triggerSql
            }
            DialectType.TIBERO -> triggerSql
        }
    }

    /**
     * Tibero 트리거 SQL에서 TriggerInfo 추출
     *
     * Tibero 트리거 구문 (Oracle 호환):
     * CREATE OR REPLACE TRIGGER schema.trigger_name
     *   BEFORE | AFTER | INSTEAD OF
     *   INSERT | UPDATE OF column_list | DELETE
     *   OR INSERT | UPDATE OF column_list | DELETE ...
     *   ON schema.table_name | schema.view_name
     *   REFERENCING OLD AS old_name NEW AS new_name
     *   FOR EACH ROW
     *   WHEN (condition)
     *   trigger_body
     */
    private fun extractTriggerInfo(triggerSql: String): TriggerInfo {
        val upperSql = triggerSql.uppercase()

        // 트리거명 추출
        val triggerNameRegex = """CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        val triggerNameMatch = triggerNameRegex.find(triggerSql)
        val triggerName = triggerNameMatch?.groupValues?.get(2)
            ?: Regex("""CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+"?(\w+)"?""", RegexOption.IGNORE_CASE)
                .find(triggerSql)?.groupValues?.get(1) ?: "UNKNOWN_TRIGGER"

        // 테이블명 추출
        val tableNameRegex = """ON\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        val tableNameMatch = tableNameRegex.find(triggerSql)
        val tableName = tableNameMatch?.groupValues?.get(2)
            ?: Regex("""ON\s+"?(\w+)"?""", RegexOption.IGNORE_CASE)
                .find(triggerSql)?.groupValues?.get(1) ?: "UNKNOWN_TABLE"

        // 타이밍 추출
        val timing = when {
            upperSql.contains("INSTEAD OF") -> TriggerInfo.TriggerTiming.INSTEAD_OF
            upperSql.contains("BEFORE") -> TriggerInfo.TriggerTiming.BEFORE
            upperSql.contains("AFTER") -> TriggerInfo.TriggerTiming.AFTER
            else -> TriggerInfo.TriggerTiming.AFTER
        }

        // 이벤트 추출
        val events = mutableListOf<TriggerInfo.TriggerEvent>()
        if (upperSql.contains("INSERT")) events.add(TriggerInfo.TriggerEvent.INSERT)
        if (upperSql.contains("UPDATE")) events.add(TriggerInfo.TriggerEvent.UPDATE)
        if (upperSql.contains("DELETE")) events.add(TriggerInfo.TriggerEvent.DELETE)
        if (events.isEmpty()) events.add(TriggerInfo.TriggerEvent.INSERT)

        // FOR EACH ROW 추출
        val forEachRow = upperSql.contains("FOR EACH ROW")

        // WHEN 조건 추출
        val whenRegex = """WHEN\s*\((.+?)\)\s*(?:BEGIN|DECLARE|$)""".toRegex(RegexOption.IGNORE_CASE)
        val whenCondition = whenRegex.find(triggerSql)?.groupValues?.get(1)?.trim()

        // REFERENCING 절 추출
        val referencingRegex = """REFERENCING\s+(?:OLD\s+(?:AS\s+)?(\w+)\s+)?(?:NEW\s+(?:AS\s+)?(\w+))?""".toRegex(RegexOption.IGNORE_CASE)
        val referencingMatch = referencingRegex.find(triggerSql)
        val referencing = if (referencingMatch != null) {
            TriggerInfo.ReferencingClause(
                oldAlias = referencingMatch.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() },
                newAlias = referencingMatch.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
            )
        } else {
            null
        }

        // 트리거 본문 추출
        val bodyRegex = """(?:BEGIN|DECLARE)\s*([\s\S]+?)\s*END\s*;""".toRegex(RegexOption.IGNORE_CASE)
        val bodyMatch = bodyRegex.find(triggerSql)
        val body = bodyMatch?.groupValues?.get(1)?.trim() ?: ""

        return TriggerInfo(
            name = triggerName,
            tableName = tableName,
            timing = timing,
            events = events,
            forEachRow = forEachRow,
            whenCondition = whenCondition,
            body = body,
            referencing = referencing
        )
    }

    /**
     * Tibero 트리거를 MySQL 형식으로 변환
     */
    private fun convertTriggerToMySql(
        triggerInfo: TriggerInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // MySQL은 하나의 트리거에 하나의 이벤트만 허용
        if (triggerInfo.events.size > 1) {
            warnings.add(createWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL은 하나의 트리거에 하나의 이벤트만 허용합니다. 여러 트리거로 분리됩니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "각 이벤트(INSERT/UPDATE/DELETE)별로 별도의 트리거를 생성하세요."
            ))
        }

        // MySQL은 INSTEAD OF 트리거를 지원하지 않음
        if (triggerInfo.timing == TriggerInfo.TriggerTiming.INSTEAD_OF) {
            warnings.add(createWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "MySQL은 INSTEAD OF 트리거를 지원하지 않습니다.",
                severity = WarningSeverity.ERROR,
                suggestion = "BEFORE 또는 AFTER 트리거로 변경하고 로직을 조정하세요."
            ))
        }

        // WHEN 조건이 있으면 경고
        if (!triggerInfo.whenCondition.isNullOrBlank()) {
            warnings.add(createWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL은 WHEN 조건을 지원하지 않습니다. 트리거 본문 내 IF 문으로 변환됩니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "트리거 본문에서 IF 조건으로 처리하세요."
            ))
        }

        val results = mutableListOf<String>()

        // 각 이벤트별로 트리거 생성
        for (event in triggerInfo.events) {
            val result = StringBuilder()

            val triggerName = if (triggerInfo.events.size > 1) {
                "${triggerInfo.name}_${event.name.lowercase()}"
            } else {
                triggerInfo.name
            }

            result.append("DELIMITER //\n\n")
            result.append("CREATE TRIGGER `$triggerName`\n")

            // 타이밍 (INSTEAD OF는 BEFORE로 변환)
            val timingStr = when (triggerInfo.timing) {
                TriggerInfo.TriggerTiming.BEFORE -> "BEFORE"
                TriggerInfo.TriggerTiming.AFTER -> "AFTER"
                TriggerInfo.TriggerTiming.INSTEAD_OF -> "BEFORE" // MySQL 호환성
            }

            val eventStr = when (event) {
                TriggerInfo.TriggerEvent.INSERT -> "INSERT"
                TriggerInfo.TriggerEvent.UPDATE -> "UPDATE"
                TriggerInfo.TriggerEvent.DELETE -> "DELETE"
            }

            result.append("    $timingStr $eventStr ON `${triggerInfo.tableName}`\n")
            result.append("    FOR EACH ROW\n")
            result.append("BEGIN\n")

            // WHEN 조건을 IF 문으로 변환
            if (!triggerInfo.whenCondition.isNullOrBlank()) {
                val condition = convertTiberoConditionToMySql(triggerInfo.whenCondition)
                result.append("    IF $condition THEN\n")

                if (triggerInfo.body.isNotEmpty()) {
                    val mysqlBody = convertTiberoBodyToMySql(triggerInfo.body)
                    result.append("        $mysqlBody\n")
                }

                result.append("    END IF;\n")
            } else if (triggerInfo.body.isNotEmpty()) {
                val mysqlBody = convertTiberoBodyToMySql(triggerInfo.body)
                result.append("    $mysqlBody\n")
            } else {
                result.append("    -- 트리거 본문을 추가하세요\n")
            }

            result.append("END //\n\n")
            result.append("DELIMITER ;")

            results.add(result.toString())
        }

        appliedRules.add("Tibero FOR EACH ROW → MySQL FOR EACH ROW")
        appliedRules.add(":NEW/:OLD → NEW/OLD 변환")

        return results.joinToString("\n\n")
    }

    /**
     * Tibero 트리거를 PostgreSQL 형식으로 변환
     */
    private fun convertTriggerToPostgreSql(
        triggerInfo: TriggerInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()
        val funcName = "${triggerInfo.name}_func"

        // PostgreSQL은 트리거 함수를 별도로 생성해야 함
        result.appendLine("-- 트리거 함수")
        result.appendLine("CREATE OR REPLACE FUNCTION \"$funcName\"()")
        result.appendLine("RETURNS TRIGGER AS \$\$")

        // 변수 선언이 필요한 경우
        if (triggerInfo.body.uppercase().contains("DECLARE")) {
            result.appendLine("DECLARE")
            // DECLARE 섹션에서 변수 추출
            val declareRegex = """DECLARE\s+([\s\S]*?)(?=BEGIN)""".toRegex(RegexOption.IGNORE_CASE)
            val declareMatch = declareRegex.find(triggerInfo.body)
            if (declareMatch != null) {
                val declarations = convertTiberoDeclarationsToPostgreSql(declareMatch.groupValues[1])
                result.appendLine(declarations)
            }
        }

        result.appendLine("BEGIN")

        // 본문 변환
        var pgBody = convertTiberoBodyToPostgreSql(triggerInfo.body)

        // WHEN 조건이 있으면 PostgreSQL 트리거 정의에 추가하므로 본문에서는 제외
        result.appendLine(pgBody)

        // RETURN 문 추가 (Tibero/Oracle과 달리 PostgreSQL은 명시적 RETURN 필요)
        if (triggerInfo.timing != TriggerInfo.TriggerTiming.INSTEAD_OF) {
            if (triggerInfo.events.any { it == TriggerInfo.TriggerEvent.INSERT || it == TriggerInfo.TriggerEvent.UPDATE }) {
                if (!pgBody.uppercase().contains("RETURN NEW") && !pgBody.uppercase().contains("RETURN OLD")) {
                    result.appendLine("    RETURN NEW;")
                }
            } else if (triggerInfo.events.any { it == TriggerInfo.TriggerEvent.DELETE }) {
                if (!pgBody.uppercase().contains("RETURN OLD") && !pgBody.uppercase().contains("RETURN NEW")) {
                    result.appendLine("    RETURN OLD;")
                }
            }
        }

        result.appendLine("END;")
        result.appendLine("\$\$ LANGUAGE plpgsql;")
        result.appendLine()

        // 트리거 생성
        result.appendLine("-- 트리거")
        result.append("CREATE TRIGGER \"${triggerInfo.name}\"")
        result.appendLine()

        // 타이밍 및 이벤트
        val timingStr = when (triggerInfo.timing) {
            TriggerInfo.TriggerTiming.BEFORE -> "BEFORE"
            TriggerInfo.TriggerTiming.AFTER -> "AFTER"
            TriggerInfo.TriggerTiming.INSTEAD_OF -> "INSTEAD OF"
        }

        val eventsStr = triggerInfo.events.joinToString(" OR ") { event ->
            when (event) {
                TriggerInfo.TriggerEvent.INSERT -> "INSERT"
                TriggerInfo.TriggerEvent.UPDATE -> "UPDATE"
                TriggerInfo.TriggerEvent.DELETE -> "DELETE"
            }
        }

        result.append("    $timingStr $eventsStr")
        result.appendLine()
        result.append("    ON \"${triggerInfo.tableName}\"")
        result.appendLine()

        // FOR EACH ROW
        if (triggerInfo.forEachRow) {
            result.append("    FOR EACH ROW")
            result.appendLine()
        }

        // WHEN 조건
        if (!triggerInfo.whenCondition.isNullOrBlank()) {
            val pgCondition = convertTiberoConditionToPostgreSql(triggerInfo.whenCondition)
            result.append("    WHEN ($pgCondition)")
            result.appendLine()
        }

        result.append("    EXECUTE FUNCTION \"$funcName\"();")

        appliedRules.add("Tibero 트리거 → PostgreSQL 트리거 함수 + 트리거 형식")
        appliedRules.add(":NEW/:OLD → NEW/OLD 변환")

        return result.toString()
    }

    /**
     * Tibero 조건문을 MySQL 형식으로 변환
     */
    private fun convertTiberoConditionToMySql(condition: String): String {
        var result = condition

        // :NEW. → NEW.
        result = result.replace(Regex(""":NEW\.""", RegexOption.IGNORE_CASE), "NEW.")

        // :OLD. → OLD.
        result = result.replace(Regex(""":OLD\.""", RegexOption.IGNORE_CASE), "OLD.")

        // NVL → IFNULL
        result = result.replace(Regex("""NVL\s*\(""", RegexOption.IGNORE_CASE), "IFNULL(")

        return result
    }

    /**
     * Tibero 트리거 본문을 MySQL 형식으로 변환
     */
    private fun convertTiberoBodyToMySql(body: String): String {
        var result = body

        // :NEW. → NEW.
        result = result.replace(Regex(""":NEW\.""", RegexOption.IGNORE_CASE), "NEW.")

        // :OLD. → OLD.
        result = result.replace(Regex(""":OLD\.""", RegexOption.IGNORE_CASE), "OLD.")

        // NVL → IFNULL
        result = result.replace(Regex("""NVL\s*\(""", RegexOption.IGNORE_CASE), "IFNULL(")

        // SYSDATE → NOW()
        result = result.replace(Regex("""SYSDATE""", RegexOption.IGNORE_CASE), "NOW()")

        // RAISE_APPLICATION_ERROR → SIGNAL SQLSTATE
        result = result.replace(Regex("""RAISE_APPLICATION_ERROR\s*\(\s*-?\d+\s*,\s*'([^']*)'\s*\)""", RegexOption.IGNORE_CASE)) { match ->
            "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '${match.groupValues[1]}'"
        }

        // TO_CHAR → DATE_FORMAT (날짜 함수)
        result = result.replace(Regex("""TO_CHAR\s*\(""", RegexOption.IGNORE_CASE), "DATE_FORMAT(")

        // TO_DATE → STR_TO_DATE
        result = result.replace(Regex("""TO_DATE\s*\(""", RegexOption.IGNORE_CASE), "STR_TO_DATE(")

        // DECODE 함수 경고 (복잡한 변환 필요)
        if (result.uppercase().contains("DECODE")) {
            result = result.replace(Regex("""DECODE\s*\(""", RegexOption.IGNORE_CASE), "/* DECODE → CASE WHEN 변환 필요 */ DECODE(")
        }

        // DECLARE 섹션 제거 (MySQL 트리거에서는 다르게 처리)
        result = result.replace(Regex("""DECLARE[\s\S]*?(?=BEGIN)""", RegexOption.IGNORE_CASE), "")

        // BEGIN 제거 (이미 트리거 본문에 BEGIN이 있음)
        result = result.replace(Regex("""^\s*BEGIN\s*""", RegexOption.IGNORE_CASE), "")

        // END; 제거
        result = result.replace(Regex("""\s*END\s*;\s*$""", RegexOption.IGNORE_CASE), "")

        return result.trim()
    }

    /**
     * Tibero 조건문을 PostgreSQL 형식으로 변환
     */
    private fun convertTiberoConditionToPostgreSql(condition: String): String {
        var result = condition

        // :NEW. → NEW.
        result = result.replace(Regex(""":NEW\.""", RegexOption.IGNORE_CASE), "NEW.")

        // :OLD. → OLD.
        result = result.replace(Regex(""":OLD\.""", RegexOption.IGNORE_CASE), "OLD.")

        // NVL → COALESCE
        result = result.replace(Regex("""NVL\s*\(""", RegexOption.IGNORE_CASE), "COALESCE(")

        return result
    }

    /**
     * Tibero 트리거 본문을 PostgreSQL 형식으로 변환
     */
    private fun convertTiberoBodyToPostgreSql(body: String): String {
        var result = body

        // :NEW. → NEW.
        result = result.replace(Regex(""":NEW\.""", RegexOption.IGNORE_CASE), "NEW.")

        // :OLD. → OLD.
        result = result.replace(Regex(""":OLD\.""", RegexOption.IGNORE_CASE), "OLD.")

        // NVL → COALESCE
        result = result.replace(Regex("""NVL\s*\(""", RegexOption.IGNORE_CASE), "COALESCE(")

        // NVL2 → CASE WHEN
        result = result.replace(Regex("""NVL2\s*\(\s*(\w+)\s*,\s*([^,]+)\s*,\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)) { match ->
            "CASE WHEN ${match.groupValues[1]} IS NOT NULL THEN ${match.groupValues[2]} ELSE ${match.groupValues[3]} END"
        }

        // SYSDATE → CURRENT_TIMESTAMP
        result = result.replace(Regex("""SYSDATE""", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")

        // RAISE_APPLICATION_ERROR → RAISE EXCEPTION
        result = result.replace(Regex("""RAISE_APPLICATION_ERROR\s*\(\s*-?\d+\s*,\s*'([^']*)'\s*\)""", RegexOption.IGNORE_CASE)) { match ->
            "RAISE EXCEPTION '${match.groupValues[1]}'"
        }

        // DECODE → CASE WHEN
        result = result.replace(Regex("""DECODE\s*\(\s*(\w+)""", RegexOption.IGNORE_CASE)) { match ->
            "/* DECODE → CASE WHEN 변환 필요 */ CASE ${match.groupValues[1]}"
        }

        // DECLARE 섹션 처리
        result = result.replace(Regex("""DECLARE[\s\S]*?(?=BEGIN)""", RegexOption.IGNORE_CASE), "")

        // BEGIN 제거
        result = result.replace(Regex("""^\s*BEGIN\s*""", RegexOption.IGNORE_CASE), "")

        // END; 제거
        result = result.replace(Regex("""\s*END\s*;\s*$""", RegexOption.IGNORE_CASE), "")

        return result.trim()
    }

    /**
     * Tibero 변수 선언을 PostgreSQL 형식으로 변환
     */
    private fun convertTiberoDeclarationsToPostgreSql(declarations: String): String {
        var result = declarations

        // VARCHAR2 → VARCHAR
        result = result.replace(Regex("""VARCHAR2\s*\((\d+)\)""", RegexOption.IGNORE_CASE), "VARCHAR($1)")

        // NUMBER → NUMERIC
        result = result.replace(Regex("""NUMBER\s*\((\d+)(?:,\s*(\d+))?\)""", RegexOption.IGNORE_CASE)) { match ->
            val precision = match.groupValues[1]
            val scale = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
            if (scale != null) "NUMERIC($precision,$scale)" else "NUMERIC($precision)"
        }
        result = result.replace(Regex("""NUMBER\b(?!\s*\()""", RegexOption.IGNORE_CASE), "NUMERIC")

        // DATE → TIMESTAMP
        result = result.replace(Regex("""\bDATE\b""", RegexOption.IGNORE_CASE), "TIMESTAMP")

        // CLOB → TEXT
        result = result.replace(Regex("""\bCLOB\b""", RegexOption.IGNORE_CASE), "TEXT")

        // BLOB → BYTEA
        result = result.replace(Regex("""\bBLOB\b""", RegexOption.IGNORE_CASE), "BYTEA")

        return result
    }
}
