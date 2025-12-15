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

        // 4. TABLESPACE 절 제거 (전체)
        result = result.replace(
            Regex("""\s+TABLESPACE\s+["']?\w+["']?""", RegexOption.IGNORE_CASE),
            ""
        )

        // 5. STORAGE 절 제거 (여러 줄에 걸친 경우 포함)
        result = result.replace(
            Regex("""\s*STORAGE\s*\([\s\S]*?\)""", RegexOption.IGNORE_CASE),
            ""
        )

        // 6. PCTFREE, INITRANS 등 물리적 옵션 제거 (단독 또는 인라인)
        result = result.replace(
            Regex("""\s+(PCTFREE|PCTUSED|INITRANS|MAXTRANS)\s+\d+""", RegexOption.IGNORE_CASE),
            ""
        )

        // 7. LOGGING/NOLOGGING 제거
        result = result.replace(
            Regex("""\s+(NO)?LOGGING\b""", RegexOption.IGNORE_CASE),
            ""
        )

        // 8. COMPRESS/NOCOMPRESS 제거
        result = result.replace(
            Regex("""\s+(NO)?COMPRESS(\s+FOR\s+\w+)?(\s+\d+)?""", RegexOption.IGNORE_CASE),
            ""
        )

        // 9. PARALLEL/NOPARALLEL 제거
        result = result.replace(
            Regex("""\s+(NO)?PARALLEL(\s+\d+)?""", RegexOption.IGNORE_CASE),
            ""
        )

        // 10. CACHE/NOCACHE 제거
        result = result.replace(
            Regex("""\s+(NO)?CACHE\b""", RegexOption.IGNORE_CASE),
            ""
        )

        // 11. MONITORING/NOMONITORING 제거
        result = result.replace(
            Regex("""\s+(NO)?MONITORING\b""", RegexOption.IGNORE_CASE),
            ""
        )

        // 12. SEGMENT CREATION 제거
        result = result.replace(
            Regex("""\s+SEGMENT\s+CREATION\s+(IMMEDIATE|DEFERRED)""", RegexOption.IGNORE_CASE),
            ""
        )

        // 13. ENABLE/DISABLE ROW MOVEMENT 제거
        result = result.replace(
            Regex("""\s+(ENABLE|DISABLE)\s+ROW\s+MOVEMENT""", RegexOption.IGNORE_CASE),
            ""
        )

        // 14. FLASHBACK ARCHIVE 제거
        result = result.replace(
            Regex("""\s+FLASHBACK\s+ARCHIVE[^;]*""", RegexOption.IGNORE_CASE),
            ""
        )

        // 15. RESULT_CACHE 힌트 제거
        result = result.replace(
            Regex("""/\*\+?\s*RESULT_CACHE[^*]*\*/""", RegexOption.IGNORE_CASE),
            ""
        )

        // 16. LOB 저장소 옵션 제거 (SECUREFILE/BASICFILE)
        result = result.replace(
            Regex("""\s+LOB\s*\([^)]+\)\s+STORE\s+AS\s+(SECUREFILE|BASICFILE)?[^)]*\)""", RegexOption.IGNORE_CASE),
            ""
        )

        // 17. USING INDEX 절 제거 (제약조건 내)
        result = result.replace(
            Regex("""\s+USING\s+INDEX(\s+TABLESPACE\s+\w+)?""", RegexOption.IGNORE_CASE),
            ""
        )

        // 18. ENABLE/DISABLE VALIDATE/NOVALIDATE 제거 (제약조건 상태)
        result = result.replace(
            Regex("""\s+(ENABLE|DISABLE)(\s+(VALIDATE|NOVALIDATE))?(?=\s*[,)]|\s*$)""", RegexOption.IGNORE_CASE),
            ""
        )

        // 19. RELY/NORELY 제거
        result = result.replace(
            Regex("""\s+(NO)?RELY\b""", RegexOption.IGNORE_CASE),
            ""
        )

        // 20. Oracle 힌트 단순화 (복잡한 힌트 제거)
        result = result.replace(
            Regex("""/\*\+[^*]+\*/"""),
            "/* hint removed */"
        )

        // 21. BYTE/CHAR 키워드 제거 (VARCHAR2(100 BYTE))
        result = result.replace(
            Regex("""\(\s*(\d+)\s+(BYTE|CHAR)\s*\)""", RegexOption.IGNORE_CASE),
            "($1)"
        )

        // 22. DEFAULT ON NULL 제거 (Oracle 12c+)
        result = result.replace(
            Regex("""\s+DEFAULT\s+ON\s+NULL\b""", RegexOption.IGNORE_CASE),
            " DEFAULT"
        )

        // 23. VISIBLE/INVISIBLE 컬럼 제거
        result = result.replace(
            Regex("""\s+(IN)?VISIBLE\b""", RegexOption.IGNORE_CASE),
            ""
        )

        // 24. ORGANIZATION (HEAP|INDEX) 제거
        result = result.replace(
            Regex("""\s+ORGANIZATION\s+(HEAP|INDEX|EXTERNAL)""", RegexOption.IGNORE_CASE),
            ""
        )

        // 25. ROWDEPENDENCIES/NOROWDEPENDENCIES 제거
        result = result.replace(
            Regex("""\s+(NO)?ROWDEPENDENCIES\b""", RegexOption.IGNORE_CASE),
            ""
        )

        // 26. Oracle 특수 데이터타입을 일반 타입으로 변환 (파서 호환)
        result = result.replace(
            Regex("""\bBINARY_FLOAT\b""", RegexOption.IGNORE_CASE),
            "FLOAT"
        )
        result = result.replace(
            Regex("""\bBINARY_DOUBLE\b""", RegexOption.IGNORE_CASE),
            "DOUBLE"
        )

        // 27. INTERVAL 타입 단순화
        result = result.replace(
            Regex("""\bINTERVAL\s+YEAR(\s*\(\d+\))?\s+TO\s+MONTH\b""", RegexOption.IGNORE_CASE),
            "VARCHAR(50)"
        )
        result = result.replace(
            Regex("""\bINTERVAL\s+DAY(\s*\(\d+\))?\s+TO\s+SECOND(\s*\(\d+\))?\b""", RegexOption.IGNORE_CASE),
            "VARCHAR(50)"
        )

        // 28. XMLTYPE 단순화
        result = result.replace(
            Regex("""\bXMLTYPE\b""", RegexOption.IGNORE_CASE),
            "CLOB"
        )

        // 29. 연속된 빈 줄 정리
        result = result.replace(Regex("""\n\s*\n\s*\n"""), "\n\n")

        // 30. 연속된 공백 정리
        result = result.replace(Regex("""  +"""), " ")

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
