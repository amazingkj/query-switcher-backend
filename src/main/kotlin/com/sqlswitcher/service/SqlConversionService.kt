package com.sqlswitcher.service

import com.sqlswitcher.model.ConversionRequest
import com.sqlswitcher.model.ConversionResponse
import com.sqlswitcher.converter.SqlConverterEngine
import com.sqlswitcher.converter.ConversionOptions
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class SqlConversionService(
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
            // Convert the SQL using the conversion engine
            val conversionOptions = request.options ?: ConversionOptions()
            val conversionResult = sqlConverterEngine.convert(
                sql = request.sql,
                sourceDialectType = request.sourceDialect,
                targetDialectType = request.targetDialect,
                options = conversionOptions as ConversionOptions
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
                appliedRules = conversionResult.appliedRules,
                conversionTime = totalTime,
                success = true
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
            
            val errorWarning = ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "Conversion failed: ${e.message}",
                severity = WarningSeverity.ERROR,
                suggestion = "Please check your SQL syntax and try again."
            )
            
            ConversionResponse(
                originalSql = request.sql,
                convertedSql = request.sql,
                sourceDialect = request.sourceDialect,
                targetDialect = request.targetDialect,
                warnings = listOf(errorWarning),
                appliedRules = emptyList(),
                conversionTime = totalTime,
                success = false,
                error = e.message
            )
        }
    }
}
