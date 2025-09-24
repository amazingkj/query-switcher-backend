package com.sqlswitcher.converter

import org.springframework.stereotype.Component

/**
 * 데이터베이스 간 데이터 타입 변환을 위한 인터페이스
 */
interface DataTypeConverter {
    
    /**
     * 데이터 타입 변환
     * @param sourceDialect 소스 방언
     * @param targetDialect 타겟 방언
     * @param dataType 변환할 데이터 타입
     * @return 변환된 데이터 타입
     */
    fun convertDataType(
        sourceDialect: DialectType,
        targetDialect: DialectType,
        dataType: String
    ): DataTypeConversionResult
}

/**
 * 데이터 타입 변환 결과
 */
data class DataTypeConversionResult(
    val convertedType: String,
    val warnings: List<ConversionWarning> = emptyList(),
    val appliedRules: List<String> = emptyList()
)

/**
 * 데이터 타입 매핑 규칙
 */
data class DataTypeMappingRule(
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val sourceType: String,
    val targetType: String,
    val warningType: WarningType? = null,
    val warningMessage: String? = null,
    val suggestion: String? = null,
    val isPartialSupport: Boolean = false
)

/**
 * 데이터 타입 변환기 구현체
 */
@Component
class DataTypeConverterImpl : DataTypeConverter {
    
    private val typeMappings = initializeTypeMappings()
    
    override fun convertDataType(
        sourceDialect: DialectType,
        targetDialect: DialectType,
        dataType: String
    ): DataTypeConversionResult {
        val normalizedType = normalizeDataType(dataType)
        val mappingRule = findMappingRule(sourceDialect, targetDialect, normalizedType)
        
        if (mappingRule == null) {
            // 매핑 규칙이 없는 경우 원본 타입 반환
            return DataTypeConversionResult(
                convertedType = dataType,
                warnings = listOf(
                    ConversionWarning(
                        type = WarningType.UNSUPPORTED_FUNCTION,
                        message = "데이터 타입 '$dataType'에 대한 변환 규칙이 없습니다.",
                        severity = WarningSeverity.WARNING,
                        suggestion = "수동으로 변환하거나 대체 타입을 사용하세요."
                    )
                )
            )
        }
        
        val warnings = mutableListOf<ConversionWarning>()
        val appliedRules = mutableListOf<String>()
        
        // 경고 추가
        if (mappingRule.warningType != null && mappingRule.warningMessage != null) {
            warnings.add(
                ConversionWarning(
                    type = mappingRule.warningType,
                    message = mappingRule.warningMessage,
                    severity = if (mappingRule.isPartialSupport) WarningSeverity.WARNING else WarningSeverity.INFO,
                    suggestion = mappingRule.suggestion
                )
            )
        }
        
        appliedRules.add("${mappingRule.sourceType} → ${mappingRule.targetType}")
        
        return DataTypeConversionResult(
            convertedType = mappingRule.targetType,
            warnings = warnings,
            appliedRules = appliedRules
        )
    }
    
    /**
     * 데이터 타입 정규화
     */
    private fun normalizeDataType(dataType: String): String {
        return dataType.trim().uppercase()
            .replace("\\s+".toRegex(), " ")
            .replace("\\(", "(")
            .replace("\\)", ")")
    }
    
    /**
     * 매핑 규칙 찾기
     */
    private fun findMappingRule(
        sourceDialect: DialectType,
        targetDialect: DialectType,
        dataType: String
    ): DataTypeMappingRule? {
        return typeMappings.find { rule ->
            rule.sourceDialect == sourceDialect &&
            rule.targetDialect == targetDialect &&
            rule.sourceType.equals(dataType, ignoreCase = true)
        }
    }
    
