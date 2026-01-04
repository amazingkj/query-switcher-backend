package com.sqlswitcher.converter.feature.udt

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * Oracle 사용자 정의 타입 (UDT) 변환기
 *
 * Oracle의 사용자 정의 타입을 MySQL/PostgreSQL의 동등한 기능으로 변환
 *
 * 지원하는 타입:
 * - Object Types (CREATE TYPE ... AS OBJECT)
 * - VARRAY (가변 배열)
 * - Nested Table (중첩 테이블)
 * - TABLE OF (컬렉션)
 * - Record Types (레코드)
 * - REF Types (참조)
 */
object UserDefinedTypeConverter {

    /**
     * CREATE TYPE ... AS OBJECT 패턴
     */
    private val OBJECT_TYPE_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?TYPE\s+(\w+)\s+(?:AS|IS)\s+OBJECT\s*\(([\s\S]+?)\)(?:\s*(?:NOT\s+)?FINAL)?(?:\s*(?:NOT\s+)?INSTANTIABLE)?;?""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * VARRAY 타입 패턴
     */
    private val VARRAY_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?TYPE\s+(\w+)\s+(?:AS|IS)\s+VARRAY\s*\(\s*(\d+)\s*\)\s+OF\s+(\w+(?:\s*\([^)]+\))?);?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * NESTED TABLE 타입 패턴
     */
    private val NESTED_TABLE_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?TYPE\s+(\w+)\s+(?:AS|IS)\s+TABLE\s+OF\s+(\w+(?:\s*\([^)]+\))?);?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 타입 속성 패턴
     */
    private val TYPE_ATTRIBUTE_PATTERN = Regex(
        """(\w+)\s+(\w+(?:\s*\([^)]+\))?)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * %TYPE 참조 패턴
     */
    private val PERCENT_TYPE_PATTERN = Regex(
        """(\w+)\.(\w+)%TYPE""",
        RegexOption.IGNORE_CASE
    )

    /**
     * %ROWTYPE 참조 패턴
     */
    private val PERCENT_ROWTYPE_PATTERN = Regex(
        """(\w+)%ROWTYPE""",
        RegexOption.IGNORE_CASE
    )

    /**
     * REF 타입 패턴
     */
    private val REF_TYPE_PATTERN = Regex(
        """REF\s+(\w+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 컬럼의 사용자 정의 타입 사용 패턴
     */
    private val COLUMN_UDT_PATTERN = Regex(
        """(\w+)\s+(\w+)(?:\s*STORE\s+AS\s+(\w+))?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Oracle 데이터 타입 → MySQL 변환 맵
     */
    private val ORACLE_TO_MYSQL_TYPE_MAP = mapOf(
        "VARCHAR2" to "VARCHAR",
        "NUMBER" to "DECIMAL",
        "DATE" to "DATETIME",
        "TIMESTAMP" to "DATETIME",
        "CLOB" to "LONGTEXT",
        "BLOB" to "LONGBLOB",
        "RAW" to "VARBINARY",
        "LONG" to "LONGTEXT",
        "BINARY_FLOAT" to "FLOAT",
        "BINARY_DOUBLE" to "DOUBLE",
        "INTEGER" to "INT",
        "PLS_INTEGER" to "INT",
        "BOOLEAN" to "TINYINT(1)"
    )

    /**
     * Oracle 데이터 타입 → PostgreSQL 변환 맵
     */
    private val ORACLE_TO_POSTGRESQL_TYPE_MAP = mapOf(
        "VARCHAR2" to "VARCHAR",
        "NUMBER" to "NUMERIC",
        "DATE" to "TIMESTAMP",
        "CLOB" to "TEXT",
        "BLOB" to "BYTEA",
        "RAW" to "BYTEA",
        "LONG" to "TEXT",
        "BINARY_FLOAT" to "REAL",
        "BINARY_DOUBLE" to "DOUBLE PRECISION",
        "INTEGER" to "INTEGER",
        "PLS_INTEGER" to "INTEGER",
        "BOOLEAN" to "BOOLEAN"
    )

    /**
     * 사용자 정의 타입 변환
     */
    fun convert(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) {
            return sql
        }

        var result = sql

        when (targetDialect) {
            DialectType.MYSQL -> {
                result = convertToMySql(result, warnings, appliedRules)
            }
            DialectType.POSTGRESQL -> {
                result = convertToPostgreSql(result, warnings, appliedRules)
            }
            else -> {
                // 다른 대상 방언은 변환하지 않음
            }
        }

        return result
    }

    /**
     * MySQL로 변환
     */
    private fun convertToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // Object Type → JSON 컬럼 또는 테이블로 변환
        result = OBJECT_TYPE_PATTERN.replace(result) { match ->
            val typeName = match.groupValues[1]
            val attributes = match.groupValues[2]
            appliedRules.add("CREATE TYPE $typeName AS OBJECT → MySQL 테이블/JSON")
            warnings.add(ConversionWarning(
                type = WarningType.PARTIAL_SUPPORT,
                message = "Oracle Object Type '$typeName'은 MySQL에서 직접 지원되지 않습니다",
                severity = WarningSeverity.WARNING,
                suggestion = "테이블로 변환하거나 JSON 컬럼을 사용하세요. 애플리케이션 레벨에서 처리가 필요할 수 있습니다."
            ))
            convertObjectTypeToMySql(typeName, attributes, appliedRules)
        }

        // VARRAY → JSON 배열
        result = VARRAY_PATTERN.replace(result) { match ->
            val typeName = match.groupValues[1]
            val maxSize = match.groupValues[2]
            val elementType = match.groupValues[3]
            appliedRules.add("VARRAY $typeName → MySQL JSON 배열")
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "VARRAY '$typeName'은 MySQL에서 JSON 배열로 변환됩니다",
                severity = WarningSeverity.INFO,
                suggestion = "JSON 배열로 데이터를 저장하고 JSON 함수로 접근하세요."
            ))
            "-- VARRAY $typeName($maxSize) OF $elementType converted to JSON\n" +
            "-- Usage: Use JSON column type and JSON_ARRAY() functions\n" +
            "-- Example column: ${typeName.lowercase()}_data JSON"
        }

        // NESTED TABLE → JSON 배열 또는 별도 테이블
        result = NESTED_TABLE_PATTERN.replace(result) { match ->
            val typeName = match.groupValues[1]
            val elementType = match.groupValues[2]
            appliedRules.add("NESTED TABLE $typeName → MySQL JSON/테이블")
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "NESTED TABLE '$typeName'은 MySQL에서 직접 지원되지 않습니다",
                severity = WarningSeverity.WARNING,
                suggestion = "JSON 배열이나 별도의 자식 테이블을 사용하세요."
            ))
            "-- NESTED TABLE $typeName OF $elementType converted\n" +
            "-- Option 1: Use JSON column type\n" +
            "-- Option 2: Create a separate child table with foreign key"
        }

        // %TYPE → 실제 타입으로 대체 필요
        result = PERCENT_TYPE_PATTERN.replace(result) { match ->
            val tableName = match.groupValues[1]
            val columnName = match.groupValues[2]
            warnings.add(ConversionWarning(
                type = WarningType.MANUAL_REVIEW_NEEDED,
                message = "%TYPE 참조 '${tableName}.${columnName}%TYPE'는 실제 데이터 타입으로 대체해야 합니다",
                severity = WarningSeverity.WARNING,
                suggestion = "해당 컬럼의 실제 데이터 타입을 명시하세요."
            ))
            "/* ${tableName}.${columnName}%TYPE - replace with actual type */"
        }

        // %ROWTYPE → 개별 컬럼으로 분리 필요
        result = PERCENT_ROWTYPE_PATTERN.replace(result) { match ->
            val tableName = match.groupValues[1]
            warnings.add(ConversionWarning(
                type = WarningType.MANUAL_REVIEW_NEEDED,
                message = "%ROWTYPE '${tableName}%ROWTYPE'는 MySQL에서 지원되지 않습니다",
                severity = WarningSeverity.ERROR,
                suggestion = "각 컬럼을 개별 변수로 선언하거나 JSON을 사용하세요."
            ))
            "/* ${tableName}%ROWTYPE - replace with individual columns or JSON */"
        }

        // REF 타입 → 외래 키로 변환
        result = REF_TYPE_PATTERN.replace(result) { match ->
            val refType = match.groupValues[1]
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "REF ${refType}은 MySQL에서 외래 키로 변환이 필요합니다",
                severity = WarningSeverity.WARNING,
                suggestion = "외래 키 컬럼으로 변환하세요."
            ))
            "/* REF $refType - use foreign key column instead */ BIGINT"
        }

        return result
    }

    /**
     * PostgreSQL로 변환
     */
    private fun convertToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // Object Type → PostgreSQL Composite Type
        result = OBJECT_TYPE_PATTERN.replace(result) { match ->
            val typeName = match.groupValues[1]
            val attributes = match.groupValues[2]
            appliedRules.add("CREATE TYPE $typeName AS OBJECT → PostgreSQL Composite Type")
            convertObjectTypeToPostgreSql(typeName, attributes, appliedRules)
        }

        // VARRAY → PostgreSQL ARRAY
        result = VARRAY_PATTERN.replace(result) { match ->
            val typeName = match.groupValues[1]
            val maxSize = match.groupValues[2]
            val elementType = match.groupValues[3]
            val pgElementType = convertOracleTypeToPg(elementType)
            appliedRules.add("VARRAY $typeName → PostgreSQL ARRAY")
            warnings.add(ConversionWarning(
                type = WarningType.INFO,
                message = "VARRAY '$typeName' 배열로 변환됨. 최대 크기($maxSize) 제약은 적용되지 않습니다.",
                severity = WarningSeverity.INFO,
                suggestion = "PostgreSQL 배열은 크기 제한이 없으므로 애플리케이션에서 검증하세요."
            ))
            "CREATE TYPE $typeName AS ($pgElementType[]);"
        }

        // NESTED TABLE → PostgreSQL ARRAY
        result = NESTED_TABLE_PATTERN.replace(result) { match ->
            val typeName = match.groupValues[1]
            val elementType = match.groupValues[2]
            val pgElementType = convertOracleTypeToPg(elementType)
            appliedRules.add("NESTED TABLE $typeName → PostgreSQL ARRAY")
            "CREATE TYPE $typeName AS ($pgElementType[]);"
        }

        // %TYPE → PostgreSQL 지원
        result = PERCENT_TYPE_PATTERN.replace(result) { match ->
            val tableName = match.groupValues[1]
            val columnName = match.groupValues[2]
            // PostgreSQL은 %TYPE을 지원하지만 문법이 다름
            appliedRules.add("%TYPE → PostgreSQL 타입 참조")
            "${tableName}.${columnName}%TYPE"  // PostgreSQL도 같은 문법 지원
        }

        // %ROWTYPE → PostgreSQL 레코드 타입
        result = PERCENT_ROWTYPE_PATTERN.replace(result) { match ->
            val tableName = match.groupValues[1]
            appliedRules.add("%ROWTYPE → PostgreSQL 레코드")
            "${tableName}%ROWTYPE"  // PostgreSQL도 같은 문법 지원
        }

        // REF 타입 → PostgreSQL은 직접 지원하지 않음
        result = REF_TYPE_PATTERN.replace(result) { match ->
            val refType = match.groupValues[1]
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "REF ${refType}은 PostgreSQL에서 직접 지원되지 않습니다",
                severity = WarningSeverity.WARNING,
                suggestion = "외래 키 또는 OID 참조를 사용하세요."
            ))
            "/* REF $refType - use foreign key */ BIGINT"
        }

        return result
    }

    /**
     * Object Type을 MySQL 구조로 변환
     */
    private fun convertObjectTypeToMySql(
        typeName: String,
        attributes: String,
        appliedRules: MutableList<String>
    ): String {
        val parsedAttrs = parseTypeAttributes(attributes)

        // 옵션 1: 테이블로 변환
        val tableColumns = parsedAttrs.map { (name, type) ->
            val mysqlType = convertOracleTypeToMySql(type)
            "    $name $mysqlType"
        }.joinToString(",\n")

        return """
-- Option 1: Table representation for $typeName
CREATE TABLE ${typeName.lowercase()}_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
$tableColumns
);

-- Option 2: Use JSON column with this structure:
-- Column definition: ${typeName.lowercase()}_data JSON
-- Example JSON: ${generateJsonExample(parsedAttrs)}
""".trim()
    }

    /**
     * Object Type을 PostgreSQL Composite Type으로 변환
     */
    private fun convertObjectTypeToPostgreSql(
        typeName: String,
        attributes: String,
        appliedRules: MutableList<String>
    ): String {
        val parsedAttrs = parseTypeAttributes(attributes)

        val typeColumns = parsedAttrs.map { (name, type) ->
            val pgType = convertOracleTypeToPg(type)
            "    $name $pgType"
        }.joinToString(",\n")

        return """
CREATE TYPE $typeName AS (
$typeColumns
);""".trim()
    }

    /**
     * 타입 속성 파싱
     */
    private fun parseTypeAttributes(attributes: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()

        // 메서드 정의 제거 (MEMBER FUNCTION, MEMBER PROCEDURE 등)
        val cleanedAttrs = attributes
            .replace(Regex("""MEMBER\s+(?:FUNCTION|PROCEDURE)[\s\S]+?(?=,\s*\w+\s+\w+|$)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""CONSTRUCTOR[\s\S]+?(?=,\s*\w+\s+\w+|$)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""STATIC\s+(?:FUNCTION|PROCEDURE)[\s\S]+?(?=,\s*\w+\s+\w+|$)""", RegexOption.IGNORE_CASE), "")

        // 속성 추출
        val attrLines = cleanedAttrs.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (line in attrLines) {
            val match = TYPE_ATTRIBUTE_PATTERN.find(line)
            if (match != null) {
                val attrName = match.groupValues[1]
                val attrType = match.groupValues[2]
                // 메서드가 아닌 경우만 추가
                if (!attrName.uppercase().let {
                    it == "MEMBER" || it == "STATIC" || it == "CONSTRUCTOR" || it == "FUNCTION" || it == "PROCEDURE"
                }) {
                    result.add(attrName to attrType)
                }
            }
        }

        return result
    }

    /**
     * Oracle 타입을 MySQL 타입으로 변환
     */
    private fun convertOracleTypeToMySql(oracleType: String): String {
        val upperType = oracleType.uppercase().trim()

        // 정확히 매칭되는 경우
        ORACLE_TO_MYSQL_TYPE_MAP[upperType]?.let { return it }

        // 파라미터가 있는 타입 처리
        val baseType = upperType.substringBefore("(").trim()
        val params = if (upperType.contains("(")) {
            upperType.substringAfter("(").substringBefore(")")
        } else null

        return when (baseType) {
            "VARCHAR2" -> if (params != null) "VARCHAR($params)" else "VARCHAR(255)"
            "NVARCHAR2" -> if (params != null) "NVARCHAR($params)" else "NVARCHAR(255)"
            "CHAR" -> if (params != null) "CHAR($params)" else "CHAR(1)"
            "NUMBER" -> {
                if (params != null) {
                    val parts = params.split(",").map { it.trim() }
                    when {
                        parts.size == 1 && parts[0].toIntOrNull()?.let { it <= 9 } == true -> "INT"
                        parts.size == 1 && parts[0].toIntOrNull()?.let { it <= 18 } == true -> "BIGINT"
                        parts.size == 2 -> "DECIMAL($params)"
                        else -> "DECIMAL(38,10)"
                    }
                } else "DECIMAL(38,10)"
            }
            "RAW" -> if (params != null) "VARBINARY($params)" else "VARBINARY(255)"
            else -> ORACLE_TO_MYSQL_TYPE_MAP[baseType] ?: oracleType
        }
    }

    /**
     * Oracle 타입을 PostgreSQL 타입으로 변환
     */
    private fun convertOracleTypeToPg(oracleType: String): String {
        val upperType = oracleType.uppercase().trim()

        ORACLE_TO_POSTGRESQL_TYPE_MAP[upperType]?.let { return it }

        val baseType = upperType.substringBefore("(").trim()
        val params = if (upperType.contains("(")) {
            upperType.substringAfter("(").substringBefore(")")
        } else null

        return when (baseType) {
            "VARCHAR2" -> if (params != null) "VARCHAR($params)" else "VARCHAR(255)"
            "NVARCHAR2" -> if (params != null) "VARCHAR($params)" else "VARCHAR(255)"
            "CHAR" -> if (params != null) "CHAR($params)" else "CHAR(1)"
            "NUMBER" -> {
                if (params != null) {
                    val parts = params.split(",").map { it.trim() }
                    when {
                        parts.size == 1 && parts[0].toIntOrNull()?.let { it <= 4 } == true -> "SMALLINT"
                        parts.size == 1 && parts[0].toIntOrNull()?.let { it <= 9 } == true -> "INTEGER"
                        parts.size == 1 && parts[0].toIntOrNull()?.let { it <= 18 } == true -> "BIGINT"
                        parts.size == 2 -> "NUMERIC($params)"
                        else -> "NUMERIC"
                    }
                } else "NUMERIC"
            }
            "RAW" -> "BYTEA"
            else -> ORACLE_TO_POSTGRESQL_TYPE_MAP[baseType] ?: oracleType.lowercase()
        }
    }

    /**
     * JSON 예시 생성
     */
    private fun generateJsonExample(attrs: List<Pair<String, String>>): String {
        val jsonParts = attrs.map { (name, _) ->
            "\"$name\": <value>"
        }
        return "{ ${jsonParts.joinToString(", ")} }"
    }

    /**
     * UDT 관련 구문이 있는지 확인
     */
    fun hasUserDefinedTypes(sql: String): Boolean {
        val patterns = listOf(
            """CREATE\s+(?:OR\s+REPLACE\s+)?TYPE\b""",
            """%TYPE\b""",
            """%ROWTYPE\b""",
            """VARRAY\s*\(""",
            """TABLE\s+OF\b""",
            """REF\s+\w+"""
        )
        return patterns.any { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(sql) }
    }

    /**
     * 정의된 타입 이름 목록 추출
     */
    fun getDefinedTypeNames(sql: String): List<String> {
        val names = mutableListOf<String>()

        val typePattern = Regex(
            """CREATE\s+(?:OR\s+REPLACE\s+)?TYPE\s+(\w+)""",
            RegexOption.IGNORE_CASE
        )

        typePattern.findAll(sql).forEach { match ->
            names.add(match.groupValues[1])
        }

        return names.distinct()
    }

    /**
     * 컬럼에서 사용된 UDT 참조 추출
     */
    fun getUsedTypeReferences(sql: String): List<String> {
        val refs = mutableListOf<String>()

        // %TYPE 참조
        PERCENT_TYPE_PATTERN.findAll(sql).forEach { match ->
            refs.add("${match.groupValues[1]}.${match.groupValues[2]}%TYPE")
        }

        // %ROWTYPE 참조
        PERCENT_ROWTYPE_PATTERN.findAll(sql).forEach { match ->
            refs.add("${match.groupValues[1]}%ROWTYPE")
        }

        return refs.distinct()
    }
}
