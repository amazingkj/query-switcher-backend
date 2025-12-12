package com.sqlswitcher.converter.feature

import com.sqlswitcher.converter.DialectType
import com.sqlswitcher.converter.ConversionWarning
import com.sqlswitcher.converter.feature.procedure.OracleProcedureConverter
import com.sqlswitcher.converter.feature.procedure.PostgreSqlProcedureConverter
import com.sqlswitcher.converter.feature.procedure.MySqlProcedureConverter
import org.springframework.stereotype.Service

/**
 * STORED PROCEDURE / FUNCTION 변환 서비스
 *
 * 데이터베이스별 프로시저 문법:
 * - Oracle: PL/SQL (CREATE PROCEDURE ... IS ... BEGIN ... EXCEPTION ... END)
 * - PostgreSQL: PL/pgSQL (CREATE FUNCTION ... AS $$ ... $$ LANGUAGE plpgsql)
 * - MySQL: CREATE PROCEDURE ... BEGIN ... END
 *
 * 실제 변환 로직은 각 DB별 컨버터 클래스에 위임:
 * - OracleProcedureConverter: Oracle PL/SQL 변환
 * - PostgreSqlProcedureConverter: PostgreSQL PL/pgSQL 변환
 * - MySqlProcedureConverter: MySQL 프로시저 변환
 */
@Service
class ProcedureConversionService(
    private val dataTypeService: DataTypeConversionService,
    private val functionService: FunctionConversionService
) {

    /**
     * PROCEDURE/FUNCTION 변환
     */
    fun convertProcedure(
        sql: String,
        sourceDialect: DialectType,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        if (sourceDialect == targetDialect) return sql

        return when (sourceDialect) {
            DialectType.ORACLE -> convertOracleProcedure(sql, targetDialect, warnings, appliedRules)
            DialectType.POSTGRESQL -> convertPostgreSqlFunction(sql, targetDialect, warnings, appliedRules)
            DialectType.MYSQL -> convertMySqlProcedure(sql, targetDialect, warnings, appliedRules)
        }
    }

    /**
     * Oracle PL/SQL 프로시저 변환
     */
    private fun convertOracleProcedure(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return when (targetDialect) {
            DialectType.POSTGRESQL -> OracleProcedureConverter.convertToPostgreSql(sql, warnings, appliedRules)
            DialectType.MYSQL -> OracleProcedureConverter.convertToMySql(sql, warnings, appliedRules)
            else -> sql
        }
    }

    /**
     * PostgreSQL PL/pgSQL 함수 변환
     */
    private fun convertPostgreSqlFunction(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return when (targetDialect) {
            DialectType.ORACLE -> PostgreSqlProcedureConverter.convertToOracle(sql, warnings, appliedRules)
            DialectType.MYSQL -> PostgreSqlProcedureConverter.convertToMySql(sql, warnings, appliedRules)
            else -> sql
        }
    }

    /**
     * MySQL 프로시저 변환
     */
    private fun convertMySqlProcedure(
        sql: String,
        targetDialect: DialectType,
        warnings: MutableList<ConversionWarning>,
        appliedRules: MutableList<String>
    ): String {
        return when (targetDialect) {
            DialectType.ORACLE -> MySqlProcedureConverter.convertToOracle(sql, warnings, appliedRules)
            DialectType.POSTGRESQL -> MySqlProcedureConverter.convertToPostgreSql(sql, warnings, appliedRules)
            else -> sql
        }
    }
}