    /**
     * 타입 매핑 규칙 초기화
     */
    private fun initializeTypeMappings(): List<DataTypeMappingRule> {
        return listOf(
            // MySQL → PostgreSQL 매핑
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TINYINT", "SMALLINT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "MEDIUMINT", "INTEGER"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LONGTEXT", "TEXT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TINYTEXT", "TEXT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "MEDIUMTEXT", "TEXT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "LONGBLOB", "BYTEA"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "TINYBLOB", "BYTEA"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "MEDIUMBLOB", "BYTEA"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "DATETIME", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "BOOLEAN", "BOOLEAN"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "BOOL", "BOOLEAN"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.POSTGRESQL, "JSON", "JSONB"),
            
            // MySQL → Oracle 매핑
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TINYINT", "NUMBER(3)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "SMALLINT", "NUMBER(5)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "MEDIUMINT", "NUMBER(7)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "INT", "NUMBER(10)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "INTEGER", "NUMBER(10)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "BIGINT", "NUMBER(19)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "FLOAT", "BINARY_FLOAT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "DOUBLE", "BINARY_DOUBLE"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "DECIMAL", "NUMBER"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "NUMERIC", "NUMBER"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "VARCHAR", "VARCHAR2"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TEXT", "CLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "LONGTEXT", "CLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "MEDIUMTEXT", "CLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TINYTEXT", "CLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "BLOB", "BLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "LONGBLOB", "BLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "MEDIUMBLOB", "BLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TINYBLOB", "BLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "DATETIME", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "DATE", "DATE"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "TIME", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "BOOLEAN", "NUMBER(1)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "BOOL", "NUMBER(1)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.ORACLE, "JSON", "CLOB"),
            
            // MySQL → Tibero 매핑 (Oracle과 동일)
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "TINYINT", "NUMBER(3)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "SMALLINT", "NUMBER(5)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "MEDIUMINT", "NUMBER(7)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "INT", "NUMBER(10)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "INTEGER", "NUMBER(10)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "BIGINT", "NUMBER(19)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "FLOAT", "BINARY_FLOAT"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "DOUBLE", "BINARY_DOUBLE"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "DECIMAL", "NUMBER"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "NUMERIC", "NUMBER"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "VARCHAR", "VARCHAR2"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "TEXT", "CLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "LONGTEXT", "CLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "MEDIUMTEXT", "CLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "TINYTEXT", "CLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "BLOB", "BLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "LONGBLOB", "BLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "MEDIUMBLOB", "BLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "TINYBLOB", "BLOB"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "DATETIME", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "DATE", "DATE"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "TIME", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "BOOLEAN", "NUMBER(1)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "BOOL", "NUMBER(1)"),
            DataTypeMappingRule(DialectType.MYSQL, DialectType.TIBERO, "JSON", "CLOB"),
            
            // PostgreSQL → MySQL 매핑
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "SERIAL", "AUTO_INCREMENT"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "BIGSERIAL", "BIGINT AUTO_INCREMENT"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "TEXT", "LONGTEXT"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "BYTEA", "LONGBLOB"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "BOOLEAN", "BOOLEAN"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "JSONB", "JSON"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "UUID", "CHAR(36)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.MYSQL, "ARRAY", "TEXT", 
                warningType = WarningType.PARTIAL_SUPPORT,
                warningMessage = "PostgreSQL ARRAY 타입을 MySQL TEXT로 변환했습니다.",
                suggestion = "배열 기능이 제한될 수 있습니다.",
                isPartialSupport = true),
            
