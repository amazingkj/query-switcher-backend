package com.sqlswitcher.converter.feature.mview

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * Materialized View 변환기
 *
 * Oracle/PostgreSQL Materialized View를 다른 DB로 변환
 * - CREATE MATERIALIZED VIEW 변환
 * - REFRESH 옵션 변환
 * - DROP MATERIALIZED VIEW 변환
 * - MySQL 에뮬레이션 (테이블 + 프로시저)
 */
object MaterializedViewConverter {

    /**
     * CREATE MATERIALIZED VIEW 패턴 (Oracle)
     */
    private val CREATE_MVIEW_PATTERN = Regex(
        """CREATE\s+MATERIALIZED\s+VIEW\s+(?:(\w+)\.)?(\w+)(.*)AS\s+(.+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * DROP MATERIALIZED VIEW 패턴
     */
    private val DROP_MVIEW_PATTERN = Regex(
        """DROP\s+MATERIALIZED\s+VIEW\s+(?:IF\s+EXISTS\s+)?(?:(\w+)\.)?(\w+)(?:\s+CASCADE)?;?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * REFRESH MATERIALIZED VIEW 패턴 (PostgreSQL)
     */
    private val REFRESH_MVIEW_PATTERN = Regex(
        """REFRESH\s+MATERIALIZED\s+VIEW\s+(?:CONCURRENTLY\s+)?(?:(\w+)\.)?(\w+)(?:\s+WITH\s+(NO\s+)?DATA)?;?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Oracle REFRESH 옵션 패턴들
     */
    private val REFRESH_FAST_PATTERN = Regex("""REFRESH\s+FAST""", RegexOption.IGNORE_CASE)
    private val REFRESH_COMPLETE_PATTERN = Regex("""REFRESH\s+COMPLETE""", RegexOption.IGNORE_CASE)
    private val REFRESH_FORCE_PATTERN = Regex("""REFRESH\s+FORCE""", RegexOption.IGNORE_CASE)
    private val ON_COMMIT_PATTERN = Regex("""ON\s+COMMIT""", RegexOption.IGNORE_CASE)
    private val ON_DEMAND_PATTERN = Regex("""ON\s+DEMAND""", RegexOption.IGNORE_CASE)
    private val BUILD_IMMEDIATE_PATTERN = Regex("""BUILD\s+IMMEDIATE""", RegexOption.IGNORE_CASE)
    private val BUILD_DEFERRED_PATTERN = Regex("""BUILD\s+DEFERRED""", RegexOption.IGNORE_CASE)
    private val QUERY_REWRITE_PATTERN = Regex("""(?:ENABLE|DISABLE)\s+QUERY\s+REWRITE""", RegexOption.IGNORE_CASE)
    private val WITH_ROWID_PATTERN = Regex("""WITH\s+ROWID""", RegexOption.IGNORE_CASE)
    private val TABLESPACE_PATTERN = Regex("""TABLESPACE\s+(\w+)""", RegexOption.IGNORE_CASE)

    /**
     * PostgreSQL WITH DATA/NO DATA 패턴
     */
    private val WITH_DATA_PATTERN = Regex("""WITH\s+DATA""", RegexOption.IGNORE_CASE)
    private val WITH_NO_DATA_PATTERN = Regex("""WITH\s+NO\s+DATA""", RegexOption.IGNORE_CASE)

    /**
     * Materialized View 변환
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // CREATE MATERIALIZED VIEW 변환
        result = CREATE_MVIEW_PATTERN.replace(result) { match ->
            val schema = match.groupValues[1].ifEmpty { null }
            val mviewName = match.groupValues[2]
            val options = match.groupValues[3]
            val query = match.groupValues[4].trim().trimEnd(';')

            // query에서 WITH DATA/NO DATA 제거하고 options에 추가
            val cleanQuery = query.replace(WITH_NO_DATA_PATTERN, "").replace(WITH_DATA_PATTERN, "").trim()
            val queryOptions = if (WITH_NO_DATA_PATTERN.containsMatchIn(query)) " WITH NO DATA" else ""
            val mviewInfo = parseMViewOptions(options + queryOptions)

            when (targetDialect) {
                DialectType.MYSQL -> {
                    appliedRules.add("CREATE MATERIALIZED VIEW $mviewName → MySQL 테이블 에뮬레이션")
                    convertCreateMViewToMySql(schema, mviewName, cleanQuery, mviewInfo, warnings)
                }
                DialectType.POSTGRESQL -> {
                    appliedRules.add("CREATE MATERIALIZED VIEW $mviewName → PostgreSQL")
                    convertCreateMViewToPostgreSql(schema, mviewName, cleanQuery, mviewInfo, warnings)
                }
                DialectType.ORACLE -> {
                    appliedRules.add("CREATE MATERIALIZED VIEW $mviewName → Oracle")
                    convertCreateMViewToOracle(schema, mviewName, cleanQuery, mviewInfo, warnings)
                }
                else -> match.value
            }
        }

        // DROP MATERIALIZED VIEW 변환
        result = DROP_MVIEW_PATTERN.replace(result) { match ->
            val schema = match.groupValues[1].ifEmpty { null }
            val mviewName = match.groupValues[2]

            when (targetDialect) {
                DialectType.MYSQL -> {
                    appliedRules.add("DROP MATERIALIZED VIEW $mviewName → MySQL")
                    val fullName = schema?.let { "$it.$mviewName" } ?: mviewName
                    """-- Drop MView emulation table and procedure
DROP TABLE IF EXISTS $fullName;
DROP PROCEDURE IF EXISTS ${fullName}_refresh;"""
                }
                DialectType.POSTGRESQL -> {
                    appliedRules.add("DROP MATERIALIZED VIEW $mviewName → PostgreSQL")
                    val fullName = schema?.let { "$it.$mviewName" } ?: mviewName
                    "DROP MATERIALIZED VIEW IF EXISTS $fullName CASCADE;"
                }
                DialectType.ORACLE -> {
                    appliedRules.add("DROP MATERIALIZED VIEW $mviewName → Oracle")
                    val fullName = schema?.let { "$it.$mviewName" } ?: mviewName
                    "DROP MATERIALIZED VIEW $fullName;"
                }
                else -> match.value
            }
        }

        // REFRESH MATERIALIZED VIEW 변환
        result = REFRESH_MVIEW_PATTERN.replace(result) { match ->
            val schema = match.groupValues[1].ifEmpty { null }
            val mviewName = match.groupValues[2]
            val noData = match.groupValues[3].isNotEmpty()

            when (targetDialect) {
                DialectType.MYSQL -> {
                    appliedRules.add("REFRESH MATERIALIZED VIEW $mviewName → MySQL 프로시저 호출")
                    val fullName = schema?.let { "$it.$mviewName" } ?: mviewName
                    "CALL ${fullName}_refresh();"
                }
                DialectType.POSTGRESQL -> {
                    val fullName = schema?.let { "$it.$mviewName" } ?: mviewName
                    val dataClause = if (noData) " WITH NO DATA" else ""
                    "REFRESH MATERIALIZED VIEW $fullName$dataClause;"
                }
                DialectType.ORACLE -> {
                    appliedRules.add("REFRESH MATERIALIZED VIEW $mviewName → Oracle DBMS_MVIEW")
                    val fullName = schema?.let { "'$it.$mviewName'" } ?: "'$mviewName'"
                    "DBMS_MVIEW.REFRESH($fullName);"
                }
                else -> match.value
            }
        }

        return result
    }

    /**
     * Materialized View 옵션 파싱
     */
    private fun parseMViewOptions(options: String): MViewInfo {
        val refreshType = when {
            REFRESH_FAST_PATTERN.containsMatchIn(options) -> RefreshType.FAST
            REFRESH_COMPLETE_PATTERN.containsMatchIn(options) -> RefreshType.COMPLETE
            REFRESH_FORCE_PATTERN.containsMatchIn(options) -> RefreshType.FORCE
            else -> RefreshType.COMPLETE
        }

        val refreshTiming = when {
            ON_COMMIT_PATTERN.containsMatchIn(options) -> RefreshTiming.ON_COMMIT
            ON_DEMAND_PATTERN.containsMatchIn(options) -> RefreshTiming.ON_DEMAND
            else -> RefreshTiming.ON_DEMAND
        }

        val withData = !WITH_NO_DATA_PATTERN.containsMatchIn(options)

        val buildOption = when {
            BUILD_DEFERRED_PATTERN.containsMatchIn(options) -> BuildOption.DEFERRED
            !withData -> BuildOption.DEFERRED  // WITH NO DATA implies DEFERRED
            else -> BuildOption.IMMEDIATE
        }

        val queryRewriteEnabled = QUERY_REWRITE_PATTERN.find(options)?.value?.uppercase()?.contains("ENABLE") ?: false
        val tablespace = TABLESPACE_PATTERN.find(options)?.groupValues?.get(1)

        return MViewInfo(
            refreshType = refreshType,
            refreshTiming = refreshTiming,
            buildOption = buildOption,
            queryRewriteEnabled = queryRewriteEnabled,
            tablespace = tablespace,
            withData = withData
        )
    }

    /**
     * MySQL CREATE MATERIALIZED VIEW 변환 (테이블 + 프로시저 에뮬레이션)
     */
    private fun convertCreateMViewToMySql(
        schema: String?,
        mviewName: String,
        query: String,
        info: MViewInfo,
        warnings: MutableList<ConversionWarning>
    ): String {
        val fullName = schema?.let { "$it.$mviewName" } ?: mviewName

        warnings.add(ConversionWarning(
            type = WarningType.UNSUPPORTED_FUNCTION,
            message = "MySQL은 Materialized View를 네이티브로 지원하지 않습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "테이블과 저장 프로시저를 사용한 에뮬레이션으로 변환됩니다. 수동 또는 이벤트 스케줄러로 갱신이 필요합니다."
        ))

        return """-- Materialized View emulation for $mviewName
-- Create table to store materialized data
CREATE TABLE $fullName AS
$query;

-- Create refresh procedure
DROP PROCEDURE IF EXISTS ${fullName}_refresh;
DELIMITER //
CREATE PROCEDURE ${fullName}_refresh()
BEGIN
    -- Truncate and repopulate
    TRUNCATE TABLE $fullName;
    INSERT INTO $fullName
    $query;
END //
DELIMITER ;

-- To refresh, call: CALL ${fullName}_refresh();
-- For automatic refresh, create an event:
-- CREATE EVENT ${mviewName}_refresh_event
-- ON SCHEDULE EVERY 1 HOUR
-- DO CALL ${fullName}_refresh();"""
    }

    /**
     * PostgreSQL CREATE MATERIALIZED VIEW 변환
     */
    private fun convertCreateMViewToPostgreSql(
        schema: String?,
        mviewName: String,
        query: String,
        info: MViewInfo,
        warnings: MutableList<ConversionWarning>
    ): String {
        val fullName = schema?.let { "$it.$mviewName" } ?: mviewName

        val parts = mutableListOf<String>()
        parts.add("CREATE MATERIALIZED VIEW $fullName AS")
        parts.add(query)

        // WITH DATA / WITH NO DATA
        if (!info.withData || info.buildOption == BuildOption.DEFERRED) {
            parts.add("WITH NO DATA")
        } else {
            parts.add("WITH DATA")
        }

        // Oracle 특화 옵션 경고
        if (info.refreshTiming == RefreshTiming.ON_COMMIT) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "PostgreSQL은 ON COMMIT 자동 갱신을 지원하지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "REFRESH MATERIALIZED VIEW를 트리거나 애플리케이션 로직에서 수동으로 호출해야 합니다."
            ))
        }

        if (info.refreshType == RefreshType.FAST) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "PostgreSQL은 증분(FAST) 리프레시를 지원하지 않습니다.",
                severity = WarningSeverity.INFO,
                suggestion = "REFRESH MATERIALIZED VIEW CONCURRENTLY를 사용하면 동시 갱신이 가능합니다."
            ))
        }

        if (info.queryRewriteEnabled) {
            warnings.add(ConversionWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "PostgreSQL은 Query Rewrite를 지원하지 않습니다.",
                severity = WarningSeverity.INFO,
                suggestion = "쿼리에서 직접 Materialized View를 참조해야 합니다."
            ))
        }

        return parts.joinToString("\n") + ";"
    }

    /**
     * Oracle CREATE MATERIALIZED VIEW 변환
     */
    private fun convertCreateMViewToOracle(
        schema: String?,
        mviewName: String,
        query: String,
        info: MViewInfo,
        warnings: MutableList<ConversionWarning>
    ): String {
        val fullName = schema?.let { "$it.$mviewName" } ?: mviewName

        val parts = mutableListOf<String>()
        parts.add("CREATE MATERIALIZED VIEW $fullName")

        // BUILD 옵션
        when (info.buildOption) {
            BuildOption.IMMEDIATE -> parts.add("BUILD IMMEDIATE")
            BuildOption.DEFERRED -> parts.add("BUILD DEFERRED")
        }

        // REFRESH 옵션
        val refreshClause = when (info.refreshType) {
            RefreshType.FAST -> "REFRESH FAST"
            RefreshType.COMPLETE -> "REFRESH COMPLETE"
            RefreshType.FORCE -> "REFRESH FORCE"
        }
        val timingClause = when (info.refreshTiming) {
            RefreshTiming.ON_COMMIT -> "ON COMMIT"
            RefreshTiming.ON_DEMAND -> "ON DEMAND"
        }
        parts.add("$refreshClause $timingClause")

        // Query Rewrite
        if (info.queryRewriteEnabled) {
            parts.add("ENABLE QUERY REWRITE")
        }

        // TABLESPACE
        info.tablespace?.let {
            parts.add("TABLESPACE $it")
        }

        parts.add("AS")
        parts.add(query)

        return parts.joinToString("\n") + ";"
    }

    /**
     * Materialized View 관련 문인지 확인
     */
    fun isMaterializedViewStatement(sql: String): Boolean {
        val upper = sql.uppercase()
        return upper.contains("MATERIALIZED VIEW") &&
               (upper.contains("CREATE") || upper.contains("DROP") || upper.contains("REFRESH") || upper.contains("ALTER"))
    }

    /**
     * Materialized View 정보
     */
    data class MViewInfo(
        val refreshType: RefreshType = RefreshType.COMPLETE,
        val refreshTiming: RefreshTiming = RefreshTiming.ON_DEMAND,
        val buildOption: BuildOption = BuildOption.IMMEDIATE,
        val queryRewriteEnabled: Boolean = false,
        val tablespace: String? = null,
        val withData: Boolean = true
    )

    enum class RefreshType {
        FAST, COMPLETE, FORCE
    }

    enum class RefreshTiming {
        ON_COMMIT, ON_DEMAND
    }

    enum class BuildOption {
        IMMEDIATE, DEFERRED
    }
}
