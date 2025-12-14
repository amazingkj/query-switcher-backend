package com.sqlswitcher.converter.feature.view

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity

/**
 * VIEW 변환 공통 유틸리티
 *
 * 지원 기능:
 * - VIEW 이름 인용문자 변환 (", `, [])
 * - TEMPORARY/TEMP VIEW 변환
 * - SECURITY DEFINER/INVOKER 변환
 * - FORCE/NOFORCE 옵션 변환
 * - WITH CHECK OPTION / WITH READ ONLY 변환
 * - 컬럼 별칭 처리
 */
object ViewConversionUtils {

    /**
     * VIEW 이름 변환 (인용문자 처리)
     * - MySQL: 백틱 (`)
     * - PostgreSQL/Oracle: 큰따옴표 (")
     * - 특수문자나 예약어가 없으면 인용문자 없이 반환
     */
    fun convertViewName(name: String, targetDialect: DialectType): String {
        val cleaned = name.trim('"', '`', '[', ']')

        // 예약어나 특수문자가 포함된 경우만 인용문자 사용
        val needsQuoting = cleaned.contains(Regex("[^a-zA-Z0-9_]")) ||
                           isReservedWord(cleaned, targetDialect)

        return when {
            !needsQuoting -> cleaned
            targetDialect == DialectType.MYSQL -> "`$cleaned`"
            else -> "\"$cleaned\""
        }
    }

    /**
     * 예약어 확인
     */
    private fun isReservedWord(word: String, dialect: DialectType): Boolean {
        val commonReserved = setOf(
            "SELECT", "FROM", "WHERE", "ORDER", "GROUP", "BY", "TABLE", "VIEW",
            "INDEX", "CREATE", "DROP", "ALTER", "INSERT", "UPDATE", "DELETE",
            "USER", "PASSWORD", "DATABASE", "SCHEMA", "KEY", "PRIMARY", "FOREIGN"
        )
        return word.uppercase() in commonReserved
    }

    /**
     * VIEW 헤더 구성 (확장 버전)
     */
    fun buildViewHeader(
        createOrReplace: String,
        forceOption: String,
        viewName: String,
        columnList: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        isTemporary: Boolean = false,
        securityOption: String? = null
    ): String {
        val sb = StringBuilder()

        // CREATE OR REPLACE 처리
        val hasOrReplace = createOrReplace.contains(Regex("OR\\s+REPLACE", RegexOption.IGNORE_CASE))
        sb.append(if (hasOrReplace) "CREATE OR REPLACE" else "CREATE")

        // TEMPORARY VIEW 처리
        if (isTemporary) {
            when (targetDialect) {
                DialectType.POSTGRESQL -> {
                    sb.append(" TEMPORARY")
                    appliedRules.add("TEMPORARY VIEW 변환 (PostgreSQL)")
                }
                DialectType.MYSQL -> {
                    // MySQL은 TEMPORARY VIEW를 직접 지원하지 않음
                    warnings.add(ConversionWarning(
                        WarningType.PARTIAL_SUPPORT,
                        "MySQL은 TEMPORARY VIEW를 지원하지 않습니다.",
                        WarningSeverity.WARNING,
                        "일반 VIEW로 생성되며, 세션 종료 후에도 유지됩니다."
                    ))
                    appliedRules.add("TEMPORARY VIEW → VIEW 변환 (MySQL 미지원)")
                }
                DialectType.ORACLE -> {
                    // Oracle도 직접적인 TEMPORARY VIEW는 없음
                    warnings.add(ConversionWarning(
                        WarningType.PARTIAL_SUPPORT,
                        "Oracle은 TEMPORARY VIEW를 직접 지원하지 않습니다.",
                        WarningSeverity.WARNING,
                        "GLOBAL TEMPORARY TABLE을 사용하거나 일반 VIEW로 생성하세요."
                    ))
                    appliedRules.add("TEMPORARY VIEW → VIEW 변환 (Oracle)")
                }
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

        // SECURITY 옵션 처리 (PostgreSQL 전용)
        if (securityOption != null) {
            val upperSecurity = securityOption.uppercase()
            when (targetDialect) {
                DialectType.POSTGRESQL -> {
                    // PostgreSQL은 SECURITY DEFINER/INVOKER 지원
                    if (upperSecurity.contains("DEFINER") || upperSecurity.contains("INVOKER")) {
                        // WITH 절에서 처리
                    }
                }
                DialectType.MYSQL -> {
                    if (upperSecurity.contains("DEFINER")) {
                        // MySQL은 DEFINER 절로 처리
                        warnings.add(ConversionWarning(
                            WarningType.SYNTAX_DIFFERENCE,
                            "MySQL에서 SECURITY DEFINER는 DEFINER 절로 처리됩니다.",
                            WarningSeverity.INFO
                        ))
                    }
                }
                DialectType.ORACLE -> {
                    if (upperSecurity.contains("DEFINER") || upperSecurity.contains("INVOKER")) {
                        warnings.add(ConversionWarning(
                            WarningType.SYNTAX_DIFFERENCE,
                            "Oracle VIEW는 기본적으로 DEFINER 권한으로 실행됩니다.",
                            WarningSeverity.INFO,
                            "INVOKER 권한이 필요하면 AUTHID CURRENT_USER 함수를 고려하세요."
                        ))
                    }
                }
            }
        }

        return sb.toString()
    }

    /**
     * VIEW 헤더 구성 (하위 호환성 유지)
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
        return buildViewHeader(
            createOrReplace, forceOption, viewName, columnList,
            sourceDialect, targetDialect, warnings, appliedRules,
            isTemporary = false, securityOption = null
        )
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