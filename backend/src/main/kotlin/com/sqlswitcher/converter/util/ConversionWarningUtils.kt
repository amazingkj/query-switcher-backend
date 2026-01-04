package com.sqlswitcher.converter.util

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * 변환 경고 메시지 생성 유틸리티
 *
 * 일관된 경고 메시지와 제안사항을 제공합니다.
 */
object ConversionWarningUtils {

    /**
     * 지원되지 않는 함수에 대한 경고 생성
     */
    fun unsupportedFunction(
        functionName: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        suggestion: String? = null
    ): ConversionWarning = ConversionWarning(
        type = WarningType.UNSUPPORTED_FUNCTION,
        message = "$functionName 함수는 ${targetDialect.displayName}에서 직접 지원되지 않습니다.",
        severity = WarningSeverity.WARNING,
        suggestion = suggestion ?: getDefaultSuggestion(functionName, targetDialect)
    )

    /**
     * 부분 지원 함수에 대한 경고 생성
     */
    fun partialSupport(
        functionName: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        limitationDescription: String
    ): ConversionWarning = ConversionWarning(
        type = WarningType.PARTIAL_SUPPORT,
        message = "$functionName 함수가 ${targetDialect.displayName}로 변환되었지만, $limitationDescription",
        severity = WarningSeverity.INFO,
        suggestion = "변환된 쿼리의 결과가 원본과 동일한지 테스트해주세요."
    )

    /**
     * 수동 검토가 필요한 경고 생성
     */
    fun manualReviewNeeded(
        feature: String,
        reason: String
    ): ConversionWarning = ConversionWarning(
        type = WarningType.MANUAL_REVIEW_NEEDED,
        message = "$feature: $reason",
        severity = WarningSeverity.WARNING,
        suggestion = "수동으로 코드를 검토하고 필요한 경우 수정해주세요."
    )

    /**
     * 성능 관련 경고 생성
     */
    fun performanceWarning(
        feature: String,
        description: String,
        suggestion: String
    ): ConversionWarning = ConversionWarning(
        type = WarningType.PERFORMANCE_WARNING,
        message = "$feature: $description",
        severity = WarningSeverity.WARNING,
        suggestion = suggestion
    )

    /**
     * 구문 차이에 대한 경고 생성
     */
    fun syntaxDifference(
        feature: String,
        description: String
    ): ConversionWarning = ConversionWarning(
        type = WarningType.SYNTAX_DIFFERENCE,
        message = "$feature: $description",
        severity = WarningSeverity.INFO,
        suggestion = "변환된 구문이 올바르게 동작하는지 확인해주세요."
    )

    /**
     * 데이터 타입 불일치 경고 생성
     */
    fun dataTypeMismatch(
        sourceType: String,
        targetType: String,
        description: String
    ): ConversionWarning = ConversionWarning(
        type = WarningType.DATA_TYPE_MISMATCH,
        message = "데이터 타입 '$sourceType' → '$targetType': $description",
        severity = WarningSeverity.WARNING,
        suggestion = "데이터 손실이 발생할 수 있으니 확인이 필요합니다."
    )

    /**
     * 미지원 기능 모음 (상세 정보 포함)
     */
    object UnsupportedFeatures {

        // CUBE/ROLLUP 관련
        val cubeInMySql = ConversionWarning(
            type = WarningType.UNSUPPORTED_FUNCTION,
            message = "CUBE 절은 MySQL에서 직접 지원되지 않습니다.",
            severity = WarningSeverity.ERROR,
            suggestion = "여러 GROUP BY 쿼리를 UNION ALL로 결합하거나, 애플리케이션 레벨에서 처리해주세요."
        )

        val groupingSetsInMySql = ConversionWarning(
            type = WarningType.UNSUPPORTED_FUNCTION,
            message = "GROUPING SETS 절은 MySQL에서 직접 지원되지 않습니다.",
            severity = WarningSeverity.ERROR,
            suggestion = "각 그룹핑 조합에 대해 별도의 쿼리를 UNION ALL로 결합해주세요."
        )

        // 시퀀스 관련
        val sequenceInMySql = ConversionWarning(
            type = WarningType.UNSUPPORTED_FUNCTION,
            message = "시퀀스(SEQUENCE)는 MySQL에서 기본적으로 지원되지 않습니다.",
            severity = WarningSeverity.ERROR,
            suggestion = "AUTO_INCREMENT 컬럼을 사용하거나, 별도의 시퀀스 테이블을 생성해주세요."
        )

        // 계층적 쿼리 관련
        val connectByInMySql = ConversionWarning(
            type = WarningType.UNSUPPORTED_FUNCTION,
            message = "CONNECT BY 계층적 쿼리는 MySQL에서 지원되지 않습니다.",
            severity = WarningSeverity.ERROR,
            suggestion = "MySQL 8.0 이상에서 WITH RECURSIVE (CTE)를 사용해주세요."
        )

