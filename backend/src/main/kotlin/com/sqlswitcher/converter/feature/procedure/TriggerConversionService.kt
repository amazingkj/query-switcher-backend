package com.sqlswitcher.converter.feature.procedure

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import org.springframework.stereotype.Service

/**
 * 트리거 변환 서비스
 *
 * 지원 변환:
 * - Oracle BEFORE/AFTER 트리거 → MySQL/PostgreSQL
 * - MySQL 트리거 → PostgreSQL/Oracle
 * - PostgreSQL 트리거 → MySQL/Oracle
 *
 * 주요 차이점:
 * - Oracle: :NEW, :OLD 사용, FOR EACH ROW 위치
 * - MySQL: NEW, OLD 사용, DELIMITER 필요
 * - PostgreSQL: 트리거 함수 분리 필요, TG_OP 변수
 */
@Service
class TriggerConversionService {

    // 트리거 패턴들
    private val ORACLE_TRIGGER_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+(\w+)\s+(BEFORE|AFTER|INSTEAD\s+OF)\s+(INSERT|UPDATE|DELETE)(?:\s+OR\s+(INSERT|UPDATE|DELETE))*\s+ON\s+(\w+)(?:\s+FOR\s+EACH\s+ROW)?(.*)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val MYSQL_TRIGGER_PATTERN = Regex(
        """CREATE\s+TRIGGER\s+(\w+)\s+(BEFORE|AFTER)\s+(INSERT|UPDATE|DELETE)\s+ON\s+(\w+)\s+FOR\s+EACH\s+ROW(.*)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val POSTGRESQL_TRIGGER_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+(\w+)\s+(BEFORE|AFTER|INSTEAD\s+OF)\s+(INSERT|UPDATE|DELETE)(?:\s+OR\s+(INSERT|UPDATE|DELETE))*\s+ON\s+(\w+)(?:\s+FOR\s+EACH\s+(ROW|STATEMENT))?\s+EXECUTE\s+(?:FUNCTION|PROCEDURE)\s+(\w+)\s*\(\s*\)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun convertTrigger(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) return sql

        return when (sourceDialect) {
            DialectType.ORACLE -> convertOracleTrigger(sql, targetDialect, warnings, appliedRules)
            DialectType.MYSQL -> convertMySqlTrigger(sql, targetDialect, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertPostgreSqlTrigger(sql, targetDialect, warnings, appliedRules)
        }
    }

    /**
     * Oracle 트리거 → 타겟 방언
     */
    private fun convertOracleTrigger(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val match = ORACLE_TRIGGER_PATTERN.find(sql)
        if (match == null) {
            warnings.add(ConversionWarning(
                WarningType.MANUAL_REVIEW_NEEDED,
                "트리거 구문을 파싱할 수 없습니다. 수동 변환이 필요합니다.",
                WarningSeverity.WARNING
            ))
            return sql
        }

        val triggerName = match.groupValues[1]
        val timing = match.groupValues[2].uppercase()
        val event1 = match.groupValues[3].uppercase()
        val event2 = match.groupValues[4].uppercase().takeIf { it.isNotEmpty() }
        val tableName = match.groupValues[5]
        val body = match.groupValues[6].trim()

        return when (targetDialect) {
            DialectType.MYSQL -> convertOracleToMySql(triggerName, timing, event1, event2, tableName, body, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertOracleToPostgreSql(triggerName, timing, event1, event2, tableName, body, warnings, appliedRules)
            else -> sql
        }
    }

    /**
     * Oracle → MySQL 트리거 변환
     */
    private fun convertOracleToMySql(
        triggerName: String,
        timing: String,
        event1: String,
        event2: String?,
        tableName: String,
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // MySQL은 트리거당 하나의 이벤트만 지원
        if (event2 != null) {
            warnings.add(ConversionWarning(
                WarningType.SYNTAX_DIFFERENCE,
                "MySQL은 복합 이벤트 트리거를 지원하지 않습니다. 각 이벤트별로 별도 트리거가 필요합니다.",
                WarningSeverity.WARNING,
                "CREATE TRIGGER ${triggerName}_${event1.lowercase()} ... 와 CREATE TRIGGER ${triggerName}_${event2.lowercase()} ... 로 분리하세요."
            ))
        }

        // INSTEAD OF는 MySQL 미지원
        if (timing == "INSTEAD OF") {
            warnings.add(ConversionWarning(
                WarningType.UNSUPPORTED_FUNCTION,
                "MySQL은 INSTEAD OF 트리거를 지원하지 않습니다.",
                WarningSeverity.ERROR,
                "BEFORE 트리거로 대체하거나 로직을 재설계하세요."
            ))
            return "-- MySQL does not support INSTEAD OF triggers\n$body"
        }

        // 본문 변환
        var convertedBody = convertOracleTriggerBodyToMySql(body, appliedRules)

        val result = buildString {
            append("DELIMITER //\n")
            append("CREATE TRIGGER $triggerName\n")
            append("$timing $event1 ON $tableName\n")
            append("FOR EACH ROW\n")
            append("BEGIN\n")
            append(convertedBody)
            append("\nEND//\n")
            append("DELIMITER ;")
        }

        appliedRules.add("Oracle 트리거 → MySQL 트리거 변환")
        return result
    }

    /**
     * Oracle → PostgreSQL 트리거 변환
     */
    private fun convertOracleToPostgreSql(
        triggerName: String,
        timing: String,
        event1: String,
        event2: String?,
        tableName: String,
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val functionName = "${triggerName}_func"

        // 본문 변환
        var convertedBody = convertOracleTriggerBodyToPostgreSql(body, appliedRules)

        val events = if (event2 != null) "$event1 OR $event2" else event1

        val result = buildString {
            // 트리거 함수 생성
            append("-- Trigger function\n")
            append("CREATE OR REPLACE FUNCTION $functionName()\n")
            append("RETURNS TRIGGER AS \$\$\n")
            append("BEGIN\n")
            append(convertedBody)
            append("\n    RETURN NEW;\n")
            append("END;\n")
            append("\$\$ LANGUAGE plpgsql;\n\n")

            // 트리거 생성
            append("-- Trigger\n")
            append("CREATE TRIGGER $triggerName\n")
            append("$timing $events ON $tableName\n")
            append("FOR EACH ROW\n")
            append("EXECUTE FUNCTION $functionName();")
        }

        appliedRules.add("Oracle 트리거 → PostgreSQL 트리거 함수 변환")
        return result
    }

    /**
     * Oracle 트리거 본문 → MySQL 변환
     */
    private fun convertOracleTriggerBodyToMySql(body: String, appliedRules: MutableList<String>): String {
        var result = body

        // :NEW → NEW, :OLD → OLD
        result = result.replace(Regex(":NEW\\.", RegexOption.IGNORE_CASE), "NEW.")
        result = result.replace(Regex(":OLD\\.", RegexOption.IGNORE_CASE), "OLD.")

        // BEGIN/END 제거 (MySQL에서 외부에서 추가)
        result = result.replace(Regex("^\\s*BEGIN\\s*", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\s*END\\s*;?\\s*$", RegexOption.IGNORE_CASE), "")

        // DECLARE 블록 처리
        val declarePattern = Regex("""DECLARE\s+(.*?)\s+BEGIN""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val declareMatch = declarePattern.find(result)
        if (declareMatch != null) {
            val declarations = declareMatch.groupValues[1]
            val mysqlDeclarations = ProcedureConversionUtils.convertOracleDeclarationsToMySql(declarations)
            result = result.replace(declareMatch.value, "$mysqlDeclarations\n")
        }

        // NVL → IFNULL
        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "IFNULL(")

