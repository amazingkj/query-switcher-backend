package com.sqlswitcher.converter.feature.dbms

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * Oracle DBMS_* 패키지 변환기
 *
 * Oracle의 내장 DBMS_* 패키지 호출을 MySQL/PostgreSQL의 동등한 기능으로 변환
 *
 * 지원하는 패키지:
 * - DBMS_OUTPUT: 디버그 출력
 * - DBMS_LOB: LOB 작업
 * - DBMS_RANDOM: 난수 생성
 * - DBMS_UTILITY: 유틸리티 함수
 * - DBMS_LOCK: 잠금 처리
 * - DBMS_CRYPTO: 암호화
 * - UTL_FILE: 파일 I/O
 * - UTL_HTTP: HTTP 호출
 * - DBMS_SQL: 동적 SQL
 * - DBMS_SCHEDULER: 작업 스케줄링
 */
object DbmsPackageConverter {

    /**
     * DBMS_OUTPUT.PUT_LINE 패턴
     */
    private val DBMS_OUTPUT_PUT_LINE = Regex(
        """DBMS_OUTPUT\.PUT_LINE\s*\(([^)]+)\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_OUTPUT.PUT 패턴
     */
    private val DBMS_OUTPUT_PUT = Regex(
        """DBMS_OUTPUT\.PUT\s*\(([^)]+)\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_RANDOM.VALUE 패턴 (범위 지정)
     */
    private val DBMS_RANDOM_VALUE_RANGE = Regex(
        """DBMS_RANDOM\.VALUE\s*\(\s*([^,]+)\s*,\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_RANDOM.VALUE 패턴 (범위 없음, 0~1)
     */
    private val DBMS_RANDOM_VALUE = Regex(
        """DBMS_RANDOM\.VALUE\b(?!\s*\()""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_RANDOM.STRING 패턴
     */
    private val DBMS_RANDOM_STRING = Regex(
        """DBMS_RANDOM\.STRING\s*\(\s*'([^']+)'\s*,\s*(\d+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_LOB.GETLENGTH 패턴
     */
    private val DBMS_LOB_GETLENGTH = Regex(
        """DBMS_LOB\.GETLENGTH\s*\(([^)]+)\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_LOB.SUBSTR 패턴
     */
    private val DBMS_LOB_SUBSTR = Regex(
        """DBMS_LOB\.SUBSTR\s*\(([^,]+),\s*(\d+)\s*(?:,\s*(\d+))?\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_LOB.INSTR 패턴
     */
    private val DBMS_LOB_INSTR = Regex(
        """DBMS_LOB\.INSTR\s*\(([^,]+),\s*([^,)]+)(?:,\s*(\d+))?\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_LOB.APPEND 패턴
     */
    private val DBMS_LOB_APPEND = Regex(
        """DBMS_LOB\.APPEND\s*\(([^,]+),\s*([^)]+)\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_UTILITY.GET_TIME 패턴
     */
    private val DBMS_UTILITY_GET_TIME = Regex(
        """DBMS_UTILITY\.GET_TIME\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_UTILITY.FORMAT_ERROR_BACKTRACE 패턴
     */
    private val DBMS_UTILITY_FORMAT_ERROR_BACKTRACE = Regex(
        """DBMS_UTILITY\.FORMAT_ERROR_BACKTRACE\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_UTILITY.FORMAT_ERROR_STACK 패턴
     */
    private val DBMS_UTILITY_FORMAT_ERROR_STACK = Regex(
        """DBMS_UTILITY\.FORMAT_ERROR_STACK\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_LOCK.SLEEP 패턴
     */
    private val DBMS_LOCK_SLEEP = Regex(
        """DBMS_LOCK\.SLEEP\s*\(([^)]+)\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_CRYPTO.HASH 패턴
     */
    private val DBMS_CRYPTO_HASH = Regex(
        """DBMS_CRYPTO\.HASH\s*\(\s*([^,]+)\s*,\s*DBMS_CRYPTO\.HASH_(MD5|SHA1|SHA256|SHA384|SHA512)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_CRYPTO.ENCRYPT 패턴
     */
    private val DBMS_CRYPTO_ENCRYPT = Regex(
        """DBMS_CRYPTO\.ENCRYPT\s*\(([^)]+)\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * UTL_FILE 호출 패턴
     */
    private val UTL_FILE_CALL = Regex(
        """UTL_FILE\.(\w+)\s*\([^)]*\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * UTL_HTTP 호출 패턴
     */
    private val UTL_HTTP_CALL = Regex(
        """UTL_HTTP\.(\w+)\s*\([^)]*\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_SQL 호출 패턴
     */
    private val DBMS_SQL_CALL = Regex(
        """DBMS_SQL\.(\w+)(?:\s*\([^)]*\))?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_SCHEDULER 호출 패턴
     */
    private val DBMS_SCHEDULER_CALL = Regex(
        """DBMS_SCHEDULER\.(\w+)\s*\([^)]*\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * RAISE_APPLICATION_ERROR 패턴
     */
    private val RAISE_APPLICATION_ERROR = Regex(
        """RAISE_APPLICATION_ERROR\s*\(\s*(-?\d+)\s*,\s*([^)]+)\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DBMS_* 패키지 변환
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) {
            return sql
        }

        var result = sql

        when (targetDialect) {
            DialectType.MYSQL -> {
                result = convertToMySql(result, warnings, appliedRules)
            }
            DialectType.POSTGRESQL -> {
                result = convertToPostgreSql(result, warnings, appliedRules)
            }
            else -> {
                // 다른 대상 방언은 변환하지 않음
            }
        }

        return result
    }

    /**
     * MySQL로 변환
     */
    private fun convertToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // DBMS_OUTPUT.PUT_LINE → SELECT (결과 출력용, 실제로는 로그로 대체)
        result = DBMS_OUTPUT_PUT_LINE.replace(result) { match ->
            val message = match.groupValues[1].trim()
            appliedRules.add("DBMS_OUTPUT.PUT_LINE → SELECT (MySQL에서는 결과 출력)")
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "DBMS_OUTPUT.PUT_LINE은 MySQL에서 직접 지원되지 않음",
                severity = WarningSeverity.WARNING,
                suggestion = "SELECT 문으로 대체되었습니다. 프로시저 내에서는 다른 로깅 방법이 필요할 수 있습니다."
            ))
            "SELECT $message AS debug_output"
        }

        result = DBMS_OUTPUT_PUT.replace(result) { match ->
            val message = match.groupValues[1].trim()
            appliedRules.add("DBMS_OUTPUT.PUT → SELECT")
            "SELECT $message AS debug_output"
        }

        // DBMS_RANDOM.VALUE(low, high) → RAND() * (high - low) + low
        result = DBMS_RANDOM_VALUE_RANGE.replace(result) { match ->
            val low = match.groupValues[1].trim()
            val high = match.groupValues[2].trim()
            appliedRules.add("DBMS_RANDOM.VALUE(low, high) → MySQL RAND() expression")
            "(RAND() * ($high - $low) + $low)"
        }

        // DBMS_RANDOM.VALUE → RAND()
        result = DBMS_RANDOM_VALUE.replace(result) {
            appliedRules.add("DBMS_RANDOM.VALUE → RAND()")
            "RAND()"
        }

        // DBMS_RANDOM.STRING → 사용자 정의 함수 필요
        result = DBMS_RANDOM_STRING.replace(result) { match ->
            val type = match.groupValues[1].uppercase()
            val length = match.groupValues[2]
            appliedRules.add("DBMS_RANDOM.STRING → MySQL 대체 표현식")
            warnings.add(ConversionWarning(
                type = WarningType.PARTIAL_SUPPORT,
                message = "DBMS_RANDOM.STRING은 MySQL에서 완전히 지원되지 않음",
                severity = WarningSeverity.WARNING,
                suggestion = "사용자 정의 함수를 생성하거나 대체 표현식을 사용하세요."
            ))
            when (type) {
                "U" -> "UPPER(SUBSTRING(MD5(RAND()) FROM 1 FOR $length))"
                "L" -> "LOWER(SUBSTRING(MD5(RAND()) FROM 1 FOR $length))"
                "A" -> "SUBSTRING(MD5(RAND()) FROM 1 FOR $length)"
                "X" -> "UPPER(SUBSTRING(MD5(RAND()) FROM 1 FOR $length))"
                else -> "SUBSTRING(MD5(RAND()) FROM 1 FOR $length)"
            }
        }

        // DBMS_LOB.GETLENGTH → LENGTH / OCTET_LENGTH
        result = DBMS_LOB_GETLENGTH.replace(result) { match ->
            val lobColumn = match.groupValues[1].trim()
            appliedRules.add("DBMS_LOB.GETLENGTH → LENGTH")
            "LENGTH($lobColumn)"
        }

        // DBMS_LOB.SUBSTR → SUBSTRING
        result = DBMS_LOB_SUBSTR.replace(result) { match ->
            val lobColumn = match.groupValues[1].trim()
            val length = match.groupValues[2]
            val offset = match.groupValues.getOrNull(3)?.ifEmpty { null } ?: "1"
            appliedRules.add("DBMS_LOB.SUBSTR → SUBSTRING")
            "SUBSTRING($lobColumn, $offset, $length)"
        }

        // DBMS_LOB.INSTR → LOCATE
        result = DBMS_LOB_INSTR.replace(result) { match ->
            val lobColumn = match.groupValues[1].trim()
            val pattern = match.groupValues[2].trim()
            val offset = match.groupValues.getOrNull(3)?.ifEmpty { null }
            appliedRules.add("DBMS_LOB.INSTR → LOCATE")
            if (offset != null) {
                "LOCATE($pattern, $lobColumn, $offset)"
            } else {
                "LOCATE($pattern, $lobColumn)"
            }
        }

        // DBMS_LOB.APPEND → CONCAT
        result = DBMS_LOB_APPEND.replace(result) { match ->
            val dest = match.groupValues[1].trim()
            val src = match.groupValues[2].trim()
            appliedRules.add("DBMS_LOB.APPEND → CONCAT")
            "SET $dest = CONCAT($dest, $src)"
        }

        // DBMS_UTILITY.GET_TIME → UNIX_TIMESTAMP() * 100
        result = DBMS_UTILITY_GET_TIME.replace(result) {
            appliedRules.add("DBMS_UTILITY.GET_TIME → UNIX_TIMESTAMP()")
            "(UNIX_TIMESTAMP() * 100)"
        }

        // DBMS_UTILITY.FORMAT_ERROR_BACKTRACE → MySQL 대체 없음
        result = DBMS_UTILITY_FORMAT_ERROR_BACKTRACE.replace(result) {
            appliedRules.add("DBMS_UTILITY.FORMAT_ERROR_BACKTRACE → 주석 처리")
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "DBMS_UTILITY.FORMAT_ERROR_BACKTRACE은 MySQL에서 지원되지 않음",
                severity = WarningSeverity.WARNING,
                suggestion = "MySQL의 GET DIAGNOSTICS를 사용하여 대체하세요."
            ))
            "NULL /* DBMS_UTILITY.FORMAT_ERROR_BACKTRACE not supported in MySQL */"
        }

        result = DBMS_UTILITY_FORMAT_ERROR_STACK.replace(result) {
            appliedRules.add("DBMS_UTILITY.FORMAT_ERROR_STACK → 주석 처리")
            "NULL /* DBMS_UTILITY.FORMAT_ERROR_STACK not supported in MySQL */"
        }

        // DBMS_LOCK.SLEEP → MySQL의 SLEEP
        result = DBMS_LOCK_SLEEP.replace(result) { match ->
            val seconds = match.groupValues[1].trim()
            appliedRules.add("DBMS_LOCK.SLEEP → SLEEP")
            "DO SLEEP($seconds)"
        }

        // DBMS_CRYPTO.HASH → MySQL 해시 함수
        result = DBMS_CRYPTO_HASH.replace(result) { match ->
            val data = match.groupValues[1].trim()
            val algorithm = match.groupValues[2].uppercase()
            appliedRules.add("DBMS_CRYPTO.HASH → MySQL 해시 함수")
            when (algorithm) {
                "MD5" -> "MD5($data)"
                "SHA1" -> "SHA1($data)"
                "SHA256" -> "SHA2($data, 256)"
                "SHA384" -> "SHA2($data, 384)"
                "SHA512" -> "SHA2($data, 512)"
                else -> "SHA2($data, 256)"
            }
        }

        // DBMS_CRYPTO.ENCRYPT → 경고
        if (DBMS_CRYPTO_ENCRYPT.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                type = WarningType.MANUAL_REVIEW_NEEDED,
                message = "DBMS_CRYPTO.ENCRYPT는 수동 변환이 필요합니다",
                severity = WarningSeverity.ERROR,
                suggestion = "MySQL의 AES_ENCRYPT 또는 애플리케이션 레벨 암호화를 사용하세요."
            ))
        }

        // UTL_FILE → 경고
        result = UTL_FILE_CALL.replace(result) { match ->
            val function = match.groupValues[1].uppercase()
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "UTL_FILE.${function}은 MySQL에서 지원되지 않음",
                severity = WarningSeverity.ERROR,
                suggestion = "파일 작업은 애플리케이션 레벨에서 처리하거나 LOAD DATA / SELECT INTO OUTFILE을 사용하세요."
            ))
            "NULL /* UTL_FILE.$function not supported in MySQL */"
        }

        // UTL_HTTP → 경고
        result = UTL_HTTP_CALL.replace(result) { match ->
            val function = match.groupValues[1].uppercase()
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "UTL_HTTP.${function}은 MySQL에서 지원되지 않음",
                severity = WarningSeverity.ERROR,
                suggestion = "HTTP 호출은 애플리케이션 레벨에서 처리하세요."
            ))
            "NULL /* UTL_HTTP.$function not supported in MySQL */"
        }

        // DBMS_SQL → 경고
        result = DBMS_SQL_CALL.replace(result) { match ->
            val function = match.groupValues[1].uppercase()
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "DBMS_SQL.${function}은 MySQL에서 지원되지 않음",
                severity = WarningSeverity.WARNING,
                suggestion = "MySQL의 PREPARE/EXECUTE 동적 SQL을 사용하세요."
            ))
            "NULL /* DBMS_SQL.$function - use PREPARE/EXECUTE instead */"
        }

        // DBMS_SCHEDULER → MySQL Event Scheduler
        result = DBMS_SCHEDULER_CALL.replace(result) { match ->
            val function = match.groupValues[1].uppercase()
            warnings.add(ConversionWarning(
                type = WarningType.PARTIAL_SUPPORT,
                message = "DBMS_SCHEDULER.${function}은 MySQL Event Scheduler로 대체 필요",
                severity = WarningSeverity.WARNING,
                suggestion = "MySQL의 CREATE EVENT 문법을 사용하여 스케줄링하세요."
            ))
            "NULL /* DBMS_SCHEDULER.$function - use MySQL Event Scheduler */"
        }

        // RAISE_APPLICATION_ERROR → SIGNAL SQLSTATE
        result = RAISE_APPLICATION_ERROR.replace(result) { match ->
            val errorCode = match.groupValues[1]
            val errorMessage = match.groupValues[2].trim()
            appliedRules.add("RAISE_APPLICATION_ERROR → SIGNAL SQLSTATE")
            "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = $errorMessage"
        }

        return result
    }

    /**
     * PostgreSQL로 변환
     */
    private fun convertToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // DBMS_OUTPUT.PUT_LINE → RAISE NOTICE
        result = DBMS_OUTPUT_PUT_LINE.replace(result) { match ->
            val message = match.groupValues[1].trim()
            appliedRules.add("DBMS_OUTPUT.PUT_LINE → RAISE NOTICE")
            // 문자열 리터럴인 경우 % 형식으로 변환
            if (message.startsWith("'") && message.endsWith("'")) {
                "RAISE NOTICE ${message.dropLast(1)}'%'"
            } else {
                "RAISE NOTICE '%', $message"
            }
        }

        result = DBMS_OUTPUT_PUT.replace(result) { match ->
            val message = match.groupValues[1].trim()
            appliedRules.add("DBMS_OUTPUT.PUT → RAISE NOTICE")
            "RAISE NOTICE '%', $message"
        }

        // DBMS_RANDOM.VALUE(low, high) → random() * (high - low) + low
        result = DBMS_RANDOM_VALUE_RANGE.replace(result) { match ->
            val low = match.groupValues[1].trim()
            val high = match.groupValues[2].trim()
            appliedRules.add("DBMS_RANDOM.VALUE(low, high) → PostgreSQL random() expression")
            "(random() * ($high - $low) + $low)"
        }

        // DBMS_RANDOM.VALUE → random()
        result = DBMS_RANDOM_VALUE.replace(result) {
            appliedRules.add("DBMS_RANDOM.VALUE → random()")
            "random()"
        }

        // DBMS_RANDOM.STRING → PostgreSQL 대체 함수
        result = DBMS_RANDOM_STRING.replace(result) { match ->
            val type = match.groupValues[1].uppercase()
            val length = match.groupValues[2]
            appliedRules.add("DBMS_RANDOM.STRING → PostgreSQL 대체 표현식")
            warnings.add(ConversionWarning(
                type = WarningType.PARTIAL_SUPPORT,
                message = "DBMS_RANDOM.STRING은 PostgreSQL에서 직접 지원되지 않음",
                severity = WarningSeverity.INFO,
                suggestion = "pgcrypto 확장의 gen_random_bytes를 사용하거나 사용자 정의 함수를 생성하세요."
            ))
            when (type) {
                "U" -> "UPPER(SUBSTRING(md5(random()::text) FROM 1 FOR $length))"
                "L" -> "LOWER(SUBSTRING(md5(random()::text) FROM 1 FOR $length))"
                "A" -> "SUBSTRING(md5(random()::text) FROM 1 FOR $length)"
                "X" -> "UPPER(SUBSTRING(md5(random()::text) FROM 1 FOR $length))"
                else -> "SUBSTRING(md5(random()::text) FROM 1 FOR $length)"
            }
        }

        // DBMS_LOB.GETLENGTH → LENGTH / OCTET_LENGTH
        result = DBMS_LOB_GETLENGTH.replace(result) { match ->
            val lobColumn = match.groupValues[1].trim()
            appliedRules.add("DBMS_LOB.GETLENGTH → octet_length")
            "octet_length($lobColumn)"
        }

        // DBMS_LOB.SUBSTR → SUBSTRING
        result = DBMS_LOB_SUBSTR.replace(result) { match ->
            val lobColumn = match.groupValues[1].trim()
            val length = match.groupValues[2]
            val offset = match.groupValues.getOrNull(3)?.ifEmpty { null } ?: "1"
            appliedRules.add("DBMS_LOB.SUBSTR → substring")
            "substring($lobColumn from $offset for $length)"
        }

        // DBMS_LOB.INSTR → POSITION
        result = DBMS_LOB_INSTR.replace(result) { match ->
            val lobColumn = match.groupValues[1].trim()
            val pattern = match.groupValues[2].trim()
            appliedRules.add("DBMS_LOB.INSTR → position")
            "position($pattern in $lobColumn)"
        }

        // DBMS_LOB.APPEND → 문자열 연결
        result = DBMS_LOB_APPEND.replace(result) { match ->
            val dest = match.groupValues[1].trim()
            val src = match.groupValues[2].trim()
            appliedRules.add("DBMS_LOB.APPEND → 문자열 연결 연산자")
            "$dest := $dest || $src"
        }

        // DBMS_UTILITY.GET_TIME → clock_timestamp()
        result = DBMS_UTILITY_GET_TIME.replace(result) {
            appliedRules.add("DBMS_UTILITY.GET_TIME → extract(epoch from clock_timestamp())")
            "(extract(epoch from clock_timestamp()) * 100)::integer"
        }

        // DBMS_UTILITY.FORMAT_ERROR_BACKTRACE → PostgreSQL 대체
        result = DBMS_UTILITY_FORMAT_ERROR_BACKTRACE.replace(result) {
            appliedRules.add("DBMS_UTILITY.FORMAT_ERROR_BACKTRACE → PG_EXCEPTION_CONTEXT")
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "DBMS_UTILITY.FORMAT_ERROR_BACKTRACE는 EXCEPTION 블록 내에서만 사용 가능",
                severity = WarningSeverity.INFO,
                suggestion = "PostgreSQL의 GET STACKED DIAGNOSTICS를 사용하세요."
            ))
            "/* Use: GET STACKED DIAGNOSTICS ... = PG_EXCEPTION_CONTEXT */"
        }

        result = DBMS_UTILITY_FORMAT_ERROR_STACK.replace(result) {
            appliedRules.add("DBMS_UTILITY.FORMAT_ERROR_STACK → SQLERRM")
            "SQLERRM"
        }

        // DBMS_LOCK.SLEEP → pg_sleep
        result = DBMS_LOCK_SLEEP.replace(result) { match ->
            val seconds = match.groupValues[1].trim()
            appliedRules.add("DBMS_LOCK.SLEEP → pg_sleep")
            "PERFORM pg_sleep($seconds)"
        }

        // DBMS_CRYPTO.HASH → PostgreSQL 해시 함수 (pgcrypto 확장 필요)
        result = DBMS_CRYPTO_HASH.replace(result) { match ->
            val data = match.groupValues[1].trim()
            val algorithm = match.groupValues[2].lowercase()
            appliedRules.add("DBMS_CRYPTO.HASH → digest (pgcrypto)")
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "PostgreSQL 해시 함수는 pgcrypto 확장이 필요합니다",
                severity = WarningSeverity.INFO,
                suggestion = "CREATE EXTENSION IF NOT EXISTS pgcrypto; 를 먼저 실행하세요."
            ))
            "digest($data, '$algorithm')"
        }

        // DBMS_CRYPTO.ENCRYPT → 경고
        if (DBMS_CRYPTO_ENCRYPT.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                type = WarningType.MANUAL_REVIEW_NEEDED,
                message = "DBMS_CRYPTO.ENCRYPT는 수동 변환이 필요합니다",
                severity = WarningSeverity.ERROR,
                suggestion = "PostgreSQL의 pgcrypto 확장 또는 애플리케이션 레벨 암호화를 사용하세요."
            ))
        }

        // UTL_FILE → 경고
        result = UTL_FILE_CALL.replace(result) { match ->
            val function = match.groupValues[1].uppercase()
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "UTL_FILE.${function}은 PostgreSQL에서 직접 지원되지 않음",
                severity = WarningSeverity.ERROR,
                suggestion = "파일 작업은 COPY 명령 또는 pg_read_file/pg_write_file을 사용하세요."
            ))
            "NULL /* UTL_FILE.$function - use COPY or pg_read_file/pg_write_file */"
        }

        // UTL_HTTP → PostgreSQL 확장 필요
        result = UTL_HTTP_CALL.replace(result) { match ->
            val function = match.groupValues[1].uppercase()
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "UTL_HTTP.${function}은 PostgreSQL에서 지원되지 않음",
                severity = WarningSeverity.ERROR,
                suggestion = "http 확장을 사용하거나 애플리케이션 레벨에서 처리하세요."
            ))
            "NULL /* UTL_HTTP.$function - use http extension or application level */"
        }

        // DBMS_SQL → PostgreSQL EXECUTE
        result = DBMS_SQL_CALL.replace(result) { match ->
            val function = match.groupValues[1].uppercase()
            warnings.add(ConversionWarning(
                type = WarningType.PARTIAL_SUPPORT,
                message = "DBMS_SQL.${function}은 PostgreSQL에서 EXECUTE로 대체",
                severity = WarningSeverity.WARNING,
                suggestion = "PostgreSQL의 EXECUTE 문 또는 PL/pgSQL 동적 쿼리를 사용하세요."
            ))
            "NULL /* DBMS_SQL.$function - use EXECUTE instead */"
        }

        // DBMS_SCHEDULER → pg_cron 확장
        result = DBMS_SCHEDULER_CALL.replace(result) { match ->
            val function = match.groupValues[1].uppercase()
            warnings.add(ConversionWarning(
                type = WarningType.PARTIAL_SUPPORT,
                message = "DBMS_SCHEDULER.${function}은 pg_cron 확장으로 대체 가능",
                severity = WarningSeverity.WARNING,
                suggestion = "pg_cron 확장을 사용하여 작업 스케줄링하세요."
            ))
            "NULL /* DBMS_SCHEDULER.$function - use pg_cron extension */"
        }

        // RAISE_APPLICATION_ERROR → RAISE EXCEPTION
        result = RAISE_APPLICATION_ERROR.replace(result) { match ->
            val errorCode = match.groupValues[1]
            val errorMessage = match.groupValues[2].trim()
            appliedRules.add("RAISE_APPLICATION_ERROR → RAISE EXCEPTION")
            "RAISE EXCEPTION '% (Error: %)', $errorMessage, '$errorCode'"
        }

        return result
    }

    /**
     * 변환 가능 여부 확인
     */
    fun hasDbmsPackageCalls(sql: String): Boolean {
        val patterns = listOf(
            """DBMS_\w+\.""",
            """UTL_\w+\.""",
            """RAISE_APPLICATION_ERROR"""
        )
        return patterns.any { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(sql) }
    }

    /**
     * 사용된 DBMS 패키지 목록 반환
     */
    fun getUsedDbmsPackages(sql: String): List<String> {
        val pattern = Regex("""(DBMS_\w+|UTL_\w+)\.""", RegexOption.IGNORE_CASE)
        return pattern.findAll(sql)
            .map { it.groupValues[1].uppercase() }
            .distinct()
            .toList()
    }
}
