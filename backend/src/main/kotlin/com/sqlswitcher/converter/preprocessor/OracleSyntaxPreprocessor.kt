package com.sqlswitcher.converter.preprocessor

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.util.SqlRegexPatterns
import org.springframework.stereotype.Component

/**
 * Oracle 특화 문법 전처리기
 *
 * JSQLParser가 파싱하지 못하는 Oracle 특화 문법을 처리합니다.
 * 각 전처리 규칙은 독립적인 Processor로 구현되어 있습니다.
 */
@Component
class OracleSyntaxPreprocessor {

    private val processors: List<SyntaxProcessor> = listOf(
        PartitionProcessor(),
        LocalGlobalIndexProcessor(),
        LobStorageProcessor(),
        TablespaceProcessor(),
        StorageClauseProcessor(),
        PhysicalOptionsProcessor(),
        ConstraintStateProcessor(),
        CompressProcessor(),
        CommentOnProcessor(),
        SchemaTableProcessor(),
        SegmentCreationProcessor(),
        LoggingProcessor(),
        ParallelProcessor(),
        CacheProcessor(),
        ResultCacheProcessor(),
        RowDependenciesProcessor(),
        MonitoringProcessor(),
        DefaultFunctionProcessor(),
        FlashbackProcessor(),
        RowMovementProcessor()
    )

    /**
     * Oracle 특화 문법 전처리 수행
     */
    fun preprocess(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        for (processor in processors) {
            result = processor.process(result, targetDialect, warnings, appliedRules)
        }

        // 연속된 빈 줄 정리
        result = SqlRegexPatterns.MULTIPLE_BLANK_LINES.replace(result, "\n\n")
        result = SqlRegexPatterns.MULTIPLE_BLANK_LINES.replace(result, "\n\n")

        return result.trim()
    }
}

/**
 * 개별 문법 처리기 인터페이스
 */
interface SyntaxProcessor {
    fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String
}

/**
 * RANGE/LIST/HASH 파티션 처리
 */
class PartitionProcessor : SyntaxProcessor {
    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!sql.contains(SqlRegexPatterns.PARTITION_BY)) {
            return sql
        }

        when (targetDialect) {
            DialectType.MYSQL -> {
                warnings.add(ConversionWarning(
                    WarningType.SYNTAX_DIFFERENCE,
                    "Oracle 파티션 구문이 감지되었습니다. MySQL 파티션 문법으로 수동 조정이 필요할 수 있습니다.",
                    WarningSeverity.WARNING
                ))
            }
            DialectType.POSTGRESQL -> {
                warnings.add(ConversionWarning(
                    WarningType.SYNTAX_DIFFERENCE,
                    "Oracle 파티션 구문이 감지되었습니다. PostgreSQL 파티션 문법으로 수동 조정이 필요할 수 있습니다.",
                    WarningSeverity.WARNING
                ))
            }
            else -> {}
        }
        appliedRules.add("파티션 구문 감지됨")
        return sql
    }
}

/**
 * LOCAL/GLOBAL 인덱스 키워드 제거
 */
class LocalGlobalIndexProcessor : SyntaxProcessor {
    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        if (SqlRegexPatterns.LOCAL_INDEX.containsMatchIn(result)) {
            result = SqlRegexPatterns.LOCAL_INDEX.replace(result, "")
            appliedRules.add("LOCAL 인덱스 키워드 제거")
            warnings.add(ConversionWarning(
                WarningType.SYNTAX_DIFFERENCE,
                "Oracle LOCAL 인덱스 키워드가 제거되었습니다.",
                WarningSeverity.INFO
            ))
        }

        if (SqlRegexPatterns.GLOBAL_INDEX.containsMatchIn(result)) {
            result = SqlRegexPatterns.GLOBAL_INDEX.replace(result, " ")
            appliedRules.add("GLOBAL 인덱스 키워드 제거")
        }

        return result
    }
}

/**
 * SECUREFILE/BASICFILE LOB 옵션 제거
 */
