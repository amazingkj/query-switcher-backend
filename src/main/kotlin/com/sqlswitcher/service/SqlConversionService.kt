package com.sqlswitcher.service

import com.sqlswitcher.model.ConversionRequest
import com.sqlswitcher.model.ConversionResponse
import com.sqlswitcher.parser.SqlParserService
import com.sqlswitcher.converter.SqlConverterEngine
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class SqlConversionService(
    private val sqlParserService: SqlParserService,
    private val sqlConverterEngine: SqlConverterEngine,
    private val sqlMetricsService: SqlMetricsService
) {

    fun convertSql(request: ConversionRequest): ConversionResponse {
        val startTime = Instant.now()
        
        // Record conversion request
        sqlMetricsService.recordConversionRequest(
            request.sourceDialect.name,
            request.targetDialect.name
        )
        
        return try {
            // Parse the SQL first
            val parseResult = sqlParserService.parseSql(request.sql)
            
            if (!parseResult.isValid) {
                // Record conversion error
                sqlMetricsService.recordConversionError(
                    request.sourceDialect.name,
                    request.targetDialect.name,
                    "parse_error"
                )
                
                return ConversionResponse(
                    originalSql = request.sql,
                    convertedSql = request.sql,
                    sourceDialect = request.sourceDialect,
                    targetDialect = request.targetDialect,
                    warnings = listOf("SQL parsing failed: ${parseResult.errors.joinToString(", ")}"),
                    conversionTime = parseResult.parseTime
                )
            }
            
            // Convert the SQL
            val conversionResult = sqlConverterEngine.convertSql(
                request.sql,
                request.sourceDialect,
                request.targetDialect
            )
            
            val endTime = Instant.now()
            val totalTime = Duration.between(startTime, endTime).toMillis()
            
            // Record successful conversion
            sqlMetricsService.recordConversionSuccess(
                request.sourceDialect.name,
                request.targetDialect.name
            )
            
            // Record conversion duration
            sqlMetricsService.recordConversionDuration(
                totalTime,
                request.sourceDialect.name,
                request.targetDialect.name
            )
            
            ConversionResponse(
                originalSql = request.sql,
                convertedSql = conversionResult.convertedSql,
                sourceDialect = request.sourceDialect,
                targetDialect = request.targetDialect,
                warnings = conversionResult.warnings,
                conversionTime = totalTime
            )
        } catch (e: Exception) {
            val endTime = Instant.now()
            val totalTime = Duration.between(startTime, endTime).toMillis()
            
            // Record conversion error
            sqlMetricsService.recordConversionError(
                request.sourceDialect.name,
                request.targetDialect.name,
                "conversion_error"
            )
            
            ConversionResponse(
                originalSql = request.sql,
                convertedSql = request.sql,
                sourceDialect = request.sourceDialect,
                targetDialect = request.targetDialect,
                warnings = listOf("Conversion failed: ${e.message}"),
                conversionTime = totalTime
            )
        }
    }
}
