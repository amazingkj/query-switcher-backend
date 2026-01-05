package com.sqlswitcher.parser

import com.sqlswitcher.parser.error.SqlErrorHandler
import com.sqlswitcher.parser.error.SqlParseException
import com.sqlswitcher.parser.error.SqlErrorType
import com.sqlswitcher.parser.model.AstAnalysisResult
import com.sqlswitcher.parser.model.ConversionDifficulty
import com.sqlswitcher.parser.model.ComplexityDetails
import com.sqlswitcher.parser.model.TableColumnInfo
import com.sqlswitcher.parser.model.FunctionExpressionInfo
import com.sqlswitcher.service.SqlCacheService
import com.sqlswitcher.service.SqlMetricsService
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.statement.select.Select
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SqlParserService 단위 테스트
 */
@ExtendWith(MockitoExtension::class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SqlParserServiceTest {

    @Mock
    private lateinit var statementHandlerFactory: StatementHandlerFactory

    @Mock
    private lateinit var astAnalysisService: AstAnalysisService

    @Mock
    private lateinit var sqlErrorHandler: SqlErrorHandler

    @Mock
    private lateinit var sqlCacheService: SqlCacheService

    @Mock
    private lateinit var sqlMetricsService: SqlMetricsService

    private lateinit var sqlParserService: SqlParserService

    @BeforeEach
    fun setUp() {
        sqlParserService = SqlParserService(
            statementHandlerFactory,
            astAnalysisService,
            sqlErrorHandler,
            sqlCacheService,
            sqlMetricsService
        )
    }

    @Nested
    @DisplayName("단순 SELECT 문 파싱")
    inner class SimpleSelectParsingTest {

        @Test
        @DisplayName("기본 SELECT 문 파싱 성공")
        fun testBasicSelectParsing() {
            // Given
            val sql = "SELECT id, name FROM users"

            val metadata = StatementMetadata(
                type = StatementType.SELECT,
                tables = listOf("users"),
                columns = listOf("id", "name"),
                hasJoins = false,
                hasSubqueries = false,
                complexity = 1
            )

            val analysisResult = createSimpleAnalysis()

            whenever(sqlCacheService.getCachedParseResult(sql)).thenReturn(null)
            whenever(sqlCacheService.getOrComputeParseResult(eq(sql), any<(String) -> ParseResult>()))
                .thenAnswer { invocation ->
                    val function = invocation.getArgument<(String) -> ParseResult>(1)
                    function(sql)
                }
            whenever(sqlCacheService.getOrComputeAnalysisResult(eq(sql), any<(String) -> AstAnalysisResult>()))
                .thenReturn(analysisResult)
            whenever(statementHandlerFactory.getStatementMetadata(any())).thenReturn(metadata)
            whenever(astAnalysisService.analyzeStatement(any())).thenReturn(analysisResult)
            whenever(astAnalysisService.getConversionDifficulty(any())).thenReturn(ConversionDifficulty.EASY)
            whenever(astAnalysisService.getConversionWarnings(any())).thenReturn(emptyList())

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
            assertNotNull(result.statement)
            assertTrue(result.statement is Select)
            assertNotNull(result.metadata)
            assertEquals(StatementType.SELECT, result.metadata?.type)
            verify(sqlMetricsService).recordParseRequest()
        }

        @Test
        @DisplayName("별칭이 포함된 SELECT 문 파싱")
        fun testSelectWithAliases() {
            // Given
            val sql = "SELECT u.id AS user_id, u.name AS user_name FROM users u"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
            assertNotNull(result.statement)
        }

        @Test
        @DisplayName("WHERE 절이 포함된 SELECT 문 파싱")
        fun testSelectWithWhere() {
            // Given
            val sql = "SELECT * FROM users WHERE active = 1 AND age > 18"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }
    }

    @Nested
    @DisplayName("복잡한 SELECT 문 파싱")
    inner class ComplexSelectParsingTest {

        @Test
        @DisplayName("JOIN이 포함된 SELECT 문 파싱")
        fun testSelectWithJoin() {
            // Given
            val sql = """
                SELECT u.id, u.name, o.order_date
                FROM users u
                LEFT JOIN orders o ON u.id = o.user_id
                WHERE o.status = 'COMPLETED'
            """.trimIndent()

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("서브쿼리가 포함된 SELECT 문 파싱")
        fun testSelectWithSubquery() {
            // Given
            val sql = """
                SELECT * FROM users
                WHERE id IN (SELECT user_id FROM orders WHERE amount > 1000)
            """.trimIndent()

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("GROUP BY와 HAVING이 포함된 SELECT 문 파싱")
        fun testSelectWithGroupByHaving() {
            // Given
            val sql = """
                SELECT department, COUNT(*) as cnt
                FROM employees
                GROUP BY department
                HAVING COUNT(*) > 5
            """.trimIndent()

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("윈도우 함수가 포함된 SELECT 문 파싱")
        fun testSelectWithWindowFunction() {
            // Given
            val sql = """
                SELECT id, name, salary,
                       ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) as rn
                FROM employees
            """.trimIndent()

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }
    }

    @Nested
    @DisplayName("DML 문 파싱")
    inner class DmlParsingTest {

        @Test
        @DisplayName("INSERT 문 파싱")
        fun testInsertParsing() {
            // Given
            val sql = "INSERT INTO users (id, name, email) VALUES (1, 'John', 'john@example.com')"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("UPDATE 문 파싱")
        fun testUpdateParsing() {
            // Given
            val sql = "UPDATE users SET name = 'Jane', updated_at = NOW() WHERE id = 1"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("DELETE 문 파싱")
        fun testDeleteParsing() {
            // Given
            val sql = "DELETE FROM users WHERE id = 1"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }
    }

    @Nested
    @DisplayName("DDL 문 파싱")
    inner class DdlParsingTest {

        @Test
        @DisplayName("CREATE TABLE 문 파싱")
        fun testCreateTableParsing() {
            // Given
            val sql = """
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(255) UNIQUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent()

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("DROP TABLE 문 파싱")
        fun testDropTableParsing() {
            // Given
            val sql = "DROP TABLE IF EXISTS users"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("CREATE INDEX 문 파싱")
        fun testCreateIndexParsing() {
            // Given
            val sql = "CREATE INDEX idx_users_email ON users(email)"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }
    }

    @Nested
    @DisplayName("Oracle 전처리")
    inner class OraclePreprocessingTest {

        @Test
        @DisplayName("CASCADE CONSTRAINTS 전처리")
        fun testCascadeConstraintsPreprocessing() {
            // Given
            val sql = "DROP TABLE users CASCADE CONSTRAINTS"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("PURGE 키워드 전처리")
        fun testPurgePreprocessing() {
            // Given
            val sql = "DROP TABLE users PURGE"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("TABLESPACE 절 전처리")
        fun testTablespacePreprocessing() {
            // Given
            val sql = "CREATE TABLE t (id INT) TABLESPACE users_ts"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("BYTE/CHAR 키워드 전처리")
        fun testByteCharPreprocessing() {
            // Given
            val sql = "CREATE TABLE t (name VARCHAR2(100 BYTE))"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }
    }

    @Nested
    @DisplayName("에러 처리")
    inner class ErrorHandlingTest {

        @Test
        @DisplayName("잘못된 SQL 구문 처리")
        fun testInvalidSqlSyntax() {
            // Given
            val sql = "SELECT FROM WHERE"

            val parseException = SqlParseException(
                message = "Syntax error",
                sql = sql,
                errorType = SqlErrorType.SYNTAX_ERROR
            )

            whenever(sqlCacheService.getCachedParseResult(sql)).thenReturn(null)
            whenever(sqlCacheService.getOrComputeParseResult(eq(sql), any<(String) -> ParseResult>()))
                .thenAnswer { invocation ->
                    val function = invocation.getArgument<(String) -> ParseResult>(1)
                    function(sql)
                }
            whenever(sqlErrorHandler.handleParsingError(any(), eq(sql))).thenReturn(parseException)
            whenever(sqlErrorHandler.getUserFriendlyMessage(parseException)).thenReturn("SQL 구문 오류")

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertFalse(result.isValid)
            assertNull(result.statement)
            assertTrue(result.errors.isNotEmpty())
            verify(sqlMetricsService).recordParseError(any())
        }

        @Test
        @DisplayName("빈 SQL 처리")
        fun testEmptySql() {
            // Given
            val sql = ""

            val parseException = SqlParseException(
                message = "Empty SQL",
                sql = sql,
                errorType = SqlErrorType.SYNTAX_ERROR
            )

            whenever(sqlCacheService.getCachedParseResult(sql)).thenReturn(null)
            whenever(sqlCacheService.getOrComputeParseResult(eq(sql), any<(String) -> ParseResult>()))
                .thenAnswer { invocation ->
                    val function = invocation.getArgument<(String) -> ParseResult>(1)
                    function(sql)
                }
            whenever(sqlErrorHandler.handleParsingError(any(), eq(sql))).thenReturn(parseException)
            whenever(sqlErrorHandler.getUserFriendlyMessage(parseException)).thenReturn("SQL이 비어있습니다")

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertFalse(result.isValid)
        }
    }

    @Nested
    @DisplayName("캐시 기능")
    inner class CachingTest {

        @Test
        @DisplayName("캐시 히트 시 캐시된 결과 반환")
        fun testCacheHit() {
            // Given
            val sql = "SELECT 1"

            val cachedResult = ParseResult(
                isValid = true,
                statement = null,
                errors = emptyList(),
                parseTime = 5L,
                metadata = null,
                astAnalysis = null,
                conversionDifficulty = ConversionDifficulty.EASY,
                warnings = emptyList()
            )

            whenever(sqlCacheService.getCachedParseResult(sql)).thenReturn(cachedResult)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertEquals(cachedResult, result)
            verify(sqlMetricsService).recordCacheHit("parse")
            verify(sqlCacheService, never()).getOrComputeParseResult(any(), any<(String) -> ParseResult>())
        }

        @Test
        @DisplayName("캐시 미스 시 파싱 후 캐시 저장")
        fun testCacheMiss() {
            // Given
            val sql = "SELECT 1"

            whenever(sqlCacheService.getCachedParseResult(sql)).thenReturn(null)

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            verify(sqlMetricsService).recordCacheMiss("parse")
            verify(sqlCacheService).getOrComputeParseResult(eq(sql), any())
        }
    }

    @Nested
    @DisplayName("여러 SQL 문장 파싱")
    inner class MultipleStatementsParsingTest {

        @Test
        @DisplayName("세미콜론으로 구분된 여러 SQL 파싱")
        fun testMultipleStatements() {
            // Given
            val sql = "SELECT 1; SELECT 2"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }

        @Test
        @DisplayName("문자열 내 세미콜론 처리")
        fun testSemicolonInString() {
            // Given
            val sql = "SELECT 'Hello; World' FROM t"

            setupMocksForValidParsing(sql)

            // When
            val result = sqlParserService.parseSql(sql)

            // Then
            assertTrue(result.isValid)
        }
    }

    @Nested
    @DisplayName("메트릭 기록")
    inner class MetricsRecordingTest {

        @Test
        @DisplayName("파싱 성공 메트릭 기록")
        fun testParseSuccessMetrics() {
            // Given
            val sql = "SELECT 1"

            setupMocksForValidParsing(sql)

            // When
            sqlParserService.parseSql(sql)

            // Then
            verify(sqlMetricsService).recordParseRequest()
            verify(sqlMetricsService).recordParseDuration(any())
        }
    }

    private fun setupMocksForValidParsing(sql: String) {
        val metadata = StatementMetadata(
            type = StatementType.SELECT,
            tables = emptyList(),
            columns = emptyList(),
            hasJoins = false,
            hasSubqueries = false,
            complexity = 1
        )

        val analysisResult = createSimpleAnalysis()

        whenever(sqlCacheService.getCachedParseResult(sql)).thenReturn(null)
        whenever(sqlCacheService.getOrComputeParseResult(eq(sql), any<(String) -> ParseResult>()))
            .thenAnswer { invocation ->
                val function = invocation.getArgument<(String) -> ParseResult>(1)
                function(sql)
            }
        whenever(sqlCacheService.getOrComputeAnalysisResult(eq(sql), any<(String) -> AstAnalysisResult>()))
            .thenReturn(analysisResult)
        whenever(statementHandlerFactory.getStatementMetadata(any())).thenReturn(metadata)
        whenever(astAnalysisService.analyzeStatement(any())).thenReturn(analysisResult)
        whenever(astAnalysisService.getConversionDifficulty(any())).thenReturn(ConversionDifficulty.EASY)
        whenever(astAnalysisService.getConversionWarnings(any())).thenReturn(emptyList())
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
}
