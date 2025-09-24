package com.sqlswitcher.model

data class ConversionResponse(
    val originalSql: String,
    val convertedSql: String,
    val sourceDialect: DatabaseDialect,
    val targetDialect: DatabaseDialect,
    val warnings: List<String>,
    val conversionTime: Long
)
