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
            // 1. SQL 파싱 및 AST 분석
            val parseResult = sqlParserService.parseSql(sql)
            if (parseResult.parseException != null) {
                warnings.add(createWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "SQL 파싱 오류: ${parseResult.parseException.message}",
                    severity = WarningSeverity.ERROR,
                    suggestion = "SQL 구문을 확인하세요."
                ))
                sqlMetricsService.recordConversionError(sourceDialectType.name, targetDialectType.name, "ParsingError")
                return ConversionResult(
                    convertedSql = sql, // 원본 SQL 반환
                    warnings = warnings,
                    appliedRules = appliedRules
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

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            sqlMetricsService.recordConversionDuration(duration, sourceDialectType.name, targetDialectType.name)
            sqlMetricsService.recordConversionSuccess(sourceDialectType.name, targetDialectType.name)

            ConversionResult(
                convertedSql = conversionResult.convertedSql,
                warnings = warnings,
                appliedRules = appliedRules
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

    private fun createWarning(
        type: WarningType,
        message: String,
        severity: WarningSeverity,
        suggestion: String? = null
    ): ConversionWarning {
        return ConversionWarning(type, message, severity, suggestion)
    }
}