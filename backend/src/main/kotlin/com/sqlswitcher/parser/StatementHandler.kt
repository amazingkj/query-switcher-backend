package com.sqlswitcher.parser

import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.update.Update
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.alter.Alter
import net.sf.jsqlparser.statement.drop.Drop

interface StatementHandler<T : Statement> {
    fun canHandle(statement: Statement): Boolean
    fun handle(statement: T): StatementMetadata
}

data class StatementMetadata(
    val type: StatementType,
    val tables: List<String>,
    val columns: List<String>,
    val hasJoins: Boolean,
    val hasSubqueries: Boolean,
    val complexity: Int
)

enum class StatementType {
    SELECT, INSERT, UPDATE, DELETE, CREATE_TABLE, ALTER, DROP, UNKNOWN
}

class SelectStatementHandler : StatementHandler<Select> {
    override fun canHandle(statement: Statement): Boolean = statement is Select
    
    override fun handle(statement: Select): StatementMetadata {
        val tables = mutableListOf<String>()
        val columns = mutableListOf<String>()
        var hasJoins = false
        var hasSubqueries = false
        
        // Extract information from select body
        val selectBody = statement.selectBody
        if (selectBody is net.sf.jsqlparser.statement.select.PlainSelect) {
            // Extract table names from FROM clause
            selectBody.fromItem?.let { fromItem ->
                tables.add(fromItem.toString())
            }
            
            // Extract join information
            selectBody.joins?.let { joins ->
                hasJoins = joins.isNotEmpty()
                joins.forEach { join ->
                    join.rightItem?.let { rightItem ->
                        tables.add(rightItem.toString())
                    }
                }
            }
            
            // Extract column information
            selectBody.selectItems?.forEach { selectItem ->
                columns.add(selectItem.toString())
            }
        }
        
        // Check for subqueries
        hasSubqueries = statement.toString().contains("SELECT", ignoreCase = true) && 
                       statement.toString().split("SELECT", ignoreCase = true).size > 2
        
        val complexity = calculateComplexity(statement)
        
        return StatementMetadata(
            type = StatementType.SELECT,
            tables = tables,
            columns = columns,
            hasJoins = hasJoins,
            hasSubqueries = hasSubqueries,
            complexity = complexity
        )
    }
    
    private fun calculateComplexity(select: Select): Int {
        var complexity = 1
        
        val selectBody = select.selectBody
        if (selectBody is net.sf.jsqlparser.statement.select.PlainSelect) {
            // Add complexity for joins
            selectBody.joins?.let { complexity += it.size }
            
            // Add complexity for WHERE clause
            if (selectBody.where != null) complexity += 1
            
            // Add complexity for GROUP BY
            if (selectBody.groupBy != null) complexity += 1
            
            // Add complexity for ORDER BY
            if (selectBody.orderByElements != null) complexity += 1
        }
        
        // Add complexity for subqueries
        if (select.toString().contains("SELECT", ignoreCase = true)) {
            complexity += select.toString().split("SELECT", ignoreCase = true).size - 1
        }
        
        return complexity
    }
}

class InsertStatementHandler : StatementHandler<Insert> {
    override fun canHandle(statement: Statement): Boolean = statement is Insert
    
    override fun handle(statement: Insert): StatementMetadata {
        val tables = listOf(statement.table.name)
        val columns = statement.columns?.map { it.columnName } ?: emptyList()
        
        return StatementMetadata(
            type = StatementType.INSERT,
            tables = tables,
            columns = columns,
            hasJoins = false,
            hasSubqueries = statement.select != null,
            complexity = 1
        )
    }
}

class UpdateStatementHandler : StatementHandler<Update> {
    override fun canHandle(statement: Statement): Boolean = statement is Update
    
    override fun handle(statement: Update): StatementMetadata {
        val tables = listOf(statement.table.name)
        val columns = statement.updateSets?.map { it.columns.map { col -> col.columnName } }?.flatten() ?: emptyList()
        
        return StatementMetadata(
            type = StatementType.UPDATE,
            tables = tables,
            columns = columns,
            hasJoins = statement.joins?.isNotEmpty() == true,
            hasSubqueries = false,
            complexity = 1 + (statement.joins?.size ?: 0)
        )
    }
}

class DeleteStatementHandler : StatementHandler<Delete> {
    override fun canHandle(statement: Statement): Boolean = statement is Delete
    
    override fun handle(statement: Delete): StatementMetadata {
        val tables = listOf(statement.table.name)
        
        return StatementMetadata(
            type = StatementType.DELETE,
            tables = tables,
            columns = emptyList(),
            hasJoins = statement.joins?.isNotEmpty() == true,
            hasSubqueries = false,
            complexity = 1 + (statement.joins?.size ?: 0)
        )
    }
}

class CreateTableStatementHandler : StatementHandler<CreateTable> {
    override fun canHandle(statement: Statement): Boolean = statement is CreateTable
    
    override fun handle(statement: CreateTable): StatementMetadata {
        val tables = listOf(statement.table.name)
        val columns = statement.columnDefinitions?.map { it.columnName } ?: emptyList()
        
        return StatementMetadata(
            type = StatementType.CREATE_TABLE,
            tables = tables,
            columns = columns,
            hasJoins = false,
            hasSubqueries = false,
            complexity = 1
        )
    }
}
