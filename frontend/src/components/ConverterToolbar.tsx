import React from 'react';
import { useUserBehaviorTracking } from '../hooks/useAnalytics';

interface ConverterToolbarProps {
  onSwapDatabases: () => void;
  onOpenSnippetPanel: () => void;
  onOpenHistoryPanel: () => void;
  onOpenSettingsPanel: () => void;
  onOpenGuidePanel: () => void;
  onOpenAnalyticsPanel: () => void;
}

/**
 * 데이터베이스 선택 영역 아래의 툴바 버튼 컴포넌트 - Modernize 스타일
 */
export const ConverterToolbar: React.FC<ConverterToolbarProps> = ({
  onSwapDatabases,
  onOpenSnippetPanel,
  onOpenHistoryPanel,
  onOpenSettingsPanel,
  onOpenGuidePanel,
  onOpenAnalyticsPanel
}) => {
  const { trackButtonClick } = useUserBehaviorTracking();

  const buttonBaseClass = `
    flex items-center justify-center gap-2 px-4 py-2.5
    text-sm font-medium rounded-button
    transition-all duration-normal active:scale-[0.98]
  `;

  return (
    <div className="mt-4 flex flex-wrap gap-2">
      {/* 바꾸기 버튼 */}
      <button
        onClick={onSwapDatabases}
        className={`${buttonBaseClass} bg-white dark:bg-dark border border-light-300 dark:border-dark-200 text-dark dark:text-light hover:bg-light-100 dark:hover:bg-dark-200 shadow-card hover:shadow-card-hover`}
        title="방향 바꾸기"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
        </svg>
        <span className="hidden sm:inline">바꾸기</span>
      </button>

      {/* 스니펫 버튼 */}
      <button
        onClick={() => {
          onOpenSnippetPanel();
          trackButtonClick('open_snippet_panel');
        }}
        className={`${buttonBaseClass} bg-info-500 text-white hover:bg-info-600 shadow-button hover:shadow-button-hover`}
        title="SQL 스니펫"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
        </svg>
        <span className="hidden sm:inline">스니펫</span>
      </button>

      {/* 히스토리 버튼 */}
      <button
        onClick={onOpenHistoryPanel}
        className={`${buttonBaseClass} bg-secondary-400 text-white hover:bg-secondary-500 shadow-button hover:shadow-button-hover`}
        title="히스토리"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span className="hidden sm:inline">히스토리</span>
      </button>

      {/* 설정 버튼 */}
      <button
        onClick={onOpenSettingsPanel}
        className={`${buttonBaseClass} bg-dark/80 dark:bg-dark-300 text-white hover:bg-dark dark:hover:bg-dark-200 shadow-card hover:shadow-card-hover`}
        title="설정"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
        <span className="hidden sm:inline">설정</span>
      </button>

      {/* 가이드 버튼 */}
      <button
        onClick={() => {
          onOpenGuidePanel();
          trackButtonClick('open_guide_panel');
        }}
        className={`${buttonBaseClass} hidden sm:flex bg-success-500 text-white hover:bg-success-600 shadow-button hover:shadow-button-hover`}
        title="변환 가이드"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
        <span>가이드</span>
      </button>

      {/* 분석 버튼 */}
      <button
        onClick={() => {
          onOpenAnalyticsPanel();
          trackButtonClick('open_analytics_dashboard');
        }}
        className={`${buttonBaseClass} hidden sm:flex bg-warning-500 text-white hover:bg-warning-600 shadow-button hover:shadow-button-hover`}
        title="분석"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
        <span>분석</span>
      </button>
    </div>
  );
};
