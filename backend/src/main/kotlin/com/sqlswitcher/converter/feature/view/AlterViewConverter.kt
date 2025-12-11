package com.sqlswitcher.converter.feature.view

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity

/**
 * ALTER VIEW 변환
 */
object AlterViewConverter {

    /**
     * ALTER VIEW 문 변환
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // ALTER VIEW는 데이터베이스마다 지원 범위가 다름
        // 대부분 CREATE OR REPLACE VIEW로 대체 권장

        when (targetDialect) {
            DialectType.MYSQL -> {
                if (sourceDialect != DialectType.MYSQL) {
                    warnings.add(ConversionWarning(
                        WarningType.MANUAL_REVIEW_NEEDED,
                        "ALTER VIEW 구문을 수동으로 검토하세요.",
                        WarningSeverity.WARNING,
                        "MySQL에서 ALTER VIEW는 CREATE OR REPLACE VIEW와 유사하게 동작합니다."
                    ))
                }
            }
            DialectType.POSTGRESQL -> {
                warnings.add(ConversionWarning(
                    WarningType.PARTIAL_SUPPORT,
                    "PostgreSQL의 ALTER VIEW는 제한적입니다. SELECT 변경은 CREATE OR REPLACE VIEW를 사용하세요.",
                    WarningSeverity.WARNING,
                    "PostgreSQL에서 ALTER VIEW는 속성(소유자, 이름, 스키마 등)만 변경 가능합니다."
                ))
            }
            DialectType.ORACLE -> {
                if (sourceDialect != DialectType.ORACLE) {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "Oracle ALTER VIEW는 주로 COMPILE에 사용됩니다. SELECT 변경은 CREATE OR REPLACE VIEW를 사용하세요.",
                        WarningSeverity.INFO
                    ))
                }
            }
        }

        appliedRules.add("ALTER VIEW 변환 (수동 검토 필요)")
        return sql // ALTER VIEW는 복잡하므로 원본 유지하고 경고만 추가
    }
}