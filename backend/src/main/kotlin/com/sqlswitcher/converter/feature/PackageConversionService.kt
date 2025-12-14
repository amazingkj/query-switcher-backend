package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import org.springframework.stereotype.Service

/**
 * Oracle PACKAGE 변환 서비스
 *
 * Oracle 패키지는 다른 RDBMS에 직접적인 대응 개념이 없음:
 * - PostgreSQL: 스키마 + 함수/프로시저로 분리
 * - MySQL: 개별 프로시저/함수로 분리
 *
 * 이 서비스는:
 * 1. 패키지 구문 감지
 * 2. 패키지 구조 분석 (사양/본문, 공개/비공개 요소)
 * 3. 변환 가이드 및 분리된 객체 생성
 */
@Service
class PackageConversionService {

    // Oracle PACKAGE 사양 패턴
    private val PACKAGE_SPEC_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?PACKAGE\s+(\w+)(?:\s+AUTHID\s+(?:DEFINER|CURRENT_USER))?\s+(?:IS|AS)\s*(.+?)\s*END\s+\1?\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // Oracle PACKAGE BODY 패턴
    private val PACKAGE_BODY_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?PACKAGE\s+BODY\s+(\w+)\s+(?:IS|AS)\s*(.+?)\s*END\s+\1?\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // 패키지 내 프로시저/함수 선언 패턴
    private val PROCEDURE_DECL_PATTERN = Regex(
        """PROCEDURE\s+(\w+)\s*(\([^)]*\))?\s*;""",
        RegexOption.IGNORE_CASE
    )

    private val FUNCTION_DECL_PATTERN = Regex(
        """FUNCTION\s+(\w+)\s*(\([^)]*\))?\s*RETURN\s+(\w+)\s*;""",
        RegexOption.IGNORE_CASE
    )

    // 패키지 내 프로시저/함수 정의 패턴
    private val PROCEDURE_DEF_PATTERN = Regex(
        """PROCEDURE\s+(\w+)\s*(\([^)]*\))?\s+(?:IS|AS)\s*(.+?)\s*END\s+\1\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val FUNCTION_DEF_PATTERN = Regex(
        """FUNCTION\s+(\w+)\s*(\([^)]*\))?\s*RETURN\s+(\w+)\s+(?:IS|AS)\s*(.+?)\s*END\s+\1\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // 패키지 변수/상수/타입 선언 패턴
    private val VARIABLE_PATTERN = Regex(
        """(\w+)\s+(?:CONSTANT\s+)?(\w+(?:\([^)]*\))?)\s*(?::=\s*([^;]+))?\s*;""",
        RegexOption.IGNORE_CASE
    )

