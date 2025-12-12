package com.sqlswitcher.api

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.model.*
import com.sqlswitcher.service.SqlTestService
import com.sqlswitcher.service.SqlValidationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/validate")
@CrossOrigin(origins = ["*"])
@Tag(name = "SQL Validation", description = "SQL validation and testing API")
class SqlValidationController(
    private val sqlValidationService: SqlValidationService,
    private val sqlTestService: SqlTestService
) {

    @PostMapping(
        "/syntax",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Validate SQL syntax",
        description = "Validates SQL syntax using JSQLParser. Fast validation without database connection."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Validation completed",
                content = [Content(schema = Schema(implementation = ValidationResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun validateSyntax(@RequestBody request: ValidationRequest): ResponseEntity<ValidationResponse> {
        val result = sqlValidationService.validateSyntax(request.sql, request.dialect)

        return ResponseEntity.ok(ValidationResponse(
            isValid = result.isValid,
            dialect = request.dialect,
            errors = result.errors.map { error ->
                ValidationErrorDto(
                    message = error.message,
                    line = error.line,
                    column = error.column,
                    suggestion = error.suggestion
                )
            },
            warnings = result.warnings,
            parsedStatementType = result.parsedStatementType
        ))
    }

    @PostMapping(
        "/test",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Test SQL in real database",
        description = "Executes SQL in a real database using Testcontainers. DryRun mode rolls back changes after execution."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Test completed",
                content = [Content(schema = Schema(implementation = TestResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "503",
                description = "Docker not available",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun testSql(@RequestBody request: TestRequest): ResponseEntity<TestResponse> {
        val result = sqlTestService.testSql(request.sql, request.dialect, request.dryRun)

        return ResponseEntity.ok(TestResponse(
            success = result.success,
            dialect = result.dialect,
            executionTimeMs = result.executionTimeMs,
            error = result.error?.let { error ->
                TestErrorDto(
                    code = error.code,
                    message = error.message,
                    sqlState = error.sqlState,
                    suggestion = error.suggestion
                )
            },
            rowsAffected = result.rowsAffected,
            message = result.message
        ))
    }

    @GetMapping("/containers/status")
    @Operation(
        summary = "Get container status",
        description = "Returns the status of database containers for each dialect"
    )
    fun getContainerStatus(): ResponseEntity<ContainerStatusResponse> {
        val status = sqlTestService.getContainerStatus()

        return ResponseEntity.ok(ContainerStatusResponse(
            containers = status.map { (dialect, running) ->
                dialect to ContainerInfo(running = running, dialect = dialect)
            }.toMap()
        ))
    }

    @PostMapping("/containers/{dialect}/start")
    @Operation(
        summary = "Start container for dialect",
        description = "Starts a database container for the specified dialect (lazy start on first test)"
    )
    fun startContainer(@PathVariable dialect: DialectType): ResponseEntity<Map<String, Any>> {
        // 컨테이너는 첫 테스트 시 자동 시작되므로, 여기서는 상태만 확인
        val running = sqlTestService.isContainerRunning(dialect)

        return ResponseEntity.ok(mapOf(
            "dialect" to dialect,
            "running" to running,
            "message" to if (running) "Container is already running" else "Container will start on first test"
        ))
    }
}