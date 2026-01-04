package com.sqlswitcher.converter.feature.ddl

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * 테이블스페이스 및 파티션 변환기
 *
 * Oracle 테이블스페이스/파티션을 타겟 DB에 맞게 변환
 *
 * 지원 기능:
 * - TABLESPACE 절 제거/변환
 * - STORAGE 절 제거
 * - PCTFREE, INITRANS 등 물리적 속성 제거
 * - RANGE, LIST, HASH 파티션 변환
 * - 서브파티션 처리
 * - INTERVAL 파티션 변환
 */
object TablespacePartitionConverter {

    // ============ 테이블스페이스 관련 패턴 ============

    /** TABLESPACE 절 패턴 */
    private val TABLESPACE_PATTERN = Regex(
        """TABLESPACE\s+["']?(\w+)["']?""",
        RegexOption.IGNORE_CASE
    )

    /** STORAGE 절 패턴 (전체 괄호 포함) */
    private val STORAGE_PATTERN = Regex(
        """STORAGE\s*\([^)]*\)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** 물리적 속성 패턴 */
    private val PHYSICAL_ATTRS_PATTERN = Regex(
        """(PCTFREE|PCTUSED|INITRANS|MAXTRANS|FREELISTS|FREELIST\s+GROUPS|BUFFER_POOL)\s*\d*""",
        RegexOption.IGNORE_CASE
    )

    /** LOGGING/NOLOGGING 패턴 */
    private val LOGGING_PATTERN = Regex(
        """\b(LOGGING|NOLOGGING)\b""",
        RegexOption.IGNORE_CASE
    )

    /** COMPRESS/NOCOMPRESS 패턴 (Oracle) - PARTITION 키워드 제외 */
    private val COMPRESS_PATTERN = Regex(
        """\b(COMPRESS|NOCOMPRESS|ROW\s+STORE\s+COMPRESS|COLUMN\s+STORE\s+COMPRESS)(?:\s+(?!PARTITION)(\w+))?""",
        RegexOption.IGNORE_CASE
    )

    // ============ 파티션 관련 패턴 ============

    /** PARTITION BY RANGE 패턴 */
    private val PARTITION_BY_RANGE_PATTERN = Regex(
        """PARTITION\s+BY\s+RANGE\s*\(\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /** PARTITION BY LIST 패턴 */
    private val PARTITION_BY_LIST_PATTERN = Regex(
        """PARTITION\s+BY\s+LIST\s*\(\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /** PARTITION BY HASH 패턴 */
    private val PARTITION_BY_HASH_PATTERN = Regex(
        """PARTITION\s+BY\s+HASH\s*\(\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /** INTERVAL 파티션 패턴 */
    private val INTERVAL_PATTERN = Regex(
        """INTERVAL\s*\(\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /** 개별 파티션 정의 패턴 */
    private val PARTITION_DEF_PATTERN = Regex(
        """PARTITION\s+(\w+)\s+VALUES\s+(LESS\s+THAN|IN)\s*\(\s*([^)]+)\s*\)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** 파티션 블록 전체 패턴 */
    private val PARTITION_BLOCK_PATTERN = Regex(
        """(PARTITION\s+BY\s+(?:RANGE|LIST|HASH)\s*\([^)]+\)(?:\s*INTERVAL\s*\([^)]+\))?)\s*\(([^;]*?)\)(?=\s*;|\s*$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** 서브파티션 TEMPLATE 패턴 */
    private val SUBPARTITION_TEMPLATE_PATTERN = Regex(
        """SUBPARTITION\s+BY\s+(\w+)\s*\(\s*([^)]+)\s*\)\s*(?:SUBPARTITION\s+TEMPLATE\s*\([^)]*\))?""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * 테이블스페이스/파티션 변환 메인 함수
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql

        var result = sql

        // 1. 테이블스페이스 관련 절 제거/변환
        result = convertTablespace(result, targetDialect, warnings, appliedRules)

        // 2. 스토리지 절 제거
        result = removeStorageClause(result, appliedRules)

        // 3. 물리적 속성 제거
        result = removePhysicalAttributes(result, appliedRules)

        // 4. 로깅 설정 처리
        result = handleLoggingClause(result, targetDialect, warnings, appliedRules)

        // 5. 압축 설정 처리
        result = handleCompressClause(result, targetDialect, warnings, appliedRules)

        // 6. 파티션 변환
        result = convertPartitions(result, targetDialect, warnings, appliedRules)

        return result
    }

    /**
     * TABLESPACE 절 변환/제거
     */
    private fun convertTablespace(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!TABLESPACE_PATTERN.containsMatchIn(sql)) return sql

        val tablespaces = TABLESPACE_PATTERN.findAll(sql).map { it.groupValues[1] }.toList()

        return when (targetDialect) {
            DialectType.MYSQL -> {
                // MySQL은 테이블스페이스를 지원하지만 문법이 다름
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Oracle TABLESPACE 절이 제거되었습니다.",
                    severity = WarningSeverity.INFO,
                    suggestion = "MySQL에서 테이블스페이스가 필요한 경우: CREATE TABLESPACE 먼저 생성 후 ENGINE=InnoDB TABLESPACE=ts_name 사용"
                ))
                appliedRules.add("TABLESPACE 절 제거: ${tablespaces.joinToString(", ")}")
                TABLESPACE_PATTERN.replace(sql, "")
            }
            DialectType.POSTGRESQL -> {
                // PostgreSQL은 TABLESPACE 지원
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Oracle TABLESPACE 절이 유지되었습니다. PostgreSQL 테이블스페이스가 존재하는지 확인하세요.",
                    severity = WarningSeverity.INFO,
                    suggestion = "테이블스페이스 생성: CREATE TABLESPACE ts_name LOCATION '/path/to/dir'"
                ))
                appliedRules.add("TABLESPACE 절 유지 (PostgreSQL 호환)")
                sql
            }
            else -> sql
        }
    }

    /**
     * STORAGE 절 제거
     */
    private fun removeStorageClause(
        sql: String,
        appliedRules: MutableList<String>
    ): String {
        if (!STORAGE_PATTERN.containsMatchIn(sql)) return sql

        appliedRules.add("STORAGE 절 제거")
        return STORAGE_PATTERN.replace(sql, "")
    }

    /**
     * 물리적 속성 제거
     */
    private fun removePhysicalAttributes(
        sql: String,
        appliedRules: MutableList<String>
    ): String {
        if (!PHYSICAL_ATTRS_PATTERN.containsMatchIn(sql)) return sql

        val attrs = PHYSICAL_ATTRS_PATTERN.findAll(sql).map { it.groupValues[1] }.distinct().toList()
        appliedRules.add("물리적 속성 제거: ${attrs.joinToString(", ")}")
        return PHYSICAL_ATTRS_PATTERN.replace(sql, "")
    }

    /**
     * LOGGING/NOLOGGING 절 처리
     */
    private fun handleLoggingClause(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!LOGGING_PATTERN.containsMatchIn(sql)) return sql

        return when (targetDialect) {
            DialectType.MYSQL -> {
                appliedRules.add("LOGGING/NOLOGGING 절 제거")
                LOGGING_PATTERN.replace(sql, "")
            }
            DialectType.POSTGRESQL -> {
                val hasNologging = sql.uppercase().contains("NOLOGGING")
                if (hasNologging) {
                    warnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "NOLOGGING 테이블은 PostgreSQL UNLOGGED 테이블로 변환할 수 있습니다.",
                        severity = WarningSeverity.INFO,
                        suggestion = "CREATE UNLOGGED TABLE 사용 고려"
                    ))
                }
                appliedRules.add("LOGGING/NOLOGGING 절 제거")
                LOGGING_PATTERN.replace(sql, "")
            }
            else -> sql
        }
    }

    /**
     * 압축 설정 처리
     */
    private fun handleCompressClause(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!COMPRESS_PATTERN.containsMatchIn(sql)) return sql

        val compressionInfo = COMPRESS_PATTERN.find(sql)?.groupValues?.get(0) ?: ""

        return when (targetDialect) {
            DialectType.MYSQL -> {
                if (compressionInfo.uppercase().contains("COMPRESS")) {
                    warnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Oracle 압축 설정이 제거되었습니다.",
                        severity = WarningSeverity.INFO,
                        suggestion = "MySQL InnoDB 압축: ROW_FORMAT=COMPRESSED KEY_BLOCK_SIZE=8"
                    ))
                }
                appliedRules.add("압축 설정 제거")
                COMPRESS_PATTERN.replace(sql, "")
            }
            DialectType.POSTGRESQL -> {
                if (compressionInfo.uppercase().contains("COMPRESS")) {
                    warnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Oracle 압축 설정이 제거되었습니다.",
                        severity = WarningSeverity.INFO,
                        suggestion = "PostgreSQL TOAST 압축 또는 pg_columnar 확장 사용 고려"
                    ))
                }
                appliedRules.add("압축 설정 제거")
                COMPRESS_PATTERN.replace(sql, "")
            }
            else -> sql
        }
    }

    /**
     * 파티션 변환
     */
    private fun convertPartitions(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // 파티션 타입 감지
        val hasRangePartition = PARTITION_BY_RANGE_PATTERN.containsMatchIn(sql)
        val hasListPartition = PARTITION_BY_LIST_PATTERN.containsMatchIn(sql)
        val hasHashPartition = PARTITION_BY_HASH_PATTERN.containsMatchIn(sql)
        val hasInterval = INTERVAL_PATTERN.containsMatchIn(sql)
        val hasSubpartition = SUBPARTITION_TEMPLATE_PATTERN.containsMatchIn(sql)

        if (!hasRangePartition && !hasListPartition && !hasHashPartition) {
            return sql
        }

        return when (targetDialect) {
            DialectType.MYSQL -> convertToMySqlPartition(
                sql, hasRangePartition, hasListPartition, hasHashPartition,
                hasInterval, hasSubpartition, warnings, appliedRules
            )
            DialectType.POSTGRESQL -> convertToPostgreSqlPartition(
                sql, hasRangePartition, hasListPartition, hasHashPartition,
                hasInterval, hasSubpartition, warnings, appliedRules
            )
            else -> sql
        }
    }

    /**
     * Oracle 파티션 → MySQL 파티션 변환
     */
    private fun convertToMySqlPartition(
        sql: String,
        hasRange: Boolean,
        hasList: Boolean,
        hasHash: Boolean,
        hasInterval: Boolean,
        hasSubpartition: Boolean,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // INTERVAL 파티션 경고 (MySQL 미지원)
        if (hasInterval) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "MySQL은 INTERVAL 파티션을 지원하지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "수동으로 파티션을 생성하거나 EVENT를 사용하여 자동 생성 스크립트를 구현하세요."
            ))
            result = INTERVAL_PATTERN.replace(result, "")
            appliedRules.add("INTERVAL 절 제거 (MySQL 미지원)")
        }

        // 서브파티션 경고
        if (hasSubpartition) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL의 서브파티션은 HASH 또는 KEY만 지원합니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "서브파티션 구조를 단순화하거나 수동으로 조정이 필요합니다."
            ))
            // 서브파티션 템플릿 제거
            result = SUBPARTITION_TEMPLATE_PATTERN.replace(result, "")
            appliedRules.add("서브파티션 템플릿 제거")
        }

        // RANGE 파티션 변환
        if (hasRange) {
            result = convertRangePartitionToMySql(result, appliedRules)
        }

        // LIST 파티션 변환
        if (hasList) {
            result = convertListPartitionToMySql(result, appliedRules)
        }

        // HASH 파티션 변환
        if (hasHash) {
            result = convertHashPartitionToMySql(result, appliedRules)
        }

        // TABLESPACE in partition 제거
        result = result.replace(Regex("""TABLESPACE\s+\w+""", RegexOption.IGNORE_CASE), "")

        return result
    }

    /**
     * Oracle 파티션 → PostgreSQL 파티션 변환
     */
    private fun convertToPostgreSqlPartition(
        sql: String,
        hasRange: Boolean,
        hasList: Boolean,
        hasHash: Boolean,
        hasInterval: Boolean,
        hasSubpartition: Boolean,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // PostgreSQL은 선언적 파티션 지원 (10+)
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "PostgreSQL 파티션으로 변환됩니다. PostgreSQL 10 이상이 필요합니다.",
            severity = WarningSeverity.INFO,
            suggestion = "각 파티션은 별도의 CREATE TABLE 문으로 생성해야 합니다."
        ))

        // INTERVAL 파티션 처리
        if (hasInterval) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "PostgreSQL은 INTERVAL 파티션을 직접 지원하지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "pg_partman 확장을 사용하거나 수동으로 파티션을 생성하세요."
            ))
            result = INTERVAL_PATTERN.replace(result, "")
            appliedRules.add("INTERVAL 절 제거 (pg_partman 권장)")
        }

        // 서브파티션 처리
        if (hasSubpartition) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "PostgreSQL은 멀티레벨 파티션을 지원합니다.",
                severity = WarningSeverity.INFO,
                suggestion = "각 서브파티션을 상위 파티션의 PARTITION OF로 정의하세요."
            ))
            result = SUBPARTITION_TEMPLATE_PATTERN.replace(result, "")
            appliedRules.add("서브파티션 템플릿 변환 필요")
        }

        // 파티션 정의 변환
        result = PARTITION_BLOCK_PATTERN.replace(result) { matchResult ->
            val partitionBy = matchResult.groupValues[1]
            val partitionDefs = matchResult.groupValues[2]

            // PostgreSQL 스타일로 변환
            val pgPartitionBy = convertPartitionByClause(partitionBy)

            // 파티션 정의는 별도 CREATE TABLE로 분리해야 함
            val partitionList = parsePartitionDefinitions(partitionDefs)
            if (partitionList.isNotEmpty()) {
                appliedRules.add("파티션 정의 ${partitionList.size}개 발견 - 별도 CREATE TABLE 필요")
            }

            pgPartitionBy
        }

        // PARTITION BY 절 정리
        if (hasRange) {
            appliedRules.add("RANGE 파티션 → PostgreSQL RANGE 파티션")
        }
        if (hasList) {
            appliedRules.add("LIST 파티션 → PostgreSQL LIST 파티션")
        }
        if (hasHash) {
            appliedRules.add("HASH 파티션 → PostgreSQL HASH 파티션")
        }

        return result
    }

    /**
     * RANGE 파티션 MySQL 변환
     */
    private fun convertRangePartitionToMySql(sql: String, appliedRules: MutableList<String>): String {
        var result = sql

        // VALUES LESS THAN MAXVALUE → VALUES LESS THAN MAXVALUE (동일)
        // 단, Oracle의 MAXVALUE는 MySQL에서도 MAXVALUE로 사용

        // TO_DATE 함수가 있는 경우 변환
        result = result.replace(
            Regex("""VALUES\s+LESS\s+THAN\s*\(\s*TO_DATE\s*\(\s*'([^']+)'[^)]*\)\s*\)""", RegexOption.IGNORE_CASE)
        ) { match ->
            val dateStr = match.groupValues[1]
            "VALUES LESS THAN ('$dateStr')"
        }

        appliedRules.add("RANGE 파티션 MySQL 호환 형식으로 변환")
        return result
    }

    /**
     * LIST 파티션 MySQL 변환
     */
    private fun convertListPartitionToMySql(sql: String, appliedRules: MutableList<String>): String {
        // LIST 파티션은 Oracle과 MySQL 문법이 유사
        appliedRules.add("LIST 파티션 MySQL 호환 형식으로 변환")
        return sql
    }

    /**
     * HASH 파티션 MySQL 변환
     */
    private fun convertHashPartitionToMySql(sql: String, appliedRules: MutableList<String>): String {
        var result = sql

        // PARTITIONS n 형식 확인
        val partitionsPattern = Regex("""PARTITIONS\s+(\d+)""", RegexOption.IGNORE_CASE)
        if (!partitionsPattern.containsMatchIn(result)) {
            // 개별 파티션 정의를 PARTITIONS n으로 변환
            val partitionCount = PARTITION_DEF_PATTERN.findAll(result).count()
            if (partitionCount > 0) {
                result = PARTITION_DEF_PATTERN.replace(result, "")
                result = result.trimEnd().removeSuffix("(").removeSuffix(",")
                if (!result.contains("PARTITIONS")) {
                    result += "\nPARTITIONS $partitionCount"
                }
            }
        }

        appliedRules.add("HASH 파티션 MySQL 호환 형식으로 변환")
        return result
    }

    /**
     * PARTITION BY 절 PostgreSQL 변환
     */
    private fun convertPartitionByClause(partitionBy: String): String {
        // Oracle: PARTITION BY RANGE (col1, col2)
        // PostgreSQL: PARTITION BY RANGE (col1, col2)
        // 기본적으로 동일하지만 일부 정리 필요
        return partitionBy.trim()
    }

    /**
     * 파티션 정의 파싱
     */
    private fun parsePartitionDefinitions(partitionDefs: String): List<PartitionInfo> {
        val partitions = mutableListOf<PartitionInfo>()

        PARTITION_DEF_PATTERN.findAll(partitionDefs).forEach { match ->
            val name = match.groupValues[1]
            val valueType = match.groupValues[2] // LESS THAN or IN
            val values = match.groupValues[3]

            partitions.add(PartitionInfo(
                name = name,
                type = if (valueType.uppercase().contains("LESS")) PartitionValueType.RANGE else PartitionValueType.LIST,
                values = values.split(",").map { it.trim() }
            ))
        }

        return partitions
    }

    /**
     * 파티션 DDL 생성 (PostgreSQL용)
     *
     * PostgreSQL에서는 각 파티션을 별도 테이블로 생성해야 함
     */
    fun generatePostgreSqlPartitionDdl(
        tableName: String,
        partitions: List<PartitionInfo>
    ): List<String> {
        return partitions.map { partition ->
            when (partition.type) {
                PartitionValueType.RANGE -> {
                    val values = partition.values
                    if (values.size == 1 && values[0].uppercase() == "MAXVALUE") {
                        "CREATE TABLE ${partition.name} PARTITION OF $tableName FOR VALUES FROM (MAXVALUE) TO (MAXVALUE);"
                    } else {
                        "CREATE TABLE ${partition.name} PARTITION OF $tableName FOR VALUES FROM (MINVALUE) TO (${values.joinToString(", ")});"
                    }
                }
                PartitionValueType.LIST -> {
                    "CREATE TABLE ${partition.name} PARTITION OF $tableName FOR VALUES IN (${partition.values.joinToString(", ")});"
                }
                PartitionValueType.HASH -> {
                    val modulus = partitions.size
                    val remainder = partitions.indexOf(partition)
                    "CREATE TABLE ${partition.name} PARTITION OF $tableName FOR VALUES WITH (MODULUS $modulus, REMAINDER $remainder);"
                }
            }
        }
    }

    /**
     * 테이블스페이스 매핑 생성
     */
    fun createTablespaceMapping(
        oracleTablespaces: List<String>,
        targetDialect: DialectType
    ): Map<String, String> {
        return when (targetDialect) {
            DialectType.MYSQL -> {
                // MySQL은 기본적으로 innodb_file_per_table 사용
                oracleTablespaces.associateWith { "innodb_file_per_table" }
            }
            DialectType.POSTGRESQL -> {
                // PostgreSQL은 Oracle 테이블스페이스와 1:1 매핑 가능
                oracleTablespaces.associateWith { it.lowercase() }
            }
            else -> emptyMap()
        }
    }

    /**
     * 파티션 정보
     */
    data class PartitionInfo(
        val name: String,
        val type: PartitionValueType,
        val values: List<String>,
        val tablespace: String? = null
    )

    /**
     * 파티션 값 타입
     */
    enum class PartitionValueType {
        RANGE, LIST, HASH
    }

    /**
     * DDL에서 테이블스페이스 목록 추출
     */
    fun extractTablespaces(sql: String): List<String> {
        return TABLESPACE_PATTERN.findAll(sql)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    /**
     * DDL에서 파티션 정보 추출
     */
    fun extractPartitionInfo(sql: String): List<PartitionInfo> {
        val partitions = mutableListOf<PartitionInfo>()

        PARTITION_BLOCK_PATTERN.find(sql)?.let { blockMatch ->
            val partitionDefs = blockMatch.groupValues[2]

            PARTITION_DEF_PATTERN.findAll(partitionDefs).forEach { match ->
                val name = match.groupValues[1]
                val valueType = match.groupValues[2]
                val values = match.groupValues[3]

                // 테이블스페이스 추출
                val tablespace = TABLESPACE_PATTERN.find(match.value)?.groupValues?.get(1)

                partitions.add(PartitionInfo(
                    name = name,
                    type = when {
                        valueType.uppercase().contains("LESS") -> PartitionValueType.RANGE
                        valueType.uppercase().contains("IN") -> PartitionValueType.LIST
                        else -> PartitionValueType.HASH
                    },
                    values = values.split(",").map { it.trim().removeSurrounding("'") },
                    tablespace = tablespace
                ))
            }
        }

        return partitions
    }
}
