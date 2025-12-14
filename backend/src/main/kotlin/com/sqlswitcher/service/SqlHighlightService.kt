package com.sqlswitcher.service

import org.springframework.stereotype.Service

/**
 * SQL 구문 하이라이팅 서비스
 * SQL 문을 토큰화하고 각 토큰에 하이라이팅 정보를 부여합니다.
 */
@Service
class SqlHighlightService {

    companion object {
        // SQL 키워드 목록
        private val KEYWORDS = setOf(
            // DML
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS", "LIKE", "BETWEEN",
            "IS", "NULL", "ORDER", "BY", "GROUP", "HAVING", "DISTINCT", "ALL", "AS",
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "ON", "USING",
            "UNION", "INTERSECT", "EXCEPT", "MINUS",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "MERGE",
            "LIMIT", "OFFSET", "FETCH", "FIRST", "NEXT", "ROWS", "ONLY", "PERCENT",
            "TOP", "WITH", "TIES", "RECURSIVE",

            // DDL
            "CREATE", "ALTER", "DROP", "TRUNCATE", "TABLE", "INDEX", "VIEW", "SEQUENCE",
            "TRIGGER", "PROCEDURE", "FUNCTION", "PACKAGE", "SCHEMA", "DATABASE",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "CHECK", "DEFAULT",
            "CONSTRAINT", "CASCADE", "RESTRICT", "NO", "ACTION",
            "ADD", "MODIFY", "COLUMN", "RENAME", "TO",

            // 데이터 타입
            "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "DECIMAL", "NUMERIC",
            "FLOAT", "DOUBLE", "REAL", "NUMBER", "VARCHAR", "VARCHAR2", "CHAR", "NVARCHAR",
            "TEXT", "CLOB", "BLOB", "DATE", "TIME", "TIMESTAMP", "DATETIME", "BOOLEAN",
            "SERIAL", "BIGSERIAL", "BYTEA", "JSON", "JSONB", "UUID",

            // 기타
            "CASE", "WHEN", "THEN", "ELSE", "END", "IF", "ELSEIF", "WHILE", "LOOP",
            "BEGIN", "DECLARE", "EXCEPTION", "RAISE", "RETURN", "RETURNS",
            "ASC", "DESC", "NULLS", "LAST",
            "OVER", "PARTITION", "WINDOW", "RANGE", "UNBOUNDED", "PRECEDING", "FOLLOWING", "CURRENT", "ROW",
            "GRANT", "REVOKE", "COMMIT", "ROLLBACK", "SAVEPOINT",
            "CONNECT", "START", "LEVEL", "PRIOR", "SIBLINGS",
            "PIVOT", "UNPIVOT", "FOR",
            "TABLESPACE", "STORAGE", "LOGGING", "NOLOGGING", "PARALLEL", "NOPARALLEL",
            "ENABLE", "DISABLE", "VALIDATE", "NOVALIDATE"
        )

        // SQL 함수 목록
        private val FUNCTIONS = setOf(
            // 집계 함수
            "COUNT", "SUM", "AVG", "MIN", "MAX", "STDDEV", "VARIANCE",
            "LISTAGG", "STRING_AGG", "GROUP_CONCAT", "ARRAY_AGG",

            // 윈도우 함수
            "ROW_NUMBER", "RANK", "DENSE_RANK", "NTILE", "LAG", "LEAD",
            "FIRST_VALUE", "LAST_VALUE", "NTH_VALUE",

            // 문자열 함수
            "CONCAT", "SUBSTRING", "SUBSTR", "TRIM", "LTRIM", "RTRIM",
            "UPPER", "LOWER", "INITCAP", "LENGTH", "CHAR_LENGTH",
            "REPLACE", "TRANSLATE", "INSTR", "POSITION", "LOCATE",
            "LPAD", "RPAD", "REVERSE", "SPLIT_PART",

            // 숫자 함수
            "ABS", "CEIL", "CEILING", "FLOOR", "ROUND", "TRUNC", "TRUNCATE",
            "MOD", "POWER", "SQRT", "SIGN", "GREATEST", "LEAST",

            // 날짜 함수
            "NOW", "SYSDATE", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP",
            "SYSTIMESTAMP", "GETDATE", "DATE_ADD", "DATE_SUB", "DATEADD", "DATEDIFF",
            "ADD_MONTHS", "MONTHS_BETWEEN", "EXTRACT", "DATE_TRUNC", "DATE_PART",
            "TO_DATE", "TO_CHAR", "TO_NUMBER", "TO_TIMESTAMP",
            "DATE_FORMAT", "STR_TO_DATE", "YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND",

            // NULL 처리 함수
            "COALESCE", "NVL", "NVL2", "NULLIF", "IFNULL", "ISNULL",

            // 조건 함수
            "DECODE", "IIF", "CHOOSE",

            // 변환 함수
            "CAST", "CONVERT", "TRY_CAST", "TRY_CONVERT",

            // 기타 함수
            "EXISTS", "RAND", "RANDOM", "UUID", "NEWID"
        )

        // 연산자
        private val OPERATORS = setOf(
            "=", "<>", "!=", "<", ">", "<=", ">=",
            "+", "-", "*", "/", "%", "||",
            "::", "->", "->>", "@>", "<@", "?", "?&", "?|"
        )

        // 정규식 패턴들
        private val STRING_PATTERN = Regex("""'(?:[^'\\]|\\.)*'""")
        private val NUMBER_PATTERN = Regex("""\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b""")
        private val IDENTIFIER_PATTERN = Regex(""""[^"]+"|`[^`]+`|\[[^\]]+\]""")
        private val COMMENT_SINGLE_PATTERN = Regex("""--[^\n]*""")
        private val COMMENT_MULTI_PATTERN = Regex("""/\*[\s\S]*?\*/""")
        private val WORD_PATTERN = Regex("""\b[A-Za-z_][A-Za-z0-9_]*\b""")
    }

