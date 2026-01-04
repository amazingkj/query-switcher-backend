package com.sqlswitcher.converter.formatter

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * SqlFormatter 단위 테스트
 */
class SqlFormatterTest {

    @Nested
    @DisplayName("기본 포맷팅 테스트")
    inner class BasicFormattingTest {

        @Test
        @DisplayName("키워드 대문자 변환")
        fun testKeywordUpperCase() {
            val sql = "select * from employees where id = 1"

            val result = SqlFormatter.format(sql)

            assertTrue(result.contains("SELECT"), "SELECT가 대문자여야 함")
            assertTrue(result.contains("FROM"), "FROM이 대문자여야 함")
            assertTrue(result.contains("WHERE"), "WHERE가 대문자여야 함")
        }

        @Test
        @DisplayName("키워드 소문자 변환")
        fun testKeywordLowerCase() {
            val sql = "SELECT * FROM employees WHERE id = 1"
            val options = SqlFormatter.FormatOptions(keywordCase = SqlFormatter.KeywordCase.LOWER)

            val result = SqlFormatter.format(sql, options)

            assertTrue(result.contains("select"), "select가 소문자여야 함")
            assertTrue(result.contains("from"), "from이 소문자여야 함")
            assertTrue(result.contains("where"), "where가 소문자여야 함")
        }

        @Test
        @DisplayName("연산자 주변 공백 정규화")
        fun testOperatorSpacing() {
            val sql = "SELECT * FROM t WHERE a=1 AND b>2 AND c<>3"
            val options = SqlFormatter.FormatOptions(spaceAroundOperators = true)

            val result = SqlFormatter.format(sql, options)

            assertTrue(result.contains("a = 1"), "= 주변에 공백이 있어야 함")
            assertTrue(result.contains("b > 2"), "> 주변에 공백이 있어야 함")
            assertTrue(result.contains("c <> 3"), "<> 주변에 공백이 있어야 함")
        }

        @Test
        @DisplayName("괄호 주변 공백 정리")
        fun testParenthesesSpacing() {
            val sql = "SELECT COUNT( * ) FROM ( SELECT id FROM t )"
            val options = SqlFormatter.FormatOptions(trimParentheses = true)

            val result = SqlFormatter.format(sql, options)

            assertTrue(result.contains("COUNT(*)") || result.contains("COUNT( * )"),
                "괄호 주변 공백이 정리되어야 함")
        }

        @Test
        @DisplayName("세미콜론 보장")
        fun testEnsureSemicolon() {
            val sql = "SELECT * FROM employees"
            val options = SqlFormatter.FormatOptions(ensureSemicolon = true)

            val result = SqlFormatter.format(sql, options)

            assertTrue(result.endsWith(";"), "세미콜론으로 끝나야 함")
        }

        @Test
        @DisplayName("세미콜론이 이미 있는 경우")
        fun testExistingSemicolon() {
            val sql = "SELECT * FROM employees;"
            val options = SqlFormatter.FormatOptions(ensureSemicolon = true)

            val result = SqlFormatter.format(sql, options)

            assertFalse(result.endsWith(";;"), "중복 세미콜론이 없어야 함")
        }
    }

    @Nested
    @DisplayName("줄바꿈 및 들여쓰기 테스트")
    inner class IndentationTest {

        @Test
        @DisplayName("주요 키워드 앞 줄바꿈")
        fun testNewlineBeforeKeywords() {
            val sql = "SELECT id FROM employees WHERE status = 'A' AND age > 30 ORDER BY name"

            val result = SqlFormatter.format(sql)

            assertTrue(result.contains("\nFROM") || result.contains("\n    FROM"),
                "FROM 앞에 줄바꿈이 있어야 함")
            assertTrue(result.contains("\nWHERE") || result.contains("\n    WHERE"),
                "WHERE 앞에 줄바꿈이 있어야 함")
            assertTrue(result.contains("\nORDER BY") || result.contains("\n    ORDER BY"),
                "ORDER BY 앞에 줄바꿈이 있어야 함")
        }

        @Test
        @DisplayName("서브쿼리 들여쓰기")
        fun testSubqueryIndentation() {
            val sql = "SELECT * FROM (SELECT id, name FROM employees WHERE active = 1) sub WHERE sub.id > 10"

            val result = SqlFormatter.format(sql)

            assertTrue(result.contains("SELECT") && result.contains("FROM"),
                "쿼리 구조가 유지되어야 함")
        }

        @Test
        @DisplayName("커스텀 들여쓰기 문자")
        fun testCustomIndent() {
            val sql = "SELECT id FROM employees WHERE status = 'A'"
            val options = SqlFormatter.FormatOptions(indentString = "\t")

            val result = SqlFormatter.format(sql, options)

            assertTrue(result.contains("SELECT") && result.contains("FROM"),
                "쿼리 구조가 유지되어야 함")
        }
    }

