package com.sqlswitcher.converter.mapping

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningType

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