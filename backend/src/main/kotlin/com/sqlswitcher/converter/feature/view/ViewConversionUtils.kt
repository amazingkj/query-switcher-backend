package com.sqlswitcher.converter.feature.view

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity

/**
 * VIEW 변환 공통 유틸리티
 */
object ViewConversionUtils {

    /**
     * VIEW 이름 변환 (인용문자 처리)
     */
    fun convertViewName(name: String, targetDialect: DialectType): String {
        val cleaned = name.trim('"', '`', '[', ']')
        return when (targetDialect) {
            DialectType.MYSQL -> "`$cleaned`"
            else -> "\"$cleaned\""
        }
    }

    /**
     * VIEW 헤더 구성
     */
    fun buildViewHeader(
        createOrReplace: String,
        forceOption: String,
        viewName: String,
        columnList: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()

        // CREATE OR REPLACE 처리
        val hasOrReplace = createOrReplace.contains(Regex("OR\\s+REPLACE", RegexOption.IGNORE_CASE))
        when (targetDialect) {
            DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE -> {
                // 모두 OR REPLACE 지원
                sb.append(if (hasOrReplace) "CREATE OR REPLACE" else "CREATE")
            }
        }

        // FORCE 옵션 처리 (Oracle 전용)
        if (forceOption.isNotEmpty()) {
            if (sourceDialect == DialectType.ORACLE && targetDialect != DialectType.ORACLE) {
                warnings.add(ConversionWarning(
                    WarningType.SYNTAX_DIFFERENCE,
                    "Oracle FORCE/NOFORCE 옵션이 제거되었습니다.",
                    WarningSeverity.INFO,
                    "FORCE 옵션은 Oracle에서만 지원됩니다."
                ))
                appliedRules.add("FORCE/NOFORCE 옵션 제거")
            } else if (targetDialect == DialectType.ORACLE) {
                sb.append(" $forceOption")
            }
        }

        sb.append(" VIEW ")

        // VIEW 이름 변환 (인용문자 처리)
        sb.append(convertViewName(viewName, targetDialect))

        // 컬럼 목록
        if (columnList.isNotEmpty()) {
            sb.append(" $columnList")
        }

        return sb.toString()
    }

    /**
     * VIEW 옵션 변환 (WITH CHECK OPTION, WITH READ ONLY)
     */
    fun convertViewOptions(
        option: String?,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (option == null) return ""

        val upperOption = option.uppercase()

        return when {
            upperOption.contains("CHECK OPTION") -> {
                when (targetDialect) {
                    DialectType.MYSQL -> {
                        appliedRules.add("WITH CHECK OPTION 유지 (MySQL)")
                        "WITH CHECK OPTION"
                    }
                    DialectType.POSTGRESQL -> {
                        appliedRules.add("WITH CHECK OPTION 유지 (PostgreSQL)")
                        "WITH CHECK OPTION"
                    }
                    DialectType.ORACLE -> {
                        appliedRules.add("WITH CHECK OPTION 유지 (Oracle)")
                        "WITH CHECK OPTION"
                    }
                }
            }
            upperOption.contains("READ ONLY") -> {
                when (targetDialect) {
                    DialectType.ORACLE -> {
                        appliedRules.add("WITH READ ONLY 유지 (Oracle)")
                        "WITH READ ONLY"
                    }
                    DialectType.POSTGRESQL -> {
                        warnings.add(ConversionWarning(
                            WarningType.PARTIAL_SUPPORT,
                            "WITH READ ONLY가 제거되었습니다. PostgreSQL에서는 RULE 또는 TRIGGER를 사용하세요.",
                            WarningSeverity.WARNING,
                            "PostgreSQL에서 읽기 전용 뷰는 INSERT/UPDATE/DELETE RULE을 사용합니다."
                        ))
                        appliedRules.add("WITH READ ONLY 제거 (PostgreSQL 미지원)")
                        ""
                    }
                    DialectType.MYSQL -> {
                        warnings.add(ConversionWarning(
                            WarningType.PARTIAL_SUPPORT,
                            "WITH READ ONLY가 제거되었습니다. MySQL에서는 직접 지원하지 않습니다.",
                            WarningSeverity.WARNING,
                            "MySQL에서는 GRANT 권한으로 읽기 전용 접근을 구현할 수 있습니다."
                        ))
                        appliedRules.add("WITH READ ONLY 제거 (MySQL 미지원)")
                        ""
                    }
                }
            }
            else -> ""
        }
    }
}