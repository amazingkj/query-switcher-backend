import React, { useState } from 'react';
import { getSnippetsByDialect, searchSnippets } from '../utils/sqlSnippets';
import { DialectType } from '../types';

interface SqlSnippetPanelProps {
  onSnippetSelect: (sql: string) => void;
  dialect: DialectType;
  isOpen: boolean;
  onClose: () => void;
}

export const SqlSnippetPanel: React.FC<SqlSnippetPanelProps> = ({
  onSnippetSelect,
  dialect,
  isOpen,
  onClose
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');

  if (!isOpen) return null;

  const filteredSnippets = searchQuery 
    ? searchSnippets(searchQuery, dialect)
    : getSnippetsByDialect(dialect);

  const categories = [
    { key: 'all', label: '전체' },
    { key: 'select', label: 'SELECT' },
    { key: 'insert', label: 'INSERT' },
    { key: 'update', label: 'UPDATE' },
    { key: 'delete', label: 'DELETE' },
    { key: 'join', label: 'JOIN' },
    { key: 'subquery', label: '서브쿼리' }
  ];

  const handleSnippetClick = (sql: string) => {
    onSnippetSelect(sql);
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full mx-4 max-h-[80vh] flex flex-col">
        {/* 헤더 */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-800">SQL 스니펫</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* 검색 및 필터 */}
        <div className="p-3 sm:p-4 border-b border-gray-200">
          <div className="flex flex-col sm:flex-row gap-3 sm:gap-4">
            <div className="flex-1">
              <input
                type="text"
                placeholder="스니펫 검색..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full p-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <div className="flex flex-wrap gap-1.5 sm:gap-2">
              {categories.map(category => (
                <button
                  key={category.key}
                  onClick={() => setSelectedCategory(category.key)}
                  className={`px-2 py-1 sm:px-3 sm:py-2 text-xs sm:text-sm rounded-lg transition-colors ${
                    selectedCategory === category.key
                      ? 'bg-blue-500 text-white'
                      : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                  }`}
                >
                  {category.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* 스니펫 목록 */}
        <div className="flex-1 overflow-y-auto p-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {filteredSnippets.map((snippet, index) => (
              <div
                key={index}
                className="border border-gray-200 rounded-lg p-4 hover:border-blue-300 hover:shadow-md transition-all cursor-pointer"
                onClick={() => handleSnippetClick(snippet.sql)}
              >
                <div className="flex items-start justify-between mb-2">
                  <h3 className="font-medium text-gray-800">{snippet.label}</h3>
                  <div className="flex gap-1">
                    {snippet.dialect.map(d => (
                      <span
                        key={d}
                        className="px-2 py-1 text-xs bg-blue-100 text-blue-800 rounded"
                      >
                        {d}
                      </span>
                    ))}
                  </div>
                </div>
                <p className="text-sm text-gray-600 mb-3">{snippet.description}</p>
                <pre className="text-xs bg-gray-50 p-2 rounded border overflow-x-auto">
                  <code>{snippet.sql}</code>
                </pre>
              </div>
            ))}
          </div>
          
          {filteredSnippets.length === 0 && (
            <div className="text-center py-8 text-gray-500">
              검색 결과가 없습니다.
            </div>
          )}
        </div>

        {/* 푸터 */}
        <div className="p-4 border-t border-gray-200 bg-gray-50">
          <p className="text-sm text-gray-600">
            스니펫을 클릭하면 에디터에 삽입됩니다.
          </p>
        </div>
      </div>
    </div>
  );
};
