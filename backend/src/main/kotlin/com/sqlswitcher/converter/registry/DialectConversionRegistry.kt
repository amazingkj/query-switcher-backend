package com.sqlswitcher.converter.registry

import com.sqlswitcher.converter.DialectType

/**
 * Dialect 변환 레지스트리 유틸리티
 *
 * 소스-타겟 방언 조합별로 변환 로직을 등록하고 실행합니다.
 * 반복되는 when (sourceDialect) { when (targetDialect) } 패턴을 대체합니다.
 */

/**
 * 방언 조합 키
 */
data class DialectPair(
    val source: DialectType,
    val target: DialectType
) {
    companion object {
        /**
         * 모든 방언 조합 생성 (자기 자신 제외)
         */
        fun allPairs(): List<DialectPair> {
            return DialectType.entries.flatMap { source ->
                DialectType.entries.filter { it != source }.map { target ->
                    DialectPair(source, target)
                }
            }
        }

        /**
         * 특정 소스에서 모든 타겟으로의 조합
         */
        fun fromSource(source: DialectType): List<DialectPair> {
            return DialectType.entries.filter { it != source }.map { target ->
                DialectPair(source, target)
            }
        }

        /**
         * 모든 소스에서 특정 타겟으로의 조합
         */
        fun toTarget(target: DialectType): List<DialectPair> {
            return DialectType.entries.filter { it != target }.map { source ->
                DialectPair(source, target)
            }
        }
    }
}

/**
 * 문자열 치환 규칙
 */
data class ReplacementRule(
    val pattern: Regex,
    val replacement: String,
    val ruleName: String? = null
) {
    constructor(
        pattern: String,
        replacement: String,
        ruleName: String? = null,
        ignoreCase: Boolean = true
    ) : this(
        Regex(pattern, if (ignoreCase) RegexOption.IGNORE_CASE else RegexOption.LITERAL),
        replacement,
        ruleName
    )
}

/**
 * 동적 변환 규칙 (람다 사용)
 */
data class DynamicReplacementRule(
    val pattern: Regex,
    val transformer: (MatchResult) -> String,
    val ruleName: String? = null
) {
    constructor(
        pattern: String,
        transformer: (MatchResult) -> String,
        ruleName: String? = null,
        ignoreCase: Boolean = true
    ) : this(
        Regex(pattern, if (ignoreCase) RegexOption.IGNORE_CASE else RegexOption.LITERAL),
        transformer,
        ruleName
    )
}

/**
 * 방언별 변환 규칙 레지스트리
 */
class ConversionRuleRegistry<T> {
    private val rules: MutableMap<DialectPair, MutableList<T>> = mutableMapOf()

    /**
     * 규칙 등록
     */
    fun register(source: DialectType, target: DialectType, rule: T): ConversionRuleRegistry<T> {
        val pair = DialectPair(source, target)
        rules.getOrPut(pair) { mutableListOf() }.add(rule)
        return this
    }

    /**
     * 여러 규칙 일괄 등록
     */
    fun registerAll(source: DialectType, target: DialectType, ruleList: List<T>): ConversionRuleRegistry<T> {
        val pair = DialectPair(source, target)
        rules.getOrPut(pair) { mutableListOf() }.addAll(ruleList)
        return this
    }

    /**
     * 특정 소스에서 모든 타겟으로 동일 규칙 등록
     */
    fun registerFromSource(source: DialectType, rule: T): ConversionRuleRegistry<T> {
        DialectPair.fromSource(source).forEach { pair ->
            rules.getOrPut(pair) { mutableListOf() }.add(rule)
        }
        return this
    }

    /**
     * 규칙 조회
     */
    fun getRules(source: DialectType, target: DialectType): List<T> {
        return rules[DialectPair(source, target)] ?: emptyList()
    }

    /**
     * 규칙 존재 여부 확인
     */
    fun hasRules(source: DialectType, target: DialectType): Boolean {
        return rules.containsKey(DialectPair(source, target))
    }
}

/**
 * 문자열 치환 레지스트리 (간단한 패턴 매칭용)
 */
class StringReplacementRegistry {
    private val registry = ConversionRuleRegistry<ReplacementRule>()

    fun register(source: DialectType, target: DialectType, rule: ReplacementRule): StringReplacementRegistry {
        registry.register(source, target, rule)
        return this
    }

    fun registerAll(source: DialectType, target: DialectType, rules: List<ReplacementRule>): StringReplacementRegistry {
        registry.registerAll(source, target, rules)
        return this
    }

    /**
     * 치환 실행
     */
    fun apply(
        sql: String,
        source: DialectType,
        target: DialectType,
        appliedRules: MutableList<String>
    ): String {
        if (source == target) return sql

        val rules = registry.getRules(source, target)
        if (rules.isEmpty()) return sql

        var result = sql
        var hasChanges = false

        for (rule in rules) {
            val newResult = rule.pattern.replace(result, rule.replacement)
            if (newResult != result) {
                result = newResult
                hasChanges = true
                rule.ruleName?.let { appliedRules.add(it) }
            }
        }

        return result
    }
}

/**
 * 동적 치환 레지스트리 (람다 기반 변환용)
 */
class DynamicReplacementRegistry {
    private val registry = ConversionRuleRegistry<DynamicReplacementRule>()

    fun register(source: DialectType, target: DialectType, rule: DynamicReplacementRule): DynamicReplacementRegistry {
        registry.register(source, target, rule)
        return this
    }

    fun registerAll(source: DialectType, target: DialectType, rules: List<DynamicReplacementRule>): DynamicReplacementRegistry {
        registry.registerAll(source, target, rules)
        return this
    }

    /**
     * 치환 실행
     */
    fun apply(
        sql: String,
        source: DialectType,
        target: DialectType,
        appliedRules: MutableList<String>
    ): String {
        if (source == target) return sql

        val rules = registry.getRules(source, target)
        if (rules.isEmpty()) return sql

        var result = sql
        var hasChanges = false

        for (rule in rules) {
            val newResult = rule.pattern.replace(result, rule.transformer)
            if (newResult != result) {
                result = newResult
                hasChanges = true
                rule.ruleName?.let { appliedRules.add(it) }
            }
        }

        return result
    }
}

/**
 * 방언별 변환 함수 레지스트리 (복잡한 로직용)
 */
typealias ConversionFunction = (sql: String, appliedRules: MutableList<String>) -> String

class FunctionConversionRegistry {
    private val registry = ConversionRuleRegistry<ConversionFunction>()

    fun register(source: DialectType, target: DialectType, fn: ConversionFunction): FunctionConversionRegistry {
        registry.register(source, target, fn)
        return this
    }

    /**
     * 변환 실행
     */
    fun apply(
        sql: String,
        source: DialectType,
        target: DialectType,
        appliedRules: MutableList<String>
    ): String {
        if (source == target) return sql

        val functions = registry.getRules(source, target)
        if (functions.isEmpty()) return sql

        var result = sql
        for (fn in functions) {
            result = fn(result, appliedRules)
        }
        return result
    }
}