    private val TYPE_PATTERN = Regex(
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
               !upper.contains("CREATE PACKAGE BODY") // BODY는 별도 처리
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
     * 패키지 변환
     */
    fun convertPackage(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // Oracle에서만 패키지 존재
        if (sourceDialect != DialectType.ORACLE) {
            return sql
        }

        return when (targetDialect) {
            DialectType.POSTGRESQL -> convertToPostgreSql(sql, warnings, appliedRules)
            DialectType.MYSQL -> convertToMySql(sql, warnings, appliedRules)
            else -> sql
        }
    }

    /**
     * Oracle 패키지 → PostgreSQL 변환
     */
    private fun convertToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val isBody = isPackageBodyStatement(sql)
        val pattern = if (isBody) PACKAGE_BODY_PATTERN else PACKAGE_SPEC_PATTERN
        val match = pattern.find(sql)

        if (match == null) {
            warnings.add(ConversionWarning(
                WarningType.MANUAL_REVIEW_NEEDED,
                "패키지 구문을 파싱할 수 없습니다.",
                WarningSeverity.WARNING
            ))
            return sql
        }

        val packageName = match.groupValues[1]
        val packageBody = match.groupValues[2]

        val result = StringBuilder()

        // 가이드 주석
        result.append("-- =====================================================\n")
        result.append("-- Oracle PACKAGE '$packageName' → PostgreSQL 변환\n")
        result.append("-- PostgreSQL은 패키지를 직접 지원하지 않습니다.\n")
        result.append("-- 스키마 + 개별 함수/프로시저로 분리하여 구현합니다.\n")
        result.append("-- =====================================================\n\n")

        // 스키마 생성 (패키지명을 스키마로)
        result.append("-- 패키지를 스키마로 변환\n")
        result.append("CREATE SCHEMA IF NOT EXISTS $packageName;\n\n")

        if (isBody) {
            // 패키지 바디: 실제 구현 추출
            result.append(extractAndConvertFunctionsToPostgreSql(packageName, packageBody, warnings, appliedRules))
        } else {
            // 패키지 사양: 선언만 (PostgreSQL에서는 함수 시그니처로)
            result.append("-- 패키지 사양 (선언부)\n")
            result.append("-- PostgreSQL에서는 함수 본문이 필요합니다.\n")
            result.append("-- PACKAGE BODY의 변환 결과를 사용하세요.\n\n")
            result.append(extractDeclarations(packageName, packageBody))
        }

        warnings.add(ConversionWarning(
            WarningType.PARTIAL_SUPPORT,
            "Oracle 패키지 '$packageName'을 PostgreSQL 스키마 + 함수로 변환했습니다.",
            WarningSeverity.WARNING,
            "패키지 변수와 초기화 블록은 수동 검토가 필요합니다. " +
            "패키지 호출부 (${packageName}.procedure_name)도 스키마 접두사로 수정하세요."
        ))
        appliedRules.add("Oracle PACKAGE → PostgreSQL 스키마 + 함수 변환")

        return result.toString()
    }

    /**
     * Oracle 패키지 → MySQL 변환
     */
    private fun convertToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val isBody = isPackageBodyStatement(sql)
        val pattern = if (isBody) PACKAGE_BODY_PATTERN else PACKAGE_SPEC_PATTERN
        val match = pattern.find(sql)

        if (match == null) {
            warnings.add(ConversionWarning(
                WarningType.MANUAL_REVIEW_NEEDED,
                "패키지 구문을 파싱할 수 없습니다.",
                WarningSeverity.WARNING
            ))
            return sql
        }

        val packageName = match.groupValues[1]
        val packageBody = match.groupValues[2]

        val result = StringBuilder()

        // 가이드 주석
        result.append("-- =====================================================\n")
        result.append("-- Oracle PACKAGE '$packageName' → MySQL 변환\n")
        result.append("-- MySQL은 패키지를 지원하지 않습니다.\n")
        result.append("-- 개별 프로시저/함수로 분리하여 구현합니다.\n")
        result.append("-- 패키지명을 접두사로 사용: ${packageName}_procedure_name\n")
        result.append("-- =====================================================\n\n")

        if (isBody) {
            result.append(extractAndConvertFunctionsToMySql(packageName, packageBody, warnings, appliedRules))
        } else {
            result.append("-- 패키지 사양 (선언부)\n")
            result.append("-- MySQL에서는 프로시저/함수 본문이 필요합니다.\n")
            result.append("-- PACKAGE BODY의 변환 결과를 사용하세요.\n\n")
            result.append(extractDeclarations(packageName, packageBody))
        }

        warnings.add(ConversionWarning(
            WarningType.PARTIAL_SUPPORT,
            "Oracle 패키지 '$packageName'을 MySQL 개별 프로시저/함수로 변환했습니다.",
            WarningSeverity.WARNING,
            "패키지 변수는 MySQL에서 지원되지 않습니다. 세션 변수 또는 테이블로 대체하세요. " +
            "패키지 호출부 (${packageName}.procedure_name)를 ${packageName}_procedure_name으로 수정하세요."
        ))
        appliedRules.add("Oracle PACKAGE → MySQL 개별 프로시저/함수 변환")

