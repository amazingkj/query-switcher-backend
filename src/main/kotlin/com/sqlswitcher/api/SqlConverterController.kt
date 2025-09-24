package com.sqlswitcher.api

import com.sqlswitcher.model.ConversionRequest
import com.sqlswitcher.model.ConversionResponse
import com.sqlswitcher.service.SqlConversionService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = ["*"])
class SqlConverterController(
    private val sqlConversionService: SqlConversionService
) {

    @PostMapping("/convert")
    fun convertSql(@Valid @RequestBody request: ConversionRequest): ResponseEntity<ConversionResponse> {
        val response = sqlConversionService.convertSql(request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "UP", "service" to "SQL Switcher"))
    }
}
