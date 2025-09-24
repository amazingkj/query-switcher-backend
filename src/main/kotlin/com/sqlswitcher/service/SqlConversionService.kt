package com.sqlswitcher.service

import com.sqlswitcher.model.ConversionRequest
import com.sqlswitcher.model.ConversionResponse
import com.sqlswitcher.parser.SqlParserService
import com.sqlswitcher.converter.SqlConverterEngine
import org.springframework.stereotype.Service

@Service
class SqlConversionService(
    private val sqlParserService: SqlParserService,
    private val sqlConverterEngine: SqlConverterEngine
) {

    fun convertSql(request: ConversionRequest): ConversionResponse {
        // TODO: Implement SQL conversion logic
        return ConversionResponse(
            originalSql = request.sql,
            convertedSql = "SELECT * FROM converted_table;",
            sourceDialect = request.sourceDialect,
            targetDialect = request.targetDialect,
            warnings = emptyList(),
            conversionTime = 0L
        )
    }
}
