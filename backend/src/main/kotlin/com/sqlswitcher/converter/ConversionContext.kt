package com.sqlswitcher.converter

import com.sqlswitcher.parser.model.AstAnalysisResult
import net.sf.jsqlparser.statement.Statement

/**
 * SQL 변환 과정에서 사용되는 컨텍스트 정보를 담는 클래스
 */
data class ConversionContext(
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val statement: Statement,
    val analysisResult: AstAnalysisResult,
    val options: ConversionOptions = ConversionOptions(),
    val metadata: MutableMap<String, Any> = mutableMapOf()
) {
    
    /**
     * 컨텍스트에 메타데이터 추가
     */
    fun addMetadata(key: String, value: Any) {
        metadata[key] = value
    }
    
    /**
     * 컨텍스트에서 메타데이터 조회
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getMetadata(key: String): T? {
        return metadata[key] as? T
    }
    
    /**
     * 컨텍스트에서 메타데이터 조회 (기본값 포함)
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getMetadata(key: String, defaultValue: T): T {
        return (metadata[key] as? T) ?: defaultValue
    }
    
    /**
     * 변환 방향 확인
     */
    fun isSameDialect(): Boolean = sourceDialect == targetDialect
    
    /**
     * 특정 방언으로의 변환인지 확인
     */
    fun isConvertingTo(dialect: DialectType): Boolean = targetDialect == dialect
    
    /**
     * 특정 방언에서의 변환인지 확인
     */
    fun isConvertingFrom(dialect: DialectType): Boolean = sourceDialect == dialect
}

/**
 * 변환 옵션을 담는 데이터 클래스
 */
data class ConversionOptions(
    val preserveComments: Boolean = true,
    val formatOutput: Boolean = true,
    val includeWarnings: Boolean = true,
    val strictMode: Boolean = false,
    val customMappings: Map<String, String> = emptyMap(),
    val skipUnsupportedFeatures: Boolean = false,
    val maxComplexityScore: Int = 100
) {
    
    /**
     * 사용자 정의 매핑 추가
     */
    fun addCustomMapping(source: String, target: String): ConversionOptions {
        return copy(customMappings = customMappings + (source to target))
    }
    
    /**
     * 사용자 정의 매핑 조회
     */
    fun getCustomMapping(source: String): String? {
        return customMappings[source]
    }
    
    /**
     * 복잡도 점수 검증
     */
    fun isComplexityAcceptable(score: Int): Boolean {
        return score <= maxComplexityScore
    }
}

/**
 * 변환 컨텍스트 빌더
 */
class ConversionContextBuilder {
    private var sourceDialect: DialectType? = null
    private var targetDialect: DialectType? = null
    private var statement: Statement? = null
    private var analysisResult: AstAnalysisResult? = null
    private var options: ConversionOptions = ConversionOptions()
    private val metadata: MutableMap<String, Any> = mutableMapOf()
    
    fun sourceDialect(dialect: DialectType) = apply { this.sourceDialect = dialect }
    fun targetDialect(dialect: DialectType) = apply { this.targetDialect = dialect }
    fun statement(statement: Statement) = apply { this.statement = statement }
    fun analysisResult(result: AstAnalysisResult) = apply { this.analysisResult = result }
    fun options(options: ConversionOptions) = apply { this.options = options }
    fun addMetadata(key: String, value: Any) = apply { this.metadata[key] = value }
    
    fun build(): ConversionContext {
        requireNotNull(sourceDialect) { "Source dialect is required" }
        requireNotNull(targetDialect) { "Target dialect is required" }
        requireNotNull(statement) { "Statement is required" }
        requireNotNull(analysisResult) { "Analysis result is required" }
        
        return ConversionContext(
            sourceDialect = sourceDialect!!,
            targetDialect = targetDialect!!,
            statement = statement!!,
            analysisResult = analysisResult!!,
            options = options,
            metadata = metadata
        )
    }
}

/**
 * ConversionContext 생성 헬퍼 함수
 */
fun conversionContext(init: ConversionContextBuilder.() -> Unit): ConversionContext {
    return ConversionContextBuilder().apply(init).build()
}

/**
 * FOREIGN KEY 정보를 담는 데이터 클래스
 */
