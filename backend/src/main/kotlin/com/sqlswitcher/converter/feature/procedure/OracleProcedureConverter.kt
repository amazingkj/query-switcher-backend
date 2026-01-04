package com.sqlswitcher.converter.feature.procedure

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity

/**
 * Oracle PL/SQL 프로시저/함수 변환
 *
 * 지원 기능:
 * - CREATE PROCEDURE / CREATE FUNCTION
 * - CURSOR 선언 및 FOR LOOP
 * - %TYPE, %ROWTYPE 변환
 * - EXCEPTION 블록 처리
 * - BULK COLLECT / FORALL 경고
 * - RETURN 문 처리
 */
object OracleProcedureConverter {

    // 패턴 정의
    private val CURSOR_PATTERN = Regex(
        """CURSOR\s+(\w+)\s+IS\s+(.+?);""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val FOR_CURSOR_LOOP_PATTERN = Regex(
        """FOR\s+(\w+)\s+IN\s+(\w+)\s+LOOP""",
        RegexOption.IGNORE_CASE
    )

    private val TYPE_REFERENCE_PATTERN = Regex(
        """(\w+)\.(\w+)%TYPE""",
        RegexOption.IGNORE_CASE
    )

    private val ROWTYPE_REFERENCE_PATTERN = Regex(
        """(\w+)%ROWTYPE""",
        RegexOption.IGNORE_CASE
    )

    private val BULK_COLLECT_PATTERN = Regex(
        """BULK\s+COLLECT\s+INTO""",
        RegexOption.IGNORE_CASE
    )

    private val FORALL_PATTERN = Regex(
        """FORALL\s+\w+\s+IN""",
        RegexOption.IGNORE_CASE
    )

    private val RETURN_VALUE_PATTERN = Regex(
        """RETURN\s+(\w+);""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Oracle → PostgreSQL 변환
     */
    fun convertToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // 0. FUNCTION 변환 확인
        val funcPattern = Regex(
            """CREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION\s+(\w+)(.+?)RETURN\s+(\w+)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val isFunction = funcPattern.containsMatchIn(result)

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

        // 1.5 FUNCTION RETURN 타입 처리
        if (isFunction) {
            result = result.replace(
                Regex("""RETURN\s+(\w+)\s+(IS|AS)""", RegexOption.IGNORE_CASE)
            ) { match ->
                val returnType = ProcedureConversionUtils.convertDataTypeForProcedure(
                    match.groupValues[1], DialectType.POSTGRESQL
                )
                "RETURNS $returnType AS"
            }
            appliedRules.add("FUNCTION RETURN 타입 변환")
        }

        // 2. 파라미터 모드 변환 (param IN VARCHAR2 → param VARCHAR)
        result = ProcedureConversionUtils.convertOracleParameters(result, DialectType.POSTGRESQL, appliedRules)

        // 3. IS/AS → RETURNS VOID AS $$\nDECLARE
        val isAsPattern = Regex("""\)\s*(IS|AS)\s*""", RegexOption.IGNORE_CASE)
        if (isAsPattern.containsMatchIn(result)) {
            result = isAsPattern.replace(result) { ")\nRETURNS VOID AS \$\$\nDECLARE\n" }
            appliedRules.add("IS/AS → RETURNS VOID AS \$\$ DECLARE 변환")
        }

        // 4. END; → END; $$ LANGUAGE plpgsql;
        val endPattern = Regex("""END\s*;\s*$""", RegexOption.IGNORE_CASE)
        if (endPattern.containsMatchIn(result)) {
            result = endPattern.replace(result) { "END;\n\$\$ LANGUAGE plpgsql;" }
            appliedRules.add("END; → END; \$\$ LANGUAGE plpgsql; 추가")
        }

        // 5. 데이터타입 변환
        result = ProcedureConversionUtils.convertOracleDataTypesInProcedure(result, DialectType.POSTGRESQL, appliedRules)

        // 6. Oracle 특유 구문 변환
        result = convertSyntaxToPostgreSql(result, warnings, appliedRules)

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
    fun convertToMySql(
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
        result = ProcedureConversionUtils.convertOracleParameters(result, DialectType.MYSQL, appliedRules)

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
                val mysqlDeclarations = ProcedureConversionUtils.convertOracleDeclarationsToMySql(declarations)
                result = isAsBlockPattern.replace(result, ")\nBEGIN\n$mysqlDeclarations\n")
            } else {
                result = isAsBlockPattern.replace(result, ")\nBEGIN\n")
            }
            appliedRules.add("IS/AS 블록 → BEGIN 변환")
        }

        // 4. 데이터타입 변환
        result = ProcedureConversionUtils.convertOracleDataTypesInProcedure(result, DialectType.MYSQL, appliedRules)

        // 5. EXCEPTION 블록 → HANDLER
        result = convertExceptionToMySqlHandler(result, warnings, appliedRules)

        // 6. Oracle 특유 구문 변환
        result = convertSyntaxToMySql(result, warnings, appliedRules)

        warnings.add(ConversionWarning(
            WarningType.MANUAL_REVIEW_NEEDED,
            "PL/SQL → MySQL 프로시저 변환이 수행되었습니다. 수동 검토가 필요합니다.",
            WarningSeverity.WARNING,
            "MySQL은 EXCEPTION 블록, 패키지 변수 등을 지원하지 않습니다."
        ))

        return result
    }

    /**
     * Oracle 특유 구문 → PostgreSQL 변환 (강화됨)
     */
    private fun convertSyntaxToPostgreSql(
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

        // %TYPE 참조 변환 (PostgreSQL도 지원하지만 문법이 약간 다름)
        if (TYPE_REFERENCE_PATTERN.containsMatchIn(result)) {
            // PostgreSQL: table.column%TYPE 그대로 사용 가능
            warnings.add(ConversionWarning(
                WarningType.SYNTAX_DIFFERENCE,
                "%TYPE 참조가 사용되었습니다. PostgreSQL에서도 지원됩니다.",
                WarningSeverity.INFO,
                "테이블/컬럼이 존재하는지 확인하세요."
            ))
        }

        // %ROWTYPE 변환
        if (ROWTYPE_REFERENCE_PATTERN.containsMatchIn(result)) {
            // PostgreSQL: table%ROWTYPE 그대로 사용 가능
            warnings.add(ConversionWarning(
                WarningType.SYNTAX_DIFFERENCE,
                "%ROWTYPE 참조가 사용되었습니다. PostgreSQL에서도 지원됩니다.",
                WarningSeverity.INFO
            ))
        }

        // CURSOR FOR LOOP → PostgreSQL 스타일
        if (FOR_CURSOR_LOOP_PATTERN.containsMatchIn(result)) {
            // PostgreSQL에서도 동일한 문법 지원
            appliedRules.add("CURSOR FOR LOOP 유지 (PostgreSQL 호환)")
        }

        // BULK COLLECT 경고
        if (BULK_COLLECT_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                WarningType.PARTIAL_SUPPORT,
                "BULK COLLECT는 PostgreSQL에서 배열로 변환해야 합니다.",
                WarningSeverity.WARNING,
                "SELECT ... INTO array_var 또는 ARRAY_AGG() 사용을 고려하세요."
            ))
            // BULK COLLECT INTO → INTO (배열)
            result = result.replace(
                Regex("""BULK\s+COLLECT\s+INTO""", RegexOption.IGNORE_CASE),
                "INTO"
            )
            appliedRules.add("BULK COLLECT INTO → INTO 변환 (배열 확인 필요)")
        }

        // FORALL 경고
        if (FORALL_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                WarningType.UNSUPPORTED_FUNCTION,
                "FORALL은 PostgreSQL에서 지원되지 않습니다.",
                WarningSeverity.WARNING,
                "일반 FOR LOOP 또는 INSERT ... SELECT로 대체하세요."
            ))
        }

        // EXECUTE IMMEDIATE → EXECUTE
        result = result.replace(
            Regex("""EXECUTE\s+IMMEDIATE\s+""", RegexOption.IGNORE_CASE),
            "EXECUTE "
        )
        if (sql.uppercase().contains("EXECUTE IMMEDIATE")) {
            appliedRules.add("EXECUTE IMMEDIATE → EXECUTE")
        }

        // SYSDATE → CURRENT_TIMESTAMP
        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")

        // SYSTIMESTAMP → CURRENT_TIMESTAMP
        result = result.replace(Regex("\\bSYSTIMESTAMP\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")

        return result
    }

    /**
     * Oracle 특유 구문 → MySQL 변환 (강화됨)
     */
    private fun convertSyntaxToMySql(
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

        // %TYPE 참조 → 직접 타입 지정 필요
        if (TYPE_REFERENCE_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                WarningType.UNSUPPORTED_FUNCTION,
                "%TYPE 참조는 MySQL에서 지원되지 않습니다.",
                WarningSeverity.WARNING,
                "직접 데이터 타입을 지정하세요 (예: VARCHAR(100), INT 등)."
            ))
            // %TYPE을 주석으로 변환
            result = TYPE_REFERENCE_PATTERN.replace(result) { match ->
                "/* ${match.value} */ VARCHAR(255)"
            }
            appliedRules.add("%TYPE → 주석 + 기본타입 변환")
        }

        // %ROWTYPE → RECORD 미지원
        if (ROWTYPE_REFERENCE_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                WarningType.UNSUPPORTED_FUNCTION,
                "%ROWTYPE은 MySQL에서 지원되지 않습니다.",
                WarningSeverity.ERROR,
                "개별 변수로 분리하거나 임시 테이블을 사용하세요."
            ))
        }

        // CURSOR FOR LOOP → MySQL CURSOR + LOOP
        if (FOR_CURSOR_LOOP_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                WarningType.SYNTAX_DIFFERENCE,
                "Oracle FOR ... IN cursor LOOP는 MySQL에서 다르게 구현해야 합니다.",
                WarningSeverity.WARNING,
                "DECLARE CURSOR, OPEN, FETCH, CLOSE 패턴으로 변환하세요."
            ))
            appliedRules.add("CURSOR FOR LOOP 수동 변환 필요")
        }

        // BULK COLLECT 미지원
        if (BULK_COLLECT_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                WarningType.UNSUPPORTED_FUNCTION,
                "BULK COLLECT는 MySQL에서 지원되지 않습니다.",
                WarningSeverity.ERROR,
                "일반 SELECT INTO 또는 커서를 사용하세요."
            ))
        }

        // FORALL 미지원
        if (FORALL_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                WarningType.UNSUPPORTED_FUNCTION,
                "FORALL은 MySQL에서 지원되지 않습니다.",
                WarningSeverity.ERROR,
                "일반 LOOP 또는 INSERT ... SELECT로 대체하세요."
            ))
        }

        // EXECUTE IMMEDIATE → PREPARE + EXECUTE
        if (sql.uppercase().contains("EXECUTE IMMEDIATE")) {
            warnings.add(ConversionWarning(
                WarningType.SYNTAX_DIFFERENCE,
                "EXECUTE IMMEDIATE는 MySQL PREPARE/EXECUTE로 변환해야 합니다.",
                WarningSeverity.WARNING,
                "PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt; 패턴을 사용하세요."
            ))
            result = result.replace(
                Regex("""EXECUTE\s+IMMEDIATE\s+(.+?);""", RegexOption.IGNORE_CASE)
            ) { match ->
                val sqlVar = match.groupValues[1]
                "SET @dynamic_sql = $sqlVar;\nPREPARE stmt FROM @dynamic_sql;\nEXECUTE stmt;\nDEALLOCATE PREPARE stmt;"
            }
            appliedRules.add("EXECUTE IMMEDIATE → PREPARE/EXECUTE")
        }

        // SYSDATE → NOW()
        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "NOW()")

        // SYSTIMESTAMP → NOW(6)
        result = result.replace(Regex("\\bSYSTIMESTAMP\\b", RegexOption.IGNORE_CASE), "NOW(6)")

        // DECODE → CASE WHEN
        val decodePattern = Regex(
            """DECODE\s*\(\s*(\w+)\s*,\s*(.+?)\s*\)""",
            RegexOption.IGNORE_CASE
        )
        if (decodePattern.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                WarningType.SYNTAX_DIFFERENCE,
                "DECODE는 CASE WHEN으로 변환이 필요합니다.",
                WarningSeverity.INFO
            ))
        }

        return result
    }

    /**
     * Oracle EXCEPTION 블록 → MySQL HANDLER 변환
     */
    private fun convertExceptionToMySqlHandler(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // EXCEPTION 블록이 없으면 그대로 반환
        if (!result.uppercase().contains("EXCEPTION")) {
            return result
        }

        // EXCEPTION 블록 전체 추출 및 변환
        val exceptionBlockPattern = Regex(
            """EXCEPTION\s+(.*?)(?=END\s*;)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val exceptionMatch = exceptionBlockPattern.find(result)
        if (exceptionMatch != null) {
            val exceptionBlock = exceptionMatch.groupValues[1]
            val handlers = parseExceptionHandlers(exceptionBlock)

            if (handlers.isNotEmpty()) {
                // HANDLER 선언을 BEGIN 바로 뒤에 삽입
                val handlerDeclarations = buildMySqlHandlers(handlers)

                // BEGIN 뒤에 HANDLER 선언 삽입
                result = result.replaceFirst(
                    Regex("""BEGIN\s*\n""", RegexOption.IGNORE_CASE),
                    "BEGIN\n$handlerDeclarations\n"
                )

                // EXCEPTION 블록 제거
                result = exceptionBlockPattern.replace(result, "")

                appliedRules.add("EXCEPTION 블록 → DECLARE HANDLER 자동 변환")
                warnings.add(ConversionWarning(
                    WarningType.SYNTAX_DIFFERENCE,
                    "Oracle EXCEPTION 블록이 MySQL HANDLER로 변환되었습니다.",
                    WarningSeverity.WARNING,
                    "변환된 HANDLER 로직을 검토하세요. MySQL은 CONTINUE/EXIT HANDLER를 지원합니다."
                ))
            } else {
                // 파싱 실패 시 가이드 제공
                result = result.replace(
                    Regex("""EXCEPTION\s+WHEN\s+OTHERS\s+THEN""", RegexOption.IGNORE_CASE),
                    "-- EXCEPTION WHEN OTHERS THEN (Convert to DECLARE HANDLER)"
                )
                appliedRules.add("EXCEPTION 블록 감지 - 수동 HANDLER 변환 필요")
                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "복잡한 EXCEPTION 블록입니다. MySQL HANDLER로 수동 변환이 필요합니다.",
                    WarningSeverity.WARNING,
                    "DECLARE CONTINUE HANDLER FOR SQLEXCEPTION ... 문법을 사용하세요."
                ))
            }
        }

        return result
    }

    /**
     * EXCEPTION 블록에서 핸들러 파싱
     */
    private fun parseExceptionHandlers(exceptionBlock: String): List<ExceptionHandler> {
        val handlers = mutableListOf<ExceptionHandler>()

        // WHEN ... THEN 패턴 파싱
        val whenPattern = Regex(
            """WHEN\s+(\w+)\s+THEN\s+(.*?)(?=WHEN|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        whenPattern.findAll(exceptionBlock).forEach { match ->
            val exceptionName = match.groupValues[1].uppercase()
            val handlerBody = match.groupValues[2].trim()

            handlers.add(ExceptionHandler(
                oracleException = exceptionName,
                mysqlCondition = mapOracleExceptionToMySql(exceptionName),
                body = convertHandlerBody(handlerBody)
            ))
        }

        return handlers
    }

    /**
     * Oracle 예외명 → MySQL 조건 매핑
     */
    private fun mapOracleExceptionToMySql(oracleException: String): String {
        return when (oracleException) {
            "OTHERS" -> "SQLEXCEPTION"
            "NO_DATA_FOUND" -> "NOT FOUND"
            "DUP_VAL_ON_INDEX" -> "1062" // Duplicate entry
            "TOO_MANY_ROWS" -> "SQLEXCEPTION"
            "ZERO_DIVIDE" -> "1365" // Division by zero
            "INVALID_CURSOR" -> "SQLEXCEPTION"
            "CURSOR_ALREADY_OPEN" -> "SQLEXCEPTION"
            "VALUE_ERROR" -> "SQLEXCEPTION"
            "INVALID_NUMBER" -> "1366" // Incorrect value
            else -> "SQLEXCEPTION"
        }
    }

    /**
     * 핸들러 본문 변환
     */
    private fun convertHandlerBody(body: String): String {
        var result = body.trim()

        // NULL; → BEGIN END
        if (result.uppercase() == "NULL;" || result.uppercase() == "NULL") {
            return "BEGIN END"
        }

        // RAISE; → RESIGNAL
        result = result.replace(Regex("\\bRAISE\\s*;", RegexOption.IGNORE_CASE), "RESIGNAL;")

        // RAISE_APPLICATION_ERROR → SIGNAL
        result = result.replace(
            Regex("""RAISE_APPLICATION_ERROR\s*\(\s*(-?\d+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)
        ) { match ->
            val errorCode = match.groupValues[1]
            val message = match.groupValues[2]
            "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '$message'"
        }

        // DBMS_OUTPUT.PUT_LINE → SELECT (debug)
        result = result.replace(
            Regex("""DBMS_OUTPUT\.PUT_LINE\s*\(\s*(.+?)\s*\)\s*;""", RegexOption.IGNORE_CASE)
        ) { match ->
            "SELECT ${match.groupValues[1]}; -- DEBUG"
        }

        return "BEGIN $result END"
    }

    /**
     * MySQL HANDLER 선언문 생성
     */
    private fun buildMySqlHandlers(handlers: List<ExceptionHandler>): String {
        val sb = StringBuilder()
        sb.append("    -- Exception handlers (converted from Oracle EXCEPTION block)\n")

        handlers.forEach { handler ->
            val handlerType = if (handler.oracleException == "OTHERS") "EXIT" else "CONTINUE"
            sb.append("    DECLARE $handlerType HANDLER FOR ${handler.mysqlCondition}\n")
            sb.append("        ${handler.body};\n")
        }

        return sb.toString()
    }

    /**
     * 예외 핸들러 정보
     */
    private data class ExceptionHandler(
        val oracleException: String,
        val mysqlCondition: String,
        val body: String
    )
}