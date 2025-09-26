package com.sqlswitcher.parser

import com.sqlswitcher.parser.model.*
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import org.springframework.stereotype.Service

@Service
class SimpleAstAnalyzer {

    fun analyzeStatement(statement: Statement): AstAnalysisResult {
        val tableColumnInfo = extractTableColumnInfo(statement)
        val functionExpressionInfo = extractFunctionExpressionInfo(statement)
        val complexityDetails = calculateComplexityDetails(statement)

        return AstAnalysisResult(
            tableColumnInfo = tableColumnInfo,
            functionExpressionInfo = functionExpressionInfo,
            complexityDetails = complexityDetails
        )
    }

    private fun extractTableColumnInfo(statement: Statement): TableColumnInfo {
        val tables = mutableSetOf<String>()
        val columns = mutableSetOf<String>()
        val tableAliases = mutableMapOf<String, String>()

        // Simple string-based analysis for now to ensure compilation works
        val sqlString = statement.toString()

        // Extract basic table names from FROM and JOIN clauses
        extractTablesFromSql(sqlString, tables, tableAliases)

        // Extract basic column references
        extractColumnsFromSql(sqlString, columns)

        return TableColumnInfo(
            tables = tables,
            columns = columns,
            tableAliases = tableAliases
        )
    }

    private fun extractTablesFromSql(sql: String, tables: MutableSet<String>, tableAliases: MutableMap<String, String>) {
        // Simple regex-based table extraction
        val fromPattern = Regex("""(?i)\bFROM\s+([a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)?)\s*(?:\s+AS\s+([a-zA-Z_]\w*)|\s+([a-zA-Z_]\w*))?""")
        val joinPattern = Regex("""(?i)\bJOIN\s+([a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)?)\s*(?:\s+AS\s+([a-zA-Z_]\w*)|\s+([a-zA-Z_]\w*))?""")

        fromPattern.findAll(sql).forEach { match ->
            val tableName = match.groupValues[1]
            tables.add(tableName)
            val alias = match.groupValues[2].ifEmpty { match.groupValues[3] }
            if (alias.isNotEmpty()) {
                tableAliases[alias] = tableName
            }
        }

        joinPattern.findAll(sql).forEach { match ->
            val tableName = match.groupValues[1]
            tables.add(tableName)
            val alias = match.groupValues[2].ifEmpty { match.groupValues[3] }
            if (alias.isNotEmpty()) {
                tableAliases[alias] = tableName
            }
        }
    }

    private fun extractColumnsFromSql(sql: String, columns: MutableSet<String>) {
        // Simple column extraction - look for SELECT clause
        val selectPattern = Regex("""(?i)SELECT\s+(.*?)\s+FROM""")
        selectPattern.find(sql)?.let { match ->
            val selectClause = match.groupValues[1]
            if (selectClause.trim() == "*") {
                columns.add("*")
            } else {
                // Split by comma and extract column names
                selectClause.split(",").forEach { column ->
                    val cleanColumn = column.trim().replace(Regex(""".*\."""), "").replace(Regex("""\s+AS\s+.*""", RegexOption.IGNORE_CASE), "")
                    if (cleanColumn.isNotEmpty() && !cleanColumn.contains("(")) {
                        columns.add(cleanColumn)
                    }
                }
            }
        }
    }

    private fun extractFunctionExpressionInfo(statement: Statement): FunctionExpressionInfo {
        val functions = mutableSetOf<String>()
        val expressions = mutableListOf<ExpressionType>()
        val aggregateFunctions = mutableSetOf<String>()
        val windowFunctions = mutableSetOf<String>()

        val sqlString = statement.toString()

        // Extract functions using pattern matching
        val functionPattern = Regex("""(?i)\b([A-Z_]+)\s*\(""")
        functionPattern.findAll(sqlString).forEach { match ->
            val functionName = match.groupValues[1].uppercase()
            functions.add(functionName)

            if (isAggregateFunction(functionName)) {
                aggregateFunctions.add(functionName)
            }

            if (isWindowFunction(functionName)) {
                windowFunctions.add(functionName)
            }
        }

        // Add basic expression types based on SQL content
        if (sqlString.contains("CASE", ignoreCase = true)) {
            expressions.add(ExpressionType.CASE)
        }
        if (sqlString.contains(" AND ", ignoreCase = true)) {
            expressions.add(ExpressionType.LOGICAL_AND)
        }
        if (sqlString.contains(" OR ", ignoreCase = true)) {
            expressions.add(ExpressionType.LOGICAL_OR)
        }
        if (sqlString.contains("=")) {
            expressions.add(ExpressionType.EQUALITY)
        }
        if (sqlString.contains(">")) {
            expressions.add(ExpressionType.GREATER_THAN)
        }
        if (sqlString.contains("<")) {
            expressions.add(ExpressionType.LESS_THAN)
        }
        if (sqlString.contains(" LIKE ", ignoreCase = true)) {
            expressions.add(ExpressionType.LIKE)
        }
        if (sqlString.contains(" IN ", ignoreCase = true)) {
            expressions.add(ExpressionType.IN)
        }
        if (sqlString.contains(" BETWEEN ", ignoreCase = true)) {
            expressions.add(ExpressionType.BETWEEN)
        }
        if (sqlString.contains(" IS NULL", ignoreCase = true)) {
            expressions.add(ExpressionType.IS_NULL)
        }

        return FunctionExpressionInfo(
            functions = functions,
            expressions = expressions,
            aggregateFunctions = aggregateFunctions,
            windowFunctions = windowFunctions
        )
    }