data class ForeignKeyInfo(
    val constraintName: String,
    val columns: List<String>,
    val referencedTable: String,
    val referencedColumns: List<String>,
    val onDelete: String? = null,   // CASCADE, SET NULL, SET DEFAULT, NO ACTION, RESTRICT
    val onUpdate: String? = null    // CASCADE, SET NULL, SET DEFAULT, NO ACTION, RESTRICT
) {
    /**
     * Oracle 스타일 FOREIGN KEY 제약조건 생성
     */
    fun toOracleConstraint(schemaOwner: String, tableName: String): String {
        val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
        val refColumnsQuoted = referencedColumns.joinToString(", ") { "\"$it\"" }
        val refTable = if (referencedTable.contains(".")) {
            referencedTable.split(".").joinToString(".") { "\"${it.trim('"')}\"" }
        } else {
            "\"$schemaOwner\".\"$referencedTable\""
        }

        val sb = StringBuilder()
        sb.append("ALTER TABLE \"$schemaOwner\".\"$tableName\" ADD CONSTRAINT \"$constraintName\"")
        sb.append(" FOREIGN KEY ($columnsQuoted)")
        sb.append(" REFERENCES $refTable ($refColumnsQuoted)")

        onDelete?.let { sb.append(" ON DELETE $it") }
        // Oracle은 ON UPDATE를 지원하지 않음

        sb.append(" ENABLE")
        return sb.toString()
    }

    /**
     * MySQL 스타일 FOREIGN KEY 제약조건 생성
     */
    fun toMySqlConstraint(tableName: String): String {
        val columnsQuoted = columns.joinToString(", ") { "`$it`" }
        val refColumnsQuoted = referencedColumns.joinToString(", ") { "`$it`" }
        val refTable = "`${referencedTable.trim('`', '"')}`"

        val sb = StringBuilder()
        sb.append("ALTER TABLE `$tableName` ADD CONSTRAINT `$constraintName`")
        sb.append(" FOREIGN KEY ($columnsQuoted)")
        sb.append(" REFERENCES $refTable ($refColumnsQuoted)")

        onDelete?.let { sb.append(" ON DELETE $it") }
        onUpdate?.let { sb.append(" ON UPDATE $it") }

        return sb.toString()
    }

    /**
     * PostgreSQL 스타일 FOREIGN KEY 제약조건 생성
     */
    fun toPostgreSqlConstraint(tableName: String): String {
        val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
        val refColumnsQuoted = referencedColumns.joinToString(", ") { "\"$it\"" }
        val refTable = "\"${referencedTable.trim('"')}\""

        val sb = StringBuilder()
        sb.append("ALTER TABLE \"$tableName\" ADD CONSTRAINT \"$constraintName\"")
        sb.append(" FOREIGN KEY ($columnsQuoted)")
        sb.append(" REFERENCES $refTable ($refColumnsQuoted)")

        onDelete?.let { sb.append(" ON DELETE $it") }
        onUpdate?.let { sb.append(" ON UPDATE $it") }

        return sb.toString()
    }
}

/**
 * UNIQUE 제약조건 정보를 담는 데이터 클래스
 */
data class UniqueConstraintInfo(
    val constraintName: String,
    val columns: List<String>
) {
    /**
     * Oracle 스타일 UNIQUE 제약조건 생성
     */
    fun toOracleConstraint(schemaOwner: String, tableName: String, indexspace: String? = null): String {
        val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
        val sb = StringBuilder()
        sb.append("ALTER TABLE \"$schemaOwner\".\"$tableName\" ADD CONSTRAINT \"$constraintName\"")
        sb.append(" UNIQUE ($columnsQuoted)")
        indexspace?.let { sb.append(" USING INDEX TABLESPACE \"$it\"") }
        sb.append(" ENABLE")
        return sb.toString()
    }

    /**
     * MySQL 스타일 UNIQUE 제약조건 생성
     */
    fun toMySqlConstraint(tableName: String): String {
        val columnsQuoted = columns.joinToString(", ") { "`$it`" }
        return "ALTER TABLE `$tableName` ADD CONSTRAINT `$constraintName` UNIQUE ($columnsQuoted)"
    }

    /**
     * PostgreSQL 스타일 UNIQUE 제약조건 생성
     */
    fun toPostgreSqlConstraint(tableName: String): String {
        val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
        return "ALTER TABLE \"$tableName\" ADD CONSTRAINT \"$constraintName\" UNIQUE ($columnsQuoted)"
    }
}

/**
 * CHECK 제약조건 정보를 담는 데이터 클래스
 */
data class CheckConstraintInfo(
    val constraintName: String,
    val expression: String
) {
    /**
     * Oracle 스타일 CHECK 제약조건 생성
     */
    fun toOracleConstraint(schemaOwner: String, tableName: String): String {
        // 표현식 내 백틱을 큰따옴표로 변환
        val oracleExpr = expression.replace("`", "\"")
        return "ALTER TABLE \"$schemaOwner\".\"$tableName\" ADD CONSTRAINT \"$constraintName\" CHECK ($oracleExpr) ENABLE"
    }

    /**
     * MySQL 스타일 CHECK 제약조건 생성
     */
    fun toMySqlConstraint(tableName: String): String {
        val mysqlExpr = expression.replace("\"", "`")
        return "ALTER TABLE `$tableName` ADD CONSTRAINT `$constraintName` CHECK ($mysqlExpr)"
    }

    /**
     * PostgreSQL 스타일 CHECK 제약조건 생성
     */
    fun toPostgreSqlConstraint(tableName: String): String {
        val pgExpr = expression.replace("`", "\"")
        return "ALTER TABLE \"$tableName\" ADD CONSTRAINT \"$constraintName\" CHECK ($pgExpr)"
    }
}

/**
 * 인덱스 컬럼 옵션 정보
 */
data class IndexColumnOption(
    val columnName: String,
    val sortOrder: SortOrder = SortOrder.ASC,
    val nullsPosition: NullsPosition? = null
) {
    enum class SortOrder { ASC, DESC }
    enum class NullsPosition { FIRST, LAST }

    fun toOracleColumn(): String {
        val sb = StringBuilder("\"$columnName\"")
        if (sortOrder == SortOrder.DESC) sb.append(" DESC")
        nullsPosition?.let {
            when (it) {
                NullsPosition.FIRST -> sb.append(" NULLS FIRST")
                NullsPosition.LAST -> sb.append(" NULLS LAST")
            }
        }
        return sb.toString()
    }

    fun toMySqlColumn(): String {
        val sb = StringBuilder("`$columnName`")
        if (sortOrder == SortOrder.DESC) sb.append(" DESC")
        // MySQL 8.0+에서만 DESC 인덱스 지원, NULLS FIRST/LAST는 미지원
        return sb.toString()
    }

    fun toPostgreSqlColumn(): String {
        val sb = StringBuilder("\"$columnName\"")
        if (sortOrder == SortOrder.DESC) sb.append(" DESC")
        nullsPosition?.let {
            when (it) {
                NullsPosition.FIRST -> sb.append(" NULLS FIRST")
                NullsPosition.LAST -> sb.append(" NULLS LAST")
            }
        }
        return sb.toString()
    }
}

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
 * 파티션 정의
 */
data class PartitionDefinition(
    val name: String,
    val values: String,          // LESS THAN 값 또는 LIST 값
    val tablespace: String? = null
) {
    fun toOracleDefinition(isLast: Boolean): String {
        val sb = StringBuilder("    PARTITION \"$name\" VALUES ")

        // MAXVALUE 처리
        if (values.uppercase().contains("MAXVALUE")) {
            sb.append("LESS THAN (MAXVALUE)")
        } else if (values.uppercase().startsWith("LESS THAN")) {
            // LESS THAN (값) 형식
            val valueMatch = Regex("LESS\\s+THAN\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE).find(values)
            val actualValue = valueMatch?.groupValues?.get(1) ?: values
            sb.append("LESS THAN ($actualValue)")
        } else if (values.uppercase().startsWith("IN")) {
            // IN (값들) 형식 (LIST 파티션)
            sb.append(values)
        } else {
            sb.append("LESS THAN ($values)")
        }

        tablespace?.let { sb.append(" TABLESPACE \"$it\"") }
        return sb.toString()
    }

    fun toMySqlDefinition(): String {
        val sb = StringBuilder("    PARTITION `$name` VALUES ")

        if (values.uppercase().contains("MAXVALUE")) {
            sb.append("LESS THAN MAXVALUE")
        } else if (values.uppercase().startsWith("LESS THAN")) {
            sb.append(values)
        } else if (values.uppercase().startsWith("IN")) {
            sb.append(values)
        } else {
            sb.append("LESS THAN ($values)")
        }

        return sb.toString()
    }

    fun toPostgreSqlDefinition(tableName: String, partitionType: PartitionType): String {
        val sb = StringBuilder("CREATE TABLE \"${tableName}_$name\" PARTITION OF \"$tableName\"")

        when (partitionType) {
            PartitionType.RANGE, PartitionType.COLUMNS -> {
                if (values.uppercase().contains("MAXVALUE")) {
                    sb.append(" FOR VALUES FROM (MAXVALUE) TO (MAXVALUE)")
                } else {
                    val valueMatch = Regex("LESS\\s+THAN\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE).find(values)
                    val actualValue = valueMatch?.groupValues?.get(1) ?: values
                    sb.append(" FOR VALUES FROM (MINVALUE) TO ($actualValue)")
                }
            }
            PartitionType.LIST -> {
                val inMatch = Regex("IN\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE).find(values)
                val listValues = inMatch?.groupValues?.get(1) ?: values
                sb.append(" FOR VALUES IN ($listValues)")
            }
            else -> { }
        }

        return sb.toString()
    }
}

/**
 * 서브파티션 정의
 */
data class SubpartitionDefinition(
    val name: String,
    val partitionName: String,
    val values: String? = null
)


/**
 * STORED PROCEDURE 파라미터 정보
 */
data class ProcedureParameter(
    val name: String,
    val mode: ParameterMode,        // IN, OUT, INOUT
    val dataType: String,
    val defaultValue: String? = null
) {
    enum class ParameterMode { IN, OUT, INOUT }

    fun toOracle(): String {
        val modeStr = when (mode) {
            ParameterMode.IN -> "IN"
            ParameterMode.OUT -> "OUT"
            ParameterMode.INOUT -> "IN OUT"
        }
        val defaultStr = defaultValue?.let { " DEFAULT $it" } ?: ""
        return "\"$name\" $modeStr $dataType$defaultStr"
    }

    fun toPostgreSql(): String {
        val modeStr = mode.name
        val defaultStr = defaultValue?.let { " DEFAULT $it" } ?: ""
        return "$modeStr \"$name\" $dataType$defaultStr"
    }

    fun toMySql(): String {
        val modeStr = mode.name
        val defaultStr = defaultValue?.let { " DEFAULT $it" } ?: ""
        return "$modeStr `$name` $dataType$defaultStr"
    }
}

/**
 * STORED PROCEDURE 정보
 */
data class ProcedureInfo(
    val name: String,
    val parameters: List<ProcedureParameter>,
    val body: String,
    val returnType: String? = null,     // FUNCTION인 경우 반환 타입
    val isFunction: Boolean = false,
    val language: String = "SQL",       // SQL, PLPGSQL 등
    val characteristics: List<String> = emptyList()  // DETERMINISTIC, NO SQL 등
) {
    /**
     * Oracle PROCEDURE/FUNCTION 생성
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        val objectType = if (isFunction) "FUNCTION" else "PROCEDURE"

        sb.append("CREATE OR REPLACE $objectType \"$schemaOwner\".\"$name\"")

        if (parameters.isNotEmpty()) {
            sb.appendLine(" (")
            sb.append(parameters.joinToString(",\n    ") { "    ${it.toOracle()}" })
            sb.appendLine()
            sb.append(")")
        }

        if (isFunction && returnType != null) {
            sb.appendLine()
            sb.append("RETURN $returnType")
        }

        sb.appendLine()
        sb.appendLine("IS")
        sb.appendLine("BEGIN")
        sb.append(body)
        sb.appendLine()
        sb.append("END;")

        return sb.toString()
    }

    /**
     * PostgreSQL PROCEDURE/FUNCTION 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        val objectType = if (isFunction) "FUNCTION" else "PROCEDURE"

        sb.append("CREATE OR REPLACE $objectType \"$name\"(")

        if (parameters.isNotEmpty()) {
            sb.appendLine()
            sb.append(parameters.joinToString(",\n    ") { "    ${it.toPostgreSql()}" })
            sb.appendLine()
        }

        sb.append(")")

        if (isFunction && returnType != null) {
            sb.appendLine()
            sb.append("RETURNS $returnType")
        }

        sb.appendLine()
        sb.appendLine("LANGUAGE plpgsql")
        sb.appendLine("AS \$\$")
        sb.appendLine("BEGIN")
        sb.append(body)
        sb.appendLine()
        sb.appendLine("END;")
        sb.append("\$\$;")

        return sb.toString()
    }

    /**
     * MySQL PROCEDURE/FUNCTION 생성
     */
    fun toMySql(): String {
        val sb = StringBuilder()
        val objectType = if (isFunction) "FUNCTION" else "PROCEDURE"

        sb.append("CREATE $objectType `$name`(")

        if (parameters.isNotEmpty()) {
            sb.append(parameters.joinToString(", ") { it.toMySql() })
        }

        sb.append(")")

        if (isFunction && returnType != null) {
            sb.appendLine()
            sb.append("RETURNS $returnType")
        }

        sb.appendLine()
        sb.appendLine("BEGIN")
        sb.append(body)
        sb.appendLine()
        sb.append("END")

        return sb.toString()
    }
}

/**
 * SEQUENCE 정보
 */
data class SequenceInfo(
    val name: String,
    val startWith: Long = 1,
    val incrementBy: Long = 1,
    val minValue: Long? = null,
    val maxValue: Long? = null,
    val cache: Int = 20,
    val cycle: Boolean = false
) {
    /**
     * Oracle SEQUENCE 생성
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.append("CREATE SEQUENCE \"$schemaOwner\".\"$name\"")
        sb.appendLine()
        sb.append("    START WITH $startWith")
        sb.appendLine()
        sb.append("    INCREMENT BY $incrementBy")
        minValue?.let { sb.appendLine(); sb.append("    MINVALUE $it") }
        maxValue?.let { sb.appendLine(); sb.append("    MAXVALUE $it") }
        if (cache > 1) {
            sb.appendLine()
            sb.append("    CACHE $cache")
        }
        if (cycle) {
            sb.appendLine()
            sb.append("    CYCLE")
        }
        return sb.toString()
    }

    /**
     * PostgreSQL SEQUENCE 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.append("CREATE SEQUENCE \"$name\"")
        sb.appendLine()
        sb.append("    START WITH $startWith")
        sb.appendLine()
        sb.append("    INCREMENT BY $incrementBy")
        minValue?.let { sb.appendLine(); sb.append("    MINVALUE $it") }
        maxValue?.let { sb.appendLine(); sb.append("    MAXVALUE $it") }
        if (cache > 1) {
            sb.appendLine()
            sb.append("    CACHE $cache")
        }
        if (cycle) {
            sb.appendLine()
            sb.append("    CYCLE")
        }
        return sb.toString()
    }

    /**
     * MySQL용 AUTO_INCREMENT 시뮬레이션 테이블 생성
     * MySQL 8.0 이전에서는 SEQUENCE를 직접 지원하지 않음
     */
    fun toMySqlSimulation(): String {
        return """
-- MySQL은 SEQUENCE를 직접 지원하지 않습니다.
-- AUTO_INCREMENT 컬럼을 사용하거나 아래 시뮬레이션 테이블을 사용하세요.
CREATE TABLE `${name}_seq` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB AUTO_INCREMENT=$startWith;

-- 다음 값 가져오기: INSERT INTO `${name}_seq` VALUES (NULL); SELECT LAST_INSERT_ID();
        """.trimIndent()
    }
}

/**
 * 재귀 CTE (WITH RECURSIVE) 정보
 */
data class RecursiveCteInfo(
    val cteName: String,
    val columns: List<String>,
    val anchorQuery: String,        // 비재귀(앵커) 부분
    val recursiveQuery: String,     // 재귀 부분
    val mainQuery: String           // 최종 SELECT
) {
    /**
     * Oracle 계층형 쿼리로 변환 (CONNECT BY 사용)
     * 주의: 모든 재귀 CTE가 CONNECT BY로 변환 가능하지는 않음
     */
    fun toOracleConnectBy(warnings: MutableList<ConversionWarning>): String {
        // Oracle 12c+ 에서는 WITH RECURSIVE도 지원하므로 그대로 사용
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "Oracle 12c 이상에서는 재귀 CTE를 지원합니다. 이전 버전에서는 CONNECT BY를 사용해야 합니다.",
            severity = WarningSeverity.INFO,
            suggestion = "Oracle 버전을 확인하고 적절한 구문을 선택하세요."
        ))

        val sb = StringBuilder()
        sb.appendLine("WITH \"$cteName\" (${columns.joinToString(", ") { "\"$it\"" }}) AS (")
        sb.appendLine("    $anchorQuery")
        sb.appendLine("    UNION ALL")
        sb.appendLine("    $recursiveQuery")
        sb.appendLine(")")
        sb.append(mainQuery)

        return sb.toString()
    }

    /**
     * PostgreSQL WITH RECURSIVE 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.appendLine("WITH RECURSIVE \"$cteName\" (${columns.joinToString(", ") { "\"$it\"" }}) AS (")
        sb.appendLine("    $anchorQuery")
        sb.appendLine("    UNION ALL")
        sb.appendLine("    $recursiveQuery")
        sb.appendLine(")")
        sb.append(mainQuery)

        return sb.toString()
    }

    /**
     * MySQL WITH RECURSIVE 생성 (MySQL 8.0+)
     */
    fun toMySql(warnings: MutableList<ConversionWarning>): String {
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "MySQL 8.0 이상에서만 WITH RECURSIVE를 지원합니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "MySQL 버전을 확인하세요. 5.7 이하에서는 저장 프로시저나 임시 테이블을 사용해야 합니다."
        ))

        val sb = StringBuilder()
        sb.appendLine("WITH RECURSIVE `$cteName` (${columns.joinToString(", ") { "`$it`" }}) AS (")
        sb.appendLine("    $anchorQuery")
        sb.appendLine("    UNION ALL")
        sb.appendLine("    $recursiveQuery")
        sb.appendLine(")")
        sb.append(mainQuery)

        return sb.toString()
    }
}

