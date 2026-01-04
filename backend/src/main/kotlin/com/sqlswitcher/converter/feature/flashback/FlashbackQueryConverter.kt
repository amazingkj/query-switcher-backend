package com.sqlswitcher.converter.feature.flashback

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * Oracle FLASHBACK 쿼리 변환기
 *
 * Oracle의 FLASHBACK 관련 구문을 다른 데이터베이스로 변환합니다.
 *
 * 지원 기능:
 * - FLASHBACK QUERY (AS OF SCN/TIMESTAMP)
 * - FLASHBACK VERSION QUERY (VERSIONS BETWEEN)
 * - FLASHBACK TABLE (FLASHBACK TABLE ... TO)
 * - DBMS_FLASHBACK 패키지 호출
 * - ORA_ROWSCN 의사 컬럼
 *
 * 참고: FLASHBACK은 Oracle 전용 기능으로, 다른 DB에서는
 * 대체 방안(Temporal Table, 감사 테이블 등)을 안내합니다.
 */
object FlashbackQueryConverter {

    // ============ FLASHBACK 감지 패턴 ============

    /** AS OF SCN 패턴 */
    private val AS_OF_SCN_PATTERN = Regex(
        """(\w+)\s+AS\s+OF\s+SCN\s+(\d+|:\w+|\?|:[A-Za-z_]\w*)""",
        RegexOption.IGNORE_CASE
    )

    /** AS OF TIMESTAMP 패턴 */
    private val AS_OF_TIMESTAMP_PATTERN = Regex(
        """(\w+)\s+AS\s+OF\s+TIMESTAMP\s+(?:TO_TIMESTAMP\s*\([^)]+\)|SYSTIMESTAMP\s*-\s*INTERVAL\s+'[^']+'\s+\w+|'[^']+'|:\w+|\?)""",
        RegexOption.IGNORE_CASE
    )

