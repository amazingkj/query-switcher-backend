package com.sqlswitcher.converter.mapping

import com.sqlswitcher.converter.core.*
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

/**
 * 함수 매핑 규칙
 */
data class FunctionMappingRule(
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val sourceFunction: String,
    val targetFunction: String,
    val parameterTransform: ParameterTransform? = null,
    val warningType: WarningType? = null,
    val warningMessage: String? = null,
    val suggestion: String? = null,
    val requiresManualReview: Boolean = false
)

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
 * 함수 매핑 레지스트리 - 모든 방언 간 함수 변환 규칙 중앙 관리
 */
@Component
class FunctionMappingRegistry {

    private val mappings = mutableMapOf<String, FunctionMappingRule>()

    @PostConstruct
    fun initialize() {
        // Oracle → MySQL
        registerOracleToMySqlMappings()
        // Oracle → PostgreSQL
        registerOracleToPostgreSqlMappings()
        // MySQL → Oracle
        registerMySqlToOracleMappings()
        // MySQL → PostgreSQL
        registerMySqlToPostgreSqlMappings()
        // PostgreSQL → Oracle
        registerPostgreSqlToOracleMappings()
        // PostgreSQL → MySQL
        registerPostgreSqlToMySqlMappings()
        // Tibero (Oracle과 호환)
        registerTiberoMappings()
    }

    private fun registerOracleToMySqlMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "SYSDATE", "NOW()"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "SYSTIMESTAMP", "CURRENT_TIMESTAMP"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "NVL", "IFNULL"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "NVL2", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN,
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "NVL2는 CASE WHEN으로 변환됩니다",
                suggestion = "CASE WHEN expr IS NOT NULL THEN val1 ELSE val2 END"
            ),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TO_CHAR", "DATE_FORMAT",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TO_DATE", "STR_TO_DATE",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LISTAGG", "GROUP_CONCAT"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "SUBSTR", "SUBSTRING"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "INSTR", "LOCATE",
                parameterTransform = ParameterTransform.SWAP_FIRST_TWO),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LENGTH", "CHAR_LENGTH"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TRUNC", "TRUNCATE"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.MYSQL, "DECODE", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN,
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "DECODE는 CASE WHEN으로 변환됩니다"
            ),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "SYS_GUID", "UUID",
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "SYS_GUID()는 UUID()로 변환됩니다 (포맷이 다릅니다)"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "DBMS_RANDOM.VALUE", "RAND"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "REGEXP_LIKE", "REGEXP"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "MONTHS_BETWEEN", "TIMESTAMPDIFF"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.MYSQL, "ADD_MONTHS", "DATE_ADD")
        )
        rules.forEach { register(it) }
    }

    private fun registerOracleToPostgreSqlMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "SYSDATE", "CURRENT_TIMESTAMP"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "SYSTIMESTAMP", "CURRENT_TIMESTAMP"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "NVL", "COALESCE"),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.POSTGRESQL, "NVL2", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN,
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "NVL2는 CASE WHEN으로 변환됩니다"
            ),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TO_CHAR", "TO_CHAR"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TO_DATE", "TO_TIMESTAMP",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "LISTAGG", "STRING_AGG"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "INSTR", "POSITION",
                parameterTransform = ParameterTransform.SWAP_FIRST_TWO),
            FunctionMappingRule(
                DialectType.ORACLE, DialectType.POSTGRESQL, "DECODE", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN
            ),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "SYS_GUID", "gen_random_uuid"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "DBMS_RANDOM.VALUE", "RANDOM"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "REGEXP_LIKE", "~"),
            FunctionMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "REGEXP_SUBSTR", "REGEXP_MATCHES")
        )
        rules.forEach { register(it) }
    }

    private fun registerMySqlToOracleMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "NOW", "SYSDATE"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "CURRENT_TIMESTAMP", "SYSTIMESTAMP"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "IFNULL", "NVL"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "DATE_FORMAT", "TO_CHAR",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "STR_TO_DATE", "TO_DATE",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "GROUP_CONCAT", "LISTAGG"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "SUBSTRING", "SUBSTR"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "LOCATE", "INSTR",
                parameterTransform = ParameterTransform.SWAP_FIRST_TWO),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "CHAR_LENGTH", "LENGTH"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "UUID", "SYS_GUID"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "RAND", "DBMS_RANDOM.VALUE"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TRUNCATE", "TRUNC"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TIMESTAMPDIFF", "MONTHS_BETWEEN"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "DATE_ADD", "ADD_MONTHS"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.ORACLE, "IF", "DECODE",
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "IF 함수는 DECODE 또는 CASE WHEN으로 변환됩니다")
        )
        rules.forEach { register(it) }
    }

    private fun registerMySqlToPostgreSqlMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "NOW", "NOW"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "IFNULL", "COALESCE"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DATE_FORMAT", "TO_CHAR",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT),
            FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "STR_TO_DATE", "TO_TIMESTAMP",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT),
            FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "GROUP_CONCAT", "STRING_AGG"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LOCATE", "POSITION",
                parameterTransform = ParameterTransform.SWAP_FIRST_TWO),
            FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "UUID", "gen_random_uuid"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "RAND", "RANDOM"),
            FunctionMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "IF", "CASE_WHEN",
                parameterTransform = ParameterTransform.TO_CASE_WHEN)
        )
        rules.forEach { register(it) }
    }

    private fun registerPostgreSqlToOracleMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "NOW", "SYSDATE"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "CURRENT_TIMESTAMP", "SYSTIMESTAMP"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "COALESCE", "NVL"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "TO_TIMESTAMP", "TO_DATE"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "STRING_AGG", "LISTAGG"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "gen_random_uuid", "SYS_GUID"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "RANDOM", "DBMS_RANDOM.VALUE")
        )
        rules.forEach { register(it) }
    }

    private fun registerPostgreSqlToMySqlMappings() {
        val rules = listOf(
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "COALESCE", "IFNULL"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "TO_CHAR", "DATE_FORMAT",
                parameterTransform = ParameterTransform.DATE_FORMAT_CONVERT),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "TO_TIMESTAMP", "STR_TO_DATE"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "STRING_AGG", "GROUP_CONCAT"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "gen_random_uuid", "UUID"),
            FunctionMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "RANDOM", "RAND")
        )
        rules.forEach { register(it) }
    }

    private fun registerTiberoMappings() {
        // Tibero → MySQL (Oracle과 동일)
        getMappingsForDialects(DialectType.ORACLE, DialectType.MYSQL).forEach { rule ->
            register(rule.copy(sourceDialect = DialectType.TIBERO))
        }
        // Tibero → PostgreSQL (Oracle과 동일)
        getMappingsForDialects(DialectType.ORACLE, DialectType.POSTGRESQL).forEach { rule ->
            register(rule.copy(sourceDialect = DialectType.TIBERO))
        }
        // MySQL → Tibero (MySQL → Oracle과 동일)
        getMappingsForDialects(DialectType.MYSQL, DialectType.ORACLE).forEach { rule ->
            register(rule.copy(targetDialect = DialectType.TIBERO))
        }
        // PostgreSQL → Tibero (PostgreSQL → Oracle과 동일)
        getMappingsForDialects(DialectType.POSTGRESQL, DialectType.ORACLE).forEach { rule ->
            register(rule.copy(targetDialect = DialectType.TIBERO))
        }
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