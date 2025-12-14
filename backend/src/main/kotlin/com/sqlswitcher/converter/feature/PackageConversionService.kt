package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.WarningType
import com.sqlswitcher.converter.WarningSeverity
import com.sqlswitcher.converter.feature.pkg.PackageBodyConverter
import com.sqlswitcher.converter.feature.pkg.PackageParser
import org.springframework.stereotype.Service

/**
 * Oracle PACKAGE 변환 서비스
 *
 * Oracle 패키지는 다른 RDBMS에 직접적인 대응 개념이 없음:
 * - PostgreSQL: 스키마 + 함수/프로시저로 분리
 * - MySQL: 개별 프로시저/함수로 분리
 *
 * 분할된 파일:
 * - PackageParser: 패키지 구문 파싱 및 분석
 * - PackageBodyConverter: 본문/파라미터/타입 변환 로직
 */
@Service
class PackageConversionService {

    /**
     * 패키지 문인지 확인
     */
    fun isPackageStatement(sql: String): Boolean = PackageParser.isPackageStatement(sql)

    /**
     * 패키지 바디 문인지 확인
     */
    fun isPackageBodyStatement(sql: String): Boolean = PackageParser.isPackageBodyStatement(sql)

    /**
     * 패키지 변환
     */
    fun convertPackage(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql

        return when (targetDialect) {
            DialectType.POSTGRESQL -> convertToPostgreSql(sql, warnings, appliedRules)
            DialectType.MYSQL -> convertToMySql(sql, warnings, appliedRules)
            else -> sql
        }
    }

    /**
     * Oracle 패키지 → PostgreSQL 변환
     */
    private fun convertToPostgreSql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val packageInfo = PackageParser.parsePackage(sql)

        if (packageInfo == null) {
            warnings.add(ConversionWarning(
                WarningType.MANUAL_REVIEW_NEEDED,
                "패키지 구문을 파싱할 수 없습니다.",
                WarningSeverity.WARNING
            ))
            return sql
        }

        val result = StringBuilder()

        // 가이드 주석
        result.append("-- =====================================================\n")
        result.append("-- Oracle PACKAGE '${packageInfo.name}' → PostgreSQL 변환\n")
        result.append("-- PostgreSQL은 패키지를 직접 지원하지 않습니다.\n")
        result.append("-- 스키마 + 개별 함수/프로시저로 분리하여 구현합니다.\n")
        result.append("-- =====================================================\n\n")

        // 스키마 생성
        result.append("-- 패키지를 스키마로 변환\n")
        result.append("CREATE SCHEMA IF NOT EXISTS ${packageInfo.name};\n\n")

        if (packageInfo.isBody) {
            result.append(PackageBodyConverter.extractAndConvertToPostgreSql(
                packageInfo.name, packageInfo.body, warnings, appliedRules
            ))
        } else {
            result.append("-- 패키지 사양 (선언부)\n")
            result.append("-- PostgreSQL에서는 함수 본문이 필요합니다.\n")
            result.append("-- PACKAGE BODY의 변환 결과를 사용하세요.\n\n")
            result.append(PackageParser.extractDeclarations(packageInfo.name, packageInfo.body))
        }

        warnings.add(ConversionWarning(
            WarningType.PARTIAL_SUPPORT,
            "Oracle 패키지 '${packageInfo.name}'을 PostgreSQL 스키마 + 함수로 변환했습니다.",
            WarningSeverity.WARNING,
            "패키지 변수와 초기화 블록은 수동 검토가 필요합니다. " +
            "패키지 호출부 (${packageInfo.name}.procedure_name)도 스키마 접두사로 수정하세요."
        ))
        appliedRules.add("Oracle PACKAGE → PostgreSQL 스키마 + 함수 변환")

