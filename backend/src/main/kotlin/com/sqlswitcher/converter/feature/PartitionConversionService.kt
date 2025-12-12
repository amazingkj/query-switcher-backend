package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.model.PartitionType
import com.sqlswitcher.converter.model.PartitionDefinition
import com.sqlswitcher.converter.model.TablePartitionDetailInfo
import org.springframework.stereotype.Service

/**
 * 파티션 변환 서비스
 *
 * 모든 Dialect의 파티션 변환 로직을 중앙화하여 관리
 */
@Service
class PartitionConversionService {

    /**
     * 파티션 테이블 CREATE 문 변환
     */
    fun convertPartitionTable(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String? {
        // PARTITION BY 가 없으면 null 반환 (일반 테이블)
        if (!sql.uppercase().contains("PARTITION BY")) {
            return null
        }

        // 테이블명 추출
        val tableNameRegex = """CREATE\s+TABLE\s+["'`]?(?:(\w+)\.)?["'`]?["'`]?(\w+)["'`]?""".toRegex(RegexOption.IGNORE_CASE)
        val tableMatch = tableNameRegex.find(sql)
        val schemaName = tableMatch?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
        val tableName = tableMatch?.groupValues?.get(2) ?: return null

        // 파티션 정보 파싱
        val partitionInfo = parsePartitionInfo(sql, tableName, sourceDialect) ?: return null

        // PARTITION BY 이전 부분 추출
        var baseSql = sql
        val partitionByIndex = baseSql.uppercase().indexOf("PARTITION BY")
        if (partitionByIndex > 0) {
            baseSql = baseSql.substring(0, partitionByIndex).trimEnd()
        }

        // 기본 테이블 SQL 변환
        val convertedBaseSql = convertBaseTableSql(baseSql, sourceDialect, targetDialect, warnings, appliedRules)

        return when (targetDialect) {
            DialectType.POSTGRESQL -> {
                convertToPostgreSql(convertedBaseSql, partitionInfo, schemaName, warnings, appliedRules)
            }
            DialectType.MYSQL -> {
                convertToMySql(convertedBaseSql, partitionInfo, warnings, appliedRules)
            }
            DialectType.ORACLE -> {
                convertToOracle(convertedBaseSql, partitionInfo, warnings, appliedRules)
            }
        }
    }

    /**
     * 파티션 정보 파싱 (소스 방언에 따라)
     */
    private fun parsePartitionInfo(
        sql: String,
        tableName: String,
        sourceDialect: DialectType
    ): TablePartitionDetailInfo? {
        val upperSql = sql.uppercase()

        // 파티션 타입 추출
        val partitionType = when {
            upperSql.contains("PARTITION BY RANGE COLUMNS") -> PartitionType.RANGE
            upperSql.contains("PARTITION BY RANGE") -> PartitionType.RANGE
            upperSql.contains("PARTITION BY LIST COLUMNS") -> PartitionType.LIST
            upperSql.contains("PARTITION BY LIST") -> PartitionType.LIST
            upperSql.contains("PARTITION BY HASH") -> PartitionType.HASH
            upperSql.contains("PARTITION BY KEY") -> PartitionType.KEY
            else -> return null
        }

        // 파티션 컬럼 추출
        val partitionColRegex = """PARTITION\s+BY\s+(?:RANGE|LIST|HASH|KEY)(?:\s+COLUMNS)?\s*\(([^)]+)\)""".toRegex(RegexOption.IGNORE_CASE)
        val partitionColumns = partitionColRegex.find(sql)?.groupValues?.get(1)
            ?.split(",")?.map { it.trim().trim('"', '`', '\'') } ?: emptyList()

        // 서브파티션 타입 추출
        val subpartitionType = when {
            upperSql.contains("SUBPARTITION BY KEY") -> PartitionType.KEY
            upperSql.contains("SUBPARTITION BY HASH") -> PartitionType.HASH
            upperSql.contains("SUBPARTITION BY RANGE") -> PartitionType.RANGE
            upperSql.contains("SUBPARTITION BY LIST") -> PartitionType.LIST
            else -> null
        }

        // 서브파티션 컬럼 추출
        val subpartitionColRegex = """SUBPARTITION\s+BY\s+(?:KEY|HASH|RANGE|LIST)\s*\(([^)]+)\)""".toRegex(RegexOption.IGNORE_CASE)
        val subpartitionColumns = subpartitionColRegex.find(sql)?.groupValues?.get(1)
            ?.split(",")?.map { it.trim().trim('"', '`', '\'') } ?: emptyList()

        // 글로벌 SUBPARTITIONS count 추출
        val globalSubpartitionCountRegex = """SUBPARTITIONS\s+(\d+)""".toRegex(RegexOption.IGNORE_CASE)
        val globalSubpartitionCount = globalSubpartitionCountRegex.find(sql)?.groupValues?.get(1)?.toIntOrNull()

        // 파티션 정의 추출
        val partitions = extractPartitionDefinitions(sql, partitionType, globalSubpartitionCount, sourceDialect)

        return TablePartitionDetailInfo(
            tableName = tableName,
            partitionType = partitionType,
            partitionColumns = partitionColumns,
            partitions = partitions,
            subpartitionType = subpartitionType,
            subpartitionColumns = subpartitionColumns,
            intervalExpression = null
        )
    }

    /**
     * 파티션 정의 추출
     */
    private fun extractPartitionDefinitions(
        sql: String,
        partitionType: PartitionType,
        globalSubpartitionCount: Int?,
        sourceDialect: DialectType
    ): List<PartitionDefinition> {
        val partitions = mutableListOf<PartitionDefinition>()

        val quoteChars = when (sourceDialect) {
            DialectType.MYSQL -> "`?"
            else -> "\"?"
        }

        val partitionPattern = when (partitionType) {
            PartitionType.RANGE -> """PARTITION\s+$quoteChars(\w+)$quoteChars\s+VALUES\s+LESS\s+THAN\s+(?:\(([^)]+)\)|MAXVALUE)""".toRegex(RegexOption.IGNORE_CASE)
            PartitionType.LIST -> """PARTITION\s+$quoteChars(\w+)$quoteChars\s+VALUES\s+(?:IN\s*)?\(([^)]+)\)""".toRegex(RegexOption.IGNORE_CASE)
            PartitionType.HASH, PartitionType.KEY -> """PARTITION\s+$quoteChars(\w+)$quoteChars""".toRegex(RegexOption.IGNORE_CASE)
            else -> return partitions
        }

        partitionPattern.findAll(sql).forEach { match ->
            val partitionName = match.groupValues[1]
            val values = when (partitionType) {
                PartitionType.RANGE -> {
                    val value = match.groupValues.getOrNull(2)?.trim()
                    if (value.isNullOrBlank()) "MAXVALUE" else "LESS THAN ($value)"
                }
                PartitionType.LIST -> "IN (${match.groupValues[2].trim()})"
                else -> ""
            }

            // TABLESPACE 추출 (Oracle)
            val afterMatch = sql.substring(match.range.last + 1).take(200)
            val tablespaceRegex = """TABLESPACE\s+["'`]?(\w+)["'`]?""".toRegex(RegexOption.IGNORE_CASE)
            val tablespace = tablespaceRegex.find(afterMatch)?.groupValues?.get(1)

            partitions.add(PartitionDefinition(
                name = partitionName,
                values = values,
                tablespace = tablespace,
                subpartitions = emptyList(),
                subpartitionCount = globalSubpartitionCount
            ))
        }

        return partitions
    }

    /**
     * 기본 테이블 SQL 변환 (데이터 타입, 따옴표 등)
     */
    private fun convertBaseTableSql(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // 따옴표 변환
        result = when (targetDialect) {
            DialectType.MYSQL -> result.replace("\"", "`")
            else -> result.replace("`", "\"")
        }

        // MySQL 특화 옵션 제거
        if (sourceDialect == DialectType.MYSQL && targetDialect != DialectType.MYSQL) {
            result = result.replace(Regex("""ENGINE\s*=\s*\w+""", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("""DEFAULT\s+CHARSET\s*=\s*\w+""", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("""COLLATE\s*=?\s*\w+""", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("""AUTO_INCREMENT\s*=?\s*\d*""", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("""COMMENT\s*=?\s*'[^']*'""", RegexOption.IGNORE_CASE), "")
        }

        // Oracle TABLESPACE 제거 (다른 DB로 변환 시)
        if (sourceDialect == DialectType.ORACLE && targetDialect != DialectType.ORACLE) {
            result = result.replace(Regex("""TABLESPACE\s+["'`]?\w+["'`]?""", RegexOption.IGNORE_CASE), "")
        }

        // 데이터 타입 변환
        result = convertDataTypes(result, sourceDialect, targetDialect)

        // 공백 정리
        result = result.replace(Regex("""\s+"""), " ").trim()

        appliedRules.add("${sourceDialect.name} CREATE TABLE → ${targetDialect.name} 변환")

        return result
    }

    /**
     * 데이터 타입 변환
     */
    private fun convertDataTypes(sql: String, sourceDialect: DialectType, targetDialect: DialectType): String {
        var result = sql

        // MySQL → PostgreSQL
        if (sourceDialect == DialectType.MYSQL && targetDialect == DialectType.POSTGRESQL) {
            result = result.replace(Regex("""DATETIME\((\d+)\)""", RegexOption.IGNORE_CASE)) { "TIMESTAMP(${it.groupValues[1]})" }
            result = result.replace(Regex("""\bDATETIME\b""", RegexOption.IGNORE_CASE), "TIMESTAMP")
            result = result.replace(Regex("""\bTINYINT\b""", RegexOption.IGNORE_CASE), "SMALLINT")
            result = result.replace(Regex("""\bDOUBLE\b(?!\s+PRECISION)""", RegexOption.IGNORE_CASE), "DOUBLE PRECISION")
        }

        // MySQL → Oracle
        if (sourceDialect == DialectType.MYSQL && targetDialect == DialectType.ORACLE) {
            result = result.replace(Regex("""DATETIME\((\d+)\)""", RegexOption.IGNORE_CASE)) { "TIMESTAMP(${it.groupValues[1]})" }
            result = result.replace(Regex("""\bDATETIME\b""", RegexOption.IGNORE_CASE), "TIMESTAMP")
            result = result.replace(Regex("""\bVARCHAR\((\d+)\)""", RegexOption.IGNORE_CASE)) { "VARCHAR2(${it.groupValues[1]})" }
            result = result.replace(Regex("""\bINT\b""", RegexOption.IGNORE_CASE), "NUMBER(10)")
            result = result.replace(Regex("""\bBIGINT\b""", RegexOption.IGNORE_CASE), "NUMBER(19)")
        }

        // PostgreSQL → MySQL
        if (sourceDialect == DialectType.POSTGRESQL && targetDialect == DialectType.MYSQL) {
            result = result.replace(Regex("""TIMESTAMP\((\d+)\)""", RegexOption.IGNORE_CASE)) { "DATETIME(${it.groupValues[1]})" }
            result = result.replace(Regex("""\bTIMESTAMP\b(?!\s*\()""", RegexOption.IGNORE_CASE), "DATETIME")
            result = result.replace(Regex("""\bBOOLEAN\b""", RegexOption.IGNORE_CASE), "TINYINT(1)")
            result = result.replace(Regex("""\bDOUBLE\s+PRECISION\b""", RegexOption.IGNORE_CASE), "DOUBLE")
            result = result.replace(Regex("""\bBIGSERIAL\b""", RegexOption.IGNORE_CASE), "BIGINT AUTO_INCREMENT")
            result = result.replace(Regex("""\bSERIAL\b""", RegexOption.IGNORE_CASE), "INT AUTO_INCREMENT")
        }

        // Oracle → PostgreSQL
        if (sourceDialect == DialectType.ORACLE && targetDialect == DialectType.POSTGRESQL) {
            result = result.replace(Regex("""\bNUMBER\((\d+)\)""", RegexOption.IGNORE_CASE)) { "NUMERIC(${it.groupValues[1]})" }
            result = result.replace(Regex("""\bNUMBER\((\d+),\s*(\d+)\)""", RegexOption.IGNORE_CASE)) { "NUMERIC(${it.groupValues[1]}, ${it.groupValues[2]})" }
            result = result.replace(Regex("""\bNUMBER\b(?!\s*\()""", RegexOption.IGNORE_CASE), "NUMERIC")
            result = result.replace(Regex("""\bVARCHAR2\((\d+)(?:\s+(?:BYTE|CHAR))?\)""", RegexOption.IGNORE_CASE)) { "VARCHAR(${it.groupValues[1]})" }
            result = result.replace(Regex("""\bDATE\b""", RegexOption.IGNORE_CASE), "TIMESTAMP")
            result = result.replace(Regex("""\bCLOB\b""", RegexOption.IGNORE_CASE), "TEXT")
            result = result.replace(Regex("""\bBLOB\b""", RegexOption.IGNORE_CASE), "BYTEA")
        }

        // Oracle → MySQL
        if (sourceDialect == DialectType.ORACLE && targetDialect == DialectType.MYSQL) {
            result = result.replace(Regex("""\bNUMBER\((\d+)\)""", RegexOption.IGNORE_CASE)) {
                val precision = it.groupValues[1].toIntOrNull() ?: 10
                if (precision > 10) "BIGINT" else "INT"
            }
            result = result.replace(Regex("""\bNUMBER\((\d+),\s*(\d+)\)""", RegexOption.IGNORE_CASE)) { "DECIMAL(${it.groupValues[1]}, ${it.groupValues[2]})" }
            result = result.replace(Regex("""\bNUMBER\b(?!\s*\()""", RegexOption.IGNORE_CASE), "DECIMAL")
            result = result.replace(Regex("""\bVARCHAR2\((\d+)(?:\s+(?:BYTE|CHAR))?\)""", RegexOption.IGNORE_CASE)) { "VARCHAR(${it.groupValues[1]})" }
            result = result.replace(Regex("""\bDATE\b""", RegexOption.IGNORE_CASE), "DATETIME")
            result = result.replace(Regex("""\bTIMESTAMP\((\d+)\)""", RegexOption.IGNORE_CASE)) { "DATETIME(${it.groupValues[1]})" }
            result = result.replace(Regex("""\bCLOB\b""", RegexOption.IGNORE_CASE), "LONGTEXT")
            result = result.replace(Regex("""\bBLOB\b""", RegexOption.IGNORE_CASE), "LONGBLOB")
        }

        return result
    }

    /**
     * PostgreSQL 형식으로 변환
     */
    private fun convertToPostgreSql(
        baseSql: String,
        partitionInfo: TablePartitionDetailInfo,
        schemaName: String?,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        appliedRules.add("파티션 테이블 → PostgreSQL 형식으로 변환")
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "PostgreSQL은 파티션을 별도의 테이블로 생성해야 합니다.",
            severity = WarningSeverity.INFO
        ))

        if (partitionInfo.subpartitionType != null) {
            warnings.add(ConversionWarning(
                type = WarningType.PARTIAL_SUPPORT,
                message = "PostgreSQL은 직접적인 서브파티션을 지원하지 않습니다. 파티션 테이블을 다시 파티셔닝하는 방식으로 구현됩니다.",
                severity = WarningSeverity.WARNING
            ))
        }

        val mainTable = "$baseSql\n${partitionInfo.toPostgreSqlPartitionClause()}"
        val partitionTables = partitionInfo.toPostgreSqlPartitionTablesFull(schemaName)

        return if (partitionTables.isNotEmpty()) {
            "$mainTable;\n\n-- 파티션 테이블 생성\n${partitionTables.joinToString(";\n\n")};"
        } else {
            "$mainTable;"
        }
    }

    /**
     * MySQL 형식으로 변환
     */
    private fun convertToMySql(
        baseSql: String,
        partitionInfo: TablePartitionDetailInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        appliedRules.add("파티션 테이블 → MySQL 형식으로 변환")

        if (partitionInfo.subpartitionType != null &&
            partitionInfo.subpartitionType != PartitionType.HASH &&
            partitionInfo.subpartitionType != PartitionType.KEY) {
            warnings.add(ConversionWarning(
                type = WarningType.PARTIAL_SUPPORT,
                message = "MySQL은 HASH/KEY 서브파티션만 지원합니다. ${partitionInfo.subpartitionType} 서브파티션은 KEY로 변환됩니다.",
                severity = WarningSeverity.WARNING
            ))
        }

        return "$baseSql\n${partitionInfo.toMySqlPartitionClause()}"
    }

    /**
     * Oracle 형식으로 변환
     */
    private fun convertToOracle(
        baseSql: String,
        partitionInfo: TablePartitionDetailInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        appliedRules.add("파티션 테이블 → Oracle 형식으로 변환")

        return "$baseSql\n${partitionInfo.toOraclePartitionClause("")}"
    }
}