/**
 * 윈도우 함수 정보
 */
data class WindowFunctionInfo(
    val functionName: String,           // ROW_NUMBER, RANK, DENSE_RANK, LAG, LEAD 등
    val arguments: List<String> = emptyList(),  // 함수 인자 (LAG, LEAD의 경우)
    val partitionBy: List<String> = emptyList(),
    val orderBy: List<OrderByColumn> = emptyList(),
    val alias: String? = null
) {
    data class OrderByColumn(
        val column: String,
        val direction: String = "ASC",  // ASC, DESC
        val nullsPosition: String? = null  // NULLS FIRST, NULLS LAST
    )

    /**
     * Oracle 윈도우 함수 생성
     */
    fun toOracle(): String {
        val sb = StringBuilder()
        sb.append("$functionName(")
        if (arguments.isNotEmpty()) {
            sb.append(arguments.joinToString(", "))
        }
        sb.append(") OVER(")

        if (partitionBy.isNotEmpty()) {
            sb.append("PARTITION BY ")
            sb.append(partitionBy.joinToString(", ") { "\"$it\"" })
        }

        if (orderBy.isNotEmpty()) {
            if (partitionBy.isNotEmpty()) sb.append(" ")
            sb.append("ORDER BY ")
            sb.append(orderBy.joinToString(", ") { col ->
                val nullsStr = col.nullsPosition?.let { " $it" } ?: ""
                "\"${col.column}\" ${col.direction}$nullsStr"
            })
        }

        sb.append(")")
        alias?.let { sb.append(" AS \"$it\"") }

        return sb.toString()
    }

    /**
     * PostgreSQL 윈도우 함수 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.append("$functionName(")
        if (arguments.isNotEmpty()) {
            sb.append(arguments.joinToString(", "))
        }
        sb.append(") OVER(")

        if (partitionBy.isNotEmpty()) {
            sb.append("PARTITION BY ")
            sb.append(partitionBy.joinToString(", ") { "\"$it\"" })
        }

        if (orderBy.isNotEmpty()) {
            if (partitionBy.isNotEmpty()) sb.append(" ")
            sb.append("ORDER BY ")
            sb.append(orderBy.joinToString(", ") { col ->
                val nullsStr = col.nullsPosition?.let { " $it" } ?: ""
                "\"${col.column}\" ${col.direction}$nullsStr"
            })
        }

        sb.append(")")
        alias?.let { sb.append(" AS \"$it\"") }

        return sb.toString()
    }

    /**
     * MySQL 윈도우 함수 생성 (MySQL 8.0+)
     */
    fun toMySql(): String {
        val sb = StringBuilder()
        sb.append("$functionName(")
        if (arguments.isNotEmpty()) {
            sb.append(arguments.joinToString(", "))
        }
        sb.append(") OVER(")

        if (partitionBy.isNotEmpty()) {
            sb.append("PARTITION BY ")
            sb.append(partitionBy.joinToString(", ") { "`$it`" })
        }

        if (orderBy.isNotEmpty()) {
            if (partitionBy.isNotEmpty()) sb.append(" ")
            sb.append("ORDER BY ")
            sb.append(orderBy.joinToString(", ") { col ->
                // MySQL은 NULLS FIRST/LAST를 직접 지원하지 않음
                "`${col.column}` ${col.direction}"
            })
        }

        sb.append(")")
        alias?.let { sb.append(" AS `$it`") }

        return sb.toString()
    }
}

/**
 * MERGE (UPSERT) 문 정보
 */
data class MergeStatementInfo(
    val targetTable: String,
    val sourceTable: String?,           // USING 절의 테이블 또는 서브쿼리
    val sourceValues: List<String>?,    // INSERT 값들 (테이블이 아닌 경우)
    val matchCondition: String,         // ON 절 조건
    val matchedUpdate: Map<String, String>?,    // WHEN MATCHED THEN UPDATE SET
    val notMatchedInsert: Pair<List<String>, List<String>>?  // (columns, values)
) {
    /**
     * Oracle MERGE 문 생성
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.appendLine("MERGE INTO \"$schemaOwner\".\"$targetTable\" t")

        if (sourceTable != null) {
            sb.appendLine("USING \"$schemaOwner\".\"$sourceTable\" s")
        } else if (sourceValues != null) {
            sb.appendLine("USING (SELECT ${sourceValues.joinToString(", ")} FROM DUAL) s")
        }

        sb.appendLine("ON ($matchCondition)")

        matchedUpdate?.let { updates ->
            sb.appendLine("WHEN MATCHED THEN")
            sb.append("    UPDATE SET ")
            sb.appendLine(updates.entries.joinToString(", ") { "\"${it.key}\" = ${it.value}" })
        }

        notMatchedInsert?.let { (columns, values) ->
            sb.appendLine("WHEN NOT MATCHED THEN")
            sb.append("    INSERT (${columns.joinToString(", ") { "\"$it\"" }})")
            sb.appendLine()
            sb.append("    VALUES (${values.joinToString(", ")})")
        }

        return sb.toString()
    }

    /**
     * PostgreSQL UPSERT (INSERT ... ON CONFLICT) 문 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()

        notMatchedInsert?.let { (columns, values) ->
            sb.append("INSERT INTO \"$targetTable\" (${columns.joinToString(", ") { "\"$it\"" }})")
            sb.appendLine()
            sb.append("VALUES (${values.joinToString(", ")})")
            sb.appendLine()

            // ON CONFLICT 절 추출 (matchCondition에서 컬럼 추출)
            val conflictColumns = extractConflictColumns(matchCondition)
            sb.append("ON CONFLICT (${conflictColumns.joinToString(", ") { "\"$it\"" }})")

            matchedUpdate?.let { updates ->
                sb.appendLine()
                sb.append("DO UPDATE SET ")
                sb.append(updates.entries.joinToString(", ") { "\"${it.key}\" = EXCLUDED.\"${it.key}\"" })
            } ?: sb.append(" DO NOTHING")
        }

        return sb.toString()
    }

    /**
     * MySQL INSERT ... ON DUPLICATE KEY UPDATE 문 생성
     */
    fun toMySql(): String {
        val sb = StringBuilder()

        notMatchedInsert?.let { (columns, values) ->
            sb.append("INSERT INTO `$targetTable` (${columns.joinToString(", ") { "`$it`" }})")
            sb.appendLine()
            sb.append("VALUES (${values.joinToString(", ")})")

            matchedUpdate?.let { updates ->
                sb.appendLine()
                sb.append("ON DUPLICATE KEY UPDATE ")
                sb.append(updates.entries.joinToString(", ") { "`${it.key}` = VALUES(`${it.key}`)" })
            }
        }

        return sb.toString()
    }

    private fun extractConflictColumns(condition: String): List<String> {
        // t.column = s.column 패턴에서 컬럼명 추출
        val columnPattern = Regex("[ts]\\.([a-zA-Z_][a-zA-Z0-9_]*)")
        return columnPattern.findAll(condition)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }
}

