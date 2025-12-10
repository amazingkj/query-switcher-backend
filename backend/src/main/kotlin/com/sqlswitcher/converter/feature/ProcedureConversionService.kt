package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import org.springframework.stereotype.Service

/**
 * STORED PROCEDURE / FUNCTION 변환 서비스
 *
 * 데이터베이스별 프로시저 문법:
 * - Oracle: PL/SQL (CREATE PROCEDURE ... IS ... BEGIN ... EXCEPTION ... END)
 * - PostgreSQL: PL/pgSQL (CREATE FUNCTION ... AS $$ ... $$ LANGUAGE plpgsql)
 * - MySQL: CREATE PROCEDURE ... BEGIN ... END
 */
@Service
class ProcedureConversionService(
    private val dataTypeService: DataTypeConversionService,
    private val functionService: FunctionConversionService
) {

    /**
     * PROCEDURE/FUNCTION 변환
     */
    fun convertProcedure(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) return sql

        return when (sourceDialect) {
            DialectType.ORACLE -> convertOracleProcedure(sql, targetDialect, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertPostgreSqlFunction(sql, targetDialect, warnings, appliedRules)
            DialectType.MYSQL -> convertMySqlProcedure(sql, targetDialect, warnings, appliedRules)
        }
    }

    /**
     * Oracle PL/SQL 프로시저 변환
     */
    private fun convertOracleProcedure(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        when (targetDialect) {
            DialectType.POSTGRESQL -> {
                return convertOracleToPostgreSql(sql, warnings, appliedRules)
            }
            DialectType.MYSQL -> {
                return convertOracleToMySql(sql, warnings, appliedRules)
            }
            else -> return sql
        }
    }

    /**
     * Oracle → PostgreSQL 변환
     */
    private fun convertOracleToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // 1. CREATE [OR REPLACE] PROCEDURE → CREATE OR REPLACE FUNCTION
        val procPattern = Regex(
            """CREATE\s+(?:OR\s+REPLACE\s+)?PROCEDURE\s+(\w+)""",
            RegexOption.IGNORE_CASE
        )
        if (procPattern.containsMatchIn(result)) {
            result = procPattern.replace(result) { match ->
                "CREATE OR REPLACE FUNCTION ${match.groupValues[1]}"
            }
            appliedRules.add("PROCEDURE → FUNCTION 변환 (PostgreSQL)")
        }

        // 2. 파라미터 모드 변환 (param IN VARCHAR2 → param VARCHAR)
        result = convertOracleParameters(result, DialectType.POSTGRESQL, appliedRules)

        // 3. IS/AS → RETURNS VOID AS $$\nDECLARE
        val isAsPattern = Regex("""\)\s*(IS|AS)\s*""", RegexOption.IGNORE_CASE)
        if (isAsPattern.containsMatchIn(result)) {
            result = isAsPattern.replace(result, ")\nRETURNS VOID AS \$\$\nDECLARE\n")
            appliedRules.add("IS/AS → RETURNS VOID AS \$\$ DECLARE 변환")
        }

        // 4. END; → END; $$ LANGUAGE plpgsql;
        val endPattern = Regex("""END\s*;\s*$""", RegexOption.IGNORE_CASE)
        if (endPattern.containsMatchIn(result)) {
            result = endPattern.replace(result, "END;\n\$\$ LANGUAGE plpgsql;")
            appliedRules.add("END; → END; \$\$ LANGUAGE plpgsql; 추가")
        }

        // 5. 데이터타입 변환
        result = convertOracleDataTypesInProcedure(result, DialectType.POSTGRESQL, appliedRules)

        // 6. Oracle 특유 구문 변환
        result = convertOracleSyntaxToPostgreSql(result, warnings, appliedRules)

        warnings.add(ConversionWarning(
            WarningType.MANUAL_REVIEW_NEEDED,
            "PL/SQL → PL/pgSQL 변환이 수행되었습니다. 수동 검토가 필요합니다.",
            WarningSeverity.WARNING,
            "변환된 코드의 로직과 예외 처리를 검토하세요."
        ))

        return result
    }

    /**
     * Oracle → MySQL 변환
     */
    private fun convertOracleToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // 1. CREATE OR REPLACE → CREATE (MySQL은 OR REPLACE 미지원)
        result = result.replace(
            Regex("""CREATE\s+OR\s+REPLACE\s+PROCEDURE""", RegexOption.IGNORE_CASE),
            "CREATE PROCEDURE"
        )
        appliedRules.add("OR REPLACE 제거 (MySQL)")

        // 2. 파라미터 모드 변환
        result = convertOracleParameters(result, DialectType.MYSQL, appliedRules)

        // 3. IS/AS 블록 → BEGIN
        val isAsBlockPattern = Regex(
            """\)\s*(IS|AS)\s*([\s\S]*?)BEGIN""",
            RegexOption.IGNORE_CASE
        )
        val isAsMatch = isAsBlockPattern.find(result)
        if (isAsMatch != null) {
            val declarations = isAsMatch.groupValues[2].trim()
            if (declarations.isNotEmpty()) {
                // 변수 선언을 DECLARE로 변환
                val mysqlDeclarations = convertOracleDeclarationsToMySql(declarations)
                result = isAsBlockPattern.replace(result, ")\nBEGIN\n$mysqlDeclarations\n")
            } else {
                result = isAsBlockPattern.replace(result, ")\nBEGIN\n")
            }
            appliedRules.add("IS/AS 블록 → BEGIN 변환")
        }

        // 4. 데이터타입 변환
        result = convertOracleDataTypesInProcedure(result, DialectType.MYSQL, appliedRules)

        // 5. EXCEPTION 블록 → HANDLER
        result = convertOracleExceptionToMySqlHandler(result, warnings, appliedRules)

        // 6. Oracle 특유 구문 변환
        result = convertOracleSyntaxToMySql(result, warnings, appliedRules)

        warnings.add(ConversionWarning(
            WarningType.MANUAL_REVIEW_NEEDED,
            "PL/SQL → MySQL 프로시저 변환이 수행되었습니다. 수동 검토가 필요합니다.",
            WarningSeverity.WARNING,
            "MySQL은 EXCEPTION 블록, 패키지 변수 등을 지원하지 않습니다."
        ))

        return result
    }

    /**
     * PostgreSQL PL/pgSQL 함수 변환
     */
    private fun convertPostgreSqlFunction(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        when (targetDialect) {
            DialectType.ORACLE -> {
                var result = sql

                // $$ 블록 제거 및 AS → IS 변환
                result = result.replace(Regex("""\s*AS\s*\$\$""", RegexOption.IGNORE_CASE), " IS")
                result = result.replace(Regex("""\$\$\s*LANGUAGE\s+plpgsql\s*;?""", RegexOption.IGNORE_CASE), "")

                // FUNCTION → PROCEDURE (RETURNS VOID인 경우)
                if (result.uppercase().contains("RETURNS VOID")) {
                    result = result.replace(
                        Regex("""CREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION""", RegexOption.IGNORE_CASE),
                        "CREATE OR REPLACE PROCEDURE"
                    )
                    result = result.replace(Regex("""RETURNS\s+VOID\s*""", RegexOption.IGNORE_CASE), "")
                    appliedRules.add("FUNCTION RETURNS VOID → PROCEDURE 변환")
                }

                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "PL/pgSQL → PL/SQL 변환이 수행되었습니다.",
                    WarningSeverity.WARNING
                ))

                return result
            }
            DialectType.MYSQL -> {
                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "PostgreSQL 함수를 MySQL로 변환하려면 수동 작업이 필요합니다.",
                    WarningSeverity.WARNING,
                    "MySQL PROCEDURE/FUNCTION 문법으로 재작성하세요."
                ))
                appliedRules.add("PostgreSQL → MySQL 프로시저 변환 (수동 필요)")
                return "-- MySQL conversion requires manual work\n$sql"
            }
            else -> return sql
        }
    }

    /**
     * MySQL 프로시저 변환
     */
    private fun convertMySqlProcedure(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        when (targetDialect) {
            DialectType.ORACLE -> {
                var result = sql

                // CREATE PROCEDURE → CREATE OR REPLACE PROCEDURE
                result = result.replace(
                    Regex("""CREATE\s+PROCEDURE""", RegexOption.IGNORE_CASE),
                    "CREATE OR REPLACE PROCEDURE"
                )

                // BEGIN 앞에 IS 추가
                result = result.replace(
                    Regex("""\)\s*BEGIN""", RegexOption.IGNORE_CASE),
                    ")\nIS\nBEGIN"
                )

                // DECLARE → IS 블록으로 이동 (간단한 케이스)
                val declarePattern = Regex("""DECLARE\s+(.+?);""", RegexOption.IGNORE_CASE)
                result = declarePattern.replace(result) { match ->
                    val varDecl = match.groupValues[1]
                    "-- Variable: $varDecl (move to IS block)"
                }

                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "MySQL → PL/SQL 변환이 수행되었습니다. 변수 선언을 IS 블록으로 이동하세요.",
                    WarningSeverity.WARNING
                ))
                appliedRules.add("MySQL → Oracle 프로시저 변환")

                return result
            }
            DialectType.POSTGRESQL -> {
                var result = sql

                // CREATE PROCEDURE → CREATE OR REPLACE FUNCTION
                result = result.replace(
                    Regex("""CREATE\s+PROCEDURE""", RegexOption.IGNORE_CASE),
                    "CREATE OR REPLACE FUNCTION"
                )

                // ) BEGIN → ) RETURNS VOID AS $$ BEGIN
                result = result.replace(
                    Regex("""\)\s*BEGIN""", RegexOption.IGNORE_CASE),
                    ")\nRETURNS VOID AS \$\$\nBEGIN"
                )

                // END; → END; $$ LANGUAGE plpgsql;
                result = result.replace(
                    Regex("""END\s*;\s*$""", RegexOption.IGNORE_CASE),
                    "END;\n\$\$ LANGUAGE plpgsql;"
                )

                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "MySQL → PL/pgSQL 변환이 수행되었습니다.",
                    WarningSeverity.WARNING
                ))
                appliedRules.add("MySQL → PostgreSQL 프로시저 변환")

                return result
            }
            else -> return sql
        }
    }

    /**
     * Oracle 파라미터 변환 (param IN VARCHAR2 → IN param VARCHAR / param VARCHAR)
     */
    private fun convertOracleParameters(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // Oracle: param_name [IN|OUT|IN OUT] data_type
        // MySQL: [IN|OUT|INOUT] param_name data_type
        // PostgreSQL: param_name data_type (OUT은 RETURNS로)

        val paramPattern = Regex(
            """(\w+)\s+(IN\s+OUT|IN|OUT)?\s*(VARCHAR2|NUMBER|DATE|CLOB|BLOB|INTEGER|BOOLEAN)(\s*\(\s*\d+\s*(?:,\s*\d+)?\s*\))?""",
            RegexOption.IGNORE_CASE
        )

        when (targetDialect) {
            DialectType.MYSQL -> {
                result = paramPattern.replace(result) { match ->
                    val paramName = match.groupValues[1]
                    val mode = match.groupValues[2].ifEmpty { "IN" }.replace(Regex("\\s+"), "")
                        .replace("INOUT", "INOUT").replace("IN OUT", "INOUT")
                    val dataType = convertDataTypeForProcedure(match.groupValues[3], DialectType.MYSQL)
                    val size = match.groupValues[4]
                    "$mode $paramName $dataType$size"
                }
                appliedRules.add("Oracle 파라미터 → MySQL 파라미터 변환")
            }
            DialectType.POSTGRESQL -> {
                result = paramPattern.replace(result) { match ->
                    val paramName = match.groupValues[1]
                    val mode = match.groupValues[2].ifEmpty { "" }
                    val dataType = convertDataTypeForProcedure(match.groupValues[3], DialectType.POSTGRESQL)
                    val size = match.groupValues[4]
                    if (mode.uppercase().contains("OUT")) {
                        "$paramName OUT $dataType$size"
                    } else {
                        "$paramName $dataType$size"
                    }
                }
                appliedRules.add("Oracle 파라미터 → PostgreSQL 파라미터 변환")
            }
            else -> {}
        }

        return result
    }

    /**
     * 프로시저 내 데이터타입 변환
     */
    private fun convertDataTypeForProcedure(dataType: String, targetDialect: DialectType): String {
        val upperType = dataType.uppercase()
        return when (targetDialect) {
            DialectType.MYSQL -> when (upperType) {
                "VARCHAR2" -> "VARCHAR"
                "NUMBER" -> "DECIMAL"
                "CLOB" -> "LONGTEXT"
                "BLOB" -> "LONGBLOB"
                "BOOLEAN" -> "TINYINT(1)"
                else -> dataType
            }
            DialectType.POSTGRESQL -> when (upperType) {
                "VARCHAR2" -> "VARCHAR"
                "NUMBER" -> "NUMERIC"
                "CLOB" -> "TEXT"
                "BLOB" -> "BYTEA"
                "INTEGER" -> "INTEGER"
                else -> dataType
            }
            else -> dataType
        }
    }

    /**
     * Oracle 프로시저 내 데이터타입 변환
     */
    private fun convertOracleDataTypesInProcedure(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.MYSQL -> {
                result = result.replace(Regex("\\bVARCHAR2\\b", RegexOption.IGNORE_CASE), "VARCHAR")
                result = result.replace(Regex("\\bNUMBER\\b", RegexOption.IGNORE_CASE), "DECIMAL")
                result = result.replace(Regex("\\bCLOB\\b", RegexOption.IGNORE_CASE), "LONGTEXT")
            }
            DialectType.POSTGRESQL -> {
                result = result.replace(Regex("\\bVARCHAR2\\b", RegexOption.IGNORE_CASE), "VARCHAR")
                result = result.replace(Regex("\\bNUMBER\\b", RegexOption.IGNORE_CASE), "NUMERIC")
                result = result.replace(Regex("\\bCLOB\\b", RegexOption.IGNORE_CASE), "TEXT")
            }
            else -> {}
        }

        if (result != sql) {
            appliedRules.add("프로시저 내 데이터타입 변환")
        }

        return result
    }

    /**
     * Oracle 선언문을 MySQL DECLARE로 변환
     */
    private fun convertOracleDeclarationsToMySql(declarations: String): String {
        val lines = declarations.split(";").filter { it.isNotBlank() }
        return lines.joinToString(";\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.uppercase().startsWith("DECLARE")) {
                "DECLARE $trimmed"
            } else {
                trimmed
            }
        }
    }

    /**
     * Oracle EXCEPTION 블록 → MySQL HANDLER 변환
     */
    private fun convertOracleExceptionToMySqlHandler(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // EXCEPTION 블록 감지
        if (result.uppercase().contains("EXCEPTION")) {
            warnings.add(ConversionWarning(
                WarningType.PARTIAL_SUPPORT,
                "Oracle EXCEPTION 블록이 감지되었습니다. MySQL HANDLER로 수동 변환이 필요합니다.",
                WarningSeverity.WARNING,
                "DECLARE CONTINUE HANDLER FOR SQLEXCEPTION ... 문법을 사용하세요."
            ))

            // WHEN OTHERS THEN → 주석 처리
            result = result.replace(
                Regex("""EXCEPTION\s+WHEN\s+OTHERS\s+THEN""", RegexOption.IGNORE_CASE),
                "-- EXCEPTION WHEN OTHERS THEN (Convert to DECLARE HANDLER)"
            )

            appliedRules.add("EXCEPTION 블록 감지 - 수동 HANDLER 변환 필요")
        }

        return result
    }

    /**
     * Oracle 특유 구문 → PostgreSQL 변환
     */
    private fun convertOracleSyntaxToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // := 대입연산자는 PostgreSQL에서도 동일
        // NVL → COALESCE
        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")

        // DBMS_OUTPUT.PUT_LINE → RAISE NOTICE
        if (result.uppercase().contains("DBMS_OUTPUT")) {
            result = result.replace(
                Regex("""DBMS_OUTPUT\.PUT_LINE\s*\(\s*(.+?)\s*\)\s*;""", RegexOption.IGNORE_CASE)
            ) { match ->
                "RAISE NOTICE '%', ${match.groupValues[1]};"
            }
            appliedRules.add("DBMS_OUTPUT.PUT_LINE → RAISE NOTICE")
        }

        return result
    }

    /**
     * Oracle 특유 구문 → MySQL 변환
     */
    private fun convertOracleSyntaxToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // := 대입연산자 → SET ... = (일부 케이스)
        // NVL → IFNULL
        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "IFNULL(")

        // DBMS_OUTPUT.PUT_LINE → SELECT (디버깅용)
        if (result.uppercase().contains("DBMS_OUTPUT")) {
            warnings.add(ConversionWarning(
                WarningType.UNSUPPORTED_FUNCTION,
                "DBMS_OUTPUT.PUT_LINE은 MySQL에서 지원하지 않습니다.",
                WarningSeverity.WARNING,
                "SELECT 문 또는 사용자 정의 로깅 테이블을 사용하세요."
            ))
            result = result.replace(
                Regex("""DBMS_OUTPUT\.PUT_LINE\s*\(\s*(.+?)\s*\)\s*;""", RegexOption.IGNORE_CASE)
            ) { match ->
                "SELECT ${match.groupValues[1]}; -- DEBUG OUTPUT"
            }
            appliedRules.add("DBMS_OUTPUT.PUT_LINE → SELECT (디버깅)")
        }

        return result
    }
}