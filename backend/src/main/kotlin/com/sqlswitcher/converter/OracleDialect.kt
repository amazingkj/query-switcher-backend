package com.sqlswitcher.converter

import com.sqlswitcher.parser.model.AstAnalysisResult
import com.sqlswitcher.model.ConversionOptions
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.statement.select.Limit
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.drop.Drop
import net.sf.jsqlparser.expression.Function as SqlFunction

import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.expression.LongValue
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
            DialectType.MYSQL, DialectType.POSTGRESQL -> {
                // Oracle FETCH FIRST → MySQL/PostgreSQL LIMIT 변환
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

                // Oracle ROWNUM → LIMIT 변환은 복잡하여 경고만 제공
                if (selectBody.fetch == null && selectBody.limit == null) {
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Oracle ROWNUM 구문을 LIMIT으로 변환해야 합니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "LIMIT n OFFSET m 구문을 사용하세요."
                    ))
                    appliedRules.add("ROWNUM → LIMIT 수동 변환 필요")
                }
            }
            DialectType.TIBERO -> {
                // Oracle과 Tibero는 동일한 구문 사용
                appliedRules.add("Oracle 구문 유지")
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
        return when (targetDialect) {
            DialectType.MYSQL -> convertCreateTableToMySql(createTable, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertCreateTableToPostgreSql(createTable, warnings, appliedRules)
            DialectType.TIBERO -> {
                appliedRules.add("Oracle CREATE TABLE 구문 유지 (Tibero 호환)")
                createTable.toString()
            }
            else -> createTable.toString()
        }
    }

    /**
     * Oracle CREATE TABLE을 MySQL 형식으로 변환
     * - 큰따옴표(") → 백틱(`) 변환
     * - 데이터 타입 변환 (NUMBER → DECIMAL, VARCHAR2 → VARCHAR 등)
     * - 정밀도(precision) 보존
     * - COMMENT 인라인으로 변환
     * - DEFAULT 함수 변환 (SYSDATE → NOW())
     */
    private fun convertCreateTableToMySql(
        createTable: CreateTable,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()

        // 테이블명 변환 (큰따옴표 → 백틱, 스키마 제거)
        val rawTableName = createTable.table.name.trim('"').split(".").last()
        result.append("CREATE TABLE `$rawTableName` (\n")

        // 컬럼 정의 변환
        val columnDefs = mutableListOf<String>()
        createTable.columnDefinitions?.forEach { colDef ->
            val columnName = colDef.columnName.trim('"')

            // 데이터 타입 변환 (정밀도 포함)
            val oracleType = colDef.colDataType?.dataType?.uppercase() ?: "VARCHAR2"
            val typeArgs = colDef.colDataType?.argumentsStringList

            val mysqlType = convertOracleTypeToMySqlWithPrecision(oracleType, typeArgs)

            // 제약조건 및 DEFAULT 추출
            val constraints = mutableListOf<String>()
            var columnComment: String? = null

            colDef.columnSpecs?.forEachIndexed { index, spec ->
                val specStr = spec.toString().uppercase()
                when {
                    specStr == "NOT" -> {
                        // 다음이 NULL인지 확인
                        val nextSpec = colDef.columnSpecs?.getOrNull(index + 1)?.toString()?.uppercase()
                        if (nextSpec == "NULL") {
                            constraints.add("NOT NULL")
                        }
                    }
                    specStr == "NULL" -> {
                        // 이전이 NOT이면 이미 처리됨
                        val prevSpec = colDef.columnSpecs?.getOrNull(index - 1)?.toString()?.uppercase()
                        if (prevSpec != "NOT") {
                            // nullable (MySQL에서는 기본값)
                        }
                    }
                    specStr == "DEFAULT" -> {
                        // 다음 값이 DEFAULT 값
                        val defaultValue = colDef.columnSpecs?.getOrNull(index + 1)?.toString()
                        if (defaultValue != null) {
                            val convertedDefault = convertOracleDefaultToMySql(defaultValue, warnings)
                            constraints.add("DEFAULT $convertedDefault")
                        }
                    }
                    specStr == "GENERATED" -> {
                        // GENERATED AS IDENTITY → AUTO_INCREMENT
                        warnings.add(createWarning(
                            type = WarningType.SYNTAX_DIFFERENCE,
                            message = "Oracle GENERATED AS IDENTITY는 MySQL AUTO_INCREMENT로 변환됩니다.",
                            severity = WarningSeverity.INFO,
                            suggestion = "AUTO_INCREMENT를 사용하세요."
                        ))
                        constraints.add("AUTO_INCREMENT")
                    }
                    else -> {}
                }
            }

            val constraintStr = if (constraints.isNotEmpty()) " ${constraints.joinToString(" ")}" else ""
            val commentStr = if (columnComment != null) " COMMENT '$columnComment'" else ""
            columnDefs.add("    `$columnName` $mysqlType$constraintStr$commentStr")
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

        appliedRules.add("CREATE TABLE Oracle → MySQL 형식으로 변환")
        appliedRules.add("큰따옴표(\") → 백틱(`) 변환")
        appliedRules.add("데이터 타입 MySQL 형식으로 변환 (정밀도 보존)")

        return result.toString()
    }

    /**
     * Oracle CREATE TABLE을 PostgreSQL 형식으로 변환
     */
    private fun convertCreateTableToPostgreSql(
        createTable: CreateTable,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()

        // 테이블명 (큰따옴표 유지, 스키마 제거)
        val rawTableName = createTable.table.name.trim('"').split(".").last()
        result.append("CREATE TABLE \"$rawTableName\" (\n")

        // 컬럼 정의 변환
        val columnDefs = mutableListOf<String>()
        createTable.columnDefinitions?.forEach { colDef ->
            val columnName = colDef.columnName.trim('"')

            // 데이터 타입 변환 (정밀도 포함)
            val oracleType = colDef.colDataType?.dataType?.uppercase() ?: "VARCHAR2"
            val typeArgs = colDef.colDataType?.argumentsStringList

            val pgType = convertOracleTypeToPostgreSqlWithPrecision(oracleType, typeArgs)

            // 제약조건 및 DEFAULT 추출
            val constraints = mutableListOf<String>()

            colDef.columnSpecs?.forEachIndexed { index, spec ->
                val specStr = spec.toString().uppercase()
                when {
                    specStr == "NOT" -> {
                        val nextSpec = colDef.columnSpecs?.getOrNull(index + 1)?.toString()?.uppercase()
                        if (nextSpec == "NULL") {
                            constraints.add("NOT NULL")
                        }
                    }
                    specStr == "NULL" -> {
                        val prevSpec = colDef.columnSpecs?.getOrNull(index - 1)?.toString()?.uppercase()
                        if (prevSpec != "NOT") {
                            // nullable
                        }
                    }
                    specStr == "DEFAULT" -> {
                        val defaultValue = colDef.columnSpecs?.getOrNull(index + 1)?.toString()
                        if (defaultValue != null) {
                            val convertedDefault = convertOracleDefaultToPostgreSql(defaultValue, warnings)
                            constraints.add("DEFAULT $convertedDefault")
                        }
                    }
                    specStr == "GENERATED" -> {
                        // PostgreSQL은 GENERATED 지원
                        constraints.add("GENERATED BY DEFAULT AS IDENTITY")
                    }
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

        appliedRules.add("CREATE TABLE Oracle → PostgreSQL 형식으로 변환")
        appliedRules.add("데이터 타입 PostgreSQL 형식으로 변환 (정밀도 보존)")

        return result.toString()
    }

    /**
     * Oracle 데이터 타입을 MySQL 데이터 타입으로 변환 (정밀도 포함)
     */
    private fun convertOracleTypeToMySqlWithPrecision(oracleType: String, args: List<String>?): String {
        val precision = args?.firstOrNull()?.toIntOrNull()
        val scale = args?.getOrNull(1)?.toIntOrNull()

        return when (oracleType.uppercase()) {
            "NUMBER" -> {
                when {
                    precision == null && scale == null -> "DECIMAL"
                    scale == null || scale == 0 -> {
                        when {
                            precision == null -> "DECIMAL"
                            precision <= 3 -> "TINYINT"
                            precision <= 5 -> "SMALLINT"
                            precision <= 7 -> "MEDIUMINT"
                            precision <= 10 -> "INT"
                            precision <= 19 -> "BIGINT"
                            else -> "DECIMAL($precision)"
                        }
                    }
                    else -> "DECIMAL($precision,$scale)"
                }
            }
            "VARCHAR2", "NVARCHAR2" -> {
                val size = precision ?: 255
                "VARCHAR($size)"
            }
            "CHAR", "NCHAR" -> {
                val size = precision ?: 1
                "CHAR($size)"
            }
            "CLOB", "NCLOB", "LONG" -> "LONGTEXT"
            "BLOB" -> "LONGBLOB"
            "RAW" -> {
                val size = precision ?: 255
                "VARBINARY($size)"
            }
            "LONG RAW" -> "LONGBLOB"
            "DATE" -> "DATETIME"
            "TIMESTAMP" -> if (precision != null && precision > 0) "DATETIME($precision)" else "DATETIME"
            "TIMESTAMP WITH TIME ZONE", "TIMESTAMP WITH LOCAL TIME ZONE" -> "DATETIME"
            "INTERVAL YEAR TO MONTH" -> "VARCHAR(20)"
            "INTERVAL DAY TO SECOND" -> "VARCHAR(30)"
            "BINARY_FLOAT" -> "FLOAT"
            "BINARY_DOUBLE" -> "DOUBLE"
            "ROWID", "UROWID" -> "VARCHAR(18)"
            "XMLType" -> "LONGTEXT"
            "BFILE" -> "VARCHAR(255)"
            else -> oracleType
        }
    }

    /**
     * Oracle 데이터 타입을 PostgreSQL 데이터 타입으로 변환 (정밀도 포함)
     */
    private fun convertOracleTypeToPostgreSqlWithPrecision(oracleType: String, args: List<String>?): String {
        val precision = args?.firstOrNull()?.toIntOrNull()
        val scale = args?.getOrNull(1)?.toIntOrNull()

        return when (oracleType.uppercase()) {
            "NUMBER" -> {
                when {
                    precision == null && scale == null -> "NUMERIC"
                    scale == null || scale == 0 -> {
                        when {
                            precision == null -> "NUMERIC"
                            precision <= 5 -> "SMALLINT"
                            precision <= 10 -> "INTEGER"
                            precision <= 19 -> "BIGINT"
                            else -> "NUMERIC($precision)"
                        }
                    }
                    else -> "NUMERIC($precision,$scale)"
                }
            }
            "VARCHAR2", "NVARCHAR2" -> {
                val size = precision ?: 255
                "VARCHAR($size)"
            }
            "CHAR", "NCHAR" -> {
                val size = precision ?: 1
                "CHAR($size)"
            }
            "CLOB", "NCLOB", "LONG" -> "TEXT"
            "BLOB" -> "BYTEA"
            "RAW" -> "BYTEA"
            "LONG RAW" -> "BYTEA"
            "DATE" -> "TIMESTAMP"
            "TIMESTAMP" -> if (precision != null) "TIMESTAMP($precision)" else "TIMESTAMP"
            "TIMESTAMP WITH TIME ZONE" -> "TIMESTAMPTZ"
            "TIMESTAMP WITH LOCAL TIME ZONE" -> "TIMESTAMP"
            "INTERVAL YEAR TO MONTH" -> "INTERVAL"
            "INTERVAL DAY TO SECOND" -> "INTERVAL"
            "BINARY_FLOAT" -> "REAL"
            "BINARY_DOUBLE" -> "DOUBLE PRECISION"
            "ROWID", "UROWID" -> "VARCHAR(18)"
            "XMLType" -> "XML"
            "BFILE" -> "VARCHAR(255)"
            else -> oracleType
        }
    }

    /**
     * Oracle DEFAULT 값을 MySQL 형식으로 변환
     */
    private fun convertOracleDefaultToMySql(defaultValue: String, warnings: MutableList<ConversionWarning>): String {
        val upperValue = defaultValue.uppercase().trim()
        return when {
            upperValue == "SYSDATE" || upperValue == "CURRENT_TIMESTAMP" -> "CURRENT_TIMESTAMP"
            upperValue == "SYSTIMESTAMP" -> "CURRENT_TIMESTAMP"
            upperValue.startsWith("SYS_GUID()") -> {
                warnings.add(createWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "Oracle SYS_GUID()는 MySQL에서 UUID()로 변환됩니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "UUID() 또는 다른 방식을 사용하세요."
                ))
                "UUID()"
            }
            upperValue.startsWith("USER") -> "CURRENT_USER"
            else -> defaultValue
        }
    }

    /**
     * Oracle DEFAULT 값을 PostgreSQL 형식으로 변환
     */
    private fun convertOracleDefaultToPostgreSql(defaultValue: String, warnings: MutableList<ConversionWarning>): String {
        val upperValue = defaultValue.uppercase().trim()
        return when {
            upperValue == "SYSDATE" -> "CURRENT_TIMESTAMP"
            upperValue == "SYSTIMESTAMP" || upperValue == "CURRENT_TIMESTAMP" -> "CURRENT_TIMESTAMP"
            upperValue.startsWith("SYS_GUID()") -> {
                warnings.add(createWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "Oracle SYS_GUID()는 PostgreSQL에서 gen_random_uuid()로 변환됩니다.",
                    severity = WarningSeverity.INFO,
                    suggestion = "pgcrypto 확장이 필요합니다."
                ))
                "gen_random_uuid()"
            }
            upperValue.startsWith("USER") -> "CURRENT_USER"
            else -> defaultValue
        }
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
