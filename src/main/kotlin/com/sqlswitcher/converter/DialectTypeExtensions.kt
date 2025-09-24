package com.sqlswitcher.converter

/**
 * 호환성 레벨
 */
enum class CompatibilityLevel {
    PERFECT,    // 완벽한 호환성
    HIGH,       // 높은 호환성
    MEDIUM,     // 중간 호환성
    LOW         // 낮은 호환성
}

/**
 * DialectType에 대한 확장 함수들
 */

/**
 * 방언의 표시명 반환
 */
val DialectType.displayName: String
    get() = when (this) {
        DialectType.MYSQL -> "MySQL"
        DialectType.POSTGRESQL -> "PostgreSQL"
        DialectType.ORACLE -> "Oracle"
        DialectType.TIBERO -> "Tibero"
    }

/**
 * 방언의 기본 인용 문자 반환
 */
val DialectType.defaultQuoteCharacter: String
    get() = when (this) {
        DialectType.MYSQL -> "`"
        DialectType.POSTGRESQL -> "\""
        DialectType.ORACLE -> "\""
        DialectType.TIBERO -> "\""
    }

/**
 * 방언이 지원하는 기본 데이터 타입들
 */
val DialectType.supportedDataTypes: Set<String>
    get() = when (this) {
        DialectType.MYSQL -> setOf(
            "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT",
            "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "REAL",
            "CHAR", "VARCHAR", "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT",
            "BINARY", "VARBINARY", "TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB",
            "DATE", "TIME", "DATETIME", "TIMESTAMP", "YEAR",
            "BOOLEAN", "BOOL", "JSON", "ENUM", "SET"
        )
        DialectType.POSTGRESQL -> setOf(
            "SMALLINT", "INTEGER", "BIGINT", "SERIAL", "BIGSERIAL",
            "DECIMAL", "NUMERIC", "REAL", "DOUBLE PRECISION", "MONEY",
            "CHAR", "VARCHAR", "TEXT", "BYTEA",
            "DATE", "TIME", "TIMESTAMP", "TIMESTAMPTZ", "INTERVAL",
            "BOOLEAN", "JSON", "JSONB", "XML", "UUID", "ARRAY"
        )
        DialectType.ORACLE -> setOf(
            "NUMBER", "FLOAT", "BINARY_FLOAT", "BINARY_DOUBLE",
            "CHAR", "VARCHAR2", "NCHAR", "NVARCHAR2", "CLOB", "NCLOB",
            "RAW", "LONG RAW", "BLOB", "BFILE",
            "DATE", "TIMESTAMP", "TIMESTAMP WITH TIME ZONE", "TIMESTAMP WITH LOCAL TIME ZONE",
            "INTERVAL YEAR TO MONTH", "INTERVAL DAY TO SECOND",
            "ROWID", "UROWID", "XMLType"
        )
        DialectType.TIBERO -> setOf(
            "NUMBER", "FLOAT", "BINARY_FLOAT", "BINARY_DOUBLE",
            "CHAR", "VARCHAR2", "NCHAR", "NVARCHAR2", "CLOB", "NCLOB",
            "RAW", "LONG RAW", "BLOB", "BFILE",
            "DATE", "TIMESTAMP", "TIMESTAMP WITH TIME ZONE", "TIMESTAMP WITH LOCAL TIME ZONE",
            "INTERVAL YEAR TO MONTH", "INTERVAL DAY TO SECOND",
            "ROWID", "UROWID", "XMLType"
        )
    }

/**
 * 방언이 지원하는 기본 함수들
 */
val DialectType.supportedFunctions: Set<String>
    get() = when (this) {
        DialectType.MYSQL -> setOf(
            "COUNT", "SUM", "AVG", "MIN", "MAX", "GROUP_CONCAT",
            "CONCAT", "SUBSTRING", "LENGTH", "UPPER", "LOWER", "TRIM",
            "NOW", "CURDATE", "CURTIME", "DATE_FORMAT", "STR_TO_DATE",
            "IFNULL", "COALESCE", "CASE", "IF", "NULLIF",
            "ABS", "ROUND", "CEIL", "FLOOR", "MOD", "POW", "SQRT",
            "CAST", "CONVERT"
        )
        DialectType.POSTGRESQL -> setOf(
            "COUNT", "SUM", "AVG", "MIN", "MAX", "STRING_AGG", "ARRAY_AGG",
            "CONCAT", "SUBSTRING", "LENGTH", "UPPER", "LOWER", "TRIM",
            "NOW", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP",
            "TO_CHAR", "TO_DATE", "TO_TIMESTAMP",
            "COALESCE", "NULLIF", "GREATEST", "LEAST",
            "ABS", "ROUND", "CEIL", "FLOOR", "MOD", "POWER", "SQRT",
            "CAST", "::"
        )
        DialectType.ORACLE -> setOf(
            "COUNT", "SUM", "AVG", "MIN", "MAX", "LISTAGG", "XMLAGG",
            "CONCAT", "SUBSTR", "LENGTH", "UPPER", "LOWER", "TRIM",
            "SYSDATE", "CURRENT_DATE", "CURRENT_TIMESTAMP",
            "TO_CHAR", "TO_DATE", "TO_TIMESTAMP",
            "NVL", "NVL2", "COALESCE", "NULLIF", "GREATEST", "LEAST",
            "ABS", "ROUND", "CEIL", "FLOOR", "MOD", "POWER", "SQRT",
            "CAST", "DECODE"
        )
        DialectType.TIBERO -> setOf(
            "COUNT", "SUM", "AVG", "MIN", "MAX", "LISTAGG", "XMLAGG",
            "CONCAT", "SUBSTR", "LENGTH", "UPPER", "LOWER", "TRIM",
            "SYSDATE", "CURRENT_DATE", "CURRENT_TIMESTAMP",
            "TO_CHAR", "TO_DATE", "TO_TIMESTAMP",
            "NVL", "NVL2", "COALESCE", "NULLIF", "GREATEST", "LEAST",
            "ABS", "ROUND", "CEIL", "FLOOR", "MOD", "POWER", "SQRT",
            "CAST", "DECODE"
        )
    }

