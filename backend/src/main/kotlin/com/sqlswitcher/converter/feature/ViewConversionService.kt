package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.feature.view.ViewConversionUtils
import com.sqlswitcher.converter.feature.view.DropViewConverter
import com.sqlswitcher.converter.feature.view.AlterViewConverter
import com.sqlswitcher.converter.feature.view.MaterializedViewConverter
import org.springframework.stereotype.Service

/**
 * VIEW 변환 서비스
 * CREATE VIEW, DROP VIEW, ALTER VIEW 등을 데이터베이스 방언에 맞게 변환
 *
 * 지원 기능:
 * - CREATE [OR REPLACE] [TEMPORARY] VIEW 변환
 * - FORCE/NOFORCE 옵션 (Oracle)
 * - WITH CHECK OPTION / WITH READ ONLY 옵션
 * - SECURITY DEFINER/INVOKER (PostgreSQL)
 * - DEFINER 절 (MySQL)
 * - RECURSIVE VIEW (CTE 기반)
 * - MATERIALIZED VIEW
 *
 * 실제 변환 로직은 각 컨버터 클래스에 위임:
 * - ViewConversionUtils: 공통 유틸리티 (헤더 구성, 이름 변환, 옵션 변환)
 * - DropViewConverter: DROP VIEW 변환
 * - AlterViewConverter: ALTER VIEW 변환
 * - MaterializedViewConverter: MATERIALIZED VIEW 변환
 */
