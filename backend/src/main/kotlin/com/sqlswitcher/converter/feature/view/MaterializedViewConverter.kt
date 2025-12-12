package com.sqlswitcher.converter.feature.view

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity

/**
 * MATERIALIZED VIEW 변환
 */
object MaterializedViewConverter {

    /**
     * MATERIALIZED VIEW 변환
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return when {
            sourceDialect == DialectType.ORACLE && targetDialect == DialectType.POSTGRESQL -> {
                convertOracleToPostgreSql(sql, warnings, appliedRules)
            }
            sourceDialect == DialectType.ORACLE && targetDialect == DialectType.MYSQL -> {
                convertOracleToMySql(sql, warnings, appliedRules)
            }
            targetDialect == DialectType.ORACLE -> {
                convertToOracle(sql, warnings, appliedRules)
            }
            else -> {
                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "MATERIALIZED VIEW 변환은 수동 검토가 필요합니다.",
                    WarningSeverity.WARNING
                ))
                appliedRules.add("MATERIALIZED VIEW 변환 (수동 검토 필요)")
                sql
            }
        }
    }

    /**
     * Oracle → PostgreSQL 변환
     */
    private fun convertOracleToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            WarningType.PARTIAL_SUPPORT,
            "Oracle MATERIALIZED VIEW를 PostgreSQL로 변환합니다. 새로고침 옵션 등이 다를 수 있습니다.",
            WarningSeverity.WARNING,
            "PostgreSQL에서 REFRESH MATERIALIZED VIEW를 수동으로 실행하거나 pg_cron 등을 사용하세요."
        ))
        appliedRules.add("MATERIALIZED VIEW 변환 (Oracle → PostgreSQL)")

        var result = sql
        // REFRESH 옵션 변환
        result = result.replace(
            Regex("REFRESH\\s+(FAST|COMPLETE|FORCE)\\s+ON\\s+(COMMIT|DEMAND)", RegexOption.IGNORE_CASE),
            "" // PostgreSQL은 수동 REFRESH 필요
        )
        // BUILD IMMEDIATE/DEFERRED 제거
        result = result.replace(
            Regex("BUILD\\s+(IMMEDIATE|DEFERRED)", RegexOption.IGNORE_CASE),
            ""
        )
        return result.trim()
    }

    /**
     * Oracle → MySQL 변환
     */
    private fun convertOracleToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            WarningType.UNSUPPORTED_STATEMENT,
            "MySQL은 MATERIALIZED VIEW를 직접 지원하지 않습니다.",
            WarningSeverity.ERROR,
            "일반 VIEW + 트리거/이벤트로 구현하거나, 별도 테이블로 데이터를 복제하세요."
        ))
        appliedRules.add("MATERIALIZED VIEW 변환 실패 (MySQL 미지원)")
        return "-- MySQL does not support MATERIALIZED VIEW\n-- Original: $sql"
    }

    /**
     * → Oracle 변환
     */
    private fun convertToOracle(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            WarningType.MANUAL_REVIEW_NEEDED,
            "MATERIALIZED VIEW를 Oracle로 변환합니다. 새로고침 로그 등 추가 설정이 필요할 수 있습니다.",
            WarningSeverity.WARNING
        ))
        appliedRules.add("MATERIALIZED VIEW 변환 (→ Oracle)")
        return sql
    }
}