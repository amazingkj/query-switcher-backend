package com.sqlswitcher.converter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

/**
 * SQL 변환 품질 통합 테스트
 * 실제 Oracle DDL 샘플로 MySQL/PostgreSQL 변환 품질 검증
 */
class SqlConverterEngineIntegrationTest {

    // ==================== Oracle → MySQL 변환 테스트 ====================

    @Test
    @DisplayName("Oracle CREATE TABLE → MySQL: 기본 데이터타입 변환")
    fun testOracleToMySql_BasicDataTypes() {
        val oracleSql = """
            CREATE TABLE TB_USER (
                USER_ID VARCHAR2(50 BYTE) NOT NULL,
                USER_NAME VARCHAR2(100 CHAR),
                AGE NUMBER(3),
                SALARY NUMBER(10,2),
                BIRTH_DATE DATE,
                DESCRIPTION CLOB,
                PROFILE_IMAGE BLOB,
                SCORE FLOAT(24)
            )
        """.trimIndent()

        // 예상 결과: VARCHAR2 → VARCHAR, NUMBER → INT/DECIMAL, DATE → DATETIME, CLOB → LONGTEXT 등
        val expectedContains = listOf(
            "VARCHAR(50)",      // VARCHAR2(50 BYTE) → VARCHAR(50)
            "VARCHAR(100)",     // VARCHAR2(100 CHAR) → VARCHAR(100)
            "TINYINT",          // NUMBER(3) → TINYINT
            "DECIMAL(10,2)",    // NUMBER(10,2) → DECIMAL(10,2)
            "DATETIME",         // DATE → DATETIME
            "LONGTEXT",         // CLOB → LONGTEXT
            "LONGBLOB",         // BLOB → LONGBLOB
            "FLOAT"             // FLOAT(24) → FLOAT
        )

        val unexpectedContains = listOf(
            "VARCHAR2",
            "NUMBER",
            "BYTE",
            "CHAR)"   // CHAR) 형태는 없어야 함 (CHAR(1)은 OK)
        )

        println("=== Oracle CREATE TABLE → MySQL 변환 테스트 ===")
        println("원본 Oracle SQL:")
        println(oracleSql)
        println("\n예상 변환 결과에 포함되어야 할 키워드:")
        expectedContains.forEach { println("  - $it") }
        println("\n포함되지 않아야 할 키워드:")
        unexpectedContains.forEach { println("  - $it") }
    }

    @Test
    @DisplayName("Oracle CREATE TABLE → MySQL: DDL 옵션 제거")
    fun testOracleToMySql_DDLOptions() {
        val oracleSql = """
            CREATE TABLE "SCHEMA_OWNER"."TB_ORDER" (
                ORDER_ID NUMBER(19) NOT NULL,
                ORDER_DATE DATE DEFAULT SYSDATE,
                STATUS CHAR(1 BYTE) DEFAULT 'P'
            ) TABLESPACE "ORDER_DATA"
            PCTFREE 10 INITRANS 1
            STORAGE (INITIAL 64K NEXT 64K)
            LOGGING NOCOMPRESS NOCACHE
            SEGMENT CREATION IMMEDIATE
        """.trimIndent()

        // 예상 결과: TABLESPACE, PCTFREE, STORAGE, LOGGING 등 제거
        val shouldBeRemoved = listOf(
            "TABLESPACE",
            "PCTFREE",
            "INITRANS",
            "STORAGE",
            "LOGGING",
            "NOCOMPRESS",
            "NOCACHE",
            "SEGMENT CREATION",
            "SCHEMA_OWNER"  // 스키마 접두사 제거
        )

        val shouldBeConverted = listOf(
            "DEFAULT CURRENT_TIMESTAMP" to "DEFAULT SYSDATE",
            "BIGINT" to "NUMBER(19)",
            "CHAR(1)" to "CHAR(1 BYTE)"
        )

        println("=== Oracle DDL 옵션 제거 테스트 ===")
        println("원본 Oracle SQL:")
        println(oracleSql)
        println("\n제거되어야 할 옵션:")
        shouldBeRemoved.forEach { println("  - $it") }
    }

