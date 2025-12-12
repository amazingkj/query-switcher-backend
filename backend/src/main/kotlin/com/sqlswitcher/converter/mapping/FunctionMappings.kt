package com.sqlswitcher.converter.mapping

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningType
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

/**
 * 파라미터 변환 방식
 */
enum class ParameterTransform {
    NONE,                    // 변환 없음
    SWAP_FIRST_TWO,         // 첫 두 파라미터 교환
    DATE_FORMAT_CONVERT,    // 날짜 포맷 변환
    TO_CASE_WHEN,           // CASE WHEN으로 변환
    WRAP_WITH_FUNCTION      // 다른 함수로 감싸기
}

/**
 * 함수 매핑 규칙
 */
data class FunctionMappingRule(
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val sourceFunction: String,
    val targetFunction: String,
    val parameterTransform: ParameterTransform = ParameterTransform.NONE,
    val warningType: WarningType? = null,
    val warningMessage: String? = null,
    val suggestion: String? = null
)

/**
 * 함수 매핑 레지스트리 - 모든 방언 간 함수 변환 규칙 중앙 관리
 */
@Component
class FunctionMappingRegistry {

    private val mappings = mutableMapOf<String, FunctionMappingRule>()

    @PostConstruct
    fun initialize() {
        registerOracleToMySqlMappings()
        registerOracleToPostgreSqlMappings()
        registerMySqlToOracleMappings()
        registerMySqlToPostgreSqlMappings()
        registerPostgreSqlToOracleMappings()
        registerPostgreSqlToMySqlMappings()
    }

    private fun registerOracleToMySqlMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "NVL", "IFNULL"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "NVL2", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN,
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "NVL2는 CASE WHEN으로 변환됩니다"
            ),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "DECODE", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN,
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "DECODE는 CASE WHEN으로 변환됩니다"
            ),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "TO_CHAR", "DATE_FORMAT",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
            ),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "TO_DATE", "STR_TO_DATE",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
            ),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LISTAGG", "GROUP_CONCAT"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "SUBSTR", "SUBSTRING"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "INSTR", "LOCATE",
                parameterTransform = ParameterTransform.SWAP_FIRST_TWO
            ),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LENGTH", "CHAR_LENGTH"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TRUNC", "TRUNCATE")
        )
        rules.forEach { register(it) }
    }

    private fun registerOracleToPostgreSqlMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "NVL", "COALESCE"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.POSTGRESQL, "NVL2", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN
            ),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.POSTGRESQL, "DECODE", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN
            ),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TO_CHAR", "TO_CHAR"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.POSTGRESQL, "TO_DATE", "TO_TIMESTAMP",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
            ),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "LISTAGG", "STRING_AGG"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.POSTGRESQL, "INSTR", "POSITION",
                parameterTransform = ParameterTransform.SWAP_FIRST_TWO
            )
        )
        rules.forEach { register(it) }
    }

    private fun registerMySqlToOracleMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "IFNULL", "NVL"),
            FunctionMappingRule(
                DialectType.MYSQL, DialectType.ORACLE, "DATE_FORMAT", "TO_CHAR",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
            ),
            FunctionMappingRule(
                DialectType.MYSQL, DialectType.ORACLE, "STR_TO_DATE", "TO_DATE",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
            ),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "GROUP_CONCAT", "LISTAGG"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "SUBSTRING", "SUBSTR"),
            FunctionMappingRule(
                DialectType.MYSQL, DialectType.ORACLE, "LOCATE", "INSTR",
                parameterTransform = ParameterTransform.SWAP_FIRST_TWO
            ),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "CHAR_LENGTH", "LENGTH"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TRUNCATE", "TRUNC"),
            FunctionMappingRule(
                DialectType.MYSQL, DialectType.ORACLE, "IF", "DECODE",
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "IF 함수는 DECODE 또는 CASE WHEN으로 변환됩니다"
            )
        )
        rules.forEach { register(it) }
    }

    private fun registerMySqlToPostgreSqlMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "IFNULL", "COALESCE"),
            FunctionMappingRule(
                DialectType.MYSQL, DialectType.POSTGRESQL, "DATE_FORMAT", "TO_CHAR",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
            ),
            FunctionMappingRule(
                DialectType.MYSQL, DialectType.POSTGRESQL, "STR_TO_DATE", "TO_TIMESTAMP",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
            ),
            FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "GROUP_CONCAT", "STRING_AGG"),
            FunctionMappingRule(
                DialectType.MYSQL, DialectType.POSTGRESQL, "LOCATE", "POSITION",
                parameterTransform = ParameterTransform.SWAP_FIRST_TWO
            ),
            FunctionMappingRule(
                DialectType.MYSQL, DialectType.POSTGRESQL, "IF", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN
            )
        )
        rules.forEach { register(it) }
    }

    private fun registerPostgreSqlToOracleMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "COALESCE", "NVL"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "TO_TIMESTAMP", "TO_DATE"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "STRING_AGG", "LISTAGG")
        )
        rules.forEach { register(it) }
    }

    private fun registerPostgreSqlToMySqlMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "COALESCE", "IFNULL"),
            FunctionMappingRule(
                DialectType.POSTGRESQL, DialectType.MYSQL, "TO_CHAR", "DATE_FORMAT",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT
            ),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "TO_TIMESTAMP", "STR_TO_DATE"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "STRING_AGG", "GROUP_CONCAT")
        )
        rules.forEach { register(it) }
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