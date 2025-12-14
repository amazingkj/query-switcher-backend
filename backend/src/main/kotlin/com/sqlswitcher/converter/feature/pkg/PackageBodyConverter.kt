package com.sqlswitcher.converter.feature.pkg

import com.sqlswitcher.converter.ConversionWarning

/**
 * Oracle PACKAGE 본문 변환 유틸리티
 */
object PackageBodyConverter {

    // =========================================================================
    // PostgreSQL 변환
    // =========================================================================

    /**
     * 패키지에서 함수/프로시저 추출 및 PostgreSQL 변환
     */
    fun extractAndConvertToPostgreSql(
        packageName: String,
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()

        // 프로시저 추출
        val procedures = PackageParser.PROCEDURE_DEF_PATTERN.findAll(body)
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
        val functions = PackageParser.FUNCTION_DEF_PATTERN.findAll(body)
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

    private fun convertParametersToPostgreSql(params: String): String {
        var result = params
        result = result.replace(Regex("\\bIN\\s+OUT\\b", RegexOption.IGNORE_CASE), "INOUT")
        result = result.replace(Regex("\\bVARCHAR2\\b", RegexOption.IGNORE_CASE), "VARCHAR")
        result = result.replace(Regex("\\bNUMBER\\b", RegexOption.IGNORE_CASE), "NUMERIC")
        result = result.replace(Regex("\\bDATE\\b", RegexOption.IGNORE_CASE), "TIMESTAMP")
        return result
    }

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

    private fun convertProcedureBodyToPostgreSql(body: String): String {
        var result = body.trim()

        if (!result.uppercase().startsWith("DECLARE") && !result.uppercase().startsWith("BEGIN")) {
            result = "DECLARE\n$result"
        }

        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
        result = result.replace(Regex("\\bDBMS_OUTPUT\\.PUT_LINE\\s*\\(", RegexOption.IGNORE_CASE), "RAISE NOTICE '%', ")
        result = result.replace(Regex("\\bRAISE_APPLICATION_ERROR\\s*\\(\\s*-?\\d+\\s*,\\s*", RegexOption.IGNORE_CASE), "RAISE EXCEPTION ")

        return result
    }

    private fun convertFunctionBodyToPostgreSql(body: String): String {
        var result = convertProcedureBodyToPostgreSql(body)

        if (!result.uppercase().contains("RETURN")) {
            result = "$result\n    RETURN NULL; -- TODO: 반환값 확인 필요"
        }

        return result
    }

    // =========================================================================
    // MySQL 변환
    // =========================================================================

    /**
     * 패키지에서 함수/프로시저 추출 및 MySQL 변환
     */
    fun extractAndConvertToMySql(
        packageName: String,
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()
        result.append("DELIMITER //\n\n")

        // 프로시저 추출
        val procedures = PackageParser.PROCEDURE_DEF_PATTERN.findAll(body)
        var procCount = 0
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
            procCount++
        }

        // 함수 추출
        val functions = PackageParser.FUNCTION_DEF_PATTERN.findAll(body)
        var funcCount = 0
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
            funcCount++
        }

        result.append("DELIMITER ;\n")

        if (procCount == 0 && funcCount == 0) {
            return buildString {
                append("-- 패키지에서 프로시저/함수를 찾을 수 없습니다.\n")
                append("-- 원본 패키지 본문:\n")
                append("/*\n$body\n*/\n")
            }
        }

        return result.toString()
    }

    private fun convertParametersToMySql(params: String): String {
        var result = params
        result = result.replace(Regex("\\bIN\\s+OUT\\b", RegexOption.IGNORE_CASE), "INOUT")
        result = result.replace(Regex("\\bVARCHAR2\\b", RegexOption.IGNORE_CASE), "VARCHAR")
        result = result.replace(Regex("\\bNUMBER\\b", RegexOption.IGNORE_CASE), "DECIMAL")
        result = result.replace(Regex("\\bCLOB\\b", RegexOption.IGNORE_CASE), "LONGTEXT")
        result = result.replace(Regex("\\bBLOB\\b", RegexOption.IGNORE_CASE), "LONGBLOB")
        return result
    }

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

    private fun convertProcedureBodyToMySql(body: String): String {
        var result = body.trim()

        result = result.replace(Regex("^\\s*DECLARE\\s*", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "IFNULL(")
        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "NOW()")
        result = result.replace(Regex("\\bDBMS_OUTPUT\\.PUT_LINE\\s*\\([^)]+\\)\\s*;", RegexOption.IGNORE_CASE), "-- DBMS_OUTPUT removed")
        result = result.replace(
            Regex("\\bRAISE_APPLICATION_ERROR\\s*\\(\\s*-?\\d+\\s*,\\s*'([^']+)'\\s*\\)", RegexOption.IGNORE_CASE)
        ) { m -> "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '${m.groupValues[1]}'" }
        result = result.replace(Regex("(\\w+)\\s*:=\\s*"), "SET $1 = ")

        return result
    }

    private fun convertFunctionBodyToMySql(body: String): String {
        return convertProcedureBodyToMySql(body)
    }
}