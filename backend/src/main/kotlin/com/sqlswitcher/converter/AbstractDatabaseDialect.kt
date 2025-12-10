package com.sqlswitcher.converter

import com.sqlswitcher.parser.model.AstAnalysisResult
import net.sf.jsqlparser.statement.Statement
import org.springframework.stereotype.Component

/**
 * 모든 데이터베이스 방언이 구현해야 할 공통 추상 클래스
 */
abstract class AbstractDatabaseDialect : DatabaseDialect {
    
    /**
     * 공통 변환 로직을 위한 템플릿 메서드
     */
    final override fun convertQuery(
        statement: Statement, 
        targetDialect: DialectType, 
        analysisResult: AstAnalysisResult
    ): ConversionResult {
        val startTime = System.currentTimeMillis()
        
        // 변환 가능 여부 확인
        if (!canConvert(statement, targetDialect)) {
            return ConversionResult(
                convertedSql = statement.toString(),
                warnings = listOf(
                    ConversionWarning(
                        type = WarningType.MANUAL_REVIEW_NEEDED,
                        message = "변환이 지원되지 않는 SQL 구문입니다.",
                        severity = WarningSeverity.ERROR
                    )
                ),
                executionTime = System.currentTimeMillis() - startTime
            )
        }
        
        // 방언별 변환 로직 실행
        val result = performConversion(statement, targetDialect, analysisResult)
        
        // 공통 후처리
        val finalResult = postProcessConversion(result, targetDialect, analysisResult)
        
        return finalResult.copy(
            executionTime = System.currentTimeMillis() - startTime,
            metadata = ConversionMetadata(
                sourceDialect = getDialectType(),
                targetDialect = targetDialect,
                complexityScore = analysisResult.complexityDetails.totalComplexityScore,
                functionCount = analysisResult.functionExpressionInfo.functions.size,
                tableCount = analysisResult.tableColumnInfo.tables.size,
                joinCount = analysisResult.complexityDetails.joinCount,
                subqueryCount = analysisResult.complexityDetails.subqueryCount
            )
        )
    }
    
    /**
     * 방언별 구체적인 변환 로직 (하위 클래스에서 구현)
     */
    protected abstract fun performConversion(
        statement: Statement,
        targetDialect: DialectType,
        analysisResult: AstAnalysisResult
    ): ConversionResult
    
    /**
     * 변환 후 공통 후처리
     */
    protected open fun postProcessConversion(
        result: ConversionResult,
        targetDialect: DialectType,
        analysisResult: AstAnalysisResult
    ): ConversionResult {
        // 기본적으로는 그대로 반환, 필요시 하위 클래스에서 오버라이드
        return result
    }
    
    /**
     * 기본 변환 가능 여부 확인
     */
    override fun canConvert(statement: Statement, targetDialect: DialectType): Boolean {
        // 기본적으로는 모든 변환을 지원한다고 가정
        // 하위 클래스에서 구체적인 제약사항을 구현
        return true
    }
    
    /**
     * 공통 유틸리티 메서드들
     */
    
    /**
     * 인용 문자로 감싸기
     */
    protected fun quoteIdentifier(identifier: String): String {
        val quoteChar = getQuoteCharacter()
        return "$quoteChar$identifier$quoteChar"
    }
    
    /**
     * 인용 문자 제거
     */
    protected fun unquoteIdentifier(identifier: String): String {
        val quoteChar = getQuoteCharacter()
        return if (identifier.startsWith(quoteChar) && identifier.endsWith(quoteChar)) {
            identifier.substring(1, identifier.length - 1)
        } else {
            identifier
        }
    }
    
    /**
     * 함수명 변환
     */
    protected fun convertFunctionName(functionName: String, targetDialect: DialectType): String {
        return when (targetDialect) {
            DialectType.MYSQL -> convertToMySqlFunction(functionName)
            DialectType.POSTGRESQL -> convertToPostgreSqlFunction(functionName)
            DialectType.ORACLE -> convertToOracleFunction(functionName)
        }
    }
    
    /**
     * 데이터 타입 변환
     */
    protected fun convertDataType(dataType: String, targetDialect: DialectType): String {
        return when (targetDialect) {
            DialectType.MYSQL -> convertToMySqlDataType(dataType)
            DialectType.POSTGRESQL -> convertToPostgreSqlDataType(dataType)
            DialectType.ORACLE -> convertToOracleDataType(dataType)
        }
    }
    
    /**
     * 방언별 함수 변환 (하위 클래스에서 구현)
     */
    protected abstract fun convertToMySqlFunction(functionName: String): String
    protected abstract fun convertToPostgreSqlFunction(functionName: String): String
    protected abstract fun convertToOracleFunction(functionName: String): String

    /**
     * 방언별 데이터 타입 변환 (하위 클래스에서 구현)
     */
    protected abstract fun convertToMySqlDataType(dataType: String): String
    protected abstract fun convertToPostgreSqlDataType(dataType: String): String
    protected abstract fun convertToOracleDataType(dataType: String): String
    
    /**
     * 경고 생성 헬퍼 메서드
     */
    protected fun createWarning(
        type: WarningType,
        message: String,
        severity: WarningSeverity = WarningSeverity.WARNING,
        suggestion: String? = null
    ): ConversionWarning {
        return ConversionWarning(
            type = type,
            message = message,
            severity = severity,
            suggestion = suggestion
        )
    }
    
    /**
     * 지원되지 않는 함수에 대한 경고 생성
     */
    protected fun createUnsupportedFunctionWarning(
        functionName: String,
        targetDialect: DialectType
    ): ConversionWarning {
        return createWarning(
            type = WarningType.UNSUPPORTED_FUNCTION,
            message = "함수 '$functionName'은 '$targetDialect'에서 지원되지 않습니다.",
            severity = WarningSeverity.ERROR,
            suggestion = "대체 함수를 사용하거나 수동으로 수정해주세요."
        )
    }
    
    /**
     * 부분 지원에 대한 경고 생성
     */
    protected fun createPartialSupportWarning(
        feature: String,
        targetDialect: DialectType
    ): ConversionWarning {
        return createWarning(
            type = WarningType.PARTIAL_SUPPORT,
            message = "'$feature' 기능은 '$targetDialect'에서 부분적으로만 지원됩니다.",
            severity = WarningSeverity.WARNING,
            suggestion = "변환된 결과를 검토하고 필요시 수정해주세요."
        )
    }
}
