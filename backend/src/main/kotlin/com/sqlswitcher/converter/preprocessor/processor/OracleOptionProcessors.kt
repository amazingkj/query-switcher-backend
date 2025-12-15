package com.sqlswitcher.converter.preprocessor.processor

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.preprocessor.SyntaxProcessor

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
    companion object {
        private val SYSDATE_PATTERN = Regex("""DEFAULT\s+SYSDATE\b""", RegexOption.IGNORE_CASE)
        private val SYSTIMESTAMP_PATTERN = Regex("""DEFAULT\s+SYSTIMESTAMP\b""", RegexOption.IGNORE_CASE)
    }

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        var result = sql

        when (targetDialect) {
            DialectType.MYSQL, DialectType.POSTGRESQL -> {
                result = SYSDATE_PATTERN.replace(result, "DEFAULT CURRENT_TIMESTAMP")
                result = SYSTIMESTAMP_PATTERN.replace(result, "DEFAULT CURRENT_TIMESTAMP")
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