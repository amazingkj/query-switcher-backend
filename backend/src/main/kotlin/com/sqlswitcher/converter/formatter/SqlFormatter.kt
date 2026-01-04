package com.sqlswitcher.converter.formatter

/**
 * SQL 포매터
 *
 * SQL 문을 보기 좋게 정렬하고 포맷팅
 *
 * 기능:
 * - 키워드 대소문자 통일 (UPPER/lower/Capitalize)
 * - 들여쓰기 적용
 * - 줄바꿈 추가
 * - 쉼표/연산자 주변 공백 정리
 * - 괄호 정렬
 * - SELECT 절 컬럼 정렬
 */
object SqlFormatter {

    /** 주요 SQL 키워드 */
    private val MAIN_KEYWORDS = setOf(
        "SELECT", "FROM", "WHERE", "AND", "OR", "ORDER BY", "GROUP BY",
        "HAVING", "JOIN", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "OUTER JOIN",
        "FULL JOIN", "CROSS JOIN", "ON", "USING", "UNION", "UNION ALL",
        "INTERSECT", "EXCEPT", "MINUS", "INSERT", "INTO", "VALUES",
        "UPDATE", "SET", "DELETE", "CREATE", "ALTER", "DROP", "TABLE",
        "INDEX", "VIEW", "TRIGGER", "PROCEDURE", "FUNCTION", "PACKAGE",
        "BEGIN", "END", "IF", "THEN", "ELSE", "ELSIF", "CASE", "WHEN",
        "LOOP", "WHILE", "FOR", "EXIT", "RETURN", "DECLARE", "EXCEPTION",
        "COMMIT", "ROLLBACK", "PARTITION", "BY", "AS", "IS", "IN", "NOT",
        "NULL", "EXISTS", "BETWEEN", "LIKE", "LIMIT", "OFFSET", "FETCH",
        "WITH", "RECURSIVE", "MERGE", "MATCHED", "CONFLICT"
    )

    /** 줄바꿈이 필요한 키워드 */
    private val NEWLINE_BEFORE_KEYWORDS = setOf(
        "SELECT", "FROM", "WHERE", "AND", "OR", "ORDER BY", "GROUP BY",
        "HAVING", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "OUTER JOIN",
        "FULL JOIN", "CROSS JOIN", "JOIN", "UNION", "UNION ALL",
        "INSERT", "UPDATE", "DELETE", "SET", "VALUES",
        "BEGIN", "END", "IF", "THEN", "ELSE", "ELSIF", "WHEN",
        "EXCEPTION", "LOOP", "DECLARE", "RETURN"
    )

    /** 들여쓰기가 증가하는 키워드 */
    private val INDENT_INCREASE_KEYWORDS = setOf(
        "BEGIN", "LOOP", "IF", "CASE", "DECLARE", "(", "SELECT"
    )

    /** 들여쓰기가 감소하는 키워드 */
    private val INDENT_DECREASE_KEYWORDS = setOf(
        "END", ")", "ELSE", "ELSIF", "WHEN", "EXCEPTION"
    )

    /**
     * 포맷팅 옵션
     */
    data class FormatOptions(
        /** 키워드 대소문자: UPPER, lower, Capitalize */
        val keywordCase: KeywordCase = KeywordCase.UPPER,
        /** 들여쓰기 문자 (공백 또는 탭) */
        val indentString: String = "    ",
        /** SELECT 절 컬럼 각 줄에 배치 */
        val columnsOnNewLine: Boolean = true,
        /** 쉼표 위치: BEFORE (앞), AFTER (뒤) */
        val commaPosition: CommaPosition = CommaPosition.AFTER,
        /** 최대 줄 길이 (0이면 무제한) */
        val maxLineLength: Int = 120,
        /** 연산자 주변 공백 추가 */
        val spaceAroundOperators: Boolean = true,
        /** 괄호 안쪽 공백 제거 */
        val trimParentheses: Boolean = true,
        /** 빈 줄 제거 */
        val removeEmptyLines: Boolean = true,
        /** 문장 끝 세미콜론 보장 */
        val ensureSemicolon: Boolean = true
    )

    enum class KeywordCase {
        UPPER, LOWER, CAPITALIZE
    }

    enum class CommaPosition {
        BEFORE, AFTER
    }

    /**
     * SQL 포맷팅 (기본 옵션)
     */
    fun format(sql: String): String {
        return format(sql, FormatOptions())
    }