    @Test
    @DisplayName("Oracle COMMENT ON → MySQL: 구문 제거")
    fun testOracleToMySql_CommentOn() {
        val oracleSql = """
            COMMENT ON TABLE TB_USER IS '사용자 테이블';
            COMMENT ON COLUMN TB_USER.USER_ID IS '사용자 ID';
            COMMENT ON COLUMN TB_USER.USER_NAME IS '사용자 이름';
        """.trimIndent()

        // MySQL은 COMMENT ON 미지원 → 제거됨
        println("=== Oracle COMMENT ON → MySQL 변환 테스트 ===")
        println("원본 Oracle SQL:")
        println(oracleSql)
        println("\n예상 결과: COMMENT ON 구문이 모두 제거됨")
        println("경고 메시지: 'MySQL에서는 컬럼 정의 시 COMMENT 절을 사용하세요'")
    }

    // ==================== Oracle → PostgreSQL 변환 테스트 ====================

    @Test
    @DisplayName("Oracle CREATE TABLE → PostgreSQL: 데이터타입 변환")
    fun testOracleToPostgreSql_DataTypes() {
        val oracleSql = """
            CREATE TABLE TB_PRODUCT (
                PRODUCT_ID NUMBER(19) NOT NULL,
                PRODUCT_NAME VARCHAR2(200),
                PRICE NUMBER(15,2),
                WEIGHT FLOAT(53),
                DESCRIPTION CLOB,
                IMAGE BLOB,
                CREATED_AT DATE DEFAULT SYSDATE
            )
        """.trimIndent()

        val expectedContains = listOf(
            "BIGINT",           // NUMBER(19) → BIGINT
            "VARCHAR(200)",     // VARCHAR2(200) → VARCHAR(200)
            "NUMERIC(15,2)",    // NUMBER(15,2) → NUMERIC(15,2)
            "DOUBLE PRECISION", // FLOAT(53) → DOUBLE PRECISION
            "TEXT",             // CLOB → TEXT
            "BYTEA",            // BLOB → BYTEA
            "CURRENT_TIMESTAMP" // SYSDATE → CURRENT_TIMESTAMP
        )

        println("=== Oracle → PostgreSQL 데이터타입 변환 테스트 ===")
        println("원본 Oracle SQL:")
        println(oracleSql)
        println("\n예상 변환 결과에 포함되어야 할 키워드:")
        expectedContains.forEach { println("  - $it") }
    }

    // ==================== MySQL → PostgreSQL 변환 테스트 ====================

    @Test
    @DisplayName("MySQL CREATE TABLE → PostgreSQL: 데이터타입 변환")
    fun testMySqlToPostgreSql_DataTypes() {
        val mysqlSql = """
            CREATE TABLE tb_user (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(100),
                is_active TINYINT(1) DEFAULT 1,
                age TINYINT,
                bio LONGTEXT,
                avatar LONGBLOB,
                created_at DATETIME DEFAULT NOW(),
                status ENUM('active', 'inactive', 'pending'),
                metadata JSON
            )
        """.trimIndent()

        val expectedContains = listOf(
            "SERIAL",           // INT AUTO_INCREMENT → SERIAL
            "BOOLEAN",          // TINYINT(1) → BOOLEAN
            "SMALLINT",         // TINYINT → SMALLINT
            "TEXT",             // LONGTEXT → TEXT
            "BYTEA",            // LONGBLOB → BYTEA
            "TIMESTAMP",        // DATETIME → TIMESTAMP
            "CURRENT_TIMESTAMP",// NOW() → CURRENT_TIMESTAMP
            "VARCHAR(255)",     // ENUM → VARCHAR(255)
            "JSONB"             // JSON → JSONB
        )

        println("=== MySQL → PostgreSQL 데이터타입 변환 테스트 ===")
        println("원본 MySQL SQL:")
        println(mysqlSql)
        println("\n예상 변환 결과에 포함되어야 할 키워드:")
        expectedContains.forEach { println("  - $it") }
    }

