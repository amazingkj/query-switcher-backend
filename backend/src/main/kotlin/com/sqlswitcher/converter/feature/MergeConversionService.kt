package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import org.springframework.stereotype.Service

/**
 * MERGE 문 변환 서비스
 *
 * 데이터베이스별 UPSERT 구문:
 * - Oracle: MERGE INTO ... USING ... WHEN MATCHED/NOT MATCHED
 * - PostgreSQL: INSERT ... ON CONFLICT ... DO UPDATE/NOTHING
 * - MySQL: INSERT ... ON DUPLICATE KEY UPDATE / REPLACE INTO
 *
 * 지원 기능:
 * - WHEN MATCHED THEN UPDATE
 * - WHEN MATCHED THEN DELETE (Oracle 10g+)
 * - WHEN NOT MATCHED THEN INSERT
 * - USING (SELECT ...) 서브쿼리
 * - USING DUAL (단일 행 UPSERT)
 * - 다중 ON 조건
 */
@Service
class MergeConversionService(
    @Suppress("UNUSED_PARAMETER") private val functionService: FunctionConversionService
) {

    // MERGE 패턴들
    private val MERGE_INTO_PATTERN = Regex(
        """MERGE\s+INTO\s+(\S+)\s+(?:(\w+)\s+)?USING\s+(.+?)\s+ON\s+\((.+?)\)(.*)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val USING_DUAL_PATTERN = Regex(
        """USING\s+DUAL\s+ON""",
        RegexOption.IGNORE_CASE
    )

    private val USING_SELECT_PATTERN = Regex(
        """USING\s*\(\s*SELECT\s+(.+?)\s+FROM\s+(.+?)\s*\)\s*(\w*)\s*ON""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val WHEN_MATCHED_UPDATE_PATTERN = Regex(
        """WHEN\s+MATCHED\s+THEN\s+UPDATE\s+SET\s+(.+?)(?=WHEN|DELETE|$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val WHEN_MATCHED_DELETE_PATTERN = Regex(
        """WHEN\s+MATCHED\s+(?:AND\s+(.+?)\s+)?THEN\s+DELETE""",
        RegexOption.IGNORE_CASE
    )

    private val WHEN_NOT_MATCHED_PATTERN = Regex(
        """WHEN\s+NOT\s+MATCHED\s+THEN\s+INSERT\s*\((.+?)\)\s*VALUES\s*\((.+?)\)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * MERGE 문 변환 (Oracle 스타일 → 타겟 방언)
     */
    fun convertMerge(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return when (sourceDialect) {
            DialectType.ORACLE -> convertOracleMerge(sql, targetDialect, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertPostgreSqlUpsert(sql, targetDialect, warnings, appliedRules)
            DialectType.MYSQL -> convertMySqlUpsert(sql, targetDialect, warnings, appliedRules)
        }
    }

    /**
     * Oracle MERGE 문 변환
     */
    private fun convertOracleMerge(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        when (targetDialect) {
            DialectType.ORACLE -> return sql

            DialectType.POSTGRESQL -> {
                // Oracle MERGE → PostgreSQL INSERT ON CONFLICT
                val parseResult = parseOracleMerge(sql)
                if (parseResult == null) {
                    warnings.add(ConversionWarning(
                        WarningType.MANUAL_REVIEW_NEEDED,
                        "복잡한 MERGE 구문입니다. 수동 변환이 필요합니다.",
                        WarningSeverity.WARNING,
                        "PostgreSQL INSERT ... ON CONFLICT ... DO UPDATE 문법을 참고하세요."
                    ))
                    appliedRules.add("MERGE 구문 감지 - 수동 변환 필요")
                    return "-- PostgreSQL: Use INSERT ... ON CONFLICT syntax\n$sql"
                }

                return buildPostgreSqlUpsert(parseResult, warnings, appliedRules)
            }

            DialectType.MYSQL -> {
                // Oracle MERGE → MySQL INSERT ON DUPLICATE KEY UPDATE
                val parseResult = parseOracleMerge(sql)
                if (parseResult == null) {
                    warnings.add(ConversionWarning(
                        WarningType.MANUAL_REVIEW_NEEDED,
                        "복잡한 MERGE 구문입니다. 수동 변환이 필요합니다.",
                        WarningSeverity.WARNING,
                        "MySQL INSERT ... ON DUPLICATE KEY UPDATE 문법을 참고하세요."
                    ))
                    appliedRules.add("MERGE 구문 감지 - 수동 변환 필요")
                    return "-- MySQL: Use INSERT ... ON DUPLICATE KEY UPDATE syntax\n$sql"
                }

                return buildMySqlUpsert(parseResult, warnings, appliedRules)
            }
        }
    }

    /**
     * PostgreSQL INSERT ON CONFLICT 변환
     */
    private fun convertPostgreSqlUpsert(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        when (targetDialect) {
            DialectType.POSTGRESQL -> return sql

            DialectType.ORACLE -> {
                // PostgreSQL ON CONFLICT → Oracle MERGE
                if (sql.uppercase().contains("ON CONFLICT")) {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "PostgreSQL ON CONFLICT를 Oracle MERGE INTO로 변환해야 합니다.",
                        WarningSeverity.WARNING,
                        "Oracle MERGE INTO 문법을 사용하세요."
                    ))
                    appliedRules.add("ON CONFLICT → MERGE INTO 수동 변환 필요")
                    return "-- Oracle: Use MERGE INTO syntax\n$sql"
                }
                return sql
            }

            DialectType.MYSQL -> {
                // PostgreSQL ON CONFLICT → MySQL ON DUPLICATE KEY UPDATE
                var result = sql

                // ON CONFLICT (col) DO UPDATE SET → ON DUPLICATE KEY UPDATE
                val onConflictPattern = Regex(
                    """ON\s+CONFLICT\s*\([^)]+\)\s+DO\s+UPDATE\s+SET\s+(.+)$""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
                )

                val match = onConflictPattern.find(result)
                if (match != null) {
                    val updateClause = match.groupValues[1]
                    // EXCLUDED.col → VALUES(col) 변환
                    val mysqlUpdateClause = updateClause.replace(
                        Regex("""EXCLUDED\.(\w+)""", RegexOption.IGNORE_CASE)
                    ) { m -> "VALUES(${m.groupValues[1]})" }

                    result = result.replace(match.value, "ON DUPLICATE KEY UPDATE $mysqlUpdateClause")
                    appliedRules.add("ON CONFLICT DO UPDATE → ON DUPLICATE KEY UPDATE")
                }

                // ON CONFLICT DO NOTHING → MySQL에서는 INSERT IGNORE
                val doNothingPattern = Regex(
                    """ON\s+CONFLICT\s*(?:\([^)]+\))?\s+DO\s+NOTHING""",
                    RegexOption.IGNORE_CASE
                )
                if (doNothingPattern.containsMatchIn(result)) {
                    result = result.replace(doNothingPattern, "")
                    result = result.replaceFirst(Regex("INSERT", RegexOption.IGNORE_CASE), "INSERT IGNORE")
                    appliedRules.add("ON CONFLICT DO NOTHING → INSERT IGNORE")
                }

                return result
            }
        }
    }

    /**
     * MySQL INSERT ON DUPLICATE KEY UPDATE 변환
     */
    private fun convertMySqlUpsert(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        when (targetDialect) {
            DialectType.MYSQL -> return sql

            DialectType.POSTGRESQL -> {
                var result = sql

                // ON DUPLICATE KEY UPDATE → ON CONFLICT DO UPDATE
                val duplicateKeyPattern = Regex(
                    """ON\s+DUPLICATE\s+KEY\s+UPDATE\s+(.+)$""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
                )

                val match = duplicateKeyPattern.find(result)
                if (match != null) {
                    val updateClause = match.groupValues[1]
                    // VALUES(col) → EXCLUDED.col 변환
                    val pgUpdateClause = updateClause.replace(
                        Regex("""VALUES\s*\(\s*(\w+)\s*\)""", RegexOption.IGNORE_CASE)
                    ) { m -> "EXCLUDED.${m.groupValues[1]}" }

                    // INSERT 문에서 테이블명과 컬럼 추출 시도
                    val conflictColumn = inferConflictColumn(sql)

                    if (conflictColumn != null) {
                        result = result.replace(match.value, "ON CONFLICT ($conflictColumn) DO UPDATE SET $pgUpdateClause")
                        appliedRules.add("ON DUPLICATE KEY UPDATE → ON CONFLICT ($conflictColumn) DO UPDATE")
                        warnings.add(ConversionWarning(
                            WarningType.SYNTAX_DIFFERENCE,
                            "ON CONFLICT 컬럼이 '$conflictColumn'으로 추론되었습니다.",
                            WarningSeverity.INFO,
                            "추론된 컬럼이 실제 PRIMARY KEY 또는 UNIQUE 컬럼인지 확인하세요."
                        ))
                    } else {
                        warnings.add(ConversionWarning(
                            WarningType.MANUAL_REVIEW_NEEDED,
                            "ON CONFLICT 절에 충돌 컬럼을 지정해야 합니다.",
                            WarningSeverity.WARNING,
                            "ON CONFLICT (primary_key_column) DO UPDATE SET ... 형식으로 변환하세요."
                        ))
                        result = result.replace(match.value, "ON CONFLICT (/* 충돌 컬럼 지정 필요 */) DO UPDATE SET $pgUpdateClause")
                        appliedRules.add("ON DUPLICATE KEY UPDATE → ON CONFLICT DO UPDATE")
                    }
                }

                // INSERT IGNORE → ON CONFLICT DO NOTHING
                if (result.uppercase().contains("INSERT IGNORE")) {
                    result = result.replaceFirst(
                        Regex("INSERT\\s+IGNORE", RegexOption.IGNORE_CASE),
                        "INSERT"
                    )
                    result = result.trimEnd() + " ON CONFLICT DO NOTHING"
                    appliedRules.add("INSERT IGNORE → ON CONFLICT DO NOTHING")
                }

                // REPLACE INTO → INSERT ON CONFLICT DO UPDATE (전체 컬럼)
                if (result.uppercase().startsWith("REPLACE")) {
                    warnings.add(ConversionWarning(
                        WarningType.SYNTAX_DIFFERENCE,
                        "REPLACE INTO는 DELETE + INSERT입니다. PostgreSQL에서는 ON CONFLICT DO UPDATE로 변환하세요.",
                        WarningSeverity.WARNING,
                        "REPLACE INTO는 행을 삭제 후 재삽입하므로 동작이 다를 수 있습니다."
                    ))
                    result = result.replaceFirst(Regex("REPLACE\\s+INTO", RegexOption.IGNORE_CASE), "INSERT INTO")
                    appliedRules.add("REPLACE INTO → INSERT INTO (ON CONFLICT 수동 추가 필요)")
                }

                return result
            }

            DialectType.ORACLE -> {
                // MySQL → Oracle MERGE
                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "MySQL UPSERT 구문을 Oracle MERGE INTO로 수동 변환해야 합니다.",
                    WarningSeverity.WARNING,
                    "MERGE INTO target USING source ON (condition) WHEN MATCHED THEN UPDATE ... WHEN NOT MATCHED THEN INSERT ..."
                ))
                appliedRules.add("MySQL UPSERT → Oracle MERGE 수동 변환 필요")
                return "-- Oracle: Use MERGE INTO syntax\n$sql"
            }
        }
    }

    /**
     * INSERT 문에서 충돌 컬럼 추론
     * - 첫 번째 컬럼이 id, *_id 패턴이면 해당 컬럼 사용
     * - 그 외에는 null 반환
     */
    private fun inferConflictColumn(sql: String): String? {
        // INSERT INTO table (col1, col2, ...) 패턴에서 컬럼 추출
        val insertPattern = Regex(
            """INSERT\s+(?:IGNORE\s+)?INTO\s+\w+\s*\(\s*(\w+)""",
            RegexOption.IGNORE_CASE
        )

        val match = insertPattern.find(sql)
        val firstColumn = match?.groupValues?.get(1)?.lowercase()

        // 첫 번째 컬럼이 id 관련이면 사용
        return when {
            firstColumn == "id" -> "id"
            firstColumn?.endsWith("_id") == true -> firstColumn
            firstColumn == "uuid" -> "uuid"
            firstColumn == "code" -> "code"
            firstColumn == "key" -> "key"
            else -> null
        }
    }

    /**
     * Oracle MERGE 구문 파싱 (강화된 버전)
     */
    private fun parseOracleMerge(sql: String): MergeParseResult? {
        val match = MERGE_INTO_PATTERN.find(sql) ?: return null

        val targetTable = match.groupValues[1].replace("\"", "")
        val targetAlias = match.groupValues[2].ifEmpty { null }
        val sourceExpr = match.groupValues[3].trim()
        val onCondition = match.groupValues[4].trim()
        val clauses = match.groupValues[5].trim()

        // USING DUAL 여부 확인
        val usingDual = USING_DUAL_PATTERN.containsMatchIn(sql)

        // USING (SELECT ...) 서브쿼리 파싱
        val selectMatch = USING_SELECT_PATTERN.find(sql)
        val sourceSelect = if (selectMatch != null) {
            SourceSelectInfo(
                columns = selectMatch.groupValues[1].trim(),
                fromClause = selectMatch.groupValues[2].trim(),
                alias = selectMatch.groupValues[3].ifEmpty { "src" }
            )
        } else null

        // WHEN MATCHED THEN UPDATE
        val matchedUpdateMatch = WHEN_MATCHED_UPDATE_PATTERN.find(clauses)
        val updateSet = matchedUpdateMatch?.groupValues?.get(1)?.trim()?.trimEnd(';')

        // WHEN MATCHED THEN DELETE
        val matchedDeleteMatch = WHEN_MATCHED_DELETE_PATTERN.find(clauses)
        val deleteCondition = matchedDeleteMatch?.groupValues?.get(1)?.trim()
        val hasDelete = matchedDeleteMatch != null

        // WHEN NOT MATCHED THEN INSERT
        val notMatchedMatch = WHEN_NOT_MATCHED_PATTERN.find(clauses)
        val insertColumns = notMatchedMatch?.groupValues?.get(1)?.trim()
        val insertValues = notMatchedMatch?.groupValues?.get(2)?.trim()

        return MergeParseResult(
            targetTable = targetTable,
            targetAlias = targetAlias,
            sourceExpression = sourceExpr,
            onCondition = onCondition,
            updateSet = updateSet,
            insertColumns = insertColumns,
            insertValues = insertValues,
            usingDual = usingDual,
            sourceSelect = sourceSelect,
            hasDelete = hasDelete,
            deleteCondition = deleteCondition
        )
    }

    /**
     * USING (SELECT ...) 정보
     */
    data class SourceSelectInfo(
        val columns: String,
        val fromClause: String,
        val alias: String
    )

    /**
     * PostgreSQL INSERT ON CONFLICT 구문 생성 (강화됨)
     */
    private fun buildPostgreSqlUpsert(
        parsed: MergeParseResult,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // USING DUAL 케이스
        if (parsed.usingDual) {
            return buildPostgreSqlUpsertFromDual(parsed, warnings, appliedRules)
        }

        val sb = StringBuilder()

        if (parsed.insertColumns != null && parsed.insertValues != null) {
            sb.append("INSERT INTO ${parsed.targetTable} (${parsed.insertColumns})\n")

            // USING (SELECT ...) 케이스
            if (parsed.sourceSelect != null) {
                sb.append("SELECT ${parsed.sourceSelect.columns} FROM ${parsed.sourceSelect.fromClause}\n")
                appliedRules.add("MERGE USING (SELECT...) → INSERT ... SELECT 변환")
            } else {
                sb.append("VALUES (${parsed.insertValues})\n")
            }

            val conflictColumn = extractConflictColumn(parsed.onCondition)
            sb.append("ON CONFLICT ($conflictColumn) DO ")

            if (parsed.updateSet != null) {
                val pgUpdateSet = convertToExcludedReference(parsed.updateSet)
                sb.append("UPDATE SET $pgUpdateSet")
                appliedRules.add("MERGE → INSERT ON CONFLICT DO UPDATE 변환")
            } else {
                sb.append("NOTHING")
                appliedRules.add("MERGE → INSERT ON CONFLICT DO NOTHING 변환")
            }

            // DELETE 경고
            if (parsed.hasDelete) {
                warnings.add(ConversionWarning(
                    WarningType.PARTIAL_SUPPORT,
                    "WHEN MATCHED THEN DELETE는 PostgreSQL ON CONFLICT에서 직접 지원되지 않습니다.",
                    WarningSeverity.WARNING,
                    "DELETE 작업을 별도 쿼리로 분리하세요: DELETE FROM ${parsed.targetTable} WHERE ${parsed.deleteCondition ?: "조건"}"
                ))
            }
        } else {
            warnings.add(ConversionWarning(
                WarningType.MANUAL_REVIEW_NEEDED,
                "MERGE 구문에 INSERT 절이 없습니다. 수동 검토가 필요합니다.",
                WarningSeverity.WARNING
            ))
            return "-- MERGE conversion incomplete\n-- Original: ${parsed.targetTable}"
        }

        return sb.toString()
    }

    /**
     * MySQL INSERT ON DUPLICATE KEY UPDATE 구문 생성 (강화됨)
     */
    private fun buildMySqlUpsert(
        parsed: MergeParseResult,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        // USING DUAL 케이스
        if (parsed.usingDual) {
            return buildMySqlUpsertFromDual(parsed, warnings, appliedRules)
        }

        val sb = StringBuilder()

        if (parsed.insertColumns != null && parsed.insertValues != null) {
            sb.append("INSERT INTO ${parsed.targetTable} (${parsed.insertColumns})\n")

            // USING (SELECT ...) 케이스
            if (parsed.sourceSelect != null) {
                sb.append("SELECT ${parsed.sourceSelect.columns} FROM ${parsed.sourceSelect.fromClause}")
                appliedRules.add("MERGE USING (SELECT...) → INSERT ... SELECT 변환")
            } else {
                sb.append("VALUES (${parsed.insertValues})")
            }

            if (parsed.updateSet != null) {
                val mysqlUpdateSet = convertToValuesReference(parsed.updateSet)
                sb.append("\nON DUPLICATE KEY UPDATE $mysqlUpdateSet")
                appliedRules.add("MERGE → INSERT ON DUPLICATE KEY UPDATE 변환")
            }

            // DELETE 경고
            if (parsed.hasDelete) {
                warnings.add(ConversionWarning(
                    WarningType.PARTIAL_SUPPORT,
                    "WHEN MATCHED THEN DELETE는 MySQL ON DUPLICATE KEY에서 지원되지 않습니다.",
                    WarningSeverity.WARNING,
                    "DELETE 작업을 별도 쿼리로 분리하세요: DELETE FROM ${parsed.targetTable} WHERE ${parsed.deleteCondition ?: "조건"}"
                ))
            }
        } else {
            warnings.add(ConversionWarning(
                WarningType.MANUAL_REVIEW_NEEDED,
                "MERGE 구문에 INSERT 절이 없습니다. 수동 검토가 필요합니다.",
                WarningSeverity.WARNING
            ))
            return "-- MERGE conversion incomplete\n-- Original: ${parsed.targetTable}"
        }

        return sb.toString()
    }

    /**
     * ON 조건에서 충돌 컬럼 추출
     */
    private fun extractConflictColumn(onCondition: String): String {
        // t.col = s.col 형식에서 col 추출
        val pattern = Regex("""(\w+)\.(\w+)\s*=\s*(\w+)\.(\w+)""")
        val match = pattern.find(onCondition)
        return match?.groupValues?.get(2) ?: "id /* 충돌 컬럼 확인 필요 */"
    }

    /**
     * MERGE 파싱 결과 (확장됨)
     */
    data class MergeParseResult(
        val targetTable: String,
        val targetAlias: String?,
        val sourceExpression: String,
        val onCondition: String,
        val updateSet: String?,
        val insertColumns: String?,
        val insertValues: String?,
        val usingDual: Boolean = false,
        val sourceSelect: SourceSelectInfo? = null,
        val hasDelete: Boolean = false,
        val deleteCondition: String? = null
    )

    /**
     * USING DUAL → 단일 값 INSERT 변환 (PostgreSQL)
     */
    private fun buildPostgreSqlUpsertFromDual(
        parsed: MergeParseResult,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()

        if (parsed.insertColumns != null && parsed.insertValues != null) {
            sb.append("INSERT INTO ${parsed.targetTable} (${parsed.insertColumns})\n")
            sb.append("VALUES (${parsed.insertValues})\n")

            val conflictColumn = extractConflictColumn(parsed.onCondition)
            sb.append("ON CONFLICT ($conflictColumn) DO ")

            if (parsed.updateSet != null) {
                // 소스 별칭 참조를 EXCLUDED로 변환
                val pgUpdateSet = convertToExcludedReference(parsed.updateSet)
                sb.append("UPDATE SET $pgUpdateSet")
                appliedRules.add("MERGE USING DUAL → INSERT ON CONFLICT DO UPDATE 변환")
            } else {
                sb.append("NOTHING")
                appliedRules.add("MERGE USING DUAL → INSERT ON CONFLICT DO NOTHING 변환")
            }

            if (parsed.hasDelete) {
                warnings.add(ConversionWarning(
                    WarningType.PARTIAL_SUPPORT,
                    "WHEN MATCHED THEN DELETE는 PostgreSQL ON CONFLICT에서 직접 지원되지 않습니다.",
                    WarningSeverity.WARNING,
                    "DELETE 작업은 별도 쿼리로 분리하거나 트리거를 사용하세요."
                ))
            }
        }

        return sb.toString()
    }

    /**
     * USING DUAL → 단일 값 INSERT 변환 (MySQL)
     */
    private fun buildMySqlUpsertFromDual(
        parsed: MergeParseResult,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()

        if (parsed.insertColumns != null && parsed.insertValues != null) {
            sb.append("INSERT INTO ${parsed.targetTable} (${parsed.insertColumns})\n")
            sb.append("VALUES (${parsed.insertValues})")

            if (parsed.updateSet != null) {
                val mysqlUpdateSet = convertToValuesReference(parsed.updateSet)
                sb.append("\nON DUPLICATE KEY UPDATE $mysqlUpdateSet")
                appliedRules.add("MERGE USING DUAL → INSERT ON DUPLICATE KEY UPDATE 변환")
            }

            if (parsed.hasDelete) {
                warnings.add(ConversionWarning(
                    WarningType.PARTIAL_SUPPORT,
                    "WHEN MATCHED THEN DELETE는 MySQL ON DUPLICATE KEY에서 지원되지 않습니다.",
                    WarningSeverity.WARNING,
                    "DELETE 작업은 별도 쿼리로 분리하세요."
                ))
            }
        }

        return sb.toString()
    }

    /**
     * 소스 별칭 참조를 EXCLUDED 참조로 변환 (PostgreSQL)
     * src.col → EXCLUDED.col
     */
    private fun convertToExcludedReference(updateSet: String): String {
        return updateSet.replace(
            Regex("""(\w+)\.(\w+)""")
        ) { match ->
            val alias = match.groupValues[1]
            val column = match.groupValues[2]
            // 소스 테이블 별칭이면 EXCLUDED로 변환
            if (alias.lowercase() in listOf("src", "s", "source", "new", "n")) {
                "EXCLUDED.$column"
            } else {
                match.value
            }
        }
    }

    /**
     * 소스 별칭 참조를 VALUES() 참조로 변환 (MySQL)
     * src.col → VALUES(col)
     */
    private fun convertToValuesReference(updateSet: String): String {
        return updateSet.replace(
            Regex("""(\w+)\.(\w+)""")
        ) { match ->
            val alias = match.groupValues[1]
            val column = match.groupValues[2]
            if (alias.lowercase() in listOf("src", "s", "source", "new", "n")) {
                "VALUES($column)"
            } else {
                match.value
            }
        }
    }
}