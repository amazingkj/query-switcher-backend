package com.sqlswitcher.converter.feature.partition

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * 고급 파티션 변환기
 *
 * Oracle/MySQL/PostgreSQL 간의 파티션 변환을 수행합니다.
 *
 * 지원 기능:
 * - RANGE 파티션 (날짜, 숫자, 다중 컬럼)
 * - LIST 파티션 (단일/다중 값)
 * - HASH 파티션
 * - INTERVAL 파티션 (Oracle → 수동 생성 안내)
 * - 복합 파티션 (Range-Hash, Range-List 등)
 * - 서브파티션 처리
 * - 파티션 인덱스 변환
 */
object AdvancedPartitionConverter {

    // ============ 파티션 감지 패턴 ============

    private val PARTITION_BY_PATTERN = Regex(
        """PARTITION\s+BY\s+(RANGE|LIST|HASH|KEY)(?:\s+COLUMNS)?\s*\(\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val INTERVAL_PATTERN = Regex(
        """INTERVAL\s*\(\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val SUBPARTITION_BY_PATTERN = Regex(
        """SUBPARTITION\s+BY\s+(RANGE|LIST|HASH|KEY)\s*\(\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val SUBPARTITIONS_COUNT_PATTERN = Regex(
        """SUBPARTITIONS\s+(\d+)""",
        RegexOption.IGNORE_CASE
    )

    private val PARTITIONS_COUNT_PATTERN = Regex(
        """PARTITIONS\s+(\d+)""",
        RegexOption.IGNORE_CASE
    )

    // ============ 개별 파티션 정의 패턴 ============

    private val RANGE_PARTITION_PATTERN = Regex(
        """PARTITION\s+["'`]?(\w+)["'`]?\s+VALUES\s+LESS\s+THAN\s*\(\s*([^)]+|MAXVALUE)\s*\)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val LIST_PARTITION_PATTERN = Regex(
        """PARTITION\s+["'`]?(\w+)["'`]?\s+VALUES\s+(?:IN\s*)?\(\s*([^)]+)\s*\)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val HASH_PARTITION_PATTERN = Regex(
        """PARTITION\s+["'`]?(\w+)["'`]?(?:\s+TABLESPACE\s+["'`]?\w+["'`]?)?""",
        RegexOption.IGNORE_CASE
    )

    private val TABLESPACE_PATTERN = Regex(
        """TABLESPACE\s+["'`]?(\w+)["'`]?""",
        RegexOption.IGNORE_CASE
    )

    // ============ Oracle 특수 함수 패턴 ============

    private val TO_DATE_PATTERN = Regex(
        """TO_DATE\s*\(\s*'([^']+)'\s*,\s*'([^']+)'\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val NUMTOYMINTERVAL_PATTERN = Regex(
        """NUMTOYMINTERVAL\s*\(\s*(\d+)\s*,\s*'(\w+)'\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val NUMTODSINTERVAL_PATTERN = Regex(
        """NUMTODSINTERVAL\s*\(\s*(\d+)\s*,\s*'(\w+)'\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 파티션 테이블 여부 확인
     */
    fun isPartitionedTable(sql: String): Boolean {
        return PARTITION_BY_PATTERN.containsMatchIn(sql)
    }

    /**
     * 파티션 테이블 변환
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!isPartitionedTable(sql)) return sql
        if (sourceDialect == targetDialect) return sql

        val partitionInfo = parsePartitionInfo(sql, sourceDialect)

        return when (targetDialect) {
            DialectType.MYSQL -> convertToMySql(sql, partitionInfo, sourceDialect, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertToPostgreSql(sql, partitionInfo, sourceDialect, warnings, appliedRules)
            DialectType.ORACLE -> convertToOracle(sql, partitionInfo, sourceDialect, warnings, appliedRules)
        }
    }

    /**
     * 파티션 정보 파싱
     */
    private fun parsePartitionInfo(sql: String, sourceDialect: DialectType): PartitionTableInfo {
        val upperSql = sql.uppercase()

        // 메인 파티션 타입 및 컬럼
        val partitionMatch = PARTITION_BY_PATTERN.find(sql)
        val partitionType = partitionMatch?.groupValues?.get(1)?.uppercase() ?: "RANGE"
        val partitionColumns = partitionMatch?.groupValues?.get(2)
            ?.split(",")?.map { it.trim().trim('"', '`', '\'') } ?: emptyList()

        // INTERVAL 표현식
        val intervalExpr = INTERVAL_PATTERN.find(sql)?.groupValues?.get(1)

        // 서브파티션 타입 및 컬럼
        val subpartMatch = SUBPARTITION_BY_PATTERN.find(sql)
        val subpartitionType = subpartMatch?.groupValues?.get(1)?.uppercase()
        val subpartitionColumns = subpartMatch?.groupValues?.get(2)
            ?.split(",")?.map { it.trim().trim('"', '`', '\'') } ?: emptyList()

        // 서브파티션 개수
        val subpartitionCount = SUBPARTITIONS_COUNT_PATTERN.find(sql)?.groupValues?.get(1)?.toIntOrNull()

        // 파티션 개수 (HASH/KEY용)
        val partitionCount = PARTITIONS_COUNT_PATTERN.find(sql)?.groupValues?.get(1)?.toIntOrNull()

        // 개별 파티션 정의
        val partitions = when (partitionType) {
            "RANGE" -> parseRangePartitions(sql, sourceDialect)
            "LIST" -> parseListPartitions(sql, sourceDialect)
            "HASH", "KEY" -> parseHashPartitions(sql, partitionCount ?: 4)
            else -> emptyList()
        }

        return PartitionTableInfo(
            partitionType = PartitionType.valueOf(partitionType),
            partitionColumns = partitionColumns,
            partitions = partitions,
            intervalExpression = intervalExpr,
            subpartitionType = subpartitionType?.let { PartitionType.valueOf(it) },
            subpartitionColumns = subpartitionColumns,
            subpartitionCount = subpartitionCount
        )
    }

    /**
     * RANGE 파티션 정의 파싱
     */
    private fun parseRangePartitions(sql: String, sourceDialect: DialectType): List<PartitionDef> {
        val partitions = mutableListOf<PartitionDef>()

        // 단순 LESS THAN 패턴
        val simplePattern = Regex(
            """PARTITION\s+["'`]?(\w+)["'`]?\s+VALUES\s+LESS\s+THAN\s*\(([^)]+)\)""",
            setOf(RegexOption.IGNORE_CASE)
        )

        // MAXVALUE 패턴
        val maxvaluePattern = Regex(
            """PARTITION\s+["'`]?(\w+)["'`]?\s+VALUES\s+LESS\s+THAN\s*\(\s*MAXVALUE\s*\)""",
            RegexOption.IGNORE_CASE
        )

        // MAXVALUE 먼저 처리
        maxvaluePattern.findAll(sql).forEach { match ->
            val name = match.groupValues[1]
            val tablespace = extractTablespace(sql, match.range.last)
            partitions.add(PartitionDef(
                name = name,
                boundValue = "MAXVALUE",
                isMaxValue = true,
                tablespace = tablespace
            ))
        }

        // 일반 값 처리
        simplePattern.findAll(sql).forEach { match ->
            val name = match.groupValues[1]
            val values = match.groupValues[2].trim()

            // MAXVALUE 파티션은 이미 처리됨
            if (values.uppercase() == "MAXVALUE") return@forEach
            if (partitions.any { it.name.equals(name, ignoreCase = true) }) return@forEach

            val tablespace = extractTablespace(sql, match.range.last)

            // TO_DATE 함수 처리
            val normalizedValue = if (sourceDialect == DialectType.ORACLE) {
                normalizeOracleDateValue(values)
            } else {
                values
            }

            partitions.add(PartitionDef(
                name = name,
                boundValue = normalizedValue,
                isMaxValue = false,
                tablespace = tablespace
            ))
        }

        return partitions.distinctBy { it.name.uppercase() }
    }

    /**
     * LIST 파티션 정의 파싱
     */
    private fun parseListPartitions(sql: String, sourceDialect: DialectType): List<PartitionDef> {
        val partitions = mutableListOf<PartitionDef>()

        val pattern = Regex(
            """PARTITION\s+["'`]?(\w+)["'`]?\s+VALUES\s+(?:IN\s*)?\(\s*([^)]+)\s*\)""",
            setOf(RegexOption.IGNORE_CASE)
        )

        // DEFAULT 파티션 패턴
        val defaultPattern = Regex(
            """PARTITION\s+["'`]?(\w+)["'`]?\s+VALUES\s+\(\s*DEFAULT\s*\)""",
            RegexOption.IGNORE_CASE
        )

        pattern.findAll(sql).forEach { match ->
            val name = match.groupValues[1]
            val values = match.groupValues[2].trim()

            // VALUES LESS THAN 패턴 제외 (RANGE 파티션)
            if (sql.substring(0, match.range.first).uppercase().let {
                val lastPartBy = it.lastIndexOf("PARTITION BY")
                if (lastPartBy >= 0) it.substring(lastPartBy).contains("RANGE") else false
            }) {
                return@forEach
            }

            val tablespace = extractTablespace(sql, match.range.last)

            partitions.add(PartitionDef(
                name = name,
                listValues = values.split(",").map { it.trim() },
                tablespace = tablespace
            ))
        }

        defaultPattern.findAll(sql).forEach { match ->
            val name = match.groupValues[1]
            if (partitions.none { it.name.equals(name, ignoreCase = true) }) {
                partitions.add(PartitionDef(
                    name = name,
                    listValues = listOf("DEFAULT"),
                    isDefault = true
                ))
            }
        }

        return partitions.distinctBy { it.name.uppercase() }
    }

    /**
     * HASH 파티션 정의 파싱
     */
    private fun parseHashPartitions(sql: String, count: Int): List<PartitionDef> {
        val partitions = mutableListOf<PartitionDef>()

        // 명시적 파티션 이름이 있는 경우
        val namedPattern = Regex(
            """PARTITION\s+["'`]?(\w+)["'`]?(?:\s+TABLESPACE\s+["'`]?(\w+)["'`]?)?""",
            RegexOption.IGNORE_CASE
        )

        // PARTITION BY HASH 이후의 파티션 정의만 추출
        val hashBlockPattern = Regex(
            """PARTITION\s+BY\s+HASH\s*\([^)]+\)[^(]*\(([^;]+)\)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val hashBlock = hashBlockPattern.find(sql)?.groupValues?.get(1)

        if (hashBlock != null) {
            namedPattern.findAll(hashBlock).forEach { match ->
                val name = match.groupValues[1]
                val tablespace = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }

                // 키워드 제외
                if (name.uppercase() !in listOf("BY", "HASH", "RANGE", "LIST", "KEY")) {
                    partitions.add(PartitionDef(
                        name = name,
                        tablespace = tablespace
                    ))
                }
            }
        }

        // 명시적 정의가 없으면 자동 생성
        if (partitions.isEmpty()) {
            repeat(count) { i ->
                partitions.add(PartitionDef(name = "p$i"))
            }
        }

        return partitions
    }

    /**
     * 테이블스페이스 추출
     */
    private fun extractTablespace(sql: String, afterPos: Int): String? {
        val afterText = sql.substring(minOf(afterPos, sql.length))
            .take(100)
        return TABLESPACE_PATTERN.find(afterText)?.groupValues?.get(1)
    }

    /**
     * Oracle TO_DATE 값 정규화
     */
    private fun normalizeOracleDateValue(value: String): String {
        return TO_DATE_PATTERN.replace(value) { match ->
            val dateStr = match.groupValues[1]
            "'$dateStr'"
        }
    }

    // ============ MySQL 변환 ============

    /**
     * MySQL 형식으로 변환
     */
    private fun convertToMySql(
        sql: String,
        info: PartitionTableInfo,
        sourceDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // 1. INTERVAL 파티션 처리 (MySQL 미지원)
        if (info.intervalExpression != null) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "MySQL은 INTERVAL 파티션을 지원하지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "MySQL EVENT를 사용하여 자동 파티션 생성 스크립트를 구현하거나, 충분한 미래 파티션을 미리 생성하세요."
            ))
            result = INTERVAL_PATTERN.replace(result, "")
            appliedRules.add("INTERVAL 절 제거 (MySQL 미지원)")
        }

        // 2. 서브파티션 처리
        if (info.subpartitionType != null) {
            if (info.subpartitionType !in listOf(PartitionType.HASH, PartitionType.KEY)) {
                warnings.add(ConversionWarning(
                    type = WarningType.PARTIAL_SUPPORT,
                    message = "MySQL은 HASH/KEY 서브파티션만 지원합니다. ${info.subpartitionType} 서브파티션이 KEY로 변환됩니다.",
                    severity = WarningSeverity.WARNING
                ))
                appliedRules.add("서브파티션 타입 → KEY 변환")
            }
        }

        // 3. TABLESPACE 제거
        result = TABLESPACE_PATTERN.replace(result, "")

        // 4. Oracle 특수 함수 변환
        if (sourceDialect == DialectType.ORACLE) {
            result = convertOracleFunctionsForMySQL(result)
            appliedRules.add("Oracle 날짜 함수 → MySQL 형식 변환")
        }

        // 5. PostgreSQL 특수 구문 변환
        if (sourceDialect == DialectType.POSTGRESQL) {
            result = convertPostgreSqlForMySQL(result, info, warnings, appliedRules)
        }

        // 6. 따옴표 변환
        result = result.replace("\"", "`")

        appliedRules.add("파티션 테이블 → MySQL 형식으로 변환")

        return result
    }

    /**
     * Oracle 함수를 MySQL 형식으로 변환
     */
    private fun convertOracleFunctionsForMySQL(sql: String): String {
        var result = sql

        // TO_DATE → STR_TO_DATE
        result = TO_DATE_PATTERN.replace(result) { match ->
            val dateStr = match.groupValues[1]
            val format = match.groupValues[2]
            val mysqlFormat = convertOracleDateFormatToMySql(format)
            "STR_TO_DATE('$dateStr', '$mysqlFormat')"
        }

        // NUMTOYMINTERVAL → INTERVAL
        result = NUMTOYMINTERVAL_PATTERN.replace(result) { match ->
            val num = match.groupValues[1]
            val unit = match.groupValues[2].uppercase()
            "INTERVAL $num $unit"
        }

        // NUMTODSINTERVAL → INTERVAL
        result = NUMTODSINTERVAL_PATTERN.replace(result) { match ->
            val num = match.groupValues[1]
            val unit = match.groupValues[2].uppercase()
            "INTERVAL $num $unit"
        }

        return result
    }

    /**
     * Oracle 날짜 포맷을 MySQL 포맷으로 변환
     */
    private fun convertOracleDateFormatToMySql(oracleFormat: String): String {
        return oracleFormat
            .replace("YYYY", "%Y")
            .replace("YY", "%y")
            .replace("MM", "%m")
            .replace("DD", "%d")
            .replace("HH24", "%H")
            .replace("HH", "%h")
            .replace("MI", "%i")
            .replace("SS", "%s")
    }

    /**
     * PostgreSQL 파티션을 MySQL 형식으로 변환
     */
    private fun convertPostgreSqlForMySQL(
        sql: String,
        info: PartitionTableInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // FOR VALUES FROM ... TO → VALUES LESS THAN
        val forValuesPattern = Regex(
            """FOR\s+VALUES\s+FROM\s*\(\s*([^)]+)\s*\)\s*TO\s*\(\s*([^)]+)\s*\)""",
            RegexOption.IGNORE_CASE
        )

        result = forValuesPattern.replace(result) { match ->
            val toValue = match.groupValues[2].trim()
            if (toValue.uppercase() == "MAXVALUE") {
                "VALUES LESS THAN MAXVALUE"
            } else {
                "VALUES LESS THAN ($toValue)"
            }
        }

        // FOR VALUES WITH (MODULUS n, REMAINDER r) → 파티션 이름만
        val hashValuesPattern = Regex(
            """FOR\s+VALUES\s+WITH\s*\(\s*MODULUS\s+\d+\s*,\s*REMAINDER\s+\d+\s*\)""",
            RegexOption.IGNORE_CASE
        )
        result = hashValuesPattern.replace(result, "")

        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "PostgreSQL 선언적 파티션이 MySQL 문법으로 변환되었습니다.",
            severity = WarningSeverity.INFO
        ))

        appliedRules.add("PostgreSQL 파티션 → MySQL 변환")

        return result
    }

    // ============ PostgreSQL 변환 ============

    /**
     * PostgreSQL 형식으로 변환
     */
    private fun convertToPostgreSql(
        sql: String,
        info: PartitionTableInfo,
        sourceDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()

        // 테이블명 추출
        val tableNamePattern = Regex(
            """CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?["'`]?(?:(\w+)\.)?["'`]?["'`]?(\w+)["'`]?""",
            RegexOption.IGNORE_CASE
        )
        val tableMatch = tableNamePattern.find(sql)
        val schemaName = tableMatch?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
        val tableName = tableMatch?.groupValues?.get(2) ?: "table"
        val fullTableName = if (schemaName != null) "\"$schemaName\".\"$tableName\"" else "\"$tableName\""

        // 기본 CREATE TABLE 부분 추출 (PARTITION BY 이전)
        val partitionByIndex = sql.uppercase().indexOf("PARTITION BY")
        val baseTableSql = if (partitionByIndex > 0) {
            sql.substring(0, partitionByIndex).trimEnd()
        } else {
            sql
        }

        // 기본 테이블 변환
        var convertedBase = convertBaseTableForPostgreSql(baseTableSql, sourceDialect)

        // PostgreSQL 파티션 절 생성
        val partitionClause = buildPostgreSqlPartitionClause(info)

        result.append(convertedBase)
        result.append("\n")
        result.append(partitionClause)
        result.append(";")

        // INTERVAL 파티션 안내
        if (info.intervalExpression != null) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "PostgreSQL은 INTERVAL 파티션을 직접 지원하지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "pg_partman 확장을 사용하거나 수동으로 파티션을 생성하세요. 예: CREATE EXTENSION pg_partman;"
            ))
            appliedRules.add("INTERVAL 절 제거 (pg_partman 권장)")
        }

        // 서브파티션 안내
        if (info.subpartitionType != null) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "PostgreSQL은 멀티레벨 파티션을 지원합니다. 각 서브파티션을 상위 파티션의 PARTITION OF로 정의해야 합니다.",
                severity = WarningSeverity.INFO
            ))
        }

        // 개별 파티션 테이블 생성문 추가
        if (info.partitions.isNotEmpty()) {
            result.append("\n\n-- 파티션 테이블 생성")
            val partitionStatements = generatePostgreSqlPartitionTables(fullTableName, info)
            partitionStatements.forEach { stmt ->
                result.append("\n")
                result.append(stmt)
                result.append(";")
            }
        }

        appliedRules.add("파티션 테이블 → PostgreSQL 선언적 파티션으로 변환")

        return result.toString()
    }

    /**
     * 기본 테이블 SQL PostgreSQL 변환
     */
    private fun convertBaseTableForPostgreSql(sql: String, sourceDialect: DialectType): String {
        var result = sql

        // 따옴표 변환
        result = result.replace("`", "\"")

        // MySQL 특수 옵션 제거
        if (sourceDialect == DialectType.MYSQL) {
            result = result.replace(Regex("""ENGINE\s*=\s*\w+""", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("""DEFAULT\s+CHARSET\s*=\s*\w+""", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("""COLLATE\s*=?\s*\w+""", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("""AUTO_INCREMENT\s*=?\s*\d*""", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("""COMMENT\s*=?\s*'[^']*'""", RegexOption.IGNORE_CASE), "")
        }

        // Oracle 특수 옵션 제거
        if (sourceDialect == DialectType.ORACLE) {
            result = result.replace(Regex("""TABLESPACE\s+["'`]?\w+["'`]?""", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("""STORAGE\s*\([^)]*\)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            result = result.replace(Regex("""(PCTFREE|INITRANS|MAXTRANS)\s*\d+""", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("""\b(LOGGING|NOLOGGING)\b""", RegexOption.IGNORE_CASE), "")
        }

        // 공백 정리
        result = result.replace(Regex("""\s+"""), " ").trim()

        return result
    }

    /**
     * PostgreSQL 파티션 절 생성
     */
    private fun buildPostgreSqlPartitionClause(info: PartitionTableInfo): String {
        val columnsQuoted = info.partitionColumns.joinToString(", ") { "\"$it\"" }

        return when (info.partitionType) {
            PartitionType.RANGE -> "PARTITION BY RANGE ($columnsQuoted)"
            PartitionType.LIST -> "PARTITION BY LIST ($columnsQuoted)"
            PartitionType.HASH, PartitionType.KEY -> "PARTITION BY HASH ($columnsQuoted)"
        }
    }

    /**
     * PostgreSQL 개별 파티션 테이블 생성문
     */
    private fun generatePostgreSqlPartitionTables(parentTable: String, info: PartitionTableInfo): List<String> {
        return info.partitions.mapIndexed { idx, partition ->
            val partitionTable = "${parentTable.replace("\"", "")}_${partition.name}"
            val sb = StringBuilder("CREATE TABLE \"$partitionTable\" PARTITION OF $parentTable")

            when (info.partitionType) {
                PartitionType.RANGE -> {
                    if (partition.isMaxValue) {
                        // MAXVALUE 파티션
                        val prevPartition = info.partitions.getOrNull(idx - 1)
                        if (prevPartition != null && !prevPartition.isMaxValue) {
                            sb.append(" FOR VALUES FROM (${prevPartition.boundValue}) TO (MAXVALUE)")
                        } else {
                            sb.append(" DEFAULT")
                        }
                    } else {
                        // 일반 RANGE 파티션
                        if (idx == 0) {
                            sb.append(" FOR VALUES FROM (MINVALUE) TO (${partition.boundValue})")
                        } else {
                            val prevValue = info.partitions[idx - 1].boundValue ?: "MINVALUE"
                            sb.append(" FOR VALUES FROM ($prevValue) TO (${partition.boundValue})")
                        }
                    }
                }
                PartitionType.LIST -> {
                    if (partition.isDefault) {
                        sb.append(" DEFAULT")
                    } else {
                        sb.append(" FOR VALUES IN (${partition.listValues.joinToString(", ")})")
                    }
                }
                PartitionType.HASH, PartitionType.KEY -> {
                    sb.append(" FOR VALUES WITH (MODULUS ${info.partitions.size}, REMAINDER $idx)")
                }
            }

            sb.toString()
        }
    }

    // ============ Oracle 변환 ============

    /**
     * Oracle 형식으로 변환
     */
    private fun convertToOracle(
        sql: String,
        info: PartitionTableInfo,
        sourceDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // PostgreSQL 선언적 파티션 → Oracle 형식
        if (sourceDialect == DialectType.POSTGRESQL) {
            result = convertPostgreSqlToOracle(result, info, warnings, appliedRules)
        }

        // MySQL 파티션 → Oracle 형식
        if (sourceDialect == DialectType.MYSQL) {
            result = convertMySqlToOracle(result, info, warnings, appliedRules)
        }

        // 따옴표 변환
        result = result.replace("`", "\"")

        appliedRules.add("파티션 테이블 → Oracle 형식으로 변환")

        return result
    }

    /**
     * PostgreSQL 파티션을 Oracle 형식으로 변환
     */
    private fun convertPostgreSqlToOracle(
        sql: String,
        info: PartitionTableInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // FOR VALUES FROM ... TO → VALUES LESS THAN
        val forValuesFromTo = Regex(
            """FOR\s+VALUES\s+FROM\s*\(\s*([^)]+)\s*\)\s*TO\s*\(\s*([^)]+)\s*\)""",
            RegexOption.IGNORE_CASE
        )

        result = forValuesFromTo.replace(result) { match ->
            val toValue = match.groupValues[2].trim()
            if (toValue.uppercase() == "MAXVALUE") {
                "VALUES LESS THAN (MAXVALUE)"
            } else {
                "VALUES LESS THAN ($toValue)"
            }
        }

        // FOR VALUES IN → VALUES
        val forValuesIn = Regex(
            """FOR\s+VALUES\s+IN\s*\(\s*([^)]+)\s*\)""",
            RegexOption.IGNORE_CASE
        )

        result = forValuesIn.replace(result) { match ->
            "VALUES (${match.groupValues[1]})"
        }

        // FOR VALUES WITH (MODULUS n, REMAINDER r) 제거
        val forValuesHash = Regex(
            """FOR\s+VALUES\s+WITH\s*\(\s*MODULUS\s+\d+\s*,\s*REMAINDER\s+\d+\s*\)""",
            RegexOption.IGNORE_CASE
        )
        result = forValuesHash.replace(result, "")

        // DEFAULT 파티션 → VALUES (DEFAULT)
        result = result.replace(Regex("""\bDEFAULT\b(?!\s*\()""", RegexOption.IGNORE_CASE), "VALUES (DEFAULT)")

        appliedRules.add("PostgreSQL 파티션 → Oracle 변환")

        return result
    }

    /**
     * MySQL 파티션을 Oracle 형식으로 변환
     */
    private fun convertMySqlToOracle(
        sql: String,
        info: PartitionTableInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // PARTITION BY KEY → PARTITION BY HASH
        result = result.replace(
            Regex("""PARTITION\s+BY\s+KEY\s*\(""", RegexOption.IGNORE_CASE),
            "PARTITION BY HASH ("
        )

        // RANGE COLUMNS → RANGE
        result = result.replace(
            Regex("""PARTITION\s+BY\s+RANGE\s+COLUMNS\s*\(""", RegexOption.IGNORE_CASE),
            "PARTITION BY RANGE ("
        )

        // LIST COLUMNS → LIST
        result = result.replace(
            Regex("""PARTITION\s+BY\s+LIST\s+COLUMNS\s*\(""", RegexOption.IGNORE_CASE),
            "PARTITION BY LIST ("
        )

        // STR_TO_DATE → TO_DATE
        val strToDatePattern = Regex(
            """STR_TO_DATE\s*\(\s*'([^']+)'\s*,\s*'([^']+)'\s*\)""",
            RegexOption.IGNORE_CASE
        )

        result = strToDatePattern.replace(result) { match ->
            val dateStr = match.groupValues[1]
            val format = match.groupValues[2]
            val oracleFormat = convertMySqlDateFormatToOracle(format)
            "TO_DATE('$dateStr', '$oracleFormat')"
        }

        appliedRules.add("MySQL 파티션 → Oracle 변환")

        return result
    }

    /**
     * MySQL 날짜 포맷을 Oracle 포맷으로 변환
     */
    private fun convertMySqlDateFormatToOracle(mysqlFormat: String): String {
        return mysqlFormat
            .replace("%Y", "YYYY")
            .replace("%y", "YY")
            .replace("%m", "MM")
            .replace("%d", "DD")
            .replace("%H", "HH24")
            .replace("%h", "HH")
            .replace("%i", "MI")
            .replace("%s", "SS")
    }

    // ============ 데이터 클래스 ============

    /**
     * 파티션 타입
     */
    enum class PartitionType {
        RANGE, LIST, HASH, KEY
    }

    /**
     * 파티션 테이블 정보
     */
    data class PartitionTableInfo(
        val partitionType: PartitionType,
        val partitionColumns: List<String>,
        val partitions: List<PartitionDef>,
        val intervalExpression: String? = null,
        val subpartitionType: PartitionType? = null,
        val subpartitionColumns: List<String> = emptyList(),
        val subpartitionCount: Int? = null
    )

    /**
     * 개별 파티션 정의
     */
    data class PartitionDef(
        val name: String,
        val boundValue: String? = null,       // RANGE: LESS THAN 값
        val listValues: List<String> = emptyList(), // LIST: IN 값들
        val isMaxValue: Boolean = false,
        val isDefault: Boolean = false,
        val tablespace: String? = null
    )
}
