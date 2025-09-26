import React, { useState } from 'react';
import { useConversionHistory } from '../hooks/useConversionHistory';
import {type ConversionHistoryItem, DialectType } from '../types';

interface ConversionHistoryPanelProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectHistory: (item: ConversionHistoryItem) => void;
}

export const ConversionHistoryPanel: React.FC<ConversionHistoryPanelProps> = ({
  isOpen,
  onClose,
  onSelectHistory
}) => {
  const { history, removeConversion, clearHistory, searchHistory, filterByDialect } = useConversionHistory();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedSourceDialect, setSelectedSourceDialect] = useState<DialectType | ''>('');
  const [selectedTargetDialect, setSelectedTargetDialect] = useState<DialectType | ''>('');

  if (!isOpen) return null;

  // 필터링된 히스토리
  const filteredHistory = React.useMemo(() => {
    let filtered = history;

    // 검색어 필터링
    if (searchQuery) {
      filtered = searchHistory(searchQuery);
    }

    // 방언 필터링
    if (selectedSourceDialect || selectedTargetDialect) {
      filtered = filterByDialect(
        selectedSourceDialect || undefined,
        selectedTargetDialect || undefined
      );
    }

    return filtered;
  }, [history, searchQuery, selectedSourceDialect, selectedTargetDialect, searchHistory, filterByDialect]);

  const handleSelectHistory = (item: ConversionHistoryItem) => {
    onSelectHistory(item);
    onClose();
  };

  const formatTime = (date: Date) => {
    return new Intl.DateTimeFormat('ko-KR', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(date);
  };

  const truncateSql = (sql: string, maxLength: number = 100) => {
    if (sql.length <= maxLength) return sql;
    return sql.substring(0, maxLength) + '...';
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-6xl w-full mx-4 max-h-[80vh] flex flex-col">
        {/* 헤더 */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-800">
            변환 히스토리 ({filteredHistory.length}개)
          </h2>
          <div className="flex gap-2">
            <button
              onClick={clearHistory}
              className="px-4 py-2 text-sm font-medium bg-red-600 text-white hover:bg-red-700 focus:outline-none focus:bg-red-700 transition-all duration-200"
            >
              전체 삭제
            </button>
            <button
              onClick={onClose}
              className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 focus:outline-none focus:bg-gray-100 transition-all duration-200"
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>

        {/* 검색 및 필터 */}
        <div className="p-4 border-b border-gray-200">
          <div className="flex gap-4">
            <div className="flex-1">
              <input
                type="text"
                placeholder="SQL 검색..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <select
              value={selectedSourceDialect}
              onChange={(e) => setSelectedSourceDialect(e.target.value as DialectType | '')}
              className="p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            >
              <option value="">모든 소스</option>
              <option value={DialectType.MYSQL}>MySQL</option>
              <option value={DialectType.POSTGRESQL}>PostgreSQL</option>
              <option value={DialectType.ORACLE}>Oracle</option>
              <option value={DialectType.TIBERO}>Tibero</option>
            </select>
            <select
              value={selectedTargetDialect}
              onChange={(e) => setSelectedTargetDialect(e.target.value as DialectType | '')}
              className="p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            >
              <option value="">모든 타겟</option>
              <option value={DialectType.MYSQL}>MySQL</option>
              <option value={DialectType.POSTGRESQL}>PostgreSQL</option>
              <option value={DialectType.ORACLE}>Oracle</option>
              <option value={DialectType.TIBERO}>Tibero</option>
            </select>
          </div>
        </div>

        {/* 히스토리 목록 */}
        <div className="flex-1 overflow-y-auto p-4">
          <div className="space-y-4">
            {filteredHistory.map((item) => (
              <div
                key={item.id}
                className="border border-gray-200 rounded-lg p-4 hover:border-blue-300 hover:shadow-md transition-all"
              >
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-1 text-xs bg-blue-100 text-blue-800 rounded">
                      {item.sourceDialect}
                    </span>
                    <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" />
                    </svg>
                    <span className="px-2 py-1 text-xs bg-green-100 text-green-800 rounded">
                      {item.targetDialect}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-sm text-gray-500">
                      {formatTime(item.timestamp)}
                    </span>
                    <button
                      onClick={() => removeConversion(item.id)}
                      className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 focus:outline-none focus:bg-red-50 transition-all duration-200"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-3">
                  <div>
                    <h4 className="text-sm font-medium text-gray-700 mb-1">원본 SQL</h4>
                    <pre className="text-xs bg-gray-50 p-2 rounded border overflow-x-auto">
                      <code>{truncateSql(item.originalSql)}</code>
                    </pre>
                  </div>
                  <div>
                    <h4 className="text-sm font-medium text-gray-700 mb-1">변환된 SQL</h4>
                    <pre className="text-xs bg-gray-50 p-2 rounded border overflow-x-auto">
                      <code>{truncateSql(item.convertedSql)}</code>
                    </pre>
                  </div>
                </div>

                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4 text-sm text-gray-500">
                    <span>변환 시간: {item.conversionTime}ms</span>
                    {item.warnings.length > 0 && (
                      <span className="text-yellow-600">
                        경고 {item.warnings.length}개
                      </span>
                    )}
                  </div>
                  <button
                    onClick={() => handleSelectHistory(item)}
                    className="px-4 py-2 text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 focus:outline-none focus:bg-blue-700 transition-all duration-200"
                  >
                    선택
                  </button>
                </div>
              </div>
            ))}
          </div>
          
          {filteredHistory.length === 0 && (
            <div className="text-center py-8 text-gray-500">
              {history.length === 0 ? '변환 히스토리가 없습니다.' : '검색 결과가 없습니다.'}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
