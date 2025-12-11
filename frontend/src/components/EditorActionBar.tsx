import React from 'react';
import { DbStatusIndicator } from './DbStatusIndicator';
import { useUserBehaviorTracking } from '../hooks/useAnalytics';

interface EditorActionBarProps {
  inputSql: string;
  outputSql: string;
  isLoading: boolean;
  showDiffView: boolean;
  showExecutionPanel: boolean;
  onConvert: () => void;
  onCopyResult: () => void;
  onDownload: () => void;
  onTriggerFileUpload: () => void;
  onSetShowDiffView: (value: boolean) => void;
  onSetShowExecutionPanel: (value: boolean) => void;
  onOpenValidationPanel: () => void;
  fileInputRef: React.RefObject<HTMLInputElement | null>;
  onFileUpload: (event: React.ChangeEvent<HTMLInputElement>) => void;
}

/**
 * SQL 에디터 영역의 액션 버튼 바
 */
export const EditorActionBar: React.FC<EditorActionBarProps> = ({
  inputSql,
  outputSql,
  isLoading,
  showDiffView,
  showExecutionPanel,
  onConvert,
  onCopyResult,
  onDownload,
  onTriggerFileUpload,
  onSetShowDiffView,
  onSetShowExecutionPanel,
  onOpenValidationPanel,
  fileInputRef,
  onFileUpload
}) => {
  const { trackFeatureUse } = useUserBehaviorTracking();

  return (
    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 mb-3">
      <div className="flex items-center gap-2 sm:gap-3">
        <h3 className="text-sm sm:text-base font-semibold text-gray-800 dark:text-gray-200">SQL 변환기</h3>
        {/* 뷰 모드 토글 */}
        {outputSql && (
          <div className="flex bg-gray-200 dark:bg-gray-700 rounded-lg p-0.5">
            <button
              onClick={() => onSetShowDiffView(false)}
              className={`px-2 sm:px-3 py-1 text-xs font-medium rounded-md transition-all ${
                !showDiffView
                  ? 'bg-white dark:bg-gray-600 text-gray-800 dark:text-white shadow-sm'
                  : 'text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200'
              }`}
            >
              에디터
            </button>
            <button
              onClick={() => {
                onSetShowDiffView(true);
                trackFeatureUse('diff_view');
              }}
              className={`px-2 sm:px-3 py-1 text-xs font-medium rounded-md transition-all ${
                showDiffView
                  ? 'bg-white dark:bg-gray-600 text-gray-800 dark:text-white shadow-sm'
                  : 'text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200'
              }`}
            >
              <svg className="w-3 h-3 inline sm:mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
              </svg>
              <span className="hidden sm:inline">Diff</span>
            </button>
          </div>
        )}
      </div>

      <div className="flex gap-1.5 sm:gap-2">
        {/* 파일 업로드 (숨겨진 input) */}
        <input
          ref={fileInputRef}
          type="file"
          accept=".sql"
          onChange={onFileUpload}
          className="hidden"
        />

        {/* 업로드 버튼 */}
        <button
          onClick={onTriggerFileUpload}
          className="flex items-center px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg hover:border-gray-400 dark:hover:border-gray-500 hover:bg-gray-50 dark:hover:bg-gray-700 focus:outline-none focus:border-blue-500 transition-all duration-200"
          title="SQL 파일 업로드"
        >
          <svg className="w-3 h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
          </svg>
          <span className="hidden sm:inline">업로드</span>
        </button>

        {/* 변환 버튼 */}
        <button
          onClick={onConvert}
          disabled={!inputSql.trim() || isLoading}
          className="flex items-center flex-1 sm:flex-none px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-blue-700 transition-all duration-200"
        >
          <svg className="w-3 h-3 mr-1 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          {isLoading ? '변환중' : '변환'}
        </button>

        {/* 복사 버튼 */}
        <button
          onClick={onCopyResult}
          disabled={!outputSql}
          className="flex items-center px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white bg-emerald-600 rounded-lg hover:bg-emerald-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-emerald-700 transition-all duration-200"
          title="복사"
        >
          <svg className="w-3 h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
          </svg>
          <span className="hidden sm:inline">복사</span>
        </button>

        {/* 다운로드 버튼 */}
        <button
          onClick={onDownload}
          disabled={!outputSql}
          className="flex items-center px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white bg-violet-600 rounded-lg hover:bg-violet-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-violet-700 transition-all duration-200"
          title="다운로드"
        >
          <svg className="w-3 h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
          <span className="hidden sm:inline">다운로드</span>
        </button>

        {/* 검증 버튼 */}
        <button
          onClick={() => {
            onOpenValidationPanel();
            trackFeatureUse('sql_validation');
          }}
          disabled={!outputSql}
          className="flex items-center px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white bg-teal-600 rounded-lg hover:bg-teal-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-teal-700 transition-all duration-200"
          title="검증"
        >
          <svg className="w-3 h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span className="hidden sm:inline">검증</span>
        </button>

        {/* DB 테스트 버튼 */}
        <button
          onClick={() => {
            onSetShowExecutionPanel(!showExecutionPanel);
            trackFeatureUse('db_test');
          }}
          disabled={!outputSql}
          className={`flex items-center px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white rounded-lg transition-all duration-200 ${
            showExecutionPanel
              ? 'bg-cyan-700 ring-2 ring-cyan-400'
              : 'bg-cyan-600 hover:bg-cyan-700'
          } disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none`}
          title="DB 테스트"
        >
          <svg className="w-3 h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01" />
          </svg>
          <span className="hidden sm:inline">DB테스트</span>
        </button>
      </div>

      {/* DB 상태 인디케이터 */}
      <div className="hidden sm:block">
        <DbStatusIndicator compact />
      </div>
    </div>
  );
};