class LobStorageProcessor : SyntaxProcessor {
    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!SqlRegexPatterns.LOB_STORAGE.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("LOB 저장소 옵션(SECUREFILE/BASICFILE) 제거")
        return SqlRegexPatterns.LOB_STORAGE.replace(sql, " ")
    }
}

/**
 * TABLESPACE 절 처리
 */
class TablespaceProcessor : SyntaxProcessor {
    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!SqlRegexPatterns.TABLESPACE.containsMatchIn(sql)) {
            return sql
        }

        if (targetDialect == DialectType.MYSQL) {
            appliedRules.add("TABLESPACE 절 제거 (MySQL)")
            return SqlRegexPatterns.TABLESPACE.replace(sql, "")
        }

        return sql
    }
}

/**
 * STORAGE 절 제거
 */
class StorageClauseProcessor : SyntaxProcessor {
    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!SqlRegexPatterns.STORAGE_CLAUSE.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("STORAGE 절 제거")
        return SqlRegexPatterns.STORAGE_CLAUSE.replace(sql, "")
    }
}

/**
 * PCTFREE, PCTUSED, INITRANS 등 Oracle 물리적 옵션 제거
 */
class PhysicalOptionsProcessor : SyntaxProcessor {
    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!SqlRegexPatterns.PHYSICAL_OPTIONS_LINE.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("Oracle 물리적 저장 옵션 제거")
        return SqlRegexPatterns.PHYSICAL_OPTIONS_LINE.replace(sql, "")
    }
}

/**
 * ENABLE/DISABLE 제약조건 옵션 제거
 */
class ConstraintStateProcessor : SyntaxProcessor {
    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!SqlRegexPatterns.CONSTRAINT_STATE.containsMatchIn(sql)) {
            return sql
        }

        if (targetDialect != DialectType.ORACLE) {
            appliedRules.add("제약조건 상태 옵션 제거")
            return SqlRegexPatterns.CONSTRAINT_STATE.replace(sql, "")
        }

        return sql
    }
}

/**
 * COMPRESS/NOCOMPRESS 옵션 제거
 */
class CompressProcessor : SyntaxProcessor {
    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!SqlRegexPatterns.COMPRESS_LINE.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("압축 옵션 제거")
        return SqlRegexPatterns.COMPRESS_LINE.replace(sql, "")
    }
}

/**
 * COMMENT ON 구문 처리 (Oracle → MySQL)
 */
class CommentOnProcessor : SyntaxProcessor {
    private val commentPattern = Regex(
        """COMMENT\s+ON\s+(COLUMN|TABLE)\s+[^\s]+\s+IS\s+'[^']*'\s*;?""",
        RegexOption.IGNORE_CASE
    )

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (targetDialect != DialectType.MYSQL || !commentPattern.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("COMMENT ON 구문 제거 (MySQL 미지원)")
        warnings.add(ConversionWarning(
            WarningType.SYNTAX_DIFFERENCE,
            "Oracle COMMENT ON 구문이 제거되었습니다. MySQL에서는 컬럼 정의 시 COMMENT 절을 사용하세요.",
            WarningSeverity.WARNING,
            "ALTER TABLE ... MODIFY COLUMN ... COMMENT '...' 형식을 사용하세요."
        ))
        return commentPattern.replace(sql, "")
    }
}

/**
 * 스키마.테이블명에서 스키마 제거
 */
class SchemaTableProcessor : SyntaxProcessor {
    private val schemaTablePattern = Regex("""["']?\w+["']?\.["']?(\w+)["']?""")

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!schemaTablePattern.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("스키마 접두사 제거")
        return schemaTablePattern.replace(sql) { match ->
            "\"${match.groupValues[1]}\""
        }
    }
}

/**
 * SEGMENT CREATION 옵션 제거
 */
class SegmentCreationProcessor : SyntaxProcessor {
    private val pattern = Regex(
        """\s*SEGMENT\s+CREATION\s+(IMMEDIATE|DEFERRED)\s*""",
        RegexOption.IGNORE_CASE
    )

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!pattern.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("SEGMENT CREATION 옵션 제거")
        return pattern.replace(sql, " ")
    }
}

