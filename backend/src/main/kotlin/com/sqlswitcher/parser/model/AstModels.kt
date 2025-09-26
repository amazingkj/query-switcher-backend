package com.sqlswitcher.parser.model

data class TableColumnInfo(
    val tables: Set<String>,
    val columns: Set<String>,
    val tableAliases: Map<String, String>
)

data class FunctionExpressionInfo(
    val functions: Set<String>,
    val expressions: List<ExpressionType>,
    val aggregateFunctions: Set<String>,
    val windowFunctions: Set<String>
)

data class ComplexityDetails(
    val joinCount: Int,
    val subqueryCount: Int,
    val functionCount: Int,
    val aggregateCount: Int,
    val windowFunctionCount: Int,
    val caseExpressionCount: Int,
    val unionCount: Int,
    val cteCount: Int,
    val recursiveQueryCount: Int,
    val pivotCount: Int,
    val lateralJoinCount: Int,
    val totalComplexityScore: Int
) {
    fun getComplexityLevel(): ComplexityLevel {
        return when {
            totalComplexityScore <= 5 -> ComplexityLevel.SIMPLE
            totalComplexityScore <= 15 -> ComplexityLevel.MODERATE
            totalComplexityScore <= 30 -> ComplexityLevel.COMPLEX
            else -> ComplexityLevel.VERY_COMPLEX
        }
    }
}

data class AstAnalysisResult(
    val tableColumnInfo: TableColumnInfo,
    val functionExpressionInfo: FunctionExpressionInfo,
    val complexityDetails: ComplexityDetails
)

enum class ExpressionType {
    COLUMN_REFERENCE,
    STRING_LITERAL,
    NUMERIC_LITERAL,
    DATE_LITERAL,
    TIME_LITERAL,
    TIMESTAMP_LITERAL,
    NULL_LITERAL,
    PARAMETER,
    NAMED_PARAMETER,
    LOGICAL_AND,
    LOGICAL_OR,
    EQUALITY,
    INEQUALITY,
    GREATER_THAN,
    GREATER_THAN_EQUALS,
    LESS_THAN,
    LESS_THAN_EQUALS,
    LIKE,
    IN,
    BETWEEN,
    IS_NULL,
    ARITHMETIC_ADD,
    ARITHMETIC_SUBTRACT,
    ARITHMETIC_MULTIPLY,
    ARITHMETIC_DIVIDE,
    ARITHMETIC_MODULO,
    CASE,
    SUBQUERY
}

enum class ComplexityLevel {
    SIMPLE,      // Basic SELECT, simple WHERE
    MODERATE,    // JOINs, basic functions
    COMPLEX,     // Subqueries, aggregates, CASE
    VERY_COMPLEX // Window functions, CTEs, recursive queries
}

enum class ConversionDifficulty {
    EASY,        // Simple SELECT, basic WHERE
    MODERATE,    // JOINs, basic functions
    HARD,        // Subqueries, aggregates, CASE
    VERY_HARD    // Window functions, CTEs, recursive queries
}
