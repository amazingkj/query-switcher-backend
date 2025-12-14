package com.sqlswitcher.parser.error

/**
 * 데이터베이스별 함수 대안 정보를 제공하는 레지스트리
 * 변환 불가능한 함수에 대해 대안을 제안합니다.
 */
object FunctionAlternativeRegistry {

    /**
     * Oracle 함수 → 대안 매핑
     */
    private val oracleAlternatives = mapOf(
        // 날짜/시간 함수
        "SYSDATE" to FunctionAlternative(
            mysql = "NOW() 또는 CURRENT_TIMESTAMP",
            postgresql = "NOW() 또는 CURRENT_TIMESTAMP",
            description = "현재 날짜와 시간을 반환"
        ),
        "SYSTIMESTAMP" to FunctionAlternative(
            mysql = "NOW(6) 또는 CURRENT_TIMESTAMP(6)",
            postgresql = "CURRENT_TIMESTAMP 또는 clock_timestamp()",
            description = "현재 타임스탬프(마이크로초 포함)"
        ),
        "ADD_MONTHS" to FunctionAlternative(
            mysql = "DATE_ADD(date, INTERVAL n MONTH)",
            postgresql = "date + INTERVAL 'n months'",
            description = "날짜에 개월 수 추가"
        ),
        "MONTHS_BETWEEN" to FunctionAlternative(
            mysql = "TIMESTAMPDIFF(MONTH, date1, date2)",
            postgresql = "EXTRACT(MONTH FROM AGE(date2, date1))",
            description = "두 날짜 사이의 개월 수"
        ),
        "TRUNC" to FunctionAlternative(
            mysql = "DATE(date) 또는 TRUNCATE(num, places)",
            postgresql = "DATE_TRUNC('day', timestamp) 또는 TRUNC(num, places)",
            description = "날짜 절단 또는 숫자 절삭"
        ),
        "TO_DATE" to FunctionAlternative(
            mysql = "STR_TO_DATE(str, format)",
            postgresql = "TO_DATE(str, format) - 형식 문자 다름",
            description = "문자열을 날짜로 변환 (형식 문자 확인 필요)"
        ),
        "TO_CHAR" to FunctionAlternative(
            mysql = "DATE_FORMAT(date, format) 또는 CAST",
            postgresql = "TO_CHAR(date, format) - 형식 문자 일부 다름",
            description = "날짜/숫자를 문자열로 변환"
        ),

        // NULL 처리 함수
        "NVL" to FunctionAlternative(
            mysql = "IFNULL(expr1, expr2) 또는 COALESCE",
            postgresql = "COALESCE(expr1, expr2)",
            description = "NULL 대체값 지정"
        ),
        "NVL2" to FunctionAlternative(
            mysql = "IF(expr1 IS NOT NULL, expr2, expr3)",
            postgresql = "CASE WHEN expr1 IS NOT NULL THEN expr2 ELSE expr3 END",
            description = "NULL 여부에 따른 조건 분기"
        ),
        "DECODE" to FunctionAlternative(
            mysql = "CASE WHEN 또는 IF/ELT 조합",
            postgresql = "CASE WHEN expr = val1 THEN result1 ... END",
            description = "값 비교 조건 분기 (CASE WHEN 사용 권장)"
        ),

        // 문자열 함수
        "SUBSTR" to FunctionAlternative(
            mysql = "SUBSTRING(str, pos, len)",
            postgresql = "SUBSTRING(str FROM pos FOR len)",
            description = "부분 문자열 추출 (인덱스 1부터 시작)"
        ),
        "INSTR" to FunctionAlternative(
            mysql = "LOCATE(substr, str) 또는 INSTR(str, substr)",
            postgresql = "POSITION(substr IN str) 또는 STRPOS(str, substr)",
            description = "문자열 내 위치 찾기"
        ),
        "LENGTH" to FunctionAlternative(
            mysql = "LENGTH(str) - 바이트 수, CHAR_LENGTH(str) - 문자 수",
            postgresql = "LENGTH(str) 또는 CHAR_LENGTH(str)",
            description = "문자열 길이"
        ),
        "LPAD" to FunctionAlternative(
            mysql = "LPAD(str, len, padstr)",
            postgresql = "LPAD(str, len, padstr)",
            description = "왼쪽 패딩 (호환됨)"
        ),
        "RPAD" to FunctionAlternative(
            mysql = "RPAD(str, len, padstr)",
            postgresql = "RPAD(str, len, padstr)",
            description = "오른쪽 패딩 (호환됨)"
        ),
        "INITCAP" to FunctionAlternative(
            mysql = "사용자 정의 함수 필요",
            postgresql = "INITCAP(str)",
            description = "단어 첫 글자 대문자 변환"
        ),

        // 숫자 함수
        "ROUND" to FunctionAlternative(
            mysql = "ROUND(num, places)",
            postgresql = "ROUND(num, places)",
            description = "반올림 (호환됨)"
        ),
        "CEIL" to FunctionAlternative(
            mysql = "CEIL(num) 또는 CEILING(num)",
            postgresql = "CEIL(num)",
            description = "올림 (호환됨)"
        ),
        "MOD" to FunctionAlternative(
            mysql = "MOD(n, m) 또는 n % m",
            postgresql = "MOD(n, m) 또는 n % m",
            description = "나머지 연산 (호환됨)"
        ),

        // 집계/분석 함수
        "ROWNUM" to FunctionAlternative(
            mysql = "ROW_NUMBER() OVER() 또는 LIMIT",
            postgresql = "ROW_NUMBER() OVER() 또는 LIMIT",
            description = "행 번호 (윈도우 함수 권장)"
        ),
        "ROWID" to FunctionAlternative(
            mysql = "AUTO_INCREMENT 컬럼 사용",
            postgresql = "ctid (시스템 컬럼, 권장하지 않음)",
            description = "행 식별자 (명시적 PK 사용 권장)"
        ),
        "LISTAGG" to FunctionAlternative(
            mysql = "GROUP_CONCAT(col SEPARATOR ',')",
            postgresql = "STRING_AGG(col, ',')",
            description = "문자열 집계"
        ),
        "WM_CONCAT" to FunctionAlternative(
            mysql = "GROUP_CONCAT(col)",
            postgresql = "STRING_AGG(col::text, ',')",
            description = "문자열 집계 (비표준, LISTAGG 권장)"
        ),

        // 시퀀스
        "SEQUENCE.NEXTVAL" to FunctionAlternative(
            mysql = "AUTO_INCREMENT (테이블 정의 시)",
            postgresql = "nextval('sequence_name')",
            description = "시퀀스 다음 값"
        ),
        "SEQUENCE.CURRVAL" to FunctionAlternative(
            mysql = "LAST_INSERT_ID() (제한적)",
            postgresql = "currval('sequence_name')",
            description = "시퀀스 현재 값"
        ),

        // 패키지 함수
        "DBMS_RANDOM.VALUE" to FunctionAlternative(
            mysql = "RAND()",
            postgresql = "RANDOM()",
            description = "0~1 사이 난수 생성"
        ),
        "DBMS_OUTPUT.PUT_LINE" to FunctionAlternative(
            mysql = "SELECT 문 사용",
            postgresql = "RAISE NOTICE '%', message",
            description = "디버그 출력"
        ),
        "UTL_RAW.CAST_TO_VARCHAR2" to FunctionAlternative(
            mysql = "CONVERT(raw USING charset)",
            postgresql = "ENCODE(raw, 'escape')::text",
            description = "RAW → VARCHAR 변환"
        )
    )