/**
 * UPDATE JOIN 정보
 */
data class UpdateJoinInfo(
    val targetTable: String,
    val targetAlias: String?,
    val joinTable: String,
    val joinAlias: String?,
    val joinCondition: String,
    val setClause: Map<String, String>,
    val whereClause: String?
) {
    /**
     * Oracle UPDATE 문 생성 (서브쿼리 방식)
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.appendLine("UPDATE \"$schemaOwner\".\"$targetTable\" t")
        sb.appendLine("SET (${setClause.keys.joinToString(", ") { "\"$it\"" }}) = (")
        sb.appendLine("    SELECT ${setClause.values.joinToString(", ")}")
        sb.appendLine("    FROM \"$schemaOwner\".\"$joinTable\" j")
        sb.appendLine("    WHERE $joinCondition")
        sb.append(")")

        whereClause?.let {
            sb.appendLine()
            sb.append("WHERE $it")
        }

        return sb.toString()
    }

    /**
     * PostgreSQL UPDATE ... FROM 문 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.appendLine("UPDATE \"$targetTable\" AS t")
        sb.append("SET ")
        sb.appendLine(setClause.entries.joinToString(", ") { "\"${it.key}\" = ${it.value}" })
        sb.appendLine("FROM \"$joinTable\" AS j")
        sb.append("WHERE $joinCondition")

        whereClause?.let {
            sb.appendLine()
            sb.append("AND $it")
        }

        return sb.toString()
    }

    /**
     * MySQL UPDATE ... JOIN 문 생성
     */
    fun toMySql(): String {
        val sb = StringBuilder()
        sb.appendLine("UPDATE `$targetTable` AS t")
        sb.appendLine("INNER JOIN `$joinTable` AS j ON $joinCondition")
        sb.append("SET ")
        sb.append(setClause.entries.joinToString(", ") { "t.`${it.key}` = ${it.value}" })

        whereClause?.let {
            sb.appendLine()
            sb.append("WHERE $it")
        }

        return sb.toString()
    }
}

/**
 * DELETE JOIN 정보
 */
data class DeleteJoinInfo(
    val targetTable: String,
    val targetAlias: String?,
    val joinTable: String,
    val joinAlias: String?,
    val joinCondition: String,
    val whereClause: String?
) {
    /**
     * Oracle DELETE 문 생성 (서브쿼리 방식)
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.appendLine("DELETE FROM \"$schemaOwner\".\"$targetTable\"")
        sb.appendLine("WHERE EXISTS (")
        sb.appendLine("    SELECT 1 FROM \"$schemaOwner\".\"$joinTable\" j")
        sb.append("    WHERE $joinCondition")

        whereClause?.let {
            sb.appendLine()
            sb.append("    AND $it")
        }

        sb.appendLine()
        sb.append(")")

        return sb.toString()
    }

    /**
     * PostgreSQL DELETE ... USING 문 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.appendLine("DELETE FROM \"$targetTable\" AS t")
        sb.appendLine("USING \"$joinTable\" AS j")
        sb.append("WHERE $joinCondition")

        whereClause?.let {
            sb.appendLine()
            sb.append("AND $it")
        }

        return sb.toString()
    }

    /**
     * MySQL DELETE ... JOIN 문 생성
     */
    fun toMySql(): String {
        val sb = StringBuilder()
        sb.appendLine("DELETE t FROM `$targetTable` AS t")
        sb.appendLine("INNER JOIN `$joinTable` AS j ON $joinCondition")

        whereClause?.let {
            sb.append("WHERE $it")
        }

        return sb.toString()
    }
}

/**
 * PIVOT 정보
 */
data class PivotInfo(
    val sourceTable: String,
    val aggregateFunction: String,      // SUM, COUNT, AVG 등
    val aggregateColumn: String,        // 집계할 컬럼
    val pivotColumn: String,            // 피벗 기준 컬럼
    val pivotValues: List<String>,      // 피벗할 값들
    val groupByColumns: List<String>    // 그룹화 컬럼들
) {
    /**
     * Oracle PIVOT 문 생성
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.appendLine("SELECT *")
        sb.appendLine("FROM (")
        sb.appendLine("    SELECT ${(groupByColumns + listOf(pivotColumn, aggregateColumn)).joinToString(", ") { "\"$it\"" }}")
        sb.appendLine("    FROM \"$schemaOwner\".\"$sourceTable\"")
        sb.appendLine(")")
        sb.append("PIVOT (")
        sb.appendLine()
        sb.append("    $aggregateFunction(\"$aggregateColumn\")")
        sb.appendLine()
        sb.append("    FOR \"$pivotColumn\" IN (")
        sb.append(pivotValues.joinToString(", ") { "'$it' AS \"$it\"" })
        sb.appendLine(")")
        sb.append(")")

        return sb.toString()
    }

    /**
     * PostgreSQL crosstab 함수 사용 (tablefunc 확장 필요)
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.appendLine("-- PostgreSQL은 PIVOT을 직접 지원하지 않습니다.")
        sb.appendLine("-- crosstab 함수 또는 CASE WHEN을 사용하세요.")
        sb.appendLine("-- tablefunc 확장 필요: CREATE EXTENSION IF NOT EXISTS tablefunc;")
        sb.appendLine()
        sb.appendLine("SELECT ${groupByColumns.joinToString(", ") { "\"$it\"" }},")

        // CASE WHEN 방식으로 변환
        pivotValues.forEachIndexed { index, value ->
            sb.append("    $aggregateFunction(CASE WHEN \"$pivotColumn\" = '$value' THEN \"$aggregateColumn\" END) AS \"$value\"")
            if (index < pivotValues.size - 1) sb.append(",")
            sb.appendLine()
        }

        sb.appendLine("FROM \"$sourceTable\"")
        sb.append("GROUP BY ${groupByColumns.joinToString(", ") { "\"$it\"" }}")

        return sb.toString()
    }

    /**
     * MySQL CASE WHEN 방식으로 변환
     */
    fun toMySql(): String {
        val sb = StringBuilder()
        sb.appendLine("SELECT ${groupByColumns.joinToString(", ") { "`$it`" }},")

        pivotValues.forEachIndexed { index, value ->
            sb.append("    $aggregateFunction(CASE WHEN `$pivotColumn` = '$value' THEN `$aggregateColumn` END) AS `$value`")
            if (index < pivotValues.size - 1) sb.append(",")
            sb.appendLine()
        }

        sb.appendLine("FROM `$sourceTable`")
        sb.append("GROUP BY ${groupByColumns.joinToString(", ") { "`$it`" }}")

        return sb.toString()
    }
}

/**
 * UNPIVOT 정보
 */
