package com.sqlswitcher.service

import com.sqlswitcher.parser.model.ConversionDifficulty
import com.sqlswitcher.parser.StatementType
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.Gauge
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong

@Service
class SqlMetricsService(private val meterRegistry: MeterRegistry) {
    
    // Counters for tracking various events
    private val parseRequestCounter = Counter.builder("sql.parse.requests.total")
        .description("Total number of SQL parse requests")
        .register(meterRegistry)
    
    private val parseSuccessCounter = Counter.builder("sql.parse.success.total")
        .description("Total number of successful SQL parse requests")
        .register(meterRegistry)
    
    private val parseErrorCounter = Counter.builder("sql.parse.errors.total")
        .description("Total number of failed SQL parse requests")
        .register(meterRegistry)
    
    private val conversionRequestCounter = Counter.builder("sql.conversion.requests.total")
        .description("Total number of SQL conversion requests")
        .register(meterRegistry)
    
    private val conversionSuccessCounter = Counter.builder("sql.conversion.success.total")
        .description("Total number of successful SQL conversions")
        .register(meterRegistry)
    
    private val conversionErrorCounter = Counter.builder("sql.conversion.errors.total")
        .description("Total number of failed SQL conversions")
        .register(meterRegistry)
    
    // Timers for measuring performance
    private val parseTimer = Timer.builder("sql.parse.duration")
        .description("Time taken to parse SQL statements")
        .register(meterRegistry)
    
    private val analysisTimer = Timer.builder("sql.analysis.duration")
        .description("Time taken to analyze SQL statements")
        .register(meterRegistry)
    
    private val conversionTimer = Timer.builder("sql.conversion.duration")
        .description("Time taken to convert SQL statements")
        .register(meterRegistry)
    
    // Gauges for tracking current state
    private val activeParseRequests = AtomicLong(0)
    private val activeConversionRequests = AtomicLong(0)
    
    init {
        // Register gauges
        Gauge.builder("sql.parse.active.requests", activeParseRequests) { it.get().toDouble() }
            .description("Number of active parse requests")
            .register(meterRegistry)

        Gauge.builder("sql.conversion.active.requests", activeConversionRequests) { it.get().toDouble() }
            .description("Number of active conversion requests")
            .register(meterRegistry)
    }
    
    /**
     * Record a parse request
     */
    fun recordParseRequest() {
        parseRequestCounter.increment()
        activeParseRequests.incrementAndGet()
    }
    
    /**
     * Record a successful parse
     */
    fun recordParseSuccess(statementType: StatementType?, complexity: ConversionDifficulty?) {
        Counter.builder("sql.parse.success.total")
            .description("Total number of successful SQL parse requests")
            .tag("statement_type", statementType?.name ?: "unknown")
            .tag("complexity", complexity?.name ?: "unknown")
            .register(meterRegistry)
            .increment()
        activeParseRequests.decrementAndGet()
    }
    
    /**
     * Record a parse error
     */
    fun recordParseError(errorType: String) {
        Counter.builder("sql.parse.errors.total")
            .description("Total number of failed SQL parse requests")
            .tag("error_type", errorType)
            .register(meterRegistry)
            .increment()
        activeParseRequests.decrementAndGet()
    }
    