    /**
     * MySQL 함수 → 대안 매핑
     */
    private val mysqlAlternatives = mapOf(
        "NOW" to FunctionAlternative(
            oracle = "SYSDATE 또는 SYSTIMESTAMP",
            postgresql = "NOW() 또는 CURRENT_TIMESTAMP",
            description = "현재 날짜와 시간"
        ),
        "IFNULL" to FunctionAlternative(
            oracle = "NVL(expr1, expr2)",
            postgresql = "COALESCE(expr1, expr2)",
            description = "NULL 대체값"
        ),
        "IF" to FunctionAlternative(
            oracle = "CASE WHEN condition THEN val1 ELSE val2 END",
            postgresql = "CASE WHEN condition THEN val1 ELSE val2 END",
            description = "조건 분기"
        ),
        "DATE_FORMAT" to FunctionAlternative(
            oracle = "TO_CHAR(date, format) - 형식 문자 다름",
            postgresql = "TO_CHAR(date, format) - 형식 문자 다름",
            description = "날짜 형식화 (형식 문자 확인 필요)"
        ),
        "STR_TO_DATE" to FunctionAlternative(
            oracle = "TO_DATE(str, format)",
            postgresql = "TO_DATE(str, format) 또는 TO_TIMESTAMP",
            description = "문자열 → 날짜 변환"
        ),
        "GROUP_CONCAT" to FunctionAlternative(
            oracle = "LISTAGG(col, ',') WITHIN GROUP (ORDER BY col)",
            postgresql = "STRING_AGG(col, ',')",
            description = "문자열 집계"
        ),
        "LIMIT" to FunctionAlternative(
            oracle = "FETCH FIRST n ROWS ONLY 또는 ROWNUM",
            postgresql = "LIMIT n",
            description = "결과 행 수 제한"
        ),
        "AUTO_INCREMENT" to FunctionAlternative(
            oracle = "GENERATED BY DEFAULT AS IDENTITY",
            postgresql = "SERIAL 또는 GENERATED AS IDENTITY",
            description = "자동 증가 컬럼"
        ),
        "CONCAT_WS" to FunctionAlternative(
            oracle = "문자열 || 연산자 조합",
            postgresql = "CONCAT_WS(sep, str1, str2, ...)",
            description = "구분자와 함께 문자열 연결"
        )
    )

