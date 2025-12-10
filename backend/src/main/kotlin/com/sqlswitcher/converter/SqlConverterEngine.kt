package com.sqlswitcher.converter

import com.sqlswitcher.parser.SqlParserService
import com.sqlswitcher.service.SqlMetricsService
import com.sqlswitcher.parser.model.AstAnalysisResult
import com.sqlswitcher.model.ConversionOptions as ModelConversionOptions
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.create.table.CreateTable
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * SQL 변환을 위한 메인 엔진.
 * Strategy 패턴을 사용하여 각 데이터베이스 방언별 변환 로직을 관리합니다.
 */
@Service
class SqlConverterEngine(
    private val sqlParserService: SqlParserService,
    private val sqlMetricsService: SqlMetricsService,
    private val dialects: List<DatabaseDialect>, // 모든 DatabaseDialect 구현체를 주입받음
    private val functionMapper: FunctionMapper,
    private val dataTypeConverter: DataTypeConverter
) {
    private val dialectMap: ConcurrentHashMap<DialectType, DatabaseDialect> = ConcurrentHashMap()

    init {
        dialects.forEach { dialect ->
            dialectMap[dialect.getDialectType()] = dialect
        }
    }

    /**
     * SQL 쿼리를 다른 데이터베이스 방언으로 변환합니다.
     * @param sql 원본 SQL 쿼리 문자열
     * @param sourceDialectType 원본 데이터베이스 방언 타입
     * @param targetDialectType 타겟 데이터베이스 방언 타입
     * @param options 변환 옵션
     * @return 변환 결과 (변환된 SQL, 경고, 적용된 규칙 등)
     */
    fun convert(
        sql: String,
        sourceDialectType: DialectType,
        targetDialectType: DialectType,
        options: ConversionOptions = ConversionOptions()
    ): ConversionResult {
        return convertWithModelOptions(sql, sourceDialectType, targetDialectType, options, null)
    }

    /**
     * SQL 쿼리를 다른 데이터베이스 방언으로 변환합니다 (모델 옵션 포함).
     * @param sql 원본 SQL 쿼리 문자열
     * @param sourceDialectType 원본 데이터베이스 방언 타입
     * @param targetDialectType 타겟 데이터베이스 방언 타입
     * @param options 변환 옵션
     * @param modelOptions 모델 변환 옵션 (Oracle DDL 옵션 등)
     * @return 변환 결과 (변환된 SQL, 경고, 적용된 규칙 등)
     */
    fun convertWithModelOptions(
        sql: String,
        sourceDialectType: DialectType,
        targetDialectType: DialectType,
        options: ConversionOptions = ConversionOptions(),
        modelOptions: ModelConversionOptions? = null
    ): ConversionResult {
        val startTime = System.currentTimeMillis()
        val warnings = mutableListOf<ConversionWarning>()
        val appliedRules = mutableListOf<String>()

        return try {
            // 여러 SQL 문장 분리 처리
            val sqlStatements = splitSqlStatements(sql)

            // 단일 문장인 경우 기존 로직
            if (sqlStatements.size <= 1) {
                return convertSingleStatement(
                    sql.trim(), sourceDialectType, targetDialectType, modelOptions,
                    warnings, appliedRules, startTime
                )
            }

            // 여러 문장인 경우 각각 변환
            val convertedStatements = mutableListOf<String>()
            for (singleSql in sqlStatements) {
                val trimmedSql = singleSql.trim()
                if (trimmedSql.isEmpty()) continue

                val result = convertSingleStatement(
                    trimmedSql, sourceDialectType, targetDialectType, modelOptions,
                    warnings, appliedRules, startTime
                )
                convertedStatements.add(result.convertedSql)
            }

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            sqlMetricsService.recordConversionDuration(duration, sourceDialectType.name, targetDialectType.name)
            sqlMetricsService.recordConversionSuccess(sourceDialectType.name, targetDialectType.name)

            appliedRules.add("${convertedStatements.size}개의 SQL 문장 일괄 변환")

            ConversionResult(
                convertedSql = convertedStatements.joinToString(";\n") + if (convertedStatements.isNotEmpty()) ";" else "",
                warnings = warnings.distinctBy { it.message },
                appliedRules = appliedRules.distinct()
            )
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            warnings.add(createWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "변환 중 오류가 발생했습니다: ${e.message}",
                severity = WarningSeverity.ERROR,
                suggestion = "SQL 구문을 확인하고 다시 시도해보세요."
            ))

            sqlMetricsService.recordConversionError(sourceDialectType.name, targetDialectType.name, "ConversionError")

            ConversionResult(
                convertedSql = sql,
                warnings = warnings,
                appliedRules = appliedRules
            )
        }
    }

    /**
     * 단일 SQL 문장 변환
     */
    private fun convertSingleStatement(
        sql: String,
        sourceDialectType: DialectType,
        targetDialectType: DialectType,
        modelOptions: ModelConversionOptions?,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        startTime: Long
    ): ConversionResult {
        // 1. SQL 파싱 및 AST 분석
        val parseResult = sqlParserService.parseSql(sql)
        if (parseResult.parseException != null) {
            // 파싱 실패 시 문자열 기반 폴백 변환 시도
            warnings.add(createWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "AST 파싱 불가 (Oracle 특화 문법) - 문자열 기반 변환을 시도합니다.",
                severity = WarningSeverity.INFO,
                suggestion = "일부 Oracle 특화 문법(RANGE 파티션, LOCAL 인덱스, SECUREFILE 등)은 문자열 변환으로 처리됩니다."
            ))

            return performFallbackConversion(
                sql, sourceDialectType, targetDialectType, warnings, appliedRules, startTime
            )
        }

        val statement = parseResult.statement!!
        val analysisResult = parseResult.astAnalysis!!

        // 2. 소스 및 타겟 방언 구현체 가져오기
        val sourceDialect = dialectMap[sourceDialectType]
            ?: throw IllegalArgumentException("Unsupported source dialect: $sourceDialectType")
        val targetDialect = dialectMap[targetDialectType]
            ?: throw IllegalArgumentException("Unsupported target dialect: $targetDialectType")

        // 3. 변환 가능 여부 확인 (선택 사항)
        if (!sourceDialect.canConvert(statement, targetDialectType)) {
            warnings.add(createWarning(
                type = WarningType.UNSUPPORTED_FUNCTION,
                message = "${sourceDialectType.name}에서 ${targetDialectType.name}으로의 변환이 완전히 지원되지 않을 수 있습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "수동 검토가 필요합니다."
            ))
        }

        // 4. 실제 변환 수행
        // CREATE TABLE 문인 경우 옵션을 포함한 변환 수행
        val conversionResult = if (statement is CreateTable) {
            when (sourceDialect) {
                is MySqlDialect -> sourceDialect.performConversionWithOptions(statement, targetDialectType, analysisResult, modelOptions)
                is PostgreSqlDialect -> sourceDialect.performConversionWithOptions(statement, targetDialectType, analysisResult, modelOptions)
                is TiberoDialect -> sourceDialect.performConversionWithOptions(statement, targetDialectType, analysisResult, modelOptions)
                is OracleDialect -> sourceDialect.convertQuery(statement, targetDialectType, analysisResult)
                else -> sourceDialect.convertQuery(statement, targetDialectType, analysisResult)
            }
        } else {
            sourceDialect.convertQuery(statement, targetDialectType, analysisResult)
        }

        warnings.addAll(conversionResult.warnings)
        appliedRules.addAll(conversionResult.appliedRules)

        return ConversionResult(
            convertedSql = conversionResult.convertedSql,
            warnings = warnings,
            appliedRules = appliedRules
        )
    }

    /**
     * SQL 문장을 세미콜론으로 분리 (문자열 리터럴 내부 세미콜론은 무시)
     */
    private fun splitSqlStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0

        while (i < sql.length) {
            val char = sql[i]

            when {
                // 이스케이프된 따옴표 처리
                char == '\\' && i + 1 < sql.length -> {
                    current.append(char)
                    current.append(sql[i + 1])
                    i += 2
                    continue
                }
                // 작은따옴표
                char == '\'' && !inDoubleQuote -> {
                    inSingleQuote = !inSingleQuote
                    current.append(char)
                }
                // 큰따옴표
                char == '"' && !inSingleQuote -> {
                    inDoubleQuote = !inDoubleQuote
                    current.append(char)
                }
                // 세미콜론 (문자열 밖에서만)
                char == ';' && !inSingleQuote && !inDoubleQuote -> {
                    val stmt = current.toString().trim()
                    if (stmt.isNotEmpty()) {
                        statements.add(stmt)
                    }
                    current.clear()
                }
                else -> {
                    current.append(char)
                }
            }
            i++
        }

        // 마지막 문장 처리
        val lastStmt = current.toString().trim()
        if (lastStmt.isNotEmpty()) {
            statements.add(lastStmt)
        }

        return statements
    }

    /**
     * 파싱 실패 시 문자열 기반 폴백 변환 수행
     */
    private fun performFallbackConversion(
        sql: String,
        sourceDialectType: DialectType,
        targetDialectType: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        startTime: Long
    ): ConversionResult {
        var convertedSql = sql

        // Oracle 특화 문법 전처리
        convertedSql = preprocessOracleSyntax(convertedSql, targetDialectType, warnings, appliedRules)

        // 함수/데이터타입 변환 (문자열 기반)
        convertedSql = convertFunctionsStringBased(convertedSql, sourceDialectType, targetDialectType, appliedRules)
        convertedSql = convertDataTypesStringBased(convertedSql, sourceDialectType, targetDialectType, appliedRules)

        val endTime = System.currentTimeMillis()
        sqlMetricsService.recordConversionDuration(endTime - startTime, sourceDialectType.name, targetDialectType.name)
        sqlMetricsService.recordConversionSuccess(sourceDialectType.name, targetDialectType.name)

        appliedRules.add("문자열 기반 폴백 변환 적용")

        return ConversionResult(
            convertedSql = convertedSql,
            warnings = warnings,
            appliedRules = appliedRules
        )
    }

    /**
     * Oracle 특화 문법 전처리 (JSQLParser가 파싱하지 못하는 문법 처리)
     */
    private fun preprocessOracleSyntax(
        sql: String,
        targetDialectType: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // 1. RANGE/LIST/HASH 파티션 처리
        if (result.contains(Regex("""PARTITION\s+BY\s+(RANGE|LIST|HASH)""", RegexOption.IGNORE_CASE))) {
            when (targetDialectType) {
                DialectType.MYSQL -> {
                    // MySQL도 파티션 지원하지만 문법이 약간 다름
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Oracle 파티션 구문이 감지되었습니다. MySQL 파티션 문법으로 수동 조정이 필요할 수 있습니다.",
                        severity = WarningSeverity.WARNING
                    ))
                }
                DialectType.POSTGRESQL -> {
                    // PostgreSQL 10+ 파티션 지원
                    warnings.add(createWarning(
                        type = WarningType.SYNTAX_DIFFERENCE,
                        message = "Oracle 파티션 구문이 감지되었습니다. PostgreSQL 파티션 문법으로 수동 조정이 필요할 수 있습니다.",
                        severity = WarningSeverity.WARNING
                    ))
                }
                else -> {}
            }
            appliedRules.add("파티션 구문 감지됨")
        }

        // 2. LOCAL/GLOBAL 인덱스 제거
        val localIndexPattern = Regex("""\s+LOCAL\s*(\([^)]*\))?""", RegexOption.IGNORE_CASE)
        if (localIndexPattern.containsMatchIn(result)) {
            result = localIndexPattern.replace(result, "")
            appliedRules.add("LOCAL 인덱스 키워드 제거")
            warnings.add(createWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "Oracle LOCAL 인덱스 키워드가 제거되었습니다.",
                severity = WarningSeverity.INFO
            ))
        }

        val globalIndexPattern = Regex("""\s+GLOBAL\s*""", RegexOption.IGNORE_CASE)
        if (globalIndexPattern.containsMatchIn(result)) {
            result = globalIndexPattern.replace(result, " ")
            appliedRules.add("GLOBAL 인덱스 키워드 제거")
        }

        // 3. SECUREFILE/BASICFILE LOB 옵션 제거
        val lobStoragePattern = Regex("""\s+(SECUREFILE|BASICFILE)\s+""", RegexOption.IGNORE_CASE)
        if (lobStoragePattern.containsMatchIn(result)) {
            result = lobStoragePattern.replace(result, " ")
            appliedRules.add("LOB 저장소 옵션(SECUREFILE/BASICFILE) 제거")
        }

        // 4. TABLESPACE 절 처리
        val tablespacePattern = Regex("""\s+TABLESPACE\s+\w+""", RegexOption.IGNORE_CASE)
        if (tablespacePattern.containsMatchIn(result) && targetDialectType == DialectType.MYSQL) {
            result = tablespacePattern.replace(result, "")
            appliedRules.add("TABLESPACE 절 제거 (MySQL)")
        }

        // 5. STORAGE 절 제거 (여러 줄에 걸친 경우 포함)
        val storagePattern = Regex("""\s*STORAGE\s*\([\s\S]*?\)""", RegexOption.IGNORE_CASE)
        if (storagePattern.containsMatchIn(result)) {
            result = storagePattern.replace(result, "")
            appliedRules.add("STORAGE 절 제거")
        }

        // 6. PCTFREE, PCTUSED, INITRANS 등 Oracle 물리적 옵션이 포함된 전체 라인 제거
        val physicalOptionsLinePattern = Regex("""^\s*(PCTFREE|PCTUSED|INITRANS|MAXTRANS|LOGGING|NOLOGGING)(\s+\d+)?\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        if (physicalOptionsLinePattern.containsMatchIn(result)) {
            result = physicalOptionsLinePattern.replace(result, "")
            appliedRules.add("Oracle 물리적 저장 옵션 제거")
        }

        // 7. ENABLE/DISABLE 제약조건 옵션
        val constraintStatePattern = Regex("""\s+(ENABLE|DISABLE)\s+(VALIDATE|NOVALIDATE)?""", RegexOption.IGNORE_CASE)
        if (constraintStatePattern.containsMatchIn(result) && targetDialectType != DialectType.ORACLE && targetDialectType != DialectType.TIBERO) {
            result = constraintStatePattern.replace(result, "")
            appliedRules.add("제약조건 상태 옵션 제거")
        }

        // 8. COMPRESS/NOCOMPRESS 옵션이 포함된 전체 라인 제거
        val compressLinePattern = Regex("""^\s*(COMPRESS|NOCOMPRESS)(\s+\d+)?\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        if (compressLinePattern.containsMatchIn(result)) {
            result = compressLinePattern.replace(result, "")
            appliedRules.add("압축 옵션 제거")
        }

        // 9. 연속된 빈 줄 정리 (2개 이상의 연속 빈 줄을 1개로)
        result = result.replace(Regex("""\n\s*\n\s*\n"""), "\n\n")
        // 추가로 한번 더 정리
        result = result.replace(Regex("""\n\s*\n\s*\n"""), "\n\n")

        return result.trim()
    }

    /**
     * 문자열 기반 함수 변환
     */
    private fun convertFunctionsStringBased(
        sql: String,
        sourceDialectType: DialectType,
        targetDialectType: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (sourceDialectType == DialectType.ORACLE || sourceDialectType == DialectType.TIBERO) {
            when (targetDialectType) {
                DialectType.MYSQL -> {
                    result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "NOW()")
                    result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "IFNULL(")
                    result = result.replace(Regex("\\bSUBSTR\\s*\\(", RegexOption.IGNORE_CASE), "SUBSTRING(")
                }
                DialectType.POSTGRESQL -> {
                    result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                    result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
                }
                else -> {}
            }
        }

        return result
    }

    /**
     * 문자열 기반 데이터타입 변환
     */
    private fun convertDataTypesStringBased(
        sql: String,
        sourceDialectType: DialectType,
        targetDialectType: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (sourceDialectType == DialectType.ORACLE || sourceDialectType == DialectType.TIBERO) {
            when (targetDialectType) {
                DialectType.MYSQL -> {
                    result = result.replace(Regex("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)) { m ->
                        val precision = m.groupValues[1].toInt()
                        when {
                            precision <= 3 -> "TINYINT"
                            precision <= 5 -> "SMALLINT"
                            precision <= 9 -> "INT"
                            else -> "BIGINT"
                        }
                    }
                    result = result.replace(Regex("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE), "DECIMAL($1,$2)")
                    result = result.replace(Regex("\\bVARCHAR2\\s*\\(", RegexOption.IGNORE_CASE), "VARCHAR(")
                    result = result.replace(Regex("\\bCLOB\\b", RegexOption.IGNORE_CASE), "LONGTEXT")
                    result = result.replace(Regex("\\bBLOB\\b", RegexOption.IGNORE_CASE), "LONGBLOB")
                    result = result.replace(Regex("\\bDATE\\b", RegexOption.IGNORE_CASE), "DATETIME")
                }
                DialectType.POSTGRESQL -> {
                    result = result.replace(Regex("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)) { m ->
                        val precision = m.groupValues[1].toInt()
                        when {
                            precision <= 4 -> "SMALLINT"
                            precision <= 9 -> "INTEGER"
                            else -> "BIGINT"
                        }
                    }
                    result = result.replace(Regex("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE), "NUMERIC($1,$2)")
                    result = result.replace(Regex("\\bVARCHAR2\\s*\\(", RegexOption.IGNORE_CASE), "VARCHAR(")
                    result = result.replace(Regex("\\bCLOB\\b", RegexOption.IGNORE_CASE), "TEXT")
                    result = result.replace(Regex("\\bBLOB\\b", RegexOption.IGNORE_CASE), "BYTEA")
                }
                else -> {}
            }
        }

        return result
    }

    private fun createWarning(
        type: WarningType,
        message: String,
        severity: WarningSeverity,
        suggestion: String? = null
    ): ConversionWarning {
        return ConversionWarning(type, message, severity, suggestion)
    }
}