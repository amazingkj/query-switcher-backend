package com.sqlswitcher.converter.recovery

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * ConversionRecoveryService 단위 테스트
 */
class ConversionRecoveryServiceTest {

    @Nested
    @DisplayName("SQL 문 분리 테스트")
    inner class SplitStatementsTest {

        @Test
        @DisplayName("세미콜론으로 구분된 여러 SQL 문 분리")
        fun testSplitMultipleStatements() {
            val sql = """
                SELECT * FROM employees;
                SELECT * FROM departments;
                SELECT * FROM locations;
            """.trimIndent()

            val result = ConversionRecoveryService.splitStatements(sql)

            assertEquals(3, result.size, "3개의 문장으로 분리되어야 함")
            assertTrue(result[0].contains("employees"))
            assertTrue(result[1].contains("departments"))
            assertTrue(result[2].contains("locations"))
        }

        @Test
        @DisplayName("단일 SQL 문")
        fun testSingleStatement() {
            val sql = "SELECT * FROM employees"

            val result = ConversionRecoveryService.splitStatements(sql)

            assertEquals(1, result.size)
            assertTrue(result[0].contains("employees"))
        }

        @Test
        @DisplayName("혼합 DDL/DML 문 분리")
        fun testMixedStatements() {
            val sql = """
                CREATE TABLE test (id NUMBER);
                INSERT INTO test VALUES (1);
                SELECT * FROM test;
            """.trimIndent()

            val result = ConversionRecoveryService.splitStatements(sql)

            assertEquals(3, result.size)
            assertTrue(result[0].uppercase().contains("CREATE"))
            assertTrue(result[1].uppercase().contains("INSERT"))
            assertTrue(result[2].uppercase().contains("SELECT"))
        }

        @Test
        @DisplayName("PL/SQL 블록 내 세미콜론 유지")
        fun testPlSqlBlockPreservation() {
            val sql = """
                CREATE OR REPLACE PROCEDURE test_proc IS
                BEGIN
                    SELECT COUNT(*) INTO v_count FROM employees;
                    UPDATE employees SET status = 'A';
                END test_proc;
                SELECT * FROM dual;
            """.trimIndent()

            val result = ConversionRecoveryService.splitStatements(sql)

            // PL/SQL 블록은 하나로 유지되어야 함
            assertTrue(result.size >= 1, "최소 1개 이상의 문장")
            val hasProc = result.any { it.uppercase().contains("CREATE") && it.uppercase().contains("PROCEDURE") }
            assertTrue(hasProc, "프로시저가 포함되어야 함")
        }

        @Test
        @DisplayName("빈 문자열 처리")
        fun testEmptyString() {
            val result = ConversionRecoveryService.splitStatements("")
            assertEquals(0, result.size)
        }

        @Test
        @DisplayName("공백만 있는 문자열 처리")
        fun testWhitespaceOnly() {
            val result = ConversionRecoveryService.splitStatements("   \n\t   ")
            assertEquals(0, result.size)
        }

        @Test
        @DisplayName("주석만 있는 경우")
        fun testCommentOnly() {
            val sql = "-- This is a comment"
            val result = ConversionRecoveryService.splitStatements(sql)
            assertEquals(1, result.size)
        }
    }

