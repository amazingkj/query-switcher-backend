package com.sqlswitcher.converter.config

import org.springframework.stereotype.Service

/**
 * 변환 규칙 관리 서비스
 *
 * 사용자/세션별 변환 규칙을 관리하고,
 * 규칙 활성화 여부를 확인하는 기능 제공
 */
@Service
class ConversionRuleService {

    // 기본 규칙 설정
    private var defaultConfig = ConversionRuleConfig.default()

    // 세션별 규칙 저장 (실제 환경에서는 Redis 등 사용)
    private val sessionConfigs = mutableMapOf<String, ConversionRuleConfig>()

    /**
     * 기본 규칙 설정 조회
     */
    fun getDefaultConfig(): ConversionRuleConfig = defaultConfig

    /**
     * 기본 규칙 설정 변경
     */
    fun setDefaultConfig(config: ConversionRuleConfig) {
        defaultConfig = config
    }

    /**
     * 세션별 규칙 조회
     */
    fun getConfigForSession(sessionId: String): ConversionRuleConfig {
        return sessionConfigs[sessionId] ?: defaultConfig
    }

    /**
     * 세션별 규칙 설정
     */
    fun setConfigForSession(sessionId: String, config: ConversionRuleConfig) {
        sessionConfigs[sessionId] = config
    }

    /**
     * 세션 규칙 삭제
     */
    fun clearSessionConfig(sessionId: String) {
        sessionConfigs.remove(sessionId)
    }

    // ============ 규칙 활성화 여부 확인 메서드들 ============

    /**
     * 데이터타입 변환 규칙 확인
     */
    fun isDataTypeConversionEnabled(config: ConversionRuleConfig, rule: DataTypeRule): Boolean {
        return when (rule) {
            DataTypeRule.VARCHAR2 -> config.dataTypeConversion.convertVarchar2
            DataTypeRule.NUMBER -> config.dataTypeConversion.convertNumber
            DataTypeRule.CLOB -> config.dataTypeConversion.convertClob
            DataTypeRule.BLOB -> config.dataTypeConversion.convertBlob
            DataTypeRule.DATE -> config.dataTypeConversion.convertDate
            DataTypeRule.BOOLEAN -> config.dataTypeConversion.convertBoolean
            DataTypeRule.RAW -> config.dataTypeConversion.convertRaw
            DataTypeRule.FLOAT -> config.dataTypeConversion.convertFloat
            DataTypeRule.BYTE_SUFFIX -> config.dataTypeConversion.removeByteSuffix
        }
    }

    /**
     * 함수 변환 규칙 확인
     */
    fun isFunctionConversionEnabled(config: ConversionRuleConfig, rule: FunctionRule): Boolean {
        return when (rule) {
            FunctionRule.NVL -> config.functionConversion.convertNvl
            FunctionRule.NVL2 -> config.functionConversion.convertNvl2
            FunctionRule.DECODE -> config.functionConversion.convertDecode
            FunctionRule.DATE_FUNCTIONS -> config.functionConversion.convertDateFunctions
            FunctionRule.STRING_FUNCTIONS -> config.functionConversion.convertStringFunctions
            FunctionRule.AGGREGATE_FUNCTIONS -> config.functionConversion.convertAggregateFunctions
            FunctionRule.ANALYTIC_FUNCTIONS -> config.functionConversion.convertAnalyticFunctions
            FunctionRule.ROWNUM -> config.functionConversion.convertRownum
            FunctionRule.DUAL_TABLE -> config.functionConversion.handleDualTable
            FunctionRule.SEQUENCES -> config.functionConversion.convertSequences
        }
    }

    /**
     * DDL 변환 규칙 확인
     */
    fun isDdlConversionEnabled(config: ConversionRuleConfig, rule: DdlRule): Boolean {
        return when (rule) {
            DdlRule.TABLESPACE -> config.ddlConversion.removeTablespace
            DdlRule.STORAGE -> config.ddlConversion.removeStorageClause
            DdlRule.PHYSICAL_ATTRIBUTES -> config.ddlConversion.removePhysicalAttributes
            DdlRule.SCHEMA_PREFIX -> config.ddlConversion.removeSchemaPrefix
            DdlRule.COMMENTS -> config.ddlConversion.convertComments
            DdlRule.CONSTRAINTS -> config.ddlConversion.convertConstraints
            DdlRule.INDEXES -> config.ddlConversion.convertIndexes
            DdlRule.SEQUENCES -> config.ddlConversion.convertSequences
            DdlRule.TRIGGERS -> config.ddlConversion.convertTriggers
            DdlRule.VIEWS -> config.ddlConversion.convertViews
            DdlRule.PARTITIONS -> config.ddlConversion.convertPartitions
        }
    }

