package com.sqlswitcher.converter.preprocessor.processor

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.preprocessor.SyntaxProcessor
import com.sqlswitcher.converter.util.SqlRegexPatterns

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
 * USING INDEX 절 제거 (Oracle 전용)
 * PRIMARY KEY (...) USING INDEX ... → PRIMARY KEY (...)
 */
class UsingIndexProcessor : SyntaxProcessor {
    // USING INDEX 절 패턴 (인덱스명, 테이블스페이스 등 포함)
    private val usingIndexPattern = Regex(
        """\s+USING\s+INDEX\s+[^\s,;)]+(\s*\([^)]*\))?(\s+TABLESPACE\s+[^\s,;)]+)?""",
        RegexOption.IGNORE_CASE
    )

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!sql.uppercase().contains("USING INDEX")) {
            return sql
        }

        val result = usingIndexPattern.replace(sql, "")

        if (result != sql) {
            appliedRules.add("USING INDEX 절 제거")
        }

        return result
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
            match.groupValues[1]  // 큰따옴표 없이 테이블명만 반환
        }
    }
}

/**
 * Oracle 큰따옴표 식별자를 일반 식별자로 변환
 * "TABLE_NAME" → TABLE_NAME
 */
class QuotedIdentifierProcessor : SyntaxProcessor {
    // 큰따옴표로 감싼 식별자 매칭
    private val quotedIdentifierPattern = Regex("\"([A-Za-z_][A-Za-z0-9_]*)\"")

    override fun process(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (!sql.contains("\"")) {
            return sql
        }

        val result = quotedIdentifierPattern.replace(sql) { match ->
            match.groupValues[1]
        }

        if (result != sql) {
            appliedRules.add("Oracle 큰따옴표 식별자 변환")
        }

        return result
    }
}