data class UnpivotInfo(
    val sourceTable: String,
    val valueColumn: String,            // 값이 들어갈 컬럼명
    val nameColumn: String,             // 원래 컬럼명이 들어갈 컬럼명
    val unpivotColumns: List<String>,   // 언피벗할 컬럼들
    val keepColumns: List<String>       // 유지할 컬럼들
) {
    /**
     * Oracle UNPIVOT 문 생성
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.appendLine("SELECT *")
        sb.appendLine("FROM \"$schemaOwner\".\"$sourceTable\"")
        sb.append("UNPIVOT (")
        sb.appendLine()
        sb.append("    \"$valueColumn\" FOR \"$nameColumn\" IN (")
        sb.append(unpivotColumns.joinToString(", ") { "\"$it\"" })
        sb.appendLine(")")
        sb.append(")")

        return sb.toString()
    }

    /**
     * PostgreSQL UNION ALL 방식으로 변환
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        sb.appendLine("-- PostgreSQL은 UNPIVOT을 직접 지원하지 않습니다.")
        sb.appendLine("-- UNION ALL 또는 LATERAL JOIN을 사용하세요.")
        sb.appendLine()

        unpivotColumns.forEachIndexed { index, col ->
            sb.append("SELECT ${keepColumns.joinToString(", ") { "\"$it\"" }}, ")
            sb.append("'$col' AS \"$nameColumn\", ")
            sb.append("\"$col\" AS \"$valueColumn\" ")
            sb.append("FROM \"$sourceTable\"")
            if (index < unpivotColumns.size - 1) {
                sb.appendLine()
                sb.append("UNION ALL")
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    /**
     * MySQL UNION ALL 방식으로 변환
     */
    fun toMySql(): String {
        val sb = StringBuilder()

        unpivotColumns.forEachIndexed { index, col ->
            sb.append("SELECT ${keepColumns.joinToString(", ") { "`$it`" }}, ")
            sb.append("'$col' AS `$nameColumn`, ")
            sb.append("`$col` AS `$valueColumn` ")
            sb.append("FROM `$sourceTable`")
            if (index < unpivotColumns.size - 1) {
                sb.appendLine()
                sb.append("UNION ALL")
                sb.appendLine()
            }
        }

        return sb.toString()
    }
}

// ==================== Phase 3: 고급 DDL/DML 변환 ====================

/**
 * 함수 기반 인덱스 정보
 */
data class FunctionBasedIndexInfo(
    val indexName: String,
    val tableName: String,
    val expressions: List<String>,      // 함수 표현식 (예: UPPER(name), SUBSTR(code, 1, 3))
    val isUnique: Boolean = false,
    val tablespace: String? = null
) {
    /**
     * Oracle 함수 기반 인덱스 생성
     */
    fun toOracle(schemaOwner: String): String {
        val uniqueStr = if (isUnique) "UNIQUE " else ""
        val exprsQuoted = expressions.joinToString(", ")
        val tablespaceStr = tablespace?.let { " TABLESPACE \"$it\"" } ?: ""

        return "CREATE ${uniqueStr}INDEX \"$schemaOwner\".\"$indexName\" ON \"$schemaOwner\".\"$tableName\" ($exprsQuoted)$tablespaceStr"
    }

    /**
     * PostgreSQL 함수 기반 인덱스 생성
     */
    fun toPostgreSql(): String {
        val uniqueStr = if (isUnique) "UNIQUE " else ""
        val exprsConverted = expressions.map { convertExpressionToPostgreSql(it) }

        return "CREATE ${uniqueStr}INDEX \"$indexName\" ON \"$tableName\" (${exprsConverted.joinToString(", ")})"
    }

    /**
     * MySQL 함수 기반 인덱스 생성 (MySQL 8.0+)
     */
    fun toMySql(): String {
        val uniqueStr = if (isUnique) "UNIQUE " else ""
        val exprsConverted = expressions.map { "($it)" }  // MySQL은 괄호로 감싸야 함

        return "CREATE ${uniqueStr}INDEX `$indexName` ON `$tableName` (${exprsConverted.joinToString(", ")})"
    }

    private fun convertExpressionToPostgreSql(expr: String): String {
        var result = expr
        // Oracle/MySQL 함수 → PostgreSQL 함수 변환
        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
        result = result.replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
        result = result.replace(Regex("\\bSUBSTR\\s*\\(", RegexOption.IGNORE_CASE), "SUBSTRING(")
        return "($result)"
    }
}

/**
 * Materialized View 정보
 */
data class MaterializedViewInfo(
    val viewName: String,
    val selectQuery: String,
    val buildOption: BuildOption = BuildOption.IMMEDIATE,   // BUILD IMMEDIATE/DEFERRED
    val refreshOption: RefreshOption = RefreshOption.COMPLETE,  // REFRESH COMPLETE/FAST/FORCE
    val refreshSchedule: String? = null,    // NEXT SYSDATE + 1/24 등
    val enableQueryRewrite: Boolean = false
) {
    enum class BuildOption { IMMEDIATE, DEFERRED }
    enum class RefreshOption { COMPLETE, FAST, FORCE, NEVER }

    /**
     * Oracle Materialized View 생성
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.append("CREATE MATERIALIZED VIEW \"$schemaOwner\".\"$viewName\"")
        sb.appendLine()
        sb.append("BUILD ${buildOption.name}")
        sb.appendLine()

        when (refreshOption) {
            RefreshOption.NEVER -> sb.append("NEVER REFRESH")
            else -> {
                sb.append("REFRESH ${refreshOption.name}")
                refreshSchedule?.let {
                    sb.appendLine()
                    sb.append("START WITH SYSDATE NEXT $it")
                }
            }
        }

        if (enableQueryRewrite) {
            sb.appendLine()
            sb.append("ENABLE QUERY REWRITE")
        }

        sb.appendLine()
        sb.append("AS")
        sb.appendLine()
        sb.append(selectQuery)

        return sb.toString()
    }

    /**
     * PostgreSQL Materialized View 생성
     */
    fun toPostgreSql(warnings: MutableList<ConversionWarning>): String {
        val sb = StringBuilder()
        sb.append("CREATE MATERIALIZED VIEW \"$viewName\" AS")
        sb.appendLine()
        sb.append(selectQuery)

        // PostgreSQL은 BUILD, REFRESH 옵션을 직접 지원하지 않음
        if (refreshOption != RefreshOption.NEVER) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "PostgreSQL은 자동 REFRESH를 지원하지 않습니다. REFRESH MATERIALIZED VIEW를 수동으로 실행해야 합니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "pg_cron 등 스케줄러를 사용하거나 트리거 기반 갱신을 구현하세요."
            ))
        }

        if (enableQueryRewrite) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "PostgreSQL은 QUERY REWRITE를 지원하지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "쿼리에서 직접 Materialized View를 참조하세요."
            ))
        }

        return sb.toString()
    }

    /**
     * MySQL용 - Materialized View 시뮬레이션 (테이블 + 프로시저)
     */
    fun toMySql(warnings: MutableList<ConversionWarning>): String {
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "MySQL은 Materialized View를 직접 지원하지 않습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "일반 테이블 + 스케줄러/이벤트로 시뮬레이션합니다."
        ))

        val sb = StringBuilder()
        sb.appendLine("-- MySQL Materialized View 시뮬레이션")
        sb.appendLine("-- 1. 테이블 생성")
        sb.appendLine("CREATE TABLE `$viewName` AS")
        sb.appendLine(selectQuery.replace("\"", "`"))
        sb.appendLine(";")
        sb.appendLine()
        sb.appendLine("-- 2. 갱신 프로시저")
        sb.appendLine("DELIMITER //")
        sb.appendLine("CREATE PROCEDURE `refresh_$viewName`()")
        sb.appendLine("BEGIN")
        sb.appendLine("    TRUNCATE TABLE `$viewName`;")
        sb.appendLine("    INSERT INTO `$viewName`")
        sb.appendLine("    ${selectQuery.replace("\"", "`")};")
        sb.appendLine("END //")
        sb.appendLine("DELIMITER ;")
        sb.appendLine()
        sb.appendLine("-- 3. 이벤트 스케줄러 (선택 사항)")
        refreshSchedule?.let {
            sb.appendLine("CREATE EVENT `refresh_${viewName}_event`")
            sb.appendLine("ON SCHEDULE EVERY 1 HOUR")
            sb.appendLine("DO CALL `refresh_$viewName`();")
        }

        return sb.toString()
    }
}

/**
 * JSON 함수 정보
 */
