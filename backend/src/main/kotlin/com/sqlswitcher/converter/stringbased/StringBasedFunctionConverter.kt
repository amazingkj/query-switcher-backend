package com.sqlswitcher.converter.stringbased

import com.sqlswitcher.converter.DialectType
import org.springframework.stereotype.Component

/**
 * 문자열 기반 함수 변환기
 *
 * AST 파싱이 불가능한 경우 정규식을 사용하여 함수를 변환합니다.
 */
@Component
class StringBasedFunctionConverter {

    private val converters: Map<DialectPair, List<FunctionReplacement>> = mapOf(
        // Oracle → MySQL
        DialectPair(DialectType.ORACLE, DialectType.MYSQL) to listOf(
            FunctionReplacement("\\bSYSDATE\\b", "NOW()"),
            FunctionReplacement("\\bNVL\\s*\\(", "IFNULL("),
            FunctionReplacement("\\bSUBSTR\\s*\\(", "SUBSTRING("),
            FunctionReplacement("\\bNVL2\\s*\\(", "IF("),
            FunctionReplacement("\\bDECODE\\s*\\(", "CASE ")
        ),

        // Oracle → PostgreSQL
        DialectPair(DialectType.ORACLE, DialectType.POSTGRESQL) to listOf(
            FunctionReplacement("\\bSYSDATE\\b", "CURRENT_TIMESTAMP"),
            FunctionReplacement("\\bNVL\\s*\\(", "COALESCE(")
        ),

        // MySQL → PostgreSQL
        DialectPair(DialectType.MYSQL, DialectType.POSTGRESQL) to listOf(
            // 날짜/시간 함수
            FunctionReplacement("\\bNOW\\s*\\(\\s*\\)", "CURRENT_TIMESTAMP"),
            FunctionReplacement("\\bCURDATE\\s*\\(\\s*\\)", "CURRENT_DATE"),
            FunctionReplacement("\\bCURTIME\\s*\\(\\s*\\)", "CURRENT_TIME"),
            FunctionReplacement("\\bUNIX_TIMESTAMP\\s*\\(\\s*\\)", "EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::INTEGER"),
            FunctionReplacement("\\bFROM_UNIXTIME\\s*\\(", "TO_TIMESTAMP("),
            FunctionReplacement("\\bDATE_FORMAT\\s*\\(", "TO_CHAR("),
            FunctionReplacement("\\bSTR_TO_DATE\\s*\\(", "TO_DATE("),
            // 문자열 함수
            FunctionReplacement("\\bIFNULL\\s*\\(", "COALESCE("),
            FunctionReplacement("\\bIF\\s*\\(", "CASE WHEN "),
            FunctionReplacement("\\bGROUP_CONCAT\\s*\\(", "STRING_AGG("),
            FunctionReplacement("\\bLOCATE\\s*\\(", "POSITION("),
            FunctionReplacement("\\bINSTR\\s*\\(", "POSITION("),
            // 수학 함수
            FunctionReplacement("\\bRAND\\s*\\(\\s*\\)", "RANDOM()"),
            FunctionReplacement("\\bTRUNCATE\\s*\\(", "TRUNC("),
            // 기타
            FunctionReplacement("\\bLAST_INSERT_ID\\s*\\(\\s*\\)", "LASTVAL()")
        ),

        // MySQL → Oracle
        DialectPair(DialectType.MYSQL, DialectType.ORACLE) to listOf(
            FunctionReplacement("\\bNOW\\s*\\(\\s*\\)", "SYSDATE"),
            FunctionReplacement("\\bIFNULL\\s*\\(", "NVL("),
            FunctionReplacement("\\bCOALESCE\\s*\\(", "NVL("),
            FunctionReplacement("\\bSUBSTRING\\s*\\(", "SUBSTR(")
        ),

        // PostgreSQL → MySQL
        DialectPair(DialectType.POSTGRESQL, DialectType.MYSQL) to listOf(
            // 날짜/시간 함수
            FunctionReplacement("\\bCURRENT_TIMESTAMP\\b", "NOW()"),
            FunctionReplacement("\\bCURRENT_DATE\\b", "CURDATE()"),
            FunctionReplacement("\\bCURRENT_TIME\\b", "CURTIME()"),
            FunctionReplacement("\\bTO_CHAR\\s*\\(", "DATE_FORMAT("),
            FunctionReplacement("\\bTO_DATE\\s*\\(", "STR_TO_DATE("),
            FunctionReplacement("\\bTO_TIMESTAMP\\s*\\(", "FROM_UNIXTIME("),
            // 문자열 함수
            FunctionReplacement("\\bCOALESCE\\s*\\(", "IFNULL("),
            FunctionReplacement("\\bSTRING_AGG\\s*\\(", "GROUP_CONCAT("),
            // 수학 함수
            FunctionReplacement("\\bRANDOM\\s*\\(\\s*\\)", "RAND()"),
            FunctionReplacement("\\bTRUNC\\s*\\(", "TRUNCATE("),
            // 타입 캐스팅 제거
            FunctionReplacement("::INTEGER\\b", ""),
            FunctionReplacement("::TEXT\\b", ""),
            FunctionReplacement("::VARCHAR\\b", ""),
            FunctionReplacement("::NUMERIC\\b", ""),
            FunctionReplacement("::TIMESTAMP\\b", "")
        ),

        // PostgreSQL → Oracle
        DialectPair(DialectType.POSTGRESQL, DialectType.ORACLE) to listOf(
            FunctionReplacement("\\bCURRENT_TIMESTAMP\\b", "SYSDATE"),
            FunctionReplacement("\\bCOALESCE\\s*\\(", "NVL("),
            FunctionReplacement("\\bRANDOM\\s*\\(\\s*\\)", "DBMS_RANDOM.VALUE")
        )
    )

    /**
     * 함수 변환 수행
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) return sql

        val replacements = converters[DialectPair(sourceDialect, targetDialect)] ?: return sql

        var result = sql
        var hasChanges = false

        for (replacement in replacements) {
            val newResult = replacement.regex.replace(result, replacement.replacement)
            if (newResult != result) {
                result = newResult
                hasChanges = true
            }
        }

        if (hasChanges) {
            appliedRules.add("${sourceDialect.name} → ${targetDialect.name} 함수 변환")
        }

        return result
    }
}

/**
 * 함수 치환 규칙
 */
data class FunctionReplacement(
    val pattern: String,
    val replacement: String
) {
    val regex: Regex = Regex(pattern, RegexOption.IGNORE_CASE)
}

/**
 * Dialect 조합 키
 */
data class DialectPair(
    val source: DialectType,
    val target: DialectType
)