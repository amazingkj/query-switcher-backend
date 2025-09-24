package com.sqlswitcher.converter

import com.sqlswitcher.parser.model.AstAnalysisResult
import com.sqlswitcher.parser.SqlParserService
import com.sqlswitcher.parser.AstAnalysisService
import com.sqlswitcher.service.SqlMetricsService
import com.sqlswitcher.service.*
import net.sf.jsqlparser.statement.Statement
import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Autowired

/**
 * SQL 변환 엔진 - Strategy 패턴을 사용하여 데이터베이스 방언별 변환 전략을 관리
 */
@Component
class SqlConverterEngine @Autowired constructor(
    private val mySqlDialect: MySqlDialect,
    private val postgreSqlDialect: PostgreSqlDialect,
    private val oracleDialect: OracleDialect,
    private val tiberoDialect: TiberoDialect,
    private val functionMapper: FunctionMapper,
    private val dataTypeConverter: DataTypeConverter,
    private val sqlParserService: SqlParserService,
    private val astAnalysisService: AstAnalysisService,
    private val sqlMetricsService: SqlMetricsService
) {
    
    private val dialectStrategies = mapOf(
        DialectType.MYSQL to mySqlDialect,
        DialectType.POSTGRESQL to postgreSqlDialect,
        DialectType.ORACLE to oracleDialect,
        DialectType.TIBERO to tiberoDialect
    )
    
    /**
     * SQL 변환 실행
     * @param sql 변환할 SQL 문자열
     * @param sourceDialect 소스 데이터베이스 방언
     * @param targetDialect 타겟 데이터베이스 방언
     * @return 변환 결과
     */
    fun convertSql(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType
    ): SqlConversionResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // 1. SQL 파싱
            val parseResult = sqlParserService.parseSql(sql)
            val statement = parseResult.statement
            val analysisResult = parseResult.analysisResult
            
            // 2. 변환 가능 여부 확인
            val sourceStrategy = getDialectStrategy(sourceDialect)
            if (!sourceStrategy.canConvert(statement, targetDialect)) {
                return SqlConversionResult(
                    originalSql = sql,
                    convertedSql = sql,
                    warnings = listOf(
                        ConversionWarning(
                            type = WarningType.UNSUPPORTED_STATEMENT,
                            message = "지원되지 않는 SQL 문장 타입입니다.",
                            severity = WarningSeverity.ERROR,
                            suggestion = "다른 SQL 문장을 시도해보세요."
                        )
                    ),
                    appliedRules = emptyList(),
                    conversionMetadata = ConversionMetadata(
                        sourceDialect = sourceDialect,
                        targetDialect = targetDialect,
                        statementType = analysisResult.complexityDetails.toString(),
                        complexity = analysisResult.complexityDetails.totalComplexityScore,
                        conversionTime = System.currentTimeMillis() - startTime
                    )
                )
            }
            
            // 3. 변환 실행
            val conversionResult = sourceStrategy.performConversion(
                statement = statement!!,
                targetDialect = targetDialect,
                analysisResult = analysisResult
            )
            
            // 4. 메트릭 기록
            val conversionTime = System.currentTimeMillis() - startTime
            sqlMetricsService.recordConversionDuration(
                conversionTime,
                sourceDialect.name,
                targetDialect.name
            )
            
            return SqlConversionResult(
                originalSql = sql,
                convertedSql = conversionResult.convertedSql,
                warnings = conversionResult.warnings,
                appliedRules = conversionResult.appliedRules,
                conversionMetadata = ConversionMetadata(
                    sourceDialect = sourceDialect,
                    targetDialect = targetDialect,
                    statementType = analysisResult.complexityDetails.toString(),
                    complexity = analysisResult.complexityDetails.totalComplexityScore,
                    conversionTime = conversionTime
                )
            )
            
        } catch (e: Exception) {
            // 5. 오류 처리
            val conversionTime = System.currentTimeMillis() - startTime
            sqlMetricsService.recordConversionError("conversion_failed")
            
            return SqlConversionResult(
                originalSql = sql,
                convertedSql = sql,
                warnings = listOf(
                    ConversionWarning(
                        type = WarningType.UNSUPPORTED_FUNCTION,
                        message = "SQL 변환 중 오류가 발생했습니다: ${e.message}",
                        severity = WarningSeverity.ERROR,
                        suggestion = "SQL 문법을 확인하고 다시 시도해보세요."
                    )
                ),
                appliedRules = emptyList(),
                conversionMetadata = ConversionMetadata(
                    sourceDialect = sourceDialect,
                    targetDialect = targetDialect,
                    statementType = "UNKNOWN",
                    complexity = 0,
                    conversionTime = conversionTime
                )
            )
        }
    }
    
    /**
     * 방언별 변환 전략 조회
     */
    private fun getDialectStrategy(dialectType: DialectType): DatabaseDialect {
        return dialectStrategies[dialectType] 
            ?: throw IllegalArgumentException("지원되지 않는 데이터베이스 방언: $dialectType")
    }
    
    /**
     * 지원되는 방언 목록 조회
     */
    fun getSupportedDialects(): List<DialectType> {
        return DialectType.values().toList()
    }
    
    /**
     * 방언 간 호환성 확인
     */
    fun checkCompatibility(sourceDialect: DialectType, targetDialect: DialectType): CompatibilityResult {
        val sourceStrategy = getDialectStrategy(sourceDialect)
        val targetStrategy = getDialectStrategy(targetDialect)
        
        val compatibility = when {
            sourceDialect == targetDialect -> CompatibilityLevel.PERFECT
            isStandardSql(sourceDialect) && isStandardSql(targetDialect) -> CompatibilityLevel.HIGH
            sourceDialect == DialectType.ORACLE && targetDialect == DialectType.TIBERO -> CompatibilityLevel.HIGH
            sourceDialect == DialectType.TIBERO && targetDialect == DialectType.ORACLE -> CompatibilityLevel.HIGH
            else -> CompatibilityLevel.MEDIUM
        }
        
        val warnings = mutableListOf<ConversionWarning>()
        val suggestions = mutableListOf<String>()
        
        when (compatibility) {
            CompatibilityLevel.PERFECT -> {
                suggestions.add("동일한 방언이므로 변환이 필요하지 않습니다.")
            }
            CompatibilityLevel.HIGH -> {
                suggestions.add("높은 호환성을 가지므로 대부분의 기능이 정상 작동할 것입니다.")
            }
            CompatibilityLevel.MEDIUM -> {
                warnings.add(
                    ConversionWarning(
                        type = WarningType.UNSUPPORTED_FUNCTION,
                        message = "일부 기능이 제한될 수 있습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "변환 결과를 검토하고 필요시 수정하세요."
                    )
                )
                suggestions.add("변환 결과를 검토하고 필요시 수정하세요.")
            }
            CompatibilityLevel.LOW -> {
                warnings.add(
                    ConversionWarning(
                        type = WarningType.UNSUPPORTED_FUNCTION,
                        message = "많은 기능이 제한될 수 있습니다.",
                        severity = WarningSeverity.ERROR,
                        suggestion = "수동 변환을 고려하세요."
                    )
                )
                suggestions.add("수동 변환을 고려하세요.")
            }
        }
        
        return CompatibilityResult(
            sourceDialect = sourceDialect,
            targetDialect = targetDialect,
            compatibility = compatibility,
            warnings = warnings,
            suggestions = suggestions
        )
    }
    
    /**
     * SQL 표준 여부 확인
     */
    private fun isStandardSql(dialectType: DialectType): Boolean {
        return when (dialectType) {
            DialectType.MYSQL, DialectType.POSTGRESQL -> true
            DialectType.ORACLE, DialectType.TIBERO -> false
        }
    }

    /**
     * 변환 통계 조회
     */
    fun getConversionStatistics(): ConversionStatistics {
        return ConversionStatistics(
            totalConversions = sqlMetricsService.getTotalConversions(),
            successfulConversions = sqlMetricsService.getSuccessfulConversions(),
            failedConversions = sqlMetricsService.getFailedConversions(),
            averageConversionTime = sqlMetricsService.getAverageConversionTime(),
            mostUsedSourceDialect = sqlMetricsService.getMostUsedSourceDialect(),
            mostUsedTargetDialect = sqlMetricsService.getMostUsedTargetDialect()
        )
    }
}

/**
 * SQL 변환 결과
 */
data class SqlConversionResult(
    val originalSql: String,
    val convertedSql: String,
    val warnings: List<ConversionWarning>,
    val appliedRules: List<String>,
    val conversionMetadata: ConversionMetadata
)

/**
 * 호환성 결과
 */
data class CompatibilityResult(
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val compatibility: CompatibilityLevel,
    val warnings: List<ConversionWarning>,
    val suggestions: List<String>
)

/**
 * 호환성 레벨
 */
enum class CompatibilityLevel {
    PERFECT,    // 완벽한 호환성
    HIGH,       // 높은 호환성
    MEDIUM,     // 중간 호환성
    LOW         // 낮은 호환성
}

/**
 * 변환 통계
 */
data class ConversionStatistics(
    val totalConversions: Long,
    val successfulConversions: Long,
    val failedConversions: Long,
    val averageConversionTime: Double,
    val mostUsedSourceDialect: String?,
    val mostUsedTargetDialect: String?
)