data class JsonFunctionInfo(
    val functionType: JsonFunctionType,
    val jsonExpression: String,         // JSON 컬럼 또는 표현식
    val path: String?,                  // JSON 경로
    val arguments: List<String> = emptyList()
) {
    enum class JsonFunctionType {
        EXTRACT,        // JSON_EXTRACT / JSON_VALUE
        SET,            // JSON_SET
        INSERT,         // JSON_INSERT
        REPLACE,        // JSON_REPLACE
        REMOVE,         // JSON_REMOVE
        CONTAINS,       // JSON_CONTAINS
        ARRAY_LENGTH,   // JSON_LENGTH / JSON_ARRAY_LENGTH
        KEYS,           // JSON_KEYS
        OBJECT,         // JSON_OBJECT
        ARRAY,          // JSON_ARRAY
        QUERY           // JSON_QUERY (복합 결과 반환)
    }

    /**
     * Oracle JSON 함수로 변환
     */
    fun toOracle(): String {
        return when (functionType) {
            JsonFunctionType.EXTRACT -> "JSON_VALUE($jsonExpression, '$path')"
            JsonFunctionType.QUERY -> "JSON_QUERY($jsonExpression, '$path')"
            JsonFunctionType.CONTAINS -> "JSON_EXISTS($jsonExpression, '$path')"
            JsonFunctionType.OBJECT -> "JSON_OBJECT(${arguments.joinToString(", ")})"
            JsonFunctionType.ARRAY -> "JSON_ARRAY(${arguments.joinToString(", ")})"
            JsonFunctionType.ARRAY_LENGTH -> "JSON_VALUE($jsonExpression, '\$.size()')"
            else -> "/* JSON 함수 수동 변환 필요 */ $jsonExpression"
        }
    }

    /**
     * PostgreSQL JSON 함수로 변환
     */
    fun toPostgreSql(): String {
        // PostgreSQL은 -> (JSON), ->> (text) 연산자 사용
        val pgPath = path?.replace("$.", "")?.replace(".", "'->'")
        return when (functionType) {
            JsonFunctionType.EXTRACT -> "$jsonExpression->>'$pgPath'"
            JsonFunctionType.QUERY -> "$jsonExpression->'$pgPath'"
            JsonFunctionType.CONTAINS -> "$jsonExpression @> '${arguments.firstOrNull() ?: "{}"}'::jsonb"
            JsonFunctionType.OBJECT -> "jsonb_build_object(${arguments.joinToString(", ")})"
            JsonFunctionType.ARRAY -> "jsonb_build_array(${arguments.joinToString(", ")})"
            JsonFunctionType.ARRAY_LENGTH -> "jsonb_array_length($jsonExpression)"
            JsonFunctionType.KEYS -> "jsonb_object_keys($jsonExpression)"
            else -> "/* JSON 함수 수동 변환 필요 */ $jsonExpression"
        }
    }

    /**
     * MySQL JSON 함수로 변환
     */
    fun toMySql(): String {
        return when (functionType) {
            JsonFunctionType.EXTRACT -> "JSON_EXTRACT($jsonExpression, '$path')"
            JsonFunctionType.QUERY -> "JSON_EXTRACT($jsonExpression, '$path')"
            JsonFunctionType.SET -> "JSON_SET($jsonExpression, '$path', ${arguments.firstOrNull()})"
            JsonFunctionType.INSERT -> "JSON_INSERT($jsonExpression, '$path', ${arguments.firstOrNull()})"
            JsonFunctionType.REPLACE -> "JSON_REPLACE($jsonExpression, '$path', ${arguments.firstOrNull()})"
            JsonFunctionType.REMOVE -> "JSON_REMOVE($jsonExpression, '$path')"
            JsonFunctionType.CONTAINS -> "JSON_CONTAINS($jsonExpression, '${arguments.firstOrNull()}')"
            JsonFunctionType.ARRAY_LENGTH -> "JSON_LENGTH($jsonExpression)"
            JsonFunctionType.KEYS -> "JSON_KEYS($jsonExpression)"
            JsonFunctionType.OBJECT -> "JSON_OBJECT(${arguments.joinToString(", ")})"
            JsonFunctionType.ARRAY -> "JSON_ARRAY(${arguments.joinToString(", ")})"
        }
    }
}

/**
 * 정규식 함수 정보
 */
data class RegexFunctionInfo(
    val functionType: RegexFunctionType,
    val sourceExpression: String,
    val pattern: String,
    val replacement: String? = null,    // REPLACE 함수용
    val position: Int = 1,              // 시작 위치
    val occurrence: Int = 0,            // 발생 횟수 (0 = 모두)
    val matchParam: String? = null      // 매치 파라미터 (i, c, n 등)
) {
    enum class RegexFunctionType {
        LIKE,           // REGEXP_LIKE / REGEXP / RLIKE
        SUBSTR,         // REGEXP_SUBSTR
        REPLACE,        // REGEXP_REPLACE
        INSTR,          // REGEXP_INSTR
        COUNT           // REGEXP_COUNT
    }

    /**
     * Oracle 정규식 함수로 변환
     */
    fun toOracle(): String {
        val matchStr = matchParam?.let { ", '$it'" } ?: ""
        return when (functionType) {
            RegexFunctionType.LIKE -> "REGEXP_LIKE($sourceExpression, '$pattern'$matchStr)"
            RegexFunctionType.SUBSTR -> "REGEXP_SUBSTR($sourceExpression, '$pattern', $position, $occurrence$matchStr)"
            RegexFunctionType.REPLACE -> "REGEXP_REPLACE($sourceExpression, '$pattern', '${replacement ?: ""}')"
            RegexFunctionType.INSTR -> "REGEXP_INSTR($sourceExpression, '$pattern', $position, $occurrence$matchStr)"
            RegexFunctionType.COUNT -> "REGEXP_COUNT($sourceExpression, '$pattern'$matchStr)"
        }
    }

    /**
     * PostgreSQL 정규식 함수로 변환
     */
    fun toPostgreSql(): String {
        // PostgreSQL은 ~ (매치), ~* (대소문자 무시), SIMILAR TO 등 사용
        val flags = if (matchParam?.contains("i") == true) "*" else ""
        return when (functionType) {
            RegexFunctionType.LIKE -> "$sourceExpression ~$flags '$pattern'"
            RegexFunctionType.SUBSTR -> "(regexp_matches($sourceExpression, '$pattern'))[1]"
            RegexFunctionType.REPLACE -> "regexp_replace($sourceExpression, '$pattern', '${replacement ?: ""}')"
            RegexFunctionType.INSTR -> "/* REGEXP_INSTR 미지원, position() + regexp_matches 조합 필요 */"
            RegexFunctionType.COUNT -> "(SELECT COUNT(*) FROM regexp_matches($sourceExpression, '$pattern', 'g'))"
        }
    }

    /**
     * MySQL 정규식 함수로 변환
     */
    fun toMySql(): String {
        return when (functionType) {
            RegexFunctionType.LIKE -> "$sourceExpression REGEXP '$pattern'"
            RegexFunctionType.SUBSTR -> "REGEXP_SUBSTR($sourceExpression, '$pattern', $position, $occurrence)"
            RegexFunctionType.REPLACE -> "REGEXP_REPLACE($sourceExpression, '$pattern', '${replacement ?: ""}')"
            RegexFunctionType.INSTR -> "REGEXP_INSTR($sourceExpression, '$pattern', $position, $occurrence)"
            RegexFunctionType.COUNT -> "/* MySQL 8.0에서는 REGEXP_COUNT 미지원. LENGTH 기반 계산 필요 */"
        }
    }
}

/**
 * 테이블 파티션 세부 정보 (Phase 3 확장)
 */
data class TablePartitionDetailInfo(
    val tableName: String,
    val partitionType: PartitionType,
    val partitionColumns: List<String>,
    val partitions: List<PartitionDefinition>,
    val subpartitionType: PartitionType? = null,
    val subpartitionColumns: List<String> = emptyList(),
    val intervalExpression: String? = null  // Oracle INTERVAL 파티션
) {
    /**
     * Oracle 파티션 테이블 생성
     */
    fun toOraclePartitionClause(schemaOwner: String): String {
        val sb = StringBuilder()

        when (partitionType) {
            PartitionType.RANGE -> {
                sb.appendLine("PARTITION BY RANGE (${partitionColumns.joinToString(", ") { "\"$it\"" }})")
                intervalExpression?.let {
                    sb.appendLine("INTERVAL ($it)")
                }
            }
            PartitionType.LIST -> {
                sb.appendLine("PARTITION BY LIST (${partitionColumns.joinToString(", ") { "\"$it\"" }})")
            }
            PartitionType.HASH -> {
                sb.appendLine("PARTITION BY HASH (${partitionColumns.joinToString(", ") { "\"$it\"" }})")
            }
            else -> sb.appendLine("PARTITION BY RANGE (${partitionColumns.joinToString(", ") { "\"$it\"" }})")
        }

        // 서브파티션
        subpartitionType?.let { subType ->
            sb.appendLine("SUBPARTITION BY ${subType.name} (${subpartitionColumns.joinToString(", ") { "\"$it\"" }})")
        }

        // 파티션 정의
        sb.appendLine("(")
        sb.append(partitions.mapIndexed { idx, p ->
            val isLast = idx == partitions.size - 1
            "    ${p.toOracleDefinition(isLast)}"
        }.joinToString(",\n"))
        sb.appendLine()
        sb.append(")")

        return sb.toString()
    }

    /**
     * PostgreSQL 파티션 테이블 생성
     */
    fun toPostgreSqlPartitionClause(): String {
        val sb = StringBuilder()

        when (partitionType) {
            PartitionType.RANGE -> {
                sb.append("PARTITION BY RANGE (${partitionColumns.joinToString(", ") { "\"$it\"" }})")
            }
            PartitionType.LIST -> {
                sb.append("PARTITION BY LIST (${partitionColumns.joinToString(", ") { "\"$it\"" }})")
            }
            PartitionType.HASH -> {
                sb.append("PARTITION BY HASH (${partitionColumns.joinToString(", ") { "\"$it\"" }})")
            }
            else -> sb.append("PARTITION BY RANGE (${partitionColumns.joinToString(", ") { "\"$it\"" }})")
        }

        return sb.toString()
    }

    /**
     * PostgreSQL 파티션 테이블 개별 파티션 생성문
     */
    fun toPostgreSqlPartitionTables(): List<String> {
        return partitions.map { p ->
            p.toPostgreSqlDefinition(tableName, partitionType)
        }
    }

    /**
     * MySQL 파티션 테이블 생성
     */
    fun toMySqlPartitionClause(): String {
        val sb = StringBuilder()

        when (partitionType) {
            PartitionType.RANGE -> {
                sb.appendLine("PARTITION BY RANGE (${partitionColumns.joinToString(", ") { "`$it`" }})")
            }
            PartitionType.COLUMNS -> {
                sb.appendLine("PARTITION BY RANGE COLUMNS (${partitionColumns.joinToString(", ") { "`$it`" }})")
            }
            PartitionType.LIST -> {
                sb.appendLine("PARTITION BY LIST (${partitionColumns.joinToString(", ") { "`$it`" }})")
            }
            PartitionType.HASH -> {
                sb.appendLine("PARTITION BY HASH (${partitionColumns.joinToString(", ") { "`$it`" }})")
            }
            PartitionType.KEY -> {
                sb.appendLine("PARTITION BY KEY (${partitionColumns.joinToString(", ") { "`$it`" }})")
            }
        }

        // 파티션 정의
        sb.appendLine("(")
        sb.append(partitions.joinToString(",\n") { "    ${it.toMySqlDefinition()}" })
        sb.appendLine()
        sb.append(")")

        return sb.toString()
    }
}

