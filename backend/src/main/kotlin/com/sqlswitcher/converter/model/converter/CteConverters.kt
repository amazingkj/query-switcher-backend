package com.sqlswitcher.converter.model.converter

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.model.RecursiveCteInfo

/**
 * RecursiveCteInfo를 타겟 방언으로 변환
 */
fun RecursiveCteInfo.toDialect(
    targetDialect: DialectType,
    warnings: MutableList<ConversionWarning>
): String {
    return when (targetDialect) {
        DialectType.ORACLE -> toOracle(warnings)
        DialectType.POSTGRESQL -> toPostgreSql()
        DialectType.MYSQL -> toMySql(warnings)
    }
}

private fun RecursiveCteInfo.toOracle(warnings: MutableList<ConversionWarning>): String {
    warnings.add(ConversionWarning(
        type = WarningType.SYNTAX_DIFFERENCE,
        message = "Oracle 12c 이상에서는 재귀 CTE를 지원합니다. 이전 버전에서는 CONNECT BY를 사용해야 합니다.",
        severity = WarningSeverity.INFO,
        suggestion = "Oracle 버전을 확인하고 적절한 구문을 선택하세요."
    ))

    return buildString {
        appendLine("WITH \"$cteName\" (${columns.joinToString(", ") { "\"$it\"" }}) AS (")
        appendLine("    $anchorQuery")
        appendLine("    UNION ALL")
        appendLine("    $recursiveQuery")
        appendLine(")")
        append(mainQuery)
    }
}

private fun RecursiveCteInfo.toPostgreSql(): String {
    return buildString {
        appendLine("WITH RECURSIVE \"$cteName\" (${columns.joinToString(", ") { "\"$it\"" }}) AS (")
        appendLine("    $anchorQuery")
        appendLine("    UNION ALL")
        appendLine("    $recursiveQuery")
        appendLine(")")
        append(mainQuery)
    }
}

private fun RecursiveCteInfo.toMySql(warnings: MutableList<ConversionWarning>): String {
    warnings.add(ConversionWarning(
        type = WarningType.SYNTAX_DIFFERENCE,
        message = "MySQL 8.0 이상에서만 WITH RECURSIVE를 지원합니다.",
        severity = WarningSeverity.WARNING,
        suggestion = "MySQL 버전을 확인하세요. 5.7 이하에서는 저장 프로시저나 임시 테이블을 사용해야 합니다."
    ))

    return buildString {
        appendLine("WITH RECURSIVE `$cteName` (${columns.joinToString(", ") { "`$it`" }}) AS (")
        appendLine("    $anchorQuery")
        appendLine("    UNION ALL")
        appendLine("    $recursiveQuery")
        appendLine(")")
        append(mainQuery)
    }
}