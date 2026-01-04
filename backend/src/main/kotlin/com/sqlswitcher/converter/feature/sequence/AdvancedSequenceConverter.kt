package com.sqlswitcher.converter.feature.sequence

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * 고급 시퀀스 변환기
 *
 * Oracle/PostgreSQL 시퀀스를 다른 DB로 변환
 * - CREATE SEQUENCE 전체 옵션 파싱
 * - AUTO_INCREMENT / SERIAL 변환
 * - Identity 컬럼 변환
 * - 테이블 기반 시퀀스 에뮬레이션 (MySQL < 8.0)
 */
object AdvancedSequenceConverter {

    /**
     * CREATE SEQUENCE 패턴
     */
    private val CREATE_SEQUENCE_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?SEQUENCE\s+(?:(\w+)\.)?(\w+)(.*)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * DROP SEQUENCE 패턴
     */
    private val DROP_SEQUENCE_PATTERN = Regex(
        """DROP\s+SEQUENCE\s+(?:IF\s+EXISTS\s+)?(?:(\w+)\.)?(\w+)(?:\s+CASCADE)?;?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * ALTER SEQUENCE 패턴
     */
    private val ALTER_SEQUENCE_PATTERN = Regex(
        """ALTER\s+SEQUENCE\s+(?:(\w+)\.)?(\w+)(.*)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * SEQUENCE.NEXTVAL 패턴
     */
    private val NEXTVAL_PATTERN = Regex(
        """(\w+)\.NEXTVAL""",
        RegexOption.IGNORE_CASE
    )

    /**
     * SEQUENCE.CURRVAL 패턴
     */
    private val CURRVAL_PATTERN = Regex(
        """(\w+)\.CURRVAL""",
        RegexOption.IGNORE_CASE
    )

    /**
     * nextval('seq') PostgreSQL 패턴
     */
    private val PG_NEXTVAL_PATTERN = Regex(
        """nextval\s*\(\s*'([^']+)'\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * currval('seq') PostgreSQL 패턴
     */
    private val PG_CURRVAL_PATTERN = Regex(
        """currval\s*\(\s*'([^']+)'\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 시퀀스 옵션 패턴들
     */
    private val START_WITH_PATTERN = Regex("""START\s+WITH\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val INCREMENT_BY_PATTERN = Regex("""INCREMENT\s+BY\s+(-?\d+)""", RegexOption.IGNORE_CASE)
    private val MINVALUE_PATTERN = Regex("""MINVALUE\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val MAXVALUE_PATTERN = Regex("""MAXVALUE\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val CACHE_PATTERN = Regex("""CACHE\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val CYCLE_PATTERN = Regex("""(?<!NO\s)(?<!NO)CYCLE\b""", RegexOption.IGNORE_CASE)
    private val NOCYCLE_PATTERN = Regex("""NO\s*CYCLE""", RegexOption.IGNORE_CASE)
    private val ORDER_PATTERN = Regex("""(?<!NO\s)(?<!NO)ORDER\b""", RegexOption.IGNORE_CASE)
    private val NOORDER_PATTERN = Regex("""NO\s*ORDER""", RegexOption.IGNORE_CASE)
    private val NOMINVALUE_PATTERN = Regex("""NO\s*MINVALUE""", RegexOption.IGNORE_CASE)
    private val NOMAXVALUE_PATTERN = Regex("""NO\s*MAXVALUE""", RegexOption.IGNORE_CASE)
    private val NOCACHE_PATTERN = Regex("""NO\s*CACHE""", RegexOption.IGNORE_CASE)

    /**
     * 시퀀스 변환
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // CREATE SEQUENCE 변환
        result = CREATE_SEQUENCE_PATTERN.replace(result) { match ->
            val schema = match.groupValues[1].ifEmpty { null }
            val seqName = match.groupValues[2]
            val options = match.groupValues[3]

            val seqInfo = parseSequenceOptions(options)

            when (targetDialect) {
                DialectType.MYSQL -> {
                    appliedRules.add("CREATE SEQUENCE $seqName → MySQL 시퀀스")
                    convertCreateSequenceToMySql(schema, seqName, seqInfo, warnings)
                }
                DialectType.POSTGRESQL -> {
                    appliedRules.add("CREATE SEQUENCE $seqName → PostgreSQL 시퀀스")
                    convertCreateSequenceToPostgreSql(schema, seqName, seqInfo, warnings)
                }
                DialectType.ORACLE -> {
                    appliedRules.add("CREATE SEQUENCE $seqName → Oracle 시퀀스")
                    convertCreateSequenceToOracle(schema, seqName, seqInfo, warnings)
                }
                else -> match.value
            }
        }

        // DROP SEQUENCE 변환
        result = DROP_SEQUENCE_PATTERN.replace(result) { match ->
            val schema = match.groupValues[1].ifEmpty { null }
            val seqName = match.groupValues[2]

            when (targetDialect) {
                DialectType.MYSQL -> {
                    appliedRules.add("DROP SEQUENCE $seqName → MySQL")
                    "DROP SEQUENCE IF EXISTS ${schema?.let { "$it." } ?: ""}$seqName;"
                }
                DialectType.POSTGRESQL -> {
                    appliedRules.add("DROP SEQUENCE $seqName → PostgreSQL")
                    "DROP SEQUENCE IF EXISTS ${schema?.let { "$it." } ?: ""}$seqName CASCADE;"
                }
                DialectType.ORACLE -> {
                    appliedRules.add("DROP SEQUENCE $seqName → Oracle")
                    "DROP SEQUENCE ${schema?.let { "$it." } ?: ""}$seqName;"
                }
                else -> match.value
            }
        }

        // SEQUENCE.NEXTVAL 변환
        if (sourceDialect == DialectType.ORACLE) {
            result = NEXTVAL_PATTERN.replace(result) { match ->
                val seqName = match.groupValues[1]
                when (targetDialect) {
                    DialectType.POSTGRESQL -> {
                        appliedRules.add("$seqName.NEXTVAL → nextval('$seqName')")
                        "nextval('$seqName')"
                    }
                    DialectType.MYSQL -> {
                        appliedRules.add("$seqName.NEXTVAL → MySQL NEXTVAL($seqName)")
                        "NEXTVAL($seqName)"
                    }
                    else -> match.value
                }
            }

            result = CURRVAL_PATTERN.replace(result) { match ->
                val seqName = match.groupValues[1]
                when (targetDialect) {
                    DialectType.POSTGRESQL -> {
                        appliedRules.add("$seqName.CURRVAL → currval('$seqName')")
                        "currval('$seqName')"
                    }
                    DialectType.MYSQL -> {
                        appliedRules.add("$seqName.CURRVAL → MySQL LASTVAL($seqName)")
                        "LASTVAL($seqName)"
                    }
                    else -> match.value
                }
            }
        }

        // PostgreSQL nextval() → Oracle/MySQL 변환
        if (sourceDialect == DialectType.POSTGRESQL) {
            result = PG_NEXTVAL_PATTERN.replace(result) { match ->
                val seqName = match.groupValues[1]
                when (targetDialect) {
                    DialectType.ORACLE -> {
                        appliedRules.add("nextval('$seqName') → $seqName.NEXTVAL")
                        "$seqName.NEXTVAL"
                    }
                    DialectType.MYSQL -> {
                        appliedRules.add("nextval('$seqName') → NEXTVAL($seqName)")
                        "NEXTVAL($seqName)"
                    }
                    else -> match.value
                }
            }

            result = PG_CURRVAL_PATTERN.replace(result) { match ->
                val seqName = match.groupValues[1]
                when (targetDialect) {
                    DialectType.ORACLE -> {
                        appliedRules.add("currval('$seqName') → $seqName.CURRVAL")
                        "$seqName.CURRVAL"
                    }
                    DialectType.MYSQL -> {
                        appliedRules.add("currval('$seqName') → LASTVAL($seqName)")
                        "LASTVAL($seqName)"
                    }
                    else -> match.value
                }
            }
        }

        return result
    }

    /**
     * 시퀀스 옵션 파싱
     */
    private fun parseSequenceOptions(options: String): SequenceInfo {
        val startWith = START_WITH_PATTERN.find(options)?.groupValues?.get(1)?.toLongOrNull() ?: 1
        val incrementBy = INCREMENT_BY_PATTERN.find(options)?.groupValues?.get(1)?.toLongOrNull() ?: 1
        val minValue = MINVALUE_PATTERN.find(options)?.groupValues?.get(1)?.toLongOrNull()
        val maxValue = MAXVALUE_PATTERN.find(options)?.groupValues?.get(1)?.toLongOrNull()
        val cache = CACHE_PATTERN.find(options)?.groupValues?.get(1)?.toIntOrNull()
        val cycle = CYCLE_PATTERN.containsMatchIn(options) && !NOCYCLE_PATTERN.containsMatchIn(options)
        val order = ORDER_PATTERN.containsMatchIn(options) && !NOORDER_PATTERN.containsMatchIn(options)
        val noMinValue = NOMINVALUE_PATTERN.containsMatchIn(options)
        val noMaxValue = NOMAXVALUE_PATTERN.containsMatchIn(options)
        val noCache = NOCACHE_PATTERN.containsMatchIn(options)

        return SequenceInfo(
            startWith = startWith,
            incrementBy = incrementBy,
            minValue = if (noMinValue) null else minValue,
            maxValue = if (noMaxValue) null else maxValue,
            cache = if (noCache) null else cache,
            cycle = cycle,
            order = order
        )
    }

    /**
     * MySQL CREATE SEQUENCE 변환
     */
    private fun convertCreateSequenceToMySql(
        schema: String?,
        seqName: String,
        info: SequenceInfo,
        warnings: MutableList<ConversionWarning>
    ): String {
        val fullName = schema?.let { "$it.$seqName" } ?: seqName

        // MySQL 8.0+ 시퀀스 문법
        val parts = mutableListOf<String>()
        parts.add("CREATE SEQUENCE $fullName")

        if (info.startWith != 1L) parts.add("START WITH ${info.startWith}")
        if (info.incrementBy != 1L) parts.add("INCREMENT BY ${info.incrementBy}")
        info.minValue?.let { parts.add("MINVALUE $it") }
        info.maxValue?.let { parts.add("MAXVALUE $it") }
        info.cache?.let { parts.add("CACHE $it") }
        if (info.cycle) parts.add("CYCLE") else parts.add("NOCYCLE")

        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "MySQL 8.0+ 시퀀스 문법으로 변환됩니다. 8.0 미만에서는 테이블 기반 시뮬레이션이 필요합니다.",
            severity = WarningSeverity.INFO,
            suggestion = "MySQL 8.0 미만에서는 generateMySqlSequenceEmulation() 메서드를 사용하세요."
        ))

        return parts.joinToString(" ") + ";"
    }

    /**
     * PostgreSQL CREATE SEQUENCE 변환
     */
    private fun convertCreateSequenceToPostgreSql(
        schema: String?,
        seqName: String,
        info: SequenceInfo,
        warnings: MutableList<ConversionWarning>
    ): String {
        val fullName = schema?.let { "$it.$seqName" } ?: seqName

        val parts = mutableListOf<String>()
        parts.add("CREATE SEQUENCE $fullName")

        if (info.startWith != 1L) parts.add("START WITH ${info.startWith}")
        if (info.incrementBy != 1L) parts.add("INCREMENT BY ${info.incrementBy}")
        if (info.minValue != null) parts.add("MINVALUE ${info.minValue}") else parts.add("NO MINVALUE")
        if (info.maxValue != null) parts.add("MAXVALUE ${info.maxValue}") else parts.add("NO MAXVALUE")
        info.cache?.let { parts.add("CACHE $it") }
        if (info.cycle) parts.add("CYCLE") else parts.add("NO CYCLE")

        return parts.joinToString(" ") + ";"
    }

    /**
     * Oracle CREATE SEQUENCE 변환
     */
    private fun convertCreateSequenceToOracle(
        schema: String?,
        seqName: String,
        info: SequenceInfo,
        warnings: MutableList<ConversionWarning>
    ): String {
        val fullName = schema?.let { "$it.$seqName" } ?: seqName

        val parts = mutableListOf<String>()
        parts.add("CREATE SEQUENCE $fullName")

        if (info.startWith != 1L) parts.add("START WITH ${info.startWith}")
        if (info.incrementBy != 1L) parts.add("INCREMENT BY ${info.incrementBy}")
        if (info.minValue != null) parts.add("MINVALUE ${info.minValue}") else parts.add("NOMINVALUE")
        if (info.maxValue != null) parts.add("MAXVALUE ${info.maxValue}") else parts.add("NOMAXVALUE")
        info.cache?.let { parts.add("CACHE $it") }
        if (info.cycle) parts.add("CYCLE") else parts.add("NOCYCLE")
        if (info.order) parts.add("ORDER") else parts.add("NOORDER")

        return parts.joinToString(" ") + ";"
    }

    /**
     * MySQL 8.0 미만용 시퀀스 테이블 에뮬레이션 생성
     */
    fun generateMySqlSequenceEmulation(seqName: String, info: SequenceInfo): String {
        return """
-- Sequence emulation table for $seqName
CREATE TABLE ${seqName}_seq (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stub CHAR(1) NOT NULL DEFAULT ''
) ENGINE=InnoDB;

-- Initialize with start value
ALTER TABLE ${seqName}_seq AUTO_INCREMENT = ${info.startWith};

-- Function to get next value
DROP FUNCTION IF EXISTS ${seqName}_nextval;
DELIMITER //
CREATE FUNCTION ${seqName}_nextval() RETURNS BIGINT
DETERMINISTIC
BEGIN
    INSERT INTO ${seqName}_seq (stub) VALUES ('');
    RETURN LAST_INSERT_ID();
END //
DELIMITER ;

-- Function to get current value
DROP FUNCTION IF EXISTS ${seqName}_currval;
DELIMITER //
CREATE FUNCTION ${seqName}_currval() RETURNS BIGINT
READS SQL DATA
BEGIN
    DECLARE val BIGINT;
    SELECT MAX(id) INTO val FROM ${seqName}_seq;
    RETURN val;
END //
DELIMITER ;
""".trim()
    }

    /**
     * Identity 컬럼을 시퀀스로 변환
     */
    fun convertIdentityToSequence(
        tableName: String,
        columnName: String,
        targetDialect: DialectType,
        startWith: Long = 1,
        incrementBy: Long = 1
    ): String {
        val seqName = "${tableName}_${columnName}_seq"

        return when (targetDialect) {
            DialectType.ORACLE -> {
                """
-- Create sequence for identity column
CREATE SEQUENCE $seqName
    START WITH $startWith
    INCREMENT BY $incrementBy
    NOCACHE;

-- Create trigger to populate column
CREATE OR REPLACE TRIGGER ${tableName}_bi
BEFORE INSERT ON $tableName
FOR EACH ROW
BEGIN
    IF :NEW.$columnName IS NULL THEN
        :NEW.$columnName := $seqName.NEXTVAL;
    END IF;
END;
/""".trim()
            }
            DialectType.POSTGRESQL -> {
                """
-- Create sequence for identity column
CREATE SEQUENCE $seqName
    START WITH $startWith
    INCREMENT BY $incrementBy;

-- Set default value using sequence
ALTER TABLE $tableName
ALTER COLUMN $columnName SET DEFAULT nextval('$seqName');

-- Set sequence ownership
ALTER SEQUENCE $seqName OWNED BY $tableName.$columnName;""".trim()
            }
            DialectType.MYSQL -> {
                """
-- MySQL uses AUTO_INCREMENT, no separate sequence needed
ALTER TABLE $tableName
MODIFY COLUMN $columnName BIGINT AUTO_INCREMENT;

-- Set auto_increment starting value
ALTER TABLE $tableName AUTO_INCREMENT = $startWith;""".trim()
            }
            else -> ""
        }
    }

    /**
     * AUTO_INCREMENT/SERIAL을 시퀀스 참조로 변환
     */
    fun convertAutoIncrementToSequence(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // MySQL AUTO_INCREMENT → Oracle/PostgreSQL 시퀀스
        val autoIncrementPattern = Regex(
            """(\w+)\s+(\w+)\s+AUTO_INCREMENT""",
            RegexOption.IGNORE_CASE
        )

        result = autoIncrementPattern.replace(result) { match ->
            val columnName = match.groupValues[1]
            val dataType = match.groupValues[2]

            when (targetDialect) {
                DialectType.ORACLE -> {
                    appliedRules.add("AUTO_INCREMENT → Oracle GENERATED ALWAYS AS IDENTITY")
                    "$columnName NUMBER GENERATED ALWAYS AS IDENTITY"
                }
                DialectType.POSTGRESQL -> {
                    appliedRules.add("AUTO_INCREMENT → PostgreSQL SERIAL")
                    // SERIAL은 BIGINT/INT에 맞게 변환
                    val serialType = when (dataType.uppercase()) {
                        "BIGINT" -> "BIGSERIAL"
                        "SMALLINT" -> "SMALLSERIAL"
                        else -> "SERIAL"
                    }
                    "$columnName $serialType"
                }
                else -> match.value
            }
        }

        // PostgreSQL SERIAL → Oracle/MySQL
        val serialPattern = Regex(
            """(\w+)\s+(BIG)?SERIAL""",
            RegexOption.IGNORE_CASE
        )

        result = serialPattern.replace(result) { match ->
            val columnName = match.groupValues[1]
            val isBig = match.groupValues[2].isNotEmpty()

            when (targetDialect) {
                DialectType.ORACLE -> {
                    appliedRules.add("SERIAL → Oracle GENERATED AS IDENTITY")
                    val dataType = if (isBig) "NUMBER(19)" else "NUMBER(10)"
                    "$columnName $dataType GENERATED ALWAYS AS IDENTITY"
                }
                DialectType.MYSQL -> {
                    appliedRules.add("SERIAL → MySQL AUTO_INCREMENT")
                    val dataType = if (isBig) "BIGINT" else "INT"
                    "$columnName $dataType AUTO_INCREMENT"
                }
                else -> match.value
            }
        }

        return result
    }

    /**
     * 시퀀스 관련 문인지 확인
     */
    fun isSequenceStatement(sql: String): Boolean {
        val upper = sql.uppercase()
        return upper.contains("SEQUENCE") &&
               (upper.contains("CREATE") || upper.contains("DROP") || upper.contains("ALTER"))
    }

    /**
     * 시퀀스 참조가 있는지 확인
     */
    fun hasSequenceReference(sql: String): Boolean {
        return NEXTVAL_PATTERN.containsMatchIn(sql) ||
               CURRVAL_PATTERN.containsMatchIn(sql) ||
               PG_NEXTVAL_PATTERN.containsMatchIn(sql) ||
               PG_CURRVAL_PATTERN.containsMatchIn(sql)
    }

    /**
     * 참조된 시퀀스 이름 목록 추출
     */
    fun getReferencedSequences(sql: String): List<String> {
        val sequences = mutableSetOf<String>()

        NEXTVAL_PATTERN.findAll(sql).forEach { sequences.add(it.groupValues[1]) }
        CURRVAL_PATTERN.findAll(sql).forEach { sequences.add(it.groupValues[1]) }
        PG_NEXTVAL_PATTERN.findAll(sql).forEach { sequences.add(it.groupValues[1]) }
        PG_CURRVAL_PATTERN.findAll(sql).forEach { sequences.add(it.groupValues[1]) }

        return sequences.toList()
    }

    /**
     * 시퀀스 정보
     */
    data class SequenceInfo(
        val startWith: Long = 1,
        val incrementBy: Long = 1,
        val minValue: Long? = null,
        val maxValue: Long? = null,
        val cache: Int? = null,
        val cycle: Boolean = false,
        val order: Boolean = false
    )
}
