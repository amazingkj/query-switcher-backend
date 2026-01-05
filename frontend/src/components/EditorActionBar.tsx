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
 * SQL 에디터 영역의 액션 버튼 바 - Modernize 스타일
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

  const actionButtonBase = `
    flex items-center gap-1.5 px-3 sm:px-4 py-2 sm:py-2.5
    text-xs sm:text-sm font-medium rounded-button
    transition-all duration-normal active:scale-[0.98]
    disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100
  `;

  return (
    <div className="card p-4 mb-4">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div className="flex items-center gap-3">
          <h3 className="text-sm sm:text-base font-semibold text-dark dark:text-light">SQL 변환기</h3>
          {/* 뷰 모드 토글 */}
          {outputSql && (
            <div className="flex bg-light-200 dark:bg-dark-200 rounded-button p-1">
              <button
                onClick={() => onSetShowDiffView(false)}
                className={`px-3 py-1.5 text-xs font-medium rounded-button transition-all duration-fast ${
                  !showDiffView
                    ? 'bg-white dark:bg-dark text-dark dark:text-light shadow-card'
                    : 'text-dark/60 dark:text-light/60 hover:text-dark dark:hover:text-light'
                }`}
              >
                에디터
              </button>
              <button
                onClick={() => {
                  onSetShowDiffView(true);
                  trackFeatureUse('diff_view');
                }}
                className={`px-3 py-1.5 text-xs font-medium rounded-button transition-all duration-fast flex items-center gap-1 ${
                  showDiffView
                    ? 'bg-white dark:bg-dark text-dark dark:text-light shadow-card'
                    : 'text-dark/60 dark:text-light/60 hover:text-dark dark:hover:text-light'
                }`}
              >
                <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                </svg>
                <span className="hidden sm:inline">Diff</span>
              </button>
            </div>
          )}
        </div>

        <div className="flex flex-wrap gap-2">
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
            className={`${actionButtonBase} bg-white dark:bg-dark border border-light-300 dark:border-dark-200 text-dark dark:text-light hover:bg-light-100 dark:hover:bg-dark-200 shadow-card`}
            title="SQL 파일 업로드"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
            </svg>
            <span className="hidden sm:inline">업로드</span>
          </button>

          {/* 변환 버튼 */}
          <button
            onClick={onConvert}
            disabled={!inputSql.trim() || isLoading}
            className={`${actionButtonBase} flex-1 sm:flex-none bg-primary-500 text-white hover:bg-primary-600 shadow-button hover:shadow-button-hover`}
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            {isLoading ? '변환중...' : '변환'}
          </button>

          {/* 복사 버튼 */}
          <button
            onClick={onCopyResult}
            disabled={!outputSql}
            className={`${actionButtonBase} bg-success-500 text-white hover:bg-success-600 shadow-button hover:shadow-button-hover`}
            title="복사"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
            </svg>
            <span className="hidden sm:inline">복사</span>
          </button>

          {/* 다운로드 버튼 */}
          <button
            onClick={onDownload}
            disabled={!outputSql}
            className={`${actionButtonBase} bg-info-500 text-white hover:bg-info-600 shadow-button hover:shadow-button-hover`}
            title="다운로드"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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
            className={`${actionButtonBase} bg-secondary-400 text-white hover:bg-secondary-500 shadow-button hover:shadow-button-hover`}
            title="검증"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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
            className={`${actionButtonBase} ${
              showExecutionPanel
                ? 'bg-warning-600 ring-2 ring-warning-300'
                : 'bg-warning-500 hover:bg-warning-600'
            } text-white shadow-button hover:shadow-button-hover`}
            title="DB 테스트"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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
    </div>
  );
};
