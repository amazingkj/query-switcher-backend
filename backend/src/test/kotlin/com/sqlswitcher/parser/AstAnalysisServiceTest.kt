package com.sqlswitcher.parser

import com.sqlswitcher.parser.model.*
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
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
import kotlin.test.assertTrue

/**
 * AstAnalysisService Unit Tests
 */
@ExtendWith(MockitoExtension::class)
class AstAnalysisServiceTest {

    @Mock
    private lateinit var simpleAstAnalyzer: SimpleAstAnalyzer

    private lateinit var astAnalysisService: AstAnalysisService

    @BeforeEach
    fun setUp() {
        astAnalysisService = AstAnalysisService(simpleAstAnalyzer)
    }

    @Nested
    @DisplayName("Table and Column Extraction Tests")
    inner class TableAndColumnExtractionTest {

        @Test
        @DisplayName("Extract single table from simple SELECT")
        fun testExtractSingleTable() {
            // Given
            val sql = "SELECT id, name FROM users"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                tables = setOf("users"),
                columns = setOf("id", "name")
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertTrue(result.tableColumnInfo.tables.contains("users"))
            assertTrue(result.tableColumnInfo.columns.contains("id"))
            assertTrue(result.tableColumnInfo.columns.contains("name"))
        }

        @Test
        @DisplayName("Extract multiple tables from JOIN query")
        fun testExtractMultipleTables() {
            // Given
            val sql = "SELECT u.id, o.order_date FROM users u JOIN orders o ON u.id = o.user_id"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                tables = setOf("users", "orders"),
                columns = setOf("id", "order_date"),
                tableAliases = mapOf("u" to "users", "o" to "orders"),
                joinCount = 1
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertEquals(2, result.tableColumnInfo.tables.size)
            assertTrue(result.tableColumnInfo.tables.contains("users"))
            assertTrue(result.tableColumnInfo.tables.contains("orders"))
        }

        @Test
        @DisplayName("Extract table aliases correctly")
        fun testExtractTableAliases() {
            // Given
            val sql = "SELECT u.id FROM users AS u"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                tables = setOf("users"),
                columns = setOf("id"),
                tableAliases = mapOf("u" to "users")
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertEquals("users", result.tableColumnInfo.tableAliases["u"])
        }
    }

    @Nested
    @DisplayName("Function Detection Tests")
    inner class FunctionDetectionTest {

        @Test
        @DisplayName("Detect basic SQL functions")
        fun testDetectBasicFunctions() {
            // Given
            val sql = "SELECT UPPER(name), LOWER(email) FROM users"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                functions = setOf("UPPER", "LOWER")
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertTrue(result.functionExpressionInfo.functions.contains("UPPER"))
            assertTrue(result.functionExpressionInfo.functions.contains("LOWER"))
        }

        @Test
        @DisplayName("Detect date functions")
        fun testDetectDateFunctions() {
            // Given
            val sql = "SELECT NOW(), CURRENT_DATE, DATE_ADD(created_at, INTERVAL 1 DAY) FROM orders"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                functions = setOf("NOW", "CURRENT_DATE", "DATE_ADD")
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertTrue(result.functionExpressionInfo.functions.contains("NOW"))
        }
    }

