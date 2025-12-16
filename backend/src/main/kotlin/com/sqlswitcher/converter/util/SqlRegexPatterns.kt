package com.sqlswitcher.converter.util

/**
 * SQL 변환에 사용되는 공통 정규식 패턴들
 *
 * 성능 최적화를 위해 정규식 패턴을 미리 컴파일하여 재사용합니다.
 */
object SqlRegexPatterns {

    // ========================================
    // Oracle 관련 패턴
    // ========================================

    /** PARTITION BY (RANGE|LIST|HASH) 패턴 */
    val PARTITION_BY = Regex("""PARTITION\s+BY\s+(RANGE|LIST|HASH)""", RegexOption.IGNORE_CASE)

    /** LOCAL 인덱스 패턴 */
    val LOCAL_INDEX = Regex("""\s+LOCAL\s*(\([^)]*\))?""", RegexOption.IGNORE_CASE)

    /** GLOBAL 인덱스 패턴 */
    val GLOBAL_INDEX = Regex("""\s+GLOBAL\s*""", RegexOption.IGNORE_CASE)

    /** LOB 저장소 (SECUREFILE/BASICFILE) 패턴 */
    val LOB_STORAGE = Regex("""\s+(SECUREFILE|BASICFILE)\s+""", RegexOption.IGNORE_CASE)

    /** TABLESPACE 패턴 (따옴표 포함 가능) */
    val TABLESPACE = Regex("""\s*TABLESPACE\s+["']?\w+["']?""", RegexOption.IGNORE_CASE)

    /** STORAGE 절 패턴 */
    val STORAGE_CLAUSE = Regex("""\s*STORAGE\s*\([\s\S]*?\)""", RegexOption.IGNORE_CASE)

    /** 물리적 옵션 개별 패턴 (인라인 사용) */
    val PCTFREE = Regex("""\s*PCTFREE\s+\d+""", RegexOption.IGNORE_CASE)
    val PCTUSED = Regex("""\s*PCTUSED\s+\d+""", RegexOption.IGNORE_CASE)
    val INITRANS = Regex("""\s*INITRANS\s+\d+""", RegexOption.IGNORE_CASE)
    val MAXTRANS = Regex("""\s*MAXTRANS\s+\d+""", RegexOption.IGNORE_CASE)

