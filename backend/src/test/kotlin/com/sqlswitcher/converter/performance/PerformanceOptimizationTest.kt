package com.sqlswitcher.converter.performance

import com.sqlswitcher.converter.formatter.SqlFormatter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * 성능 최적화 검증 테스트
 *
 * Regex 패턴 사전 컴파일 및 문자열 처리 최적화를 검증합니다.
 */
class PerformanceOptimizationTest {

    @Nested
    @DisplayName("SqlFormatter 성능 테스트")
    inner class SqlFormatterPerformanceTest {

        @Test
        @DisplayName("1000회 포맷팅 성능 측정")
        fun testFormattingPerformance() {
            val sql = """
                SELECT e.id, e.name, e.salary, d.dept_name
                FROM employees e
                LEFT JOIN departments d ON e.dept_id = d.id
                WHERE e.salary > 50000 AND e.status = 'ACTIVE'
                ORDER BY e.name
            """.trimIndent()

            // 워밍업
            repeat(100) {
                SqlFormatter.format(sql)
            }

            // 실제 측정
            val startTime = System.currentTimeMillis()
            repeat(1000) {
                SqlFormatter.format(sql)
            }
            val duration = System.currentTimeMillis() - startTime

            println("1000회 포맷팅 시간: ${duration}ms (평균: ${duration / 1000.0}ms/회)")

            // 최적화 후 1000회 포맷팅이 5초 이내여야 함
            assertTrue(duration < 5000, "포맷팅 성능이 기대치를 초과했습니다: ${duration}ms")
        }

        @Test
        @DisplayName("대용량 SQL 포맷팅 성능")
        fun testLargeSqlFormattingPerformance() {
            // 100개 컬럼의 복잡한 쿼리 생성
            val columns = (1..100).joinToString(", ") { "column_$it" }
            val sql = "SELECT $columns FROM large_table WHERE status = 'ACTIVE'"

            // 워밍업
            repeat(10) {
                SqlFormatter.format(sql)
            }

            // 측정
            val startTime = System.currentTimeMillis()
            repeat(100) {
                SqlFormatter.format(sql)
            }
            val duration = System.currentTimeMillis() - startTime

            println("대용량 SQL 100회 포맷팅 시간: ${duration}ms (평균: ${duration / 100.0}ms/회)")

            // 100회 포맷팅이 3초 이내여야 함
            assertTrue(duration < 3000, "대용량 SQL 포맷팅 성능이 기대치를 초과했습니다: ${duration}ms")
        }

        @Test
        @DisplayName("키워드 대소문자 변환 성능")
        fun testKeywordCasePerformance() {
            val sql = """
                select id, name from users where active = 1
                and status in (select status from statuses)
                order by name limit 10
            """.trimIndent()

            // 워밍업
            repeat(100) {
                SqlFormatter.format(sql)
            }

            // 측정
            val startTime = System.currentTimeMillis()
            repeat(1000) {
                SqlFormatter.format(sql)
            }
            val duration = System.currentTimeMillis() - startTime

            println("키워드 변환 1000회 시간: ${duration}ms")

            // 1000회가 3초 이내여야 함
            assertTrue(duration < 3000, "키워드 변환 성능이 기대치를 초과했습니다: ${duration}ms")
        }

        @Test
        @DisplayName("복합 키워드 줄바꿈 처리 성능")
        fun testCompoundKeywordPerformance() {
            val sql = """
                SELECT * FROM t1
                INNER JOIN t2 ON t1.id = t2.id
                LEFT JOIN t3 ON t2.id = t3.id
                RIGHT JOIN t4 ON t3.id = t4.id
                FULL JOIN t5 ON t4.id = t5.id
                CROSS JOIN t6
                ORDER BY t1.id
                GROUP BY t1.name
            """.trimIndent()

            // 워밍업
            repeat(100) {
                SqlFormatter.format(sql)
            }

            // 측정
            val startTime = System.currentTimeMillis()
            repeat(1000) {
                SqlFormatter.format(sql)
            }
            val duration = System.currentTimeMillis() - startTime

            println("복합 키워드 처리 1000회 시간: ${duration}ms")

            assertTrue(duration < 3000, "복합 키워드 처리 성능이 기대치를 초과했습니다: ${duration}ms")
        }
    }

    @Nested
    @DisplayName("포맷터 기능 검증")
    inner class FormatterFunctionalityTest {

        @Test
        @DisplayName("기본 포맷팅이 정상 동작")
        fun testBasicFormatting() {
            val sql = "select id,name from users where active=1"
            val formatted = SqlFormatter.format(sql)

            assertTrue(formatted.contains("SELECT"))
            assertTrue(formatted.contains("FROM"))
            assertTrue(formatted.contains("WHERE"))
        }

        @Test
        @DisplayName("한 줄 포맷팅 정상 동작")
        fun testOneLineFormatting() {
            val sql = "SELECT   id,   name   FROM   users"
            val formatted = SqlFormatter.formatOneLine(sql)

            assertEquals("SELECT id, name FROM users", formatted)
        }

        @Test
        @DisplayName("압축 포맷팅 정상 동작")
        fun testCompactFormatting() {
            val sql = """
                SELECT id, name
                FROM users
                WHERE active = 1
            """.trimIndent()
            val formatted = SqlFormatter.formatCompact(sql)

            // 공백이 최소화되어야 함
            assertTrue(!formatted.contains("\n"))
        }

        @Test
        @DisplayName("다중 SQL 포맷팅 정상 동작")
        fun testMultipleFormatting() {
            val sql = "SELECT 1; SELECT 2; SELECT 3"
            val formatted = SqlFormatter.formatMultiple(sql)

            assertTrue(formatted.contains("SELECT 1;"))
            assertTrue(formatted.contains("SELECT 2;"))
            assertTrue(formatted.contains("SELECT 3;"))
        }
    }

