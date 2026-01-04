package com.sqlswitcher.cli

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.feature.dblink.DatabaseLinkConverter
import com.sqlswitcher.converter.feature.dbms.DbmsPackageConverter
import com.sqlswitcher.converter.feature.index.AdvancedIndexConverter
import com.sqlswitcher.converter.feature.mview.MaterializedViewConverter
import com.sqlswitcher.converter.feature.sequence.AdvancedSequenceConverter
import com.sqlswitcher.converter.feature.synonym.SynonymConverter
import java.io.File

/**
 * SQL 변환 CLI 도구
 *
 * 명령줄에서 SQL 파일을 변환할 수 있는 독립 실행형 도구입니다.
 *
 * 사용법:
 *   java -jar sql-converter-cli.jar [options]
 *
 * 옵션:
 *   -s, --source <dialect>    소스 데이터베이스 (oracle, mysql, postgresql)
 *   -t, --target <dialect>    대상 데이터베이스 (oracle, mysql, postgresql)
 *   -i, --input <file>        입력 SQL 파일
 *   -o, --output <file>       출력 파일 (기본: stdout)
 *   -q, --query <sql>         직접 SQL 쿼리 입력
 *   -v, --verbose             상세 출력
 *   -h, --help                도움말 표시
 */
object SqlConverterCli {

    private const val VERSION = "1.0.0"

    @JvmStatic
    fun main(args: Array<String>) {
        val config = parseArguments(args)

        if (config.showHelp) {
            printHelp()
            return
        }

        if (config.showVersion) {
            println("SQL Converter CLI v$VERSION")
            return
        }

        try {
            val result = convert(config)
            if (config.outputFile != null) {
                File(config.outputFile).writeText(result.convertedSql)
                println("변환 완료: ${config.outputFile}")
            } else {
                println(result.convertedSql)
            }

            if (config.verbose && result.warnings.isNotEmpty()) {
                println("\n--- 경고 ---")
                result.warnings.forEach { warning ->
                    println("[${warning.severity}] ${warning.message}")
                    warning.suggestion?.let { println("  제안: $it") }
                }
            }

            if (config.verbose && result.appliedRules.isNotEmpty()) {
                println("\n--- 적용된 규칙 ---")
                result.appliedRules.forEach { rule ->
                    println("  - $rule")
                }
            }
        } catch (e: Exception) {
            System.err.println("오류: ${e.message}")
            if (config.verbose) {
                e.printStackTrace()
            }
            System.exit(1)
        }
    }