        return result.toString()
    }

    /**
     * Oracle 패키지 → MySQL 변환
     */
    private fun convertToMySql(
        sql: String,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        val packageInfo = PackageParser.parsePackage(sql)

        if (packageInfo == null) {
            warnings.add(ConversionWarning(
                WarningType.MANUAL_REVIEW_NEEDED,
                "패키지 구문을 파싱할 수 없습니다.",
                WarningSeverity.WARNING
            ))
            return sql
        }

        val result = StringBuilder()

        // 가이드 주석
        result.append("-- =====================================================\n")
        result.append("-- Oracle PACKAGE '${packageInfo.name}' → MySQL 변환\n")
        result.append("-- MySQL은 패키지를 지원하지 않습니다.\n")
        result.append("-- 개별 프로시저/함수로 분리하여 구현합니다.\n")
        result.append("-- 패키지명을 접두사로 사용: ${packageInfo.name}_procedure_name\n")
        result.append("-- =====================================================\n\n")

        if (packageInfo.isBody) {
            result.append(PackageBodyConverter.extractAndConvertToMySql(
                packageInfo.name, packageInfo.body, warnings, appliedRules
            ))
        } else {
            result.append("-- 패키지 사양 (선언부)\n")
            result.append("-- MySQL에서는 프로시저/함수 본문이 필요합니다.\n")
            result.append("-- PACKAGE BODY의 변환 결과를 사용하세요.\n\n")
            result.append(PackageParser.extractDeclarations(packageInfo.name, packageInfo.body))
        }

        warnings.add(ConversionWarning(
            WarningType.PARTIAL_SUPPORT,
            "Oracle 패키지 '${packageInfo.name}'을 MySQL 개별 프로시저/함수로 변환했습니다.",
            WarningSeverity.WARNING,
            "패키지 변수는 MySQL에서 지원되지 않습니다. 세션 변수 또는 테이블로 대체하세요. " +
            "패키지 호출부 (${packageInfo.name}.procedure_name)를 ${packageInfo.name}_procedure_name으로 수정하세요."
        ))
        appliedRules.add("Oracle PACKAGE → MySQL 개별 프로시저/함수 변환")

        return result.toString()
    }

    /**
     * 패키지 호출부 변환 (package.procedure → schema.procedure 또는 package_procedure)
     */
    fun convertPackageCall(
        sql: String,
        packageName: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql

        var result = sql

        when (targetDialect) {
            DialectType.POSTGRESQL -> {
                appliedRules.add("패키지 호출 유지 (PostgreSQL 스키마)")
            }
            DialectType.MYSQL -> {
                val callPattern = Regex("""${packageName}\.(\w+)""", RegexOption.IGNORE_CASE)
                result = result.replace(callPattern) { m ->
                    "${packageName}_${m.groupValues[1]}"
                }
                appliedRules.add("패키지 호출 변환: $packageName.xxx → ${packageName}_xxx")
            }
            else -> {}
        }

        return result
    }

    /**
     * DROP PACKAGE 변환
     */
    fun convertDropPackage(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect != DialectType.ORACLE) return sql

        val dropPattern = Regex(
            """DROP\s+PACKAGE\s+(BODY\s+)?(\w+)""",
            RegexOption.IGNORE_CASE
        )
        val match = dropPattern.find(sql) ?: return sql

        val packageName = match.groupValues[2]

        return when (targetDialect) {
            DialectType.POSTGRESQL -> {
                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "DROP PACKAGE를 DROP SCHEMA로 변환했습니다. 스키마 내 모든 객체가 삭제됩니다.",
                    WarningSeverity.WARNING
                ))
                appliedRules.add("DROP PACKAGE → DROP SCHEMA")
                "DROP SCHEMA IF EXISTS $packageName CASCADE;"
            }
            DialectType.MYSQL -> {
                warnings.add(ConversionWarning(
                    WarningType.MANUAL_REVIEW_NEEDED,
                    "MySQL에서 패키지의 개별 프로시저/함수를 수동으로 삭제해야 합니다.",
                    WarningSeverity.WARNING,
                    "DROP PROCEDURE ${packageName}_xxx, DROP FUNCTION ${packageName}_xxx 형식으로 삭제하세요."
                ))
                appliedRules.add("DROP PACKAGE → 수동 삭제 필요")
                "-- MySQL: Drop individual procedures/functions with prefix '${packageName}_'\n-- DROP PROCEDURE IF EXISTS ${packageName}_procedure_name;\n-- DROP FUNCTION IF EXISTS ${packageName}_function_name;"
            }
            else -> sql
        }
    }
}