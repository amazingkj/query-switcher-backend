package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.core.*
import com.sqlswitcher.converter.mapping.*
import org.springframework.stereotype.Service

/**
 * 데이터 타입 변환 서비스 - 모든 방언 간 데이터 타입 변환을 담당
 */
@Service
class DataTypeConversionService(
    private val mappingRegistry: DataTypeMappingRegistry
) {

    /**
     * 데이터 타입 변환
     */
    fun convertDataType(
        dataType: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) {
            return dataType
        }

        // 정밀도 추출
        val (baseType, precision, scale) = parseDataType(dataType)

        // 매핑 규칙 조회
        val rule = mappingRegistry.getMapping(sourceDialect, targetDialect, baseType)

        if (rule == null) {
            // 매핑 규칙이 없으면 원본 반환
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "데이터 타입 '$dataType'에 대한 변환 규칙이 없습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "수동으로 변환하거나 대체 타입을 사용하세요."
            ))
            return dataType
        }

        // 경고 추가
        if (rule.warningType != null && rule.warningMessage != null) {
            warnings.add(ConversionWarning(
                type = rule.warningType,
                message = rule.warningMessage,
                severity = WarningSeverity.WARNING
            ))
        }

        // 결과 타입 생성
        val resultType = buildResultType(rule, precision, scale)
        appliedRules.add("$baseType → ${rule.targetType}")

        return resultType
    }

    /**
     * NUMBER 타입의 정밀도 기반 정수형 매핑
     */
    fun convertNumberToInteger(
        precision: Int?,
        scale: Int?,
        targetDialect: DialectType
    ): String {
        // 소수점이 있으면 DECIMAL 유지
        if (scale != null && scale > 0) {
            return when (targetDialect) {
                DialectType.MYSQL -> if (precision != null) "DECIMAL($precision,$scale)" else "DECIMAL(38,$scale)"
                DialectType.POSTGRESQL -> if (precision != null) "NUMERIC($precision,$scale)" else "NUMERIC"
                else -> if (precision != null) "NUMBER($precision,$scale)" else "NUMBER"
            }
        }

        // 정수형 매핑
        return when (targetDialect) {
            DialectType.MYSQL -> when {
                precision == null -> "DECIMAL"
                precision <= 3 -> "TINYINT"
                precision <= 5 -> "SMALLINT"
                precision <= 7 -> "MEDIUMINT"
                precision <= 10 -> "INT"
                precision <= 19 -> "BIGINT"
                else -> "DECIMAL($precision)"
            }
            DialectType.POSTGRESQL -> when {
                precision == null -> "NUMERIC"
                precision <= 5 -> "SMALLINT"
                precision <= 10 -> "INTEGER"
                precision <= 19 -> "BIGINT"
                else -> "NUMERIC($precision)"
            }
            else -> if (precision != null) "NUMBER($precision)" else "NUMBER"
        }
    }

    /**
     * 데이터 타입 파싱 (타입명, 정밀도, 스케일 분리)
     */
    private fun parseDataType(dataType: String): Triple<String, Int?, Int?> {
        val match = Regex("^([A-Za-z0-9_ ]+)(?:\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\))?$").find(dataType.trim())

        return if (match != null) {
            Triple(
                match.groupValues[1].trim().uppercase(),
                match.groupValues[2].toIntOrNull(),
                match.groupValues[3].toIntOrNull()
            )
        } else {
            Triple(dataType.trim().uppercase(), null, null)
        }
    }

    /**
     * 결과 타입 생성 (정밀도 포함)
     */
    private fun buildResultType(
        rule: DataTypeMappingRule,
        precision: Int?,
        scale: Int?
    ): String {
        val targetType = rule.targetType

        // 이미 정밀도가 포함된 타입이면 그대로 반환
        if (targetType.contains("(")) {
            return targetType
        }

        // 정밀도 처리
        return when (rule.precisionHandler) {
            PrecisionHandler.PRESERVE -> {
                when {
                    precision != null && scale != null -> "$targetType($precision,$scale)"
                    precision != null -> "$targetType($precision)"
                    else -> targetType
                }
            }
            PrecisionHandler.DROP -> targetType
            PrecisionHandler.CONVERT -> {
                // 특별한 변환 로직이 필요한 경우
                when {
                    precision != null && scale != null -> "$targetType($precision,$scale)"
                    precision != null -> "$targetType($precision)"
                    else -> targetType
                }
            }
            PrecisionHandler.MAP_TO_INTEGER -> {
                // NUMBER를 정수형으로 매핑할 때
                convertNumberToInteger(precision, scale, rule.targetDialect)
            }
        }
    }

    /**
     * 컬럼 정의 전체 변환 (데이터 타입 + 제약조건)
     */
    fun convertColumnDefinition(
        columnDef: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // 간단한 컬럼 정의 파싱: column_name TYPE [(precision)] [constraints]
        val parts = columnDef.trim().split(Regex("\\s+"), limit = 2)
        if (parts.size < 2) return columnDef

        val columnName = parts[0]
        val rest = parts[1]

        // 타입과 제약조건 분리
        val typeMatch = Regex("^([A-Za-z0-9_]+(?:\\s*\\([^)]+\\))?)(.*)$").find(rest)
        if (typeMatch == null) return columnDef

        val dataType = typeMatch.groupValues[1].trim()
        val constraints = typeMatch.groupValues[2].trim()

        // 타입 변환
        val convertedType = convertDataType(dataType, sourceDialect, targetDialect, warnings, appliedRules)

        // 제약조건 변환
        val convertedConstraints = convertConstraints(constraints, sourceDialect, targetDialect, warnings, appliedRules)

        // 인용문자 변환
        val quotedColumnName = quoteIdentifier(columnName, targetDialect)

        return if (convertedConstraints.isNotEmpty()) {
            "$quotedColumnName $convertedType $convertedConstraints"
        } else {
            "$quotedColumnName $convertedType"
        }
    }

    /**
     * 식별자 인용
     */
    private fun quoteIdentifier(identifier: String, targetDialect: DialectType): String {
        val cleaned = identifier.trim('"', '`', '[', ']')
        return when (targetDialect) {
            DialectType.MYSQL -> "`$cleaned`"
            DialectType.ORACLE, DialectType.POSTGRESQL, DialectType.TIBERO -> "\"$cleaned\""
        }
    }

    /**
     * 제약조건 변환
     */
    private fun convertConstraints(
        constraints: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = constraints

        // DEFAULT 값 변환
        result = convertDefaultValue(result, sourceDialect, targetDialect, warnings, appliedRules)

        // AUTO_INCREMENT / GENERATED / SERIAL 변환
        result = convertAutoIncrement(result, sourceDialect, targetDialect, warnings, appliedRules)

        return result
    }

    /**
     * DEFAULT 값 변환
     */
    private fun convertDefaultValue(
        constraints: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = constraints

        when {
            // Oracle/Tibero → MySQL/PostgreSQL
            (sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.TIBERO) -> {
                when (targetDialect) {
                    DialectType.MYSQL -> {
                        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                        result = result.replace(Regex("\\bSYSTIMESTAMP\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                        result = result.replace(Regex("\\bSYS_GUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "UUID()")
                    }
                    DialectType.POSTGRESQL -> {
                        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                        result = result.replace(Regex("\\bSYSTIMESTAMP\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                        result = result.replace(Regex("\\bSYS_GUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "gen_random_uuid()")
                    }
                    else -> {}
                }
            }
            // MySQL → Oracle/Tibero
            sourceDialect == DialectType.MYSQL -> {
                when (targetDialect) {
                    DialectType.ORACLE, DialectType.TIBERO -> {
                        result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSDATE")
                        result = result.replace(Regex("\\bCURRENT_TIMESTAMP\\b", RegexOption.IGNORE_CASE), "SYSTIMESTAMP")
                        result = result.replace(Regex("\\bUUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYS_GUID()")
                    }
                    DialectType.POSTGRESQL -> {
                        result = result.replace(Regex("\\bUUID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "gen_random_uuid()")
                    }
                    else -> {}
                }
            }
        }

        return result
    }

    /**
     * AUTO_INCREMENT 변환
     */
    private fun convertAutoIncrement(
        constraints: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = constraints

        when {
            // MySQL AUTO_INCREMENT → Oracle GENERATED / PostgreSQL SERIAL
            sourceDialect == DialectType.MYSQL && constraints.contains(Regex("AUTO_INCREMENT", RegexOption.IGNORE_CASE)) -> {
                when (targetDialect) {
                    DialectType.ORACLE, DialectType.TIBERO -> {
                        result = result.replace(Regex("\\bAUTO_INCREMENT\\b", RegexOption.IGNORE_CASE), "GENERATED BY DEFAULT AS IDENTITY")
                        appliedRules.add("AUTO_INCREMENT → GENERATED BY DEFAULT AS IDENTITY")
                    }
                    DialectType.POSTGRESQL -> {
                        warnings.add(ConversionWarning(
                            type = WarningType.SYNTAX_DIFFERENCE,
                            message = "AUTO_INCREMENT는 SERIAL 또는 GENERATED AS IDENTITY로 변환됩니다.",
                            severity = WarningSeverity.INFO,
                            suggestion = "컬럼 타입을 SERIAL로 변경하거나 GENERATED AS IDENTITY를 사용하세요."
                        ))
                        result = result.replace(Regex("\\bAUTO_INCREMENT\\b", RegexOption.IGNORE_CASE), "GENERATED BY DEFAULT AS IDENTITY")
                        appliedRules.add("AUTO_INCREMENT → GENERATED BY DEFAULT AS IDENTITY")
                    }
                    else -> {}
                }
            }

            // Oracle GENERATED → MySQL AUTO_INCREMENT
            (sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.TIBERO) &&
            constraints.contains(Regex("GENERATED", RegexOption.IGNORE_CASE)) -> {
                when (targetDialect) {
                    DialectType.MYSQL -> {
                        result = result.replace(Regex("GENERATED\\s+(?:BY\\s+DEFAULT\\s+|ALWAYS\\s+)?AS\\s+IDENTITY(?:\\s*\\([^)]*\\))?", RegexOption.IGNORE_CASE), "AUTO_INCREMENT")
                        appliedRules.add("GENERATED AS IDENTITY → AUTO_INCREMENT")
                    }
                    DialectType.POSTGRESQL -> {
                        // PostgreSQL도 GENERATED AS IDENTITY 지원
                    }
                    else -> {}
                }
            }
        }

        return result
    }
}