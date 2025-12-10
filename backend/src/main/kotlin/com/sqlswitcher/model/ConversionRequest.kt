package com.sqlswitcher.model

import com.sqlswitcher.converter.DialectType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ConversionRequest(
    @field:NotBlank(message = "SQL cannot be blank")
    @field:Size(max = 1000000, message = "SQL cannot exceed 1,000,000 characters")
    val sql: String,
    
    @field:NotNull(message = "Source dialect is required")
    val sourceDialect: DialectType,
    
    @field:NotNull(message = "Target dialect is required")
    val targetDialect: DialectType,
    
    val options: ConversionOptions? = null
)

data class ConversionOptions(
    val strictMode: Boolean = false,
    val enableComments: Boolean = true,
    val formatSql: Boolean = false,
    val replaceUnsupportedFunctions: Boolean = false,

    // Oracle DDL 변환 옵션
    val oracleSchemaOwner: String? = null,        // 스키마/오너명
    val oracleTablespace: String? = null,         // 테이블 TABLESPACE
    val oracleIndexspace: String? = null,         // 인덱스 TABLESPACE
    val separatePrimaryKey: Boolean = true,       // PRIMARY KEY를 별도 ALTER TABLE로 분리
    val separateComments: Boolean = true,         // COMMENT를 별도 문으로 분리
    val generateIndex: Boolean = true             // UNIQUE INDEX 생성 포함
)