/**
 * LOGGING/NOLOGGING 옵션 제거
 */
class LoggingProcessor : SyntaxProcessor {
    private val pattern = Regex("""\s*(NO)?LOGGING\b""", RegexOption.IGNORE_CASE)

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!pattern.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("LOGGING/NOLOGGING 옵션 제거")
        return pattern.replace(sql, "")
    }
}

/**
 * PARALLEL 옵션 제거
 */
class ParallelProcessor : SyntaxProcessor {
    private val pattern = Regex(
        """\s*(NO)?PARALLEL(\s+\d+)?\s*""",
        RegexOption.IGNORE_CASE
    )

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!pattern.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("PARALLEL 옵션 제거")
        return pattern.replace(sql, " ")
    }
}

/**
 * CACHE/NOCACHE 옵션 제거
 */
class CacheProcessor : SyntaxProcessor {
    private val pattern = Regex("""\s*(NO)?CACHE\b""", RegexOption.IGNORE_CASE)

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!pattern.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("CACHE/NOCACHE 옵션 제거")
        return pattern.replace(sql, "")
    }
}

/**
 * RESULT_CACHE 힌트 제거
 */
class ResultCacheProcessor : SyntaxProcessor {
    private val pattern = Regex(
        """/\*\+?\s*RESULT_CACHE[^*]*\*/""",
        RegexOption.IGNORE_CASE
    )

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!pattern.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("RESULT_CACHE 힌트 제거")
        return pattern.replace(sql, "")
    }
}

/**
 * ROWDEPENDENCIES/NOROWDEPENDENCIES 제거
 */
class RowDependenciesProcessor : SyntaxProcessor {
    private val pattern = Regex("""\s*(NO)?ROWDEPENDENCIES\b""", RegexOption.IGNORE_CASE)

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!pattern.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("ROWDEPENDENCIES 옵션 제거")
        return pattern.replace(sql, "")
    }
}

/**
 * MONITORING/NOMONITORING 제거
 */
class MonitoringProcessor : SyntaxProcessor {
    private val pattern = Regex("""\s*(NO)?MONITORING\b""", RegexOption.IGNORE_CASE)

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!pattern.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("MONITORING 옵션 제거")
        return pattern.replace(sql, "")
    }
}

/**
 * DEFAULT 절의 Oracle 함수 변환
 */
class DefaultFunctionProcessor : SyntaxProcessor {
    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.MYSQL, DialectType.POSTGRESQL -> {
                result = result.replace(
                    Regex("""DEFAULT\s+SYSDATE\b""", RegexOption.IGNORE_CASE),
                    "DEFAULT CURRENT_TIMESTAMP"
                )
                result = result.replace(
                    Regex("""DEFAULT\s+SYSTIMESTAMP\b""", RegexOption.IGNORE_CASE),
                    "DEFAULT CURRENT_TIMESTAMP"
                )
            }
            else -> {}
        }

        return result
    }
}

/**
 * FLASHBACK 관련 구문 제거
 */
class FlashbackProcessor : SyntaxProcessor {
    private val pattern = Regex(
        """\s*FLASHBACK\s+ARCHIVE[^;]*""",
        RegexOption.IGNORE_CASE
    )

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!pattern.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("FLASHBACK ARCHIVE 옵션 제거")
        return pattern.replace(sql, "")
    }
}

/**
 * ROW MOVEMENT 옵션 제거
 */
class RowMovementProcessor : SyntaxProcessor {
    private val pattern = Regex(
        """\s*(ENABLE|DISABLE)\s+ROW\s+MOVEMENT\s*""",
        RegexOption.IGNORE_CASE
    )

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!pattern.containsMatchIn(sql)) {
            return sql
        }

        appliedRules.add("ROW MOVEMENT 옵션 제거")
        return pattern.replace(sql, " ")
    }
}