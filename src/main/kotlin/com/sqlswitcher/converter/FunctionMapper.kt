package com.sqlswitcher.converter

import net.sf.jsqlparser.expression.Function as SqlFunction
import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.expression.operators.relational.ExpressionList
import net.sf.jsqlparser.expression.StringValue
import org.springframework.stereotype.Component

/**
 * 데이터베이스 간 함수명 및 시그니처 변환을 위한 매핑 시스템
 */
interface FunctionMapper {
    
    /**
     * 함수 변환
     * @param sourceDialect 소스 방언
     * @param targetDialect 타겟 방언
     * @param function 변환할 함수
     * @return 변환된 함수
     */
    fun mapFunction(
        sourceDialect: DialectType,
        targetDialect: DialectType,
        function: SqlFunction
    ): FunctionMappingResult
}

/**
 * 함수 매핑 결과
 */
data class FunctionMappingResult(
    val mappedFunction: SqlFunction,
    val warnings: List<ConversionWarning> = emptyList(),
    val appliedRules: List<String> = emptyList()
)

/**
 * 함수 매핑 규칙을 저장하는 레지스트리
 */
@Component
class FunctionMappingRegistry {
    
    private val functionMappings = mutableMapOf<String, FunctionMappingRule>()
    
    /**
     * 함수 매핑 규칙 등록
     */
    fun registerMapping(rule: FunctionMappingRule) {
        val key = "${rule.sourceDialect}_${rule.targetDialect}_${rule.sourceFunction}"
        functionMappings[key] = rule
    }
    
    /**
     * 함수 매핑 규칙 조회
     */
    fun getMapping(
        sourceDialect: DialectType,
        targetDialect: DialectType,
        sourceFunction: String
    ): FunctionMappingRule? {
        val key = "${sourceDialect}_${targetDialect}_${sourceFunction.uppercase()}"
        return functionMappings[key]
    }
    
    /**
     * 모든 매핑 규칙 조회
     */
    fun getAllMappings(): Map<String, FunctionMappingRule> = functionMappings.toMap()
    
    /**
     * 특정 방언 조합의 모든 매핑 규칙 조회
     */
    fun getMappingsForDialects(
        sourceDialect: DialectType,
        targetDialect: DialectType
    ): List<FunctionMappingRule> {
        return functionMappings.values.filter { 
            it.sourceDialect == sourceDialect && it.targetDialect == targetDialect 
        }
    }
}

/**
 * 함수 매핑 규칙
 */
data class FunctionMappingRule(
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val sourceFunction: String,
    val targetFunction: String,
    val parameterMappings: List<ParameterMapping> = emptyList(),
    val warningType: WarningType? = null,
    val warningMessage: String? = null,
    val suggestion: String? = null,
    val isPartialSupport: Boolean = false
)

/**
 * 파라미터 매핑 규칙
 */
data class ParameterMapping(
    val sourceIndex: Int,
    val targetIndex: Int,
    val transformation: ParameterTransformation? = null
)

/**
 * 파라미터 변환 타입
 */
enum class ParameterTransformation {
    FORMAT_STRING,      // 포맷 문자열 변환 (예: DATE_FORMAT → TO_CHAR)
    CONCAT_OPERATOR,    // CONCAT → || 연산자
    ARRAY_TO_STRING,    // 배열을 문자열로 변환
    CUSTOM              // 사용자 정의 변환
}

/**
 * 함수 매핑 시스템 구현체
 */
