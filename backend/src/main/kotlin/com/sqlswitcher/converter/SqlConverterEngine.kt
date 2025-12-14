package com.sqlswitcher.converter

import com.sqlswitcher.converter.feature.PackageConversionService
import com.sqlswitcher.converter.preprocessor.OracleSyntaxPreprocessor
import com.sqlswitcher.converter.stringbased.StringBasedFunctionConverter
import com.sqlswitcher.converter.stringbased.StringBasedDataTypeConverter
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
    private val stringBasedDataTypeConverter: StringBasedDataTypeConverter
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

        // Oracle PACKAGE 감지 및 변환
        if (sourceDialectType == DialectType.ORACLE && targetDialectType != DialectType.ORACLE) {
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

            // DROP PACKAGE 처리
            if (sql.uppercase().contains("DROP") && sql.uppercase().contains("PACKAGE")) {
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
        }

        // Oracle 특화 문법 전처리 (별도 컴포넌트로 위임)
        convertedSql = oracleSyntaxPreprocessor.preprocess(convertedSql, targetDialectType, warnings, appliedRules)

        // 함수/데이터타입 변환 (별도 컴포넌트로 위임)
        convertedSql = stringBasedFunctionConverter.convert(convertedSql, sourceDialectType, targetDialectType, appliedRules)
        convertedSql = stringBasedDataTypeConverter.convert(convertedSql, sourceDialectType, targetDialectType, appliedRules)

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

    private fun createWarning(
        type: WarningType,
        message: String,
        severity: WarningSeverity,
        suggestion: String? = null
    ): ConversionWarning {
        return ConversionWarning(type, message, severity, suggestion)
    }
}