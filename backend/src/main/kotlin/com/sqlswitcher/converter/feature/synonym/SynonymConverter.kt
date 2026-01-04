package com.sqlswitcher.converter.feature.synonym

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType

/**
 * Oracle Synonym (동의어) 변환기
 *
 * Oracle의 Synonym을 MySQL/PostgreSQL의 동등한 기능으로 변환
 *
 * Oracle Synonym 유형:
 * - PUBLIC SYNONYM: 모든 사용자가 접근 가능한 동의어
 * - PRIVATE SYNONYM: 특정 스키마에서만 접근 가능한 동의어
 *
 * 변환 전략:
 * - MySQL: 뷰(VIEW)로 변환하거나 주석으로 표시
 * - PostgreSQL: 뷰(VIEW) 또는 search_path 설정으로 변환
 */
object SynonymConverter {

    /**
     * CREATE SYNONYM 패턴
     */
    private val CREATE_SYNONYM_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?(?:PUBLIC\s+)?SYNONYM\s+(?:(\w+)\.)?(\w+)\s+FOR\s+(?:(\w+)\.)?(\w+)(?:@(\w+))?;?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DROP SYNONYM 패턴
     */
    private val DROP_SYNONYM_PATTERN = Regex(
        """DROP\s+(?:PUBLIC\s+)?SYNONYM\s+(?:(\w+)\.)?(\w+)(?:\s+FORCE)?;?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * PUBLIC SYNONYM 감지 패턴
     */
    private val PUBLIC_SYNONYM_PATTERN = Regex(
        """CREATE\s+(?:OR\s+REPLACE\s+)?PUBLIC\s+SYNONYM""",
        RegexOption.IGNORE_CASE
    )

    /**
     * DB 링크 참조 패턴
     */
    private val DB_LINK_PATTERN = Regex(
        """@(\w+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Synonym 변환
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
     *
     * MySQL은 Synonym을 지원하지 않으므로 VIEW로 변환
     */
    private fun convertToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // CREATE SYNONYM → CREATE VIEW
        result = CREATE_SYNONYM_PATTERN.replace(result) { match ->
            val synSchema = match.groupValues[1].ifEmpty { null }
            val synName = match.groupValues[2]
            val targetSchema = match.groupValues[3].ifEmpty { null }
            val targetObject = match.groupValues[4]
            val dbLink = match.groupValues[5].ifEmpty { null }

            appliedRules.add("CREATE SYNONYM $synName → MySQL VIEW")

            // DB 링크가 있는 경우
            if (dbLink != null) {
                warnings.add(ConversionWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "DB Link '@${dbLink}'를 참조하는 Synonym은 MySQL에서 지원되지 않습니다",
                    severity = WarningSeverity.ERROR,
                    suggestion = "FEDERATED 테이블이나 애플리케이션 레벨의 데이터 연동을 고려하세요."
                ))
                return@replace """
-- Cannot convert synonym with DB Link to MySQL
-- Original: CREATE SYNONYM $synName FOR ${targetSchema?.let { "$it." } ?: ""}$targetObject@$dbLink
-- Suggestion: Use FEDERATED table or application-level data federation
""".trim()
            }

            // PUBLIC SYNONYM인 경우 경고
            if (PUBLIC_SYNONYM_PATTERN.containsMatchIn(match.value)) {
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "PUBLIC SYNONYM '$synName'은 MySQL에서 VIEW로 변환됩니다. 권한 설정이 필요합니다.",
                    severity = WarningSeverity.WARNING,
                    suggestion = "VIEW 생성 후 필요한 사용자에게 SELECT 권한을 부여하세요."
                ))
            } else {
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "SYNONYM '$synName'은 MySQL에서 VIEW로 변환됩니다",
                    severity = WarningSeverity.INFO
                ))
            }

            val fullTarget = if (targetSchema != null) "$targetSchema.$targetObject" else targetObject
            val viewName = if (synSchema != null) "$synSchema.$synName" else synName

            """
-- Synonym converted to VIEW
CREATE OR REPLACE VIEW $viewName AS
SELECT * FROM $fullTarget;""".trim()
        }

        // DROP SYNONYM → DROP VIEW
        result = DROP_SYNONYM_PATTERN.replace(result) { match ->
            val schema = match.groupValues[1].ifEmpty { null }
            val synName = match.groupValues[2]

            appliedRules.add("DROP SYNONYM $synName → DROP VIEW")

            val viewName = if (schema != null) "$schema.$synName" else synName
            "DROP VIEW IF EXISTS $viewName;"
        }

        return result
    }

    /**
     * PostgreSQL로 변환
     *
     * PostgreSQL도 Synonym을 직접 지원하지 않으므로 VIEW 또는 스키마 설정으로 변환
     */
    private fun convertToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        // CREATE SYNONYM → CREATE VIEW 또는 스키마 매핑
        result = CREATE_SYNONYM_PATTERN.replace(result) { match ->
            val synSchema = match.groupValues[1].ifEmpty { null }
            val synName = match.groupValues[2]
            val targetSchema = match.groupValues[3].ifEmpty { null }
            val targetObject = match.groupValues[4]
            val dbLink = match.groupValues[5].ifEmpty { null }

            appliedRules.add("CREATE SYNONYM $synName → PostgreSQL VIEW")

            // DB 링크가 있는 경우
            if (dbLink != null) {
                warnings.add(ConversionWarning(
                    type = WarningType.UNSUPPORTED_FUNCTION,
                    message = "DB Link '@${dbLink}'를 참조하는 Synonym은 PostgreSQL에서 직접 지원되지 않습니다",
                    severity = WarningSeverity.ERROR,
                    suggestion = "postgres_fdw 확장을 사용하여 외부 테이블을 설정하세요."
                ))
                return@replace """
-- Cannot convert synonym with DB Link to PostgreSQL
-- Original: CREATE SYNONYM $synName FOR ${targetSchema?.let { "$it." } ?: ""}$targetObject@$dbLink
-- Suggestion: Use postgres_fdw extension for foreign data wrapper
-- Example:
-- CREATE EXTENSION IF NOT EXISTS postgres_fdw;
-- CREATE SERVER ${dbLink}_server FOREIGN DATA WRAPPER postgres_fdw OPTIONS (host 'remote_host', dbname 'remote_db');
-- CREATE FOREIGN TABLE $synName (...) SERVER ${dbLink}_server OPTIONS (table_name '$targetObject');
""".trim()
            }

            // PUBLIC SYNONYM인 경우
            if (PUBLIC_SYNONYM_PATTERN.containsMatchIn(match.value)) {
                warnings.add(ConversionWarning(
                    type = WarningType.SYNTAX_DIFFERENCE,
                    message = "PUBLIC SYNONYM '$synName'은 PostgreSQL의 public 스키마에 VIEW로 생성됩니다",
                    severity = WarningSeverity.WARNING,
                    suggestion = "search_path 설정으로 대체할 수도 있습니다."
                ))

                val fullTarget = if (targetSchema != null) "$targetSchema.$targetObject" else targetObject

                return@replace """
-- PUBLIC SYNONYM converted to public schema VIEW
CREATE OR REPLACE VIEW public.$synName AS
SELECT * FROM $fullTarget;
-- Alternative: SET search_path TO ${targetSchema ?: "public"}, public;""".trim()
            }

            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "SYNONYM '$synName'은 PostgreSQL VIEW로 변환됩니다",
                severity = WarningSeverity.INFO
            ))

            val fullTarget = if (targetSchema != null) "$targetSchema.$targetObject" else targetObject
            val viewName = if (synSchema != null) "$synSchema.$synName" else synName

            """
-- Synonym converted to VIEW
CREATE OR REPLACE VIEW $viewName AS
SELECT * FROM $fullTarget;""".trim()
        }

        // DROP SYNONYM → DROP VIEW
        result = DROP_SYNONYM_PATTERN.replace(result) { match ->
            val schema = match.groupValues[1].ifEmpty { null }
            val synName = match.groupValues[2]

            appliedRules.add("DROP SYNONYM $synName → DROP VIEW")

            val viewName = if (schema != null) "$schema.$synName" else synName
            "DROP VIEW IF EXISTS $viewName CASCADE;"
        }

        return result
    }

    /**
     * Synonym 관련 구문이 있는지 확인
     */
    fun hasSynonymStatements(sql: String): Boolean {
        return CREATE_SYNONYM_PATTERN.containsMatchIn(sql) ||
               DROP_SYNONYM_PATTERN.containsMatchIn(sql)
    }

    /**
     * 정의된 Synonym 목록 추출
     */
    fun getDefinedSynonyms(sql: String): List<SynonymInfo> {
        val synonyms = mutableListOf<SynonymInfo>()

        CREATE_SYNONYM_PATTERN.findAll(sql).forEach { match ->
            val isPublic = PUBLIC_SYNONYM_PATTERN.containsMatchIn(match.value)
            val synSchema = match.groupValues[1].ifEmpty { null }
            val synName = match.groupValues[2]
            val targetSchema = match.groupValues[3].ifEmpty { null }
            val targetObject = match.groupValues[4]
            val dbLink = match.groupValues[5].ifEmpty { null }

            synonyms.add(SynonymInfo(
                name = synName,
                schema = synSchema,
                targetObject = targetObject,
                targetSchema = targetSchema,
                dbLink = dbLink,
                isPublic = isPublic
            ))
        }

        return synonyms
    }

    /**
     * Synonym 사용 참조를 대상 객체로 대체
     *
     * 정의된 Synonym을 실제 대상 객체 이름으로 치환
     */
    fun replaceSynonymReferences(
        sql: String,
        synonymMap: Map<String, String>,
        warnings: MutableList<ConversionWarning>
    ): String {
        var result = sql

        synonymMap.forEach { (synonymName, targetName) ->
            // 단어 경계로 정확히 매칭
            val pattern = Regex("""\b$synonymName\b""", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(result)) {
                result = pattern.replace(result, targetName)
                warnings.add(ConversionWarning(
                    type = WarningType.INFO,
                    message = "Synonym '$synonymName' 참조를 '$targetName'으로 대체했습니다",
                    severity = WarningSeverity.INFO
                ))
            }
        }

        return result
    }

    /**
     * 스키마 매핑 생성 (PostgreSQL search_path 용)
     */
    fun generateSchemaSearchPath(synonyms: List<SynonymInfo>): String {
        val schemas = synonyms
            .mapNotNull { it.targetSchema }
            .distinct()
            .toMutableList()

        if (schemas.isEmpty()) {
            return ""
        }

        schemas.add("public")

        return "SET search_path TO ${schemas.joinToString(", ")};"
    }

    /**
     * Synonym 정보
     */
    data class SynonymInfo(
        /** Synonym 이름 */
        val name: String,
        /** Synonym이 속한 스키마 */
        val schema: String?,
        /** 대상 객체 이름 */
        val targetObject: String,
        /** 대상 객체의 스키마 */
        val targetSchema: String?,
        /** DB 링크 (원격 접근용) */
        val dbLink: String?,
        /** PUBLIC Synonym 여부 */
        val isPublic: Boolean
    ) {
        /** 전체 Synonym 이름 */
        val fullName: String
            get() = if (schema != null) "$schema.$name" else name

        /** 전체 대상 객체 이름 */
        val fullTargetName: String
            get() = buildString {
                if (targetSchema != null) append("$targetSchema.")
                append(targetObject)
                if (dbLink != null) append("@$dbLink")
            }

        /** 원격 Synonym 여부 */
        val isRemote: Boolean
            get() = dbLink != null
    }

    /**
     * Synonym DDL 생성 (Oracle 형식)
     */
    fun generateOracleSynonymDdl(info: SynonymInfo): String {
        return buildString {
            append("CREATE ")
            if (info.isPublic) append("PUBLIC ")
            append("SYNONYM ")
            if (info.schema != null) append("${info.schema}.")
            append(info.name)
            append(" FOR ")
            append(info.fullTargetName)
            append(";")
        }
    }

    /**
     * MySQL용 Synonym 대체 DDL 생성 (VIEW)
     */
    fun generateMySqlAlternative(info: SynonymInfo): String {
        if (info.isRemote) {
            return """
-- Remote synonym cannot be directly converted to MySQL
-- Consider using FEDERATED table:
-- CREATE SERVER ${info.dbLink}_link FOREIGN DATA WRAPPER mysql OPTIONS (HOST 'host', DATABASE 'db');
-- CREATE TABLE ${info.name} (...) ENGINE=FEDERATED CONNECTION='${info.dbLink}_link/${info.targetObject}';
""".trim()
        }

        return """
CREATE OR REPLACE VIEW ${info.fullName} AS
SELECT * FROM ${info.targetSchema?.let { "$it." } ?: ""}${info.targetObject};
""".trim()
    }

    /**
     * PostgreSQL용 Synonym 대체 DDL 생성 (VIEW 또는 FDW)
     */
    fun generatePostgreSqlAlternative(info: SynonymInfo): String {
        if (info.isRemote) {
            return """
-- Remote synonym - use Foreign Data Wrapper
CREATE EXTENSION IF NOT EXISTS postgres_fdw;
CREATE SERVER ${info.dbLink}_server FOREIGN DATA WRAPPER postgres_fdw
    OPTIONS (host 'remote_host', port '5432', dbname 'remote_db');
CREATE USER MAPPING FOR current_user SERVER ${info.dbLink}_server
    OPTIONS (user 'remote_user', password 'password');
-- Import the table or create foreign table manually
IMPORT FOREIGN SCHEMA ${info.targetSchema ?: "public"} LIMIT TO (${info.targetObject})
    FROM SERVER ${info.dbLink}_server INTO ${info.schema ?: "public"};
-- Or create alias view:
-- CREATE VIEW ${info.name} AS SELECT * FROM ${info.targetObject};
""".trim()
        }

        val targetFull = "${info.targetSchema?.let { "$it." } ?: ""}${info.targetObject}"

        return if (info.isPublic) {
            """
-- PUBLIC SYNONYM as public schema view
CREATE OR REPLACE VIEW public.${info.name} AS
SELECT * FROM $targetFull;
GRANT SELECT ON public.${info.name} TO PUBLIC;
""".trim()
        } else {
            """
CREATE OR REPLACE VIEW ${info.fullName} AS
SELECT * FROM $targetFull;
""".trim()
        }
    }
}
