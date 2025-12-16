package com.sqlswitcher.converter.feature.function

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.registry.DynamicReplacementRule
import com.sqlswitcher.converter.registry.DynamicReplacementRegistry

/**
 * 페이징 구문 변환
 * OFFSET FETCH ↔ LIMIT OFFSET
 */
object PaginationConverter {

    // 페이징 변환 레지스트리
    private val paginationRegistry = DynamicReplacementRegistry().apply {
        // Oracle → MySQL/PostgreSQL: OFFSET FETCH → LIMIT OFFSET
        registerAll(DialectType.ORACLE, DialectType.MYSQL, listOf(
            DynamicReplacementRule(
                """OFFSET\s+(\d+)\s+ROWS?\s+FETCH\s+(?:FIRST|NEXT)\s+(\d+)\s+ROWS?\s+ONLY""",
                { match -> "LIMIT ${match.groupValues[2]} OFFSET ${match.groupValues[1]}" },
                "OFFSET ... FETCH → LIMIT ... OFFSET 변환"
            )
        ))

        registerAll(DialectType.ORACLE, DialectType.POSTGRESQL, listOf(
            DynamicReplacementRule(
                """OFFSET\s+(\d+)\s+ROWS?\s+FETCH\s+(?:FIRST|NEXT)\s+(\d+)\s+ROWS?\s+ONLY""",
                { match -> "LIMIT ${match.groupValues[2]} OFFSET ${match.groupValues[1]}" },
                "OFFSET ... FETCH → LIMIT ... OFFSET 변환"
            )
        ))

        // MySQL → Oracle: LIMIT OFFSET → OFFSET FETCH
        registerAll(DialectType.MYSQL, DialectType.ORACLE, listOf(
            DynamicReplacementRule(
                """LIMIT\s+(\d+)\s+OFFSET\s+(\d+)""",
                { match -> "OFFSET ${match.groupValues[2]} ROWS FETCH NEXT ${match.groupValues[1]} ROWS ONLY" },
                "LIMIT ... OFFSET → OFFSET ... FETCH 변환"
            ),
            DynamicReplacementRule(
                """LIMIT\s+(\d+)(?!\s+OFFSET)""",
                { match -> "FETCH FIRST ${match.groupValues[1]} ROWS ONLY" },
                "LIMIT → FETCH FIRST 변환"
            )
        ))

        // PostgreSQL → Oracle: LIMIT OFFSET → OFFSET FETCH
        registerAll(DialectType.POSTGRESQL, DialectType.ORACLE, listOf(
            DynamicReplacementRule(
                """LIMIT\s+(\d+)\s+OFFSET\s+(\d+)""",
                { match -> "OFFSET ${match.groupValues[2]} ROWS FETCH NEXT ${match.groupValues[1]} ROWS ONLY" },
                "LIMIT ... OFFSET → OFFSET ... FETCH 변환"
            ),
            DynamicReplacementRule(
                """LIMIT\s+(\d+)(?!\s+OFFSET)""",
                { match -> "FETCH FIRST ${match.groupValues[1]} ROWS ONLY" },
                "LIMIT → FETCH FIRST 변환"
            )
        ))
    }

    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        return paginationRegistry.apply(sql, sourceDialect, targetDialect, appliedRules)
    }
}