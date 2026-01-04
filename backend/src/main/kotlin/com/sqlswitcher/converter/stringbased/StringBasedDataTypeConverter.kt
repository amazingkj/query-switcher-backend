package com.sqlswitcher.converter.stringbased

import com.sqlswitcher.converter.DialectType
import org.springframework.stereotype.Component

/**
 * 문자열 기반 데이터타입 변환기
 *
 * AST 파싱이 불가능한 경우 정규식을 사용하여 데이터타입을 변환합니다.
 */
@Component
class StringBasedDataTypeConverter {

    /**
     * 데이터타입 변환 수행
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) return sql

        var result = sql

        // Oracle 공통 전처리
        if (sourceDialect == DialectType.ORACLE && targetDialect != DialectType.ORACLE) {
            result = removeOracleByteCharKeyword(result, appliedRules)
        }

        result = when (sourceDialect) {
            DialectType.ORACLE -> convertFromOracle(result, targetDialect, appliedRules)
            DialectType.MYSQL -> convertFromMySql(result, targetDialect, appliedRules)
            DialectType.POSTGRESQL -> convertFromPostgreSql(result, targetDialect, appliedRules)
        }

        return result
    }

    /**
     * Oracle BYTE/CHAR 키워드 제거
     */
    private fun removeOracleByteCharKeyword(sql: String, appliedRules: MutableList<String>): String {
        var result = sql
        result = result.replace(Regex("\\(\\s*(\\d+)\\s+BYTE\\s*\\)", RegexOption.IGNORE_CASE), "($1)")
        result = result.replace(Regex("\\(\\s*(\\d+)\\s+CHAR\\s*\\)", RegexOption.IGNORE_CASE), "($1)")
        if (result != sql) {
            appliedRules.add("Oracle BYTE/CHAR 키워드 제거")
        }
        return result
    }

    /**
     * Oracle → 타겟 데이터타입 변환
     */
    private fun convertFromOracle(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        val replacements = when (targetDialect) {
            DialectType.MYSQL -> oracleToMySqlReplacements
            DialectType.POSTGRESQL -> oracleToPostgreSqlReplacements
            else -> return sql
        }

        return applyReplacements(sql, replacements, "Oracle → ${targetDialect.name} 데이터타입 변환", appliedRules)
    }

    /**
     * MySQL → 타겟 데이터타입 변환
     */
    private fun convertFromMySql(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        val replacements = when (targetDialect) {
            DialectType.POSTGRESQL -> mySqlToPostgreSqlReplacements
            DialectType.ORACLE -> mySqlToOracleReplacements
            else -> return sql
        }

        return applyReplacements(sql, replacements, "MySQL → ${targetDialect.name} 데이터타입 변환", appliedRules)
    }

    /**
     * PostgreSQL → 타겟 데이터타입 변환
     */
    private fun convertFromPostgreSql(
        sql: String,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        val replacements = when (targetDialect) {
            DialectType.MYSQL -> postgreSqlToMySqlReplacements
            DialectType.ORACLE -> postgreSqlToOracleReplacements
            else -> return sql
        }

        return applyReplacements(sql, replacements, "PostgreSQL → ${targetDialect.name} 데이터타입 변환", appliedRules)
    }

    /**
     * 치환 규칙 적용
     */
    private fun applyReplacements(
        sql: String,
        replacements: List<DataTypeReplacement>,
        ruleName: String,
        appliedRules: MutableList<String>
    ): String {
        var result = sql
        var hasChanges = false

        for (replacement in replacements) {
            val newResult = replacement.apply(result)
            if (newResult != result) {
                result = newResult
                hasChanges = true
            }
        }

        if (hasChanges) {
            appliedRules.add(ruleName)
        }

        return result
    }