    @Nested
    @DisplayName("Aggregate Function Detection Tests")
    inner class AggregateFunctionDetectionTest {

        @Test
        @DisplayName("Detect COUNT aggregate function")
        fun testDetectCountFunction() {
            // Given
            val sql = "SELECT COUNT(*) FROM users"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                functions = setOf("COUNT"),
                aggregateFunctions = setOf("COUNT"),
                aggregateCount = 1
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertTrue(result.functionExpressionInfo.aggregateFunctions.contains("COUNT"))
        }

        @Test
        @DisplayName("Detect multiple aggregate functions")
        fun testDetectMultipleAggregateFunctions() {
            // Given
            val sql = "SELECT COUNT(*), SUM(amount), AVG(price), MIN(quantity), MAX(quantity) FROM orders"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                functions = setOf("COUNT", "SUM", "AVG", "MIN", "MAX"),
                aggregateFunctions = setOf("COUNT", "SUM", "AVG", "MIN", "MAX"),
                aggregateCount = 5
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertEquals(5, result.functionExpressionInfo.aggregateFunctions.size)
            assertTrue(result.functionExpressionInfo.aggregateFunctions.containsAll(
                setOf("COUNT", "SUM", "AVG", "MIN", "MAX")
            ))
        }

        @Test
        @DisplayName("Detect GROUP_CONCAT aggregate function")
        fun testDetectGroupConcatFunction() {
            // Given
            val sql = "SELECT department, GROUP_CONCAT(name) FROM employees GROUP BY department"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                functions = setOf("GROUP_CONCAT"),
                aggregateFunctions = setOf("GROUP_CONCAT"),
                aggregateCount = 1
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertTrue(result.functionExpressionInfo.aggregateFunctions.contains("GROUP_CONCAT"))
        }
    }

    @Nested
    @DisplayName("Window Function Detection Tests")
    inner class WindowFunctionDetectionTest {

        @Test
        @DisplayName("Detect ROW_NUMBER window function")
        fun testDetectRowNumberFunction() {
            // Given
            val sql = "SELECT id, ROW_NUMBER() OVER (ORDER BY created_at) as rn FROM users"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                functions = setOf("ROW_NUMBER"),
                windowFunctions = setOf("ROW_NUMBER"),
                windowFunctionCount = 1
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertTrue(result.functionExpressionInfo.windowFunctions.contains("ROW_NUMBER"))
            assertEquals(1, result.complexityDetails.windowFunctionCount)
        }

        @Test
        @DisplayName("Detect RANK and DENSE_RANK window functions")
        fun testDetectRankFunctions() {
            // Given
            val sql = """
                SELECT id,
                       RANK() OVER (ORDER BY score DESC) as rank,
                       DENSE_RANK() OVER (ORDER BY score DESC) as dense_rank
                FROM students
            """.trimIndent()
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                functions = setOf("RANK", "DENSE_RANK"),
                windowFunctions = setOf("RANK", "DENSE_RANK"),
                windowFunctionCount = 2
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertTrue(result.functionExpressionInfo.windowFunctions.contains("RANK"))
            assertTrue(result.functionExpressionInfo.windowFunctions.contains("DENSE_RANK"))
        }

        @Test
        @DisplayName("Detect LAG and LEAD window functions")
        fun testDetectLagLeadFunctions() {
            // Given
            val sql = """
                SELECT id,
                       LAG(price, 1) OVER (ORDER BY date) as prev_price,
                       LEAD(price, 1) OVER (ORDER BY date) as next_price
                FROM stock_prices
            """.trimIndent()
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                functions = setOf("LAG", "LEAD"),
                windowFunctions = setOf("LAG", "LEAD"),
                windowFunctionCount = 2
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertTrue(result.functionExpressionInfo.windowFunctions.contains("LAG"))
            assertTrue(result.functionExpressionInfo.windowFunctions.contains("LEAD"))
        }
    }

    @Nested
    @DisplayName("Complexity Calculation Tests")
    inner class ComplexityCalculationTest {

        @Test
        @DisplayName("Simple SELECT has low complexity")
        fun testSimpleSelectLowComplexity() {
            // Given
            val sql = "SELECT id FROM users"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                totalComplexityScore = 2
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)
            val difficulty = astAnalysisService.getConversionDifficulty(result)

            // Then
            assertEquals(ConversionDifficulty.EASY, difficulty)
        }

        @Test
        @DisplayName("Query with JOINs has moderate complexity")
        fun testJoinsModerateComplexity() {
            // Given
            val sql = "SELECT u.id FROM users u JOIN orders o ON u.id = o.user_id JOIN products p ON o.product_id = p.id"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                joinCount = 2,
                totalComplexityScore = 10
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)
            val difficulty = astAnalysisService.getConversionDifficulty(result)