// ==================== Phase 4: 고급 데이터베이스 객체 변환 ====================

/**
 * 트리거 정보
 */
data class TriggerInfo(
    val name: String,
    val tableName: String,
    val timing: TriggerTiming,           // BEFORE, AFTER, INSTEAD OF
    val events: List<TriggerEvent>,      // INSERT, UPDATE, DELETE
    val forEachRow: Boolean = true,
    val whenCondition: String? = null,   // WHEN 조건
    val body: String,                    // 트리거 본문
    val referencing: ReferencingClause? = null,  // OLD/NEW 별칭
    val triggerOrder: String? = null,    // FOLLOWS, PRECEDES (MySQL 5.7+)
    val orderTriggerName: String? = null // triggerOrder가 참조하는 트리거 이름
) {
    enum class TriggerTiming { BEFORE, AFTER, INSTEAD_OF }
    enum class TriggerEvent { INSERT, UPDATE, DELETE }

    data class ReferencingClause(
        val oldAlias: String? = "OLD",
        val newAlias: String? = "NEW"
    )

    /**
     * Oracle 트리거 생성
     */
    fun toOracle(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.append("CREATE OR REPLACE TRIGGER \"$schemaOwner\".\"$name\"")
        sb.appendLine()
        sb.append("${timing.name.replace("_", " ")} ${events.joinToString(" OR ") { it.name }}")
        sb.appendLine()
        sb.append("ON \"$schemaOwner\".\"$tableName\"")

        referencing?.let { ref ->
            sb.appendLine()
            sb.append("REFERENCING")
            ref.oldAlias?.let { sb.append(" OLD AS $it") }
            ref.newAlias?.let { sb.append(" NEW AS $it") }
        }

        if (forEachRow) {
            sb.appendLine()
            sb.append("FOR EACH ROW")
        }

        whenCondition?.let {
            sb.appendLine()
            sb.append("WHEN ($it)")
        }

        sb.appendLine()
        sb.appendLine("BEGIN")
        sb.append(body)
        sb.appendLine()
        sb.append("END;")

        return sb.toString()
    }

    /**
     * PostgreSQL 트리거 생성
     */
    fun toPostgreSql(): String {
        val sb = StringBuilder()
        val funcName = "${name}_func"

        // 1. 트리거 함수 생성
        sb.appendLine("-- 트리거 함수")
        sb.appendLine("CREATE OR REPLACE FUNCTION \"$funcName\"()")
        sb.appendLine("RETURNS TRIGGER AS \$\$")
        sb.appendLine("BEGIN")

        // 본문 변환 (Oracle → PostgreSQL)
        var pgBody = body
            .replace(Regex("\\b:NEW\\.", RegexOption.IGNORE_CASE), "NEW.")
            .replace(Regex("\\b:OLD\\.", RegexOption.IGNORE_CASE), "OLD.")
            .replace(Regex("\\bRAISE_APPLICATION_ERROR\\s*\\(", RegexOption.IGNORE_CASE), "RAISE EXCEPTION ")

        sb.appendLine(pgBody)

        // RETURN 문 추가
        if (timing == TriggerTiming.BEFORE || timing == TriggerTiming.INSTEAD_OF) {
            if (events.contains(TriggerEvent.DELETE)) {
                sb.appendLine("    RETURN OLD;")
            } else {
                sb.appendLine("    RETURN NEW;")
            }
        } else {
            sb.appendLine("    RETURN NULL;")
        }

        sb.appendLine("END;")
        sb.appendLine("\$\$ LANGUAGE plpgsql;")
        sb.appendLine()

        // 2. 트리거 생성
        sb.appendLine("-- 트리거")
        sb.append("CREATE TRIGGER \"$name\"")
        sb.appendLine()

        val pgTiming = when (timing) {
            TriggerTiming.INSTEAD_OF -> "INSTEAD OF"
            else -> timing.name
        }
        sb.append("$pgTiming ${events.joinToString(" OR ") { it.name }}")
        sb.appendLine()
        sb.append("ON \"$tableName\"")

        if (forEachRow) {
            sb.appendLine()
            sb.append("FOR EACH ROW")
        }

        whenCondition?.let {
            sb.appendLine()
            sb.append("WHEN ($it)")
        }

        sb.appendLine()
        sb.append("EXECUTE FUNCTION \"$funcName\"();")

        return sb.toString()
    }

    /**
     * MySQL 트리거 생성
     */
    fun toMySql(warnings: MutableList<ConversionWarning>): String {
        // MySQL은 한 트리거에 하나의 이벤트만 지원
        if (events.size > 1) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL은 트리거당 하나의 이벤트만 지원합니다. 여러 트리거로 분할됩니다.",
                severity = WarningSeverity.WARNING
            ))
        }

        if (timing == TriggerTiming.INSTEAD_OF) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "MySQL은 INSTEAD OF 트리거를 지원하지 않습니다.",
                severity = WarningSeverity.ERROR,
                suggestion = "BEFORE 트리거로 대체하거나 애플리케이션 레벨에서 처리하세요."
            ))
        }

        val sb = StringBuilder()

        events.forEachIndexed { index, event ->
            if (index > 0) {
                sb.appendLine()
                sb.appendLine()
            }

            val triggerName = if (events.size > 1) "${name}_${event.name.lowercase()}" else name

            sb.appendLine("DELIMITER //")
            sb.append("CREATE TRIGGER `$triggerName`")
            sb.appendLine()
            sb.append("${timing.name} $event")
            sb.appendLine()
            sb.append("ON `$tableName`")

            if (forEachRow) {
                sb.appendLine()
                sb.append("FOR EACH ROW")
            }

            sb.appendLine()
            sb.appendLine("BEGIN")

            // 본문 변환 (Oracle → MySQL)
            var mysqlBody = body
                .replace(Regex("\\b:NEW\\.", RegexOption.IGNORE_CASE), "NEW.")
                .replace(Regex("\\b:OLD\\.", RegexOption.IGNORE_CASE), "OLD.")
                .replace(Regex("\\bRAISE_APPLICATION_ERROR\\s*\\([^,]+,\\s*", RegexOption.IGNORE_CASE), "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = ")

            sb.appendLine(mysqlBody)
            sb.appendLine("END //")
            sb.append("DELIMITER ;")
        }

        return sb.toString()
    }
}

/**
 * 커서 정보 (저장 프로시저 내)
 */
data class CursorInfo(
    val name: String,
    val query: String,
    val parameters: List<CursorParameter> = emptyList(),
    val isRefCursor: Boolean = false     // REF CURSOR (Oracle)
) {
    data class CursorParameter(
        val name: String,
        val dataType: String
    )

    /**
     * Oracle 커서 선언
     */
    fun toOracleDeclare(): String {
        return if (parameters.isEmpty()) {
            "CURSOR $name IS\n    $query;"
        } else {
            val params = parameters.joinToString(", ") { "${it.name} ${it.dataType}" }
            "CURSOR $name ($params) IS\n    $query;"
        }
    }

    /**
     * PostgreSQL 커서 선언 (DECLARE 블록)
     */
    fun toPostgreSqlDeclare(): String {
        return "$name CURSOR FOR\n    $query;"
    }

    /**
     * MySQL 커서 선언
     */
    fun toMySqlDeclare(): String {
        return "DECLARE $name CURSOR FOR\n    $query;"
    }
}

/**
 * 예외 처리 정보
 */
data class ExceptionHandlerInfo(
    val exceptionName: String,           // NO_DATA_FOUND, TOO_MANY_ROWS, DUP_VAL_ON_INDEX, OTHERS 등
    val errorCode: Int? = null,          // Oracle 에러 코드 (-20001 등)
    val handlerBody: String              // 예외 처리 본문
) {
    /**
     * Oracle 예외 핸들러
     */
    fun toOracleHandler(): String {
        return "WHEN $exceptionName THEN\n    $handlerBody"
    }

    /**
     * PostgreSQL 예외 핸들러
     */
    fun toPostgreSqlHandler(): String {
        val pgException = when (exceptionName.uppercase()) {
            "NO_DATA_FOUND" -> "NO_DATA_FOUND"
            "TOO_MANY_ROWS" -> "TOO_MANY_ROWS"
            "DUP_VAL_ON_INDEX" -> "UNIQUE_VIOLATION"
            "OTHERS" -> "OTHERS"
            else -> exceptionName
        }
        return "WHEN $pgException THEN\n    $handlerBody"
    }

    /**
     * MySQL 핸들러
     */
    fun toMySqlHandler(): String {
        val (condition, handlerType) = when (exceptionName.uppercase()) {
            "NO_DATA_FOUND" -> Pair("NOT FOUND", "CONTINUE")
            "TOO_MANY_ROWS" -> Pair("SQLSTATE '21000'", "EXIT")
            "DUP_VAL_ON_INDEX" -> Pair("SQLSTATE '23000'", "EXIT")
            "OTHERS" -> Pair("SQLEXCEPTION", "EXIT")
            else -> Pair("SQLEXCEPTION", "EXIT")
        }
        return "DECLARE $handlerType HANDLER FOR $condition\nBEGIN\n    $handlerBody\nEND;"
    }
}

