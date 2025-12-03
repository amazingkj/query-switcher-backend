package com.sqlswitcher.converter

import com.sqlswitcher.parser.model.AstAnalysisResult
import com.sqlswitcher.model.ConversionOptions
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.statement.select.Limit
import net.sf.jsqlparser.statement.select.Offset
import net.sf.jsqlparser.statement.select.Top
import net.sf.jsqlparser.statement.select.Fetch
import net.sf.jsqlparser.statement.select.SelectItem
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.create.table.ColumnDefinition
import net.sf.jsqlparser.statement.create.table.Index
import net.sf.jsqlparser.expression.Function as SqlFunction
import net.sf.jsqlparser.expression.StringValue
import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.expression.CastExpression
import net.sf.jsqlparser.expression.LongValue
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
                val convertedSql = convertCreateTableToOracle(statement, targetDialect, warnings, appliedRules, options)
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
                // MySQL LIMIT/OFFSET → Tibero FETCH FIRST
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

    /**
     * MySQL CREATE TABLE을 Oracle DDL 형식으로 변환
     * - 백틱(`) → 큰따옴표(") 변환
     * - 데이터 타입 변환 (varchar → VARCHAR2, int → NUMBER 등)
     * - COMMENT를 별도 COMMENT ON 문으로 분리
     * - ENGINE, CHARSET 등 MySQL 전용 옵션 제거
     * - 스키마/TABLESPACE 지정
     * - PRIMARY KEY를 별도 ALTER TABLE/INDEX로 분리
     */
    private fun convertCreateTableToOracle(
        createTable: CreateTable,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        if (targetDialect != DialectType.ORACLE && targetDialect != DialectType.TIBERO) {
            // Oracle/Tibero가 아닌 경우 기본 변환
            return createTable.toString()
        }

        val schemaOwner = options?.oracleSchemaOwner ?: "SCHEMA_OWNER"
        val tablespace = options?.oracleTablespace ?: "TABLESPACE_NAME"
        val indexspace = options?.oracleIndexspace ?: "INDEXSPACE_NAME"
        val separatePk = options?.separatePrimaryKey ?: true
        val separateComments = options?.separateComments ?: true
        val generateIndex = options?.generateIndex ?: true

        val result = StringBuilder()
        val columnComments = mutableListOf<Triple<String, String, String>>() // table, column, comment
        var tableComment: String? = null
        var primaryKeyColumns: List<String>? = null
        var primaryKeyName: String? = null

        // 테이블명 추출 및 변환 (백틱 제거)
        val rawTableName = createTable.table.name.trim('`', '"')
        val fullTableName = "\"$schemaOwner\".\"$rawTableName\""

        // 테이블 COMMENT 추출 (MySQL의 COMMENT 'xxx' 구문에서)
        createTable.tableOptionsStrings?.forEach { optStr ->
            val opt = optStr.toString()
            val commentMatch = Regex("COMMENT\\s*[=]?\\s*'([^']*)'", RegexOption.IGNORE_CASE).find(opt)
            if (commentMatch != null) {
                tableComment = commentMatch.groupValues[1]
            }
        }

        // PRIMARY KEY 정보 추출
        createTable.indexes?.forEach { index ->
            if (index.type?.uppercase() == "PRIMARY KEY" || index.toString().uppercase().contains("PRIMARY KEY")) {
                primaryKeyColumns = index.columnsNames?.map { it.trim('`', '"') }
                primaryKeyName = "PK_${rawTableName.removePrefix("TB_").removePrefix("T_")}"
            }
        }

        // CREATE TABLE 시작
        result.appendLine("CREATE TABLE $fullTableName")
        result.appendLine("(")

        // 컬럼 정의 변환
        val columnDefs = mutableListOf<String>()
        createTable.columnDefinitions?.forEach { colDef ->
            val columnName = colDef.columnName.trim('`', '"')
            val oracleColumnName = "\"$columnName\""

            // 데이터 타입 변환
            val mysqlType = colDef.colDataType?.dataType?.uppercase() ?: "VARCHAR"
            val typeArgs = colDef.colDataType?.argumentsStringList

            val oracleType = convertMySqlTypeToOracleWithPrecision(mysqlType, typeArgs)

            // NOT NULL, DEFAULT 등 제약조건 추출
            val constraints = mutableListOf<String>()
            var columnComment: String? = null

            colDef.columnSpecs?.forEach { spec ->
                val specStr = spec.toString().uppercase()
                when {
                    specStr == "NOT" -> {} // NOT NULL의 NOT 부분
                    specStr == "NULL" && constraints.lastOrNull() == "NOT" -> {
                        constraints.remove("NOT")
                        constraints.add("NOT NULL")
                    }
                    specStr == "NULL" -> {} // nullable (Oracle에서는 기본값)
                    specStr.startsWith("DEFAULT") -> constraints.add(spec.toString())
                    specStr.startsWith("COMMENT") -> {
                        // COMMENT 'xxx' 형식에서 주석 추출
                        val match = Regex("COMMENT\\s*'([^']*)'", RegexOption.IGNORE_CASE).find(spec.toString())
                        columnComment = match?.groupValues?.get(1)
                    }
                    specStr == "AUTO_INCREMENT" -> {
                        warnings.add(createWarning(
                            type = WarningType.SYNTAX_DIFFERENCE,
                            message = "AUTO_INCREMENT는 Oracle에서 SEQUENCE로 변환해야 합니다.",
                            severity = WarningSeverity.WARNING,
                            suggestion = "SEQUENCE와 TRIGGER를 생성하거나 IDENTITY 컬럼을 사용하세요."
                        ))
                    }
                    specStr == "PRIMARY" || specStr == "KEY" -> {} // 별도 처리
                    else -> {
                        if (specStr != "NOT") {
                            // constraints.add(spec.toString())
                        }
                    }
                }
            }

            // 컬럼 정의 생성
            val constraintStr = if (constraints.isNotEmpty()) " ${constraints.joinToString(" ")}" else ""
            columnDefs.add("    $oracleColumnName $oracleType$constraintStr")

            // 컬럼 주석 저장
            if (columnComment != null && separateComments) {
                columnComments.add(Triple(rawTableName, columnName, columnComment!!))
            }
        }

        result.appendLine(columnDefs.joinToString(",\n"))
        result.append(") TABLESPACE \"$tablespace\"")

        // 세미콜론 추가
        result.appendLine(";")

        appliedRules.add("CREATE TABLE 구문 Oracle 형식으로 변환")
        appliedRules.add("백틱(`) → 큰따옴표(\") 변환")
        appliedRules.add("데이터 타입 Oracle 형식으로 변환")
        appliedRules.add("ENGINE, CHARSET 등 MySQL 전용 옵션 제거")

        // COMMENT ON 문 생성
        if (separateComments) {
            result.appendLine()

            // 컬럼 주석
            columnComments.forEach { (table, column, comment) ->
                result.appendLine("   COMMENT ON COLUMN \"$schemaOwner\".\"$table\".\"$column\" IS '$comment';")
            }

            // 테이블 주석
            if (tableComment != null) {
                result.appendLine("   COMMENT ON TABLE \"$schemaOwner\".\"$rawTableName\" IS '$tableComment';")
            }

            appliedRules.add("COMMENT를 별도 COMMENT ON 문으로 분리")
        }

        // PRIMARY KEY 인덱스 및 제약조건 생성
        if (separatePk && primaryKeyColumns != null && primaryKeyColumns!!.isNotEmpty()) {
            result.appendLine()

            val pkColumnsQuoted = primaryKeyColumns!!.joinToString(", ") { "\"$it\"" }

            // UNIQUE INDEX 생성
            if (generateIndex) {
                result.appendLine("CREATE UNIQUE INDEX \"$schemaOwner\".\"$primaryKeyName\" ON \"$schemaOwner\".\"$rawTableName\" ($pkColumnsQuoted)")
                result.appendLine("    TABLESPACE \"$indexspace\" ;")
                result.appendLine()
                appliedRules.add("PRIMARY KEY UNIQUE INDEX 생성")
            }

            // ALTER TABLE ADD CONSTRAINT
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
     * MySQL 데이터 타입을 Oracle 데이터 타입으로 변환 (정밀도 포함)
     */
    private fun convertMySqlTypeToOracleWithPrecision(mysqlType: String, args: List<String>?): String {
        val baseType = mysqlType.uppercase()
        val precision = args?.firstOrNull()?.toIntOrNull()
        val scale = args?.getOrNull(1)?.toIntOrNull()

        return when (baseType) {
            "TINYINT" -> "NUMBER(3)"
            "SMALLINT" -> "NUMBER(5)"
            "MEDIUMINT" -> "NUMBER(7)"
            "INT", "INTEGER" -> if (precision != null) "NUMBER($precision)" else "NUMBER"
            "BIGINT" -> "NUMBER(19)"
            "FLOAT" -> "BINARY_FLOAT"
            "DOUBLE" -> "BINARY_DOUBLE"
            "DECIMAL", "NUMERIC" -> {
                when {
                    precision != null && scale != null -> "NUMBER($precision,$scale)"
                    precision != null -> "NUMBER($precision)"
                    else -> "NUMBER"
                }
            }
            "VARCHAR", "CHAR" -> {
                val size = precision ?: 255
                val unit = if (size > 4000) "CHAR" else "BYTE"
                if (baseType == "CHAR") "CHAR($size $unit)" else "VARCHAR2($size $unit)"
            }
            "TEXT", "LONGTEXT", "MEDIUMTEXT", "TINYTEXT" -> "CLOB"
            "BLOB", "LONGBLOB", "MEDIUMBLOB", "TINYBLOB" -> "BLOB"
            "DATETIME" -> if (precision != null && precision > 0) "TIMESTAMP($precision)" else "DATE"
            "TIMESTAMP" -> if (precision != null) "TIMESTAMP($precision)" else "TIMESTAMP"
            "DATE" -> "DATE"
            "TIME" -> "TIMESTAMP"
            "BOOLEAN", "BOOL" -> "NUMBER(1)"
            "JSON" -> "CLOB"
            "BINARY", "VARBINARY" -> {
                val size = precision ?: 255
                "RAW($size)"
            }
            "BIT" -> {
                val size = precision ?: 1
                if (size == 1) "NUMBER(1)" else "RAW(${(size + 7) / 8})"
            }
            "ENUM", "SET" -> "VARCHAR2(255 BYTE)"
            "YEAR" -> "NUMBER(4)"
            else -> baseType
        }
    }
}
