package com.sqlswitcher.converter

import com.sqlswitcher.converter.error.ConversionErrorHandler
import com.sqlswitcher.converter.feature.PackageConversionService
import com.sqlswitcher.converter.feature.dblink.DatabaseLinkConverter
import com.sqlswitcher.converter.feature.dbms.DbmsPackageConverter
import com.sqlswitcher.converter.feature.function.CteConverter
import com.sqlswitcher.converter.feature.function.HierarchicalQueryConverter
import com.sqlswitcher.converter.feature.function.PivotUnpivotConverter
import com.sqlswitcher.converter.feature.function.WindowFunctionConverter
import com.sqlswitcher.converter.feature.index.AdvancedIndexConverter
import com.sqlswitcher.converter.feature.mview.MaterializedViewConverter
import com.sqlswitcher.converter.feature.plsql.OraclePackageConverter
import com.sqlswitcher.converter.feature.sequence.AdvancedSequenceConverter
import com.sqlswitcher.converter.feature.synonym.SynonymConverter
import com.sqlswitcher.converter.feature.trigger.OracleTriggerConverter
import com.sqlswitcher.converter.feature.udt.UserDefinedTypeConverter
import com.sqlswitcher.converter.formatter.SqlFormatter
import com.sqlswitcher.converter.preprocessor.OracleSyntaxPreprocessor
import com.sqlswitcher.converter.recovery.ConversionRecoveryService
import com.sqlswitcher.converter.streaming.LargeSqlProcessor
import com.sqlswitcher.converter.stringbased.StringBasedFunctionConverter
import com.sqlswitcher.converter.stringbased.StringBasedDataTypeConverter
import com.sqlswitcher.converter.validation.SqlValidationService
import com.sqlswitcher.parser.SqlParserService
import com.sqlswitcher.service.SqlMetricsService
import com.sqlswitcher.model.ConversionOptions as ModelConversionOptions
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * SQL 변환을 위한 메인 엔진.
 * Strategy 패턴을 사용하여 각 데이터베이스 방언별 변환 로직을 관리합니다.
 *
 * 주요 컴포넌트:
 * - OracleSyntaxPreprocessor: Oracle 특화 문법 전처리
 * - StringBasedFunctionConverter: 문자열 기반 함수 변환
 * - StringBasedDataTypeConverter: 문자열 기반 데이터타입 변환
 * - PackageConversionService: Oracle 패키지 변환
 */
