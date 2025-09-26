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
            // Split SQL by semicolon to handle multiple statements
            val sqlStatements = request.sql.split(";")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            // Map model options to converter options
            val conversionOptions = request.options?.let {
                ConversionOptions(
                    preserveComments = it.enableComments,
                    formatOutput = it.formatSql,
                    includeWarnings = true,
                    strictMode = it.strictMode,
                    customMappings = emptyMap(),
                    skipUnsupportedFeatures = !it.replaceUnsupportedFunctions,
                    maxComplexityScore = 100
                )
            } ?: ConversionOptions()

            // Convert each statement separately
            val convertedStatements = mutableListOf<String>()
            val allWarnings = mutableListOf<ConversionWarning>()
            val allAppliedRules = mutableListOf<String>()

            for (sqlStatement in sqlStatements) {
                try {
                    val conversionResult = sqlConverterEngine.convert(
                        sql = sqlStatement,
                        sourceDialectType = request.sourceDialect,
                        targetDialectType = request.targetDialect,
                        options = conversionOptions
                    )
                    convertedStatements.add(conversionResult.convertedSql)
                    allWarnings.addAll(conversionResult.warnings)
                    allAppliedRules.addAll(conversionResult.appliedRules)
                } catch (e: Exception) {
                    // If one statement fails, add the original and a warning
                    convertedStatements.add(sqlStatement)
                    allWarnings.add(ConversionWarning(
                        type = WarningType.UNSUPPORTED_STATEMENT,
                        message = "Failed to convert statement: ${e.message}",
                        severity = WarningSeverity.WARNING,
                        suggestion = "Please review this statement manually"
                    ))
                }
            }

            // Join converted statements back together
            val convertedSql = convertedStatements.joinToString(";\n") + if (convertedStatements.isNotEmpty()) ";" else ""
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
                convertedSql = convertedSql,
                sourceDialect = request.sourceDialect,
                targetDialect = request.targetDialect,
                warnings = allWarnings,
                appliedRules = allAppliedRules.distinct(),
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
