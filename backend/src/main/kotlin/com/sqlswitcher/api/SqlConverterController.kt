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
