package com.sqlswitcher.converter.config

/**
 * 변환 규칙 설정
 *
 * 사용자가 특정 변환 규칙을 활성화/비활성화할 수 있도록 함
 */
data class ConversionRuleConfig(
    // 데이터타입 변환 규칙
    val dataTypeConversion: DataTypeRules = DataTypeRules(),

    // 함수 변환 규칙
    val functionConversion: FunctionRules = FunctionRules(),

    // DDL 변환 규칙
    val ddlConversion: DdlRules = DdlRules(),

    // 구문 변환 규칙
    val syntaxConversion: SyntaxRules = SyntaxRules(),

    // 경고 설정
    val warningSettings: WarningSettings = WarningSettings()
) {
    companion object {
        /**
         * 기본 설정 (모든 규칙 활성화)
         */
        fun default() = ConversionRuleConfig()

        /**
         * 최소 변환 설정 (필수 규칙만)
         */
        fun minimal() = ConversionRuleConfig(
            dataTypeConversion = DataTypeRules(
                convertVarchar2 = true,
                convertNumber = true,
                convertClob = true,
                convertBlob = true,
                convertDate = false,
                convertBoolean = false
            ),
            functionConversion = FunctionRules(
                convertNvl = true,
                convertDecode = false,
                convertDateFunctions = false,
                convertStringFunctions = false,
                convertAnalyticFunctions = false
            ),
            ddlConversion = DdlRules(
                removeTablespace = true,
                removeStorageClause = true,
                removeSchemaPrefix = true,
                convertConstraints = false,
                convertIndexes = false
            )
        )

        /**
         * 엄격한 변환 설정 (모든 규칙 + 추가 검증)
         */
        fun strict() = ConversionRuleConfig(
            warningSettings = WarningSettings(
                warnOnSelectStar = true,
                warnOnLargeInClause = true,
                warnOnMissingIndex = true,
                warnOnDeprecatedSyntax = true,
                maxInClauseSize = 50
            )
        )
    }
}

/**
 * 데이터타입 변환 규칙
 */
data class DataTypeRules(
    /** VARCHAR2 → VARCHAR/TEXT 변환 */
    val convertVarchar2: Boolean = true,

    /** NUMBER → INT/DECIMAL/BIGINT 변환 */
    val convertNumber: Boolean = true,

    /** CLOB → TEXT/LONGTEXT 변환 */
    val convertClob: Boolean = true,

    /** BLOB → BYTEA/LONGBLOB 변환 */
    val convertBlob: Boolean = true,

    /** DATE → TIMESTAMP/DATETIME 변환 */
    val convertDate: Boolean = true,

    /** BOOLEAN 타입 변환 */
    val convertBoolean: Boolean = true,

    /** RAW → BINARY 변환 */
    val convertRaw: Boolean = true,

    /** FLOAT 정밀도 변환 */
    val convertFloat: Boolean = true,

    /** BYTE/CHAR 길이 지정자 제거 */
    val removeByteSuffix: Boolean = true
)

/**
 * 함수 변환 규칙
 */
data class FunctionRules(
    /** NVL → COALESCE/IFNULL 변환 */
    val convertNvl: Boolean = true,

    /** NVL2 → CASE WHEN 변환 */
    val convertNvl2: Boolean = true,

    /** DECODE → CASE WHEN 변환 */
    val convertDecode: Boolean = true,

    /** 날짜 함수 변환 (SYSDATE, TO_DATE, TO_CHAR 등) */
    val convertDateFunctions: Boolean = true,

    /** 문자열 함수 변환 (SUBSTR, INSTR 등) */
    val convertStringFunctions: Boolean = true,

    /** 집계 함수 변환 (LISTAGG 등) */
    val convertAggregateFunctions: Boolean = true,

    /** 윈도우/분석 함수 변환 */
    val convertAnalyticFunctions: Boolean = true,

    /** ROWNUM → LIMIT/ROW_NUMBER 변환 */
    val convertRownum: Boolean = true,

    /** DUAL 테이블 처리 */
    val handleDualTable: Boolean = true,

    /** 시퀀스 변환 (NEXTVAL, CURRVAL) */
    val convertSequences: Boolean = true
)

/**
 * DDL 변환 규칙
 */
data class DdlRules(
    /** TABLESPACE 절 제거 */
    val removeTablespace: Boolean = true,

    /** STORAGE 절 제거 */
    val removeStorageClause: Boolean = true,

    /** PCTFREE/INITRANS 등 제거 */
    val removePhysicalAttributes: Boolean = true,

    /** 스키마 접두사 제거 ("OWNER"."TABLE" → TABLE) */
    val removeSchemaPrefix: Boolean = true,

    /** COMMENT ON 변환/제거 */
    val convertComments: Boolean = true,

    /** 제약조건 변환 */
    val convertConstraints: Boolean = true,

    /** 인덱스 변환 */
    val convertIndexes: Boolean = true,

    /** 시퀀스 변환 */
    val convertSequences: Boolean = true,

    /** 트리거 변환 시도 */
    val convertTriggers: Boolean = false,

    /** 뷰 변환 */
    val convertViews: Boolean = true,

    /** 파티션 변환 시도 */
    val convertPartitions: Boolean = false
)

/**
 * 구문 변환 규칙
 */
data class SyntaxRules(
    /** Oracle 조인 (+) → ANSI JOIN */
    val convertOracleJoin: Boolean = true,

    /** CONNECT BY → WITH RECURSIVE */
    val convertHierarchicalQuery: Boolean = true,

    /** PIVOT/UNPIVOT 변환 */
    val convertPivot: Boolean = true,

    /** MERGE INTO 변환 */
    val convertMerge: Boolean = true,

    /** Oracle 힌트 처리 */
    val handleOracleHints: Boolean = true,

    /** :: 캐스팅 변환 (PostgreSQL) */
    val convertCasting: Boolean = true,

    /** RETURNING 절 변환 */
    val convertReturning: Boolean = true
)

/**
 * 경고 설정
 */
data class WarningSettings(
    /** SELECT * 사용 시 경고 */
    val warnOnSelectStar: Boolean = true,

    /** 대형 IN 절 경고 */
    val warnOnLargeInClause: Boolean = true,

    /** IN 절 최대 크기 */
    val maxInClauseSize: Int = 100,

    /** 인덱스 힌트 없음 경고 (성능) */
    val warnOnMissingIndex: Boolean = false,

    /** 미지원 구문 경고 */
    val warnOnUnsupportedSyntax: Boolean = true,

    /** 부분 변환 경고 */
    val warnOnPartialConversion: Boolean = true,

    /** deprecated 구문 경고 */
    val warnOnDeprecatedSyntax: Boolean = false,

    /** 데이터 손실 가능성 경고 */
    val warnOnPotentialDataLoss: Boolean = true
)
