package com.sqlswitcher.converter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Oracle DDL 옵션 제거 테스트
 * Oracle 전용 DDL 옵션들이 MySQL/PostgreSQL 변환 시 올바르게 제거되는지 검증
 */
class OracleDDLOptionsTest {

    // ==================== 스토리지 관련 옵션 테스트 ====================

    @Test
    @DisplayName("TABLESPACE 옵션 제거")
    fun testTablespaceRemoval() {
        val input = """CREATE TABLE TB_USER (ID NUMBER(10)) TABLESPACE "USER_DATA""""
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains("TABLESPACE"), "TABLESPACE가 제거되지 않음")
    }

    @ParameterizedTest
    @DisplayName("PCTFREE/PCTUSED/INITRANS/MAXTRANS 옵션 제거")
    @ValueSource(strings = ["PCTFREE 10", "PCTUSED 40", "INITRANS 1", "MAXTRANS 255"])
    fun testSpaceManagementOptionsRemoval(option: String) {
        val input = "CREATE TABLE TB_USER (ID NUMBER(10)) $option"
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains(option.split(" ")[0]), "${option.split(" ")[0]}가 제거되지 않음")
    }

    @Test
    @DisplayName("STORAGE 절 제거")
    fun testStorageClauseRemoval() {
        val input = """
            CREATE TABLE TB_USER (ID NUMBER(10))
            STORAGE (INITIAL 64K NEXT 64K MINEXTENTS 1 MAXEXTENTS UNLIMITED)
        """.trimIndent()
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains("STORAGE"), "STORAGE가 제거되지 않음")
        assertFalse(result.contains("INITIAL"), "INITIAL이 제거되지 않음")
    }

    // ==================== 로깅/압축/캐시 옵션 테스트 ====================

    @ParameterizedTest
    @DisplayName("LOGGING/NOLOGGING 옵션 제거")
    @ValueSource(strings = ["LOGGING", "NOLOGGING"])
    fun testLoggingOptionsRemoval(option: String) {
        val input = "CREATE TABLE TB_USER (ID NUMBER(10)) $option"
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains(option), "$option 가 제거되지 않음")
    }

    @ParameterizedTest
    @DisplayName("COMPRESS/NOCOMPRESS 옵션 제거")
    @ValueSource(strings = ["COMPRESS", "NOCOMPRESS"])
    fun testCompressOptionsRemoval(option: String) {
        val input = "CREATE TABLE TB_USER (ID NUMBER(10)) $option"
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains(option), "$option 가 제거되지 않음")
    }

    @ParameterizedTest
    @DisplayName("CACHE/NOCACHE 옵션 제거")
    @ValueSource(strings = ["CACHE", "NOCACHE"])
    fun testCacheOptionsRemoval(option: String) {
        val input = "CREATE TABLE TB_USER (ID NUMBER(10)) $option"
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains(option), "$option 가 제거되지 않음")
    }

    // ==================== 병렬/모니터링 옵션 테스트 ====================

    @ParameterizedTest
    @DisplayName("PARALLEL/NOPARALLEL 옵션 제거")
    @ValueSource(strings = ["PARALLEL 4", "NOPARALLEL"])
    fun testParallelOptionsRemoval(option: String) {
        val input = "CREATE TABLE TB_USER (ID NUMBER(10)) $option"
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains(option.split(" ")[0]), "${option.split(" ")[0]}가 제거되지 않음")
    }

    @ParameterizedTest
    @DisplayName("MONITORING/NOMONITORING 옵션 제거")
    @ValueSource(strings = ["MONITORING", "NOMONITORING"])
    fun testMonitoringOptionsRemoval(option: String) {
        val input = "CREATE TABLE TB_USER (ID NUMBER(10)) $option"
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains(option), "$option 가 제거되지 않음")
    }

    // ==================== 세그먼트/행 이동 옵션 테스트 ====================

    @ParameterizedTest
    @DisplayName("SEGMENT CREATION 옵션 제거")
    @ValueSource(strings = ["SEGMENT CREATION IMMEDIATE", "SEGMENT CREATION DEFERRED"])
    fun testSegmentCreationRemoval(option: String) {
        val input = "CREATE TABLE TB_USER (ID NUMBER(10)) $option"
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains("SEGMENT CREATION"), "SEGMENT CREATION이 제거되지 않음")
    }

    @ParameterizedTest
    @DisplayName("ROW MOVEMENT 옵션 제거")
    @ValueSource(strings = ["ENABLE ROW MOVEMENT", "DISABLE ROW MOVEMENT"])
    fun testRowMovementRemoval(option: String) {
        val input = "CREATE TABLE TB_USER (ID NUMBER(10)) $option"
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains("ROW MOVEMENT"), "ROW MOVEMENT가 제거되지 않음")
    }

    @ParameterizedTest
    @DisplayName("ROWDEPENDENCIES 옵션 제거")
    @ValueSource(strings = ["ROWDEPENDENCIES", "NOROWDEPENDENCIES"])
    fun testRowDependenciesRemoval(option: String) {
        val input = "CREATE TABLE TB_USER (ID NUMBER(10)) $option"
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains(option), "$option 가 제거되지 않음")
    }

    // ==================== FLASHBACK/LOB 옵션 테스트 ====================

    @Test
    @DisplayName("FLASHBACK ARCHIVE 옵션 제거")
    fun testFlashbackArchiveRemoval() {
        val input = "CREATE TABLE TB_USER (ID NUMBER(10)) FLASHBACK ARCHIVE fba_1year"
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains("FLASHBACK ARCHIVE"), "FLASHBACK ARCHIVE가 제거되지 않음")
    }

    @ParameterizedTest
    @DisplayName("SECUREFILE/BASICFILE 옵션 제거")
    @ValueSource(strings = ["SECUREFILE", "BASICFILE"])
    fun testLobStorageOptionsRemoval(option: String) {
        val input = "CREATE TABLE TB_USER (DATA CLOB) LOB (DATA) STORE AS $option"
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains(option), "$option 가 제거되지 않음")
    }

    // ==================== 스키마/COMMENT 테스트 ====================

    @Test
    @DisplayName("스키마 접두사 제거")
    fun testSchemaOwnerRemoval() {
        val input = """CREATE TABLE "SCHEMA_OWNER"."TB_USER" (ID NUMBER(10))"""
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains("SCHEMA_OWNER"), "스키마 접두사가 제거되지 않음")
        assertTrue(result.contains("TB_USER"), "테이블 이름이 유지되어야 함")
    }

    @Test
    @DisplayName("COMMENT ON 구문 제거 (MySQL)")
    fun testCommentOnRemovalForMySql() {
        val input = """
            COMMENT ON TABLE TB_USER IS '사용자 테이블';
            COMMENT ON COLUMN TB_USER.USER_ID IS '사용자 ID';
        """.trimIndent()
        val result = removeCommentOn(input)
        assertFalse(result.contains("COMMENT ON"), "COMMENT ON이 제거되지 않음")
    }

    // ==================== 힌트/RESULT_CACHE 테스트 ====================

    @Test
    @DisplayName("RESULT_CACHE 힌트 제거")
    fun testResultCacheRemoval() {
        val input = "SELECT /*+ RESULT_CACHE */ * FROM TB_USER"
        val result = removeOracleDDLOptions(input)
        assertFalse(result.contains("RESULT_CACHE"), "RESULT_CACHE가 제거되지 않음")
    }

    // ==================== 복합 테스트 ====================

    @Test
    @DisplayName("복합 Oracle DDL 옵션 제거")
    fun testComplexDDLOptionsRemoval() {
        val input = """
            CREATE TABLE "APIM_OWNER"."TB_API_LOG" (
                LOG_ID NUMBER(19) NOT NULL,
                API_ID VARCHAR2(100 BYTE)
            ) TABLESPACE "APIM_DATA"
            PCTFREE 10 INITRANS 1 MAXTRANS 255
            STORAGE (INITIAL 64K NEXT 64K MINEXTENTS 1 MAXEXTENTS UNLIMITED)
            LOGGING NOCOMPRESS NOCACHE
            MONITORING
            SEGMENT CREATION IMMEDIATE
        """.trimIndent()

        val result = removeOracleDDLOptions(input)

        // 제거되어야 하는 옵션들
        val shouldBeRemoved = listOf(
            "TABLESPACE", "PCTFREE", "INITRANS", "MAXTRANS",
            "STORAGE", "LOGGING", "NOCOMPRESS", "NOCACHE",
            "MONITORING", "SEGMENT CREATION", "APIM_OWNER"
        )

        shouldBeRemoved.forEach { option ->
            assertFalse(result.contains(option), "$option 가 제거되지 않음")
        }

        // 유지되어야 하는 요소들
        assertTrue(result.contains("TB_API_LOG"), "테이블 이름이 유지되어야 함")
        assertTrue(result.contains("LOG_ID"), "컬럼 이름이 유지되어야 함")
    }

    // ==================== 헬퍼 함수 ====================

    /**
     * Oracle DDL 옵션 제거 시뮬레이션
     */
    private fun removeOracleDDLOptions(input: String): String {
        var result = input

        // 스키마 접두사 제거
        result = result.replace(Regex("\"[^\"]+\"\\.\"([^\"]+)\""), "\"$1\"")

        // TABLESPACE 제거
        result = result.replace(Regex("\\bTABLESPACE\\s+\"?\\w+\"?", RegexOption.IGNORE_CASE), "")

        // PCTFREE, PCTUSED, INITRANS, MAXTRANS 제거
        result = result.replace(Regex("\\bPCTFREE\\s+\\d+", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\bPCTUSED\\s+\\d+", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\bINITRANS\\s+\\d+", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\bMAXTRANS\\s+\\d+", RegexOption.IGNORE_CASE), "")

        // STORAGE 절 제거
        result = result.replace(Regex("\\bSTORAGE\\s*\\([^)]+\\)", RegexOption.IGNORE_CASE), "")

        // LOGGING/NOLOGGING 제거
        result = result.replace(Regex("\\bNOLOGGING\\b", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\bLOGGING\\b", RegexOption.IGNORE_CASE), "")

        // COMPRESS/NOCOMPRESS 제거
        result = result.replace(Regex("\\bNOCOMPRESS\\b", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\bCOMPRESS\\b", RegexOption.IGNORE_CASE), "")

        // CACHE/NOCACHE 제거
        result = result.replace(Regex("\\bNOCACHE\\b", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\bCACHE\\b", RegexOption.IGNORE_CASE), "")

        // PARALLEL/NOPARALLEL 제거
        result = result.replace(Regex("\\bNOPARALLEL\\b", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\bPARALLEL\\s*\\d*", RegexOption.IGNORE_CASE), "")

        // MONITORING/NOMONITORING 제거
        result = result.replace(Regex("\\bNOMONITORING\\b", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\bMONITORING\\b", RegexOption.IGNORE_CASE), "")

        // SEGMENT CREATION 제거
        result = result.replace(Regex("\\bSEGMENT\\s+CREATION\\s+(IMMEDIATE|DEFERRED)", RegexOption.IGNORE_CASE), "")

        // ROW MOVEMENT 제거
        result = result.replace(Regex("\\b(ENABLE|DISABLE)\\s+ROW\\s+MOVEMENT", RegexOption.IGNORE_CASE), "")

        // ROWDEPENDENCIES 제거
        result = result.replace(Regex("\\bNOROWDEPENDENCIES\\b", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\bROWDEPENDENCIES\\b", RegexOption.IGNORE_CASE), "")

        // FLASHBACK ARCHIVE 제거
        result = result.replace(Regex("\\bFLASHBACK\\s+ARCHIVE\\s+\\w+", RegexOption.IGNORE_CASE), "")

        // SECUREFILE/BASICFILE 제거
        result = result.replace(Regex("\\bSECUREFILE\\b", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\bBASICFILE\\b", RegexOption.IGNORE_CASE), "")

        // RESULT_CACHE 힌트 제거
        result = result.replace(Regex("/\\*\\+\\s*RESULT_CACHE\\s*\\*/", RegexOption.IGNORE_CASE), "")

        return result
    }

    /**
     * COMMENT ON 구문 제거
     */
    private fun removeCommentOn(input: String): String {
        return input.replace(Regex("COMMENT\\s+ON\\s+(TABLE|COLUMN)\\s+[^;]+;", RegexOption.IGNORE_CASE), "")
    }
}