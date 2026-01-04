package com.sqlswitcher.api

import com.sqlswitcher.model.ConversionRequest
import com.sqlswitcher.model.ConversionResponse
import com.sqlswitcher.service.SqlConversionService
import com.sqlswitcher.service.SqlHighlightService
import com.sqlswitcher.service.SqlHighlightResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = ["*"])
@Tag(name = "SQL Converter", description = "SQL dialect conversion API")
class SqlConverterController(
    private val sqlConversionService: SqlConversionService,
    private val sqlHighlightService: SqlHighlightService
) {

    @PostMapping(
        "/convert",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Convert SQL between database dialects",
        description = "Converts SQL queries from one database dialect to another with detailed warnings and applied rules"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "SQL conversion successful",
                content = [Content(schema = Schema(implementation = ConversionResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request or SQL syntax error",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun convertSql(@Valid @RequestBody request: ConversionRequest): ResponseEntity<ConversionResponse> {
        val response = sqlConversionService.convertSql(request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns the health status of the service")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "UP", "service" to "SQL Switcher"))
    }

    @GetMapping("/dialects")
    @Operation(
        summary = "Get supported dialects",
        description = "Returns a list of supported database dialects for conversion"
    )
    fun getSupportedDialects(): ResponseEntity<DialectsResponse> {
        return ResponseEntity.ok(DialectsResponse(
            dialects = listOf(
                DialectInfo("oracle", "Oracle Database", listOf("11g", "12c", "19c", "21c")),
                DialectInfo("mysql", "MySQL", listOf("5.7", "8.0")),
                DialectInfo("postgresql", "PostgreSQL", listOf("12", "13", "14", "15", "16"))
            ),
            supportedConversions = listOf(
                ConversionPath("oracle", "mysql"),
                ConversionPath("oracle", "postgresql"),
                ConversionPath("mysql", "oracle"),
                ConversionPath("mysql", "postgresql"),
                ConversionPath("postgresql", "oracle"),
                ConversionPath("postgresql", "mysql")
            )
        ))
    }

    @GetMapping("/features")
    @Operation(
        summary = "Get supported conversion features",
        description = "Returns a list of SQL features that can be converted"
    )
    fun getSupportedFeatures(): ResponseEntity<FeaturesResponse> {
        return ResponseEntity.ok(FeaturesResponse(
            features = listOf(
                FeatureInfo(
                    category = "DDL",
                    features = listOf(
                        "CREATE/DROP TABLE",
                        "CREATE/DROP INDEX (BITMAP, REVERSE, FUNCTION-BASED)",
                        "CREATE/DROP SEQUENCE",
                        "CREATE/DROP MATERIALIZED VIEW",
                        "CREATE/DROP SYNONYM",
                        "CREATE/DROP DATABASE LINK"
                    )
                ),
                FeatureInfo(
                    category = "DML",
                    features = listOf(
                        "SELECT (JOIN, SUBQUERY, CTE)",
                        "INSERT/UPDATE/DELETE",
                        "MERGE (UPSERT)"
                    )
                ),
                FeatureInfo(
                    category = "Functions",
                    features = listOf(
                        "Date/Time functions (SYSDATE, NOW, CURRENT_TIMESTAMP)",
                        "String functions (NVL, COALESCE, DECODE, SUBSTR)",
                        "Aggregate functions (LISTAGG, GROUP_CONCAT, STRING_AGG)",
                        "Window functions (ROW_NUMBER, RANK, LEAD, LAG)",
                        "DBMS_* packages (DBMS_OUTPUT, DBMS_LOB, DBMS_RANDOM)"
                    )
                ),
                FeatureInfo(
                    category = "Oracle Specific",
                    features = listOf(
                        "Hierarchical queries (CONNECT BY → WITH RECURSIVE)",
                        "PIVOT/UNPIVOT",
                        "PL/SQL Packages and Procedures",
                        "Triggers",
                        "User-Defined Types",
                        "ROWNUM → LIMIT/OFFSET"
                    )
                )
            )
        ))
    }

    @PostMapping(
        "/batch-convert",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Batch convert multiple SQL statements",
        description = "Converts multiple SQL statements in a single request"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Batch conversion successful",
                content = [Content(schema = Schema(implementation = BatchConversionResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun batchConvertSql(@Valid @RequestBody request: BatchConversionRequest): ResponseEntity<BatchConversionResponse> {
        val sourceDialect = com.sqlswitcher.converter.DialectType.valueOf(request.sourceDialect.uppercase())
        val targetDialect = com.sqlswitcher.converter.DialectType.valueOf(request.targetDialect.uppercase())

        val results = request.statements.mapIndexed { index, sql ->
            try {
                val conversionRequest = ConversionRequest(
                    sql = sql,
                    sourceDialect = sourceDialect,
                    targetDialect = targetDialect
                )
                val response = sqlConversionService.convertSql(conversionRequest)
                BatchConversionResult(
                    index = index,
                    originalSql = sql,
                    convertedSql = response.convertedSql,
                    success = true,
                    warningCount = response.warnings.size,
                    appliedRules = response.appliedRules
                )
            } catch (e: Exception) {
                BatchConversionResult(
                    index = index,
                    originalSql = sql,
                    convertedSql = null,
                    success = false,
                    error = e.message,
                    warningCount = 0,
                    appliedRules = emptyList()
                )
            }
        }

        val successCount = results.count { it.success }
        val failCount = results.size - successCount

        return ResponseEntity.ok(BatchConversionResponse(
            totalCount = results.size,
            successCount = successCount,
            failCount = failCount,
            results = results
        ))
    }

    @PostMapping(
        "/highlight",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Highlight SQL syntax",
        description = "Tokenizes SQL and returns syntax highlighting information with HTML and ANSI outputs"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "SQL highlighting successful",
                content = [Content(schema = Schema(implementation = HighlightResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun highlightSql(@Valid @RequestBody request: HighlightRequest): ResponseEntity<HighlightResponse> {
        val result = sqlHighlightService.highlightSql(request.sql)
        return ResponseEntity.ok(HighlightResponse(
            originalSql = result.originalSql,
            html = result.html,
            ansi = result.ansi,
            tokenCount = result.tokens.size,
            tokens = if (request.includeTokens == true) result.tokens.map { token ->
                TokenInfo(
                    value = token.value,
                    type = token.type.name,
                    position = token.position
                )
            } else null
        ))
    }
}

data class ErrorResponse(
    val errorCode: String,
    val message: String,
    val timestamp: String,
    val details: String? = null
)

/**
 * 변환 에러 응답 (상세 정보 포함)
 */
data class ConversionErrorResponse(
    val errorCode: String,
    val title: String,
    val message: String,
    val suggestions: List<String>,
    val timestamp: String,
    val technicalDetails: Map<String, Any?>? = null
)

/**
 * SQL 파싱 에러 응답 (상세 정보 포함)
 */
data class SqlParseErrorResponse(
    val errorCode: String,
    val errorType: String,
    val message: String,
    val timestamp: String,
    val location: ErrorLocation? = null,
    val suggestions: List<String> = emptyList(),
    val sqlSnippet: String? = null
)

/**
 * 에러 위치 정보
 */
data class ErrorLocation(
    val line: Int? = null,
    val column: Int? = null
)

/**
 * SQL 하이라이팅 요청
 */
data class HighlightRequest(
    val sql: String,
    val includeTokens: Boolean? = false
)

/**
 * SQL 하이라이팅 응답
 */
data class HighlightResponse(
    val originalSql: String,
    val html: String,
    val ansi: String,
    val tokenCount: Int,
    val tokens: List<TokenInfo>? = null
)

/**
 * 토큰 정보
 */
data class TokenInfo(
    val value: String,
    val type: String,
    val position: Int
)

/**
 * 지원 방언 응답
 */
data class DialectsResponse(
    val dialects: List<DialectInfo>,
    val supportedConversions: List<ConversionPath>
)

data class DialectInfo(
    val id: String,
    val name: String,
    val versions: List<String>
)

data class ConversionPath(
    val source: String,
    val target: String
)

/**
 * 지원 기능 응답
 */
data class FeaturesResponse(
    val features: List<FeatureInfo>
)

data class FeatureInfo(
    val category: String,
    val features: List<String>
)

/**
 * 배치 변환 요청
 */
data class BatchConversionRequest(
    val statements: List<String>,
    val sourceDialect: String,
    val targetDialect: String
)

/**
 * 배치 변환 응답
 */
data class BatchConversionResponse(
    val totalCount: Int,
    val successCount: Int,
    val failCount: Int,
    val results: List<BatchConversionResult>
)

data class BatchConversionResult(
    val index: Int,
    val originalSql: String,
    val convertedSql: String?,
    val success: Boolean,
    val error: String? = null,
    val warningCount: Int,
    val appliedRules: List<String>
)
