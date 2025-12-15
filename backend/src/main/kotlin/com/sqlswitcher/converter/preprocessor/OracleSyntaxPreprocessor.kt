package com.sqlswitcher.converter.preprocessor

import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.preprocessor.processor.*
import com.sqlswitcher.converter.util.SqlRegexPatterns
import org.springframework.stereotype.Component

/**
 * Oracle 특화 문법 전처리기
 *
 * JSQLParser가 파싱하지 못하는 Oracle 특화 문법을 처리합니다.
 * 각 전처리 규칙은 독립적인 Processor로 구현되어 있습니다.
 *
 * Processor 파일:
 * - IndexStorageProcessors.kt: 인덱스/스토리지 관련 (LOCAL, GLOBAL, LOB, TABLESPACE, STORAGE, PCTFREE, COMPRESS)
 * - ConstraintProcessors.kt: 제약조건/파티션 관련 (PARTITION, CONSTRAINT, COMMENT ON, SCHEMA)
 * - OracleOptionProcessors.kt: Oracle 전용 옵션 (SEGMENT, LOGGING, PARALLEL, CACHE, FLASHBACK 등)
 */
@Component
class OracleSyntaxPreprocessor {

    private val processors: List<SyntaxProcessor> = listOf(
        // 파티션/제약조건
        PartitionProcessor(),
        ConstraintStateProcessor(),
        CommentOnProcessor(),
        SchemaTableProcessor(),

        // 인덱스/스토리지
        LocalGlobalIndexProcessor(),
        LobStorageProcessor(),
        TablespaceProcessor(),
        StorageClauseProcessor(),
        PhysicalOptionsProcessor(),
        CompressProcessor(),

        // Oracle 전용 옵션
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