package com.sqlswitcher.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SqlHighlightService unit tests
 */
class SqlHighlightServiceTest {

    private lateinit var sqlHighlightService: SqlHighlightService

    @BeforeEach
    fun setUp() {
        sqlHighlightService = SqlHighlightService()
    }

    @Nested
    @DisplayName("Keyword Highlighting Tests")
    inner class KeywordHighlightingTest {

        @Test
        @DisplayName("Should highlight SELECT keyword")
        fun testSelectKeywordHighlighting() {
            // Given
            val sql = "SELECT id FROM users"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val selectToken = result.tokens.find { it.value == "SELECT" }
            assertEquals(TokenType.KEYWORD, selectToken?.type)
            assertTrue(result.html.contains("""<span class="sql-keyword">SELECT</span>"""))
        }

        @Test
        @DisplayName("Should highlight multiple SQL keywords")
        fun testMultipleKeywordsHighlighting() {
            // Given
            val sql = "SELECT id FROM users WHERE active = 1 ORDER BY name"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val keywords = result.tokens.filter { it.type == TokenType.KEYWORD }
            val keywordValues = keywords.map { it.value.uppercase() }
            assertTrue(keywordValues.contains("SELECT"))
            assertTrue(keywordValues.contains("FROM"))
            assertTrue(keywordValues.contains("WHERE"))
            assertTrue(keywordValues.contains("ORDER"))
            assertTrue(keywordValues.contains("BY"))
        }

        @Test
        @DisplayName("Should highlight case-insensitive keywords")
        fun testCaseInsensitiveKeywords() {
            // Given
            val sql = "select ID from USERS where active = 1"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val selectToken = result.tokens.find { it.value.equals("select", ignoreCase = true) }
            assertEquals(TokenType.KEYWORD, selectToken?.type)
        }

        @Test
        @DisplayName("Should highlight JOIN keywords")
        fun testJoinKeywordsHighlighting() {
            // Given
            val sql = "SELECT * FROM orders INNER JOIN customers ON orders.cust_id = customers.id"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val keywords = result.tokens.filter { it.type == TokenType.KEYWORD }
            val keywordValues = keywords.map { it.value.uppercase() }
            assertTrue(keywordValues.contains("INNER"))
            assertTrue(keywordValues.contains("JOIN"))
            assertTrue(keywordValues.contains("ON"))
        }
    }

    @Nested
    @DisplayName("Function Highlighting Tests")
    inner class FunctionHighlightingTest {

        @Test
        @DisplayName("Should highlight aggregate functions")
        fun testAggregateFunctionHighlighting() {
            // Given
            val sql = "SELECT COUNT(*), SUM(amount), AVG(price) FROM orders"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val functions = result.tokens.filter { it.type == TokenType.FUNCTION }
            val functionValues = functions.map { it.value.uppercase() }
            assertTrue(functionValues.contains("COUNT"))
            assertTrue(functionValues.contains("SUM"))
            assertTrue(functionValues.contains("AVG"))
        }

        @Test
        @DisplayName("Should highlight string functions")
        fun testStringFunctionHighlighting() {
            // Given
            val sql = "SELECT UPPER(name), LOWER(email), TRIM(address) FROM users"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val functions = result.tokens.filter { it.type == TokenType.FUNCTION }
            val functionValues = functions.map { it.value.uppercase() }
            assertTrue(functionValues.contains("UPPER"))
            assertTrue(functionValues.contains("LOWER"))
            assertTrue(functionValues.contains("TRIM"))
        }

        @Test
        @DisplayName("Should highlight date functions")
        fun testDateFunctionHighlighting() {
            // Given
            val sql = "SELECT NOW(), SYSDATE, CURRENT_DATE FROM dual"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val functions = result.tokens.filter { it.type == TokenType.FUNCTION }
            val functionValues = functions.map { it.value.uppercase() }
            assertTrue(functionValues.contains("NOW"))
            assertTrue(functionValues.contains("SYSDATE"))
            assertTrue(functionValues.contains("CURRENT_DATE"))
        }

        @Test
        @DisplayName("Should highlight NULL handling functions")
        fun testNullHandlingFunctionHighlighting() {
            // Given
            val sql = "SELECT COALESCE(name, 'N/A'), NVL(email, 'unknown'), IFNULL(phone, '') FROM users"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val functions = result.tokens.filter { it.type == TokenType.FUNCTION }
            val functionValues = functions.map { it.value.uppercase() }
            assertTrue(functionValues.contains("COALESCE"))
            assertTrue(functionValues.contains("NVL"))
            assertTrue(functionValues.contains("IFNULL"))
        }
    }