    /**
     * SQL 포맷팅 (옵션 지정)
     */
    fun format(sql: String, options: FormatOptions): String {
        if (sql.isBlank()) return sql

        var result = sql.trim()

        // 1. 공백/줄바꿈 정규화
        result = normalizeWhitespace(result)

        // 2. 키워드 대소문자 처리
        result = applyKeywordCase(result, options.keywordCase)

        // 3. 연산자 주변 공백
        if (options.spaceAroundOperators) {
            result = normalizeOperators(result)
        }

        // 4. 괄호 주변 공백 정리
        if (options.trimParentheses) {
            result = normalizeParentheses(result)
        }

        // 5. 주요 키워드 앞 줄바꿈
        result = addNewlines(result, options)

        // 6. 들여쓰기 적용
        result = applyIndentation(result, options)

        // 7. SELECT 컬럼 정렬
        if (options.columnsOnNewLine) {
            result = formatSelectColumns(result, options)
        }

        // 8. 빈 줄 정리
        if (options.removeEmptyLines) {
            result = removeExcessiveEmptyLines(result)
        }

        // 9. 세미콜론 보장
        if (options.ensureSemicolon && !result.trimEnd().endsWith(";")) {
            result = "$result;"
        }

        return result
    }

    /**
     * 공백/줄바꿈 정규화
     */
    private fun normalizeWhitespace(sql: String): String {
        var result = sql

        // 연속 공백을 하나로
        result = result.replace(Regex("""[ \t]+"""), " ")

        // 연속 줄바꿈을 하나로
        result = result.replace(Regex("""\n\s*\n\s*\n+"""), "\n\n")

        return result.trim()
    }

    /**
     * 키워드 대소문자 적용
     */
    private fun applyKeywordCase(sql: String, keywordCase: KeywordCase): String {
        var result = sql

        MAIN_KEYWORDS.forEach { keyword ->
            val regex = Regex("""\b${Regex.escape(keyword)}\b""", RegexOption.IGNORE_CASE)
            result = regex.replace(result) { match ->
                when (keywordCase) {
                    KeywordCase.UPPER -> match.value.uppercase()
                    KeywordCase.LOWER -> match.value.lowercase()
                    KeywordCase.CAPITALIZE -> match.value.lowercase().replaceFirstChar { it.uppercase() }
                }
            }
        }

        return result
    }

    /**
     * 연산자 주변 공백 정규화
     */
    private fun normalizeOperators(sql: String): String {
        var result = sql

        // 비교 연산자
        result = result.replace(Regex("""\s*(=|<>|!=|>=|<=|>|<)\s*""")) { " ${it.groupValues[1]} " }

        // 산술 연산자 (문자열 내부 제외)
        result = result.replace(Regex("""\s*(\+|-|\*|/)\s*(?=[^']*(?:'[^']*'[^']*)*$)""")) { " ${it.groupValues[1]} " }

        // 문자열 연결 연산자
        result = result.replace(Regex("""\s*\|\|\s*"""), " || ")

        // 이중 공백 제거
        result = result.replace(Regex("""  +"""), " ")

        return result
    }

    /**
     * 괄호 주변 공백 정리
     */
    private fun normalizeParentheses(sql: String): String {
        var result = sql

        // 여는 괄호 뒤 공백 제거
        result = result.replace(Regex("""\(\s+"""), "(")

        // 닫는 괄호 앞 공백 제거
        result = result.replace(Regex("""\s+\)"""), ")")

        return result
    }

    /**
     * 주요 키워드 앞에 줄바꿈 추가
     */
    private fun addNewlines(sql: String, options: FormatOptions): String {
        var result = sql

        // 먼저 복합 키워드 처리 (INNER JOIN, LEFT JOIN 등)
        val compoundKeywords = listOf(
            "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN",
            "OUTER JOIN", "CROSS JOIN", "LEFT OUTER JOIN", "RIGHT OUTER JOIN",
            "ORDER BY", "GROUP BY", "UNION ALL", "INSERT INTO"
        )

        compoundKeywords.forEach { keyword ->
            val regex = Regex("""(?<!\n)\s+\b(${Regex.escape(keyword)})\b""", RegexOption.IGNORE_CASE)
            result = regex.replace(result) { match ->
                "\n${match.groupValues[1].uppercase()}"
            }
        }

        // 그 다음 단일 키워드 처리 (복합 키워드의 일부가 아닌 경우)
        val singleKeywords = NEWLINE_BEFORE_KEYWORDS.filter { keyword ->
            compoundKeywords.none { it.contains(keyword) }
        }

        singleKeywords.forEach { keyword ->
            val regex = Regex("""(?<!\n)\s+\b(${Regex.escape(keyword)})\b""", RegexOption.IGNORE_CASE)
            result = regex.replace(result) { match ->
                "\n${match.groupValues[1]}"
            }
        }

        return result
    }

    /**
     * 들여쓰기 적용
     */
    private fun applyIndentation(sql: String, options: FormatOptions): String {
        val lines = sql.split("\n")
        val result = StringBuilder()
        var indentLevel = 0

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                result.appendLine()
                continue
            }

