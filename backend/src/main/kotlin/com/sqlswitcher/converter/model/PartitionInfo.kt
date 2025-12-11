package com.sqlswitcher.converter.model

/**
 * 파티션 타입
 */
enum class PartitionType {
    RANGE,      // 범위 파티션
    LIST,       // 목록 파티션
    HASH,       // 해시 파티션
    KEY,        // 키 파티션 (MySQL 전용)
    COLUMNS     // 컬럼 기반 파티션
}

/**
 * 파티션 정보를 담는 데이터 클래스
 */
data class PartitionInfo(
    val partitionType: PartitionType,
    val columns: List<String>,
    val partitions: List<PartitionDefinition>,
    val subpartitions: List<SubpartitionDefinition> = emptyList()
) {
    /**
     * Oracle 파티션 구문 생성
     */
    fun toOraclePartition(schemaOwner: String): String {
        val sb = StringBuilder()

        when (partitionType) {
            PartitionType.RANGE, PartitionType.COLUMNS -> {
                val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
                sb.appendLine("PARTITION BY RANGE ($columnsQuoted)")
                sb.appendLine("(")
                sb.append(partitions.mapIndexed { idx, p ->
                    val isLast = idx == partitions.size - 1
                    p.toOracleDefinition(isLast)
                }.joinToString(",\n"))
                sb.appendLine()
                sb.append(")")
            }
            PartitionType.LIST -> {
                val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
                sb.appendLine("PARTITION BY LIST ($columnsQuoted)")
                sb.appendLine("(")
                sb.append(partitions.joinToString(",\n") { it.toOracleDefinition(false) })
                sb.appendLine()
                sb.append(")")
            }
            PartitionType.HASH, PartitionType.KEY -> {
                val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
                sb.appendLine("PARTITION BY HASH ($columnsQuoted)")
                sb.append("PARTITIONS ${partitions.size}")
            }
        }

        return sb.toString()
    }

    /**
     * PostgreSQL 파티션 구문 생성
     */
    fun toPostgreSqlPartition(): String {
        val sb = StringBuilder()

        when (partitionType) {
            PartitionType.RANGE, PartitionType.COLUMNS -> {
                val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
                sb.append("PARTITION BY RANGE ($columnsQuoted)")
            }
            PartitionType.LIST -> {
                val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
                sb.append("PARTITION BY LIST ($columnsQuoted)")
            }
            PartitionType.HASH, PartitionType.KEY -> {
                val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
                sb.append("PARTITION BY HASH ($columnsQuoted)")
            }
        }

        return sb.toString()
    }

    /**
     * MySQL 파티션 구문 생성
     */
    fun toMySqlPartition(): String {
        val sb = StringBuilder()

        when (partitionType) {
            PartitionType.RANGE -> {
                val columnsStr = columns.joinToString(", ") { "`$it`" }
                sb.appendLine("PARTITION BY RANGE ($columnsStr)")
                sb.appendLine("(")
                sb.append(partitions.joinToString(",\n") { it.toMySqlDefinition() })
                sb.appendLine()
                sb.append(")")
            }
            PartitionType.COLUMNS -> {
                val columnsStr = columns.joinToString(", ") { "`$it`" }
                sb.appendLine("PARTITION BY RANGE COLUMNS ($columnsStr)")
                sb.appendLine("(")
                sb.append(partitions.joinToString(",\n") { it.toMySqlDefinition() })
                sb.appendLine()
                sb.append(")")
            }
            PartitionType.LIST -> {
                val columnsStr = columns.joinToString(", ") { "`$it`" }
                sb.appendLine("PARTITION BY LIST ($columnsStr)")
                sb.appendLine("(")
                sb.append(partitions.joinToString(",\n") { it.toMySqlDefinition() })
                sb.appendLine()
                sb.append(")")
            }
            PartitionType.HASH -> {
                val columnsStr = columns.joinToString(", ") { "`$it`" }
                sb.appendLine("PARTITION BY HASH ($columnsStr)")
                sb.append("PARTITIONS ${partitions.size}")
            }
            PartitionType.KEY -> {
                val columnsStr = columns.joinToString(", ") { "`$it`" }
                sb.appendLine("PARTITION BY KEY ($columnsStr)")
                sb.append("PARTITIONS ${partitions.size}")
            }
        }

        return sb.toString()
    }
}

