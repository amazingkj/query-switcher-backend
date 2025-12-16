package com.sqlswitcher.converter.feature.view

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity

/**
 * MATERIALIZED VIEW 변환
 *
 * 지원 변환:
 * - Oracle → PostgreSQL: REFRESH 옵션 변환, BUILD 옵션 제거
 * - Oracle → MySQL: 미지원 안내 (테이블+트리거 대안 제시)
 * - PostgreSQL → Oracle: REFRESH 옵션 변환, TABLESPACE 추가
 * - PostgreSQL → MySQL: 미지원 안내
 *
 * Oracle 주요 옵션:
 * - BUILD IMMEDIATE/DEFERRED
 * - REFRESH FAST/COMPLETE/FORCE ON COMMIT/DEMAND
 * - ENABLE/DISABLE QUERY REWRITE
 *
 * PostgreSQL 주요 옵션:
 * - WITH DATA/WITH NO DATA
 * - TABLESPACE
 * - REFRESH MATERIALIZED VIEW [CONCURRENTLY]
 */
object MaterializedViewConverter {

    // Oracle MV 패턴
    private val ORACLE_MV_PATTERN = Regex(
        """CREATE\s+MATERIALIZED\s+VIEW\s+(\S+)\s*(BUILD\s+(?:IMMEDIATE|DEFERRED))?\s*(REFRESH\s+(?:FAST|COMPLETE|FORCE)\s+ON\s+(?:COMMIT|DEMAND))?\s*(ENABLE\s+QUERY\s+REWRITE|DISABLE\s+QUERY\s+REWRITE)?\s*AS\s*(.+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // PostgreSQL MV 패턴
    private val POSTGRESQL_MV_PATTERN = Regex(
        """CREATE\s+MATERIALIZED\s+VIEW\s+(?:IF\s+NOT\s+EXISTS\s+)?(\S+)(?:\s+TABLESPACE\s+(\S+))?\s*AS\s*(.+?)(WITH\s+(?:NO\s+)?DATA)?$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * MATERIALIZED VIEW 변환
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return when {
            sourceDialect == DialectType.ORACLE && targetDialect == DialectType.POSTGRESQL -> {
                convertOracleToPostgreSql(sql, warnings, appliedRules)
            }
            sourceDialect == DialectType.ORACLE && targetDialect == DialectType.MYSQL -> {
                convertOracleToMySql(sql, warnings, appliedRules)
            }
            sourceDialect == DialectType.POSTGRESQL && targetDialect == DialectType.ORACLE -> {
                convertPostgreSqlToOracle(sql, warnings, appliedRules)
            }
            sourceDialect == DialectType.POSTGRESQL && targetDialect == DialectType.MYSQL -> {
                convertPostgreSqlToMySql(sql, warnings, appliedRules)
            }
            targetDialect == DialectType.ORACLE -> {
                convertToOracle(sql, sourceDialect, warnings, appliedRules)
            }
            else -> {
                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "MATERIALIZED VIEW 변환은 수동 검토가 필요합니다.",
                    WarningSeverity.WARNING
                ))
                appliedRules.add("MATERIALIZED VIEW 변환 (수동 검토 필요)")
                sql
            }
        }
    }

    /**
     * Oracle → PostgreSQL 변환
     */
    private fun convertOracleToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            WarningType.PARTIAL_SUPPORT,
            "Oracle MATERIALIZED VIEW를 PostgreSQL로 변환합니다. 새로고침 옵션 등이 다를 수 있습니다.",
            WarningSeverity.WARNING,
            "PostgreSQL에서 REFRESH MATERIALIZED VIEW를 수동으로 실행하거나 pg_cron 등을 사용하세요."
        ))
        appliedRules.add("MATERIALIZED VIEW 변환 (Oracle → PostgreSQL)")

        var result = sql
        // REFRESH 옵션 변환
        result = result.replace(
            Regex("REFRESH\\s+(FAST|COMPLETE|FORCE)\\s+ON\\s+(COMMIT|DEMAND)", RegexOption.IGNORE_CASE),
            "" // PostgreSQL은 수동 REFRESH 필요
        )
        // BUILD IMMEDIATE/DEFERRED 제거
        result = result.replace(
            Regex("BUILD\\s+(IMMEDIATE|DEFERRED)", RegexOption.IGNORE_CASE),
            ""
        )
        return result.trim()
    }

    /**
     * Oracle → MySQL 변환
     */
    private fun convertOracleToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            WarningType.UNSUPPORTED_STATEMENT,
            "MySQL은 MATERIALIZED VIEW를 직접 지원하지 않습니다.",
            WarningSeverity.ERROR,
            "일반 VIEW + 트리거/이벤트로 구현하거나, 별도 테이블로 데이터를 복제하세요."
        ))
        appliedRules.add("MATERIALIZED VIEW 변환 실패 (MySQL 미지원)")
        return "-- MySQL does not support MATERIALIZED VIEW\n-- Original: $sql"
    }

    /**
     * PostgreSQL → Oracle 변환
     */
    private fun convertPostgreSqlToOracle(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val match = POSTGRESQL_MV_PATTERN.find(sql)
        if (match == null) {
            warnings.add(ConversionWarning(
                WarningType.MANUAL_REVIEW_NEEDED,
                "MATERIALIZED VIEW 구문을 파싱할 수 없습니다.",
                WarningSeverity.WARNING
            ))
            return sql
        }

        val viewName = match.groupValues[1]
        val tablespace = match.groupValues[2]
        val selectQuery = match.groupValues[3].trim()
        val withData = match.groupValues[4].uppercase()

        val result = buildString {
            append("CREATE MATERIALIZED VIEW $viewName\n")

            // TABLESPACE 변환
            if (tablespace.isNotEmpty()) {
                append("TABLESPACE $tablespace\n")
            }

            // BUILD 옵션 (WITH NO DATA → BUILD DEFERRED)
            if (withData.contains("NO DATA")) {
                append("BUILD DEFERRED\n")
                appliedRules.add("WITH NO DATA → BUILD DEFERRED 변환")
            } else {
                append("BUILD IMMEDIATE\n")
            }

            // 기본 REFRESH 옵션 추가
            append("REFRESH COMPLETE ON DEMAND\n")
            append("AS\n")
            append(selectQuery)
        }

        warnings.add(ConversionWarning(
            WarningType.PARTIAL_SUPPORT,
            "PostgreSQL MATERIALIZED VIEW를 Oracle로 변환합니다. REFRESH 로그가 필요할 수 있습니다.",
            WarningSeverity.WARNING,
            "FAST REFRESH가 필요하면 MATERIALIZED VIEW LOG를 생성하세요."
        ))
        appliedRules.add("MATERIALIZED VIEW 변환 (PostgreSQL → Oracle)")
        return result
    }

    /**
     * PostgreSQL → MySQL 변환
     */
    private fun convertPostgreSqlToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            WarningType.UNSUPPORTED_STATEMENT,
            "MySQL은 MATERIALIZED VIEW를 직접 지원하지 않습니다.",
            WarningSeverity.ERROR,
            "테이블로 데이터를 복사하고 이벤트 스케줄러로 갱신하세요:\n" +
            "1. CREATE TABLE mv_xxx AS SELECT ...\n" +
            "2. CREATE EVENT refresh_mv ON SCHEDULE EVERY 1 HOUR DO TRUNCATE mv_xxx; INSERT INTO mv_xxx SELECT ..."
        ))
        appliedRules.add("MATERIALIZED VIEW 변환 실패 (MySQL 미지원)")
        return "-- MySQL does not support MATERIALIZED VIEW\n-- Consider using TABLE + EVENT for similar functionality\n-- Original: $sql"
    }

    /**
     * → Oracle 변환 (기타 소스)
     */
    private fun convertToOracle(
        sql: String,
        sourceDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            WarningType.MANUAL_REVIEW_NEEDED,
            "MATERIALIZED VIEW를 Oracle로 변환합니다. 새로고침 로그 등 추가 설정이 필요할 수 있습니다.",
            WarningSeverity.WARNING
        ))
        appliedRules.add("MATERIALIZED VIEW 변환 (→ Oracle)")
        return sql
    }

    /**
     * REFRESH MATERIALIZED VIEW 변환
     */
    fun convertRefreshMaterializedView(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // PostgreSQL: REFRESH MATERIALIZED VIEW [CONCURRENTLY] view_name
        // Oracle: DBMS_MVIEW.REFRESH('view_name')

        val pgRefreshPattern = Regex(
            """REFRESH\s+MATERIALIZED\s+VIEW\s+(CONCURRENTLY\s+)?(\S+)""",
            RegexOption.IGNORE_CASE
        )

        return when {
            sourceDialect == DialectType.POSTGRESQL && targetDialect == DialectType.ORACLE -> {
                val match = pgRefreshPattern.find(sql)
                if (match != null) {
                    val viewName = match.groupValues[2]
                    appliedRules.add("REFRESH MATERIALIZED VIEW → DBMS_MVIEW.REFRESH 변환")
                    "BEGIN\n    DBMS_MVIEW.REFRESH('$viewName');\nEND;"
                } else {
                    sql
                }
            }
            sourceDialect == DialectType.ORACLE && targetDialect == DialectType.POSTGRESQL -> {
                // DBMS_MVIEW.REFRESH 패턴 검색
                val oracleRefreshPattern = Regex(
                    """DBMS_MVIEW\.REFRESH\s*\(\s*'(\w+)'""",
                    RegexOption.IGNORE_CASE
                )
                val match = oracleRefreshPattern.find(sql)
                if (match != null) {
                    val viewName = match.groupValues[1]
                    appliedRules.add("DBMS_MVIEW.REFRESH → REFRESH MATERIALIZED VIEW 변환")
                    "REFRESH MATERIALIZED VIEW $viewName"
                } else {
                    sql
                }
            }
            else -> sql
        }
    }
}