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
        val convertedHeader = ViewConversionUtils.buildViewHeader(
            createOrReplace, forceOption, viewName, columnList,
            sourceDialect, targetDialect, warnings, appliedRules
        )

        // 4. VIEW 옵션 변환
        val convertedOptions = ViewConversionUtils.convertViewOptions(
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