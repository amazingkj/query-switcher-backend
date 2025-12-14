package com.sqlswitcher.converter.mapping

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.mapping.dialect.MySqlMappings
import com.sqlswitcher.converter.mapping.dialect.OracleToMySqlMappings
import com.sqlswitcher.converter.mapping.dialect.OracleToPostgreSqlMappings
import com.sqlswitcher.converter.mapping.dialect.PostgreSqlMappings
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

/**
 * 함수 매핑 레지스트리 - 모든 방언 간 함수 변환 규칙 중앙 관리
 *
 * 매핑 규칙은 각 방언별 파일로 분리되어 있습니다:
 * - OracleToMySqlMappings: Oracle → MySQL
 * - OracleToPostgreSqlMappings: Oracle → PostgreSQL
 * - MySqlMappings: MySQL → Oracle, MySQL → PostgreSQL
 * - PostgreSqlMappings: PostgreSQL → Oracle, PostgreSQL → MySQL
 */
@Component
class FunctionMappingRegistry {

    private val mappings = mutableMapOf<String, FunctionMappingRule>()

    @PostConstruct
    fun initialize() {
        // Oracle → MySQL
        OracleToMySqlMappings.getMappings().forEach { register(it) }

        // Oracle → PostgreSQL
        OracleToPostgreSqlMappings.getMappings().forEach { register(it) }

        // MySQL → Oracle
        MySqlMappings.getToOracleMappings().forEach { register(it) }

        // MySQL → PostgreSQL
        MySqlMappings.getToPostgreSqlMappings().forEach { register(it) }

        // PostgreSQL → Oracle
        PostgreSqlMappings.getToOracleMappings().forEach { register(it) }

        // PostgreSQL → MySQL
        PostgreSqlMappings.getToMySqlMappings().forEach { register(it) }
    }

    private fun register(rule: FunctionMappingRule) {
        val key = "${rule.sourceDialect}_${rule.targetDialect}_${rule.sourceFunction.uppercase()}"
        mappings[key] = rule
    }

    fun getMapping(source: DialectType, target: DialectType, functionName: String): FunctionMappingRule? {
        val key = "${source}_${target}_${functionName.uppercase()}"
        return mappings[key]
    }

    fun getMappingsForDialects(source: DialectType, target: DialectType): List<FunctionMappingRule> {
        return mappings.values.filter { it.sourceDialect == source && it.targetDialect == target }
    }

    fun getAllMappings(): Map<String, FunctionMappingRule> = mappings.toMap()
}