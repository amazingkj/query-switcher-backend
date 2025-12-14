package com.sqlswitcher.converter.mapping.dialect

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.mapping.FunctionMappingRule
import com.sqlswitcher.converter.mapping.ParameterTransform

/**
 * Oracle → MySQL 함수 매핑 규칙
 */
object OracleToMySqlMappings {

    fun getMappings(): List<FunctionMappingRule> = listOf(
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
}