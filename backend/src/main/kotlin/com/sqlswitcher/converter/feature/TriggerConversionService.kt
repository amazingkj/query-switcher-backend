package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.core.*
import org.springframework.stereotype.Service

/**
 * 트리거 정보
 */
data class TriggerInfo(
    val name: String,
    val tableName: String,
    val timing: TriggerTiming,
    val events: List<TriggerEvent>,
    val forEachRow: Boolean = true,
    val whenCondition: String? = null,
    val body: String,
    val referencing: ReferencingClause? = null
) {
    enum class TriggerTiming { BEFORE, AFTER, INSTEAD_OF }
    enum class TriggerEvent { INSERT, UPDATE, DELETE }

    data class ReferencingClause(
        val oldAlias: String? = null,
        val newAlias: String? = null
    )
}

/**
 * 트리거 변환 서비스
 */
@Service
class TriggerConversionService(
    private val functionService: FunctionConversionService
) {

    /**
     * 트리거 SQL 변환
     */
    fun convertTrigger(
        triggerSql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) {
            return triggerSql
        }

        val triggerInfo = parseTriggerInfo(triggerSql, sourceDialect)

        return when (targetDialect) {
            DialectType.MYSQL -> convertToMySql(triggerInfo, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertToPostgreSql(triggerInfo, warnings, appliedRules)
            DialectType.ORACLE -> convertToOracle(triggerInfo, warnings, appliedRules)
            DialectType.TIBERO -> convertToTibero(triggerInfo, triggerSql, warnings, appliedRules)
        }
    }

    /**
     * 트리거 SQL 파싱
     */
    private fun parseTriggerInfo(sql: String, dialect: DialectType): TriggerInfo {
        val upperSql = sql.uppercase()

        // 트리거명 추출
        val triggerNameRegex = """CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+"?(\w+)"?(?:\."?(\w+)"?)?""".toRegex(RegexOption.IGNORE_CASE)
        val triggerNameMatch = triggerNameRegex.find(sql)
        val triggerName = triggerNameMatch?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
            ?: triggerNameMatch?.groupValues?.get(1)
            ?: "UNKNOWN_TRIGGER"

        // 테이블명 추출
        val tableNameRegex = """ON\s+"?(\w+)"?(?:\."?(\w+)"?)?""".toRegex(RegexOption.IGNORE_CASE)
        val tableNameMatch = tableNameRegex.find(sql)
        val tableName = tableNameMatch?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
            ?: tableNameMatch?.groupValues?.get(1)
            ?: "UNKNOWN_TABLE"

        // 타이밍 추출
        val timing = when {
            upperSql.contains("INSTEAD OF") -> TriggerInfo.TriggerTiming.INSTEAD_OF
            upperSql.contains("BEFORE") -> TriggerInfo.TriggerTiming.BEFORE
            upperSql.contains("AFTER") -> TriggerInfo.TriggerTiming.AFTER
            else -> TriggerInfo.TriggerTiming.AFTER
        }

        // 이벤트 추출
        val events = mutableListOf<TriggerInfo.TriggerEvent>()
        if (upperSql.contains("INSERT")) events.add(TriggerInfo.TriggerEvent.INSERT)
        if (upperSql.contains("UPDATE")) events.add(TriggerInfo.TriggerEvent.UPDATE)
        if (upperSql.contains("DELETE")) events.add(TriggerInfo.TriggerEvent.DELETE)
        if (events.isEmpty()) events.add(TriggerInfo.TriggerEvent.INSERT)

        // FOR EACH ROW 여부
        val forEachRow = upperSql.contains("FOR EACH ROW")

        // WHEN 조건 추출
        val whenRegex = """WHEN\s*\((.+?)\)\s*(?:BEGIN|DECLARE|CALL)""".toRegex(RegexOption.IGNORE_CASE)
        val whenCondition = whenRegex.find(sql)?.groupValues?.get(1)?.trim()

        // 본문 추출
        val bodyRegex = """(BEGIN.+END\s*;?|CALL\s+\w+\s*\([^)]*\)\s*;?)""".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val body = bodyRegex.find(sql)?.groupValues?.get(1)?.trim() ?: ""

        // REFERENCING 절 추출
        val referencingRegex = """REFERENCING\s+(?:OLD\s+(?:AS\s+)?(\w+)\s+)?(?:NEW\s+(?:AS\s+)?(\w+))?""".toRegex(RegexOption.IGNORE_CASE)
        val referencingMatch = referencingRegex.find(sql)
        val referencing = if (referencingMatch != null) {
            TriggerInfo.ReferencingClause(
                oldAlias = referencingMatch.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() },
                newAlias = referencingMatch.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
            )
        } else null

        return TriggerInfo(
            name = triggerName,
            tableName = tableName,
            timing = timing,
            events = events,
            forEachRow = forEachRow,
            whenCondition = whenCondition,
            body = body,
            referencing = referencing
        )
    }

    /**
     * MySQL 형식으로 변환
     */
    private fun convertToMySql(
        trigger: TriggerInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // MySQL은 하나의 트리거에 하나의 이벤트만 허용
        if (trigger.events.size > 1) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL은 하나의 트리거에 하나의 이벤트만 허용합니다. 여러 트리거로 분리됩니다.",
                severity = WarningSeverity.WARNING
            ))
        }

        // INSTEAD OF 미지원
        if (trigger.timing == TriggerInfo.TriggerTiming.INSTEAD_OF) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "MySQL은 INSTEAD OF 트리거를 지원하지 않습니다.",
                severity = WarningSeverity.ERROR,
                suggestion = "BEFORE 트리거로 변경하고 로직을 조정하세요."
            ))
        }

        val results = mutableListOf<String>()

        for (event in trigger.events) {
            val sb = StringBuilder()
            val triggerName = if (trigger.events.size > 1) "${trigger.name}_${event.name.lowercase()}" else trigger.name

            sb.append("DELIMITER //\n\n")
            sb.append("CREATE TRIGGER `$triggerName`\n")

            val timingStr = if (trigger.timing == TriggerInfo.TriggerTiming.INSTEAD_OF) "BEFORE" else trigger.timing.name
            sb.append("    $timingStr ${event.name} ON `${trigger.tableName}`\n")
            sb.append("    FOR EACH ROW\n")
            sb.append("BEGIN\n")

            // WHEN 조건을 IF로 변환
            if (!trigger.whenCondition.isNullOrBlank()) {
                val condition = convertConditionToMySql(trigger.whenCondition)
                sb.append("    IF $condition THEN\n")
            }

            // 본문 변환
            val convertedBody = convertBodyToMySql(trigger.body)
            sb.append("        $convertedBody\n")

            if (!trigger.whenCondition.isNullOrBlank()) {
                sb.append("    END IF;\n")
            }

            sb.append("END //\n\n")
            sb.append("DELIMITER ;")

            results.add(sb.toString())
        }

        appliedRules.add("트리거 → MySQL 형식으로 변환")
        return results.joinToString("\n\n")
    }

    /**
     * PostgreSQL 형식으로 변환
     */
    private fun convertToPostgreSql(
        trigger: TriggerInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()
        val functionName = "${trigger.name}_func"

        // 트리거 함수 생성
        sb.append("-- 트리거 함수 생성\n")
        sb.append("CREATE OR REPLACE FUNCTION $functionName()\n")
        sb.append("RETURNS TRIGGER AS \$\$\n")
        sb.append("BEGIN\n")

        // WHEN 조건 처리
        if (!trigger.whenCondition.isNullOrBlank()) {
            val condition = convertConditionToPostgreSql(trigger.whenCondition)
            sb.append("    IF $condition THEN\n")
            sb.append("        ${convertBodyToPostgreSql(trigger.body, trigger.referencing)}\n")
            sb.append("    END IF;\n")
        } else {
            sb.append("    ${convertBodyToPostgreSql(trigger.body, trigger.referencing)}\n")
        }

        // RETURN 추가
        if (trigger.timing == TriggerInfo.TriggerTiming.BEFORE) {
            sb.append("    RETURN NEW;\n")
        } else {
            sb.append("    RETURN NULL;\n")
        }

        sb.append("END;\n")
        sb.append("\$\$ LANGUAGE plpgsql;\n\n")

        // 트리거 생성
        sb.append("-- 트리거 생성\n")
        sb.append("CREATE TRIGGER \"${trigger.name}\"\n")
        sb.append("    ${trigger.timing.name} ${trigger.events.joinToString(" OR ") { it.name }}\n")
        sb.append("    ON \"${trigger.tableName}\"\n")
        if (trigger.forEachRow) {
            sb.append("    FOR EACH ROW\n")
        }
        sb.append("    EXECUTE FUNCTION $functionName();")

        appliedRules.add("트리거 → PostgreSQL 형식으로 변환 (함수 + 트리거 분리)")
        return sb.toString()
    }

    /**
     * Oracle 형식으로 변환
     */
    private fun convertToOracle(
        trigger: TriggerInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()

        sb.append("CREATE OR REPLACE TRIGGER \"${trigger.name}\"\n")
        sb.append("    ${trigger.timing.name} ${trigger.events.joinToString(" OR ") { it.name }}\n")
        sb.append("    ON \"${trigger.tableName}\"\n")

        if (trigger.forEachRow) {
            sb.append("    FOR EACH ROW\n")
        }

        if (!trigger.whenCondition.isNullOrBlank()) {
            sb.append("    WHEN (${trigger.whenCondition})\n")
        }

        sb.append(convertBodyToOracle(trigger.body))

        appliedRules.add("트리거 → Oracle 형식으로 변환")
        return sb.toString()
    }

    /**
     * Tibero 형식으로 변환 (Oracle과 호환)
     */
    private fun convertToTibero(
        trigger: TriggerInfo,
        originalSql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        appliedRules.add("트리거 유지 (Tibero - Oracle 호환)")
        return originalSql
    }

    // 조건문/본문 변환 헬퍼 메서드들
    private fun convertConditionToMySql(condition: String): String {
        return condition
            .replace(Regex(""":OLD\.""", RegexOption.IGNORE_CASE), "OLD.")
            .replace(Regex(""":NEW\.""", RegexOption.IGNORE_CASE), "NEW.")
    }

    private fun convertConditionToPostgreSql(condition: String): String {
        return condition
            .replace(Regex(""":OLD\.""", RegexOption.IGNORE_CASE), "OLD.")
            .replace(Regex(""":NEW\.""", RegexOption.IGNORE_CASE), "NEW.")
    }

    private fun convertBodyToMySql(body: String): String {
        var result = body
            .replace(Regex("""^\s*BEGIN\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*END\s*;?\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex(""":OLD\.""", RegexOption.IGNORE_CASE), "OLD.")
            .replace(Regex(""":NEW\.""", RegexOption.IGNORE_CASE), "NEW.")
            .replace(Regex("""RAISE_APPLICATION_ERROR\s*\(\s*(-?\d+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)) { match ->
                "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '${match.groupValues[2]}'"
            }
            .replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "NOW()")
            .replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "IFNULL(")

        return result.trim()
    }

    private fun convertBodyToPostgreSql(body: String, referencing: TriggerInfo.ReferencingClause?): String {
        var result = body
            .replace(Regex("""^\s*BEGIN\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*END\s*;?\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex(""":OLD\.""", RegexOption.IGNORE_CASE), "OLD.")
            .replace(Regex(""":NEW\.""", RegexOption.IGNORE_CASE), "NEW.")
            .replace(Regex("""RAISE_APPLICATION_ERROR\s*\(\s*(-?\d+)\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)) { match ->
                "RAISE EXCEPTION '${match.groupValues[2]}'"
            }
            .replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
            .replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")

        // REFERENCING 별칭 처리
        referencing?.let { ref ->
            ref.oldAlias?.let { alias ->
                result = result.replace(Regex("\\b$alias\\.", RegexOption.IGNORE_CASE), "OLD.")
            }
            ref.newAlias?.let { alias ->
                result = result.replace(Regex("\\b$alias\\.", RegexOption.IGNORE_CASE), "NEW.")
            }
        }

        return result.trim()
    }

    private fun convertBodyToOracle(body: String): String {
        return if (body.uppercase().startsWith("BEGIN")) body else "BEGIN\n$body\nEND;"
    }
}