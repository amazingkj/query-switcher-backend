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
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex
import net.sf.jsqlparser.statement.create.index.CreateIndex
import net.sf.jsqlparser.statement.drop.Drop
import net.sf.jsqlparser.statement.alter.Alter
import net.sf.jsqlparser.statement.create.view.CreateView
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
            is CreateIndex -> {
                val convertedSql = convertCreateIndex(statement, targetDialect, warnings, appliedRules, options)
                return ConversionResult(
                    convertedSql = convertedSql,
                    warnings = warnings,
                    appliedRules = appliedRules
                )
            }
            is Drop -> {
                val convertedSql = convertDrop(statement, targetDialect, warnings, appliedRules)
                return ConversionResult(
                    convertedSql = convertedSql,
                    warnings = warnings,
                    appliedRules = appliedRules
                )
            }
            is Alter -> {
                val convertedSql = convertAlter(statement, targetDialect, warnings, appliedRules)
                return ConversionResult(
                    convertedSql = convertedSql,
                    warnings = warnings,
                    appliedRules = appliedRules
                )
            }
            is CreateView -> {
                val convertedSql = convertCreateView(statement, targetDialect, warnings, appliedRules, options)
                return ConversionResult(
                    convertedSql = convertedSql,
                    warnings = warnings,
                    appliedRules = appliedRules
                )
            }
            else -> {
                // CREATE TRIGGER 문은 JSQLParser에서 직접 지원하지 않으므로 문자열로 처리
                val statementStr = statement.toString().uppercase()
                if (statementStr.trimStart().startsWith("CREATE") && statementStr.contains("TRIGGER")) {
                    val convertedSql = convertCreateTriggerFromString(statement.toString(), targetDialect, warnings, appliedRules, options)
                    return ConversionResult(
                        convertedSql = convertedSql,
                        warnings = warnings,
                        appliedRules = appliedRules
                    )
                }
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

        // PRIMARY KEY, FOREIGN KEY, UNIQUE, CHECK 정보 추출
        val foreignKeys = mutableListOf<ForeignKeyInfo>()
        val uniqueConstraints = mutableListOf<UniqueConstraintInfo>()
        val checkConstraints = mutableListOf<CheckConstraintInfo>()

        createTable.indexes?.forEach { index ->
            val indexStr = index.toString().uppercase()
            val indexType = index.type?.uppercase() ?: ""

            // PRIMARY KEY 추출
            if (indexType == "PRIMARY KEY" || indexStr.contains("PRIMARY KEY")) {
                primaryKeyColumns = index.columnsNames?.map { it.trim('`', '"') }
                primaryKeyName = "PK_${rawTableName.removePrefix("TB_").removePrefix("T_")}"
            }

            // UNIQUE 제약조건 추출
            if (indexType == "UNIQUE" || indexStr.contains("UNIQUE KEY") || indexStr.contains("UNIQUE INDEX")) {
                val ukColumns = index.columnsNames?.map { it.trim('`', '"') } ?: emptyList()
                if (ukColumns.isNotEmpty()) {
                    val ukName = index.name?.trim('`', '"') ?: "UK_${rawTableName}_${ukColumns.first()}"
                    uniqueConstraints.add(UniqueConstraintInfo(
                        constraintName = ukName,
                        columns = ukColumns
                    ))
                }
            }

            // FOREIGN KEY 추출 (ForeignKeyIndex 타입 체크)
            if (index is ForeignKeyIndex) {
                val fkIndex = index as ForeignKeyIndex
                val fkColumns = fkIndex.columnsNames?.map { it.trim('`', '"') } ?: emptyList()
                val referencedTable = fkIndex.table?.name?.trim('`', '"')
                val referencedColumns = fkIndex.referencedColumnNames?.map { it.trim('`', '"') } ?: emptyList()

                // ON DELETE/UPDATE 액션 추출 (deprecated API 사용, 향후 JSQLParser 업데이트 시 수정 필요)
                @Suppress("DEPRECATION")
                val onDeleteAction = fkIndex.onDeleteReferenceOption
                @Suppress("DEPRECATION")
                val onUpdateAction = fkIndex.onUpdateReferenceOption

                if (fkColumns.isNotEmpty()) {
                    foreignKeys.add(ForeignKeyInfo(
                        constraintName = fkIndex.name?.trim('`', '"') ?: "FK_${rawTableName}_${fkColumns.first()}",
                        columns = fkColumns,
                        referencedTable = referencedTable ?: "",
                        referencedColumns = referencedColumns,
                        onDelete = onDeleteAction,
                        onUpdate = onUpdateAction
                    ))
                }
            }
        }

        // CHECK 제약조건 추출 (MySQL 8.0.16+에서 지원)
        // JSQLParser가 CHECK를 인덱스로 파싱하지 않으므로 테이블 옵션에서 추출 시도
        createTable.tableOptionsStrings?.forEach { optStr ->
            val opt = optStr.toString()
            val checkMatch = Regex("CONSTRAINT\\s+[`\"]?(\\w+)[`\"]?\\s+CHECK\\s*\\((.+)\\)", RegexOption.IGNORE_CASE).find(opt)
            if (checkMatch != null) {
                checkConstraints.add(CheckConstraintInfo(
                    constraintName = checkMatch.groupValues[1],
                    expression = checkMatch.groupValues[2]
                ))
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

        // FOREIGN KEY 제약조건 생성
        if (foreignKeys.isNotEmpty()) {
            result.appendLine()

            foreignKeys.forEach { fk ->
                if (fk.referencedTable.isNotEmpty() && fk.referencedColumns.isNotEmpty()) {
                    result.appendLine(fk.toOracleConstraint(schemaOwner, rawTableName) + ";")
                } else {
                    warnings.add(createWarning(
                        type = WarningType.PARTIAL_SUPPORT,
                        message = "FOREIGN KEY '${fk.constraintName}'의 참조 테이블/컬럼 정보가 불완전합니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "FOREIGN KEY 제약조건을 수동으로 확인하세요."
                    ))
                }
            }

            appliedRules.add("FOREIGN KEY를 별도 ALTER TABLE로 분리")
        }

        // UNIQUE 제약조건 생성
        if (uniqueConstraints.isNotEmpty()) {
            result.appendLine()

            uniqueConstraints.forEach { uk ->
                result.appendLine(uk.toOracleConstraint(schemaOwner, rawTableName, indexspace) + ";")
            }

            appliedRules.add("UNIQUE 제약조건을 별도 ALTER TABLE로 분리")
        }

        // CHECK 제약조건 생성
        if (checkConstraints.isNotEmpty()) {
            result.appendLine()

            checkConstraints.forEach { ck ->
                result.appendLine(ck.toOracleConstraint(schemaOwner, rawTableName) + ";")
            }

            appliedRules.add("CHECK 제약조건을 별도 ALTER TABLE로 분리")
        }

        // PARTITION 정보 추출 및 변환
        val partitionInfo = extractPartitionInfo(createTable.toString())
        if (partitionInfo != null) {
            result.appendLine()
            result.appendLine("-- PARTITION 정보 (테이블 생성 시 포함 필요)")
            result.appendLine("-- " + partitionInfo.toOraclePartition(schemaOwner).replace("\n", "\n-- "))
            appliedRules.add("PARTITION 구문 Oracle 형식으로 변환")

            warnings.add(createWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL PARTITION 구문이 Oracle 형식으로 변환되었습니다.",
                severity = WarningSeverity.INFO,
                suggestion = "파티션 정의를 CREATE TABLE 문에 직접 포함하거나, 별도의 ALTER TABLE로 추가하세요."
            ))
        }

        return result.toString().trim()
    }

    /**
     * CREATE TABLE 문에서 PARTITION 정보 추출
     */
    private fun extractPartitionInfo(createTableSql: String): PartitionInfo? {
        val upperSql = createTableSql.uppercase()

        // PARTITION BY 구문 찾기
        if (!upperSql.contains("PARTITION BY")) {
            return null
        }

        // 파티션 타입 추출
        val partitionType = when {
            upperSql.contains("PARTITION BY RANGE COLUMNS") -> PartitionType.COLUMNS
            upperSql.contains("PARTITION BY RANGE") -> PartitionType.RANGE
            upperSql.contains("PARTITION BY LIST") -> PartitionType.LIST
            upperSql.contains("PARTITION BY HASH") -> PartitionType.HASH
            upperSql.contains("PARTITION BY KEY") -> PartitionType.KEY
            else -> return null
        }

        // 파티션 컬럼 추출
        val partitionByMatch = Regex(
            "PARTITION\\s+BY\\s+(?:RANGE|LIST|HASH|KEY)(?:\\s+COLUMNS)?\\s*\\(([^)]+)\\)",
            RegexOption.IGNORE_CASE
        ).find(createTableSql)

        val columns = partitionByMatch?.groupValues?.get(1)
            ?.split(",")
            ?.map { it.trim().trim('`', '"') }
            ?: return null

        // 파티션 정의 추출
        val partitions = mutableListOf<PartitionDefinition>()

        // PARTITION 정의 패턴 매칭
        val partitionDefPattern = Regex(
            "PARTITION\\s+[`\"]?(\\w+)[`\"]?\\s+VALUES\\s+(LESS\\s+THAN\\s*\\([^)]+\\)|LESS\\s+THAN\\s+MAXVALUE|IN\\s*\\([^)]+\\))",
            RegexOption.IGNORE_CASE
        )

        partitionDefPattern.findAll(createTableSql).forEach { match ->
            val partName = match.groupValues[1]
            val partValues = match.groupValues[2]
            partitions.add(PartitionDefinition(
                name = partName,
                values = partValues
            ))
        }

        // HASH/KEY 파티션의 경우 PARTITIONS N 추출
        if ((partitionType == PartitionType.HASH || partitionType == PartitionType.KEY) && partitions.isEmpty()) {
            val partitionsCountMatch = Regex("PARTITIONS\\s+(\\d+)", RegexOption.IGNORE_CASE).find(createTableSql)
            val count = partitionsCountMatch?.groupValues?.get(1)?.toIntOrNull() ?: 4
            for (i in 0 until count) {
                partitions.add(PartitionDefinition(name = "p$i", values = ""))
            }
        }

        return if (columns.isNotEmpty()) {
            PartitionInfo(
                partitionType = partitionType,
                columns = columns,
                partitions = partitions
            )
        } else null
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

    /**
     * MySQL CREATE INDEX를 다른 방언으로 변환
     * - ASC/DESC 정렬 옵션 지원
     * - NULLS FIRST/LAST 옵션 지원 (Oracle/PostgreSQL)
     */
    private fun convertCreateIndex(
        createIndex: CreateIndex,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val schemaOwner = options?.oracleSchemaOwner ?: "SCHEMA_OWNER"
        val indexspace = options?.oracleIndexspace ?: "INDEXSPACE_NAME"

        val index = createIndex.index
        val indexName = index?.name?.trim('`', '"') ?: "IDX_UNNAMED"
        val tableName = createIndex.table?.name?.trim('`', '"') ?: ""
        val isUnique = createIndex.toString().uppercase().contains("UNIQUE")

        // 인덱스 컬럼과 옵션 추출 (ASC/DESC, NULLS FIRST/LAST)
        val indexColumns = extractIndexColumnsWithOptions(createIndex.toString())

        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                val result = StringBuilder()
                val columnsFormatted = indexColumns.joinToString(", ") { col ->
                    val colName = "\"${col.columnName}\""
                    val sortOrder = if (col.sortOrder == IndexColumnOption.SortOrder.DESC) " DESC" else ""
                    val nullsPos = when (col.nullsPosition) {
                        IndexColumnOption.NullsPosition.FIRST -> " NULLS FIRST"
                        IndexColumnOption.NullsPosition.LAST -> " NULLS LAST"
                        else -> ""
                    }
                    "$colName$sortOrder$nullsPos"
                }

                if (isUnique) {
                    result.append("CREATE UNIQUE INDEX \"$schemaOwner\".\"$indexName\" ON \"$schemaOwner\".\"$tableName\" ($columnsFormatted)")
                } else {
                    result.append("CREATE INDEX \"$schemaOwner\".\"$indexName\" ON \"$schemaOwner\".\"$tableName\" ($columnsFormatted)")
                }
                result.appendLine()
                result.append("    TABLESPACE \"$indexspace\"")

                appliedRules.add("CREATE INDEX MySQL → Oracle 형식으로 변환")
                appliedRules.add("백틱(`) → 큰따옴표(\") 변환")
                appliedRules.add("TABLESPACE 지정 추가")
                if (indexColumns.any { it.sortOrder == IndexColumnOption.SortOrder.DESC }) {
                    appliedRules.add("DESC 정렬 옵션 유지")
                }
                if (indexColumns.any { it.nullsPosition != null }) {
                    appliedRules.add("NULLS FIRST/LAST 옵션 유지")
                }

                result.toString()
            }
            DialectType.POSTGRESQL -> {
                val columnsFormatted = indexColumns.joinToString(", ") { col ->
                    val colName = "\"${col.columnName}\""
                    val sortOrder = if (col.sortOrder == IndexColumnOption.SortOrder.DESC) " DESC" else ""
                    val nullsPos = when (col.nullsPosition) {
                        IndexColumnOption.NullsPosition.FIRST -> " NULLS FIRST"
                        IndexColumnOption.NullsPosition.LAST -> " NULLS LAST"
                        else -> ""
                    }
                    "$colName$sortOrder$nullsPos"
                }

                val result = if (isUnique) {
                    "CREATE UNIQUE INDEX \"$indexName\" ON \"$tableName\" ($columnsFormatted)"
                } else {
                    "CREATE INDEX \"$indexName\" ON \"$tableName\" ($columnsFormatted)"
                }

                appliedRules.add("CREATE INDEX MySQL → PostgreSQL 형식으로 변환")
                appliedRules.add("백틱(`) → 큰따옴표(\") 변환")
                if (indexColumns.any { it.sortOrder == IndexColumnOption.SortOrder.DESC }) {
                    appliedRules.add("DESC 정렬 옵션 유지")
                }
                if (indexColumns.any { it.nullsPosition != null }) {
                    appliedRules.add("NULLS FIRST/LAST 옵션 유지")
                }

                result
            }
            DialectType.MYSQL -> {
                // MySQL 8.0+는 DESC 인덱스 지원, NULLS FIRST/LAST는 미지원
                val columnsFormatted = indexColumns.joinToString(", ") { col ->
                    val colName = "`${col.columnName}`"
                    val sortOrder = if (col.sortOrder == IndexColumnOption.SortOrder.DESC) " DESC" else ""
                    "$colName$sortOrder"
                }

                if (indexColumns.any { it.nullsPosition != null }) {
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "MySQL은 NULLS FIRST/LAST 옵션을 지원하지 않습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "ORDER BY 절에서 CASE WHEN을 사용하여 NULL 정렬을 처리하세요."
                    ))
                }

                val result = if (isUnique) {
                    "CREATE UNIQUE INDEX `$indexName` ON `$tableName` ($columnsFormatted)"
                } else {
                    "CREATE INDEX `$indexName` ON `$tableName` ($columnsFormatted)"
                }

                appliedRules.add("CREATE INDEX 형식 유지")
                result
            }
        }
    }

    /**
     * CREATE INDEX 문에서 컬럼과 옵션(ASC/DESC, NULLS FIRST/LAST) 추출
     */
    private fun extractIndexColumnsWithOptions(createIndexSql: String): List<IndexColumnOption> {
        val columns = mutableListOf<IndexColumnOption>()

        // 괄호 안의 컬럼 목록 추출
        val columnListMatch = Regex("\\(([^)]+)\\)").find(createIndexSql)
        if (columnListMatch != null) {
            val columnList = columnListMatch.groupValues[1]
            // 각 컬럼 파싱 (쉼표로 분리)
            columnList.split(",").forEach { colDef ->
                val trimmed = colDef.trim()
                // 컬럼명 추출 (백틱/큰따옴표 포함 가능)
                val colNameMatch = Regex("^[`\"]?([^`\"\\s]+)[`\"]?").find(trimmed)
                val columnName = colNameMatch?.groupValues?.get(1)?.trim('`', '"') ?: return@forEach

                // ASC/DESC 확인
                val sortOrder = when {
                    trimmed.uppercase().contains(" DESC") -> IndexColumnOption.SortOrder.DESC
                    else -> IndexColumnOption.SortOrder.ASC
                }

                // NULLS FIRST/LAST 확인
                val nullsPosition = when {
                    trimmed.uppercase().contains("NULLS FIRST") -> IndexColumnOption.NullsPosition.FIRST
                    trimmed.uppercase().contains("NULLS LAST") -> IndexColumnOption.NullsPosition.LAST
                    else -> null
                }

                columns.add(IndexColumnOption(
                    columnName = columnName,
                    sortOrder = sortOrder,
                    nullsPosition = nullsPosition
                ))
            }
        }

        return columns
    }

    /**
     * MySQL DROP 문을 다른 방언으로 변환
     */
    private fun convertDrop(
        drop: Drop,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val dropType = drop.type?.uppercase() ?: "TABLE"
        val objectName = drop.name?.name?.trim('`', '"') ?: ""
        val ifExists = drop.isIfExists

        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                // Oracle은 IF EXISTS를 지원하지 않음
                if (ifExists) {
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Oracle은 DROP IF EXISTS 구문을 지원하지 않습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "PL/SQL 블록을 사용하거나 예외 처리를 추가하세요."
                    ))
                }

                val result = "DROP $dropType \"$objectName\""
                appliedRules.add("DROP 문 Oracle 형식으로 변환")
                appliedRules.add("백틱(`) → 큰따옴표(\") 변환")

                if (dropType == "TABLE") {
                    // Oracle에서는 CASCADE CONSTRAINTS 옵션 안내
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Oracle에서 FK가 있는 테이블 삭제 시 CASCADE CONSTRAINTS가 필요할 수 있습니다.",
                        severity = WarningSeverity.INFO,
                        suggestion = "DROP TABLE \"$objectName\" CASCADE CONSTRAINTS 사용을 고려하세요."
                    ))
                }

                result
            }
            DialectType.POSTGRESQL -> {
                val ifExistsStr = if (ifExists) "IF EXISTS " else ""
                val result = "DROP $dropType $ifExistsStr\"$objectName\""
                appliedRules.add("DROP 문 PostgreSQL 형식으로 변환")
                appliedRules.add("백틱(`) → 큰따옴표(\") 변환")
                result
            }
            else -> drop.toString()
        }
    }

    /**
     * MySQL ALTER TABLE 문을 다른 방언으로 변환
     */
    private fun convertAlter(
        alter: Alter,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val tableName = alter.table?.name?.trim('`', '"') ?: ""

        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                val result = StringBuilder()
                result.append("ALTER TABLE \"$tableName\"")

                // ALTER 표현식들 처리
                alter.alterExpressions?.forEach { expr ->
                    val operation = expr.operation?.name?.uppercase() ?: ""
                    when (operation) {
                        "ADD" -> {
                            // colDataTypeList를 통해 컬럼 정보 접근
                            val columnDataTypes = expr.colDataTypeList
                            if (!columnDataTypes.isNullOrEmpty()) {
                                columnDataTypes.forEach { colDef ->
                                    val columnName = colDef.columnName?.trim('`', '"')
                                    val colDataType = colDef.colDataType
                                    val dataType = if (colDataType != null) {
                                        val typeName = colDataType.dataType?.uppercase() ?: "VARCHAR"
                                        val typeArgs = colDataType.argumentsStringList
                                        convertMySqlTypeToOracleWithPrecision(typeName, typeArgs)
                                    } else {
                                        "VARCHAR2(255)"
                                    }
                                    result.append(" ADD (\"$columnName\" $dataType)")
                                }
                            } else {
                                val columnName = expr.columnName?.trim('`', '"')
                                if (columnName != null) {
                                    result.append(" ADD (\"$columnName\" VARCHAR2(255))")
                                }
                            }
                        }
                        "DROP" -> {
                            val columnName = expr.columnName?.trim('`', '"')
                            if (columnName != null) {
                                result.append(" DROP COLUMN \"$columnName\"")
                            }
                        }
                        "MODIFY" -> {
                            // colDataTypeList를 통해 컬럼 정보 접근
                            val columnDataTypes = expr.colDataTypeList
                            if (!columnDataTypes.isNullOrEmpty()) {
                                columnDataTypes.forEach { colDef ->
                                    val columnName = colDef.columnName?.trim('`', '"')
                                    val colDataType = colDef.colDataType
                                    val dataType = if (colDataType != null) {
                                        val typeName = colDataType.dataType?.uppercase() ?: "VARCHAR"
                                        val typeArgs = colDataType.argumentsStringList
                                        convertMySqlTypeToOracleWithPrecision(typeName, typeArgs)
                                    } else {
                                        "VARCHAR2(255)"
                                    }
                                    result.append(" MODIFY (\"$columnName\" $dataType)")
                                }
                            } else {
                                val columnName = expr.columnName?.trim('`', '"')
                                if (columnName != null) {
                                    result.append(" MODIFY (\"$columnName\" VARCHAR2(255))")
                                }
                            }
                        }
                        else -> {
                            warnings.add(createWarning(
                                type = WarningType.PARTIAL_SUPPORT,
                                message = "ALTER TABLE $operation 작업이 완전히 변환되지 않았을 수 있습니다.",
                                severity = WarningSeverity.WARNING,
                                suggestion = "수동으로 확인하세요."
                            ))
                        }
                    }
                }

                appliedRules.add("ALTER TABLE MySQL → Oracle 형식으로 변환")
                result.toString()
            }
            DialectType.POSTGRESQL -> {
                val result = StringBuilder()
                result.append("ALTER TABLE \"$tableName\"")

                alter.alterExpressions?.forEach { expr ->
                    val operation = expr.operation?.name?.uppercase() ?: ""
                    when (operation) {
                        "ADD" -> {
                            // colDataTypeList를 통해 컬럼 정보 접근
                            val columnDataTypes = expr.colDataTypeList
                            if (!columnDataTypes.isNullOrEmpty()) {
                                columnDataTypes.forEach { colDef ->
                                    val columnName = colDef.columnName?.trim('`', '"')
                                    val colDataType = colDef.colDataType
                                    val dataType = if (colDataType != null) {
                                        val typeName = colDataType.dataType?.uppercase() ?: "VARCHAR"
                                        convertToPostgreSqlDataType(typeName)
                                    } else {
                                        "VARCHAR(255)"
                                    }
                                    result.append(" ADD COLUMN \"$columnName\" $dataType")
                                }
                            } else {
                                val columnName = expr.columnName?.trim('`', '"')
                                if (columnName != null) {
                                    result.append(" ADD COLUMN \"$columnName\" VARCHAR(255)")
                                }
                            }
                        }
                        "DROP" -> {
                            val columnName = expr.columnName?.trim('`', '"')
                            if (columnName != null) {
                                result.append(" DROP COLUMN \"$columnName\"")
                            }
                        }
                        "MODIFY" -> {
                            // PostgreSQL은 ALTER COLUMN 사용
                            // colDataTypeList를 통해 컬럼 정보 접근
                            val columnDataTypes = expr.colDataTypeList
                            if (!columnDataTypes.isNullOrEmpty()) {
                                columnDataTypes.forEach { colDef ->
                                    val columnName = colDef.columnName?.trim('`', '"')
                                    val colDataType = colDef.colDataType
                                    val dataType = if (colDataType != null) {
                                        val typeName = colDataType.dataType?.uppercase() ?: "VARCHAR"
                                        convertToPostgreSqlDataType(typeName)
                                    } else {
                                        "VARCHAR(255)"
                                    }
                                    result.append(" ALTER COLUMN \"$columnName\" TYPE $dataType")
                                }
                            } else {
                                val columnName = expr.columnName?.trim('`', '"')
                                if (columnName != null) {
                                    result.append(" ALTER COLUMN \"$columnName\" TYPE VARCHAR(255)")
                                }
                            }
                        }
                        else -> {
                            warnings.add(createWarning(
                                type = WarningType.PARTIAL_SUPPORT,
                                message = "ALTER TABLE $operation 작업이 완전히 변환되지 않았을 수 있습니다.",
                                severity = WarningSeverity.WARNING,
                                suggestion = "수동으로 확인하세요."
                            ))
                        }
                    }
                }

                appliedRules.add("ALTER TABLE MySQL → PostgreSQL 형식으로 변환")
                result.toString()
            }
            else -> alter.toString()
        }
    }

    /**
     * MySQL CREATE VIEW를 다른 방언으로 변환
     * - 백틱(`) → 큰따옴표(") 변환
     * - 함수 변환 (NOW → SYSDATE 등)
     * - LIMIT → FETCH FIRST 변환 (Oracle)
     * - DEFINER, ALGORITHM, SQL SECURITY 등 MySQL 전용 옵션 제거
     */
    private fun convertCreateView(
        createView: CreateView,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val schemaOwner = options?.oracleSchemaOwner ?: "SCHEMA_OWNER"
        val viewName = createView.view?.name?.trim('`', '"') ?: ""
        val isOrReplace = createView.isOrReplace

        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                val result = StringBuilder()

                // CREATE OR REPLACE VIEW
                if (isOrReplace) {
                    result.append("CREATE OR REPLACE VIEW \"$schemaOwner\".\"$viewName\"")
                } else {
                    result.append("CREATE VIEW \"$schemaOwner\".\"$viewName\"")
                }

                // 컬럼 목록이 있는 경우
                val columnNames = createView.columnNames
                if (!columnNames.isNullOrEmpty()) {
                    val columns = columnNames.map { col -> "\"${col.toString().trim('`', '"')}\"" }
                    result.append(" (${columns.joinToString(", ")})")
                }

                result.appendLine(" AS")

                // SELECT 문 변환
                val selectStatement = createView.select
                if (selectStatement != null) {
                    // SELECT 문 내부의 함수, LIMIT 등을 변환
                    val convertedSelect = convertSelectForView(selectStatement.toString(), targetDialect, warnings, appliedRules)
                    result.append(convertedSelect)
                }

                appliedRules.add("CREATE VIEW MySQL → Oracle 형식으로 변환")
                appliedRules.add("백틱(`) → 큰따옴표(\") 변환")

                // MySQL 전용 옵션 경고
                if (createView.toString().uppercase().contains("DEFINER")) {
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "MySQL DEFINER 옵션은 Oracle에서 지원되지 않습니다.",
                        severity = WarningSeverity.INFO,
                        suggestion = "Oracle에서는 뷰 소유자가 자동으로 DEFINER가 됩니다."
                    ))
                    appliedRules.add("DEFINER 옵션 제거")
                }

                if (createView.toString().uppercase().contains("ALGORITHM")) {
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "MySQL ALGORITHM 옵션은 Oracle에서 지원되지 않습니다.",
                        severity = WarningSeverity.INFO,
                        suggestion = "Oracle은 자동으로 최적의 알고리즘을 선택합니다."
                    ))
                    appliedRules.add("ALGORITHM 옵션 제거")
                }

                result.toString()
            }
            DialectType.POSTGRESQL -> {
                val result = StringBuilder()

                // CREATE OR REPLACE VIEW
                if (isOrReplace) {
                    result.append("CREATE OR REPLACE VIEW \"$viewName\"")
                } else {
                    result.append("CREATE VIEW \"$viewName\"")
                }

                // 컬럼 목록이 있는 경우
                val columnNames = createView.columnNames
                if (!columnNames.isNullOrEmpty()) {
                    val columns = columnNames.map { col -> "\"${col.toString().trim('`', '"')}\"" }
                    result.append(" (${columns.joinToString(", ")})")
                }

                result.appendLine(" AS")

                // SELECT 문 변환
                val selectStatement = createView.select
                if (selectStatement != null) {
                    val convertedSelect = convertSelectForView(selectStatement.toString(), targetDialect, warnings, appliedRules)
                    result.append(convertedSelect)
                }

                appliedRules.add("CREATE VIEW MySQL → PostgreSQL 형식으로 변환")
                appliedRules.add("백틱(`) → 큰따옴표(\") 변환")

                // MySQL 전용 옵션 경고
                if (createView.toString().uppercase().contains("DEFINER")) {
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "MySQL DEFINER 옵션은 PostgreSQL에서 지원되지 않습니다.",
                        severity = WarningSeverity.INFO,
                        suggestion = "PostgreSQL에서는 뷰 소유자가 자동으로 설정됩니다."
                    ))
                    appliedRules.add("DEFINER 옵션 제거")
                }

                result.toString()
            }
            else -> createView.toString()
        }
    }

    /**
     * VIEW 내부 SELECT 문 변환
     * - 백틱 → 큰따옴표
     * - 함수 변환
     * - LIMIT → FETCH FIRST (Oracle)
     */
    private fun convertSelectForView(
        selectSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = selectSql

        // 백틱 → 큰따옴표 변환
        result = result.replace("`", "\"")

        when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                // 함수 변환
                result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSDATE")
                result = result.replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
                result = result.replace(Regex("\\bCOALESCE\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
                result = result.replace(Regex("\\bDATE_FORMAT\\s*\\(", RegexOption.IGNORE_CASE), "TO_CHAR(")
                result = result.replace(Regex("\\bGROUP_CONCAT\\s*\\(", RegexOption.IGNORE_CASE), "LISTAGG(")
                result = result.replace(Regex("\\bCURRENT_TIMESTAMP\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSTIMESTAMP")
                result = result.replace(Regex("\\bCURRENT_TIMESTAMP\\b", RegexOption.IGNORE_CASE), "SYSTIMESTAMP")
                result = result.replace(Regex("\\bCURRENT_DATE\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSDATE")
                result = result.replace(Regex("\\bCURRENT_DATE\\b", RegexOption.IGNORE_CASE), "TRUNC(SYSDATE)")

                // LIMIT 변환 (간단한 패턴 매칭)
                val limitMatch = Regex("\\bLIMIT\\s+(\\d+)", RegexOption.IGNORE_CASE).find(result)
                if (limitMatch != null) {
                    val limitValue = limitMatch.groupValues[1]
                    result = result.replace(limitMatch.value, "FETCH FIRST $limitValue ROWS ONLY")
                    appliedRules.add("LIMIT → FETCH FIRST 변환")
                }

                // OFFSET 변환
                val offsetMatch = Regex("\\bOFFSET\\s+(\\d+)", RegexOption.IGNORE_CASE).find(result)
                if (offsetMatch != null) {
                    val offsetValue = offsetMatch.groupValues[1]
                    result = result.replace(offsetMatch.value, "OFFSET $offsetValue ROWS")
                }

                appliedRules.add("MySQL 함수 → Oracle 함수 변환")
            }
            DialectType.POSTGRESQL -> {
                // PostgreSQL 함수 변환
                result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                result = result.replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
                result = result.replace(Regex("\\bDATE_FORMAT\\s*\\(", RegexOption.IGNORE_CASE), "TO_CHAR(")
                result = result.replace(Regex("\\bGROUP_CONCAT\\s*\\(", RegexOption.IGNORE_CASE), "STRING_AGG(")

                // PostgreSQL은 LIMIT/OFFSET 지원하므로 변환 불필요

                appliedRules.add("MySQL 함수 → PostgreSQL 함수 변환")
            }
            else -> { }
        }

        return result
    }

    /**
     * MySQL CREATE TRIGGER를 다른 방언으로 변환
     * - MySQL TRIGGER 구문 → Oracle/PostgreSQL 형식 변환
     * - BEFORE/AFTER/INSTEAD OF 타이밍 변환
     * - INSERT/UPDATE/DELETE 이벤트 변환
     * - NEW/OLD 참조 변환
     * - BEGIN...END 블록을 PL/SQL 또는 PL/pgSQL 형식으로 변환
     * - DEFINER 옵션 제거
     */
    private fun convertCreateTriggerFromString(
        triggerSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val schemaOwner = options?.oracleSchemaOwner ?: "SCHEMA_OWNER"

        // 트리거 정보 추출
        val triggerInfo = extractTriggerInfo(triggerSql)

        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                convertTriggerToOracle(triggerInfo, schemaOwner, warnings, appliedRules)
            }
            DialectType.POSTGRESQL -> {
                convertTriggerToPostgreSql(triggerInfo, warnings, appliedRules)
            }
            else -> triggerSql
        }
    }

    /**
     * MySQL TRIGGER 문에서 트리거 정보 추출
     */
    private fun extractTriggerInfo(triggerSql: String): TriggerInfo {
        val upperSql = triggerSql.uppercase()

        // 트리거 이름 추출
        val triggerNameMatch = Regex(
            "CREATE\\s+(?:DEFINER\\s*=\\s*[^\\s]+\\s+)?TRIGGER\\s+[`\"]?(\\w+)[`\"]?",
            RegexOption.IGNORE_CASE
        ).find(triggerSql)
        val triggerName = triggerNameMatch?.groupValues?.get(1) ?: "UNNAMED_TRIGGER"

        // 타이밍 추출 (BEFORE/AFTER)
        val timing = when {
            upperSql.contains("BEFORE INSERT") || upperSql.contains("BEFORE UPDATE") || upperSql.contains("BEFORE DELETE") -> "BEFORE"
            upperSql.contains("AFTER INSERT") || upperSql.contains("AFTER UPDATE") || upperSql.contains("AFTER DELETE") -> "AFTER"
            else -> "BEFORE"
        }

        // 이벤트 추출 (INSERT/UPDATE/DELETE)
        val events = mutableListOf<String>()
        if (upperSql.contains(" INSERT")) events.add("INSERT")
        if (upperSql.contains(" UPDATE")) events.add("UPDATE")
        if (upperSql.contains(" DELETE")) events.add("DELETE")
        if (events.isEmpty()) events.add("INSERT")

        // 테이블 이름 추출
        val tableNameMatch = Regex(
            "ON\\s+[`\"]?(\\w+)[`\"]?",
            RegexOption.IGNORE_CASE
        ).find(triggerSql)
        val tableName = tableNameMatch?.groupValues?.get(1) ?: "UNKNOWN_TABLE"

        // FOR EACH ROW 여부
        val forEachRow = upperSql.contains("FOR EACH ROW")

        // 트리거 본문 추출 (BEGIN...END 사이)
        val bodyMatch = Regex(
            "BEGIN\\s+(.+?)\\s+END",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(triggerSql)
        val body = bodyMatch?.groupValues?.get(1)?.trim() ?: ""

        // FOLLOWS/PRECEDES 추출 (MySQL 5.7+)
        val orderMatch = Regex(
            "(FOLLOWS|PRECEDES)\\s+[`\"]?(\\w+)[`\"]?",
            RegexOption.IGNORE_CASE
        ).find(triggerSql)
        val triggerOrder = orderMatch?.groupValues?.get(1)?.uppercase()
        val orderTriggerName = orderMatch?.groupValues?.get(2)

        return TriggerInfo(
            name = triggerName,
            timing = timing,
            events = events,
            tableName = tableName,
            forEachRow = forEachRow,
            body = body,
            triggerOrder = triggerOrder,
            orderTriggerName = orderTriggerName
        )
    }

    /**
     * MySQL TRIGGER를 Oracle PL/SQL TRIGGER로 변환
     */
    private fun convertTriggerToOracle(
        triggerInfo: TriggerInfo,
        schemaOwner: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()

        // CREATE OR REPLACE TRIGGER
        result.appendLine("CREATE OR REPLACE TRIGGER \"$schemaOwner\".\"${triggerInfo.name}\"")

        // BEFORE/AFTER + 이벤트
        val events = triggerInfo.events.joinToString(" OR ")
        result.appendLine("${triggerInfo.timing} $events")

        // ON 테이블
        result.appendLine("ON \"$schemaOwner\".\"${triggerInfo.tableName}\"")

        // FOR EACH ROW
        if (triggerInfo.forEachRow) {
            result.appendLine("FOR EACH ROW")
        }

        // FOLLOWS/PRECEDES 경고
        if (triggerInfo.triggerOrder != null) {
            warnings.add(createWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL의 ${triggerInfo.triggerOrder} 옵션은 Oracle에서 직접 지원되지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "Oracle에서는 트리거 실행 순서를 FOLLOWS/PRECEDES로 제어할 수 없습니다. 트리거 로직을 통합하거나 COMPOUND TRIGGER를 고려하세요."
            ))
        }

        // 트리거 본문 변환
        val oracleBody = convertTriggerBodyToOracle(triggerInfo.body, warnings, appliedRules)
        result.appendLine("BEGIN")
        result.appendLine("    $oracleBody")
        result.append("END;")

        appliedRules.add("CREATE TRIGGER MySQL → Oracle 형식으로 변환")
        appliedRules.add("백틱(`) → 큰따옴표(\") 변환")
        appliedRules.add("DEFINER 옵션 제거")

        return result.toString()
    }

    /**
     * MySQL TRIGGER를 PostgreSQL PL/pgSQL TRIGGER로 변환
     * PostgreSQL은 트리거 함수와 트리거를 분리하여 생성해야 함
     */
    private fun convertTriggerToPostgreSql(
        triggerInfo: TriggerInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()
        val functionName = "${triggerInfo.name}_func"

        // 1. 트리거 함수 생성
        result.appendLine("-- 트리거 함수 생성")
        result.appendLine("CREATE OR REPLACE FUNCTION \"$functionName\"()")
        result.appendLine("RETURNS TRIGGER AS \$\$")
        result.appendLine("BEGIN")

        // 트리거 본문 변환
        val pgBody = convertTriggerBodyToPostgreSql(triggerInfo.body, triggerInfo.events, warnings, appliedRules)
        result.appendLine("    $pgBody")

        // RETURN 문 추가 (PostgreSQL 필수)
        if (triggerInfo.timing == "BEFORE") {
            if (triggerInfo.events.contains("DELETE")) {
                result.appendLine("    RETURN OLD;")
            } else {
                result.appendLine("    RETURN NEW;")
            }
        } else {
            result.appendLine("    RETURN NULL;")
        }

        result.appendLine("END;")
        result.appendLine("\$\$ LANGUAGE plpgsql;")
        result.appendLine()

        // 2. 트리거 생성
        result.appendLine("-- 트리거 생성")
        result.append("CREATE TRIGGER \"${triggerInfo.name}\"")
        result.appendLine()

        // BEFORE/AFTER + 이벤트
        val events = triggerInfo.events.joinToString(" OR ")
        result.appendLine("${triggerInfo.timing} $events")

        // ON 테이블
        result.appendLine("ON \"${triggerInfo.tableName}\"")

        // FOR EACH ROW
        if (triggerInfo.forEachRow) {
            result.appendLine("FOR EACH ROW")
        }

        // EXECUTE FUNCTION
        result.append("EXECUTE FUNCTION \"$functionName\"();")

        // FOLLOWS/PRECEDES 경고
        if (triggerInfo.triggerOrder != null) {
            warnings.add(createWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL의 ${triggerInfo.triggerOrder} 옵션은 PostgreSQL에서 지원되지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "PostgreSQL에서는 트리거 실행 순서가 이름의 알파벳 순서로 결정됩니다."
            ))
        }

        appliedRules.add("CREATE TRIGGER MySQL → PostgreSQL 형식으로 변환")
        appliedRules.add("트리거 함수 분리 생성 (PostgreSQL 필수)")
        appliedRules.add("백틱(`) → 큰따옴표(\") 변환")
        appliedRules.add("DEFINER 옵션 제거")

        return result.toString()
    }

    /**
     * MySQL 트리거 본문을 Oracle PL/SQL 형식으로 변환
     */
    private fun convertTriggerBodyToOracle(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // 백틱 → 큰따옴표 변환
        result = result.replace("`", "\"")

        // NEW/OLD 참조 변환 (MySQL NEW.col → Oracle :NEW.col)
        result = result.replace(Regex("\\bNEW\\.([a-zA-Z_][a-zA-Z0-9_]*)", RegexOption.IGNORE_CASE)) { matchResult ->
            ":NEW.${matchResult.groupValues[1]}"
        }
        result = result.replace(Regex("\\bOLD\\.([a-zA-Z_][a-zA-Z0-9_]*)", RegexOption.IGNORE_CASE)) { matchResult ->
            ":OLD.${matchResult.groupValues[1]}"
        }

        // MySQL 함수 → Oracle 함수 변환
        result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSDATE")
        result = result.replace(Regex("\\bCURRENT_TIMESTAMP\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSTIMESTAMP")
        result = result.replace(Regex("\\bCURRENT_TIMESTAMP\\b", RegexOption.IGNORE_CASE), "SYSTIMESTAMP")
        result = result.replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
        result = result.replace(Regex("\\bCOALESCE\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
        result = result.replace(Regex("\\bCONCAT\\s*\\(", RegexOption.IGNORE_CASE), "CONCAT(")
        result = result.replace(Regex("\\bUUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYS_GUID()")

        // IF 문 변환 (MySQL IF...THEN...END IF → Oracle IF...THEN...END IF)
        // MySQL과 Oracle의 IF 구문은 유사하므로 기본적으로 호환됨

        // SET 문 변환 (MySQL SET var = value → Oracle var := value)
        result = result.replace(Regex("\\bSET\\s+(@?[a-zA-Z_][a-zA-Z0-9_]*)\\s*=", RegexOption.IGNORE_CASE)) { matchResult ->
            val varName = matchResult.groupValues[1].removePrefix("@")
            "$varName :="
        }

        // SIGNAL SQLSTATE 변환
        if (result.uppercase().contains("SIGNAL SQLSTATE")) {
            result = result.replace(
                Regex("SIGNAL\\s+SQLSTATE\\s+'[^']*'\\s+SET\\s+MESSAGE_TEXT\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE)
            ) { matchResult ->
                "RAISE_APPLICATION_ERROR(-20001, '${matchResult.groupValues[1]}')"
            }
            appliedRules.add("SIGNAL SQLSTATE → RAISE_APPLICATION_ERROR 변환")
        }

        appliedRules.add("NEW/OLD 참조를 :NEW/:OLD로 변환")
        appliedRules.add("MySQL 함수 → Oracle 함수 변환")

        return result
    }

    /**
     * MySQL 트리거 본문을 PostgreSQL PL/pgSQL 형식으로 변환
     */
    private fun convertTriggerBodyToPostgreSql(
        body: String,
        events: List<String>,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // 백틱 → 큰따옴표 변환
        result = result.replace("`", "\"")

        // NEW/OLD 참조는 PostgreSQL에서도 동일하게 사용 가능 (콜론 없이)
        // MySQL: NEW.col, PostgreSQL: NEW.col (동일)

        // MySQL 함수 → PostgreSQL 함수 변환
        result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
        result = result.replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
        result = result.replace(Regex("\\bCONCAT\\s*\\(", RegexOption.IGNORE_CASE), "CONCAT(")
        result = result.replace(Regex("\\bUUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "gen_random_uuid()")

        // SET 문 변환 (MySQL SET var = value → PostgreSQL var := value)
        result = result.replace(Regex("\\bSET\\s+(@?[a-zA-Z_][a-zA-Z0-9_]*)\\s*=", RegexOption.IGNORE_CASE)) { matchResult ->
            val varName = matchResult.groupValues[1].removePrefix("@")
            "$varName :="
        }

        // SIGNAL SQLSTATE 변환
        if (result.uppercase().contains("SIGNAL SQLSTATE")) {
            result = result.replace(
                Regex("SIGNAL\\s+SQLSTATE\\s+'([^']*)'\\s+SET\\s+MESSAGE_TEXT\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE)
            ) { matchResult ->
                "RAISE EXCEPTION '${matchResult.groupValues[2]}' USING ERRCODE = '${matchResult.groupValues[1]}'"
            }
            appliedRules.add("SIGNAL SQLSTATE → RAISE EXCEPTION 변환")
        }

        // INSERT INTO ... SELECT 문은 PostgreSQL에서도 동일하게 사용 가능

        appliedRules.add("MySQL 함수 → PostgreSQL 함수 변환")

        return result
    }

    /**
     * MySQL CREATE PROCEDURE를 다른 방언으로 변환
     */
    fun convertCreateProcedureFromString(
        procedureSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: ConversionOptions?
    ): String {
        val schemaOwner = options?.oracleSchemaOwner ?: "SCHEMA_OWNER"

        // 프로시저 정보 추출
        val procedureInfo = extractProcedureInfo(procedureSql)

        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                convertProcedureToOracle(procedureInfo, schemaOwner, warnings, appliedRules)
            }
            DialectType.POSTGRESQL -> {
                convertProcedureToPostgreSql(procedureInfo, warnings, appliedRules)
            }
            else -> procedureSql
        }
    }

    /**
     * MySQL PROCEDURE/FUNCTION 문에서 정보 추출
     */
    private fun extractProcedureInfo(procedureSql: String): ProcedureInfo {
        val upperSql = procedureSql.uppercase()
        val isFunction = upperSql.contains("CREATE FUNCTION") || upperSql.contains("CREATE DEFINER") && upperSql.contains(" FUNCTION ")

        // 프로시저/함수 이름 추출
        val nameMatch = Regex(
            "CREATE\\s+(?:DEFINER\\s*=\\s*[^\\s]+\\s+)?(?:PROCEDURE|FUNCTION)\\s+[`\"]?(\\w+)[`\"]?",
            RegexOption.IGNORE_CASE
        ).find(procedureSql)
        val name = nameMatch?.groupValues?.get(1) ?: "UNNAMED_PROCEDURE"

        // 파라미터 추출
        val paramsMatch = Regex(
            "(?:PROCEDURE|FUNCTION)\\s+[`\"]?\\w+[`\"]?\\s*\\(([^)]*)\\)",
            RegexOption.IGNORE_CASE
        ).find(procedureSql)
        val paramsStr = paramsMatch?.groupValues?.get(1) ?: ""
        val parameters = parseParameters(paramsStr)

        // 반환 타입 추출 (FUNCTION인 경우)
        var returnType: String? = null
        if (isFunction) {
            val returnMatch = Regex(
                "RETURNS\\s+(\\w+(?:\\([^)]+\\))?)",
                RegexOption.IGNORE_CASE
            ).find(procedureSql)
            returnType = returnMatch?.groupValues?.get(1)
        }

        // 본문 추출 (BEGIN...END 사이)
        val bodyMatch = Regex(
            "BEGIN\\s+(.+?)\\s+END",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(procedureSql)
        val body = bodyMatch?.groupValues?.get(1)?.trim() ?: ""

        // 특성 추출 (DETERMINISTIC, NO SQL 등)
        val characteristics = mutableListOf<String>()
        if (upperSql.contains("DETERMINISTIC")) characteristics.add("DETERMINISTIC")
        if (upperSql.contains("NO SQL")) characteristics.add("NO SQL")
        if (upperSql.contains("READS SQL DATA")) characteristics.add("READS SQL DATA")
        if (upperSql.contains("MODIFIES SQL DATA")) characteristics.add("MODIFIES SQL DATA")

        return ProcedureInfo(
            name = name,
            parameters = parameters,
            body = body,
            returnType = returnType,
            isFunction = isFunction,
            characteristics = characteristics
        )
    }

    /**
     * 파라미터 문자열 파싱
     */
    private fun parseParameters(paramsStr: String): List<ProcedureParameter> {
        if (paramsStr.isBlank()) return emptyList()

        val params = mutableListOf<ProcedureParameter>()
        val paramParts = paramsStr.split(",").map { it.trim() }

        for (part in paramParts) {
            if (part.isBlank()) continue

            val tokens = part.split(Regex("\\s+"))
            if (tokens.isEmpty()) continue

            var mode = ProcedureParameter.ParameterMode.IN
            var nameIndex = 0

            // 모드 확인 (IN, OUT, INOUT)
            when (tokens[0].uppercase()) {
                "IN" -> { mode = ProcedureParameter.ParameterMode.IN; nameIndex = 1 }
                "OUT" -> { mode = ProcedureParameter.ParameterMode.OUT; nameIndex = 1 }
                "INOUT" -> { mode = ProcedureParameter.ParameterMode.INOUT; nameIndex = 1 }
            }

            if (nameIndex >= tokens.size) continue

            val paramName = tokens[nameIndex].trim('`', '"')
            val dataType = if (nameIndex + 1 < tokens.size) {
                tokens.drop(nameIndex + 1).joinToString(" ")
            } else {
                "VARCHAR(255)"
            }

            params.add(ProcedureParameter(
                name = paramName,
                mode = mode,
                dataType = dataType
            ))
        }

        return params
    }

    /**
     * MySQL PROCEDURE를 Oracle PL/SQL로 변환
     */
    private fun convertProcedureToOracle(
        procedureInfo: ProcedureInfo,
        schemaOwner: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()
        val objectType = if (procedureInfo.isFunction) "FUNCTION" else "PROCEDURE"

        result.append("CREATE OR REPLACE $objectType \"$schemaOwner\".\"${procedureInfo.name}\"")

        // 파라미터
        if (procedureInfo.parameters.isNotEmpty()) {
            result.appendLine(" (")
            val oracleParams = procedureInfo.parameters.map { param ->
                val oracleType = convertMySqlTypeToOracleType(param.dataType)
                val modeStr = when (param.mode) {
                    ProcedureParameter.ParameterMode.IN -> "IN"
                    ProcedureParameter.ParameterMode.OUT -> "OUT"
                    ProcedureParameter.ParameterMode.INOUT -> "IN OUT"
                }
                "    \"${param.name}\" $modeStr $oracleType"
            }
            result.append(oracleParams.joinToString(",\n"))
            result.appendLine()
            result.append(")")
        }

        // 반환 타입 (FUNCTION인 경우)
        if (procedureInfo.isFunction && procedureInfo.returnType != null) {
            result.appendLine()
            val oracleReturnType = convertMySqlTypeToOracleType(procedureInfo.returnType)
            result.append("RETURN $oracleReturnType")
        }

        result.appendLine()
        result.appendLine("IS")

        // 변수 선언부 추출 (DECLARE ... 부분)
        val bodyConverted = convertProcedureBodyToOracle(procedureInfo.body, warnings, appliedRules)

        result.appendLine("BEGIN")
        result.appendLine("    $bodyConverted")
        result.append("END;")

        appliedRules.add("CREATE ${objectType} MySQL → Oracle 형식으로 변환")
        appliedRules.add("DEFINER 옵션 제거")
        appliedRules.add("파라미터 타입 Oracle 형식으로 변환")

        return result.toString()
    }

    /**
     * MySQL PROCEDURE를 PostgreSQL PL/pgSQL로 변환
     */
    private fun convertProcedureToPostgreSql(
        procedureInfo: ProcedureInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()
        val objectType = if (procedureInfo.isFunction) "FUNCTION" else "PROCEDURE"

        result.append("CREATE OR REPLACE $objectType \"${procedureInfo.name}\"(")

        // 파라미터
        if (procedureInfo.parameters.isNotEmpty()) {
            result.appendLine()
            val pgParams = procedureInfo.parameters.map { param ->
                val pgType = convertMySqlTypeToPostgreSqlType(param.dataType)
                val modeStr = param.mode.name
                "    $modeStr \"${param.name}\" $pgType"
            }
            result.append(pgParams.joinToString(",\n"))
            result.appendLine()
        }

        result.append(")")

        // 반환 타입 (FUNCTION인 경우)
        if (procedureInfo.isFunction && procedureInfo.returnType != null) {
            result.appendLine()
            val pgReturnType = convertMySqlTypeToPostgreSqlType(procedureInfo.returnType)
            result.append("RETURNS $pgReturnType")
        }

        result.appendLine()
        result.appendLine("LANGUAGE plpgsql")
        result.appendLine("AS \$\$")

        // DECLARE 블록이 필요한 경우
        val (declares, bodyConverted) = convertProcedureBodyToPostgreSql(procedureInfo.body, warnings, appliedRules)
        if (declares.isNotEmpty()) {
            result.appendLine("DECLARE")
            result.appendLine("    $declares")
        }

        result.appendLine("BEGIN")
        result.appendLine("    $bodyConverted")
        result.appendLine("END;")
        result.append("\$\$;")

        appliedRules.add("CREATE ${objectType} MySQL → PostgreSQL 형식으로 변환")
        appliedRules.add("DEFINER 옵션 제거")
        appliedRules.add("파라미터 타입 PostgreSQL 형식으로 변환")

        return result.toString()
    }

    /**
     * MySQL 프로시저 본문을 Oracle PL/SQL로 변환
     */
    private fun convertProcedureBodyToOracle(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // 백틱 → 큰따옴표
        result = result.replace("`", "\"")

        // DECLARE 문 변환 (MySQL은 BEGIN 안에서, Oracle은 IS 이후에)
        result = result.replace(Regex("\\bDECLARE\\b", RegexOption.IGNORE_CASE), "-- Variables moved to IS section")

        // SET 문 변환 (SET var = value → var := value)
        result = result.replace(Regex("\\bSET\\s+(@?[a-zA-Z_][a-zA-Z0-9_]*)\\s*=", RegexOption.IGNORE_CASE)) { matchResult ->
            val varName = matchResult.groupValues[1].removePrefix("@")
            "$varName :="
        }

        // 함수 변환
        result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSDATE")
        result = result.replace(Regex("\\bCURRENT_TIMESTAMP\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSTIMESTAMP")
        result = result.replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
        result = result.replace(Regex("\\bCOALESCE\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
        result = result.replace(Regex("\\bCONCAT\\s*\\(", RegexOption.IGNORE_CASE), "CONCAT(")
        result = result.replace(Regex("\\bUUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYS_GUID()")
        result = result.replace(Regex("\\bLAST_INSERT_ID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "RETURNING INTO")

        // IF 문 변환 (MySQL IF...THEN...ELSEIF...ELSE...END IF)
        // Oracle과 구문 동일하므로 대부분 호환됨

        // WHILE 문 변환 (MySQL: WHILE...DO...END WHILE → Oracle: WHILE...LOOP...END LOOP)
        result = result.replace(Regex("\\bWHILE\\s+(.+?)\\s+DO\\b", RegexOption.IGNORE_CASE)) { matchResult ->
            "WHILE ${matchResult.groupValues[1]} LOOP"
        }
        result = result.replace(Regex("\\bEND\\s+WHILE\\b", RegexOption.IGNORE_CASE), "END LOOP")

        // REPEAT 문 변환 (MySQL: REPEAT...UNTIL...END REPEAT → Oracle: LOOP...EXIT WHEN...END LOOP)
        if (result.uppercase().contains("REPEAT")) {
            warnings.add(createWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL REPEAT...UNTIL 구문은 Oracle LOOP...EXIT WHEN으로 수동 변환이 필요합니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "LOOP ... EXIT WHEN condition; END LOOP; 형식으로 변경하세요."
            ))
        }

        // LEAVE/ITERATE 변환 (MySQL: LEAVE label → Oracle: EXIT; / ITERATE → CONTINUE)
        result = result.replace(Regex("\\bLEAVE\\s+(\\w+)\\b", RegexOption.IGNORE_CASE), "EXIT")
        result = result.replace(Regex("\\bITERATE\\s+(\\w+)\\b", RegexOption.IGNORE_CASE), "CONTINUE")

        // SIGNAL SQLSTATE 변환
        result = result.replace(
            Regex("SIGNAL\\s+SQLSTATE\\s+'[^']*'\\s+SET\\s+MESSAGE_TEXT\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE)
        ) { matchResult ->
            "RAISE_APPLICATION_ERROR(-20001, '${matchResult.groupValues[1]}')"
        }

        // CURSOR 변환
        // MySQL: DECLARE cursor_name CURSOR FOR SELECT...
        // Oracle: CURSOR cursor_name IS SELECT...
        result = result.replace(
            Regex("DECLARE\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+CURSOR\\s+FOR\\s+", RegexOption.IGNORE_CASE)
        ) { matchResult ->
            "CURSOR ${matchResult.groupValues[1]} IS "
        }

        // OPEN, FETCH, CLOSE는 Oracle에서도 동일

        appliedRules.add("프로시저 본문 Oracle PL/SQL 형식으로 변환")

        return result
    }

    /**
     * MySQL 프로시저 본문을 PostgreSQL PL/pgSQL로 변환
     * @return Pair(DECLARE 부분, 본문)
     */
    private fun convertProcedureBodyToPostgreSql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): Pair<String, String> {
        var result = body
        val declares = StringBuilder()

        // 백틱 → 큰따옴표
        result = result.replace("`", "\"")

        // DECLARE 문 추출 및 변환
        val declareMatches = Regex(
            "DECLARE\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+(\\w+(?:\\([^)]+\\))?)",
            RegexOption.IGNORE_CASE
        ).findAll(result)

        for (match in declareMatches) {
            val varName = match.groupValues[1]
            val varType = convertMySqlTypeToPostgreSqlType(match.groupValues[2])
            declares.appendLine("$varName $varType;")
        }

        // DECLARE 문 제거 (PostgreSQL은 BEGIN 전에 DECLARE 블록이 있음)
        result = result.replace(
            Regex("DECLARE\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s+\\w+(?:\\([^)]+\\))?\\s*;?", RegexOption.IGNORE_CASE),
            ""
        )

        // SET 문 변환 (SET var = value → var := value)
        result = result.replace(Regex("\\bSET\\s+(@?[a-zA-Z_][a-zA-Z0-9_]*)\\s*=", RegexOption.IGNORE_CASE)) { matchResult ->
            val varName = matchResult.groupValues[1].removePrefix("@")
            "$varName :="
        }

        // 함수 변환
        result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
        result = result.replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
        result = result.replace(Regex("\\bCONCAT\\s*\\(", RegexOption.IGNORE_CASE), "CONCAT(")
        result = result.replace(Regex("\\bUUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "gen_random_uuid()")

        // WHILE 문 변환 (MySQL: WHILE...DO...END WHILE → PostgreSQL: WHILE...LOOP...END LOOP)
        result = result.replace(Regex("\\bWHILE\\s+(.+?)\\s+DO\\b", RegexOption.IGNORE_CASE)) { matchResult ->
            "WHILE ${matchResult.groupValues[1]} LOOP"
        }
        result = result.replace(Regex("\\bEND\\s+WHILE\\b", RegexOption.IGNORE_CASE), "END LOOP")

        // LEAVE/ITERATE 변환
        result = result.replace(Regex("\\bLEAVE\\s+(\\w+)\\b", RegexOption.IGNORE_CASE), "EXIT")
        result = result.replace(Regex("\\bITERATE\\s+(\\w+)\\b", RegexOption.IGNORE_CASE), "CONTINUE")

        // SIGNAL SQLSTATE 변환
        result = result.replace(
            Regex("SIGNAL\\s+SQLSTATE\\s+'([^']*)'\\s+SET\\s+MESSAGE_TEXT\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE)
        ) { matchResult ->
            "RAISE EXCEPTION '${matchResult.groupValues[2]}' USING ERRCODE = '${matchResult.groupValues[1]}'"
        }

        // CURSOR 변환
        // MySQL: DECLARE cursor_name CURSOR FOR SELECT...
        // PostgreSQL: cursor_name CURSOR FOR SELECT... (DECLARE 블록에서)
        val cursorMatches = Regex(
            "DECLARE\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+CURSOR\\s+FOR\\s+(.+?)(?:;|$)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(body)

        for (match in cursorMatches) {
            val cursorName = match.groupValues[1]
            val cursorQuery = match.groupValues[2].trim()
            declares.appendLine("$cursorName CURSOR FOR $cursorQuery;")
        }

        result = result.replace(
            Regex("DECLARE\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s+CURSOR\\s+FOR\\s+.+?;", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ""
        )

        appliedRules.add("프로시저 본문 PostgreSQL PL/pgSQL 형식으로 변환")

        return Pair(declares.toString().trim(), result.trim())
    }

    /**
     * MySQL 데이터 타입을 Oracle 타입으로 변환 (프로시저용)
     */
    private fun convertMySqlTypeToOracleType(mysqlType: String): String {
        val upperType = mysqlType.uppercase().trim()
        return when {
            upperType.startsWith("VARCHAR") -> upperType.replace("VARCHAR", "VARCHAR2")
            upperType.startsWith("INT") -> "NUMBER"
            upperType == "BIGINT" -> "NUMBER(19)"
            upperType == "SMALLINT" -> "NUMBER(5)"
            upperType == "TINYINT" -> "NUMBER(3)"
            upperType.startsWith("DECIMAL") || upperType.startsWith("NUMERIC") -> upperType.replace(Regex("DECIMAL|NUMERIC"), "NUMBER")
            upperType == "DOUBLE" -> "BINARY_DOUBLE"
            upperType == "FLOAT" -> "BINARY_FLOAT"
            upperType == "TEXT" || upperType == "LONGTEXT" -> "CLOB"
            upperType == "BLOB" || upperType == "LONGBLOB" -> "BLOB"
            upperType == "DATETIME" || upperType == "TIMESTAMP" -> "TIMESTAMP"
            upperType == "DATE" -> "DATE"
            upperType == "BOOLEAN" || upperType == "BOOL" -> "NUMBER(1)"
            else -> upperType
        }
    }

    /**
     * MySQL 데이터 타입을 PostgreSQL 타입으로 변환 (프로시저용)
     */
    private fun convertMySqlTypeToPostgreSqlType(mysqlType: String): String {
        val upperType = mysqlType.uppercase().trim()
        return when {
            upperType.startsWith("INT") -> "INTEGER"
            upperType == "BIGINT" -> "BIGINT"
            upperType == "SMALLINT" -> "SMALLINT"
            upperType == "TINYINT" -> "SMALLINT"
            upperType.startsWith("VARCHAR") -> upperType
            upperType.startsWith("DECIMAL") || upperType.startsWith("NUMERIC") -> "NUMERIC${upperType.substringAfter("DECIMAL").substringAfter("NUMERIC")}"
            upperType == "DOUBLE" -> "DOUBLE PRECISION"
            upperType == "FLOAT" -> "REAL"
            upperType == "TEXT" || upperType == "LONGTEXT" -> "TEXT"
            upperType == "BLOB" || upperType == "LONGBLOB" -> "BYTEA"
            upperType == "DATETIME" || upperType == "TIMESTAMP" -> "TIMESTAMP"
            upperType == "DATE" -> "DATE"
            upperType == "BOOLEAN" || upperType == "BOOL" -> "BOOLEAN"
            else -> upperType
        }
    }

    /**
     * AUTO_INCREMENT 컬럼에 대한 SEQUENCE + TRIGGER 생성 (Oracle/Tibero용)
     */
    fun generateSequenceForAutoIncrement(
        tableName: String,
        columnName: String,
        schemaOwner: String,
        startWith: Long = 1
    ): String {
        val seqName = "SEQ_${tableName}_${columnName}".uppercase()
        val triggerName = "TRG_${tableName}_${columnName}_BI".uppercase()

        return """
-- SEQUENCE 생성
CREATE SEQUENCE "$schemaOwner"."$seqName"
    START WITH $startWith
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- AUTO_INCREMENT 트리거 생성
CREATE OR REPLACE TRIGGER "$schemaOwner"."$triggerName"
BEFORE INSERT ON "$schemaOwner"."$tableName"
FOR EACH ROW
BEGIN
    IF :NEW."$columnName" IS NULL THEN
        SELECT "$schemaOwner"."$seqName".NEXTVAL INTO :NEW."$columnName" FROM DUAL;
    END IF;
END;
/
        """.trimIndent()
    }

    /**
     * AUTO_INCREMENT 컬럼에 대한 SERIAL 변환 (PostgreSQL용)
     */
    fun generateSerialForAutoIncrement(
        tableName: String,
        columnName: String
    ): String {
        return """
-- PostgreSQL에서는 SERIAL 또는 GENERATED AS IDENTITY 사용
-- 컬럼 타입을 SERIAL로 변경하거나:
-- "$columnName" SERIAL PRIMARY KEY

-- 또는 시퀀스를 직접 생성:
CREATE SEQUENCE "${tableName}_${columnName}_seq";
ALTER TABLE "$tableName" ALTER COLUMN "$columnName" SET DEFAULT nextval('${tableName}_${columnName}_seq');
        """.trimIndent()
    }
}