    @Nested
    @DisplayName("String Literal Handling Tests")
    inner class StringLiteralTest {

        @Test
        @DisplayName("Should highlight simple string literals")
        fun testSimpleStringLiteralHighlighting() {
            // Given
            val sql = "SELECT * FROM users WHERE name = 'John'"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val stringToken = result.tokens.find { it.value == "'John'" }
            assertEquals(TokenType.STRING, stringToken?.type)
            assertTrue(result.html.contains("""<span class="sql-string">&#39;John&#39;</span>"""))
        }

        @Test
        @DisplayName("Should handle string with spaces")
        fun testStringWithSpaces() {
            // Given
            val sql = "SELECT * FROM users WHERE name = 'John Doe'"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val stringToken = result.tokens.find { it.value == "'John Doe'" }
            assertEquals(TokenType.STRING, stringToken?.type)
        }
    }

    @Nested
    @DisplayName("Number Highlighting Tests")
    inner class NumberHighlightingTest {

        @Test
        @DisplayName("Should highlight integer numbers")
        fun testIntegerHighlighting() {
            // Given
            val sql = "SELECT * FROM users WHERE age = 25"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val numberToken = result.tokens.find { it.value == "25" }
            assertEquals(TokenType.NUMBER, numberToken?.type)
            assertTrue(result.html.contains("""<span class="sql-number">25</span>"""))
        }

        @Test
        @DisplayName("Should highlight decimal numbers")
        fun testDecimalHighlighting() {
            // Given
            val sql = "SELECT * FROM products WHERE price > 99.99"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val numberToken = result.tokens.find { it.value == "99.99" }
            assertEquals(TokenType.NUMBER, numberToken?.type)
        }

        @Test
        @DisplayName("Should highlight scientific notation")
        fun testScientificNotationHighlighting() {
            // Given
            val sql = "SELECT * FROM data WHERE value > 1.5e10"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val numberToken = result.tokens.find { it.value == "1.5e10" }
            assertEquals(TokenType.NUMBER, numberToken?.type)
        }
    }

    @Nested
    @DisplayName("Comment Handling Tests")
    inner class CommentHandlingTest {

        @Test
        @DisplayName("Should highlight single-line comments")
        fun testSingleLineCommentHighlighting() {
            // Given
            val sql = "SELECT * FROM users -- get all users"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val commentToken = result.tokens.find { it.value.startsWith("--") }
            assertEquals(TokenType.COMMENT, commentToken?.type)
            assertTrue(result.html.contains("""<span class="sql-comment">"""))
        }

        @Test
        @DisplayName("Should highlight multi-line comments")
        fun testMultiLineCommentHighlighting() {
            // Given
            val sql = "SELECT /* this is a comment */ * FROM users"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val commentToken = result.tokens.find { it.value.contains("this is a comment") }
            assertEquals(TokenType.COMMENT, commentToken?.type)
        }
    }

    @Nested
    @DisplayName("Operator Handling Tests")
    inner class OperatorHandlingTest {

        @Test
        @DisplayName("Should highlight comparison operators")
        fun testComparisonOperatorHighlighting() {
            // Given
            val sql = "SELECT * FROM users WHERE age > 18 AND age < 65"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val operators = result.tokens.filter { it.type == TokenType.OPERATOR }
            assertTrue(operators.any { it.value == ">" })
            assertTrue(operators.any { it.value == "<" })
        }

        @Test
        @DisplayName("Should highlight equality operators")
        fun testEqualityOperatorHighlighting() {
            // Given
            val sql = "SELECT * FROM users WHERE status = 'active' AND role <> 'admin'"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val operators = result.tokens.filter { it.type == TokenType.OPERATOR }
            assertTrue(operators.any { it.value == "=" })
            assertTrue(operators.any { it.value == "<>" })
        }

        @Test
        @DisplayName("Should highlight arithmetic operators")
        fun testArithmeticOperatorHighlighting() {
            // Given
            val sql = "SELECT price * quantity + tax - discount FROM orders"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val operators = result.tokens.filter { it.type == TokenType.OPERATOR }
            assertTrue(operators.any { it.value == "*" })
            assertTrue(operators.any { it.value == "+" })
            assertTrue(operators.any { it.value == "-" })
        }
    }

