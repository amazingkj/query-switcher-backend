package com.sqlswitcher.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class ConversionRequest(
    @field:NotBlank(message = "SQL cannot be blank")
    val sql: String,
    
    @field:NotNull(message = "Source dialect is required")
    val sourceDialect: DatabaseDialect,
    
    @field:NotNull(message = "Target dialect is required")
    val targetDialect: DatabaseDialect
)