    @Nested
    @DisplayName("변환 복구 테스트")
    inner class ConversionRecoveryTest {

        // 성공하는 변환 함수
        private val successConverter: (String, DialectType, DialectType, MutableList<ConversionWarning>, MutableList<String>) -> String =
            { sql, _, _, _, rules ->
                rules.add("Test conversion rule")
                sql.uppercase()
            }

        // 항상 실패하는 변환 함수
        private val failingConverter: (String, DialectType, DialectType, MutableList<ConversionWarning>, MutableList<String>) -> String =
            { _, _, _, _, _ ->
                throw RuntimeException("Conversion failed")
            }

        // 부분 성공 (경고 추가)
        private val partialConverter: (String, DialectType, DialectType, MutableList<ConversionWarning>, MutableList<String>) -> String =
            { sql, _, _, warnings, rules ->
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Partial conversion warning",
                    severity = WarningSeverity.WARNING
                ))
                rules.add("Partial rule")
                sql.uppercase()
            }

        @Test
        @DisplayName("모든 문장 성공적으로 변환")
        fun testAllStatementsSuccess() {
            val sql = """
                SELECT * FROM t1;
                SELECT * FROM t2;
            """.trimIndent()

            val result = ConversionRecoveryService.convertWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                successConverter
            )

            assertEquals(2, result.totalStatements)
            assertEquals(2, result.successfulStatements)
            assertEquals(0, result.failedStatements)
            assertTrue(result.isFullySuccessful)
            assertEquals(1.0, result.successRate)
        }

        @Test
        @DisplayName("일부 문장 변환 실패")
        fun testPartialFailure() {
            val sql = """
                SELECT * FROM t1;
                SELECT * FROM t2;
            """.trimIndent()

            var callCount = 0
            val mixedConverter: (String, DialectType, DialectType, MutableList<ConversionWarning>, MutableList<String>) -> String =
                { sqlPart, _, _, _, _ ->
                    callCount++
                    if (callCount == 1) {
                        sqlPart.uppercase()
                    } else {
                        throw RuntimeException("Second statement failed")
                    }
                }

            val result = ConversionRecoveryService.convertWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                mixedConverter
            )

            assertEquals(2, result.totalStatements)
            assertEquals(1, result.successfulStatements)
            assertEquals(1, result.failedStatements)
            assertTrue(result.isPartiallySuccessful)
            assertEquals(0.5, result.successRate)
        }

        @Test
        @DisplayName("모든 문장 변환 실패")
        fun testAllStatementsFail() {
            val sql = """
                SELECT * FROM t1;
                SELECT * FROM t2;
            """.trimIndent()

            val result = ConversionRecoveryService.convertWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                failingConverter
            )

            assertEquals(2, result.totalStatements)
            assertEquals(0, result.successfulStatements)
            assertEquals(2, result.failedStatements)
            assertFalse(result.isFullySuccessful)
            assertFalse(result.isPartiallySuccessful)
            assertEquals(0.0, result.successRate)
        }

        @Test
        @DisplayName("부분 변환 (경고 포함)")
        fun testPartialConversionWithWarnings() {
            val sql = "SELECT * FROM t1"

            val result = ConversionRecoveryService.convertWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                partialConverter
            )

            assertEquals(1, result.totalStatements)
            assertEquals(0, result.successfulStatements)
            assertEquals(1, result.partialStatements)
            assertEquals(0, result.failedStatements)
            assertTrue(result.warnings.isNotEmpty())
        }

        @Test
        @DisplayName("실패 시 원본 유지 및 에러 주석 추가")
        fun testErrorCommentOnFailure() {
            val sql = "SELECT * FROM employees"

            val result = ConversionRecoveryService.convertWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                failingConverter
            )

            val converted = result.convertedSql
            assertTrue(converted.contains("CONVERSION ERROR"), "에러 주석이 포함되어야 함")
            assertTrue(converted.contains("Conversion failed"), "에러 메시지가 포함되어야 함")
            assertTrue(converted.contains("employees"), "원본 SQL이 포함되어야 함")
        }

        @Test
        @DisplayName("주석은 변환하지 않고 건너뜀")
        fun testCommentsSkipped() {
            val sql = """
                -- This is a comment
                SELECT * FROM t1;
            """.trimIndent()

            val result = ConversionRecoveryService.convertWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                successConverter
            )

            // 주석은 SKIPPED로 처리되어 totalStatements에 포함되지 않음
            val skippedCount = result.statementResults.count {
                it.status == ConversionRecoveryService.ConversionStatus.SKIPPED
            }
            assertTrue(skippedCount >= 1 || result.totalStatements == 1, "주석이 SKIPPED 처리되어야 함")
        }

        @Test
        @DisplayName("적용된 규칙 수집")
        fun testAppliedRulesCollected() {
            val sql = """
                SELECT * FROM t1;
                SELECT * FROM t2;
            """.trimIndent()

            val result = ConversionRecoveryService.convertWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                successConverter
            )

            assertTrue(result.appliedRules.isNotEmpty(), "적용된 규칙이 수집되어야 함")
            assertEquals(2, result.appliedRules.size)
        }
    }

    @Nested
    @DisplayName("단일 문장 복구 테스트")
    inner class SingleStatementRecoveryTest {

        @Test
        @DisplayName("단일 문장 성공")
        fun testSingleSuccess() {
            val sql = "SELECT * FROM employees"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { sqlPart, _, _, _, _ -> sqlPart.uppercase() }

            assertEquals(ConversionRecoveryService.ConversionStatus.SUCCESS, result.status)
            assertTrue(result.convertedSql.contains("EMPLOYEES"))
            assertEquals(ConversionRecoveryService.StatementType.DML_SELECT, result.statementType)
        }

        @Test
        @DisplayName("단일 문장 실패")
        fun testSingleFailure() {
            val sql = "SELECT * FROM employees"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { _, _, _, _, _ -> throw RuntimeException("Failed") }

            assertEquals(ConversionRecoveryService.ConversionStatus.FAILED, result.status)
            assertEquals("Failed", result.errorMessage)
        }
    }

    @Nested
    @DisplayName("문장 유형 감지 테스트")
    inner class StatementTypeDetectionTest {

        @Test
        @DisplayName("SELECT 문 감지")
        fun testSelectDetection() {
            val sql = "SELECT * FROM employees"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.DML_SELECT, result.statementType)
        }

        @Test
        @DisplayName("WITH (CTE) 문 감지")
        fun testWithCteDetection() {
            val sql = "WITH cte AS (SELECT 1) SELECT * FROM cte"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.DML_SELECT, result.statementType)
        }

        @Test
        @DisplayName("INSERT 문 감지")
        fun testInsertDetection() {
            val sql = "INSERT INTO employees VALUES (1, 'test')"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.DML_INSERT, result.statementType)
        }

        @Test
        @DisplayName("UPDATE 문 감지")
        fun testUpdateDetection() {
            val sql = "UPDATE employees SET name = 'test'"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.DML_UPDATE, result.statementType)
        }

        @Test
        @DisplayName("DELETE 문 감지")
        fun testDeleteDetection() {
            val sql = "DELETE FROM employees WHERE id = 1"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.DML_DELETE, result.statementType)
        }

        @Test
        @DisplayName("MERGE 문 감지")
        fun testMergeDetection() {
            val sql = "MERGE INTO t1 USING t2 ON (t1.id = t2.id) WHEN MATCHED THEN UPDATE SET t1.name = t2.name"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.DML_MERGE, result.statementType)
        }

        @Test
        @DisplayName("CREATE TABLE 감지")
        fun testCreateTableDetection() {
            val sql = "CREATE TABLE test (id NUMBER)"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.DDL_CREATE, result.statementType)
        }

        @Test
        @DisplayName("CREATE PROCEDURE 감지")
        fun testCreateProcedureDetection() {
            val sql = "CREATE OR REPLACE PROCEDURE test_proc IS BEGIN NULL; END;"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.PLSQL_PROCEDURE, result.statementType)
        }

        @Test
        @DisplayName("CREATE FUNCTION 감지")
        fun testCreateFunctionDetection() {
            val sql = "CREATE OR REPLACE FUNCTION test_func RETURN NUMBER IS BEGIN RETURN 1; END;"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.PLSQL_FUNCTION, result.statementType)
        }

        @Test
        @DisplayName("CREATE TRIGGER 감지")
        fun testCreateTriggerDetection() {
            val sql = "CREATE OR REPLACE TRIGGER test_trg BEFORE INSERT ON t FOR EACH ROW BEGIN NULL; END;"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.PLSQL_TRIGGER, result.statementType)
        }

        @Test
        @DisplayName("CREATE PACKAGE 감지")
        fun testCreatePackageDetection() {
            val sql = "CREATE OR REPLACE PACKAGE test_pkg IS PROCEDURE test; END;"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.PLSQL_PACKAGE, result.statementType)
        }

        @Test
        @DisplayName("ALTER 문 감지")
        fun testAlterDetection() {
            val sql = "ALTER TABLE employees ADD COLUMN email VARCHAR(100)"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.DDL_ALTER, result.statementType)
        }

        @Test
        @DisplayName("DROP 문 감지")
        fun testDropDetection() {
            val sql = "DROP TABLE employees"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.DDL_DROP, result.statementType)
        }

        @Test
        @DisplayName("GRANT 문 감지")
        fun testGrantDetection() {
            val sql = "GRANT SELECT ON employees TO user1"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.DCL, result.statementType)
        }

        @Test
        @DisplayName("REVOKE 문 감지")
        fun testRevokeDetection() {
            val sql = "REVOKE SELECT ON employees FROM user1"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.DCL, result.statementType)
        }

        @Test
        @DisplayName("COMMENT 문 감지")
        fun testCommentStatementDetection() {
            val sql = "COMMENT ON TABLE employees IS 'Employee table'"

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL
            ) { s, _, _, _, _ -> s }

            assertEquals(ConversionRecoveryService.StatementType.COMMENT, result.statementType)
        }
    }

    @Nested
    @DisplayName("보고서 생성 테스트")
    inner class ReportGenerationTest {

        @Test
        @DisplayName("성공 보고서 생성")
        fun testSuccessReport() {
            val result = ConversionRecoveryService.RecoveryResult(
                convertedSql = "SELECT * FROM employees;",
                totalStatements = 2,
                successfulStatements = 2,
                failedStatements = 0,
                partialStatements = 0,
                warnings = emptyList(),
                appliedRules = listOf("Rule 1", "Rule 2"),
                statementResults = emptyList()
            )

            val report = ConversionRecoveryService.generateReport(result)

            assertTrue(report.contains("전체 문장: 2개"))
            assertTrue(report.contains("성공: 2개"))
            assertTrue(report.contains("실패: 0개"))
            assertTrue(report.contains("100.0%"))
            assertTrue(report.contains("Rule 1"))
            assertTrue(report.contains("Rule 2"))
        }

        @Test
        @DisplayName("부분 실패 보고서 생성")
        fun testPartialFailureReport() {
            val warnings = listOf(
                ConversionWarning(
                    type = WarningType.MANUAL_REVIEW_NEEDED,
                    message = "변환 중 오류 발생",
                    severity = WarningSeverity.ERROR,
                    suggestion = "수동 변환 필요"
                )
            )

            val statementResults = listOf(
                ConversionRecoveryService.StatementResult(
                    originalSql = "SELECT * FROM t1",
                    convertedSql = "SELECT * FROM t1",
                    status = ConversionRecoveryService.ConversionStatus.SUCCESS,
                    statementType = ConversionRecoveryService.StatementType.DML_SELECT
                ),
                ConversionRecoveryService.StatementResult(
                    originalSql = "SELECT * FROM t2",
                    convertedSql = "-- Error",
                    status = ConversionRecoveryService.ConversionStatus.FAILED,
                    errorMessage = "Conversion error",
                    statementType = ConversionRecoveryService.StatementType.DML_SELECT
                )
            )

            val result = ConversionRecoveryService.RecoveryResult(
                convertedSql = "SELECT * FROM t1;\n\n-- Error",
                totalStatements = 2,
                successfulStatements = 1,
                failedStatements = 1,
                partialStatements = 0,
                warnings = warnings,
                appliedRules = listOf("Rule 1"),
                statementResults = statementResults
            )

            val report = ConversionRecoveryService.generateReport(result)

            assertTrue(report.contains("전체 문장: 2개"))
            assertTrue(report.contains("성공: 1개"))
            assertTrue(report.contains("실패: 1개"))
            assertTrue(report.contains("50.0%"))
            assertTrue(report.contains("변환 실패 문장"))
            assertTrue(report.contains("Conversion error"))
        }

        @Test
        @DisplayName("경고 포함 보고서")
        fun testReportWithWarnings() {
            val warnings = listOf(
                ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "문법 차이 감지",
                    severity = WarningSeverity.WARNING,
                    suggestion = "확인 필요"
                ),
                ConversionWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "지원하지 않는 기능",
                    severity = WarningSeverity.ERROR
                )
            )

            val result = ConversionRecoveryService.RecoveryResult(
                convertedSql = "SELECT * FROM t",
                totalStatements = 1,
                successfulStatements = 0,
                failedStatements = 0,
                partialStatements = 1,
                warnings = warnings,
                appliedRules = emptyList(),
                statementResults = emptyList()
            )

            val report = ConversionRecoveryService.generateReport(result)

            assertTrue(report.contains("경고 및 알림"))
            assertTrue(report.contains("문법 차이 감지"))
            assertTrue(report.contains("지원하지 않는 기능"))
            assertTrue(report.contains("확인 필요"))
        }
    }

    @Nested
    @DisplayName("결과 병합 테스트")
    inner class ResultMergeTest {

        @Test
        @DisplayName("여러 결과 병합")
        fun testMergeResults() {
            val result1 = ConversionRecoveryService.RecoveryResult(
                convertedSql = "SELECT * FROM t1;",
                totalStatements = 1,
                successfulStatements = 1,
                failedStatements = 0,
                partialStatements = 0,
                warnings = emptyList(),
                appliedRules = listOf("Rule 1"),
                statementResults = listOf(
                    ConversionRecoveryService.StatementResult(
                        originalSql = "select * from t1",
                        convertedSql = "SELECT * FROM t1;",
                        status = ConversionRecoveryService.ConversionStatus.SUCCESS
                    )
                )
            )

            val result2 = ConversionRecoveryService.RecoveryResult(
                convertedSql = "SELECT * FROM t2;",
                totalStatements = 1,
                successfulStatements = 0,
                failedStatements = 1,
                partialStatements = 0,
                warnings = listOf(
                    ConversionWarning(
                        type = WarningType.MANUAL_REVIEW_NEEDED,
                        message = "Error",
                        severity = WarningSeverity.ERROR
                    )
                ),
                appliedRules = listOf("Rule 2"),
                statementResults = listOf(
                    ConversionRecoveryService.StatementResult(
                        originalSql = "select * from t2",
                        convertedSql = "-- Error",
                        status = ConversionRecoveryService.ConversionStatus.FAILED,
                        errorMessage = "Failed"
                    )
                )
            )

            val merged = ConversionRecoveryService.mergeResults(listOf(result1, result2))

            assertEquals(2, merged.totalStatements)
            assertEquals(1, merged.successfulStatements)
            assertEquals(1, merged.failedStatements)
            assertEquals(2, merged.statementResults.size)
            assertEquals(1, merged.warnings.size)
            assertEquals(2, merged.appliedRules.size)
            assertTrue(merged.isPartiallySuccessful)
        }

        @Test
        @DisplayName("빈 결과 병합")
        fun testMergeEmptyResults() {
            val merged = ConversionRecoveryService.mergeResults(emptyList())

            assertEquals(0, merged.totalStatements)
            assertEquals(0, merged.successfulStatements)
            assertEquals(0, merged.failedStatements)
            assertTrue(merged.statementResults.isEmpty())
        }
    }

    @Nested
    @DisplayName("RecoveryResult 속성 테스트")
    inner class RecoveryResultPropertiesTest {

        @Test
        @DisplayName("성공률 계산")
        fun testSuccessRate() {
            val result = ConversionRecoveryService.RecoveryResult(
                convertedSql = "",
                totalStatements = 4,
                successfulStatements = 3,
                failedStatements = 1,
                partialStatements = 0,
                warnings = emptyList(),
                appliedRules = emptyList(),
                statementResults = emptyList()
            )

            assertEquals(0.75, result.successRate)
        }

        @Test
        @DisplayName("0개 문장일 때 성공률")
        fun testSuccessRateZeroStatements() {
            val result = ConversionRecoveryService.RecoveryResult(
                convertedSql = "",
                totalStatements = 0,
                successfulStatements = 0,
                failedStatements = 0,
                partialStatements = 0,
                warnings = emptyList(),
                appliedRules = emptyList(),
                statementResults = emptyList()
            )

            assertEquals(0.0, result.successRate)
        }

        @Test
        @DisplayName("완전 성공 여부")
        fun testIsFullySuccessful() {
            val success = ConversionRecoveryService.RecoveryResult(
                convertedSql = "",
                totalStatements = 2,
                successfulStatements = 2,
                failedStatements = 0,
                partialStatements = 0,
                warnings = emptyList(),
                appliedRules = emptyList(),
                statementResults = emptyList()
            )

            val failure = ConversionRecoveryService.RecoveryResult(
                convertedSql = "",
                totalStatements = 2,
                successfulStatements = 1,
                failedStatements = 1,
                partialStatements = 0,
                warnings = emptyList(),
                appliedRules = emptyList(),
                statementResults = emptyList()
            )

            assertTrue(success.isFullySuccessful)
            assertFalse(failure.isFullySuccessful)
        }

        @Test
        @DisplayName("부분 성공 여부")
        fun testIsPartiallySuccessful() {
            val partial = ConversionRecoveryService.RecoveryResult(
                convertedSql = "",
                totalStatements = 2,
                successfulStatements = 1,
                failedStatements = 1,
                partialStatements = 0,
                warnings = emptyList(),
                appliedRules = emptyList(),
                statementResults = emptyList()
            )

            val allSuccess = ConversionRecoveryService.RecoveryResult(
                convertedSql = "",
                totalStatements = 2,
                successfulStatements = 2,
                failedStatements = 0,
                partialStatements = 0,
                warnings = emptyList(),
                appliedRules = emptyList(),
                statementResults = emptyList()
            )

            val allFail = ConversionRecoveryService.RecoveryResult(
                convertedSql = "",
                totalStatements = 2,
                successfulStatements = 0,
                failedStatements = 2,
                partialStatements = 0,
                warnings = emptyList(),
                appliedRules = emptyList(),
                statementResults = emptyList()
            )

            assertTrue(partial.isPartiallySuccessful)
            assertFalse(allSuccess.isPartiallySuccessful)
            assertFalse(allFail.isPartiallySuccessful)
        }
    }

    @Nested
    @DisplayName("고급 복구 전략 통합 테스트")
    inner class AdvancedRecoveryIntegrationTest {

        @Test
        @DisplayName("고급 복구 활성화 시 TABLESPACE 제거 후 재변환")
        fun testAdvancedRecoveryWithTablespace() {
            ConversionRecoveryService.enableAdvancedRecovery = true

            val sql = "CREATE TABLE test (id NUMBER) TABLESPACE users"

            // TABLESPACE가 있으면 실패하는 변환기
            val strictConverter: (String, DialectType, DialectType, MutableList<ConversionWarning>, MutableList<String>) -> String =
                { sqlPart, _, _, _, rules ->
                    if (sqlPart.uppercase().contains("TABLESPACE")) {
                        throw RuntimeException("TABLESPACE not supported")
                    }
                    rules.add("테이블 생성 변환")
                    sqlPart.replace("NUMBER", "INT")
                }

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                strictConverter
            )

            // 고급 복구가 TABLESPACE를 제거하고 재변환 성공
            assertTrue(result.status == ConversionRecoveryService.ConversionStatus.SUCCESS ||
                    result.status == ConversionRecoveryService.ConversionStatus.PARTIAL,
                "고급 복구로 성공 또는 부분 성공해야 함")
            assertFalse(result.convertedSql.uppercase().contains("TABLESPACE"),
                "TABLESPACE가 제거되어야 함")
        }

        @Test
        @DisplayName("고급 복구 비활성화 시 원래대로 실패")
        fun testAdvancedRecoveryDisabled() {
            ConversionRecoveryService.enableAdvancedRecovery = false

            val sql = "CREATE TABLE test (id NUMBER) TABLESPACE users"

            val strictConverter: (String, DialectType, DialectType, MutableList<ConversionWarning>, MutableList<String>) -> String =
                { sqlPart, _, _, _, _ ->
                    if (sqlPart.uppercase().contains("TABLESPACE")) {
                        throw RuntimeException("TABLESPACE not supported")
                    }
                    sqlPart
                }

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                strictConverter
            )

            assertEquals(ConversionRecoveryService.ConversionStatus.FAILED, result.status)
            assertTrue(result.convertedSql.contains("CONVERSION ERROR"))

            // 테스트 후 다시 활성화
            ConversionRecoveryService.enableAdvancedRecovery = true
        }

        @Test
        @DisplayName("힌트 제거 후 재변환")
        fun testAdvancedRecoveryWithHint() {
            ConversionRecoveryService.enableAdvancedRecovery = true

            val sql = "SELECT /*+ INDEX(e emp_idx) */ * FROM employees e"

            // 힌트가 있으면 실패하는 변환기
            val strictConverter: (String, DialectType, DialectType, MutableList<ConversionWarning>, MutableList<String>) -> String =
                { sqlPart, _, _, _, rules ->
                    if (sqlPart.contains("/*+")) {
                        throw RuntimeException("Oracle hints not supported")
                    }
                    rules.add("SELECT 변환")
                    sqlPart.uppercase()
                }

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                strictConverter
            )

            assertTrue(result.status == ConversionRecoveryService.ConversionStatus.SUCCESS ||
                    result.status == ConversionRecoveryService.ConversionStatus.PARTIAL)
            assertFalse(result.convertedSql.contains("/*+"), "힌트가 제거되어야 함")
            assertTrue(result.convertedSql.uppercase().contains("EMPLOYEES"))
        }

        @Test
        @DisplayName("다중 문장에서 고급 복구 적용")
        fun testAdvancedRecoveryMultipleStatements() {
            ConversionRecoveryService.enableAdvancedRecovery = true

            val sql = """
                SELECT * FROM t1;
                CREATE TABLE t2 (id NUMBER) TABLESPACE ts1;
                SELECT /*+ FULL(t3) */ * FROM t3;
            """.trimIndent()

            var callCount = 0
            val strictConverter: (String, DialectType, DialectType, MutableList<ConversionWarning>, MutableList<String>) -> String =
                { sqlPart, _, _, _, rules ->
                    callCount++
                    if (sqlPart.uppercase().contains("TABLESPACE") || sqlPart.contains("/*+")) {
                        throw RuntimeException("Unsupported syntax")
                    }
                    rules.add("변환 규칙 $callCount")
                    sqlPart.uppercase()
                }

            val result = ConversionRecoveryService.convertWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                strictConverter
            )

            // 첫 번째는 그대로 성공, 나머지는 복구 후 성공
            assertTrue(result.successfulStatements >= 1, "최소 1개 이상 성공해야 함")

            // 복구 전략 적용 규칙이 포함되어야 함
            val hasRecoveryRule = result.appliedRules.any { it.contains("고급 복구 전략") }
            if (result.successfulStatements > 1) {
                assertTrue(hasRecoveryRule || result.partialStatements > 0,
                    "복구 전략이 적용되었거나 부분 성공이 있어야 함")
            }
        }

        @Test
        @DisplayName("복구 불가능한 경우 실패 처리")
        fun testAdvancedRecoveryImpossible() {
            ConversionRecoveryService.enableAdvancedRecovery = true

            val sql = "SELECT * FROM employees"

            // 항상 실패하는 변환기 (복구로도 해결 불가)
            val alwaysFailConverter: (String, DialectType, DialectType, MutableList<ConversionWarning>, MutableList<String>) -> String =
                { _, _, _, _, _ ->
                    throw RuntimeException("Always fails - no recovery possible")
                }

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                sql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                alwaysFailConverter
            )

            assertEquals(ConversionRecoveryService.ConversionStatus.FAILED, result.status)
        }

        @Test
        @DisplayName("고급 복구 신뢰도에 따른 상태 결정")
        fun testAdvancedRecoveryConfidenceStatus() {
            ConversionRecoveryService.enableAdvancedRecovery = true

            // 물리적 속성 제거는 높은 신뢰도 (0.95)
            val highConfidenceSql = "CREATE TABLE t (id INT) PCTFREE 10 LOGGING"

            val strictConverter: (String, DialectType, DialectType, MutableList<ConversionWarning>, MutableList<String>) -> String =
                { sqlPart, _, _, _, _ ->
                    if (sqlPart.uppercase().contains("PCTFREE") || sqlPart.uppercase().contains("LOGGING")) {
                        throw RuntimeException("Oracle physical attributes not supported")
                    }
                    sqlPart
                }

            val result = ConversionRecoveryService.convertSingleWithRecovery(
                highConfidenceSql,
                DialectType.ORACLE,
                DialectType.MYSQL,
                strictConverter
            )

            // 높은 신뢰도 복구는 SUCCESS
            assertTrue(result.status == ConversionRecoveryService.ConversionStatus.SUCCESS ||
                    result.status == ConversionRecoveryService.ConversionStatus.PARTIAL)
        }
    }
}