    private fun calculateComplexityDetails(statement: Statement): ComplexityDetails {
        val sqlString = statement.toString()

        // Count various SQL constructs
        val joinCount = countOccurrences(sqlString, listOf("JOIN"))
        val subqueryCount = countOccurrences(sqlString, listOf("SELECT")) - 1 // Subtract main SELECT
        val unionCount = countOccurrences(sqlString, listOf("UNION"))
        val caseExpressionCount = countOccurrences(sqlString, listOf("CASE"))
        val cteCount = countOccurrences(sqlString, listOf("WITH"))
        val recursiveQueryCount = countOccurrences(sqlString, listOf("RECURSIVE"))
        val lateralJoinCount = countOccurrences(sqlString, listOf("LATERAL"))

        // Count functions
        val functionPattern = Regex("""(?i)\b[A-Z_]+\s*\(""")
        val functionCount = functionPattern.findAll(sqlString).count()

        // Count aggregates
        val aggregateFunctions = listOf("COUNT", "SUM", "AVG", "MIN", "MAX", "GROUP_CONCAT", "STRING_AGG")
        val aggregateCount = aggregateFunctions.sumOf { func ->
            countOccurrences(sqlString, listOf(func))
        }

        // Count window functions
        val windowFunctionsList = listOf("ROW_NUMBER", "RANK", "DENSE_RANK", "LAG", "LEAD")
        val windowFunctionCount = windowFunctionsList.sumOf { func ->
            countOccurrences(sqlString, listOf(func))
        }

        val totalComplexityScore = 1 + joinCount * 2 + subqueryCount * 3 + functionCount +
                                 aggregateCount * 2 + windowFunctionCount * 4 + caseExpressionCount * 2 +
                                 unionCount * 2 + cteCount * 3 + recursiveQueryCount * 5 + lateralJoinCount * 3

        return ComplexityDetails(
            joinCount = joinCount,
            subqueryCount = maxOf(0, subqueryCount), // Ensure non-negative
            functionCount = functionCount,
            aggregateCount = aggregateCount,
            windowFunctionCount = windowFunctionCount,
            caseExpressionCount = caseExpressionCount,
            unionCount = unionCount,
            cteCount = cteCount,
            recursiveQueryCount = recursiveQueryCount,
            pivotCount = 0, // Not implemented in simple version
            lateralJoinCount = lateralJoinCount,
            totalComplexityScore = totalComplexityScore
        )
    }

    private fun countOccurrences(text: String, keywords: List<String>): Int {
        return keywords.sumOf { keyword ->
            Regex("""(?i)\b$keyword\b""").findAll(text).count()
        }
    }

    private fun isAggregateFunction(functionName: String): Boolean {
        val aggregateFunctions = setOf(
            "COUNT", "SUM", "AVG", "MIN", "MAX", "GROUP_CONCAT",
            "STRING_AGG", "LISTAGG", "ARRAY_AGG", "JSON_AGG"
        )
        return aggregateFunctions.contains(functionName.uppercase())
    }

    private fun isWindowFunction(functionName: String): Boolean {
        val windowFunctions = setOf(
            "ROW_NUMBER", "RANK", "DENSE_RANK", "NTILE", "PERCENT_RANK", "CUME_DIST",
            "LAG", "LEAD", "FIRST_VALUE", "LAST_VALUE", "NTH_VALUE"
        )
        return windowFunctions.contains(functionName.uppercase())
    }
}