package com.sqlswitcher.parser

import net.sf.jsqlparser.statement.Statement
import org.springframework.stereotype.Component

@Component
class StatementHandlerFactory {
    
    private val handlers = listOf(
        SelectStatementHandler(),
        InsertStatementHandler(),
        UpdateStatementHandler(),
        DeleteStatementHandler(),
        CreateTableStatementHandler()
    )
    
    fun getHandler(statement: Statement): StatementHandler<*>? {
        return handlers.find { it.canHandle(statement) }
    }
    
    fun getStatementMetadata(statement: Statement): StatementMetadata {
        val handler = getHandler(statement)
        return if (handler != null) {
            @Suppress("UNCHECKED_CAST")
            (handler as StatementHandler<Statement>).handle(statement)
        } else {
            StatementMetadata(
                type = StatementType.UNKNOWN,
                tables = emptyList(),
                columns = emptyList(),
                hasJoins = false,
                hasSubqueries = false,
                complexity = 1
            )
        }
    }
}
