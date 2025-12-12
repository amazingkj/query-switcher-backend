package com.sqlswitcher.service

import com.sqlswitcher.converter.DialectType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

/**
 * 외부 Docker DB에 연결하여 SQL을 실행하는 서비스
 */
@Service
class SqlExecutionService {

    private val logger = LoggerFactory.getLogger(SqlExecutionService::class.java)

    // DB 연결 정보 (application.yml에서 설정)
    @Value("\${sql-test.mysql.url:jdbc:mysql://localhost:3306/testdb}")
    private lateinit var mysqlUrl: String

    @Value("\${sql-test.mysql.username:testuser}")
    private lateinit var mysqlUsername: String

    @Value("\${sql-test.mysql.password:test1234}")
    private lateinit var mysqlPassword: String

    @Value("\${sql-test.postgresql.url:jdbc:postgresql://localhost:5432/testdb}")
    private lateinit var postgresqlUrl: String

    @Value("\${sql-test.postgresql.username:testuser}")
    private lateinit var postgresqlUsername: String

    @Value("\${sql-test.postgresql.password:test1234}")
    private lateinit var postgresqlPassword: String

    @Value("\${sql-test.oracle.url:jdbc:oracle:thin:@localhost:1521/XEPDB1}")
    private lateinit var oracleUrl: String

    @Value("\${sql-test.oracle.username:testuser}")
    private lateinit var oracleUsername: String

    @Value("\${sql-test.oracle.password:test1234}")
    private lateinit var oraclePassword: String

    /**
     * SQL 실행 결과
     */
    data class ExecutionResult(
        val success: Boolean,
        val dialect: DialectType,
        val executionTimeMs: Long,
        val data: List<Map<String, Any?>>? = null,
        val columns: List<ColumnInfo>? = null,
        val rowsAffected: Int? = null,
        val error: ExecutionError? = null,
        val message: String? = null
    )

    data class ColumnInfo(
        val name: String,
        val type: String,
        val displaySize: Int
    )

    data class ExecutionError(
        val code: String?,
        val message: String,
        val sqlState: String?,
        val suggestion: String? = null
    )

    /**
     * DB 연결 상태 확인
     */
    data class ConnectionStatus(
        val dialect: DialectType,
        val connected: Boolean,
        val message: String,
        val version: String? = null
    )

    /**
     * SQL 실행 (SELECT는 결과 반환, 그 외는 영향받은 행 수 반환)
     */
    fun executeSql(sql: String, dialect: DialectType, dryRun: Boolean = true): ExecutionResult {
        val startTime = System.currentTimeMillis()

        return try {
            getConnection(dialect).use { conn ->
                if (dryRun) {
                    conn.autoCommit = false
                }

                try {
                    val result = when {
                        isSelectStatement(sql) -> executeQuery(conn, sql)
                        else -> executeUpdate(conn, sql)
                    }

                    if (dryRun) {
                        conn.rollback()
                        result.copy(
                            executionTimeMs = System.currentTimeMillis() - startTime,
                            message = "DryRun 모드: 변경사항이 롤백되었습니다."
                        )
                    } else {
                        conn.commit()
                        result.copy(
                            executionTimeMs = System.currentTimeMillis() - startTime,
                            message = "실행 완료"
                        )
                    }
                } catch (e: SQLException) {
                    if (dryRun) {
                        try { conn.rollback() } catch (_: Exception) {}
                    }
                    throw e
                }
            }
        } catch (e: SQLException) {
            logger.warn("SQL 실행 실패 [${dialect}]: ${e.message}")
            ExecutionResult(
                success = false,
                dialect = dialect,
                executionTimeMs = System.currentTimeMillis() - startTime,
                error = ExecutionError(
                    code = e.errorCode.toString(),
                    message = e.message ?: "SQL 실행 오류",
                    sqlState = e.sqlState,
                    suggestion = getSuggestion(e, dialect)
                )
            )
        } catch (e: Exception) {
            logger.error("DB 연결 실패 [${dialect}]: ${e.message}")
            ExecutionResult(
                success = false,
                dialect = dialect,
                executionTimeMs = System.currentTimeMillis() - startTime,
                error = ExecutionError(
                    code = null,
                    message = e.message ?: "연결 오류",
                    sqlState = null,
                    suggestion = "Docker 컨테이너가 실행 중인지 확인하세요: docker-compose -f docker-compose.test.yml ps"
                )
            )
        }
    }

