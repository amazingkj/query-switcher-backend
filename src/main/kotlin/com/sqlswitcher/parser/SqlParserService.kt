package com.sqlswitcher.parser

import org.springframework.stereotype.Service

@Service
class SqlParserService {

    fun parseSql(sql: String): ParseResult {
        // TODO: Implement JSQLParser integration
        return ParseResult(
            isValid = true,
            statement = null,
            errors = emptyList(),
            parseTime = 0L
        )
    }
}

data class ParseResult(
    val isValid: Boolean,
    val statement: Any?, // Will be JSQLParser Statement type
    val errors: List<String>,
    val parseTime: Long
)
