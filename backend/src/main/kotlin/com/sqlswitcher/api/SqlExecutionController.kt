package com.sqlswitcher.api

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.service.SqlExecutionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/execute")
@CrossOrigin(origins = ["*"])
@Tag(name = "SQL Execution", description = "SQL execution API for testing converted queries")
class SqlExecutionController(
    private val sqlExecutionService: SqlExecutionService
) {

    /**
     * SQL 실행 요청
     */
    data class ExecuteRequest(
        @field:NotBlank(message = "SQL is required")
        val sql: String,

        val dialect: DialectType,

        val dryRun: Boolean = true
    )

    /**
     * SQL 실행
     */
    @PostMapping
    @Operation(
        summary = "Execute SQL on target database",
        description = "Executes SQL query on the specified database. Use dryRun=true to validate without committing changes."
    )
    fun executeSql(@Valid @RequestBody request: ExecuteRequest): ResponseEntity<SqlExecutionService.ExecutionResult> {
        val result = sqlExecutionService.executeSql(
            sql = request.sql,
            dialect = request.dialect,
            dryRun = request.dryRun
        )
        return ResponseEntity.ok(result.copy(dialect = request.dialect))
    }

    /**
     * 특정 DB 연결 상태 확인
     */
    @GetMapping("/status/{dialect}")
    @Operation(
        summary = "Check database connection status",
        description = "Checks if the specified database is accessible"
    )
    fun checkConnection(@PathVariable dialect: DialectType): ResponseEntity<SqlExecutionService.ConnectionStatus> {
        val status = sqlExecutionService.checkConnection(dialect)
        return ResponseEntity.ok(status)
    }

    /**
     * 모든 DB 연결 상태 확인
     */
    @GetMapping("/status")
    @Operation(
        summary = "Check all database connections",
        description = "Returns connection status for all supported databases"
    )
    fun checkAllConnections(): ResponseEntity<Map<DialectType, SqlExecutionService.ConnectionStatus>> {
        val statuses = sqlExecutionService.checkAllConnections()
        return ResponseEntity.ok(statuses)
    }
}