    /**
     * DB 연결 상태 확인
     */
    fun checkConnection(dialect: DialectType): ConnectionStatus {
        return try {
            getConnection(dialect).use { conn ->
                val version = conn.metaData.databaseProductVersion
                ConnectionStatus(
                    dialect = dialect,
                    connected = true,
                    message = "연결 성공",
                    version = version
                )
            }
        } catch (e: Exception) {
            ConnectionStatus(
                dialect = dialect,
                connected = false,
                message = e.message ?: "연결 실패"
            )
        }
    }

    /**
     * 모든 DB 연결 상태 확인
     */
    fun checkAllConnections(): Map<DialectType, ConnectionStatus> {
        return DialectType.entries.associateWith { checkConnection(it) }
    }

    /**
     * SELECT 쿼리 실행
     */
    private fun executeQuery(conn: Connection, sql: String): ExecutionResult {
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val columns = getColumnInfo(rs)
                val data = mutableListOf<Map<String, Any?>>()

                // 최대 1000행까지만 반환
                var rowCount = 0
                while (rs.next() && rowCount < 1000) {
                    val row = mutableMapOf<String, Any?>()
                    columns.forEach { col ->
                        row[col.name] = rs.getObject(col.name)
                    }
                    data.add(row)
                    rowCount++
                }

                return ExecutionResult(
                    success = true,
                    dialect = DialectType.MYSQL, // 임시, 호출 시 덮어씀
                    executionTimeMs = 0,
                    data = data,
                    columns = columns,
                    rowsAffected = data.size
                )
            }
        }
    }

    /**
     * UPDATE/INSERT/DELETE 등 실행
     */
    private fun executeUpdate(conn: Connection, sql: String): ExecutionResult {
        // 여러 문장 지원
        val statements = sql.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var totalAffected = 0

        statements.forEach { stmt ->
            conn.createStatement().use { statement ->
                totalAffected += statement.executeUpdate(stmt)
            }
        }

        return ExecutionResult(
            success = true,
            dialect = DialectType.MYSQL,
            executionTimeMs = 0,
            rowsAffected = totalAffected
        )
    }

    /**
     * 컬럼 정보 추출
     */
    private fun getColumnInfo(rs: ResultSet): List<ColumnInfo> {
        val metaData = rs.metaData
        return (1..metaData.columnCount).map { i ->
            ColumnInfo(
                name = metaData.getColumnLabel(i),
                type = metaData.getColumnTypeName(i),
                displaySize = metaData.getColumnDisplaySize(i)
            )
        }
    }

    /**
     * DB 연결 가져오기
     */
    private fun getConnection(dialect: DialectType): Connection {
        val (url, username, password) = when (dialect) {
            DialectType.MYSQL -> Triple(mysqlUrl, mysqlUsername, mysqlPassword)
            DialectType.POSTGRESQL -> Triple(postgresqlUrl, postgresqlUsername, postgresqlPassword)
            DialectType.ORACLE -> Triple(oracleUrl, oracleUsername, oraclePassword)
        }

        return DriverManager.getConnection(url, username, password)
    }

    /**
     * SELECT 문인지 확인
     */
    private fun isSelectStatement(sql: String): Boolean {
        val trimmed = sql.trim().uppercase()
        return trimmed.startsWith("SELECT") ||
                trimmed.startsWith("WITH") ||
                trimmed.startsWith("SHOW") ||
                trimmed.startsWith("DESCRIBE") ||
                trimmed.startsWith("EXPLAIN")
    }

    /**
     * 오류에 대한 제안 생성
     */
    private fun getSuggestion(e: SQLException, dialect: DialectType): String? {
        val message = e.message?.uppercase() ?: return null

        return when {
            message.contains("CONNECTION") || message.contains("REFUSED") ->
                "데이터베이스 서버에 연결할 수 없습니다. Docker 컨테이너가 실행 중인지 확인하세요."

            message.contains("ACCESS DENIED") || message.contains("AUTHENTICATION") ->
                "인증 실패. 사용자명과 비밀번호를 확인하세요."

            message.contains("DOESN'T EXIST") || message.contains("DOES NOT EXIST") ->
                "테이블 또는 객체가 존재하지 않습니다."

            message.contains("SYNTAX") ->
                "${dialect.name} SQL 문법을 확인하세요."

            message.contains("DUPLICATE") ->
                "중복된 키 또는 데이터가 존재합니다."

            message.contains("CONSTRAINT") || message.contains("FOREIGN KEY") ->
                "제약조건 위반입니다. 참조 무결성을 확인하세요."

            else -> null
        }
    }
}