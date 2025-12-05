package com.sqlswitcher.converter.mapping

import com.sqlswitcher.converter.core.*
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

/**
 * 데이터 타입 매핑 규칙
 */
data class DataTypeMappingRule(
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val sourceType: String,
    val targetType: String,
    val precisionHandler: PrecisionHandler = PrecisionHandler.PRESERVE,
    val warningType: WarningType? = null,
    val warningMessage: String? = null
)

/**
 * 정밀도 처리 방식
 */
enum class PrecisionHandler {
    PRESERVE,           // 정밀도 유지
    CONVERT,            // 정밀도 변환
    DROP,               // 정밀도 제거
    MAP_TO_INTEGER      // 정수형으로 매핑
}

/**
 * 데이터 타입 변환 결과
 */
data class DataTypeConversionResult(
    val convertedType: String,
    val warnings: List<ConversionWarning> = emptyList(),
    val appliedRule: String? = null
)

/**
 * 데이터 타입 매핑 레지스트리 - 모든 방언 간 데이터 타입 변환 규칙 중앙 관리
 */
@Component
class DataTypeMappingRegistry {

    private val mappings = mutableMapOf<String, DataTypeMappingRule>()

    @PostConstruct
    fun initialize() {
        registerOracleToMySqlMappings()
        registerOracleToPostgreSqlMappings()
        registerMySqlToOracleMappings()
        registerMySqlToPostgreSqlMappings()
        registerPostgreSqlToOracleMappings()
        registerPostgreSqlToMySqlMappings()
        registerTiberoMappings()
    }