@Service
class ViewConversionService(
    private val selectService: SelectConversionService,
    private val functionService: FunctionConversionService
) {

    // 확장된 VIEW 헤더 패턴
    // CREATE [OR REPLACE] [ALGORITHM=...] [DEFINER=...] [SQL SECURITY ...] [TEMPORARY|TEMP] [RECURSIVE] [FORCE|NOFORCE] VIEW name
    private val EXTENDED_VIEW_HEADER_PATTERN = Regex(
        """(CREATE\s+(?:OR\s+REPLACE\s+)?)(ALGORITHM\s*=\s*\w+\s+)?(DEFINER\s*=\s*\S+\s+)?(SQL\s+SECURITY\s+(?:DEFINER|INVOKER)\s+)?(TEMP(?:ORARY)?\s+)?(RECURSIVE\s+)?(FORCE\s+|NOFORCE\s+)?(VIEW\s+)(\S+)(\s*\([^)]*\))?\s*AS\s*""",
        RegexOption.IGNORE_CASE
    )

    /**
     * CREATE VIEW 문 변환
     */
    fun convertCreateView(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql.trim()

        // 1. 확장된 VIEW 헤더 파싱
        val headerMatch = EXTENDED_VIEW_HEADER_PATTERN.find(result)
        if (headerMatch == null) {
            // 기본 패턴으로 폴백
            return convertBasicCreateView(result, sourceDialect, targetDialect, warnings, appliedRules)
        }

        val createOrReplace = headerMatch.groupValues[1].trim()
        val algorithm = headerMatch.groupValues[2].trim()         // MySQL: ALGORITHM=...
        val definer = headerMatch.groupValues[3].trim()           // MySQL: DEFINER=...
        val sqlSecurity = headerMatch.groupValues[4].trim()       // MySQL: SQL SECURITY DEFINER|INVOKER
        val temporary = headerMatch.groupValues[5].trim()         // TEMPORARY|TEMP
        val recursive = headerMatch.groupValues[6].trim()         // RECURSIVE
        val forceOption = headerMatch.groupValues[7].trim()       // Oracle: FORCE|NOFORCE
        val viewName = headerMatch.groupValues[9].trim()
        val columnList = headerMatch.groupValues[10].trim()

        val isTemporary = temporary.isNotEmpty()
        val isRecursive = recursive.isNotEmpty()

        // SELECT 부분 추출
        val selectStartIndex = headerMatch.range.last + 1
        var selectPart = result.substring(selectStartIndex).trim()

        // VIEW 옵션 추출 (WITH CHECK OPTION, WITH READ ONLY, WITH LOCAL/CASCADED CHECK OPTION 등)
        val viewOptionsPattern = Regex(
            """\s+(WITH\s+(?:(?:LOCAL|CASCADED)\s+)?CHECK\s+OPTION|WITH\s+READ\s+ONLY)\s*$""",
            RegexOption.IGNORE_CASE
        )
        val viewOption = viewOptionsPattern.find(selectPart)?.groupValues?.get(1)?.trim()
        if (viewOption != null) {
            selectPart = selectPart.replace(viewOptionsPattern, "").trim()
        }

        // 2. SELECT 부분 변환
        val convertedSelect = selectService.convertSelect(
            selectPart, sourceDialect, targetDialect, warnings, appliedRules
        )

        // 3. VIEW 헤더 재구성
        val convertedHeader = buildEnhancedViewHeader(
            createOrReplace = createOrReplace,
            algorithm = algorithm,
            definer = definer,
            sqlSecurity = sqlSecurity,
            isTemporary = isTemporary,
            isRecursive = isRecursive,
            forceOption = forceOption,
            viewName = viewName,
            columnList = columnList,
            sourceDialect = sourceDialect,
            targetDialect = targetDialect,
            warnings = warnings,
            appliedRules = appliedRules
        )

        // 4. VIEW 옵션 변환
        val convertedOptions = convertEnhancedViewOptions(
            viewOption, sourceDialect, targetDialect, warnings, appliedRules
        )

        appliedRules.add("CREATE VIEW 변환: ${sourceDialect.name} → ${targetDialect.name}")

        return if (convertedOptions.isNotEmpty()) {
            "$convertedHeader AS\n$convertedSelect\n$convertedOptions"
        } else {
            "$convertedHeader AS\n$convertedSelect"
        }
    }

    /**
     * 기본 CREATE VIEW 변환 (폴백)
     */
    private fun convertBasicCreateView(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val basicPattern = Regex(
            """(CREATE\s+(?:OR\s+REPLACE\s+)?)(FORCE\s+|NOFORCE\s+)?(VIEW\s+)(\S+)(\s*\([^)]*\))?\s*AS\s*""",
            RegexOption.IGNORE_CASE
        )

        val headerMatch = basicPattern.find(sql)
        if (headerMatch == null) {
            warnings.add(ConversionWarning(
                WarningType.SYNTAX_DIFFERENCE,
                "VIEW 구문 파싱에 실패했습니다. 원본 SQL을 반환합니다.",
                WarningSeverity.WARNING,
                "VIEW 이름과 AS 절이 올바른지 확인하세요."
            ))
            return sql
        }

        val createOrReplace = headerMatch.groupValues[1].trim()
        val forceOption = headerMatch.groupValues[2].trim()
        val viewName = headerMatch.groupValues[4].trim()
        val columnList = headerMatch.groupValues[5].trim()

        val selectStartIndex = headerMatch.range.last + 1
        var selectPart = sql.substring(selectStartIndex).trim()

        val viewOptionsPattern = Regex(
            """\s+(WITH\s+(?:CHECK\s+OPTION|READ\s+ONLY))\s*$""",
            RegexOption.IGNORE_CASE
        )
        val viewOption = viewOptionsPattern.find(selectPart)?.groupValues?.get(1)?.trim()
        if (viewOption != null) {
            selectPart = selectPart.replace(viewOptionsPattern, "").trim()
        }

        val convertedSelect = selectService.convertSelect(
            selectPart, sourceDialect, targetDialect, warnings, appliedRules
        )

        val convertedHeader = ViewConversionUtils.buildViewHeader(
            createOrReplace, forceOption, viewName, columnList,
            sourceDialect, targetDialect, warnings, appliedRules
        )

        val convertedOptions = ViewConversionUtils.convertViewOptions(
            viewOption, sourceDialect, targetDialect, warnings, appliedRules
        )

        return if (convertedOptions.isNotEmpty()) {
            "$convertedHeader AS\n$convertedSelect\n$convertedOptions"
        } else {
            "$convertedHeader AS\n$convertedSelect"
        }
    }

    /**
     * 확장된 VIEW 헤더 구성
     */
    private fun buildEnhancedViewHeader(
        createOrReplace: String,
        algorithm: String,
        definer: String,
        sqlSecurity: String,
        isTemporary: Boolean,
        isRecursive: Boolean,
        forceOption: String,
        viewName: String,
        columnList: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()

        // CREATE OR REPLACE
        val hasOrReplace = createOrReplace.contains(Regex("OR\\s+REPLACE", RegexOption.IGNORE_CASE))
        sb.append(if (hasOrReplace) "CREATE OR REPLACE" else "CREATE")

        // MySQL ALGORITHM 옵션
        if (algorithm.isNotEmpty()) {
            when (targetDialect) {
                DialectType.MYSQL -> {
                    sb.append(" $algorithm")
                }
                else -> {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "MySQL ALGORITHM 옵션이 제거되었습니다.",
                        WarningSeverity.INFO
                    ))
                    appliedRules.add("ALGORITHM 옵션 제거")
                }
            }
        }

        // MySQL DEFINER 옵션
        if (definer.isNotEmpty()) {
            when (targetDialect) {
                DialectType.MYSQL -> {
                    sb.append(" $definer")
                }
                else -> {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "MySQL DEFINER 옵션이 제거되었습니다.",
                        WarningSeverity.INFO,
                        "PostgreSQL/Oracle에서는 뷰 생성자 권한으로 실행됩니다."
                    ))
                    appliedRules.add("DEFINER 옵션 제거")
                }
            }
        }

        // SQL SECURITY 옵션
        if (sqlSecurity.isNotEmpty()) {
            when (targetDialect) {
                DialectType.MYSQL -> {
                    sb.append(" $sqlSecurity")
                }
                DialectType.POSTGRESQL -> {
                    // PostgreSQL에서는 SECURITY INVOKER/DEFINER로 변환 가능 (9.2+)
                    if (sqlSecurity.uppercase().contains("INVOKER")) {
                        warnings.add(ConversionWarning(
                            WarningType.SYNTAX_DIFFERENCE,
                            "SQL SECURITY INVOKER는 PostgreSQL에서 기본 동작입니다.",
                            WarningSeverity.INFO
                        ))
                    }
                    appliedRules.add("SQL SECURITY 옵션 변환")
                }
                else -> {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "SQL SECURITY 옵션이 제거되었습니다.",
                        WarningSeverity.INFO
                    ))
                    appliedRules.add("SQL SECURITY 옵션 제거")
                }
            }
        }

        // TEMPORARY VIEW
        if (isTemporary) {
            when (targetDialect) {
                DialectType.POSTGRESQL -> {
                    sb.append(" TEMPORARY")
                    appliedRules.add("TEMPORARY VIEW 변환")
                }
                DialectType.MYSQL -> {
                    warnings.add(ConversionWarning(
                        WarningType.PARTIAL_SUPPORT,
                        "MySQL은 TEMPORARY VIEW를 지원하지 않습니다.",
                        WarningSeverity.WARNING,
                        "일반 VIEW로 생성됩니다."
                    ))
                    appliedRules.add("TEMPORARY VIEW → VIEW (MySQL 미지원)")
                }
                DialectType.ORACLE -> {
                    warnings.add(ConversionWarning(
                        WarningType.PARTIAL_SUPPORT,
                        "Oracle은 TEMPORARY VIEW를 지원하지 않습니다.",
                        WarningSeverity.WARNING,
                        "GLOBAL TEMPORARY TABLE을 고려하세요."
                    ))
                    appliedRules.add("TEMPORARY VIEW → VIEW (Oracle 미지원)")
                }
            }
        }

        // RECURSIVE VIEW
        if (isRecursive) {
            when (targetDialect) {
                DialectType.POSTGRESQL -> {
                    sb.append(" RECURSIVE")
                    appliedRules.add("RECURSIVE VIEW 유지 (PostgreSQL)")
                }
                DialectType.MYSQL -> {
                    // MySQL 8.0+에서 지원하지만 구문이 다름
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "MySQL RECURSIVE VIEW는 WITH RECURSIVE CTE를 사용합니다.",
                        WarningSeverity.WARNING
                    ))
                    appliedRules.add("RECURSIVE VIEW 변환 주의 (MySQL)")
                }
                DialectType.ORACLE -> {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "Oracle에서 재귀 뷰는 WITH 절의 RECURSIVE를 사용합니다.",
                        WarningSeverity.WARNING
                    ))
                    appliedRules.add("RECURSIVE VIEW 변환 주의 (Oracle)")
                }
            }
        }

        // FORCE 옵션 (Oracle)
        if (forceOption.isNotEmpty()) {
            when (targetDialect) {
                DialectType.ORACLE -> {
                    sb.append(" $forceOption")
                }
                else -> {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "Oracle FORCE/NOFORCE 옵션이 제거되었습니다.",
                        WarningSeverity.INFO
                    ))
                    appliedRules.add("FORCE 옵션 제거")
                }
            }
        }

        sb.append(" VIEW ")
        sb.append(ViewConversionUtils.convertViewName(viewName, targetDialect))

        if (columnList.isNotEmpty()) {
            sb.append(" $columnList")
        }

        return sb.toString()
    }

    /**
     * 확장된 VIEW 옵션 변환 (LOCAL/CASCADED CHECK OPTION 포함)
     */
    private fun convertEnhancedViewOptions(
        option: String?,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (option == null) return ""

        val upperOption = option.uppercase()

        return when {
            upperOption.contains("LOCAL") && upperOption.contains("CHECK OPTION") -> {
                when (targetDialect) {
                    DialectType.MYSQL -> {
                        appliedRules.add("WITH LOCAL CHECK OPTION 유지 (MySQL)")
                        "WITH LOCAL CHECK OPTION"
                    }
                    DialectType.POSTGRESQL -> {
                        appliedRules.add("WITH LOCAL CHECK OPTION 유지 (PostgreSQL)")
                        "WITH LOCAL CHECK OPTION"
                    }
                    DialectType.ORACLE -> {
                        // Oracle은 LOCAL CHECK OPTION 미지원
                        warnings.add(ConversionWarning(
                            WarningType.SYNTAX_DIFFERENCE,
                            "Oracle은 LOCAL CHECK OPTION을 지원하지 않습니다.",
                            WarningSeverity.WARNING,
                            "WITH CHECK OPTION으로 대체됩니다."
                        ))
                        appliedRules.add("LOCAL CHECK OPTION → CHECK OPTION (Oracle)")
                        "WITH CHECK OPTION"
                    }
                }
            }
            upperOption.contains("CASCADED") && upperOption.contains("CHECK OPTION") -> {
                when (targetDialect) {
                    DialectType.MYSQL -> {
                        appliedRules.add("WITH CASCADED CHECK OPTION 유지 (MySQL)")
                        "WITH CASCADED CHECK OPTION"
                    }
                    DialectType.POSTGRESQL -> {
                        appliedRules.add("WITH CASCADED CHECK OPTION 유지 (PostgreSQL)")
                        "WITH CASCADED CHECK OPTION"
                    }
                    DialectType.ORACLE -> {
                        // Oracle은 기본이 CASCADED와 동일
                        appliedRules.add("CASCADED CHECK OPTION → CHECK OPTION (Oracle 기본)")
                        "WITH CHECK OPTION"
                    }
                }
            }
            else -> ViewConversionUtils.convertViewOptions(option, sourceDialect, targetDialect, warnings, appliedRules)
        }
    }

    /**
     * DROP VIEW 문 변환
     */
    fun convertDropView(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return DropViewConverter.convert(sql, sourceDialect, targetDialect, warnings, appliedRules)
    }

    /**
     * ALTER VIEW 문 변환
     */
    fun convertAlterView(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return AlterViewConverter.convert(sql, sourceDialect, targetDialect, warnings, appliedRules)
    }

    /**
     * MATERIALIZED VIEW 변환
     */
    fun convertMaterializedView(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return MaterializedViewConverter.convert(sql, sourceDialect, targetDialect, warnings, appliedRules)
    }
}