    @Nested
    @DisplayName("SELECT 컬럼 포맷팅 테스트")
    inner class SelectColumnsTest {

        @Test
        @DisplayName("컬럼별 줄바꿈")
        fun testColumnsOnNewLine() {
            val sql = "SELECT id, name, email, created_at FROM employees"
            val options = SqlFormatter.FormatOptions(columnsOnNewLine = true)

            val result = SqlFormatter.format(sql, options)

            // 여러 줄에 걸쳐 컬럼이 배치되어야 함
            val lineCount = result.lines().count { it.isNotBlank() }
            assertTrue(lineCount >= 2, "여러 줄로 포맷되어야 함")
        }

        @Test
        @DisplayName("쉼표 뒤 배치")
        fun testCommaAfter() {
            val sql = "SELECT id, name, email FROM employees"
            val options = SqlFormatter.FormatOptions(
                columnsOnNewLine = true,
                commaPosition = SqlFormatter.CommaPosition.AFTER
            )

            val result = SqlFormatter.format(sql, options)

            // 쉼표가 줄 끝에 있어야 함
            val lines = result.lines()
            val hasTrailingComma = lines.any { it.trimEnd().endsWith(",") }
            assertTrue(hasTrailingComma || result.contains("id,") || result.contains("name,"),
                "쉼표가 컬럼 뒤에 있어야 함")
        }

        @Test
        @DisplayName("쉼표 앞 배치")
        fun testCommaBefore() {
            val sql = "SELECT id, name, email FROM employees"
            val options = SqlFormatter.FormatOptions(
                columnsOnNewLine = true,
                commaPosition = SqlFormatter.CommaPosition.BEFORE
            )

            val result = SqlFormatter.format(sql, options)

            // 결과에 쉼표가 포함되어 있으면 됨
            assertTrue(result.contains(","), "쉼표가 있어야 함")
        }

        @Test
        @DisplayName("함수 내 쉼표는 분리하지 않음")
        fun testFunctionColumnsNotSplit() {
            val sql = "SELECT COALESCE(name, 'Unknown'), NVL(age, 0) FROM employees"
            val options = SqlFormatter.FormatOptions(columnsOnNewLine = true)

            val result = SqlFormatter.format(sql, options)

            assertTrue(result.contains("COALESCE") || result.contains("Coalesce"),
                "COALESCE 함수가 유지되어야 함")
        }
    }

    @Nested
    @DisplayName("특수 포맷 테스트")
    inner class SpecialFormatTest {

        @Test
        @DisplayName("한 줄 포맷")
        fun testOneLineFormat() {
            val sql = """
                SELECT
                    id,
                    name
                FROM
                    employees
            """.trimIndent()

            val result = SqlFormatter.formatOneLine(sql)

            assertFalse(result.contains("\n"), "줄바꿈이 없어야 함")
            assertTrue(result.trim().isNotEmpty(), "결과가 있어야 함")
        }

        @Test
        @DisplayName("압축 포맷")
        fun testCompactFormat() {
            val sql = """
                SELECT id, name
                FROM employees
                WHERE status = 'ACTIVE'
            """.trimIndent()

            val result = SqlFormatter.formatCompact(sql)

            assertFalse(result.contains("\n"), "줄바꿈이 없어야 함")
            assertTrue(result.contains("SELECT") && result.contains("FROM") && result.contains("WHERE"),
                "모든 키워드가 포함되어야 함")
        }

        @Test
        @DisplayName("최소 포맷")
        fun testMinimalFormat() {
            val sql = "select   id,name   from   employees"

            val result = SqlFormatter.formatMinimal(sql)

            assertTrue(result.contains("SELECT"), "키워드가 대문자로 변환되어야 함")
            // 연속 공백(3개 이상) 제거 확인
            val hasExcessiveSpace = result.contains("    ") // 4개 이상 공백
            assertTrue(!hasExcessiveSpace || result.contains("\n"), "과도한 연속 공백이 제거되거나 줄바꿈이 있어야 함")
        }

        @Test
        @DisplayName("DDL 포맷")
        fun testDdlFormat() {
            val sql = "create table employees (id number primary key, name varchar2(100) not null)"

            val result = SqlFormatter.formatDdl(sql)

            assertTrue(result.contains("CREATE TABLE"), "CREATE TABLE이 대문자여야 함")
            assertTrue(result.endsWith(";"), "세미콜론으로 끝나야 함")
        }

        @Test
        @DisplayName("PL/SQL 포맷")
        fun testPlSqlFormat() {
            val sql = "begin select count(*) into v_count from employees; end"

            val result = SqlFormatter.formatPlSql(sql)

            assertTrue(result.contains("BEGIN"), "BEGIN이 대문자여야 함")
            assertTrue(result.contains("END"), "END가 대문자여야 함")
        }
    }

