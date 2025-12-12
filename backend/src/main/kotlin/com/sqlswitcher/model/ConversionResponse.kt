package com.sqlswitcher.model

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType

data class ConversionResponse(
    val originalSql: String,
    val convertedSql: String,
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val warnings: List<ConversionWarning>,
    val appliedRules: List<String>,
    val conversionTime: Long,
    val success: Boolean = true,
    val error: String? = null
)
