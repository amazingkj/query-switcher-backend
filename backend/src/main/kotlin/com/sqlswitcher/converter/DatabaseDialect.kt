package com.sqlswitcher.converter

import com.sqlswitcher.parser.model.AstAnalysisResult
import net.sf.jsqlparser.statement.Statement

/**
 * 데이터베이스 방언별 SQL 변환을 위한 공통 인터페이스
 */
interface DatabaseDialect {
    
    /**
     * SQL 쿼리를 현재 방언에서 다른 방언으로 변환
     * @param statement 파싱된 SQL Statement
     * @param targetDialect 변환 대상 방언
     * @param analysisResult AST 분석 결과
     * @return 변환된 SQL 문자열
     */
    fun convertQuery(
        statement: Statement, 
        targetDialect: DialectType, 
        analysisResult: AstAnalysisResult
    ): ConversionResult
    
    /**
     * 현재 방언에서 지원하는 함수 목록 반환
     * @return 지원하는 함수명 Set
     */
    fun getSupportedFunctions(): Set<String>
    
    /**
     * 데이터 타입 매핑 정보 반환
     * @param sourceType 소스 데이터 타입
     * @param targetDialect 변환 대상 방언
     * @return 변환된 데이터 타입
     */
    fun getDataTypeMapping(sourceType: String, targetDialect: DialectType): String
    
    /**
     * 현재 방언의 인용 문자 반환
     * @return 인용 문자 (예: `, ", [])
     */
    fun getQuoteCharacter(): String
    
    /**
     * 현재 방언 타입 반환
     * @return DialectType
     */
    fun getDialectType(): DialectType
    
    /**
     * 변환 가능 여부 확인
     * @param statement 변환할 Statement
     * @param targetDialect 변환 대상 방언
     * @return 변환 가능 여부
     */
    fun canConvert(statement: Statement, targetDialect: DialectType): Boolean
}

/**
 * 데이터베이스 방언 타입 열거형
 */
enum class DialectType {
    MYSQL,
    POSTGRESQL,
    ORACLE
}

/**
 * SQL 변환 결과를 담는 데이터 클래스
 */
data class ConversionResult(
    val convertedSql: String,
    val warnings: List<ConversionWarning> = emptyList(),
    val executionTime: Long = 0,
    val appliedRules: List<String> = emptyList(),
    val metadata: ConversionMetadata? = null,
    val validation: ValidationInfo? = null
)

/**
 * 변환 결과 검증 정보
 */
data class ValidationInfo(
    /** SQL 파싱 유효성 */
    val isValid: Boolean,
    /** 파싱된 문장 유형 */
    val statementType: String? = null,
    /** 프로덕션 사용 준비 여부 */
    val isProductionReady: Boolean = false,
    /** 변환 품질 점수 (0.0 ~ 1.0) */
    val qualityScore: Double = 1.0,
    /** 호환성 이슈 목록 */
    val compatibilityIssues: List<String> = emptyList(),
    /** 권장 사항 */
    val recommendation: String? = null
)

/**
 * 변환 경고 정보
 */
data class ConversionWarning(
    val type: WarningType,
    val message: String,
    val severity: WarningSeverity,
    val suggestion: String? = null,
    val lineNumber: Int? = null,
    val columnNumber: Int? = null
)

/**
 * 경고 타입
 */
enum class WarningType {
    UNSUPPORTED_FUNCTION,
    UNSUPPORTED_STATEMENT,
    PARTIAL_SUPPORT,
    MANUAL_REVIEW_NEEDED,
    PERFORMANCE_WARNING,
    SYNTAX_DIFFERENCE,
    DATA_TYPE_MISMATCH,
    INFO
}

/**
 * 경고 심각도
 */
enum class WarningSeverity {
    INFO,
    WARNING,
    ERROR
}

/**
 * 변환 메타데이터
 */
data class ConversionMetadata(
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val complexityScore: Int = 0,
    val functionCount: Int = 0,
    val tableCount: Int = 0,
    val joinCount: Int = 0,
    val subqueryCount: Int = 0
)