/**
 * 사용자 정의 타입 정보
 */
data class UserDefinedTypeInfo(
    val name: String,
    val typeCategory: TypeCategory,
    val attributes: List<TypeAttribute> = emptyList(),   // OBJECT 타입용
    val elementType: String? = null,                      // VARRAY/TABLE 타입용
    val maxSize: Int? = null                              // VARRAY 타입용
) {
    enum class TypeCategory {
        OBJECT,         // 복합 객체 타입
        VARRAY,         // 가변 배열
        NESTED_TABLE,   // 중첩 테이블
        ENUM            // ENUM 타입 (PostgreSQL)
    }

    data class TypeAttribute(
        val name: String,
        val dataType: String
    )

    /**
     * Oracle 사용자 정의 타입 생성
     */
    fun toOracle(schemaOwner: String): String {
        return when (typeCategory) {
            TypeCategory.OBJECT -> {
                val attrs = attributes.joinToString(",\n    ") { "${it.name} ${it.dataType}" }
                "CREATE OR REPLACE TYPE \"$schemaOwner\".\"$name\" AS OBJECT (\n    $attrs\n);"
            }
            TypeCategory.VARRAY -> {
                "CREATE OR REPLACE TYPE \"$schemaOwner\".\"$name\" AS VARRAY($maxSize) OF $elementType;"
            }
            TypeCategory.NESTED_TABLE -> {
                "CREATE OR REPLACE TYPE \"$schemaOwner\".\"$name\" AS TABLE OF $elementType;"
            }
            TypeCategory.ENUM -> {
                // Oracle은 ENUM 없음, CHECK 제약조건 또는 별도 테이블로 처리
                "-- Oracle은 ENUM을 직접 지원하지 않습니다.\n-- CHECK 제약조건이나 참조 테이블을 사용하세요."
            }
        }
    }

    /**
     * PostgreSQL 사용자 정의 타입 생성
     */
    fun toPostgreSql(): String {
        return when (typeCategory) {
            TypeCategory.OBJECT -> {
                val attrs = attributes.joinToString(",\n    ") { "\"${it.name}\" ${it.dataType}" }
                "CREATE TYPE \"$name\" AS (\n    $attrs\n);"
            }
            TypeCategory.VARRAY, TypeCategory.NESTED_TABLE -> {
                // PostgreSQL은 배열 타입 사용
                "-- PostgreSQL은 네이티브 배열 사용: ${elementType}[]"
            }
            TypeCategory.ENUM -> {
                val values = attributes.joinToString(", ") { "'${it.name}'" }
                "CREATE TYPE \"$name\" AS ENUM ($values);"
            }
        }
    }

    /**
     * MySQL 사용자 정의 타입 (제한적 지원)
     */
    fun toMySql(warnings: MutableList<ConversionWarning>): String {
        warnings.add(ConversionWarning(
            type = WarningType.UNSUPPORTED_FUNCTION,
            message = "MySQL은 사용자 정의 타입을 직접 지원하지 않습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "JSON 컬럼 또는 별도 테이블로 대체하세요."
        ))

        return when (typeCategory) {
            TypeCategory.OBJECT -> {
                // JSON 컬럼으로 시뮬레이션
                "-- MySQL: JSON 컬럼으로 시뮬레이션\n-- 컬럼 타입으로 JSON을 사용하세요."
            }
            TypeCategory.VARRAY, TypeCategory.NESTED_TABLE -> {
                "-- MySQL: JSON 배열로 시뮬레이션\n-- 컬럼 타입으로 JSON을 사용하세요."
            }
            TypeCategory.ENUM -> {
                val values = attributes.joinToString(", ") { "'${it.name}'" }
                "-- MySQL ENUM 타입\nENUM($values)"
            }
        }
    }
}

/**
 * 시노님 정보
 */
data class SynonymInfo(
    val name: String,
    val targetSchema: String?,
    val targetObject: String,
    val isPublic: Boolean = false
) {
    /**
     * Oracle 시노님 생성
     */
    fun toOracle(schemaOwner: String): String {
        val publicKeyword = if (isPublic) "PUBLIC " else ""
        val targetRef = if (targetSchema != null) "\"$targetSchema\".\"$targetObject\"" else "\"$targetObject\""
        return if (isPublic) {
            "CREATE OR REPLACE ${publicKeyword}SYNONYM \"$name\" FOR $targetRef;"
        } else {
            "CREATE OR REPLACE SYNONYM \"$schemaOwner\".\"$name\" FOR $targetRef;"
        }
    }

    /**
     * PostgreSQL 시노님 (뷰로 시뮬레이션)
     */
    fun toPostgreSql(warnings: MutableList<ConversionWarning>): String {
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "PostgreSQL은 시노님을 직접 지원하지 않습니다. 뷰로 시뮬레이션됩니다.",
            severity = WarningSeverity.INFO
        ))

        val targetRef = if (targetSchema != null) "\"$targetSchema\".\"$targetObject\"" else "\"$targetObject\""
        return "CREATE OR REPLACE VIEW \"$name\" AS SELECT * FROM $targetRef;"
    }

    /**
     * MySQL 시노님 (뷰로 시뮬레이션)
     */
    fun toMySql(warnings: MutableList<ConversionWarning>): String {
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "MySQL은 시노님을 직접 지원하지 않습니다. 뷰로 시뮬레이션됩니다.",
            severity = WarningSeverity.INFO
        ))

        val targetRef = if (targetSchema != null) "`$targetSchema`.`$targetObject`" else "`$targetObject`"
        return "CREATE OR REPLACE VIEW `$name` AS SELECT * FROM $targetRef;"
    }
}

/**
 * 데이터베이스 링크 정보
 */
data class DatabaseLinkInfo(
    val name: String,
    val connectString: String,           // 연결 문자열 (host:port/service)
    val username: String?,
    val isPublic: Boolean = false
) {
    /**
     * Oracle 데이터베이스 링크 생성
     */
    fun toOracle(): String {
        val publicKeyword = if (isPublic) "PUBLIC " else ""
        val sb = StringBuilder()
        sb.append("CREATE ${publicKeyword}DATABASE LINK \"$name\"")
        sb.appendLine()

        username?.let {
            sb.append("CONNECT TO \"$it\" IDENTIFIED BY \"****\"")
            sb.appendLine()
        }

        sb.append("USING '$connectString'")
        sb.append(";")

        return sb.toString()
    }

    /**
     * PostgreSQL FDW (Foreign Data Wrapper)
     */
    fun toPostgreSql(warnings: MutableList<ConversionWarning>): String {
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "PostgreSQL은 Database Link 대신 Foreign Data Wrapper (FDW)를 사용합니다.",
            severity = WarningSeverity.INFO,
            suggestion = "postgres_fdw 확장을 설치하고 설정하세요."
        ))

        val sb = StringBuilder()
        sb.appendLine("-- PostgreSQL Foreign Data Wrapper 설정")
        sb.appendLine("-- 1. 확장 설치 (한 번만)")
        sb.appendLine("CREATE EXTENSION IF NOT EXISTS postgres_fdw;")
        sb.appendLine()
        sb.appendLine("-- 2. 외부 서버 생성")
        sb.appendLine("CREATE SERVER \"$name\"")
        sb.appendLine("    FOREIGN DATA WRAPPER postgres_fdw")
        sb.appendLine("    OPTIONS (host 'hostname', port '5432', dbname 'dbname');")
        sb.appendLine()
        sb.appendLine("-- 3. 사용자 매핑")
        username?.let {
            sb.appendLine("CREATE USER MAPPING FOR CURRENT_USER")
            sb.appendLine("    SERVER \"$name\"")
            sb.appendLine("    OPTIONS (user '$it', password '****');")
        }
        sb.appendLine()
        sb.appendLine("-- 4. 외부 테이블 생성 (필요에 따라)")
        sb.append("-- CREATE FOREIGN TABLE remote_table (...) SERVER \"$name\" OPTIONS (table_name 'original_table');")

        return sb.toString()
    }

    /**
     * MySQL Federated Engine
     */
    fun toMySql(warnings: MutableList<ConversionWarning>): String {
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "MySQL은 Database Link 대신 FEDERATED 스토리지 엔진을 사용합니다.",
            severity = WarningSeverity.INFO,
            suggestion = "FEDERATED 엔진이 활성화되어 있어야 합니다."
        ))

        val sb = StringBuilder()
        sb.appendLine("-- MySQL FEDERATED 테이블 예시")
        sb.appendLine("-- FEDERATED 엔진을 활성화해야 합니다: SET GLOBAL federated=1")
        sb.appendLine()
        sb.appendLine("-- 원격 테이블에 대한 FEDERATED 테이블 생성:")
        sb.appendLine("-- CREATE TABLE `remote_table` (")
        sb.appendLine("--     ... 컬럼 정의 ...")
        sb.appendLine("-- ) ENGINE=FEDERATED")
        sb.append("-- CONNECTION='mysql://${username ?: "user"}:****@hostname:3306/dbname/table_name';")

        return sb.toString()
    }
}