    /**
     * 구문 변환 규칙 확인
     */
    fun isSyntaxConversionEnabled(config: ConversionRuleConfig, rule: SyntaxRule): Boolean {
        return when (rule) {
            SyntaxRule.ORACLE_JOIN -> config.syntaxConversion.convertOracleJoin
            SyntaxRule.HIERARCHICAL_QUERY -> config.syntaxConversion.convertHierarchicalQuery
            SyntaxRule.PIVOT -> config.syntaxConversion.convertPivot
            SyntaxRule.MERGE -> config.syntaxConversion.convertMerge
            SyntaxRule.ORACLE_HINTS -> config.syntaxConversion.handleOracleHints
            SyntaxRule.CASTING -> config.syntaxConversion.convertCasting
            SyntaxRule.RETURNING -> config.syntaxConversion.convertReturning
        }
    }

    /**
     * 경고 활성화 여부 확인
     */
    fun isWarningEnabled(config: ConversionRuleConfig, warning: WarningRule): Boolean {
        return when (warning) {
            WarningRule.SELECT_STAR -> config.warningSettings.warnOnSelectStar
            WarningRule.LARGE_IN_CLAUSE -> config.warningSettings.warnOnLargeInClause
            WarningRule.MISSING_INDEX -> config.warningSettings.warnOnMissingIndex
            WarningRule.UNSUPPORTED_SYNTAX -> config.warningSettings.warnOnUnsupportedSyntax
            WarningRule.PARTIAL_CONVERSION -> config.warningSettings.warnOnPartialConversion
            WarningRule.DEPRECATED_SYNTAX -> config.warningSettings.warnOnDeprecatedSyntax
            WarningRule.POTENTIAL_DATA_LOSS -> config.warningSettings.warnOnPotentialDataLoss
        }
    }

    /**
     * 규칙 설정 빌더
     */
    fun buildConfig(block: ConversionRuleConfigBuilder.() -> Unit): ConversionRuleConfig {
        return ConversionRuleConfigBuilder().apply(block).build()
    }
}

/**
 * 데이터타입 변환 규칙 열거형
 */
enum class DataTypeRule {
    VARCHAR2, NUMBER, CLOB, BLOB, DATE, BOOLEAN, RAW, FLOAT, BYTE_SUFFIX
}

/**
 * 함수 변환 규칙 열거형
 */
enum class FunctionRule {
    NVL, NVL2, DECODE, DATE_FUNCTIONS, STRING_FUNCTIONS,
    AGGREGATE_FUNCTIONS, ANALYTIC_FUNCTIONS, ROWNUM, DUAL_TABLE, SEQUENCES
}

/**
 * DDL 변환 규칙 열거형
 */
enum class DdlRule {
    TABLESPACE, STORAGE, PHYSICAL_ATTRIBUTES, SCHEMA_PREFIX,
    COMMENTS, CONSTRAINTS, INDEXES, SEQUENCES, TRIGGERS, VIEWS, PARTITIONS
}

/**
 * 구문 변환 규칙 열거형
 */
enum class SyntaxRule {
    ORACLE_JOIN, HIERARCHICAL_QUERY, PIVOT, MERGE, ORACLE_HINTS, CASTING, RETURNING
}

/**
 * 경고 규칙 열거형
 */
enum class WarningRule {
    SELECT_STAR, LARGE_IN_CLAUSE, MISSING_INDEX, UNSUPPORTED_SYNTAX,
    PARTIAL_CONVERSION, DEPRECATED_SYNTAX, POTENTIAL_DATA_LOSS
}

/**
 * 규칙 설정 빌더
 */
