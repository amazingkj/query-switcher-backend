package com.sqlswitcher.converter.feature.index

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * 고급 인덱스 변환기
 *
 * Oracle/PostgreSQL/MySQL 간 인덱스 변환
 * - CREATE INDEX 전체 옵션 파싱
 * - BITMAP 인덱스 변환
 * - FUNCTION-BASED 인덱스 변환
 * - REVERSE 인덱스 변환
 * - LOCAL/GLOBAL 파티션 인덱스 변환
 * - DROP INDEX 변환
 */
object AdvancedIndexConverter {

    /**
     * CREATE INDEX 패턴 - 컬럼 부분에서 중첩 괄호 지원
     */
    private val CREATE_INDEX_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?(?:(UNIQUE|BITMAP)\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:(\w+)\.)?(\w+)\s+ON\s+(?:(\w+)\.)?(\w+)\s*\((.+?)\)(\s*(?:TABLESPACE|LOCAL|GLOBAL|PARALLEL|COMPRESS|REVERSE|ONLINE|INVISIBLE|NOCOMPRESS|NOREVERSE|LOGGING|NOLOGGING|;).*)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * 간단한 CREATE INDEX 패턴 (옵션 없음)
     */
    private val SIMPLE_CREATE_INDEX_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?(?:(UNIQUE|BITMAP)\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:(\w+)\.)?(\w+)\s+ON\s+(?:(\w+)\.)?(\w+)\s*\((.+?)\)\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * DROP INDEX 패턴
     */
    private val DROP_INDEX_PATTERN = Regex(
        """DROP\s+INDEX\s+(?:IF\s+EXISTS\s+)?(?:(\w+)\.)?(\w+)(?:\s+ON\s+(?:(\w+)\.)?(\w+))?(?:\s+CASCADE)?;?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * ALTER INDEX 패턴
     */
    private val ALTER_INDEX_PATTERN = Regex(
        """ALTER\s+INDEX\s+(?:(\w+)\.)?(\w+)(.*)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * TABLESPACE 패턴
     */
    private val TABLESPACE_PATTERN = Regex(
        """TABLESPACE\s+(\w+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * LOCAL/GLOBAL 파티션 패턴
     */
    private val LOCAL_PATTERN = Regex("""LOCAL\b""", RegexOption.IGNORE_CASE)
    private val GLOBAL_PATTERN = Regex("""GLOBAL\b""", RegexOption.IGNORE_CASE)

    /**
     * PARALLEL 패턴
     */
    private val PARALLEL_PATTERN = Regex(
        """PARALLEL\s*\(?(\d+)\)?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * COMPRESS 패턴
     */
    private val COMPRESS_PATTERN = Regex(
        """COMPRESS\s*(?:(\d+))?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * NOCOMPRESS 패턴
     */
    private val NOCOMPRESS_PATTERN = Regex("""NOCOMPRESS\b""", RegexOption.IGNORE_CASE)

    /**
     * REVERSE 패턴
     */
    private val REVERSE_PATTERN = Regex("""REVERSE\b""", RegexOption.IGNORE_CASE)

    /**
     * NOREVERSE 패턴
     */
    private val NOREVERSE_PATTERN = Regex("""NOREVERSE\b""", RegexOption.IGNORE_CASE)

    /**
     * LOGGING/NOLOGGING 패턴
     */
    private val LOGGING_PATTERN = Regex("""(?:NO)?LOGGING\b""", RegexOption.IGNORE_CASE)

    /**
     * ONLINE 패턴
     */
    private val ONLINE_PATTERN = Regex("""ONLINE\b""", RegexOption.IGNORE_CASE)

    /**
     * COMPUTE STATISTICS 패턴
     */
    private val COMPUTE_STATS_PATTERN = Regex("""COMPUTE\s+STATISTICS""", RegexOption.IGNORE_CASE)

    /**
     * INVISIBLE 패턴
     */
    private val INVISIBLE_PATTERN = Regex("""INVISIBLE\b""", RegexOption.IGNORE_CASE)

    /**
     * 인덱스 변환
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // CREATE INDEX 변환 (옵션이 있는 경우)
        result = CREATE_INDEX_PATTERN.replace(result) { match ->
            convertCreateIndex(match, targetDialect, warnings, appliedRules)
        }

        // CREATE INDEX 변환 (간단한 경우 - 옵션 없음)
        result = SIMPLE_CREATE_INDEX_PATTERN.replace(result) { match ->
            val indexType = match.groupValues[1].uppercase().ifEmpty { null }
            val indexSchema = match.groupValues[2].ifEmpty { null }
            val indexName = match.groupValues[3]
            val tableSchema = match.groupValues[4].ifEmpty { null }
            val tableName = match.groupValues[5]
            val columns = match.groupValues[6]

            val indexInfo = parseIndexOptions("", indexType)

            when (targetDialect) {
                DialectType.MYSQL -> {
                    appliedRules.add("CREATE INDEX $indexName → MySQL 인덱스")
                    convertCreateIndexToMySql(indexSchema, indexName, tableSchema, tableName, columns, indexInfo, warnings)
                }
                DialectType.POSTGRESQL -> {
                    appliedRules.add("CREATE INDEX $indexName → PostgreSQL 인덱스")
                    convertCreateIndexToPostgreSql(indexSchema, indexName, tableSchema, tableName, columns, indexInfo, warnings)
                }
                DialectType.ORACLE -> {
                    appliedRules.add("CREATE INDEX $indexName → Oracle 인덱스")
                    convertCreateIndexToOracle(indexSchema, indexName, tableSchema, tableName, columns, indexInfo, warnings)
                }
                else -> match.value
            }
        }

        // DROP INDEX 변환
        result = DROP_INDEX_PATTERN.replace(result) { match ->
            val indexSchema = match.groupValues[1].ifEmpty { null }
            val indexName = match.groupValues[2]
            val tableSchema = match.groupValues[3].ifEmpty { null }
            val tableName = match.groupValues[4].ifEmpty { null }

            when (targetDialect) {
                DialectType.MYSQL -> {
                    appliedRules.add("DROP INDEX $indexName → MySQL")
                    if (tableName != null) {
                        "DROP INDEX $indexName ON ${tableSchema?.let { "$it." } ?: ""}$tableName;"
                    } else {
                        warnings.add(ConversionWarning(
                            type = WarningType.SYNTAX_DIFFERENCE,
                            message = "MySQL DROP INDEX는 ON 테이블명이 필요합니다.",
                            severity = WarningSeverity.WARNING,
                            suggestion = "DROP INDEX $indexName ON table_name; 형태로 수정이 필요합니다."
                        ))
                        "-- MySQL requires: DROP INDEX $indexName ON <table_name>;\n" +
                        "DROP INDEX ${indexSchema?.let { "$it." } ?: ""}$indexName;"
                    }
                }
                DialectType.POSTGRESQL -> {
                    appliedRules.add("DROP INDEX $indexName → PostgreSQL")
                    "DROP INDEX IF EXISTS ${indexSchema?.let { "$it." } ?: ""}$indexName CASCADE;"
                }
                DialectType.ORACLE -> {
                    appliedRules.add("DROP INDEX $indexName → Oracle")
                    "DROP INDEX ${indexSchema?.let { "$it." } ?: ""}$indexName;"
                }
                else -> match.value
            }
        }

        return result
    }

    /**
     * CREATE INDEX 변환 (옵션 포함)
     */
    private fun convertCreateIndex(
        match: MatchResult,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val indexType = match.groupValues[1].uppercase().ifEmpty { null }
        val indexSchema = match.groupValues[2].ifEmpty { null }
        val indexName = match.groupValues[3]
        val tableSchema = match.groupValues[4].ifEmpty { null }
        val tableName = match.groupValues[5]
        val columns = match.groupValues[6]
        val options = match.groupValues[7]

        val indexInfo = parseIndexOptions(options, indexType)

        return when (targetDialect) {
            DialectType.MYSQL -> {
                appliedRules.add("CREATE INDEX $indexName → MySQL 인덱스")
                convertCreateIndexToMySql(indexSchema, indexName, tableSchema, tableName, columns, indexInfo, warnings)
            }
            DialectType.POSTGRESQL -> {
                appliedRules.add("CREATE INDEX $indexName → PostgreSQL 인덱스")
                convertCreateIndexToPostgreSql(indexSchema, indexName, tableSchema, tableName, columns, indexInfo, warnings)
            }
            DialectType.ORACLE -> {
                appliedRules.add("CREATE INDEX $indexName → Oracle 인덱스")
                convertCreateIndexToOracle(indexSchema, indexName, tableSchema, tableName, columns, indexInfo, warnings)
            }
            else -> match.value
        }
    }

    /**
     * 인덱스 옵션 파싱
     */
    private fun parseIndexOptions(options: String, indexType: String?): IndexInfo {
        val tablespace = TABLESPACE_PATTERN.find(options)?.groupValues?.get(1)
        val isLocal = LOCAL_PATTERN.containsMatchIn(options)
        val isGlobal = GLOBAL_PATTERN.containsMatchIn(options)
        val parallelMatch = PARALLEL_PATTERN.find(options)
        val parallel = parallelMatch?.groupValues?.get(1)?.toIntOrNull()
        val compressMatch = COMPRESS_PATTERN.find(options)
        val compress = when {
            NOCOMPRESS_PATTERN.containsMatchIn(options) -> null
            compressMatch != null -> compressMatch.groupValues[1].toIntOrNull() ?: 1
            else -> null
        }
        val isReverse = REVERSE_PATTERN.containsMatchIn(options) && !NOREVERSE_PATTERN.containsMatchIn(options)
        val isOnline = ONLINE_PATTERN.containsMatchIn(options)
        val isInvisible = INVISIBLE_PATTERN.containsMatchIn(options)

        return IndexInfo(
            isUnique = indexType == "UNIQUE",
            isBitmap = indexType == "BITMAP",
            tablespace = tablespace,
            isLocal = isLocal,
            isGlobal = isGlobal,
            parallel = parallel,
            compress = compress,
            isReverse = isReverse,
            isOnline = isOnline,
            isInvisible = isInvisible
        )
    }

    /**
     * MySQL CREATE INDEX 변환
     */
    private fun convertCreateIndexToMySql(
        indexSchema: String?,
        indexName: String,
        tableSchema: String?,
        tableName: String,
        columns: String,
        info: IndexInfo,
        warnings: MutableList<ConversionWarning>
    ): String {
        val parts = mutableListOf<String>()

        // MySQL은 스키마를 인덱스 이름에 포함하지 않음
        val indexPart = if (info.isUnique) "CREATE UNIQUE INDEX" else "CREATE INDEX"
        parts.add(indexPart)
        parts.add(indexName)
        parts.add("ON")
        parts.add("${tableSchema?.let { "$it." } ?: ""}$tableName")

        // 컬럼 변환 (함수 표현식 처리)
        val convertedColumns = convertColumnsForMySql(columns)
        parts.add("($convertedColumns)")

        // BITMAP 인덱스 경고
        if (info.isBitmap) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "MySQL은 BITMAP 인덱스를 지원하지 않습니다. 일반 B-Tree 인덱스로 변환됩니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "대량의 중복 값이 있는 컬럼에서는 성능이 다를 수 있습니다."
            ))
        }

        // REVERSE 인덱스 경고
        if (info.isReverse) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "MySQL은 REVERSE 인덱스를 지원하지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "시퀀스 기반 키에서의 인서트 경합이 발생할 수 있습니다."
            ))
        }

        // LOCAL/GLOBAL 파티션 인덱스 경고
        if (info.isLocal || info.isGlobal) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL 파티션 인덱스는 Oracle과 다르게 동작합니다.",
                severity = WarningSeverity.INFO,
                suggestion = "MySQL에서는 파티션 테이블의 인덱스가 자동으로 로컬 파티션됩니다."
            ))
        }

        // INVISIBLE 옵션 (MySQL 8.0+)
        if (info.isInvisible) {
            parts.add("INVISIBLE")
        }

        // TABLESPACE 무시 (MySQL InnoDB는 다른 방식)
        if (info.tablespace != null) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL InnoDB에서 TABLESPACE는 다르게 처리됩니다.",
                severity = WarningSeverity.INFO,
                suggestion = "인덱스 전용 테이블스페이스 대신 테이블 테이블스페이스가 사용됩니다."
            ))
        }

        return parts.joinToString(" ") + ";"
    }

    /**
     * PostgreSQL CREATE INDEX 변환
     */
    private fun convertCreateIndexToPostgreSql(
        indexSchema: String?,
        indexName: String,
        tableSchema: String?,
        tableName: String,
        columns: String,
        info: IndexInfo,
        warnings: MutableList<ConversionWarning>
    ): String {
        val parts = mutableListOf<String>()

        val indexPart = if (info.isUnique) "CREATE UNIQUE INDEX" else "CREATE INDEX"
        parts.add(indexPart)

        // CONCURRENTLY 옵션 (ONLINE 대응)
        if (info.isOnline) {
            parts.add("CONCURRENTLY")
        }

        parts.add("IF NOT EXISTS")
        parts.add("${indexSchema?.let { "$it." } ?: ""}$indexName")
        parts.add("ON")
        parts.add("${tableSchema?.let { "$it." } ?: ""}$tableName")

        // 컬럼 변환
        val convertedColumns = convertColumnsForPostgreSql(columns)
        parts.add("($convertedColumns)")

        // BITMAP → BRIN 또는 일반 인덱스
        if (info.isBitmap) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "PostgreSQL은 BITMAP 대신 BRIN 인덱스를 사용할 수 있습니다.",
                severity = WarningSeverity.INFO,
                suggestion = "대용량 테이블에서는 USING BRIN을 고려하세요."
            ))
        }

        // REVERSE 인덱스 경고
        if (info.isReverse) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "PostgreSQL은 REVERSE 인덱스를 지원하지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "해시 파티셔닝을 고려하세요."
            ))
        }

        // TABLESPACE 옵션
        if (info.tablespace != null) {
            parts.add("TABLESPACE ${info.tablespace}")
        }

        return parts.joinToString(" ") + ";"
    }

    /**
     * Oracle CREATE INDEX 변환
     */
    private fun convertCreateIndexToOracle(
        indexSchema: String?,
        indexName: String,
        tableSchema: String?,
        tableName: String,
        columns: String,
        info: IndexInfo,
        warnings: MutableList<ConversionWarning>
    ): String {
        val parts = mutableListOf<String>()

        parts.add("CREATE")
        if (info.isUnique) parts.add("UNIQUE")
        if (info.isBitmap) parts.add("BITMAP")
        parts.add("INDEX")
        parts.add("${indexSchema?.let { "$it." } ?: ""}$indexName")
        parts.add("ON")
        parts.add("${tableSchema?.let { "$it." } ?: ""}$tableName")

        // 컬럼 변환
        val convertedColumns = convertColumnsForOracle(columns)
        parts.add("($convertedColumns)")

        // 옵션들
        if (info.tablespace != null) {
            parts.add("TABLESPACE ${info.tablespace}")
        }

        if (info.isLocal) {
            parts.add("LOCAL")
        } else if (info.isGlobal) {
            parts.add("GLOBAL")
        }

        if (info.parallel != null) {
            parts.add("PARALLEL ${info.parallel}")
        }

        if (info.compress != null) {
            parts.add("COMPRESS ${info.compress}")
        }

        if (info.isReverse) {
            parts.add("REVERSE")
        }

        if (info.isOnline) {
            parts.add("ONLINE")
        }

        if (info.isInvisible) {
            parts.add("INVISIBLE")
        }

        return parts.joinToString(" ") + ";"
    }

    /**
     * MySQL용 컬럼 표현식 변환
     */
    private fun convertColumnsForMySql(columns: String): String {
        // UPPER(), LOWER() 등의 함수 기반 인덱스를 MySQL 표현식으로 변환
        var result = columns

        // NVL → COALESCE
        result = result.replace(Regex("""NVL\s*\(""", RegexOption.IGNORE_CASE), "COALESCE(")

        // SUBSTR → SUBSTRING
        result = result.replace(Regex("""SUBSTR\s*\(""", RegexOption.IGNORE_CASE), "SUBSTRING(")

        // Oracle 표현식 인덱스 → MySQL 생성된 컬럼 또는 함수형 인덱스 (8.0.13+)
        // MySQL 8.0.13+ 지원: 표현식 기반 인덱스는 괄호로 감싸야 함
        val expressionPattern = Regex("""(UPPER|LOWER|TRUNC|ROUND)\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
        result = expressionPattern.replace(result) { match ->
            val func = match.groupValues[1].uppercase()
            val arg = match.groupValues[2]
            when (func) {
                "UPPER", "LOWER" -> "(${func}($arg))"
                "TRUNC" -> "(TRUNCATE($arg, 0))"
                "ROUND" -> "(ROUND($arg))"
                else -> "(${match.value})"
            }
        }

        return result.trim()
    }

    /**
     * PostgreSQL용 컬럼 표현식 변환
     */
    private fun convertColumnsForPostgreSql(columns: String): String {
        var result = columns

        // NVL → COALESCE
        result = result.replace(Regex("""NVL\s*\(""", RegexOption.IGNORE_CASE), "COALESCE(")

        // SUBSTR → SUBSTRING
        result = result.replace(Regex("""SUBSTR\s*\(""", RegexOption.IGNORE_CASE), "SUBSTRING(")

        // TRUNC → TRUNC (PostgreSQL도 지원)
        // DECODE → CASE WHEN
        val decodePattern = Regex(
            """DECODE\s*\(\s*(\w+)\s*,\s*([^)]+)\)""",
            RegexOption.IGNORE_CASE
        )
        result = decodePattern.replace(result) { match ->
            val expr = match.groupValues[1]
            val args = match.groupValues[2].split(",").map { it.trim() }
            if (args.size >= 2) {
                val pairs = args.chunked(2)
                val cases = pairs.dropLast(if (args.size % 2 == 1) 0 else 1)
                    .joinToString(" ") { "WHEN ${it[0]} THEN ${it.getOrElse(1) { "NULL" }}" }
                val elseValue = if (args.size % 2 == 1) " ELSE ${args.last()}" else ""
                "(CASE $expr $cases$elseValue END)"
            } else {
                match.value
            }
        }

        return result.trim()
    }

    /**
     * Oracle용 컬럼 표현식 변환
     */
    private fun convertColumnsForOracle(columns: String): String {
        var result = columns

        // COALESCE → NVL (단순 버전)
        // 사실 Oracle도 COALESCE 지원하므로 변환 불필요
        // SUBSTRING → SUBSTR
        result = result.replace(Regex("""SUBSTRING\s*\(""", RegexOption.IGNORE_CASE), "SUBSTR(")

        return result.trim()
    }

    /**
     * 인덱스 관련 문인지 확인
     */
    fun isIndexStatement(sql: String): Boolean {
        val upper = sql.uppercase()
        return (upper.contains("INDEX") || upper.contains("INDEXES")) &&
               (upper.contains("CREATE") || upper.contains("DROP") || upper.contains("ALTER"))
    }

    /**
     * 인덱스 정보
     */
    data class IndexInfo(
        val isUnique: Boolean = false,
        val isBitmap: Boolean = false,
        val tablespace: String? = null,
        val isLocal: Boolean = false,
        val isGlobal: Boolean = false,
        val parallel: Int? = null,
        val compress: Int? = null,
        val isReverse: Boolean = false,
        val isOnline: Boolean = false,
        val isInvisible: Boolean = false
    )
}
