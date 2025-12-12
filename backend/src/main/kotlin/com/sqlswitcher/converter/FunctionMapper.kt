package com.sqlswitcher.converter

import com.sqlswitcher.converter.mapping.FunctionMappingRegistry
import net.sf.jsqlparser.expression.Function as SqlFunction
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
 * 함수 매핑 규칙을 저장하는 레지스트리 (레거시 - mapping.FunctionMappingRegistry 사용)
 * @deprecated Use com.sqlswitcher.converter.mapping.FunctionMappingRegistry instead
 */
@Deprecated("Use com.sqlswitcher.converter.mapping.FunctionMappingRegistry instead")
class FunctionMappingRegistryLegacy {
    
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
 * mapping.FunctionMappingRegistry가 @PostConstruct에서 매핑을 초기화함
 */
@Component
class FunctionMapperImpl(
    private val mappingRegistry: FunctionMappingRegistry
) : FunctionMapper {

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

        // 경고 추가
        if (mappingRule.warningType != null && mappingRule.warningMessage != null) {
            warnings.add(
                ConversionWarning(
                    type = mappingRule.warningType,
                    message = mappingRule.warningMessage,
                    severity = WarningSeverity.WARNING,
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
}

