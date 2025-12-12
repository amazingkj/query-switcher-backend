import React from 'react';
import type { ExportTabProps } from './types';

export const ExportTab: React.FC<ExportTabProps> = ({
  onExportData,
  onClearData,
}) => {
  return (
    <div className="space-y-6">
      <h4 className="text-lg font-semibold text-gray-800">데이터 관리</h4>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-blue-50 p-6 rounded-lg border border-blue-200">
          <h5 className="font-medium text-blue-800 mb-2">데이터 내보내기</h5>
          <p className="text-sm text-blue-600 mb-4">
            모든 분석 데이터를 JSON 파일로 내보냅니다.
          </p>
          <button
            onClick={onExportData}
            className="w-full px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            데이터 내보내기
          </button>
        </div>

        <div className="bg-red-50 p-6 rounded-lg border border-red-200">
          <h5 className="font-medium text-red-800 mb-2">데이터 초기화</h5>
          <p className="text-sm text-red-600 mb-4">
            모든 분석 데이터를 삭제합니다. 이 작업은 되돌릴 수 없습니다.
          </p>
          <button
            onClick={onClearData}
            className="w-full px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
          >
            데이터 삭제
          </button>
        </div>
      </div>

      <div className="bg-gray-50 p-4 rounded-lg">
        <h5 className="font-medium text-gray-700 mb-2">데이터 수집 정보</h5>
        <ul className="text-sm text-gray-600 space-y-1">
          <li>• 사용자 행동 이벤트 (버튼 클릭, 페이지 뷰 등)</li>
          <li>• SQL 변환 통계 (성공/실패, 실행 시간 등)</li>
          <li>• 방언 사용 패턴</li>
          <li>• 경고 및 오류 통계</li>
          <li>• 브라우저 및 화면 해상도 정보</li>
        </ul>
        <p className="text-xs text-gray-500 mt-2">
          모든 데이터는 로컬에 저장되며, Google Analytics가 설정된 경우에만 외부로
          전송됩니다.
        </p>
      </div>
    </div>
  );
};