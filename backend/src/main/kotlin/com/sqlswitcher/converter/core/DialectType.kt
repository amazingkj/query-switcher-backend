package com.sqlswitcher.converter.core

/**
 * 지원하는 SQL 방언 타입
 */
enum class DialectType(
    val displayName: String,
    val supportedFunctions: Set<String>
) {
    ORACLE(
        displayName = "Oracle",
        supportedFunctions = setOf(
            "NVL", "NVL2", "DECODE", "TO_CHAR", "TO_DATE", "TO_NUMBER",
            "SYSDATE", "SYSTIMESTAMP", "TRUNC", "SUBSTR", "INSTR",
            "LISTAGG", "CONNECT_BY_ROOT", "SYS_CONNECT_BY_PATH",
            "ROWNUM", "ROWID", "LEVEL", "LPAD", "RPAD",
            "REGEXP_LIKE", "REGEXP_SUBSTR", "REGEXP_REPLACE", "REGEXP_INSTR",
            "JSON_VALUE", "JSON_QUERY", "JSON_EXISTS", "JSON_TABLE"
        )
    ),
    MYSQL(
        displayName = "MySQL",
        supportedFunctions = setOf(
            "IFNULL", "COALESCE", "IF", "DATE_FORMAT", "STR_TO_DATE",
            "NOW", "CURRENT_TIMESTAMP", "SUBSTRING", "LOCATE",
            "GROUP_CONCAT", "LIMIT", "AUTO_INCREMENT",
            "REGEXP", "REGEXP_LIKE", "REGEXP_SUBSTR", "REGEXP_REPLACE",
            "JSON_EXTRACT", "JSON_UNQUOTE", "JSON_CONTAINS", "JSON_CONTAINS_PATH"
        )
    ),
    POSTGRESQL(
        displayName = "PostgreSQL",
        supportedFunctions = setOf(
            "COALESCE", "NULLIF", "TO_CHAR", "TO_TIMESTAMP",
            "CURRENT_TIMESTAMP", "NOW", "SUBSTRING", "POSITION",
            "STRING_AGG", "ARRAY_AGG", "LIMIT", "OFFSET", "SERIAL",
            "SIMILAR TO", "REGEXP_MATCHES", "REGEXP_REPLACE",
            "JSONB", "JSON_BUILD_OBJECT", "JSON_AGG"
        )
    ),
    TIBERO(
        displayName = "Tibero",
        supportedFunctions = setOf(
            "NVL", "NVL2", "DECODE", "TO_CHAR", "TO_DATE", "TO_NUMBER",
            "SYSDATE", "SYSTIMESTAMP", "TRUNC", "SUBSTR", "INSTR",
            "LISTAGG", "CONNECT_BY_ROOT", "SYS_CONNECT_BY_PATH",
            "ROWNUM", "ROWID", "LEVEL", "LPAD", "RPAD",
            "REGEXP_LIKE", "REGEXP_SUBSTR", "REGEXP_REPLACE"
        )
    );

    companion object {
        fun fromString(value: String): DialectType? {
            return entries.find {
                it.name.equals(value, ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true)
            }
        }
    }
}

/**
 * 방언 간 변환 가능 여부 확인
 */
fun DialectType.canConvertTo(target: DialectType): Boolean = true

/**
 * 방언의 인용 문자 반환
 */
fun DialectType.getQuoteCharacter(): String = when (this) {
    DialectType.MYSQL -> "`"
    DialectType.ORACLE, DialectType.POSTGRESQL, DialectType.TIBERO -> "\""
}