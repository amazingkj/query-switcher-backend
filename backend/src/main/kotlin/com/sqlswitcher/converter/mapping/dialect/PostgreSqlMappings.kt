package com.sqlswitcher.converter.mapping.dialect

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.mapping.FunctionMappingRule
import com.sqlswitcher.converter.mapping.ParameterTransform

/**
 * PostgreSQL → 다른 방언 함수 매핑 규칙
 */
object PostgreSqlMappings {

    /**
     * PostgreSQL → Oracle
     */
    fun getToOracleMappings(): List<FunctionMappingRule> = listOf(
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "COALESCE", "NVL"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "TO_TIMESTAMP", "TO_DATE"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "STRING_AGG", "LISTAGG")
    )

    /**
     * PostgreSQL → MySQL
     */
    fun getToMySqlMappings(): List<FunctionMappingRule> = listOf(
        // NULL 처리
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "COALESCE", "COALESCE"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "NULLIF", "NULLIF"),

        // 날짜/시간 함수
        FunctionMappingRule(
            DialectType.POSTGRESQL, DialectType.MYSQL, "TO_CHAR", "DATE_FORMAT",
            parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
        ),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "TO_TIMESTAMP", "STR_TO_DATE"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "NOW", "NOW"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "CURRENT_DATE", "CURDATE"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "CURRENT_TIME", "CURTIME"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "CURRENT_TIMESTAMP", "NOW"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "DATE_TRUNC", "/* DATE_FORMAT based */"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "AGE", "TIMESTAMPDIFF"),

        // 문자열 함수
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "STRING_AGG", "GROUP_CONCAT"),
        FunctionMappingRule(
            DialectType.POSTGRESQL, DialectType.MYSQL, "POSITION", "LOCATE",
            parameterTransform = ParameterTransform.SWAP_FIRST_TWO
        ),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "SUBSTRING", "SUBSTRING"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "CONCAT", "CONCAT"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "CONCAT_WS", "CONCAT_WS"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "LENGTH", "LENGTH"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "CHAR_LENGTH", "CHAR_LENGTH"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "REPLACE", "REPLACE"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "TRIM", "TRIM"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "LTRIM", "LTRIM"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "RTRIM", "RTRIM"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "UPPER", "UPPER"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "LOWER", "LOWER"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "INITCAP", "/* no equivalent */"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "LPAD", "LPAD"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "RPAD", "RPAD"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "REVERSE", "REVERSE"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "LEFT", "LEFT"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "RIGHT", "RIGHT"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "REPEAT", "REPEAT"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "ASCII", "ASCII"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "CHR", "CHAR"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "SPLIT_PART", "SUBSTRING_INDEX"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "REGEXP_REPLACE", "REGEXP_REPLACE"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "REGEXP_MATCHES", "REGEXP"),

        // 수학 함수
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "ABS", "ABS"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "CEIL", "CEIL"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "CEILING", "CEILING"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "FLOOR", "FLOOR"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "ROUND", "ROUND"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "TRUNC", "TRUNCATE"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "MOD", "MOD"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "POWER", "POW"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "SQRT", "SQRT"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "EXP", "EXP"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "LN", "LOG"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "LOG", "LOG10"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "RANDOM", "RAND"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "SIGN", "SIGN"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "PI", "PI"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "RADIANS", "RADIANS"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "DEGREES", "DEGREES"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "SIN", "SIN"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "COS", "COS"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "TAN", "TAN"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "ASIN", "ASIN"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "ACOS", "ACOS"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "ATAN", "ATAN"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "ATAN2", "ATAN2"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "COT", "COT"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "GREATEST", "GREATEST"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "LEAST", "LEAST"),

        // JSON 함수
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "JSON_BUILD_ARRAY", "JSON_ARRAY"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "JSON_BUILD_OBJECT", "JSON_OBJECT"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "JSON_AGG", "JSON_ARRAYAGG"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "JSON_OBJECT_AGG", "JSON_OBJECTAGG"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "JSONB_EXTRACT_PATH_TEXT", "JSON_UNQUOTE(JSON_EXTRACT())"),

        // 집계 함수
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "COUNT", "COUNT"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "SUM", "SUM"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "AVG", "AVG"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "MIN", "MIN"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "MAX", "MAX"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "ARRAY_AGG", "GROUP_CONCAT"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "BIT_AND", "BIT_AND"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "BIT_OR", "BIT_OR"),

        // 배열 함수 (PostgreSQL 전용)
        FunctionMappingRule(
            DialectType.POSTGRESQL, DialectType.MYSQL, "ARRAY_LENGTH", "/* no equivalent */",
            warningType = WarningType.UNSUPPORTED_FUNCTION,
            warningMessage = "MySQL은 배열 타입을 지원하지 않습니다"
        ),
        FunctionMappingRule(
            DialectType.POSTGRESQL, DialectType.MYSQL, "UNNEST", "/* no equivalent */",
            warningType = WarningType.UNSUPPORTED_FUNCTION
        ),

        // 암호화/해시 함수
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "MD5", "MD5"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "GEN_RANDOM_UUID", "UUID"),

        // 윈도우 함수
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "ROW_NUMBER", "ROW_NUMBER"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "RANK", "RANK"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "DENSE_RANK", "DENSE_RANK"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "NTILE", "NTILE"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "LAG", "LAG"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "LEAD", "LEAD"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "FIRST_VALUE", "FIRST_VALUE"),
        FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "LAST_VALUE", "LAST_VALUE")
    )
}