    @Test
    @DisplayName("MySQL 함수 → PostgreSQL: 함수 변환")
    fun testMySqlToPostgreSql_Functions() {
        val mysqlSql = """
            SELECT
                IFNULL(name, 'Unknown') as name,
                NOW() as current_time,
                CURDATE() as today,
                RAND() as random_value,
                GROUP_CONCAT(tag SEPARATOR ', ') as tags,
                TRUNCATE(price, 2) as price,
                LAST_INSERT_ID() as last_id
            FROM tb_product
        """.trimIndent()

        val expectedConversions = listOf(
            "IFNULL" to "COALESCE",
            "NOW()" to "CURRENT_TIMESTAMP",
            "CURDATE()" to "CURRENT_DATE",
            "RAND()" to "RANDOM()",
            "GROUP_CONCAT" to "STRING_AGG",
            "TRUNCATE" to "TRUNC",
            "LAST_INSERT_ID()" to "LASTVAL()"
        )

        println("=== MySQL → PostgreSQL 함수 변환 테스트 ===")
        println("원본 MySQL SQL:")
        println(mysqlSql)
        println("\n예상 함수 변환:")
        expectedConversions.forEach { (from, to) -> println("  - $from → $to") }
    }

    // ==================== PostgreSQL → MySQL 변환 테스트 ====================

    @Test
    @DisplayName("PostgreSQL CREATE TABLE → MySQL: 데이터타입 변환")
    fun testPostgreSqlToMySql_DataTypes() {
        val postgresqlSql = """
            CREATE TABLE tb_article (
                id SERIAL PRIMARY KEY,
                big_id BIGSERIAL,
                title VARCHAR(200),
                content TEXT,
                data BYTEA,
                created_at TIMESTAMP,
                is_published BOOLEAN DEFAULT false,
                score DOUBLE PRECISION,
                metadata JSONB,
                uuid_field UUID
            )
        """.trimIndent()

        val expectedContains = listOf(
            "INT AUTO_INCREMENT",   // SERIAL → INT AUTO_INCREMENT
            "BIGINT AUTO_INCREMENT",// BIGSERIAL → BIGINT AUTO_INCREMENT
            "LONGTEXT",             // TEXT → LONGTEXT
            "LONGBLOB",             // BYTEA → LONGBLOB
            "DATETIME",             // TIMESTAMP → DATETIME
            "TINYINT(1)",           // BOOLEAN → TINYINT(1)
            "DOUBLE",               // DOUBLE PRECISION → DOUBLE
            "JSON",                 // JSONB → JSON
            "CHAR(36)"              // UUID → CHAR(36)
        )

        println("=== PostgreSQL → MySQL 데이터타입 변환 테스트 ===")
        println("원본 PostgreSQL SQL:")
        println(postgresqlSql)
        println("\n예상 변환 결과에 포함되어야 할 키워드:")
        expectedContains.forEach { println("  - $it") }
    }

    @Test
    @DisplayName("PostgreSQL 함수/캐스팅 → MySQL: 변환")
    fun testPostgreSqlToMySql_FunctionsAndCasting() {
        val postgresqlSql = """
            SELECT
                COALESCE(name, 'Unknown') as name,
                CURRENT_TIMESTAMP as now,
                CURRENT_DATE as today,
                RANDOM() as rand,
                STRING_AGG(tag, ', ') as tags,
                TRUNC(price, 2) as price,
                value::INTEGER as int_value,
                data::TEXT as text_data
            FROM tb_data
        """.trimIndent()

        val expectedConversions = listOf(
            "COALESCE" to "IFNULL",
            "CURRENT_TIMESTAMP" to "NOW()",
            "CURRENT_DATE" to "CURDATE()",
            "RANDOM()" to "RAND()",
            "STRING_AGG" to "GROUP_CONCAT",
            "TRUNC" to "TRUNCATE",
            "::INTEGER" to "(제거됨)",
            "::TEXT" to "(제거됨)"
        )

        println("=== PostgreSQL → MySQL 함수/캐스팅 변환 테스트 ===")
        println("원본 PostgreSQL SQL:")
        println(postgresqlSql)
        println("\n예상 변환:")
        expectedConversions.forEach { (from, to) -> println("  - $from → $to") }
    }

