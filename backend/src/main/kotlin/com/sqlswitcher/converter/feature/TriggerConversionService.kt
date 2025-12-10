package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import org.springframework.stereotype.Service

/**
 * 트리거 변환 서비스
 */
@Service
class TriggerConversionService {

    /**
     * 트리거 변환
     */
    fun convertTrigger(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) {
            return sql
        }

        var result = sql

        when {
            // Oracle/Tibero → MySQL
            (sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.TIBERO)
                && targetDialect == DialectType.MYSQL -> {
                result = convertOracleToMySqlTrigger(result, warnings, appliedRules)
            }
            // Oracle/Tibero → PostgreSQL
            (sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.TIBERO)
                && targetDialect == DialectType.POSTGRESQL -> {
                result = convertOracleToPostgreSqlTrigger(result, warnings, appliedRules)
            }
            // MySQL → Oracle/Tibero
            sourceDialect == DialectType.MYSQL
                && (targetDialect == DialectType.ORACLE || targetDialect == DialectType.TIBERO) -> {
                result = convertMySqlToOracleTrigger(result, warnings, appliedRules)
            }
            // MySQL → PostgreSQL
            sourceDialect == DialectType.MYSQL && targetDialect == DialectType.POSTGRESQL -> {
                result = convertMySqlToPostgreSqlTrigger(result, warnings, appliedRules)
            }
        }

        return result
    }

    private fun convertOracleToMySqlTrigger(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // :NEW → NEW, :OLD → OLD
        result = result.replace(Regex(":NEW\\.", RegexOption.IGNORE_CASE), "NEW.")
        result = result.replace(Regex(":OLD\\.", RegexOption.IGNORE_CASE), "OLD.")

        // FOR EACH ROW 위치 조정
        // Oracle: BEFORE INSERT FOR EACH ROW
        // MySQL: BEFORE INSERT FOR EACH ROW

        // BEGIN/END 블록
        result = result.replace(Regex("\\bDECLARE\\b", RegexOption.IGNORE_CASE), "-- DECLARE")

        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "Oracle 트리거를 MySQL 트리거로 변환했습니다. 수동 검토가 필요할 수 있습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "MySQL은 트리거당 하나의 이벤트만 지원합니다."
        ))

        appliedRules.add("Oracle 트리거 → MySQL 트리거 변환")
        return result
    }

    private fun convertOracleToPostgreSqlTrigger(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // :NEW → NEW, :OLD → OLD
        result = result.replace(Regex(":NEW\\.", RegexOption.IGNORE_CASE), "NEW.")
        result = result.replace(Regex(":OLD\\.", RegexOption.IGNORE_CASE), "OLD.")

        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "PostgreSQL 트리거는 별도의 함수가 필요합니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "CREATE FUNCTION ... RETURNS TRIGGER 후 CREATE TRIGGER ... EXECUTE FUNCTION ..."
        ))

        appliedRules.add("Oracle 트리거 → PostgreSQL 트리거 변환 (수동 검토 필요)")
        return result
    }

    private fun convertMySqlToOracleTrigger(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // NEW → :NEW, OLD → :OLD
        result = result.replace(Regex("\\bNEW\\.", RegexOption.IGNORE_CASE), ":NEW.")
        result = result.replace(Regex("\\bOLD\\.", RegexOption.IGNORE_CASE), ":OLD.")

        // DELIMITER 제거
        result = result.replace(Regex("DELIMITER\\s+\\S+", RegexOption.IGNORE_CASE), "")

        appliedRules.add("MySQL 트리거 → Oracle 트리거 변환")
        return result
    }

    private fun convertMySqlToPostgreSqlTrigger(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // DELIMITER 제거
        result = result.replace(Regex("DELIMITER\\s+\\S+", RegexOption.IGNORE_CASE), "")

        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "PostgreSQL 트리거는 별도의 함수가 필요합니다.",
            severity = WarningSeverity.WARNING
        ))

        appliedRules.add("MySQL 트리거 → PostgreSQL 트리거 변환")
        return result
    }
}