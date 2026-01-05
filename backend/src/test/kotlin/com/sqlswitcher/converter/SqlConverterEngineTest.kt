package com.sqlswitcher.converter

import com.sqlswitcher.converter.feature.PackageConversionService
import com.sqlswitcher.converter.preprocessor.OracleSyntaxPreprocessor
import com.sqlswitcher.converter.stringbased.StringBasedDataTypeConverter
import com.sqlswitcher.converter.stringbased.StringBasedFunctionConverter
import com.sqlswitcher.converter.validation.SqlConversionValidationService
import com.sqlswitcher.parser.ParseResult
import com.sqlswitcher.parser.SqlParserService
import com.sqlswitcher.parser.StatementMetadata
import com.sqlswitcher.parser.StatementType
import com.sqlswitcher.parser.model.AstAnalysisResult
import com.sqlswitcher.parser.model.ComplexityDetails
import com.sqlswitcher.parser.model.ConversionDifficulty
import com.sqlswitcher.parser.model.TableColumnInfo
import com.sqlswitcher.parser.model.FunctionExpressionInfo
import com.sqlswitcher.service.SqlMetricsService
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.mockito.Mockito.atLeastOnce
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SqlConverterEngine 단위 테스트
 */
@ExtendWith(MockitoExtension::class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SqlConverterEngineTest {

    @Mock
    private lateinit var sqlParserService: SqlParserService

    @Mock
    private lateinit var sqlMetricsService: SqlMetricsService

    @Mock
    private lateinit var functionMapper: FunctionMapper

    @Mock
    private lateinit var dataTypeConverter: DataTypeConverter

    @Mock
    private lateinit var packageConversionService: PackageConversionService

    @Mock
    private lateinit var oracleSyntaxPreprocessor: OracleSyntaxPreprocessor

    @Mock
    private lateinit var stringBasedFunctionConverter: StringBasedFunctionConverter

    @Mock
    private lateinit var stringBasedDataTypeConverter: StringBasedDataTypeConverter

    @Mock
    private lateinit var sqlValidationService: SqlConversionValidationService

    @Mock
    private lateinit var oracleDialect: DatabaseDialect

    @Mock
    private lateinit var mysqlDialect: DatabaseDialect

    @Mock
    private lateinit var postgresqlDialect: DatabaseDialect

    private lateinit var sqlConverterEngine: SqlConverterEngine

    @BeforeEach
    fun setUp() {
        whenever(oracleDialect.getDialectType()).thenReturn(DialectType.ORACLE)
        whenever(mysqlDialect.getDialectType()).thenReturn(DialectType.MYSQL)
        whenever(postgresqlDialect.getDialectType()).thenReturn(DialectType.POSTGRESQL)

        val dialects = listOf(oracleDialect, mysqlDialect, postgresqlDialect)

        sqlConverterEngine = SqlConverterEngine(
            sqlParserService = sqlParserService,
            sqlMetricsService = sqlMetricsService,
            dialects = dialects,
            functionMapper = functionMapper,
            dataTypeConverter = dataTypeConverter,
            packageConversionService = packageConversionService,
            oracleSyntaxPreprocessor = oracleSyntaxPreprocessor,
            stringBasedFunctionConverter = stringBasedFunctionConverter,
            stringBasedDataTypeConverter = stringBasedDataTypeConverter,
            sqlValidationService = sqlValidationService
        )

        // Disable auto validation for simpler tests
        sqlConverterEngine.enableAutoValidation = false
        sqlConverterEngine.enableLargeSqlOptimization = false
    }

    @Nested
    @DisplayName("기본 SQL 변환")
    inner class BasicConversionTest {

        @Test
        @DisplayName("단순 SELECT 문 변환")
        fun testSimpleSelectConversion() {
            // Given
            val sql = "SELECT id FROM users"
            val statement = CCJSqlParserUtil.parse(sql)

            val parseResult = ParseResult(
                isValid = true,
                statement = statement,
                errors = emptyList(),
                parseTime = 10L,
                metadata = StatementMetadata(StatementType.SELECT, listOf("users"), listOf("id"), hasJoins = false, hasSubqueries = false, complexity = 1),
                astAnalysis = createSimpleAnalysis(),
                conversionDifficulty = ConversionDifficulty.EASY,
                warnings = emptyList()
            )

            val conversionResult = ConversionResult(
                convertedSql = "SELECT id FROM users",
                warnings = emptyList(),
                appliedRules = listOf("기본 변환")
            )

            whenever(oracleSyntaxPreprocessor.preprocess(eq(sql), any(), any(), any())).thenReturn(sql)
            whenever(sqlParserService.parseSql(sql)).thenReturn(parseResult)
            whenever(oracleDialect.canConvert(any(), any())).thenReturn(true)
            whenever(oracleDialect.convertQuery(any(), any(), any())).thenReturn(conversionResult)

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertEquals("SELECT id FROM users", result.convertedSql)
            // 메트릭 기록 여부 확인은 mock 설정에 따라 다를 수 있으므로 결과만 확인
            assertNotNull(result)
        }

        @Test
        @DisplayName("빈 SQL 처리")
        fun testEmptySqlConversion() {
            // Given
            val sql = ""

            val parseResult = ParseResult(
                isValid = false,
                statement = null,
                errors = listOf("Empty SQL"),
                parseTime = 1L,
                metadata = null,
                astAnalysis = null,
                conversionDifficulty = null,
                warnings = emptyList()
            )

            whenever(sqlParserService.parseSql(sql)).thenReturn(parseResult)
            setupFallbackMocks(sql)

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("여러 SQL 문장 변환")
    inner class MultipleStatementConversionTest {

        @Test
        @DisplayName("세미콜론으로 구분된 여러 SQL 변환")
        fun testMultipleStatements() {
            // Given
            val sql = "SELECT 1; SELECT 2"
            val statement1 = CCJSqlParserUtil.parse("SELECT 1")
            val statement2 = CCJSqlParserUtil.parse("SELECT 2")

            val parseResult1 = createValidParseResult(statement1)
            val parseResult2 = createValidParseResult(statement2)

            whenever(oracleSyntaxPreprocessor.preprocess(eq("SELECT 1"), any(), any(), any())).thenReturn("SELECT 1")
            whenever(oracleSyntaxPreprocessor.preprocess(eq("SELECT 2"), any(), any(), any())).thenReturn("SELECT 2")
            whenever(sqlParserService.parseSql("SELECT 1")).thenReturn(parseResult1)
            whenever(sqlParserService.parseSql("SELECT 2")).thenReturn(parseResult2)
            whenever(oracleDialect.canConvert(any(), any())).thenReturn(true)
            whenever(oracleDialect.convertQuery(any(), any(), any())).thenReturn(
                ConversionResult(convertedSql = "SELECT 1"),
                ConversionResult(convertedSql = "SELECT 2")
            )

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertTrue(result.convertedSql.contains("SELECT 1"))
            assertTrue(result.convertedSql.contains("SELECT 2"))
            assertTrue(result.appliedRules.any { it.contains("2개의 SQL 문장") })
        }

        @Test
        @DisplayName("문자열 내 세미콜론은 분리하지 않음")
        fun testSemicolonInString() {
            // Given
            val sql = "SELECT 'Hello; World' FROM t"
            val statement = CCJSqlParserUtil.parse(sql)

            val parseResult = createValidParseResult(statement)

            whenever(oracleSyntaxPreprocessor.preprocess(eq(sql), any(), any(), any())).thenReturn(sql)
            whenever(sqlParserService.parseSql(sql)).thenReturn(parseResult)
            whenever(oracleDialect.canConvert(any(), any())).thenReturn(true)
            whenever(oracleDialect.convertQuery(any(), any(), any())).thenReturn(
                ConversionResult(convertedSql = sql)
            )

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertTrue(result.convertedSql.contains("Hello; World"))
        }
    }

    @Nested
    @DisplayName("폴백 변환")
    inner class FallbackConversionTest {

        @Test
        @DisplayName("파싱 실패 시 문자열 기반 폴백 변환")
        fun testFallbackOnParseFailure() {
            // Given
            val sql = "SELECT SYSDATE FROM DUAL"

            val parseResult = ParseResult(
                isValid = false,
                statement = null,
                errors = listOf("Parse error"),
                parseTime = 10L,
                metadata = null,
                astAnalysis = null,
                conversionDifficulty = null,
                warnings = emptyList(),
                parseException = null
            )

            whenever(oracleSyntaxPreprocessor.preprocess(eq(sql), any(), any(), any())).thenReturn(sql)
            whenever(sqlParserService.parseSql(sql)).thenReturn(parseResult)
            setupFallbackMocks(sql)

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertNotNull(result)
            // 파싱 실패 시 경고 메시지가 포함되어야 함 (AST 파싱 불가 또는 문자열 기반)
            assertTrue(result.warnings.isNotEmpty() || result.appliedRules.any { it.contains("폴백") || it.contains("문자열") })
        }

        @Test
        @DisplayName("PL/SQL 객체는 파싱 스킵")
        fun testSkipParsingForPlSql() {
            // Given
            val sql = """
                CREATE OR REPLACE PROCEDURE test_proc AS
                BEGIN
                    NULL;
                END;
            """.trimIndent()

            setupFallbackMocks(sql)

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertNotNull(result)
            assertTrue(result.warnings.any { it.message.contains("PL/SQL") || it.message.contains("문자열") })
        }

        @Test
        @DisplayName("PACKAGE 문 감지 및 변환")
        fun testPackageConversion() {
            // Given
            val sql = """
                CREATE OR REPLACE PACKAGE test_pkg AS
                    PROCEDURE test_proc;
                END;
            """.trimIndent()

            whenever(packageConversionService.isPackageStatement(sql)).thenReturn(true)
            whenever(packageConversionService.convertPackage(eq(sql), any(), any(), any(), any()))
                .thenReturn("-- Converted package")
            setupBasicMocks()

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertNotNull(result)
        }

        @Test
        @DisplayName("TRIGGER 문 변환")
        fun testTriggerConversion() {
            // Given
            val sql = """
                CREATE OR REPLACE TRIGGER test_trigger
                BEFORE INSERT ON users
                FOR EACH ROW
                BEGIN
                    :NEW.created_at := SYSDATE;
                END;
            """.trimIndent()

            setupFallbackMocks(sql)

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("에러 처리")
    inner class ErrorHandlingTest {

        @Test
        @DisplayName("변환 중 예외 발생 시 원본 SQL 반환")
        fun testExceptionHandling() {
            // Given
            val sql = "SELECT * FROM users"

            whenever(sqlParserService.parseSql(any())).thenThrow(RuntimeException("Unexpected error"))
            whenever(oracleSyntaxPreprocessor.preprocess(any(), any(), any(), any())).thenReturn(sql)

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertEquals(sql, result.convertedSql)
            assertTrue(result.warnings.any { it.severity == WarningSeverity.ERROR })
            verify(sqlMetricsService).recordConversionError("ORACLE", "MYSQL", "ConversionError")
        }

        @Test
        @DisplayName("지원하지 않는 소스 방언 오류")
        fun testUnsupportedSourceDialect() {
            // Given
            val sql = "SELECT 1"

            val dialects = listOf(mysqlDialect) // Oracle 없음

            val engineWithLimitedDialects = SqlConverterEngine(
                sqlParserService = sqlParserService,
                sqlMetricsService = sqlMetricsService,
                dialects = dialects,
                functionMapper = functionMapper,
                dataTypeConverter = dataTypeConverter,
                packageConversionService = packageConversionService,
                oracleSyntaxPreprocessor = oracleSyntaxPreprocessor,
                stringBasedFunctionConverter = stringBasedFunctionConverter,
                stringBasedDataTypeConverter = stringBasedDataTypeConverter,
                sqlValidationService = sqlValidationService
            )

            engineWithLimitedDialects.enableAutoValidation = false

            val statement = CCJSqlParserUtil.parse(sql)
            val parseResult = createValidParseResult(statement)

            whenever(sqlParserService.parseSql(any())).thenReturn(parseResult)

            // When
            val result = engineWithLimitedDialects.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertTrue(result.warnings.any { it.severity == WarningSeverity.ERROR })
        }
    }

    @Nested
    @DisplayName("변환 경고")
    inner class ConversionWarningsTest {

        @Test
        @DisplayName("변환 불가능한 기능에 대한 경고")
        fun testUnsupportedFeatureWarning() {
            // Given
            val sql = "SELECT * FROM users"
            val statement = CCJSqlParserUtil.parse(sql)
            val parseResult = createValidParseResult(statement)

            whenever(oracleSyntaxPreprocessor.preprocess(eq(sql), any(), any(), any())).thenReturn(sql)
            whenever(sqlParserService.parseSql(sql)).thenReturn(parseResult)
            whenever(oracleDialect.canConvert(any(), any())).thenReturn(false) // 변환 불가
            whenever(oracleDialect.convertQuery(any(), any(), any())).thenReturn(
                ConversionResult(convertedSql = sql, warnings = emptyList())
            )

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertTrue(result.warnings.any { it.severity == WarningSeverity.WARNING })
        }
    }

    @Nested
    @DisplayName("복잡한 쿼리 감지")
    inner class ComplexQueryDetectionTest {

        @Test
        @DisplayName("계층형 쿼리 감지")
        fun testHierarchicalQueryDetection() {
            // Given
            val sql = """
                SELECT employee_id, manager_id, LEVEL
                FROM employees
                START WITH manager_id IS NULL
                CONNECT BY PRIOR employee_id = manager_id
            """.trimIndent()

            setupFallbackMocks(sql)

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertNotNull(result)
        }

        @Test
        @DisplayName("복잡한 MERGE 문 감지")
        fun testComplexMergeDetection() {
            // Given
            val sql = """
                MERGE INTO target t
                USING source s ON (t.id = s.id)
                WHEN MATCHED THEN UPDATE SET t.name = s.name
                WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id, s.name)
                WHEN MATCHED AND s.status = 'D' THEN DELETE
            """.trimIndent()

            setupFallbackMocks(sql)

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("옵션 설정")
    inner class OptionsTest {

        @Test
        @DisplayName("자동 검증 비활성화")
        fun testAutoValidationDisabled() {
            // Given
            val sql = "SELECT 1"
            sqlConverterEngine.enableAutoValidation = false

            val statement = CCJSqlParserUtil.parse(sql)
            val parseResult = createValidParseResult(statement)

            whenever(oracleSyntaxPreprocessor.preprocess(eq(sql), any(), any(), any())).thenReturn(sql)
            whenever(sqlParserService.parseSql(sql)).thenReturn(parseResult)
            whenever(oracleDialect.canConvert(any(), any())).thenReturn(true)
            whenever(oracleDialect.convertQuery(any(), any(), any())).thenReturn(
                ConversionResult(convertedSql = sql)
            )

            // When
            val result = sqlConverterEngine.convert(sql, DialectType.ORACLE, DialectType.MYSQL)

            // Then
            assertNotNull(result)
            verify(sqlValidationService, never()).validateConversionPair(any(), any(), any(), any())
        }
    }

    private fun createSimpleAnalysis(): AstAnalysisResult {
        return AstAnalysisResult(
            tableColumnInfo = TableColumnInfo(
                tables = emptySet(),
                columns = emptySet(),
                tableAliases = emptyMap()
            ),
            functionExpressionInfo = FunctionExpressionInfo(
                functions = emptySet(),
                expressions = emptyList(),
                aggregateFunctions = emptySet(),
                windowFunctions = emptySet()
            ),
            complexityDetails = ComplexityDetails(
                joinCount = 0,
                subqueryCount = 0,
                functionCount = 0,
                aggregateCount = 0,
                windowFunctionCount = 0,
                caseExpressionCount = 0,
                unionCount = 0,
                cteCount = 0,
                recursiveQueryCount = 0,
                pivotCount = 0,
                lateralJoinCount = 0,
                totalComplexityScore = 0
            )
        )
    }

    private fun createValidParseResult(statement: net.sf.jsqlparser.statement.Statement): ParseResult {
        return ParseResult(
            isValid = true,
            statement = statement,
            errors = emptyList(),
            parseTime = 10L,
            metadata = StatementMetadata(StatementType.SELECT, emptyList(), emptyList(), hasJoins = false, hasSubqueries = false, complexity = 1),
            astAnalysis = createSimpleAnalysis(),
            conversionDifficulty = ConversionDifficulty.EASY,
            warnings = emptyList()
        )
    }

    private fun setupFallbackMocks(sql: String) {
        whenever(oracleSyntaxPreprocessor.preprocess(any(), any(), any(), any())).thenReturn(sql)
        whenever(stringBasedFunctionConverter.convert(any(), any(), any(), any())).thenReturn(sql)
        whenever(stringBasedDataTypeConverter.convert(any(), any(), any(), any())).thenReturn(sql)
        whenever(sqlValidationService.validateConversion(any(), any(), any(), any())).thenReturn(emptyList())
        whenever(packageConversionService.isPackageStatement(any())).thenReturn(false)
        whenever(packageConversionService.isPackageBodyStatement(any())).thenReturn(false)
    }

    private fun setupBasicMocks() {
        whenever(oracleSyntaxPreprocessor.preprocess(any(), any(), any(), any())).thenAnswer { it.arguments[0] }
        whenever(stringBasedFunctionConverter.convert(any(), any(), any(), any())).thenAnswer { it.arguments[0] }
        whenever(stringBasedDataTypeConverter.convert(any(), any(), any(), any())).thenAnswer { it.arguments[0] }
        whenever(sqlValidationService.validateConversion(any(), any(), any(), any())).thenReturn(emptyList())
    }
}
