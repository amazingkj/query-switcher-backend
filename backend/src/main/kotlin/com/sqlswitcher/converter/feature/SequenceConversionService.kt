package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.registry.DynamicReplacementRule
import com.sqlswitcher.converter.registry.DynamicReplacementRegistry
import com.sqlswitcher.converter.registry.ReplacementRule
import com.sqlswitcher.converter.registry.StringReplacementRegistry
import org.springframework.stereotype.Service

/**
 * 시퀀스 변환 서비스
 */
@Service
class SequenceConversionService {

    // CREATE SEQUENCE 변환 레지스트리
    private val createSequenceRegistry = StringReplacementRegistry().apply {
        // Oracle → MySQL
        registerAll(DialectType.ORACLE, DialectType.MYSQL, listOf(
            ReplacementRule("NOCACHE", "", "시퀀스 NOCACHE 제거"),
            ReplacementRule("NOORDER", "", "시퀀스 NOORDER 제거"),
            ReplacementRule("NOCYCLE", "", "시퀀스 NOCYCLE 제거")
        ))

        // Oracle → PostgreSQL
        registerAll(DialectType.ORACLE, DialectType.POSTGRESQL, listOf(
            ReplacementRule("NOCACHE", "", "시퀀스 NOCACHE 제거"),
            ReplacementRule("NOORDER", "", "시퀀스 NOORDER 제거"),
            ReplacementRule("NOCYCLE", "NO CYCLE", "NOCYCLE → NO CYCLE 변환"),
            ReplacementRule("NOMAXVALUE", "NO MAXVALUE", "NOMAXVALUE → NO MAXVALUE 변환"),
            ReplacementRule("NOMINVALUE", "NO MINVALUE", "NOMINVALUE → NO MINVALUE 변환")
        ))

        // PostgreSQL → Oracle
        registerAll(DialectType.POSTGRESQL, DialectType.ORACLE, listOf(
            ReplacementRule("NO\\s+CYCLE", "NOCYCLE", "NO CYCLE → NOCYCLE 변환"),
            ReplacementRule("NO\\s+MAXVALUE", "NOMAXVALUE", "NO MAXVALUE → NOMAXVALUE 변환"),
            ReplacementRule("NO\\s+MINVALUE", "NOMINVALUE", "NO MINVALUE → NOMINVALUE 변환")
        ))
    }

    // 시퀀스 사용 구문 변환 레지스트리
    private val sequenceUsageRegistry = DynamicReplacementRegistry().apply {
        // Oracle → PostgreSQL: seq_name.NEXTVAL → nextval('seq_name')
        registerAll(DialectType.ORACLE, DialectType.POSTGRESQL, listOf(
            DynamicReplacementRule(
                "(\\w+)\\.NEXTVAL",
                { match -> "nextval('${match.groupValues[1]}')" },
                "시퀀스.NEXTVAL → nextval('시퀀스') 변환"
            ),
            DynamicReplacementRule(
                "(\\w+)\\.CURRVAL",
                { match -> "currval('${match.groupValues[1]}')" },
                "시퀀스.CURRVAL → currval('시퀀스') 변환"
            )
        ))

        // Oracle → MySQL
        registerAll(DialectType.ORACLE, DialectType.MYSQL, listOf(
            DynamicReplacementRule(
                "(\\w+)\\.NEXTVAL",
                { match -> "nextval(${match.groupValues[1]})" },
                "시퀀스.NEXTVAL → MySQL nextval() 변환"
            ),
            DynamicReplacementRule(
                "(\\w+)\\.CURRVAL",
                { match -> "lastval(${match.groupValues[1]})" },
                "시퀀스.CURRVAL → MySQL lastval() 변환"
            )
        ))

        // PostgreSQL → Oracle
        registerAll(DialectType.POSTGRESQL, DialectType.ORACLE, listOf(
            DynamicReplacementRule(
                "nextval\\s*\\(\\s*'(\\w+)'\\s*\\)",
                { match -> "${match.groupValues[1]}.NEXTVAL" },
                "nextval('시퀀스') → 시퀀스.NEXTVAL 변환"
            ),
            DynamicReplacementRule(
                "currval\\s*\\(\\s*'(\\w+)'\\s*\\)",
                { match -> "${match.groupValues[1]}.CURRVAL" },
                "currval('시퀀스') → 시퀀스.CURRVAL 변환"
            )
        ))

        // PostgreSQL → MySQL
        registerAll(DialectType.POSTGRESQL, DialectType.MYSQL, listOf(
            DynamicReplacementRule(
                "nextval\\s*\\(\\s*'(\\w+)'\\s*\\)",
                { match -> "nextval(${match.groupValues[1]})" },
                "nextval('시퀀스') → MySQL nextval() 변환"
            ),
            DynamicReplacementRule(
                "currval\\s*\\(\\s*'(\\w+)'\\s*\\)",
                { match -> "lastval(${match.groupValues[1]})" },
                "currval('시퀀스') → MySQL lastval() 변환"
            )
        ))

        // MySQL → Oracle
        registerAll(DialectType.MYSQL, DialectType.ORACLE, listOf(
            DynamicReplacementRule(
                "nextval\\s*\\(\\s*(\\w+)\\s*\\)",
                { match -> "${match.groupValues[1]}.NEXTVAL" },
                "MySQL nextval() → 시퀀스.NEXTVAL 변환"
            ),
            DynamicReplacementRule(
                "lastval\\s*\\(\\s*(\\w+)\\s*\\)",
                { match -> "${match.groupValues[1]}.CURRVAL" },
                "MySQL lastval() → 시퀀스.CURRVAL 변환"
            )
        ))

        // MySQL → PostgreSQL
        registerAll(DialectType.MYSQL, DialectType.POSTGRESQL, listOf(
            DynamicReplacementRule(
                "nextval\\s*\\(\\s*(\\w+)\\s*\\)",
                { match -> "nextval('${match.groupValues[1]}')" },
                "MySQL nextval() → nextval('시퀀스') 변환"
            ),
            DynamicReplacementRule(
                "lastval\\s*\\(\\s*(\\w+)\\s*\\)",
                { match -> "currval('${match.groupValues[1]}')" },
                "MySQL lastval() → currval('시퀀스') 변환"
            )
        ))
    }

