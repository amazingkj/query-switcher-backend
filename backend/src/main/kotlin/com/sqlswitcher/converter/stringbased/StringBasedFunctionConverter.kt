package com.sqlswitcher.converter.stringbased

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.feature.function.DecodeConverter
import com.sqlswitcher.converter.util.SqlParsingUtils
import org.springframework.stereotype.Component

/**
 * 문자열 기반 함수 변환기
 *
 * AST 파싱이 불가능한 경우 정규식을 사용하여 함수를 변환합니다.
 */
@Component
class StringBasedFunctionConverter {

    // Oracle → MySQL 날짜 포맷 매핑
    private val oracleToMySqlDateFormat = mapOf(
        "YYYY" to "%Y",
        "YY" to "%y",
        "MM" to "%m",
        "MON" to "%b",
        "MONTH" to "%M",
        "DD" to "%d",
        "DY" to "%a",
        "DAY" to "%W",
        "HH24" to "%H",
        "HH12" to "%h",
        "HH" to "%h",
        "MI" to "%i",
        "SS" to "%s",
        "AM" to "%p",
        "PM" to "%p",
        "D" to "%w",
        "J" to "%j",
        "WW" to "%U",
        "IW" to "%V",
        "Q" to "" // MySQL doesn't have quarter format
    )

    // MySQL → Oracle 날짜 포맷 매핑 (역방향)
    private val mySqlToOracleDateFormat = mapOf(
        "%Y" to "YYYY",
        "%y" to "YY",
        "%m" to "MM",
        "%b" to "MON",
        "%M" to "MONTH",
        "%d" to "DD",
        "%e" to "DD",
        "%a" to "DY",
        "%W" to "DAY",
        "%H" to "HH24",
        "%h" to "HH12",
        "%i" to "MI",
        "%s" to "SS",
        "%p" to "AM",
        "%w" to "D",
        "%j" to "DDD",
        "%U" to "WW",
        "%V" to "IW"
    )

    // Oracle → PostgreSQL 날짜 포맷 매핑 (PostgreSQL도 Oracle 형식 대부분 지원)
    private val oracleToPostgreSqlDateFormat = mapOf(
        "YYYY" to "YYYY",
        "YY" to "YY",
        "MM" to "MM",
        "MON" to "Mon",
        "MONTH" to "Month",
        "DD" to "DD",
        "DY" to "Dy",
        "DAY" to "Day",
        "HH24" to "HH24",
        "HH12" to "HH12",
        "HH" to "HH12",
        "MI" to "MI",
        "SS" to "SS",
        "AM" to "AM",
        "PM" to "PM"
    )

