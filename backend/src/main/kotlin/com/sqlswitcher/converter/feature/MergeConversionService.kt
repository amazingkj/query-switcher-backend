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
 */
@Service
class MergeConversionService(
    private val functionService: FunctionConversionService
) {

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
     * Oracle MERGE 구문 파싱
     */
    private fun parseOracleMerge(sql: String): MergeParseResult? {
        // 간단한 MERGE 구문만 파싱 시도
        val mergePattern = Regex(
            """MERGE\s+INTO\s+(\S+)\s+(?:(\w+)\s+)?USING\s+(.+?)\s+ON\s+\((.+?)\)(.*)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val match = mergePattern.find(sql) ?: return null

        val targetTable = match.groupValues[1]
        val targetAlias = match.groupValues[2].ifEmpty { null }
        val sourceExpr = match.groupValues[3].trim()
        val onCondition = match.groupValues[4].trim()
        val clauses = match.groupValues[5].trim()

        // WHEN MATCHED/NOT MATCHED 절 파싱
        val whenMatchedPattern = Regex(
            """WHEN\s+MATCHED\s+THEN\s+UPDATE\s+SET\s+(.+?)(?=WHEN|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val whenNotMatchedPattern = Regex(
            """WHEN\s+NOT\s+MATCHED\s+THEN\s+INSERT\s*\((.+?)\)\s*VALUES\s*\((.+?)\)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val matchedMatch = whenMatchedPattern.find(clauses)
        val notMatchedMatch = whenNotMatchedPattern.find(clauses)

        return MergeParseResult(
            targetTable = targetTable,
            targetAlias = targetAlias,
            sourceExpression = sourceExpr,
            onCondition = onCondition,
            updateSet = matchedMatch?.groupValues?.get(1)?.trim(),
            insertColumns = notMatchedMatch?.groupValues?.get(1)?.trim(),
            insertValues = notMatchedMatch?.groupValues?.get(2)?.trim()
        )
    }

    /**
     * PostgreSQL INSERT ON CONFLICT 구문 생성
     */
    private fun buildPostgreSqlUpsert(
        parsed: MergeParseResult,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()

        if (parsed.insertColumns != null && parsed.insertValues != null) {
            sb.append("INSERT INTO ${parsed.targetTable} (${parsed.insertColumns})\n")
            sb.append("VALUES (${parsed.insertValues})\n")

            // ON CONFLICT 절 - ON 조건에서 컬럼 추출 시도
            val conflictColumn = extractConflictColumn(parsed.onCondition)
            sb.append("ON CONFLICT ($conflictColumn) DO ")

            if (parsed.updateSet != null) {
                sb.append("UPDATE SET ${parsed.updateSet}")
                appliedRules.add("MERGE → INSERT ON CONFLICT DO UPDATE 변환")
            } else {
                sb.append("NOTHING")
                appliedRules.add("MERGE → INSERT ON CONFLICT DO NOTHING 변환")
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
     * MySQL INSERT ON DUPLICATE KEY UPDATE 구문 생성
     */
    private fun buildMySqlUpsert(
        parsed: MergeParseResult,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val sb = StringBuilder()

        if (parsed.insertColumns != null && parsed.insertValues != null) {
            sb.append("INSERT INTO ${parsed.targetTable} (${parsed.insertColumns})\n")
            sb.append("VALUES (${parsed.insertValues})")

            if (parsed.updateSet != null) {
                // VALUES(col) 형식으로 변환
                val mysqlUpdateSet = parsed.updateSet.replace(
                    Regex("""(\w+)\s*=\s*\w+\.(\w+)""")
                ) { m -> "${m.groupValues[1]} = VALUES(${m.groupValues[2]})" }

                sb.append("\nON DUPLICATE KEY UPDATE $mysqlUpdateSet")
                appliedRules.add("MERGE → INSERT ON DUPLICATE KEY UPDATE 변환")
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
     * MERGE 파싱 결과
     */
    data class MergeParseResult(
        val targetTable: String,
        val targetAlias: String?,
        val sourceExpression: String,
        val onCondition: String,
        val updateSet: String?,
        val insertColumns: String?,
        val insertValues: String?
    )
}