    /** VERSIONS BETWEEN 패턴 */
    private val VERSIONS_BETWEEN_PATTERN = Regex(
        """(\w+)\s+VERSIONS\s+BETWEEN\s+(SCN|TIMESTAMP)\s+(.+?)\s+AND\s+(.+?)(?=\s+WHERE|\s+ORDER|\s+GROUP|\s*;|\s*$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** FLASHBACK TABLE TO 패턴 */
    private val FLASHBACK_TABLE_PATTERN = Regex(
        """FLASHBACK\s+TABLE\s+["'`]?(\w+)["'`]?\s+TO\s+(SCN|TIMESTAMP|BEFORE\s+DROP)(?:\s+(.+?))?(?=\s*;|\s*$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** ORA_ROWSCN 의사 컬럼 패턴 */
    private val ORA_ROWSCN_PATTERN = Regex(
        """\bORA_ROWSCN\b""",
        RegexOption.IGNORE_CASE
    )

    /** DBMS_FLASHBACK 패키지 패턴 */
    private val DBMS_FLASHBACK_PATTERN = Regex(
        """DBMS_FLASHBACK\.(\w+)\s*\(([^)]*)\)""",
        RegexOption.IGNORE_CASE
    )

    /** FLASHBACK ARCHIVE 패턴 */
    private val FLASHBACK_ARCHIVE_PATTERN = Regex(
        """FLASHBACK\s+ARCHIVE\s+(\w+)""",
        RegexOption.IGNORE_CASE
    )

    /** SCN_TO_TIMESTAMP 패턴 */
    private val SCN_TO_TIMESTAMP_PATTERN = Regex(
        """SCN_TO_TIMESTAMP\s*\(\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /** TIMESTAMP_TO_SCN 패턴 */
    private val TIMESTAMP_TO_SCN_PATTERN = Regex(
        """TIMESTAMP_TO_SCN\s*\(\s*([^)]+)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * FLASHBACK 관련 구문 포함 여부 확인
     */
    fun hasFlashbackSyntax(sql: String): Boolean {
        return AS_OF_SCN_PATTERN.containsMatchIn(sql) ||
                AS_OF_TIMESTAMP_PATTERN.containsMatchIn(sql) ||
                VERSIONS_BETWEEN_PATTERN.containsMatchIn(sql) ||
                FLASHBACK_TABLE_PATTERN.containsMatchIn(sql) ||
                ORA_ROWSCN_PATTERN.containsMatchIn(sql) ||
                DBMS_FLASHBACK_PATTERN.containsMatchIn(sql) ||
                FLASHBACK_ARCHIVE_PATTERN.containsMatchIn(sql) ||
                SCN_TO_TIMESTAMP_PATTERN.containsMatchIn(sql) ||
                TIMESTAMP_TO_SCN_PATTERN.containsMatchIn(sql)
    }

    /**
     * FLASHBACK 구문 변환
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!hasFlashbackSyntax(sql)) return sql
        if (sourceDialect != DialectType.ORACLE) return sql

        return when (targetDialect) {
            DialectType.MYSQL -> convertToMySql(sql, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertToPostgreSql(sql, warnings, appliedRules)
            DialectType.ORACLE -> sql
        }
    }

    // ============ MySQL 변환 ============

    /**
     * MySQL 형식으로 변환
     */
    private fun convertToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // 1. AS OF TIMESTAMP 변환
        if (AS_OF_TIMESTAMP_PATTERN.containsMatchIn(result)) {
            result = convertAsOfTimestampForMySql(result, warnings, appliedRules)
        }

        // 2. AS OF SCN 변환
        if (AS_OF_SCN_PATTERN.containsMatchIn(result)) {
            result = convertAsOfScnForMySql(result, warnings, appliedRules)
        }

        // 3. VERSIONS BETWEEN 변환
        if (VERSIONS_BETWEEN_PATTERN.containsMatchIn(result)) {
            result = convertVersionsBetweenForMySql(result, warnings, appliedRules)
        }

        // 4. FLASHBACK TABLE 변환
        if (FLASHBACK_TABLE_PATTERN.containsMatchIn(result)) {
            result = convertFlashbackTableForMySql(result, warnings, appliedRules)
        }

        // 5. ORA_ROWSCN 변환
        if (ORA_ROWSCN_PATTERN.containsMatchIn(result)) {
            result = convertOraRowscnForMySql(result, warnings, appliedRules)
        }

        // 6. DBMS_FLASHBACK 변환
        if (DBMS_FLASHBACK_PATTERN.containsMatchIn(result)) {
            result = convertDbmsFlashbackForMySql(result, warnings, appliedRules)
        }

        // 7. SCN_TO_TIMESTAMP / TIMESTAMP_TO_SCN 변환
        if (SCN_TO_TIMESTAMP_PATTERN.containsMatchIn(result) ||
            TIMESTAMP_TO_SCN_PATTERN.containsMatchIn(result)) {
            result = convertScnFunctionsForMySql(result, warnings, appliedRules)
        }

        // 8. FLASHBACK ARCHIVE 변환
        if (FLASHBACK_ARCHIVE_PATTERN.containsMatchIn(result)) {
            result = convertFlashbackArchiveForMySql(result, warnings, appliedRules)
        }

        return result
    }

    /**
     * AS OF TIMESTAMP → MySQL 변환
     * MySQL은 InnoDB 히스토리를 통해 제한적인 과거 시점 조회 가능
     */
    private fun convertAsOfTimestampForMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.UNSUPPORTED_STATEMENT,
            message = "Oracle AS OF TIMESTAMP는 MySQL에서 직접 지원되지 않습니다.",
            severity = WarningSeverity.ERROR,
            suggestion = "MySQL에서는 다음 대안을 고려하세요:\n" +
                    "1. Temporal Table 사용 (MySQL 8.0.23+에서 시스템 버전 테이블)\n" +
                    "2. 감사(Audit) 테이블을 직접 구현하여 변경 이력 추적\n" +
                    "3. binlog를 사용한 Point-in-Time Recovery"
        ))

        appliedRules.add("AS OF TIMESTAMP → 주석 처리 (MySQL 미지원)")