/**
 * 개별 파티션 정의
 */
data class PartitionDefinition(
    val name: String,
    val value: String? = null,              // RANGE: LESS THAN 값, LIST: IN 값들
    val values: String? = null,             // 대체 필드 (values 형식 지원)
    val tablespace: String? = null,
    val subpartitions: List<SubpartitionDefinition> = emptyList(),
    val subpartitionCount: Int? = null      // 서브파티션 개수
) {
    // value와 values 중 사용 가능한 값 반환
    private val effectiveValue: String?
        get() = value ?: values
    /**
     * Oracle 파티션 정의 생성
     */
    fun toOracleDefinition(isLastPartition: Boolean): String {
        val sb = StringBuilder("    PARTITION \"$name\"")

        if (value != null) {
            if (isLastPartition && value.uppercase() == "MAXVALUE") {
                sb.append(" VALUES LESS THAN (MAXVALUE)")
            } else if (value.contains(",")) {
                // LIST 파티션
                sb.append(" VALUES ($value)")
            } else {
                // RANGE 파티션
                sb.append(" VALUES LESS THAN ($value)")
            }
        }

        tablespace?.let { sb.append(" TABLESPACE \"$it\"") }

        if (subpartitions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("    (")
            sb.append(subpartitions.joinToString(",\n") { it.toOracleDefinition() })
            sb.appendLine()
            sb.append("    )")
        }

        return sb.toString()
    }

    /**
     * PostgreSQL 파티션 테이블 생성문 (CREATE TABLE ... PARTITION OF)
     */
    fun toPostgreSqlDefinition(parentTable: String, partitionType: PartitionType): String {
        val sb = StringBuilder()
        sb.append("CREATE TABLE \"${parentTable}_$name\" PARTITION OF \"$parentTable\"")

        when (partitionType) {
            PartitionType.RANGE, PartitionType.COLUMNS -> {
                if (value?.uppercase() == "MAXVALUE") {
                    sb.append(" FOR VALUES FROM (MAXVALUE) TO (MAXVALUE)")
                } else {
                    sb.append(" FOR VALUES FROM ($value) TO (next_value)")  // 실제 사용시 조정 필요
                }
            }
            PartitionType.LIST -> {
                sb.append(" FOR VALUES IN ($value)")
            }
            else -> {
                sb.append(" FOR VALUES WITH (MODULUS ${partitionType.ordinal}, REMAINDER 0)")
            }
        }

        return sb.toString()
    }

    /**
     * MySQL 파티션 정의 생성
     */
    fun toMySqlDefinition(): String {
        val sb = StringBuilder("    PARTITION `$name`")

        if (value != null) {
            if (value.uppercase() == "MAXVALUE") {
                sb.append(" VALUES LESS THAN MAXVALUE")
            } else if (value.contains(",")) {
                // LIST 파티션
                sb.append(" VALUES IN ($value)")
            } else {
                // RANGE 파티션
                sb.append(" VALUES LESS THAN ($value)")
            }
        }

        // MySQL은 파티션별 TABLESPACE 미지원 (InnoDB)

        return sb.toString()
    }
}

/**
 * 서브파티션 정의
 */
data class SubpartitionDefinition(
    val name: String,
    val partitionName: String,
    val values: String? = null,
    val tablespace: String? = null
) {
    fun toOracleDefinition(): String {
        val sb = StringBuilder("        SUBPARTITION \"$name\"")
        values?.let { sb.append(" VALUES ($it)") }
        tablespace?.let { sb.append(" TABLESPACE \"$it\"") }
        return sb.toString()
    }

    fun toMySqlDefinition(): String {
        val sb = StringBuilder("        SUBPARTITION `$name`")
        values?.let { sb.append(" VALUES ($it)") }
        // MySQL은 서브파티션에 TABLESPACE 미지원
        return sb.toString()
    }
}

/**
 * 테이블 파티션 상세 정보 (파티션 변환 서비스용)
 */
