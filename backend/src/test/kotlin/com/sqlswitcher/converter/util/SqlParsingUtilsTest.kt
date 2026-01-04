package com.sqlswitcher.converter.util

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals

/**
 * SqlParsingUtils 단위 테스트
 */
class SqlParsingUtilsTest {

    @Nested
    @DisplayName("findMatchingBracket 테스트")
    inner class FindMatchingBracketTest {

        @Test
        @DisplayName("기본 괄호 매칭")
        fun testBasicBracketMatching() {
            val sql = "FUNC(arg1, arg2)"
            val result = SqlParsingUtils.findMatchingBracket(sql, 5)
            assertEquals(16, result)
        }

        @Test
        @DisplayName("중첩된 괄호 매칭")
        fun testNestedBrackets() {
            val sql = "OUTER(INNER(a, b), c)"
            val result = SqlParsingUtils.findMatchingBracket(sql, 6)
            assertEquals(21, result)
        }

        @Test
        @DisplayName("깊게 중첩된 괄호")
        fun testDeeplyNestedBrackets() {
            val sql = "A(B(C(D(x))))"
            val result = SqlParsingUtils.findMatchingBracket(sql, 2)
            assertEquals(13, result)
        }

        @Test
        @DisplayName("문자열 리터럴 내부 괄호 무시")
        fun testIgnoresParensInStringLiteral() {
            val sql = "FUNC('(test)', arg)"
            val result = SqlParsingUtils.findMatchingBracket(sql, 5)
            assertEquals(19, result)
        }

        @Test
        @DisplayName("이스케이프된 작은따옴표 처리")
        fun testEscapedSingleQuotes() {
            val sql = "FUNC('it''s (test)', arg)"
            val result = SqlParsingUtils.findMatchingBracket(sql, 5)
            assertEquals(25, result)
        }

        @Test
        @DisplayName("큰따옴표 문자열 내부 괄호 무시")
        fun testIgnoresParensInDoubleQuotes() {
            val sql = "FUNC(\"(column)\", arg)"
            val result = SqlParsingUtils.findMatchingBracket(sql, 5)
            assertEquals(21, result)
        }

        @Test
        @DisplayName("매칭 실패 시 -1 반환")
        fun testReturnsMinusOneOnFailure() {
            val sql = "FUNC(arg1, arg2"
            val result = SqlParsingUtils.findMatchingBracket(sql, 5)
            assertEquals(-1, result)
        }

        @Test
        @DisplayName("빈 괄호")
        fun testEmptyParentheses() {
            val sql = "FUNC()"
            val result = SqlParsingUtils.findMatchingBracket(sql, 5)
            assertEquals(6, result)
        }

        @Test
        @DisplayName("복잡한 중첩 구조")
        fun testComplexNestedStructure() {
            val sql = "DECODE(a, 1, NVL(b, 'default'), 2, COALESCE(c, d, e))"
            val result = SqlParsingUtils.findMatchingBracket(sql, 7)
            assertEquals(53, result)
        }
    }

    @Nested
    @DisplayName("splitFunctionArgs 테스트")
    inner class SplitFunctionArgsTest {

        @Test
        @DisplayName("기본 인자 분리")
        fun testBasicArgSplit() {
            val args = "arg1, arg2, arg3"
            val result = SqlParsingUtils.splitFunctionArgs(args)
            assertEquals(listOf("arg1", "arg2", "arg3"), result)
        }

        @Test
        @DisplayName("중첩 함수가 있는 인자")
        fun testNestedFunctionArgs() {
            val args = "a, INNER(b, c), d"
            val result = SqlParsingUtils.splitFunctionArgs(args)
            assertEquals(listOf("a", "INNER(b, c)", "d"), result)
        }

        @Test
        @DisplayName("문자열 리터럴 내부 콤마 무시")
        fun testIgnoresCommaInString() {
            val args = "a, 'hello, world', b"
            val result = SqlParsingUtils.splitFunctionArgs(args)
            assertEquals(listOf("a", "'hello, world'", "b"), result)
        }

        @Test
        @DisplayName("이스케이프된 따옴표 처리")
        fun testEscapedQuotes() {
            val args = "a, 'it''s, fine', b"
            val result = SqlParsingUtils.splitFunctionArgs(args)
            assertEquals(listOf("a", "'it''s, fine'", "b"), result)
        }

        @Test
        @DisplayName("큰따옴표 문자열 내부 콤마 무시")
        fun testIgnoresCommaInDoubleQuotes() {
            val args = "a, \"hello, world\", b"
            val result = SqlParsingUtils.splitFunctionArgs(args)
            assertEquals(listOf("a", "\"hello, world\"", "b"), result)
        }

        @Test
        @DisplayName("단일 인자")
        fun testSingleArg() {
            val args = "single_arg"
            val result = SqlParsingUtils.splitFunctionArgs(args)
            assertEquals(listOf("single_arg"), result)
        }

        @Test
        @DisplayName("빈 문자열")
        fun testEmptyString() {
            val args = ""
            val result = SqlParsingUtils.splitFunctionArgs(args)
            assertEquals(emptyList<String>(), result)
        }

        @Test
        @DisplayName("공백만 있는 인자")
        fun testWhitespaceOnly() {
            val args = "  a  ,  b  ,  c  "
            val result = SqlParsingUtils.splitFunctionArgs(args)
            assertEquals(listOf("a", "b", "c"), result)
        }

        @Test
        @DisplayName("깊게 중첩된 함수")
        fun testDeeplyNestedFunctions() {
            val args = "A(B(C(1, 2), 3), 4), 5"
            val result = SqlParsingUtils.splitFunctionArgs(args)
            assertEquals(listOf("A(B(C(1, 2), 3), 4)", "5"), result)
        }

        @Test
        @DisplayName("복잡한 DECODE 인자")
        fun testComplexDecodeArgs() {
            val args = "status, 'A', 'Active', 'I', 'Inactive', 'Unknown'"
            val result = SqlParsingUtils.splitFunctionArgs(args)
            assertEquals(listOf("status", "'A'", "'Active'", "'I'", "'Inactive'", "'Unknown'"), result)
        }

        @Test
        @DisplayName("혼합된 복잡한 케이스")
        fun testMixedComplexCase() {
            val args = "NVL(a, 'default, value'), DECODE(b, 1, 'one', 'other'), c"
            val result = SqlParsingUtils.splitFunctionArgs(args)
            assertEquals(3, result.size)
            assertEquals("NVL(a, 'default, value')", result[0])
            assertEquals("DECODE(b, 1, 'one', 'other')", result[1])
            assertEquals("c", result[2])
        }
    }