        val connectByInPostgreSql = ConversionWarning(
            type = WarningType.UNSUPPORTED_FUNCTION,
            message = "CONNECT BY 계층적 쿼리는 PostgreSQL에서 지원되지 않습니다.",
            severity = WarningSeverity.ERROR,
            suggestion = "WITH RECURSIVE (CTE)를 사용하여 재귀 쿼리로 변환해주세요."
        )

        // RETURNING 절 관련
        val returningInMySqlLegacy = ConversionWarning(
            type = WarningType.UNSUPPORTED_FUNCTION,
            message = "RETURNING 절은 MySQL 5.x에서 지원되지 않습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "INSERT 후 LAST_INSERT_ID()를 사용하거나, MySQL 8.0 이상으로 업그레이드해주세요."
        )

        // TRANSLATE 함수 관련
        val translateInMySql = ConversionWarning(
            type = WarningType.PARTIAL_SUPPORT,
            message = "TRANSLATE 함수는 MySQL에서 직접 지원되지 않습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "중첩된 REPLACE() 함수로 대체되었습니다. 복잡한 변환의 경우 애플리케이션 레벨에서 처리해주세요."
        )

        // 힌트 관련
        val hintsRemoved = ConversionWarning(
            type = WarningType.INFO,
            message = "Oracle 힌트(/*+ ... */)가 제거되었습니다.",
            severity = WarningSeverity.INFO,
            suggestion = "대상 데이터베이스에 적합한 힌트나 인덱스 최적화를 고려해주세요."
        )

        // Full-Text 검색 관련
        fun fullTextSearch(sourceDialect: DialectType, targetDialect: DialectType) = ConversionWarning(
            type = WarningType.PARTIAL_SUPPORT,
            message = "Full-Text 검색이 ${sourceDialect.displayName}에서 ${targetDialect.displayName}로 변환되었습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "Full-Text 인덱스 설정이 필요합니다. 검색 결과와 성능이 다를 수 있습니다."
        )

        // 암호화 함수 관련
        val cryptoFunctionExtensionNeeded = ConversionWarning(
            type = WarningType.PARTIAL_SUPPORT,
            message = "암호화 함수가 변환되었습니다.",
            severity = WarningSeverity.INFO,
            suggestion = "PostgreSQL에서는 pgcrypto 확장이 필요합니다: CREATE EXTENSION IF NOT EXISTS pgcrypto;"
        )

        // ROWNUM 관련
        val rownumApproximation = ConversionWarning(
            type = WarningType.PARTIAL_SUPPORT,
            message = "ROWNUM이 근사적으로 변환되었습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "MySQL에서는 사용자 변수 초기화가 필요합니다: SET @rownum := 0; 또는 ROW_NUMBER() 윈도우 함수 사용을 권장합니다."
        )
    }

    /**
     * 함수별 기본 제안사항
     */
    private fun getDefaultSuggestion(functionName: String, targetDialect: DialectType): String {
        return when (functionName.uppercase()) {
            "DECODE" -> "CASE WHEN 구문으로 변환하세요."
            "NVL" -> "${if (targetDialect == DialectType.MYSQL) "IFNULL" else "COALESCE"} 함수를 사용하세요."
            "NVL2" -> "CASE WHEN expr IS NOT NULL THEN ... ELSE ... END 구문을 사용하세요."
            "LISTAGG" -> "${if (targetDialect == DialectType.MYSQL) "GROUP_CONCAT" else "STRING_AGG"} 함수를 사용하세요."
            "ROWNUM" -> "${if (targetDialect == DialectType.MYSQL) "LIMIT 절 또는 @rownum 변수" else "ROW_NUMBER() OVER ()"} 를 사용하세요."
            "CONNECT BY" -> "WITH RECURSIVE (CTE)를 사용하여 재귀 쿼리로 변환하세요."
            "SYSDATE" -> "${if (targetDialect == DialectType.MYSQL) "NOW()" else "CURRENT_TIMESTAMP"} 를 사용하세요."
            "REGEXP_COUNT" -> "LENGTH()와 REPLACE() 조합 또는 정규식 함수를 사용하세요."
            else -> "대상 데이터베이스의 문서를 참조하여 대체 함수를 확인하세요."
        }
    }

    /**
     * DialectType 확장: 표시 이름
     */
    private val DialectType.displayName: String
        get() = when (this) {
            DialectType.ORACLE -> "Oracle"
            DialectType.MYSQL -> "MySQL"
            DialectType.POSTGRESQL -> "PostgreSQL"
        }
}
