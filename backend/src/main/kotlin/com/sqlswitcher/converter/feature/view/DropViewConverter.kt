package com.sqlswitcher.converter.feature.view

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity

/**
 * DROP VIEW 변환
 */
object DropViewConverter {

    /**
     * DROP VIEW 문 변환
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = sql.trim()

        // DROP VIEW [IF EXISTS] view_name [CASCADE|RESTRICT]
        val dropPattern = Regex(
            """DROP\s+VIEW\s+(IF\s+EXISTS\s+)?(\S+)(\s+CASCADE|\s+RESTRICT)?""",
            RegexOption.IGNORE_CASE
        )

        val match = dropPattern.find(result) ?: return result

        val ifExists = match.groupValues[1].isNotEmpty()
        val viewName = match.groupValues[2]
        val cascadeOption = match.groupValues[3].trim().uppercase()

        val sb = StringBuilder("DROP VIEW ")

        // IF EXISTS 처리
        when (targetDialect) {
            DialectType.MYSQL, DialectType.POSTGRESQL -> {
                if (ifExists) sb.append("IF EXISTS ")
            }
            DialectType.ORACLE -> {
                // Oracle 23c 이전에는 IF EXISTS 미지원
                if (ifExists) {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "Oracle 23c 이전 버전에서는 IF EXISTS를 지원하지 않습니다.",
                        WarningSeverity.WARNING,
                        "PL/SQL 블록 또는 예외 처리를 사용하세요."
                    ))
                }
            }
        }

        // VIEW 이름
        sb.append(ViewConversionUtils.convertViewName(viewName, targetDialect))

        // CASCADE/RESTRICT 처리
        if (cascadeOption.isNotEmpty()) {
            when (targetDialect) {
                DialectType.POSTGRESQL -> {
                    sb.append(" $cascadeOption")
                    appliedRules.add("CASCADE/RESTRICT 유지 (PostgreSQL)")
                }
                DialectType.ORACLE -> {
                    if (cascadeOption == "CASCADE") {
                        sb.append(" CASCADE CONSTRAINTS")
                        appliedRules.add("CASCADE → CASCADE CONSTRAINTS (Oracle)")
                    }
                }
                DialectType.MYSQL -> {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "MySQL에서 CASCADE/RESTRICT는 무시됩니다.",
                        WarningSeverity.INFO
                    ))
                    appliedRules.add("CASCADE/RESTRICT 제거 (MySQL)")
                }
            }
        }

        appliedRules.add("DROP VIEW 변환: ${sourceDialect.name} → ${targetDialect.name}")
        return sb.toString()
    }
}