    private val converters: Map<DialectPair, List<FunctionReplacement>> = mapOf(
        // Oracle → MySQL
        DialectPair(DialectType.ORACLE, DialectType.MYSQL) to listOf(
            // 날짜/시간 함수
            FunctionReplacement("\\bSYSDATE\\b", "NOW()"),
            FunctionReplacement("\\bSYSTIMESTAMP\\b", "NOW(6)"),
            FunctionReplacement("\\bCURRENT_DATE\\b", "CURDATE()"),
            FunctionReplacement("\\bCURRENT_TIMESTAMP\\b", "NOW()"),
            // TO_CHAR → DATE_FORMAT (기본 날짜 형식)
            FunctionReplacement("\\bTO_CHAR\\s*\\(", "DATE_FORMAT("),
            // TO_DATE → STR_TO_DATE
            FunctionReplacement("\\bTO_DATE\\s*\\(", "STR_TO_DATE("),
            // TO_NUMBER → CAST
            FunctionReplacement("\\bTO_NUMBER\\s*\\(([^)]+)\\)", "CAST($1 AS DECIMAL)"),
            // ADD_MONTHS → DATE_ADD
            FunctionReplacement("\\bADD_MONTHS\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "DATE_ADD($1, INTERVAL $2 MONTH)"),
            // MONTHS_BETWEEN → TIMESTAMPDIFF
            FunctionReplacement("\\bMONTHS_BETWEEN\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "TIMESTAMPDIFF(MONTH, $2, $1)"),
            // TRUNC(date) → DATE()
            FunctionReplacement("\\bTRUNC\\s*\\(\\s*([^,)]+)\\s*\\)", "DATE($1)"),
            // TRUNC(number, n) → TRUNCATE()
            FunctionReplacement("\\bTRUNC\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "TRUNCATE($1, $2)"),
            // LISTAGG → GROUP_CONCAT
            FunctionReplacement("\\bLISTAGG\\s*\\(\\s*([^,]+)\\s*,\\s*('[^']*')\\s*\\)\\s*WITHIN\\s+GROUP\\s*\\(\\s*ORDER\\s+BY\\s+([^)]+)\\s*\\)", "GROUP_CONCAT($1 ORDER BY $3 SEPARATOR $2)"),
            FunctionReplacement("\\bLISTAGG\\s*\\(", "GROUP_CONCAT("),
            // NULL 처리 함수
            FunctionReplacement("\\bNVL\\s*\\(", "IFNULL("),
            // NVL2는 별도 처리 (DecodeConverter.convertNvl2ToCaseWhen 사용)
            // 문자열 함수
            FunctionReplacement("\\bSUBSTR\\s*\\(", "SUBSTRING("),
            FunctionReplacement("\\bLENGTH\\s*\\(", "CHAR_LENGTH("),
            FunctionReplacement("\\bLENGTHB\\s*\\(", "LENGTH("),
            // INSTR은 별도 처리 필요 (파라미터 순서 변환)
            // 수학 함수
            FunctionReplacement("\\bMOD\\s*\\(", "MOD("),
            FunctionReplacement("\\bPOWER\\s*\\(", "POW("),
            FunctionReplacement("\\bDBMS_RANDOM\\.VALUE\\b", "RAND()"),
            // DECODE는 별도 처리 (DecodeConverter 사용)
            // 시퀀스 (MySQL 8.0+는 시퀀스 미지원, 경고 필요)
            FunctionReplacement("([A-Za-z_][A-Za-z0-9_]*)\\.NEXTVAL", "NULL /* SEQUENCE $1.NEXTVAL not supported in MySQL */"),
            FunctionReplacement("([A-Za-z_][A-Za-z0-9_]*)\\.CURRVAL", "NULL /* SEQUENCE $1.CURRVAL not supported in MySQL */"),
            // DUAL 테이블 제거
            FunctionReplacement("\\s+FROM\\s+DUAL\\b", ""),
            // ROWNUM (단순 케이스)
            FunctionReplacement("\\bROWNUM\\b", "@rownum := @rownum + 1")
        ),

        // Oracle → PostgreSQL
        DialectPair(DialectType.ORACLE, DialectType.POSTGRESQL) to listOf(
            // 날짜/시간 함수
            FunctionReplacement("\\bSYSDATE\\b", "CURRENT_TIMESTAMP"),
            FunctionReplacement("\\bSYSTIMESTAMP\\b", "CURRENT_TIMESTAMP"),
            // TO_CHAR 유지 (PostgreSQL도 TO_CHAR 지원)
            // TO_DATE 유지 (PostgreSQL도 TO_DATE 지원)
            // ADD_MONTHS → + INTERVAL
            FunctionReplacement("\\bADD_MONTHS\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "($1 + INTERVAL '$2 months')"),
            // MONTHS_BETWEEN → EXTRACT/DATE_PART
            FunctionReplacement("\\bMONTHS_BETWEEN\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "(EXTRACT(YEAR FROM $1) - EXTRACT(YEAR FROM $2)) * 12 + (EXTRACT(MONTH FROM $1) - EXTRACT(MONTH FROM $2))"),
            // TRUNC(date) → DATE_TRUNC
            FunctionReplacement("\\bTRUNC\\s*\\(\\s*([^,)]+)\\s*\\)", "DATE_TRUNC('day', $1)"),
            // LISTAGG → STRING_AGG
            FunctionReplacement("\\bLISTAGG\\s*\\(\\s*([^,]+)\\s*,\\s*('[^']*')\\s*\\)\\s*WITHIN\\s+GROUP\\s*\\(\\s*ORDER\\s+BY\\s+([^)]+)\\s*\\)", "STRING_AGG($1, $2 ORDER BY $3)"),
            FunctionReplacement("\\bLISTAGG\\s*\\(", "STRING_AGG("),
            // NULL 처리 함수
            FunctionReplacement("\\bNVL\\s*\\(", "COALESCE("),
            FunctionReplacement("\\bNVL2\\s*\\(\\s*([^,]+)\\s*,\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "CASE WHEN $1 IS NOT NULL THEN $2 ELSE $3 END"),
            // 문자열 함수
            FunctionReplacement("\\bSUBSTR\\s*\\(", "SUBSTRING("),
            FunctionReplacement("\\bLENGTH\\s*\\(", "LENGTH("),
            FunctionReplacement("\\bINSTR\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "POSITION($2 IN $1)"),
            // 문자열 연결 (||는 PostgreSQL도 지원하므로 그대로)
            // 수학 함수
            FunctionReplacement("\\bDBMS_RANDOM\\.VALUE\\b", "RANDOM()"),
            FunctionReplacement("\\bDBMS_RANDOM\\.VALUE\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "($1 + RANDOM() * ($2 - $1))"),
            // DECODE는 별도 처리 (DecodeConverter 사용)
            // 시퀀스
            FunctionReplacement("([A-Za-z_][A-Za-z0-9_]*)\\.NEXTVAL", "nextval('$1')"),
            FunctionReplacement("([A-Za-z_][A-Za-z0-9_]*)\\.CURRVAL", "currval('$1')"),
            // DUAL 테이블 제거 (PostgreSQL은 FROM 절 없이 SELECT 가능)
            FunctionReplacement("\\s+FROM\\s+DUAL\\b", ""),
            // ROWNUM → ROW_NUMBER() (완전한 변환은 복잡)
            FunctionReplacement("\\bROWNUM\\b", "ROW_NUMBER() OVER ()")
        ),

        // MySQL → PostgreSQL
        DialectPair(DialectType.MYSQL, DialectType.POSTGRESQL) to listOf(
            // 날짜/시간 함수
            FunctionReplacement("\\bNOW\\s*\\(\\s*\\)", "CURRENT_TIMESTAMP"),
            FunctionReplacement("\\bCURDATE\\s*\\(\\s*\\)", "CURRENT_DATE"),
            FunctionReplacement("\\bCURTIME\\s*\\(\\s*\\)", "CURRENT_TIME"),
            FunctionReplacement("\\bUNIX_TIMESTAMP\\s*\\(\\s*\\)", "EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::INTEGER"),
            FunctionReplacement("\\bUNIX_TIMESTAMP\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(EPOCH FROM $1)::INTEGER"),
            FunctionReplacement("\\bFROM_UNIXTIME\\s*\\(", "TO_TIMESTAMP("),
            FunctionReplacement("\\bDATE_FORMAT\\s*\\(", "TO_CHAR("),
            FunctionReplacement("\\bSTR_TO_DATE\\s*\\(", "TO_DATE("),
            FunctionReplacement("\\bDATEDIFF\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "($1::DATE - $2::DATE)"),
            FunctionReplacement("\\bDATE_ADD\\s*\\(\\s*([^,]+)\\s*,\\s*INTERVAL\\s+([^)]+)\\s*\\)", "($1 + INTERVAL '$2')"),
            FunctionReplacement("\\bDATE_SUB\\s*\\(\\s*([^,]+)\\s*,\\s*INTERVAL\\s+([^)]+)\\s*\\)", "($1 - INTERVAL '$2')"),
            FunctionReplacement("\\bYEAR\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(YEAR FROM $1)"),
            FunctionReplacement("\\bMONTH\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(MONTH FROM $1)"),
            FunctionReplacement("\\bDAY\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(DAY FROM $1)"),
            FunctionReplacement("\\bHOUR\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(HOUR FROM $1)"),
            FunctionReplacement("\\bMINUTE\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(MINUTE FROM $1)"),
            FunctionReplacement("\\bSECOND\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(SECOND FROM $1)"),
            FunctionReplacement("\\bDAYOFWEEK\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(DOW FROM $1)"),
            FunctionReplacement("\\bDAYOFYEAR\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(DOY FROM $1)"),
            FunctionReplacement("\\bWEEK\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(WEEK FROM $1)"),
            // 문자열 함수
            FunctionReplacement("\\bIFNULL\\s*\\(", "COALESCE("),
            // IF는 별도 처리 (DecodeConverter.convertIfToCaseWhen 사용)
            FunctionReplacement("\\bGROUP_CONCAT\\s*\\(\\s*([^)]+)\\s+ORDER\\s+BY\\s+([^)]+)\\s+SEPARATOR\\s+('[^']*')\\s*\\)", "STRING_AGG($1, $3 ORDER BY $2)"),
            FunctionReplacement("\\bGROUP_CONCAT\\s*\\(\\s*([^)]+)\\s+SEPARATOR\\s+('[^']*')\\s*\\)", "STRING_AGG($1, $2)"),
            FunctionReplacement("\\bGROUP_CONCAT\\s*\\(", "STRING_AGG("),
            FunctionReplacement("\\bLOCATE\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "POSITION($1 IN $2)"),
            FunctionReplacement("\\bINSTR\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "POSITION($2 IN $1)"),
            FunctionReplacement("\\bCONCAT_WS\\s*\\(", "CONCAT_WS("),
            FunctionReplacement("\\bLEFT\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "LEFT($1, $2)"),
            FunctionReplacement("\\bRIGHT\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "RIGHT($1, $2)"),
            FunctionReplacement("\\bLPAD\\s*\\(", "LPAD("),
            FunctionReplacement("\\bRPAD\\s*\\(", "RPAD("),
            FunctionReplacement("\\bREVERSE\\s*\\(", "REVERSE("),
            // 수학 함수
            FunctionReplacement("\\bRAND\\s*\\(\\s*\\)", "RANDOM()"),
            FunctionReplacement("\\bTRUNCATE\\s*\\(", "TRUNC("),
            FunctionReplacement("\\bCEIL\\s*\\(", "CEIL("),
            FunctionReplacement("\\bFLOOR\\s*\\(", "FLOOR("),
            FunctionReplacement("\\bROUND\\s*\\(", "ROUND("),
            FunctionReplacement("\\bABS\\s*\\(", "ABS("),
            FunctionReplacement("\\bSIGN\\s*\\(", "SIGN("),
            // 기타
            FunctionReplacement("\\bLAST_INSERT_ID\\s*\\(\\s*\\)", "LASTVAL()"),
            FunctionReplacement("\\bFOUND_ROWS\\s*\\(\\s*\\)", "(SELECT COUNT(*) FROM ...)"),
            // LIMIT OFFSET 순서 (MySQL과 PostgreSQL 동일하므로 유지)
            // AUTO_INCREMENT → SERIAL (DDL에서 처리)
            // ENGINE= 제거
            FunctionReplacement("\\s*ENGINE\\s*=\\s*\\w+", "")
        ),

        // MySQL → Oracle
        DialectPair(DialectType.MYSQL, DialectType.ORACLE) to listOf(
            // 날짜/시간 함수
            FunctionReplacement("\\bNOW\\s*\\(\\s*\\)", "SYSDATE"),
            FunctionReplacement("\\bCURDATE\\s*\\(\\s*\\)", "TRUNC(SYSDATE)"),
            FunctionReplacement("\\bCURTIME\\s*\\(\\s*\\)", "TO_CHAR(SYSDATE, 'HH24:MI:SS')"),
            FunctionReplacement("\\bDATE_FORMAT\\s*\\(", "TO_CHAR("),
            FunctionReplacement("\\bSTR_TO_DATE\\s*\\(", "TO_DATE("),
            FunctionReplacement("\\bDATE_ADD\\s*\\(\\s*([^,]+)\\s*,\\s*INTERVAL\\s+(\\d+)\\s+MONTH\\s*\\)", "ADD_MONTHS($1, $2)"),
            FunctionReplacement("\\bDATE_ADD\\s*\\(\\s*([^,]+)\\s*,\\s*INTERVAL\\s+(\\d+)\\s+DAY\\s*\\)", "($1 + $2)"),
            FunctionReplacement("\\bDATE_SUB\\s*\\(\\s*([^,]+)\\s*,\\s*INTERVAL\\s+(\\d+)\\s+MONTH\\s*\\)", "ADD_MONTHS($1, -$2)"),
            FunctionReplacement("\\bDATE_SUB\\s*\\(\\s*([^,]+)\\s*,\\s*INTERVAL\\s+(\\d+)\\s+DAY\\s*\\)", "($1 - $2)"),
            FunctionReplacement("\\bDATEDIFF\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "($1 - $2)"),
            FunctionReplacement("\\bYEAR\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(YEAR FROM $1)"),
            FunctionReplacement("\\bMONTH\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(MONTH FROM $1)"),
            FunctionReplacement("\\bDAY\\s*\\(\\s*([^)]+)\\s*\\)", "EXTRACT(DAY FROM $1)"),
            // NULL 처리 함수
            FunctionReplacement("\\bIFNULL\\s*\\(", "NVL("),
            FunctionReplacement("\\bCOALESCE\\s*\\(", "NVL("),
            // IF는 별도 처리 (DecodeConverter.convertIfToCaseWhen 사용)
            // 문자열 함수
            FunctionReplacement("\\bSUBSTRING\\s*\\(", "SUBSTR("),
            FunctionReplacement("\\bCHAR_LENGTH\\s*\\(", "LENGTH("),
            FunctionReplacement("\\bLENGTH\\s*\\(", "LENGTHB("),
            FunctionReplacement("\\bLOCATE\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "INSTR($2, $1)"),
            FunctionReplacement("\\bGROUP_CONCAT\\s*\\(", "LISTAGG("),
            FunctionReplacement("\\bCONCAT\\s*\\(([^)]+)\\)", "($1)"),
            // 수학 함수
            FunctionReplacement("\\bRAND\\s*\\(\\s*\\)", "DBMS_RANDOM.VALUE"),
            FunctionReplacement("\\bTRUNCATE\\s*\\(", "TRUNC("),
            FunctionReplacement("\\bPOW\\s*\\(", "POWER("),
            FunctionReplacement("\\bMOD\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "MOD($1, $2)"),
            // LIMIT → ROWNUM/FETCH (복잡한 변환 필요)
            FunctionReplacement("\\bLIMIT\\s+(\\d+)\\s*$", "FETCH FIRST $1 ROWS ONLY"),
            FunctionReplacement("\\bLIMIT\\s+(\\d+)\\s+OFFSET\\s+(\\d+)", "OFFSET $2 ROWS FETCH NEXT $1 ROWS ONLY"),
            // AUTO_INCREMENT 제거 (Oracle은 IDENTITY 사용)
            FunctionReplacement("\\bAUTO_INCREMENT\\b", "GENERATED ALWAYS AS IDENTITY"),
            // ENGINE= 제거
            FunctionReplacement("\\s*ENGINE\\s*=\\s*\\w+", "")
        ),

        // PostgreSQL → MySQL
        DialectPair(DialectType.POSTGRESQL, DialectType.MYSQL) to listOf(
            // 날짜/시간 함수
            FunctionReplacement("\\bCURRENT_TIMESTAMP\\b", "NOW()"),
            FunctionReplacement("\\bCURRENT_DATE\\b", "CURDATE()"),
            FunctionReplacement("\\bCURRENT_TIME\\b", "CURTIME()"),
            FunctionReplacement("\\bNOW\\s*\\(\\s*\\)", "NOW()"),
            FunctionReplacement("\\bTO_CHAR\\s*\\(", "DATE_FORMAT("),
            FunctionReplacement("\\bTO_DATE\\s*\\(", "STR_TO_DATE("),
            FunctionReplacement("\\bTO_TIMESTAMP\\s*\\(", "FROM_UNIXTIME("),
            FunctionReplacement("\\bEXTRACT\\s*\\(\\s*EPOCH\\s+FROM\\s+([^)]+)\\s*\\)", "UNIX_TIMESTAMP($1)"),
            FunctionReplacement("\\bEXTRACT\\s*\\(\\s*YEAR\\s+FROM\\s+([^)]+)\\s*\\)", "YEAR($1)"),
            FunctionReplacement("\\bEXTRACT\\s*\\(\\s*MONTH\\s+FROM\\s+([^)]+)\\s*\\)", "MONTH($1)"),
            FunctionReplacement("\\bEXTRACT\\s*\\(\\s*DAY\\s+FROM\\s+([^)]+)\\s*\\)", "DAY($1)"),
            FunctionReplacement("\\bEXTRACT\\s*\\(\\s*HOUR\\s+FROM\\s+([^)]+)\\s*\\)", "HOUR($1)"),
            FunctionReplacement("\\bEXTRACT\\s*\\(\\s*MINUTE\\s+FROM\\s+([^)]+)\\s*\\)", "MINUTE($1)"),
            FunctionReplacement("\\bEXTRACT\\s*\\(\\s*SECOND\\s+FROM\\s+([^)]+)\\s*\\)", "SECOND($1)"),
            FunctionReplacement("\\bEXTRACT\\s*\\(\\s*DOW\\s+FROM\\s+([^)]+)\\s*\\)", "DAYOFWEEK($1)"),
            FunctionReplacement("\\bEXTRACT\\s*\\(\\s*DOY\\s+FROM\\s+([^)]+)\\s*\\)", "DAYOFYEAR($1)"),
            FunctionReplacement("\\bEXTRACT\\s*\\(\\s*WEEK\\s+FROM\\s+([^)]+)\\s*\\)", "WEEK($1)"),
            FunctionReplacement("\\bDATE_TRUNC\\s*\\(\\s*'day'\\s*,\\s*([^)]+)\\s*\\)", "DATE($1)"),
            FunctionReplacement("\\bDATE_TRUNC\\s*\\(\\s*'month'\\s*,\\s*([^)]+)\\s*\\)", "DATE_FORMAT($1, '%Y-%m-01')"),
            FunctionReplacement("\\bDATE_TRUNC\\s*\\(\\s*'year'\\s*,\\s*([^)]+)\\s*\\)", "DATE_FORMAT($1, '%Y-01-01')"),
            FunctionReplacement("\\bAGE\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "TIMESTAMPDIFF(SECOND, $2, $1)"),
            // INTERVAL 변환
            FunctionReplacement("\\+\\s*INTERVAL\\s+'(\\d+)\\s+months?'", "+ INTERVAL $1 MONTH"),
            FunctionReplacement("\\+\\s*INTERVAL\\s+'(\\d+)\\s+days?'", "+ INTERVAL $1 DAY"),
            FunctionReplacement("\\+\\s*INTERVAL\\s+'(\\d+)\\s+hours?'", "+ INTERVAL $1 HOUR"),
            FunctionReplacement("-\\s*INTERVAL\\s+'(\\d+)\\s+months?'", "- INTERVAL $1 MONTH"),
            FunctionReplacement("-\\s*INTERVAL\\s+'(\\d+)\\s+days?'", "- INTERVAL $1 DAY"),
            // 문자열 함수
            FunctionReplacement("\\bCOALESCE\\s*\\(", "IFNULL("),
            FunctionReplacement("\\bSTRING_AGG\\s*\\(\\s*([^,]+)\\s*,\\s*('[^']*')\\s+ORDER\\s+BY\\s+([^)]+)\\s*\\)", "GROUP_CONCAT($1 ORDER BY $3 SEPARATOR $2)"),
            FunctionReplacement("\\bSTRING_AGG\\s*\\(\\s*([^,]+)\\s*,\\s*('[^']*')\\s*\\)", "GROUP_CONCAT($1 SEPARATOR $2)"),
            FunctionReplacement("\\bSTRING_AGG\\s*\\(", "GROUP_CONCAT("),
            FunctionReplacement("\\bPOSITION\\s*\\(\\s*([^)]+)\\s+IN\\s+([^)]+)\\s*\\)", "LOCATE($1, $2)"),
            FunctionReplacement("\\bSUBSTRING\\s*\\(\\s*([^,]+)\\s+FROM\\s+(\\d+)\\s+FOR\\s+(\\d+)\\s*\\)", "SUBSTRING($1, $2, $3)"),
            FunctionReplacement("\\bSUBSTRING\\s*\\(\\s*([^,]+)\\s+FROM\\s+(\\d+)\\s*\\)", "SUBSTRING($1, $2)"),
            FunctionReplacement("\\bLOWER\\s*\\(", "LOWER("),
            FunctionReplacement("\\bUPPER\\s*\\(", "UPPER("),
            FunctionReplacement("\\bTRIM\\s*\\(", "TRIM("),
            FunctionReplacement("\\bLTRIM\\s*\\(", "LTRIM("),
            FunctionReplacement("\\bRTRIM\\s*\\(", "RTRIM("),
            FunctionReplacement("\\bLEFT\\s*\\(", "LEFT("),
            FunctionReplacement("\\bRIGHT\\s*\\(", "RIGHT("),
            FunctionReplacement("\\bLPAD\\s*\\(", "LPAD("),
            FunctionReplacement("\\bRPAD\\s*\\(", "RPAD("),
            FunctionReplacement("\\bREPLACE\\s*\\(", "REPLACE("),
            FunctionReplacement("\\bREVERSE\\s*\\(", "REVERSE("),
            // ILIKE → LIKE (MySQL은 기본적으로 대소문자 구분 안함)
            FunctionReplacement("\\bILIKE\\b", "LIKE"),
            FunctionReplacement("\\bNOT\\s+ILIKE\\b", "NOT LIKE"),
            // 수학 함수
            FunctionReplacement("\\bRANDOM\\s*\\(\\s*\\)", "RAND()"),
            FunctionReplacement("\\bTRUNC\\s*\\(", "TRUNCATE("),
            FunctionReplacement("\\bCEIL\\s*\\(", "CEIL("),
            FunctionReplacement("\\bFLOOR\\s*\\(", "FLOOR("),
            FunctionReplacement("\\bROUND\\s*\\(", "ROUND("),
            FunctionReplacement("\\bABS\\s*\\(", "ABS("),
            FunctionReplacement("\\bSIGN\\s*\\(", "SIGN("),
            FunctionReplacement("\\bPOWER\\s*\\(", "POW("),
            FunctionReplacement("\\bSQRT\\s*\\(", "SQRT("),
            FunctionReplacement("\\bLN\\s*\\(", "LOG("),
            FunctionReplacement("\\bLOG\\s*\\(", "LOG10("),
            FunctionReplacement("\\bEXP\\s*\\(", "EXP("),
            // 타입 캐스팅 제거
            FunctionReplacement("::\\s*INTEGER\\b", ""),
            FunctionReplacement("::\\s*BIGINT\\b", ""),
            FunctionReplacement("::\\s*SMALLINT\\b", ""),
            FunctionReplacement("::\\s*TEXT\\b", ""),
            FunctionReplacement("::\\s*VARCHAR\\b", ""),
            FunctionReplacement("::\\s*VARCHAR\\s*\\(\\d+\\)", ""),
            FunctionReplacement("::\\s*CHAR\\s*\\(\\d+\\)", ""),
            FunctionReplacement("::\\s*NUMERIC\\b", ""),
            FunctionReplacement("::\\s*NUMERIC\\s*\\(\\d+\\s*,\\s*\\d+\\)", ""),
            FunctionReplacement("::\\s*DECIMAL\\b", ""),
            FunctionReplacement("::\\s*FLOAT\\b", ""),
            FunctionReplacement("::\\s*DOUBLE PRECISION\\b", ""),
            FunctionReplacement("::\\s*REAL\\b", ""),
            FunctionReplacement("::\\s*TIMESTAMP\\b", ""),
            FunctionReplacement("::\\s*DATE\\b", ""),
            FunctionReplacement("::\\s*TIME\\b", ""),
            FunctionReplacement("::\\s*BOOLEAN\\b", ""),
            FunctionReplacement("::\\s*JSONB?\\b", ""),
            FunctionReplacement("::\\s*BYTEA\\b", ""),
            // RETURNING 제거 (MySQL 5.x 미지원)
            FunctionReplacement("\\s+RETURNING\\s+.*$", " /* RETURNING not supported */"),
            // ARRAY 구문 제거
            FunctionReplacement("\\bARRAY\\s*\\[", "JSON_ARRAY("),
            FunctionReplacement("\\]", ")")
        ),

        // PostgreSQL → Oracle
        DialectPair(DialectType.POSTGRESQL, DialectType.ORACLE) to listOf(
            // 날짜/시간 함수
            FunctionReplacement("\\bCURRENT_TIMESTAMP\\b", "SYSDATE"),
            FunctionReplacement("\\bCURRENT_DATE\\b", "TRUNC(SYSDATE)"),
            FunctionReplacement("\\bCURRENT_TIME\\b", "TO_CHAR(SYSDATE, 'HH24:MI:SS')"),
            FunctionReplacement("\\bNOW\\s*\\(\\s*\\)", "SYSDATE"),
            FunctionReplacement("\\bEXTRACT\\s*\\(\\s*EPOCH\\s+FROM\\s+([^)]+)\\s*\\)", "(($1 - DATE '1970-01-01') * 86400)"),
            FunctionReplacement("\\bDATE_TRUNC\\s*\\(\\s*'day'\\s*,\\s*([^)]+)\\s*\\)", "TRUNC($1)"),
            FunctionReplacement("\\bDATE_TRUNC\\s*\\(\\s*'month'\\s*,\\s*([^)]+)\\s*\\)", "TRUNC($1, 'MM')"),
            FunctionReplacement("\\bDATE_TRUNC\\s*\\(\\s*'year'\\s*,\\s*([^)]+)\\s*\\)", "TRUNC($1, 'YYYY')"),
            FunctionReplacement("\\bAGE\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", "($1 - $2)"),
            // INTERVAL 변환
            FunctionReplacement("\\+\\s*INTERVAL\\s+'(\\d+)\\s+months?'", "+ NUMTOYMINTERVAL($1, 'MONTH')"),
            FunctionReplacement("\\+\\s*INTERVAL\\s+'(\\d+)\\s+days?'", "+ $1"),
            FunctionReplacement("-\\s*INTERVAL\\s+'(\\d+)\\s+months?'", "- NUMTOYMINTERVAL($1, 'MONTH')"),
            FunctionReplacement("-\\s*INTERVAL\\s+'(\\d+)\\s+days?'", "- $1"),
            // NULL 처리 함수
            FunctionReplacement("\\bCOALESCE\\s*\\(", "NVL("),
            FunctionReplacement("\\bNULLIF\\s*\\(", "NULLIF("),
            // 문자열 함수
            FunctionReplacement("\\bSTRING_AGG\\s*\\(\\s*([^,]+)\\s*,\\s*('[^']*')\\s+ORDER\\s+BY\\s+([^)]+)\\s*\\)", "LISTAGG($1, $2) WITHIN GROUP (ORDER BY $3)"),
            FunctionReplacement("\\bSTRING_AGG\\s*\\(\\s*([^,]+)\\s*,\\s*('[^']*')\\s*\\)", "LISTAGG($1, $2) WITHIN GROUP (ORDER BY 1)"),
            FunctionReplacement("\\bSTRING_AGG\\s*\\(", "LISTAGG("),
            FunctionReplacement("\\bSUBSTRING\\s*\\(\\s*([^,]+)\\s+FROM\\s+(\\d+)\\s+FOR\\s+(\\d+)\\s*\\)", "SUBSTR($1, $2, $3)"),
            FunctionReplacement("\\bSUBSTRING\\s*\\(\\s*([^,]+)\\s+FROM\\s+(\\d+)\\s*\\)", "SUBSTR($1, $2)"),
            FunctionReplacement("\\bSUBSTRING\\s*\\(", "SUBSTR("),
            FunctionReplacement("\\bPOSITION\\s*\\(\\s*([^)]+)\\s+IN\\s+([^)]+)\\s*\\)", "INSTR($2, $1)"),
            FunctionReplacement("\\bLENGTH\\s*\\(", "LENGTH("),
            FunctionReplacement("\\bCHAR_LENGTH\\s*\\(", "LENGTH("),
            FunctionReplacement("\\bLOWER\\s*\\(", "LOWER("),
            FunctionReplacement("\\bUPPER\\s*\\(", "UPPER("),
            FunctionReplacement("\\bTRIM\\s*\\(", "TRIM("),
            FunctionReplacement("\\bLTRIM\\s*\\(", "LTRIM("),
            FunctionReplacement("\\bRTRIM\\s*\\(", "RTRIM("),
            FunctionReplacement("\\bLPAD\\s*\\(", "LPAD("),
            FunctionReplacement("\\bRPAD\\s*\\(", "RPAD("),
            FunctionReplacement("\\bREPLACE\\s*\\(", "REPLACE("),
            // 수학 함수
            FunctionReplacement("\\bRANDOM\\s*\\(\\s*\\)", "DBMS_RANDOM.VALUE"),
            FunctionReplacement("\\bTRUNC\\s*\\(", "TRUNC("),
            FunctionReplacement("\\bCEIL\\s*\\(", "CEIL("),
            FunctionReplacement("\\bFLOOR\\s*\\(", "FLOOR("),
            FunctionReplacement("\\bROUND\\s*\\(", "ROUND("),
            FunctionReplacement("\\bABS\\s*\\(", "ABS("),
            FunctionReplacement("\\bSIGN\\s*\\(", "SIGN("),
            FunctionReplacement("\\bPOWER\\s*\\(", "POWER("),
            FunctionReplacement("\\bSQRT\\s*\\(", "SQRT("),
            FunctionReplacement("\\bLN\\s*\\(", "LN("),
            FunctionReplacement("\\bLOG\\s*\\(", "LOG(10, "),
            FunctionReplacement("\\bEXP\\s*\\(", "EXP("),
            FunctionReplacement("\\bMOD\\s*\\(", "MOD("),
            // 타입 캐스팅 → TO_NUMBER/TO_CHAR
            FunctionReplacement("::\\s*INTEGER\\b", ""),
            FunctionReplacement("::\\s*BIGINT\\b", ""),
            FunctionReplacement("::\\s*SMALLINT\\b", ""),
            FunctionReplacement("::\\s*TEXT\\b", ""),
            FunctionReplacement("::\\s*VARCHAR\\b", ""),
            FunctionReplacement("::\\s*NUMERIC\\b", ""),
            FunctionReplacement("::\\s*TIMESTAMP\\b", ""),
            FunctionReplacement("::\\s*DATE\\b", ""),
            // 시퀀스
            FunctionReplacement("\\bnextval\\s*\\(\\s*'([^']+)'\\s*\\)", "$1.NEXTVAL"),
            FunctionReplacement("\\bcurrval\\s*\\(\\s*'([^']+)'\\s*\\)", "$1.CURRVAL"),
            // LIMIT → FETCH FIRST
            FunctionReplacement("\\bLIMIT\\s+(\\d+)\\s*$", "FETCH FIRST $1 ROWS ONLY"),
            FunctionReplacement("\\bLIMIT\\s+(\\d+)\\s+OFFSET\\s+(\\d+)", "OFFSET $2 ROWS FETCH NEXT $1 ROWS ONLY"),
            // RETURNING → RETURNING INTO (Oracle은 다른 문법)
            FunctionReplacement("\\s+RETURNING\\s+", " RETURNING "),
            // BOOLEAN → NUMBER(1)
            FunctionReplacement("\\bTRUE\\b", "1"),
            FunctionReplacement("\\bFALSE\\b", "0")
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

        // 1. DECODE, NVL2 함수 변환 (Oracle → 다른 DB)
        if (sourceDialect == DialectType.ORACLE) {
            val beforeDecode = result
            result = DecodeConverter.convertDecodeToCaseWhen(result)
            if (result != beforeDecode) {
                appliedRules.add("DECODE → CASE WHEN 변환")
                hasChanges = true
            }

            // NVL2 함수 변환 (Oracle → 다른 DB)
            val beforeNvl2 = result
            result = DecodeConverter.convertNvl2ToCaseWhen(result)
            if (result != beforeNvl2) {
                appliedRules.add("NVL2 → CASE WHEN 변환")
                hasChanges = true
            }
        }

        // 2. IF 함수 변환 (MySQL → 다른 DB)
        if (sourceDialect == DialectType.MYSQL && targetDialect != DialectType.MYSQL) {
            val beforeIf = result
            result = DecodeConverter.convertIfToCaseWhen(result)
            if (result != beforeIf) {
                appliedRules.add("IF → CASE WHEN 변환")
                hasChanges = true
            }
        }

        // 3. 정규식 기반 함수 변환
        for (replacement in replacements) {
            val newResult = replacement.regex.replace(result, replacement.replacement)
            if (newResult != result) {
                result = newResult
                hasChanges = true
            }
        }

        // 4. INSTR 파라미터 순서 변환 (Oracle → MySQL)
        if (sourceDialect == DialectType.ORACLE && targetDialect == DialectType.MYSQL) {
            val beforeInstr = result
            result = convertOracleInstrToMySqlLocate(result)
            if (result != beforeInstr) {
                appliedRules.add("INSTR → LOCATE 파라미터 순서 변환")
                hasChanges = true
            }
        }

        // 5. 날짜 포맷 문자열 변환
        result = convertDateFormats(result, sourceDialect, targetDialect, appliedRules)

        if (hasChanges) {
            appliedRules.add("${sourceDialect.name} → ${targetDialect.name} 함수 변환")
        }

        return result
    }

    /**
     * Oracle INSTR(string, substring) → MySQL LOCATE(substring, string) 변환
     */
    private fun convertOracleInstrToMySqlLocate(sql: String): String {
        val instrPattern = Regex("""INSTR\s*\(\s*([^,]+)\s*,\s*([^,)]+)\s*\)""", RegexOption.IGNORE_CASE)
        return instrPattern.replace(sql) { match ->
            val stringArg = match.groupValues[1].trim()
            val substringArg = match.groupValues[2].trim()
            "LOCATE($substringArg, $stringArg)"
        }
    }

    /**
     * 날짜 포맷 문자열 변환
     */
    private fun convertDateFormats(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when {
            // Oracle → MySQL: TO_CHAR/TO_DATE 안의 날짜 포맷 변환
            sourceDialect == DialectType.ORACLE && targetDialect == DialectType.MYSQL -> {
                val formatPattern = Regex("""(DATE_FORMAT|STR_TO_DATE)\s*\(\s*([^,]+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)
                val beforeFormat = result
                result = formatPattern.replace(result) { match ->
                    val funcName = match.groupValues[1]
                    val dateExpr = match.groupValues[2]
                    val oracleFormat = match.groupValues[3]
                    val mysqlFormat = convertOracleFormatToMySql(oracleFormat)
                    "$funcName($dateExpr, '$mysqlFormat')"
                }
                if (result != beforeFormat) {
                    appliedRules.add("Oracle → MySQL 날짜 포맷 변환")
                }
            }

            // MySQL → Oracle: DATE_FORMAT/STR_TO_DATE 안의 날짜 포맷 변환
            sourceDialect == DialectType.MYSQL && targetDialect == DialectType.ORACLE -> {
                val formatPattern = Regex("""(TO_CHAR|TO_DATE)\s*\(\s*([^,]+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)
                val beforeFormat = result
                result = formatPattern.replace(result) { match ->
                    val funcName = match.groupValues[1]
                    val dateExpr = match.groupValues[2]
                    val mysqlFormat = match.groupValues[3]
                    val oracleFormat = convertMySqlFormatToOracle(mysqlFormat)
                    "$funcName($dateExpr, '$oracleFormat')"
                }
                if (result != beforeFormat) {
                    appliedRules.add("MySQL → Oracle 날짜 포맷 변환")
                }
            }

            // MySQL → PostgreSQL: DATE_FORMAT → TO_CHAR 후 포맷 변환
            sourceDialect == DialectType.MYSQL && targetDialect == DialectType.POSTGRESQL -> {
                val formatPattern = Regex("""(TO_CHAR|TO_DATE)\s*\(\s*([^,]+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)
                val beforeFormat = result
                result = formatPattern.replace(result) { match ->
                    val funcName = match.groupValues[1]
                    val dateExpr = match.groupValues[2]
                    val mysqlFormat = match.groupValues[3]
                    val pgFormat = convertMySqlFormatToPostgreSql(mysqlFormat)
                    "$funcName($dateExpr, '$pgFormat')"
                }
                if (result != beforeFormat) {
                    appliedRules.add("MySQL → PostgreSQL 날짜 포맷 변환")
                }
            }

            // PostgreSQL → MySQL: TO_CHAR → DATE_FORMAT 후 포맷 변환
            sourceDialect == DialectType.POSTGRESQL && targetDialect == DialectType.MYSQL -> {
                val formatPattern = Regex("""(DATE_FORMAT|STR_TO_DATE)\s*\(\s*([^,]+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)
                val beforeFormat = result
                result = formatPattern.replace(result) { match ->
                    val funcName = match.groupValues[1]
                    val dateExpr = match.groupValues[2]
                    val pgFormat = match.groupValues[3]
                    val mysqlFormat = convertPostgreSqlFormatToMySql(pgFormat)
                    "$funcName($dateExpr, '$mysqlFormat')"
                }
                if (result != beforeFormat) {
                    appliedRules.add("PostgreSQL → MySQL 날짜 포맷 변환")
                }
            }
        }

        return result
    }

    /**
     * Oracle 날짜 포맷 → MySQL 날짜 포맷 변환
     * 플레이스홀더를 사용하여 변환된 값이 다시 변환되는 것을 방지
     */
    private fun convertOracleFormatToMySql(oracleFormat: String): String {
        var result = oracleFormat
        val placeholders = mutableMapOf<String, String>()
        var placeholderIndex = 0

        // 길이가 긴 것부터 변환 (HH24를 먼저 처리해야 HH만 변환되는 것을 방지)
        val sortedMappings = oracleToMySqlDateFormat.entries.sortedByDescending { it.key.length }
        for ((oracle, mysql) in sortedMappings) {
            if (mysql.isNotEmpty()) {
                val placeholder = "\u0000PH${placeholderIndex++}\u0000"
                placeholders[placeholder] = mysql
                result = result.replace(oracle, placeholder, ignoreCase = true)
            }
        }

        // 플레이스홀더를 실제 값으로 대체
        for ((placeholder, mysql) in placeholders) {
            result = result.replace(placeholder, mysql)
        }
        return result
    }

    /**
     * MySQL 날짜 포맷 → Oracle 날짜 포맷 변환
     * 플레이스홀더를 사용하여 변환된 값이 다시 변환되는 것을 방지
     */
    private fun convertMySqlFormatToOracle(mysqlFormat: String): String {
        var result = mysqlFormat
        val placeholders = mutableMapOf<String, String>()
        var placeholderIndex = 0

        // 길이가 긴 것부터 변환
        val sortedMappings = mySqlToOracleDateFormat.entries.sortedByDescending { it.key.length }
        for ((mysql, oracle) in sortedMappings) {
            val placeholder = "\u0000PH${placeholderIndex++}\u0000"
            placeholders[placeholder] = oracle
            result = result.replace(mysql, placeholder)
        }

        // 플레이스홀더를 실제 값으로 대체
        for ((placeholder, oracle) in placeholders) {
            result = result.replace(placeholder, oracle)
        }
        return result
    }

    /**
     * MySQL 날짜 포맷 → PostgreSQL 날짜 포맷 변환
     */
    private fun convertMySqlFormatToPostgreSql(mysqlFormat: String): String {
        // MySQL → Oracle 변환 (플레이스홀더 사용)
        val oracleFormat = convertMySqlFormatToOracle(mysqlFormat)

        // Oracle → PostgreSQL 변환 (플레이스홀더 사용)
        var result = oracleFormat
        val placeholders = mutableMapOf<String, String>()
        var placeholderIndex = 0

        val sortedMappings = oracleToPostgreSqlDateFormat.entries.sortedByDescending { it.key.length }
        for ((oracle, pg) in sortedMappings) {
            val placeholder = "\u0000PH${placeholderIndex++}\u0000"
            placeholders[placeholder] = pg
            result = result.replace(oracle, placeholder, ignoreCase = true)
        }

        for ((placeholder, pg) in placeholders) {
            result = result.replace(placeholder, pg)
        }
        return result
    }

    /**
     * PostgreSQL 날짜 포맷 → MySQL 날짜 포맷 변환
     */
    private fun convertPostgreSqlFormatToMySql(pgFormat: String): String {
        // PostgreSQL → Oracle 역변환 (플레이스홀더 사용)
        var result = pgFormat
        val placeholders = mutableMapOf<String, String>()
        var placeholderIndex = 0

        val pgToOracle = oracleToPostgreSqlDateFormat.entries.associate { it.value to it.key }
        val sortedMappings = pgToOracle.entries.sortedByDescending { it.key.length }
        for ((pg, oracle) in sortedMappings) {
            val placeholder = "\u0000PH${placeholderIndex++}\u0000"
            placeholders[placeholder] = oracle
            result = result.replace(pg, placeholder, ignoreCase = true)
        }

        for ((placeholder, oracle) in placeholders) {
            result = result.replace(placeholder, oracle)
        }

        // Oracle → MySQL 변환
        return convertOracleFormatToMySql(result)
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