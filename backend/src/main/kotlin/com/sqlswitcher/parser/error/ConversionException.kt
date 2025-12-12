package com.sqlswitcher.parser.error

/**
 * SQL 변환 관련 예외 클래스
 * 변환 중 발생하는 다양한 에러 상황을 구체적으로 처리
 */
class ConversionException(
    message: String,
    val errorCode: ConversionErrorCode,
    val sourceDialect: String,
    val targetDialect: String,
    val originalSql: String? = null,
    val problematicPart: String? = null,
    val suggestions: List<String> = emptyList(),
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 변환 에러 코드 정의
 */
enum class ConversionErrorCode(
    val code: String,
    val titleKo: String,
    val titleEn: String
) {
    // 데이터타입 변환 관련
    UNSUPPORTED_DATATYPE(
        "CONV_001",
        "지원되지 않는 데이터타입",
        "Unsupported data type"
    ),
    DATATYPE_PRECISION_LOSS(
        "CONV_002",
        "데이터타입 정밀도 손실 가능성",
        "Potential data type precision loss"
    ),

    // 함수 변환 관련
    UNSUPPORTED_FUNCTION(
        "CONV_010",
        "지원되지 않는 함수",
        "Unsupported function"
    ),
    FUNCTION_PARAMETER_MISMATCH(
        "CONV_011",
        "함수 매개변수 불일치",
        "Function parameter mismatch"
    ),
    FUNCTION_REQUIRES_MANUAL_CONVERSION(
        "CONV_012",
        "수동 변환 필요 함수",
        "Function requires manual conversion"
    ),

    // DDL 변환 관련
    UNSUPPORTED_DDL_OPTION(
        "CONV_020",
        "지원되지 않는 DDL 옵션",
        "Unsupported DDL option"
    ),
    SEQUENCE_CONVERSION_REQUIRED(
        "CONV_021",
        "시퀀스 변환 필요",
        "Sequence conversion required"
    ),
    TRIGGER_CONVERSION_REQUIRED(
        "CONV_022",
        "트리거 변환 필요",
        "Trigger conversion required"
    ),
    PARTITION_CONVERSION_REQUIRED(
        "CONV_023",
        "파티션 변환 필요",
        "Partition conversion required"
    ),

    // 구문 관련
    HIERARCHICAL_QUERY_NOT_SUPPORTED(
        "CONV_030",
        "계층적 쿼리 미지원",
        "Hierarchical query not supported"
    ),
    PACKAGE_FUNCTION_NOT_SUPPORTED(
        "CONV_031",
        "패키지 함수 미지원",
        "Package function not supported"
    ),
    HINT_NOT_CONVERTED(
        "CONV_032",
        "힌트 변환 불가",
        "Hint cannot be converted"
    ),

    // 기타
    EMPTY_SQL(
        "CONV_100",
        "빈 SQL 입력",
        "Empty SQL input"
    ),
    INVALID_DIALECT(
        "CONV_101",
        "잘못된 데이터베이스 방언",
        "Invalid database dialect"
    ),
    SAME_DIALECT(
        "CONV_102",
        "동일한 방언으로 변환 시도",
        "Conversion to same dialect"
    ),
    GENERAL_ERROR(
        "CONV_999",
        "일반 변환 오류",
        "General conversion error"
    )
}

/**
 * 변환 에러 메시지 빌더
 */
object ConversionErrorMessageBuilder {

    /**
     * 사용자 친화적인 에러 메시지 생성
     */
    fun buildUserFriendlyMessage(exception: ConversionException): ConversionErrorMessage {
        val title = exception.errorCode.titleKo
        val description = buildDescription(exception)
        val suggestions = buildSuggestions(exception)
        val technicalDetails = buildTechnicalDetails(exception)

        return ConversionErrorMessage(
            code = exception.errorCode.code,
            title = title,
            description = description,
            suggestions = suggestions,
            technicalDetails = technicalDetails
        )
    }

    private fun buildDescription(exception: ConversionException): String {
        return when (exception.errorCode) {
            ConversionErrorCode.UNSUPPORTED_DATATYPE -> {
                val part = exception.problematicPart?.let { "'$it'" } ?: "해당 데이터타입"
                "$part 은(는) ${exception.targetDialect}에서 직접 지원되지 않습니다."
            }
            ConversionErrorCode.DATATYPE_PRECISION_LOSS -> {
                "${exception.sourceDialect}에서 ${exception.targetDialect}로 변환 시 정밀도가 손실될 수 있습니다."
            }
            ConversionErrorCode.UNSUPPORTED_FUNCTION -> {
                val part = exception.problematicPart?.let { "'$it'" } ?: "해당 함수"
                "$part 은(는) ${exception.targetDialect}에서 동일한 함수로 변환할 수 없습니다."
            }
            ConversionErrorCode.FUNCTION_REQUIRES_MANUAL_CONVERSION -> {
                val part = exception.problematicPart?.let { "'$it'" } ?: "해당 함수"
                "$part 은(는) ${exception.targetDialect}에서 다른 방식으로 구현해야 합니다."
            }
            ConversionErrorCode.HIERARCHICAL_QUERY_NOT_SUPPORTED -> {
                "CONNECT BY, START WITH 같은 계층 쿼리는 ${exception.targetDialect}에서 WITH RECURSIVE CTE로 변환해야 합니다."
            }
            ConversionErrorCode.PACKAGE_FUNCTION_NOT_SUPPORTED -> {
                val part = exception.problematicPart?.let { "'$it'" } ?: "Oracle 패키지 함수"
                "$part 은(는) ${exception.targetDialect}에서 대체 구현이 필요합니다."
            }
            ConversionErrorCode.TRIGGER_CONVERSION_REQUIRED -> {
                "트리거 문법은 데이터베이스별로 크게 다르므로 수동 변환이 필요합니다."
            }
            ConversionErrorCode.PARTITION_CONVERSION_REQUIRED -> {
                "파티션 테이블 문법은 데이터베이스별로 다르므로 수동 조정이 필요합니다."
            }
            ConversionErrorCode.SEQUENCE_CONVERSION_REQUIRED -> {
                "${exception.targetDialect}에서는 시퀀스 대신 AUTO_INCREMENT나 SERIAL을 사용할 수 있습니다."
            }
            ConversionErrorCode.HINT_NOT_CONVERTED -> {
                "데이터베이스별 힌트 문법이 다르므로 힌트가 제거되었습니다."
            }
            ConversionErrorCode.EMPTY_SQL -> {
                "SQL 입력이 비어있습니다. 변환할 SQL을 입력해주세요."
            }
            ConversionErrorCode.INVALID_DIALECT -> {
                "지원하지 않는 데이터베이스 방언입니다. Oracle, MySQL, PostgreSQL을 지원합니다."
            }
            ConversionErrorCode.SAME_DIALECT -> {
                "동일한 데이터베이스 방언으로의 변환은 불필요합니다."
            }
            else -> exception.message ?: "변환 중 오류가 발생했습니다."
        }
    }

    private fun buildSuggestions(exception: ConversionException): List<String> {
        if (exception.suggestions.isNotEmpty()) {
            return exception.suggestions
        }

        return when (exception.errorCode) {
            ConversionErrorCode.UNSUPPORTED_DATATYPE -> listOf(
                "대체 가능한 데이터타입을 확인하세요",
                "SQL_CONVERSION_RULES.md 문서를 참고하세요",
                "필요시 수동으로 데이터타입을 조정하세요"
            )
            ConversionErrorCode.UNSUPPORTED_FUNCTION -> listOf(
                "${exception.targetDialect}에서 동등한 함수를 찾아보세요",
                "CASE WHEN 문으로 대체할 수 있는지 확인하세요",
                "애플리케이션 레벨에서 처리를 고려하세요"
            )
            ConversionErrorCode.HIERARCHICAL_QUERY_NOT_SUPPORTED -> listOf(
                "WITH RECURSIVE 절을 사용한 CTE로 변환하세요",
                "PostgreSQL: https://www.postgresql.org/docs/current/queries-with.html",
                "MySQL 8.0+: https://dev.mysql.com/doc/refman/8.0/en/with.html"
            )
            ConversionErrorCode.TRIGGER_CONVERSION_REQUIRED -> listOf(
                "트리거 로직을 대상 데이터베이스 문법에 맞게 재작성하세요",
                "PostgreSQL은 트리거 함수를 별도로 생성해야 합니다",
                "MySQL은 DELIMITER 설정이 필요할 수 있습니다"
            )
            ConversionErrorCode.PARTITION_CONVERSION_REQUIRED -> listOf(
                "대상 데이터베이스의 파티션 문법을 확인하세요",
                "파티션 키와 범위를 수동으로 조정하세요",
                "PostgreSQL은 상속 테이블 방식도 고려하세요"
            )
            ConversionErrorCode.PACKAGE_FUNCTION_NOT_SUPPORTED -> listOf(
                "DBMS_RANDOM.VALUE → RAND() (MySQL) 또는 RANDOM() (PostgreSQL)",
                "DBMS_OUTPUT.PUT_LINE → SELECT 또는 RAISE NOTICE 사용",
                "UTL_FILE.* → 애플리케이션 레벨에서 파일 처리"
            )
            else -> listOf(
                "SQL 문법을 다시 확인하세요",
                "변환 규칙 문서를 참고하세요",
                "문제가 지속되면 수동으로 조정하세요"
            )
        }
    }

    private fun buildTechnicalDetails(exception: ConversionException): Map<String, Any?> {
        return mapOf(
            "errorCode" to exception.errorCode.code,
            "sourceDialect" to exception.sourceDialect,
            "targetDialect" to exception.targetDialect,
            "problematicPart" to exception.problematicPart,
            "originalMessage" to exception.message
        )
    }
}

/**
 * 구조화된 에러 메시지 응답
 */
data class ConversionErrorMessage(
    val code: String,
    val title: String,
    val description: String,
    val suggestions: List<String>,
    val technicalDetails: Map<String, Any?>
)