        // AS OF TIMESTAMP 절을 주석으로 변환
        return AS_OF_TIMESTAMP_PATTERN.replace(sql) { match ->
            val tableName = match.groupValues[1]
            val fullMatch = match.value
            "/* $fullMatch */ $tableName"
        }
    }

    /**
     * AS OF SCN → MySQL 변환
     */
    private fun convertAsOfScnForMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.UNSUPPORTED_STATEMENT,
            message = "Oracle AS OF SCN은 MySQL에서 지원되지 않습니다.",
            severity = WarningSeverity.ERROR,
            suggestion = "MySQL에서는 SCN 개념이 없습니다. 대신 binlog 포지션 또는 GTID를 사용한 복구를 고려하세요."
        ))

        appliedRules.add("AS OF SCN → 주석 처리 (MySQL 미지원)")

        return AS_OF_SCN_PATTERN.replace(sql) { match ->
            val tableName = match.groupValues[1]
            val fullMatch = match.value
            "/* $fullMatch */ $tableName"
        }
    }

    /**
     * VERSIONS BETWEEN → MySQL 변환
     */
    private fun convertVersionsBetweenForMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.UNSUPPORTED_STATEMENT,
            message = "Oracle VERSIONS BETWEEN은 MySQL에서 지원되지 않습니다.",
            severity = WarningSeverity.ERROR,
            suggestion = "MySQL에서 행 버전 이력을 추적하려면:\n" +
                    "1. 감사 테이블에 변경 이력 저장\n" +
                    "2. 트리거를 사용하여 변경 사항 기록\n" +
                    "3. MySQL Enterprise의 Audit Plugin 사용"
        ))

        appliedRules.add("VERSIONS BETWEEN → 주석 처리 (MySQL 미지원)")

        return VERSIONS_BETWEEN_PATTERN.replace(sql) { match ->
            val tableName = match.groupValues[1]
            val fullMatch = match.value
            "/* $fullMatch */ $tableName"
        }
    }

    /**
     * FLASHBACK TABLE → MySQL 변환
     */
    private fun convertFlashbackTableForMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val match = FLASHBACK_TABLE_PATTERN.find(sql)
        if (match != null) {
            val tableName = match.groupValues[1]
            val flashbackType = match.groupValues[2].uppercase()

            when {
                flashbackType.contains("BEFORE DROP") -> {
                    warnings.add(ConversionWarning(
                        type = WarningType.UNSUPPORTED_STATEMENT,
                        message = "Oracle FLASHBACK TABLE TO BEFORE DROP은 MySQL에서 지원되지 않습니다.",
                        severity = WarningSeverity.ERROR,
                        suggestion = "MySQL에서 삭제된 테이블을 복구하려면 백업에서 복원해야 합니다."
                    ))

                    appliedRules.add("FLASHBACK TABLE TO BEFORE DROP → 주석 처리")

                    return "-- FLASHBACK TABLE '$tableName' TO BEFORE DROP 은 MySQL에서 지원되지 않습니다.\n" +
                            "-- 백업에서 테이블을 복원하세요."
                }
                else -> {
                    warnings.add(ConversionWarning(
                        type = WarningType.UNSUPPORTED_STATEMENT,
                        message = "Oracle FLASHBACK TABLE은 MySQL에서 지원되지 않습니다.",
                        severity = WarningSeverity.ERROR,
                        suggestion = "MySQL에서 테이블을 과거 시점으로 복원하려면:\n" +
                                "1. mysqlbinlog와 Point-in-Time Recovery 사용\n" +
                                "2. 백업에서 특정 시점으로 복원"
                    ))

                    appliedRules.add("FLASHBACK TABLE → 주석 처리")

                    return "-- Oracle: ${match.value}\n-- MySQL에서는 지원되지 않습니다. PITR 또는 백업 복원을 사용하세요."
                }
            }
        }

        return sql
    }

    /**
     * ORA_ROWSCN → MySQL 변환
     */
    private fun convertOraRowscnForMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.UNSUPPORTED_STATEMENT,
            message = "Oracle ORA_ROWSCN 의사 컬럼은 MySQL에서 지원되지 않습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "MySQL에서는 TIMESTAMP 컬럼을 ON UPDATE CURRENT_TIMESTAMP로 설정하여 마지막 수정 시간을 추적하세요."
        ))

        appliedRules.add("ORA_ROWSCN → NULL 대체 (MySQL 미지원)")

        // ORA_ROWSCN을 NULL로 대체
        return ORA_ROWSCN_PATTERN.replace(sql, "NULL /* ORA_ROWSCN not supported */")
    }

    /**
     * DBMS_FLASHBACK → MySQL 변환
     */
    private fun convertDbmsFlashbackForMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = DBMS_FLASHBACK_PATTERN.replace(sql) { match ->
            val procedure = match.groupValues[1].uppercase()
            val args = match.groupValues[2]

            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "DBMS_FLASHBACK.$procedure 는 MySQL에서 지원되지 않습니다.",
                severity = WarningSeverity.ERROR
            ))

            "/* DBMS_FLASHBACK.$procedure($args) - MySQL not supported */ NULL"
        }

        appliedRules.add("DBMS_FLASHBACK → 주석 처리 (MySQL 미지원)")

        return result
    }

    /**
     * SCN_TO_TIMESTAMP / TIMESTAMP_TO_SCN → MySQL 변환
     */
    private fun convertScnFunctionsForMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (SCN_TO_TIMESTAMP_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "Oracle SCN_TO_TIMESTAMP 함수는 MySQL에서 지원되지 않습니다.",
                severity = WarningSeverity.ERROR
            ))

            result = SCN_TO_TIMESTAMP_PATTERN.replace(result) { match ->
                val arg = match.groupValues[1]
                "/* SCN_TO_TIMESTAMP($arg) */ NULL"
            }

            appliedRules.add("SCN_TO_TIMESTAMP → NULL 대체")
        }

        if (TIMESTAMP_TO_SCN_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "Oracle TIMESTAMP_TO_SCN 함수는 MySQL에서 지원되지 않습니다.",
                severity = WarningSeverity.ERROR
            ))

            result = TIMESTAMP_TO_SCN_PATTERN.replace(result) { match ->
                val arg = match.groupValues[1]
                "/* TIMESTAMP_TO_SCN($arg) */ NULL"
            }

            appliedRules.add("TIMESTAMP_TO_SCN → NULL 대체")
        }

        return result
    }

    /**
     * FLASHBACK ARCHIVE → MySQL 변환
     */
    private fun convertFlashbackArchiveForMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.UNSUPPORTED_STATEMENT,
            message = "Oracle FLASHBACK ARCHIVE는 MySQL에서 지원되지 않습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "MySQL에서 히스토리 데이터 관리를 위해:\n" +
                    "1. 감사 테이블 직접 구현\n" +
                    "2. 파티션 테이블로 이력 관리\n" +
                    "3. MySQL Enterprise Audit 사용"
        ))

        appliedRules.add("FLASHBACK ARCHIVE → 주석 처리 (MySQL 미지원)")

        return FLASHBACK_ARCHIVE_PATTERN.replace(sql) { match ->
            "/* ${match.value} - MySQL not supported */"
        }
    }

    // ============ PostgreSQL 변환 ============

    /**
     * PostgreSQL 형식으로 변환
     */
    private fun convertToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // 1. AS OF TIMESTAMP 변환 - PostgreSQL temporal queries 가능
        if (AS_OF_TIMESTAMP_PATTERN.containsMatchIn(result)) {
            result = convertAsOfTimestampForPostgreSql(result, warnings, appliedRules)
        }

        // 2. AS OF SCN 변환
        if (AS_OF_SCN_PATTERN.containsMatchIn(result)) {
            result = convertAsOfScnForPostgreSql(result, warnings, appliedRules)
        }

        // 3. VERSIONS BETWEEN 변환
        if (VERSIONS_BETWEEN_PATTERN.containsMatchIn(result)) {
            result = convertVersionsBetweenForPostgreSql(result, warnings, appliedRules)
        }

        // 4. FLASHBACK TABLE 변환
        if (FLASHBACK_TABLE_PATTERN.containsMatchIn(result)) {
            result = convertFlashbackTableForPostgreSql(result, warnings, appliedRules)
        }

        // 5. ORA_ROWSCN 변환
        if (ORA_ROWSCN_PATTERN.containsMatchIn(result)) {
            result = convertOraRowscnForPostgreSql(result, warnings, appliedRules)
        }

        // 6. DBMS_FLASHBACK 변환
        if (DBMS_FLASHBACK_PATTERN.containsMatchIn(result)) {
            result = convertDbmsFlashbackForPostgreSql(result, warnings, appliedRules)
        }

        // 7. SCN_TO_TIMESTAMP / TIMESTAMP_TO_SCN 변환
        if (SCN_TO_TIMESTAMP_PATTERN.containsMatchIn(result) ||
            TIMESTAMP_TO_SCN_PATTERN.containsMatchIn(result)) {
            result = convertScnFunctionsForPostgreSql(result, warnings, appliedRules)
        }

        // 8. FLASHBACK ARCHIVE 변환
        if (FLASHBACK_ARCHIVE_PATTERN.containsMatchIn(result)) {
            result = convertFlashbackArchiveForPostgreSql(result, warnings, appliedRules)
        }

        return result
    }

    /**
     * AS OF TIMESTAMP → PostgreSQL 변환
     * PostgreSQL은 Temporal Table (SQL:2011) 지원
     */
    private fun convertAsOfTimestampForPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.PARTIAL_SUPPORT,
            message = "Oracle AS OF TIMESTAMP는 PostgreSQL temporal_tables 확장으로 구현 가능합니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "PostgreSQL에서 temporal query를 사용하려면:\n" +
                    "1. temporal_tables 확장 설치: CREATE EXTENSION temporal_tables;\n" +
                    "2. 시스템 버전 테이블 생성\n" +
                    "3. FOR SYSTEM_TIME AS OF 구문 사용\n" +
                    "또는 수동으로 history 테이블과 트리거를 구현하세요."
        ))

        appliedRules.add("AS OF TIMESTAMP → FOR SYSTEM_TIME AS OF 변환 필요 (temporal_tables)")

        // PostgreSQL temporal query 형식으로 변환 시도
        return AS_OF_TIMESTAMP_PATTERN.replace(sql) { match ->
            val tableName = match.groupValues[1]
            val timestampExpr = match.value.substringAfter("AS OF TIMESTAMP").trim()

            // Oracle TO_TIMESTAMP → PostgreSQL TIMESTAMP
            val pgTimestamp = convertOracleTimestampToPostgreSql(timestampExpr)

            // 주석과 함께 temporal 구문 예시 제공
            "$tableName /* Oracle: AS OF TIMESTAMP $timestampExpr */ /* PostgreSQL temporal: FOR SYSTEM_TIME AS OF $pgTimestamp */"
        }
    }

    /**
     * AS OF SCN → PostgreSQL 변환
     */
    private fun convertAsOfScnForPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.UNSUPPORTED_STATEMENT,
            message = "Oracle AS OF SCN은 PostgreSQL에서 직접 지원되지 않습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "PostgreSQL에서는 txid_snapshot 또는 pg_xact 시스템 테이블을 통해 트랜잭션 정보를 조회할 수 있습니다."
        ))

        appliedRules.add("AS OF SCN → 주석 처리 (PostgreSQL 미지원)")

        return AS_OF_SCN_PATTERN.replace(sql) { match ->
            val tableName = match.groupValues[1]
            val fullMatch = match.value
            "/* $fullMatch */ $tableName"
        }
    }

    /**
     * VERSIONS BETWEEN → PostgreSQL 변환
     */
    private fun convertVersionsBetweenForPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.PARTIAL_SUPPORT,
            message = "Oracle VERSIONS BETWEEN은 PostgreSQL temporal_tables 확장으로 유사하게 구현 가능합니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "PostgreSQL에서 버전 이력 조회:\n" +
                    "1. temporal_tables 확장 사용\n" +
                    "2. FOR SYSTEM_TIME FROM ... TO ... 구문 사용\n" +
                    "또는 직접 구현한 history 테이블에서 시간 범위로 조회"
        ))

        appliedRules.add("VERSIONS BETWEEN → 주석 처리 (temporal_tables 권장)")

        return VERSIONS_BETWEEN_PATTERN.replace(sql) { match ->
            val tableName = match.groupValues[1]
            val type = match.groupValues[2]
            val from = match.groupValues[3]
            val to = match.groupValues[4]
            val fullMatch = match.value

            "/* Oracle: $fullMatch */\n" +
                    "/* PostgreSQL: SELECT * FROM $tableName FOR SYSTEM_TIME FROM $from TO $to */"
        }
    }

    /**
     * FLASHBACK TABLE → PostgreSQL 변환
     */
    private fun convertFlashbackTableForPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val match = FLASHBACK_TABLE_PATTERN.find(sql)
        if (match != null) {
            val tableName = match.groupValues[1]
            val flashbackType = match.groupValues[2].uppercase()

            when {
                flashbackType.contains("BEFORE DROP") -> {
                    warnings.add(ConversionWarning(
                        type = WarningType.UNSUPPORTED_STATEMENT,
                        message = "Oracle FLASHBACK TABLE TO BEFORE DROP은 PostgreSQL에서 지원되지 않습니다.",
                        severity = WarningSeverity.ERROR,
                        suggestion = "PostgreSQL에서 삭제된 테이블 복구:\n" +
                                "1. pg_dump 백업에서 복원\n" +
                                "2. Point-in-Time Recovery (PITR) 사용"
                    ))

                    appliedRules.add("FLASHBACK TABLE TO BEFORE DROP → 주석 처리")

                    return "-- Oracle: FLASHBACK TABLE \"$tableName\" TO BEFORE DROP\n" +
                            "-- PostgreSQL: 백업에서 복원하거나 PITR을 사용하세요."
                }
                else -> {
                    warnings.add(ConversionWarning(
                        type = WarningType.UNSUPPORTED_STATEMENT,
                        message = "Oracle FLASHBACK TABLE은 PostgreSQL에서 직접 지원되지 않습니다.",
                        severity = WarningSeverity.ERROR,
                        suggestion = "PostgreSQL에서 테이블 복구:\n" +
                                "1. pg_basebackup + WAL archiving으로 PITR 수행\n" +
                                "2. temporal_tables 확장으로 시점 조회 후 데이터 복원"
                    ))

                    appliedRules.add("FLASHBACK TABLE → 주석 처리")

                    return "-- Oracle: ${match.value}\n-- PostgreSQL: PITR 또는 temporal_tables 사용"
                }
            }
        }

        return sql
    }

    /**
     * ORA_ROWSCN → PostgreSQL 변환
     */
    private fun convertOraRowscnForPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.PARTIAL_SUPPORT,
            message = "Oracle ORA_ROWSCN은 PostgreSQL xmin 시스템 컬럼으로 부분 대체 가능합니다.",
            severity = WarningSeverity.INFO,
            suggestion = "PostgreSQL에서 행 버전 정보:\n" +
                    "- xmin: 행을 삽입/갱신한 트랜잭션 ID\n" +
                    "- xmax: 행을 삭제한 트랜잭션 ID (0이면 삭제 안됨)\n" +
                    "- ctid: 물리적 위치 (업데이트 시 변경됨)"
        ))

        appliedRules.add("ORA_ROWSCN → xmin 변환")

        // ORA_ROWSCN을 xmin으로 대체
        return ORA_ROWSCN_PATTERN.replace(sql, "xmin::text::bigint")
    }

    /**
     * DBMS_FLASHBACK → PostgreSQL 변환
     */
    private fun convertDbmsFlashbackForPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val result = DBMS_FLASHBACK_PATTERN.replace(sql) { match ->
            val procedure = match.groupValues[1].uppercase()
            val args = match.groupValues[2]

            val replacement = when (procedure) {
                "ENABLE_AT_TIME" -> {
                    warnings.add(ConversionWarning(
                        type = WarningType.UNSUPPORTED_STATEMENT,
                        message = "DBMS_FLASHBACK.ENABLE_AT_TIME은 PostgreSQL에서 지원되지 않습니다.",
                        severity = WarningSeverity.ERROR,
                        suggestion = "PostgreSQL에서는 temporal_tables 확장 또는 직접 구현한 버전 관리 시스템을 사용하세요."
                    ))
                    "/* DBMS_FLASHBACK.ENABLE_AT_TIME($args) - use temporal_tables extension */"
                }
                "GET_SYSTEM_CHANGE_NUMBER" -> {
                    warnings.add(ConversionWarning(
                        type = WarningType.PARTIAL_SUPPORT,
                        message = "DBMS_FLASHBACK.GET_SYSTEM_CHANGE_NUMBER는 PostgreSQL pg_current_xact_id()로 대체 가능합니다.",
                        severity = WarningSeverity.INFO
                    ))
                    "pg_current_xact_id()"
                }
                else -> {
                    warnings.add(ConversionWarning(
                        type = WarningType.UNSUPPORTED_STATEMENT,
                        message = "DBMS_FLASHBACK.$procedure 는 PostgreSQL에서 지원되지 않습니다.",
                        severity = WarningSeverity.ERROR
                    ))
                    "/* DBMS_FLASHBACK.$procedure($args) - not supported */"
                }
            }

            replacement
        }

        appliedRules.add("DBMS_FLASHBACK → PostgreSQL 대체 함수/주석")

        return result
    }

    /**
     * SCN_TO_TIMESTAMP / TIMESTAMP_TO_SCN → PostgreSQL 변환
     */
    private fun convertScnFunctionsForPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (SCN_TO_TIMESTAMP_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "Oracle SCN_TO_TIMESTAMP는 PostgreSQL에서 직접 지원되지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "PostgreSQL에서 트랜잭션 시간 정보를 얻으려면 pg_xact_commit_timestamp() 함수를 사용하세요 (track_commit_timestamp 활성화 필요)."
            ))

            result = SCN_TO_TIMESTAMP_PATTERN.replace(result) { match ->
                val arg = match.groupValues[1]
                "pg_xact_commit_timestamp($arg::xid)"
            }

            appliedRules.add("SCN_TO_TIMESTAMP → pg_xact_commit_timestamp 변환")
        }

        if (TIMESTAMP_TO_SCN_PATTERN.containsMatchIn(result)) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_STATEMENT,
                message = "Oracle TIMESTAMP_TO_SCN는 PostgreSQL에서 지원되지 않습니다.",
                severity = WarningSeverity.ERROR,
                suggestion = "PostgreSQL에서는 타임스탬프에서 트랜잭션 ID를 직접 조회하는 방법이 없습니다."
            ))

            result = TIMESTAMP_TO_SCN_PATTERN.replace(result) { match ->
                val arg = match.groupValues[1]
                "/* TIMESTAMP_TO_SCN($arg) - not supported */ NULL"
            }

            appliedRules.add("TIMESTAMP_TO_SCN → NULL 대체")
        }

        return result
    }

    /**
     * FLASHBACK ARCHIVE → PostgreSQL 변환
     */
    private fun convertFlashbackArchiveForPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.PARTIAL_SUPPORT,
            message = "Oracle FLASHBACK ARCHIVE는 PostgreSQL temporal_tables 확장으로 유사하게 구현 가능합니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "PostgreSQL에서 히스토리 데이터 관리:\n" +
                    "1. temporal_tables 확장 설치\n" +
                    "2. 시스템 시간 버전 테이블 생성\n" +
                    "3. 또는 직접 history 테이블 + 트리거 구현"
        ))

        appliedRules.add("FLASHBACK ARCHIVE → 주석 처리 (temporal_tables 권장)")

        return FLASHBACK_ARCHIVE_PATTERN.replace(sql) { match ->
            "/* ${match.value} - use temporal_tables extension */"
        }
    }

    /**
     * Oracle TIMESTAMP 표현식을 PostgreSQL 형식으로 변환
     */
    private fun convertOracleTimestampToPostgreSql(timestampExpr: String): String {
        // TO_TIMESTAMP 변환
        val toTimestampPattern = Regex("""TO_TIMESTAMP\s*\(\s*'([^']+)'\s*,\s*'([^']+)'\s*\)""", RegexOption.IGNORE_CASE)
        val toTimestampMatch = toTimestampPattern.find(timestampExpr)
        if (toTimestampMatch != null) {
            val dateStr = toTimestampMatch.groupValues[1]
            val format = toTimestampMatch.groupValues[2]
            val pgFormat = convertOracleDateFormatToPostgreSql(format)
            return "TO_TIMESTAMP('$dateStr', '$pgFormat')"
        }

        // SYSTIMESTAMP - INTERVAL 변환
        val sysTimestampPattern = Regex("""SYSTIMESTAMP\s*-\s*INTERVAL\s+'(\d+)'\s+(\w+)""", RegexOption.IGNORE_CASE)
        val sysMatch = sysTimestampPattern.find(timestampExpr)
        if (sysMatch != null) {
            val value = sysMatch.groupValues[1]
            val unit = sysMatch.groupValues[2].lowercase()
            return "CURRENT_TIMESTAMP - INTERVAL '$value $unit'"
        }

        return timestampExpr
    }

    /**
     * Oracle 날짜 포맷을 PostgreSQL 포맷으로 변환
     */
    private fun convertOracleDateFormatToPostgreSql(oracleFormat: String): String {
        return oracleFormat
            .replace("DD", "DD")
            .replace("MM", "MM")
            .replace("YYYY", "YYYY")
            .replace("YY", "YY")
            .replace("HH24", "HH24")
            .replace("HH", "HH12")
            .replace("MI", "MI")
            .replace("SS", "SS")
    }
}