@Component
class FunctionMapperImpl(
    private val mappingRegistry: FunctionMappingRegistry
) : FunctionMapper {
    
    init {
        initializeDefaultMappings()
    }
    
    override fun mapFunction(
        sourceDialect: DialectType,
        targetDialect: DialectType,
        function: SqlFunction
    ): FunctionMappingResult {
        val functionName = function.name?.uppercase() ?: ""
        val mappingRule = mappingRegistry.getMapping(sourceDialect, targetDialect, functionName)
        
        if (mappingRule == null) {
            // 매핑 규칙이 없는 경우 원본 함수 반환
            return FunctionMappingResult(
                mappedFunction = function,
                warnings = listOf(
                    ConversionWarning(
                        type = WarningType.UNSUPPORTED_FUNCTION,
                        message = "함수 '$functionName'에 대한 변환 규칙이 없습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "수동으로 변환하거나 대체 함수를 사용하세요."
                    )
                )
            )
        }
        
        val mappedFunction = SqlFunction()
        val warnings = mutableListOf<ConversionWarning>()
        val appliedRules = mutableListOf<String>()
        
        // 함수명 변환
        mappedFunction.name = mappingRule.targetFunction
        mappedFunction.parameters = function.parameters
        appliedRules.add("${mappingRule.sourceFunction}() → ${mappingRule.targetFunction}()")
        
        // 파라미터 변환
        if (mappingRule.parameterMappings.isNotEmpty()) {
            transformParameters(mappedFunction, mappingRule, warnings, appliedRules)
        }
        
        // 경고 추가
        if (mappingRule.warningType != null && mappingRule.warningMessage != null) {
            warnings.add(
                ConversionWarning(
                    type = mappingRule.warningType,
                    message = mappingRule.warningMessage,
                    severity = if (mappingRule.isPartialSupport) WarningSeverity.WARNING else WarningSeverity.INFO,
                    suggestion = mappingRule.suggestion
                )
            )
        }
        
        return FunctionMappingResult(
            mappedFunction = mappedFunction,
            warnings = warnings,
            appliedRules = appliedRules
        )
    }
    
    /**
     * 파라미터 변환
     */
    private fun transformParameters(
        function: SqlFunction,
        mappingRule: FunctionMappingRule,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ) {
        val expressions = function.parameters?.expressions
        if (expressions == null || expressions.isEmpty()) return

        // 새로운 ExpressionList를 생성하여 변환된 파라미터들 저장
        val newExpressionList = ExpressionList<Expression>()
        val newExpressions = mutableListOf<Expression>()

        // 원래 파라미터들을 복사
        newExpressions.addAll(expressions)

        mappingRule.parameterMappings.forEach { paramMapping ->
            val sourceIndex = paramMapping.sourceIndex
            val targetIndex = paramMapping.targetIndex

            if (sourceIndex < expressions.size) {
                val sourceParam = expressions[sourceIndex]
                val transformedParam = transformParameter(sourceParam, paramMapping.transformation)

                if (targetIndex < newExpressions.size) {
                    newExpressions[targetIndex] = transformedParam
                } else {
                    // 새로운 파라미터 추가
                    newExpressions.add(transformedParam)
                }

                appliedRules.add("파라미터 ${sourceIndex + 1} 변환")
            }
        }

        // 변환된 파라미터들로 함수 업데이트
        newExpressionList.addAll(newExpressions)
        function.parameters = newExpressionList
    }
    
    /**
     * 개별 파라미터 변환
     */
    private fun transformParameter(
        parameter: Expression,
        transformation: ParameterTransformation?
    ): Expression {
        return when (transformation) {
            ParameterTransformation.FORMAT_STRING -> {
                // 포맷 문자열 변환 (예: MySQL DATE_FORMAT → Oracle TO_CHAR)
                transformFormatString(parameter)
            }
            ParameterTransformation.CONCAT_OPERATOR -> {
                // CONCAT 함수를 || 연산자로 변환
                transformConcatOperator(parameter)
            }
            ParameterTransformation.ARRAY_TO_STRING -> {
                // 배열을 문자열로 변환
                transformArrayToString(parameter)
            }
            ParameterTransformation.CUSTOM -> {
                // 사용자 정의 변환
                parameter
            }
            null -> parameter
        }
    }
    
    /**
     * 포맷 문자열 변환
     */
    private fun transformFormatString(parameter: Expression): Expression {
        // MySQL DATE_FORMAT 포맷을 Oracle TO_CHAR 포맷으로 변환
        if (parameter is StringValue) {
            val mysqlFormat = parameter.value
            val oracleFormat = convertDateFormat(mysqlFormat)
            return StringValue("'$oracleFormat'")
        }
        return parameter
    }
    
    /**
     * CONCAT 연산자 변환
     */
    private fun transformConcatOperator(parameter: Expression): Expression {
        // CONCAT 함수를 || 연산자로 변환하는 로직
        // 실제 구현에서는 더 복잡한 변환이 필요할 수 있음
        return parameter
    }
    
    /**
     * 배열을 문자열로 변환
     */
    private fun transformArrayToString(parameter: Expression): Expression {
        // 배열을 문자열로 변환하는 로직
        return parameter
    }
    
    /**
     * MySQL DATE_FORMAT을 Oracle TO_CHAR 포맷으로 변환
     */
    private fun convertDateFormat(mysqlFormat: String): String {
        return mysqlFormat
            .replace("%Y", "YYYY")
            .replace("%m", "MM")
            .replace("%d", "DD")
            .replace("%H", "HH24")
            .replace("%i", "MI")
            .replace("%s", "SS")
            .replace("%w", "D")
            .replace("%W", "DAY")
            .replace("%M", "MONTH")
    }
    
    /**
     * 기본 매핑 규칙 초기화
     */
    private fun initializeDefaultMappings() {
        // MySQL → PostgreSQL 매핑
        registerMySqlToPostgreSqlMappings()
        
        // MySQL → Oracle 매핑
        registerMySqlToOracleMappings()
        
        // MySQL → Tibero 매핑
        registerMySqlToTiberoMappings()
        
        // PostgreSQL → MySQL 매핑
        registerPostgreSqlToMySqlMappings()
        
        // PostgreSQL → Oracle 매핑
        registerPostgreSqlToOracleMappings()
        
        // PostgreSQL → Tibero 매핑
        registerPostgreSqlToTiberoMappings()
        
        // Oracle → MySQL 매핑
        registerOracleToMySqlMappings()
        
        // Oracle → PostgreSQL 매핑
        registerOracleToPostgreSqlMappings()
        
        // Oracle → Tibero 매핑
        registerOracleToTiberoMappings()
        
        // Tibero → MySQL 매핑
        registerTiberoToMySqlMappings()
        
        // Tibero → PostgreSQL 매핑
        registerTiberoToPostgreSqlMappings()
        
        // Tibero → Oracle 매핑
        registerTiberoToOracleMappings()
    }
    
    private fun registerMySqlToPostgreSqlMappings() {
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                sourceFunction = "NOW",
                targetFunction = "CURRENT_TIMESTAMP"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                sourceFunction = "IFNULL",
                targetFunction = "COALESCE"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                sourceFunction = "DATE_FORMAT",
                targetFunction = "TO_CHAR",
                parameterMappings = listOf(
                    ParameterMapping(0, 0), // date parameter
                    ParameterMapping(1, 1, ParameterTransformation.FORMAT_STRING) // format parameter
                ),
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "MySQL DATE_FORMAT() 함수를 PostgreSQL TO_CHAR()로 변환했습니다.",
                suggestion = "포맷 문자열을 확인하고 필요시 수정하세요."
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                sourceFunction = "GROUP_CONCAT",
                targetFunction = "STRING_AGG"
            )
        )
    }
    
    private fun registerMySqlToOracleMappings() {
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE,
                sourceFunction = "NOW",
                targetFunction = "SYSDATE"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE,
                sourceFunction = "IFNULL",
                targetFunction = "NVL"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE,
                sourceFunction = "DATE_FORMAT",
                targetFunction = "TO_CHAR",
                parameterMappings = listOf(
                    ParameterMapping(0, 0), // date parameter
                    ParameterMapping(1, 1, ParameterTransformation.FORMAT_STRING) // format parameter
                )
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE,
                sourceFunction = "GROUP_CONCAT",
                targetFunction = "LISTAGG"
            )
        )
    }
    
    private fun registerMySqlToTiberoMappings() {
        // Tibero는 Oracle과 유사하므로 동일한 매핑 사용
        registerMySqlToOracleMappings()
    }
    
    private fun registerPostgreSqlToMySqlMappings() {
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL,
                sourceFunction = "CURRENT_TIMESTAMP",
                targetFunction = "NOW"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL,
                sourceFunction = "TO_CHAR",
                targetFunction = "DATE_FORMAT",
                parameterMappings = listOf(
                    ParameterMapping(0, 0), // date parameter
                    ParameterMapping(1, 1, ParameterTransformation.FORMAT_STRING) // format parameter
                ),
                warningType = WarningType.SYNTAX_DIFFERENCE,
                warningMessage = "PostgreSQL TO_CHAR() 함수를 MySQL DATE_FORMAT()으로 변환했습니다.",
                suggestion = "포맷 문자열을 확인하고 필요시 수정하세요."
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL,
                sourceFunction = "STRING_AGG",
                targetFunction = "GROUP_CONCAT"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL,
                sourceFunction = "ARRAY_AGG",
                targetFunction = "GROUP_CONCAT",
                isPartialSupport = true,
                warningType = WarningType.PARTIAL_SUPPORT,
                warningMessage = "PostgreSQL ARRAY_AGG() 함수를 MySQL GROUP_CONCAT()으로 변환했습니다.",
                suggestion = "배열 기능이 제한될 수 있습니다."
            )
        )
    }
    
    private fun registerPostgreSqlToOracleMappings() {
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.ORACLE,
                sourceFunction = "CURRENT_TIMESTAMP",
                targetFunction = "SYSDATE"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.ORACLE,
                sourceFunction = "STRING_AGG",
                targetFunction = "LISTAGG"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.ORACLE,
                sourceFunction = "ARRAY_AGG",
                targetFunction = "LISTAGG",
                isPartialSupport = true,
                warningType = WarningType.PARTIAL_SUPPORT,
                warningMessage = "PostgreSQL ARRAY_AGG() 함수를 Oracle LISTAGG()으로 변환했습니다.",
                suggestion = "배열 기능이 제한될 수 있습니다."
            )
        )
    }
    
    private fun registerPostgreSqlToTiberoMappings() {
        // Tibero는 Oracle과 유사하므로 동일한 매핑 사용
        registerPostgreSqlToOracleMappings()
    }
    
    private fun registerOracleToMySqlMappings() {
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                sourceFunction = "SYSDATE",
                targetFunction = "NOW"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                sourceFunction = "NVL",
                targetFunction = "IFNULL"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                sourceFunction = "TO_CHAR",
                targetFunction = "DATE_FORMAT",
                parameterMappings = listOf(
                    ParameterMapping(0, 0), // date parameter
                    ParameterMapping(1, 1, ParameterTransformation.FORMAT_STRING) // format parameter
                )
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                sourceFunction = "LISTAGG",
                targetFunction = "GROUP_CONCAT"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                sourceFunction = "NVL2",
                targetFunction = "CASE",
                isPartialSupport = true,
                warningType = WarningType.UNSUPPORTED_FUNCTION,
                warningMessage = "Oracle NVL2() 함수는 MySQL에서 지원되지 않습니다.",
                suggestion = "CASE WHEN 구문을 사용하세요."
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                sourceFunction = "DECODE",
                targetFunction = "CASE",
                isPartialSupport = true,
                warningType = WarningType.UNSUPPORTED_FUNCTION,
                warningMessage = "Oracle DECODE() 함수는 MySQL에서 지원되지 않습니다.",
                suggestion = "CASE WHEN 구문을 사용하세요."
            )
        )
    }
    
    private fun registerOracleToPostgreSqlMappings() {
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL,
                sourceFunction = "SYSDATE",
                targetFunction = "CURRENT_TIMESTAMP"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL,
                sourceFunction = "NVL",
                targetFunction = "COALESCE"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL,
                sourceFunction = "LISTAGG",
                targetFunction = "STRING_AGG"
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL,
                sourceFunction = "NVL2",
                targetFunction = "CASE",
                isPartialSupport = true,
                warningType = WarningType.UNSUPPORTED_FUNCTION,
                warningMessage = "Oracle NVL2() 함수는 PostgreSQL에서 지원되지 않습니다.",
                suggestion = "CASE WHEN 구문을 사용하세요."
            )
        )
        
        mappingRegistry.registerMapping(
            FunctionMappingRule(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.POSTGRESQL,
                sourceFunction = "DECODE",
                targetFunction = "CASE",
                isPartialSupport = true,
                warningType = WarningType.UNSUPPORTED_FUNCTION,
                warningMessage = "Oracle DECODE() 함수는 PostgreSQL에서 지원되지 않습니다.",
                suggestion = "CASE WHEN 구문을 사용하세요."
            )
        )
    }
    
    private fun registerOracleToTiberoMappings() {
        // Oracle과 Tibero는 대부분 동일한 함수를 사용하므로 매핑이 거의 필요 없음
    }
    
    private fun registerTiberoToMySqlMappings() {
        // Tibero는 Oracle과 유사하므로 동일한 매핑 사용
        registerOracleToMySqlMappings()
    }
    
    private fun registerTiberoToPostgreSqlMappings() {
        // Tibero는 Oracle과 유사하므로 동일한 매핑 사용
        registerOracleToPostgreSqlMappings()
    }
    
    private fun registerTiberoToOracleMappings() {
        // Tibero와 Oracle은 대부분 동일한 함수를 사용하므로 매핑이 거의 필요 없음
    }
}