            // 현재 줄이 들여쓰기 감소 키워드로 시작하면 먼저 감소
            val firstWord = trimmedLine.split(Regex("\\s+"))[0].uppercase()
            if (INDENT_DECREASE_KEYWORDS.any { firstWord.startsWith(it) }) {
                indentLevel = maxOf(0, indentLevel - 1)
            }

            // 들여쓰기 적용
            val indent = options.indentString.repeat(indentLevel)
            result.appendLine("$indent$trimmedLine")

            // 들여쓰기 증가 키워드 확인
            if (INDENT_INCREASE_KEYWORDS.any { trimmedLine.uppercase().contains(it) }) {
                // END나 닫는 괄호로 끝나면 증가하지 않음
                val lastWord = trimmedLine.split(Regex("\\s+")).lastOrNull()?.uppercase() ?: ""
                if (!lastWord.contains("END") && !trimmedLine.endsWith(")") && !trimmedLine.endsWith(");")) {
                    indentLevel++
                }
            }
        }

        return result.toString().trimEnd()
    }

    /**
     * SELECT 절 컬럼 정렬
     */
    private fun formatSelectColumns(sql: String, options: FormatOptions): String {
        val selectPattern = Regex(
            """(SELECT\s+(?:DISTINCT\s+)?)([\s\S]+?)(?=\bFROM\b)""",
            RegexOption.IGNORE_CASE
        )

        return selectPattern.replace(sql) { match ->
            val selectKeyword = match.groupValues[1].trim()
            val columns = match.groupValues[2]

            // 컬럼들을 파싱
            val columnList = parseColumns(columns)

            if (columnList.size <= 1) {
                match.value
            } else {
                val formattedColumns = when (options.commaPosition) {
                    CommaPosition.AFTER -> columnList.mapIndexed { index, col ->
                        val suffix = if (index < columnList.size - 1) "," else ""
                        "${options.indentString}${col.trim()}$suffix"
                    }
                    CommaPosition.BEFORE -> columnList.mapIndexed { index, col ->
                        val prefix = if (index > 0) ", " else "  "
                        "$prefix${col.trim()}"
                    }
                }

                "$selectKeyword\n${formattedColumns.joinToString("\n")}\n"
            }
        }
    }

    /**
     * 컬럼 파싱 (괄호 안의 쉼표는 무시)
     */
    private fun parseColumns(columnsStr: String): List<String> {
        val columns = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0

        for (char in columnsStr) {
            when {
                char == '(' -> {
                    parenDepth++
                    current.append(char)
                }
                char == ')' -> {
                    parenDepth--
                    current.append(char)
                }
                char == ',' && parenDepth == 0 -> {
                    columns.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }

        if (current.isNotBlank()) {
            columns.add(current.toString().trim())
        }

        return columns
    }

    /**
     * 과도한 빈 줄 제거
     */
    private fun removeExcessiveEmptyLines(sql: String): String {
        return sql.replace(Regex("""\n{3,}"""), "\n\n")
    }

    /**
     * 간단한 한 줄 포맷 (공백만 정리)
     */
    fun formatOneLine(sql: String): String {
        return sql
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * 최소 포맷 (공백만 정리, 대소문자 변환 없음)
     */
    fun formatMinimal(sql: String): String {
        return format(sql, FormatOptions(
            keywordCase = KeywordCase.UPPER,
            columnsOnNewLine = false,
            spaceAroundOperators = true,
            trimParentheses = true,
            removeEmptyLines = true
        ))
    }

    /**
     * 압축 포맷 (불필요한 공백 모두 제거)
     */
    fun formatCompact(sql: String): String {
        var result = sql

        // 모든 줄바꿈을 공백으로
        result = result.replace(Regex("""\s+"""), " ")

        // 괄호 주변 공백 제거
        result = result.replace(Regex("""\(\s+"""), "(")
        result = result.replace(Regex("""\s+\)"""), ")")

        // 쉼표 뒤 공백만 유지
        result = result.replace(Regex("""\s*,\s*"""), ", ")

        return result.trim()
    }

    /**
     * DDL 전용 포맷
     */
    fun formatDdl(sql: String): String {
        return format(sql, FormatOptions(
            keywordCase = KeywordCase.UPPER,
            indentString = "    ",
            columnsOnNewLine = true,
            commaPosition = CommaPosition.AFTER,
            ensureSemicolon = true
        ))
    }

    /**
     * PL/SQL 전용 포맷
     */
    fun formatPlSql(sql: String): String {
        return format(sql, FormatOptions(
            keywordCase = KeywordCase.UPPER,
            indentString = "    ",
            columnsOnNewLine = false,
            ensureSemicolon = true
        ))
    }

    /**
     * 여러 SQL 문 포맷팅
     */
    fun formatMultiple(sql: String, separator: String = ";"): String {
        val statements = sql.split(Regex(""";\s*"""))
            .filter { it.isNotBlank() }

        return statements.joinToString(";\n\n") { format(it.trim()) } + ";"
    }
}