            // Then
            assertEquals(ConversionDifficulty.MODERATE, difficulty)
        }

        @Test
        @DisplayName("Query with subqueries and window functions has high complexity")
        fun testHighComplexity() {
            // Given
            val sql = """
                SELECT * FROM users WHERE id IN (
                    SELECT user_id FROM orders WHERE ROW_NUMBER() OVER (ORDER BY date) = 1
                )
            """.trimIndent()
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                subqueryCount = 1,
                windowFunctionCount = 1,
                totalComplexityScore = 25
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)
            val difficulty = astAnalysisService.getConversionDifficulty(result)

            // Then
            assertEquals(ConversionDifficulty.HARD, difficulty)
        }

        @Test
        @DisplayName("Query with CTEs and recursive has very high complexity")
        fun testVeryHighComplexity() {
            // Given
            val sql = """
                WITH RECURSIVE cte AS (
                    SELECT 1 as n
                    UNION ALL
                    SELECT n + 1 FROM cte WHERE n < 10
                )
                SELECT * FROM cte
            """.trimIndent()
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                cteCount = 1,
                recursiveQueryCount = 1,
                unionCount = 1,
                totalComplexityScore = 35
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)
            val difficulty = astAnalysisService.getConversionDifficulty(result)

            // Then
            assertEquals(ConversionDifficulty.VERY_HARD, difficulty)
        }
    }

    @Nested
    @DisplayName("Join Detection Tests")
    inner class JoinDetectionTest {

        @Test
        @DisplayName("Detect INNER JOIN")
        fun testDetectInnerJoin() {
            // Given
            val sql = "SELECT * FROM users u INNER JOIN orders o ON u.id = o.user_id"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                tables = setOf("users", "orders"),
                joinCount = 1
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertEquals(1, result.complexityDetails.joinCount)
        }

        @Test
        @DisplayName("Detect LEFT JOIN")
        fun testDetectLeftJoin() {
            // Given
            val sql = "SELECT * FROM users u LEFT JOIN orders o ON u.id = o.user_id"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                tables = setOf("users", "orders"),
                joinCount = 1
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertEquals(1, result.complexityDetails.joinCount)
        }

        @Test
        @DisplayName("Detect multiple JOINs")
        fun testDetectMultipleJoins() {
            // Given
            val sql = """
                SELECT u.*, o.*, p.*
                FROM users u
                JOIN orders o ON u.id = o.user_id
                JOIN products p ON o.product_id = p.id
                LEFT JOIN categories c ON p.category_id = c.id
            """.trimIndent()
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                tables = setOf("users", "orders", "products", "categories"),
                joinCount = 3
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertEquals(3, result.complexityDetails.joinCount)
        }
    }

    @Nested
    @DisplayName("Subquery Detection Tests")
    inner class SubqueryDetectionTest {

        @Test
        @DisplayName("Detect subquery in WHERE clause")
        fun testDetectSubqueryInWhere() {
            // Given
            val sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders)"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                subqueryCount = 1
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertEquals(1, result.complexityDetails.subqueryCount)
        }

        @Test
        @DisplayName("Detect subquery in FROM clause")
        fun testDetectSubqueryInFrom() {
            // Given
            val sql = "SELECT * FROM (SELECT id, name FROM users WHERE active = 1) AS active_users"
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                subqueryCount = 1
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertEquals(1, result.complexityDetails.subqueryCount)
        }

        @Test
        @DisplayName("Detect nested subqueries")
        fun testDetectNestedSubqueries() {
            // Given
            val sql = """
                SELECT * FROM users WHERE id IN (
                    SELECT user_id FROM orders WHERE product_id IN (
                        SELECT id FROM products WHERE price > 100
                    )
                )
            """.trimIndent()
            val statement = CCJSqlParserUtil.parse(sql)

            val expectedResult = createAnalysisResult(
                subqueryCount = 2
            )

            whenever(simpleAstAnalyzer.analyzeStatement(statement)).thenReturn(expectedResult)

            // When
            val result = astAnalysisService.analyzeStatement(statement)

            // Then
            assertEquals(2, result.complexityDetails.subqueryCount)
        }
    }

    @Nested
    @DisplayName("Conversion Warning Tests")
    inner class ConversionWarningTest {

        @Test
        @DisplayName("Generate warning for window functions")
        fun testWarningForWindowFunctions() {
            // Given
            val analysisResult = createAnalysisResult(
                windowFunctionCount = 1
            )

            // When
            val warnings = astAnalysisService.getConversionWarnings(analysisResult)

            // Then
            assertTrue(warnings.any { it.contains("Window functions") })
        }

        @Test
        @DisplayName("Generate warning for CTEs")
        fun testWarningForCTEs() {
            // Given
            val analysisResult = createAnalysisResult(
                cteCount = 1
            )

            // When
            val warnings = astAnalysisService.getConversionWarnings(analysisResult)

            // Then
            assertTrue(warnings.any { it.contains("Common Table Expressions") || it.contains("CTE") })
        }

        @Test
        @DisplayName("Generate warning for database-specific functions")
        fun testWarningForDatabaseSpecificFunctions() {
            // Given
            val analysisResult = createAnalysisResult(
                functions = setOf("NVL", "DECODE")
            )

            // When
            val warnings = astAnalysisService.getConversionWarnings(analysisResult)

            // Then
            assertTrue(warnings.any { it.contains("Database-specific functions") })
        }

        @Test
        @DisplayName("Generate warning for LATERAL joins")
        fun testWarningForLateralJoins() {
            // Given
            val analysisResult = createAnalysisResult(
                lateralJoinCount = 1
            )

            // When
            val warnings = astAnalysisService.getConversionWarnings(analysisResult)

            // Then
            assertTrue(warnings.any { it.contains("LATERAL") })
        }

        @Test
        @DisplayName("Generate warning for many JOINs")
        fun testWarningForManyJoins() {
            // Given
            val analysisResult = createAnalysisResult(
                joinCount = 6
            )

            // When
            val warnings = astAnalysisService.getConversionWarnings(analysisResult)

            // Then
            assertTrue(warnings.any { it.contains("Many JOINs") })
        }

        @Test
        @DisplayName("Generate warning for multiple subqueries")
        fun testWarningForMultipleSubqueries() {
            // Given
            val analysisResult = createAnalysisResult(
                subqueryCount = 4
            )

            // When
            val warnings = astAnalysisService.getConversionWarnings(analysisResult)

            // Then
            assertTrue(warnings.any { it.contains("Multiple subqueries") })
        }

        @Test
        @DisplayName("Generate warning for recursive queries")
        fun testWarningForRecursiveQueries() {
            // Given
            val analysisResult = createAnalysisResult(
                recursiveQueryCount = 1
            )

            // When
            val warnings = astAnalysisService.getConversionWarnings(analysisResult)

            // Then
            assertTrue(warnings.any { it.contains("Recursive") })
        }

        @Test
        @DisplayName("No warnings for simple query")
        fun testNoWarningsForSimpleQuery() {
            // Given
            val analysisResult = createAnalysisResult()

            // When
            val warnings = astAnalysisService.getConversionWarnings(analysisResult)

            // Then
            assertTrue(warnings.isEmpty())
        }
    }

    @Nested
    @DisplayName("Complexity Level Tests")
    inner class ComplexityLevelTest {

        @Test
        @DisplayName("Complexity level SIMPLE for score <= 5")
        fun testSimpleComplexityLevel() {
            // Given
            val complexityDetails = createComplexityDetails(totalComplexityScore = 3)

            // When
            val level = complexityDetails.getComplexityLevel()

            // Then
            assertEquals(ComplexityLevel.SIMPLE, level)
        }

        @Test
        @DisplayName("Complexity level MODERATE for score <= 15")
        fun testModerateComplexityLevel() {
            // Given
            val complexityDetails = createComplexityDetails(totalComplexityScore = 12)

            // When
            val level = complexityDetails.getComplexityLevel()

            // Then
            assertEquals(ComplexityLevel.MODERATE, level)
        }

        @Test
        @DisplayName("Complexity level COMPLEX for score <= 30")
        fun testComplexComplexityLevel() {
            // Given
            val complexityDetails = createComplexityDetails(totalComplexityScore = 25)

            // When
            val level = complexityDetails.getComplexityLevel()

            // Then
            assertEquals(ComplexityLevel.COMPLEX, level)
        }

        @Test
        @DisplayName("Complexity level VERY_COMPLEX for score > 30")
        fun testVeryComplexComplexityLevel() {
            // Given
            val complexityDetails = createComplexityDetails(totalComplexityScore = 50)

            // When
            val level = complexityDetails.getComplexityLevel()

            // Then
            assertEquals(ComplexityLevel.VERY_COMPLEX, level)
        }
    }

    // Helper methods

    private fun createAnalysisResult(
        tables: Set<String> = emptySet(),
        columns: Set<String> = emptySet(),
        tableAliases: Map<String, String> = emptyMap(),
        functions: Set<String> = emptySet(),
        expressions: List<ExpressionType> = emptyList(),
        aggregateFunctions: Set<String> = emptySet(),
        windowFunctions: Set<String> = emptySet(),
        joinCount: Int = 0,
        subqueryCount: Int = 0,
        functionCount: Int = 0,
        aggregateCount: Int = 0,
        windowFunctionCount: Int = 0,
        caseExpressionCount: Int = 0,
        unionCount: Int = 0,
        cteCount: Int = 0,
        recursiveQueryCount: Int = 0,
        pivotCount: Int = 0,
        lateralJoinCount: Int = 0,
        totalComplexityScore: Int = 1
    ): AstAnalysisResult {
        return AstAnalysisResult(
            tableColumnInfo = TableColumnInfo(
                tables = tables,
                columns = columns,
                tableAliases = tableAliases
            ),
            functionExpressionInfo = FunctionExpressionInfo(
                functions = functions,
                expressions = expressions,
                aggregateFunctions = aggregateFunctions,
                windowFunctions = windowFunctions
            ),
            complexityDetails = createComplexityDetails(
                joinCount = joinCount,
                subqueryCount = subqueryCount,
                functionCount = functionCount,
                aggregateCount = aggregateCount,
                windowFunctionCount = windowFunctionCount,
                caseExpressionCount = caseExpressionCount,
                unionCount = unionCount,
                cteCount = cteCount,
                recursiveQueryCount = recursiveQueryCount,
                pivotCount = pivotCount,
                lateralJoinCount = lateralJoinCount,
                totalComplexityScore = totalComplexityScore
            )
        )
    }

    private fun createComplexityDetails(
        joinCount: Int = 0,
        subqueryCount: Int = 0,
        functionCount: Int = 0,
        aggregateCount: Int = 0,
        windowFunctionCount: Int = 0,
        caseExpressionCount: Int = 0,
        unionCount: Int = 0,
        cteCount: Int = 0,
        recursiveQueryCount: Int = 0,
        pivotCount: Int = 0,
        lateralJoinCount: Int = 0,
        totalComplexityScore: Int = 1
    ): ComplexityDetails {
        return ComplexityDetails(
            joinCount = joinCount,
            subqueryCount = subqueryCount,
            functionCount = functionCount,
            aggregateCount = aggregateCount,
            windowFunctionCount = windowFunctionCount,
            caseExpressionCount = caseExpressionCount,
            unionCount = unionCount,
            cteCount = cteCount,
            recursiveQueryCount = recursiveQueryCount,
            pivotCount = pivotCount,
            lateralJoinCount = lateralJoinCount,
            totalComplexityScore = totalComplexityScore
        )
    }
}
