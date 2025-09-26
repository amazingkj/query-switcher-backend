package com.sqlswitcher.parser

import com.sqlswitcher.parser.error.SqlErrorHandler
import com.sqlswitcher.parser.error.SqlParseException
import com.sqlswitcher.parser.model.AstAnalysisResult
import com.sqlswitcher.parser.model.ConversionDifficulty
import com.sqlswitcher.service.SqlCacheService
import com.sqlswitcher.service.SqlMetricsService
import net.sf.jsqlparser.JSQLParserException
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class SqlParserService(
    private val statementHandlerFactory: StatementHandlerFactory,
    private val astAnalysisService: AstAnalysisService,
    private val sqlErrorHandler: SqlErrorHandler,
    private val sqlCacheService: SqlCacheService,
    private val sqlMetricsService: SqlMetricsService
) {

    fun parseSql(sql: String): ParseResult {
        // Record parse request
        sqlMetricsService.recordParseRequest()
        
        // Try to get from cache first
        val cachedResult = sqlCacheService.getCachedParseResult(sql)
        if (cachedResult != null) {
            sqlMetricsService.recordCacheHit("parse")
            return cachedResult
        }
        
        sqlMetricsService.recordCacheMiss("parse")
        
        // If not in cache, compute and cache the result
        return sqlCacheService.getOrComputeParseResult(sql) { sqlToParse ->
            parseSqlInternal(sqlToParse)
        }
    }
    
    private fun parseSqlInternal(sql: String): ParseResult {
        val startTime = Instant.now()
        
        return try {
            val statement = CCJSqlParserUtil.parse(sql)
            val endTime = Instant.now()
            val parseTime = Duration.between(startTime, endTime).toMillis()
            
            // Record parse duration
            sqlMetricsService.recordParseDuration(parseTime)
            
            // Extract metadata using statement handlers
            val metadata = statementHandlerFactory.getStatementMetadata(statement)
            
            // Perform comprehensive AST analysis with caching
            val analysisStartTime = Instant.now()
            val astAnalysis = sqlCacheService.getOrComputeAnalysisResult(sql) { sqlToAnalyze ->
                astAnalysisService.analyzeStatement(statement)
            }
            val analysisEndTime = Instant.now()
            val analysisTime = Duration.between(analysisStartTime, analysisEndTime).toMillis()
            
            // Record analysis duration
            sqlMetricsService.recordAnalysisDuration(analysisTime)
            
            val conversionDifficulty = astAnalysisService.getConversionDifficulty(astAnalysis)
            val warnings = astAnalysisService.getConversionWarnings(astAnalysis)
            
            // Record successful parse with metadata
            sqlMetricsService.recordParseSuccess(metadata?.type, conversionDifficulty)
            
            // Record statement complexity metrics
            astAnalysis?.let { analysis ->
                sqlMetricsService.recordStatementComplexity(
                    statementType = metadata?.type ?: StatementType.UNKNOWN,
                    complexity = conversionDifficulty ?: ConversionDifficulty.EASY,
                    joinCount = analysis.complexityDetails.joinCount,
                    subqueryCount = analysis.complexityDetails.subqueryCount,
                    functionCount = analysis.complexityDetails.functionCount
                )
            }
            
            ParseResult(
                isValid = true,
                statement = statement,
                errors = emptyList(),
                parseTime = parseTime,
                metadata = metadata,
                astAnalysis = astAnalysis,
                conversionDifficulty = conversionDifficulty,
                warnings = warnings,
                parseException = null
            )
        } catch (e: JSQLParserException) {
            val endTime = Instant.now()
            val parseTime = Duration.between(startTime, endTime).toMillis()
            
            // Record parse duration even for errors
            sqlMetricsService.recordParseDuration(parseTime)
            
            val sqlParseException = sqlErrorHandler.handleParsingError(e, sql)
            val userFriendlyMessage = sqlErrorHandler.getUserFriendlyMessage(sqlParseException)
            
            // Record parse error
            sqlMetricsService.recordParseError(sqlParseException.errorType.name)
            
            ParseResult(
                isValid = false,
                statement = null,
                errors = listOf(userFriendlyMessage),
                parseTime = parseTime,
                metadata = null,
                astAnalysis = null,
                conversionDifficulty = null,
                warnings = emptyList(),
                parseException = sqlParseException
            )
        } catch (e: Exception) {
            val endTime = Instant.now()
            val parseTime = Duration.between(startTime, endTime).toMillis()
            
            // Record parse duration even for errors
            sqlMetricsService.recordParseDuration(parseTime)
            
            val sqlParseException = sqlErrorHandler.handleUnexpectedError(e, sql)
            val userFriendlyMessage = sqlErrorHandler.getUserFriendlyMessage(sqlParseException)
            
            // Record parse error
            sqlMetricsService.recordParseError(sqlParseException.errorType.name)
            
            ParseResult(
                isValid = false,
                statement = null,
                errors = listOf(userFriendlyMessage),
                parseTime = parseTime,
                metadata = null,
                astAnalysis = null,
                conversionDifficulty = null,
                warnings = emptyList(),
                parseException = sqlParseException
            )
        }
    }
}

data class ParseResult(
    val isValid: Boolean,
    val statement: Statement?,
    val errors: List<String>,
    val parseTime: Long,
    val metadata: StatementMetadata?,
    val astAnalysis: AstAnalysisResult?,
    val conversionDifficulty: ConversionDifficulty?,
    val warnings: List<String>,
    val parseException: SqlParseException? = null
)