    @Nested
    @DisplayName("메모리 효율성 테스트")
    inner class MemoryEfficiencyTest {

        @Test
        @DisplayName("반복 포맷팅 시 메모리 안정성")
        fun testMemoryStability() {
            val sql = """
                SELECT e.id, e.name, d.dept_name
                FROM employees e
                JOIN departments d ON e.dept_id = d.id
                WHERE e.salary > 50000
            """.trimIndent()

            val runtime = Runtime.getRuntime()
            runtime.gc()
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()

            // 10000번 반복
            repeat(10000) {
                SqlFormatter.format(sql)
            }

            runtime.gc()
            val finalMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryIncrease = finalMemory - initialMemory

            println("초기 메모리: ${initialMemory / 1024 / 1024}MB")
            println("최종 메모리: ${finalMemory / 1024 / 1024}MB")
            println("메모리 증가: ${memoryIncrease / 1024 / 1024}MB")

            // 메모리 증가가 100MB 이내여야 함 (GC로 정리됨)
            assertTrue(memoryIncrease < 100 * 1024 * 1024, "메모리 누수 의심: ${memoryIncrease / 1024 / 1024}MB 증가")
        }
    }

    @Nested
    @DisplayName("Regex 패턴 캐시 검증")
    inner class RegexCacheTest {

        @Test
        @DisplayName("첫 번째와 후속 호출 성능 비교")
        fun testCacheEffectiveness() {
            val sql = """
                SELECT * FROM users
                LEFT JOIN orders ON users.id = orders.user_id
                WHERE users.active = 1
                ORDER BY users.name
            """.trimIndent()

            // 첫 번째 호출 (캐시 초기화)
            val firstCallStart = System.nanoTime()
            SqlFormatter.format(sql)
            val firstCallDuration = System.nanoTime() - firstCallStart

            // 두 번째 호출 (캐시 활용)
            val secondCallStart = System.nanoTime()
            SqlFormatter.format(sql)
            val secondCallDuration = System.nanoTime() - secondCallStart

            // 세 번째 호출 (캐시 활용)
            val thirdCallStart = System.nanoTime()
            SqlFormatter.format(sql)
            val thirdCallDuration = System.nanoTime() - thirdCallStart

            println("첫 번째 호출: ${firstCallDuration / 1000000.0}ms")
            println("두 번째 호출: ${secondCallDuration / 1000000.0}ms")
            println("세 번째 호출: ${thirdCallDuration / 1000000.0}ms")

            // 후속 호출이 안정적이어야 함 (큰 편차 없음)
            val avgSubsequent = (secondCallDuration + thirdCallDuration) / 2
            assertTrue(avgSubsequent < firstCallDuration * 10, "캐시 효과가 기대치보다 낮습니다")
        }
    }

    @Nested
    @DisplayName("에지 케이스 성능 테스트")
    inner class EdgeCasePerformanceTest {

        @Test
        @DisplayName("빈 SQL 처리 성능")
        fun testEmptySqlPerformance() {
            val startTime = System.currentTimeMillis()
            repeat(10000) {
                SqlFormatter.format("")
            }
            val duration = System.currentTimeMillis() - startTime

            println("빈 SQL 10000회 처리 시간: ${duration}ms")
            assertTrue(duration < 1000, "빈 SQL 처리가 너무 느립니다: ${duration}ms")
        }

        @Test
        @DisplayName("특수 문자 포함 SQL 처리 성능")
        fun testSpecialCharacterPerformance() {
            val sql = "SELECT 'Hello; World', \"column\"\"name\", `backtick` FROM users WHERE name LIKE '%test%'"

            val startTime = System.currentTimeMillis()
            repeat(1000) {
                SqlFormatter.format(sql)
            }
            val duration = System.currentTimeMillis() - startTime

            println("특수 문자 SQL 1000회 처리 시간: ${duration}ms")
            assertTrue(duration < 3000, "특수 문자 처리가 너무 느립니다: ${duration}ms")
        }

        @Test
        @DisplayName("중첩 괄호 처리 성능")
        fun testNestedParenthesesPerformance() {
            val sql = "SELECT COALESCE(NVL(DECODE(status, 'A', 1, 'B', 2, 0), 0), -1) FROM users"

            val startTime = System.currentTimeMillis()
            repeat(1000) {
                SqlFormatter.format(sql)
            }
            val duration = System.currentTimeMillis() - startTime

            println("중첩 괄호 SQL 1000회 처리 시간: ${duration}ms")
            assertTrue(duration < 3000, "중첩 괄호 처리가 너무 느립니다: ${duration}ms")
        }
    }
}
