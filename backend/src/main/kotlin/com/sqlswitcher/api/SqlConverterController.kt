package com.sqlswitcher.api

import com.sqlswitcher.model.ConversionRequest
import com.sqlswitcher.model.ConversionResponse
import com.sqlswitcher.service.SqlConversionService
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
    private val sqlConversionService: SqlConversionService
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