    private fun parseArguments(args: Array<String>): CliConfig {
        var source: DialectType? = null
        var target: DialectType? = null
        var inputFile: String? = null
        var outputFile: String? = null
        var query: String? = null
        var verbose = false
        var showHelp = false
        var showVersion = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-s", "--source" -> {
                    source = parseDialect(args.getOrNull(++i))
                }
                "-t", "--target" -> {
                    target = parseDialect(args.getOrNull(++i))
                }
                "-i", "--input" -> {
                    inputFile = args.getOrNull(++i)
                }
                "-o", "--output" -> {
                    outputFile = args.getOrNull(++i)
                }
                "-q", "--query" -> {
                    query = args.getOrNull(++i)
                }
                "-v", "--verbose" -> {
                    verbose = true
                }
                "-h", "--help" -> {
                    showHelp = true
                }
                "--version" -> {
                    showVersion = true
                }
            }
            i++
        }

        return CliConfig(
            sourceDialect = source,
            targetDialect = target,
            inputFile = inputFile,
            outputFile = outputFile,
            query = query,
            verbose = verbose,
            showHelp = showHelp,
            showVersion = showVersion
        )
    }

    private fun parseDialect(value: String?): DialectType? {
        return when (value?.lowercase()) {
            "oracle" -> DialectType.ORACLE
            "mysql" -> DialectType.MYSQL
            "postgresql", "postgres", "pg" -> DialectType.POSTGRESQL
            else -> null
        }
    }

    private fun convert(config: CliConfig): ConversionResult {
        val sourceDialect = config.sourceDialect
            ?: throw IllegalArgumentException("소스 데이터베이스를 지정해주세요 (-s oracle/mysql/postgresql)")

        val targetDialect = config.targetDialect
            ?: throw IllegalArgumentException("대상 데이터베이스를 지정해주세요 (-t oracle/mysql/postgresql)")

        val sql = when {
            config.query != null -> config.query
            config.inputFile != null -> File(config.inputFile).readText()
            else -> throw IllegalArgumentException("SQL 쿼리 (-q) 또는 입력 파일 (-i)을 지정해주세요")
        }

        val warnings = mutableListOf<ConversionWarning>()
        val appliedRules = mutableListOf<String>()

        var convertedSql = sql

        // 각 변환기 적용
        if (AdvancedSequenceConverter.isSequenceStatement(convertedSql) ||
            AdvancedSequenceConverter.hasSequenceReference(convertedSql)) {
            convertedSql = AdvancedSequenceConverter.convert(
                convertedSql, sourceDialect, targetDialect, warnings, appliedRules
            )
        }

        if (AdvancedIndexConverter.isIndexStatement(convertedSql)) {
            convertedSql = AdvancedIndexConverter.convert(
                convertedSql, sourceDialect, targetDialect, warnings, appliedRules
            )
        }

        if (MaterializedViewConverter.isMaterializedViewStatement(convertedSql)) {
            convertedSql = MaterializedViewConverter.convert(
                convertedSql, sourceDialect, targetDialect, warnings, appliedRules
            )
        }

        if (DatabaseLinkConverter.isDatabaseLinkStatement(convertedSql) ||
            DatabaseLinkConverter.hasDbLinkReference(convertedSql)) {
            convertedSql = DatabaseLinkConverter.convert(
                convertedSql, sourceDialect, targetDialect, warnings, appliedRules
            )
        }

        if (SynonymConverter.hasSynonymStatements(convertedSql)) {
            convertedSql = SynonymConverter.convert(
                convertedSql, sourceDialect, targetDialect, warnings, appliedRules
            )
        }

        if (DbmsPackageConverter.hasDbmsPackageCalls(convertedSql)) {
            convertedSql = DbmsPackageConverter.convert(
                convertedSql, sourceDialect, targetDialect, warnings, appliedRules
            )
        }

        return ConversionResult(
            convertedSql = convertedSql,
            warnings = warnings,
            appliedRules = appliedRules
        )
    }

    private fun printHelp() {
        println("""
SQL Converter CLI v$VERSION
Oracle/MySQL/PostgreSQL 간 SQL 변환 도구

사용법:
  java -jar sql-converter-cli.jar [options]

옵션:
  -s, --source <dialect>    소스 데이터베이스 (oracle, mysql, postgresql)
  -t, --target <dialect>    대상 데이터베이스 (oracle, mysql, postgresql)
  -i, --input <file>        입력 SQL 파일
  -o, --output <file>       출력 파일 (기본: stdout)
  -q, --query <sql>         직접 SQL 쿼리 입력
  -v, --verbose             상세 출력 (경고, 적용된 규칙)
  -h, --help                도움말 표시
  --version                 버전 표시

예제:
  # Oracle SQL을 PostgreSQL로 변환
  java -jar sql-converter-cli.jar -s oracle -t postgresql -i input.sql -o output.sql

  # 직접 쿼리 변환
  java -jar sql-converter-cli.jar -s oracle -t mysql -q "SELECT emp_seq.NEXTVAL FROM dual"

지원 기능:
  - 시퀀스 (CREATE/DROP SEQUENCE, NEXTVAL/CURRVAL)
  - 인덱스 (CREATE/DROP INDEX, BITMAP, REVERSE, FUNCTION-BASED)
  - Materialized View (CREATE/DROP/REFRESH)
  - Database Link (@dblink 참조, CREATE/DROP DATABASE LINK)
  - Synonym (CREATE/DROP SYNONYM)
  - DBMS_* 패키지 (DBMS_OUTPUT, DBMS_LOB, DBMS_RANDOM 등)
        """.trimIndent())
    }

    data class CliConfig(
        val sourceDialect: DialectType?,
        val targetDialect: DialectType?,
        val inputFile: String?,
        val outputFile: String?,
        val query: String?,
        val verbose: Boolean,
        val showHelp: Boolean,
        val showVersion: Boolean
    )

    data class ConversionResult(
        val convertedSql: String,
        val warnings: List<ConversionWarning>,
        val appliedRules: List<String>
    )
}
