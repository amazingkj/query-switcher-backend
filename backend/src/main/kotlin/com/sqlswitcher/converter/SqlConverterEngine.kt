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

        // 5. 후처리: Oracle DDL 옵션 제거 (파싱 성공 시에도 적용)
        var finalSql = conversionResult.convertedSql
        if (sourceDialectType == DialectType.ORACLE && targetDialectType != DialectType.ORACLE) {
            finalSql = removeOracleDdlOptions(finalSql, targetDialectType, appliedRules)
        }

        // 6. MySQL 타겟인 경우 큰따옴표를 백틱으로 변환
        if (targetDialectType == DialectType.MYSQL) {
            finalSql = convertQuotesToBackticks(finalSql, appliedRules)
        }

        return ConversionResult(
            convertedSql = finalSql,
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

        // 4. Oracle DDL 옵션 블록 제거 (닫는 괄호 뒤의 모든 Oracle 전용 옵션)
        // 먼저 전체 블록 패턴으로 시도
        if (SqlRegexPatterns.ORACLE_DDL_OPTIONS_BLOCK.containsMatchIn(result) && targetDialectType != DialectType.ORACLE) {
            result = SqlRegexPatterns.ORACLE_DDL_OPTIONS_BLOCK.replace(result, ")")
            appliedRules.add("Oracle DDL 옵션 블록 제거")
        }

        // 5. TABLESPACE 절 처리 (개별 처리)
        if (SqlRegexPatterns.TABLESPACE.containsMatchIn(result) && targetDialectType != DialectType.ORACLE) {
            result = SqlRegexPatterns.TABLESPACE.replace(result, "")
            appliedRules.add("TABLESPACE 절 제거")
        }

        // 6. STORAGE 절 제거 (여러 줄에 걸친 경우 포함)
        if (SqlRegexPatterns.STORAGE_CLAUSE.containsMatchIn(result)) {
            result = SqlRegexPatterns.STORAGE_CLAUSE.replace(result, "")
            appliedRules.add("STORAGE 절 제거")
        }

        // 7. 개별 물리적 옵션 제거 (인라인으로 존재하는 경우)
        if (targetDialectType != DialectType.ORACLE) {
            if (SqlRegexPatterns.PCTFREE.containsMatchIn(result)) {
                result = SqlRegexPatterns.PCTFREE.replace(result, "")
                appliedRules.add("PCTFREE 옵션 제거")
            }
            if (SqlRegexPatterns.PCTUSED.containsMatchIn(result)) {
                result = SqlRegexPatterns.PCTUSED.replace(result, "")
                appliedRules.add("PCTUSED 옵션 제거")
            }
            if (SqlRegexPatterns.INITRANS.containsMatchIn(result)) {
                result = SqlRegexPatterns.INITRANS.replace(result, "")
                appliedRules.add("INITRANS 옵션 제거")
            }
            if (SqlRegexPatterns.MAXTRANS.containsMatchIn(result)) {
                result = SqlRegexPatterns.MAXTRANS.replace(result, "")
                appliedRules.add("MAXTRANS 옵션 제거")
            }
        }

        // 8. PCTFREE, PCTUSED 등 Oracle 물리적 옵션이 포함된 전체 라인 제거 (레거시)
        if (SqlRegexPatterns.PHYSICAL_OPTIONS_LINE.containsMatchIn(result)) {
            result = SqlRegexPatterns.PHYSICAL_OPTIONS_LINE.replace(result, "")
        }

        // 9. ENABLE/DISABLE 제약조건 옵션
        if (SqlRegexPatterns.CONSTRAINT_STATE.containsMatchIn(result) && targetDialectType != DialectType.ORACLE) {
            result = SqlRegexPatterns.CONSTRAINT_STATE.replace(result, "")
            appliedRules.add("제약조건 상태 옵션 제거")
        }

        // 10. COMPRESS/NOCOMPRESS 옵션 제거 (개별 및 라인)
        if (targetDialectType != DialectType.ORACLE) {
            if (SqlRegexPatterns.COMPRESS.containsMatchIn(result)) {
                result = SqlRegexPatterns.COMPRESS.replace(result, "")
                appliedRules.add("압축 옵션 제거")
            }
        }
        if (SqlRegexPatterns.COMPRESS_LINE.containsMatchIn(result)) {
            result = SqlRegexPatterns.COMPRESS_LINE.replace(result, "")
        }

        // 9. COMMENT ON 구문 처리 (Oracle → MySQL/PostgreSQL)
        if (targetDialectType == DialectType.MYSQL) {
            // MySQL은 COMMENT ON 구문을 지원하지 않음 - 제거하고 경고 추가
            // "SCHEMA"."TABLE"."COLUMN" 또는 TABLE.COLUMN 형식 모두 처리
            // 여러 줄에 걸친 COMMENT도 처리
            val commentPattern = Regex(
                """COMMENT\s+ON\s+(COLUMN|TABLE)\s+("[^"]+"\s*\.\s*)*"?[^"'\s]+("?\s*\.\s*"?[^"'\s]+"?)?\s+IS\s+'[^']*'\s*;?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
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

            // 더 간단한 패턴으로 한번 더 정리 (남은 COMMENT ON 문 처리)
            val simpleCommentPattern = Regex(
                """^\s*COMMENT\s+ON\s+[^;]+;\s*$""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
            )
            result = simpleCommentPattern.replace(result, "")
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
                        // NVL2(expr, val_not_null, val_null) → IF(expr IS NOT NULL, val_not_null, val_null)
                        result = result.replace(
                            Regex("\\bNVL2\\s*\\(\\s*([^,]+)\\s*,\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", RegexOption.IGNORE_CASE),
                            "IF($1 IS NOT NULL, $2, $3)"
                        )
                        // DECODE 함수 변환 (Oracle → MySQL CASE)
                        result = convertDecodeToCase(result)
                        result = result.replace(Regex("\\bINSTR\\s*\\(", RegexOption.IGNORE_CASE), "LOCATE(")
                        result = result.replace(Regex("\\bLENGTH\\s*\\(", RegexOption.IGNORE_CASE), "CHAR_LENGTH(")
                        // TO_CHAR, TO_DATE 변환
                        result = result.replace(Regex("\\bTO_CHAR\\s*\\(", RegexOption.IGNORE_CASE), "DATE_FORMAT(")
                        result = result.replace(Regex("\\bTO_DATE\\s*\\(", RegexOption.IGNORE_CASE), "STR_TO_DATE(")
                        result = result.replace(Regex("\\bTO_NUMBER\\s*\\(", RegexOption.IGNORE_CASE), "CAST(")
                        result = result.replace(Regex("\\bADD_MONTHS\\s*\\(", RegexOption.IGNORE_CASE), "DATE_ADD(")
                        result = result.replace(Regex("\\bMONTHS_BETWEEN\\s*\\(", RegexOption.IGNORE_CASE), "TIMESTAMPDIFF(MONTH,")
                        result = result.replace(Regex("\\bTRUNC\\s*\\(", RegexOption.IGNORE_CASE), "TRUNCATE(")
                        // LISTAGG → GROUP_CONCAT
                        result = result.replace(Regex("\\bLISTAGG\\s*\\(", RegexOption.IGNORE_CASE), "GROUP_CONCAT(")
                        // 수학 함수 변환
                        result = result.replace(Regex("\\bCEIL\\s*\\(", RegexOption.IGNORE_CASE), "CEILING(")
                        result = result.replace(Regex("\\bMOD\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", RegexOption.IGNORE_CASE), "($1 % $2)")
                        result = result.replace(Regex("\\bPOWER\\s*\\(", RegexOption.IGNORE_CASE), "POW(")
                        result = result.replace(Regex("\\bLN\\s*\\(", RegexOption.IGNORE_CASE), "LOG(")
                        result = result.replace(Regex("\\bLOG\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", RegexOption.IGNORE_CASE), "LOG($2) / LOG($1)")
                        // 문자열 함수 변환 (Oracle → MySQL)
                        result = result.replace(Regex("\\bLENGTHB\\s*\\(", RegexOption.IGNORE_CASE), "LENGTH(")
                        result = result.replace(Regex("\\bREGEXP_LIKE\\s*\\(([^,]+),\\s*([^)]+)\\)", RegexOption.IGNORE_CASE), "$1 REGEXP $2")
                        result = result.replace(Regex("\\bREGEXP_SUBSTR\\s*\\(", RegexOption.IGNORE_CASE), "REGEXP_SUBSTR(")
                        result = result.replace(Regex("\\bREGEXP_REPLACE\\s*\\(", RegexOption.IGNORE_CASE), "REGEXP_REPLACE(")
                        result = result.replace(Regex("\\bTRANSLATE\\s*\\(", RegexOption.IGNORE_CASE), "REPLACE(") // 부분 호환, 수동 검토 필요
                        // EXTRACT 함수 변환 (Oracle → MySQL)
                        result = result.replace(Regex("\\bEXTRACT\\s*\\(\\s*YEAR\\s+FROM\\s+([^)]+)\\)", RegexOption.IGNORE_CASE), "YEAR($1)")
                        result = result.replace(Regex("\\bEXTRACT\\s*\\(\\s*MONTH\\s+FROM\\s+([^)]+)\\)", RegexOption.IGNORE_CASE), "MONTH($1)")
                        result = result.replace(Regex("\\bEXTRACT\\s*\\(\\s*DAY\\s+FROM\\s+([^)]+)\\)", RegexOption.IGNORE_CASE), "DAY($1)")
                        result = result.replace(Regex("\\bEXTRACT\\s*\\(\\s*HOUR\\s+FROM\\s+([^)]+)\\)", RegexOption.IGNORE_CASE), "HOUR($1)")
                        result = result.replace(Regex("\\bEXTRACT\\s*\\(\\s*MINUTE\\s+FROM\\s+([^)]+)\\)", RegexOption.IGNORE_CASE), "MINUTE($1)")
                        result = result.replace(Regex("\\bEXTRACT\\s*\\(\\s*SECOND\\s+FROM\\s+([^)]+)\\)", RegexOption.IGNORE_CASE), "SECOND($1)")
                        // ROWNUM 변환
                        result = convertRownumToLimit(result, DialectType.MYSQL)
                        // || 문자열 연결 → CONCAT()
                        result = convertOracleConcatToMysql(result)
                        // DUAL 테이블 제거
                        result = result.replace(Regex("\\s+FROM\\s+DUAL\\b", RegexOption.IGNORE_CASE), "")
                        // MINUS → EXCEPT (Oracle → MySQL: MySQL 8.0.31+ 지원)
                        result = result.replace(Regex("\\bMINUS\\b", RegexOption.IGNORE_CASE), "EXCEPT")
                        appliedRules.add("Oracle → MySQL 함수 변환")
                    }
                    DialectType.POSTGRESQL -> {
                        result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                        result = result.replace(Regex("\\bNVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
                        // NVL2(expr, val_not_null, val_null) → CASE WHEN expr IS NOT NULL THEN val_not_null ELSE val_null END
                        result = result.replace(
                            Regex("\\bNVL2\\s*\\(\\s*([^,]+)\\s*,\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", RegexOption.IGNORE_CASE),
                            "CASE WHEN $1 IS NOT NULL THEN $2 ELSE $3 END"
                        )
                        result = result.replace(Regex("\\bSUBSTR\\s*\\(", RegexOption.IGNORE_CASE), "SUBSTRING(")
                        result = result.replace(Regex("\\bINSTR\\s*\\(", RegexOption.IGNORE_CASE), "POSITION(")
                        result = result.replace(Regex("\\bLENGTH\\s*\\(", RegexOption.IGNORE_CASE), "CHAR_LENGTH(")
                        // TO_NUMBER 변환
                        result = result.replace(Regex("\\bTO_NUMBER\\s*\\(", RegexOption.IGNORE_CASE), "CAST(")
                        result = result.replace(Regex("\\bADD_MONTHS\\s*\\(([^,]+),\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE), "$1 + INTERVAL '$2 months'")
                        result = result.replace(Regex("\\bTRUNC\\s*\\(([^,]+)\\)", RegexOption.IGNORE_CASE), "DATE_TRUNC('day', $1)")
                        // LISTAGG → STRING_AGG
                        result = result.replace(Regex("\\bLISTAGG\\s*\\(", RegexOption.IGNORE_CASE), "STRING_AGG(")
                        // 수학 함수 변환 (Oracle → PostgreSQL)
                        result = result.replace(Regex("\\bMOD\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", RegexOption.IGNORE_CASE), "MOD($1, $2)")
                        result = result.replace(Regex("\\bLOG\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", RegexOption.IGNORE_CASE), "LOG($1, $2)")
                        // 문자열 함수 변환 (Oracle → PostgreSQL)
                        result = result.replace(Regex("\\bLENGTHB\\s*\\(", RegexOption.IGNORE_CASE), "OCTET_LENGTH(")
                        result = result.replace(Regex("\\bREGEXP_LIKE\\s*\\(([^,]+),\\s*([^)]+)\\)", RegexOption.IGNORE_CASE), "$1 ~ $2")
                        result = result.replace(Regex("\\bREGEXP_SUBSTR\\s*\\(", RegexOption.IGNORE_CASE), "SUBSTRING(")
                        result = result.replace(Regex("\\bREGEXP_REPLACE\\s*\\(", RegexOption.IGNORE_CASE), "REGEXP_REPLACE(")
                        result = result.replace(Regex("\\bTRANSLATE\\s*\\(", RegexOption.IGNORE_CASE), "TRANSLATE(")
                        result = result.replace(Regex("\\bINITCAP\\s*\\(", RegexOption.IGNORE_CASE), "INITCAP(")
                        // ROWNUM 변환
                        result = convertRownumToLimit(result, DialectType.POSTGRESQL)
                        // DUAL 테이블 제거
                        result = result.replace(Regex("\\s+FROM\\s+DUAL\\b", RegexOption.IGNORE_CASE), "")
                        // MINUS → EXCEPT (Oracle → PostgreSQL)
                        result = result.replace(Regex("\\bMINUS\\b", RegexOption.IGNORE_CASE), "EXCEPT")
                        // PostgreSQL은 || 지원하므로 변환 불필요
                        appliedRules.add("Oracle → PostgreSQL 함수 변환")
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
                        result = result.replace(Regex("\\bCEILING\\s*\\(", RegexOption.IGNORE_CASE), "CEIL(")
                        result = result.replace(Regex("\\bPOW\\s*\\(", RegexOption.IGNORE_CASE), "POWER(")

                        // EXTRACT 함수 변환 (MySQL → PostgreSQL)
                        // MySQL에서 YEAR(), MONTH() 등을 PostgreSQL EXTRACT로
                        result = result.replace(Regex("\\bYEAR\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE), "EXTRACT(YEAR FROM $1)")
                        result = result.replace(Regex("\\bMONTH\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE), "EXTRACT(MONTH FROM $1)")
                        result = result.replace(Regex("\\bDAY\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE), "EXTRACT(DAY FROM $1)")
                        result = result.replace(Regex("\\bHOUR\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE), "EXTRACT(HOUR FROM $1)")
                        result = result.replace(Regex("\\bMINUTE\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE), "EXTRACT(MINUTE FROM $1)")
                        result = result.replace(Regex("\\bSECOND\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE), "EXTRACT(SECOND FROM $1)")
                        result = result.replace(Regex("\\bDAYOFWEEK\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE), "EXTRACT(DOW FROM $1)")
                        result = result.replace(Regex("\\bDAYOFYEAR\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE), "EXTRACT(DOY FROM $1)")
                        result = result.replace(Regex("\\bWEEK\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE), "EXTRACT(WEEK FROM $1)")
                        result = result.replace(Regex("\\bQUARTER\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE), "EXTRACT(QUARTER FROM $1)")

                        // 기타
                        result = result.replace(Regex("\\bLAST_INSERT_ID\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "LASTVAL()")

                        appliedRules.add("MySQL → PostgreSQL 함수 변환")
                    }
                    DialectType.ORACLE -> {
                        result = result.replace(Regex("\\bNOW\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "SYSDATE")
                        result = result.replace(Regex("\\bIFNULL\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
                        result = result.replace(Regex("\\bCOALESCE\\s*\\(", RegexOption.IGNORE_CASE), "NVL(")
                        result = result.replace(Regex("\\bSUBSTRING\\s*\\(", RegexOption.IGNORE_CASE), "SUBSTR(")
                        // 수학 함수 (MySQL → Oracle)
                        result = result.replace(Regex("\\bCEILING\\s*\\(", RegexOption.IGNORE_CASE), "CEIL(")
                        result = result.replace(Regex("\\bPOW\\s*\\(", RegexOption.IGNORE_CASE), "POWER(")
                        result = result.replace(Regex("\\bLOG\\s*\\(", RegexOption.IGNORE_CASE), "LN(")
                        result = result.replace(Regex("\\bLOG10\\s*\\(", RegexOption.IGNORE_CASE), "LOG(10,")
                        result = result.replace(Regex("\\bLOG2\\s*\\(", RegexOption.IGNORE_CASE), "LOG(2,")
                        result = result.replace(Regex("\\bTRUNCATE\\s*\\(", RegexOption.IGNORE_CASE), "TRUNC(")
                        result = result.replace(Regex("\\bRAND\\s*\\(\\s*\\)", RegexOption.IGNORE_CASE), "DBMS_RANDOM.VALUE")
                        // 문자열 함수 (MySQL → Oracle)
                        result = result.replace(Regex("\\bCHAR_LENGTH\\s*\\(", RegexOption.IGNORE_CASE), "LENGTH(")
                        result = result.replace(Regex("\\bCONCAT\\s*\\(([^,]+),\\s*([^)]+)\\)", RegexOption.IGNORE_CASE), "$1 || $2")
                        result = result.replace(Regex("\\bLOCATE\\s*\\(([^,]+),\\s*([^)]+)\\)", RegexOption.IGNORE_CASE), "INSTR($2, $1)")
                        result = result.replace(Regex("\\bGROUP_CONCAT\\s*\\(", RegexOption.IGNORE_CASE), "LISTAGG(")
                        result = result.replace(Regex("([^\\s]+)\\s+REGEXP\\s+([^\\s]+)", RegexOption.IGNORE_CASE), "REGEXP_LIKE($1, $2)")
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
                        // NUMBER(p,s) - 소수점 포함
                        result = result.replace(Regex("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE), "DECIMAL($1,$2)")
                        // NUMBER(p) - 정수
                        result = result.replace(Regex("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)) { m ->
                            val precision = m.groupValues[1].toInt()
                            when {
                                precision <= 3 -> "TINYINT"
                                precision <= 5 -> "SMALLINT"
                                precision <= 9 -> "INT"
                                else -> "BIGINT"
                            }
                        }
                        // NUMBER (정밀도 없음) - DECIMAL로 변환
                        result = result.replace(Regex("\\bNUMBER\\b(?!\\s*\\()", RegexOption.IGNORE_CASE), "DECIMAL(38,10)")
                        result = result.replace(Regex("\\bVARCHAR2\\s*\\(", RegexOption.IGNORE_CASE), "VARCHAR(")
                        result = result.replace(Regex("\\bNVARCHAR2\\s*\\(", RegexOption.IGNORE_CASE), "VARCHAR(")
                        result = result.replace(Regex("\\bNCHAR\\s*\\(", RegexOption.IGNORE_CASE), "CHAR(")
                        result = result.replace(Regex("\\bNCLOB\\b", RegexOption.IGNORE_CASE), "LONGTEXT")
                        result = result.replace(Regex("\\bCLOB\\b", RegexOption.IGNORE_CASE), "LONGTEXT")
                        result = result.replace(Regex("\\bBLOB\\b", RegexOption.IGNORE_CASE), "LONGBLOB")
                        result = result.replace(Regex("\\bRAW\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE), "VARBINARY($1)")
                        result = result.replace(Regex("\\bLONG\\s+RAW\\b", RegexOption.IGNORE_CASE), "LONGBLOB")
                        result = result.replace(Regex("\\bLONG\\b", RegexOption.IGNORE_CASE), "LONGTEXT")
                        result = result.replace(Regex("\\bDATE\\b", RegexOption.IGNORE_CASE), "DATETIME")
                        result = result.replace(Regex("\\bTIMESTAMP\\s*\\(\\s*\\d+\\s*\\)", RegexOption.IGNORE_CASE), "DATETIME(6)")
                        result = result.replace(Regex("\\bTIMESTAMP\\b", RegexOption.IGNORE_CASE), "DATETIME")
                        result = result.replace(Regex("\\bINTERVAL\\s+YEAR.*?TO\\s+MONTH\\b", RegexOption.IGNORE_CASE), "VARCHAR(30)")
                        result = result.replace(Regex("\\bINTERVAL\\s+DAY.*?TO\\s+SECOND\\b", RegexOption.IGNORE_CASE), "VARCHAR(30)")
                        result = result.replace(Regex("\\bXMLTYPE\\b", RegexOption.IGNORE_CASE), "LONGTEXT")
                        result = result.replace(Regex("\\bFLOAT\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)) { m ->
                            val precision = m.groupValues[1].toInt()
                            if (precision <= 24) "FLOAT" else "DOUBLE"
                        }
                        result = result.replace(Regex("\\bBINARY_FLOAT\\b", RegexOption.IGNORE_CASE), "FLOAT")
                        result = result.replace(Regex("\\bBINARY_DOUBLE\\b", RegexOption.IGNORE_CASE), "DOUBLE")
                        appliedRules.add("Oracle → MySQL 데이터타입 변환")
                    }
                    DialectType.POSTGRESQL -> {
                        // NUMBER(p,s) - 소수점 포함
                        result = result.replace(Regex("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE), "NUMERIC($1,$2)")
                        // NUMBER(p) - 정수
                        result = result.replace(Regex("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)) { m ->
                            val precision = m.groupValues[1].toInt()
                            when {
                                precision <= 4 -> "SMALLINT"
                                precision <= 9 -> "INTEGER"
                                else -> "BIGINT"
                            }
                        }
                        // NUMBER (정밀도 없음) - NUMERIC으로 변환
                        result = result.replace(Regex("\\bNUMBER\\b(?!\\s*\\()", RegexOption.IGNORE_CASE), "NUMERIC")
                        result = result.replace(Regex("\\bVARCHAR2\\s*\\(", RegexOption.IGNORE_CASE), "VARCHAR(")
                        result = result.replace(Regex("\\bNVARCHAR2\\s*\\(", RegexOption.IGNORE_CASE), "VARCHAR(")
                        result = result.replace(Regex("\\bNCHAR\\s*\\(", RegexOption.IGNORE_CASE), "CHAR(")
                        result = result.replace(Regex("\\bNCLOB\\b", RegexOption.IGNORE_CASE), "TEXT")
                        result = result.replace(Regex("\\bCLOB\\b", RegexOption.IGNORE_CASE), "TEXT")
                        result = result.replace(Regex("\\bBLOB\\b", RegexOption.IGNORE_CASE), "BYTEA")
                        result = result.replace(Regex("\\bRAW\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE), "BYTEA")
                        result = result.replace(Regex("\\bLONG\\s+RAW\\b", RegexOption.IGNORE_CASE), "BYTEA")
                        result = result.replace(Regex("\\bLONG\\b", RegexOption.IGNORE_CASE), "TEXT")
                        result = result.replace(Regex("\\bTIMESTAMP\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE), "TIMESTAMP($1)")
                        result = result.replace(Regex("\\bINTERVAL\\s+YEAR.*?TO\\s+MONTH\\b", RegexOption.IGNORE_CASE), "INTERVAL")
                        result = result.replace(Regex("\\bINTERVAL\\s+DAY.*?TO\\s+SECOND\\b", RegexOption.IGNORE_CASE), "INTERVAL")
                        result = result.replace(Regex("\\bXMLTYPE\\b", RegexOption.IGNORE_CASE), "XML")
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

    /**
     * Oracle DDL 옵션 제거 (파싱 성공 후에도 적용)
     */
    private fun removeOracleDdlOptions(
        sql: String,
        targetDialectType: DialectType,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // Oracle DDL 옵션 블록 제거 (닫는 괄호 뒤의 모든 Oracle 전용 옵션)
        if (SqlRegexPatterns.ORACLE_DDL_OPTIONS_BLOCK.containsMatchIn(result)) {
            result = SqlRegexPatterns.ORACLE_DDL_OPTIONS_BLOCK.replace(result, ")")
            appliedRules.add("Oracle DDL 옵션 블록 제거")
        }

        // TABLESPACE 제거
        if (SqlRegexPatterns.TABLESPACE.containsMatchIn(result)) {
            result = SqlRegexPatterns.TABLESPACE.replace(result, "")
            appliedRules.add("TABLESPACE 절 제거")
        }

        // STORAGE 절 제거
        if (SqlRegexPatterns.STORAGE_CLAUSE.containsMatchIn(result)) {
            result = SqlRegexPatterns.STORAGE_CLAUSE.replace(result, "")
            appliedRules.add("STORAGE 절 제거")
        }

        // 개별 물리적 옵션 제거
        if (SqlRegexPatterns.PCTFREE.containsMatchIn(result)) {
            result = SqlRegexPatterns.PCTFREE.replace(result, "")
        }
        if (SqlRegexPatterns.PCTUSED.containsMatchIn(result)) {
            result = SqlRegexPatterns.PCTUSED.replace(result, "")
        }
        if (SqlRegexPatterns.INITRANS.containsMatchIn(result)) {
            result = SqlRegexPatterns.INITRANS.replace(result, "")
        }
        if (SqlRegexPatterns.MAXTRANS.containsMatchIn(result)) {
            result = SqlRegexPatterns.MAXTRANS.replace(result, "")
        }

        // COMPRESS/NOCOMPRESS 제거
        if (SqlRegexPatterns.COMPRESS.containsMatchIn(result)) {
            result = SqlRegexPatterns.COMPRESS.replace(result, "")
        }

        // LOGGING/NOLOGGING 제거
        if (SqlRegexPatterns.LOGGING.containsMatchIn(result)) {
            result = SqlRegexPatterns.LOGGING.replace(result, "")
        }

        // CACHE/NOCACHE 제거
        if (SqlRegexPatterns.CACHE.containsMatchIn(result)) {
            result = SqlRegexPatterns.CACHE.replace(result, "")
        }

        // MONITORING/NOMONITORING 제거
        if (SqlRegexPatterns.MONITORING.containsMatchIn(result)) {
            result = SqlRegexPatterns.MONITORING.replace(result, "")
        }

        // SEGMENT CREATION 제거
        if (SqlRegexPatterns.SEGMENT_CREATION.containsMatchIn(result)) {
            result = SqlRegexPatterns.SEGMENT_CREATION.replace(result, "")
        }

        // ROW MOVEMENT 제거
        if (SqlRegexPatterns.ROW_MOVEMENT.containsMatchIn(result)) {
            result = SqlRegexPatterns.ROW_MOVEMENT.replace(result, "")
        }

        // PARALLEL 제거
        if (SqlRegexPatterns.PARALLEL.containsMatchIn(result)) {
            result = SqlRegexPatterns.PARALLEL.replace(result, "")
        }

        // 연속된 빈 줄 정리
        result = SqlRegexPatterns.MULTIPLE_BLANK_LINES.replace(result, "\n\n")

        return result.trim()
    }

    /**
     * MySQL 타겟용: 큰따옴표를 백틱으로 변환
     * 문자열 리터럴(작은따옴표) 내부는 변환하지 않음
     */
    private fun convertQuotesToBackticks(
        sql: String,
        appliedRules: MutableList<String>
    ): String {
        val result = StringBuilder()
        var inSingleQuote = false
        var hasConversion = false
        var i = 0

        while (i < sql.length) {
            val char = sql[i]

            when {
                // 이스케이프된 따옴표
                char == '\\' && i + 1 < sql.length -> {
                    result.append(char)
                    result.append(sql[i + 1])
                    i += 2
                    continue
                }
                // 작은따옴표 (문자열 리터럴 시작/끝)
                char == '\'' -> {
                    inSingleQuote = !inSingleQuote
                    result.append(char)
                }
                // 큰따옴표 (문자열 밖에서만 백틱으로 변환)
                char == '"' && !inSingleQuote -> {
                    result.append('`')
                    hasConversion = true
                }
                else -> {
                    result.append(char)
                }
            }
            i++
        }

        if (hasConversion) {
            appliedRules.add("식별자 인용부호 변환 (\" → `)")
        }

        return result.toString()
    }

    /**
     * Oracle ROWNUM을 MySQL LIMIT 또는 PostgreSQL LIMIT으로 변환
     */
    private fun convertRownumToLimit(sql: String, targetDialect: DialectType): String {
        var result = sql

        // 패턴 1: WHERE ROWNUM <= N 또는 WHERE ROWNUM < N
        val whereRownumPattern = Regex(
            """(\bWHERE\s+)(ROWNUM\s*<=?\s*(\d+))(\s+AND\s+)?""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )

        val match1 = whereRownumPattern.find(result)
        if (match1 != null) {
            val limit = match1.groupValues[3].toInt()
            val hasAnd = match1.groupValues[4].isNotBlank()

            // WHERE ROWNUM <= N 제거하고 끝에 LIMIT 추가
            result = if (hasAnd) {
                // WHERE ROWNUM <= N AND ... → WHERE ...
                result.replace(match1.value, match1.groupValues[1])
            } else {
                // WHERE ROWNUM <= N → (WHERE 제거)
                result.replace(match1.value, "")
            }

            // 끝에 LIMIT 추가
            result = result.trimEnd().removeSuffix(";") + " LIMIT $limit"
        }

        // 패턴 2: AND ROWNUM <= N (WHERE 절 중간에 있는 경우)
        val andRownumPattern = Regex(
            """\s+AND\s+ROWNUM\s*<=?\s*(\d+)""",
            setOf(RegexOption.IGNORE_CASE)
        )

        val match2 = andRownumPattern.find(result)
        if (match2 != null) {
            val limit = match2.groupValues[1].toInt()
            result = result.replace(match2.value, "")
            result = result.trimEnd().removeSuffix(";") + " LIMIT $limit"
        }

        // 패턴 3: 서브쿼리에서 ROWNUM 사용 (복잡한 케이스 - 경고만 표시)
        // SELECT * FROM (SELECT ..., ROWNUM AS rn FROM ...) WHERE rn BETWEEN 1 AND 10
        // 이 패턴은 너무 복잡하므로 경고만 추가

        return result
    }

    /**
     * Oracle || 문자열 연결 연산자를 MySQL CONCAT()으로 변환
     * SELECT문 내에서만 변환 (CREATE TABLE 등에서는 변환하지 않음)
     */
    private fun convertOracleConcatToMysql(sql: String): String {
        // SELECT 문이 아니면 건너뜀
        if (!sql.trim().uppercase().startsWith("SELECT")) {
            return sql
        }

        // || 가 없으면 건너뜀
        if (!sql.contains("||")) {
            return sql
        }

        val result = StringBuilder()
        var inSingleQuote = false
        var i = 0

        while (i < sql.length) {
            val char = sql[i]
            when {
                char == '\'' -> {
                    inSingleQuote = !inSingleQuote
                    result.append(char)
                }
                char == '|' && i + 1 < sql.length && sql[i + 1] == '|' && !inSingleQuote -> {
                    // || → , (나중에 CONCAT으로 감싸야 함)
                    // 간단한 변환: || 를 CONCAT 호출로 변경
                    result.append(", ")
                    i += 2
                    continue
                }
                else -> result.append(char)
            }
            i++
        }

        // CONCAT으로 감싸기는 복잡하므로 경고만 추가하고 , 로 대체
        // 완벽한 변환이 아니므로 수동 검토 필요
        return result.toString()
    }

    /**
     * Oracle || 문자열 연결 연산자를 CONCAT()으로 변환 (MySQL용)
     */
    private fun convertStringConcatToConcat(sql: String, targetDialect: DialectType): String {
        if (targetDialect != DialectType.MYSQL) return sql

        var result = sql
        val concatPattern = Regex(
            """([^|])\|\|([^|])""",
            RegexOption.MULTILINE
        )

        // 간단한 경우만 처리: a || b → CONCAT(a, b)
        // 복잡한 케이스: a || b || c 는 중첩 CONCAT 필요
        if (concatPattern.containsMatchIn(result)) {
            // || 를 , 로 변경하고 CONCAT()으로 감싸기
            // 단순화된 처리: 전체 표현식을 찾아서 변환하기 어려우므로
            // 문자열 리터럴 밖의 || 만 처리

            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var inSingleQuote = false
            var i = 0

            while (i < result.length) {
                val char = result[i]
                when {
                    char == '\'' -> {
                        inSingleQuote = !inSingleQuote
                        current.append(char)
                    }
                    char == '|' && i + 1 < result.length && result[i + 1] == '|' && !inSingleQuote -> {
                        parts.add(current.toString().trim())
                        current.clear()
                        i += 2
                        continue
                    }
                    else -> current.append(char)
                }
                i++
            }
            if (current.isNotEmpty()) {
                parts.add(current.toString().trim())
            }

            // parts가 2개 이상이면 CONCAT으로 변환
            if (parts.size >= 2) {
                result = "CONCAT(${parts.joinToString(", ")})"
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

    /**
     * Oracle DECODE 함수를 CASE 표현식으로 변환
     * DECODE(expr, val1, result1, val2, result2, ..., default) →
     * CASE expr WHEN val1 THEN result1 WHEN val2 THEN result2 ... ELSE default END
     */
    private fun convertDecodeToCase(sql: String): String {
        val decodePattern = Regex("""DECODE\s*\(""", RegexOption.IGNORE_CASE)
        if (!decodePattern.containsMatchIn(sql)) {
            return sql
        }

        val result = StringBuilder()
        var i = 0

        while (i < sql.length) {
            val remaining = sql.substring(i)
            val match = decodePattern.find(remaining)

            if (match != null && match.range.first == 0) {
                // DECODE 함수 발견 - 인자들을 파싱
                val startIdx = i + match.value.length
                val (args, endIdx) = extractFunctionArgs(sql, startIdx)

                if (args.size >= 3) {
                    val expr = args[0].trim()
                    val caseExpr = StringBuilder("CASE $expr ")

                    // 쌍으로 처리 (val, result)
                    var j = 1
                    while (j < args.size - 1) {
                        val value = args[j].trim()
                        val resultVal = args[j + 1].trim()
                        caseExpr.append("WHEN $value THEN $resultVal ")
                        j += 2
                    }

                    // 마지막 인자가 홀수개면 default 값
                    if ((args.size - 1) % 2 == 1) {
                        val defaultVal = args.last().trim()
                        caseExpr.append("ELSE $defaultVal ")
                    }

                    caseExpr.append("END")
                    result.append(caseExpr)
                    i = endIdx + 1
                } else {
                    // 인자가 부족하면 원본 유지
                    result.append(match.value)
                    i += match.value.length
                }
            } else {
                result.append(sql[i])
                i++
            }
        }

        return result.toString()
    }

    /**
     * 함수 인자 추출 (중첩 괄호 처리)
     */
    private fun extractFunctionArgs(sql: String, startIdx: Int): Pair<List<String>, Int> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 1
        var i = startIdx
        var inSingleQuote = false

        while (i < sql.length && depth > 0) {
            val char = sql[i]

            when {
                char == '\'' && (i == 0 || sql[i - 1] != '\\') -> {
                    inSingleQuote = !inSingleQuote
                    current.append(char)
                }
                !inSingleQuote && char == '(' -> {
                    depth++
                    current.append(char)
                }
                !inSingleQuote && char == ')' -> {
                    depth--
                    if (depth == 0) {
                        if (current.isNotEmpty()) {
                            args.add(current.toString())
                        }
                    } else {
                        current.append(char)
                    }
                }
                !inSingleQuote && char == ',' && depth == 1 -> {
                    args.add(current.toString())
                    current.clear()
                }
                else -> {
                    current.append(char)
                }
            }
            i++
        }

        return Pair(args, i - 1)
    }
}