    /** 물리적 옵션 라인 패턴 (PCTFREE, PCTUSED 등) - 레거시 호환 */
    val PHYSICAL_OPTIONS_LINE = Regex(
        """^\s*(PCTFREE|PCTUSED|INITRANS|MAXTRANS|LOGGING|NOLOGGING)(\s+\d+)?\s*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    /** 제약조건 상태 패턴 (ENABLE/DISABLE) */
    val CONSTRAINT_STATE = Regex("""\s+(ENABLE|DISABLE)\s+(VALIDATE|NOVALIDATE)?""", RegexOption.IGNORE_CASE)

    /** COMPRESS/NOCOMPRESS 개별 패턴 */
    val COMPRESS = Regex("""\s*(NO)?COMPRESS(\s+\d+)?""", RegexOption.IGNORE_CASE)

    /** COMPRESS 라인 패턴 - 레거시 호환 */
    val COMPRESS_LINE = Regex(
        """^\s*(COMPRESS|NOCOMPRESS)(\s+\d+)?\s*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    /** LOGGING/NOLOGGING 개별 패턴 */
    val LOGGING = Regex("""\s*(NO)?LOGGING\b""", RegexOption.IGNORE_CASE)

    /** CACHE/NOCACHE 개별 패턴 */
    val CACHE = Regex("""\s*(NO)?CACHE\b""", RegexOption.IGNORE_CASE)

    /** MONITORING/NOMONITORING 개별 패턴 */
    val MONITORING = Regex("""\s*(NO)?MONITORING\b""", RegexOption.IGNORE_CASE)

    /** SEGMENT CREATION 패턴 */
    val SEGMENT_CREATION = Regex("""\s*SEGMENT\s+CREATION\s+(IMMEDIATE|DEFERRED)""", RegexOption.IGNORE_CASE)

    /** ROW MOVEMENT 패턴 */
    val ROW_MOVEMENT = Regex("""\s*(ENABLE|DISABLE)\s+ROW\s+MOVEMENT""", RegexOption.IGNORE_CASE)

    /** PARALLEL 옵션 패턴 */
    val PARALLEL = Regex("""\s*(NO)?PARALLEL(\s+\d+)?""", RegexOption.IGNORE_CASE)

    /** Oracle DDL 옵션 전체 블록 패턴 (닫는 괄호 뒤의 모든 Oracle 전용 옵션들) */
    val ORACLE_DDL_OPTIONS_BLOCK = Regex(
        """\)\s*(TABLESPACE\s+["']?\w+["']?|PCTFREE\s+\d+|PCTUSED\s+\d+|INITRANS\s+\d+|MAXTRANS\s+\d+|(NO)?LOGGING|(NO)?COMPRESS(\s+\d+)?|(NO)?CACHE|(NO)?MONITORING|SEGMENT\s+CREATION\s+(IMMEDIATE|DEFERRED)|(ENABLE|DISABLE)\s+ROW\s+MOVEMENT|(NO)?PARALLEL(\s+\d+)?|STORAGE\s*\([^)]*\)|\s)+""",
        RegexOption.IGNORE_CASE
    )

    /** ROWNUM WHERE 절 패턴 */
    val ROWNUM_WHERE = Regex("""WHERE\s+ROWNUM\s*([<>=]+)\s*(\d+)""", RegexOption.IGNORE_CASE)

    /** DECODE 시작 패턴 */
    val DECODE_START = Regex("""DECODE\s*\(""", RegexOption.IGNORE_CASE)

    /** NVL2 함수 패턴 */
    val NVL2_FUNCTION = Regex("""NVL2\s*\(\s*([^,]+)\s*,\s*([^,]+)\s*,\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)

    /** PRIOR 패턴 (계층 쿼리) */
    val PRIOR_CLAUSE = Regex("""PRIOR\s+(\w+)\s*=\s*(\w+)""", RegexOption.IGNORE_CASE)

    /** TO_NUMBER 함수 패턴 */
    val TO_NUMBER = Regex("""TO_NUMBER\s*\(\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)

    // ========================================
    // MySQL 관련 패턴
    // ========================================

    /** LIMIT N 패턴 (OFFSET 없음) */
    val LIMIT_SIMPLE = Regex("""LIMIT\s+(\d+)(?!\s*,)""", RegexOption.IGNORE_CASE)

    /** LIMIT N, M 패턴 (MySQL 스타일) */
    val LIMIT_OFFSET_MYSQL = Regex("""LIMIT\s+(\d+)\s*,\s*(\d+)""", RegexOption.IGNORE_CASE)

    /** ENGINE 옵션 패턴 */
    val MYSQL_ENGINE = Regex("""ENGINE\s*=\s*\w+""", RegexOption.IGNORE_CASE)

    /** DEFAULT CHARSET 패턴 */
    val MYSQL_CHARSET = Regex("""DEFAULT\s+CHARSET\s*=\s*\w+""", RegexOption.IGNORE_CASE)

    /** COLLATE 패턴 */
    val MYSQL_COLLATE = Regex("""COLLATE\s*=?\s*\w+""", RegexOption.IGNORE_CASE)

    /** AUTO_INCREMENT 패턴 */
    val AUTO_INCREMENT = Regex("""AUTO_INCREMENT\s*=?\s*\d*""", RegexOption.IGNORE_CASE)

    /** COMMENT 패턴 */
    val MYSQL_COMMENT = Regex("""COMMENT\s*=?\s*'[^']*'""", RegexOption.IGNORE_CASE)

    /** IF 함수 패턴 */
    val IF_FUNCTION = Regex("""IF\s*\(\s*([^,]+)\s*,\s*([^,]+)\s*,\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)

    // ========================================
    // PostgreSQL 관련 패턴
    // ========================================

    /** 타입 캐스팅 패턴 (::) */
    val PG_TYPE_CAST = Regex("""(\w+)::(\w+)""")

    /** ILIKE 패턴 */
    val PG_ILIKE = Regex("""(\w+)\s+ILIKE\s+'([^']+)'""", RegexOption.IGNORE_CASE)

    /** RETURNING 절 패턴 */
    val PG_RETURNING = Regex("""\s+RETURNING\s+.+$""", RegexOption.IGNORE_CASE)

    /** EXCLUDED 참조 패턴 (UPSERT) */
    val PG_EXCLUDED = Regex("""EXCLUDED\.(\w+)""", RegexOption.IGNORE_CASE)

    // ========================================
    // 프로시저/함수 관련 패턴
    // ========================================

    /** IS/AS 패턴 */
    val PROCEDURE_IS_AS = Regex("""\)\s*(IS|AS)\s*""", RegexOption.IGNORE_CASE)

    /** END; 패턴 */
    val PROCEDURE_END = Regex("""END\s*;\s*$""", RegexOption.IGNORE_CASE)

    /** CREATE OR REPLACE PROCEDURE 패턴 */
    val CREATE_OR_REPLACE_PROCEDURE = Regex("""CREATE\s+OR\s+REPLACE\s+PROCEDURE""", RegexOption.IGNORE_CASE)

    /** CREATE PROCEDURE 패턴 */
    val CREATE_PROCEDURE = Regex("""CREATE\s+PROCEDURE""", RegexOption.IGNORE_CASE)

    /** CREATE OR REPLACE FUNCTION 패턴 */
    val CREATE_OR_REPLACE_FUNCTION = Regex("""CREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION""", RegexOption.IGNORE_CASE)

    /** ) BEGIN 패턴 */
    val PAREN_BEGIN = Regex("""\)\s*BEGIN""", RegexOption.IGNORE_CASE)

    /** AS $$ 패턴 (PostgreSQL) */
    val PG_AS_DOLLAR = Regex("""\s*AS\s*\$\$""", RegexOption.IGNORE_CASE)

    /** $$ LANGUAGE plpgsql 패턴 */
    val PG_DOLLAR_LANGUAGE = Regex("""\$\$\s*LANGUAGE\s+plpgsql\s*;?""", RegexOption.IGNORE_CASE)

    /** RETURNS VOID 패턴 */
    val RETURNS_VOID = Regex("""RETURNS\s+VOID\s*""", RegexOption.IGNORE_CASE)

    /** DECLARE 패턴 */
    val DECLARE_STATEMENT = Regex("""DECLARE\s+(.+?);""", RegexOption.IGNORE_CASE)

    // ========================================
    // MERGE/UPSERT 관련 패턴
    // ========================================

    /** VALUES (column) 패턴 */
    val VALUES_COLUMN = Regex("""VALUES\s*\(\s*(\w+)\s*\)""", RegexOption.IGNORE_CASE)

    /** column = table.column 패턴 */
    val COLUMN_ASSIGNMENT = Regex("""(\w+)\s*=\s*\w+\.(\w+)""")

    /** table1.col = table2.col 패턴 (조인 조건) */
    val JOIN_CONDITION = Regex("""(\w+)\.(\w+)\s*=\s*(\w+)\.(\w+)""")

    // ========================================
    // 파티션 관련 패턴
    // ========================================

    /** LESS THAN ( 패턴 */
    val LESS_THAN_PAREN = Regex("""LESS\s+THAN\s*\(""", RegexOption.IGNORE_CASE)

    /** LESS THAN 패턴 */
    val LESS_THAN = Regex("""LESS\s+THAN\s*""", RegexOption.IGNORE_CASE)

    /** IN ( 패턴 */
    val IN_PAREN = Regex("""IN\s*\(""", RegexOption.IGNORE_CASE)

    /** IN 패턴 */
    val IN_KEYWORD = Regex("""IN\s*""", RegexOption.IGNORE_CASE)

    // ========================================
    // 공백/포맷팅 패턴
    // ========================================

    /** 다중 빈 줄 패턴 */
    val MULTIPLE_BLANK_LINES = Regex("""\n\s*\n\s*\n""")

    /** 다중 공백 패턴 */
    val MULTIPLE_SPACES = Regex("""\s+""")

    /** 테이블 접두어 패턴 */
    val TABLE_PREFIX = Regex("""^\w+\.""")

    // ========================================
    // 유틸리티 함수
    // ========================================

    /**
     * 함수명으로 함수 시작 패턴 생성
     */
    fun functionStart(functionName: String): Regex {
        return Regex("""$functionName\s*\(""", RegexOption.IGNORE_CASE)
    }

    /**
     * 따옴표 문자로 패턴 생성
     */
    fun quotedIdentifier(quoteChars: String = "\"`'"): Regex {
        return Regex("""[$quoteChars]?(\w+)[$quoteChars]?""")
    }
}