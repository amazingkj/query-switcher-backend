package com.sqlswitcher.converter

import com.sqlswitcher.converter.util.SqlRegexPatterns
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
        val conversionResult = sourceDialect.convertQuery(statement, targetDialectType, analysisResult)

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
        if (result.contains(SqlRegexPatterns.PARTITION_BY)) {
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
        if (SqlRegexPatterns.LOCAL_INDEX.containsMatchIn(result)) {
            result = SqlRegexPatterns.LOCAL_INDEX.replace(result, "")
            appliedRules.add("LOCAL 인덱스 키워드 제거")
            warnings.add(createWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "Oracle LOCAL 인덱스 키워드가 제거되었습니다.",
                severity = WarningSeverity.INFO
            ))
        }

        if (SqlRegexPatterns.GLOBAL_INDEX.containsMatchIn(result)) {
            result = SqlRegexPatterns.GLOBAL_INDEX.replace(result, " ")
            appliedRules.add("GLOBAL 인덱스 키워드 제거")
        }

        // 3. SECUREFILE/BASICFILE LOB 옵션 제거
        if (SqlRegexPatterns.LOB_STORAGE.containsMatchIn(result)) {
            result = SqlRegexPatterns.LOB_STORAGE.replace(result, " ")
            appliedRules.add("LOB 저장소 옵션(SECUREFILE/BASICFILE) 제거")
        }

        // 4. TABLESPACE 절 처리
        if (SqlRegexPatterns.TABLESPACE.containsMatchIn(result) && targetDialectType == DialectType.MYSQL) {
            result = SqlRegexPatterns.TABLESPACE.replace(result, "")
            appliedRules.add("TABLESPACE 절 제거 (MySQL)")
        }

        // 5. STORAGE 절 제거 (여러 줄에 걸친 경우 포함)
        if (SqlRegexPatterns.STORAGE_CLAUSE.containsMatchIn(result)) {
            result = SqlRegexPatterns.STORAGE_CLAUSE.replace(result, "")
            appliedRules.add("STORAGE 절 제거")
        }

        // 6. PCTFREE, PCTUSED, INITRANS 등 Oracle 물리적 옵션이 포함된 전체 라인 제거
        if (SqlRegexPatterns.PHYSICAL_OPTIONS_LINE.containsMatchIn(result)) {
            result = SqlRegexPatterns.PHYSICAL_OPTIONS_LINE.replace(result, "")
            appliedRules.add("Oracle 물리적 저장 옵션 제거")
        }

        // 7. ENABLE/DISABLE 제약조건 옵션
        if (SqlRegexPatterns.CONSTRAINT_STATE.containsMatchIn(result) && targetDialectType != DialectType.ORACLE) {
            result = SqlRegexPatterns.CONSTRAINT_STATE.replace(result, "")
            appliedRules.add("제약조건 상태 옵션 제거")
        }

        // 8. COMPRESS/NOCOMPRESS 옵션이 포함된 전체 라인 제거
        if (SqlRegexPatterns.COMPRESS_LINE.containsMatchIn(result)) {
            result = SqlRegexPatterns.COMPRESS_LINE.replace(result, "")
            appliedRules.add("압축 옵션 제거")
        }

        // 9. COMMENT ON 구문 처리 (Oracle → MySQL)
        if (targetDialectType == DialectType.MYSQL) {
            // MySQL은 COMMENT ON 구문을 지원하지 않음 - 제거하고 경고 추가
            val commentPattern = Regex(
                """COMMENT\s+ON\s+(COLUMN|TABLE)\s+[^\s]+\s+IS\s+'[^']*'\s*;?""",
                RegexOption.IGNORE_CASE
            )
            if (commentPattern.containsMatchIn(result)) {
                result = commentPattern.replace(result, "")
                appliedRules.add("COMMENT ON 구문 제거 (MySQL 미지원)")
                warnings.add(createWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "Oracle COMMENT ON 구문이 제거되었습니다. MySQL에서는 컬럼 정의 시 COMMENT 절을 사용하세요.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "ALTER TABLE ... MODIFY COLUMN ... COMMENT '...' 형식을 사용하세요."
                ))
            }
        }

        // 10. 스키마.테이블명에서 스키마 제거 (선택적)
        // "SCHEMA_NAME"."TABLE_NAME" → "TABLE_NAME"
        val schemaTablePattern = Regex("""["']?\w+["']?\.["']?(\w+)["']?""")
        if (schemaTablePattern.containsMatchIn(result)) {
            result = schemaTablePattern.replace(result) { match ->
                "\"${match.groupValues[1]}\""
            }
            appliedRules.add("스키마 접두사 제거")
        }

        // 11. SEGMENT CREATION 옵션 제거
        val segmentCreationPattern = Regex(
            """\s*SEGMENT\s+CREATION\s+(IMMEDIATE|DEFERRED)\s*""",
            RegexOption.IGNORE_CASE
        )
        if (segmentCreationPattern.containsMatchIn(result)) {
            result = segmentCreationPattern.replace(result, " ")
            appliedRules.add("SEGMENT CREATION 옵션 제거")
        }

        // 12. LOGGING/NOLOGGING 옵션 제거
        val loggingPattern = Regex("""\s*(NO)?LOGGING\b""", RegexOption.IGNORE_CASE)
        if (loggingPattern.containsMatchIn(result)) {
            result = loggingPattern.replace(result, "")
            appliedRules.add("LOGGING/NOLOGGING 옵션 제거")
        }

        // 13. PARALLEL 옵션 제거
        val parallelPattern = Regex(
            """\s*(NO)?PARALLEL(\s+\d+)?\s*""",
            RegexOption.IGNORE_CASE
        )
        if (parallelPattern.containsMatchIn(result)) {
            result = parallelPattern.replace(result, " ")
            appliedRules.add("PARALLEL 옵션 제거")
        }

        // 14. CACHE/NOCACHE 옵션 제거
        val cachePattern = Regex("""\s*(NO)?CACHE\b""", RegexOption.IGNORE_CASE)
        if (cachePattern.containsMatchIn(result)) {
            result = cachePattern.replace(result, "")
            appliedRules.add("CACHE/NOCACHE 옵션 제거")
        }

        // 15. RESULT_CACHE 힌트 제거
        val resultCachePattern = Regex(
            """/\*\+?\s*RESULT_CACHE[^*]*\*/""",
            RegexOption.IGNORE_CASE
        )
        if (resultCachePattern.containsMatchIn(result)) {
            result = resultCachePattern.replace(result, "")
            appliedRules.add("RESULT_CACHE 힌트 제거")
        }

        // 16. ROWDEPENDENCIES/NOROWDEPENDENCIES 제거
        val rowDepsPattern = Regex("""\s*(NO)?ROWDEPENDENCIES\b""", RegexOption.IGNORE_CASE)
        if (rowDepsPattern.containsMatchIn(result)) {
            result = rowDepsPattern.replace(result, "")
            appliedRules.add("ROWDEPENDENCIES 옵션 제거")
        }

        // 17. MONITORING/NOMONITORING 제거
        val monitoringPattern = Regex("""\s*(NO)?MONITORING\b""", RegexOption.IGNORE_CASE)
        if (monitoringPattern.containsMatchIn(result)) {
            result = monitoringPattern.replace(result, "")
            appliedRules.add("MONITORING 옵션 제거")
        }

        // 18. DEFAULT 절의 Oracle 함수 변환
        if (targetDialectType == DialectType.MYSQL) {
            result = result.replace(Regex("""DEFAULT\s+SYSDATE\b""", RegexOption.IGNORE_CASE), "DEFAULT CURRENT_TIMESTAMP")
            result = result.replace(Regex("""DEFAULT\s+SYSTIMESTAMP\b""", RegexOption.IGNORE_CASE), "DEFAULT CURRENT_TIMESTAMP")
        } else if (targetDialectType == DialectType.POSTGRESQL) {
            result = result.replace(Regex("""DEFAULT\s+SYSDATE\b""", RegexOption.IGNORE_CASE), "DEFAULT CURRENT_TIMESTAMP")
            result = result.replace(Regex("""DEFAULT\s+SYSTIMESTAMP\b""", RegexOption.IGNORE_CASE), "DEFAULT CURRENT_TIMESTAMP")
        }

        // 19. FLASHBACK 관련 구문 제거
        val flashbackPattern = Regex(
            """\s*FLASHBACK\s+ARCHIVE[^;]*""",
            RegexOption.IGNORE_CASE
        )
        if (flashbackPattern.containsMatchIn(result)) {
            result = flashbackPattern.replace(result, "")
            appliedRules.add("FLASHBACK ARCHIVE 옵션 제거")
        }

        // 20. ROW MOVEMENT 옵션 제거
        val rowMovementPattern = Regex(
            """\s*(ENABLE|DISABLE)\s+ROW\s+MOVEMENT\s*""",
            RegexOption.IGNORE_CASE
        )
        if (rowMovementPattern.containsMatchIn(result)) {
            result = rowMovementPattern.replace(result, " ")
            appliedRules.add("ROW MOVEMENT 옵션 제거")
        }

        // 21. 연속된 빈 줄 정리 (2개 이상의 연속 빈 줄을 1개로)
        result = SqlRegexPatterns.MULTIPLE_BLANK_LINES.replace(result, "\n\n")
        // 추가로 한번 더 정리
        result = SqlRegexPatterns.MULTIPLE_BLANK_LINES.replace(result, "\n\n")

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

        when (sourceDialectType) {
            DialectType.ORACLE -> {
                when (targetDialectType) {
                    DialectType.MYSQL -> {
                        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "NOW()")
                        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "IFNULL(")
                        result = result.replace(Regex("\\bSUBSTR\\s*\\(", RegexOption.IGNORE_CASE), "SUBSTRING(")
                        result = result.replace(Regex("\\bNVL2\\s*\\(", RegexOption.IGNORE_CASE), "IF(")
                        result = result.replace(Regex("\\bDECODE\\s*\\(", RegexOption.IGNORE_CASE), "CASE ")
                    }
                    DialectType.POSTGRESQL -> {
                        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
                    }
                    else -> {}
                }
            }
            DialectType.MYSQL -> {
                when (targetDialectType) {
                    DialectType.POSTGRESQL -> {
                        // 날짜/시간 함수
                        result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                        result = result.replace(Regex("\\bCURDATE\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "CURRENT_DATE")
                        result = result.replace(Regex("\\bCURTIME\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "CURRENT_TIME")
                        result = result.replace(Regex("\\bUNIX_TIMESTAMP\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::INTEGER")
                        result = result.replace(Regex("\\bFROM_UNIXTIME\\s*\\(", RegexOption.IGNORE_CASE), "TO_TIMESTAMP(")
                        result = result.replace(Regex("\\bDATE_FORMAT\\s*\\(", RegexOption.IGNORE_CASE), "TO_CHAR(")
                        result = result.replace(Regex("\\bSTR_TO_DATE\\s*\\(", RegexOption.IGNORE_CASE), "TO_DATE(")

                        // 문자열 함수
                        result = result.replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
                        result = result.replace(Regex("\\bIF\\s*\\(", RegexOption.IGNORE_CASE), "CASE WHEN ")
                        result = result.replace(Regex("\\bCONCAT_WS\\s*\\(", RegexOption.IGNORE_CASE), "CONCAT_WS(")
                        result = result.replace(Regex("\\bGROUP_CONCAT\\s*\\(", RegexOption.IGNORE_CASE), "STRING_AGG(")
                        result = result.replace(Regex("\\bLOCATE\\s*\\(", RegexOption.IGNORE_CASE), "POSITION(")
                        result = result.replace(Regex("\\bINSTR\\s*\\(", RegexOption.IGNORE_CASE), "POSITION(")

                        // 수학 함수
                        result = result.replace(Regex("\\bRAND\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "RANDOM()")
                        result = result.replace(Regex("\\bTRUNCATE\\s*\\(", RegexOption.IGNORE_CASE), "TRUNC(")

                        // 기타
                        result = result.replace(Regex("\\bLAST_INSERT_ID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "LASTVAL()")

                        appliedRules.add("MySQL → PostgreSQL 함수 변환")
                    }
                    DialectType.ORACLE -> {
                        result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSDATE")
                        result = result.replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
                        result = result.replace(Regex("\\bCOALESCE\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
                        result = result.replace(Regex("\\bSUBSTRING\\s*\\(", RegexOption.IGNORE_CASE), "SUBSTR(")
                        appliedRules.add("MySQL → Oracle 함수 변환")
                    }
                    else -> {}
                }
            }
            DialectType.POSTGRESQL -> {
                when (targetDialectType) {
                    DialectType.MYSQL -> {
                        // 날짜/시간 함수
                        result = result.replace(Regex("\\bCURRENT_TIMESTAMP\\b", RegexOption.IGNORE_CASE), "NOW()")
                        result = result.replace(Regex("\\bCURRENT_DATE\\b", RegexOption.IGNORE_CASE), "CURDATE()")
                        result = result.replace(Regex("\\bCURRENT_TIME\\b", RegexOption.IGNORE_CASE), "CURTIME()")
                        result = result.replace(Regex("\\bTO_CHAR\\s*\\(", RegexOption.IGNORE_CASE), "DATE_FORMAT(")
                        result = result.replace(Regex("\\bTO_DATE\\s*\\(", RegexOption.IGNORE_CASE), "STR_TO_DATE(")
                        result = result.replace(Regex("\\bTO_TIMESTAMP\\s*\\(", RegexOption.IGNORE_CASE), "FROM_UNIXTIME(")

                        // 문자열 함수
                        result = result.replace(Regex("\\bCOALESCE\\s*\\(", RegexOption.IGNORE_CASE), "IFNULL(")
                        result = result.replace(Regex("\\bSTRING_AGG\\s*\\(", RegexOption.IGNORE_CASE), "GROUP_CONCAT(")

                        // 수학 함수
                        result = result.replace(Regex("\\bRANDOM\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "RAND()")
                        result = result.replace(Regex("\\bTRUNC\\s*\\(", RegexOption.IGNORE_CASE), "TRUNCATE(")

                        // 타입 캐스팅
                        result = result.replace(Regex("::INTEGER\\b", RegexOption.IGNORE_CASE), "")
                        result = result.replace(Regex("::TEXT\\b", RegexOption.IGNORE_CASE), "")
                        result = result.replace(Regex("::VARCHAR\\b", RegexOption.IGNORE_CASE), "")
                        result = result.replace(Regex("::NUMERIC\\b", RegexOption.IGNORE_CASE), "")
                        result = result.replace(Regex("::TIMESTAMP\\b", RegexOption.IGNORE_CASE), "")

                        appliedRules.add("PostgreSQL → MySQL 함수 변환")
                    }
                    DialectType.ORACLE -> {
                        result = result.replace(Regex("\\bCURRENT_TIMESTAMP\\b", RegexOption.IGNORE_CASE), "SYSDATE")
                        result = result.replace(Regex("\\bCOALESCE\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
                        result = result.replace(Regex("\\bRANDOM\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "DBMS_RANDOM.VALUE")
                        appliedRules.add("PostgreSQL → Oracle 함수 변환")
                    }
                    else -> {}
                }
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

        when (sourceDialectType) {
            DialectType.ORACLE -> {
                // Oracle BYTE/CHAR 키워드 제거
                if (targetDialectType != DialectType.ORACLE) {
                    result = result.replace(Regex("\\(\\s*(\\d+)\\s+BYTE\\s*\\)", RegexOption.IGNORE_CASE), "($1)")
                    result = result.replace(Regex("\\(\\s*(\\d+)\\s+CHAR\\s*\\)", RegexOption.IGNORE_CASE), "($1)")
                    appliedRules.add("Oracle BYTE/CHAR 키워드 제거")
                }

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
                        result = result.replace(Regex("\\bFLOAT\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)) { m ->
                            val precision = m.groupValues[1].toInt()
                            if (precision <= 24) "FLOAT" else "DOUBLE"
                        }
                        result = result.replace(Regex("\\bBINARY_FLOAT\\b", RegexOption.IGNORE_CASE), "FLOAT")
                        result = result.replace(Regex("\\bBINARY_DOUBLE\\b", RegexOption.IGNORE_CASE), "DOUBLE")
                        appliedRules.add("Oracle → MySQL 데이터타입 변환")
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
                        result = result.replace(Regex("\\bFLOAT\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)) { m ->
                            val precision = m.groupValues[1].toInt()
                            if (precision <= 24) "REAL" else "DOUBLE PRECISION"
                        }
                        result = result.replace(Regex("\\bBINARY_FLOAT\\b", RegexOption.IGNORE_CASE), "REAL")
                        result = result.replace(Regex("\\bBINARY_DOUBLE\\b", RegexOption.IGNORE_CASE), "DOUBLE PRECISION")
                        appliedRules.add("Oracle → PostgreSQL 데이터타입 변환")
                    }
                    else -> {}
                }
            }
            DialectType.MYSQL -> {
                when (targetDialectType) {
                    DialectType.POSTGRESQL -> {
                        // 정수형
                        result = result.replace(Regex("\\bTINYINT\\s*\\(\\s*1\\s*\\)", RegexOption.IGNORE_CASE), "BOOLEAN")
                        result = result.replace(Regex("\\bTINYINT\\b", RegexOption.IGNORE_CASE), "SMALLINT")
                        result = result.replace(Regex("\\bMEDIUMINT\\b", RegexOption.IGNORE_CASE), "INTEGER")
                        result = result.replace(Regex("\\bINT\\s+UNSIGNED\\b", RegexOption.IGNORE_CASE), "BIGINT")
                        result = result.replace(Regex("\\bBIGINT\\s+UNSIGNED\\b", RegexOption.IGNORE_CASE), "NUMERIC(20)")

                        // 문자열
                        result = result.replace(Regex("\\bLONGTEXT\\b", RegexOption.IGNORE_CASE), "TEXT")
                        result = result.replace(Regex("\\bMEDIUMTEXT\\b", RegexOption.IGNORE_CASE), "TEXT")
                        result = result.replace(Regex("\\bTINYTEXT\\b", RegexOption.IGNORE_CASE), "TEXT")

                        // 바이너리
                        result = result.replace(Regex("\\bLONGBLOB\\b", RegexOption.IGNORE_CASE), "BYTEA")
                        result = result.replace(Regex("\\bMEDIUMBLOB\\b", RegexOption.IGNORE_CASE), "BYTEA")
                        result = result.replace(Regex("\\bTINYBLOB\\b", RegexOption.IGNORE_CASE), "BYTEA")
                        result = result.replace(Regex("\\bBLOB\\b", RegexOption.IGNORE_CASE), "BYTEA")
                        result = result.replace(Regex("\\bVARBINARY\\s*\\(", RegexOption.IGNORE_CASE), "BYTEA")
                        result = result.replace(Regex("\\bBINARY\\s*\\(", RegexOption.IGNORE_CASE), "BYTEA")

                        // 날짜/시간
                        result = result.replace(Regex("\\bDATETIME\\b", RegexOption.IGNORE_CASE), "TIMESTAMP")
                        result = result.replace(Regex("\\bYEAR\\b", RegexOption.IGNORE_CASE), "SMALLINT")

                        // 부동소수점
                        result = result.replace(Regex("\\bDOUBLE\\b", RegexOption.IGNORE_CASE), "DOUBLE PRECISION")
                        result = result.replace(Regex("\\bFLOAT\\b", RegexOption.IGNORE_CASE), "REAL")

                        // ENUM/SET
                        result = result.replace(Regex("\\bENUM\\s*\\([^)]+\\)", RegexOption.IGNORE_CASE), "VARCHAR(255)")
                        result = result.replace(Regex("\\bSET\\s*\\([^)]+\\)", RegexOption.IGNORE_CASE), "VARCHAR(255)")

                        // AUTO_INCREMENT → SERIAL
                        result = result.replace(Regex("\\bINT\\s+AUTO_INCREMENT\\b", RegexOption.IGNORE_CASE), "SERIAL")
                        result = result.replace(Regex("\\bBIGINT\\s+AUTO_INCREMENT\\b", RegexOption.IGNORE_CASE), "BIGSERIAL")
                        result = result.replace(Regex("\\bAUTO_INCREMENT\\b", RegexOption.IGNORE_CASE), "")

                        // JSON
                        result = result.replace(Regex("\\bJSON\\b", RegexOption.IGNORE_CASE), "JSONB")

                        appliedRules.add("MySQL → PostgreSQL 데이터타입 변환")
                    }
                    DialectType.ORACLE -> {
                        result = result.replace(Regex("\\bVARCHAR\\s*\\(", RegexOption.IGNORE_CASE), "VARCHAR2(")
                        result = result.replace(Regex("\\bTINYINT\\b", RegexOption.IGNORE_CASE), "NUMBER(3)")
                        result = result.replace(Regex("\\bSMALLINT\\b", RegexOption.IGNORE_CASE), "NUMBER(5)")
                        result = result.replace(Regex("\\bMEDIUMINT\\b", RegexOption.IGNORE_CASE), "NUMBER(7)")
                        result = result.replace(Regex("\\bINT\\b", RegexOption.IGNORE_CASE), "NUMBER(10)")
                        result = result.replace(Regex("\\bBIGINT\\b", RegexOption.IGNORE_CASE), "NUMBER(19)")
                        result = result.replace(Regex("\\bDATETIME\\b", RegexOption.IGNORE_CASE), "DATE")
                        result = result.replace(Regex("\\bLONGTEXT\\b", RegexOption.IGNORE_CASE), "CLOB")
                        result = result.replace(Regex("\\bLONGBLOB\\b", RegexOption.IGNORE_CASE), "BLOB")
                        result = result.replace(Regex("\\bTEXT\\b", RegexOption.IGNORE_CASE), "CLOB")
                        appliedRules.add("MySQL → Oracle 데이터타입 변환")
                    }
                    else -> {}
                }
            }
            DialectType.POSTGRESQL -> {
                when (targetDialectType) {
                    DialectType.MYSQL -> {
                        // 정수형
                        result = result.replace(Regex("\\bSERIAL\\b", RegexOption.IGNORE_CASE), "INT AUTO_INCREMENT")
                        result = result.replace(Regex("\\bBIGSERIAL\\b", RegexOption.IGNORE_CASE), "BIGINT AUTO_INCREMENT")
                        result = result.replace(Regex("\\bSMALLSERIAL\\b", RegexOption.IGNORE_CASE), "SMALLINT AUTO_INCREMENT")

                        // 문자열/바이너리
                        result = result.replace(Regex("\\bTEXT\\b", RegexOption.IGNORE_CASE), "LONGTEXT")
                        result = result.replace(Regex("\\bBYTEA\\b", RegexOption.IGNORE_CASE), "LONGBLOB")

                        // 날짜/시간
                        result = result.replace(Regex("\\bTIMESTAMP\\s+WITHOUT\\s+TIME\\s+ZONE\\b", RegexOption.IGNORE_CASE), "DATETIME")
                        result = result.replace(Regex("\\bTIMESTAMP\\s+WITH\\s+TIME\\s+ZONE\\b", RegexOption.IGNORE_CASE), "DATETIME")
                        result = result.replace(Regex("\\bTIMESTAMP\\b", RegexOption.IGNORE_CASE), "DATETIME")
                        result = result.replace(Regex("\\bINTERVAL\\b", RegexOption.IGNORE_CASE), "VARCHAR(255)")

                        // 부동소수점
                        result = result.replace(Regex("\\bDOUBLE PRECISION\\b", RegexOption.IGNORE_CASE), "DOUBLE")
                        result = result.replace(Regex("\\bREAL\\b", RegexOption.IGNORE_CASE), "FLOAT")

                        // JSON
                        result = result.replace(Regex("\\bJSONB\\b", RegexOption.IGNORE_CASE), "JSON")

                        // UUID
                        result = result.replace(Regex("\\bUUID\\b", RegexOption.IGNORE_CASE), "CHAR(36)")

                        // 배열 타입 제거
                        result = result.replace(Regex("\\[\\]", RegexOption.IGNORE_CASE), "")

                        // BOOLEAN
                        result = result.replace(Regex("\\bBOOLEAN\\b", RegexOption.IGNORE_CASE), "TINYINT(1)")

                        appliedRules.add("PostgreSQL → MySQL 데이터타입 변환")
                    }
                    DialectType.ORACLE -> {
                        result = result.replace(Regex("\\bVARCHAR\\s*\\(", RegexOption.IGNORE_CASE), "VARCHAR2(")
                        result = result.replace(Regex("\\bSERIAL\\b", RegexOption.IGNORE_CASE), "NUMBER GENERATED ALWAYS AS IDENTITY")
                        result = result.replace(Regex("\\bBIGSERIAL\\b", RegexOption.IGNORE_CASE), "NUMBER GENERATED ALWAYS AS IDENTITY")
                        result = result.replace(Regex("\\bINTEGER\\b", RegexOption.IGNORE_CASE), "NUMBER(10)")
                        result = result.replace(Regex("\\bSMALLINT\\b", RegexOption.IGNORE_CASE), "NUMBER(5)")
                        result = result.replace(Regex("\\bBIGINT\\b", RegexOption.IGNORE_CASE), "NUMBER(19)")
                        result = result.replace(Regex("\\bTEXT\\b", RegexOption.IGNORE_CASE), "CLOB")
                        result = result.replace(Regex("\\bBYTEA\\b", RegexOption.IGNORE_CASE), "BLOB")
                        result = result.replace(Regex("\\bTIMESTAMP\\b", RegexOption.IGNORE_CASE), "DATE")
                        result = result.replace(Regex("\\bBOOLEAN\\b", RegexOption.IGNORE_CASE), "NUMBER(1)")
                        result = result.replace(Regex("\\bDOUBLE PRECISION\\b", RegexOption.IGNORE_CASE), "BINARY_DOUBLE")
                        result = result.replace(Regex("\\bREAL\\b", RegexOption.IGNORE_CASE), "BINARY_FLOAT")
                        appliedRules.add("PostgreSQL → Oracle 데이터타입 변환")
                    }
                    else -> {}
                }
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