package com.sqlswitcher.converter.preprocessor.processor

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.preprocessor.SyntaxProcessor
import com.sqlswitcher.converter.util.SqlRegexPatterns

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