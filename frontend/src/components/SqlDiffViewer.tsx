import React, { useState, useEffect } from 'react';
import { DiffEditor } from '@monaco-editor/react';
import { useTheme } from '../hooks/useTheme';

interface SqlDiffViewerProps {
  originalSql: string;
  convertedSql: string;
  height?: string;
  onClose?: () => void;
}

interface DiffStats {
  added: number;
  removed: number;
  modified: number;
}

export const SqlDiffViewer: React.FC<SqlDiffViewerProps> = ({
  originalSql,
  convertedSql,
  height = '500px',
  onClose
}) => {
  // 모바일에서는 기본적으로 inline 모드
  const [viewMode, setViewMode] = useState<'side-by-side' | 'inline'>('side-by-side');
  const [isMobile, setIsMobile] = useState(false);
  const { isDark } = useTheme();

  useEffect(() => {
    const checkMobile = () => {
      setIsMobile(window.innerWidth < 640);
      if (window.innerWidth < 640) {
        setViewMode('inline');
      }
    };
    checkMobile();
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  // 간단한 diff 통계 계산
  const calculateDiffStats = (): DiffStats => {
    const originalLines = originalSql.split('\n');
    const convertedLines = convertedSql.split('\n');

    let added = 0;
    let removed = 0;
    let modified = 0;

    const maxLen = Math.max(originalLines.length, convertedLines.length);

    for (let i = 0; i < maxLen; i++) {
      const origLine = originalLines[i]?.trim() || '';
      const convLine = convertedLines[i]?.trim() || '';

      if (!origLine && convLine) {
        added++;
      } else if (origLine && !convLine) {
        removed++;
      } else if (origLine !== convLine) {
        modified++;
      }
    }

    return { added, removed, modified };
  };

  const stats = calculateDiffStats();
  const hasChanges = stats.added > 0 || stats.removed > 0 || stats.modified > 0;

  return (
    <div className="sql-diff-viewer bg-white dark:bg-gray-900 border-2 border-gray-300 dark:border-gray-600 rounded-lg overflow-hidden">
      {/* 헤더 */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between px-3 sm:px-4 py-2 sm:py-3 bg-gradient-to-r from-gray-50 to-gray-100 dark:from-gray-800 dark:to-gray-700 border-b border-gray-200 dark:border-gray-600 gap-2">
        <div className="flex items-center gap-2 sm:gap-4">
          <h3 className="text-xs sm:text-sm font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-1.5 sm:gap-2">
            <svg className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-blue-600 dark:text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
            <span className="hidden sm:inline">변환 비교 (Diff)</span>
            <span className="sm:hidden">Diff</span>
          </h3>

          {/* Diff 통계 */}
          <div className="flex items-center gap-1 sm:gap-2 text-xs">
            {hasChanges ? (
              <>
                {stats.added > 0 && (
                  <span className="px-1.5 sm:px-2 py-0.5 bg-green-100 dark:bg-green-900 text-green-700 dark:text-green-300 rounded-full">
                    +{stats.added}
                  </span>
                )}
                {stats.removed > 0 && (
                  <span className="px-1.5 sm:px-2 py-0.5 bg-red-100 dark:bg-red-900 text-red-700 dark:text-red-300 rounded-full">
                    -{stats.removed}
                  </span>
                )}
                {stats.modified > 0 && (
                  <span className="px-1.5 sm:px-2 py-0.5 bg-yellow-100 dark:bg-yellow-900 text-yellow-700 dark:text-yellow-300 rounded-full">
                    ~{stats.modified}
                  </span>
                )}
              </>
            ) : (
              <span className="px-1.5 sm:px-2 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 rounded-full">
                -
              </span>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* 뷰 모드 토글 - 모바일에서 숨김 (자동 inline) */}
          <div className="hidden sm:flex bg-gray-200 dark:bg-gray-700 rounded-lg p-0.5">
            <button
              onClick={() => setViewMode('side-by-side')}
              className={`px-3 py-1 text-xs font-medium rounded-md transition-all ${
                viewMode === 'side-by-side'
                  ? 'bg-white dark:bg-gray-600 text-gray-800 dark:text-gray-200 shadow-sm'
                  : 'text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200'
              }`}
            >
              나란히 보기
            </button>
            <button
              onClick={() => setViewMode('inline')}
              className={`px-3 py-1 text-xs font-medium rounded-md transition-all ${
                viewMode === 'inline'
                  ? 'bg-white dark:bg-gray-600 text-gray-800 dark:text-gray-200 shadow-sm'
                  : 'text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200'
              }`}
            >
              인라인
            </button>
          </div>

          {onClose && (
            <button
              onClick={onClose}
              className="p-1.5 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-lg transition-colors"
              title="닫기"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* 라벨 - 데스크톱에서만 표시 */}
      <div className="hidden sm:flex border-b border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-800">
        <div className="flex-1 px-4 py-2 text-xs font-medium text-gray-600 dark:text-gray-400 border-r border-gray-200 dark:border-gray-600">
          <span className="inline-flex items-center gap-1">
            <span className="w-2 h-2 bg-red-400 rounded-full"></span>
            원본 SQL
          </span>
        </div>
        <div className="flex-1 px-4 py-2 text-xs font-medium text-gray-600 dark:text-gray-400">
          <span className="inline-flex items-center gap-1">
            <span className="w-2 h-2 bg-green-400 rounded-full"></span>
            변환된 SQL
          </span>
        </div>
      </div>

      {/* Monaco Diff Editor */}
      <DiffEditor
        height={isMobile ? '300px' : height}
        language="sql"
        original={originalSql}
        modified={convertedSql}
        options={{
          readOnly: true,
          renderSideBySide: viewMode === 'side-by-side' && !isMobile,
          minimap: { enabled: false },
          fontSize: isMobile ? 11 : 13,
          lineNumbers: isMobile ? 'off' : 'on',
          scrollBeyondLastLine: false,
          wordWrap: 'on',
          diffWordWrap: 'on',
          automaticLayout: true,
          renderOverviewRuler: !isMobile,
          ignoreTrimWhitespace: false,
          renderIndicators: true,
          originalEditable: false
        }}
        theme={isDark ? 'vs-dark' : 'vs'}
      />

      {/* 범례 */}
      <div className="flex items-center justify-center gap-3 sm:gap-6 px-3 sm:px-4 py-1.5 sm:py-2 bg-gray-50 dark:bg-gray-800 border-t border-gray-200 dark:border-gray-600 text-xs text-gray-600 dark:text-gray-400">
        <span className="flex items-center gap-1">
          <span className="w-2.5 h-2.5 sm:w-3 sm:h-3 bg-red-200 dark:bg-red-800 border border-red-300 dark:border-red-600 rounded"></span>
          <span className="hidden sm:inline">삭제된 부분</span>
          <span className="sm:hidden">삭제</span>
        </span>
        <span className="flex items-center gap-1">
          <span className="w-2.5 h-2.5 sm:w-3 sm:h-3 bg-green-200 dark:bg-green-800 border border-green-300 dark:border-green-600 rounded"></span>
          <span className="hidden sm:inline">추가된 부분</span>
          <span className="sm:hidden">추가</span>
        </span>
        <span className="flex items-center gap-1">
          <span className="w-2.5 h-2.5 sm:w-3 sm:h-3 bg-yellow-200 dark:bg-yellow-800 border border-yellow-300 dark:border-yellow-600 rounded"></span>
          <span className="hidden sm:inline">수정된 부분</span>
          <span className="sm:hidden">수정</span>
        </span>
      </div>
    </div>
  );
};