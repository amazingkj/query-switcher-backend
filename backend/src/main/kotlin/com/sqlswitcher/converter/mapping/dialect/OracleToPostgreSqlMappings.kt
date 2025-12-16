package com.sqlswitcher.converter.mapping.dialect

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.mapping.FunctionMappingRule
import com.sqlswitcher.converter.mapping.ParameterTransform

/**
 * Oracle → PostgreSQL 함수 매핑 규칙
 */
object OracleToPostgreSqlMappings {

    fun getMappings(): List<FunctionMappingRule> = listOf(
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
}