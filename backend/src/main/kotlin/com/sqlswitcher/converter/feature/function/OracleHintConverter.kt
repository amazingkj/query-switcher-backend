package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * Oracle 힌트 변환기
 *
 * Oracle 힌트 (/*+ ... */)를 타겟 DB에 맞게 처리
 *
 * 지원 힌트:
 * - INDEX, NO_INDEX, FULL - 인덱스/스캔 힌트
 * - PARALLEL - 병렬 처리 힌트
 * - LEADING, USE_NL, USE_HASH - 조인 힌트
 * - FIRST_ROWS, ALL_ROWS - 옵티마이저 힌트
 * - APPEND, NOAPPEND - 직접 로드 힌트
 * - CACHE, NOCACHE - 캐시 힌트
 *
 * 변환 전략:
 * - MySQL: 힌트 주석 유지 (호환 힌트는 변환, 미지원은 주석으로)
 * - PostgreSQL: 일부 힌트는 SET 명령으로 변환, 나머지는 주석화
 */
object OracleHintConverter {

    // Oracle 힌트 패턴
    private val HINT_PATTERN = Regex(
        """/\*\+\s*(.+?)\s*\*/""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // 개별 힌트 파싱 패턴
    private val INDEX_HINT_PATTERN = Regex(
        """INDEX\s*\(\s*(\w+)(?:\s+(\w+))?\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val NO_INDEX_HINT_PATTERN = Regex(
        """NO_INDEX\s*\(\s*(\w+)(?:\s+(\w+))?\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val FULL_HINT_PATTERN = Regex(
        """FULL\s*\(\s*(\w+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val PARALLEL_HINT_PATTERN = Regex(
        """PARALLEL\s*\(\s*(\w+)?(?:\s*,?\s*(\d+))?\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val LEADING_HINT_PATTERN = Regex(
        """LEADING\s*\(\s*(.+?)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val USE_NL_PATTERN = Regex(
        """USE_NL\s*\(\s*(.+?)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val USE_HASH_PATTERN = Regex(
        """USE_HASH\s*\(\s*(.+?)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val FIRST_ROWS_PATTERN = Regex(
        """FIRST_ROWS\s*(?:\(\s*(\d+)\s*\))?""",
        RegexOption.IGNORE_CASE
    )

    private val ALL_ROWS_PATTERN = Regex(
        """ALL_ROWS""",
        RegexOption.IGNORE_CASE
    )

    private val APPEND_PATTERN = Regex(
        """APPEND""",
        RegexOption.IGNORE_CASE
    )

    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql
        if (!HINT_PATTERN.containsMatchIn(sql)) return sql

        return when (targetDialect) {
            DialectType.MYSQL -> convertToMySql(sql, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertToPostgreSql(sql, warnings, appliedRules)
            DialectType.ORACLE -> sql
        }
    }

    /**
     * Oracle 힌트 → MySQL 변환
     */
    private fun convertToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql
        val convertedHints = mutableListOf<String>()
        val removedHints = mutableListOf<String>()

        result = HINT_PATTERN.replace(result) { match ->
            val hintContent = match.groupValues[1].trim()
            val mysqlHints = mutableListOf<String>()

            // INDEX 힌트 → FORCE INDEX
            INDEX_HINT_PATTERN.findAll(hintContent).forEach { indexMatch ->
                val table = indexMatch.groupValues[1]
                val index = indexMatch.groupValues[2].ifEmpty { null }
                if (index != null) {
                    mysqlHints.add("FORCE INDEX ($index)")
                    convertedHints.add("INDEX($table $index) → FORCE INDEX($index)")
                } else {
                    removedHints.add("INDEX($table) - 인덱스명 필요")
                }
            }

            // NO_INDEX 힌트 → IGNORE INDEX
            NO_INDEX_HINT_PATTERN.findAll(hintContent).forEach { indexMatch ->
                val table = indexMatch.groupValues[1]
                val index = indexMatch.groupValues[2].ifEmpty { null }
                if (index != null) {
                    mysqlHints.add("IGNORE INDEX ($index)")
                    convertedHints.add("NO_INDEX($table $index) → IGNORE INDEX($index)")
                } else {
                    removedHints.add("NO_INDEX($table)")
                }
            }

            // FULL 힌트 → IGNORE INDEX (모든 인덱스 무시)
            FULL_HINT_PATTERN.findAll(hintContent).forEach { fullMatch ->
                val table = fullMatch.groupValues[1]
                // MySQL에서는 테이블 뒤에 IGNORE INDEX를 붙여야 함
                removedHints.add("FULL($table) - 테이블 스캔은 MySQL에서 직접 지원되지 않음")
            }

            // PARALLEL 힌트 - MySQL에서 미지원
            if (PARALLEL_HINT_PATTERN.containsMatchIn(hintContent)) {
                removedHints.add("PARALLEL - MySQL에서 미지원")
            }

            // STRAIGHT_JOIN 관련 힌트
            if (LEADING_HINT_PATTERN.containsMatchIn(hintContent)) {
                mysqlHints.add("STRAIGHT_JOIN")
                convertedHints.add("LEADING → STRAIGHT_JOIN")
            }

            // 조인 힌트 - 경고만
            if (USE_NL_PATTERN.containsMatchIn(hintContent)) {
                removedHints.add("USE_NL - MySQL 옵티마이저에 위임")
            }
            if (USE_HASH_PATTERN.containsMatchIn(hintContent)) {
                removedHints.add("USE_HASH - MySQL 옵티마이저에 위임 (BNL/BKA)")
            }

            // FIRST_ROWS → SQL_BUFFER_RESULT 또는 LIMIT 추천
            if (FIRST_ROWS_PATTERN.containsMatchIn(hintContent)) {
                removedHints.add("FIRST_ROWS - LIMIT 사용 권장")
            }

            // 결과 생성
            if (mysqlHints.isNotEmpty()) {
                "/*+ ${mysqlHints.joinToString(" ")} */"
            } else {
                "/* Oracle hints removed: $hintContent */"
            }
        }

        // 규칙 및 경고 추가
        if (convertedHints.isNotEmpty()) {
            appliedRules.add("Oracle 힌트 → MySQL 힌트 변환: ${convertedHints.joinToString(", ")}")
        }

        if (removedHints.isNotEmpty()) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "일부 Oracle 힌트가 MySQL에서 지원되지 않아 제거되었습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "제거된 힌트: ${removedHints.joinToString(", ")}"
            ))
            appliedRules.add("Oracle 힌트 제거: ${removedHints.size}개")
        }

        return result
    }

    /**
     * Oracle 힌트 → PostgreSQL 변환
     */
    private fun convertToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql
        val pgSettings = mutableListOf<String>()
        val removedHints = mutableListOf<String>()

        result = HINT_PATTERN.replace(result) { match ->
            val hintContent = match.groupValues[1].trim()

            // PARALLEL 힌트 → pg_hint_plan 또는 SET max_parallel_workers_per_gather
            PARALLEL_HINT_PATTERN.find(hintContent)?.let { parallelMatch ->
                val degree = parallelMatch.groupValues[2].ifEmpty { "4" }
                pgSettings.add("SET max_parallel_workers_per_gather = $degree")
            }

            // FIRST_ROWS → cursor_tuple_fraction
            FIRST_ROWS_PATTERN.find(hintContent)?.let { firstRowsMatch ->
                val n = firstRowsMatch.groupValues[1].ifEmpty { "10" }
                pgSettings.add("SET cursor_tuple_fraction = ${1.0 / n.toInt()}")
            }

            // ALL_ROWS → cursor_tuple_fraction = 1.0
            if (ALL_ROWS_PATTERN.containsMatchIn(hintContent)) {
                pgSettings.add("SET cursor_tuple_fraction = 1.0")
            }

            // INDEX 힌트 - pg_hint_plan 사용 권장
            if (INDEX_HINT_PATTERN.containsMatchIn(hintContent)) {
                removedHints.add("INDEX - pg_hint_plan 확장 사용 권장")
            }

            // 조인 힌트 - pg_hint_plan 사용 권장
            if (USE_NL_PATTERN.containsMatchIn(hintContent)) {
                removedHints.add("USE_NL - NestLoop(table ...) pg_hint_plan 사용")
            }
            if (USE_HASH_PATTERN.containsMatchIn(hintContent)) {
                removedHints.add("USE_HASH - HashJoin(table ...) pg_hint_plan 사용")
            }

            if (LEADING_HINT_PATTERN.containsMatchIn(hintContent)) {
                removedHints.add("LEADING - Leading(tables ...) pg_hint_plan 사용")
            }

            // APPEND 힌트 - COPY 명령 권장
            if (APPEND_PATTERN.containsMatchIn(hintContent)) {
                removedHints.add("APPEND - COPY 명령 또는 UNLOGGED 테이블 사용 권장")
            }

            // 힌트 주석으로 변환
            "/* Oracle hint: $hintContent - see pg_hint_plan extension */"
        }

        // SET 문 추가
        if (pgSettings.isNotEmpty()) {
            val setStatements = pgSettings.joinToString(";\n") + ";\n"
            result = setStatements + result
            appliedRules.add("Oracle 힌트 → PostgreSQL SET 명령 변환: ${pgSettings.size}개")
        }

        if (removedHints.isNotEmpty()) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "Oracle 힌트가 PostgreSQL에서 직접 지원되지 않습니다.",
                severity = WarningSeverity.INFO,
                suggestion = "pg_hint_plan 확장을 설치하면 유사한 힌트를 사용할 수 있습니다. " +
                        "제거된 힌트: ${removedHints.joinToString(", ")}"
            ))
        }

        return result
    }

    /**
     * 힌트 제거 (옵션)
     */
    fun removeAllHints(sql: String): String {
        return HINT_PATTERN.replace(sql, "")
    }

    /**
     * 힌트 추출
     */
    fun extractHints(sql: String): List<String> {
        return HINT_PATTERN.findAll(sql).map { it.groupValues[1].trim() }.toList()
    }

    /**
     * 힌트 파싱
     */
    fun parseHints(hintContent: String): List<HintInfo> {
        val hints = mutableListOf<HintInfo>()

        // INDEX 힌트
        INDEX_HINT_PATTERN.findAll(hintContent).forEach { match ->
            hints.add(HintInfo(
                type = HintType.INDEX,
                table = match.groupValues[1],
                index = match.groupValues[2].ifEmpty { null }
            ))
        }

        // PARALLEL 힌트
        PARALLEL_HINT_PATTERN.find(hintContent)?.let { match ->
            hints.add(HintInfo(
                type = HintType.PARALLEL,
                table = match.groupValues[1].ifEmpty { null },
                degree = match.groupValues[2].toIntOrNull()
            ))
        }

        // FULL 힌트
        FULL_HINT_PATTERN.findAll(hintContent).forEach { match ->
            hints.add(HintInfo(
                type = HintType.FULL,
                table = match.groupValues[1]
            ))
        }

        // LEADING 힌트
        LEADING_HINT_PATTERN.find(hintContent)?.let { match ->
            val tables = match.groupValues[1].split(Regex("\\s+")).map { it.trim() }
            hints.add(HintInfo(
                type = HintType.LEADING,
                tables = tables
            ))
        }

        return hints
    }

    /**
     * 힌트 정보
     */
    data class HintInfo(
        val type: HintType,
        val table: String? = null,
        val index: String? = null,
        val degree: Int? = null,
        val tables: List<String> = emptyList()
    )

    /**
     * 힌트 타입
     */
    enum class HintType {
        INDEX, NO_INDEX, FULL, PARALLEL, LEADING,
        USE_NL, USE_HASH, FIRST_ROWS, ALL_ROWS, APPEND
    }
}
