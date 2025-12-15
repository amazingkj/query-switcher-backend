package com.sqlswitcher.converter.model

/**
 * 재귀 CTE (WITH RECURSIVE) 정보
 */
data class RecursiveCteInfo(
    val cteName: String,
    val columns: List<String>,
    val anchorQuery: String,        // 비재귀(앵커) 부분
    val recursiveQuery: String,     // 재귀 부분
    val mainQuery: String           // 최종 SELECT
)

/**
 * 윈도우 함수 정보
 */
data class WindowFunctionInfo(
    val functionName: String,           // ROW_NUMBER, RANK, DENSE_RANK, LAG, LEAD 등
    val arguments: List<String> = emptyList(),  // 함수 인자 (LAG, LEAD의 경우)
    val partitionBy: List<String> = emptyList(),
    val orderBy: List<OrderByColumn> = emptyList(),
    val alias: String? = null
) {
    data class OrderByColumn(
        val column: String,
        val direction: String = "ASC",  // ASC, DESC
        val nullsPosition: String? = null  // NULLS FIRST, NULLS LAST
    )
}

/**
 * MERGE (UPSERT) 문 정보
 */
data class MergeStatementInfo(
    val targetTable: String,
    val sourceTable: String?,           // USING 절의 테이블 또는 서브쿼리
    val sourceValues: List<String>?,    // INSERT 값들 (테이블이 아닌 경우)
    val matchCondition: String,         // ON 절 조건
    val matchedUpdate: Map<String, String>?,    // WHEN MATCHED THEN UPDATE SET
    val notMatchedInsert: Pair<List<String>, List<String>>?  // (columns, values)
)

/**
 * UPDATE JOIN 정보
 */
data class UpdateJoinInfo(
    val targetTable: String,
    val targetAlias: String?,
    val joinTable: String,
    val joinAlias: String?,
    val joinCondition: String,
    val setClause: Map<String, String>,
    val whereClause: String?
)

/**
 * DELETE JOIN 정보
 */
data class DeleteJoinInfo(
    val targetTable: String,
    val targetAlias: String?,
    val joinTable: String,
    val joinAlias: String?,
    val joinCondition: String,
    val whereClause: String?
)

/**
 * PIVOT 정보
 */
data class PivotInfo(
    val sourceTable: String,
    val aggregateFunction: String,      // SUM, COUNT, AVG 등
    val aggregateColumn: String,        // 집계할 컬럼
    val pivotColumn: String,            // 피벗 기준 컬럼
    val pivotValues: List<String>,      // 피벗할 값들
    val groupByColumns: List<String>    // 그룹화 컬럼들
)

/**
 * UNPIVOT 정보
 */
data class UnpivotInfo(
    val sourceTable: String,
    val valueColumn: String,            // 값이 들어갈 컬럼명
    val nameColumn: String,             // 원래 컬럼명이 들어갈 컬럼명
    val unpivotColumns: List<String>,   // 언피벗할 컬럼들
    val keepColumns: List<String>       // 유지할 컬럼들
)