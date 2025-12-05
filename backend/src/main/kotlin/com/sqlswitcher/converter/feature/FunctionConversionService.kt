package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.core.*
import com.sqlswitcher.converter.mapping.*
import net.sf.jsqlparser.expression.Function as SqlFunction
import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.expression.CaseExpression
import net.sf.jsqlparser.expression.WhenClause
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression
import net.sf.jsqlparser.expression.operators.relational.ExpressionList
import org.springframework.stereotype.Service

/**
 * 함수 변환 서비스 - 모든 방언 간 SQL 함수 변환을 담당
 */
@Service
class FunctionConversionService(
    private val mappingRegistry: FunctionMappingRegistry
) {

    /**
     * SQL 함수를 타겟 방언으로 변환
     */
    fun convertFunction(
        function: SqlFunction,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): Expression {
        if (sourceDialect == targetDialect) {
            return function
        }

        val functionName = function.name?.uppercase() ?: return function
        val rule = mappingRegistry.getMapping(sourceDialect, targetDialect, functionName)

        if (rule == null) {
            // 매핑 규칙이 없으면 원본 반환
            return function
        }

        // 경고 추가
        if (rule.warningType != null && rule.warningMessage != null) {
            warnings.add(ConversionWarning(
                type = rule.warningType,
                message = rule.warningMessage,
                severity = WarningSeverity.WARNING,
                suggestion = rule.suggestion
            ))
        }

        // CASE WHEN 변환이 필요한 경우
        if (rule.parameterTransform == ParameterTransform.TO_CASE_WHEN) {
            return convertToCaseWhen(function, functionName, sourceDialect, targetDialect, appliedRules)
        }

        // 일반 함수 변환
        val convertedFunction = function.clone() as SqlFunction
        convertedFunction.name = rule.targetFunction

        // 파라미터 변환
        when (rule.parameterTransform) {
            ParameterTransform.SWAP_FIRST_TWO -> swapFirstTwoParameters(convertedFunction)
            ParameterTransform.DATE_FORMAT_CONVERT -> convertDateFormat(convertedFunction, sourceDialect, targetDialect)
            else -> { /* 변환 없음 */ }
        }

        appliedRules.add("${rule.sourceFunction}() → ${rule.targetFunction}()")
        return convertedFunction
    }

    /**
     * DECODE, NVL2 등을 CASE WHEN으로 변환
     */
    private fun convertToCaseWhen(
        function: SqlFunction,
        functionName: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): Expression {
        val params = function.parameters?.expressions ?: return function

        return when (functionName) {
            "DECODE" -> convertDecodeToCaseWhen(params, appliedRules)
            "NVL2" -> convertNvl2ToCaseWhen(params, appliedRules)
            "IF" -> convertIfToCaseWhen(params, appliedRules)
            else -> function
        }
    }

    /**
     * DECODE(expr, search1, result1, search2, result2, ..., default) → CASE WHEN
     */
    private fun convertDecodeToCaseWhen(
        params: List<Expression>,
        appliedRules: MutableList<String>
    ): CaseExpression {
        if (params.isEmpty()) {
            return CaseExpression()
        }

        val caseExpr = CaseExpression()
        val switchExpr = params[0]
        caseExpr.switchExpression = switchExpr

        val whenClauses = mutableListOf<WhenClause>()
        var i = 1
        while (i + 1 < params.size) {
            val whenClause = WhenClause()
            whenClause.whenExpression = params[i]
            whenClause.thenExpression = params[i + 1]
            whenClauses.add(whenClause)
            i += 2
        }
        caseExpr.whenClauses = whenClauses

        // 홀수 개 파라미터면 마지막이 default
        if (i < params.size) {
            caseExpr.elseExpression = params[i]
        }

        appliedRules.add("DECODE() → CASE WHEN 변환")
        return caseExpr
    }

    /**
     * NVL2(expr, not_null_value, null_value) → CASE WHEN expr IS NOT NULL THEN ... ELSE ... END
     */
    private fun convertNvl2ToCaseWhen(
        params: List<Expression>,
        appliedRules: MutableList<String>
    ): CaseExpression {
        if (params.size < 3) {
            return CaseExpression()
        }

        val caseExpr = CaseExpression()

        val isNotNull = IsNullExpression()
        isNotNull.leftExpression = params[0]
        isNotNull.isNot = true

        val whenClause = WhenClause()
        whenClause.whenExpression = isNotNull
        whenClause.thenExpression = params[1]

        caseExpr.whenClauses = listOf(whenClause)
        caseExpr.elseExpression = params[2]

        appliedRules.add("NVL2() → CASE WHEN expr IS NOT NULL 변환")
        return caseExpr
    }

    /**
     * IF(condition, true_value, false_value) → CASE WHEN condition THEN ... ELSE ... END
     */
    private fun convertIfToCaseWhen(
        params: List<Expression>,
        appliedRules: MutableList<String>
    ): CaseExpression {
        if (params.size < 3) {
            return CaseExpression()
        }

        val caseExpr = CaseExpression()

        val whenClause = WhenClause()
        whenClause.whenExpression = params[0]
        whenClause.thenExpression = params[1]

        caseExpr.whenClauses = listOf(whenClause)
        caseExpr.elseExpression = params[2]

        appliedRules.add("IF() → CASE WHEN 변환")
        return caseExpr
    }

    /**
     * 첫 두 파라미터 교환 (INSTR ↔ LOCATE 등)
     */
    private fun swapFirstTwoParameters(function: SqlFunction) {
        val params = function.parameters?.expressions
        if (params != null && params.size >= 2) {
            val temp = params[0]
            (params as MutableList<Expression>)[0] = params[1]
            params[1] = temp
        }
    }

    /**
     * 날짜 포맷 변환
     */
    private fun convertDateFormat(
        function: SqlFunction,
        sourceDialect: DialectType,
        targetDialect: DialectType
    ) {
        val params = function.parameters?.expressions
        if (params != null && params.size >= 2) {
            // 날짜 포맷 문자열 변환 (두 번째 파라미터)
            val formatParam = params[1]
            if (formatParam is net.sf.jsqlparser.expression.StringValue) {
                val convertedFormat = convertDateFormatString(
                    formatParam.value,
                    sourceDialect,
                    targetDialect
                )
                (params as MutableList<Expression>)[1] =
                    net.sf.jsqlparser.expression.StringValue(convertedFormat)
            }
        }
    }

    /**
     * 날짜 포맷 문자열 변환
     */
    private fun convertDateFormatString(
        format: String,
        sourceDialect: DialectType,
        targetDialect: DialectType
    ): String {
        var result = format

        // Oracle/Tibero 포맷 → MySQL 포맷
        if ((sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.TIBERO) &&
            targetDialect == DialectType.MYSQL
        ) {
            result = result
                .replace("YYYY", "%Y")
                .replace("YY", "%y")
                .replace("MM", "%m")
                .replace("DD", "%d")
                .replace("HH24", "%H")
                .replace("HH", "%h")
                .replace("MI", "%i")
                .replace("SS", "%s")
                .replace("AM", "%p")
                .replace("PM", "%p")
        }

        // MySQL 포맷 → Oracle/Tibero 포맷
        if (sourceDialect == DialectType.MYSQL &&
            (targetDialect == DialectType.ORACLE || targetDialect == DialectType.TIBERO)
        ) {
            result = result
                .replace("%Y", "YYYY")
                .replace("%y", "YY")
                .replace("%m", "MM")
                .replace("%d", "DD")
                .replace("%H", "HH24")
                .replace("%h", "HH")
                .replace("%i", "MI")
                .replace("%s", "SS")
                .replace("%p", "AM")
        }

        // Oracle/Tibero 포맷 → PostgreSQL 포맷 (유사함)
        // PostgreSQL은 TO_CHAR에서 Oracle 스타일 포맷을 많이 지원

        return result
    }

    /**
     * 문자열 기반 함수 변환 (이미 파싱된 SQL 없이 문자열 직접 변환)
     */
    fun convertFunctionsInSql(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) {
            return sql
        }

        var result = sql
        val rules = mappingRegistry.getMappingsForDialects(sourceDialect, targetDialect)

        for (rule in rules) {
            // 함수명 교체 (단순 텍스트 교체 - 복잡한 케이스는 파서 사용 권장)
            val pattern = Regex("\\b${rule.sourceFunction}\\s*\\(", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(result)) {
                result = result.replace(pattern, "${rule.targetFunction}(")
                appliedRules.add("${rule.sourceFunction}() → ${rule.targetFunction}()")

                if (rule.warningType != null && rule.warningMessage != null) {
                    warnings.add(ConversionWarning(
                        type = rule.warningType,
                        message = rule.warningMessage,
                        severity = WarningSeverity.WARNING,
                        suggestion = rule.suggestion
                    ))
                }
            }
        }

        // 특수 변환: SYSDATE (파라미터 없는 함수)
        if (sourceDialect == DialectType.ORACLE || sourceDialect == DialectType.TIBERO) {
            when (targetDialect) {
                DialectType.MYSQL -> {
                    result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "NOW()")
                    result = result.replace(Regex("\\bSYSTIMESTAMP\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                }
                DialectType.POSTGRESQL -> {
                    result = result.replace(Regex("\\bSYSDATE\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                    result = result.replace(Regex("\\bSYSTIMESTAMP\\b", RegexOption.IGNORE_CASE), "CURRENT_TIMESTAMP")
                }
                else -> {}
            }
        }

        return result
    }
}

/**
 * SqlFunction clone 확장
 */
private fun SqlFunction.clone(): SqlFunction {
    val cloned = SqlFunction()
    cloned.name = this.name
    cloned.parameters = this.parameters
    cloned.isAllColumns = this.isAllColumns
    cloned.isDistinct = this.isDistinct
    return cloned
}