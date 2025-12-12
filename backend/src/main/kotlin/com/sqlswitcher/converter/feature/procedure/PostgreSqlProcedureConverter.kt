package com.sqlswitcher.converter.feature.procedure

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity

/**
 * PostgreSQL PL/pgSQL 프로시저/함수 변환
 */
object PostgreSqlProcedureConverter {

    /**
     * PostgreSQL → Oracle 변환
     */
    fun convertToOracle(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // $$ 블록 제거 및 AS → IS 변환
        result = result.replace(Regex("""\s*AS\s*\$\$""", RegexOption.IGNORE_CASE), " IS")
        result = result.replace(Regex("""\$\$\s*LANGUAGE\s+plpgsql\s*;?""", RegexOption.IGNORE_CASE), "")

        // FUNCTION → PROCEDURE (RETURNS VOID인 경우)
        if (result.uppercase().contains("RETURNS VOID")) {
            result = result.replace(
                Regex("""CREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION""", RegexOption.IGNORE_CASE),
                "CREATE OR REPLACE PROCEDURE"
            )
            result = result.replace(Regex("""RETURNS\s+VOID\s*""", RegexOption.IGNORE_CASE), "")
            appliedRules.add("FUNCTION RETURNS VOID → PROCEDURE 변환")
        }

        warnings.add(ConversionWarning(
            WarningType.MANUAL_REVIEW_NEEDED,
            "PL/pgSQL → PL/SQL 변환이 수행되었습니다.",
            WarningSeverity.WARNING
        ))

        return result
    }

    /**
     * PostgreSQL → MySQL 변환
     */
    fun convertToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            WarningType.MANUAL_REVIEW_NEEDED,
            "PostgreSQL 함수를 MySQL로 변환하려면 수동 작업이 필요합니다.",
            WarningSeverity.WARNING,
            "MySQL PROCEDURE/FUNCTION 문법으로 재작성하세요."
        ))
        appliedRules.add("PostgreSQL → MySQL 프로시저 변환 (수동 필요)")
        return "-- MySQL conversion requires manual work\n$sql"
    }
}