            // PostgreSQL → Oracle 매핑
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "SERIAL", "NUMBER GENERATED BY DEFAULT AS IDENTITY"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "BIGSERIAL", "NUMBER GENERATED BY DEFAULT AS IDENTITY"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "TEXT", "CLOB"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "BYTEA", "BLOB"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "BOOLEAN", "NUMBER(1)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "JSONB", "CLOB"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "UUID", "RAW(16)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.ORACLE, "ARRAY", "CLOB",
                warningType = WarningType.PARTIAL_SUPPORT,
                warningMessage = "PostgreSQL ARRAY 타입을 Oracle CLOB로 변환했습니다.",
                suggestion = "배열 기능이 제한될 수 있습니다.",
                isPartialSupport = true),
            
            // PostgreSQL → Tibero 매핑 (Oracle과 동일)
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.TIBERO, "SERIAL", "NUMBER GENERATED BY DEFAULT AS IDENTITY"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.TIBERO, "BIGSERIAL", "NUMBER GENERATED BY DEFAULT AS IDENTITY"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.TIBERO, "TEXT", "CLOB"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.TIBERO, "BYTEA", "BLOB"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.TIBERO, "BOOLEAN", "NUMBER(1)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.TIBERO, "JSONB", "CLOB"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.TIBERO, "UUID", "RAW(16)"),
            DataTypeMappingRule(DialectType.POSTGRESQL, DialectType.TIBERO, "ARRAY", "CLOB",
                warningType = WarningType.PARTIAL_SUPPORT,
                warningMessage = "PostgreSQL ARRAY 타입을 Tibero CLOB로 변환했습니다.",
                suggestion = "배열 기능이 제한될 수 있습니다.",
                isPartialSupport = true),
            
            // Oracle → MySQL 매핑
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "NUMBER", "DECIMAL"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "VARCHAR2", "VARCHAR"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "CLOB", "LONGTEXT"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "BLOB", "LONGBLOB"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "RAW", "VARBINARY"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "LONG RAW", "LONGBLOB"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TIMESTAMP", "DATETIME"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TIMESTAMP WITH TIME ZONE", "DATETIME"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "TIMESTAMP WITH LOCAL TIME ZONE", "DATETIME"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "INTERVAL YEAR TO MONTH", "VARCHAR(20)"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "INTERVAL DAY TO SECOND", "VARCHAR(30)"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "ROWID", "VARCHAR(18)"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "UROWID", "VARCHAR(4000)"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.MYSQL, "XMLType", "LONGTEXT"),
            
            // Oracle → PostgreSQL 매핑
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "NUMBER", "NUMERIC"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "VARCHAR2", "VARCHAR"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "CLOB", "TEXT"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "BLOB", "BYTEA"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "RAW", "BYTEA"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "LONG RAW", "BYTEA"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TIMESTAMP", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TIMESTAMP WITH TIME ZONE", "TIMESTAMPTZ"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "TIMESTAMP WITH LOCAL TIME ZONE", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "INTERVAL YEAR TO MONTH", "INTERVAL"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "INTERVAL DAY TO SECOND", "INTERVAL"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "ROWID", "VARCHAR(18)"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "UROWID", "VARCHAR(4000)"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.POSTGRESQL, "XMLType", "XML"),
            
            // Oracle → Tibero 매핑 (대부분 동일)
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "NUMBER", "NUMBER"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "VARCHAR2", "VARCHAR2"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "CLOB", "CLOB"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "BLOB", "BLOB"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "RAW", "RAW"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "LONG RAW", "LONG RAW"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "TIMESTAMP", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "TIMESTAMP WITH TIME ZONE", "TIMESTAMP WITH TIME ZONE"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "TIMESTAMP WITH LOCAL TIME ZONE", "TIMESTAMP WITH LOCAL TIME ZONE"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "INTERVAL YEAR TO MONTH", "INTERVAL YEAR TO MONTH"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "INTERVAL DAY TO SECOND", "INTERVAL DAY TO SECOND"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "ROWID", "ROWID"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "UROWID", "UROWID"),
            DataTypeMappingRule(DialectType.ORACLE, DialectType.TIBERO, "XMLType", "XMLType"),
            
            // Tibero → MySQL 매핑 (Oracle과 동일)
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "NUMBER", "DECIMAL"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "VARCHAR2", "VARCHAR"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "CLOB", "LONGTEXT"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "BLOB", "LONGBLOB"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "RAW", "VARBINARY"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "LONG RAW", "LONGBLOB"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "TIMESTAMP", "DATETIME"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "TIMESTAMP WITH TIME ZONE", "DATETIME"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "TIMESTAMP WITH LOCAL TIME ZONE", "DATETIME"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "INTERVAL YEAR TO MONTH", "VARCHAR(20)"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "INTERVAL DAY TO SECOND", "VARCHAR(30)"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "ROWID", "VARCHAR(18)"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "UROWID", "VARCHAR(4000)"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.MYSQL, "XMLType", "LONGTEXT"),
            
            // Tibero → PostgreSQL 매핑 (Oracle과 동일)
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "NUMBER", "NUMERIC"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "VARCHAR2", "VARCHAR"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "CLOB", "TEXT"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "BLOB", "BYTEA"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "RAW", "BYTEA"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "LONG RAW", "BYTEA"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "TIMESTAMP", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "TIMESTAMP WITH TIME ZONE", "TIMESTAMPTZ"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "TIMESTAMP WITH LOCAL TIME ZONE", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "INTERVAL YEAR TO MONTH", "INTERVAL"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "INTERVAL DAY TO SECOND", "INTERVAL"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "ROWID", "VARCHAR(18)"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "UROWID", "VARCHAR(4000)"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.POSTGRESQL, "XMLType", "XML"),
            
            // Tibero → Oracle 매핑 (대부분 동일)
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "NUMBER", "NUMBER"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "VARCHAR2", "VARCHAR2"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "CLOB", "CLOB"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "BLOB", "BLOB"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "RAW", "RAW"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "LONG RAW", "LONG RAW"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "TIMESTAMP", "TIMESTAMP"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "TIMESTAMP WITH TIME ZONE", "TIMESTAMP WITH TIME ZONE"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "TIMESTAMP WITH LOCAL TIME ZONE", "TIMESTAMP WITH LOCAL TIME ZONE"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "INTERVAL YEAR TO MONTH", "INTERVAL YEAR TO MONTH"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "INTERVAL DAY TO SECOND", "INTERVAL DAY TO SECOND"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "ROWID", "ROWID"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "UROWID", "UROWID"),
            DataTypeMappingRule(DialectType.TIBERO, DialectType.ORACLE, "XMLType", "XMLType")
        )
    }
}