@Service
class SqlConverterEngine(
    private val sqlParserService: SqlParserService,
    private val sqlMetricsService: SqlMetricsService,
    private val dialects: List<DatabaseDialect>,
    private val functionMapper: FunctionMapper,
    private val dataTypeConverter: DataTypeConverter,
    private val packageConversionService: PackageConversionService,
    private val oracleSyntaxPreprocessor: OracleSyntaxPreprocessor,
    private val stringBasedFunctionConverter: StringBasedFunctionConverter,
    private val stringBasedDataTypeConverter: StringBasedDataTypeConverter,
    private val sqlValidationService: SqlValidationService
) {
    private val dialectMap: ConcurrentHashMap<DialectType, DatabaseDialect> = ConcurrentHashMap()

    // ========== 사전 컴파일된 Regex 패턴들 (성능 최적화) ==========

    companion object {
        /** CREATE OR REPLACE 패턴 */
        private val CREATE_OR_REPLACE_PATTERN = Regex(
            """^\s*CREATE\s+(?:OR\s+REPLACE\s+)?""",
            RegexOption.IGNORE_CASE
        )

        /** PL/SQL 객체 타입별 패턴 캐시 */
        private val PLSQL_OBJECT_PATTERNS: Map<String, Regex> by lazy {
            listOf(
                "PROCEDURE", "FUNCTION", "PACKAGE", "PACKAGE BODY",
                "TYPE", "TYPE BODY", "TRIGGER", "LIBRARY",
                "JAVA SOURCE", "JAVA CLASS"
            ).associateWith { objType ->
                Regex(
                    """CREATE\s+(?:OR\s+REPLACE\s+)?(?:EDITIONABLE\s+|NONEDITIONABLE\s+)?$objType\b""",
                    RegexOption.IGNORE_CASE
                )
            }
        }

        /** BEGIN 키워드 패턴 */
        private val BEGIN_PATTERN = Regex("""\bBEGIN\b""")

        /** CASE 키워드 패턴 */
        private val CASE_PATTERN = Regex("""\bCASE\b""")

        /** WHEN MATCHED 패턴 (MERGE 문 분석용) */
        private val WHEN_MATCHED_PATTERN = Regex("""WHEN\s+(NOT\s+)?MATCHED""")
    }

    /**
     * 자동 검증 활성화 여부
     */
    var enableAutoValidation: Boolean = true

    /**
     * 대용량 SQL 자동 최적화 활성화 여부
     */
    var enableLargeSqlOptimization: Boolean = true

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
            // 대용량 SQL 처리 최적화
            if (enableLargeSqlOptimization && LargeSqlProcessor.isLargeSql(sql)) {
                return convertLargeSql(sql, sourceDialectType, targetDialectType, startTime)
            }

            // 여러 SQL 문장 분리 처리
            val sqlStatements = splitSqlStatements(sql)

            // 단일 문장인 경우 기존 로직
            if (sqlStatements.size <= 1) {
                val result = convertSingleStatement(
                    sql.trim(), sourceDialectType, targetDialectType, modelOptions,
                    warnings, appliedRules, startTime
                )
                return addValidationToResult(result, sql, sourceDialectType, targetDialectType)
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

            val finalSql = convertedStatements.joinToString(";\n") + if (convertedStatements.isNotEmpty()) ";" else ""

            val result = ConversionResult(
                convertedSql = finalSql,
                warnings = warnings.distinctBy { it.message },
                appliedRules = appliedRules.distinct(),
                executionTime = duration
            )

            addValidationToResult(result, sql, sourceDialectType, targetDialectType)
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()

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
                appliedRules = appliedRules,
                executionTime = endTime - startTime
            )
        }
    }

    /**
     * 대용량 SQL 변환 (청크 병렬 처리)
     */
    private fun convertLargeSql(
        sql: String,
        sourceDialectType: DialectType,
        targetDialectType: DialectType,
        startTime: Long
    ): ConversionResult {
        val strategy = LargeSqlProcessor.determineStrategy(sql)

        val result = LargeSqlProcessor.processInChunks(
            sql = sql,
            sourceDialect = sourceDialectType,
            targetDialect = targetDialectType,
            converter = { stmt, src, tgt ->
                val warnings = mutableListOf<ConversionWarning>()
                val rules = mutableListOf<String>()
                convertSingleStatement(stmt, src, tgt, null, warnings, rules, startTime)
            },
            chunkSize = strategy.chunkSize
        )

        val endTime = System.currentTimeMillis()
        sqlMetricsService.recordConversionDuration(endTime - startTime, sourceDialectType.name, targetDialectType.name)

        if (result.failedStatements == 0) {
            sqlMetricsService.recordConversionSuccess(sourceDialectType.name, targetDialectType.name)
        }

        val conversionResult = ConversionResult(
            convertedSql = result.convertedSql,
            warnings = result.warnings,
            appliedRules = result.appliedRules + listOf(
                "대용량 SQL 최적화 적용",
                "전체 ${result.totalStatements}개 문장 중 ${result.successfulStatements}개 성공"
            ),
            executionTime = result.processingTimeMs
        )

        return addValidationToResult(conversionResult, sql, sourceDialectType, targetDialectType)
    }

    /**
     * 변환 결과에 검증 정보 추가
     */
    private fun addValidationToResult(
        result: ConversionResult,
        originalSql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType
    ): ConversionResult {
        if (!enableAutoValidation) {
            return result
        }

        return try {
            val validationResult = sqlValidationService.validateConversionPair(
                originalSql,
                result.convertedSql,
                sourceDialect,
                targetDialect
            )

            val validationInfo = ValidationInfo(
                isValid = validationResult.convertedValid,
                statementType = validationResult.detailedValidation.parseResult.statementType,
                isProductionReady = validationResult.detailedValidation.isProductionReady,
                qualityScore = validationResult.qualityScore,
                compatibilityIssues = validationResult.detailedValidation.compatibilityIssues,
                recommendation = validationResult.recommendation
            )

            // 검증 경고도 결과에 추가
            val allWarnings = result.warnings + validationResult.conversionWarnings.filter { vw ->
                result.warnings.none { it.message == vw.message }
            }

            result.copy(
                warnings = allWarnings,
                validation = validationInfo,
                appliedRules = result.appliedRules + listOf("자동 검증 완료 (품질: ${String.format("%.0f", validationInfo.qualityScore * 100)}%)")
            )
        } catch (e: Exception) {
            // 검증 실패 시 원본 결과 반환
            result
        }
    }

    /**
     * 단일 SQL 문장 변환
     */
    private fun convertSingleStatement(
        sql: String,
        sourceDialectType: DialectType,
        targetDialectType: DialectType,
        @Suppress("UNUSED_PARAMETER") modelOptions: ModelConversionOptions?,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        startTime: Long
    ): ConversionResult {
        // 0. 파싱 스킵 대상인지 먼저 확인 (PL/SQL, PACKAGE, TYPE 등)
        if (shouldSkipParsing(sql)) {
            warnings.add(createWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "PL/SQL 또는 Oracle 전용 객체 - 문자열 기반 변환을 수행합니다.",
                severity = WarningSeverity.INFO,
                suggestion = "CREATE PROCEDURE/FUNCTION/PACKAGE/TYPE/TRIGGER 등은 AST 파싱 없이 처리됩니다."
            ))
            return performFallbackConversion(
                sql, sourceDialectType, targetDialectType, warnings, appliedRules, startTime
            )
        }

        // 0.5. Oracle에서 다른 DB로 변환 시, 파싱 전에 Oracle DDL 옵션 전처리
        val preprocessedSql = if (sourceDialectType == DialectType.ORACLE && targetDialectType != DialectType.ORACLE) {
            oracleSyntaxPreprocessor.preprocess(sql, targetDialectType, warnings, appliedRules)
        } else {
            sql
        }

        // 1. SQL 파싱 및 AST 분석
        val parseResult = sqlParserService.parseSql(preprocessedSql)
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
        @Suppress("UNUSED_VARIABLE") val targetDialect = dialectMap[targetDialectType]
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

        // Oracle에서 다른 DB로 변환 시 특화 변환기 적용
        if (sourceDialectType == DialectType.ORACLE && targetDialectType != DialectType.ORACLE) {

            // Oracle PACKAGE 감지 및 변환 (신규 OraclePackageConverter 사용)
            if (OraclePackageConverter.isPackageStatement(sql)) {
                convertedSql = OraclePackageConverter.convert(
                    sql, sourceDialectType, targetDialectType, warnings, appliedRules
                )
                val endTime = System.currentTimeMillis()
                sqlMetricsService.recordConversionDuration(endTime - startTime, sourceDialectType.name, targetDialectType.name)
                sqlMetricsService.recordConversionSuccess(sourceDialectType.name, targetDialectType.name)
                return ConversionResult(
                    convertedSql = convertedSql,
                    warnings = warnings,
                    appliedRules = appliedRules
                )
            }

            // 기존 PackageConversionService 폴백
            if (packageConversionService.isPackageStatement(sql) || packageConversionService.isPackageBodyStatement(sql)) {
                convertedSql = packageConversionService.convertPackage(
                    sql, sourceDialectType, targetDialectType, warnings, appliedRules
                )
                val endTime = System.currentTimeMillis()
                sqlMetricsService.recordConversionDuration(endTime - startTime, sourceDialectType.name, targetDialectType.name)
                sqlMetricsService.recordConversionSuccess(sourceDialectType.name, targetDialectType.name)
                return ConversionResult(
                    convertedSql = convertedSql,
                    warnings = warnings,
                    appliedRules = appliedRules
                )
            }

            // DROP PACKAGE 처리 (upperSql 캐시 재사용)
            val upperSqlCached = sql.uppercase()
            if (upperSqlCached.contains("DROP") && upperSqlCached.contains("PACKAGE")) {
                convertedSql = packageConversionService.convertDropPackage(
                    sql, sourceDialectType, targetDialectType, warnings, appliedRules
                )
                val endTime = System.currentTimeMillis()
                sqlMetricsService.recordConversionDuration(endTime - startTime, sourceDialectType.name, targetDialectType.name)
                sqlMetricsService.recordConversionSuccess(sourceDialectType.name, targetDialectType.name)
                return ConversionResult(
                    convertedSql = convertedSql,
                    warnings = warnings,
                    appliedRules = appliedRules
                )
            }

            // Oracle TRIGGER 변환
            if (OracleTriggerConverter.isTriggerStatement(sql)) {
                convertedSql = OracleTriggerConverter.convert(
                    sql, sourceDialectType, targetDialectType, warnings, appliedRules
                )
                val endTime = System.currentTimeMillis()
                sqlMetricsService.recordConversionDuration(endTime - startTime, sourceDialectType.name, targetDialectType.name)
                sqlMetricsService.recordConversionSuccess(sourceDialectType.name, targetDialectType.name)
                return ConversionResult(
                    convertedSql = convertedSql,
                    warnings = warnings,
                    appliedRules = appliedRules
                )
            }

            // Oracle SYNONYM 변환
            if (SynonymConverter.hasSynonymStatements(sql)) {
                convertedSql = SynonymConverter.convert(
                    sql, sourceDialectType, targetDialectType, warnings, appliedRules
                )
                val endTime = System.currentTimeMillis()
                sqlMetricsService.recordConversionDuration(endTime - startTime, sourceDialectType.name, targetDialectType.name)
                sqlMetricsService.recordConversionSuccess(sourceDialectType.name, targetDialectType.name)
                return ConversionResult(
                    convertedSql = convertedSql,
                    warnings = warnings,
                    appliedRules = appliedRules
                )
            }

            // 사용자 정의 타입(UDT) 변환
            if (UserDefinedTypeConverter.hasUserDefinedTypes(sql)) {
                convertedSql = UserDefinedTypeConverter.convert(
                    sql, sourceDialectType, targetDialectType, warnings, appliedRules
                )
                // UDT 변환 후 계속 진행 (다른 변환도 적용 가능)
            }
        }

        // Oracle 특화 문법 전처리 (별도 컴포넌트로 위임)
        convertedSql = oracleSyntaxPreprocessor.preprocess(convertedSql, targetDialectType, warnings, appliedRules)

        // PIVOT/UNPIVOT 변환
        convertedSql = PivotUnpivotConverter.convert(
            convertedSql, sourceDialectType, targetDialectType, warnings, appliedRules
        )

        // 계층형 쿼리 변환 (CONNECT BY → WITH RECURSIVE)
        convertedSql = HierarchicalQueryConverter.convert(
            convertedSql, sourceDialectType, targetDialectType, warnings, appliedRules
        )

        // CTE(WITH 절) 변환
        convertedSql = CteConverter.convert(
            convertedSql, sourceDialectType, targetDialectType, warnings, appliedRules
        )

        // 윈도우 함수 변환
        convertedSql = WindowFunctionConverter.convert(
            convertedSql, sourceDialectType, targetDialectType, warnings, appliedRules
        )

        // DBMS_* 패키지 변환 (DBMS_OUTPUT, DBMS_LOB, DBMS_RANDOM 등)
        if (DbmsPackageConverter.hasDbmsPackageCalls(convertedSql)) {
            convertedSql = DbmsPackageConverter.convert(
                convertedSql, sourceDialectType, targetDialectType, warnings, appliedRules
            )
        }

        // 시퀀스 변환 (SEQUENCE 문장 또는 NEXTVAL/CURRVAL 참조)
        if (AdvancedSequenceConverter.isSequenceStatement(convertedSql) ||
            AdvancedSequenceConverter.hasSequenceReference(convertedSql)) {
            convertedSql = AdvancedSequenceConverter.convert(
                convertedSql, sourceDialectType, targetDialectType, warnings, appliedRules
            )
        }

        // 인덱스 변환 (CREATE/DROP/ALTER INDEX)
        if (AdvancedIndexConverter.isIndexStatement(convertedSql)) {
            convertedSql = AdvancedIndexConverter.convert(
                convertedSql, sourceDialectType, targetDialectType, warnings, appliedRules
            )
        }

        // Materialized View 변환
        if (MaterializedViewConverter.isMaterializedViewStatement(convertedSql)) {
            convertedSql = MaterializedViewConverter.convert(
                convertedSql, sourceDialectType, targetDialectType, warnings, appliedRules
            )
        }

        // Database Link 변환 (CREATE/DROP DATABASE LINK, @dblink 참조)
        if (DatabaseLinkConverter.isDatabaseLinkStatement(convertedSql) ||
            DatabaseLinkConverter.hasDbLinkReference(convertedSql)) {
            convertedSql = DatabaseLinkConverter.convert(
                convertedSql, sourceDialectType, targetDialectType, warnings, appliedRules
            )
        }

        // 함수/데이터타입 변환 (별도 컴포넌트로 위임)
        convertedSql = stringBasedFunctionConverter.convert(convertedSql, sourceDialectType, targetDialectType, appliedRules)
        convertedSql = stringBasedDataTypeConverter.convert(convertedSql, sourceDialectType, targetDialectType, appliedRules)

        // 변환 결과 검증
        val validationWarnings = sqlValidationService.validateConversion(
            sql, convertedSql, sourceDialectType, targetDialectType
        )
        warnings.addAll(validationWarnings)

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
     * 파싱을 스킵해야 하는 SQL인지 확인
     * PL/SQL, PACKAGE, TYPE, TRIGGER 등 JSQLParser가 처리하지 못하는 구문
     * (사전 컴파일된 패턴 사용으로 성능 최적화)
     */
    private fun shouldSkipParsing(sql: String): Boolean {
        val upperSql = sql.uppercase().trim()

        // CREATE OR REPLACE 패턴 체크 (사전 컴파일된 패턴 사용)
        if (!CREATE_OR_REPLACE_PATTERN.containsMatchIn(sql)) {
            // CREATE 문이 아니면 계층형 쿼리, MERGE 등 확인
            return isHierarchicalQuery(upperSql) || isComplexMerge(upperSql)
        }

        // PL/SQL 객체 타입들 (사전 컴파일된 패턴 캐시 사용)
        for ((_, pattern) in PLSQL_OBJECT_PATTERNS) {
            if (pattern.containsMatchIn(sql)) {
                return true
            }
        }

        // BEGIN...END 블록이 포함된 경우 (익명 PL/SQL 블록)
        if (upperSql.contains("BEGIN") && upperSql.contains("END")) {
            // SELECT 문 내의 CASE WHEN ... END는 제외 (사전 컴파일된 패턴 사용)
            val beginCount = BEGIN_PATTERN.findAll(upperSql).count()
            val caseEndCount = CASE_PATTERN.findAll(upperSql).count()
            if (beginCount > 0 && beginCount > caseEndCount) {
                return true
            }
        }

        // DECLARE 블록
        if (upperSql.startsWith("DECLARE")) {
            return true
        }

        // Oracle DIRECTORY, CONTEXT, SYNONYM 등 (CREATE 체크를 루프 밖에서 한 번만 수행)
        val oracleOnlyObjects = listOf(
            "DIRECTORY", "CONTEXT", "SYNONYM", "PUBLIC SYNONYM",
            "DATABASE LINK", "DBLINK", "MATERIALIZED VIEW LOG",
            "DIMENSION", "CLUSTER", "CONTROLFILE", "SPFILE",
            "PFILE", "ROLLBACK SEGMENT", "UNDO TABLESPACE"
        )

        val hasCreate = upperSql.contains("CREATE")
        for (objType in oracleOnlyObjects) {
            if (hasCreate && upperSql.contains(objType)) {
                return true
            }
        }

        // PARTITION BY가 복잡한 경우 (서브파티션 포함)
        if (upperSql.contains("PARTITION BY") && upperSql.contains("SUBPARTITION")) {
            return true
        }

        // CONNECT BY 계층형 쿼리
        if (isHierarchicalQuery(upperSql)) {
            return true
        }

        return false
    }

    /**
     * Oracle 계층형 쿼리인지 확인
     */
    private fun isHierarchicalQuery(upperSql: String): Boolean {
        return upperSql.contains("CONNECT BY") ||
               upperSql.contains("START WITH") && upperSql.contains("PRIOR") ||
               upperSql.contains("SYS_CONNECT_BY_PATH") ||
               upperSql.contains("CONNECT_BY_ROOT") ||
               upperSql.contains("CONNECT_BY_ISLEAF") ||
               upperSql.contains("CONNECT_BY_ISCYCLE")
    }

    /**
     * 복잡한 MERGE 문인지 확인 (다중 WHEN 절 등, 사전 컴파일된 패턴 사용)
     */
    private fun isComplexMerge(upperSql: String): Boolean {
        if (!upperSql.contains("MERGE")) return false

        // WHEN MATCHED/NOT MATCHED가 여러 개인 경우 (사전 컴파일된 패턴 사용)
        val whenMatchedCount = WHEN_MATCHED_PATTERN.findAll(upperSql).count()
        return whenMatchedCount > 2
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