data class TablePartitionDetailInfo(
    val tableName: String,
    val partitionType: PartitionType,
    val partitionColumns: List<String>,
    val partitions: List<PartitionDefinition>,
    val subpartitionType: PartitionType? = null,
    val subpartitionColumns: List<String> = emptyList(),
    val intervalExpression: String? = null
) {
    /**
     * PostgreSQL 파티션 절 생성
     */
    fun toPostgreSqlPartitionClause(): String {
        val columnsQuoted = partitionColumns.joinToString(", ") { "\"$it\"" }
        return when (partitionType) {
            PartitionType.RANGE, PartitionType.COLUMNS -> "PARTITION BY RANGE ($columnsQuoted)"
            PartitionType.LIST -> "PARTITION BY LIST ($columnsQuoted)"
            PartitionType.HASH, PartitionType.KEY -> "PARTITION BY HASH ($columnsQuoted)"
        }
    }

    /**
     * PostgreSQL 파티션 테이블 생성문 목록
     */
    fun toPostgreSqlPartitionTablesFull(schemaName: String?): List<String> {
        val prefix = schemaName?.let { "\"$it\"." } ?: ""
        return partitions.mapIndexed { idx, partition ->
            val partitionTableName = "${tableName}_${partition.name}"
            val sb = StringBuilder("CREATE TABLE $prefix\"$partitionTableName\" PARTITION OF $prefix\"$tableName\"")

            when (partitionType) {
                PartitionType.RANGE, PartitionType.COLUMNS -> {
                    val values = partition.values ?: partition.value
                    if (values?.uppercase()?.contains("MAXVALUE") == true ||
                        values?.uppercase()?.contains("LESS THAN") == true && values.uppercase().contains("MAXVALUE")) {
                        sb.append(" FOR VALUES FROM (MAXVALUE) TO (MAXVALUE)")
                    } else {
                        // LESS THAN 값에서 실제 값 추출
                        val cleanValue = values?.replace(Regex("""LESS\s+THAN\s*\(""", RegexOption.IGNORE_CASE), "")
                            ?.trimEnd(')')?.trim() ?: ""
                        if (idx == 0) {
                            sb.append(" FOR VALUES FROM (MINVALUE) TO ($cleanValue)")
                        } else {
                            val prevValues = partitions[idx - 1].values ?: partitions[idx - 1].value
                            val prevCleanValue = prevValues?.replace(Regex("""LESS\s+THAN\s*\(""", RegexOption.IGNORE_CASE), "")
                                ?.trimEnd(')')?.trim() ?: ""
                            sb.append(" FOR VALUES FROM ($prevCleanValue) TO ($cleanValue)")
                        }
                    }
                }
                PartitionType.LIST -> {
                    val values = partition.values ?: partition.value
                    val cleanValues = values?.replace(Regex("""IN\s*\(""", RegexOption.IGNORE_CASE), "")
                        ?.trimEnd(')')?.trim() ?: ""
                    sb.append(" FOR VALUES IN ($cleanValues)")
                }
                PartitionType.HASH, PartitionType.KEY -> {
                    sb.append(" FOR VALUES WITH (MODULUS ${partitions.size}, REMAINDER $idx)")
                }
            }

            sb.toString()
        }
    }

    /**
     * MySQL 파티션 절 생성
     */
    fun toMySqlPartitionClause(): String {
        val sb = StringBuilder()
        val columnsQuoted = partitionColumns.joinToString(", ") { "`$it`" }

        when (partitionType) {
            PartitionType.RANGE -> {
                sb.appendLine("PARTITION BY RANGE ($columnsQuoted)")
            }
            PartitionType.COLUMNS -> {
                sb.appendLine("PARTITION BY RANGE COLUMNS ($columnsQuoted)")
            }
            PartitionType.LIST -> {
                sb.appendLine("PARTITION BY LIST ($columnsQuoted)")
            }
            PartitionType.HASH -> {
                sb.appendLine("PARTITION BY HASH ($columnsQuoted)")
            }
            PartitionType.KEY -> {
                sb.appendLine("PARTITION BY KEY ($columnsQuoted)")
            }
        }

        // 서브파티션 정의
        if (subpartitionType != null && subpartitionColumns.isNotEmpty()) {
            val subColsQuoted = subpartitionColumns.joinToString(", ") { "`$it`" }
            when (subpartitionType) {
                PartitionType.HASH -> sb.appendLine("SUBPARTITION BY HASH ($subColsQuoted)")
                PartitionType.KEY -> sb.appendLine("SUBPARTITION BY KEY ($subColsQuoted)")
                else -> sb.appendLine("SUBPARTITION BY KEY ($subColsQuoted)") // MySQL은 HASH/KEY만 지원
            }
        }

        // 파티션 정의
        if (partitionType == PartitionType.HASH || partitionType == PartitionType.KEY) {
            sb.append("PARTITIONS ${partitions.size}")
        } else {
            sb.appendLine("(")
            sb.append(partitions.mapIndexed { idx, p ->
                val partDef = StringBuilder("    PARTITION `${p.name}`")
                val values = p.values ?: p.value

                when (partitionType) {
                    PartitionType.RANGE, PartitionType.COLUMNS -> {
                        if (values?.uppercase()?.contains("MAXVALUE") == true) {
                            partDef.append(" VALUES LESS THAN MAXVALUE")
                        } else {
                            partDef.append(" $values")
                        }
                    }
                    PartitionType.LIST -> {
                        partDef.append(" VALUES $values")
                    }
                    else -> {}
                }

                // 서브파티션 개수
                p.subpartitionCount?.let { count ->
                    if (count > 0) partDef.append(" SUBPARTITIONS $count")
                }

                partDef.toString()
            }.joinToString(",\n"))
            sb.appendLine()
            sb.append(")")
        }

        return sb.toString()
    }

    /**
     * Oracle 파티션 절 생성
     */
    fun toOraclePartitionClause(schemaOwner: String): String {
        val sb = StringBuilder()
        val columnsQuoted = partitionColumns.joinToString(", ") { "\"$it\"" }

        when (partitionType) {
            PartitionType.RANGE, PartitionType.COLUMNS -> {
                sb.appendLine("PARTITION BY RANGE ($columnsQuoted)")
            }
            PartitionType.LIST -> {
                sb.appendLine("PARTITION BY LIST ($columnsQuoted)")
            }
            PartitionType.HASH, PartitionType.KEY -> {
                sb.appendLine("PARTITION BY HASH ($columnsQuoted)")
            }
        }

        // INTERVAL 표현식
        intervalExpression?.let { sb.appendLine("INTERVAL ($it)") }

        // 서브파티션 정의
        if (subpartitionType != null && subpartitionColumns.isNotEmpty()) {
            val subColsQuoted = subpartitionColumns.joinToString(", ") { "\"$it\"" }
            when (subpartitionType) {
                PartitionType.HASH -> sb.appendLine("SUBPARTITION BY HASH ($subColsQuoted)")
                PartitionType.LIST -> sb.appendLine("SUBPARTITION BY LIST ($subColsQuoted)")
                PartitionType.RANGE -> sb.appendLine("SUBPARTITION BY RANGE ($subColsQuoted)")
                else -> sb.appendLine("SUBPARTITION BY HASH ($subColsQuoted)")
            }
        }

        // 파티션 정의
        if (partitionType == PartitionType.HASH || partitionType == PartitionType.KEY) {
            sb.append("PARTITIONS ${partitions.size}")
        } else {
            sb.appendLine("(")
            sb.append(partitions.mapIndexed { idx, p ->
                val partDef = StringBuilder("    PARTITION \"${p.name}\"")
                val values = p.values ?: p.value

                when (partitionType) {
                    PartitionType.RANGE, PartitionType.COLUMNS -> {
                        if (values?.uppercase()?.contains("MAXVALUE") == true) {
                            partDef.append(" VALUES LESS THAN (MAXVALUE)")
                        } else {
                            val cleanValue = values?.replace(Regex("""LESS\s+THAN\s*""", RegexOption.IGNORE_CASE), "")?.trim() ?: ""
                            partDef.append(" VALUES LESS THAN $cleanValue")
                        }
                    }
                    PartitionType.LIST -> {
                        val cleanValue = values?.replace(Regex("""IN\s*""", RegexOption.IGNORE_CASE), "")?.trim() ?: ""
                        partDef.append(" VALUES ($cleanValue)")
                    }
                    else -> {}
                }

                p.tablespace?.let { partDef.append(" TABLESPACE \"$it\"") }

                partDef.toString()
            }.joinToString(",\n"))
            sb.appendLine()
            sb.append(")")
        }

        return sb.toString()
    }
}