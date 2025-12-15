package com.sqlswitcher.converter.mapping.dialect

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.mapping.FunctionMappingRule
import com.sqlswitcher.converter.mapping.ParameterTransform

/**
 * MySQL → 다른 방언 함수 매핑 규칙
 */
object MySqlMappings {

    /**
     * MySQL → Oracle
     */
    fun getToOracleMappings(): List<FunctionMappingRule> = listOf(
        FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "IFNULL", "NVL"),
        FunctionMappingRule(
            DialectType.MYSQL, DialectType.ORACLE, "DATE_FORMAT", "TO_CHAR",
            parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
        ),
        FunctionMappingRule(
            DialectType.MYSQL, DialectType.ORACLE, "STR_TO_DATE", "TO_DATE",
            parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
        ),
        FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "GROUP_CONCAT", "LISTAGG"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "SUBSTRING", "SUBSTR"),
        FunctionMappingRule(
            DialectType.MYSQL, DialectType.ORACLE, "LOCATE", "INSTR",
            parameterTransform = ParameterTransform.SWAP_FIRST_TWO
        ),
        FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "CHAR_LENGTH", "LENGTH"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TRUNCATE", "TRUNC"),
        FunctionMappingRule(
            DialectType.MYSQL, DialectType.ORACLE, "IF", "DECODE",
            warningType = WarningType.SYNTAX_DIFFERENCE,
            warningMessage = "IF 함수는 DECODE 또는 CASE WHEN으로 변환됩니다"
        )
    )

    /**
     * MySQL → PostgreSQL
     */
    fun getToPostgreSqlMappings(): List<FunctionMappingRule> = listOf(
        // NULL 처리
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "IFNULL", "COALESCE"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "NULLIF", "NULLIF"),

        // 날짜/시간 함수
        FunctionMappingRule(
            DialectType.MYSQL, DialectType.POSTGRESQL, "DATE_FORMAT", "TO_CHAR",
            parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
        ),
        FunctionMappingRule(
            DialectType.MYSQL, DialectType.POSTGRESQL, "STR_TO_DATE", "TO_TIMESTAMP",
            parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
        ),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "NOW", "NOW"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "CURDATE", "CURRENT_DATE"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "CURTIME", "CURRENT_TIME"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DATE_ADD", "/* + INTERVAL */"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DATE_SUB", "/* - INTERVAL */"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DATEDIFF", "/* date1 - date2 */"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DAYOFWEEK", "EXTRACT(DOW FROM date) + 1"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DAYOFMONTH", "EXTRACT(DAY FROM date)"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DAYOFYEAR", "EXTRACT(DOY FROM date)"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "WEEK", "EXTRACT(WEEK FROM date)"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "MONTH", "EXTRACT(MONTH FROM date)"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "YEAR", "EXTRACT(YEAR FROM date)"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "HOUR", "EXTRACT(HOUR FROM time)"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "MINUTE", "EXTRACT(MINUTE FROM time)"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "SECOND", "EXTRACT(SECOND FROM time)"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LAST_DAY", "/* DATE_TRUNC('month', date) + INTERVAL '1 month - 1 day' */"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TIMESTAMPDIFF", "/* EXTRACT/AGE combination */"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "UNIX_TIMESTAMP", "EXTRACT(EPOCH FROM timestamp)"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "FROM_UNIXTIME", "TO_TIMESTAMP"),

        // 문자열 함수
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "GROUP_CONCAT", "STRING_AGG"),
        FunctionMappingRule(
            DialectType.MYSQL, DialectType.POSTGRESQL, "LOCATE", "POSITION",
            parameterTransform = ParameterTransform.SWAP_FIRST_TWO
        ),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "SUBSTRING", "SUBSTRING"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "SUBSTR", "SUBSTRING"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "CONCAT", "CONCAT"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "CONCAT_WS", "CONCAT_WS"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LENGTH", "LENGTH"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "CHAR_LENGTH", "CHAR_LENGTH"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "REPLACE", "REPLACE"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TRIM", "TRIM"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LTRIM", "LTRIM"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "RTRIM", "RTRIM"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "UPPER", "UPPER"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LOWER", "LOWER"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LPAD", "LPAD"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "RPAD", "RPAD"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "REVERSE", "REVERSE"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LEFT", "LEFT"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "RIGHT", "RIGHT"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "INSTR", "POSITION"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "REPEAT", "REPEAT"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "SPACE", "REPEAT(' ', n)"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "ASCII", "ASCII"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "CHAR", "CHR"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "ORD", "ASCII"),

        // 수학 함수
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "ABS", "ABS"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "CEIL", "CEIL"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "CEILING", "CEILING"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "FLOOR", "FLOOR"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "ROUND", "ROUND"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TRUNCATE", "TRUNC"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "MOD", "MOD"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "POW", "POWER"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "POWER", "POWER"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "SQRT", "SQRT"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "EXP", "EXP"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LOG", "LN"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LOG10", "LOG"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LOG2", "LOG(2, x)"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "RAND", "RANDOM"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "SIGN", "SIGN"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "PI", "PI"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "RADIANS", "RADIANS"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DEGREES", "DEGREES"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "SIN", "SIN"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "COS", "COS"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TAN", "TAN"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "ASIN", "ASIN"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "ACOS", "ACOS"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "ATAN", "ATAN"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "ATAN2", "ATAN2"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "COT", "COT"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "GREATEST", "GREATEST"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LEAST", "LEAST"),

        // 조건 함수
        FunctionMappingRule(
            DialectType.MYSQL, DialectType.POSTGRESQL, "IF", "CASE_WHEN",
            parameterTransform = ParameterTransform.TO_CASE_WHEN
        ),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "COALESCE", "COALESCE"),

        // JSON 함수
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "JSON_EXTRACT", "json_path ->>"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "JSON_UNQUOTE", "/* no equivalent, use ->> */"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "JSON_ARRAY", "JSON_BUILD_ARRAY"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "JSON_OBJECT", "JSON_BUILD_OBJECT"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "JSON_ARRAYAGG", "JSON_AGG"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "JSON_OBJECTAGG", "JSON_OBJECT_AGG"),

        // 집계 함수
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "COUNT", "COUNT"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "SUM", "SUM"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "AVG", "AVG"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "MIN", "MIN"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "MAX", "MAX"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "BIT_AND", "BIT_AND"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "BIT_OR", "BIT_OR"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "BIT_XOR", "/* no direct equivalent */"),

        // 암호화/해시 함수
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "MD5", "MD5"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "SHA1", "ENCODE(DIGEST(data, 'sha1'), 'hex')"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "SHA2", "ENCODE(DIGEST(data, 'sha256'), 'hex')"),
        FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "UUID", "GEN_RANDOM_UUID")
    )
}