package com.sqlswitcher.converter.feature.ddl

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import org.springframework.stereotype.Component

/**
 * CREATE INDEX 변환 서비스
 *
 * 각 데이터베이스 방언별 인덱스 문법 변환을 담당합니다.
 */
@Component
class IndexConversionService {

    companion object {
        // Oracle 관련 패턴
        private val TABLESPACE_PATTERN = Regex("""TABLESPACE\s+\w+""", RegexOption.IGNORE_CASE)
        private val STORAGE_OPTIONS_PATTERN = Regex(
            """(PCTFREE|INITRANS|MAXTRANS|STORAGE\s*\([^)]*\)|LOGGING|NOLOGGING|PARALLEL|NOPARALLEL)\s*\d*""",
            RegexOption.IGNORE_CASE
        )
        private val BITMAP_PATTERN = Regex("""\bBITMAP\b""", RegexOption.IGNORE_CASE)
        private val REVERSE_PATTERN = Regex("""\bREVERSE\b""", RegexOption.IGNORE_CASE)
        private val PARTITION_INDEX_PATTERN = Regex("""(LOCAL|GLOBAL)\s*(PARTITION)?""", RegexOption.IGNORE_CASE)

        // MySQL 관련 패턴
        private val USING_BTREE_HASH_PATTERN = Regex("""USING\s+(BTREE|HASH)""", RegexOption.IGNORE_CASE)
        private val FULLTEXT_PATTERN = Regex("""\bFULLTEXT\b""", RegexOption.IGNORE_CASE)
        private val SPATIAL_PATTERN = Regex("""\bSPATIAL\b""", RegexOption.IGNORE_CASE)

        // PostgreSQL 관련 패턴
        private val USING_PG_PATTERN = Regex("""USING\s+(GIN|GIST|BRIN|HASH|BTREE)""", RegexOption.IGNORE_CASE)
        private val INCLUDE_PATTERN = Regex("""INCLUDE\s*\([^)]+\)""", RegexOption.IGNORE_CASE)
        private val WHERE_PATTERN = Regex("""WHERE\s+.+$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        // 함수 기반 인덱스 패턴
        private val UPPER_PATTERN = Regex("""UPPER\s*\(\s*(\w+)\s*\)""", RegexOption.IGNORE_CASE)
        private val LOWER_PATTERN = Regex("""LOWER\s*\(\s*(\w+)\s*\)""", RegexOption.IGNORE_CASE)
        private val NVL_PATTERN = Regex("""\bNVL\s*\(""", RegexOption.IGNORE_CASE)
        private val COALESCE_PATTERN = Regex("""\bCOALESCE\s*\(""", RegexOption.IGNORE_CASE)

        // 공통 패턴
        private val MULTIPLE_SPACES_PATTERN = Regex("""\s+""")
    }

    /**
     * CREATE INDEX 변환
     */
    fun convertCreateIndex(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // 인용문자 변환
        val sourceQuote = if (sourceDialect == DialectType.MYSQL) "`" else "\""
        val targetQuote = if (targetDialect == DialectType.MYSQL) "`" else "\""
        result = result.replace(sourceQuote, targetQuote)

        // Oracle 전용 인덱스 옵션 처리
        if (sourceDialect == DialectType.ORACLE) {
            result = convertOracleIndexOptions(result, targetDialect, warnings, appliedRules)
        }

        // MySQL 전용 인덱스 옵션 처리
        if (sourceDialect == DialectType.MYSQL) {
            result = convertMySqlIndexOptions(result, targetDialect, warnings, appliedRules)
        }

        // PostgreSQL 전용 인덱스 옵션 처리
        if (sourceDialect == DialectType.POSTGRESQL) {
            result = convertPostgreSqlIndexOptions(result, targetDialect, warnings, appliedRules)
        }

        // 함수 기반 인덱스 변환
        result = convertFunctionBasedIndex(result, sourceDialect, targetDialect, warnings, appliedRules)

        appliedRules.add("CREATE INDEX 변환")
        return result
    }

    /**
     * Oracle 인덱스 옵션 변환
     */
    private fun convertOracleIndexOptions(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // TABLESPACE 절 처리
        if (TABLESPACE_PATTERN.containsMatchIn(result)) {
            when (targetDialect) {
                DialectType.MYSQL -> {
                    result = TABLESPACE_PATTERN.replace(result, "")
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "MySQL은 TABLESPACE 절을 인덱스에서 지원하지 않습니다.",
                        WarningSeverity.INFO
                    ))
                }
                DialectType.POSTGRESQL -> {
                    appliedRules.add("TABLESPACE 절 유지 (PostgreSQL)")
                }
                else -> {}
            }
        }

        // PCTFREE, INITRANS 등 Oracle 스토리지 옵션 제거
        if (STORAGE_OPTIONS_PATTERN.containsMatchIn(result)) {
            result = STORAGE_OPTIONS_PATTERN.replace(result, "")
            appliedRules.add("Oracle 스토리지 옵션 제거")
        }

        // BITMAP 인덱스 처리
        if (result.uppercase().contains("BITMAP")) {
            when (targetDialect) {
                DialectType.MYSQL -> {
                    result = BITMAP_PATTERN.replace(result, "")
                    warnings.add(ConversionWarning(
                        WarningType.UNSUPPORTED_FUNCTION,
                        "MySQL은 BITMAP 인덱스를 지원하지 않습니다.",
                        WarningSeverity.WARNING,
                        "일반 B-tree 인덱스로 변환됩니다."
                    ))
                }
                DialectType.POSTGRESQL -> {
                    result = BITMAP_PATTERN.replace(result, "")
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "PostgreSQL은 BITMAP 인덱스 대신 GIN 인덱스를 고려하세요.",
                        WarningSeverity.INFO
                    ))
                }
                else -> {}
            }
        }

        // REVERSE 인덱스 처리
        if (result.uppercase().contains("REVERSE")) {
            result = REVERSE_PATTERN.replace(result, "")
            warnings.add(ConversionWarning(
                WarningType.UNSUPPORTED_FUNCTION,
                "REVERSE 인덱스는 ${targetDialect.name}에서 지원하지 않습니다.",
                WarningSeverity.WARNING
            ))
        }

        // LOCAL/GLOBAL 파티션 인덱스
        if (PARTITION_INDEX_PATTERN.containsMatchIn(result)) {
            result = PARTITION_INDEX_PATTERN.replace(result, "")
            warnings.add(ConversionWarning(
                WarningType.MANUAL_REVIEW_NEEDED,
                "파티션 인덱스는 수동 변환이 필요합니다.",
                WarningSeverity.WARNING
            ))
        }

        return MULTIPLE_SPACES_PATTERN.replace(result, " ").trim()
    }

    /**
     * MySQL 인덱스 옵션 변환
     */
    private fun convertMySqlIndexOptions(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // USING BTREE/HASH 처리
        val usingMatch = USING_BTREE_HASH_PATTERN.find(result)
        if (usingMatch != null) {
            when (targetDialect) {
                DialectType.ORACLE -> {
                    result = USING_BTREE_HASH_PATTERN.replace(result, "")
                    appliedRules.add("USING 절 제거 (Oracle)")
                }
                DialectType.POSTGRESQL -> {
                    val method = usingMatch.groupValues[1].uppercase()
                    if (method == "HASH") {
                        appliedRules.add("USING HASH 유지 (PostgreSQL)")
                    }
                }
                else -> {}
            }
        }

        // FULLTEXT 인덱스 처리
        if (result.uppercase().contains("FULLTEXT")) {
            when (targetDialect) {
                DialectType.ORACLE -> {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "Oracle에서는 Oracle Text를 사용하세요.",
                        WarningSeverity.WARNING,
                        "CREATE INDEX idx ON table(col) INDEXTYPE IS CTXSYS.CONTEXT"
                    ))
                    result = FULLTEXT_PATTERN.replace(result, "")
                }
                DialectType.POSTGRESQL -> {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "PostgreSQL에서는 GIN 인덱스와 to_tsvector를 사용하세요.",
                        WarningSeverity.WARNING,
                        "CREATE INDEX idx ON table USING GIN(to_tsvector('english', col))"
                    ))
                    result = FULLTEXT_PATTERN.replace(result, "")
                }
                else -> {}
            }
        }

        // SPATIAL 인덱스 처리
        if (result.uppercase().contains("SPATIAL")) {
            when (targetDialect) {
                DialectType.POSTGRESQL -> {
                    result = SPATIAL_PATTERN.replace(result, "")
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "PostgreSQL에서는 PostGIS와 GIST 인덱스를 사용하세요.",
                        WarningSeverity.WARNING,
                        "CREATE INDEX idx ON table USING GIST(geom_col)"
                    ))
                }
                else -> {
                    result = SPATIAL_PATTERN.replace(result, "")
                    warnings.add(ConversionWarning(
                        WarningType.UNSUPPORTED_FUNCTION,
                        "SPATIAL 인덱스는 ${targetDialect.name}에서 다른 방식으로 구현해야 합니다.",
                        WarningSeverity.WARNING
                    ))
                }
            }
        }

        return MULTIPLE_SPACES_PATTERN.replace(result, " ").trim()
    }

    /**
     * PostgreSQL 인덱스 옵션 변환
     */
    private fun convertPostgreSqlIndexOptions(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // USING GIN/GIST/BRIN 처리
        val usingMatch = USING_PG_PATTERN.find(result)
        if (usingMatch != null) {
            val method = usingMatch.groupValues[1].uppercase()
            when (targetDialect) {
                DialectType.MYSQL -> {
                    when (method) {
                        "GIN", "GIST", "BRIN" -> {
                            result = USING_PG_PATTERN.replace(result, "")
                            warnings.add(ConversionWarning(
                                WarningType.UNSUPPORTED_FUNCTION,
                                "MySQL은 $method 인덱스를 지원하지 않습니다.",
                                WarningSeverity.WARNING,
                                "B-tree 인덱스로 대체됩니다."
                            ))
                        }
                        "HASH" -> {
                            appliedRules.add("USING HASH 유지 (MySQL)")
                        }
                    }
                }
                DialectType.ORACLE -> {
                    result = USING_PG_PATTERN.replace(result, "")
                    if (method in listOf("GIN", "GIST", "BRIN")) {
                        warnings.add(ConversionWarning(
                            WarningType.UNSUPPORTED_FUNCTION,
                            "Oracle은 $method 인덱스를 지원하지 않습니다.",
                            WarningSeverity.WARNING
                        ))
                    }
                }
                else -> {}
            }
        }

        // INCLUDE 절 처리 (PostgreSQL 11+)
        if (INCLUDE_PATTERN.containsMatchIn(result)) {
            when (targetDialect) {
                DialectType.MYSQL -> {
                    result = INCLUDE_PATTERN.replace(result, "")
                    warnings.add(ConversionWarning(
                        WarningType.UNSUPPORTED_FUNCTION,
                        "MySQL은 INCLUDE 절을 지원하지 않습니다.",
                        WarningSeverity.WARNING
                    ))
                }
                DialectType.ORACLE -> {
                    result = INCLUDE_PATTERN.replace(result, "")
                    warnings.add(ConversionWarning(
                        WarningType.UNSUPPORTED_FUNCTION,
                        "Oracle은 INCLUDE 절을 지원하지 않습니다.",
                        WarningSeverity.WARNING
                    ))
                }
                else -> {}
            }
        }

        // WHERE 절 (Partial Index) 처리
        if (WHERE_PATTERN.containsMatchIn(result)) {
            when (targetDialect) {
                DialectType.MYSQL -> {
                    result = WHERE_PATTERN.replace(result, "")
                    warnings.add(ConversionWarning(
                        WarningType.UNSUPPORTED_FUNCTION,
                        "MySQL은 부분 인덱스(Partial Index)를 지원하지 않습니다.",
                        WarningSeverity.WARNING,
                        "전체 인덱스로 변환됩니다."
                    ))
                }
                DialectType.ORACLE -> {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "Oracle에서는 함수 기반 인덱스로 부분 인덱스를 시뮬레이션할 수 있습니다.",
                        WarningSeverity.WARNING
                    ))
                }
                else -> {}
            }
        }

        return MULTIPLE_SPACES_PATTERN.replace(result, " ").trim()
    }

    /**
     * 함수 기반 인덱스 변환
     */
    private fun convertFunctionBasedIndex(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // UPPER, LOWER 함수 기반 인덱스
        when (targetDialect) {
            DialectType.MYSQL -> {
                if (UPPER_PATTERN.containsMatchIn(result) || LOWER_PATTERN.containsMatchIn(result)) {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "MySQL 8.0+에서만 함수 기반 인덱스가 지원됩니다.",
                        WarningSeverity.WARNING,
                        "MySQL 8.0 미만에서는 Generated Column을 사용하세요."
                    ))
                }
            }
            else -> {}
        }

        // NVL 함수를 COALESCE로 변환 (Oracle → PostgreSQL)
        if (sourceDialect == DialectType.ORACLE && targetDialect == DialectType.POSTGRESQL) {
            result = NVL_PATTERN.replace(result, "COALESCE(")
            if (sql != result) {
                appliedRules.add("인덱스 내 NVL → COALESCE 변환")
            }
        }

        // COALESCE를 NVL로 변환 (PostgreSQL → Oracle)
        if (sourceDialect == DialectType.POSTGRESQL && targetDialect == DialectType.ORACLE) {
            result = COALESCE_PATTERN.replace(result, "NVL(")
            if (sql != result) {
                appliedRules.add("인덱스 내 COALESCE → NVL 변환")
            }
        }

        return result
    }
}