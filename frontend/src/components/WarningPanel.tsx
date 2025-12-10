import React, { useState } from 'react';
import {type ConversionWarning, WarningSeverity, WarningType, DialectType } from '../types';
import { WARNING_MESSAGES, WARNING_SOLUTIONS, SEVERITY_COLORS, analyzeWarnings, sortWarnings } from '../utils/warningSystem';

interface WarningPanelProps {
  warnings: ConversionWarning[];
  sourceDialect?: DialectType;
  targetDialect?: DialectType;
}
export const WarningPanel: React.FC<WarningPanelProps> = ({
  warnings, 
  sourceDialect, 
  targetDialect 
}) => {
  const [expandedWarnings, setExpandedWarnings] = useState<Set<number>>(new Set());
  const [filterSeverity, setFilterSeverity] = useState<WarningSeverity | 'all'>('all');
  const [filterType, setFilterType] = useState<WarningType | 'all'>('all');

  const toggleWarning = (index: number) => {
    const newExpanded = new Set(expandedWarnings);
    if (newExpanded.has(index)) {
      newExpanded.delete(index);
    } else {
      newExpanded.add(index);
    }
    setExpandedWarnings(newExpanded);
  };

  // 경고 분석
  const analysis = analyzeWarnings(warnings);
  const sortedWarnings = sortWarnings(warnings);
  
  // 필터링된 경고
  const filteredWarnings = sortedWarnings.filter(warning => {
    if (filterSeverity !== 'all' && warning.severity !== filterSeverity) return false;
    if (filterType !== 'all' && warning.type !== filterType) return false;
    return true;
  });

  if (warnings.length === 0) {
    return null;
  }

  // @ts-ignore
    return (
    <div className="warning-panel mt-4">
      {/* 헤더 및 통계 */}
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-200">
          변환 경고 ({filteredWarnings.length}개)
        </h3>
        <div className="flex items-center gap-2 text-sm">
          {analysis.bySeverity[WarningSeverity.ERROR] > 0 && (
            <span className="px-2 py-1 bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-400 rounded">
              오류 {analysis.bySeverity[WarningSeverity.ERROR]}개
            </span>
          )}
          {analysis.bySeverity[WarningSeverity.WARNING] > 0 && (
            <span className="px-2 py-1 bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-400 rounded">
              경고 {analysis.bySeverity[WarningSeverity.WARNING]}개
            </span>
          )}
          {analysis.bySeverity[WarningSeverity.INFO] > 0 && (
            <span className="px-2 py-1 bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-400 rounded">
              정보 {analysis.bySeverity[WarningSeverity.INFO]}개
            </span>
          )}
        </div>
      </div>

      {/* 필터 */}
      <div className="flex gap-4 mb-4">
        <select
          value={filterSeverity}
          onChange={(e) => setFilterSeverity(e.target.value as WarningSeverity | 'all')}
          className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 dark:text-gray-200 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="all">모든 심각도</option>
          <option value={WarningSeverity.ERROR}>오류</option>
          <option value={WarningSeverity.WARNING}>경고</option>
          <option value={WarningSeverity.INFO}>정보</option>
        </select>
        <select
          value={filterType}
          onChange={(e) => setFilterType(e.target.value as WarningType | 'all')}
          className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 dark:text-gray-200 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="all">모든 타입</option>
          <option value={WarningType.SYNTAX_DIFFERENCE}>문법 차이</option>
          <option value={WarningType.UNSUPPORTED_FUNCTION}>지원하지 않는 함수</option>
          <option value={WarningType.UNSUPPORTED_STATEMENT}>지원하지 않는 구문</option>
          <option value={WarningType.PARTIAL_SUPPORT}>부분 지원</option>
          <option value={WarningType.MANUAL_REVIEW_NEEDED}>수동 검토 필요</option>
          <option value={WarningType.PERFORMANCE_WARNING}>성능 경고</option>
          <option value={WarningType.DATA_TYPE_MISMATCH}>데이터타입 불일치</option>
        </select>
      </div>

      {/* 경고 목록 */}
      <div className="space-y-3">
        {filteredWarnings.map((warning, index) => (
          <div
            key={index}
            className={`border rounded-lg p-4 ${SEVERITY_COLORS[warning.severity].bg} ${SEVERITY_COLORS[warning.severity].border}`}
          >
            <div
              className="flex items-start cursor-pointer"
              onClick={() => toggleWarning(index)}
            >
              <div className="flex-shrink-0 mr-3 mt-0.5">
                <span className="text-2xl">
                  {WARNING_MESSAGES[warning.type]?.icon || '⚠️'}
                </span>
              </div>
              <div className="flex-1">
                <div className="flex items-center justify-between">
                  <div>
                    <h4 className={`font-medium ${SEVERITY_COLORS[warning.severity].text}`}>
                      {WARNING_MESSAGES[warning.type]?.title || warning.type}
                    </h4>
                    <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                      {warning.message}
                    </p>
                  </div>
                  <svg
                    className={`w-5 h-5 text-gray-400 dark:text-gray-500 transition-transform ${
                      expandedWarnings.has(index) ? 'rotate-180' : ''
                    }`}
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M19 9l-7 7-7-7"
                    />
                  </svg>
                </div>
                {expandedWarnings.has(index) && (
                  <div className="mt-3 text-sm text-gray-600 dark:text-gray-400">
                    {warning.suggestion && (
                      <div className="mb-3 p-3 bg-white dark:bg-gray-800 rounded border dark:border-gray-600">
                        <strong className="text-blue-600 dark:text-blue-400">💡 제안:</strong>
                        <p className="mt-1">{warning.suggestion}</p>
                      </div>
                    )}

                    {/* 해결 방법 가이드 */}
                    {sourceDialect && targetDialect && (() => {
                      const solutionEntry = WARNING_SOLUTIONS[warning.type];
                      const dialectSolutions = solutionEntry?.[sourceDialect as keyof typeof solutionEntry];
                      const targetSolutions = dialectSolutions?.[targetDialect as keyof typeof dialectSolutions] as string[] | undefined;
                      const solutions = targetSolutions || solutionEntry?.general;

                      if (!solutions || solutions.length === 0) return null;

                      return (
                        <div className="mb-3 p-3 bg-white dark:bg-gray-800 rounded border dark:border-gray-600">
                          <strong className="text-green-600 dark:text-green-400">🔧 해결 방법:</strong>
                          <ul className="mt-2 space-y-1">
                            {solutions.map((solution: string, idx: number) => (
                              <li key={idx} className="flex items-start">
                                <span className="text-green-500 dark:text-green-400 mr-2">•</span>
                                <span>{solution}</span>
                              </li>
                            ))}
                          </ul>
                        </div>
                      );
                    })()}

                    <div className="flex items-center gap-4 text-xs text-gray-500 dark:text-gray-500">
                      <span>타입: {warning.type}</span>
                      <span>심각도: {warning.severity}</span>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