    companion object {
        // Oracle → MySQL 변환 규칙
        private val oracleToMySqlReplacements = listOf(
            // NUMBER(n) → 정수형 매핑
            DynamicDataTypeReplacement("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*\\)") { match ->
                val precision = match.groupValues[1].toInt()
                when {
                    precision <= 3 -> "TINYINT"
                    precision <= 5 -> "SMALLINT"
                    precision <= 9 -> "INT"
                    else -> "BIGINT"
                }
            },
            SimpleDataTypeReplacement("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", "DECIMAL($1,$2)"),
            SimpleDataTypeReplacement("\\bNUMBER\\b", "DECIMAL"),
            SimpleDataTypeReplacement("\\bVARCHAR2\\s*\\(", "VARCHAR("),
            SimpleDataTypeReplacement("\\bNVARCHAR2\\s*\\(", "VARCHAR("),
            SimpleDataTypeReplacement("\\bNCHAR\\s*\\(", "CHAR("),
            SimpleDataTypeReplacement("\\bCHAR\\s*\\(", "CHAR("),
            SimpleDataTypeReplacement("\\bCLOB\\b", "LONGTEXT"),
            SimpleDataTypeReplacement("\\bNCLOB\\b", "LONGTEXT"),
            SimpleDataTypeReplacement("\\bBLOB\\b", "LONGBLOB"),
            SimpleDataTypeReplacement("\\bLONG\\s+RAW\\b", "LONGBLOB"),
            SimpleDataTypeReplacement("\\bLONG\\b", "LONGTEXT"),
            SimpleDataTypeReplacement("\\bRAW\\s*\\(\\s*(\\d+)\\s*\\)", "VARBINARY($1)"),
            SimpleDataTypeReplacement("\\bDATE\\b", "DATETIME"),
            SimpleDataTypeReplacement("\\bTIMESTAMP\\s+WITH\\s+LOCAL\\s+TIME\\s+ZONE\\b", "DATETIME"),
            SimpleDataTypeReplacement("\\bTIMESTAMP\\s+WITH\\s+TIME\\s+ZONE\\b", "DATETIME"),
            SimpleDataTypeReplacement("\\bTIMESTAMP\\s*\\(\\s*(\\d+)\\s*\\)", "DATETIME($1)"),
            SimpleDataTypeReplacement("\\bTIMESTAMP\\b", "DATETIME"),
            SimpleDataTypeReplacement("\\bINTERVAL\\s+YEAR.*?TO\\s+MONTH\\b", "VARCHAR(30)"),
            SimpleDataTypeReplacement("\\bINTERVAL\\s+DAY.*?TO\\s+SECOND\\b", "VARCHAR(30)"),
            DynamicDataTypeReplacement("\\bFLOAT\\s*\\(\\s*(\\d+)\\s*\\)") { match ->
                val precision = match.groupValues[1].toInt()
                if (precision <= 24) "FLOAT" else "DOUBLE"
            },
            SimpleDataTypeReplacement("\\bFLOAT\\b", "DOUBLE"),
            SimpleDataTypeReplacement("\\bBINARY_FLOAT\\b", "FLOAT"),
            SimpleDataTypeReplacement("\\bBINARY_DOUBLE\\b", "DOUBLE"),
            SimpleDataTypeReplacement("\\bBFILE\\b", "VARCHAR(255)"),
            SimpleDataTypeReplacement("\\bROWID\\b", "VARCHAR(18)"),
            SimpleDataTypeReplacement("\\bUROWID\\b", "VARCHAR(4000)"),
            SimpleDataTypeReplacement("\\bXMLTYPE\\b", "LONGTEXT")
        )

        // Oracle → PostgreSQL 변환 규칙
        private val oracleToPostgreSqlReplacements = listOf(
            DynamicDataTypeReplacement("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*\\)") { match ->
                val precision = match.groupValues[1].toInt()
                when {
                    precision <= 4 -> "SMALLINT"
                    precision <= 9 -> "INTEGER"
                    else -> "BIGINT"
                }
            },
            SimpleDataTypeReplacement("\\bNUMBER\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", "NUMERIC($1,$2)"),
            SimpleDataTypeReplacement("\\bNUMBER\\b", "NUMERIC"),
            SimpleDataTypeReplacement("\\bVARCHAR2\\s*\\(", "VARCHAR("),
            SimpleDataTypeReplacement("\\bNVARCHAR2\\s*\\(", "VARCHAR("),
            SimpleDataTypeReplacement("\\bNCHAR\\s*\\(", "CHAR("),
            SimpleDataTypeReplacement("\\bCHAR\\s*\\(", "CHAR("),
            SimpleDataTypeReplacement("\\bCLOB\\b", "TEXT"),
            SimpleDataTypeReplacement("\\bNCLOB\\b", "TEXT"),
            SimpleDataTypeReplacement("\\bBLOB\\b", "BYTEA"),
            SimpleDataTypeReplacement("\\bLONG\\s+RAW\\b", "BYTEA"),
            SimpleDataTypeReplacement("\\bLONG\\b", "TEXT"),
            SimpleDataTypeReplacement("\\bRAW\\s*\\(\\s*(\\d+)\\s*\\)", "BYTEA"),
            SimpleDataTypeReplacement("\\bDATE\\b", "TIMESTAMP"),
            SimpleDataTypeReplacement("\\bTIMESTAMP\\s+WITH\\s+LOCAL\\s+TIME\\s+ZONE\\b", "TIMESTAMPTZ"),
            SimpleDataTypeReplacement("\\bTIMESTAMP\\s+WITH\\s+TIME\\s+ZONE\\b", "TIMESTAMPTZ"),
            SimpleDataTypeReplacement("\\bTIMESTAMP\\s*\\(\\s*(\\d+)\\s*\\)", "TIMESTAMP($1)"),
            SimpleDataTypeReplacement("\\bINTERVAL\\s+YEAR.*?TO\\s+MONTH\\b", "INTERVAL"),
            SimpleDataTypeReplacement("\\bINTERVAL\\s+DAY.*?TO\\s+SECOND\\b", "INTERVAL"),
            DynamicDataTypeReplacement("\\bFLOAT\\s*\\(\\s*(\\d+)\\s*\\)") { match ->
                val precision = match.groupValues[1].toInt()
                if (precision <= 24) "REAL" else "DOUBLE PRECISION"
            },
            SimpleDataTypeReplacement("\\bFLOAT\\b", "DOUBLE PRECISION"),
            SimpleDataTypeReplacement("\\bBINARY_FLOAT\\b", "REAL"),
            SimpleDataTypeReplacement("\\bBINARY_DOUBLE\\b", "DOUBLE PRECISION"),
            SimpleDataTypeReplacement("\\bBFILE\\b", "VARCHAR(255)"),
            SimpleDataTypeReplacement("\\bROWID\\b", "VARCHAR(18)"),
            SimpleDataTypeReplacement("\\bUROWID\\b", "VARCHAR(4000)"),
            SimpleDataTypeReplacement("\\bXMLTYPE\\b", "XML")
        )

        // MySQL → PostgreSQL 변환 규칙
        private val mySqlToPostgreSqlReplacements = listOf(
            // 정수형
            SimpleDataTypeReplacement("\\bTINYINT\\s*\\(\\s*1\\s*\\)", "BOOLEAN"),
            SimpleDataTypeReplacement("\\bTINYINT\\b", "SMALLINT"),
            SimpleDataTypeReplacement("\\bMEDIUMINT\\b", "INTEGER"),
            SimpleDataTypeReplacement("\\bINT\\s+UNSIGNED\\b", "BIGINT"),
            SimpleDataTypeReplacement("\\bBIGINT\\s+UNSIGNED\\b", "NUMERIC(20)"),
            // 문자열
            SimpleDataTypeReplacement("\\bLONGTEXT\\b", "TEXT"),
            SimpleDataTypeReplacement("\\bMEDIUMTEXT\\b", "TEXT"),
            SimpleDataTypeReplacement("\\bTINYTEXT\\b", "TEXT"),
            // 바이너리
            SimpleDataTypeReplacement("\\bLONGBLOB\\b", "BYTEA"),
            SimpleDataTypeReplacement("\\bMEDIUMBLOB\\b", "BYTEA"),
            SimpleDataTypeReplacement("\\bTINYBLOB\\b", "BYTEA"),
            SimpleDataTypeReplacement("\\bBLOB\\b", "BYTEA"),
            SimpleDataTypeReplacement("\\bVARBINARY\\s*\\(", "BYTEA"),
            SimpleDataTypeReplacement("\\bBINARY\\s*\\(", "BYTEA"),
            // 날짜/시간
            SimpleDataTypeReplacement("\\bDATETIME\\b", "TIMESTAMP"),
            SimpleDataTypeReplacement("\\bYEAR\\b", "SMALLINT"),
            // 부동소수점
            SimpleDataTypeReplacement("\\bDOUBLE\\b", "DOUBLE PRECISION"),
            SimpleDataTypeReplacement("\\bFLOAT\\b", "REAL"),
            // ENUM/SET
            SimpleDataTypeReplacement("\\bENUM\\s*\\([^)]+\\)", "VARCHAR(255)"),
            SimpleDataTypeReplacement("\\bSET\\s*\\([^)]+\\)", "VARCHAR(255)"),
            // AUTO_INCREMENT → SERIAL
            SimpleDataTypeReplacement("\\bINT\\s+AUTO_INCREMENT\\b", "SERIAL"),
            SimpleDataTypeReplacement("\\bBIGINT\\s+AUTO_INCREMENT\\b", "BIGSERIAL"),
            SimpleDataTypeReplacement("\\bAUTO_INCREMENT\\b", ""),
            // JSON
            SimpleDataTypeReplacement("\\bJSON\\b", "JSONB")
        )

        // MySQL → Oracle 변환 규칙
        private val mySqlToOracleReplacements = listOf(
            SimpleDataTypeReplacement("\\bVARCHAR\\s*\\(", "VARCHAR2("),
            SimpleDataTypeReplacement("\\bTINYINT\\b", "NUMBER(3)"),
            SimpleDataTypeReplacement("\\bSMALLINT\\b", "NUMBER(5)"),
            SimpleDataTypeReplacement("\\bMEDIUMINT\\b", "NUMBER(7)"),
            SimpleDataTypeReplacement("\\bINT\\b", "NUMBER(10)"),
            SimpleDataTypeReplacement("\\bBIGINT\\b", "NUMBER(19)"),
            SimpleDataTypeReplacement("\\bDATETIME\\b", "DATE"),
            SimpleDataTypeReplacement("\\bLONGTEXT\\b", "CLOB"),
            SimpleDataTypeReplacement("\\bLONGBLOB\\b", "BLOB"),
            SimpleDataTypeReplacement("\\bTEXT\\b", "CLOB")
        )

        // PostgreSQL → MySQL 변환 규칙
        private val postgreSqlToMySqlReplacements = listOf(
            // 정수형
            SimpleDataTypeReplacement("\\bSERIAL\\b", "INT AUTO_INCREMENT"),
            SimpleDataTypeReplacement("\\bBIGSERIAL\\b", "BIGINT AUTO_INCREMENT"),
            SimpleDataTypeReplacement("\\bSMALLSERIAL\\b", "SMALLINT AUTO_INCREMENT"),
            // 문자열/바이너리
            SimpleDataTypeReplacement("\\bTEXT\\b", "LONGTEXT"),
            SimpleDataTypeReplacement("\\bBYTEA\\b", "LONGBLOB"),
            // 날짜/시간
            SimpleDataTypeReplacement("\\bTIMESTAMP\\s+WITHOUT\\s+TIME\\s+ZONE\\b", "DATETIME"),
            SimpleDataTypeReplacement("\\bTIMESTAMP\\s+WITH\\s+TIME\\s+ZONE\\b", "DATETIME"),
            SimpleDataTypeReplacement("\\bTIMESTAMP\\b", "DATETIME"),
            SimpleDataTypeReplacement("\\bINTERVAL\\b", "VARCHAR(255)"),
            // 부동소수점
            SimpleDataTypeReplacement("\\bDOUBLE PRECISION\\b", "DOUBLE"),
            SimpleDataTypeReplacement("\\bREAL\\b", "FLOAT"),
            // JSON
            SimpleDataTypeReplacement("\\bJSONB\\b", "JSON"),
            // UUID
            SimpleDataTypeReplacement("\\bUUID\\b", "CHAR(36)"),
            // 배열 타입 제거
            SimpleDataTypeReplacement("\\[\\]", ""),
            // BOOLEAN
            SimpleDataTypeReplacement("\\bBOOLEAN\\b", "TINYINT(1)")
        )

        // PostgreSQL → Oracle 변환 규칙
        private val postgreSqlToOracleReplacements = listOf(
            SimpleDataTypeReplacement("\\bVARCHAR\\s*\\(", "VARCHAR2("),
            SimpleDataTypeReplacement("\\bSERIAL\\b", "NUMBER GENERATED ALWAYS AS IDENTITY"),
            SimpleDataTypeReplacement("\\bBIGSERIAL\\b", "NUMBER GENERATED ALWAYS AS IDENTITY"),
            SimpleDataTypeReplacement("\\bINTEGER\\b", "NUMBER(10)"),
            SimpleDataTypeReplacement("\\bSMALLINT\\b", "NUMBER(5)"),
            SimpleDataTypeReplacement("\\bBIGINT\\b", "NUMBER(19)"),
            SimpleDataTypeReplacement("\\bTEXT\\b", "CLOB"),
            SimpleDataTypeReplacement("\\bBYTEA\\b", "BLOB"),
            SimpleDataTypeReplacement("\\bTIMESTAMP\\b", "DATE"),
            SimpleDataTypeReplacement("\\bBOOLEAN\\b", "NUMBER(1)"),
            SimpleDataTypeReplacement("\\bDOUBLE PRECISION\\b", "BINARY_DOUBLE"),
            SimpleDataTypeReplacement("\\bREAL\\b", "BINARY_FLOAT")
        )
    }
}

/**
 * 데이터타입 치환 규칙 인터페이스
 */
interface DataTypeReplacement {
    fun apply(sql: String): String
}

/**
 * 단순 문자열 치환 규칙
 */
class SimpleDataTypeReplacement(
    pattern: String,
    private val replacement: String
) : DataTypeReplacement {
    private val regex = Regex(pattern, RegexOption.IGNORE_CASE)

    override fun apply(sql: String): String = regex.replace(sql, replacement)
}

/**
 * 동적 치환 규칙 (람다 사용)
 */
class DynamicDataTypeReplacement(
    pattern: String,
    private val transformer: (MatchResult) -> String
) : DataTypeReplacement {
    private val regex = Regex(pattern, RegexOption.IGNORE_CASE)

    override fun apply(sql: String): String = regex.replace(sql, transformer)
}