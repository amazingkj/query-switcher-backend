package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.core.*
import org.springframework.stereotype.Service

/**
 * 시퀀스 정보
 */
data class SequenceInfo(
    val name: String,
    val startWith: Long = 1,
    val incrementBy: Long = 1,
    val minValue: Long? = null,
    val maxValue: Long? = null,
    val cache: Long? = null,
    val cycle: Boolean = false,
    val noMinValue: Boolean = false,
    val noMaxValue: Boolean = false,
    val noCache: Boolean = false
) {
    companion object {
        fun parse(sql: String): SequenceInfo {
            val upperSql = sql.uppercase()

            // 시퀀스명 추출
            val nameRegex = """CREATE\s+SEQUENCE\s+"?(\w+)"?(?:\."?(\w+)"?)?""".toRegex(RegexOption.IGNORE_CASE)
            val nameMatch = nameRegex.find(sql)
            val name = nameMatch?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
                ?: nameMatch?.groupValues?.get(1)
                ?: "UNKNOWN_SEQ"

            // START WITH
            val startWith = Regex("""START\s+WITH\s+(\d+)""", RegexOption.IGNORE_CASE)
                .find(sql)?.groupValues?.get(1)?.toLongOrNull() ?: 1

            // INCREMENT BY
            val incrementBy = Regex("""INCREMENT\s+BY\s+(-?\d+)""", RegexOption.IGNORE_CASE)
                .find(sql)?.groupValues?.get(1)?.toLongOrNull() ?: 1

            // MINVALUE
            val noMinValue = upperSql.contains("NOMINVALUE")
            val minValue = if (!noMinValue) {
                Regex("""MINVALUE\s+(\d+)""", RegexOption.IGNORE_CASE)
                    .find(sql)?.groupValues?.get(1)?.toLongOrNull()
            } else null

            // MAXVALUE
            val noMaxValue = upperSql.contains("NOMAXVALUE")
            val maxValue = if (!noMaxValue) {
                Regex("""MAXVALUE\s+(\d+)""", RegexOption.IGNORE_CASE)
                    .find(sql)?.groupValues?.get(1)?.toLongOrNull()
            } else null

            // CACHE
            val noCache = upperSql.contains("NOCACHE")
            val cache = if (!noCache) {
                Regex("""CACHE\s+(\d+)""", RegexOption.IGNORE_CASE)
                    .find(sql)?.groupValues?.get(1)?.toLongOrNull()
            } else null

            // CYCLE
            val cycle = upperSql.contains("CYCLE") && !upperSql.contains("NOCYCLE")

            return SequenceInfo(
                name = name,
                startWith = startWith,
                incrementBy = incrementBy,
                minValue = minValue,
                maxValue = maxValue,
                cache = cache,
                cycle = cycle,
                noMinValue = noMinValue,
                noMaxValue = noMaxValue,
                noCache = noCache
            )
        }
    }
}

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

        val seqInfo = SequenceInfo.parse(sql)

        return when (targetDialect) {
            DialectType.MYSQL -> convertToMySql(seqInfo, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertToPostgreSql(seqInfo, warnings, appliedRules)
            DialectType.ORACLE -> convertToOracle(seqInfo, schemaOwner, appliedRules)
            DialectType.TIBERO -> convertToTibero(seqInfo, schemaOwner, appliedRules)
        }
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

        when {
            // Oracle/Tibero → MySQL
            (sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.TIBERO) && targetDialect == DialectType.MYSQL -> {
                result = Regex("""(\w+)\.NEXTVAL""", RegexOption.IGNORE_CASE).replace(result) { match ->
                    appliedRules.add("NEXTVAL → MySQL 함수로 변환")
                    "${match.groupValues[1]}_nextval()"
                }
                result = Regex("""(\w+)\.CURRVAL""", RegexOption.IGNORE_CASE).replace(result) { match ->
                    appliedRules.add("CURRVAL → MySQL 함수로 변환")
                    "${match.groupValues[1]}_currval()"
                }
            }

            // Oracle/Tibero → PostgreSQL
            (sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.TIBERO) && targetDialect == DialectType.POSTGRESQL -> {
                result = Regex("""(\w+)\.NEXTVAL""", RegexOption.IGNORE_CASE).replace(result) { match ->
                    appliedRules.add("NEXTVAL → PostgreSQL nextval()로 변환")
                    "nextval('${match.groupValues[1]}')"
                }
                result = Regex("""(\w+)\.CURRVAL""", RegexOption.IGNORE_CASE).replace(result) { match ->
                    appliedRules.add("CURRVAL → PostgreSQL currval()로 변환")
                    "currval('${match.groupValues[1]}')"
                }
            }

            // MySQL → Oracle/Tibero
            sourceDialect == DialectType.MYSQL && (targetDialect == DialectType.ORACLE || targetDialect == DialectType.TIBERO) -> {
                result = Regex("""(\w+)_nextval\s*\(\s*\)""", RegexOption.IGNORE_CASE).replace(result) { match ->
                    appliedRules.add("MySQL 시퀀스 함수 → NEXTVAL로 변환")
                    "${match.groupValues[1]}.NEXTVAL"
                }
                result = Regex("""(\w+)_currval\s*\(\s*\)""", RegexOption.IGNORE_CASE).replace(result) { match ->
                    appliedRules.add("MySQL 시퀀스 함수 → CURRVAL로 변환")
                    "${match.groupValues[1]}.CURRVAL"
                }
            }

            // PostgreSQL → Oracle/Tibero
            sourceDialect == DialectType.POSTGRESQL && (targetDialect == DialectType.ORACLE || targetDialect == DialectType.TIBERO) -> {
                result = Regex("""nextval\s*\(\s*'(\w+)'\s*\)""", RegexOption.IGNORE_CASE).replace(result) { match ->
                    appliedRules.add("PostgreSQL nextval() → NEXTVAL로 변환")
                    "${match.groupValues[1]}.NEXTVAL"
                }
                result = Regex("""currval\s*\(\s*'(\w+)'\s*\)""", RegexOption.IGNORE_CASE).replace(result) { match ->
                    appliedRules.add("PostgreSQL currval() → CURRVAL로 변환")
                    "${match.groupValues[1]}.CURRVAL"
                }
            }

            // PostgreSQL → MySQL
            sourceDialect == DialectType.POSTGRESQL && targetDialect == DialectType.MYSQL -> {
                result = Regex("""nextval\s*\(\s*'(\w+)'\s*\)""", RegexOption.IGNORE_CASE).replace(result) { match ->
                    appliedRules.add("PostgreSQL nextval() → MySQL 함수로 변환")
                    "${match.groupValues[1]}_nextval()"
                }
                result = Regex("""currval\s*\(\s*'(\w+)'\s*\)""", RegexOption.IGNORE_CASE).replace(result) { match ->
                    appliedRules.add("PostgreSQL currval() → MySQL 함수로 변환")
                    "${match.groupValues[1]}_currval()"
                }
            }

            // MySQL → PostgreSQL
            sourceDialect == DialectType.MYSQL && targetDialect == DialectType.POSTGRESQL -> {
                result = Regex("""(\w+)_nextval\s*\(\s*\)""", RegexOption.IGNORE_CASE).replace(result) { match ->
                    appliedRules.add("MySQL 시퀀스 함수 → PostgreSQL nextval()로 변환")
                    "nextval('${match.groupValues[1]}')"
                }
                result = Regex("""(\w+)_currval\s*\(\s*\)""", RegexOption.IGNORE_CASE).replace(result) { match ->
                    appliedRules.add("MySQL 시퀀스 함수 → PostgreSQL currval()로 변환")
                    "currval('${match.groupValues[1]}')"
                }
            }
        }

        return result
    }

    /**
     * MySQL 형식 변환 (테이블 + 함수 시뮬레이션)
     */
    private fun convertToMySql(
        seq: SequenceInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "MySQL은 네이티브 시퀀스를 지원하지 않습니다. 테이블 + 함수로 시뮬레이션됩니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "생성된 함수를 사용하여 시퀀스 값을 가져오세요."
        ))

        val result = StringBuilder()

        // 시퀀스 테이블 생성
        result.appendLine("-- MySQL 시퀀스 시뮬레이션: ${seq.name}")
        result.appendLine("CREATE TABLE IF NOT EXISTS `seq_${seq.name}` (")
        result.appendLine("    `current_value` BIGINT NOT NULL DEFAULT ${seq.startWith - seq.incrementBy},")
        result.appendLine("    `increment_by` BIGINT NOT NULL DEFAULT ${seq.incrementBy}")
        result.appendLine(") ENGINE=InnoDB;")
        result.appendLine()
        result.appendLine("INSERT INTO `seq_${seq.name}` (`current_value`, `increment_by`) VALUES (${seq.startWith - seq.incrementBy}, ${seq.incrementBy});")
        result.appendLine()

        // NEXTVAL 함수
        result.appendLine("DELIMITER //")
        result.appendLine("CREATE FUNCTION `${seq.name}_nextval`() RETURNS BIGINT")
        result.appendLine("DETERMINISTIC")
        result.appendLine("MODIFIES SQL DATA")
        result.appendLine("BEGIN")
        result.appendLine("    DECLARE next_val BIGINT;")
        result.appendLine("    UPDATE `seq_${seq.name}` SET `current_value` = `current_value` + `increment_by`;")
        result.appendLine("    SELECT `current_value` INTO next_val FROM `seq_${seq.name}`;")
        result.appendLine("    RETURN next_val;")
        result.appendLine("END //")
        result.appendLine("DELIMITER ;")
        result.appendLine()

        // CURRVAL 함수
        result.appendLine("DELIMITER //")
        result.appendLine("CREATE FUNCTION `${seq.name}_currval`() RETURNS BIGINT")
        result.appendLine("DETERMINISTIC")
        result.appendLine("READS SQL DATA")
        result.appendLine("BEGIN")
        result.appendLine("    DECLARE curr_val BIGINT;")
        result.appendLine("    SELECT `current_value` INTO curr_val FROM `seq_${seq.name}`;")
        result.appendLine("    RETURN curr_val;")
        result.appendLine("END //")
        result.appendLine("DELIMITER ;")

        appliedRules.add("시퀀스 → MySQL 테이블 + 함수 시뮬레이션으로 변환")
        return result.toString()
    }

    /**
     * PostgreSQL 형식 변환
     */
    private fun convertToPostgreSql(
        seq: SequenceInfo,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()

        sb.append("CREATE SEQUENCE \"${seq.name}\"")
        sb.append("\n    START WITH ${seq.startWith}")
        sb.append("\n    INCREMENT BY ${seq.incrementBy}")

        if (seq.minValue != null) {
            sb.append("\n    MINVALUE ${seq.minValue}")
        } else if (seq.noMinValue) {
            sb.append("\n    NO MINVALUE")
        }

        if (seq.maxValue != null) {
            sb.append("\n    MAXVALUE ${seq.maxValue}")
        } else if (seq.noMaxValue) {
            sb.append("\n    NO MAXVALUE")
        }

        if (seq.cache != null && seq.cache > 1) {
            sb.append("\n    CACHE ${seq.cache}")
        }

        if (seq.cycle) {
            sb.append("\n    CYCLE")
        } else {
            sb.append("\n    NO CYCLE")
        }

        appliedRules.add("시퀀스 → PostgreSQL 형식으로 변환")
        return sb.toString()
    }

    /**
     * Oracle 형식 변환
     */
    private fun convertToOracle(
        seq: SequenceInfo,
        schemaOwner: String?,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()

        val fullName = if (schemaOwner != null) "\"$schemaOwner\".\"${seq.name}\"" else "\"${seq.name}\""
        sb.append("CREATE SEQUENCE $fullName")
        sb.append("\n    START WITH ${seq.startWith}")
        sb.append("\n    INCREMENT BY ${seq.incrementBy}")

        if (seq.minValue != null) {
            sb.append("\n    MINVALUE ${seq.minValue}")
        } else {
            sb.append("\n    NOMINVALUE")
        }

        if (seq.maxValue != null) {
            sb.append("\n    MAXVALUE ${seq.maxValue}")
        } else {
            sb.append("\n    NOMAXVALUE")
        }

        if (seq.cache != null && seq.cache > 1) {
            sb.append("\n    CACHE ${seq.cache}")
        } else {
            sb.append("\n    NOCACHE")
        }

        if (seq.cycle) {
            sb.append("\n    CYCLE")
        } else {
            sb.append("\n    NOCYCLE")
        }

        appliedRules.add("시퀀스 → Oracle 형식으로 변환")
        return sb.toString()
    }

    /**
     * Tibero 형식 변환 (Oracle과 동일)
     */
    private fun convertToTibero(
        seq: SequenceInfo,
        schemaOwner: String?,
        appliedRules: MutableList<String>
    ): String {
        appliedRules.add("시퀀스 → Tibero 형식으로 변환 (Oracle 호환)")
        return convertToOracle(seq, schemaOwner, appliedRules)
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
        if (sourceDialect == targetDialect) {
            return sql
        }

        // 시퀀스명 추출
        val nameMatch = Regex("""DROP\s+SEQUENCE\s+"?(\w+)"?(?:\."?(\w+)"?)?""", RegexOption.IGNORE_CASE).find(sql)
        val seqName = nameMatch?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
            ?: nameMatch?.groupValues?.get(1)
            ?: return sql

        return when (targetDialect) {
            DialectType.MYSQL -> {
                appliedRules.add("DROP SEQUENCE → MySQL 시뮬레이션 삭제로 변환")
                """-- MySQL 시퀀스 시뮬레이션 삭제
DROP TABLE IF EXISTS `seq_$seqName`;
DROP FUNCTION IF EXISTS `${seqName}_nextval`;
DROP FUNCTION IF EXISTS `${seqName}_currval`;"""
            }
            DialectType.POSTGRESQL -> {
                appliedRules.add("DROP SEQUENCE → PostgreSQL 형식으로 변환")
                "DROP SEQUENCE IF EXISTS \"$seqName\""
            }
            DialectType.ORACLE, DialectType.TIBERO -> {
                appliedRules.add("DROP SEQUENCE 유지")
                "DROP SEQUENCE \"$seqName\""
            }
        }
    }
}