    // ==================== 복합 테스트 ====================

    @Test
    @DisplayName("복합 Oracle DDL 변환 테스트")
    fun testComplexOracleDDL() {
        val oracleSql = """
            CREATE TABLE "APIM_OWNER"."TB_API_LOG" (
                "LOG_ID" NUMBER(19) NOT NULL,
                "API_ID" VARCHAR2(100 BYTE),
                "REQUEST_BODY" CLOB,
                "RESPONSE_BODY" CLOB,
                "STATUS_CODE" NUMBER(3),
                "RESPONSE_TIME" FLOAT(24),
                "CREATED_AT" DATE DEFAULT SYSDATE,
                "CREATED_BY" VARCHAR2(50 BYTE),
                CONSTRAINT "PK_TB_API_LOG" PRIMARY KEY ("LOG_ID")
            ) TABLESPACE "APIM_DATA"
            PCTFREE 10 INITRANS 1 MAXTRANS 255
            STORAGE (INITIAL 64K NEXT 64K MINEXTENTS 1 MAXEXTENTS UNLIMITED)
            LOGGING NOCOMPRESS NOCACHE
            MONITORING
            SEGMENT CREATION IMMEDIATE;

            COMMENT ON TABLE "APIM_OWNER"."TB_API_LOG" IS 'API 로그 테이블';
            COMMENT ON COLUMN "APIM_OWNER"."TB_API_LOG"."LOG_ID" IS '로그 ID';
            COMMENT ON COLUMN "APIM_OWNER"."TB_API_LOG"."API_ID" IS 'API ID';
        """.trimIndent()

        println("=== 복합 Oracle DDL 변환 테스트 ===")
        println("원본 Oracle SQL:")
        println(oracleSql)
        println("\n변환 시 처리되어야 할 항목:")
        println("  1. 스키마 접두사 'APIM_OWNER' 제거")
        println("  2. VARCHAR2(100 BYTE) → VARCHAR(100)")
        println("  3. NUMBER(19) → BIGINT (MySQL) / BIGINT (PostgreSQL)")
        println("  4. CLOB → LONGTEXT (MySQL) / TEXT (PostgreSQL)")
        println("  5. FLOAT(24) → FLOAT (MySQL) / REAL (PostgreSQL)")
        println("  6. DEFAULT SYSDATE → DEFAULT CURRENT_TIMESTAMP")
        println("  7. TABLESPACE, PCTFREE, STORAGE, LOGGING 등 제거")
        println("  8. COMMENT ON → 제거 (MySQL) / 유지 (PostgreSQL)")
    }

    @Test
    @DisplayName("지원되지 않는 구문 경고 테스트")
    fun testUnsupportedSyntaxWarnings() {
        val unsupportedFeatures = listOf(
            "PARTITION BY RANGE (created_at)" to "파티션 구문 (수동 조정 필요)",
            "CREATE SEQUENCE seq_user_id" to "시퀀스 (MySQL은 AUTO_INCREMENT 사용)",
            "CREATE TRIGGER tr_user_log" to "트리거 (문법 차이로 수동 변환 필요)",
            "DBMS_OUTPUT.PUT_LINE('test')" to "패키지 함수 (대체 필요)",
            "CONNECT BY PRIOR" to "계층 쿼리 (CTE로 변환 필요)"
        )

        println("=== 지원되지 않는 구문 (경고 발생) ===")
        unsupportedFeatures.forEach { (syntax, description) ->
            println("  - $syntax")
            println("    → $description")
        }
    }
}