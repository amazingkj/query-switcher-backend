package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import org.springframework.stereotype.Service

/**
 * VIEW 변환 서비스
 * CREATE VIEW, DROP VIEW, ALTER VIEW 등을 데이터베이스 방언에 맞게 변환
 */
@Service
class ViewConversionService(
    private val selectService: SelectConversionService,
    private val functionService: FunctionConversionService
) {

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

        // 1. VIEW 헤더 파싱 (CREATE [OR REPLACE] [FORCE|NOFORCE] VIEW name [(columns)] AS)
        val viewHeaderPattern = Regex(
            """(CREATE\s+(?:OR\s+REPLACE\s+)?)(FORCE\s+|NOFORCE\s+)?(VIEW\s+)(\S+)(\s*\([^)]*\))?\s*AS\s*""",
            RegexOption.IGNORE_CASE
        )

        val headerMatch = viewHeaderPattern.find(result)
        if (headerMatch == null) {
            warnings.add(ConversionWarning(
                WarningType.SYNTAX_DIFFERENCE,
                "VIEW 구문 파싱에 실패했습니다. 원본 SQL을 반환합니다.",
                WarningSeverity.WARNING,
                "VIEW 이름과 AS 절이 올바른지 확인하세요."
            ))
            return result
        }

        val createOrReplace = headerMatch.groupValues[1].trim()
        val forceOption = headerMatch.groupValues[2].trim()
        val viewKeyword = headerMatch.groupValues[3].trim()
        val viewName = headerMatch.groupValues[4].trim()
        val columnList = headerMatch.groupValues[5].trim()

        // SELECT 부분 추출
        val selectStartIndex = headerMatch.range.last + 1
        var selectPart = result.substring(selectStartIndex).trim()

        // VIEW 옵션 추출 (WITH CHECK OPTION, WITH READ ONLY 등)
        val viewOptionsPattern = Regex(
            """\s+(WITH\s+(?:CHECK\s+OPTION|READ\s+ONLY))\s*$""",
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
        val convertedHeader = buildViewHeader(
            createOrReplace, forceOption, viewName, columnList,
            sourceDialect, targetDialect, warnings, appliedRules
        )

        // 4. VIEW 옵션 변환
        val convertedOptions = convertViewOptions(
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
     * VIEW 헤더 구성
     */
    private fun buildViewHeader(
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
     * VIEW 이름 변환 (인용문자 처리)
     */
    private fun convertViewName(name: String, targetDialect: DialectType): String {
        val cleaned = name.trim('"', '`', '[', ']')
        return when (targetDialect) {
            DialectType.MYSQL -> "`$cleaned`"
            else -> "\"$cleaned\""
        }
    }

    /**
     * VIEW 옵션 변환
     */
    private fun convertViewOptions(
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
                        // PostgreSQL은 SECURITY_BARRIER 등 다른 방식 사용
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
        var result = sql.trim()

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
                // Oracle 23c 이전에는 IF EXISTS 미지원, 사용 시 경고
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
        sb.append(convertViewName(viewName, targetDialect))

        // CASCADE/RESTRICT 처리
        if (cascadeOption.isNotEmpty()) {
            when (targetDialect) {
                DialectType.POSTGRESQL -> {
                    sb.append(" $cascadeOption")
                    appliedRules.add("CASCADE/RESTRICT 유지 (PostgreSQL)")
                }
                DialectType.ORACLE -> {
                    // Oracle은 DROP VIEW에서 CASCADE CONSTRAINTS 사용
                    if (cascadeOption == "CASCADE") {
                        sb.append(" CASCADE CONSTRAINTS")
                        appliedRules.add("CASCADE → CASCADE CONSTRAINTS (Oracle)")
                    }
                }
                DialectType.MYSQL -> {
                    // MySQL은 CASCADE/RESTRICT 무시 (실제로 동작하지 않음)
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
        // ALTER VIEW는 데이터베이스마다 지원 범위가 다름
        // 대부분 CREATE OR REPLACE VIEW로 대체 권장

        when (targetDialect) {
            DialectType.MYSQL -> {
                // MySQL: ALTER VIEW 지원 (8.0+)
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
                // PostgreSQL: ALTER VIEW는 속성 변경만 가능 (소유자, 이름 등)
                // SELECT 변경은 CREATE OR REPLACE VIEW 필요
                warnings.add(ConversionWarning(
                    WarningType.PARTIAL_SUPPORT,
                    "PostgreSQL의 ALTER VIEW는 제한적입니다. SELECT 변경은 CREATE OR REPLACE VIEW를 사용하세요.",
                    WarningSeverity.WARNING,
                    "PostgreSQL에서 ALTER VIEW는 속성(소유자, 이름, 스키마 등)만 변경 가능합니다."
                ))
            }
            DialectType.ORACLE -> {
                // Oracle: ALTER VIEW는 제약조건 컴파일 등에 사용
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

    /**
     * MATERIALIZED VIEW 변환 (기본 처리)
     */
    fun convertMaterializedView(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // Materialized View는 데이터베이스별로 문법이 크게 다름

        when {
            sourceDialect == DialectType.ORACLE && targetDialect == DialectType.POSTGRESQL -> {
                warnings.add(ConversionWarning(
                    WarningType.PARTIAL_SUPPORT,
                    "Oracle MATERIALIZED VIEW를 PostgreSQL로 변환합니다. 새로고침 옵션 등이 다를 수 있습니다.",
                    WarningSeverity.WARNING,
                    "PostgreSQL에서 REFRESH MATERIALIZED VIEW를 수동으로 실행하거나 pg_cron 등을 사용하세요."
                ))
                appliedRules.add("MATERIALIZED VIEW 변환 (Oracle → PostgreSQL)")

                // 기본적인 변환 시도
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
            sourceDialect == DialectType.ORACLE && targetDialect == DialectType.MYSQL -> {
                warnings.add(ConversionWarning(
                    WarningType.UNSUPPORTED_STATEMENT,
                    "MySQL은 MATERIALIZED VIEW를 직접 지원하지 않습니다.",
                    WarningSeverity.ERROR,
                    "일반 VIEW + 트리거/이벤트로 구현하거나, 별도 테이블로 데이터를 복제하세요."
                ))
                appliedRules.add("MATERIALIZED VIEW 변환 실패 (MySQL 미지원)")
                return "-- MySQL does not support MATERIALIZED VIEW\n-- Original: $sql"
            }
            targetDialect == DialectType.ORACLE -> {
                // PostgreSQL → Oracle
                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "MATERIALIZED VIEW를 Oracle로 변환합니다. 새로고침 로그 등 추가 설정이 필요할 수 있습니다.",
                    WarningSeverity.WARNING
                ))
                appliedRules.add("MATERIALIZED VIEW 변환 (→ Oracle)")
                return sql
            }
            else -> {
                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "MATERIALIZED VIEW 변환은 수동 검토가 필요합니다.",
                    WarningSeverity.WARNING
                ))
                appliedRules.add("MATERIALIZED VIEW 변환 (수동 검토 필요)")
                return sql
            }
        }
    }
}