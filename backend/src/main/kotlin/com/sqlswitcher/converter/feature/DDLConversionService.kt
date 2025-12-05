package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.core.*
import com.sqlswitcher.converter.mapping.*
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.create.table.ColumnDefinition
import org.springframework.stereotype.Service

/**
 * DDL (CREATE TABLE, CREATE INDEX 등) 변환 서비스
 */
@Service
class DDLConversionService(
    private val dataTypeService: DataTypeConversionService,
    private val dataTypeMappingRegistry: DataTypeMappingRegistry
) {

    /**
     * CREATE TABLE 문 변환
     */
    fun convertCreateTable(
        createTable: CreateTable,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) {
            return createTable.toString()
        }

        return when (targetDialect) {
            DialectType.MYSQL -> convertToMySql(createTable, sourceDialect, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertToPostgreSql(createTable, sourceDialect, warnings, appliedRules)
            DialectType.ORACLE -> convertToOracle(createTable, sourceDialect, warnings, appliedRules)
            DialectType.TIBERO -> convertToTibero(createTable, sourceDialect, warnings, appliedRules)
        }
    }

    /**
     * CREATE TABLE → MySQL
     */
    private fun convertToMySql(
        createTable: CreateTable,
        sourceDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()

        // 테이블명 (백틱 사용)
        val tableName = extractTableName(createTable.table.name)
        sb.append("CREATE TABLE `$tableName` (\n")

        // 컬럼 정의
        val columnDefs = mutableListOf<String>()
        createTable.columnDefinitions?.forEach { colDef ->
            columnDefs.add(convertColumnToMySql(colDef, sourceDialect, warnings, appliedRules))
        }

        // PRIMARY KEY
        createTable.indexes?.forEach { index ->
            if (index.type?.uppercase()?.contains("PRIMARY") == true ||
                index.toString().uppercase().contains("PRIMARY KEY")) {
                val pkColumns = index.columnsNames?.joinToString(", ") { "`${it.trim('"', '`')}`" }
                if (pkColumns != null) {
                    columnDefs.add("    PRIMARY KEY ($pkColumns)")
                }
            }
        }

        sb.append(columnDefs.joinToString(",\n"))
        sb.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4")

        appliedRules.add("CREATE TABLE → MySQL 형식으로 변환")
        return sb.toString()
    }

    /**
     * CREATE TABLE → PostgreSQL
     */
    private fun convertToPostgreSql(
        createTable: CreateTable,
        sourceDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()

        // 테이블명 (큰따옴표 사용)
        val tableName = extractTableName(createTable.table.name)
        sb.append("CREATE TABLE \"$tableName\" (\n")

        // 컬럼 정의
        val columnDefs = mutableListOf<String>()
        createTable.columnDefinitions?.forEach { colDef ->
            columnDefs.add(convertColumnToPostgreSql(colDef, sourceDialect, warnings, appliedRules))
        }

        // PRIMARY KEY
        createTable.indexes?.forEach { index ->
            if (index.type?.uppercase()?.contains("PRIMARY") == true ||
                index.toString().uppercase().contains("PRIMARY KEY")) {
                val pkColumns = index.columnsNames?.joinToString(", ") { "\"${it.trim('"', '`')}\"" }
                if (pkColumns != null) {
                    columnDefs.add("    PRIMARY KEY ($pkColumns)")
                }
            }
        }

        sb.append(columnDefs.joinToString(",\n"))
        sb.append("\n)")

        appliedRules.add("CREATE TABLE → PostgreSQL 형식으로 변환")
        return sb.toString()
    }

    /**
     * CREATE TABLE → Oracle
     */
    private fun convertToOracle(
        createTable: CreateTable,
        sourceDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()

        // 테이블명 (큰따옴표 사용)
        val tableName = extractTableName(createTable.table.name)
        sb.append("CREATE TABLE \"$tableName\" (\n")

        // 컬럼 정의
        val columnDefs = mutableListOf<String>()
        createTable.columnDefinitions?.forEach { colDef ->
            columnDefs.add(convertColumnToOracle(colDef, sourceDialect, warnings, appliedRules))
        }

        // PRIMARY KEY
        createTable.indexes?.forEach { index ->
            if (index.type?.uppercase()?.contains("PRIMARY") == true ||
                index.toString().uppercase().contains("PRIMARY KEY")) {
                val pkColumns = index.columnsNames?.joinToString(", ") { "\"${it.trim('"', '`')}\"" }
                if (pkColumns != null) {
                    columnDefs.add("    PRIMARY KEY ($pkColumns)")
                }
            }
        }

        sb.append(columnDefs.joinToString(",\n"))
        sb.append("\n)")

        appliedRules.add("CREATE TABLE → Oracle 형식으로 변환")
        return sb.toString()
    }

    /**
     * CREATE TABLE → Tibero (Oracle과 동일)
     */
    private fun convertToTibero(
        createTable: CreateTable,
        sourceDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        appliedRules.add("CREATE TABLE → Tibero 형식으로 변환 (Oracle 호환)")
        return convertToOracle(createTable, sourceDialect, warnings, appliedRules)
    }

    /**
     * 컬럼 정의 → MySQL
     */
    private fun convertColumnToMySql(
        colDef: ColumnDefinition,
        sourceDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val columnName = colDef.columnName.trim('"', '`')
        val dataType = colDef.colDataType?.toString() ?: "VARCHAR(255)"

        // 데이터 타입 변환
        val convertedType = dataTypeService.convertDataType(
            dataType, sourceDialect, DialectType.MYSQL, warnings, appliedRules
        )

        // 제약조건 처리
        val constraints = extractConstraints(colDef, sourceDialect, DialectType.MYSQL, warnings, appliedRules)

        return "    `$columnName` $convertedType$constraints"
    }

    /**
     * 컬럼 정의 → PostgreSQL
     */
    private fun convertColumnToPostgreSql(
        colDef: ColumnDefinition,
        sourceDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val columnName = colDef.columnName.trim('"', '`')
        val dataType = colDef.colDataType?.toString() ?: "VARCHAR(255)"

        // 데이터 타입 변환
        val convertedType = dataTypeService.convertDataType(
            dataType, sourceDialect, DialectType.POSTGRESQL, warnings, appliedRules
        )

        // 제약조건 처리
        val constraints = extractConstraints(colDef, sourceDialect, DialectType.POSTGRESQL, warnings, appliedRules)

        return "    \"$columnName\" $convertedType$constraints"
    }

    /**
     * 컬럼 정의 → Oracle
     */
    private fun convertColumnToOracle(
        colDef: ColumnDefinition,
        sourceDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val columnName = colDef.columnName.trim('"', '`')
        val dataType = colDef.colDataType?.toString() ?: "VARCHAR2(255)"

        // 데이터 타입 변환
        val convertedType = dataTypeService.convertDataType(
            dataType, sourceDialect, DialectType.ORACLE, warnings, appliedRules
        )

        // 제약조건 처리
        val constraints = extractConstraints(colDef, sourceDialect, DialectType.ORACLE, warnings, appliedRules)

        return "    \"$columnName\" $convertedType$constraints"
    }

    /**
     * 제약조건 추출 및 변환
     */
    private fun extractConstraints(
        colDef: ColumnDefinition,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
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
                specStr == "DEFAULT" -> {
                    val defaultValue = colDef.columnSpecs?.getOrNull(index + 1)?.toString()
                    if (defaultValue != null) {
                        val converted = convertDefaultValue(defaultValue, sourceDialect, targetDialect)
                        constraints.add("DEFAULT $converted")
                    }
                }
                specStr == "AUTO_INCREMENT" -> {
                    when (targetDialect) {
                        DialectType.MYSQL -> constraints.add("AUTO_INCREMENT")
                        DialectType.ORACLE, DialectType.TIBERO -> {
                            constraints.add("GENERATED BY DEFAULT AS IDENTITY")
                            appliedRules.add("AUTO_INCREMENT → GENERATED BY DEFAULT AS IDENTITY")
                        }
                        DialectType.POSTGRESQL -> {
                            constraints.add("GENERATED BY DEFAULT AS IDENTITY")
                            appliedRules.add("AUTO_INCREMENT → GENERATED BY DEFAULT AS IDENTITY")
                        }
                    }
                }
                specStr.contains("GENERATED") -> {
                    when (targetDialect) {
                        DialectType.MYSQL -> {
                            constraints.add("AUTO_INCREMENT")
                            appliedRules.add("GENERATED AS IDENTITY → AUTO_INCREMENT")
                        }
                        else -> constraints.add(colDef.columnSpecs?.drop(index)?.take(4)?.joinToString(" ") ?: "")
                    }
                }
            }
        }

        return if (constraints.isNotEmpty()) " ${constraints.joinToString(" ")}" else ""
    }

    /**
     * DEFAULT 값 변환
     */
    private fun convertDefaultValue(
        value: String,
        sourceDialect: DialectType,
        targetDialect: DialectType
    ): String {
        var result = value

        when {
            (sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.TIBERO) -> {
                when (targetDialect) {
                    DialectType.MYSQL -> {
                        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                        result = result.replace(Regex("\\bSYS_GUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "UUID()")
                    }
                    DialectType.POSTGRESQL -> {
                        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                        result = result.replace(Regex("\\bSYS_GUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "gen_random_uuid()")
                    }
                    else -> {}
                }
            }
            sourceDialect == DialectType.MYSQL -> {
                when (targetDialect) {
                    DialectType.ORACLE, DialectType.TIBERO -> {
                        result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSDATE")
                        result = result.replace(Regex("\\bUUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYS_GUID()")
                    }
                    DialectType.POSTGRESQL -> {
                        result = result.replace(Regex("\\bUUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "gen_random_uuid()")
                    }
                    else -> {}
                }
            }
        }

        return result
    }

    /**
     * 테이블명 추출 (스키마 제거)
     */
    private fun extractTableName(fullName: String): String {
        return fullName.trim('"', '`', '[', ']').split(".").last()
    }

    /**
     * CREATE INDEX 변환
     */
    fun convertCreateIndex(
        indexSql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) {
            return indexSql
        }

        var result = indexSql

        // 인용문자 변환
        when (targetDialect) {
            DialectType.MYSQL -> {
                result = result.replace("\"", "`")
                appliedRules.add("인덱스 인용문자 → 백틱(`) 변환")
            }
            DialectType.ORACLE, DialectType.POSTGRESQL, DialectType.TIBERO -> {
                result = result.replace("`", "\"")
                appliedRules.add("인덱스 인용문자 → 큰따옴표(\") 변환")
            }
        }

        // TABLESPACE 처리
        if (targetDialect == DialectType.MYSQL) {
            result = result.replace(Regex("""TABLESPACE\s+"?\w+"?""", RegexOption.IGNORE_CASE), "")
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL은 TABLESPACE를 지원하지 않습니다.",
                severity = WarningSeverity.INFO
            ))
        }

        appliedRules.add("CREATE INDEX → $targetDialect 형식으로 변환")
        return result.trim()
    }

    /**
     * DROP TABLE 변환
     */
    fun convertDropTable(
        dropSql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) {
            return dropSql
        }

        var result = dropSql

        // 인용문자 변환
        when (targetDialect) {
            DialectType.MYSQL -> {
                result = result.replace("\"", "`")
            }
            DialectType.ORACLE, DialectType.POSTGRESQL, DialectType.TIBERO -> {
                result = result.replace("`", "\"")
            }
        }

        // CASCADE CONSTRAINTS (Oracle) → CASCADE (PostgreSQL)
        if (sourceDialect == DialectType.ORACLE && targetDialect == DialectType.POSTGRESQL) {
            result = result.replace(Regex("CASCADE\\s+CONSTRAINTS", RegexOption.IGNORE_CASE), "CASCADE")
        }

        // PURGE 제거 (MySQL/PostgreSQL은 미지원)
        if (targetDialect == DialectType.MYSQL || targetDialect == DialectType.POSTGRESQL) {
            result = result.replace(Regex("\\s+PURGE", RegexOption.IGNORE_CASE), "")
        }

        appliedRules.add("DROP TABLE → $targetDialect 형식으로 변환")
        return result
    }
}