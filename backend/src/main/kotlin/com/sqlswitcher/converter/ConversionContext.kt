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
