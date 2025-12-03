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
                val convertedSql = convertCreateTable(statement, targetDialect, warnings, appliedRules, options)
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
                // PostgreSQL LIMIT/OFFSET → Oracle FETCH FIRST
                if (selectBody.limit != null) {
                    val limitExpr = selectBody.limit.rowCount
                    val limitValue = when (limitExpr) {
                        is LongValue -> limitExpr.value
                        else -> limitExpr?.toString()?.toLongOrNull() ?: 10L
                    }

                    // FETCH 구문 생성 (Oracle 12c+)
                    val fetch = Fetch()
                    fetch.rowCount = limitValue
                    fetch.isFetchParamFirst = true
                    fetch.fetchParam = "ROWS"

                    if (selectBody.offset != null) {
                        selectBody.offset.offsetParam = "ROWS"
                        appliedRules.add("OFFSET 구문 유지")
                    }

                    // LIMIT 제거하고 FETCH로 교체
                    selectBody.limit = null
                    selectBody.fetch = fetch

                    appliedRules.add("LIMIT → FETCH FIRST 변환 완료")
                }
            }
            DialectType.TIBERO -> {
                // PostgreSQL LIMIT/OFFSET → Tibero FETCH FIRST
                if (selectBody.limit != null) {
                    val limitExpr = selectBody.limit.rowCount
                    val limitValue = when (limitExpr) {
                        is LongValue -> limitExpr.value
                        else -> limitExpr?.toString()?.toLongOrNull() ?: 10L
                    }

                    // FETCH 구문 생성 (Tibero는 Oracle 호환)
                    val fetch = Fetch()
                    fetch.rowCount = limitValue
                    fetch.isFetchParamFirst = true
                    fetch.fetchParam = "ROWS"

                    if (selectBody.offset != null) {
                        selectBody.offset.offsetParam = "ROWS"
                        appliedRules.add("OFFSET 구문 유지")
                    }

                    // LIMIT 제거하고 FETCH로 교체
                    selectBody.limit = null
                    selectBody.fetch = fetch

                    appliedRules.add("LIMIT → FETCH FIRST 변환 완료")
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
                        function.name = "DATE_FORMAT"
                        warnings.add(createWarning(
                            type = WarningType.SYNTAX_DIFFERENCE,
                            message = "PostgreSQL TO_CHAR() 포맷 문자열이 MySQL DATE_FORMAT() 포맷과 다를 수 있습니다.",
                            severity = WarningSeverity.WARNING,
                            suggestion = "포맷 문자열을 MySQL 형식으로 변경하세요. (예: 'YYYY-MM-DD' → '%Y-%m-%d')"
                        ))
                        appliedRules.add("TO_CHAR() → DATE_FORMAT() 변환 완료")
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

    /**
     * PostgreSQL CREATE TABLE을 다른 방언으로 변환
     */
    private fun convertCreateTable(
        createTable: CreateTable,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                convertCreateTableToOracle(createTable, targetDialect, warnings, appliedRules, options)
            }
            DialectType.MYSQL -> {
                convertCreateTableToMySql(createTable, warnings, appliedRules)
            }
            else -> createTable.toString()
        }
    }

    /**
     * PostgreSQL CREATE TABLE을 Oracle/Tibero DDL 형식으로 변환
     */
    private fun convertCreateTableToOracle(
        createTable: CreateTable,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val schemaOwner = options?.oracleSchemaOwner ?: "SCHEMA_OWNER"
        val tablespace = options?.oracleTablespace ?: "TABLESPACE_NAME"
        val indexspace = options?.oracleIndexspace ?: "INDEXSPACE_NAME"
        val separatePk = options?.separatePrimaryKey ?: true
        val separateComments = options?.separateComments ?: true
        val generateIndex = options?.generateIndex ?: true

        val result = StringBuilder()
        val columnComments = mutableListOf<Triple<String, String, String>>()
        var primaryKeyColumns: List<String>? = null
        var primaryKeyName: String? = null

        // 테이블명 추출
        val rawTableName = createTable.table.name.trim('"')
        val fullTableName = "\"$schemaOwner\".\"$rawTableName\""

        // PRIMARY KEY 정보 추출
        createTable.indexes?.forEach { index ->
            if (index.type?.uppercase() == "PRIMARY KEY" || index.toString().uppercase().contains("PRIMARY KEY")) {
                primaryKeyColumns = index.columnsNames?.map { it.trim('"') }
                primaryKeyName = "PK_${rawTableName.removePrefix("TB_").removePrefix("T_")}"
            }
        }

        // CREATE TABLE 시작
        result.appendLine("CREATE TABLE $fullTableName")
        result.appendLine("(")

        // 컬럼 정의 변환
        val columnDefs = mutableListOf<String>()
        createTable.columnDefinitions?.forEach { colDef ->
            val columnName = colDef.columnName.trim('"')
            val oracleColumnName = "\"$columnName\""

            // 데이터 타입 변환
            val pgType = colDef.colDataType?.dataType?.uppercase() ?: "VARCHAR"
            val typeArgs = colDef.colDataType?.argumentsStringList

            val oracleType = convertPostgreSqlTypeToOracle(pgType, typeArgs)

            // NOT NULL 등 제약조건 추출
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
                    specStr == "PRIMARY" || specStr == "KEY" -> {}
                    else -> {}
                }
            }

            val constraintStr = if (constraints.isNotEmpty()) " ${constraints.joinToString(" ")}" else ""
            columnDefs.add("    $oracleColumnName $oracleType$constraintStr")
        }

        result.appendLine(columnDefs.joinToString(",\n"))
        result.append(") TABLESPACE \"$tablespace\"")
        result.appendLine(";")

        appliedRules.add("CREATE TABLE PostgreSQL → Oracle 형식으로 변환")
        appliedRules.add("데이터 타입 Oracle 형식으로 변환")

        // PRIMARY KEY 인덱스 및 제약조건 생성
        if (separatePk && primaryKeyColumns != null && primaryKeyColumns!!.isNotEmpty()) {
            result.appendLine()

            val pkColumnsQuoted = primaryKeyColumns!!.joinToString(", ") { "\"$it\"" }

            if (generateIndex) {
                result.appendLine("CREATE UNIQUE INDEX \"$schemaOwner\".\"$primaryKeyName\" ON \"$schemaOwner\".\"$rawTableName\" ($pkColumnsQuoted)")
                result.appendLine("    TABLESPACE \"$indexspace\" ;")
                result.appendLine()
            }

            result.appendLine("ALTER TABLE \"$schemaOwner\".\"$rawTableName\" ADD CONSTRAINT \"$primaryKeyName\" PRIMARY KEY ($pkColumnsQuoted)")
            if (generateIndex) {
                result.appendLine("    USING INDEX \"$schemaOwner\".\"$primaryKeyName\" ENABLE;")
            } else {
                result.appendLine("    ENABLE;")
            }

            appliedRules.add("PRIMARY KEY를 별도 ALTER TABLE로 분리")
        }

        return result.toString().trim()
    }

    /**
     * PostgreSQL CREATE TABLE을 MySQL 형식으로 변환
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
            val pgType = colDef.colDataType?.dataType?.uppercase() ?: "VARCHAR"
            val typeArgs = colDef.colDataType?.argumentsStringList

            val mysqlType = convertPostgreSqlTypeToMySql(pgType, typeArgs, warnings)

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

        appliedRules.add("CREATE TABLE PostgreSQL → MySQL 형식으로 변환")
        appliedRules.add("큰따옴표(\") → 백틱(`) 변환")
        appliedRules.add("데이터 타입 MySQL 형식으로 변환")

        return result.toString()
    }

    /**
     * PostgreSQL 데이터 타입을 Oracle 데이터 타입으로 변환
     */
    private fun convertPostgreSqlTypeToOracle(pgType: String, args: List<String>?): String {
        val precision = args?.firstOrNull()?.toIntOrNull()
        val scale = args?.getOrNull(1)?.toIntOrNull()

        return when (pgType.uppercase()) {
            "SERIAL" -> "NUMBER GENERATED BY DEFAULT AS IDENTITY"
            "BIGSERIAL" -> "NUMBER GENERATED BY DEFAULT AS IDENTITY"
            "SMALLSERIAL" -> "NUMBER(5) GENERATED BY DEFAULT AS IDENTITY"
            "INTEGER", "INT", "INT4" -> "NUMBER(10)"
            "BIGINT", "INT8" -> "NUMBER(19)"
            "SMALLINT", "INT2" -> "NUMBER(5)"
            "NUMERIC", "DECIMAL" -> {
                when {
                    precision != null && scale != null -> "NUMBER($precision,$scale)"
                    precision != null -> "NUMBER($precision)"
                    else -> "NUMBER"
                }
            }
            "REAL", "FLOAT4" -> "BINARY_FLOAT"
            "DOUBLE PRECISION", "FLOAT8" -> "BINARY_DOUBLE"
            "VARCHAR", "CHARACTER VARYING" -> {
                val size = precision ?: 255
                "VARCHAR2($size BYTE)"
            }
            "CHAR", "CHARACTER" -> {
                val size = precision ?: 1
                "CHAR($size BYTE)"
            }
            "TEXT" -> "CLOB"
            "BYTEA" -> "BLOB"
            "BOOLEAN", "BOOL" -> "NUMBER(1)"
            "DATE" -> "DATE"
            "TIME" -> "TIMESTAMP"
            "TIMESTAMP", "TIMESTAMPTZ" -> if (precision != null) "TIMESTAMP($precision)" else "TIMESTAMP"
            "UUID" -> "RAW(16)"
            "JSON", "JSONB" -> "CLOB"
            "INTERVAL" -> "VARCHAR2(50 BYTE)"
            else -> pgType
        }
    }

    /**
     * PostgreSQL 데이터 타입을 MySQL 데이터 타입으로 변환
     */
    private fun convertPostgreSqlTypeToMySql(
        pgType: String,
        args: List<String>?,
        warnings: MutableList<ConversionWarning>
    ): String {
        val precision = args?.firstOrNull()?.toIntOrNull()
        val scale = args?.getOrNull(1)?.toIntOrNull()

        return when (pgType.uppercase()) {
            "SERIAL" -> {
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "PostgreSQL SERIAL은 MySQL AUTO_INCREMENT로 변환됩니다.",
                    severity = WarningSeverity.INFO,
                    suggestion = "INT AUTO_INCREMENT를 사용하세요."
                ))
                "INT AUTO_INCREMENT"
            }
            "BIGSERIAL" -> "BIGINT AUTO_INCREMENT"
            "SMALLSERIAL" -> "SMALLINT AUTO_INCREMENT"
            "INTEGER", "INT", "INT4" -> "INT"
            "BIGINT", "INT8" -> "BIGINT"
            "SMALLINT", "INT2" -> "SMALLINT"
            "NUMERIC", "DECIMAL" -> {
                when {
                    precision != null && scale != null -> "DECIMAL($precision,$scale)"
                    precision != null -> "DECIMAL($precision)"
                    else -> "DECIMAL"
                }
            }
            "REAL", "FLOAT4" -> "FLOAT"
            "DOUBLE PRECISION", "FLOAT8" -> "DOUBLE"
            "VARCHAR", "CHARACTER VARYING" -> {
                val size = precision ?: 255
                "VARCHAR($size)"
            }
            "CHAR", "CHARACTER" -> {
                val size = precision ?: 1
                "CHAR($size)"
            }
            "TEXT" -> "LONGTEXT"
            "BYTEA" -> "LONGBLOB"
            "BOOLEAN", "BOOL" -> "BOOLEAN"
            "DATE" -> "DATE"
            "TIME" -> "TIME"
            "TIMESTAMP", "TIMESTAMPTZ" -> if (precision != null && precision > 0) "DATETIME($precision)" else "DATETIME"
            "UUID" -> "CHAR(36)"
            "JSON", "JSONB" -> "JSON"
            "INTERVAL" -> "VARCHAR(50)"
            else -> pgType
        }
    }

    // ==================== Phase 3: 고급 SQL 기능 변환 ====================

    /**
     * PostgreSQL 함수 기반 인덱스를 다른 방언으로 변환
     * 예: CREATE INDEX idx_name ON table (LOWER(column))
     */
    fun convertFunctionBasedIndex(
        indexSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val indexInfo = extractFunctionBasedIndexInfo(indexSql)
        val schemaOwner = options?.oracleSchemaOwner ?: "SCHEMA_OWNER"

        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                appliedRules.add("PostgreSQL 함수 기반 인덱스 → Oracle/Tibero 형식으로 변환")
                indexInfo.toOracle(schemaOwner)
            }
            DialectType.MYSQL -> {
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "MySQL 8.0 미만에서는 함수 기반 인덱스가 지원되지 않습니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "MySQL 8.0 이상을 사용하거나 Generated Column을 사용하세요."
                ))
                appliedRules.add("PostgreSQL 함수 기반 인덱스 → MySQL 형식으로 변환")
                indexInfo.toMySql()
            }
            DialectType.POSTGRESQL -> indexSql
        }
    }

    /**
     * PostgreSQL 함수 기반 인덱스 정보 추출
     */
    private fun extractFunctionBasedIndexInfo(sql: String): FunctionBasedIndexInfo {
        val upperSql = sql.uppercase()
        val isUnique = upperSql.contains("CREATE UNIQUE INDEX")

        // 인덱스명 추출
        val indexNameRegex = if (isUnique) {
            """CREATE\s+UNIQUE\s+INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:CONCURRENTLY\s+)?(\w+)""".toRegex(RegexOption.IGNORE_CASE)
        } else {
            """CREATE\s+INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:CONCURRENTLY\s+)?(\w+)""".toRegex(RegexOption.IGNORE_CASE)
        }
        val indexName = indexNameRegex.find(sql)?.groupValues?.get(1) ?: "UNKNOWN_INDEX"

        // 테이블명 추출
        val tableNameRegex = """ON\s+(\w+)""".toRegex(RegexOption.IGNORE_CASE)
        val tableName = tableNameRegex.find(sql)?.groupValues?.get(1) ?: "UNKNOWN_TABLE"

        // 표현식 추출 (괄호 안의 내용)
        val expressionRegex = """ON\s+\w+\s*\((.+)\)""".toRegex(RegexOption.IGNORE_CASE)
        val expressionMatch = expressionRegex.find(sql)
        val expressions = if (expressionMatch != null) {
            parseIndexExpressions(expressionMatch.groupValues[1])
        } else {
            emptyList()
        }

        // 테이블스페이스 추출
        val tablespaceRegex = """TABLESPACE\s+(\w+)""".toRegex(RegexOption.IGNORE_CASE)
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
     * PostgreSQL Materialized View를 다른 방언으로 변환
     */
    fun convertMaterializedView(
        mvSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val mvInfo = extractMaterializedViewInfo(mvSql)
        val schemaOwner = options?.oracleSchemaOwner ?: "SCHEMA_OWNER"

        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                appliedRules.add("PostgreSQL Materialized View → Oracle/Tibero 형식으로 변환")
                mvInfo.toOracle(schemaOwner)
            }
            DialectType.MYSQL -> {
                warnings.add(createWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "MySQL은 네이티브 Materialized View를 지원하지 않습니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "테이블 + 프로시저 + 이벤트 스케줄러로 시뮬레이션됩니다."
                ))
                appliedRules.add("PostgreSQL Materialized View → MySQL 시뮬레이션 (테이블 + 프로시저)")
                mvInfo.toMySql(warnings)
            }
            DialectType.POSTGRESQL -> mvSql
        }
    }

    /**
     * PostgreSQL Materialized View 정보 추출
     */
    private fun extractMaterializedViewInfo(sql: String): MaterializedViewInfo {
        // 뷰명 추출
        val viewNameRegex = """CREATE\s+MATERIALIZED\s+VIEW\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)""".toRegex(RegexOption.IGNORE_CASE)
        val viewName = viewNameRegex.find(sql)?.groupValues?.get(1) ?: "UNKNOWN_MV"

        // SELECT 쿼리 추출
        val selectRegex = """AS\s+(SELECT.+)$""".toRegex(RegexOption.IGNORE_CASE)
        val selectQuery = selectRegex.find(sql)?.groupValues?.get(1)?.trim()?.trimEnd(';') ?: ""

        // WITH DATA / WITH NO DATA 추출
        val buildOption = if (sql.uppercase().contains("WITH NO DATA")) {
            MaterializedViewInfo.BuildOption.DEFERRED
        } else {
            MaterializedViewInfo.BuildOption.IMMEDIATE
        }

        return MaterializedViewInfo(
            viewName = viewName,
            selectQuery = selectQuery,
            buildOption = buildOption,
            refreshOption = MaterializedViewInfo.RefreshOption.COMPLETE
        )
    }

    /**
     * PostgreSQL 파티션 테이블을 다른 방언으로 변환
     */
    fun convertPartitionTable(
        partitionSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val partitionInfo = extractTablePartitionInfo(partitionSql)
        val schemaOwner = options?.oracleSchemaOwner ?: "SCHEMA_OWNER"

        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                appliedRules.add("PostgreSQL 파티션 테이블 → Oracle/Tibero 형식으로 변환")
                partitionInfo.toOraclePartitionClause(schemaOwner)
            }
            DialectType.MYSQL -> {
                appliedRules.add("PostgreSQL 파티션 테이블 → MySQL 형식으로 변환")
                partitionInfo.toMySqlPartitionClause()
            }
            DialectType.POSTGRESQL -> partitionSql
        }
    }

    /**
     * PostgreSQL 파티션 정보 추출
     */
    private fun extractTablePartitionInfo(sql: String): TablePartitionDetailInfo {
        val upperSql = sql.uppercase()

        // 테이블명 추출
        val tableNameRegex = """CREATE\s+TABLE\s+(\w+)""".toRegex(RegexOption.IGNORE_CASE)
        val tableName = tableNameRegex.find(sql)?.groupValues?.get(1) ?: "UNKNOWN_TABLE"

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
            ?.split(",")?.map { it.trim() } ?: emptyList()

        return TablePartitionDetailInfo(
            tableName = tableName,
            partitionType = partitionType,
            partitionColumns = partitionColumns,
            partitions = emptyList()  // PostgreSQL은 파티션을 별도 테이블로 생성
        )
    }

    /**
     * PostgreSQL JSON 함수를 다른 방언으로 변환
     * 예: jsonb_column -> 'key', jsonb_column ->> 'key', json_extract_path
     */
    fun convertJsonFunction(
        jsonSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = jsonSql

        when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                // PostgreSQL -> operator (JSON 객체 반환) → Oracle JSON_QUERY
                val arrowRegex = """(\w+)\s*->\s*'([^']+)'""".toRegex()
                result = arrowRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val key = match.groupValues[2]
                    appliedRules.add("PostgreSQL -> 연산자 → Oracle JSON_QUERY 변환")
                    "JSON_QUERY($column, '\$.$key')"
                }

                // PostgreSQL ->> operator (텍스트 반환) → Oracle JSON_VALUE
                val doubleArrowRegex = """(\w+)\s*->>\s*'([^']+)'""".toRegex()
                result = doubleArrowRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val key = match.groupValues[2]
                    appliedRules.add("PostgreSQL ->> 연산자 → Oracle JSON_VALUE 변환")
                    "JSON_VALUE($column, '\$.$key')"
                }

                // jsonb_extract_path → JSON_QUERY
                val extractPathRegex = """jsonb?_extract_path\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = extractPathRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("PostgreSQL jsonb_extract_path → Oracle JSON_QUERY 변환")
                    "JSON_QUERY($column, '\$.$path')"
                }

                // jsonb_extract_path_text → JSON_VALUE
                val extractPathTextRegex = """jsonb?_extract_path_text\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = extractPathTextRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("PostgreSQL jsonb_extract_path_text → Oracle JSON_VALUE 변환")
                    "JSON_VALUE($column, '\$.$path')"
                }

                // jsonb_array_length → JSON_VALUE with size()
                val arrayLengthRegex = """jsonb?_array_length\s*\(\s*(\w+)\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = arrayLengthRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    appliedRules.add("PostgreSQL jsonb_array_length → Oracle JSON_VALUE 변환")
                    "JSON_VALUE($column, '\$.size()')"
                }

                // @> (contains) → JSON_EXISTS
                val containsRegex = """(\w+)\s*@>\s*'([^']+)'""".toRegex()
                result = containsRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val value = match.groupValues[2]
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "PostgreSQL @> 연산자의 완전한 변환은 Oracle에서 복잡할 수 있습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "JSON_EXISTS 또는 JSON_VALUE로 확인하세요."
                    ))
                    appliedRules.add("PostgreSQL @> 연산자 → Oracle JSON_EXISTS 변환")
                    "JSON_EXISTS($column, '\$[*]?(@ == $value)')"
                }
            }
            DialectType.MYSQL -> {
                // PostgreSQL -> operator → MySQL JSON_EXTRACT
                val arrowRegex = """(\w+)\s*->\s*'([^']+)'""".toRegex()
                result = arrowRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val key = match.groupValues[2]
                    appliedRules.add("PostgreSQL -> 연산자 → MySQL JSON_EXTRACT 변환")
                    "JSON_EXTRACT($column, '\$.$key')"
                }

                // PostgreSQL ->> operator → MySQL JSON_UNQUOTE(JSON_EXTRACT())
                val doubleArrowRegex = """(\w+)\s*->>\s*'([^']+)'""".toRegex()
                result = doubleArrowRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val key = match.groupValues[2]
                    appliedRules.add("PostgreSQL ->> 연산자 → MySQL JSON_UNQUOTE(JSON_EXTRACT()) 변환")
                    "JSON_UNQUOTE(JSON_EXTRACT($column, '\$.$key'))"
                }

                // jsonb_extract_path → JSON_EXTRACT
                val extractPathRegex = """jsonb?_extract_path\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = extractPathRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("PostgreSQL jsonb_extract_path → MySQL JSON_EXTRACT 변환")
                    "JSON_EXTRACT($column, '\$.$path')"
                }

                // jsonb_array_length → JSON_LENGTH
                val arrayLengthRegex = """jsonb?_array_length\s*\(\s*(\w+)\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = arrayLengthRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    appliedRules.add("PostgreSQL jsonb_array_length → MySQL JSON_LENGTH 변환")
                    "JSON_LENGTH($column)"
                }

                // @> (contains) → JSON_CONTAINS
                val containsRegex = """(\w+)\s*@>\s*'([^']+)'""".toRegex()
                result = containsRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val value = match.groupValues[2]
                    appliedRules.add("PostgreSQL @> 연산자 → MySQL JSON_CONTAINS 변환")
                    "JSON_CONTAINS($column, '$value')"
                }
            }
            DialectType.POSTGRESQL -> { /* 변환 불필요 */ }
        }

        return result
    }

    /**
     * PostgreSQL 정규식 함수를 다른 방언으로 변환
     * 예: column ~ 'pattern', regexp_matches, regexp_replace
     */
    fun convertRegexFunction(
        regexSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = regexSql

        when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                // PostgreSQL ~ (match) → Oracle REGEXP_LIKE
                val matchRegex = """(\w+)\s*~\s*'([^']+)'""".toRegex()
                result = matchRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    appliedRules.add("PostgreSQL ~ 연산자 → Oracle REGEXP_LIKE 변환")
                    "REGEXP_LIKE($column, '$pattern')"
                }

                // PostgreSQL ~* (case-insensitive match) → Oracle REGEXP_LIKE with 'i'
                val matchCaseInsensitiveRegex = """(\w+)\s*~\*\s*'([^']+)'""".toRegex()
                result = matchCaseInsensitiveRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    appliedRules.add("PostgreSQL ~* 연산자 → Oracle REGEXP_LIKE (대소문자 무시) 변환")
                    "REGEXP_LIKE($column, '$pattern', 'i')"
                }

                // PostgreSQL !~ (not match) → Oracle NOT REGEXP_LIKE
                val notMatchRegex = """(\w+)\s*!~\s*'([^']+)'""".toRegex()
                result = notMatchRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    appliedRules.add("PostgreSQL !~ 연산자 → Oracle NOT REGEXP_LIKE 변환")
                    "NOT REGEXP_LIKE($column, '$pattern')"
                }

                // regexp_matches → REGEXP_SUBSTR (참고: 결과 형식이 다름)
                val regexpMatchesRegex = """regexp_matches?\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpMatchesRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "PostgreSQL regexp_matches는 배열을 반환하지만 Oracle REGEXP_SUBSTR은 문자열을 반환합니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "반환값 처리 로직을 확인하세요."
                    ))
                    appliedRules.add("PostgreSQL regexp_matches → Oracle REGEXP_SUBSTR 변환")
                    "REGEXP_SUBSTR($column, '$pattern')"
                }

                // regexp_replace → REGEXP_REPLACE (동일 이름)
                val regexpReplaceRegex = """regexp_replace\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*,\s*'([^']*)'\s*(?:,\s*'([^']*)'\s*)?\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpReplaceRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    val replacement = match.groupValues[3]
                    val flags = match.groupValues.getOrNull(4) ?: ""
                    appliedRules.add("PostgreSQL regexp_replace → Oracle REGEXP_REPLACE 변환")
                    if (flags.isNotEmpty()) {
                        "REGEXP_REPLACE($column, '$pattern', '$replacement', 1, 0, '$flags')"
                    } else {
                        "REGEXP_REPLACE($column, '$pattern', '$replacement')"
                    }
                }

                // substring with pattern → REGEXP_SUBSTR
                val substringRegex = """substring\s*\(\s*(\w+)\s+from\s+'([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = substringRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    appliedRules.add("PostgreSQL substring (정규식) → Oracle REGEXP_SUBSTR 변환")
                    "REGEXP_SUBSTR($column, '$pattern')"
                }
            }
            DialectType.MYSQL -> {
                // PostgreSQL ~ (match) → MySQL REGEXP
                val matchRegex = """(\w+)\s*~\s*'([^']+)'""".toRegex()
                result = matchRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    appliedRules.add("PostgreSQL ~ 연산자 → MySQL REGEXP 변환")
                    "$column REGEXP '$pattern'"
                }

                // PostgreSQL ~* (case-insensitive) → MySQL REGEXP (기본적으로 대소문자 무시)
                val matchCaseInsensitiveRegex = """(\w+)\s*~\*\s*'([^']+)'""".toRegex()
                result = matchCaseInsensitiveRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    appliedRules.add("PostgreSQL ~* 연산자 → MySQL REGEXP 변환")
                    "$column REGEXP '$pattern'"
                }

                // PostgreSQL !~ (not match) → MySQL NOT REGEXP
                val notMatchRegex = """(\w+)\s*!~\s*'([^']+)'""".toRegex()
                result = notMatchRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    appliedRules.add("PostgreSQL !~ 연산자 → MySQL NOT REGEXP 변환")
                    "$column NOT REGEXP '$pattern'"
                }

                // regexp_matches → REGEXP_SUBSTR (MySQL 8.0+)
                val regexpMatchesRegex = """regexp_matches?\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpMatchesRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "MySQL REGEXP_SUBSTR은 8.0 이상에서만 지원됩니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "MySQL 8.0 이상을 사용하세요."
                    ))
                    appliedRules.add("PostgreSQL regexp_matches → MySQL REGEXP_SUBSTR 변환")
                    "REGEXP_SUBSTR($column, '$pattern')"
                }

                // regexp_replace → REGEXP_REPLACE (MySQL 8.0+)
                val regexpReplaceRegex = """regexp_replace\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*,\s*'([^']*)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpReplaceRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    val replacement = match.groupValues[3]
                    appliedRules.add("PostgreSQL regexp_replace → MySQL REGEXP_REPLACE 변환")
                    "REGEXP_REPLACE($column, '$pattern', '$replacement')"
                }
            }
            DialectType.POSTGRESQL -> { /* 변환 불필요 */ }
        }

        return result
    }

    /**
     * JSON 함수 정보 추출
     */
    fun extractJsonFunctionInfo(jsonFuncStr: String): JsonFunctionInfo? {
        // -> 연산자
        val arrowRegex = """(\w+)\s*->\s*'([^']+)'""".toRegex()
        arrowRegex.find(jsonFuncStr)?.let { match ->
            return JsonFunctionInfo(
                functionType = JsonFunctionInfo.JsonFunctionType.QUERY,
                jsonExpression = match.groupValues[1],
                path = match.groupValues[2]
            )
        }

        // ->> 연산자
        val doubleArrowRegex = """(\w+)\s*->>\s*'([^']+)'""".toRegex()
        doubleArrowRegex.find(jsonFuncStr)?.let { match ->
            return JsonFunctionInfo(
                functionType = JsonFunctionInfo.JsonFunctionType.EXTRACT,
                jsonExpression = match.groupValues[1],
                path = match.groupValues[2]
            )
        }

        // jsonb_array_length
        val arrayLengthRegex = """jsonb?_array_length\s*\(\s*(\w+)\s*\)""".toRegex(RegexOption.IGNORE_CASE)
        arrayLengthRegex.find(jsonFuncStr)?.let { match ->
            return JsonFunctionInfo(
                functionType = JsonFunctionInfo.JsonFunctionType.ARRAY_LENGTH,
                jsonExpression = match.groupValues[1],
                path = null
            )
        }

        return null
    }

    /**
     * 정규식 함수 정보 추출
     */
    fun extractRegexFunctionInfo(regexFuncStr: String): RegexFunctionInfo? {
        // ~ 연산자
        val matchRegex = """(\w+)\s*~\s*'([^']+)'""".toRegex()
        matchRegex.find(regexFuncStr)?.let { match ->
            return RegexFunctionInfo(
                functionType = RegexFunctionInfo.RegexFunctionType.LIKE,
                sourceExpression = match.groupValues[1],
                pattern = match.groupValues[2]
            )
        }

        // regexp_replace
        val replaceRegex = """regexp_replace\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*,\s*'([^']*)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
        replaceRegex.find(regexFuncStr)?.let { match ->
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
     * PostgreSQL 트리거를 다른 방언으로 변환
     */
    fun convertTrigger(
        triggerSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val triggerInfo = extractTriggerInfo(triggerSql)
        val schemaOwner = options?.oracleSchemaOwner ?: "SCHEMA_OWNER"

        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                appliedRules.add("PostgreSQL 트리거 → Oracle/Tibero 형식으로 변환")
                convertTriggerToOracle(triggerInfo, schemaOwner, warnings, appliedRules)
            }
            DialectType.MYSQL -> {
                appliedRules.add("PostgreSQL 트리거 → MySQL 형식으로 변환")
                convertTriggerToMySql(triggerInfo, warnings, appliedRules)
            }
            DialectType.POSTGRESQL -> triggerSql
        }
    }

    /**
     * PostgreSQL 트리거 SQL에서 TriggerInfo 추출
     *
     * PostgreSQL 트리거 구문:
     * CREATE TRIGGER trigger_name
     *   { BEFORE | AFTER | INSTEAD OF } { INSERT | UPDATE | DELETE } [ OR ... ]
     *   ON table_name
     *   [ FOR [ EACH ] { ROW | STATEMENT } ]
     *   [ WHEN ( condition ) ]
     *   EXECUTE { FUNCTION | PROCEDURE } function_name ( arguments )
     */
    private fun extractTriggerInfo(triggerSql: String): TriggerInfo {
        val upperSql = triggerSql.uppercase()

        // 트리거명 추출
        val triggerNameRegex = """CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+(\w+)""".toRegex(RegexOption.IGNORE_CASE)
        val triggerName = triggerNameRegex.find(triggerSql)?.groupValues?.get(1) ?: "UNKNOWN_TRIGGER"

        // 테이블명 추출
        val tableNameRegex = """ON\s+(\w+)""".toRegex(RegexOption.IGNORE_CASE)
        val tableName = tableNameRegex.find(triggerSql)?.groupValues?.get(1) ?: "UNKNOWN_TABLE"

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

        // FOR EACH ROW / STATEMENT 추출
        val forEachRow = !upperSql.contains("FOR EACH STATEMENT")

        // WHEN 조건 추출
        val whenRegex = """WHEN\s*\((.+?)\)\s*EXECUTE""".toRegex(RegexOption.IGNORE_CASE)
        val whenCondition = whenRegex.find(triggerSql)?.groupValues?.get(1)?.trim()

        // 함수명 및 본문 추출 (PostgreSQL은 트리거 함수를 별도로 정의)
        val functionRegex = """EXECUTE\s+(?:FUNCTION|PROCEDURE)\s+(\w+)\s*\(([^)]*)\)""".toRegex(RegexOption.IGNORE_CASE)
        val functionMatch = functionRegex.find(triggerSql)
        val functionName = functionMatch?.groupValues?.get(1) ?: ""
        val functionArgs = functionMatch?.groupValues?.get(2) ?: ""

        // 본문은 EXECUTE FUNCTION/PROCEDURE 호출로 설정
        val body = if (functionName.isNotEmpty()) {
            "CALL $functionName($functionArgs)"
        } else {
            ""
        }

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
     * PostgreSQL 트리거를 Oracle/Tibero 형식으로 변환
     */
    private fun convertTriggerToOracle(
        triggerInfo: TriggerInfo,
        schemaOwner: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()

        // CREATE OR REPLACE TRIGGER
        result.append("CREATE OR REPLACE TRIGGER \"$schemaOwner\".\"${triggerInfo.name}\"\n")

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

        result.append("    $timingStr $eventsStr\n")
        result.append("    ON \"$schemaOwner\".\"${triggerInfo.tableName}\"\n")

        // REFERENCING 절
        if (triggerInfo.referencing != null) {
            val refParts = mutableListOf<String>()
            triggerInfo.referencing.oldAlias?.let { refParts.add("OLD AS $it") }
            triggerInfo.referencing.newAlias?.let { refParts.add("NEW AS $it") }
            if (refParts.isNotEmpty()) {
                result.append("    REFERENCING ${refParts.joinToString(" ")}\n")
            }
        }

        // FOR EACH ROW
        if (triggerInfo.forEachRow) {
            result.append("    FOR EACH ROW\n")
        }

        // WHEN 조건
        if (!triggerInfo.whenCondition.isNullOrBlank()) {
            result.append("    WHEN (${triggerInfo.whenCondition})\n")
        }

        // 트리거 본문
        if (triggerInfo.body.isNotEmpty()) {
            result.append("BEGIN\n")
            // PostgreSQL의 CALL 문을 Oracle PL/SQL로 변환
            if (triggerInfo.body.startsWith("CALL ")) {
                val procedureCall = triggerInfo.body.removePrefix("CALL ").trimEnd(';')
                result.append("    $procedureCall;\n")
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "PostgreSQL 트리거 함수 '$procedureCall'를 Oracle 프로시저로 변환해야 합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "해당 트리거 함수를 Oracle PL/SQL 프로시저로 마이그레이션하세요."
                ))
            } else {
                result.append("    ${triggerInfo.body}\n")
            }
            result.append("END;\n")
        } else {
            result.append("BEGIN\n")
            result.append("    NULL; -- 트리거 본문을 추가하세요\n")
            result.append("END;\n")
        }

        result.append("/")

        appliedRules.add("트리거 타이밍: $timingStr")
        appliedRules.add("트리거 이벤트: $eventsStr")

        return result.toString()
    }

    /**
     * PostgreSQL 트리거를 MySQL 형식으로 변환
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
        for ((index, event) in triggerInfo.events.withIndex()) {
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
                val condition = convertPostgreSqlConditionToMySql(triggerInfo.whenCondition)
                result.append("    IF $condition THEN\n")

                if (triggerInfo.body.isNotEmpty()) {
                    val mysqlBody = convertPostgreSqlBodyToMySql(triggerInfo.body)
                    result.append("        $mysqlBody\n")
                }

                result.append("    END IF;\n")
            } else if (triggerInfo.body.isNotEmpty()) {
                val mysqlBody = convertPostgreSqlBodyToMySql(triggerInfo.body)
                result.append("    $mysqlBody\n")
            } else {
                result.append("    -- 트리거 본문을 추가하세요\n")
            }

            result.append("END //\n\n")
            result.append("DELIMITER ;")

            results.add(result.toString())
        }

        appliedRules.add("PostgreSQL FOR EACH ROW → MySQL FOR EACH ROW")

        return results.joinToString("\n\n")
    }

    /**
     * PostgreSQL 조건문을 MySQL 형식으로 변환
     */
    private fun convertPostgreSqlConditionToMySql(condition: String): String {
        var result = condition

        // OLD. → OLD. (동일)
        // NEW. → NEW. (동일)

        // PostgreSQL specific: IS DISTINCT FROM → <>
        result = result.replace(Regex("""IS\s+DISTINCT\s+FROM""", RegexOption.IGNORE_CASE), "<>")

        // PostgreSQL specific: IS NOT DISTINCT FROM → =
        result = result.replace(Regex("""IS\s+NOT\s+DISTINCT\s+FROM""", RegexOption.IGNORE_CASE), "=")

        return result
    }

    /**
     * PostgreSQL 트리거 본문을 MySQL 형식으로 변환
     */
    private fun convertPostgreSqlBodyToMySql(body: String): String {
        var result = body

        // CALL → CALL (동일)
        // RAISE NOTICE → SELECT (디버깅용)
        result = result.replace(Regex("""RAISE\s+NOTICE\s+'([^']*)'""", RegexOption.IGNORE_CASE)) { match ->
            "SELECT '${match.groupValues[1]}'"
        }

        // RETURN NEW/OLD 제거 (MySQL에서는 필요 없음)
        result = result.replace(Regex("""RETURN\s+(NEW|OLD);?""", RegexOption.IGNORE_CASE), "")

        return result.trim()
    }
}
