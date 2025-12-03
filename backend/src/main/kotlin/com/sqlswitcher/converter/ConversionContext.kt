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
 * 트리거 정보를 담는 데이터 클래스
 */
data class TriggerInfo(
    val name: String,
    val timing: String,             // BEFORE, AFTER
    val events: List<String>,       // INSERT, UPDATE, DELETE
    val tableName: String,
    val forEachRow: Boolean = true,
    val body: String,
    val triggerOrder: String? = null,       // FOLLOWS, PRECEDES (MySQL 5.7+)
    val orderTriggerName: String? = null    // triggerOrder가 참조하는 트리거 이름
) {
    /**
     * Oracle 트리거 헤더 생성
     */
    fun toOracleHeader(schemaOwner: String): String {
        val sb = StringBuilder()
        sb.appendLine("CREATE OR REPLACE TRIGGER \"$schemaOwner\".\"$name\"")
        sb.appendLine("$timing ${events.joinToString(" OR ")}")
        sb.appendLine("ON \"$schemaOwner\".\"$tableName\"")
        if (forEachRow) {
            sb.append("FOR EACH ROW")
        }
        return sb.toString()
    }

    /**
     * PostgreSQL 트리거 헤더 생성
     */
    fun toPostgreSqlHeader(): String {
        val sb = StringBuilder()
        sb.appendLine("CREATE TRIGGER \"$name\"")
        sb.appendLine("$timing ${events.joinToString(" OR ")}")
        sb.appendLine("ON \"$tableName\"")
        if (forEachRow) {
            sb.appendLine("FOR EACH ROW")
        }
        return sb.toString()
    }

    /**
     * MySQL 트리거 헤더 생성
     */
    fun toMySqlHeader(): String {
        val sb = StringBuilder()
        sb.appendLine("CREATE TRIGGER `$name`")
        sb.appendLine("$timing ${events.first()}")  // MySQL은 한 이벤트만 지원
        sb.appendLine("ON `$tableName`")
        if (forEachRow) {
            sb.append("FOR EACH ROW")
        }
        return sb.toString()
    }
}