    @Nested
    @DisplayName("extractExpressionBeforeWithIndex 테스트")
    inner class ExtractExpressionBeforeWithIndexTest {

        @Test
        @DisplayName("단순 컬럼명 추출")
        fun testSimpleColumn() {
            val sql = "column_name || ' suffix'"
            val (expr, idx) = SqlParsingUtils.extractExpressionBeforeWithIndex(sql, 12)
            assertEquals("column_name", expr)
            assertEquals(0, idx)
        }

        @Test
        @DisplayName("테이블.컬럼 형식 추출")
        fun testTableDotColumn() {
            val sql = "table.column || ' suffix'"
            val (expr, idx) = SqlParsingUtils.extractExpressionBeforeWithIndex(sql, 13)
            assertEquals("table.column", expr)
            assertEquals(0, idx)
        }

        @Test
        @DisplayName("문자열 리터럴 추출")
        fun testStringLiteral() {
            val sql = "'prefix' || column"
            val (expr, idx) = SqlParsingUtils.extractExpressionBeforeWithIndex(sql, 9)
            assertEquals("'prefix'", expr)
            assertEquals(0, idx)
        }

        @Test
        @DisplayName("함수 호출 추출")
        fun testFunctionCall() {
            val sql = "UPPER(name) || ' suffix'"
            val (expr, idx) = SqlParsingUtils.extractExpressionBeforeWithIndex(sql, 12)
            assertEquals("UPPER(name)", expr)
            assertEquals(0, idx)
        }

        @Test
        @DisplayName("중첩 함수 호출 추출")
        fun testNestedFunctionCall() {
            val sql = "TRIM(UPPER(name)) || ' suffix'"
            val (expr, idx) = SqlParsingUtils.extractExpressionBeforeWithIndex(sql, 18)
            assertEquals("TRIM(UPPER(name))", expr)
            assertEquals(0, idx)
        }

        @Test
        @DisplayName("공백이 있는 경우")
        fun testWithWhitespace() {
            val sql = "column   || suffix"
            val (expr, idx) = SqlParsingUtils.extractExpressionBeforeWithIndex(sql, 9)
            assertEquals("column", expr)
            assertEquals(0, idx)
        }

        @Test
        @DisplayName("|| 앞에 아무것도 없는 경우")
        fun testEmptyBefore() {
            val sql = "|| suffix"
            val (expr, idx) = SqlParsingUtils.extractExpressionBeforeWithIndex(sql, 0)
            assertEquals("", expr)
            assertEquals(0, idx)
        }
    }