/**
 * 방언 간 호환성 매트릭스
 */
val DialectType.compatibilityMatrix: Map<DialectType, CompatibilityLevel>
    get() = when (this) {
        DialectType.MYSQL -> mapOf(
            DialectType.MYSQL to CompatibilityLevel.PERFECT,
            DialectType.POSTGRESQL to CompatibilityLevel.HIGH,
            DialectType.ORACLE to CompatibilityLevel.MEDIUM,
            DialectType.TIBERO to CompatibilityLevel.MEDIUM
        )
        DialectType.POSTGRESQL -> mapOf(
            DialectType.MYSQL to CompatibilityLevel.HIGH,
            DialectType.POSTGRESQL to CompatibilityLevel.PERFECT,
            DialectType.ORACLE to CompatibilityLevel.MEDIUM,
            DialectType.TIBERO to CompatibilityLevel.MEDIUM
        )
        DialectType.ORACLE -> mapOf(
            DialectType.MYSQL to CompatibilityLevel.MEDIUM,
            DialectType.POSTGRESQL to CompatibilityLevel.MEDIUM,
            DialectType.ORACLE to CompatibilityLevel.PERFECT,
            DialectType.TIBERO to CompatibilityLevel.HIGH
        )
        DialectType.TIBERO -> mapOf(
            DialectType.MYSQL to CompatibilityLevel.MEDIUM,
            DialectType.POSTGRESQL to CompatibilityLevel.MEDIUM,
            DialectType.ORACLE to CompatibilityLevel.HIGH,
            DialectType.TIBERO to CompatibilityLevel.PERFECT
        )
    }


/**
 * 특정 방언과의 호환성 확인
 */
fun DialectType.getCompatibilityWith(target: DialectType): CompatibilityLevel {
    return compatibilityMatrix[target] ?: CompatibilityLevel.LOW
}

/**
 * 방언이 특정 기능을 지원하는지 확인
 */
fun DialectType.supportsFeature(feature: DatabaseFeature): Boolean {
    return when (this) {
        DialectType.MYSQL -> when (feature) {
            DatabaseFeature.CTE -> true
            DatabaseFeature.WINDOW_FUNCTIONS -> true
            DatabaseFeature.JSON_FUNCTIONS -> true
            DatabaseFeature.ARRAY_FUNCTIONS -> false
            DatabaseFeature.RECURSIVE_QUERIES -> true
            DatabaseFeature.PIVOT -> false
            DatabaseFeature.LATERAL_JOIN -> false
        }
        DialectType.POSTGRESQL -> when (feature) {
            DatabaseFeature.CTE -> true
            DatabaseFeature.WINDOW_FUNCTIONS -> true
            DatabaseFeature.JSON_FUNCTIONS -> true
            DatabaseFeature.ARRAY_FUNCTIONS -> true
            DatabaseFeature.RECURSIVE_QUERIES -> true
            DatabaseFeature.PIVOT -> false
            DatabaseFeature.LATERAL_JOIN -> true
        }
        DialectType.ORACLE -> when (feature) {
            DatabaseFeature.CTE -> true
            DatabaseFeature.WINDOW_FUNCTIONS -> true
            DatabaseFeature.JSON_FUNCTIONS -> true
            DatabaseFeature.ARRAY_FUNCTIONS -> false
            DatabaseFeature.RECURSIVE_QUERIES -> true
            DatabaseFeature.PIVOT -> true
            DatabaseFeature.LATERAL_JOIN -> false
        }
        DialectType.TIBERO -> when (feature) {
            DatabaseFeature.CTE -> true
            DatabaseFeature.WINDOW_FUNCTIONS -> true
            DatabaseFeature.JSON_FUNCTIONS -> true
            DatabaseFeature.ARRAY_FUNCTIONS -> false
            DatabaseFeature.RECURSIVE_QUERIES -> true
            DatabaseFeature.PIVOT -> true
            DatabaseFeature.LATERAL_JOIN -> false
        }
    }
}

/**
 * 데이터베이스 기능 열거형
 */
enum class DatabaseFeature {
    CTE,                    // Common Table Expressions
    WINDOW_FUNCTIONS,       // Window Functions
    JSON_FUNCTIONS,         // JSON Functions
    ARRAY_FUNCTIONS,        // Array Functions
    RECURSIVE_QUERIES,      // Recursive Queries
    PIVOT,                  // PIVOT/UNPIVOT
    LATERAL_JOIN           // LATERAL JOIN
}
