package com.sqlswitcher.converter.feature.pkg

/**
 * Oracle PACKAGE 파싱 유틸리티
 */
object PackageParser {

    // Oracle PACKAGE 사양 패턴
    val PACKAGE_SPEC_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?PACKAGE\s+(\w+)(?:\s+AUTHID\s+(?:DEFINER|CURRENT_USER))?\s+(?:IS|AS)\s*(.+?)\s*END\s+\1?\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // Oracle PACKAGE BODY 패턴
    val PACKAGE_BODY_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?PACKAGE\s+BODY\s+(\w+)\s+(?:IS|AS)\s*(.+?)\s*END\s+\1?\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // 패키지 내 프로시저/함수 선언 패턴
    val PROCEDURE_DECL_PATTERN = Regex(
        """PROCEDURE\s+(\w+)\s*(\([^)]*\))?\s*;""",
        RegexOption.IGNORE_CASE
    )

    val FUNCTION_DECL_PATTERN = Regex(
        """FUNCTION\s+(\w+)\s*(\([^)]*\))?\s*RETURN\s+(\w+)\s*;""",
        RegexOption.IGNORE_CASE
    )

    // 패키지 내 프로시저/함수 정의 패턴
    val PROCEDURE_DEF_PATTERN = Regex(
        """PROCEDURE\s+(\w+)\s*(\([^)]*\))?\s+(?:IS|AS)\s*(.+?)\s*END\s+\1\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    val FUNCTION_DEF_PATTERN = Regex(
        """FUNCTION\s+(\w+)\s*(\([^)]*\))?\s*RETURN\s+(\w+)\s+(?:IS|AS)\s*(.+?)\s*END\s+\1\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // 패키지 타입 선언 패턴
    val TYPE_PATTERN = Regex(
        """TYPE\s+(\w+)\s+IS\s+(.+?)\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * 패키지 문인지 확인
     */
    fun isPackageStatement(sql: String): Boolean {
        val upper = sql.uppercase()
        return upper.contains("CREATE") &&
               upper.contains("PACKAGE") &&
               !upper.contains("CREATE PACKAGE BODY")
    }

    /**
     * 패키지 바디 문인지 확인
     */
    fun isPackageBodyStatement(sql: String): Boolean {
        val upper = sql.uppercase()
        return upper.contains("CREATE") &&
               upper.contains("PACKAGE") &&
               upper.contains("BODY")
    }

    /**
     * 패키지 정보 추출
     */
    data class PackageInfo(
        val name: String,
        val body: String,
        val isBody: Boolean
    )

    fun parsePackage(sql: String): PackageInfo? {
        val isBody = isPackageBodyStatement(sql)
        val pattern = if (isBody) PACKAGE_BODY_PATTERN else PACKAGE_SPEC_PATTERN
        val match = pattern.find(sql) ?: return null

        return PackageInfo(
            name = match.groupValues[1],
            body = match.groupValues[2],
            isBody = isBody
        )
    }

    /**
     * 패키지 선언부 추출 (사양용)
     */
    fun extractDeclarations(packageName: String, body: String): String {
        val result = StringBuilder()

        // 프로시저 선언
        val procedures = PROCEDURE_DECL_PATTERN.findAll(body)
        for (proc in procedures) {
            result.append("-- PROCEDURE ${proc.groupValues[1]}${proc.groupValues[2]}\n")
        }

        // 함수 선언
        val functions = FUNCTION_DECL_PATTERN.findAll(body)
        for (func in functions) {
            result.append("-- FUNCTION ${func.groupValues[1]}${func.groupValues[2]} RETURNS ${func.groupValues[3]}\n")
        }

        // 타입 선언
        val types = TYPE_PATTERN.findAll(body)
        for (type in types) {
            result.append("-- TYPE ${type.groupValues[1]} IS ${type.groupValues[2]}\n")
        }

        return result.toString()
    }
}