    /**
     * SQL 문을 토큰화하고 하이라이팅 정보를 반환합니다.
     */
    fun highlightSql(sql: String): SqlHighlightResult {
        val tokens = mutableListOf<HighlightToken>()
        var position = 0

        while (position < sql.length) {
            val remaining = sql.substring(position)
            var matched = false

            // 공백 처리
            if (remaining[0].isWhitespace()) {
                val whitespace = remaining.takeWhile { it.isWhitespace() }
                tokens.add(HighlightToken(whitespace, TokenType.WHITESPACE, position))
                position += whitespace.length
                continue
            }

            // 주석 처리 (여러 줄)
            if (!matched) {
                val match = COMMENT_MULTI_PATTERN.find(remaining)
                if (match != null && match.range.first == 0) {
                    tokens.add(HighlightToken(match.value, TokenType.COMMENT, position))
                    position += match.value.length
                    matched = true
                }
            }

            // 주석 처리 (단일 줄)
            if (!matched) {
                val match = COMMENT_SINGLE_PATTERN.find(remaining)
                if (match != null && match.range.first == 0) {
                    tokens.add(HighlightToken(match.value, TokenType.COMMENT, position))
                    position += match.value.length
                    matched = true
                }
            }

            // 문자열 처리
            if (!matched) {
                val match = STRING_PATTERN.find(remaining)
                if (match != null && match.range.first == 0) {
                    tokens.add(HighlightToken(match.value, TokenType.STRING, position))
                    position += match.value.length
                    matched = true
                }
            }

            // 식별자 처리 (따옴표로 감싼)
            if (!matched) {
                val match = IDENTIFIER_PATTERN.find(remaining)
                if (match != null && match.range.first == 0) {
                    tokens.add(HighlightToken(match.value, TokenType.IDENTIFIER, position))
                    position += match.value.length
                    matched = true
                }
            }

            // 숫자 처리
            if (!matched) {
                val match = NUMBER_PATTERN.find(remaining)
                if (match != null && match.range.first == 0) {
                    tokens.add(HighlightToken(match.value, TokenType.NUMBER, position))
                    position += match.value.length
                    matched = true
                }
            }

            // 단어 처리 (키워드, 함수, 식별자)
            if (!matched) {
                val match = WORD_PATTERN.find(remaining)
                if (match != null && match.range.first == 0) {
                    val word = match.value
                    val upperWord = word.uppercase()
                    val type = when {
                        KEYWORDS.contains(upperWord) -> TokenType.KEYWORD
                        FUNCTIONS.contains(upperWord) -> TokenType.FUNCTION
                        else -> TokenType.IDENTIFIER
                    }
                    tokens.add(HighlightToken(word, type, position))
                    position += word.length
                    matched = true
                }
            }

            // 연산자 처리
            if (!matched) {
                val operatorMatch = OPERATORS.find { remaining.startsWith(it) }
                if (operatorMatch != null) {
                    tokens.add(HighlightToken(operatorMatch, TokenType.OPERATOR, position))
                    position += operatorMatch.length
                    matched = true
                }
            }

            // 괄호 및 구두점 처리
            if (!matched) {
                val char = remaining[0]
                val type = when (char) {
                    '(', ')' -> TokenType.PARENTHESIS
                    ',', ';' -> TokenType.PUNCTUATION
                    else -> TokenType.OTHER
                }
                tokens.add(HighlightToken(char.toString(), type, position))
                position++
            }
        }

        return SqlHighlightResult(
            originalSql = sql,
            tokens = tokens,
            html = generateHtml(tokens),
            ansi = generateAnsi(tokens)
        )
    }