    private fun registerOracleToMySqlMappings() {
        val rules = listOf(
            // 숫자형
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "NUMBER", "DECIMAL", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "BINARY_FLOAT", "FLOAT"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "BINARY_DOUBLE", "DOUBLE"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "INTEGER", "INT"),

            // 문자형
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "VARCHAR2", "VARCHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "NVARCHAR2", "VARCHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "CHAR", "CHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "NCHAR", "CHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "CLOB", "LONGTEXT"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "NCLOB", "LONGTEXT"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LONG", "LONGTEXT"),

            // 바이너리
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "BLOB", "LONGBLOB"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "RAW", "VARBINARY", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LONG RAW", "LONGBLOB"),

            // 날짜/시간
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "DATE", "DATETIME"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TIMESTAMP", "DATETIME", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TIMESTAMP WITH TIME ZONE", "DATETIME"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TIMESTAMP WITH LOCAL TIME ZONE", "DATETIME"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "INTERVAL YEAR TO MONTH", "VARCHAR(20)"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "INTERVAL DAY TO SECOND", "VARCHAR(30)"),

            // 기타
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "ROWID", "VARCHAR(18)"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "UROWID", "VARCHAR(4000)"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "XMLTYPE", "LONGTEXT"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "BFILE", "VARCHAR(255)",
                warningType = WarningType.UNSUPPORTED_FUNCTION,
                warningMessage = "BFILE은 MySQL에서 직접 지원되지 않습니다")
        )
        rules.forEach { register(it) }
    }

    private fun registerOracleToPostgreSqlMappings() {
        val rules = listOf(
            // 숫자형
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "NUMBER", "NUMERIC", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "BINARY_FLOAT", "REAL"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "BINARY_DOUBLE", "DOUBLE PRECISION"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "INTEGER", "INTEGER"),

            // 문자형
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "VARCHAR2", "VARCHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "NVARCHAR2", "VARCHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "CHAR", "CHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "NCHAR", "CHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "CLOB", "TEXT"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "NCLOB", "TEXT"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "LONG", "TEXT"),

            // 바이너리
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "BLOB", "BYTEA"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "RAW", "BYTEA"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "LONG RAW", "BYTEA"),

            // 날짜/시간
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "DATE", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TIMESTAMP", "TIMESTAMP", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TIMESTAMP WITH TIME ZONE", "TIMESTAMPTZ"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TIMESTAMP WITH LOCAL TIME ZONE", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "INTERVAL YEAR TO MONTH", "INTERVAL"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "INTERVAL DAY TO SECOND", "INTERVAL"),

            // 기타
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "ROWID", "VARCHAR(18)"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "UROWID", "VARCHAR(4000)"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "XMLTYPE", "XML"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "BFILE", "VARCHAR(255)")
        )
        rules.forEach { register(it) }
    }

    private fun registerMySqlToOracleMappings() {
        val rules = listOf(
            // 숫자형
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TINYINT", "NUMBER(3)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "SMALLINT", "NUMBER(5)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "MEDIUMINT", "NUMBER(7)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "INT", "NUMBER(10)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "INTEGER", "NUMBER(10)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "BIGINT", "NUMBER(19)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "DECIMAL", "NUMBER", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "FLOAT", "BINARY_FLOAT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "DOUBLE", "BINARY_DOUBLE"),

            // 문자형
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "VARCHAR", "VARCHAR2", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "CHAR", "CHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TEXT", "CLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TINYTEXT", "VARCHAR2(255)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "MEDIUMTEXT", "CLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "LONGTEXT", "CLOB"),

            // 바이너리
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "BLOB", "BLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TINYBLOB", "RAW(255)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "MEDIUMBLOB", "BLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "LONGBLOB", "BLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "VARBINARY", "RAW", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "BINARY", "RAW", PrecisionHandler.PRESERVE),

            // 날짜/시간
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "DATE", "DATE"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "DATETIME", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TIMESTAMP", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TIME", "VARCHAR2(15)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "YEAR", "NUMBER(4)"),

            // 기타
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "BOOLEAN", "NUMBER(1)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "BIT", "NUMBER(1)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "ENUM", "VARCHAR2(255)",
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "ENUM은 Oracle에서 CHECK 제약조건으로 대체됩니다"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "SET", "VARCHAR2(255)",
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "SET은 Oracle에서 직접 지원되지 않습니다"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "JSON", "CLOB")
        )
        rules.forEach { register(it) }
    }

    private fun registerMySqlToPostgreSqlMappings() {
        val rules = listOf(
            // 숫자형
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TINYINT", "SMALLINT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "SMALLINT", "SMALLINT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "MEDIUMINT", "INTEGER"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "INT", "INTEGER"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "INTEGER", "INTEGER"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "BIGINT", "BIGINT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DECIMAL", "NUMERIC", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "FLOAT", "REAL"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DOUBLE", "DOUBLE PRECISION"),

            // 문자형
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "VARCHAR", "VARCHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "CHAR", "CHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TEXT", "TEXT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TINYTEXT", "TEXT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "MEDIUMTEXT", "TEXT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LONGTEXT", "TEXT"),

            // 바이너리
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "BLOB", "BYTEA"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TINYBLOB", "BYTEA"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "MEDIUMBLOB", "BYTEA"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LONGBLOB", "BYTEA"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "VARBINARY", "BYTEA"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "BINARY", "BYTEA"),

            // 날짜/시간
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DATE", "DATE"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DATETIME", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TIMESTAMP", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TIME", "TIME"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "YEAR", "INTEGER"),

            // 기타
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "BOOLEAN", "BOOLEAN"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "BIT", "BIT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "ENUM", "VARCHAR(255)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "SET", "VARCHAR(255)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "JSON", "JSONB")
        )
        rules.forEach { register(it) }
    }

    private fun registerPostgreSqlToOracleMappings() {
        val rules = listOf(
            // 숫자형
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "SMALLINT", "NUMBER(5)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "INTEGER", "NUMBER(10)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "BIGINT", "NUMBER(19)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "NUMERIC", "NUMBER", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "REAL", "BINARY_FLOAT"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "DOUBLE PRECISION", "BINARY_DOUBLE"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "SERIAL", "NUMBER(10)",
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "SERIAL은 시퀀스 + 트리거로 변환됩니다"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "BIGSERIAL", "NUMBER(19)"),

            // 문자형
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "VARCHAR", "VARCHAR2", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "CHAR", "CHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "TEXT", "CLOB"),

            // 바이너리
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "BYTEA", "BLOB"),

            // 날짜/시간
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "DATE", "DATE"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "TIMESTAMP", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "TIME", "VARCHAR2(15)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "INTERVAL", "INTERVAL DAY TO SECOND"),

            // 기타
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "BOOLEAN", "NUMBER(1)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "UUID", "RAW(16)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "JSON", "CLOB"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "JSONB", "CLOB"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "XML", "XMLTYPE"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "ARRAY", "VARCHAR2(4000)",
                warningType = WarningType.UNSUPPORTED_FUNCTION,
                warningMessage = "PostgreSQL ARRAY는 Oracle에서 직접 지원되지 않습니다")
        )
        rules.forEach { register(it) }
    }

    private fun registerPostgreSqlToMySqlMappings() {
        val rules = listOf(
            // 숫자형
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "SMALLINT", "SMALLINT"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "INTEGER", "INT"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "BIGINT", "BIGINT"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "NUMERIC", "DECIMAL", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "REAL", "FLOAT"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "DOUBLE PRECISION", "DOUBLE"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "SERIAL", "INT AUTO_INCREMENT"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "BIGSERIAL", "BIGINT AUTO_INCREMENT"),

            // 문자형
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "VARCHAR", "VARCHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "CHAR", "CHAR", PrecisionHandler.PRESERVE),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "TEXT", "LONGTEXT"),

            // 바이너리
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "BYTEA", "LONGBLOB"),

            // 날짜/시간
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "DATE", "DATE"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "TIMESTAMP", "DATETIME"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "TIMESTAMPTZ", "DATETIME"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "TIME", "TIME"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "INTERVAL", "VARCHAR(50)"),

            // 기타
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "BOOLEAN", "TINYINT(1)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "UUID", "CHAR(36)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "JSON", "JSON"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "JSONB", "JSON"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "XML", "LONGTEXT"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "ARRAY", "JSON")
        )
        rules.forEach { register(it) }
    }

    private fun registerTiberoMappings() {
        // Tibero ↔ MySQL (Oracle과 동일)
        getMappingsForDialects(DialectType.ORACLE, DialectType.MYSQL).forEach { rule ->
            register(rule.copy(sourceDialect = DialectType.TIBERO))
        }
        getMappingsForDialects(DialectType.MYSQL, DialectType.ORACLE).forEach { rule ->
            register(rule.copy(targetDialect = DialectType.TIBERO))
        }

        // Tibero ↔ PostgreSQL (Oracle과 동일)
        getMappingsForDialects(DialectType.ORACLE, DialectType.POSTGRESQL).forEach { rule ->
            register(rule.copy(sourceDialect = DialectType.TIBERO))
        }
        getMappingsForDialects(DialectType.POSTGRESQL, DialectType.ORACLE).forEach { rule ->
            register(rule.copy(targetDialect = DialectType.TIBERO))
        }
    }

    private fun register(rule: DataTypeMappingRule) {
        val key = "${rule.sourceDialect}_${rule.targetDialect}_${rule.sourceType.uppercase()}"
        mappings[key] = rule
    }

    fun getMapping(source: DialectType, target: DialectType, dataType: String): DataTypeMappingRule? {
        // 정밀도 제거된 타입으로 검색
        val normalizedType = normalizeTypeName(dataType)
        val key = "${source}_${target}_${normalizedType.uppercase()}"
        return mappings[key]
    }

    fun getMappingsForDialects(source: DialectType, target: DialectType): List<DataTypeMappingRule> {
        return mappings.values.filter { it.sourceDialect == source && it.targetDialect == target }
    }

    private fun normalizeTypeName(dataType: String): String {
        // VARCHAR(100) → VARCHAR, NUMBER(10,2) → NUMBER 등
        return dataType.replace(Regex("\\([^)]*\\)"), "").trim().uppercase()
    }
}