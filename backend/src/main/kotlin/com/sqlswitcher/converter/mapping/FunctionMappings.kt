package com.sqlswitcher.converter.mapping

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningType
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

/**
 * 파라미터 변환 방식
 */
enum class ParameterTransform {
    NONE,                    // 변환 없음
    SWAP_FIRST_TWO,         // 첫 두 파라미터 교환
    DATE_FORMAT_CONVERT,    // 날짜 포맷 변환
    TO_CASE_WHEN,           // CASE WHEN으로 변환
    WRAP_WITH_FUNCTION      // 다른 함수로 감싸기
}

/**
 * 함수 매핑 규칙
 */
data class FunctionMappingRule(
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val sourceFunction: String,
    val targetFunction: String,
    val parameterTransform: ParameterTransform = ParameterTransform.NONE,
    val warningType: WarningType? = null,
    val warningMessage: String? = null,
    val suggestion: String? = null
)

/**
 * 함수 매핑 레지스트리 - 모든 방언 간 함수 변환 규칙 중앙 관리
 */
@Component
class FunctionMappingRegistry {

    private val mappings = mutableMapOf<String, FunctionMappingRule>()

    @PostConstruct
    fun initialize() {
        registerOracleToMySqlMappings()
        registerOracleToPostgreSqlMappings()
        registerMySqlToOracleMappings()
        registerMySqlToPostgreSqlMappings()
        registerPostgreSqlToOracleMappings()
        registerPostgreSqlToMySqlMappings()
    }