    /**
     * 토큰을 HTML로 변환합니다.
     */
    private fun generateHtml(tokens: List<HighlightToken>): String {
        return tokens.joinToString("") { token ->
            val cssClass = getCssClass(token.type)
            val escapedValue = escapeHtml(token.value)
            if (cssClass.isNotEmpty()) {
                """<span class="$cssClass">$escapedValue</span>"""
            } else {
                escapedValue
            }
        }
    }

    /**
     * 토큰을 ANSI 컬러 코드로 변환합니다. (터미널용)
     */
    private fun generateAnsi(tokens: List<HighlightToken>): String {
        return tokens.joinToString("") { token ->
            val ansiCode = getAnsiCode(token.type)
            if (ansiCode.isNotEmpty()) {
                "$ansiCode${token.value}\u001B[0m"
            } else {
                token.value
            }
        }
    }

    private fun getCssClass(type: TokenType): String {
        return when (type) {
            TokenType.KEYWORD -> "sql-keyword"
            TokenType.FUNCTION -> "sql-function"
            TokenType.STRING -> "sql-string"
            TokenType.NUMBER -> "sql-number"
            TokenType.COMMENT -> "sql-comment"
            TokenType.OPERATOR -> "sql-operator"
            TokenType.IDENTIFIER -> "sql-identifier"
            TokenType.PARENTHESIS -> "sql-parenthesis"
            TokenType.PUNCTUATION -> "sql-punctuation"
            else -> ""
        }
    }

    private fun getAnsiCode(type: TokenType): String {
        return when (type) {
            TokenType.KEYWORD -> "\u001B[34m"      // 파란색
            TokenType.FUNCTION -> "\u001B[33m"     // 노란색
            TokenType.STRING -> "\u001B[32m"       // 초록색
            TokenType.NUMBER -> "\u001B[35m"       // 자주색
            TokenType.COMMENT -> "\u001B[90m"      // 회색
            TokenType.OPERATOR -> "\u001B[36m"     // 청록색
            else -> ""
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("\n", "<br>")
            .replace(" ", "&nbsp;")
    }
}

/**
 * 토큰 타입
 */
enum class TokenType {
    KEYWORD,        // SQL 키워드 (SELECT, FROM, WHERE 등)
    FUNCTION,       // SQL 함수 (COUNT, SUM, NVL 등)
    STRING,         // 문자열 리터럴 ('hello')
    NUMBER,         // 숫자 리터럴 (123, 45.67)
    IDENTIFIER,     // 테이블명, 컬럼명 등
    OPERATOR,       // 연산자 (=, <>, + 등)
    COMMENT,        // 주석 (-- 또는 /* */)
    PARENTHESIS,    // 괄호 ( )
    PUNCTUATION,    // 구두점 (, ;)
    WHITESPACE,     // 공백
    OTHER           // 기타
}

/**
 * 하이라이팅 토큰
 */
data class HighlightToken(
    val value: String,
    val type: TokenType,
    val position: Int
)

/**
 * 하이라이팅 결과
 */
data class SqlHighlightResult(
    val originalSql: String,
    val tokens: List<HighlightToken>,
    val html: String,
    val ansi: String
)