    /**
     * Record parse duration
     */
    fun recordParseDuration(durationMs: Long) {
        parseTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    
    /**
     * Record analysis duration
     */
    fun recordAnalysisDuration(durationMs: Long) {
        analysisTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    
    /**
     * Record a conversion request
     */
    fun recordConversionRequest(sourceDialect: String, targetDialect: String) {
        Counter.builder("sql.conversion.requests.total")
            .description("Total number of SQL conversion requests")
            .tag("source_dialect", sourceDialect)
            .tag("target_dialect", targetDialect)
            .register(meterRegistry)
            .increment()
        activeConversionRequests.incrementAndGet()
    }
    
    /**
     * Record a successful conversion
     */
    fun recordConversionSuccess(sourceDialect: String, targetDialect: String) {
        Counter.builder("sql.conversion.success.total")
            .description("Total number of successful SQL conversions")
            .tag("source_dialect", sourceDialect)
            .tag("target_dialect", targetDialect)
            .register(meterRegistry)
            .increment()
        activeConversionRequests.decrementAndGet()
    }
    
    /**
     * Record a conversion error
     */
    fun recordConversionError(errorType: String) {
        Counter.builder("sql.conversion.errors.total")
            .description("Total number of failed SQL conversions")
            .tag("error_type", errorType)
            .register(meterRegistry)
            .increment()
        activeConversionRequests.decrementAndGet()
    }

    /**
     * Record a conversion error with dialect info
     */
    fun recordConversionError(sourceDialect: String, targetDialect: String, errorType: String) {
        Counter.builder("sql.conversion.errors.total")
            .description("Total number of failed SQL conversions")
            .tag("source_dialect", sourceDialect)
            .tag("target_dialect", targetDialect)
            .tag("error_type", errorType)
            .register(meterRegistry)
            .increment()
        activeConversionRequests.decrementAndGet()
    }
    
    /**
     * Record conversion duration
     */
    fun recordConversionDuration(durationMs: Long, sourceDialect: String, targetDialect: String) {
        Timer.builder("sql.conversion.duration")
            .description("Time taken to convert SQL statements")
            .tag("source_dialect", sourceDialect)
            .tag("target_dialect", targetDialect)
            .register(meterRegistry)
            .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    
    /**
     * Record cache hit
     */
    fun recordCacheHit(cacheType: String) {
        Counter.builder("sql.cache.hits.total")
            .description("Total number of cache hits")
            .tag("cache_type", cacheType)
            .register(meterRegistry)
            .increment()
    }
    
    /**
     * Record cache miss
     */
    fun recordCacheMiss(cacheType: String) {
        Counter.builder("sql.cache.misses.total")
            .description("Total number of cache misses")
            .tag("cache_type", cacheType)
            .register(meterRegistry)
            .increment()
    }
    
    /**
     * Record statement complexity metrics
     */
    fun recordStatementComplexity(
        statementType: StatementType,
        complexity: ConversionDifficulty,
        joinCount: Int,
        subqueryCount: Int,
        functionCount: Int
    ) {
        Counter.builder("sql.statement.complexity.total")
            .description("Statement complexity metrics")
            .tag("statement_type", statementType.name)
            .tag("complexity", complexity.name)
            .register(meterRegistry)
            .increment()
        
        // Record individual complexity factors
        Counter.builder("sql.statement.joins.total")
            .description("Total number of joins in statements")
            .tag("statement_type", statementType.name)
            .register(meterRegistry)
            .increment(joinCount.toDouble())
        
        Counter.builder("sql.statement.subqueries.total")
            .description("Total number of subqueries in statements")
            .tag("statement_type", statementType.name)
            .register(meterRegistry)
            .increment(subqueryCount.toDouble())
        
        Counter.builder("sql.statement.functions.total")
            .description("Total number of functions in statements")
            .tag("statement_type", statementType.name)
            .register(meterRegistry)
            .increment(functionCount.toDouble())
    }
    
    /**
     * Get current metrics summary
     */
    fun getMetricsSummary(): MetricsSummary {
        return MetricsSummary(
            totalParseRequests = parseRequestCounter.count(),
            totalParseSuccess = parseSuccessCounter.count(),
            totalParseErrors = parseErrorCounter.count(),
            totalConversionRequests = conversionRequestCounter.count(),
            totalConversionSuccess = conversionSuccessCounter.count(),
            totalConversionErrors = conversionErrorCounter.count(),
            averageParseTime = parseTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
            averageAnalysisTime = analysisTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
            averageConversionTime = conversionTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
            activeParseRequests = activeParseRequests.get(),
            activeConversionRequests = activeConversionRequests.get()
        )
    }
}

data class MetricsSummary(
    val totalParseRequests: Double,
    val totalParseSuccess: Double,
    val totalParseErrors: Double,
    val totalConversionRequests: Double,
    val totalConversionSuccess: Double,
    val totalConversionErrors: Double,
    val averageParseTime: Double,
    val averageAnalysisTime: Double,
    val averageConversionTime: Double,
    val activeParseRequests: Long,
    val activeConversionRequests: Long
) {
    val parseSuccessRate: Double
        get() = if (totalParseRequests > 0) totalParseSuccess / totalParseRequests else 0.0
    
    val conversionSuccessRate: Double
        get() = if (totalConversionRequests > 0) totalConversionSuccess / totalConversionRequests else 0.0
}

// Extension methods for SqlMetricsService to provide statistics
fun SqlMetricsService.getTotalConversions(): Long {
    return this.getMetricsSummary().totalConversionRequests.toLong()
}

fun SqlMetricsService.getSuccessfulConversions(): Long {
    return this.getMetricsSummary().totalConversionSuccess.toLong()
}

fun SqlMetricsService.getFailedConversions(): Long {
    return this.getMetricsSummary().totalConversionErrors.toLong()
}

fun SqlMetricsService.getAverageConversionTime(): Double {
    return this.getMetricsSummary().averageConversionTime
}

fun SqlMetricsService.getMostUsedSourceDialect(): String? {
    // For now, return null - this would require more complex metric aggregation
    return null
}

fun SqlMetricsService.getMostUsedTargetDialect(): String? {
    // For now, return null - this would require more complex metric aggregation
    return null
}
