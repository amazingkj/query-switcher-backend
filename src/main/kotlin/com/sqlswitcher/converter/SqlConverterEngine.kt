package com.sqlswitcher.converter

import com.sqlswitcher.model.DatabaseDialect
import org.springframework.stereotype.Service

@Service
class SqlConverterEngine {

    fun convertSql(
        sql: String,
        sourceDialect: DatabaseDialect,
        targetDialect: DatabaseDialect
    ): ConversionResult {
        // TODO: Implement SQL conversion logic
        return ConversionResult(
            convertedSql = sql,
            warnings = emptyList(),
            conversionTime = 0L
        )
    }
}

data class ConversionResult(
    val convertedSql: String,
    val warnings: List<String>,
    val conversionTime: Long
)
