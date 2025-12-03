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
