package com.sqlswitcher.parser

import com.sqlswitcher.parser.model.*
import net.sf.jsqlparser.statement.Statement
import org.springframework.stereotype.Service

/**
 * Service to perform comprehensive AST analysis using simple analyzer
 */
@Service
class AstAnalysisService(
    private val simpleAstAnalyzer: SimpleAstAnalyzer
) {
    
    fun analyzeStatement(statement: Statement): AstAnalysisResult {
        return simpleAstAnalyzer.analyzeStatement(statement)
    }
    
    fun getConversionDifficulty(analysisResult: AstAnalysisResult): ConversionDifficulty {
        val complexity = analysisResult.complexityDetails
        
        return when {
            complexity.totalComplexityScore <= 5 -> ConversionDifficulty.EASY
            complexity.totalComplexityScore <= 15 -> ConversionDifficulty.MODERATE
            complexity.totalComplexityScore <= 30 -> ConversionDifficulty.HARD
            else -> ConversionDifficulty.VERY_HARD
        }
    }
    
    fun getConversionWarnings(analysisResult: AstAnalysisResult): List<String> {
        val warnings = mutableListOf<String>()
        val complexity = analysisResult.complexityDetails
        val functions = analysisResult.functionExpressionInfo.functions
        
        // Check for complex features that might need manual review
        if (complexity.windowFunctionCount > 0) {
            warnings.add("Window functions detected - may require manual review for target database")
        }
        
        if (complexity.recursiveQueryCount > 0) {
            warnings.add("Recursive queries detected - complex conversion required")
        }
        
        if (complexity.cteCount > 0) {
            warnings.add("Common Table Expressions (CTEs) detected - verify target database support")
        }
        
        if (complexity.lateralJoinCount > 0) {
            warnings.add("LATERAL joins detected - not supported in all databases")
        }
        
        // Check for database-specific functions
        val mysqlSpecificFunctions = setOf("GROUP_CONCAT", "DATE_FORMAT", "IFNULL")
        val oracleSpecificFunctions = setOf("NVL", "TO_CHAR", "DECODE", "ROWNUM")
        val postgresSpecificFunctions = setOf("STRING_AGG", "ARRAY_AGG", "JSON_AGG")
        
        val detectedSpecificFunctions = functions.intersect(
            mysqlSpecificFunctions + oracleSpecificFunctions + postgresSpecificFunctions
        )
        
        if (detectedSpecificFunctions.isNotEmpty()) {
            warnings.add("Database-specific functions detected: ${detectedSpecificFunctions.joinToString(", ")}")
        }
        
        // Check for complex subqueries
        if (complexity.subqueryCount > 3) {
            warnings.add("Multiple subqueries detected - performance may be affected")
        }
        
        // Check for many JOINs
        if (complexity.joinCount > 5) {
            warnings.add("Many JOINs detected - verify query performance")
        }
        
        return warnings
    }
}

data class AstAnalysisResult(
    val tableColumnInfo: TableColumnInfo,
    val functionExpressionInfo: FunctionExpressionInfo,
    val complexityDetails: ComplexityDetails
)

enum class ConversionDifficulty {
    EASY,        // Simple SELECT, basic WHERE
    MODERATE,    // JOINs, basic functions
    HARD,        // Subqueries, aggregates, CASE
    VERY_HARD    // Window functions, CTEs, recursive queries
}
