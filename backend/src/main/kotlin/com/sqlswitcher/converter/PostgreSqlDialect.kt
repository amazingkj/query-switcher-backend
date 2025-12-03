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
}
