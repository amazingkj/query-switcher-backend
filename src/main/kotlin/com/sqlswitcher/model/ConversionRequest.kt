package com.sqlswitcher.model

import com.sqlswitcher.converter.DialectType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ConversionRequest(
    @field:NotBlank(message = "SQL cannot be blank")
    @field:Size(max = 50000, message = "SQL cannot exceed 50,000 characters")
    val sql: String,
    
    @field:NotNull(message = "Source dialect is required")
    val sourceDialect: DialectType,
    
    @field:NotNull(message = "Target dialect is required")
    val targetDialect: DialectType,
    
    val options: ConversionOptions? = null
)

data class ConversionOptions(
    val strictMode: Boolean = false,
    val enableComments: Boolean = true,
    val formatSql: Boolean = false,
    val replaceUnsupportedFunctions: Boolean = false
)