        // SYSDATE → NOW()
        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "NOW()")

        // RAISE_APPLICATION_ERROR → SIGNAL
        result = result.replace(
            Regex("""RAISE_APPLICATION_ERROR\s*\(\s*(-?\d+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)
        ) { m ->
            "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '${m.groupValues[2]}'"
        }

        if (result != body) {
            appliedRules.add("트리거 본문 Oracle → MySQL 변환")
        }

        return result.trim()
    }

    /**
     * Oracle 트리거 본문 → PostgreSQL 변환
     */
    private fun convertOracleTriggerBodyToPostgreSql(body: String, appliedRules: MutableList<String>): String {
        var result = body

        // :NEW → NEW, :OLD → OLD
        result = result.replace(Regex(":NEW\\.", RegexOption.IGNORE_CASE), "NEW.")
        result = result.replace(Regex(":OLD\\.", RegexOption.IGNORE_CASE), "OLD.")

        // BEGIN/END 제거 (PostgreSQL에서 외부에서 추가)
        result = result.replace(Regex("^\\s*BEGIN\\s*", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\s*END\\s*;?\\s*$", RegexOption.IGNORE_CASE), "")

        // NVL → COALESCE
        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")

        // SYSDATE → CURRENT_TIMESTAMP
        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")

        // RAISE_APPLICATION_ERROR → RAISE EXCEPTION
        result = result.replace(
            Regex("""RAISE_APPLICATION_ERROR\s*\(\s*(-?\d+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)
        ) { m ->
            "RAISE EXCEPTION '${m.groupValues[2]}'"
        }

        if (result != body) {
            appliedRules.add("트리거 본문 Oracle → PostgreSQL 변환")
        }

        return "    " + result.trim().replace("\n", "\n    ")
    }

    /**
     * MySQL 트리거 → 타겟 방언
     */
    private fun convertMySqlTrigger(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val match = MYSQL_TRIGGER_PATTERN.find(sql)
        if (match == null) {
            warnings.add(ConversionWarning(
                WarningType.MANUAL_REVIEW_NEEDED,
                "MySQL 트리거 구문을 파싱할 수 없습니다.",
                WarningSeverity.WARNING
            ))
            return sql
        }

        val triggerName = match.groupValues[1]
        val timing = match.groupValues[2].uppercase()
        val event = match.groupValues[3].uppercase()
        val tableName = match.groupValues[4]
        val body = match.groupValues[5].trim()

        return when (targetDialect) {
            DialectType.POSTGRESQL -> convertMySqlToPostgreSql(triggerName, timing, event, tableName, body, warnings, appliedRules)
            DialectType.ORACLE -> convertMySqlToOracle(triggerName, timing, event, tableName, body, warnings, appliedRules)
            else -> sql
        }
    }

    /**
     * MySQL → PostgreSQL 트리거 변환
     */
    private fun convertMySqlToPostgreSql(
        triggerName: String,
        timing: String,
        event: String,
        tableName: String,
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val functionName = "${triggerName}_func"

        var convertedBody = body
            .replace(Regex("^\\s*BEGIN\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*END\\s*;?\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
            .replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")

        val result = buildString {
            append("CREATE OR REPLACE FUNCTION $functionName()\n")
            append("RETURNS TRIGGER AS \$\$\n")
            append("BEGIN\n")
            append("    $convertedBody\n")
            append("    RETURN NEW;\n")
            append("END;\n")
            append("\$\$ LANGUAGE plpgsql;\n\n")
            append("CREATE TRIGGER $triggerName\n")
            append("$timing $event ON $tableName\n")
            append("FOR EACH ROW\n")
            append("EXECUTE FUNCTION $functionName();")
        }

        appliedRules.add("MySQL 트리거 → PostgreSQL 트리거 함수 변환")
        return result
    }

    /**
     * MySQL → Oracle 트리거 변환
     */
    private fun convertMySqlToOracle(
        triggerName: String,
        timing: String,
        event: String,
        tableName: String,
        body: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var convertedBody = body
            .replace(Regex("\\bNEW\\.", RegexOption.IGNORE_CASE), ":NEW.")
            .replace(Regex("\\bOLD\\.", RegexOption.IGNORE_CASE), ":OLD.")
            .replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
            .replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSDATE")

        // BEGIN/END 확인
        if (!convertedBody.trim().uppercase().startsWith("BEGIN")) {
            convertedBody = "BEGIN\n$convertedBody\nEND;"
        }

        val result = buildString {
            append("CREATE OR REPLACE TRIGGER $triggerName\n")
            append("$timing $event ON $tableName\n")
            append("FOR EACH ROW\n")
            append(convertedBody)
        }

        appliedRules.add("MySQL 트리거 → Oracle 트리거 변환")
        return result
    }

    /**
     * PostgreSQL 트리거 → 타겟 방언
     */
    private fun convertPostgreSqlTrigger(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            WarningType.MANUAL_REVIEW_NEEDED,
            "PostgreSQL 트리거는 별도의 함수를 참조합니다. 함수 본문도 함께 변환해야 합니다.",
            WarningSeverity.WARNING,
            "트리거 함수의 본문을 확인하고 해당 방언에 맞게 변환하세요."
        ))

        appliedRules.add("PostgreSQL 트리거 감지 - 수동 변환 필요")
        return "-- PostgreSQL trigger conversion requires manual review\n-- The trigger function body needs to be converted separately\n$sql"
    }

    /**
     * 트리거 감지
     */
    fun isTriggerStatement(sql: String): Boolean {
        return sql.uppercase().contains("CREATE") &&
               sql.uppercase().contains("TRIGGER") &&
               (sql.uppercase().contains("BEFORE") ||
                sql.uppercase().contains("AFTER") ||
                sql.uppercase().contains("INSTEAD OF"))
    }
}