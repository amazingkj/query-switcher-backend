package com.sqlswitcher.converter.core

/**
 * SQL 변환 결과를 담는 데이터 클래스
 */
data class ConversionResult(
    val convertedSql: String,
    val warnings: List<ConversionWarning> = emptyList(),
    val appliedRules: List<String> = emptyList(),
    val sourceDialect: DialectType? = null,
    val targetDialect: DialectType? = null
)

/**
 * 변환 경고 정보
 */
data class ConversionWarning(
    val type: WarningType,
    val message: String,
    val severity: WarningSeverity = WarningSeverity.WARNING,
    val suggestion: String? = null,
    val lineNumber: Int? = null,
    val columnNumber: Int? = null
)

/**
 * 경고 타입
 */
enum class WarningType {
    UNSUPPORTED_FUNCTION,      // 지원되지 않는 함수
    SYNTAX_DIFFERENCE,         // 문법 차이
    DATA_TYPE_MISMATCH,        // 데이터 타입 불일치
    PRECISION_LOSS,            // 정밀도 손실 가능
    SEMANTIC_DIFFERENCE,       // 의미론적 차이
    PERFORMANCE_IMPACT,        // 성능 영향
    MANUAL_REVIEW_REQUIRED,    // 수동 검토 필요
    DEPRECATED_FEATURE,        // 사용 중단 예정 기능
    SECURITY_CONCERN,          // 보안 관련 주의사항
    COMPATIBILITY_ISSUE        // 호환성 문제
}

/**
 * 경고 심각도
 */
enum class WarningSeverity {
    INFO,       // 정보
    WARNING,    // 경고
    ERROR,      // 오류
    CRITICAL    // 심각
}

/**
 * 변환 옵션
 */
data class ConversionOptions(
    val preserveComments: Boolean = true,
    val preserveFormatting: Boolean = false,
    val strictMode: Boolean = false,
    val generateMigrationScript: Boolean = false,
    val includeRollback: Boolean = false,
    val targetVersion: String? = null,
    val schemaOwner: String? = null,
    val tableNamePrefix: String? = null,
    val tableNameSuffix: String? = null
)