        return result.toString()
    }

    /**
     * 패키지에서 함수/프로시저 추출 및 PostgreSQL 변환
     */
    private fun extractAndConvertFunctionsToPostgreSql(
        packageName: String,
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()

        // 프로시저 추출
        val procedures = PROCEDURE_DEF_PATTERN.findAll(body)
        for (proc in procedures) {
            val procName = proc.groupValues[1]
            val params = proc.groupValues[2]
            val procBody = proc.groupValues[3]

            result.append("-- Procedure: $packageName.$procName\n")
            result.append("CREATE OR REPLACE PROCEDURE $packageName.$procName")
            if (params.isNotEmpty()) {
                result.append(convertParametersToPostgreSql(params))
            } else {
                result.append("()")
            }
            result.append("\nLANGUAGE plpgsql AS \$\$\n")
            result.append(convertProcedureBodyToPostgreSql(procBody))
            result.append("\n\$\$;\n\n")
        }

        // 함수 추출
        val functions = FUNCTION_DEF_PATTERN.findAll(body)
        for (func in functions) {
            val funcName = func.groupValues[1]
            val params = func.groupValues[2]
            val returnType = func.groupValues[3]
            val funcBody = func.groupValues[4]

            result.append("-- Function: $packageName.$funcName\n")
            result.append("CREATE OR REPLACE FUNCTION $packageName.$funcName")
            if (params.isNotEmpty()) {
                result.append(convertParametersToPostgreSql(params))
            } else {
                result.append("()")
            }
            result.append("\nRETURNS ${convertDataTypeToPostgreSql(returnType)}\n")
            result.append("LANGUAGE plpgsql AS \$\$\n")
            result.append(convertFunctionBodyToPostgreSql(funcBody))
            result.append("\n\$\$;\n\n")
        }

        if (result.isEmpty()) {
            result.append("-- 패키지에서 프로시저/함수를 찾을 수 없습니다.\n")
            result.append("-- 원본 패키지 본문:\n")
            result.append("/*\n$body\n*/\n")
        }

        return result.toString()
    }

    /**
     * 패키지에서 함수/프로시저 추출 및 MySQL 변환
     */
    private fun extractAndConvertFunctionsToMySql(
        packageName: String,
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()
        result.append("DELIMITER //\n\n")

        // 프로시저 추출
        val procedures = PROCEDURE_DEF_PATTERN.findAll(body)
        for (proc in procedures) {
            val procName = proc.groupValues[1]
            val params = proc.groupValues[2]
            val procBody = proc.groupValues[3]

            result.append("-- Procedure: ${packageName}_$procName\n")
            result.append("CREATE PROCEDURE ${packageName}_$procName")
            if (params.isNotEmpty()) {
                result.append(convertParametersToMySql(params))
            } else {
                result.append("()")
            }
            result.append("\nBEGIN\n")
            result.append(convertProcedureBodyToMySql(procBody))
            result.append("\nEND//\n\n")
        }

        // 함수 추출
        val functions = FUNCTION_DEF_PATTERN.findAll(body)
        for (func in functions) {
            val funcName = func.groupValues[1]
            val params = func.groupValues[2]
            val returnType = func.groupValues[3]
            val funcBody = func.groupValues[4]

            result.append("-- Function: ${packageName}_$funcName\n")
            result.append("CREATE FUNCTION ${packageName}_$funcName")
            if (params.isNotEmpty()) {
                result.append(convertParametersToMySql(params))
            } else {
                result.append("()")
            }
            result.append("\nRETURNS ${convertDataTypeToMySql(returnType)}\n")
            result.append("DETERMINISTIC\n")
            result.append("BEGIN\n")
            result.append(convertFunctionBodyToMySql(funcBody))
            result.append("\nEND//\n\n")
        }

        result.append("DELIMITER ;\n")

        if (procedures.count() == 0 && functions.count() == 0) {
            return buildString {
                append("-- 패키지에서 프로시저/함수를 찾을 수 없습니다.\n")
                append("-- 원본 패키지 본문:\n")
                append("/*\n$body\n*/\n")
            }
        }

        return result.toString()
    }

    /**
     * 패키지 선언부 추출 (사양용)
     */
    private fun extractDeclarations(packageName: String, body: String): String {
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

    /**
     * Oracle 파라미터 → PostgreSQL 변환
     */
    private fun convertParametersToPostgreSql(params: String): String {
        var result = params
        // IN/OUT/IN OUT 변환
        result = result.replace(Regex("\\bIN\\s+OUT\\b", RegexOption.IGNORE_CASE), "INOUT")
        // 데이터 타입 변환
        result = result.replace(Regex("\\bVARCHAR2\\b", RegexOption.IGNORE_CASE), "VARCHAR")
        result = result.replace(Regex("\\bNUMBER\\b", RegexOption.IGNORE_CASE), "NUMERIC")
        result = result.replace(Regex("\\bDATE\\b", RegexOption.IGNORE_CASE), "TIMESTAMP")
        return result
    }

    /**
     * Oracle 파라미터 → MySQL 변환
     */
    private fun convertParametersToMySql(params: String): String {
        var result = params
        // IN OUT → INOUT
        result = result.replace(Regex("\\bIN\\s+OUT\\b", RegexOption.IGNORE_CASE), "INOUT")
        // 데이터 타입 변환
        result = result.replace(Regex("\\bVARCHAR2\\b", RegexOption.IGNORE_CASE), "VARCHAR")
        result = result.replace(Regex("\\bNUMBER\\b", RegexOption.IGNORE_CASE), "DECIMAL")
        result = result.replace(Regex("\\bCLOB\\b", RegexOption.IGNORE_CASE), "LONGTEXT")
        result = result.replace(Regex("\\bBLOB\\b", RegexOption.IGNORE_CASE), "LONGBLOB")
        return result
    }

    /**
     * Oracle 데이터 타입 → PostgreSQL 변환
     */
    private fun convertDataTypeToPostgreSql(type: String): String {
        return when (type.uppercase()) {
            "VARCHAR2" -> "VARCHAR"
            "NUMBER" -> "NUMERIC"
            "DATE" -> "TIMESTAMP"
            "CLOB" -> "TEXT"
            "BLOB" -> "BYTEA"
            "BOOLEAN" -> "BOOLEAN"
            "INTEGER", "INT" -> "INTEGER"
            else -> type
        }
    }

    /**
     * Oracle 데이터 타입 → MySQL 변환
     */
    private fun convertDataTypeToMySql(type: String): String {
        return when (type.uppercase()) {
            "VARCHAR2" -> "VARCHAR(4000)"
            "NUMBER" -> "DECIMAL(38,10)"
            "DATE" -> "DATETIME"
            "CLOB" -> "LONGTEXT"
            "BLOB" -> "LONGBLOB"
            "BOOLEAN" -> "TINYINT(1)"
            "INTEGER", "INT" -> "INT"
            else -> type
        }
    }

    /**
     * Oracle 프로시저 본문 → PostgreSQL 변환
     */
    private fun convertProcedureBodyToPostgreSql(body: String): String {
        var result = body.trim()

        // DECLARE 블록 처리
        if (!result.uppercase().startsWith("DECLARE") && !result.uppercase().startsWith("BEGIN")) {
            result = "DECLARE\n$result"
        }

        // Oracle 함수 → PostgreSQL
        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
        result = result.replace(Regex("\\bDBMS_OUTPUT\\.PUT_LINE\\s*\\(", RegexOption.IGNORE_CASE), "RAISE NOTICE '%', ")

        // 예외 처리 변환
        result = result.replace(Regex("\\bRAISE_APPLICATION_ERROR\\s*\\(\\s*-?\\d+\\s*,\\s*", RegexOption.IGNORE_CASE), "RAISE EXCEPTION ")

        return result
    }

    /**
     * Oracle 함수 본문 → PostgreSQL 변환
     */
    private fun convertFunctionBodyToPostgreSql(body: String): String {
        var result = convertProcedureBodyToPostgreSql(body)

        // RETURN 문 확인
        if (!result.uppercase().contains("RETURN")) {
            result = "$result\n    RETURN NULL; -- TODO: 반환값 확인 필요"
        }

        return result
    }

    /**
     * Oracle 프로시저 본문 → MySQL 변환
     */
    private fun convertProcedureBodyToMySql(body: String): String {
        var result = body.trim()

        // DECLARE 블록 제거 (MySQL은 BEGIN 내부에서 선언)
        result = result.replace(Regex("^\\s*DECLARE\\s*", RegexOption.IGNORE_CASE), "")

        // Oracle 함수 → MySQL
        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "IFNULL(")
        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "NOW()")
        result = result.replace(Regex("\\bDBMS_OUTPUT\\.PUT_LINE\\s*\\([^)]+\\)\\s*;", RegexOption.IGNORE_CASE), "-- DBMS_OUTPUT removed")

        // 예외 처리 변환
        result = result.replace(
            Regex("\\bRAISE_APPLICATION_ERROR\\s*\\(\\s*-?\\d+\\s*,\\s*'([^']+)'\\s*\\)", RegexOption.IGNORE_CASE)
        ) { m -> "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '${m.groupValues[1]}'" }

        // := 할당 연산자 → SET
        result = result.replace(Regex("(\\w+)\\s*:=\\s*"), "SET $1 = ")

        return result
    }

    /**
     * Oracle 함수 본문 → MySQL 변환
     */
    private fun convertFunctionBodyToMySql(body: String): String {
        return convertProcedureBodyToMySql(body)
    }

    /**
     * 패키지 호출부 변환 (package.procedure → schema.procedure 또는 package_procedure)
     */
    fun convertPackageCall(
        sql: String,
        packageName: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql

        var result = sql

        when (targetDialect) {
            DialectType.POSTGRESQL -> {
                // Oracle: package.procedure → PostgreSQL: schema.procedure (동일)
                // 패키지명.프로시저명 유지
                appliedRules.add("패키지 호출 유지 (PostgreSQL 스키마)")
            }
            DialectType.MYSQL -> {
                // Oracle: package.procedure → MySQL: package_procedure
                val callPattern = Regex("""${packageName}\.(\w+)""", RegexOption.IGNORE_CASE)
                result = result.replace(callPattern) { m ->
                    "${packageName}_${m.groupValues[1]}"
                }
                appliedRules.add("패키지 호출 변환: $packageName.xxx → ${packageName}_xxx")
            }
            else -> {}
        }

        return result
    }

    /**
     * DROP PACKAGE 변환
     */
    fun convertDropPackage(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql

        val dropPattern = Regex(
            """DROP\s+PACKAGE\s+(BODY\s+)?(\w+)""",
            RegexOption.IGNORE_CASE
        )
        val match = dropPattern.find(sql) ?: return sql

        val isBody = match.groupValues[1].isNotEmpty()
        val packageName = match.groupValues[2]

        return when (targetDialect) {
            DialectType.POSTGRESQL -> {
                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "DROP PACKAGE를 DROP SCHEMA로 변환했습니다. 스키마 내 모든 객체가 삭제됩니다.",
                    WarningSeverity.WARNING
                ))
                appliedRules.add("DROP PACKAGE → DROP SCHEMA")
                "DROP SCHEMA IF EXISTS $packageName CASCADE;"
            }
            DialectType.MYSQL -> {
                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "MySQL에서 패키지의 개별 프로시저/함수를 수동으로 삭제해야 합니다.",
                    WarningSeverity.WARNING,
                    "DROP PROCEDURE ${packageName}_xxx, DROP FUNCTION ${packageName}_xxx 형식으로 삭제하세요."
                ))
                appliedRules.add("DROP PACKAGE → 수동 삭제 필요")
                "-- MySQL: Drop individual procedures/functions with prefix '${packageName}_'\n-- DROP PROCEDURE IF EXISTS ${packageName}_procedure_name;\n-- DROP FUNCTION IF EXISTS ${packageName}_function_name;"
            }
            else -> sql
        }
    }
}