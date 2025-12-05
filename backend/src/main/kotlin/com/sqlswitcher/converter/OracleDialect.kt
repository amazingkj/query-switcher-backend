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

    /**
     * Oracle SELECT 쿼리 문자열을 다른 방언으로 변환 (ROWNUM, DECODE 포함)
     */
    fun convertSelectQueryFromString(
        selectSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = selectSql

        when (targetDialect) {
            DialectType.MYSQL, DialectType.POSTGRESQL -> {
                // DECODE → CASE WHEN 변환
                result = convertDecodeToCase(result, warnings, appliedRules)

                // NVL2 → CASE WHEN 변환
                result = convertNvl2ToCase(result, warnings, appliedRules)

                // ROWNUM → LIMIT/ROW_NUMBER() 변환
                result = convertRownumToLimit(result, targetDialect, warnings, appliedRules)

                // 함수 변환
                result = convertOracleFunctionsInQuery(result, targetDialect, warnings, appliedRules)

                // 인용 문자 변환
                if (targetDialect == DialectType.MYSQL) {
                    result = result.replace("\"", "`")
                    appliedRules.add("큰따옴표(\") → 백틱(`) 변환")
                }
            }
            DialectType.TIBERO -> {
                // Tibero는 Oracle과 호환
                appliedRules.add("Oracle 구문 유지 (Tibero 호환)")
            }
            else -> { }
        }

        return result
    }

    /**
     * Oracle DECODE 함수를 CASE WHEN 구문으로 변환
     * DECODE(expr, search1, result1, search2, result2, ..., default)
     * → CASE expr WHEN search1 THEN result1 WHEN search2 THEN result2 ... ELSE default END
     */
    private fun convertDecodeToCase(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // DECODE 함수 패턴 매칭 (중첩 괄호 처리를 위한 재귀적 파싱)
        val decodePattern = Regex("\\bDECODE\\s*\\(", RegexOption.IGNORE_CASE)
        var match = decodePattern.find(result)

        while (match != null) {
            val startIndex = match.range.first
            val argsStartIndex = match.range.last + 1

            // 괄호 매칭으로 DECODE 인자 추출
            val args = extractFunctionArguments(result, argsStartIndex - 1)
            if (args.isNotEmpty()) {
                val endIndex = findMatchingParenthesis(result, argsStartIndex - 1)
                if (endIndex > argsStartIndex) {
                    val caseExpr = convertDecodeToCaseExpression(args)
                    result = result.substring(0, startIndex) + caseExpr + result.substring(endIndex + 1)
                }
            }

            match = decodePattern.find(result, startIndex + 1)
        }

        if (result != sql) {
            appliedRules.add("DECODE() → CASE WHEN 변환 완료")
        }

        return result
    }

    /**
     * DECODE 인자를 CASE WHEN 표현식으로 변환
     */
    private fun convertDecodeToCaseExpression(args: List<String>): String {
        if (args.isEmpty()) return "NULL"

        val expr = args[0].trim()
        val sb = StringBuilder("CASE ")

        // 단순 CASE 형식 (검색값이 동등 비교인 경우)
        var hasDefault = false
        var i = 1
        while (i < args.size) {
            if (i + 1 < args.size) {
                // search, result 쌍
                val search = args[i].trim()
                val resultVal = args[i + 1].trim()

                // NULL 검색인 경우 특별 처리
                if (search.uppercase() == "NULL") {
                    sb.append("WHEN $expr IS NULL THEN $resultVal ")
                } else {
                    sb.append("WHEN $expr = $search THEN $resultVal ")
                }
                i += 2
            } else {
                // 마지막 하나가 남으면 default
                sb.append("ELSE ${args[i].trim()} ")
                hasDefault = true
                i++
            }
        }

        sb.append("END")
        return sb.toString()
    }

    /**
     * Oracle NVL2 함수를 CASE WHEN 구문으로 변환
     * NVL2(expr, not_null_value, null_value)
     * → CASE WHEN expr IS NOT NULL THEN not_null_value ELSE null_value END
     */
    private fun convertNvl2ToCase(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        val nvl2Pattern = Regex("\\bNVL2\\s*\\(", RegexOption.IGNORE_CASE)
        var match = nvl2Pattern.find(result)

        while (match != null) {
            val startIndex = match.range.first
            val argsStartIndex = match.range.last + 1

            val args = extractFunctionArguments(result, argsStartIndex - 1)
            if (args.size >= 3) {
                val endIndex = findMatchingParenthesis(result, argsStartIndex - 1)
                if (endIndex > argsStartIndex) {
                    val expr = args[0].trim()
                    val notNullVal = args[1].trim()
                    val nullVal = args[2].trim()

                    val caseExpr = "CASE WHEN $expr IS NOT NULL THEN $notNullVal ELSE $nullVal END"
                    result = result.substring(0, startIndex) + caseExpr + result.substring(endIndex + 1)
                }
            }

            match = nvl2Pattern.find(result, startIndex + 1)
        }

        if (result != sql) {
            appliedRules.add("NVL2() → CASE WHEN 변환 완료")
        }

        return result
    }

    /**
     * Oracle ROWNUM을 LIMIT 또는 ROW_NUMBER()로 변환
     * - WHERE ROWNUM <= n → LIMIT n
     * - WHERE ROWNUM = 1 → LIMIT 1
     * - 복잡한 ROWNUM 사용 → ROW_NUMBER() OVER() 권장
     */
    private fun convertRownumToLimit(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql
        val upperSql = sql.uppercase()

        // 패턴 1: WHERE ROWNUM <= n 또는 WHERE ROWNUM < n
        val rownumLePattern = Regex(
            "WHERE\\s+ROWNUM\\s*<=?\\s*(\\d+)",
            RegexOption.IGNORE_CASE
        )
        val leMatch = rownumLePattern.find(result)
        if (leMatch != null) {
            val limitValue = leMatch.groupValues[1].toIntOrNull() ?: 10
            val actualLimit = if (leMatch.value.contains("<") && !leMatch.value.contains("<=")) {
                limitValue - 1
            } else {
                limitValue
            }

            // WHERE ROWNUM <= n 제거하고 LIMIT 추가
            result = result.replace(leMatch.value, "")
            // 남은 AND/OR 정리
            result = result.replace(Regex("\\bWHERE\\s+AND\\b", RegexOption.IGNORE_CASE), "WHERE")
            result = result.replace(Regex("\\bAND\\s+AND\\b", RegexOption.IGNORE_CASE), "AND")
            result = result.trim()
            if (result.uppercase().endsWith("WHERE")) {
                result = result.dropLast(5).trim()
            }

            // LIMIT 추가
            result = "$result LIMIT $actualLimit"
            appliedRules.add("WHERE ROWNUM <= $limitValue → LIMIT $actualLimit 변환")
        }

        // 패턴 2: AND ROWNUM <= n (WHERE 절 중간에 있는 경우)
        val andRownumPattern = Regex(
            "AND\\s+ROWNUM\\s*<=?\\s*(\\d+)",
            RegexOption.IGNORE_CASE
        )
        val andMatch = andRownumPattern.find(result)
        if (andMatch != null) {
            val limitValue = andMatch.groupValues[1].toIntOrNull() ?: 10
            val actualLimit = if (andMatch.value.contains("<") && !andMatch.value.contains("<=")) {
                limitValue - 1
            } else {
                limitValue
            }

            result = result.replace(andMatch.value, "")
            result = "$result LIMIT $actualLimit"
            appliedRules.add("AND ROWNUM <= $limitValue → LIMIT $actualLimit 변환")
        }

        // 패턴 3: ROWNUM = 1
        val rownumEqPattern = Regex(
            "WHERE\\s+ROWNUM\\s*=\\s*1",
            RegexOption.IGNORE_CASE
        )
        if (rownumEqPattern.containsMatchIn(result)) {
            result = result.replace(rownumEqPattern, "")
            result = result.replace(Regex("\\bWHERE\\s+AND\\b", RegexOption.IGNORE_CASE), "WHERE")
            result = result.trim()
            if (result.uppercase().endsWith("WHERE")) {
                result = result.dropLast(5).trim()
            }
            result = "$result LIMIT 1"
            appliedRules.add("WHERE ROWNUM = 1 → LIMIT 1 변환")
        }

        // 패턴 4: 서브쿼리 내의 ROWNUM (페이징)
        // SELECT * FROM (SELECT ..., ROWNUM rn FROM ...) WHERE rn BETWEEN x AND y
        val pagingPattern = Regex(
            "ROWNUM\\s+(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)",
            RegexOption.IGNORE_CASE
        )
        if (pagingPattern.containsMatchIn(result)) {
            warnings.add(createWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "Oracle ROWNUM 기반 페이징은 LIMIT/OFFSET 또는 ROW_NUMBER()로 변환이 필요합니다.",
                severity = WarningSeverity.WARNING,
                suggestion = when (targetDialect) {
                    DialectType.MYSQL -> "SELECT * FROM table LIMIT offset, count 형식을 사용하세요."
                    DialectType.POSTGRESQL -> "SELECT * FROM table LIMIT count OFFSET offset 형식을 사용하세요."
                    else -> "LIMIT/OFFSET 구문을 사용하세요."
                }
            ))
        }

        // 패턴 5: SELECT 절에서 ROWNUM 사용 (행 번호 할당)
        val selectRownumPattern = Regex(
            "SELECT\\s+(.*)\\bROWNUM\\b",
            RegexOption.IGNORE_CASE
        )
        if (selectRownumPattern.containsMatchIn(result) && !result.uppercase().contains("WHERE ROWNUM")) {
            warnings.add(createWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "SELECT 절의 ROWNUM은 ROW_NUMBER() OVER()로 변환해야 합니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "ROW_NUMBER() OVER(ORDER BY column) AS rn 형식을 사용하세요."
            ))

            // 간단한 경우 자동 변환 시도
            result = result.replace(Regex("\\bROWNUM\\b(?!\\s*<=?|\\s*=)", RegexOption.IGNORE_CASE), "ROW_NUMBER() OVER() AS rn")
            if (result.contains("ROW_NUMBER()")) {
                appliedRules.add("SELECT ROWNUM → ROW_NUMBER() OVER() 변환")
            }
        }

        return result
    }

    /**
     * Oracle 함수를 타겟 방언 함수로 변환
     */
    private fun convertOracleFunctionsInQuery(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.MYSQL -> {
                result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "NOW()")
                result = result.replace(Regex("\\bSYSTIMESTAMP\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "IFNULL(")
                result = result.replace(Regex("\\bTO_CHAR\\s*\\(", RegexOption.IGNORE_CASE), "DATE_FORMAT(")
                result = result.replace(Regex("\\bTO_DATE\\s*\\(", RegexOption.IGNORE_CASE), "STR_TO_DATE(")
                result = result.replace(Regex("\\bLISTAGG\\s*\\(", RegexOption.IGNORE_CASE), "GROUP_CONCAT(")
                result = result.replace(Regex("\\bSYS_GUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "UUID()")
                result = result.replace(Regex("\\bDBMS_RANDOM\\.VALUE\\b", RegexOption.IGNORE_CASE), "RAND()")

                // SUBSTR → SUBSTRING (MySQL도 SUBSTR 지원하지만 표준화)
                // INSTR 유지 (MySQL도 지원)

                appliedRules.add("Oracle 함수 → MySQL 함수 변환")
            }
            DialectType.POSTGRESQL -> {
                result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                result = result.replace(Regex("\\bSYSTIMESTAMP\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
                // TO_CHAR는 PostgreSQL도 지원
                result = result.replace(Regex("\\bTO_DATE\\s*\\(", RegexOption.IGNORE_CASE), "TO_TIMESTAMP(")
                result = result.replace(Regex("\\bLISTAGG\\s*\\(", RegexOption.IGNORE_CASE), "STRING_AGG(")
                result = result.replace(Regex("\\bSYS_GUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "gen_random_uuid()")
                result = result.replace(Regex("\\bDBMS_RANDOM\\.VALUE\\b", RegexOption.IGNORE_CASE), "RANDOM()")

                // INSTR → POSITION 또는 STRPOS
                result = result.replace(Regex("\\bINSTR\\s*\\(([^,]+),\\s*([^)]+)\\)", RegexOption.IGNORE_CASE)) { matchResult ->
                    val str = matchResult.groupValues[1].trim()
                    val substr = matchResult.groupValues[2].trim()
                    "POSITION($substr IN $str)"
                }

                appliedRules.add("Oracle 함수 → PostgreSQL 함수 변환")
            }
            else -> { }
        }

        return result
    }

    /**
     * 함수의 인자를 추출 (괄호 중첩 처리)
     */
    private fun extractFunctionArguments(sql: String, openParenIndex: Int): List<String> {
        val args = mutableListOf<String>()
        var depth = 0
        var currentArg = StringBuilder()
        var inString = false
        var stringChar = ' '

        for (i in openParenIndex until sql.length) {
            val c = sql[i]

            // 문자열 리터럴 처리
            if ((c == '\'' || c == '"') && (i == 0 || sql[i - 1] != '\\')) {
                if (!inString) {
                    inString = true
                    stringChar = c
                } else if (c == stringChar) {
                    inString = false
                }
            }

            if (!inString) {
                when (c) {
                    '(' -> {
                        depth++
                        if (depth > 1) currentArg.append(c)
                    }
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            if (currentArg.isNotBlank()) {
                                args.add(currentArg.toString().trim())
                            }
                            return args
                        }
                        currentArg.append(c)
                    }
                    ',' -> {
                        if (depth == 1) {
                            args.add(currentArg.toString().trim())
                            currentArg = StringBuilder()
                        } else {
                            currentArg.append(c)
                        }
                    }
                    else -> {
                        if (depth >= 1) currentArg.append(c)
                    }
                }
            } else {
                if (depth >= 1) currentArg.append(c)
            }
        }

        return args
    }

    /**
     * 매칭되는 닫는 괄호 위치 찾기
     */
    private fun findMatchingParenthesis(sql: String, openParenIndex: Int): Int {
        var depth = 0
        var inString = false
        var stringChar = ' '

        for (i in openParenIndex until sql.length) {
            val c = sql[i]

            // 문자열 리터럴 처리
            if ((c == '\'' || c == '"') && (i == 0 || sql[i - 1] != '\\')) {
                if (!inString) {
                    inString = true
                    stringChar = c
                } else if (c == stringChar) {
                    inString = false
                }
            }

            if (!inString) {
                when (c) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
        }

        return -1
    }

    /**
     * 재귀 CTE (WITH RECURSIVE) 변환
     * Oracle 12c+에서는 WITH RECURSIVE 지원, 이전 버전에서는 CONNECT BY 사용
     */
    fun convertRecursiveCteFromString(
        cteSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = cteSql

        when (targetDialect) {
            DialectType.MYSQL -> {
                // MySQL 8.0+에서 WITH RECURSIVE 지원
                // Oracle WITH → MySQL WITH RECURSIVE
                if (!result.uppercase().contains("WITH RECURSIVE")) {
                    result = result.replaceFirst(Regex("\\bWITH\\b", RegexOption.IGNORE_CASE), "WITH RECURSIVE")
                    appliedRules.add("WITH → WITH RECURSIVE 변환 (MySQL 8.0+)")
                }

                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "MySQL 8.0 이상에서만 WITH RECURSIVE를 지원합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "MySQL 버전을 확인하세요. 5.7 이하에서는 저장 프로시저를 사용해야 합니다."
                ))

                // 인용 문자 변환
                result = result.replace("\"", "`")
            }
            DialectType.POSTGRESQL -> {
                // PostgreSQL은 WITH RECURSIVE 완전 지원
                if (!result.uppercase().contains("WITH RECURSIVE")) {
                    result = result.replaceFirst(Regex("\\bWITH\\b", RegexOption.IGNORE_CASE), "WITH RECURSIVE")
                    appliedRules.add("WITH → WITH RECURSIVE 변환")
                }
            }
            DialectType.TIBERO -> {
                // Tibero는 Oracle 호환
                appliedRules.add("Oracle 구문 유지 (Tibero 호환)")
            }
            else -> { }
        }

        return result
    }

    /**
     * Oracle CONNECT BY 계층형 쿼리를 WITH RECURSIVE로 변환
     */
    fun convertConnectByToRecursiveCte(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val upperSql = sql.uppercase()

        // CONNECT BY 패턴 확인
        if (!upperSql.contains("CONNECT BY")) {
            return sql
        }

        warnings.add(createWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "Oracle CONNECT BY 계층형 쿼리는 WITH RECURSIVE로 수동 변환이 필요합니다.",
            severity = WarningSeverity.WARNING,
            suggestion = """
                WITH RECURSIVE cte AS (
                    -- 앵커: START WITH 조건에 해당하는 행 선택
                    SELECT ... FROM table WHERE start_condition
                    UNION ALL
                    -- 재귀: CONNECT BY 조건에 따라 하위 행 선택
                    SELECT ... FROM table t JOIN cte c ON t.parent = c.id
                )
                SELECT * FROM cte;
            """.trimIndent()
        ))

        // START WITH 추출
        val startWithMatch = Regex("START\\s+WITH\\s+(.+?)(?=CONNECT|ORDER|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(sql)
        val startWithCondition = startWithMatch?.groupValues?.get(1)?.trim()

        // CONNECT BY 추출
        val connectByMatch = Regex("CONNECT\\s+BY\\s+(?:NOCYCLE\\s+)?(?:PRIOR\\s+)?(.+?)(?=ORDER|START|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(sql)
        val connectByCondition = connectByMatch?.groupValues?.get(1)?.trim()

        if (startWithCondition != null && connectByCondition != null) {
            appliedRules.add("CONNECT BY 계층형 쿼리 감지 (수동 변환 필요)")

            // 변환 힌트 제공
            val hint = """
-- 원본 Oracle CONNECT BY 쿼리:
-- START WITH: $startWithCondition
-- CONNECT BY: $connectByCondition

-- 변환된 WITH RECURSIVE 구조 (수동 조정 필요):
-- WITH RECURSIVE hierarchy AS (
--     SELECT *, 1 as level FROM table WHERE $startWithCondition
--     UNION ALL
--     SELECT t.*, h.level + 1 FROM table t JOIN hierarchy h ON $connectByCondition
-- )
-- SELECT * FROM hierarchy;

$sql
            """.trimIndent()

            return hint
        }

        return sql
    }

    // ==================== Phase 3: 고급 SQL 기능 변환 ====================

    /**
     * Oracle 함수 기반 인덱스를 다른 방언으로 변환
     * 예: CREATE INDEX idx_name ON table (UPPER(column))
     */
    fun convertFunctionBasedIndex(
        indexSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: com.sqlswitcher.model.ConversionOptions?
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
                appliedRules.add("Oracle 함수 기반 인덱스 → MySQL 형식으로 변환")
                indexInfo.toMySql()
            }
            DialectType.POSTGRESQL -> {
                appliedRules.add("Oracle 함수 기반 인덱스 → PostgreSQL 형식으로 변환")
                indexInfo.toPostgreSql()
            }
            DialectType.TIBERO -> {
                appliedRules.add("Oracle 함수 기반 인덱스 유지 (Tibero 호환)")
                indexSql
            }
            DialectType.ORACLE -> indexSql
        }
    }

    /**
     * Oracle 함수 기반 인덱스 정보 추출
     */
    private fun extractFunctionBasedIndexInfo(sql: String): FunctionBasedIndexInfo {
        val upperSql = sql.uppercase()
        val isUnique = upperSql.contains("CREATE UNIQUE INDEX")

        // 인덱스명 추출 (스키마.인덱스명 형식 처리)
        val indexNameRegex = if (isUnique) {
            """CREATE\s+UNIQUE\s+INDEX\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        } else {
            """CREATE\s+INDEX\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        }
        val indexNameMatch = indexNameRegex.find(sql)
        val indexName = indexNameMatch?.groupValues?.get(2) ?:
            Regex("""CREATE\s+(?:UNIQUE\s+)?INDEX\s+"?(\w+)"?""", RegexOption.IGNORE_CASE)
                .find(sql)?.groupValues?.get(1) ?: "UNKNOWN_INDEX"

        // 테이블명 추출 (스키마.테이블명 형식 처리)
        val tableNameRegex = """ON\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        val tableNameMatch = tableNameRegex.find(sql)
        val tableName = tableNameMatch?.groupValues?.get(2) ?:
            Regex("""ON\s+"?(\w+)"?""", RegexOption.IGNORE_CASE)
                .find(sql)?.groupValues?.get(1) ?: "UNKNOWN_TABLE"

        // 표현식 추출 (괄호 안의 내용)
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
     * Oracle Materialized View를 다른 방언으로 변환
     */
    fun convertMaterializedView(
        mvSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: com.sqlswitcher.model.ConversionOptions?
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
                appliedRules.add("Oracle Materialized View → MySQL 시뮬레이션 (테이블 + 프로시저)")
                mvInfo.toMySql(warnings)
            }
            DialectType.POSTGRESQL -> {
                appliedRules.add("Oracle Materialized View → PostgreSQL 형식으로 변환")
                mvInfo.toPostgreSql(warnings)
            }
            DialectType.TIBERO -> {
                appliedRules.add("Oracle Materialized View 유지 (Tibero 호환)")
                mvSql
            }
            DialectType.ORACLE -> mvSql
        }
    }

    /**
     * Oracle Materialized View 정보 추출
     */
    private fun extractMaterializedViewInfo(sql: String): MaterializedViewInfo {
        val upperSql = sql.uppercase()

        // 뷰명 추출 (스키마.뷰명 형식 처리)
        val viewNameRegex = """CREATE\s+MATERIALIZED\s+VIEW\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        val viewNameMatch = viewNameRegex.find(sql)
        val viewName = viewNameMatch?.groupValues?.get(2) ?:
            Regex("""CREATE\s+MATERIALIZED\s+VIEW\s+"?(\w+)"?""", RegexOption.IGNORE_CASE)
                .find(sql)?.groupValues?.get(1) ?: "UNKNOWN_MV"

        // BUILD 옵션 추출
        val buildOption = when {
            upperSql.contains("BUILD DEFERRED") -> MaterializedViewInfo.BuildOption.DEFERRED
            upperSql.contains("BUILD IMMEDIATE") -> MaterializedViewInfo.BuildOption.IMMEDIATE
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

        // ENABLE QUERY REWRITE 추출
        val enableQueryRewrite = upperSql.contains("ENABLE QUERY REWRITE")

        // SELECT 쿼리 추출
        val selectRegex = """AS\s+(SELECT.+)$""".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val selectQuery = selectRegex.find(sql)?.groupValues?.get(1)?.trim()?.trimEnd(';') ?: ""

        return MaterializedViewInfo(
            viewName = viewName,
            selectQuery = selectQuery,
            buildOption = buildOption,
            refreshOption = refreshOption,
            enableQueryRewrite = enableQueryRewrite
        )
    }

    /**
     * Oracle 파티션 테이블을 다른 방언으로 변환
     */
    fun convertPartitionTable(
        partitionSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: com.sqlswitcher.model.ConversionOptions?
    ): String {
        val partitionInfo = extractTablePartitionInfo(partitionSql)

        return when (targetDialect) {
            DialectType.MYSQL -> {
                appliedRules.add("Oracle 파티션 테이블 → MySQL 형식으로 변환")
                partitionInfo.toMySqlPartitionClause()
            }
            DialectType.POSTGRESQL -> {
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "PostgreSQL은 파티션을 별도의 테이블로 생성해야 합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "각 파티션에 대해 CREATE TABLE ... PARTITION OF 구문을 사용하세요."
                ))
                appliedRules.add("Oracle 파티션 테이블 → PostgreSQL 형식으로 변환")
                val mainTable = partitionInfo.toPostgreSqlPartitionClause()
                val partitionTables = partitionInfo.toPostgreSqlPartitionTables()
                if (partitionTables.isNotEmpty()) {
                    "$mainTable\n\n${partitionTables.joinToString("\n\n")}"
                } else {
                    mainTable
                }
            }
            DialectType.TIBERO -> {
                appliedRules.add("Oracle 파티션 테이블 유지 (Tibero 호환)")
                partitionSql
            }
            DialectType.ORACLE -> partitionSql
        }
    }

    /**
     * Oracle 파티션 정보 추출
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

        // INTERVAL 추출 (Oracle 11g+ interval partitioning)
        val intervalRegex = """INTERVAL\s*\(([^)]+)\)""".toRegex(RegexOption.IGNORE_CASE)
        val intervalExpression = intervalRegex.find(sql)?.groupValues?.get(1)

        return TablePartitionDetailInfo(
            tableName = tableName,
            partitionType = partitionType,
            partitionColumns = partitionColumns,
            partitions = partitions,
            intervalExpression = intervalExpression
        )
    }

    /**
     * 파티션 정의 추출
     */
    private fun extractPartitionDefinitions(sql: String, partitionType: PartitionType): List<PartitionDefinition> {
        val partitions = mutableListOf<PartitionDefinition>()

        when (partitionType) {
            PartitionType.RANGE -> {
                // PARTITION name VALUES LESS THAN (value)
                val rangeRegex = """PARTITION\s+"?(\w+)"?\s+VALUES\s+LESS\s+THAN\s*\(([^)]+)\)""".toRegex(RegexOption.IGNORE_CASE)
                rangeRegex.findAll(sql).forEach { match ->
                    partitions.add(PartitionDefinition(
                        name = match.groupValues[1],
                        values = match.groupValues[2].trim()
                    ))
                }
            }
            PartitionType.LIST -> {
                // PARTITION name VALUES (value1, value2, ...)
                val listRegex = """PARTITION\s+"?(\w+)"?\s+VALUES\s*\(([^)]+)\)""".toRegex(RegexOption.IGNORE_CASE)
                listRegex.findAll(sql).forEach { match ->
                    partitions.add(PartitionDefinition(
                        name = match.groupValues[1],
                        values = match.groupValues[2].trim()
                    ))
                }
            }
            PartitionType.HASH -> {
                // PARTITION name
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
     * Oracle JSON 함수를 다른 방언으로 변환
     * 예: JSON_VALUE, JSON_QUERY, JSON_EXISTS, JSON_TABLE
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
                // Oracle JSON_VALUE → MySQL JSON_EXTRACT + JSON_UNQUOTE
                val jsonValueRegex = """JSON_VALUE\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = jsonValueRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("Oracle JSON_VALUE → MySQL JSON_UNQUOTE(JSON_EXTRACT()) 변환")
                    "JSON_UNQUOTE(JSON_EXTRACT($column, '\$.$path'))"
                }

                // Oracle JSON_QUERY → MySQL JSON_EXTRACT
                val jsonQueryRegex = """JSON_QUERY\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = jsonQueryRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("Oracle JSON_QUERY → MySQL JSON_EXTRACT 변환")
                    "JSON_EXTRACT($column, '\$.$path')"
                }

                // Oracle JSON_EXISTS → MySQL JSON_CONTAINS_PATH
                val jsonExistsRegex = """JSON_EXISTS\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = jsonExistsRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("Oracle JSON_EXISTS → MySQL JSON_CONTAINS_PATH 변환")
                    "JSON_CONTAINS_PATH($column, 'one', '\$.$path')"
                }

                // JSON_TABLE 경고
                if (result.uppercase().contains("JSON_TABLE")) {
                    warnings.add(createWarning(
                        type = WarningType.UNSUPPORTED_FUNCTION,
                        message = "Oracle JSON_TABLE은 MySQL에서 직접 지원되지 않습니다.",
                        severity = WarningSeverity.ERROR,
                        suggestion = "JSON_EXTRACT와 서브쿼리를 조합하여 사용하세요."
                    ))
                }
            }
            DialectType.POSTGRESQL -> {
                // Oracle JSON_VALUE → PostgreSQL ->> operator
                val jsonValueRegex = """JSON_VALUE\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = jsonValueRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("Oracle JSON_VALUE → PostgreSQL ->> 연산자 변환")
                    "$column ->> '$path'"
                }

                // Oracle JSON_QUERY → PostgreSQL -> operator
                val jsonQueryRegex = """JSON_QUERY\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = jsonQueryRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("Oracle JSON_QUERY → PostgreSQL -> 연산자 변환")
                    "$column -> '$path'"
                }

                // Oracle JSON_EXISTS → PostgreSQL ? operator
                val jsonExistsRegex = """JSON_EXISTS\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = jsonExistsRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val path = match.groupValues[2]
                    appliedRules.add("Oracle JSON_EXISTS → PostgreSQL ? 연산자 변환")
                    "$column ? '$path'"
                }

                // JSON_TABLE → PostgreSQL json_to_recordset 또는 jsonb_to_recordset
                if (result.uppercase().contains("JSON_TABLE")) {
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Oracle JSON_TABLE은 PostgreSQL json_to_recordset으로 변환이 필요합니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "json_to_recordset() 또는 jsonb_to_recordset()을 사용하세요."
                    ))
                }
            }
            DialectType.TIBERO -> {
                appliedRules.add("Oracle JSON 함수 유지 (Tibero 호환)")
            }
            DialectType.ORACLE -> { /* 변환 불필요 */ }
        }

        return result
    }

    /**
     * Oracle 정규식 함수를 다른 방언으로 변환
     * 예: REGEXP_LIKE, REGEXP_SUBSTR, REGEXP_REPLACE, REGEXP_INSTR, REGEXP_COUNT
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
                // REGEXP_LIKE → REGEXP (MySQL은 REGEXP_LIKE 8.0+에서 지원하지만 REGEXP로도 변환 가능)
                val regexpLikeRegex = """REGEXP_LIKE\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*(?:,\s*'([^']*)'\s*)?\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpLikeRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    appliedRules.add("Oracle REGEXP_LIKE → MySQL REGEXP 변환")
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
                    appliedRules.add("Oracle REGEXP_SUBSTR → MySQL REGEXP_SUBSTR 변환")
                    "REGEXP_SUBSTR($column, '$pattern')"
                }

                // REGEXP_REPLACE → REGEXP_REPLACE (MySQL 8.0+)
                val regexpReplaceRegex = """REGEXP_REPLACE\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*,\s*'([^']*)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpReplaceRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    val replacement = match.groupValues[3]
                    appliedRules.add("Oracle REGEXP_REPLACE → MySQL REGEXP_REPLACE 변환")
                    "REGEXP_REPLACE($column, '$pattern', '$replacement')"
                }

                // REGEXP_INSTR → REGEXP_INSTR (MySQL 8.0+)
                val regexpInstrRegex = """REGEXP_INSTR\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpInstrRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    appliedRules.add("Oracle REGEXP_INSTR → MySQL REGEXP_INSTR 변환")
                    "REGEXP_INSTR($column, '$pattern')"
                }

                // REGEXP_COUNT → 지원 안됨 (수동 구현 필요)
                if (result.uppercase().contains("REGEXP_COUNT")) {
                    warnings.add(createWarning(
                        type = WarningType.UNSUPPORTED_FUNCTION,
                        message = "Oracle REGEXP_COUNT는 MySQL에서 지원되지 않습니다.",
                        severity = WarningSeverity.ERROR,
                        suggestion = "사용자 정의 함수를 작성하거나 애플리케이션에서 처리하세요."
                    ))
                }
            }
            DialectType.POSTGRESQL -> {
                // REGEXP_LIKE → ~ operator
                val regexpLikeRegex = """REGEXP_LIKE\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*(?:,\s*'([^']*)'\s*)?\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpLikeRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    val flags = match.groupValues.getOrNull(3) ?: ""
                    appliedRules.add("Oracle REGEXP_LIKE → PostgreSQL ~ 연산자 변환")
                    if (flags.contains("i", ignoreCase = true)) {
                        "$column ~* '$pattern'"  // 대소문자 무시
                    } else {
                        "$column ~ '$pattern'"
                    }
                }

                // REGEXP_SUBSTR → regexp_matches()[1]
                val regexpSubstrRegex = """REGEXP_SUBSTR\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpSubstrRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "PostgreSQL regexp_matches는 배열을 반환합니다.",
                        severity = WarningSeverity.INFO,
                        suggestion = "(regexp_matches(column, pattern))[1] 형식을 사용하세요."
                    ))
                    appliedRules.add("Oracle REGEXP_SUBSTR → PostgreSQL regexp_matches 변환")
                    "(regexp_matches($column, '$pattern'))[1]"
                }

                // REGEXP_REPLACE → regexp_replace (동일 이름)
                val regexpReplaceRegex = """REGEXP_REPLACE\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*,\s*'([^']*)'\s*(?:,\s*(\d+)\s*)?(?:,\s*(\d+)\s*)?(?:,\s*'([^']*)'\s*)?\)""".toRegex(RegexOption.IGNORE_CASE)
                result = regexpReplaceRegex.replace(result) { match ->
                    val column = match.groupValues[1]
                    val pattern = match.groupValues[2]
                    val replacement = match.groupValues[3]
                    val flags = match.groupValues.getOrNull(6) ?: "g"  // PostgreSQL은 기본적으로 모두 대체
                    appliedRules.add("Oracle REGEXP_REPLACE → PostgreSQL regexp_replace 변환")
                    "regexp_replace($column, '$pattern', '$replacement', '$flags')"
                }

                // REGEXP_INSTR → 지원 안됨 (수동 구현 필요)
                if (result.uppercase().contains("REGEXP_INSTR")) {
                    warnings.add(createWarning(
                        type = WarningType.UNSUPPORTED_FUNCTION,
                        message = "Oracle REGEXP_INSTR는 PostgreSQL에서 직접 지원되지 않습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "regexp_matches와 조합하여 구현하거나 사용자 정의 함수를 사용하세요."
                    ))
                }

                // REGEXP_COUNT → length - length(regexp_replace(...)) 방식으로 근사
                if (result.uppercase().contains("REGEXP_COUNT")) {
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Oracle REGEXP_COUNT는 PostgreSQL에서 직접 지원되지 않습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "array_length(regexp_matches(column, pattern, 'g'), 1) 또는 사용자 정의 함수를 사용하세요."
                    ))
                }
            }
            DialectType.TIBERO -> {
                appliedRules.add("Oracle 정규식 함수 유지 (Tibero 호환)")
            }
            DialectType.ORACLE -> { /* 변환 불필요 */ }
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

        // JSON_EXISTS
        val jsonExistsRegex = """JSON_EXISTS\s*\(\s*(\w+)\s*,\s*'\$\.([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
        jsonExistsRegex.find(jsonFuncStr)?.let { match ->
            return JsonFunctionInfo(
                functionType = JsonFunctionInfo.JsonFunctionType.CONTAINS,
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

        // REGEXP_SUBSTR
        val regexpSubstrRegex = """REGEXP_SUBSTR\s*\(\s*(\w+)\s*,\s*'([^']+)'\s*\)""".toRegex(RegexOption.IGNORE_CASE)
        regexpSubstrRegex.find(regexFuncStr)?.let { match ->
            return RegexFunctionInfo(
                functionType = RegexFunctionInfo.RegexFunctionType.SUBSTR,
                sourceExpression = match.groupValues[1],
                pattern = match.groupValues[2]
            )
        }

        return null
    }

    // ==================== Phase 4: 트리거 변환 ====================

    /**
     * Oracle 트리거를 다른 방언으로 변환
     */
    fun convertTrigger(
        triggerSql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        options: com.sqlswitcher.model.ConversionOptions?
    ): String {
        val triggerInfo = extractTriggerInfo(triggerSql)

        return when (targetDialect) {
            DialectType.MYSQL -> {
                appliedRules.add("Oracle 트리거 → MySQL 형식으로 변환")
                convertTriggerToMySql(triggerInfo, warnings, appliedRules)
            }
            DialectType.POSTGRESQL -> {
                appliedRules.add("Oracle 트리거 → PostgreSQL 형식으로 변환")
                convertTriggerToPostgreSql(triggerInfo, warnings, appliedRules)
            }
            DialectType.TIBERO -> {
                appliedRules.add("Oracle 트리거 유지 (Tibero 호환)")
                triggerSql
            }
            DialectType.ORACLE -> triggerSql
        }
    }

    /**
     * Oracle 트리거 SQL에서 TriggerInfo 추출
     *
     * Oracle 트리거 구문:
     * CREATE OR REPLACE TRIGGER schema.trigger_name
     *   BEFORE | AFTER | INSTEAD OF INSERT | UPDATE | DELETE OR ...
     *   ON schema.table_name
     *   REFERENCING OLD AS old NEW AS new ...
     *   FOR EACH ROW
     *   WHEN (condition)
     * PL/SQL block | CALL procedure
     */
    private fun extractTriggerInfo(triggerSql: String): TriggerInfo {
        val upperSql = triggerSql.uppercase()

        // 트리거명 추출 (스키마.트리거명 형식 처리)
        val triggerNameRegex = """CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        val triggerNameMatch = triggerNameRegex.find(triggerSql)
        val triggerName = triggerNameMatch?.groupValues?.get(2) ?:
            Regex("""CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+"?(\w+)"?""", RegexOption.IGNORE_CASE)
                .find(triggerSql)?.groupValues?.get(1) ?: "UNKNOWN_TRIGGER"

        // 테이블명 추출 (스키마.테이블명 형식 처리)
        val tableNameRegex = """ON\s+"?(\w+)"?\."?(\w+)"?""".toRegex(RegexOption.IGNORE_CASE)
        val tableNameMatch = tableNameRegex.find(triggerSql)
        val tableName = tableNameMatch?.groupValues?.get(2) ?:
            Regex("""ON\s+"?(\w+)"?""", RegexOption.IGNORE_CASE)
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

        // FOR EACH ROW 여부
        val forEachRow = upperSql.contains("FOR EACH ROW")

        // WHEN 조건 추출
        val whenRegex = """WHEN\s*\((.+?)\)\s*(?:BEGIN|DECLARE|CALL)""".toRegex(RegexOption.IGNORE_CASE)
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

        // 트리거 본문 추출 (BEGIN...END 또는 CALL)
        val bodyRegex = """(BEGIN.+END\s*;?|CALL\s+\w+\s*\([^)]*\)\s*;?)""".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val body = bodyRegex.find(triggerSql)?.groupValues?.get(1)?.trim() ?: ""

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
     * Oracle 트리거를 MySQL 형식으로 변환
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
                TriggerInfo.TriggerTiming.INSTEAD_OF -> "BEFORE"
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
                val condition = convertOracleConditionToMySql(triggerInfo.whenCondition)
                result.append("    IF $condition THEN\n")
            }

            // 본문 변환
            if (triggerInfo.body.isNotEmpty()) {
                val mysqlBody = convertOracleTriggerBodyToMySql(triggerInfo.body)
                result.append("        $mysqlBody\n")
            }

            if (!triggerInfo.whenCondition.isNullOrBlank()) {
                result.append("    END IF;\n")
            }

            result.append("END //\n\n")
            result.append("DELIMITER ;")

            results.add(result.toString())
        }

        appliedRules.add("Oracle PL/SQL → MySQL 프로시저 구문 변환")

        return results.joinToString("\n\n")
    }

    /**
     * Oracle 트리거를 PostgreSQL 형식으로 변환
     */
    private fun convertTriggerToPostgreSql(
        triggerInfo: TriggerInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()

        // PostgreSQL은 트리거 함수를 먼저 정의해야 함
        val functionName = "${triggerInfo.name}_func"

        // 트리거 함수 생성
        result.append("-- 트리거 함수 생성\n")
        result.append("CREATE OR REPLACE FUNCTION $functionName()\n")
        result.append("RETURNS TRIGGER AS \$\$\n")

        // WHEN 조건이 있으면 본문에 포함
        if (!triggerInfo.whenCondition.isNullOrBlank()) {
            val pgCondition = convertOracleConditionToPostgreSql(triggerInfo.whenCondition)
            result.append("BEGIN\n")
            result.append("    IF $pgCondition THEN\n")

            if (triggerInfo.body.isNotEmpty()) {
                val pgBody = convertOracleTriggerBodyToPostgreSql(triggerInfo.body, triggerInfo.referencing)
                result.append("        $pgBody\n")
            }

            result.append("    END IF;\n")
        } else {
            result.append("BEGIN\n")
            if (triggerInfo.body.isNotEmpty()) {
                val pgBody = convertOracleTriggerBodyToPostgreSql(triggerInfo.body, triggerInfo.referencing)
                result.append("    $pgBody\n")
            }
        }

        // RETURN 문 추가
        if (triggerInfo.timing == TriggerInfo.TriggerTiming.BEFORE) {
            if (triggerInfo.events.contains(TriggerInfo.TriggerEvent.DELETE)) {
                result.append("    RETURN OLD;\n")
            } else {
                result.append("    RETURN NEW;\n")
            }
        } else {
            result.append("    RETURN NULL;\n")
        }

        result.append("END;\n")
        result.append("\$\$ LANGUAGE plpgsql;\n\n")

        // 트리거 생성
        result.append("-- 트리거 생성\n")
        result.append("CREATE TRIGGER \"${triggerInfo.name}\"\n")

        // 타이밍
        val timingStr = when (triggerInfo.timing) {
            TriggerInfo.TriggerTiming.BEFORE -> "BEFORE"
            TriggerInfo.TriggerTiming.AFTER -> "AFTER"
            TriggerInfo.TriggerTiming.INSTEAD_OF -> "INSTEAD OF"
        }

        // 이벤트
        val eventsStr = triggerInfo.events.joinToString(" OR ") { event ->
            when (event) {
                TriggerInfo.TriggerEvent.INSERT -> "INSERT"
                TriggerInfo.TriggerEvent.UPDATE -> "UPDATE"
                TriggerInfo.TriggerEvent.DELETE -> "DELETE"
            }
        }

        result.append("    $timingStr $eventsStr\n")
        result.append("    ON \"${triggerInfo.tableName}\"\n")

        if (triggerInfo.forEachRow) {
            result.append("    FOR EACH ROW\n")
        }

        result.append("    EXECUTE FUNCTION $functionName();")

        appliedRules.add("Oracle PL/SQL → PostgreSQL PL/pgSQL 변환")
        appliedRules.add("Oracle 트리거 → PostgreSQL 트리거 함수 + 트리거 분리")

        return result.toString()
    }

    /**
     * Oracle 조건문을 MySQL 형식으로 변환
     */
    private fun convertOracleConditionToMySql(condition: String): String {
        var result = condition

        // :OLD. → OLD. (MySQL에서는 콜론 없음)
        result = result.replace(Regex(""":OLD\.""", RegexOption.IGNORE_CASE), "OLD.")
        result = result.replace(Regex(""":NEW\.""", RegexOption.IGNORE_CASE), "NEW.")

        return result
    }

    /**
     * Oracle 조건문을 PostgreSQL 형식으로 변환
     */
    private fun convertOracleConditionToPostgreSql(condition: String): String {
        var result = condition

        // :OLD. → OLD. (PostgreSQL에서는 콜론 없음)
        result = result.replace(Regex(""":OLD\.""", RegexOption.IGNORE_CASE), "OLD.")
        result = result.replace(Regex(""":NEW\.""", RegexOption.IGNORE_CASE), "NEW.")

        return result
    }

    /**
     * Oracle 트리거 본문을 MySQL 형식으로 변환
     */
    private fun convertOracleTriggerBodyToMySql(body: String): String {
        var result = body

        // BEGIN/END 제거 (MySQL 트리거에서 이미 BEGIN/END가 있음)
        result = result.replace(Regex("""^\s*BEGIN\s*""", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("""\s*END\s*;?\s*$""", RegexOption.IGNORE_CASE), "")

        // :OLD. → OLD., :NEW. → NEW.
        result = result.replace(Regex(""":OLD\.""", RegexOption.IGNORE_CASE), "OLD.")
        result = result.replace(Regex(""":NEW\.""", RegexOption.IGNORE_CASE), "NEW.")

        // RAISE_APPLICATION_ERROR → SIGNAL SQLSTATE
        result = result.replace(Regex("""RAISE_APPLICATION_ERROR\s*\(\s*(-?\d+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)) { match ->
            val errorMessage = match.groupValues[2]
            "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '$errorMessage'"
        }

        // DBMS_OUTPUT.PUT_LINE → SELECT (디버깅용)
        result = result.replace(Regex("""DBMS_OUTPUT\.PUT_LINE\s*\(\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)) { match ->
            "SELECT '${match.groupValues[1]}'"
        }

        // SYSDATE → NOW()
        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "NOW()")

        // NVL → IFNULL
        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "IFNULL(")

        return result.trim()
    }

    /**
     * Oracle 트리거 본문을 PostgreSQL 형식으로 변환
     */
    private fun convertOracleTriggerBodyToPostgreSql(body: String, referencing: TriggerInfo.ReferencingClause?): String {
        var result = body

        // BEGIN/END 제거 (PostgreSQL 트리거 함수에서 이미 BEGIN/END가 있음)
        result = result.replace(Regex("""^\s*BEGIN\s*""", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("""\s*END\s*;?\s*$""", RegexOption.IGNORE_CASE), "")

        // :OLD. → OLD., :NEW. → NEW.
        result = result.replace(Regex(""":OLD\.""", RegexOption.IGNORE_CASE), "OLD.")
        result = result.replace(Regex(""":NEW\.""", RegexOption.IGNORE_CASE), "NEW.")

        // REFERENCING 별칭 처리
        if (referencing != null) {
            referencing.oldAlias?.let { alias ->
                result = result.replace(Regex("\\b$alias\\.", RegexOption.IGNORE_CASE), "OLD.")
            }
            referencing.newAlias?.let { alias ->
                result = result.replace(Regex("\\b$alias\\.", RegexOption.IGNORE_CASE), "NEW.")
            }
        }

        // RAISE_APPLICATION_ERROR → RAISE EXCEPTION
        result = result.replace(Regex("""RAISE_APPLICATION_ERROR\s*\(\s*(-?\d+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)) { match ->
            val errorMessage = match.groupValues[2]
            "RAISE EXCEPTION '$errorMessage'"
        }

        // DBMS_OUTPUT.PUT_LINE → RAISE NOTICE
        result = result.replace(Regex("""DBMS_OUTPUT\.PUT_LINE\s*\(\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)) { match ->
            "RAISE NOTICE '${match.groupValues[1]}'"
        }

        // SYSDATE → CURRENT_TIMESTAMP
        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")

        // NVL → COALESCE
        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")

        return result.trim()
    }

    // ==================== 시퀀스 변환 ====================

    /**
     * Oracle 시퀀스 생성문을 다른 방언으로 변환
     */
    fun convertSequence(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        schemaOwner: String = "SCHEMA"
    ): String {
        val upperSql = sql.trim().uppercase()

        if (!upperSql.startsWith("CREATE SEQUENCE")) {
            return sql
        }

        val seqInfo = SequenceInfo.parseFromOracle(sql)

        return when (targetDialect) {
            DialectType.ORACLE -> seqInfo.toOracle(schemaOwner)
            DialectType.TIBERO -> seqInfo.toTibero(schemaOwner)
            DialectType.MYSQL -> {
                appliedRules.add("Oracle 시퀀스를 MySQL 시뮬레이션으로 변환")
                seqInfo.toMySql(warnings)
            }
            DialectType.POSTGRESQL -> {
                appliedRules.add("Oracle 시퀀스를 PostgreSQL 형식으로 변환")
                seqInfo.toPostgreSql()
            }
        }
    }

    /**
     * 시퀀스 사용 구문 변환 (NEXTVAL, CURRVAL)
     */
    fun convertSequenceUsage(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                // PostgreSQL 형식: nextval('seq_name') -> seq_name.NEXTVAL
                result = Regex("""nextval\s*\(\s*'(\w+)'\s*\)""", RegexOption.IGNORE_CASE)
                    .replace(result) { match ->
                        appliedRules.add("PostgreSQL nextval을 Oracle 형식으로 변환")
                        "${match.groupValues[1]}.NEXTVAL"
                    }
                result = Regex("""currval\s*\(\s*'(\w+)'\s*\)""", RegexOption.IGNORE_CASE)
                    .replace(result) { match ->
                        appliedRules.add("PostgreSQL currval을 Oracle 형식으로 변환")
                        "${match.groupValues[1]}.CURRVAL"
                    }

                // MySQL 함수 형식: seq_name_nextval() -> seq_name.NEXTVAL
                result = Regex("""(\w+)_nextval\s*\(\s*\)""", RegexOption.IGNORE_CASE)
                    .replace(result) { match ->
                        appliedRules.add("MySQL 시퀀스 함수를 Oracle 형식으로 변환")
                        "${match.groupValues[1]}.NEXTVAL"
                    }
                result = Regex("""(\w+)_currval\s*\(\s*\)""", RegexOption.IGNORE_CASE)
                    .replace(result) { match ->
                        appliedRules.add("MySQL 시퀀스 함수를 Oracle 형식으로 변환")
                        "${match.groupValues[1]}.CURRVAL"
                    }
            }

            DialectType.MYSQL -> {
                // Oracle 형식: seq_name.NEXTVAL -> seq_name_nextval()
                result = Regex("""(\w+)\.NEXTVAL""", RegexOption.IGNORE_CASE)
                    .replace(result) { match ->
                        appliedRules.add("Oracle NEXTVAL을 MySQL 함수로 변환")
                        "${match.groupValues[1]}_nextval()"
                    }
                result = Regex("""(\w+)\.CURRVAL""", RegexOption.IGNORE_CASE)
                    .replace(result) { match ->
                        appliedRules.add("Oracle CURRVAL을 MySQL 함수로 변환")
                        "${match.groupValues[1]}_currval()"
                    }
            }

            DialectType.POSTGRESQL -> {
                // Oracle 형식: seq_name.NEXTVAL -> nextval('seq_name')
                result = Regex("""(\w+)\.NEXTVAL""", RegexOption.IGNORE_CASE)
                    .replace(result) { match ->
                        appliedRules.add("Oracle NEXTVAL을 PostgreSQL nextval로 변환")
                        "nextval('${match.groupValues[1]}')"
                    }
                result = Regex("""(\w+)\.CURRVAL""", RegexOption.IGNORE_CASE)
                    .replace(result) { match ->
                        appliedRules.add("Oracle CURRVAL을 PostgreSQL currval로 변환")
                        "currval('${match.groupValues[1]}')"
                    }
            }
        }

        return result
    }

    /**
     * ALTER SEQUENCE 변환
     */
    fun convertAlterSequence(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        schemaOwner: String = "SCHEMA"
    ): String {
        val upperSql = sql.uppercase()

        // 시퀀스명 추출
        val seqNameMatch = Regex("""ALTER\s+SEQUENCE\s+"?(\w+)"?\."?(\w+)"?""", RegexOption.IGNORE_CASE).find(sql)
            ?: Regex("""ALTER\s+SEQUENCE\s+"?(\w+)"?""", RegexOption.IGNORE_CASE).find(sql)
        val seqName = if (seqNameMatch?.groupValues?.size ?: 0 > 2 && seqNameMatch?.groupValues?.get(2)?.isNotBlank() == true) {
            seqNameMatch?.groupValues?.get(2)
        } else {
            seqNameMatch?.groupValues?.get(1)
        } ?: return sql

        // INCREMENT BY 추출
        val incrementMatch = Regex("""INCREMENT\s+BY\s+(-?\d+)""", RegexOption.IGNORE_CASE).find(sql)
        val incrementValue = incrementMatch?.groupValues?.get(1)?.toLongOrNull()

        // MINVALUE/MAXVALUE/CACHE/CYCLE 추출
        val hasNoMinValue = upperSql.contains("NOMINVALUE")
        val hasNoMaxValue = upperSql.contains("NOMAXVALUE")
        val hasNoCache = upperSql.contains("NOCACHE")
        val cacheMatch = Regex("""CACHE\s+(\d+)""", RegexOption.IGNORE_CASE).find(sql)
        val cacheValue = cacheMatch?.groupValues?.get(1)?.toLongOrNull()
        val hasCycle = upperSql.contains("CYCLE") && !upperSql.contains("NOCYCLE")

        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> sql // 그대로 유지
            DialectType.MYSQL -> {
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "MySQL은 ALTER SEQUENCE를 지원하지 않습니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "시퀀스 시뮬레이션 테이블을 직접 UPDATE하세요."
                ))
                val result = StringBuilder("-- MySQL 시퀀스 시뮬레이션 수정\n")
                if (incrementValue != null) {
                    result.appendLine("UPDATE `seq_$seqName` SET `increment_by` = $incrementValue;")
                }
                appliedRules.add("ALTER SEQUENCE를 MySQL UPDATE로 변환")
                result.toString().trim()
            }
            DialectType.POSTGRESQL -> {
                val result = StringBuilder("ALTER SEQUENCE \"$seqName\"")
                if (incrementValue != null) {
                    result.append("\n    INCREMENT BY $incrementValue")
                }
                if (hasNoMinValue) {
                    result.append("\n    NO MINVALUE")
                }
                if (hasNoMaxValue) {
                    result.append("\n    NO MAXVALUE")
                }
                if (cacheValue != null && cacheValue > 1) {
                    result.append("\n    CACHE $cacheValue")
                }
                if (hasCycle) {
                    result.append("\n    CYCLE")
                } else {
                    result.append("\n    NO CYCLE")
                }
                appliedRules.add("ALTER SEQUENCE를 PostgreSQL 형식으로 변환")
                result.toString()
            }
        }
    }

    /**
     * DROP SEQUENCE 변환
     */
    fun convertDropSequence(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // 시퀀스명 추출
        val seqNameMatch = Regex("""DROP\s+SEQUENCE\s+"?(\w+)"?\."?(\w+)"?""", RegexOption.IGNORE_CASE).find(sql)
            ?: Regex("""DROP\s+SEQUENCE\s+"?(\w+)"?""", RegexOption.IGNORE_CASE).find(sql)
        val seqName = if (seqNameMatch?.groupValues?.size ?: 0 > 2 && seqNameMatch?.groupValues?.get(2)?.isNotBlank() == true) {
            seqNameMatch?.groupValues?.get(2)
        } else {
            seqNameMatch?.groupValues?.get(1)
        } ?: return sql

        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> sql // 그대로 유지
            DialectType.MYSQL -> {
                appliedRules.add("DROP SEQUENCE를 MySQL 시뮬레이션 삭제로 변환")
                """-- MySQL 시퀀스 시뮬레이션 삭제
DROP TABLE IF EXISTS `seq_$seqName`;
DROP FUNCTION IF EXISTS `${seqName}_nextval`;
DROP FUNCTION IF EXISTS `${seqName}_currval`;"""
            }
            DialectType.POSTGRESQL -> {
                appliedRules.add("DROP SEQUENCE를 PostgreSQL 형식으로 변환")
                "DROP SEQUENCE IF EXISTS \"$seqName\""
            }
        }
    }

    /**
     * 시퀀스에서 다음 값 가져오기 - 다른 방언용 SELECT 문 생성
     */
    fun generateSequenceNextValueSelect(
        seqName: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        return when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                appliedRules.add("Oracle 시퀀스 NEXTVAL SELECT 생성")
                "SELECT $seqName.NEXTVAL FROM DUAL"
            }
            DialectType.POSTGRESQL -> {
                appliedRules.add("PostgreSQL nextval SELECT 생성")
                "SELECT nextval('$seqName')"
            }
            DialectType.MYSQL -> {
                appliedRules.add("MySQL 시퀀스 함수 호출 생성")
                "SELECT ${seqName}_nextval()"
            }
        }
    }

    /**
     * INSERT 문에서 시퀀스 사용을 다른 방언으로 변환
     * 예: INSERT INTO t VALUES (seq.NEXTVAL, ...) -> INSERT INTO t VALUES (nextval('seq'), ...)
     */
    fun convertInsertWithSequence(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.ORACLE, DialectType.TIBERO -> {
                // 이미 Oracle 형식이면 유지
            }
            DialectType.POSTGRESQL -> {
                // seq.NEXTVAL -> nextval('seq')
                result = Regex("""(\w+)\.NEXTVAL""", RegexOption.IGNORE_CASE)
                    .replace(result) { match ->
                        appliedRules.add("INSERT 문의 Oracle NEXTVAL을 PostgreSQL nextval로 변환")
                        "nextval('${match.groupValues[1]}')"
                    }
                result = Regex("""(\w+)\.CURRVAL""", RegexOption.IGNORE_CASE)
                    .replace(result) { match ->
                        appliedRules.add("INSERT 문의 Oracle CURRVAL을 PostgreSQL currval로 변환")
                        "currval('${match.groupValues[1]}')"
                    }
            }
            DialectType.MYSQL -> {
                // seq.NEXTVAL -> seq_nextval()
                result = Regex("""(\w+)\.NEXTVAL""", RegexOption.IGNORE_CASE)
                    .replace(result) { match ->
                        appliedRules.add("INSERT 문의 Oracle NEXTVAL을 MySQL 함수로 변환")
                        "${match.groupValues[1]}_nextval()"
                    }
                result = Regex("""(\w+)\.CURRVAL""", RegexOption.IGNORE_CASE)
                    .replace(result) { match ->
                        appliedRules.add("INSERT 문의 Oracle CURRVAL을 MySQL 함수로 변환")
                        "${match.groupValues[1]}_currval()"
                    }

                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "MySQL 시퀀스 시뮬레이션 함수가 미리 생성되어 있어야 합니다.",
                    severity = WarningSeverity.INFO,
                    suggestion = "시퀀스 생성문을 먼저 변환하여 함수를 생성하세요."
                ))
            }
        }

        return result
    }
}
