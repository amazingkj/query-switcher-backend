package com.sqlswitcher.converter.feature.plsql

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * Oracle PL/SQL 패키지 변환기
 *
 * Oracle 패키지를 개별 함수/프로시저로 분리하여 MySQL/PostgreSQL로 변환
 *
 * 지원 기능:
 * - PACKAGE SPECIFICATION 파싱
 * - PACKAGE BODY 파싱 및 분리
 * - 패키지 함수/프로시저 → 독립 함수/프로시저
 * - 패키지 변수 → 테이블 또는 세션 변수
 * - 패키지 타입 → 독립 타입 정의
 * - 패키지 상수 → 상수 함수 또는 변수
 *
 * 변환 전략:
 * - MySQL: 각 함수/프로시저를 개별로 생성, 패키지명을 접두사로
 * - PostgreSQL: 스키마로 그룹화하거나 접두사 방식
 */
object OraclePackageConverter {

    // ============ 패키지 파싱 패턴 ============

    /** 패키지 스펙 헤더 패턴 */
    private val PACKAGE_SPEC_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?PACKAGE\s+(?:(\w+)\.)?(\w+)\s+(?:AUTHID\s+\w+\s+)?(?:AS|IS)""",
        RegexOption.IGNORE_CASE
    )

    /** 패키지 바디 헤더 패턴 */
    private val PACKAGE_BODY_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?PACKAGE\s+BODY\s+(?:(\w+)\.)?(\w+)\s+(?:AS|IS)""",
        RegexOption.IGNORE_CASE
    )

    /** 함수 선언 패턴 (스펙) */
    private val FUNCTION_DECL_PATTERN = Regex(
        """FUNCTION\s+(\w+)\s*\(([^)]*)\)\s*RETURN\s+(\w+(?:\s*\([^)]*\))?)\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** 프로시저 선언 패턴 (스펙) */
    private val PROCEDURE_DECL_PATTERN = Regex(
        """PROCEDURE\s+(\w+)\s*(?:\(([^)]*)\))?\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** 함수 정의 패턴 (바디) */
    private val FUNCTION_DEF_PATTERN = Regex(
        """FUNCTION\s+(\w+)\s*\(([^)]*)\)\s*RETURN\s+(\w+(?:\s*\([^)]*\))?)\s+(?:IS|AS)\s+(.+?)END\s+\1\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** 프로시저 정의 패턴 (바디) */
    private val PROCEDURE_DEF_PATTERN = Regex(
        """PROCEDURE\s+(\w+)\s*(?:\(([^)]*)\))?\s+(?:IS|AS)\s+(.+?)END\s+\1\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** 패키지 변수 패턴 */
    private val PACKAGE_VAR_PATTERN = Regex(
        """(\w+)\s+(\w+(?:\s*\([^)]*\))?)\s*(?::=\s*(.+?))?;""",
        RegexOption.IGNORE_CASE
    )

    /** 패키지 상수 패턴 */
    private val PACKAGE_CONST_PATTERN = Regex(
        """(\w+)\s+CONSTANT\s+(\w+(?:\s*\([^)]*\))?)\s*:=\s*(.+?);""",
        RegexOption.IGNORE_CASE
    )

    /** TYPE 정의 패턴 */
    private val TYPE_DEF_PATTERN = Regex(
        """TYPE\s+(\w+)\s+IS\s+(.+?);""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** CURSOR 정의 패턴 */
    private val CURSOR_DEF_PATTERN = Regex(
        """CURSOR\s+(\w+)(?:\s*\(([^)]*)\))?\s+IS\s+(.+?);""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** 패키지 END 패턴 */
    private val PACKAGE_END_PATTERN = Regex(
        """END\s+(\w+)\s*;""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 패키지 변환 메인 함수
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql

        val isSpec = PACKAGE_SPEC_PATTERN.containsMatchIn(sql) &&
                !PACKAGE_BODY_PATTERN.containsMatchIn(sql)
        val isBody = PACKAGE_BODY_PATTERN.containsMatchIn(sql)

        if (!isSpec && !isBody) return sql

        return when (targetDialect) {
            DialectType.MYSQL -> {
                if (isBody) convertBodyToMySql(sql, warnings, appliedRules)
                else convertSpecToMySql(sql, warnings, appliedRules)
            }
            DialectType.POSTGRESQL -> {
                if (isBody) convertBodyToPostgreSql(sql, warnings, appliedRules)
                else convertSpecToPostgreSql(sql, warnings, appliedRules)
            }
            DialectType.ORACLE -> sql
        }
    }

    /**
     * 패키지 스펙 → MySQL (주석으로 변환)
     */
    private fun convertSpecToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val packageInfo = parsePackageSpec(sql) ?: return sql

        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "MySQL은 패키지 개념을 지원하지 않습니다. 패키지 스펙은 주석으로 변환됩니다.",
            severity = WarningSeverity.INFO,
            suggestion = "패키지 바디의 함수/프로시저가 개별적으로 생성됩니다."
        ))

        val sb = StringBuilder()
        sb.appendLine("-- ============================================")
        sb.appendLine("-- Oracle Package Specification: ${packageInfo.name}")
        sb.appendLine("-- MySQL에서는 패키지 스펙이 필요하지 않습니다.")
        sb.appendLine("-- 함수/프로시저는 ${packageInfo.name}_ 접두사로 생성됩니다.")
        sb.appendLine("-- ============================================")
        sb.appendLine()

        // 상수를 MySQL 함수로 변환
        packageInfo.constants.forEach { const ->
            sb.appendLine("-- Constant: ${const.name}")
            sb.appendLine("DELIMITER //")
            sb.appendLine("CREATE FUNCTION ${packageInfo.name}_${const.name}()")
            sb.appendLine("RETURNS ${convertDataType(const.dataType, DialectType.MYSQL)}")
            sb.appendLine("DETERMINISTIC")
            sb.appendLine("BEGIN")
            sb.appendLine("    RETURN ${const.defaultValue};")
            sb.appendLine("END//")
            sb.appendLine("DELIMITER ;")
            sb.appendLine()
        }

        appliedRules.add("Oracle 패키지 스펙 → MySQL 주석 변환: ${packageInfo.name}")
        return sb.toString()
    }

    /**
     * 패키지 스펙 → PostgreSQL
     */
    private fun convertSpecToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val packageInfo = parsePackageSpec(sql) ?: return sql

        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "PostgreSQL은 패키지 개념을 지원하지 않습니다.",
            severity = WarningSeverity.INFO,
            suggestion = "스키마를 사용하여 패키지를 시뮬레이션하거나 함수 접두사를 사용합니다."
        ))

        val sb = StringBuilder()
        sb.appendLine("-- ============================================")
        sb.appendLine("-- Oracle Package Specification: ${packageInfo.name}")
        sb.appendLine("-- PostgreSQL 스키마로 변환")
        sb.appendLine("-- ============================================")
        sb.appendLine()

        // 스키마 생성
        sb.appendLine("CREATE SCHEMA IF NOT EXISTS ${packageInfo.name.lowercase()};")
        sb.appendLine()

        // 상수를 immutable 함수로 변환
        packageInfo.constants.forEach { const ->
            sb.appendLine("-- Constant: ${const.name}")
            sb.appendLine("CREATE OR REPLACE FUNCTION ${packageInfo.name.lowercase()}.${const.name}()")
            sb.appendLine("RETURNS ${convertDataType(const.dataType, DialectType.POSTGRESQL)}")
            sb.appendLine("LANGUAGE sql IMMUTABLE")
            sb.appendLine("AS \$\$ SELECT ${const.defaultValue}::${convertDataType(const.dataType, DialectType.POSTGRESQL)} \$\$;")
            sb.appendLine()
        }

        appliedRules.add("Oracle 패키지 스펙 → PostgreSQL 스키마 변환: ${packageInfo.name}")
        return sb.toString()
    }

    /**
     * 패키지 바디 → MySQL
     */
    private fun convertBodyToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val packageInfo = parsePackageBody(sql) ?: return sql

        val sb = StringBuilder()
        sb.appendLine("-- ============================================")
        sb.appendLine("-- Oracle Package Body: ${packageInfo.name}")
        sb.appendLine("-- MySQL 개별 함수/프로시저로 변환")
        sb.appendLine("-- ============================================")
        sb.appendLine()

        // 패키지 변수 → 테이블로 변환
        if (packageInfo.variables.isNotEmpty()) {
            sb.appendLine("-- 패키지 변수 저장용 테이블")
            sb.appendLine("CREATE TABLE IF NOT EXISTS ${packageInfo.name}_vars (")
            sb.appendLine("    var_name VARCHAR(100) PRIMARY KEY,")
            sb.appendLine("    var_value TEXT,")
            sb.appendLine("    connection_id BIGINT DEFAULT CONNECTION_ID()")
            sb.appendLine(");")
            sb.appendLine()

            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "패키지 변수가 테이블로 변환되었습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "세션 변수(@var) 사용을 고려하세요. 테이블: ${packageInfo.name}_vars"
            ))
        }

        // 함수 변환
        packageInfo.functions.forEach { func ->
            sb.appendLine("-- Function: ${func.name}")
            sb.appendLine("DELIMITER //")
            sb.appendLine("DROP FUNCTION IF EXISTS ${packageInfo.name}_${func.name}//")
            sb.appendLine("CREATE FUNCTION ${packageInfo.name}_${func.name}(${convertParameters(func.parameters, DialectType.MYSQL)})")
            sb.appendLine("RETURNS ${convertDataType(func.returnType, DialectType.MYSQL)}")
            sb.appendLine("DETERMINISTIC")
            sb.appendLine("BEGIN")

            val body = convertFunctionBody(func.body, packageInfo.name, DialectType.MYSQL)
            body.lines().forEach { line ->
                if (line.isNotBlank()) {
                    sb.appendLine("    $line")
                }
            }

            sb.appendLine("END//")
            sb.appendLine("DELIMITER ;")
            sb.appendLine()
        }

        // 프로시저 변환
        packageInfo.procedures.forEach { proc ->
            sb.appendLine("-- Procedure: ${proc.name}")
            sb.appendLine("DELIMITER //")
            sb.appendLine("DROP PROCEDURE IF EXISTS ${packageInfo.name}_${proc.name}//")
            sb.appendLine("CREATE PROCEDURE ${packageInfo.name}_${proc.name}(${convertParameters(proc.parameters, DialectType.MYSQL)})")
            sb.appendLine("BEGIN")

            val body = convertProcedureBody(proc.body, packageInfo.name, DialectType.MYSQL)
            body.lines().forEach { line ->
                if (line.isNotBlank()) {
                    sb.appendLine("    $line")
                }
            }

            sb.appendLine("END//")
            sb.appendLine("DELIMITER ;")
            sb.appendLine()
        }

        appliedRules.add("Oracle 패키지 바디 → MySQL 함수/프로시저 ${packageInfo.functions.size + packageInfo.procedures.size}개 변환")
        return sb.toString()
    }

    /**
     * 패키지 바디 → PostgreSQL
     */
    private fun convertBodyToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val packageInfo = parsePackageBody(sql) ?: return sql

        val schemaName = packageInfo.name.lowercase()

        val sb = StringBuilder()
        sb.appendLine("-- ============================================")
        sb.appendLine("-- Oracle Package Body: ${packageInfo.name}")
        sb.appendLine("-- PostgreSQL 스키마 내 함수로 변환")
        sb.appendLine("-- ============================================")
        sb.appendLine()

        // 스키마 생성
        sb.appendLine("CREATE SCHEMA IF NOT EXISTS $schemaName;")
        sb.appendLine()

        // 패키지 변수 → 테이블로 변환
        if (packageInfo.variables.isNotEmpty()) {
            sb.appendLine("-- 패키지 변수 저장용 테이블")
            sb.appendLine("CREATE TABLE IF NOT EXISTS $schemaName.pkg_variables (")
            sb.appendLine("    var_name VARCHAR(100) PRIMARY KEY,")
            sb.appendLine("    var_value TEXT,")
            sb.appendLine("    session_id TEXT DEFAULT pg_backend_pid()::TEXT")
            sb.appendLine(");")
            sb.appendLine()

            // 변수 접근 함수 생성
            sb.appendLine("-- 변수 getter/setter")
            sb.appendLine("CREATE OR REPLACE FUNCTION $schemaName.get_var(p_name VARCHAR)")
            sb.appendLine("RETURNS TEXT")
            sb.appendLine("LANGUAGE plpgsql")
            sb.appendLine("AS \$\$")
            sb.appendLine("BEGIN")
            sb.appendLine("    RETURN (SELECT var_value FROM $schemaName.pkg_variables WHERE var_name = p_name);")
            sb.appendLine("END;")
            sb.appendLine("\$\$;")
            sb.appendLine()

            sb.appendLine("CREATE OR REPLACE FUNCTION $schemaName.set_var(p_name VARCHAR, p_value TEXT)")
            sb.appendLine("RETURNS VOID")
            sb.appendLine("LANGUAGE plpgsql")
            sb.appendLine("AS \$\$")
            sb.appendLine("BEGIN")
            sb.appendLine("    INSERT INTO $schemaName.pkg_variables (var_name, var_value)")
            sb.appendLine("    VALUES (p_name, p_value)")
            sb.appendLine("    ON CONFLICT (var_name) DO UPDATE SET var_value = p_value;")
            sb.appendLine("END;")
            sb.appendLine("\$\$;")
            sb.appendLine()
        }

        // 함수 변환
        packageInfo.functions.forEach { func ->
            sb.appendLine("-- Function: ${func.name}")
            sb.appendLine("CREATE OR REPLACE FUNCTION $schemaName.${func.name}(${convertParameters(func.parameters, DialectType.POSTGRESQL)})")
            sb.appendLine("RETURNS ${convertDataType(func.returnType, DialectType.POSTGRESQL)}")
            sb.appendLine("LANGUAGE plpgsql")
            sb.appendLine("AS \$\$")

            // DECLARE 블록 추출
            val (declareBlock, execBlock) = extractDeclareAndExec(func.body)
            if (declareBlock.isNotBlank()) {
                sb.appendLine("DECLARE")
                convertDeclareBlock(declareBlock, DialectType.POSTGRESQL).lines().forEach { line ->
                    if (line.isNotBlank()) {
                        sb.appendLine("    $line")
                    }
                }
            }

            sb.appendLine("BEGIN")
            val body = convertFunctionBody(execBlock, packageInfo.name, DialectType.POSTGRESQL)
            body.lines().forEach { line ->
                if (line.isNotBlank()) {
                    sb.appendLine("    $line")
                }
            }
            sb.appendLine("END;")
            sb.appendLine("\$\$;")
            sb.appendLine()
        }

        // 프로시저 변환 (PostgreSQL 11+)
        packageInfo.procedures.forEach { proc ->
            sb.appendLine("-- Procedure: ${proc.name}")
            sb.appendLine("CREATE OR REPLACE PROCEDURE $schemaName.${proc.name}(${convertParameters(proc.parameters, DialectType.POSTGRESQL)})")
            sb.appendLine("LANGUAGE plpgsql")
            sb.appendLine("AS \$\$")

            val (declareBlock, execBlock) = extractDeclareAndExec(proc.body)
            if (declareBlock.isNotBlank()) {
                sb.appendLine("DECLARE")
                convertDeclareBlock(declareBlock, DialectType.POSTGRESQL).lines().forEach { line ->
                    if (line.isNotBlank()) {
                        sb.appendLine("    $line")
                    }
                }
            }

            sb.appendLine("BEGIN")
            val body = convertProcedureBody(execBlock, packageInfo.name, DialectType.POSTGRESQL)
            body.lines().forEach { line ->
                if (line.isNotBlank()) {
                    sb.appendLine("    $line")
                }
            }
            sb.appendLine("END;")
            sb.appendLine("\$\$;")
            sb.appendLine()
        }

        appliedRules.add("Oracle 패키지 바디 → PostgreSQL $schemaName 스키마 내 함수 ${packageInfo.functions.size + packageInfo.procedures.size}개 변환")
        return sb.toString()
    }

    /**
     * 패키지 스펙 파싱
     */
    private fun parsePackageSpec(sql: String): PackageInfo? {
        val headerMatch = PACKAGE_SPEC_PATTERN.find(sql) ?: return null
        val schema = headerMatch.groupValues[1].takeIf { it.isNotBlank() }
        val name = headerMatch.groupValues[2]

        val constants = mutableListOf<ConstantInfo>()
        val variables = mutableListOf<VariableInfo>()
        val types = mutableListOf<TypeInfo>()
        val functions = mutableListOf<FunctionDecl>()
        val procedures = mutableListOf<ProcedureDecl>()

        // 상수 파싱
        PACKAGE_CONST_PATTERN.findAll(sql).forEach { match ->
            constants.add(ConstantInfo(
                name = match.groupValues[1],
                dataType = match.groupValues[2],
                defaultValue = match.groupValues[3].trim()
            ))
        }

        // 함수 선언 파싱
        FUNCTION_DECL_PATTERN.findAll(sql).forEach { match ->
            functions.add(FunctionDecl(
                name = match.groupValues[1],
                parameters = match.groupValues[2],
                returnType = match.groupValues[3]
            ))
        }

        // 프로시저 선언 파싱
        PROCEDURE_DECL_PATTERN.findAll(sql).forEach { match ->
            procedures.add(ProcedureDecl(
                name = match.groupValues[1],
                parameters = match.groupValues[2]
            ))
        }

        // TYPE 파싱
        TYPE_DEF_PATTERN.findAll(sql).forEach { match ->
            types.add(TypeInfo(
                name = match.groupValues[1],
                definition = match.groupValues[2].trim()
            ))
        }

        return PackageInfo(
            schema = schema,
            name = name,
            constants = constants,
            variables = variables,
            types = types,
            functions = emptyList(),
            procedures = emptyList(),
            functionDecls = functions,
            procedureDecls = procedures
        )
    }

    /**
     * 패키지 바디 파싱
     */
    private fun parsePackageBody(sql: String): PackageInfo? {
        val headerMatch = PACKAGE_BODY_PATTERN.find(sql) ?: return null
        val schema = headerMatch.groupValues[1].takeIf { it.isNotBlank() }
        val name = headerMatch.groupValues[2]

        val variables = mutableListOf<VariableInfo>()
        val functions = mutableListOf<FunctionDef>()
        val procedures = mutableListOf<ProcedureDef>()

        // 함수 정의 파싱
        FUNCTION_DEF_PATTERN.findAll(sql).forEach { match ->
            functions.add(FunctionDef(
                name = match.groupValues[1],
                parameters = match.groupValues[2],
                returnType = match.groupValues[3],
                body = match.groupValues[4].trim()
            ))
        }

        // 프로시저 정의 파싱
        PROCEDURE_DEF_PATTERN.findAll(sql).forEach { match ->
            procedures.add(ProcedureDef(
                name = match.groupValues[1],
                parameters = match.groupValues[2],
                body = match.groupValues[3].trim()
            ))
        }

        return PackageInfo(
            schema = schema,
            name = name,
            constants = emptyList(),
            variables = variables,
            types = emptyList(),
            functions = functions,
            procedures = procedures,
            functionDecls = emptyList(),
            procedureDecls = emptyList()
        )
    }

    /**
     * 파라미터 변환
     */
    private fun convertParameters(params: String, target: DialectType): String {
        if (params.isBlank()) return ""

        return params.split(",").joinToString(", ") { param ->
            val trimmed = param.trim()
            // p_name IN VARCHAR2 형식 파싱
            val parts = trimmed.split(Regex("\\s+"))

            when {
                parts.size >= 3 -> {
                    val name = parts[0]
                    val direction = parts[1].uppercase()
                    val type = parts.drop(2).joinToString(" ")

                    when (target) {
                        DialectType.MYSQL -> {
                            val mysqlDir = when (direction) {
                                "IN" -> "IN"
                                "OUT" -> "OUT"
                                "IN OUT", "INOUT" -> "INOUT"
                                else -> "IN"
                            }
                            "$mysqlDir $name ${convertDataType(type, target)}"
                        }
                        DialectType.POSTGRESQL -> {
                            val pgDir = when (direction) {
                                "IN" -> ""
                                "OUT" -> "OUT"
                                "IN OUT", "INOUT" -> "INOUT"
                                else -> ""
                            }
                            if (pgDir.isNotEmpty()) "$name $pgDir ${convertDataType(type, target)}"
                            else "$name ${convertDataType(type, target)}"
                        }
                        else -> trimmed
                    }
                }
                parts.size == 2 -> {
                    val name = parts[0]
                    val type = parts[1]
                    "$name ${convertDataType(type, target)}"
                }
                else -> trimmed
            }
        }
    }

    /**
     * 데이터 타입 변환
     */
    private fun convertDataType(type: String, target: DialectType): String {
        val upperType = type.uppercase().trim()

        return when (target) {
            DialectType.MYSQL -> when {
                upperType.startsWith("VARCHAR2") -> type.replace(Regex("VARCHAR2", RegexOption.IGNORE_CASE), "VARCHAR")
                upperType == "NUMBER" -> "DECIMAL"
                upperType.startsWith("NUMBER(") -> type.replace(Regex("NUMBER", RegexOption.IGNORE_CASE), "DECIMAL")
                upperType == "INTEGER" -> "INT"
                upperType == "BOOLEAN" -> "TINYINT(1)"
                upperType == "CLOB" -> "LONGTEXT"
                upperType == "BLOB" -> "LONGBLOB"
                upperType == "DATE" -> "DATETIME"
                else -> type
            }
            DialectType.POSTGRESQL -> when {
                upperType.startsWith("VARCHAR2") -> type.replace(Regex("VARCHAR2", RegexOption.IGNORE_CASE), "VARCHAR")
                upperType == "NUMBER" -> "NUMERIC"
                upperType.startsWith("NUMBER(") -> type.replace(Regex("NUMBER", RegexOption.IGNORE_CASE), "NUMERIC")
                upperType == "INTEGER" -> "INTEGER"
                upperType == "CLOB" -> "TEXT"
                upperType == "BLOB" -> "BYTEA"
                else -> type
            }
            else -> type
        }
    }

    /**
     * DECLARE와 실행 블록 분리
     */
    private fun extractDeclareAndExec(body: String): Pair<String, String> {
        val declarePattern = Regex("""(.+?)BEGIN\s+(.+)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val match = declarePattern.find(body)

        return if (match != null) {
            val declare = match.groupValues[1].trim()
            val exec = match.groupValues[2].trim()
            Pair(declare, exec)
        } else {
            Pair("", body)
        }
    }

    /**
     * DECLARE 블록 변환
     */
    private fun convertDeclareBlock(declare: String, target: DialectType): String {
        var result = declare

        // VARCHAR2 → VARCHAR
        result = result.replace(Regex("""VARCHAR2\s*\((\d+)\)""", RegexOption.IGNORE_CASE)) { match ->
            "VARCHAR(${match.groupValues[1]})"
        }

        // NUMBER → NUMERIC/DECIMAL
        val numericType = if (target == DialectType.MYSQL) "DECIMAL" else "NUMERIC"
        result = result.replace(Regex("""\bNUMBER\b""", RegexOption.IGNORE_CASE), numericType)

        return result
    }

    /**
     * 함수 본문 변환
     */
    private fun convertFunctionBody(body: String, packageName: String, target: DialectType): String {
        var result = body

        // 패키지 내 함수/프로시저 호출 변환
        result = convertPackageReferences(result, packageName, target)

        // 공통 변환
        result = convertCommonSyntax(result, target)

        return result
    }

    /**
     * 프로시저 본문 변환
     */
    private fun convertProcedureBody(body: String, packageName: String, target: DialectType): String {
        var result = body

        // 패키지 내 함수/프로시저 호출 변환
        result = convertPackageReferences(result, packageName, target)

        // 공통 변환
        result = convertCommonSyntax(result, target)

        return result
    }

    /**
     * 패키지 참조 변환
     */
    private fun convertPackageReferences(body: String, packageName: String, target: DialectType): String {
        var result = body

        // 같은 패키지 내 함수 호출: func_name() → package_func_name() 또는 schema.func_name()
        // 이 부분은 컨텍스트가 필요하여 간단히 처리

        return result
    }

    /**
     * 공통 문법 변환
     */
    private fun convertCommonSyntax(body: String, target: DialectType): String {
        var result = body

        // SYSDATE
        result = result.replace(Regex("""\bSYSDATE\b""", RegexOption.IGNORE_CASE)) {
            when (target) {
                DialectType.MYSQL -> "NOW()"
                DialectType.POSTGRESQL -> "CURRENT_TIMESTAMP"
                else -> it.value
            }
        }

        // NVL
        result = result.replace(Regex("""NVL\s*\(""", RegexOption.IGNORE_CASE)) {
            when (target) {
                DialectType.MYSQL -> "IFNULL("
                DialectType.POSTGRESQL -> "COALESCE("
                else -> it.value
            }
        }

        // || → CONCAT (MySQL)
        if (target == DialectType.MYSQL) {
            result = result.replace(Regex("""'([^']*?)'\s*\|\|\s*'([^']*?)'""")) { match ->
                "CONCAT('${match.groupValues[1]}', '${match.groupValues[2]}')"
            }
        }

        // DBMS_OUTPUT.PUT_LINE
        result = result.replace(Regex("""DBMS_OUTPUT\.PUT_LINE\s*\((.+?)\);""", RegexOption.IGNORE_CASE)) { match ->
            when (target) {
                DialectType.MYSQL -> "SELECT ${match.groupValues[1]} AS debug_output;"
                DialectType.POSTGRESQL -> "RAISE NOTICE '%', ${match.groupValues[1]};"
                else -> match.value
            }
        }

        return result
    }

    // ============ 데이터 클래스 ============

    data class PackageInfo(
        val schema: String?,
        val name: String,
        val constants: List<ConstantInfo>,
        val variables: List<VariableInfo>,
        val types: List<TypeInfo>,
        val functions: List<FunctionDef>,
        val procedures: List<ProcedureDef>,
        val functionDecls: List<FunctionDecl>,
        val procedureDecls: List<ProcedureDecl>
    )

    data class ConstantInfo(
        val name: String,
        val dataType: String,
        val defaultValue: String
    )

    data class VariableInfo(
        val name: String,
        val dataType: String,
        val defaultValue: String?
    )

    data class TypeInfo(
        val name: String,
        val definition: String
    )

    data class FunctionDecl(
        val name: String,
        val parameters: String,
        val returnType: String
    )

    data class ProcedureDecl(
        val name: String,
        val parameters: String
    )

    data class FunctionDef(
        val name: String,
        val parameters: String,
        val returnType: String,
        val body: String
    )

    data class ProcedureDef(
        val name: String,
        val parameters: String,
        val body: String
    )

    /**
     * 패키지 문인지 확인
     */
    fun isPackageStatement(sql: String): Boolean {
        return PACKAGE_SPEC_PATTERN.containsMatchIn(sql) ||
               PACKAGE_BODY_PATTERN.containsMatchIn(sql)
    }

    /**
     * 패키지 이름 추출
     */
    fun extractPackageName(sql: String): String? {
        return PACKAGE_SPEC_PATTERN.find(sql)?.groupValues?.get(2)
            ?: PACKAGE_BODY_PATTERN.find(sql)?.groupValues?.get(2)
    }

    /**
     * 패키지 타입 확인 (SPEC or BODY)
     */
    fun getPackageType(sql: String): PackageType? {
        return when {
            PACKAGE_BODY_PATTERN.containsMatchIn(sql) -> PackageType.BODY
            PACKAGE_SPEC_PATTERN.containsMatchIn(sql) -> PackageType.SPECIFICATION
            else -> null
        }
    }

    enum class PackageType {
        SPECIFICATION, BODY
    }
}
