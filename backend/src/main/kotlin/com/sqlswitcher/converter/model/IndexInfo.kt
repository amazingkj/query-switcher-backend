package com.sqlswitcher.converter.model

/**
 * 인덱스 컬럼 옵션 정보
 */
data class IndexColumnOption(
    val columnName: String,
    val sortOrder: SortOrder = SortOrder.ASC,
    val nullsPosition: NullsPosition? = null
) {
    enum class SortOrder { ASC, DESC }
    enum class NullsPosition { FIRST, LAST }

    fun toOracleColumn(): String {
        val sb = StringBuilder("\"$columnName\"")
        if (sortOrder == SortOrder.DESC) sb.append(" DESC")
        nullsPosition?.let {
            when (it) {
                NullsPosition.FIRST -> sb.append(" NULLS FIRST")
                NullsPosition.LAST -> sb.append(" NULLS LAST")
            }
        }
        return sb.toString()
    }

    fun toMySqlColumn(): String {
        val sb = StringBuilder("`$columnName`")
        if (sortOrder == SortOrder.DESC) sb.append(" DESC")
        // MySQL 8.0+에서만 DESC 인덱스 지원, NULLS FIRST/LAST는 미지원
        return sb.toString()
    }

    fun toPostgreSqlColumn(): String {
        val sb = StringBuilder("\"$columnName\"")
        if (sortOrder == SortOrder.DESC) sb.append(" DESC")
        nullsPosition?.let {
            when (it) {
                NullsPosition.FIRST -> sb.append(" NULLS FIRST")
                NullsPosition.LAST -> sb.append(" NULLS LAST")
            }
        }
        return sb.toString()
    }
}

/**
 * 함수 기반 인덱스 정보
 */
data class FunctionBasedIndexInfo(
    val indexName: String,
    val tableName: String,
    val expressions: List<String>,      // 함수 표현식 (예: UPPER(name), SUBSTR(code, 1, 3))
    val isUnique: Boolean = false,
    val tablespace: String? = null
) {
    /**
     * Oracle 함수 기반 인덱스 생성
     */
    fun toOracle(schemaOwner: String): String {
        val uniqueStr = if (isUnique) "UNIQUE " else ""
        val exprsQuoted = expressions.joinToString(", ")
        val tablespaceStr = tablespace?.let { " TABLESPACE \"$it\"" } ?: ""

        return "CREATE ${uniqueStr}INDEX \"$schemaOwner\".\"$indexName\" ON \"$schemaOwner\".\"$tableName\" ($exprsQuoted)$tablespaceStr"
    }

    /**
     * PostgreSQL 함수 기반 인덱스 생성
     */
    fun toPostgreSql(): String {
        val uniqueStr = if (isUnique) "UNIQUE " else ""
        val exprsConverted = expressions.map { convertExpressionToPostgreSql(it) }

        return "CREATE ${uniqueStr}INDEX \"$indexName\" ON \"$tableName\" (${exprsConverted.joinToString(", ")})"
    }

    /**
     * MySQL 함수 기반 인덱스 생성 (MySQL 8.0+)
     */
    fun toMySql(): String {
        val uniqueStr = if (isUnique) "UNIQUE " else ""
        val exprsConverted = expressions.map { "($it)" }  // MySQL은 괄호로 감싸야 함

        return "CREATE ${uniqueStr}INDEX `$indexName` ON `$tableName` (${exprsConverted.joinToString(", ")})"
    }

    private fun convertExpressionToPostgreSql(expr: String): String {
        var result = expr
        // Oracle/MySQL 함수 → PostgreSQL 함수 변환
        result = result.replace(Regex("NVL\\s*\\(", RegexOption.IGNORE_CASE), "COALESCE(")
        result = result.replace(Regex("SUBSTR\\s*\\(", RegexOption.IGNORE_CASE), "SUBSTRING(")
        return result
    }
}