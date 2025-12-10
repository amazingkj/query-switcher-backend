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

        // Oracle 전용 문법 전처리 (JSQLParser가 파싱 가능하도록)
        val preprocessedSql = preprocessOracleSyntax(sql)

        return try {
            // Try to parse as multiple statements first
            val statements = parseMultipleStatements(preprocessedSql)

            // Use the first statement for now (can be extended to handle multiple)
            val statement = statements.firstOrNull() ?: throw JSQLParserException("No valid SQL statement found")

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

    /**
     * 여러 SQL 문장을 파싱 (세미콜론으로 구분)
     */
    private fun parseMultipleStatements(sql: String): List<Statement> {
        // 1. 먼저 JSQLParser의 parseStatements 시도
        try {
            val parsed = CCJSqlParserUtil.parseStatements(sql)
            if (parsed?.statements?.isNotEmpty() == true) {
                return parsed.statements
            }
        } catch (e: Exception) {
            // parseStatements 실패 시 수동 분리 시도
        }

        // 2. 세미콜론으로 분리해서 개별 파싱
        val statements = mutableListOf<Statement>()
        val sqlStatements = splitSqlStatements(sql)

        for (singleSql in sqlStatements) {
            val trimmed = singleSql.trim()
            if (trimmed.isNotEmpty()) {
                try {
                    val statement = CCJSqlParserUtil.parse(trimmed)
                    statements.add(statement)
                } catch (e: Exception) {
                    // 개별 문장 파싱 실패 시 첫 번째 오류를 throw
                    if (statements.isEmpty()) {
                        throw e
                    }
                    // 이미 파싱된 문장이 있으면 계속 진행
                }
            }
        }

        if (statements.isEmpty()) {
            // 모든 방법 실패 시 원본 SQL로 단일 파싱 시도
            return listOf(CCJSqlParserUtil.parse(sql))
        }

        return statements
    }

    /**
     * SQL 문장을 세미콜론으로 분리 (문자열 리터럴 내부 세미콜론은 무시)
     */
    private fun splitSqlStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0

        while (i < sql.length) {
            val char = sql[i]

            when {
                // 이스케이프된 따옴표 처리
                char == '\\' && i + 1 < sql.length -> {
                    current.append(char)
                    current.append(sql[i + 1])
                    i += 2
                    continue
                }
                // 작은따옴표
                char == '\'' && !inDoubleQuote -> {
                    inSingleQuote = !inSingleQuote
                    current.append(char)
                }
                // 큰따옴표
                char == '"' && !inSingleQuote -> {
                    inDoubleQuote = !inDoubleQuote
                    current.append(char)
                }
                // 세미콜론 (문자열 밖에서만)
                char == ';' && !inSingleQuote && !inDoubleQuote -> {
                    val stmt = current.toString().trim()
                    if (stmt.isNotEmpty()) {
                        statements.add(stmt)
                    }
                    current.clear()
                }
                else -> {
                    current.append(char)
                }
            }
            i++
        }

        // 마지막 문장 처리
        val lastStmt = current.toString().trim()
        if (lastStmt.isNotEmpty()) {
            statements.add(lastStmt)
        }

        return statements
    }

    /**
     * Oracle 전용 문법을 JSQLParser가 파싱 가능한 형태로 전처리
     */
    private fun preprocessOracleSyntax(sql: String): String {
        var result = sql

        // 1. CASCADE CONSTRAINTS → CASCADE (Oracle DROP TABLE)
        result = result.replace(
            Regex("""CASCADE\s+CONSTRAINTS""", RegexOption.IGNORE_CASE),
            "CASCADE"
        )

        // 2. PURGE 키워드 제거 (Oracle DROP TABLE)
        result = result.replace(
            Regex("""\s+PURGE\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            ""
        )

        // 3. SUBPARTITIONS ... STORE IN (...) 제거
        result = result.replace(
            Regex("""\s*SUBPARTITIONS\s+\d+\s+STORE\s+IN\s*\([^)]+\)""", RegexOption.IGNORE_CASE),
            ""
        )

        // 4. TABLESPACE 절 (파티션 내부) - 파티션 정의 내 단독 라인
        result = result.replace(
            Regex("""^\s*TABLESPACE\s+\w+\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            ""
        )

        // 5. STORAGE 절 제거 (여러 줄에 걸친 경우 포함)
        result = result.replace(
            Regex("""\s*STORAGE\s*\([\s\S]*?\)""", RegexOption.IGNORE_CASE),
            ""
        )

        // 6. PCTFREE, INITRANS 등 물리적 옵션 라인 제거
        result = result.replace(
            Regex("""^\s*(PCTFREE|PCTUSED|INITRANS|MAXTRANS|LOGGING|NOLOGGING|COMPRESS|NOCOMPRESS)(\s+\d+)?\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            ""
        )

        // 7. 연속된 빈 줄 정리
        result = result.replace(Regex("""\n\s*\n\s*\n"""), "\n\n")

        return result.trim()
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