    /**
     * PostgreSQL 함수 → 대안 매핑
     */
    private val postgresqlAlternatives = mapOf(
        "NOW" to FunctionAlternative(
            oracle = "SYSDATE 또는 SYSTIMESTAMP",
            mysql = "NOW() 또는 CURRENT_TIMESTAMP",
            description = "현재 날짜와 시간"
        ),
        "COALESCE" to FunctionAlternative(
            oracle = "NVL(expr1, expr2) 또는 COALESCE",
            mysql = "IFNULL(expr1, expr2) 또는 COALESCE",
            description = "NULL 대체값"
        ),
        "STRING_AGG" to FunctionAlternative(
            oracle = "LISTAGG(col, ',') WITHIN GROUP (ORDER BY col)",
            mysql = "GROUP_CONCAT(col SEPARATOR ',')",
            description = "문자열 집계"
        ),
        "SERIAL" to FunctionAlternative(
            oracle = "GENERATED BY DEFAULT AS IDENTITY",
            mysql = "AUTO_INCREMENT",
            description = "자동 증가 컬럼"
        ),
        "ILIKE" to FunctionAlternative(
            oracle = "UPPER(col) LIKE UPPER(pattern)",
            mysql = "col LIKE pattern (기본 대소문자 무시) 또는 LOWER",
            description = "대소문자 무시 패턴 매칭"
        ),
        "::" to FunctionAlternative(
            oracle = "CAST(expr AS type) 또는 TO_타입()",
            mysql = "CAST(expr AS type) 또는 CONVERT()",
            description = "타입 캐스팅"
        ),
        "RETURNING" to FunctionAlternative(
            oracle = "RETURNING INTO 절 (PL/SQL 내)",
            mysql = "LAST_INSERT_ID() (INSERT 후)",
            description = "DML 결과 반환"
        ),
        "GENERATE_SERIES" to FunctionAlternative(
            oracle = "CONNECT BY LEVEL 또는 재귀 CTE",
            mysql = "재귀 CTE 또는 미리 정의된 숫자 테이블",
            description = "연속 숫자 생성"
        )
    )

    /**
     * 함수 대안 정보 조회
     */
    fun getAlternative(functionName: String, sourceDialect: String): FunctionAlternative? {
        val normalizedName = functionName.uppercase().trim()
        return when (sourceDialect.uppercase()) {
            "ORACLE" -> oracleAlternatives[normalizedName]
            "MYSQL" -> mysqlAlternatives[normalizedName]
            "POSTGRESQL", "POSTGRES" -> postgresqlAlternatives[normalizedName]
            else -> null
        }
    }

    /**
     * 특정 함수에 대한 타겟 방언의 대안 문자열 반환
     */
    fun getAlternativeFor(functionName: String, sourceDialect: String, targetDialect: String): String? {
        val alternative = getAlternative(functionName, sourceDialect) ?: return null
        return when (targetDialect.uppercase()) {
            "ORACLE" -> alternative.oracle
            "MYSQL" -> alternative.mysql
            "POSTGRESQL", "POSTGRES" -> alternative.postgresql
            else -> null
        }
    }

    /**
     * 함수 대안 제안 메시지 생성
     */
    fun buildSuggestionMessage(functionName: String, sourceDialect: String, targetDialect: String): String? {
        val alternative = getAlternative(functionName, sourceDialect) ?: return null
        val targetAlt = getAlternativeFor(functionName, sourceDialect, targetDialect) ?: return null

        return buildString {
            appendLine("'$functionName' 함수는 $targetDialect 에서 다음으로 대체할 수 있습니다:")
            appendLine("  → $targetAlt")
            if (alternative.description.isNotEmpty()) {
                appendLine("  설명: ${alternative.description}")
            }
        }
    }
}

/**
 * 함수 대안 정보
 */
data class FunctionAlternative(
    val oracle: String? = null,
    val mysql: String? = null,
    val postgresql: String? = null,
    val description: String = ""
)