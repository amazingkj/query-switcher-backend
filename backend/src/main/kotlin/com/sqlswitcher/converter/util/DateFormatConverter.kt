package com.sqlswitcher.converter.util

/**
 * 날짜 포맷 변환 유틸리티
 * Oracle, MySQL, PostgreSQL 간의 날짜 포맷 문자열 변환
 */
object DateFormatConverter {

    // Oracle → MySQL 포맷 매핑
    private val oracleToMysqlMap = mapOf(
        "YYYY" to "%Y",
        "YY" to "%y",
        "MM" to "%m",
        "DD" to "%d",
        "HH24" to "%H",
        "HH" to "%h",
        "MI" to "%i",
        "SS" to "%s",
        "DAY" to "%W",
        "DY" to "%a",
        "MON" to "%b",
        "MONTH" to "%M"
    )

    // MySQL → Oracle 포맷 매핑
    private val mysqlToOracleMap = mapOf(
        "%Y" to "YYYY",
        "%y" to "YY",
        "%m" to "MM",
        "%d" to "DD",
        "%H" to "HH24",
        "%h" to "HH",
        "%i" to "MI",
        "%s" to "SS",
        "%W" to "DAY",
        "%a" to "DY",
        "%b" to "MON",
        "%M" to "MONTH"
    )

    /**
     * Oracle 날짜 포맷 → MySQL 날짜 포맷
     */
    fun oracleToMysql(oracleFormat: String): String {
        var result = oracleFormat
        // 긴 패턴부터 변환 (YYYY, HH24 등이 YY, HH보다 먼저 처리되도록)
        oracleToMysqlMap.entries.sortedByDescending { it.key.length }.forEach { (oracle, mysql) ->
            result = result.replace(oracle, mysql)
        }
        return result
    }

    /**
     * MySQL 날짜 포맷 → Oracle 날짜 포맷
     */
    fun mysqlToOracle(mysqlFormat: String): String {
        var result = mysqlFormat
        mysqlToOracleMap.forEach { (mysql, oracle) ->
            result = result.replace(mysql, oracle)
        }
        return result
    }

    /**
     * Oracle 날짜 포맷 → PostgreSQL 날짜 포맷
     * PostgreSQL은 Oracle과 유사한 포맷을 사용
     */
    fun oracleToPostgresql(oracleFormat: String): String {
        // PostgreSQL은 대부분 Oracle 포맷과 호환
        return oracleFormat
    }

    /**
     * MySQL 날짜 포맷 → PostgreSQL 날짜 포맷
     */
    fun mysqlToPostgresql(mysqlFormat: String): String {
        return mysqlToOracle(mysqlFormat)
    }
}