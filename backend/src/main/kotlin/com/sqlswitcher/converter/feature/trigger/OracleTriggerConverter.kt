package com.sqlswitcher.converter.feature.trigger

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * Oracle 트리거 변환기
 *
 * Oracle 트리거를 MySQL/PostgreSQL로 변환
 *
 * 지원 트리거 타입:
 * - BEFORE/AFTER INSERT/UPDATE/DELETE
 * - FOR EACH ROW (행 레벨 트리거)
 * - INSTEAD OF (뷰 트리거 - PostgreSQL만)
 * - 복합 트리거 (COMPOUND TRIGGER)
 *
 * 변환 전략:
 * - MySQL: 직접 트리거 문법으로 변환
 * - PostgreSQL: 트리거 함수 + 트리거 정의 분리
 */
object OracleTriggerConverter {

    // ============ 트리거 파싱 패턴 ============

    /** 트리거 헤더 패턴 */
    private val TRIGGER_HEADER_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+["']?(\w+)["']?""",
        RegexOption.IGNORE_CASE
    )

    /** 트리거 타이밍 패턴 */
    private val TRIGGER_TIMING_PATTERN = Regex(
        """(BEFORE|AFTER|INSTEAD\s+OF)""",
        RegexOption.IGNORE_CASE
    )

    /** 트리거 이벤트 패턴 */
    private val TRIGGER_EVENT_PATTERN = Regex(
        """(INSERT|UPDATE|DELETE)(?:\s+OF\s+([^O\s]+(?:\s*,\s*\w+)*))?""",
        RegexOption.IGNORE_CASE
    )

    /** ON 테이블 패턴 */
    private val ON_TABLE_PATTERN = Regex(
        """ON\s+["']?(?:(\w+)\.)?(\w+)["']?""",
        RegexOption.IGNORE_CASE
    )

    /** FOR EACH ROW 패턴 */
    private val FOR_EACH_ROW_PATTERN = Regex(
        """FOR\s+EACH\s+ROW""",
        RegexOption.IGNORE_CASE
    )

