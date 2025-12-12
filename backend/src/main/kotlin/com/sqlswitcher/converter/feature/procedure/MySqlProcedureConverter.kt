package com.sqlswitcher.converter.feature.procedure

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity

/**
 * MySQL 프로시저 변환
 */
object MySqlProcedureConverter {

    /**
     * MySQL → Oracle 변환
     */
    fun convertToOracle(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // CREATE PROCEDURE → CREATE OR REPLACE PROCEDURE
        result = result.replace(
            Regex("""CREATE\s+PROCEDURE""", RegexOption.IGNORE_CASE),
            "CREATE OR REPLACE PROCEDURE"
        )

        // BEGIN 앞에 IS 추가
        result = result.replace(
            Regex("""\)\s*BEGIN""", RegexOption.IGNORE_CASE),
            ")\nIS\nBEGIN"
        )

        // DECLARE → IS 블록으로 이동 (간단한 케이스)
        val declarePattern = Regex("""DECLARE\s+(.+?);""", RegexOption.IGNORE_CASE)
        result = declarePattern.replace(result) { match ->
            val varDecl = match.groupValues[1]
            "-- Variable: $varDecl (move to IS block)"
        }

        warnings.add(ConversionWarning(
            WarningType.MANUAL_REVIEW_NEEDED,
            "MySQL → PL/SQL 변환이 수행되었습니다. 변수 선언을 IS 블록으로 이동하세요.",
            WarningSeverity.WARNING
        ))
        appliedRules.add("MySQL → Oracle 프로시저 변환")

        return result
    }

    /**
     * MySQL → PostgreSQL 변환
     */
    fun convertToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // CREATE PROCEDURE → CREATE OR REPLACE FUNCTION
        result = result.replace(
            Regex("""CREATE\s+PROCEDURE""", RegexOption.IGNORE_CASE),
            "CREATE OR REPLACE FUNCTION"
        )

        // ) BEGIN → ) RETURNS VOID AS $$ BEGIN
        result = result.replace(
            Regex("""\)\s*BEGIN""", RegexOption.IGNORE_CASE),
            ")\nRETURNS VOID AS \$\$\nBEGIN"
        )

        // END; → END; $$ LANGUAGE plpgsql;
        result = result.replace(
            Regex("""END\s*;\s*$""", RegexOption.IGNORE_CASE),
            "END;\n\$\$ LANGUAGE plpgsql;"
        )

        warnings.add(ConversionWarning(
            WarningType.MANUAL_REVIEW_NEEDED,
            "MySQL → PL/pgSQL 변환이 수행되었습니다.",
            WarningSeverity.WARNING
        ))
        appliedRules.add("MySQL → PostgreSQL 프로시저 변환")

        return result
    }
}