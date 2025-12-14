@file:Suppress("unused")
package com.sqlswitcher.converter.model

/**
 * 모델 클래스들의 패키지 export 파일
 *
 * 기존 코드 호환성을 위해 제공됩니다.
 * 새 코드에서는 직접 개별 파일을 import하는 것을 권장합니다.
 *
 * 사용 예시:
 * import com.sqlswitcher.converter.model.*
 *
 * 또는 개별 import:
 * import com.sqlswitcher.converter.model.ForeignKeyInfo
 * import com.sqlswitcher.converter.model.PartitionInfo
 *
 * 모델 클래스 파일:
 * - ConstraintInfo.kt: ForeignKeyInfo, UniqueConstraintInfo, CheckConstraintInfo
 * - IndexInfo.kt: IndexColumnOption, FunctionBasedIndexInfo
 * - PartitionInfo.kt: PartitionType, PartitionInfo, PartitionDefinition, SubpartitionDefinition
 * - ProcedureInfo.kt: ProcedureParameter, ProcedureInfo
 * - QueryInfo.kt: RecursiveCteInfo, WindowFunctionInfo, MergeStatementInfo, UpdateJoinInfo, DeleteJoinInfo, PivotInfo, UnpivotInfo
 *
 * 변환 로직 파일 (확장 함수):
 * - QueryInfoConverters.kt: toDialect() 확장 함수들 (RecursiveCteInfo, WindowFunctionInfo 등)
 * - ProcedureInfoConverters.kt: toDialect() 확장 함수들 (ProcedureParameter, ProcedureInfo)
 */

// 이 파일은 패키지의 존재를 보장하기 위한 마커 파일입니다.
// 데이터 모델과 변환 로직이 분리되어 있습니다:
// - 데이터 모델: QueryInfo.kt, ProcedureInfo.kt 등 (순수 데이터 클래스)
// - 변환 로직: QueryInfoConverters.kt, ProcedureInfoConverters.kt (확장 함수)