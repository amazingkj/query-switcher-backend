package com.sqlswitcher.converter.feature.dblink

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * Database Link 변환기
 *
 * Oracle Database Link를 다른 DB로 변환
 * - CREATE DATABASE LINK 변환
 * - @dblink 참조 변환
 * - DROP DATABASE LINK 변환
 *
 * 대상 DB 변환:
 * - PostgreSQL: postgres_fdw (Foreign Data Wrapper)
 * - MySQL: FEDERATED 엔진 (제한적)
 */
object DatabaseLinkConverter {

    /**
     * CREATE DATABASE LINK 패턴
     */
    private val CREATE_DBLINK_PATTERN = Regex(
        """CREATE\s+(?:(PUBLIC|SHARED)\s+)?DATABASE\s+LINK\s+(\w+)\s+CONNECT\s+TO\s+(\w+)\s+IDENTIFIED\s+BY\s+(\S+)\s+USING\s+'([^']+)'""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DROP DATABASE LINK 패턴
     */
    private val DROP_DBLINK_PATTERN = Regex(
        """DROP\s+(?:(PUBLIC)\s+)?DATABASE\s+LINK\s+(?:IF\s+EXISTS\s+)?(\w+);?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Oracle @dblink 참조 패턴
     * table@dblink, schema.table@dblink 형식
     */
    private val DBLINK_REFERENCE_PATTERN = Regex(
        """(\w+(?:\.\w+)?)@(\w+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Database Link 변환
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // CREATE DATABASE LINK 변환
        result = CREATE_DBLINK_PATTERN.replace(result) { match ->
            val isPublic = match.groupValues[1].isNotEmpty()
            val linkName = match.groupValues[2]
            val username = match.groupValues[3]
            val password = match.groupValues[4]
            val connectString = match.groupValues[5]

            val linkInfo = DbLinkInfo(
                isPublic = isPublic,
                linkName = linkName,
                username = username,
                password = password,
                connectString = connectString
            )

            when (targetDialect) {
                DialectType.MYSQL -> {
                    appliedRules.add("CREATE DATABASE LINK $linkName → MySQL FEDERATED (제한적)")
                    convertCreateDbLinkToMySql(linkInfo, warnings)
                }
                DialectType.POSTGRESQL -> {
                    appliedRules.add("CREATE DATABASE LINK $linkName → PostgreSQL FDW")
                    convertCreateDbLinkToPostgreSql(linkInfo, warnings)
                }
                DialectType.ORACLE -> {
                    // Oracle → Oracle은 그대로
                    match.value
                }
                else -> match.value
            }
        }

        // DROP DATABASE LINK 변환
        result = DROP_DBLINK_PATTERN.replace(result) { match ->
            val isPublic = match.groupValues[1].isNotEmpty()
            val linkName = match.groupValues[2]

            when (targetDialect) {
                DialectType.MYSQL -> {
                    appliedRules.add("DROP DATABASE LINK $linkName → MySQL")
                    warnings.add(ConversionWarning(
                        type = WarningType.UNSUPPORTED_FUNCTION,
                        message = "MySQL은 Database Link를 직접 지원하지 않습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "FEDERATED 테이블을 수동으로 삭제해야 합니다."
                    ))
                    "-- MySQL: Remove FEDERATED tables manually\n-- DROP DATABASE LINK $linkName;"
                }
                DialectType.POSTGRESQL -> {
                    appliedRules.add("DROP DATABASE LINK $linkName → PostgreSQL")
                    """-- Drop foreign server and user mapping
DROP USER MAPPING IF EXISTS FOR CURRENT_USER SERVER ${linkName}_server;
DROP SERVER IF EXISTS ${linkName}_server CASCADE;"""
                }
                DialectType.ORACLE -> {
                    val publicPart = if (isPublic) "PUBLIC " else ""
                    "DROP ${publicPart}DATABASE LINK $linkName;"
                }
                else -> match.value
            }
        }

        // @dblink 참조 변환
        if (sourceDialect == DialectType.ORACLE && targetDialect != DialectType.ORACLE) {
            result = convertDbLinkReferences(result, targetDialect, warnings, appliedRules)
        }

        return result
    }

    /**
     * @dblink 참조 변환
     */
    private fun convertDbLinkReferences(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val dblinks = mutableSetOf<String>()

        val result = DBLINK_REFERENCE_PATTERN.replace(sql) { match ->
            val tableRef = match.groupValues[1]
            val linkName = match.groupValues[2]
            dblinks.add(linkName)

            when (targetDialect) {
                DialectType.POSTGRESQL -> {
                    // PostgreSQL FDW: 외부 테이블명 사용
                    "${linkName}_${tableRef.replace(".", "_")}"
                }
                DialectType.MYSQL -> {
                    // MySQL FEDERATED: 로컬 테이블명 사용
                    "${linkName}_${tableRef.replace(".", "_")}"
                }
                else -> match.value
            }
        }

        if (dblinks.isNotEmpty()) {
            appliedRules.add("@dblink 참조 변환: ${dblinks.joinToString(", ")}")
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "Database Link 참조(@${dblinks.first()})가 로컬 테이블 참조로 변환되었습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "원격 테이블을 Foreign Table/FEDERATED Table로 먼저 생성해야 합니다."
            ))
        }

        return result
    }

    /**
     * MySQL CREATE DATABASE LINK 변환
     * MySQL은 FEDERATED 엔진으로 제한적 지원
     */
    private fun convertCreateDbLinkToMySql(
        info: DbLinkInfo,
        warnings: MutableList<ConversionWarning>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.UNSUPPORTED_FUNCTION,
            message = "MySQL은 Database Link를 직접 지원하지 않습니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "FEDERATED 스토리지 엔진으로 각 테이블별로 연결해야 합니다. FEDERATED 엔진이 활성화되어 있어야 합니다."
        ))

        // TNS 연결 문자열을 MySQL 연결 정보로 변환 시도
        val (host, port, service) = parseTnsConnectString(info.connectString)

        return """-- MySQL FEDERATED table emulation for Database Link '${info.linkName}'
-- Note: FEDERATED storage engine must be enabled
-- Create FEDERATED tables for each remote table you need:
--
-- CREATE TABLE ${info.linkName}_<remote_table> (
--     <column definitions matching remote table>
-- ) ENGINE=FEDERATED
-- CONNECTION='mysql://${info.username}:${info.password}@$host:$port/$service/<remote_table>';
--
-- Example:
CREATE TABLE ${info.linkName}_example (
    id INT,
    name VARCHAR(100)
) ENGINE=FEDERATED
CONNECTION='mysql://${info.username}:${info.password}@$host:$port/$service/example';"""
    }

    /**
     * PostgreSQL CREATE DATABASE LINK 변환
     * PostgreSQL postgres_fdw 사용
     */
    private fun convertCreateDbLinkToPostgreSql(
        info: DbLinkInfo,
        warnings: MutableList<ConversionWarning>
    ): String {
        warnings.add(ConversionWarning(
            type = WarningType.SYNTAX_DIFFERENCE,
            message = "PostgreSQL은 Foreign Data Wrapper (postgres_fdw)를 사용합니다.",
            severity = WarningSeverity.INFO,
            suggestion = "원격 테이블마다 FOREIGN TABLE을 생성해야 합니다."
        ))

        // TNS 연결 문자열을 호스트/포트/데이터베이스로 파싱
        val (host, port, dbname) = parseTnsConnectString(info.connectString)

        return """-- PostgreSQL Foreign Data Wrapper for Database Link '${info.linkName}'
-- Ensure postgres_fdw extension is installed
CREATE EXTENSION IF NOT EXISTS postgres_fdw;

-- Create foreign server
CREATE SERVER IF NOT EXISTS ${info.linkName}_server
    FOREIGN DATA WRAPPER postgres_fdw
    OPTIONS (host '$host', port '$port', dbname '$dbname');

-- Create user mapping
CREATE USER MAPPING IF NOT EXISTS FOR CURRENT_USER
    SERVER ${info.linkName}_server
    OPTIONS (user '${info.username}', password '${info.password}');

-- Import foreign schema or create individual foreign tables
-- Example:
-- IMPORT FOREIGN SCHEMA public
-- FROM SERVER ${info.linkName}_server
-- INTO ${info.linkName}_schema;
--
-- Or create individual foreign table:
-- CREATE FOREIGN TABLE ${info.linkName}_example (
--     id INTEGER,
--     name TEXT
-- ) SERVER ${info.linkName}_server
-- OPTIONS (schema_name 'public', table_name 'example');"""
    }

    /**
     * Oracle TNS 연결 문자열 파싱
     * (DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=host)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=orcl)))
     * 또는 간단한 형식: host:port/service
     */
    private fun parseTnsConnectString(connectString: String): Triple<String, String, String> {
        // 간단한 형식 시도: host:port/service 또는 host/service
        val simplePattern = Regex("""([^:/]+)(?::(\d+))?/(\w+)""")
        val simpleMatch = simplePattern.find(connectString)
        if (simpleMatch != null) {
            val host = simpleMatch.groupValues[1]
            val port = simpleMatch.groupValues[2].ifEmpty { "1521" }
            val service = simpleMatch.groupValues[3]
            return Triple(host, port, service)
        }

        // TNS 형식 파싱
        val hostPattern = Regex("""HOST\s*=\s*([^)]+)""", RegexOption.IGNORE_CASE)
        val portPattern = Regex("""PORT\s*=\s*(\d+)""", RegexOption.IGNORE_CASE)
        val servicePattern = Regex("""SERVICE_NAME\s*=\s*([^)]+)""", RegexOption.IGNORE_CASE)
        val sidPattern = Regex("""SID\s*=\s*([^)]+)""", RegexOption.IGNORE_CASE)

        val host = hostPattern.find(connectString)?.groupValues?.get(1)?.trim() ?: "localhost"
        val port = portPattern.find(connectString)?.groupValues?.get(1)?.trim() ?: "1521"
        val service = servicePattern.find(connectString)?.groupValues?.get(1)?.trim()
            ?: sidPattern.find(connectString)?.groupValues?.get(1)?.trim()
            ?: "orcl"

        return Triple(host, port, service)
    }

    /**
     * Database Link 관련 문인지 확인
     */
    fun isDatabaseLinkStatement(sql: String): Boolean {
        val upper = sql.uppercase()
        return (upper.contains("DATABASE LINK") || upper.contains("DBLINK")) &&
               (upper.contains("CREATE") || upper.contains("DROP"))
    }

    /**
     * @dblink 참조가 있는지 확인
     */
    fun hasDbLinkReference(sql: String): Boolean {
        return DBLINK_REFERENCE_PATTERN.containsMatchIn(sql)
    }

    /**
     * 참조된 Database Link 이름 추출
     */
    fun getReferencedDbLinks(sql: String): List<String> {
        return DBLINK_REFERENCE_PATTERN.findAll(sql)
            .map { it.groupValues[2] }
            .distinct()
            .toList()
    }

    /**
     * Database Link 정보
     */
    data class DbLinkInfo(
        val isPublic: Boolean = false,
        val linkName: String,
        val username: String,
        val password: String,
        val connectString: String
    )
}