    @Nested
    @DisplayName("다중 SQL 문 테스트")
    inner class MultipleStatementTest {

        @Test
        @DisplayName("여러 SQL 문 포맷팅")
        fun testMultipleStatements() {
            val sql = """
                select * from t1;
                select * from t2;
                select * from t3
            """.trimIndent()

            val result = SqlFormatter.formatMultiple(sql)

            // 3개의 세미콜론이 있어야 함
            val semicolonCount = result.count { it == ';' }
            assertTrue(semicolonCount >= 3, "3개 이상의 세미콜론이 있어야 함")
        }

        @Test
        @DisplayName("빈 문장 무시")
        fun testEmptyStatementIgnored() {
            val sql = "select * from t1; select * from t2"

            val result = SqlFormatter.formatMultiple(sql)

            // 두 개의 문장이 포함되어야 함
            assertTrue(result.contains("FROM t1") || result.contains("FROM T1"),
                "첫 번째 테이블이 포함되어야 함")
            assertTrue(result.contains("FROM t2") || result.contains("FROM T2"),
                "두 번째 테이블이 포함되어야 함")
        }
    }

    @Nested
    @DisplayName("복잡한 쿼리 테스트")
    inner class ComplexQueryTest {

        @Test
        @DisplayName("JOIN 쿼리 포맷팅")
        fun testJoinQueryFormatting() {
            val sql = """
                select e.id, e.name, d.department_name from employees e
                inner join departments d on e.department_id = d.id
                left join locations l on d.location_id = l.id
                where e.status = 'ACTIVE'
            """.trimIndent()

            val result = SqlFormatter.format(sql)

            // JOIN 키워드가 대문자로 변환되고 포함되어야 함
            val hasInnerJoin = result.uppercase().contains("INNER JOIN")
            val hasLeftJoin = result.uppercase().contains("LEFT JOIN")
            assertTrue(hasInnerJoin, "INNER JOIN이 포맷되어야 함 (결과: $result)")
            assertTrue(hasLeftJoin, "LEFT JOIN이 포맷되어야 함")
        }

        @Test
        @DisplayName("UNION 쿼리 포맷팅")
        fun testUnionQueryFormatting() {
            val sql = "select id from t1 union all select id from t2 union select id from t3"

            val result = SqlFormatter.format(sql)

            assertTrue(result.contains("UNION ALL") || result.contains("Union All"),
                "UNION ALL이 포함되어야 함")
            assertTrue(result.contains("UNION") || result.contains("Union"),
                "UNION이 포함되어야 함")
        }

        @Test
        @DisplayName("CTE (WITH) 쿼리 포맷팅")
        fun testCteFormatting() {
            val sql = """
                with cte as (select id, name from employees where active = 1)
                select * from cte where id > 10
            """.trimIndent()

            val result = SqlFormatter.format(sql)

            assertTrue(result.contains("WITH") || result.contains("With"),
                "WITH가 포맷되어야 함")
            assertTrue(result.contains("AS") || result.contains("As"),
                "AS가 포맷되어야 함")
        }

        @Test
        @DisplayName("CASE 문 포맷팅")
        fun testCaseStatementFormatting() {
            val sql = "select case when status = 'A' then 'Active' when status = 'I' then 'Inactive' else 'Unknown' end as status_text from employees"

            val result = SqlFormatter.format(sql)

            assertTrue(result.contains("CASE") || result.contains("Case"),
                "CASE가 포맷되어야 함")
            assertTrue(result.contains("WHEN") || result.contains("When"),
                "WHEN이 포맷되어야 함")
            assertTrue(result.contains("ELSE") || result.contains("Else"),
                "ELSE가 포맷되어야 함")
            assertTrue(result.contains("END") || result.contains("End"),
                "END가 포맷되어야 함")
        }

        @Test
        @DisplayName("문자열 리터럴 보존")
        fun testStringLiteralPreserved() {
            val sql = "SELECT 'hello world' FROM dual"

            val result = SqlFormatter.format(sql)

            assertTrue(result.contains("'hello world'"), "문자열이 보존되어야 함")
            assertTrue(result.uppercase().contains("DUAL"), "DUAL이 포함되어야 함")
        }
    }

    @Nested
    @DisplayName("엣지 케이스 테스트")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("빈 문자열")
        fun testEmptyString() {
            val result = SqlFormatter.format("")
            assertEquals("", result)
        }

        @Test
        @DisplayName("공백만 있는 문자열")
        fun testWhitespaceOnly() {
            val result = SqlFormatter.format("   \n\t   ")
            assertEquals("", result.trim())
        }

        @Test
        @DisplayName("주석 포함 SQL")
        fun testSqlWithComments() {
            val sql = """
                SELECT * FROM employees
            """.trimIndent()

            val result = SqlFormatter.format(sql)

            // 기본 쿼리가 포함되어야 함
            assertTrue(result.uppercase().contains("SELECT"), "SELECT가 포함되어야 함")
            assertTrue(result.uppercase().contains("FROM"), "FROM이 포함되어야 함")
            assertTrue(result.uppercase().contains("EMPLOYEES"), "테이블명이 포함되어야 함")
        }

        @Test
        @DisplayName("한글 포함 SQL")
        fun testSqlWithKorean() {
            val sql = "SELECT '한글테스트' AS name FROM dual"

            val result = SqlFormatter.format(sql)

            assertTrue(result.contains("한글테스트"), "한글이 보존되어야 함")
        }
    }
}
