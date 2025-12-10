package com.sqlswitcher.service

import com.sqlswitcher.converter.DialectType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.OracleContainer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap
import jakarta.annotation.PreDestroy

/**
 * Testcontainers를 사용한 실제 SQL 실행 테스트 서비스
 */
@Service
class SqlTestService {

    private val logger = LoggerFactory.getLogger(SqlTestService::class.java)

    // 컨테이너 캐시 (재사용을 위해)
    private val containerCache = ConcurrentHashMap<DialectType, ContainerInfo>()

    data class ContainerInfo(
        val container: Any,
        val jdbcUrl: String,
        val username: String,
        val password: String
    )

    /**
     * SQL 실행 테스트 결과
     */
    data class TestResult(
        val success: Boolean,
        val dialect: DialectType,
        val executionTimeMs: Long,
        val error: TestError? = null,
        val rowsAffected: Int? = null,
        val message: String? = null
    )

    data class TestError(
        val code: String?,
        val message: String,
        val sqlState: String?,
        val suggestion: String? = null
    )

    /**
     * SQL을 실제 DB에서 테스트 실행
     * DryRun 모드: 트랜잭션을 롤백하여 실제 변경 없이 검증
     */
    fun testSql(sql: String, dialect: DialectType, dryRun: Boolean = true): TestResult {
        val startTime = System.currentTimeMillis()

        return try {
            val containerInfo = getOrCreateContainer(dialect)
            val connection = DriverManager.getConnection(
                containerInfo.jdbcUrl,
                containerInfo.username,
                containerInfo.password
            )

            connection.use { conn ->
                if (dryRun) {
                    conn.autoCommit = false
                }

                try {
                    val result = executeSql(conn, sql, dialect)
                    val executionTime = System.currentTimeMillis() - startTime

                    if (dryRun) {
                        conn.rollback() // DryRun: 롤백으로 변경 취소
                    } else {
                        conn.commit()
                    }

                    TestResult(
                        success = true,
                        dialect = dialect,
                        executionTimeMs = executionTime,
                        rowsAffected = result,
                        message = if (dryRun) "DryRun 성공 (변경사항 롤백됨)" else "실행 성공"
                    )
                } catch (e: SQLException) {
                    if (dryRun) {
                        conn.rollback()
                    }
                    throw e
                }
            }
        } catch (e: SQLException) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.warn("SQL 테스트 실패: ${e.message}")

            TestResult(
                success = false,
                dialect = dialect,
                executionTimeMs = executionTime,
                error = TestError(
                    code = e.errorCode.toString(),
                    message = e.message ?: "알 수 없는 SQL 오류",
                    sqlState = e.sqlState,
                    suggestion = getSuggestionForError(e, dialect)
                )
            )
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("SQL 테스트 중 오류: ${e.message}", e)

            TestResult(
                success = false,
                dialect = dialect,
                executionTimeMs = executionTime,
                error = TestError(
                    code = null,
                    message = e.message ?: "알 수 없는 오류",
                    sqlState = null,
                    suggestion = "Docker가 실행 중인지 확인하세요."
                )
            )
        }
    }

    /**
     * SQL 실행
     */
    private fun executeSql(conn: Connection, sql: String, dialect: DialectType): Int {
        // 여러 문장 지원 (세미콜론으로 분리)
        val statements = sql.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var totalAffected = 0

        statements.forEach { stmt ->
            conn.createStatement().use { statement ->
                // SELECT인지 확인
                if (stmt.trim().uppercase().startsWith("SELECT")) {
                    statement.executeQuery(stmt).use { rs ->
                        // 결과 세트 확인 (검증 목적)
                        var rowCount = 0
                        while (rs.next()) {
                            rowCount++
                        }
                        totalAffected += rowCount
                    }
                } else {
                    totalAffected += statement.executeUpdate(stmt)
                }
            }
        }

        return totalAffected
    }

    /**
     * 컨테이너 가져오기 또는 생성
     */
    @Synchronized
    private fun getOrCreateContainer(dialect: DialectType): ContainerInfo {
        return containerCache.getOrPut(dialect) {
            logger.info("$dialect 컨테이너 시작 중...")
            createContainer(dialect)
        }
    }

    /**
     * 방언별 컨테이너 생성
     */
    private fun createContainer(dialect: DialectType): ContainerInfo {
        return when (dialect) {
            DialectType.MYSQL -> {
                val container = MySQLContainer("mysql:8.0")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test")
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")

                container.start()
                logger.info("MySQL 컨테이너 시작 완료: ${container.jdbcUrl}")

                ContainerInfo(
                    container = container,
                    jdbcUrl = container.jdbcUrl,
                    username = container.username,
                    password = container.password
                )
            }
            DialectType.POSTGRESQL -> {
                val container = PostgreSQLContainer("postgres:15")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test")

                container.start()
                logger.info("PostgreSQL 컨테이너 시작 완료: ${container.jdbcUrl}")

                ContainerInfo(
                    container = container,
                    jdbcUrl = container.jdbcUrl,
                    username = container.username,
                    password = container.password
                )
            }
            DialectType.ORACLE -> {
                // Oracle XE 컨테이너
                val container = OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test")

                container.start()
                logger.info("Oracle 컨테이너 시작 완료: ${container.jdbcUrl}")

                ContainerInfo(
                    container = container,
                    jdbcUrl = container.jdbcUrl,
                    username = container.username,
                    password = container.password
                )
            }
        }
    }

    /**
     * SQL 오류에 대한 제안 생성
     */
    private fun getSuggestionForError(e: SQLException, dialect: DialectType): String? {
        val message = e.message?.uppercase() ?: return null

        return when {
            // 테이블 없음
            message.contains("TABLE") && (message.contains("DOESN'T EXIST") || message.contains("DOES NOT EXIST")) ->
                "테이블이 존재하지 않습니다. CREATE TABLE 문을 먼저 실행하세요."

            // 컬럼 없음
            message.contains("COLUMN") && message.contains("NOT FOUND") ->
                "컬럼이 존재하지 않습니다. 컬럼명을 확인하세요."

            // 문법 오류
            message.contains("SYNTAX") ->
                "SQL 문법 오류입니다. ${dialect.name} 문법을 확인하세요."

            // 함수 없음
            message.contains("FUNCTION") && message.contains("NOT EXIST") ->
                "함수가 존재하지 않습니다. ${dialect.name}에서 지원하는 함수인지 확인하세요."

            // 권한 오류
            message.contains("PERMISSION") || message.contains("DENIED") ->
                "권한이 없습니다."

            else -> null
        }
    }

    /**
     * 컨테이너 상태 확인
     */
    fun isContainerRunning(dialect: DialectType): Boolean {
        val info = containerCache[dialect] ?: return false
        return when (val container = info.container) {
            is MySQLContainer<*> -> container.isRunning
            is PostgreSQLContainer<*> -> container.isRunning
            is OracleContainer -> container.isRunning
            else -> false
        }
    }

    /**
     * 컨테이너 상태 정보
     */
    fun getContainerStatus(): Map<DialectType, Boolean> {
        return DialectType.entries.associateWith { isContainerRunning(it) }
    }

    /**
     * 컨테이너 정리
     */
    @PreDestroy
    fun cleanup() {
        logger.info("테스트 컨테이너 정리 중...")
        containerCache.values.forEach { info ->
            try {
                when (val container = info.container) {
                    is MySQLContainer<*> -> container.stop()
                    is PostgreSQLContainer<*> -> container.stop()
                    is OracleContainer -> container.stop()
                }
            } catch (e: Exception) {
                logger.warn("컨테이너 정리 중 오류: ${e.message}")
            }
        }
        containerCache.clear()
    }
}