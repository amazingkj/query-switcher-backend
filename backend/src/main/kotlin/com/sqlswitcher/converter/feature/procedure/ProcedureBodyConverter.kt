package com.sqlswitcher.converter.feature.procedure

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * 저장 프로시저/함수 본문 변환기
 *
 * Oracle PL/SQL 본문을 MySQL/PostgreSQL로 변환합니다.
 *
 * 지원 기능:
 * - SQL%ROWCOUNT, SQL%FOUND, SQL%NOTFOUND 변환
 * - AUTONOMOUS_TRANSACTION pragma
 * - PIPE ROW (Pipelined Function)
 * - RECORD 타입 선언
 * - Collection 타입 (TABLE OF, VARRAY)
 * - REF CURSOR
 * - RETURNING INTO
 * - GOTO 문
 * - EXIT WHEN / CONTINUE WHEN
 * - RAISE_APPLICATION_ERROR
 * - User-defined exceptions
 */
object ProcedureBodyConverter {

    // ============ SQL 속성 패턴 ============

    private val SQL_ROWCOUNT_PATTERN = Regex(
        """\bSQL%ROWCOUNT\b""",
        RegexOption.IGNORE_CASE
    )

    private val SQL_FOUND_PATTERN = Regex(
        """\bSQL%FOUND\b""",
        RegexOption.IGNORE_CASE
    )

    private val SQL_NOTFOUND_PATTERN = Regex(
        """\bSQL%NOTFOUND\b""",
        RegexOption.IGNORE_CASE
    )

    private val SQL_ISOPEN_PATTERN = Regex(
        """\bSQL%ISOPEN\b""",
        RegexOption.IGNORE_CASE
    )

    // ============ Pragma 패턴 ============

    private val AUTONOMOUS_TRANSACTION_PATTERN = Regex(
        """PRAGMA\s+AUTONOMOUS_TRANSACTION\s*;""",
        RegexOption.IGNORE_CASE
    )

    private val EXCEPTION_INIT_PATTERN = Regex(
        """PRAGMA\s+EXCEPTION_INIT\s*\(\s*(\w+)\s*,\s*(-?\d+)\s*\)\s*;""",
        RegexOption.IGNORE_CASE
    )

    // ============ Pipelined Function 패턴 ============

    private val PIPELINED_PATTERN = Regex(
        """\bPIPELINED\b""",
        RegexOption.IGNORE_CASE
    )

    private val PIPE_ROW_PATTERN = Regex(
        """PIPE\s+ROW\s*\(\s*(.+?)\s*\)\s*;""",
        RegexOption.IGNORE_CASE
    )

    // ============ Collection/Record 패턴 ============

