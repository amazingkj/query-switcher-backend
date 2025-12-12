package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.DialectType

/**
 * 페이징 구문 변환
 * OFFSET FETCH ↔ LIMIT OFFSET
 */
object PaginationConverter {

    private val OFFSET_FETCH_PATTERN = Regex(
        """OFFSET\s+(\d+)\s+ROWS?\s+FETCH\s+(?:FIRST|NEXT)\s+(\d+)\s+ROWS?\s+ONLY""",
        RegexOption.IGNORE_CASE
    )
    private val LIMIT_OFFSET_PATTERN = Regex(
        """LIMIT\s+(\d+)\s+OFFSET\s+(\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val LIMIT_ONLY_PATTERN = Regex(
        """LIMIT\s+(\d+)(?!\s+OFFSET)""",
        RegexOption.IGNORE_CASE
    )

    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // Oracle 12c OFFSET FETCH → MySQL/PostgreSQL LIMIT OFFSET
        if (sourceDialect == DialectType.ORACLE && OFFSET_FETCH_PATTERN.containsMatchIn(result)) {
            result = OFFSET_FETCH_PATTERN.replace(result) { match ->
                val offset = match.groupValues[1]
                val limit = match.groupValues[2]
                when (targetDialect) {
                    DialectType.MYSQL, DialectType.POSTGRESQL -> {
                        appliedRules.add("OFFSET ... FETCH → LIMIT ... OFFSET 변환")
                        "LIMIT $limit OFFSET $offset"
                    }
                    else -> match.value
                }
            }
        }

        // MySQL/PostgreSQL LIMIT OFFSET → Oracle OFFSET FETCH
        if ((sourceDialect == DialectType.MYSQL || sourceDialect == DialectType.POSTGRESQL)
            && targetDialect == DialectType.ORACLE) {

            if (LIMIT_OFFSET_PATTERN.containsMatchIn(result)) {
                result = LIMIT_OFFSET_PATTERN.replace(result) { match ->
                    val limit = match.groupValues[1]
                    val offset = match.groupValues[2]
                    appliedRules.add("LIMIT ... OFFSET → OFFSET ... FETCH 변환")
                    "OFFSET $offset ROWS FETCH NEXT $limit ROWS ONLY"
                }
            } else if (LIMIT_ONLY_PATTERN.containsMatchIn(result)) {
                result = LIMIT_ONLY_PATTERN.replace(result) { match ->
                    val limit = match.groupValues[1]
                    appliedRules.add("LIMIT → FETCH FIRST 변환")
                    "FETCH FIRST $limit ROWS ONLY"
                }
            }
        }

        return result
    }
}