class ConversionRuleConfigBuilder {
    private var dataTypeRules = DataTypeRules()
    private var functionRules = FunctionRules()
    private var ddlRules = DdlRules()
    private var syntaxRules = SyntaxRules()
    private var warningSettings = WarningSettings()

    fun dataTypes(block: DataTypeRulesBuilder.() -> Unit) {
        dataTypeRules = DataTypeRulesBuilder(dataTypeRules).apply(block).build()
    }

    fun functions(block: FunctionRulesBuilder.() -> Unit) {
        functionRules = FunctionRulesBuilder(functionRules).apply(block).build()
    }

    fun ddl(block: DdlRulesBuilder.() -> Unit) {
        ddlRules = DdlRulesBuilder(ddlRules).apply(block).build()
    }

    fun syntax(block: SyntaxRulesBuilder.() -> Unit) {
        syntaxRules = SyntaxRulesBuilder(syntaxRules).apply(block).build()
    }

    fun warnings(block: WarningSettingsBuilder.() -> Unit) {
        warningSettings = WarningSettingsBuilder(warningSettings).apply(block).build()
    }

    fun build() = ConversionRuleConfig(dataTypeRules, functionRules, ddlRules, syntaxRules, warningSettings)
}

class DataTypeRulesBuilder(private var rules: DataTypeRules) {
    var convertVarchar2: Boolean = rules.convertVarchar2
    var convertNumber: Boolean = rules.convertNumber
    var convertClob: Boolean = rules.convertClob
    var convertBlob: Boolean = rules.convertBlob
    var convertDate: Boolean = rules.convertDate
    var convertBoolean: Boolean = rules.convertBoolean

    fun build() = rules.copy(
        convertVarchar2 = convertVarchar2,
        convertNumber = convertNumber,
        convertClob = convertClob,
        convertBlob = convertBlob,
        convertDate = convertDate,
        convertBoolean = convertBoolean
    )
}

class FunctionRulesBuilder(private var rules: FunctionRules) {
    var convertNvl: Boolean = rules.convertNvl
    var convertDecode: Boolean = rules.convertDecode
    var convertDateFunctions: Boolean = rules.convertDateFunctions
    var convertAnalyticFunctions: Boolean = rules.convertAnalyticFunctions

    fun build() = rules.copy(
        convertNvl = convertNvl,
        convertDecode = convertDecode,
        convertDateFunctions = convertDateFunctions,
        convertAnalyticFunctions = convertAnalyticFunctions
    )
}

class DdlRulesBuilder(private var rules: DdlRules) {
    var removeTablespace: Boolean = rules.removeTablespace
    var removeStorageClause: Boolean = rules.removeStorageClause
    var convertConstraints: Boolean = rules.convertConstraints
    var convertIndexes: Boolean = rules.convertIndexes
    var convertTriggers: Boolean = rules.convertTriggers

    fun build() = rules.copy(
        removeTablespace = removeTablespace,
        removeStorageClause = removeStorageClause,
        convertConstraints = convertConstraints,
        convertIndexes = convertIndexes,
        convertTriggers = convertTriggers
    )
}

class SyntaxRulesBuilder(private var rules: SyntaxRules) {
    var convertOracleJoin: Boolean = rules.convertOracleJoin
    var convertHierarchicalQuery: Boolean = rules.convertHierarchicalQuery
    var convertPivot: Boolean = rules.convertPivot
    var convertMerge: Boolean = rules.convertMerge
    var handleOracleHints: Boolean = rules.handleOracleHints

    fun build() = rules.copy(
        convertOracleJoin = convertOracleJoin,
        convertHierarchicalQuery = convertHierarchicalQuery,
        convertPivot = convertPivot,
        convertMerge = convertMerge,
        handleOracleHints = handleOracleHints
    )
}

class WarningSettingsBuilder(private var settings: WarningSettings) {
    var warnOnSelectStar: Boolean = settings.warnOnSelectStar
    var warnOnLargeInClause: Boolean = settings.warnOnLargeInClause
    var maxInClauseSize: Int = settings.maxInClauseSize

    fun build() = settings.copy(
        warnOnSelectStar = warnOnSelectStar,
        warnOnLargeInClause = warnOnLargeInClause,
        maxInClauseSize = maxInClauseSize
    )
}