    /**
     * CREATE SEQUENCE 변환
     */
    fun convertCreateSequence(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>,
        schemaOwner: String? = null
    ): String {
        if (sourceDialect == targetDialect) return sql

        // MySQL 대상 경고 추가
        if (targetDialect == DialectType.MYSQL) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL은 기본적으로 시퀀스를 지원하지 않습니다 (8.0 이상에서 지원).",
                severity = WarningSeverity.WARNING,
                suggestion = "AUTO_INCREMENT 또는 테이블+함수 방식으로 시뮬레이션하세요."
            ))
        }

        var result = createSequenceRegistry.apply(sql, sourceDialect, targetDialect, appliedRules)
        appliedRules.add("시퀀스 → ${targetDialect.name} 시퀀스 변환")

        return result
    }

    /**
     * DROP SEQUENCE 변환
     */
    fun convertDropSequence(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (targetDialect == DialectType.MYSQL) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL 8.0 미만에서는 시퀀스가 지원되지 않습니다.",
                severity = WarningSeverity.WARNING
            ))
        }

        appliedRules.add("DROP SEQUENCE 변환")
        return sql
    }

    /**
     * 시퀀스 사용 구문 변환 (NEXTVAL, CURRVAL)
     */
    fun convertSequenceUsage(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) return sql

        val result = sequenceUsageRegistry.apply(sql, sourceDialect, targetDialect, appliedRules)

        // MySQL 대상 경고
        if (sourceDialect == DialectType.ORACLE && targetDialect == DialectType.MYSQL) {
            warnings.add(ConversionWarning(
                type = WarningType.SYNTAX_DIFFERENCE,
                message = "MySQL 8.0 미만에서는 시퀀스 함수가 지원되지 않습니다.",
                severity = WarningSeverity.WARNING,
                suggestion = "AUTO_INCREMENT 또는 테이블 기반 시퀀스를 고려하세요."
            ))
        }

        // MySQL → Oracle LAST_INSERT_ID 경고
        if (sourceDialect == DialectType.MYSQL && targetDialect == DialectType.ORACLE) {
            if (result.uppercase().contains("LAST_INSERT_ID")) {
                warnings.add(ConversionWarning(
                    type = WarningType.MANUAL_REVIEW_NEEDED,
                    message = "LAST_INSERT_ID()는 시퀀스명을 확인하여 수동 변환이 필요합니다.",
                    severity = WarningSeverity.WARNING
                ))
            }
        }

        return result
    }

    /**
     * 시퀀스 관련 문인지 확인
     */
    fun isSequenceRelated(sql: String): Boolean {
        val upper = sql.uppercase()
        return (upper.contains("CREATE") && upper.contains("SEQUENCE")) ||
               (upper.contains("DROP") && upper.contains("SEQUENCE")) ||
               (upper.contains("ALTER") && upper.contains("SEQUENCE")) ||
               upper.contains(".NEXTVAL") ||
               upper.contains(".CURRVAL") ||
               upper.contains("NEXTVAL(") ||
               upper.contains("CURRVAL(") ||
               upper.contains("LASTVAL(")
    }

    /**
     * CREATE SEQUENCE 문인지 확인
     */
    fun isCreateSequence(sql: String): Boolean {
        val upper = sql.uppercase()
        return upper.contains("CREATE") && upper.contains("SEQUENCE") && !upper.contains("DROP")
    }
}