    @Nested
    @DisplayName("extractExpressionAfterWithIndex 테스트")
    inner class ExtractExpressionAfterWithIndexTest {

        @Test
        @DisplayName("단순 컬럼명 추출")
        fun testSimpleColumn() {
            val sql = "prefix || column_name"
            val (expr, idx) = SqlParsingUtils.extractExpressionAfterWithIndex(sql, 10)
            assertEquals("column_name", expr)
            assertEquals(21, idx)
        }

        @Test
        @DisplayName("테이블.컬럼 형식 추출")
        fun testTableDotColumn() {
            val sql = "prefix || table.column"
            val (expr, idx) = SqlParsingUtils.extractExpressionAfterWithIndex(sql, 10)
            assertEquals("table.column", expr)
            assertEquals(22, idx)
        }

        @Test
        @DisplayName("문자열 리터럴 추출")
        fun testStringLiteral() {
            val sql = "column || 'suffix'"
            val (expr, idx) = SqlParsingUtils.extractExpressionAfterWithIndex(sql, 10)
            assertEquals("'suffix'", expr)
            assertEquals(18, idx)
        }

        @Test
        @DisplayName("함수 호출 추출")
        fun testFunctionCall() {
            val sql = "prefix || LOWER(name)"
            val (expr, idx) = SqlParsingUtils.extractExpressionAfterWithIndex(sql, 10)
            assertEquals("LOWER(name)", expr)
            assertEquals(21, idx)
        }

        @Test
        @DisplayName("중첩 함수 호출 추출")
        fun testNestedFunctionCall() {
            val sql = "prefix || TRIM(LOWER(name))"
            val (expr, idx) = SqlParsingUtils.extractExpressionAfterWithIndex(sql, 10)
            assertEquals("TRIM(LOWER(name))", expr)
            assertEquals(27, idx)
        }

        @Test
        @DisplayName("괄호로 시작하는 표현식")
        fun testParenthesizedExpression() {
            val sql = "prefix || (a + b)"
            val (expr, idx) = SqlParsingUtils.extractExpressionAfterWithIndex(sql, 10)
            assertEquals("(a + b)", expr)
            assertEquals(17, idx)
        }

        @Test
        @DisplayName("공백이 있는 경우")
        fun testWithWhitespace() {
            val sql = "prefix ||   column"
            val (expr, idx) = SqlParsingUtils.extractExpressionAfterWithIndex(sql, 9)
            assertEquals("column", expr)
            assertEquals(18, idx)
        }

        @Test
        @DisplayName("|| 뒤에 아무것도 없는 경우")
        fun testEmptyAfter() {
            val sql = "prefix ||"
            val (expr, idx) = SqlParsingUtils.extractExpressionAfterWithIndex(sql, 9)
            assertEquals("", expr)
            assertEquals(9, idx)
        }
    }

    @Nested
    @DisplayName("통합 시나리오 테스트")
    inner class IntegrationScenarioTest {

        @Test
        @DisplayName("DECODE 함수 파싱")
        fun testDecodeFunction() {
            val sql = "DECODE(status, 'A', 'Active', 'I', 'Inactive', 'Unknown')"
            val argsStart = 7
            val endIdx = SqlParsingUtils.findMatchingBracket(sql, argsStart)
            assertEquals(57, endIdx)

            val argsStr = sql.substring(argsStart, endIdx - 1)
            val args = SqlParsingUtils.splitFunctionArgs(argsStr)
            assertEquals(6, args.size)
            assertEquals("status", args[0])
            assertEquals("'A'", args[1])
            assertEquals("'Active'", args[2])
        }

        @Test
        @DisplayName("NVL2 함수 파싱")
        fun testNvl2Function() {
            val sql = "NVL2(commission, salary + commission, salary)"
            val argsStart = 5
            val endIdx = SqlParsingUtils.findMatchingBracket(sql, argsStart)
            assertEquals(45, endIdx)

            val argsStr = sql.substring(argsStart, endIdx - 1)
            val args = SqlParsingUtils.splitFunctionArgs(argsStr)
            assertEquals(3, args.size)
            assertEquals("commission", args[0])
            assertEquals("salary + commission", args[1])
            assertEquals("salary", args[2])
        }

        @Test
        @DisplayName("중첩 함수 파싱")
        fun testNestedFunctions() {
            val sql = "DECODE(a, 1, NVL(b, 0), 2, NVL(c, 0), -1)"
            val argsStart = 7
            val endIdx = SqlParsingUtils.findMatchingBracket(sql, argsStart)
            assertEquals(41, endIdx)

            val argsStr = sql.substring(argsStart, endIdx - 1)
            val args = SqlParsingUtils.splitFunctionArgs(argsStr)
            assertEquals(6, args.size)
            assertEquals("a", args[0])
            assertEquals("1", args[1])
            assertEquals("NVL(b, 0)", args[2])
            assertEquals("2", args[3])
            assertEquals("NVL(c, 0)", args[4])
            assertEquals("-1", args[5])
        }

        @Test
        @DisplayName("복잡한 문자열이 포함된 함수")
        fun testFunctionWithComplexStrings() {
            val sql = "DECODE(type, 'A,B', 'Type (A) or (B)', 'C', 'Type C')"
            val argsStart = 7
            val endIdx = SqlParsingUtils.findMatchingBracket(sql, argsStart)

            val argsStr = sql.substring(argsStart, endIdx - 1)
            val args = SqlParsingUtils.splitFunctionArgs(argsStr)
            assertEquals(5, args.size)
            assertEquals("type", args[0])
            assertEquals("'A,B'", args[1])
            assertEquals("'Type (A) or (B)'", args[2])
            assertEquals("'C'", args[3])
            assertEquals("'Type C'", args[4])
        }
    }
}