    private val TABLE_OF_PATTERN = Regex(
        """TYPE\s+(\w+)\s+IS\s+TABLE\s+OF\s+(.+?)(?:\s+INDEX\s+BY\s+(\w+))?\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val VARRAY_PATTERN = Regex(
        """TYPE\s+(\w+)\s+IS\s+(?:VARRAY|VARYING\s+ARRAY)\s*\(\s*(\d+)\s*\)\s+OF\s+(.+?)\s*;""",
        RegexOption.IGNORE_CASE
    )

    private val RECORD_TYPE_PATTERN = Regex(
        """TYPE\s+(\w+)\s+IS\s+RECORD\s*\((.+?)\)\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // ============ REF CURSOR 패턴 ============

    private val REF_CURSOR_PATTERN = Regex(
        """TYPE\s+(\w+)\s+IS\s+REF\s+CURSOR(?:\s+RETURN\s+(.+?))?;""",
        RegexOption.IGNORE_CASE
    )

    private val SYS_REFCURSOR_PATTERN = Regex(
        """\bSYS_REFCURSOR\b""",
        RegexOption.IGNORE_CASE
    )

    // ============ RETURNING INTO 패턴 ============

    private val RETURNING_INTO_PATTERN = Regex(
        """RETURNING\s+(.+?)\s+INTO\s+(.+?);""",
        RegexOption.IGNORE_CASE
    )

    // ============ Control Flow 패턴 ============

    private val GOTO_PATTERN = Regex(
        """GOTO\s+(\w+)\s*;""",
        RegexOption.IGNORE_CASE
    )

    private val LABEL_PATTERN = Regex(
        """<<(\w+)>>""",
        RegexOption.IGNORE_CASE
    )

    private val EXIT_WHEN_PATTERN = Regex(
        """EXIT\s+WHEN\s+(.+?);""",
        RegexOption.IGNORE_CASE
    )

    private val CONTINUE_WHEN_PATTERN = Regex(
        """CONTINUE\s+WHEN\s+(.+?);""",
        RegexOption.IGNORE_CASE
    )

    // ============ Exception 패턴 ============

    private val RAISE_APPLICATION_ERROR_PATTERN = Regex(
        """RAISE_APPLICATION_ERROR\s*\(\s*(-?\d+)\s*,\s*(.+?)\s*(?:,\s*(TRUE|FALSE))?\s*\)\s*;""",
        RegexOption.IGNORE_CASE
    )

    private val USER_EXCEPTION_PATTERN = Regex(
        """(\w+)\s+EXCEPTION\s*;""",
        RegexOption.IGNORE_CASE
    )

    private val RAISE_EXCEPTION_PATTERN = Regex(
        """RAISE\s+(\w+)\s*;""",
        RegexOption.IGNORE_CASE
    )

    // ============ DML 패턴 ============

    private val MERGE_PATTERN = Regex(
        """\bMERGE\s+INTO\b""",
        RegexOption.IGNORE_CASE
    )

    private val SAVEPOINT_PATTERN = Regex(
        """SAVEPOINT\s+(\w+)\s*;""",
        RegexOption.IGNORE_CASE
    )

    private val ROLLBACK_TO_PATTERN = Regex(
        """ROLLBACK\s+TO\s+(?:SAVEPOINT\s+)?(\w+)\s*;""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 프로시저 본문 변환
     */
    fun convertBody(
        body: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return body

        return when (targetDialect) {
            DialectType.MYSQL -> convertToMySql(body, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertToPostgreSql(body, warnings, appliedRules)
            DialectType.ORACLE -> body
        }
    }

    // ============ PostgreSQL 변환 ============

    /**
     * Oracle → PostgreSQL 본문 변환
     */
    private fun convertToPostgreSql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // 1. SQL 속성 변환
        result = convertSqlAttributesForPostgreSql(result, appliedRules)

        // 2. AUTONOMOUS_TRANSACTION 처리
        result = convertAutonomousTransactionForPostgreSql(result, warnings, appliedRules)

        // 3. Pipelined Function 변환
        result = convertPipelinedForPostgreSql(result, warnings, appliedRules)

        // 4. Collection 타입 변환
        result = convertCollectionTypesForPostgreSql(result, warnings, appliedRules)

        // 5. REF CURSOR 변환
        result = convertRefCursorForPostgreSql(result, warnings, appliedRules)

        // 6. RETURNING INTO 변환
        result = convertReturningIntoForPostgreSql(result, appliedRules)

        // 7. Control Flow 변환
        result = convertControlFlowForPostgreSql(result, warnings, appliedRules)

        // 8. Exception 변환
        result = convertExceptionsForPostgreSql(result, warnings, appliedRules)

        // 9. Transaction 제어 변환
        result = convertTransactionControlForPostgreSql(result, appliedRules)

        // 10. PRAGMA EXCEPTION_INIT 처리
        result = handlePragmaExceptionInit(result, warnings, appliedRules)

        return result
    }

    /**
     * SQL 속성 → PostgreSQL 변환
     */
    private fun convertSqlAttributesForPostgreSql(
        body: String,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // SQL%ROWCOUNT → GET DIAGNOSTICS ... ROW_COUNT 또는 ROW_COUNT 함수
        if (SQL_ROWCOUNT_PATTERN.containsMatchIn(result)) {
            // 변수 대입인 경우: var := SQL%ROWCOUNT → GET DIAGNOSTICS var = ROW_COUNT
            result = result.replace(
                Regex("""(\w+)\s*:=\s*SQL%ROWCOUNT\s*;""", RegexOption.IGNORE_CASE)
            ) { match ->
                "GET DIAGNOSTICS ${match.groupValues[1]} = ROW_COUNT;"
            }
            // 조건문에서 사용: IF SQL%ROWCOUNT > 0 → 변수로 저장 후 비교
            result = SQL_ROWCOUNT_PATTERN.replace(result, "/* SQL%ROWCOUNT - use GET DIAGNOSTICS */ 0")
            appliedRules.add("SQL%ROWCOUNT → GET DIAGNOSTICS ROW_COUNT")
        }

        // SQL%FOUND → FOUND
        if (SQL_FOUND_PATTERN.containsMatchIn(result)) {
            result = SQL_FOUND_PATTERN.replace(result, "FOUND")
            appliedRules.add("SQL%FOUND → FOUND")
        }

        // SQL%NOTFOUND → NOT FOUND
        if (SQL_NOTFOUND_PATTERN.containsMatchIn(result)) {
            result = SQL_NOTFOUND_PATTERN.replace(result, "NOT FOUND")
            appliedRules.add("SQL%NOTFOUND → NOT FOUND")
        }

        // SQL%ISOPEN → 항상 FALSE (implicit cursor)
        if (SQL_ISOPEN_PATTERN.containsMatchIn(result)) {
            result = SQL_ISOPEN_PATTERN.replace(result, "FALSE /* SQL%ISOPEN */")
            appliedRules.add("SQL%ISOPEN → FALSE")
        }

        return result
    }

    /**
     * AUTONOMOUS_TRANSACTION → PostgreSQL 변환
     */
    private fun convertAutonomousTransactionForPostgreSql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!AUTONOMOUS_TRANSACTION_PATTERN.containsMatchIn(body)) return body

        warnings.add(ConversionWarning(
            type = WarningType.PARTIAL_SUPPORT,
            message = "PRAGMA AUTONOMOUS_TRANSACTION은 PostgreSQL에서 dblink 또는 pg_background를 사용하여 구현해야 합니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "PostgreSQL에서 자율 트랜잭션 구현:\n" +
                    "1. dblink 확장을 사용하여 별도 연결에서 실행\n" +
                    "2. pg_background 확장 사용\n" +
                    "3. 별도의 세션에서 작업 수행"
        ))

        appliedRules.add("PRAGMA AUTONOMOUS_TRANSACTION → 주석 처리")

        return AUTONOMOUS_TRANSACTION_PATTERN.replace(body) {
            "-- PRAGMA AUTONOMOUS_TRANSACTION (use dblink for autonomous transactions)"
        }
    }

    /**
     * Pipelined Function → PostgreSQL SETOF 변환
     */
    private fun convertPipelinedForPostgreSql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        if (PIPELINED_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "Oracle PIPELINED 함수는 PostgreSQL RETURNS SETOF / RETURNS TABLE로 변환됩니다.",
                severity = WarningSeverity.INFO,
                suggestion = "RETURN NEXT 또는 RETURN QUERY를 사용하세요."
            ))

            result = PIPELINED_PATTERN.replace(result, "/* PIPELINED */")
            appliedRules.add("PIPELINED → 주석 처리 (RETURNS SETOF 사용)")
        }

        // PIPE ROW → RETURN NEXT
        if (PIPE_ROW_PATTERN.containsMatchIn(result)) {
            result = PIPE_ROW_PATTERN.replace(result) { match ->
                val rowValue = match.groupValues[1]
                "RETURN NEXT $rowValue;"
            }
            appliedRules.add("PIPE ROW → RETURN NEXT")
        }

        return result
    }

    /**
     * Collection 타입 → PostgreSQL 배열 변환
     */
    private fun convertCollectionTypesForPostgreSql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // TABLE OF → ARRAY[]
        if (TABLE_OF_PATTERN.containsMatchIn(result)) {
            result = TABLE_OF_PATTERN.replace(result) { match ->
                val typeName = match.groupValues[1]
                val elementType = match.groupValues[2].trim()
                val indexBy = match.groupValues.getOrNull(3)

                val pgType = convertDataTypeForPostgreSql(elementType)

                if (indexBy != null) {
                    // Associative array → hstore 또는 jsonb
                    warnings.add(ConversionWarning(
                        type = WarningType.PARTIAL_SUPPORT,
                        message = "Oracle INDEX BY 컬렉션은 PostgreSQL에서 hstore 또는 jsonb로 구현할 수 있습니다.",
                        severity = WarningSeverity.WARNING
                    ))
                    "-- TYPE $typeName IS TABLE OF $elementType INDEX BY $indexBy\n" +
                            "$typeName ALIAS FOR \$1; -- Use hstore or jsonb"
                } else {
                    "$typeName $pgType[];"
                }
            }
            appliedRules.add("TABLE OF → 배열 타입 변환")
        }

        // VARRAY → PostgreSQL 배열
        if (VARRAY_PATTERN.containsMatchIn(result)) {
            result = VARRAY_PATTERN.replace(result) { match ->
                val typeName = match.groupValues[1]
                val size = match.groupValues[2]
                val elementType = match.groupValues[3].trim()

                val pgType = convertDataTypeForPostgreSql(elementType)

                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "VARRAY($size)는 PostgreSQL 배열로 변환됩니다. 크기 제한은 수동으로 CHECK 제약조건으로 구현하세요.",
                    severity = WarningSeverity.INFO
                ))

                "$typeName $pgType[]; -- Original: VARRAY($size)"
            }
            appliedRules.add("VARRAY → 배열 타입 변환")
        }

        // RECORD 타입 → PostgreSQL RECORD 또는 복합 타입
        if (RECORD_TYPE_PATTERN.containsMatchIn(result)) {
            result = RECORD_TYPE_PATTERN.replace(result) { match ->
                val typeName = match.groupValues[1]
                val fields = match.groupValues[2].trim()

                // 필드를 PostgreSQL 문법으로 변환
                val pgFields = convertRecordFieldsForPostgreSql(fields)

                "-- Oracle RECORD type\n" +
                        "CREATE TYPE $typeName AS ($pgFields);"
            }
            appliedRules.add("RECORD 타입 → PostgreSQL 복합 타입")
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "Oracle RECORD 타입이 PostgreSQL CREATE TYPE으로 변환되었습니다. 프로시저 외부에서 타입을 생성해야 합니다.",
                severity = WarningSeverity.WARNING
            ))
        }

        return result
    }

    /**
     * REF CURSOR → PostgreSQL refcursor 변환
     */
    private fun convertRefCursorForPostgreSql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // TYPE ... IS REF CURSOR → refcursor
        if (REF_CURSOR_PATTERN.containsMatchIn(result)) {
            result = REF_CURSOR_PATTERN.replace(result) { match ->
                val typeName = match.groupValues[1]
                val returnType = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }

                if (returnType != null) {
                    warnings.add(ConversionWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "PostgreSQL refcursor는 RETURN 타입을 지정하지 않습니다.",
                        severity = WarningSeverity.INFO
                    ))
                }

                "$typeName refcursor; -- Original: REF CURSOR${returnType?.let { " RETURN $it" } ?: ""}"
            }
            appliedRules.add("REF CURSOR → refcursor")
        }

        // SYS_REFCURSOR → refcursor
        if (SYS_REFCURSOR_PATTERN.containsMatchIn(result)) {
            result = SYS_REFCURSOR_PATTERN.replace(result, "refcursor")
            appliedRules.add("SYS_REFCURSOR → refcursor")
        }

        return result
    }

    /**
     * RETURNING INTO → PostgreSQL 변환
     */
    private fun convertReturningIntoForPostgreSql(
        body: String,
        appliedRules: MutableList<String>
    ): String {
        if (!RETURNING_INTO_PATTERN.containsMatchIn(body)) return body

        // PostgreSQL도 RETURNING INTO 지원 (문법 동일)
        appliedRules.add("RETURNING INTO 유지 (PostgreSQL 호환)")
        return body
    }

    /**
     * Control Flow → PostgreSQL 변환
     */
    private fun convertControlFlowForPostgreSql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // GOTO → PostgreSQL 지원하지만 권장되지 않음
        if (GOTO_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "PostgreSQL에서도 GOTO를 지원하지만, 구조화된 제어문 사용을 권장합니다.",
                severity = WarningSeverity.INFO
            ))
            appliedRules.add("GOTO 유지 (PostgreSQL 호환)")
        }

        // EXIT WHEN → PostgreSQL 동일
        // CONTINUE WHEN → PostgreSQL 동일
        // 라벨 <<label>> → PostgreSQL 동일

        return result
    }

    /**
     * Exception → PostgreSQL 변환
     */
    private fun convertExceptionsForPostgreSql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // RAISE_APPLICATION_ERROR → RAISE EXCEPTION
        if (RAISE_APPLICATION_ERROR_PATTERN.containsMatchIn(result)) {
            result = RAISE_APPLICATION_ERROR_PATTERN.replace(result) { match ->
                val errorCode = match.groupValues[1]
                val message = match.groupValues[2]
                "RAISE EXCEPTION '%', $message USING ERRCODE = '$errorCode';"
            }
            appliedRules.add("RAISE_APPLICATION_ERROR → RAISE EXCEPTION")
        }

        // 사용자 정의 예외 → PostgreSQL RAISE
        if (RAISE_EXCEPTION_PATTERN.containsMatchIn(result)) {
            result = RAISE_EXCEPTION_PATTERN.replace(result) { match ->
                val exceptionName = match.groupValues[1]
                // Oracle 표준 예외 매핑
                val pgException = when (exceptionName.uppercase()) {
                    "NO_DATA_FOUND" -> "RAISE EXCEPTION 'No data found' USING ERRCODE = 'P0002';"
                    "TOO_MANY_ROWS" -> "RAISE EXCEPTION 'Too many rows' USING ERRCODE = 'P0003';"
                    "DUP_VAL_ON_INDEX" -> "RAISE EXCEPTION 'Duplicate value on index' USING ERRCODE = '23505';"
                    "ZERO_DIVIDE" -> "RAISE EXCEPTION 'Division by zero' USING ERRCODE = '22012';"
                    else -> "RAISE EXCEPTION '$exceptionName' USING ERRCODE = 'P0001';"
                }
                pgException
            }
            appliedRules.add("RAISE exception_name → RAISE EXCEPTION")
        }

        return result
    }

    /**
     * Transaction Control → PostgreSQL 변환
     */
    private fun convertTransactionControlForPostgreSql(
        body: String,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // SAVEPOINT → PostgreSQL 동일
        // ROLLBACK TO → PostgreSQL 동일
        // 주요 차이는 프로시저 내에서 COMMIT/ROLLBACK 사용 불가 (대부분의 경우)

        return result
    }

    /**
     * PRAGMA EXCEPTION_INIT 처리
     */
    private fun handlePragmaExceptionInit(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!EXCEPTION_INIT_PATTERN.containsMatchIn(body)) return body

        warnings.add(ConversionWarning(
            type = WarningType.UNSUPPORTED_STATEMENT,
            message = "PRAGMA EXCEPTION_INIT은 PostgreSQL에서 지원되지 않습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "PostgreSQL에서는 ERRCODE를 직접 사용하세요. 예: WHEN SQLSTATE '23505' THEN ..."
        ))

        val result = EXCEPTION_INIT_PATTERN.replace(body) { match ->
            val exceptionName = match.groupValues[1]
            val errorCode = match.groupValues[2]
            "-- PRAGMA EXCEPTION_INIT($exceptionName, $errorCode) - Use SQLSTATE in EXCEPTION handler"
        }

        appliedRules.add("PRAGMA EXCEPTION_INIT → 주석 처리")
        return result
    }

    // ============ MySQL 변환 ============

    /**
     * Oracle → MySQL 본문 변환
     */
    private fun convertToMySql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // 1. SQL 속성 변환
        result = convertSqlAttributesForMySql(result, warnings, appliedRules)

        // 2. AUTONOMOUS_TRANSACTION 처리
        result = convertAutonomousTransactionForMySql(result, warnings, appliedRules)

        // 3. Pipelined Function 경고
        result = handlePipelinedForMySql(result, warnings, appliedRules)

        // 4. Collection 타입 경고
        result = handleCollectionTypesForMySql(result, warnings, appliedRules)

        // 5. REF CURSOR 경고
        result = handleRefCursorForMySql(result, warnings, appliedRules)

        // 6. RETURNING INTO 변환
        result = convertReturningIntoForMySql(result, warnings, appliedRules)

        // 7. Control Flow 변환
        result = convertControlFlowForMySql(result, warnings, appliedRules)

        // 8. Exception 변환
        result = convertExceptionsForMySql(result, warnings, appliedRules)

        // 9. Transaction 제어 변환
        result = convertTransactionControlForMySql(result, appliedRules)

        // 10. PRAGMA 처리
        result = handlePragmasForMySql(result, warnings, appliedRules)

        return result
    }

    /**
     * SQL 속성 → MySQL 변환
     */
    private fun convertSqlAttributesForMySql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // SQL%ROWCOUNT → ROW_COUNT()
        if (SQL_ROWCOUNT_PATTERN.containsMatchIn(result)) {
            result = SQL_ROWCOUNT_PATTERN.replace(result, "ROW_COUNT()")
            appliedRules.add("SQL%ROWCOUNT → ROW_COUNT()")
        }

        // SQL%FOUND → ROW_COUNT() > 0
        if (SQL_FOUND_PATTERN.containsMatchIn(result)) {
            result = SQL_FOUND_PATTERN.replace(result, "(ROW_COUNT() > 0)")
            appliedRules.add("SQL%FOUND → ROW_COUNT() > 0")
        }

        // SQL%NOTFOUND → ROW_COUNT() = 0
        if (SQL_NOTFOUND_PATTERN.containsMatchIn(result)) {
            result = SQL_NOTFOUND_PATTERN.replace(result, "(ROW_COUNT() = 0)")
            appliedRules.add("SQL%NOTFOUND → ROW_COUNT() = 0")
        }

        // SQL%ISOPEN → FALSE
        if (SQL_ISOPEN_PATTERN.containsMatchIn(result)) {
            result = SQL_ISOPEN_PATTERN.replace(result, "FALSE")
            appliedRules.add("SQL%ISOPEN → FALSE")
        }

        return result
    }

    /**
     * AUTONOMOUS_TRANSACTION → MySQL 경고
     */
    private fun convertAutonomousTransactionForMySql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!AUTONOMOUS_TRANSACTION_PATTERN.containsMatchIn(body)) return body

        warnings.add(ConversionWarning(
            type = WarningType.UNSUPPORTED_STATEMENT,
            message = "PRAGMA AUTONOMOUS_TRANSACTION은 MySQL에서 지원되지 않습니다.",
            severity = WarningSeverity.ERROR,
            suggestion = "MySQL에서는 별도의 연결을 사용하거나, 작업을 외부 프로그램으로 분리해야 합니다."
        ))

        appliedRules.add("PRAGMA AUTONOMOUS_TRANSACTION → 주석 처리 (MySQL 미지원)")

        return AUTONOMOUS_TRANSACTION_PATTERN.replace(body) {
            "-- PRAGMA AUTONOMOUS_TRANSACTION (not supported in MySQL)"
        }
    }

    /**
     * Pipelined Function → MySQL 경고
     */
    private fun handlePipelinedForMySql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        if (PIPELINED_PATTERN.containsMatchIn(result) || PIPE_ROW_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "Oracle PIPELINED 함수 및 PIPE ROW는 MySQL에서 지원되지 않습니다.",
                severity = WarningSeverity.ERROR,
                suggestion = "임시 테이블에 결과를 저장하거나, 커서를 사용하세요."
            ))

            result = PIPELINED_PATTERN.replace(result) { "/* PIPELINED - not supported */" }
            result = PIPE_ROW_PATTERN.replace(result) { match ->
                "-- PIPE ROW(${match.groupValues[1]}) - not supported in MySQL"
            }
            appliedRules.add("PIPELINED/PIPE ROW → 주석 처리 (MySQL 미지원)")
        }

        return result
    }

    /**
     * Collection 타입 → MySQL 경고
     */
    private fun handleCollectionTypesForMySql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        if (TABLE_OF_PATTERN.containsMatchIn(result) ||
            VARRAY_PATTERN.containsMatchIn(result) ||
            RECORD_TYPE_PATTERN.containsMatchIn(result)) {

            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "Oracle 컬렉션 타입(TABLE OF, VARRAY, RECORD)은 MySQL에서 지원되지 않습니다.",
                severity = WarningSeverity.ERROR,
                suggestion = "임시 테이블, JSON 타입, 또는 개별 변수를 사용하세요."
            ))

            result = TABLE_OF_PATTERN.replace(result) { match ->
                "-- ${match.value} - not supported in MySQL"
            }
            result = VARRAY_PATTERN.replace(result) { match ->
                "-- ${match.value} - not supported in MySQL"
            }
            result = RECORD_TYPE_PATTERN.replace(result) { match ->
                "-- ${match.value} - not supported in MySQL"
            }

            appliedRules.add("Collection 타입 → 주석 처리 (MySQL 미지원)")
        }

        return result
    }

    /**
     * REF CURSOR → MySQL 경고
     */
    private fun handleRefCursorForMySql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        if (REF_CURSOR_PATTERN.containsMatchIn(result) || SYS_REFCURSOR_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                type = WarningType.PARTIAL_SUPPORT,
                message = "Oracle REF CURSOR는 MySQL 커서로 부분 변환 가능합니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "DECLARE cursor CURSOR FOR ... 문법을 사용하세요."
            ))

            result = REF_CURSOR_PATTERN.replace(result) { match ->
                "-- ${match.value} - use MySQL CURSOR"
            }
            result = SYS_REFCURSOR_PATTERN.replace(result, "/* SYS_REFCURSOR */ CURSOR")

            appliedRules.add("REF CURSOR → 주석 처리 (MySQL CURSOR 권장)")
        }

        return result
    }

    /**
     * RETURNING INTO → MySQL 변환
     */
    private fun convertReturningIntoForMySql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!RETURNING_INTO_PATTERN.containsMatchIn(body)) return body

        warnings.add(ConversionWarning(
            type = WarningType.UNSUPPORTED_STATEMENT,
            message = "RETURNING INTO는 MySQL에서 지원되지 않습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "INSERT 후 LAST_INSERT_ID() 사용, 또는 별도 SELECT 수행"
        ))

        val result = RETURNING_INTO_PATTERN.replace(body) { match ->
            val columns = match.groupValues[1]
            val variables = match.groupValues[2]
            "-- RETURNING $columns INTO $variables\n" +
                    "-- Use SELECT $columns INTO $variables FROM ... or LAST_INSERT_ID()"
        }

        appliedRules.add("RETURNING INTO → 주석 처리 (MySQL 미지원)")
        return result
    }

    /**
     * Control Flow → MySQL 변환
     */
    private fun convertControlFlowForMySql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // GOTO → MySQL 미지원
        if (GOTO_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "GOTO는 MySQL에서 지원되지 않습니다.",
                severity = WarningSeverity.ERROR,
                suggestion = "LOOP, WHILE, REPEAT 등의 구조화된 제어문을 사용하세요."
            ))

            result = GOTO_PATTERN.replace(result) { match ->
                "-- GOTO ${match.groupValues[1]} - not supported in MySQL"
            }
            result = LABEL_PATTERN.replace(result) { match ->
                "-- <<${match.groupValues[1]}>> label"
            }

            appliedRules.add("GOTO → 주석 처리 (MySQL 미지원)")
        }

        // EXIT WHEN → LEAVE with IF
        if (EXIT_WHEN_PATTERN.containsMatchIn(result)) {
            result = EXIT_WHEN_PATTERN.replace(result) { match ->
                val condition = match.groupValues[1]
                "IF $condition THEN LEAVE; END IF;"
            }
            appliedRules.add("EXIT WHEN → IF ... THEN LEAVE")
        }

        // CONTINUE WHEN → ITERATE with IF
        if (CONTINUE_WHEN_PATTERN.containsMatchIn(result)) {
            result = CONTINUE_WHEN_PATTERN.replace(result) { match ->
                val condition = match.groupValues[1]
                "IF $condition THEN ITERATE; END IF;"
            }
            appliedRules.add("CONTINUE WHEN → IF ... THEN ITERATE")
        }

        return result
    }

    /**
     * Exception → MySQL 변환
     */
    private fun convertExceptionsForMySql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        // RAISE_APPLICATION_ERROR → SIGNAL SQLSTATE
        if (RAISE_APPLICATION_ERROR_PATTERN.containsMatchIn(result)) {
            result = RAISE_APPLICATION_ERROR_PATTERN.replace(result) { match ->
                val message = match.groupValues[2]
                "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = $message;"
            }
            appliedRules.add("RAISE_APPLICATION_ERROR → SIGNAL SQLSTATE")
        }

        // RAISE exception_name → SIGNAL
        if (RAISE_EXCEPTION_PATTERN.containsMatchIn(result)) {
            result = RAISE_EXCEPTION_PATTERN.replace(result) { match ->
                val exceptionName = match.groupValues[1]
                "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '$exceptionName';"
            }
            appliedRules.add("RAISE exception → SIGNAL SQLSTATE")
        }

        return result
    }

    /**
     * Transaction Control → MySQL 변환
     */
    private fun convertTransactionControlForMySql(
        body: String,
        appliedRules: MutableList<String>
    ): String {
        // SAVEPOINT, ROLLBACK TO → MySQL 동일
        return body
    }

    /**
     * PRAGMA 처리 → MySQL
     */
    private fun handlePragmasForMySql(
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = body

        if (EXCEPTION_INIT_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "PRAGMA EXCEPTION_INIT은 MySQL에서 지원되지 않습니다.",
                severity = WarningSeverity.WARNING
            ))

            result = EXCEPTION_INIT_PATTERN.replace(result) { match ->
                "-- ${match.value}"
            }
            appliedRules.add("PRAGMA EXCEPTION_INIT → 주석 처리")
        }

        return result
    }

    // ============ 유틸리티 함수 ============

    /**
     * 데이터타입 PostgreSQL 변환
     */
    private fun convertDataTypeForPostgreSql(oracleType: String): String {
        val upperType = oracleType.uppercase().trim()
        return when {
            upperType.startsWith("VARCHAR2") -> upperType.replace("VARCHAR2", "VARCHAR")
            upperType == "NUMBER" -> "NUMERIC"
            upperType.startsWith("NUMBER(") -> upperType.replace("NUMBER", "NUMERIC")
            upperType == "CLOB" -> "TEXT"
            upperType == "BLOB" -> "BYTEA"
            upperType == "DATE" -> "TIMESTAMP"
            upperType.contains("%TYPE") -> upperType // 그대로 유지
            upperType.contains("%ROWTYPE") -> upperType // 그대로 유지
            else -> oracleType
        }
    }

    /**
     * RECORD 필드 PostgreSQL 변환
     */
    private fun convertRecordFieldsForPostgreSql(fields: String): String {
        return fields.split(",").joinToString(", ") { field ->
            val parts = field.trim().split(Regex("""\s+"""), 2)
            if (parts.size == 2) {
                val fieldName = parts[0]
                val fieldType = convertDataTypeForPostgreSql(parts[1])
                "$fieldName $fieldType"
            } else {
                field.trim()
            }
        }
    }
}