    private fun registerOracleToMySqlMappings() {
        val rules = listOf(
            // 기본 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "NVL", "IFNULL"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "NVL2", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN,
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "NVL2는 CASE WHEN으로 변환됩니다"
            ),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "DECODE", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN,
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "DECODE는 CASE WHEN으로 변환됩니다"
            ),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "TO_CHAR", "DATE_FORMAT",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
            ),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "TO_DATE", "STR_TO_DATE",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
            ),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LISTAGG", "GROUP_CONCAT"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "SUBSTR", "SUBSTRING"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "INSTR", "LOCATE",
                parameterTransform = ParameterTransform.SWAP_FIRST_TWO
            ),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LENGTH", "CHAR_LENGTH"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TRUNC", "TRUNCATE"),

            // 날짜/시간 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "SYSDATE", "NOW()"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "SYSTIMESTAMP", "NOW(6)"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "ADD_MONTHS", "DATE_ADD"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "MONTHS_BETWEEN", "TIMESTAMPDIFF"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LAST_DAY", "LAST_DAY"),

            // 수학 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "MOD", "MOD"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "POWER", "POW"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LOG", "LOG"),

            // 문자열 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "CONCAT", "CONCAT"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "REPLACE", "REPLACE"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TRIM", "TRIM"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LTRIM", "LTRIM"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "RTRIM", "RTRIM"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "UPPER", "UPPER"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LOWER", "LOWER"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "INITCAP", "-- INITCAP"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LPAD", "LPAD"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "RPAD", "RPAD"),

            // DBMS_RANDOM 패키지
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "DBMS_RANDOM.VALUE", "RAND()",
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "DBMS_RANDOM.VALUE는 RAND()로 변환됩니다. 범위 지정은 수동 조정 필요"
            ),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "DBMS_RANDOM.STRING", "-- DBMS_RANDOM.STRING",
                warningType = WarningType.UNSUPPORTED_FUNCTION,
                warningMessage = "DBMS_RANDOM.STRING은 MySQL에서 직접 지원하지 않습니다",
                suggestion = "사용자 정의 함수로 대체하세요"
            ),

            // DBMS_LOB 패키지
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "DBMS_LOB.GETLENGTH", "LENGTH",
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "DBMS_LOB.GETLENGTH는 LENGTH로 변환됩니다"
            ),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "DBMS_LOB.SUBSTR", "SUBSTRING",
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "DBMS_LOB.SUBSTR는 SUBSTRING으로 변환됩니다"
            ),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "DBMS_LOB.INSTR", "LOCATE",
                parameterTransform = ParameterTransform.SWAP_FIRST_TWO
            ),

            // DBMS_UTILITY 패키지
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "DBMS_UTILITY.GET_TIME", "UNIX_TIMESTAMP() * 100",
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "DBMS_UTILITY.GET_TIME은 근사값으로 변환됩니다"
            ),

            // REGEXP 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "REGEXP_LIKE", "REGEXP"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "REGEXP_SUBSTR", "REGEXP_SUBSTR"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "REGEXP_REPLACE", "REGEXP_REPLACE"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "REGEXP_INSTR", "-- REGEXP_INSTR"),

            // 변환 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TO_NUMBER", "CAST"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TO_CLOB", "-- TO_CLOB"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TO_BLOB", "-- TO_BLOB"),

            // 집계 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "WM_CONCAT", "GROUP_CONCAT"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "XMLAGG", "GROUP_CONCAT")
        )
        rules.forEach { register(it) }
    }

    private fun registerOracleToPostgreSqlMappings() {
        val rules = listOf(
            // 기본 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "NVL", "COALESCE"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.POSTGRESQL, "NVL2", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN
            ),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.POSTGRESQL, "DECODE", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN
            ),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TO_CHAR", "TO_CHAR"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.POSTGRESQL, "TO_DATE", "TO_TIMESTAMP",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
            ),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "LISTAGG", "STRING_AGG"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.POSTGRESQL, "INSTR", "POSITION",
                parameterTransform = ParameterTransform.SWAP_FIRST_TWO
            ),

            // 날짜/시간 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "SYSDATE", "CURRENT_TIMESTAMP"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "SYSTIMESTAMP", "CURRENT_TIMESTAMP"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "ADD_MONTHS", "/* date + interval 'n months' */"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "LAST_DAY", "DATE_TRUNC('month', date) + INTERVAL '1 month - 1 day'"),

            // 문자열 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "SUBSTR", "SUBSTRING"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "LENGTH", "LENGTH"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "CONCAT", "CONCAT"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "REPLACE", "REPLACE"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TRIM", "TRIM"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "UPPER", "UPPER"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "LOWER", "LOWER"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "INITCAP", "INITCAP"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "LPAD", "LPAD"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "RPAD", "RPAD"),

            // 수학 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TRUNC", "TRUNC"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "MOD", "MOD"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "POWER", "POWER"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "LOG", "LOG"),

            // DBMS_RANDOM 패키지
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.POSTGRESQL, "DBMS_RANDOM.VALUE", "RANDOM()",
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "DBMS_RANDOM.VALUE는 RANDOM()으로 변환됩니다"
            ),

            // DBMS_LOB 패키지
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "DBMS_LOB.GETLENGTH", "LENGTH"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "DBMS_LOB.SUBSTR", "SUBSTRING"),

            // REGEXP 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "REGEXP_LIKE", "~ (regex operator)"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "REGEXP_SUBSTR", "SUBSTRING"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "REGEXP_REPLACE", "REGEXP_REPLACE"),

            // 변환 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TO_NUMBER", "CAST"),

            // 집계 함수
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "WM_CONCAT", "STRING_AGG"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "XMLAGG", "STRING_AGG")
        )
        rules.forEach { register(it) }
    }

    private fun registerMySqlToOracleMappings() {
        val rules = listOf(
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
        rules.forEach { register(it) }
    }

    private fun registerMySqlToPostgreSqlMappings() {
        val rules = listOf(
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
        rules.forEach { register(it) }
    }

    private fun registerPostgreSqlToOracleMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "COALESCE", "NVL"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "TO_TIMESTAMP", "TO_DATE"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "STRING_AGG", "LISTAGG")
        )
        rules.forEach { register(it) }
    }

    private fun registerPostgreSqlToMySqlMappings() {
        val rules = listOf(
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
        rules.forEach { register(it) }
    }

    private fun register(rule: FunctionMappingRule) {
        val key = "${rule.sourceDialect}_${rule.targetDialect}_${rule.sourceFunction.uppercase()}"
        mappings[key] = rule
    }

    fun getMapping(source: DialectType, target: DialectType, functionName: String): FunctionMappingRule? {
        val key = "${source}_${target}_${functionName.uppercase()}"
        return mappings[key]
    }

    fun getMappingsForDialects(source: DialectType, target: DialectType): List<FunctionMappingRule> {
        return mappings.values.filter { it.sourceDialect == source && it.targetDialect == target }
    }

    fun getAllMappings(): Map<String, FunctionMappingRule> = mappings.toMap()
}