package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import org.springframework.stereotype.Service

/**
 * 시퀀스 변환 서비스
 */
@Service
class SequenceConversionService {

    /**
     * CREATE SEQUENCE 변환
     */
    fun convertCreateSequence(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        schemaOwner: String? = null
    ): String {
        if (sourceDialect == targetDialect) {
            return sql
        }

        var result = sql

        when (targetDialect) {
            DialectType.MYSQL -> {
                result = convertToMySqlSequence(sql, warnings, appliedRules)
            }
            DialectType.POSTGRESQL -> {
                result = convertToPostgreSqlSequence(sql, sourceDialect, appliedRules)
            }
            DialectType.ORACLE, DialectType.TIBERO -> {
                result = convertToOracleSequence(sql, sourceDialect, appliedRules)
            }
        }

        return result
    }

    private fun convertToMySqlSequence(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "MySQL은 기본적으로 시퀀스를 지원하지 않습니다 (8.0 이상에서 지원).",
            severity = WarningSeverity.WARNING,
            suggestion = "AUTO_INCREMENT 또는 테이블+함수 방식으로 시뮬레이션하세요."
        ))

        // MySQL 8.0+ 시퀀스 문법으로 변환
        var result = sql
        result = result.replace(Regex("NOCACHE", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("NOORDER", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("NOCYCLE", RegexOption.IGNORE_CASE), "")

        appliedRules.add("시퀀스 → MySQL 8.0+ 시퀀스 변환")
        return result
    }

    private fun convertToPostgreSqlSequence(
        sql: String,
        sourceDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // Oracle 문법 → PostgreSQL
        if (sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.TIBERO) {
            result = result.replace(Regex("NOCACHE", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("NOORDER", RegexOption.IGNORE_CASE), "")
            result = result.replace(Regex("NOCYCLE", RegexOption.IGNORE_CASE), "NO CYCLE")
            result = result.replace(Regex("NOMAXVALUE", RegexOption.IGNORE_CASE), "NO MAXVALUE")
            result = result.replace(Regex("NOMINVALUE", RegexOption.IGNORE_CASE), "NO MINVALUE")
        }

        appliedRules.add("시퀀스 → PostgreSQL 시퀀스 변환")
        return result
    }

    private fun convertToOracleSequence(
        sql: String,
        sourceDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // PostgreSQL 문법 → Oracle
        if (sourceDialect == DialectType.POSTGRESQL) {
            result = result.replace(Regex("NO\\s+CYCLE", RegexOption.IGNORE_CASE), "NOCYCLE")
            result = result.replace(Regex("NO\\s+MAXVALUE", RegexOption.IGNORE_CASE), "NOMAXVALUE")
            result = result.replace(Regex("NO\\s+MINVALUE", RegexOption.IGNORE_CASE), "NOMINVALUE")
        }

        appliedRules.add("시퀀스 → Oracle 시퀀스 변환")
        return result
    }

    /**
     * DROP SEQUENCE 변환
     */
    fun convertDropSequence(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (targetDialect == DialectType.MYSQL) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL 8.0 미만에서는 시퀀스가 지원되지 않습니다.",
                severity = WarningSeverity.WARNING
            ))
        }

        appliedRules.add("DROP SEQUENCE 변환")
        return sql
    }

    /**
     * 시퀀스 사용 구문 변환 (NEXTVAL, CURRVAL)
     */
    fun convertSequenceUsage(
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

        // Oracle/Tibero: seq_name.NEXTVAL → PostgreSQL: nextval('seq_name')
        if ((sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.TIBERO)
            && targetDialect == DialectType.POSTGRESQL) {
            result = result.replace(
                Regex("(\\w+)\\.NEXTVAL", RegexOption.IGNORE_CASE)
            ) { match -> "nextval('${match.groupValues[1]}')" }

            result = result.replace(
                Regex("(\\w+)\\.CURRVAL", RegexOption.IGNORE_CASE)
            ) { match -> "currval('${match.groupValues[1]}')" }

            appliedRules.add("시퀀스.NEXTVAL → nextval('시퀀스') 변환")
        }

        // PostgreSQL: nextval('seq_name') → Oracle: seq_name.NEXTVAL
        if (sourceDialect == DialectType.POSTGRESQL
            && (targetDialect == DialectType.ORACLE || targetDialect == DialectType.TIBERO)) {
            result = result.replace(
                Regex("nextval\\s*\\(\\s*'(\\w+)'\\s*\\)", RegexOption.IGNORE_CASE)
            ) { match -> "${match.groupValues[1]}.NEXTVAL" }

            result = result.replace(
                Regex("currval\\s*\\(\\s*'(\\w+)'\\s*\\)", RegexOption.IGNORE_CASE)
            ) { match -> "${match.groupValues[1]}.CURRVAL" }

            appliedRules.add("nextval('시퀀스') → 시퀀스.NEXTVAL 변환")
        }

        return result
    }
}