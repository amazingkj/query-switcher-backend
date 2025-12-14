@file:Suppress("unused")
package com.sqlswitcher.converter.model

/**
 * QueryInfo 데이터 클래스들의 방언별 변환 로직
 *
 * 데이터 모델과 변환 로직을 분리하여 단일 책임 원칙을 준수합니다.
 *
 * 변환 로직은 기능별로 분리된 파일에서 구현됩니다:
 * - converter/CteConverters.kt: RecursiveCteInfo 변환
 * - converter/WindowFunctionConverters.kt: WindowFunctionInfo 변환
 * - converter/MergeJoinConverters.kt: MergeStatementInfo, UpdateJoinInfo, DeleteJoinInfo 변환
 * - converter/PivotConverters.kt: PivotInfo, UnpivotInfo 변환
 *
 * 사용 예시:
 * ```kotlin
 * import com.sqlswitcher.converter.model.converter.toDialect
 *
 * val cteInfo: RecursiveCteInfo = ...
 * val result = cteInfo.toDialect(DialectType.MYSQL, warnings)
 * ```
 */

// 이 파일은 문서화 목적으로 유지됩니다.
// 실제 변환 로직은 converter 패키지의 파일들에서 구현됩니다.