    /** WHEN 조건 패턴 */
    private val WHEN_PATTERN = Regex(
        """WHEN\s*\((.+?)\)(?=\s*(?:DECLARE|BEGIN))""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** DECLARE 블록 패턴 */
    private val DECLARE_PATTERN = Regex(
        """DECLARE\s+(.+?)(?=BEGIN)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** BEGIN...END 블록 패턴 */
    private val BEGIN_END_PATTERN = Regex(
        """BEGIN\s+(.+?)\s+END\s*;?\s*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** COMPOUND TRIGGER 패턴 */
    private val COMPOUND_TRIGGER_PATTERN = Regex(
        """COMPOUND\s+TRIGGER""",
        RegexOption.IGNORE_CASE
    )

    /** :NEW/:OLD 참조 패턴 */
    private val NEW_OLD_REFERENCE_PATTERN = Regex(
        """:(\s*)(NEW|OLD)\.(\w+)""",
        RegexOption.IGNORE_CASE
    )

    /** REFERENCING 절 패턴 */
    private val REFERENCING_PATTERN = Regex(
        """REFERENCING\s+(?:NEW\s+AS\s+(\w+)\s*)?(?:OLD\s+AS\s+(\w+))?""",
        RegexOption.IGNORE_CASE
    )

    /** RAISE_APPLICATION_ERROR 패턴 */
    private val RAISE_ERROR_PATTERN = Regex(
        """RAISE_APPLICATION_ERROR\s*\(\s*(-?\d+)\s*,\s*['"](.+?)['"]\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /** INSERTING/UPDATING/DELETING 조건 패턴 */
    private val TRIGGER_PREDICATE_PATTERN = Regex(
        """\b(INSERTING|UPDATING|DELETING)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 트리거 변환 메인 함수
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql
        if (!TRIGGER_HEADER_PATTERN.containsMatchIn(sql)) return sql

        return when (targetDialect) {
            DialectType.MYSQL -> convertToMySql(sql, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertToPostgreSql(sql, warnings, appliedRules)
            DialectType.ORACLE -> sql
        }
    }

    /**
     * Oracle 트리거 → MySQL 변환
     */
    fun convertToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val triggerInfo = parseTrigger(sql) ?: return sql

        // COMPOUND TRIGGER 체크
        if (COMPOUND_TRIGGER_PATTERN.containsMatchIn(sql)) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "MySQL은 COMPOUND TRIGGER를 지원하지 않습니다.",
                severity = WarningSeverity.ERROR,
                suggestion = "각 타이밍 포인트별로 별도의 트리거를 생성해야 합니다."
            ))
            return "-- COMPOUND TRIGGER는 MySQL에서 지원되지 않습니다. 수동 변환 필요.\n$sql"
        }

        // INSTEAD OF 체크
        if (triggerInfo.timing.uppercase().contains("INSTEAD")) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "MySQL은 INSTEAD OF 트리거를 지원하지 않습니다.",
                severity = WarningSeverity.ERROR,
                suggestion = "뷰 대신 저장 프로시저를 사용하거나 BEFORE 트리거로 대체하세요."
            ))
            return "-- INSTEAD OF 트리거는 MySQL에서 지원되지 않습니다.\n$sql"
        }

        // WHEN 조건 체크
        if (triggerInfo.whenCondition != null) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL은 트리거의 WHEN 절을 지원하지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "WHEN 조건을 트리거 본문 내 IF 문으로 이동합니다."
            ))
        }

        // MySQL 트리거 생성
        val result = buildMySqlTrigger(triggerInfo, warnings)
        appliedRules.add("Oracle 트리거 → MySQL 트리거 변환: ${triggerInfo.name}")

        return result
    }

    /**
     * Oracle 트리거 → PostgreSQL 변환
     */
    fun convertToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val triggerInfo = parseTrigger(sql) ?: return sql

        // COMPOUND TRIGGER 체크
        if (COMPOUND_TRIGGER_PATTERN.containsMatchIn(sql)) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "PostgreSQL은 COMPOUND TRIGGER를 직접 지원하지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "각 타이밍 포인트별로 별도의 트리거 함수를 생성합니다."
            ))
        }

        // PostgreSQL 트리거 함수 + 트리거 생성
        val result = buildPostgreSqlTrigger(triggerInfo, warnings)
        appliedRules.add("Oracle 트리거 → PostgreSQL 트리거 함수 변환: ${triggerInfo.name}")

        return result
    }

    /**
     * 트리거 파싱
     */
    private fun parseTrigger(sql: String): TriggerInfo? {
        val nameMatch = TRIGGER_HEADER_PATTERN.find(sql) ?: return null
        val name = nameMatch.groupValues[1]

        val timing = TRIGGER_TIMING_PATTERN.find(sql)?.groupValues?.get(1) ?: "BEFORE"

        val events = mutableListOf<String>()
        val updateColumns = mutableListOf<String>()
        TRIGGER_EVENT_PATTERN.findAll(sql).forEach { match ->
            events.add(match.groupValues[1].uppercase())
            match.groupValues[2].takeIf { it.isNotBlank() }?.let { cols ->
                updateColumns.addAll(cols.split(",").map { it.trim() })
            }
        }

        val tableMatch = ON_TABLE_PATTERN.find(sql)
        val schema = tableMatch?.groupValues?.get(1)
        val tableName = tableMatch?.groupValues?.get(2) ?: return null

        val forEachRow = FOR_EACH_ROW_PATTERN.containsMatchIn(sql)

        val whenCondition = WHEN_PATTERN.find(sql)?.groupValues?.get(1)?.trim()

        val declareBlock = DECLARE_PATTERN.find(sql)?.groupValues?.get(1)?.trim()

        val body = BEGIN_END_PATTERN.find(sql)?.groupValues?.get(1)?.trim() ?: ""

        // REFERENCING 절 파싱
        val refMatch = REFERENCING_PATTERN.find(sql)
        val newAlias = refMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val oldAlias = refMatch?.groupValues?.get(2)?.takeIf { it.isNotBlank() }

        return TriggerInfo(
            name = name,
            timing = timing,
            events = events,
            updateColumns = updateColumns,
            schema = schema,
            tableName = tableName,
            forEachRow = forEachRow,
            whenCondition = whenCondition,
            declareBlock = declareBlock,
            body = body,
            newAlias = newAlias,
            oldAlias = oldAlias
        )
    }

    /**
     * MySQL 트리거 빌드
     */
    private fun buildMySqlTrigger(info: TriggerInfo, warnings: MutableList<ConversionWarning>): String {
        val results = mutableListOf<String>()

        // MySQL은 하나의 트리거에 하나의 이벤트만 가능
        // 여러 이벤트가 있으면 각각 트리거 생성
        for (event in info.events) {
            val triggerName = if (info.events.size > 1) {
                "${info.name}_${event.lowercase()}"
            } else {
                info.name
            }

            val sb = StringBuilder()
            sb.appendLine("DELIMITER //")
            sb.appendLine()
            sb.appendLine("CREATE TRIGGER $triggerName")
            sb.appendLine("${info.timing.uppercase()} $event ON ${info.tableName}")
            sb.appendLine("FOR EACH ROW")
            sb.appendLine("BEGIN")

            // WHEN 조건을 IF로 변환
            var body = convertTriggerBody(info.body, info, DialectType.MYSQL)

            if (info.whenCondition != null) {
                val condition = convertWhenCondition(info.whenCondition, DialectType.MYSQL)
                sb.appendLine("    IF $condition THEN")
                body.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        sb.appendLine("        $line")
                    }
                }
                sb.appendLine("    END IF;")
            } else {
                body.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        sb.appendLine("    $line")
                    }
                }
            }

            sb.appendLine("END//")
            sb.appendLine()
            sb.appendLine("DELIMITER ;")

            results.add(sb.toString())
        }

        if (info.events.size > 1) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL은 트리거당 하나의 이벤트만 지원하므로 ${info.events.size}개의 트리거로 분리되었습니다.",
                severity = WarningSeverity.INFO,
                suggestion = "트리거명: ${info.events.map { "${info.name}_${it.lowercase()}" }.joinToString(", ")}"
            ))
        }

        return results.joinToString("\n")
    }

    /**
     * PostgreSQL 트리거 빌드
     */
    private fun buildPostgreSqlTrigger(info: TriggerInfo, warnings: MutableList<ConversionWarning>): String {
        val functionName = "${info.name}_func"
        val sb = StringBuilder()

        // 1. 트리거 함수 생성
        sb.appendLine("-- 트리거 함수")
        sb.appendLine("CREATE OR REPLACE FUNCTION $functionName()")
        sb.appendLine("RETURNS TRIGGER")
        sb.appendLine("LANGUAGE plpgsql")
        sb.appendLine("AS \$\$")

        // DECLARE 블록
        if (!info.declareBlock.isNullOrBlank()) {
            sb.appendLine("DECLARE")
            val declareConverted = convertDeclareBlock(info.declareBlock)
            declareConverted.lines().forEach { line ->
                if (line.isNotBlank()) {
                    sb.appendLine("    $line")
                }
            }
        }

        sb.appendLine("BEGIN")

        // 트리거 본문 변환
        val body = convertTriggerBody(info.body, info, DialectType.POSTGRESQL)
        body.lines().forEach { line ->
            if (line.isNotBlank()) {
                sb.appendLine("    $line")
            }
        }

        // RETURN 추가
        val returnStatement = determineReturnStatement(info)
        sb.appendLine("    $returnStatement")

        sb.appendLine("END;")
        sb.appendLine("\$\$;")
        sb.appendLine()

        // 2. 트리거 생성
        sb.appendLine("-- 트리거 정의")
        sb.append("CREATE OR REPLACE TRIGGER ${info.name}")
        sb.appendLine()
        sb.append("${info.timing.uppercase()} ")
        sb.append(info.events.joinToString(" OR "))
        sb.appendLine()
        sb.appendLine("ON ${info.tableName}")

        if (info.forEachRow) {
            sb.appendLine("FOR EACH ROW")
        }

        // WHEN 조건
        if (info.whenCondition != null) {
            val condition = convertWhenCondition(info.whenCondition, DialectType.POSTGRESQL)
            sb.appendLine("WHEN ($condition)")
        }

        sb.appendLine("EXECUTE FUNCTION $functionName();")

        return sb.toString()
    }

    /**
     * 트리거 본문 변환
     */
    private fun convertTriggerBody(body: String, info: TriggerInfo, target: DialectType): String {
        var result = body

        // :NEW/:OLD 참조 변환
        result = NEW_OLD_REFERENCE_PATTERN.replace(result) { match ->
            val refType = match.groupValues[2].uppercase()
            val column = match.groupValues[3]

            // 커스텀 alias 처리
            val actualRef = when (refType) {
                "NEW" -> info.newAlias ?: "NEW"
                "OLD" -> info.oldAlias ?: "OLD"
                else -> refType
            }

            "$actualRef.$column"
        }

        // INSERTING/UPDATING/DELETING 변환
        result = when (target) {
            DialectType.MYSQL -> convertTriggerPredicatesForMySql(result, info)
            DialectType.POSTGRESQL -> convertTriggerPredicatesForPostgreSql(result)
            else -> result
        }

        // RAISE_APPLICATION_ERROR 변환
        result = RAISE_ERROR_PATTERN.replace(result) { match ->
            val errorCode = match.groupValues[1]
            val message = match.groupValues[2]

            when (target) {
                DialectType.MYSQL -> "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '$message'"
                DialectType.POSTGRESQL -> "RAISE EXCEPTION '$message' USING ERRCODE = '$errorCode'"
                else -> match.value
            }
        }

        // SYSDATE 변환
        result = result.replace(Regex("""\bSYSDATE\b""", RegexOption.IGNORE_CASE)) {
            when (target) {
                DialectType.MYSQL -> "NOW()"
                DialectType.POSTGRESQL -> "CURRENT_TIMESTAMP"
                else -> it.value
            }
        }

        // NVL 변환
        result = result.replace(Regex("""NVL\s*\(""", RegexOption.IGNORE_CASE)) {
            when (target) {
                DialectType.MYSQL -> "IFNULL("
                DialectType.POSTGRESQL -> "COALESCE("
                else -> it.value
            }
        }

        return result
    }

    /**
     * MySQL용 트리거 조건부 변환
     * MySQL은 INSERTING/UPDATING/DELETING을 지원하지 않음
     */
    private fun convertTriggerPredicatesForMySql(body: String, info: TriggerInfo): String {
        // 단일 이벤트 트리거에서는 해당 조건을 TRUE/FALSE로 치환
        val event = info.events.firstOrNull()?.uppercase() ?: return body

        return body
            .replace(Regex("""\bINSERTING\b""", RegexOption.IGNORE_CASE)) {
                if (event == "INSERT") "TRUE" else "FALSE"
            }
            .replace(Regex("""\bUPDATING\b""", RegexOption.IGNORE_CASE)) {
                if (event == "UPDATE") "TRUE" else "FALSE"
            }
            .replace(Regex("""\bDELETING\b""", RegexOption.IGNORE_CASE)) {
                if (event == "DELETE") "TRUE" else "FALSE"
            }
    }

    /**
     * PostgreSQL용 트리거 조건부 변환
     */
    private fun convertTriggerPredicatesForPostgreSql(body: String): String {
        return body
            .replace(Regex("""\bINSERTING\b""", RegexOption.IGNORE_CASE), "TG_OP = 'INSERT'")
            .replace(Regex("""\bUPDATING\b""", RegexOption.IGNORE_CASE), "TG_OP = 'UPDATE'")
            .replace(Regex("""\bDELETING\b""", RegexOption.IGNORE_CASE), "TG_OP = 'DELETE'")
    }

    /**
     * WHEN 조건 변환
     */
    private fun convertWhenCondition(condition: String, target: DialectType): String {
        var result = condition

        // :NEW/:OLD 참조 변환
        result = NEW_OLD_REFERENCE_PATTERN.replace(result) { match ->
            val refType = match.groupValues[2].uppercase()
            val column = match.groupValues[3]
            "$refType.$column"
        }

        return result
    }

    /**
     * DECLARE 블록 변환
     */
    private fun convertDeclareBlock(declare: String): String {
        var result = declare

        // VARCHAR2 → VARCHAR
        result = result.replace(Regex("""VARCHAR2\s*\((\d+)\)""", RegexOption.IGNORE_CASE)) { match ->
            "VARCHAR(${match.groupValues[1]})"
        }

        // NUMBER → NUMERIC
        result = result.replace(Regex("""\bNUMBER\b""", RegexOption.IGNORE_CASE), "NUMERIC")

        return result
    }

    /**
     * 적절한 RETURN 문 결정
     */
    private fun determineReturnStatement(info: TriggerInfo): String {
        val timing = info.timing.uppercase()

        return when {
            timing.contains("INSTEAD") -> "RETURN NEW;" // INSTEAD OF의 경우
            timing == "BEFORE" -> "RETURN NEW;" // BEFORE 트리거
            timing == "AFTER" -> "RETURN NULL;" // AFTER 트리거
            else -> "RETURN NEW;"
        }
    }

    /**
     * 트리거 정보 데이터 클래스
     */
    data class TriggerInfo(
        val name: String,
        val timing: String,
        val events: List<String>,
        val updateColumns: List<String>,
        val schema: String?,
        val tableName: String,
        val forEachRow: Boolean,
        val whenCondition: String?,
        val declareBlock: String?,
        val body: String,
        val newAlias: String?,
        val oldAlias: String?
    )

    /**
     * 트리거 추출
     */
    fun extractTriggers(sql: String): List<String> {
        val triggers = mutableListOf<String>()
        val pattern = Regex(
            """CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+.+?END\s*;""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        pattern.findAll(sql).forEach { match ->
            triggers.add(match.value)
        }

        return triggers
    }

    /**
     * 트리거 이름 추출
     */
    fun extractTriggerName(sql: String): String? {
        return TRIGGER_HEADER_PATTERN.find(sql)?.groupValues?.get(1)
    }

    /**
     * 트리거 유효성 검사
     */
    fun validateTrigger(sql: String): List<String> {
        val errors = mutableListOf<String>()

        if (!TRIGGER_HEADER_PATTERN.containsMatchIn(sql)) {
            errors.add("트리거 헤더를 찾을 수 없습니다.")
        }

        if (!ON_TABLE_PATTERN.containsMatchIn(sql)) {
            errors.add("ON 테이블 절을 찾을 수 없습니다.")
        }

        if (!BEGIN_END_PATTERN.containsMatchIn(sql)) {
            errors.add("BEGIN...END 블록을 찾을 수 없습니다.")
        }

        return errors
    }
}
