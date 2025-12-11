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
 */

// 이 파일은 패키지의 존재를 보장하기 위한 마커 파일입니다.
// 실제 클래스들은 각각의 파일에 정의되어 있습니다:
// - ConstraintInfo.kt: ForeignKeyInfo, UniqueConstraintInfo, CheckConstraintInfo
// - IndexInfo.kt: IndexColumnOption, FunctionBasedIndexInfo
// - PartitionInfo.kt: PartitionType, PartitionInfo, PartitionDefinition, SubpartitionDefinition
// - ProcedureInfo.kt: ProcedureParameter, ProcedureInfo
// - QueryInfo.kt: RecursiveCteInfo, WindowFunctionInfo, MergeStatementInfo, UpdateJoinInfo, DeleteJoinInfo, PivotInfo, UnpivotInfo