    @Nested
    @DisplayName("HTML and ANSI Generation Tests")
    inner class OutputGenerationTest {

        @Test
        @DisplayName("Should generate correct HTML output")
        fun testHtmlGeneration() {
            // Given
            val sql = "SELECT name FROM users"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            assertTrue(result.html.contains("<span class="))
            assertTrue(result.html.contains("sql-keyword"))
        }

        @Test
        @DisplayName("Should escape HTML special characters")
        fun testHtmlEscaping() {
            // Given
            val sql = "SELECT * FROM users WHERE name = '<script>'"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            assertTrue(result.html.contains("&lt;"))
            assertTrue(result.html.contains("&gt;"))
        }

        @Test
        @DisplayName("Should generate ANSI color codes")
        fun testAnsiGeneration() {
            // Given
            val sql = "SELECT name FROM users"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            // ANSI codes should be present
            assertTrue(result.ansi.contains("\u001B["))
            // Reset code should be present
            assertTrue(result.ansi.contains("\u001B[0m"))
        }

        @Test
        @DisplayName("Should preserve original SQL in result")
        fun testOriginalSqlPreservation() {
            // Given
            val sql = "SELECT * FROM users WHERE id = 1"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            assertEquals(sql, result.originalSql)
        }
    }

    @Nested
    @DisplayName("Token Position Tests")
    inner class TokenPositionTest {

        @Test
        @DisplayName("Should track token positions correctly")
        fun testTokenPositions() {
            // Given
            val sql = "SELECT id FROM users"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val selectToken = result.tokens.first { it.value == "SELECT" }
            assertEquals(0, selectToken.position)

            val fromToken = result.tokens.first { it.value == "FROM" }
            assertEquals(10, fromToken.position)
        }
    }

    @Nested
    @DisplayName("Parenthesis and Punctuation Tests")
    inner class ParenthesisAndPunctuationTest {

        @Test
        @DisplayName("Should identify parentheses")
        fun testParenthesesHighlighting() {
            // Given
            val sql = "SELECT COUNT(*) FROM users"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val parentheses = result.tokens.filter { it.type == TokenType.PARENTHESIS }
            assertEquals(2, parentheses.size)
        }

        @Test
        @DisplayName("Should identify punctuation")
        fun testPunctuationHighlighting() {
            // Given
            val sql = "SELECT id, name, email FROM users;"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val punctuations = result.tokens.filter { it.type == TokenType.PUNCTUATION }
            // Should have commas and semicolon
            assertTrue(punctuations.any { it.value == "," })
            assertTrue(punctuations.any { it.value == ";" })
        }
    }

    @Nested
    @DisplayName("Identifier Tests")
    inner class IdentifierTest {

        @Test
        @DisplayName("Should identify table and column names as identifiers")
        fun testIdentifierHighlighting() {
            // Given
            val sql = "SELECT user_name FROM my_table"

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val identifiers = result.tokens.filter { it.type == TokenType.IDENTIFIER }
            assertTrue(identifiers.any { it.value == "user_name" })
            assertTrue(identifiers.any { it.value == "my_table" })
        }

        @Test
        @DisplayName("Should handle quoted identifiers")
        fun testQuotedIdentifierHighlighting() {
            // Given
            val sql = """SELECT "User Name" FROM users"""

            // When
            val result = sqlHighlightService.highlightSql(sql)

            // Then
            val identifiers = result.tokens.filter { it.type == TokenType.IDENTIFIER }
            assertTrue(identifiers.any { it.value == "\"User Name\"" })
        }
    }
}
