package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.mapping.DataTypeMappingRegistry
import com.sqlswitcher.converter.mapping.DataTypeMappingRule
import com.sqlswitcher.converter.mapping.PrecisionHandler
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

        val (baseType, precision, scale) = parseDataType(dataType)
        val rule = mappingRegistry.getMapping(sourceDialect, targetDialect, baseType)

        if (rule == null) {
            warnings.add(ConversionWarning(
                type = WarningType.DATA_TYPE_MISMATCH,
                message = "데이터 타입 '$dataType'에 대한 변환 규칙이 없습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "수동으로 변환하거나 대체 타입을 사용하세요."
            ))
            return dataType
        }

        if (rule.warningType != null && rule.warningMessage != null) {
            warnings.add(ConversionWarning(
                type = rule.warningType,
                message = rule.warningMessage,
                severity = WarningSeverity.WARNING
            ))
        }

        val resultType = buildResultType(rule, precision, scale, targetDialect)
        appliedRules.add("$baseType → ${rule.targetType}")

        return resultType
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
        scale: Int?,
        targetDialect: DialectType
    ): String {
        val targetType = rule.targetType

        if (targetType.contains("(")) {
            return targetType
        }

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
                when {
                    precision != null && scale != null -> "$targetType($precision,$scale)"
                    precision != null -> "$targetType($precision)"
                    else -> targetType
                }
            }
            PrecisionHandler.MAP_TO_INTEGER -> {
                convertNumberToInteger(precision, scale, targetDialect)
            }
        }
    }

    /**
     * NUMBER 타입의 정밀도 기반 정수형 매핑
     */
    private fun convertNumberToInteger(
        precision: Int?,
        scale: Int?,
        targetDialect: DialectType
    ): String {
        if (scale != null && scale > 0) {
            return when (targetDialect) {
                DialectType.MYSQL -> if (precision != null) "DECIMAL($precision,$scale)" else "DECIMAL(38,$scale)"
                DialectType.POSTGRESQL -> if (precision != null) "NUMERIC($precision,$scale)" else "NUMERIC"
                else -> if (precision != null) "NUMBER($precision,$scale)" else "NUMBER"
            }
        }

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
}