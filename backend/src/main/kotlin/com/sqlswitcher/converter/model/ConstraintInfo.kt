package com.sqlswitcher.converter.model

/**
 * FOREIGN KEY 정보를 담는 데이터 클래스
 */
data class ForeignKeyInfo(
    val constraintName: String,
    val columns: List<String>,
    val referencedTable: String,
    val referencedColumns: List<String>,
    val onDelete: String? = null,   // CASCADE, SET NULL, SET DEFAULT, NO ACTION, RESTRICT
    val onUpdate: String? = null    // CASCADE, SET NULL, SET DEFAULT, NO ACTION, RESTRICT
) {
    /**
     * Oracle 스타일 FOREIGN KEY 제약조건 생성
     */
    fun toOracleConstraint(schemaOwner: String, tableName: String): String {
        val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
        val refColumnsQuoted = referencedColumns.joinToString(", ") { "\"$it\"" }
        val refTable = if (referencedTable.contains(".")) {
            referencedTable.split(".").joinToString(".") { "\"${it.trim('"')}\"" }
        } else {
            "\"$schemaOwner\".\"$referencedTable\""
        }

        val sb = StringBuilder()
        sb.append("ALTER TABLE \"$schemaOwner\".\"$tableName\" ADD CONSTRAINT \"$constraintName\"")
        sb.append(" FOREIGN KEY ($columnsQuoted)")
        sb.append(" REFERENCES $refTable ($refColumnsQuoted)")

        onDelete?.let { sb.append(" ON DELETE $it") }
        // Oracle은 ON UPDATE를 지원하지 않음

        sb.append(" ENABLE")
        return sb.toString()
    }

    /**
     * MySQL 스타일 FOREIGN KEY 제약조건 생성
     */
    fun toMySqlConstraint(tableName: String): String {
        val columnsQuoted = columns.joinToString(", ") { "`$it`" }
        val refColumnsQuoted = referencedColumns.joinToString(", ") { "`$it`" }
        val refTable = "`${referencedTable.trim('`', '"')}`"

        val sb = StringBuilder()
        sb.append("ALTER TABLE `$tableName` ADD CONSTRAINT `$constraintName`")
        sb.append(" FOREIGN KEY ($columnsQuoted)")
        sb.append(" REFERENCES $refTable ($refColumnsQuoted)")

        onDelete?.let { sb.append(" ON DELETE $it") }
        onUpdate?.let { sb.append(" ON UPDATE $it") }

        return sb.toString()
    }

    /**
     * PostgreSQL 스타일 FOREIGN KEY 제약조건 생성
     */
    fun toPostgreSqlConstraint(tableName: String): String {
        val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
        val refColumnsQuoted = referencedColumns.joinToString(", ") { "\"$it\"" }
        val refTable = "\"${referencedTable.trim('"')}\""

        val sb = StringBuilder()
        sb.append("ALTER TABLE \"$tableName\" ADD CONSTRAINT \"$constraintName\"")
        sb.append(" FOREIGN KEY ($columnsQuoted)")
        sb.append(" REFERENCES $refTable ($refColumnsQuoted)")

        onDelete?.let { sb.append(" ON DELETE $it") }
        onUpdate?.let { sb.append(" ON UPDATE $it") }

        return sb.toString()
    }
}

/**
 * UNIQUE 제약조건 정보를 담는 데이터 클래스
 */
data class UniqueConstraintInfo(
    val constraintName: String,
    val columns: List<String>
) {
    /**
     * Oracle 스타일 UNIQUE 제약조건 생성
     */
    fun toOracleConstraint(schemaOwner: String, tableName: String, indexspace: String? = null): String {
        val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
        val sb = StringBuilder()
        sb.append("ALTER TABLE \"$schemaOwner\".\"$tableName\" ADD CONSTRAINT \"$constraintName\"")
        sb.append(" UNIQUE ($columnsQuoted)")
        indexspace?.let { sb.append(" USING INDEX TABLESPACE \"$it\"") }
        sb.append(" ENABLE")
        return sb.toString()
    }

    /**
     * MySQL 스타일 UNIQUE 제약조건 생성
     */
    fun toMySqlConstraint(tableName: String): String {
        val columnsQuoted = columns.joinToString(", ") { "`$it`" }
        return "ALTER TABLE `$tableName` ADD CONSTRAINT `$constraintName` UNIQUE ($columnsQuoted)"
    }

    /**
     * PostgreSQL 스타일 UNIQUE 제약조건 생성
     */
    fun toPostgreSqlConstraint(tableName: String): String {
        val columnsQuoted = columns.joinToString(", ") { "\"$it\"" }
        return "ALTER TABLE \"$tableName\" ADD CONSTRAINT \"$constraintName\" UNIQUE ($columnsQuoted)"
    }
}

/**
 * CHECK 제약조건 정보를 담는 데이터 클래스
 */
data class CheckConstraintInfo(
    val constraintName: String,
    val expression: String
) {
    /**
     * Oracle 스타일 CHECK 제약조건 생성
     */
    fun toOracleConstraint(schemaOwner: String, tableName: String): String {
        // 표현식 내 백틱을 큰따옴표로 변환
        val oracleExpr = expression.replace("`", "\"")
        return "ALTER TABLE \"$schemaOwner\".\"$tableName\" ADD CONSTRAINT \"$constraintName\" CHECK ($oracleExpr) ENABLE"
    }

    /**
     * MySQL 스타일 CHECK 제약조건 생성
     */
    fun toMySqlConstraint(tableName: String): String {
        val mysqlExpr = expression.replace("\"", "`")
        return "ALTER TABLE `$tableName` ADD CONSTRAINT `$constraintName` CHECK ($mysqlExpr)"
    }

    /**
     * PostgreSQL 스타일 CHECK 제약조건 생성
     */
    fun toPostgreSqlConstraint(tableName: String): String {
        val pgExpr = expression.replace("`", "\"")
        return "ALTER TABLE \"$tableName\" ADD CONSTRAINT \"$constraintName\" CHECK ($pgExpr)"
    }
}